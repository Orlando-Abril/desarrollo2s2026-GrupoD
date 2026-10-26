# Research: Algoritmo de la Estrategia 2 "MOMENTUM"

Decisiones de diseño para [spec.md](spec.md). La fórmula exacta y los ejemplos numéricos están en [contracts/momentum-algorithm.md](contracts/momentum-algorithm.md).

## R1. Qué mide MOMENTUM frente a OFFICIAL (argumento para la defensa)

**Decision**: OFFICIAL mide el **nivel** de la temporada; MOMENTUM mide la **tendencia**: si en las últimas fechas el jugador rinde por encima o por debajo de lo que viene rindiendo en la temporada, si está disponible y si se hace expulsar.

**Rationale**:

- OFFICIAL compara las estadísticas acumuladas de un jugador contra las de su posición y fija un precio cercano a `baseValue + score × 0.10`. Un jugador de élite queda caro aunque venga de cinco partidos malos, y uno modesto queda barato aunque esté en racha.
- MOMENTUM toma las últimas 5 ventanas de 014 (≈ 5 fechas) y mueve el precio anterior un porcentaje acotado. El precio es acumulativo: una racha de varias semanas se compone (+3% por semana durante 5 semanas ≈ +16%) y una mala racha lo devuelve.
- Así las dos estrategias responden preguntas distintas del mercado ("¿qué tan bueno es?" vs. "¿cómo viene?"), y cambiar la estrategia activa tiene sentido económico.
- Ambas cumplen §1.2 de la constitución: usan las métricas de rendimiento con pesos diferenciados por posición, y MOMENTUM agrega minutos y tarjetas.

**Alternatives considered**:

- *MOMENTUM como OFFICIAL sobre ventanas* (mismo score contra la posición, pero sólo con las últimas fechas): mide el nivel reciente, no la tendencia, y obliga a armar una población de ventanas por corrida (ver R3).
- *Forma sobre cotizaciones previas* (lo que declaraba la v1 original: últimas 4 cotizaciones): la cotización ya es un derivado; medir la tendencia del precio lo realimenta sin mirar el juego, y no puede ver tarjetas ni minutos.

## R2. Redefinir MOMENTUM v1 en lugar de publicar v2

**Decision**: Se modifica `MomentumStrategyV1` en el lugar, se actualiza su huella en `PublishedStrategyVersionsTest.PUBLISHED` y se documenta la excepción en el contrato de 013 (§2, §4 y una nota en §5).

**Rationale**:

- La regla de 013 §5 existe para que el historial explique cada precio: una cotización guardada con `MOMENTUM v1` tiene que poder recalcularse con la definición v1.
- No existe ninguna cotización MOMENTUM: 016 la saltea con `NO_ALGORITHM` desde que existe la corrida. Cambiar v1 no deja ningún precio sin explicación.
- Publicar v2 dejaría una v1 que nunca se usó, que nadie puede ejecutar (no tendría algoritmo compatible) y que confunde la defensa.
- La excepción queda acotada: a partir de la primera cotización MOMENTUM, la regla vuelve a regir y cualquier cambio requiere v2.

**Alternatives considered**: publicar `MomentumStrategyV2` y retirar v1 del catálogo (más código y una versión fantasma en el registro de huellas, sin beneficio de trazabilidad).

## R3. Normalización sin población: contra el promedio de temporada del propio jugador

**Decision**: `MomentumFormAlgorithm` no sobrescribe `preparePopulation` (devuelve `null`). Cada métrica de una ventana se compara contra el promedio de temporada **del mismo jugador**, tomado de `ValuationInput.stats` (las estadísticas que 016 ya carga para todas las estrategias):

- Contadores (goles, asistencias, tiros, pases clave, gambetas, quites): `ratio = (valorVentana / minutosVentana) / (valorTemporada / minutosTemporada)` y `n = min(ratio / 2, 1)`. Rendir el promedio da 0.5, duplicarlo da 1 y no producir nada da 0.
- Rating: `n = clamp(0.5 + (ratingVentana − ratingTemporada) / (2 × ratingBand), 0, 1)` con `ratingBand = 1.5`.
- Métrica sin dato (`null` en la ventana, o promedio de temporada nulo o 0): se excluye en esa ventana y su peso se reparte. Si no queda ninguna métrica, la ventana vale 0.5.

**Rationale**:

- El pedido del equipo fija que el algoritmo no arma población. Sin población no hay mínimo–máximo por posición, así que hace falta una referencia por jugador; el promedio de temporada es la referencia natural para medir tendencia (R1).
- El punto neutro sale solo: un jugador que rinde lo de siempre tiene forma 0.5 y variación 0. Esto evita el sesgo que tendría comparar contra la posición (los jugadores por debajo de la media de su posición bajarían todas las semanas aunque jueguen como siempre).
- Es por jugador y puro: no depende del orden ni del tamaño de la población, lo que simplifica el determinismo (FR-022) y los tests.
- El rating no puede usar el cociente: los ratings se concentran entre 6 y 8, así que `8.0 / 7.0 = 1.14` daría 0.57 y la métrica casi no se movería (y con peso 0.60 en arqueros, el arquero quedaría casi sin forma). La diferencia absoluta con margen 1.5 lleva 8.5 vs. 7.0 a 1 y 5.5 vs. 7.0 a 0.
- Excluir los datos faltantes, en lugar de contarlos como 0, evita que un hueco de la fuente (por ejemplo gambetas sin dato en un snapshot) haga caer el precio.

**Alternatives considered**:

- *Sobrescribir `preparePopulation` con ventanas de todos los jugadores*: requiere que 016 cargue las ventanas de toda la población antes de calcular y cambie la firma de `PopulationEntry`; contradice el pedido y agranda el cambio en 016.
- *Topes absolutos por 90' declarados en la definición* (por ejemplo 1 gol por 90' = 1.0): mide nivel, no tendencia; requiere ~28 parámetros nuevos por posición y vuelve a introducir el sesgo de neutro.
- *Ventana contra la ventana anterior*: muy ruidoso (una fecha contra otra) y castiga al que viene de una fecha excepcional.

**Limitación conocida**: el promedio de temporada incluye a las propias ventanas, así que la señal se amortigua. Al comienzo de una temporada (tras un reinicio en 014) ventanas y promedio coinciden y la forma tiende a 0.5. Se acepta: el efecto es conservador y el tope ±15% ya limita los saltos.

## R4. Fórmula: ajustes a la propuesta del equipo

La propuesta del pedido era:

```text
variación = clamp((formScore ponderado − 0.5) × 2 × maxVariation − penalización por tarjetas, ±maxVariation) × disponibilidad
```

**Decision**: se mantiene la estructura (forma ponderada por recencia, centrada en 0.5, escalada a ±15%, con tarjetas que restan) con tres ajustes:

```text
R  = Σ ρⱼ·aⱼ·rⱼ / Σ ρⱼ·aⱼ            (ventanas jugadas; 0.5 si no hay)
D  = Σ ρⱼ·aⱼ / Σ ρⱼ                    (disponibilidad ponderada por recencia)
C  = Σ (ρⱼ/ρ₀)·(|w_Y|·amarillasⱼ + |w_R|·rojasⱼ)
S  = clamp(R − w_MIN·(1 − D) − C, 0, 1)        → score
v  = clamp(2·(S − 0.5)·scaleFactor, −maxVariation, +maxVariation)
valor = round4(anterior × (1 + v))
```

`ρⱼ` son los pesos de recencia (5, 4, 3, 2, 1 desde la más reciente), `aⱼ = min(minutosⱼ / 90, 1)` y `w_*` los pesos de la posición (promedio si tiene varias).

**Rationale de cada ajuste**:

1. **Disponibilidad resta en vez de multiplicar.** Con `× disponibilidad`, un jugador que no jugó (D = 0) tendría variación 0: su precio no cambiaría, lo que contradice el requerimiento 3 ("si no jugó, la cotización baja") y FR-015. Además, multiplicar achica también las caídas: un jugador en mala forma que deja de jugar perdería *menos* valor. Restar `w_MIN·(1 − D)` hace que la ausencia siempre reste, en proporción al peso de minutos de su posición (arquero 0.30, defensor 0.20, mediocampista y delantero 0.10), y no reste nada con disponibilidad plena. Sin minutos en 5 ventanas: −3% (mediocampista/delantero), −6% (defensor), −9% (arquero) por corrida.
2. **Las tarjetas restan a la forma, dentro del mismo tope.** Restarlas dentro de `S` (en unidades de forma, con los pesos negativos de la definición) en lugar de en puntos porcentuales usa directamente los pesos de 013 §4 y garantiza ±15% con un solo `clamp`. Una amarilla en la última ventana de un mediocampista resta 0.05 de forma (−1.5%) y una roja 0.15 (−4.5%); en la ventana más vieja pesan 1/5. Se pondera por `ρⱼ/ρ₀` (la más reciente vale 1) y no por los pesos normalizados, para que una tarjeta reciente cueste lo mismo tenga el jugador 1 o 5 ventanas.
3. **El rendimiento de cada ventana pesa por sus minutos (`ρⱼ·aⱼ`).** Un ingreso de 5 minutos con un gol daría un cociente enorme; ponderar por minutos evita que domine la forma. Las ventanas sin minutos no aportan rendimiento (FR-008) y, si ninguna tiene minutos, `R = 0.5`: la caída la produce sólo la disponibilidad, sin el salto discontinuo que daría `R = 0`.

Además se usa `scaleFactor` (0.15) para escalar y `maxVariation` (0.15) como tope, igual que OFFICIAL usa ambos. Con los dos en 0.15, forma 1 da exactamente +15% (FR-017) y el tope actúa de resguardo.

**Propiedades verificadas con el modelo de referencia** (ver ejemplos del contrato): forma neutra → 0%; forma máxima → +15%; menos minutos con igual rendimiento → estrictamente menos (∂S/∂D = w_MIN > 0); roja < amarilla < sin tarjeta; reciente pesa más que vieja; nunca fuera de ±15%.

**Alternatives considered**:

- *`S = D·R − C`* (la ausencia cuenta como rendimiento 0): una suplencia de 45' por fecha costaría −7.5% por corrida; demasiado severo.
- *Reparto de lo no jugado como neutro (`R = D·R_jugado + (1 − D)·0.5`)*: con mal rendimiento, jugar menos *subiría* la forma; viola FR-014.

## R5. Parámetros en `MomentumParameters`

**Decision**: `MomentumParameters` pasa a ser `(window, minHistory, recencyWeights, possibleMinutes, ratingBand)`:

| Campo | Valor v1 | Significado | Validación |
|-------|----------|-------------|------------|
| `window` | 5 | ventanas de 014 consideradas | `≥ 1` |
| `minHistory` | 2 | snapshots mínimos (= 1 ventana) | `2 ≤ minHistory ≤ window + 1` |
| `recencyWeights` | `[5, 4, 3, 2, 1]` | peso de cada ventana, de la más reciente a la más vieja | tamaño `window`, positivos, estrictamente decrecientes |
| `possibleMinutes` | 90 | minutos posibles por ventana | `> 0` |
| `ratingBand` | 1.5 | margen de rating que lleva la métrica a 0 o 1 | `> 0` |

**Rationale**: todos los números del modelo quedan en la definición (FR-020) y entran en la huella. Los pesos de recencia se guardan enteros (5..1) y el algoritmo los normaliza sobre las ventanas presentes; guardar `5/15` exigiría decimales periódicos. `minHistory` cambia de unidad (cotizaciones → snapshots) y se documenta en el Javadoc y el contrato. El factor 2 de `ratio / 2` y el neutro 0.5 son parte del modelo (como el mínimo–máximo de OFFICIAL), no parámetros.

`ValuationStrategyDefinition.fingerprint()` incorpora los campos nuevos en el tramo de `momentum`; la huella de OFFICIAL no cambia porque su `momentum` es `null` (`"-"`).

**Alternatives considered**: un record `MomentumModel` separado (duplica la relación definición ↔ parámetros); constantes en el algoritmo (viola FR-020).

## R6. Integración con 016

**Decision**: en `QuoteCalculationService.quote(...)`, si `definition.type() == MOMENTUM`, cargar `snapshotService.recentWindows(playerId, definition.momentum().window() + 1)` y pasarlas en `ValuationInput.windows`; para OFFICIAL se sigue pasando `List.of()`. No se cambian `previousQuotes` (016 ya pasa la última cotización de la misma estrategia, que es lo que usa MOMENTUM).

**Rationale**:

- `recentWindows(n)` toma los últimos `n` snapshots y devuelve `n − 1` ventanas en orden cronológico ascendente; `window + 1 = 6` snapshots → 5 ventanas.
- `snapshotAll(runId)` se ejecuta antes de cargar el plan, así que la ventana más reciente termina en el snapshot de la corrida actual (FR-025).
- Cargar dentro de la transacción del jugador hace que una falla de lectura cuente como falla de ese jugador (comportamiento de 016) sin abortar la corrida.
- Cuesta una consulta paginada por jugador y corrida (~2.500 en la escala del proyecto) contra el índice `(player_id, captured_at)` de 014; aceptable para un batch semanal/manual. Una carga en bloque queda como optimización futura.

**Alternatives considered**: cargar todas las ventanas en `loadPlan` (requiere una consulta nueva con ventana por jugador en el repositorio de 014; más cambio para una mejora no necesaria hoy).

## R7. Orden y validación de ventanas dentro del algoritmo

**Decision**: el algoritmo ordena las ventanas por `toCapturedAt` y luego `currentSnapshotId` (más reciente primero) y toma las primeras `window`. No asume el orden de la entrada.

**Rationale**: determinismo independiente de quién arme el `ValuationInput` (tests o 016) y protección ante más ventanas de las necesarias (escenario US1-5).

## R8. Testing

**Decision**:

- `MomentumFormAlgorithmTest` (unitario, sin Spring): escenarios de la spec (alza, baja, neutro, recencia, tope ±15%, amarilla vs. roja, tarjeta reciente vs. vieja, sin minutos, menos minutos, < 2 snapshots, sin cotización previa, previa de otra estrategia, previa sin valor, tipo incorrecto, sin posiciones, varias posiciones, métrica `null`, sin estadísticas de temporada, más de 5 ventanas, orden de entrada) y los ejemplos numéricos del contrato a 4 decimales.
- En `MomentumFormAlgorithmTest` también: descarte de ventanas anteriores al reinicio (§5.11), sin datos nuevos por mismo `runId` (§5.10) y por cotización previa posterior al snapshot, y construcción con `new MomentumFormAlgorithm()` sin colaboradores.
- Propiedad: para entradas generadas, `|valor/anterior − 1| ≤ 0.15 + 0.00005` (tolerancia de redondeo) y dos cálculos dan lo mismo.
- `MomentumStrategyV1Test`, `ValuationStrategyDefinitionTest`, `PublishedStrategyVersionsTest`: nuevos parámetros, validaciones de `MomentumParameters`, nueva huella y que cambiar cualquier campo nuevo cambia la huella.
- `QuoteCalculationServiceTest`: con un algoritmo MOMENTUM registrado no se saltea, recibe 5 ventanas (`recentWindows(id, 6)`), OFFICIAL recibe `List.of()` y `recentWindows` no se llama para OFFICIAL; un jugador sin estadísticas queda `INSUFFICIENT_HISTORY` en MOMENTUM y `NO_STATS` en OFFICIAL; sin datos nuevos se conserva el valor.
- `QuoteRecalculationE2ETest`: se actualiza la expectativa (MOMENTUM ya no se saltea; hay cotizaciones de ambas estrategias; la primera corrida deja MOMENTUM en `INSUFFICIENT_HISTORY` con 1.00), una segunda corrida con estadísticas cambiadas da `CALCULATED` y tres corridas seguidas sin cambios de estadísticas dan el mismo valor.

Los ejemplos numéricos se calcularon con un modelo de referencia independiente en aritmética decimal de 16 dígitos (equivalente a `MathContext.DECIMAL64`) y se verificaron a mano.

## R9. Detección de datos nuevos

**Decision**: hay datos nuevos si y sólo si el snapshot más reciente del jugador es de un `runId` distinto al de su última cotización MOMENTUM y su `capturedAt` es posterior al `calculatedAt` de esa cotización; sin cotización previa, hay datos nuevos. Sin datos nuevos, `CALCULATED` con variación 0 (FR-028).

**Rationale**: `snapshotAll` sólo guarda un snapshot si cambió el `fetchedAt` de las estadísticas. Sin esta regla, cada corrida sin sincronización (POST manual, scheduler, parate del campeonato) volvería a aplicar la variación sobre las mismas ventanas y el precio derivaría sin información nueva. OFFICIAL no tiene el problema porque su valor es absoluto. Cada condición cubre un caso: el `runId` reconoce el snapshot de la misma corrida, que siempre es posterior a su `calculatedAt` porque la corrida lo fija antes de `snapshotAll`; los tiempos cubren las corridas repetidas siguientes, cuya cotización ya es de otra corrida.

**Alternatives considered**: sólo `runId` (falla desde la tercera corrida seguida sin sincronización); sólo tiempos (trata el snapshot de la misma corrida como nuevo); que 016 cargue los `runId` de todas las cotizaciones MOMENTUM del jugador (más consultas y cambia lo que recibe el algoritmo).

## R10. Reinicio de temporada

**Decision**: se descartan las ventanas anteriores a la ventana de reinicio más reciente (`seasonReset = true`); la de reinicio se conserva.

**Rationale**: las ventanas de la temporada anterior se compararían contra el promedio de la temporada nueva, que tiene pocos minutos, y distorsionarían la forma en las primeras fechas.

**Alternatives considered**: tratarlas como una ventana más (distorsiona la forma); descartar también la de reinicio (pierde la primera fecha de la temporada nueva).
