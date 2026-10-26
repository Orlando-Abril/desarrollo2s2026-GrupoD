# Feature Specification: Algoritmo de la Estrategia 2 "MOMENTUM"

**Feature Branch**: `feature/estrategia-momentum`

**Created**: 2026-10-26

**Status**: Draft

**Input**: User description: "Algoritmo de la estrategia propia "MOMENTUM": la cotización se mueve según la forma reciente del jugador, con penalizaciones por tarjetas y por falta de disponibilidad. Usa las últimas 5 ventanas de estadísticas (014), rendimiento por 90' con los pesos por posición de MOMENTUM y más peso a lo reciente; penaliza amarillas y rojas con los pesos negativos de la definición; factor de disponibilidad; variación acotada a ±15% sobre la cotización MOMENTUM anterior (1.00 si no hay); INSUFFICIENT_HISTORY con menos de 2 snapshots; MOMENTUM v1 se redefine en MomentumStrategyV1 (nunca produjo una cotización) actualizando la huella y el contrato de 013 con el motivo; la corrida de recálculo pasa las ventanas del jugador sin cambios para OFFICIAL; determinístico. Regla: no modificar archivos de Entrega 1 sin consultar. Fuera de alcance: archivos yml o propiedades de configuración para las estrategias."

## Contexto y restricciones

- **013** declaró el catálogo de estrategias **en código** (no hay yml ni tabla). MOMENTUM v1 está en `MomentumStrategyV1`: valor base 1.00, factor de escala 0.15, variación máxima 0.30, parámetros de forma reciente `(window = 4, minHistory = 2)` medidos en **cotizaciones**, y pesos por posición para goles, asistencias, tiros, pases clave, gambetas, quites, rating y minutos (los positivos suman 1.00 en cada posición), más amarillas y rojas con peso negativo (la roja más negativa en cada posición). Está documentada en `specs/013-cotizacion-modelo/contracts/valuation-strategies.md` (§2 y §4) y protegida por un test de huella.
- **014** expone las ventanas recientes de un jugador: diferencias entre snapshots consecutivos de sus estadísticas acumuladas (≈ una fecha del campeonato), con minutos, contadores, tarjetas, rating y marca de reinicio de temporada.
- **015** definió el contrato del algoritmo de valuación: un cálculo puro que recibe definición, posiciones, estadísticas, cotizaciones previas de la misma estrategia y ventanas, y devuelve un resultado por jugador (estado, score y valor). Un resultado no `CALCULATED` no trae valor: quien llama decide qué registrar.
- **016** corre todas las estrategias del catálogo que tengan algoritmo. Hoy le pasa al algoritmo una lista de ventanas vacía y sólo la última cotización previa, y saltea MOMENTUM con motivo "sin algoritmo". Toma el snapshot de la corrida antes de calcular. Para un resultado no `CALCULATED` registra la última cotización de esa estrategia o el valor base. **Todavía no se registró ninguna cotización MOMENTUM.**

Esta feature agrega el algoritmo que interpreta MOMENTUM: a partir de las últimas ventanas de un jugador calcula su forma reciente (rendimiento, tarjetas y disponibilidad), la convierte en una variación porcentual acotada y la aplica sobre su cotización MOMENTUM anterior.

**Decisión: se redefine MOMENTUM v1 en el lugar.** La regla de versionado de 013 (§5) prohíbe modificar una versión publicada. Se hace una excepción documentada porque MOMENTUM v1 **nunca produjo una cotización** (no tenía algoritmo y 016 siempre la salteó), así que ningún precio registrado depende de sus parámetros actuales. Los nuevos parámetros (5 ventanas de estadísticas, mínimo 2 snapshots, ±15%, pesos de recencia) reemplazan a los anteriores en `MomentumStrategyV1`, se actualiza la huella del test y el contrato de 013 deja escrito el motivo de la excepción. Una vez registrada la primera cotización MOMENTUM, la regla de 013 §5 vuelve a regir sin excepciones.

Restricciones del equipo:

- Los archivos protegidos de Entrega 1 (`PlayerStats`, `PlayerStatsService`, `PlayerStatsRepository`, `Player`, `PlayerRepository`, `PlayerController`, `SecurityConfig`, `GlobalExceptionHandler`, los filtros de seguridad y `application.properties`) no se modifican sin consulta previa. Si un cambio resultara imprescindible, se detiene el trabajo y se pide aprobación indicando archivo, cambio mínimo y alternativa.
- OFFICIAL v1, su algoritmo y lo que 016 le pasa a OFFICIAL no se modifican.
- Los parámetros de las estrategias siguen declarados en código; no se crean archivos yml ni propiedades de configuración.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - La cotización sigue la forma reciente (Priority: P1)

Como dueño de un token, quiero que bajo MOMENTUM el precio de un jugador suba cuando en las últimas fechas viene rindiendo por encima de su propio promedio de temporada y baje cuando viene rindiendo por debajo, para que el valor refleje su tendencia y no sólo el nivel acumulado (que ya mide OFFICIAL).

**Why this priority**: Es el núcleo de la estrategia. Sin esto MOMENTUM no produce ninguna cotización y 016 la sigue salteando.

**Independent Test**: Con jugadores que tienen 5 ventanas de 90 minutos y sin tarjetas, se calcula MOMENTUM y se verifica que el que rinde por encima de su promedio de temporada sube, el que rinde por debajo baja, el que rinde igual no cambia, y ninguno supera el ±15%.

**Acceptance Scenarios**:

1. **Given** dos delanteros con el mismo promedio de temporada y 5 ventanas de 90 minutos sin tarjetas, uno con más goles y tiros por 90' que el otro en cada ventana, **When** se calcula MOMENTUM, **Then** el primero obtiene una variación mayor que el segundo y ambas quedan dentro de [−15%, +15%].
2. **Given** un jugador que en todas sus ventanas rinde exactamente su promedio de temporada, con 90 minutos por ventana y sin tarjetas, **When** se calcula MOMENTUM, **Then** la variación es 0.
3. **Given** un jugador con rendimiento alto en las 2 ventanas más recientes y bajo en las 3 más viejas, y otro con exactamente las mismas ventanas en orden inverso, **When** se calcula MOMENTUM, **Then** el primero obtiene una variación mayor que el segundo.
4. **Given** un jugador que en todas sus ventanas duplica o más su promedio de temporada en cada contador y supera su rating de temporada por el margen máximo, con plena disponibilidad y sin tarjetas, **When** se calcula MOMENTUM, **Then** la variación es exactamente +15% sobre su cotización anterior.
5. **Given** un jugador con más de 5 ventanas disponibles, **When** se calcula MOMENTUM, **Then** sólo cuentan las 5 más recientes y cambiar cualquier ventana anterior no altera el resultado.
6. **Given** un defensor y un delantero idénticos salvo que uno mejora sus quites y el otro sus goles respecto de su promedio, **When** se calcula MOMENTUM, **Then** cada uno se evalúa con los pesos de su posición (los quites pesan más en el defensor y los goles en el delantero).

---

### User Story 2 - Las tarjetas penalizan (Priority: P1)

Como dueño de un token, quiero que las amarillas y rojas recientes bajen la cotización, con una roja pesando más que una amarilla, para que la indisciplina tenga costo.

**Why this priority**: Es uno de los dos factores que diferencian MOMENTUM de OFFICIAL y es parte explícita de la consigna de la estrategia propia.

**Independent Test**: Dos jugadores idénticos en todo salvo tarjetas: se verifica que el que tiene tarjetas obtiene menor variación y que una roja resta más que una amarilla.

**Acceptance Scenarios**:

1. **Given** dos jugadores idénticos salvo que uno recibió una amarilla en la última ventana, **When** se calcula MOMENTUM, **Then** el amonestado obtiene una variación estrictamente menor.
2. **Given** dos jugadores idénticos salvo que uno recibió una amarilla y el otro una roja en la misma ventana, **When** se calcula MOMENTUM, **Then** el expulsado obtiene una variación estrictamente menor que el amonestado.
3. **Given** dos jugadores idénticos salvo que uno recibió la amarilla en la ventana más reciente y el otro en la más vieja de las 5, **When** se calcula MOMENTUM, **Then** la tarjeta reciente penaliza más.
4. **Given** un jugador con muchas tarjetas, **When** se calcula MOMENTUM, **Then** la variación nunca baja de −15%.

---

### User Story 3 - La falta de disponibilidad baja la cotización (Priority: P1)

Como dueño de un token, quiero que un jugador que no juega (lesión, suspensión, suplencia) pierda valor, para que el precio refleje si realmente está aportando.

**Why this priority**: Es el segundo factor diferencial pedido explícitamente; sin él, un jugador ausente conservaría su valor.

**Independent Test**: Dos jugadores con el mismo rendimiento por 90' pero distintos minutos jugados en las ventanas: se verifica que el de menos minutos obtiene menor variación y que uno sin minutos baja.

**Acceptance Scenarios**:

1. **Given** dos jugadores con el mismo rendimiento por 90', uno con 90 minutos en cada una de sus 5 ventanas y otro con 45, **When** se calcula MOMENTUM, **Then** el de 45 minutos obtiene una variación estrictamente menor.
2. **Given** un jugador que no sumó minutos en ninguna de sus ventanas, **When** se calcula MOMENTUM, **Then** el estado es `CALCULATED` y su cotización baja respecto de la anterior.
3. **Given** un jugador que jugó en las 4 ventanas más viejas pero no en la más reciente, y otro que no jugó en la más vieja pero sí en las 4 más recientes, con los mismos minutos y rendimiento en las ventanas jugadas, **When** se calcula MOMENTUM, **Then** el primero obtiene una variación menor.

---

### User Story 4 - Historial insuficiente y punto de partida (Priority: P1)

Como administrador, quiero que un jugador sin historial estadístico suficiente no cambie de precio y quede marcado, y que el primer cálculo parta de 1.00, para que la estrategia no invente forma donde no hay datos.

**Why this priority**: Es el caso de todos los jugadores en la primera corrida (un solo snapshot), así que define el comportamiento inicial del sistema.

**Independent Test**: Se calcula MOMENTUM para jugadores con 0, 1 y 2 snapshots, con y sin cotización MOMENTUM anterior, y se verifican estado y valor.

**Acceptance Scenarios**:

1. **Given** un jugador con 0 o 1 snapshot (ninguna ventana), **When** se calcula MOMENTUM, **Then** el resultado es `INSUFFICIENT_HISTORY`, sin valor ni score, y la corrida registra su cotización MOMENTUM anterior o 1.00 si no tiene.
2. **Given** un jugador con exactamente 2 snapshots (1 ventana) y sin cotización MOMENTUM anterior, **When** se calcula MOMENTUM, **Then** el resultado es `CALCULATED` y el valor es 1.00 × (1 + variación).
3. **Given** un jugador con cotización MOMENTUM anterior 1.20 y una cotización OFFICIAL más reciente de 0.90, **When** se calcula MOMENTUM, **Then** la variación se aplica sobre 1.20.
4. **Given** un jugador cuya última cotización MOMENTUM fue `INSUFFICIENT_HISTORY` con valor conservado 1.00, **When** ya tiene ventanas y se calcula MOMENTUM, **Then** la variación se aplica sobre 1.00.
5. **Given** la primera corrida después de que cada jugador tenga 1 snapshot previo, y las estadísticas del jugador se actualizaron desde el snapshot previo, **When** la corrida toma su propio snapshot y calcula, **Then** los jugadores con estadísticas obtienen MOMENTUM `CALCULATED` en esa misma corrida.
6. **Given** un jugador con cotización MOMENTUM `CALCULATED` de una corrida, y desde esa corrida no se capturó ningún snapshot nuevo suyo (no hubo sincronización), **When** se corre otra vez el recálculo, **Then** el resultado es `CALCULATED` con variación 0 y el valor es igual al anterior.

---

### User Story 5 - La corrida de recálculo incluye MOMENTUM (Priority: P1)

Como administrador, quiero que al recalcular cotizaciones MOMENTUM deje de figurar como salteada y se calcule junto con OFFICIAL, sin que OFFICIAL cambie, para que ambas estrategias tengan historial.

**Why this priority**: Sin esta integración el algoritmo existe pero nunca se ejecuta.

**Independent Test**: Se corre el recálculo para todas las estrategias sobre una población con ventanas y se verifica que MOMENTUM tiene cotizaciones, que no figura entre las salteadas y que los resultados OFFICIAL son idénticos a los de antes de la feature.

**Acceptance Scenarios**:

1. **Given** jugadores con al menos 2 snapshots, **When** se corre el recálculo para todas las estrategias, **Then** cada jugador tiene una cotización MOMENTUM nueva y el resumen no lista MOMENTUM como salteada.
2. **Given** la misma población antes y después de esta feature, **When** se corre el recálculo, **Then** las cotizaciones OFFICIAL (valor, score y estado) son idénticas.
3. **Given** que la estrategia activa es OFFICIAL, **When** se corre el recálculo, **Then** el valor de mercado sigue a OFFICIAL y no a MOMENTUM.

---

### User Story 6 - Resultado determinístico y auditable (Priority: P2)

Como equipo, quiero que con las mismas entradas MOMENTUM dé siempre el mismo resultado y que cada parámetro esté declarado en la definición de MOMENTUM v1, para poder defender el modelo y reproducir cualquier precio.

**Why this priority**: Es requisito de la consigna y habilita los tests, pero depende de que las historias anteriores existan.

**Independent Test**: Se calcula dos veces la misma población (también en distinto orden) y se comparan los resultados; se verifica que los ejemplos documentados del contrato coinciden al decimal y que un cambio de parámetro sin cambio de huella hace fallar los tests.

**Acceptance Scenarios**:

1. **Given** las mismas ventanas, posiciones, cotización anterior y definición, **When** se calcula MOMENTUM dos veces o con la población en otro orden, **Then** valor y score coinciden exactamente.
2. **Given** los ejemplos numéricos del contrato de MOMENTUM, **When** se calculan, **Then** valor y score coinciden con los documentados a 4 decimales.
3. **Given** la definición redefinida de MOMENTUM v1, **When** se modifica cualquiera de sus parámetros sin actualizar la huella publicada, **Then** la suite de tests falla.

---

### Edge Cases

- **Métrica sin dato** (`null` en la ventana, o sin promedio de temporada mayor a 0): se excluye en esa ventana y su peso se reparte entre las métricas restantes. Un dato faltante no hace bajar el precio.
- **Ninguna métrica evaluable en una ventana jugada** (por ejemplo, sin estadísticas de temporada): el rendimiento de esa ventana es neutro (0.5).
- **Ventana con minutos `null` o 0**: no aporta rendimiento, cuenta como 0 minutos para disponibilidad y sus tarjetas siguen contando.
- **Ventana con pocos minutos**: su rendimiento pesa en proporción a los minutos jugados, para que un ingreso corto no domine la forma.
- **Ventana con tarjetas `null`**: cuenta como 0 tarjetas.
- **Ventana de reinicio de temporada** (014): se trata como una ventana más, con los valores que entrega 014, y se descartan las ventanas anteriores a ella (son de la temporada previa y no se comparan contra el promedio de la nueva). Al empezar la temporada el promedio de temporada se parece a las ventanas y la forma tiende a neutra.
- **Sin datos nuevos desde la última cotización MOMENTUM** (dos o más corridas seguidas sin sincronización): el snapshot más reciente del jugador es de la misma corrida que su última cotización MOMENTUM, o fue capturado antes de ella; el valor se conserva con variación 0 y estado `CALCULATED`, en cada corrida repetida.
- **Jugador sin estadísticas**: no tiene snapshots, así que en MOMENTUM queda `INSUFFICIENT_HISTORY`, mientras que en OFFICIAL queda `NO_STATS`.
- **Menos de 5 ventanas** (entre 1 y 4): se usan todas y los pesos de recencia se renormalizan sobre las ventanas presentes.
- **Ventana con más minutos que los posibles** (dos partidos entre snapshots): la disponibilidad de la ventana se acota a 1.
- **Jugador sin posiciones**: `NO_STATS`, sin valor.
- **Jugador con varias posiciones**: se usan los pesos promedio de sus posiciones.
- **Jugador con ventanas pero sin estadísticas de temporada**: el rendimiento es neutro; la variación depende sólo de disponibilidad y tarjetas.
- **Cotización previa de otra estrategia en la entrada**: se rechaza con error que nombra al jugador, igual que en OFFICIAL.
- **Última cotización MOMENTUM con valor `null`**: se rechaza con error que nombra al jugador.
- **Definición de tipo distinto de MOMENTUM**: se rechaza con error.
- **Penalización por tarjetas que llevaría la forma por debajo de 0**: la forma se acota a [0, 1] y la variación nunca supera el tope.
- **Falla en la carga de ventanas de un jugador**: se trata como cualquier falla por jugador de 016 (el jugador cuenta como fallido y la corrida sigue).

## Requirements *(mandatory)*

### Functional Requirements

**Entrada y elegibilidad**

- **FR-001**: El algoritmo MUST usar, para cada jugador, las ventanas de estadísticas de 014, tomando como máximo las 5 más recientes (cantidad declarada en la definición) y descartando las anteriores a la ventana más reciente con reinicio de temporada (esa se conserva).
- **FR-002**: Con menos de 2 snapshots (0 ventanas) el resultado MUST ser `INSUFFICIENT_HISTORY`, sin valor ni score (mínimo declarado en la definición). La corrida registra la última cotización MOMENTUM del jugador, o el valor base 1.00 si no tiene, como ya hace 016.
- **FR-003**: Un jugador sin posiciones MUST resultar `NO_STATS`, sin valor ni score.
- **FR-004**: La cotización anterior MUST ser la más reciente de la estrategia MOMENTUM (de cualquier estado) entre las recibidas; si no hay ninguna, MUST usarse el valor base de la definición (1.00).
- **FR-005**: El algoritmo MUST rechazar con error que identifique al jugador una entrada cuyas cotizaciones previas incluyan otra estrategia o cuya cotización más reciente no tenga valor, y MUST rechazar una definición cuyo tipo no sea MOMENTUM.

**Rendimiento por ventana**

- **FR-006**: El rendimiento de cada ventana jugada MUST calcularse con las métricas por 90' (goles, asistencias, tiros, pases clave, gambetas, quites) y el rating de la ventana, usando los pesos por posición de MOMENTUM v1.
- **FR-007**: Cada métrica MUST normalizarse a [0, 1] contra el promedio de temporada del propio jugador, sin depender del resto de la población: 0.5 cuando la ventana iguala su promedio; para los contadores por 90', 1 cuando lo duplica o más y 0 cuando es 0; para el rating, 1 o 0 cuando lo supera o queda por debajo por el margen declarado en la definición. Una métrica sin dato se excluye en esa ventana y su peso se reparte proporcionalmente.
- **FR-008**: Una ventana sin minutos jugados (0 o `null`) MUST NOT participar del promedio de rendimiento y cada ventana jugada MUST pesar en proporción a sus minutos (acotados a los posibles); si ninguna ventana tiene minutos, el rendimiento MUST ser neutro (0.5) y la caída la produce la disponibilidad.
- **FR-009**: El rendimiento reciente MUST ser el promedio ponderado del rendimiento de las ventanas, con pesos de recencia declarados en la definición, estrictamente decrecientes desde la ventana más reciente hacia la más vieja y renormalizados sobre las ventanas presentes.
- **FR-010**: Para un jugador con varias posiciones, MUST usarse para cada métrica (incluidos minutos y tarjetas) el promedio de los pesos de sus posiciones.

**Penalización por tarjetas**

- **FR-011**: El algoritmo MUST restar a la forma una penalización por cada amarilla y roja recibida en las ventanas consideradas, usando los pesos negativos de amarilla y roja de la posición, ponderada por los mismos pesos de recencia.
- **FR-012**: La definición MUST mantener, en cada posición, pesos de amarilla y roja negativos y el de la roja más negativo que el de la amarilla.

**Disponibilidad**

- **FR-013**: La disponibilidad MUST calcularse como minutos jugados sobre minutos posibles en las ventanas consideradas, ponderada por recencia, con 90 minutos posibles por ventana (declarado en la definición) y la disponibilidad de cada ventana acotada a [0, 1].
- **FR-014**: La falta de disponibilidad MUST restar a la forma en proporción al peso de minutos de la posición, de modo que con igual rendimiento y tarjetas menos minutos impliquen estrictamente menor variación, y con disponibilidad plena no reste nada.
- **FR-015**: Un jugador sin minutos en ninguna de sus ventanas MUST obtener una variación estrictamente negativa.

**Variación y valor**

- **FR-016**: La forma del jugador MUST combinar rendimiento reciente, disponibilidad y penalización por tarjetas en un índice acotado a [0, 1], donde 0.5 corresponde a variación 0.
- **FR-017**: La variación porcentual MUST ser lineal en la distancia de la forma a 0.5: 0 con forma 0.5, +15% con forma 1 y −15% con forma 0, y MUST quedar siempre dentro de [−15%, +15%] (variación máxima declarada en la definición), salvo lo indicado en FR-028.
- **FR-018**: El valor MUST ser `anterior × (1 + variación)`, redondeado a 4 decimales HALF_UP, y el score registrado MUST ser la forma redondeada a 4 decimales.
- **FR-019**: El resultado MUST ser `CALCULATED` con valor y score cuando el jugador tiene al menos una ventana y al menos una posición.
- **FR-028**: Hay datos nuevos si y sólo si el snapshot más reciente del jugador es de un `runId` distinto al de su última cotización MOMENTUM **y** su `capturedAt` es posterior al `calculatedAt` de esa cotización; sin cotización MOMENTUM previa, hay datos nuevos. Sin datos nuevos, el resultado MUST ser `CALCULATED` con variación 0 (valor igual al anterior) y el score de la forma calculada. La comparación de `runId` cubre el snapshot de la misma corrida (que siempre es posterior a su `calculatedAt`, porque la corrida lo fija antes de tomar el snapshot) y la de tiempos cubre las corridas repetidas siguientes. No agrega estados a `QuoteStatus`.

**Definición y determinismo**

- **FR-020**: MOMENTUM v1 MUST redefinirse en su clase de definición existente con: valor base 1.00, variación máxima 0.15, factor de escala 0.15, 5 ventanas, mínimo 2 snapshots, pesos de recencia, 90 minutos posibles por ventana, margen de rating 1.5 y los pesos por posición actuales (métricas, minutos, amarilla y roja). Ningún parámetro MUST quedar fijo dentro del algoritmo.
- **FR-021**: La huella publicada de MOMENTUM v1 MUST actualizarse a la nueva definición, y el contrato de 013 MUST documentar los nuevos parámetros y el motivo de la excepción a la regla de versionado (v1 nunca produjo una cotización).
- **FR-022**: Con las mismas entradas el algoritmo MUST producir exactamente el mismo resultado, sin depender del orden de la población, de la hora, de la zona horaria ni de aleatoriedad; los intermedios MUST usar aritmética decimal.
- **FR-023**: El algoritmo MUST ser un cálculo puro: no guarda cotizaciones, no actualiza el valor de mercado, no consulta fuentes externas ni expone endpoints.

**Integración con la corrida de recálculo (016)**

- **FR-024**: Al registrar el algoritmo, la corrida MUST calcular MOMENTUM junto con OFFICIAL y dejar de listarla como salteada.
- **FR-025**: La corrida MUST pasarle a MOMENTUM las 5 ventanas más recientes de cada jugador, incluyendo el snapshot tomado al inicio de esa misma corrida, y su última cotización MOMENTUM.
- **FR-026**: Lo que la corrida le pasa a OFFICIAL y sus resultados MUST NOT cambiar; las ventanas sólo se cargan para las estrategias que las usan.
- **FR-027**: Esta feature MUST NOT modificar OFFICIAL v1, su algoritmo, archivos protegidos de Entrega 1 sin aprobación previa del equipo, ni agregar archivos yml o propiedades de configuración para las estrategias.

### Key Entities

- **Definición MOMENTUM v1** (redefinida): valor base, factor de escala, variación máxima, cantidad de ventanas, snapshots mínimos, pesos de recencia, minutos posibles por ventana, margen de rating y pesos por posición (métricas de rendimiento, minutos, amarillas y rojas). Protegida por huella.
- **Ventana de estadísticas** (014): minutos, contadores, tarjetas y rating atribuibles al intervalo entre dos snapshots consecutivos, con marca de reinicio y la corrida (`runId`) del snapshot que la cierra.
- **Promedio de temporada**: estadísticas acumuladas actuales del jugador (por 90' y rating), contra las que se compara cada ventana.
- **Forma**: índice en [0, 1] que combina rendimiento reciente ponderado, disponibilidad y penalización por tarjetas; se registra como score.
- **Resultado MOMENTUM**: estado (`CALCULATED`, `NO_STATS`, `INSUFFICIENT_HISTORY`), score y valor por jugador, según el contrato de 015.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En una corrida para todas las estrategias, MOMENTUM deja de figurar como salteada y el 100% de los jugadores con al menos una ventana y una posición obtienen una cotización MOMENTUM `CALCULATED`.
- **SC-002**: En el 100% de los casos, la variación entre la cotización MOMENTUM nueva y la anterior queda dentro de ±15%.
- **SC-003**: El 100% de los jugadores con menos de 2 snapshots quedan `INSUFFICIENT_HISTORY` y su cotización MOMENTUM no cambia.
- **SC-004**: En los escenarios de prueba, el 100% de los pares "por encima vs. por debajo de su promedio", "reciente vs. viejo", "con tarjeta vs. sin tarjeta", "roja vs. amarilla" y "menos minutos vs. más minutos" ordenan la variación en el sentido esperado.
- **SC-005**: Calcular dos veces la misma población, en cualquier orden, produce resultados idénticos al último decimal en el 100% de los jugadores.
- **SC-006**: Cada ejemplo numérico documentado en el contrato de MOMENTUM se reproduce exactamente a 4 decimales.
- **SC-007**: Un cambio en cualquier parámetro de MOMENTUM v1 sin actualizar su huella es detectado por la suite de tests.
- **SC-008**: Las cotizaciones OFFICIAL de una corrida son idénticas antes y después de la feature en el 100% de los jugadores.
- **SC-009**: La feature introduce 0 endpoints, 0 tablas, 0 archivos de configuración, 0 consultas externas y 0 cambios a OFFICIAL v1 o a archivos protegidos de Entrega 1.

## Assumptions

- **Ventana ≈ fecha**: cada ventana de 014 representa aproximadamente una fecha del campeonato; por eso los minutos posibles por ventana son 90.
- **Pesos de recencia**: 5, 4, 3, 2, 1 desde la ventana más reciente a la más vieja. Para el rendimiento (y la disponibilidad) se normalizan para sumar 1 sobre las ventanas presentes; las tarjetas usan el peso relativo a la ventana más reciente (ρⱼ/ρ₀, la más reciente vale 1).
- **Pesos por posición**: se conservan los de la tabla actual de MOMENTUM v1 (013 §4); sólo cambian los parámetros de forma reciente y la variación máxima. Los pesos de tarjetas se aplican por tarjeta.
- **Factor de escala igual a la variación máxima (0.15)**: con forma 1 la variación llega exactamente a +15% y el tope actúa como resguardo.
- **Forma neutra**: un jugador que rinde su promedio de temporada, juega todos los minutos y no recibe tarjetas tiene forma 0.5 y variación 0; el contrato de MOMENTUM fija la fórmula exacta y ejemplos numéricos.
- **Normalización contra el propio jugador**: MOMENTUM mide tendencia, así que cada ventana se compara contra el promedio de temporada del mismo jugador (las estadísticas que la corrida ya carga). No necesita armar una población por corrida; OFFICIAL sigue midiendo el nivel contra la posición.
- **Margen de rating**: 1.5 puntos por encima o por debajo del rating de temporada llevan la métrica a 1 o 0.
- **Mercado**: el valor de mercado sólo sigue a MOMENTUM si es la estrategia activa; la estrategia activa por defecto sigue siendo OFFICIAL.
- **Orden del snapshot**: 016 toma el snapshot de la corrida antes de calcular las estrategias, así que las ventanas incluyen el de la corrida actual.

## Out of Scope

- Crear archivos yml, tablas o propiedades de configuración para las estrategias.
- Publicar una MOMENTUM v2 o cambiar la regla de versionado de 013 más allá de documentar esta excepción.
- Cambiar la orquestación, los endpoints, el resumen o la persistencia de la corrida de recálculo (016) más allá de proveer las ventanas y la cotización previa que MOMENTUM necesita.
- Cambiar OFFICIAL v1, su algoritmo o lo que recibe.
- Cambiar cómo 014 captura snapshots o deriva ventanas.
- Cambiar la estrategia activa por defecto.
- Exponer la forma, la disponibilidad o la penalización por separado en la API.
- Ajuste por rival, localía o minutos posibles reales por partido (el calendario no está disponible).
