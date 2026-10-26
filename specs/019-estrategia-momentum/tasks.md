---

description: "Tareas de implementación del algoritmo de la Estrategia 2 MOMENTUM"
---

# Tasks: Algoritmo de la Estrategia 2 "MOMENTUM"

**Input**: Design documents from `/specs/019-estrategia-momentum/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/momentum-algorithm.md](contracts/momentum-algorithm.md), [quickstart.md](quickstart.md)

**Tests**: La spec exige escenarios verificables, ejemplos numéricos reproducibles a 4 decimales (SC-006) y detección de cambios de huella (SC-007). En cada historia los tests se escriben primero y deben fallar antes de implementar.

**Organization**: Las tareas se agrupan por historia. Reglas:

- No modificar archivos protegidos de Entrega 1 (`PlayerStats`, `PlayerStatsService`, `PlayerStatsRepository`, `Player`, `PlayerRepository`, `PlayerController`, `SecurityConfig`, `GlobalExceptionHandler`, filtros de seguridad, `application.properties`) sin aprobación.
- No modificar `OfficialStrategyV1` ni `OfficialPerformanceAlgorithm`.
- No crear yml ni propiedades de configuración para estrategias.
- Las rutas son relativas a la raíz del repo; `<val>` = `backend/src/main/java/com/example/demo/service/valuation`, `<tval>` = `backend/src/test/java/com/example/demo/service/valuation`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias con tareas incompletas del mismo grupo).
- **[Story]**: Historia de usuario trazable (`US1`..`US6`).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Exponer la corrida del snapshot en las ventanas de 014 y crear fixtures de test reutilizables por todas las historias.

- [X] T001 Agregar `UUID currentRunId` (no nulo, con `Objects.requireNonNull`) a `StatsWindow` en `<val>/StatsWindow.java`, después de `currentSnapshotId`, y copiarlo de `current.getRunId()` en `StatsSnapshotService.toWindow` (`<val>/StatsSnapshotService.java`). En `<tval>/StatsSnapshotServiceTest.java`, agregar una aserción de que cada ventana trae el `runId` del snapshot que la cierra y ajustar los usos existentes del constructor si los hay. Reflejar `currentRunId` en la sección `StatsWindow` de `specs/014-stats-snapshots/data-model.md`. No cambia ninguna tabla.
- [X] T002 Crear `MomentumFixtures` (final, constructor privado) en `<tval>/momentum/MomentumFixtures.java` con builders deterministas: `window(minutes, goals, assists, shots, keyPasses, dribbles, tackles, rating, yellow, red)` que arme `StatsWindow` con ids de snapshot e `Instant` crecientes por posición en la lista (lista ascendente = de la más vieja a la más reciente), `seasonReset = false` y un `currentRunId` distinto por ventana, más variantes `withSeasonReset(window)` y `withRunId(window, runId)`; `momentumQuote(value, calculatedAt, runId)` con el `runId` explícito para los casos de datos nuevos; `season(minutes, goals, assists, shots, keyPasses, dribbles, tackles, rating)` que arme un `PlayerStats` con el builder existente (sólo lectura del archivo de Entrega 1, sin modificarlo); `quote(code, value, calculatedAt, runId)` con `PlayerQuote.builder()`; `input(positions, stats, quotes, windows)` que arme `ValuationInput` con `population = null`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Redefinir MOMENTUM v1 con sus nuevos parámetros y crear el esqueleto del algoritmo. Todas las historias dependen de esto.

**⚠️ CRITICAL**: Ninguna historia comienza hasta completar esta fase.

### Tests (escribir primero, deben fallar)

- [X] T003 [P] Actualizar `<tval>/strategy/ValuationStrategyDefinitionTest.java`:
  - reemplazar `new MomentumParameters(4, 2)` por `new MomentumParameters(5, 2, List.of(5, 4, 3, 2, 1 como BigDecimal), 90, new BigDecimal("1.5"))`;
  - agregar casos inválidos que lancen `IllegalArgumentException`:
    - `window < 1`;
    - `minHistory < 2` o `minHistory > window + 1`;
    - `recencyWeights` nulo, de tamaño distinto de `window`, con nulos, con un peso `<= 0` o no estrictamente decreciente;
    - `possibleMinutes <= 0`;
    - `ratingBand` nulo o `<= 0`;
  - agregar a la lista de cambios de huella `momentum.recencyWeights`, `momentum.possibleMinutes` y `momentum.ratingBand`, además de los existentes `momentum.window` y `momentum.minHistory`, que hoy usan `(5, 2)` y `(4, 3)` y deben ajustarse a la firma nueva;
  - verificar que la huella de una definición OFFICIAL (`momentum == null`) no cambia respecto del formato anterior.
- [X] T004 [P] Actualizar `<tval>/strategy/MomentumStrategyV1Test.java#parameters`:
  - `maxVariation` debe ser `0.15` y `scaleFactor` `0.15`;
  - `momentum` debe ser igual a `new MomentumParameters(5, 2, [5,4,3,2,1], 90, 1.5)`;
  - los tests de pesos por posición se mantienen sin cambios (la tabla de 013 §4 no cambia).

### Implementation

- [X] T005 Ampliar `MomentumParameters` en `<val>/strategy/MomentumParameters.java` a `record MomentumParameters(int window, int minHistory, List<BigDecimal> recencyWeights, int possibleMinutes, BigDecimal ratingBand)`:
  - Validación con mensaje que incluya todos los valores:
    - `window >= 1`;
    - `2 <= minHistory <= window + 1`;
    - `recencyWeights` no nulo, de tamaño `window`, sin nulos, todos `> 0` y estrictamente decrecientes;
    - `possibleMinutes > 0`;
    - `ratingBand` no nulo y `> 0`.
  - Guardar `recencyWeights = List.copyOf(...)`.
  - Javadoc: `window` = ventanas de 014; `minHistory` = snapshots mínimos (con menos, el algoritmo devuelve `INSUFFICIENT_HISTORY`); `recencyWeights` van de la ventana más reciente a la más vieja y se normalizan sobre las ventanas presentes; `possibleMinutes` = minutos posibles por ventana; `ratingBand` = diferencia de rating que lleva la métrica a 0 o 1.
- [X] T006 Cambiar `fingerprint()` en `<val>/strategy/ValuationStrategyDefinition.java`:
  - el tramo de momentum pasa a ser `window + "," + minHistory + ",[" + pesos normalizados con normalize() unidos por "," + "]," + possibleMinutes + "," + normalize(ratingBand)`;
  - con `momentum == null` sigue siendo `"-"`, así que la huella de OFFICIAL no cambia.
  - Depende de T005.
- [X] T007 Redefinir `DEFINITION` en `<val>/strategy/MomentumStrategyV1.java`:
  - `maxVariation` `new BigDecimal("0.15")`, con `scaleFactor` 0.15 y `baseValue` 1.00;
  - `new MomentumParameters(5, 2, List.of(new BigDecimal("5"), new BigDecimal("4"), new BigDecimal("3"), new BigDecimal("2"), new BigDecimal("1")), 90, new BigDecimal("1.5"))`;
  - pesos por posición sin cambios;
  - reescribir el Javadoc: forma reciente sobre las últimas 5 ventanas de 014 (mínimo 2 snapshots), contra el promedio de temporada del jugador, con disponibilidad y tarjetas, ±15% por corrida. Agregar un párrafo "Redefinición": v1 se redefinió en la feature 019 sin subir de versión porque nunca produjo una cotización (no tenía algoritmo y 016 la salteaba); desde la primera cotización MOMENTUM rige la regla de 013 §5.
  - Depende de T005.
- [X] T008 Correr `PublishedStrategyVersionsTest`, copiar la huella "actual" del mensaje de falla de `MOMENTUM v1` y reemplazar el valor de `"MOMENTUM:1"` en `PUBLISHED` de `<tval>/strategy/PublishedStrategyVersionsTest.java`. Agregar arriba de la entrada un comentario de una línea con el motivo (redefinida en 019; nunca produjo cotización). `"OFFICIAL:1"` no se toca. Depende de T006 y T007.
- [X] T009 [P] Actualizar `specs/013-cotizacion-modelo/contracts/valuation-strategies.md`:
  - **§2**: fila MOMENTUM con `maxVariation` 0.15 (±15% por corrida) y `momentum` = `window = 5` (ventanas), `minHistory = 2` (snapshots), `recencyWeights = [5,4,3,2,1]`, `possibleMinutes = 90`, `ratingBand = 1.5`.
  - **§4**: reemplazar el primer párrafo por la descripción del algoritmo de 019, con link a `specs/019-estrategia-momentum/contracts/momentum-algorithm.md`.
  - **§5**: agregar al final una nota "Excepción única (feature 019)" con el motivo y la aclaración de que, desde la primera cotización MOMENTUM, la regla vuelve a regir sin excepciones.
  - Corregir de paso el punto 4 duplicado de §5.
- [X] T010 Crear el esqueleto de `MomentumFormAlgorithm` en `<val>/momentum/MomentumFormAlgorithm.java`:
  - **Declaración**: **sin** `@Component` (se agrega en T021, cuando `calculate` está completo, para que 016 no lo descubra antes), `implements ValuationAlgorithm`, sin estado ni dependencias, `MC = MathContext.DECIMAL64`, `SCALE = 4`.
  - **Métodos**: `type()` devuelve `StrategyType.MOMENTUM`; no sobrescribe `preparePopulation`.
  - **`validate(input, strategy)`**:
    - lanza `IllegalArgumentException` si `strategy.type() != MOMENTUM`, nombrando código y tipo;
    - lanza si alguna cotización previa tiene `strategyCode` distinto de `strategy.code()`, nombrando al jugador y el código;
    - lanza si `previousQuotes.get(0).getValue() == null`, nombrando al jugador.
  - **Helpers privados**:
    - `selectWindows(input, k)`: ordena por `toCapturedAt` desc y `currentSnapshotId` desc; si alguna tiene `seasonReset = true`, descarta las más viejas que la ventana de reinicio más reciente (la de reinicio se conserva); y toma las primeras `k`;
    - `averagedWeights(positions, strategy)`: `Map<Metric, BigDecimal>` con el promedio por métrica entre posiciones, dividido con `MC`.
  - **Javadoc**: resume la fórmula de `contracts/momentum-algorithm.md` §4, como el de `OfficialPerformanceAlgorithm`.
  - **`calculate`**: por ahora lanza `UnsupportedOperationException`; se completa en US1.
  - Depende de T007.

**Checkpoint**: `./mvnw.cmd "-Dtest=MomentumStrategyV1Test,ValuationStrategyDefinitionTest,PublishedStrategyVersionsTest,OfficialStrategyV1Test" test` en verde; la huella de OFFICIAL no cambió.

---

## Phase 3: User Story 1 - La cotización sigue la forma reciente (Priority: P1) 🎯 MVP

**Goal**: MOMENTUM calcula la forma de un jugador comparando sus últimas ventanas contra su promedio de temporada, con pesos de la posición y de recencia, y mueve la cotización anterior hasta ±15%.

**Independent Test**: Con jugadores con 5 ventanas de 90' sin tarjetas, el que rinde por encima de su promedio sube, el que rinde por debajo baja, el que rinde igual queda en 0%, y ninguno supera ±15%. Los ejemplos §5.1, §5.2, §5.5 y §5.8 del contrato se reproducen a 4 decimales.

### Tests for User Story 1 (escribir primero, deben fallar)

- [X] T011 [P] [US1] Crear `<tval>/momentum/MomentumFormAlgorithmTest.java` con `MomentumFixtures`, un `@Nested` "Forma reciente" y estos casos:
  - Escenarios de la US1 de la spec:
    - (1) dos delanteros con el mismo promedio, uno con más goles y tiros por 90' en cada ventana: variación mayor, y ambas en [−15%, +15%];
    - (2) rinde exactamente su promedio en todo: `score` 0.5000, valor igual al anterior;
    - (3) alto en las 2 recientes y bajo en las 3 viejas, contra el mismo set en orden inverso: el primero mayor;
    - (4) duplica o más cada contador y supera el rating por ≥1.5: variación exactamente +15%;
    - (5) con 7 ventanas, cambiar las 2 más viejas no cambia el resultado;
    - (6) un defensor que mejora quites contra un delantero que mejora goles con iguales cocientes: se aplican los pesos de cada posición, y los quites mueven más al defensor.
  - Normalización:
    - un contador con ratio 1.5 da n = 0.75;
    - un contador en 0 da n = 0;
    - un rating igual al de temporada da 0.5, +1.5 da 1 y −1.5 da 0;
    - una métrica `null` en la ventana se excluye y su peso se reparte (no baja el precio);
    - una métrica con promedio de temporada 0 o `null` se excluye;
    - `stats == null` da R = 0.5.
  - Ventana corta: una de 10' con muchos goles pesa menos que una de 90' (ponderación `ρ·a`).
  - Reinicio de temporada: con 3 ventanas malas, luego una con `seasonReset = true` y una más, sólo cuentan las 2 últimas; cambiar las 3 anteriores al reinicio no altera el resultado. Ejemplo §5.11 del contrato (1.0000 / 1.1500).
  - Ejemplos del contrato: §5.1 (score 0.6097, valor 1.2395 desde 1.2000), §5.2 (0.5000 / 1.0000), §5.5 (1.0000 / 1.1500) y §5.8 (dos posiciones: 0.6201 / 1.2433), comparados con `isEqualByComparingTo` a escala 4.

### Implementation for User Story 1

- [X] T012 [US1] Implementar en `<val>/momentum/MomentumFormAlgorithm.java` el rendimiento por ventana `windowPerformance(window, stats, weights, params)`:
  - Si `minutes` es nulo o `<= 0`, la ventana no aporta.
  - Contadores GOALS, ASSISTS, SHOTS, KEY_PASSES, DRIBBLES y TACKLES con `w(m) > 0`:
    - hay dato si `valorVentana != null`, `stats != null`, `stats.minutesPlayed > 0` y `valorTemporada > 0`;
    - `ratio = (valorVentana × minutosTemporada) / (minutosVentana × valorTemporada)` y `n = min(ratio / 2, 1)`.
  - RATING con `w > 0`:
    - hay dato si `ratingVentana != null` y `ratingTemporada > 0`;
    - `n = clamp(0.5 + (ratingVentana − ratingTemporada) / (2 × ratingBand), 0, 1)`.
  - `rⱼ = Σ w·n / Σ w` sobre las métricas con dato; 0.5 si no hay ninguna.
  - Todo con `MC`. Leer `PlayerStats` sólo con getters, sin modificarlo.
  - Depende de T010.
- [X] T013 [US1] Completar `calculate` en `<val>/momentum/MomentumFormAlgorithm.java`:
  - `validate`; `k = min(window, size)`; `ρⱼ = recencyWeights.get(j)`;
  - `aⱼ = min(minutos / possibleMinutes, 1)`, con minutos nulos = 0;
  - `R = Σ ρⱼ·aⱼ·rⱼ / Σ ρⱼ·aⱼ` sobre ventanas jugadas, o 0.5 si no hay;
  - por ahora `S = clamp(R, 0, 1)`; los términos de disponibilidad (US3) y tarjetas (US2) se agregan después;
  - `v = clamp(2·(S − 0.5)·scaleFactor, −maxVariation, +maxVariation)`;
  - `anterior = previousQuotes.isEmpty() ? baseValue : previousQuotes.get(0).getValue()`;
  - `valor = (anterior × (1 + v)).setScale(4, HALF_UP)`, `score = S.setScale(4, HALF_UP)`;
  - devolver `QuoteResult.calculated(valor, score, Map.of())`.
  - Depende de T012.

**Checkpoint**: `./mvnw.cmd "-Dtest=MomentumFormAlgorithmTest" test`: los casos de US1 en verde. Los ejemplos §5.1 y §5.8 recién pasan tras US2, porque incluyen una amarilla: marcarlos `@Disabled("US2")` hasta T017 o escribirlos en T015.

---

## Phase 4: User Story 2 - Las tarjetas penalizan (Priority: P1)

**Goal**: Amarillas y rojas de las ventanas restan forma con los pesos negativos de la posición, más cuanto más recientes, y la roja más que la amarilla.

**Independent Test**: Dos jugadores idénticos salvo tarjetas: el amonestado tiene menos variación, el expulsado menos que el amonestado, una tarjeta reciente resta más que una vieja, y muchas tarjetas nunca bajan de −15%.

### Tests for User Story 2

- [X] T014 [P] [US2] Agregar a `<tval>/momentum/MomentumFormAlgorithmTest.java` un `@Nested` "Tarjetas" con:
  - Escenarios de la US2:
    - (1) amarilla en la última ventana contra ninguna: estrictamente menor;
    - (2) roja contra amarilla en la misma ventana: estrictamente menor;
    - (3) amarilla en la más reciente contra amarilla en la más vieja de 5: la reciente resta más;
    - (4) 5 rojas en cada ventana: el valor es exactamente `anterior × 0.85`.
  - Tarjetas `null` cuentan 0.
  - Una tarjeta en una ventana sin minutos igual penaliza.
- [X] T015 [P] [US2] Agregar el ejemplo §5.7 (delantero en el tope con roja reciente: score 0.8500, valor 1.1050 desde 1.00) y habilitar §5.1 y §5.8 en `<tval>/momentum/MomentumFormAlgorithmTest.java`.

### Implementation for User Story 2

- [X] T016 [US2] Agregar en `<val>/momentum/MomentumFormAlgorithm.java` la penalización por tarjetas:
  - `C = Σⱼ (ρⱼ / ρ₀) × (|w(YELLOW_CARDS)| × amarillasⱼ + |w(RED_CARDS)| × rojasⱼ)` sobre las `k` ventanas, con tarjetas nulas = 0 y `ρ₀ = recencyWeights.get(0)`;
  - restar `C` dentro del clamp de `S`.
  - Depende de T013.
- [X] T017 [US2] Correr `MomentumFormAlgorithmTest` y verificar en verde los casos de US1 y US2, incluidos §5.1, §5.7 y §5.8.

**Checkpoint**: forma y tarjetas funcionando de forma independiente de la disponibilidad.

---

## Phase 5: User Story 3 - La falta de disponibilidad baja la cotización (Priority: P1)

**Goal**: Menos minutos sobre los posibles restan forma, en proporción al peso de minutos de la posición; sin minutos la cotización baja.

**Independent Test**: Con igual rendimiento por 90', 45' por ventana da menos variación que 90'. Sin minutos en ninguna ventana, `CALCULATED` con caída de −3% (mediocampista) o −9% (arquero). Jugar las 4 viejas y no la reciente da menos que lo inverso.

### Tests for User Story 3

- [X] T018 [P] [US3] Agregar a `<tval>/momentum/MomentumFormAlgorithmTest.java` un `@Nested` "Disponibilidad" con:
  - Escenarios de la US3:
    - (1) 90' contra 45' por ventana con el mismo rendimiento por 90': estrictamente menor;
    - (2) sin minutos en ninguna ventana: estado `CALCULATED` y valor menor al anterior;
    - (3) jugó las 4 más viejas y no la reciente, contra no jugó la más vieja y sí las 4 recientes: el primero menor.
  - Una ventana con 180' tiene disponibilidad acotada a 1.
  - Minutos `null` cuentan 0.
  - Ejemplos del contrato §5.3 (mediocampista sin minutos: 0.4000 / 0.9700), §5.4 (arquero sin minutos desde 1.1000: 0.2000 / 1.0010) y §5.6 (delantero en el tope con 45': 0.9500 / 1.1350).

### Implementation for User Story 3

- [X] T019 [US3] Agregar en `<val>/momentum/MomentumFormAlgorithm.java` la disponibilidad:
  - `D = Σ ρⱼ·aⱼ / Σ ρⱼ` sobre las `k` ventanas;
  - `S = clamp(R − w(MINUTES)·(1 − D) − C, 0, 1)`, con `w(MINUTES)` promediado entre posiciones.
  - Depende de T016.

**Checkpoint**: `./mvnw.cmd "-Dtest=MomentumFormAlgorithmTest" test` con US1–US3 en verde.

---

## Phase 6: User Story 4 - Historial insuficiente y punto de partida (Priority: P1)

**Goal**: Con menos de 2 snapshots, `INSUFFICIENT_HISTORY` sin valor; sin posiciones, `NO_STATS`; el primer cálculo parte de 1.00 y siempre se usa la anterior de MOMENTUM.

**Independent Test**: Jugadores con 0, 1 y 2 snapshots, con y sin cotización MOMENTUM previa, devuelven el estado y el valor esperados.

### Tests for User Story 4

- [X] T020 [P] [US4] Agregar a `<tval>/momentum/MomentumFormAlgorithmTest.java` un `@Nested` "Historial y validación" con:
  - Escenarios de la US4:
    - (1) 0 ventanas (0 o 1 snapshot): `INSUFFICIENT_HISTORY` con `value` y `score` nulos;
    - (2) 1 ventana sin anterior: `CALCULATED` con valor `round4(1.00 × (1 + v))`;
    - (3) anterior MOMENTUM 1.20: se aplica sobre 1.20 (la entrada sólo trae cotizaciones MOMENTUM, como garantiza 016);
    - (4) anterior `INSUFFICIENT_HISTORY` con valor 1.0000: se aplica sobre 1.0000;
    - (5) se cubre en T023;
    - (6) ventana más reciente con `currentRunId` igual al `runId` de la cotización anterior: `CALCULATED`, valor igual al anterior y score de la forma calculada. Ejemplo §5.10 del contrato (0.6097 / 1.2000).
  - Cotización previa de una corrida distinta a la del snapshot más reciente pero con `calculatedAt` posterior a su `toCapturedAt`: no aplica variación (valor igual al anterior).
  - Con `runId` distinto y `toCapturedAt` posterior al `calculatedAt` de la cotización previa se aplica la variación; sin cotización previa siempre se aplica (hay datos nuevos).
  - Sin posiciones: `NO_STATS`.
  - Validación:
    - cotización previa con código `OFFICIAL`: `IllegalArgumentException` que contiene el id del jugador y `OFFICIAL`;
    - cotización más reciente con `value == null`: `IllegalArgumentException` con el id;
    - definición `OfficialStrategyV1.DEFINITION`: `IllegalArgumentException`.

### Implementation for User Story 4

- [X] T021 [US4] En `calculate` de `<val>/momentum/MomentumFormAlgorithm.java`, después de `validate` y en este orden:
  - `positions` vacío → `QuoteResult.noStats()`;
  - `windows.size() < minHistory − 1` → `QuoteResult.insufficientHistory()`.
  - Verificar que la cotización anterior sea `previousQuotes.get(0)`, de cualquier estado, o `baseValue`.
  - Datos nuevos (FR-028): `datosNuevos = previousQuotes.isEmpty() || (!ventanaMásReciente.currentRunId().equals(anterior.getRunId()) && ventanaMásReciente.toCapturedAt().isAfter(anterior.getCalculatedAt()))`, con `anterior = previousQuotes.get(0)` y `ventanaMásReciente` la primera del orden de `selectWindows` antes de recortar. Si no hay datos nuevos, `v = 0` y el valor es el anterior redondeado a 4; el score se calcula igual. No agregar estados a `QuoteStatus`.
  - Agregar `@Component` a la clase: `calculate` ya está completo.
  - Depende de T019.

**Checkpoint**: el algoritmo completo cumple el contrato §2–§5 de forma aislada.

---

## Phase 7: User Story 5 - La corrida de recálculo incluye MOMENTUM (Priority: P1)

**Goal**: 016 calcula MOMENTUM junto con OFFICIAL, le pasa las ventanas de cada jugador y deja de saltearla; OFFICIAL no cambia.

**Independent Test**: El recálculo con ambos algoritmos registrados no lista MOMENTUM en `skipped`, guarda una cotización por estrategia y jugador, llama a `recentWindows(id, 6)` sólo para MOMENTUM y deja OFFICIAL idéntico.

### Tests for User Story 5

- [X] T022 [P] [US5] Agregar a `<tval>/QuoteCalculationServiceTest.java` dos grupos de casos:
  - **Con `MomentumFormAlgorithm` real y `OfficialPerformanceAlgorithm`**:
    - (1) `result.skipped()` vacío y cotizaciones guardadas para `OFFICIAL` y `MOMENTUM` por jugador;
    - (3) con `recentWindows` vacío (incluido un jugador sin estadísticas, que no tiene snapshots), MOMENTUM queda `INSUFFICIENT_HISTORY` con la anterior o `1.0000`, mientras OFFICIAL deja a ese jugador en `NO_STATS`;
    - (4) las cotizaciones OFFICIAL son iguales con y sin `MomentumFormAlgorithm` registrado;
    - (5) si `recentWindows` lanza para un jugador, ese jugador cuenta como fallido y la corrida sigue;
    - (6) con la última cotización MOMENTUM de un `runId` igual al `currentRunId` de la ventana más reciente devuelta por el stub, el valor guardado es igual al anterior y el estado es `CALCULATED`.
  - **Con un algoritmo mock MOMENTUM**:
    - (2) `snapshotService.recentWindows(playerId, 6)` se invoca una vez por jugador y nunca para OFFICIAL; un `ArgumentCaptor` sobre `ValuationInput` comprueba que MOMENTUM recibe las ventanas devueltas por el stub y que OFFICIAL recibe `windows` vacío.
  - Mantener los tests existentes que construyen el servicio sólo con OFFICIAL: ahí MOMENTUM sigue salteada.
- [X] T023 [P] [US5] Actualizar `backend/src/test/java/com/example/demo/e2e/QuoteRecalculationE2ETest.java#manualRunStoresQuotesMarketValueAndSnapshots`:
  - `skippedStrategies` vacío;
  - 10 cotizaciones de la corrida: 5 `OFFICIAL` y 5 `MOMENTUM`, todas con el `correlation_id`;
  - las MOMENTUM con `status = INSUFFICIENT_HISTORY` y `quote_value = 1.0000`, porque es la primera corrida y hay un solo snapshot;
  - las aserciones de valor de mercado se mantienen sobre OFFICIAL.
  - Agregar un test que corra dos veces, modificando por `jdbcTemplate` o el repositorio las estadísticas de un jugador entre corridas (también cambia `fetched_at` para que haya snapshot nuevo), y verifique que la segunda corrida deja MOMENTUM `CALCULATED` para ese jugador con `|valor − 1| <= 0.15`.
  - Agregar un test de corridas seguidas sin sincronización: después de una corrida con un jugador `CALCULATED` (preparada como el test anterior), correr tres veces más sin cambiar estadísticas; ninguna captura snapshot nuevo para ese jugador y sus tres cotizaciones MOMENTUM nuevas son `CALCULATED` con el mismo `quote_value` que la anterior.

### Implementation for User Story 5

- [X] T024 [US5] En `quote(...)` de `<val>/QuoteCalculationService.java`:
  - construir `List<StatsWindow> windows = definition.type() == StrategyType.MOMENTUM ? snapshotService.recentWindows(player.getId(), definition.momentum().window() + 1) : List.of();` y pasarlo como último argumento de `new ValuationInput(...)`, en lugar de `List.of()`;
  - no cambiar `previousQuotes`, la persistencia, el valor de mercado ni el resumen;
  - actualizar el Javadoc de la clase para mencionar que MOMENTUM recibe las ventanas de 014.
- [X] T025 [P] [US5] Actualizar el Javadoc de `ValuationInput` en `<val>/ValuationInput.java`: MOMENTUM (feature 019) usa `windows`, `stats` como promedio de temporada y la cotización previa. Sólo comentarios.

**Checkpoint**: `./mvnw.cmd "-Dtest=QuoteCalculationServiceTest" test` en verde; el E2E en verde con PostgreSQL y Redis.

---

## Phase 8: User Story 6 - Resultado determinístico y auditable (Priority: P2)

**Goal**: Mismas entradas, mismo resultado; los ejemplos del contrato se reproducen; cualquier cambio de parámetro sin actualizar la huella se detecta.

**Independent Test**: Calcular dos veces y con ventanas en otro orden da igual. Entradas generadas nunca superan ±15%. La huella protege la definición.

### Tests for User Story 6

- [X] T026 [P] [US6] Agregar a `<tval>/momentum/MomentumFormAlgorithmTest.java` un `@Nested` "Determinismo":
  - (1) mismo input calculado dos veces da `QuoteResult` igual;
  - (2) las mismas ventanas mezcladas (`Collections.shuffle` con `new Random(42)`) dan el mismo resultado;
  - (3) agregar ejemplo §5.9 (0 ventanas: `INSUFFICIENT_HISTORY`) para tener los 11 ejemplos del contrato cubiertos;
  - (4) pureza: el algoritmo se construye con `new MomentumFormAlgorithm()` sin argumentos y calcula sin ningún colaborador.
- [X] T027 [P] [US6] Crear `<tval>/momentum/MomentumFormAlgorithmPropertyTest.java` con un `Random` de semilla fija:
  - 500 casos con 1–7 ventanas;
  - minutos 0–180 o `null`, contadores 0–5 o `null`, rating 4–10 o `null`, tarjetas 0–2;
  - posiciones aleatorias no vacías, temporada aleatoria o `null`;
  - anterior entre 0.5 y 3.0 o ausente;
  - verificar `score ∈ [0, 1]`, `|valor − round4(anterior)| <= anterior × 0.15 + 0.00005` y que dos cálculos coinciden.
  - Seguir el estilo de `OfficialMinMinutesPropertyTest`.
- [X] T028 [P] [US6] Agregar a `<tval>/strategy/PublishedStrategyVersionsTest.java` un test que cambie `MomentumStrategyV1.DEFINITION.momentum()` (por ejemplo `ratingBand` 2.0) manteniendo la versión 1 y verifique que `check(...)` devuelve el mensaje "MOMENTUM v1 ya está publicada".

**Checkpoint**: suite del algoritmo completa en verde.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T029 [P] Actualizar en `backend/src/main/java/com/example/demo/dto/quote/QuoteRunResponse.java` y `specs/016-recalculo-cotizaciones/contracts/quotes-api.yaml` los ejemplos que muestran MOMENTUM salteada, para que el ejemplo de la corrida no liste estrategias salteadas. El schema no cambia y `QuoteOpenApiTest` debe seguir en verde.
- [X] T030 [P] Revisar `specs/019-estrategia-momentum/quickstart.md` y agregar al final, debajo de un título "Resultados", los comandos ejecutados y su resultado.
- [X] T031 Ejecutar `./mvnw.cmd test` en `backend/`. Si una suite falla por el cambio de MOMENTUM, corregir la expectativa sólo si refleja el comportamiento especificado. Confirmar sin issues nuevos de Sonar (sin duplicación con `OfficialPerformanceAlgorithm`, sin métodos con complejidad cognitiva > 15: dividir `calculate` en helpers si hace falta).
- [X] T032 Verificar con `git diff --stat` que no se modificó ningún archivo protegido de Entrega 1, ni `OfficialStrategyV1` ni `OfficialPerformanceAlgorithm`, y que no se agregaron `.yml` ni propiedades.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias.
- **Foundational (Phase 2)**: depende de Setup. T005 → T006 y T007 → T008; T007 → T010; T001 (Setup) va antes de T002. Bloquea todas las historias.
- **US1 (Phase 3)**: depende de Foundational. Es el MVP del algoritmo.
- **US2 (Phase 4)**: depende de US1 (T013), porque extiende la fórmula de `S`.
- **US3 (Phase 5)**: depende de US2 (T016), porque comparte la línea de `S`.
- **US4 (Phase 6)**: depende de US3 (T019), porque toca el mismo método. Sus tests (T020) pueden escribirse en paralelo desde Foundational.
- **US5 (Phase 7)**: T024 depende de T021, porque recién ahí `MomentumFormAlgorithm` se registra como `@Component` y 016 lo descubre. Sus tests de cotizaciones `CALCULATED` requieren US1–US4 completas.
- **US6 (Phase 8)**: depende de US1–US4.
- **Polish (Phase 9)**: al final.

### Within Each User Story

Primero los tests (deben fallar), después la implementación y por último el checkpoint. US1–US4 tocan el mismo archivo de implementación, así que se hacen en secuencia.

### Parallel Opportunities

- T003, T004 y T009 en paralelo (archivos distintos).
- T011, T014, T018 y T020 se pueden escribir en paralelo, porque son `@Nested` distintos del mismo test. Si se evita el conflicto de edición, conviene escribirlos en secuencia.
- T022, T023 y T025 en paralelo entre sí, y con US2–US4 si se acepta que algunos casos fallen hasta completar el algoritmo.
- T026, T027 y T028 en paralelo.
- T029 y T030 en paralelo.

## Parallel Example: Foundational

```text
Task: "T003 Actualizar ValuationStrategyDefinitionTest con la firma nueva de MomentumParameters"
Task: "T004 Actualizar MomentumStrategyV1Test#parameters"
Task: "T009 Actualizar el contrato de 013 (§2, §4, nota en §5)"
```

## Parallel Example: User Story 5

```text
Task: "T022 Tests de QuoteCalculationService con MOMENTUM registrado"
Task: "T023 Actualizar QuoteRecalculationE2ETest"
Task: "T025 Javadoc de ValuationInput"
```

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 y Phase 2: definición redefinida, huella y esqueleto.
2. Phase 3 (US1): el algoritmo ya mueve precios según la forma.
3. Validar con `MomentumFormAlgorithmTest` antes de seguir.

### Incremental Delivery

1. US2 (tarjetas) y US3 (disponibilidad) completan los factores de la consigna.
2. US4 cierra los estados (`INSUFFICIENT_HISTORY` y `NO_STATS`).
3. US5 conecta la corrida de 016: desde acá MOMENTUM produce cotizaciones reales.
4. US6 y Polish: determinismo, propiedades, ejemplos de API y suite completa.

### Notes

- US1–US4 entregan un algoritmo correcto pero sin usar hasta US5. No hacer merge de US5 sin US1–US4: MOMENTUM empezaría a guardar cotizaciones con una fórmula incompleta, y desde la primera cotización la regla de 013 §5 impide corregir v1.
- No ejecutar commits: los hace el equipo.
