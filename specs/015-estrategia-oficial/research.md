# Research: Algoritmo de la Estrategia 1 "OFFICIAL"

## D1. Interfaz común `ValuationAlgorithm`

**Decision**: Usar `interface ValuationAlgorithm { StrategyType type(); QuoteResult calculate(ValuationInput input, ValuationStrategyDefinition strategy); }` en `service/valuation`. `ValuationInput` lleva:

- `playerId`;
- `positions`;
- `stats` (`PlayerStats`, que puede ser null);
- `population` (`NormalizedPopulation`);
- `previousQuotes`: `List<PlayerQuote>` en orden descendente por `calculatedAt`;
- `windows`: `List<StatsWindow>` de 014.

**Rationale**:
- 016 puede resolver la implementación con `definition.type()` sin conocer clases concretas.
- MOMENTUM (019) recibe ya armado lo que necesita: las últimas N cotizaciones previas (`window` y `minHistory` de `MomentumParameters`) y las ventanas de forma reciente.
- Reutilizar `PlayerQuote` y `StatsWindow` evita tipos espejo.

**Alternatives considered**:
- Una interfaz por estrategia: obliga a 016 a ramificar por tipo.
- Pasar sólo `BigDecimal previousValue`: alcanza para OFFICIAL, pero no para MOMENTUM, que mira varias cotizaciones y su estado.
- Pasar la entidad `Player`: arrastra relaciones JPA a tests puros. Con `playerId` + `positions` alcanza.

## D2. `QuoteResult` con exclusiones por posición

**Decision**: `QuoteResult(BigDecimal value, BigDecimal score, QuoteStatus status, Map<Position, Set<Metric>> excludedMetrics)`, con fábricas `calculated(...)`, `noStats()` e `insufficientHistory()` (esta última la usa 019). El constructor valida que CALCULATED tenga valor y score, y que los demás estados no tengan ninguno de los dos. El mapa se copia de forma inmutable.

**Rationale**: FR-002 y FR-013 piden indicar qué métricas se excluyeron y en qué posición. Un jugador MIDFIELDER + FORWARD puede tener exclusiones distintas en cada posición.

**Alternatives considered**:
- `Set<Metric>` plano: pierde la posición y no explica bien a un jugador multiposición.
- Un valor en NO_STATS: la spec dice que quien llama conserva la cotización anterior (016), así que el algoritmo no inventa un valor.

## D3. Dónde vive el umbral de minutos

**Decision**: `OfficialPerformanceAlgorithm` recibe `@Value("${valuation.official.min-minutes:90}")` y valida que sea mayor que 0. `MetricNormalizer` es una clase pura que se construye con ese umbral. El algoritmo expone `normalize(Collection<PopulationEntry>)`. `NormalizedPopulation` guarda su `minMinutes` y `calculate` rechaza con `IllegalArgumentException` una población con otro umbral. El chequeo de NO_STATS del jugador que se valúa lee `population.minMinutes()`, no el campo del `@Value`. Así, dentro del cálculo hay una sola fuente del corte.

**Rationale**:
- La exclusión de NO_STATS tiene que ocurrir antes del min-max (FR-007), así que el normalizador necesita el umbral.
- Ubicarlo en el algoritmo, como pidió el equipo, y propagarlo deja una única fuente.
- La validación cruzada impide combinar por error una población y un algoritmo inconsistentes.
- El default vive en el código, así que `application.properties` (Entrega 1) no se toca.

**Alternatives considered**:
- Que `MetricNormalizer` sea un bean con su propio `@Value`: duplica la clave de la propiedad.
- Que 016 pase el umbral: lo expone fuera de OFFICIAL.
- Aceptar 0: dividiría por cero en el cálculo por 90.

## D4. Población normalizada independiente de los pesos

**Decision**: `NormalizedPopulation` guarda, por jugador elegible y por posición, las 7 métricas normalizadas (`GOALS`, `ASSISTS`, `SHOTS`, `KEY_PASSES`, `DRIBBLES`, `TACKLES`, `RATING`) y, por posición, las métricas sin dato en toda la población de esa posición. No conoce los pesos. El algoritmo decide qué excluir: las métricas sin dato con peso > 0.

`PopulationEntry`, `NormalizedPopulation` y `MetricNormalizer` viven en `service/valuation`, no en `official`:

- no tienen nada propio de OFFICIAL y MOMENTUM (019) los va a reusar;
- `ValuationInput`, que es compartido, referencia a `NormalizedPopulation` sin depender de un subpaquete de OFFICIAL;
- al compartir paquete con el normalizador, el constructor de `NormalizedPopulation` sigue package-private.

En `official` queda sólo `OfficialPerformanceAlgorithm`. Esta ubicación se descartó primero dentro de `official` y se corrigió en el análisis (I2).

**Rationale**:
- Se calcula una sola vez por corrida y no depende de la versión de la estrategia, así que una OFFICIAL v2 con otros pesos reutiliza la misma población.
- La regla "con peso cero no se informa" (assumption de la spec) queda en el único lugar que conoce los pesos.

**Alternatives considered**:
- Normalizar dentro de `calculate`: recalcularía min/max por cada jugador, O(n²) por corrida.
- Que la población ya tenga los pesos repartidos: la ata a una definición.

## D5. Aritmética

**Decision**: Usar `BigDecimal` en todo el cálculo, con `MathContext.DECIMAL64` para divisiones (por 90', min-max, reparto y promedio). Los valores intermedios no se redondean. El por 90 se calcula como `valor × 90 / minutos`.

**Rationale**: Garantiza el determinismo (FR-022) sin errores de `double` y es la misma precisión que usa `StatsSnapshotService` (014) para el rating.

**Alternatives considered**:
- `double` y redondear al final: los resultados podrían variar en el último decimal según el orden de las operaciones.
- Una escala fija en cada paso: acumula error de redondeo.

## D6. Redondeo del score y del valor

**Decision**:
- Primero se redondea el score a 4 decimales HALF_UP y se acota a [0,1].
- Después se calcula el valor con ese score redondeado: `baseValue + score × scaleFactor`.
- Ese valor se acota respecto de la cotización anterior sin redondear.
- Por último, el valor se redondea a 4 decimales HALF_UP.

**Rationale**:
- `PlayerQuote.score` y `PlayerQuote.value` son `scale = 4`.
- Si el valor sale del score guardado, cualquiera puede verificar con lo persistido que `value = round4(1.00 + score × 0.10)`, sin acceder a los intermedios. Esto sirve para la defensa del modelo.
- Con `scaleFactor` 0.10, redondear el score a 4 decimales mueve el valor a lo sumo 0.000005 antes de su propio redondeo, así que no cambia nada que importe.
- Un score de 4 decimales por 0.10 da un valor de 5 decimales, así que el valor también se redondea con HALF_UP. El test lo muestra con un empate: score 0.5645 → 1.05645 → **1.0565** (HALF_EVEN daría 1.0564).
- Acotar antes de redondear sigue la assumption de la spec.

**Límite de la verificación**: cuando actúa el tope de `maxVariation`, el valor no es `1 + score × 0.10` sino `round4(anterior × (1 ± maxVariation))`, así que con el score guardado no se reconstruye. Para auditar ese caso hace falta la cotización anterior.

**Alternatives considered**:
- Calcular el valor con el score sin redondear: en casos límite (score 0.95449…) el valor guardado difiere en 0.0001 del que se reconstruye con el score guardado.

## D7. Reparto proporcional de pesos excluidos

**Decision**: Por posición, `peso' = peso / Σ pesos de las métricas no excluidas`. Se excluyen sólo las métricas con peso > 0 que no tienen dato en ningún jugador elegible de la posición. Las de peso 0 nunca se informan. Si todas las métricas con peso > 0 quedan excluidas, la posición no produce score.

**Rationale**: Es la regla de FR-013 y FR-015. Mantiene la suma de pesos efectivos en 1.00, así que el score sigue en [0,1] y los pesos relativos de la consigna no cambian entre sí.

**Alternatives considered**:
- Repartir el peso en partes iguales: altera las proporciones de la consigna.
- Contar la métrica como 0 para todos: comprime los scores. Es el problema que la spec quiere evitar.

## D8. Jugadores con varias posiciones

**Decision**: El score es el promedio simple, con DECIMAL64, de los scores de cada posición que produjo uno, recorriendo las posiciones en orden de declaración de `Position`. El jugador entra en la población de cada una de sus posiciones.

**Rationale**: Es la regla de FR-016. Recorrer en orden de enum hace que el resultado no dependa del orden del `Set` de posiciones.

**Alternatives considered**:
- Tomar la posición principal: el modelo no tiene ese dato.
- Tomar el máximo: favorece a los jugadores multiposición.

## D9. Cotización anterior

**Decision**: La cotización anterior es `previousQuotes.get(0).getValue()`, sea cual sea su estado. Si la lista está vacía, no se acota. Además se valida:

- `ValuationInput` rechaza en su constructor una lista que no esté en orden descendente por `calculatedAt`. Acepta empates, que 013 resuelve por id. Es una regla común que también protege a MOMENTUM.
- `calculate` rechaza cotizaciones cuyo `strategyCode` no sea `strategy.code()`. Si no, una cotización de MOMENTUM haría calcular el tope contra otro precio.
- `calculate` rechaza que `get(0).getValue()` sea null, con un mensaje que nombra al jugador. 016 tiene que guardar los NO_STATS con el precio conservado, no con valor null.
- Estas validaciones son del contrato de entrada y no del jugador. Por eso corren primero en `calculate`, antes de evaluar NO_STATS, y el orden ya se validó al construir `ValuationInput`. Así un error de 016 falla siempre, aunque el jugador sea NO_STATS, y no se esconde según qué jugador lo haya disparado.

Todos los rechazos son `IllegalArgumentException`.

**Rationale**:
- Las cotizaciones NO_STATS también tienen valor (013, y así se le pidió a 016), y es el precio vigente en el mercado.
- Acotar contra el precio vigente es lo que evita saltos para quien opera.
- Las validaciones convierten un error de integración de 016 en una falla clara en lugar de un precio mal acotado.

**Alternatives considered**:
- Usar la última CALCULATED: permitiría saltos mayores al 20% respecto del precio vigente después de una corrida NO_STATS.
- Dejar el orden y la estrategia sólo documentados en el contrato: el chequeo es O(n) sobre una lista corta y evita errores silenciosos.
- Ignorar un valor null y no acotar: oculta un error de 016 y permite un salto sin tope.

## D10. Determinismo

**Decision**:
- `MetricNormalizer` calcula min/max, que no dependen del orden.
- Las estructuras internas son `EnumMap` para posición y métrica, y un `Map` por `playerId`.
- Los recorridos van siempre en orden de enum.
- Un `playerId` duplicado en la población lanza `IllegalArgumentException` con el id.
- Los tests verifican resultados idénticos con 10 permutaciones de la población.

**Rationale**: FR-021 y SC-006. Con `BigDecimal`, sumar en un orden fijo produce resultados idénticos bit a bit.

**Alternatives considered**: ordenar la población por id antes de normalizar. No hace falta porque min/max son conmutativos, pero el test de permutaciones lo cubre igual.
