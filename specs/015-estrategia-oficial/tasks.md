---

description: "Implementation tasks for the OFFICIAL valuation algorithm"
---

# Tasks: Algoritmo de la Estrategia 1 "OFFICIAL"

**Input**: Design documents from `/specs/015-estrategia-oficial/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/valuation-strategies.md`, `quickstart.md`

**Tests**: El plan pide explícitamente tests unitarios puros (casos de la consigna con números calculados a mano, bordes y permutaciones) y un test de la propiedad con `ApplicationContextRunner`. Los tipos de la Phase 2 se crean primero para que la suite compile. Dentro de cada historia, los tests se escriben antes que la implementación y tienen que fallar primero. Los números esperados salen de `contracts/valuation-strategies.md` §5.

**Archivos protegidos**: no se modifican `backend/src/main/java/com/example/demo/model/**`, `backend/src/main/java/com/example/demo/repository/**`, `backend/src/main/resources/application*.properties`, `OfficialStrategyV1.java`, `ValuationStrategyDefinition.java` ni `PublishedStrategyVersionsTest.java`. Todo lo de esta feature es código nuevo.

**Rutas abreviadas**:
- `MAIN` = `backend/src/main/java/com/example/demo/service/valuation`
- `TEST` = `backend/src/test/java/com/example/demo/service/valuation`

**Organization**: Las tareas se agrupan por historia de usuario de la spec. US1 es el MVP y las demás extienden `OfficialPerformanceAlgorithm` y sus tests, así que se implementan en orden aunque cada una se pueda verificar por separado.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo porque toca archivos distintos y no depende de una tarea incompleta de la misma fase.
- **[Story]**: Historia de usuario que cubre (`US1` … `US6`).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirmar la línea base antes de agregar código.

- [X] T001 Ejecutar `./mvnw.cmd test "-DexcludedGroups=e2e"` desde `backend/` y confirmar que todo pasa, incluido `backend/src/test/java/com/example/demo/service/valuation/strategy/PublishedStrategyVersionsTest.java`. Esa es la línea base que no puede romperse.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Crear el contrato común de algoritmos y los tipos de la población, que bloquean todas las historias.

**⚠️ CRITICAL**: No comenzar las historias hasta completar esta fase.

### Tests

- [X] T002 [P] Crear `TEST/QuoteResultTest.java`. Tiene que verificar:
  - `calculated(value, score, excluded)` exige `value` y `score` no null;
  - `noStats()` e `insufficientHistory()` tienen `value` y `score` null y `excludedMetrics` vacío;
  - el constructor canónico rechaza `CALCULATED` sin valor o sin score, y rechaza `NO_STATS` con valor o con score;
  - `status` null se rechaza;
  - `excludedMetrics` es inmutable (modificar el mapa o un `Set` interno lanza `UnsupportedOperationException`) y una copia defensiva (cambiar el mapa original no altera el resultado).
- [X] T003 [P] Crear `TEST/ValuationInputTest.java`. Tiene que verificar:
  - `playerId` null se rechaza;
  - `positions` null se rechaza y un `Set` vacío se acepta;
  - `previousQuotes` y `windows` null se tratan como listas vacías;
  - las listas y el `Set` se copian de forma inmutable;
  - `previousQuotes` en orden **ascendente** por `calculatedAt` lanza `IllegalArgumentException`;
  - dos cotizaciones con el mismo `calculatedAt` se aceptan (empate);
  - `stats` y `population` null se aceptan, porque los valida el algoritmo.
  
  Las cotizaciones se arman con `PlayerQuote.builder()`.

### Implementation

- [X] T004 [P] Crear la interfaz `MAIN/ValuationAlgorithm.java` con `StrategyType type()` y `QuoteResult calculate(ValuationInput input, ValuationStrategyDefinition strategy)`. El Javadoc dice que 016 elige la implementación por `definition.type()`, que MOMENTUM (019) implementa la misma interfaz y que un `strategy.type()` distinto de `type()` lanza `IllegalArgumentException`.
- [X] T005 [P] Crear el record `MAIN/QuoteResult.java` `(BigDecimal value, BigDecimal score, QuoteStatus status, Map<Position, Set<Metric>> excludedMetrics)`.
  - Constructor compacto:
    - `status` es obligatorio;
    - si es `CALCULATED`, `value` y `score` son obligatorios; en los demás estados los dos son null;
    - copia `excludedMetrics` a un `EnumMap` con `Set`s inmutables y lo envuelve con `Collections.unmodifiableMap`. Si es null queda vacío.
  - Fábricas `calculated(value, score, excludedMetrics)`, `noStats()` e `insufficientHistory()`.
  
  Debe pasar T002.
- [X] T006 [P] Crear el record `MAIN/ValuationInput.java` `(Long playerId, Set<Position> positions, PlayerStats stats, NormalizedPopulation population, List<PlayerQuote> previousQuotes, List<StatsWindow> windows)`.
  - Constructor compacto:
    - `playerId` y `positions` son obligatorios;
    - `positions` se copia con `Set.copyOf`;
    - `previousQuotes` y `windows` null pasan a `List.of()`, y si no lo son se copian con `List.copyOf`;
    - rechaza con `IllegalArgumentException` una `previousQuotes` en la que algún `calculatedAt` sea posterior al del elemento anterior. El mensaje dice "de la más nueva a la más vieja".
  - Javadoc: `previousQuotes` trae sólo cotizaciones de la estrategia que se calcula, y `calculate` valida el código.
  - `NormalizedPopulation` está en el mismo paquete `service/valuation` (T008), así que `ValuationInput` no importa nada de `service/valuation/official`.
  
  Compila junto con T008 y debe pasar T003.
- [X] T007 [P] Crear el record `MAIN/PopulationEntry.java` `(Long playerId, Set<Position> positions, PlayerStats stats)`. `playerId` es obligatorio, `positions` null pasa a `Set.of()` y si no se copia con `Set.copyOf`, y `stats` puede ser null.
- [X] T008 Crear la clase final inmutable `MAIN/NormalizedPopulation.java` en el paquete `com.example.demo.service.valuation`, el mismo de `MetricNormalizer` (T012).
  - Constructor **package-private**, nunca `public`: sólo `MetricNormalizer` puede crear poblaciones, y ninguna clase de `official` ni de 016 puede armarlas a mano. Recibe:
    - `int minMinutes`;
    - `Map<Long, Map<Position, Map<Metric, BigDecimal>>> normalized`;
    - `Map<Position, Set<Metric>> metricsWithoutData`.
  - Copia profunda e inmutable de los mapas: `EnumMap` por posición y métrica, envuelto con `unmodifiable`.
  - Métodos:
    - `minMinutes()`;
    - `contains(Long playerId)`;
    - `normalized(Long playerId, Position position)`: devuelve `Map.of()` si el jugador o la posición no están;
    - `metricsWithoutData(Position position)`: devuelve `Set.of()` si la posición no está.
  - Una población sin jugadores es válida: con mapas vacíos, `contains` da false y `metricsWithoutData` está vacío para las 4 posiciones.
  
  Depende de T007 sólo por el paquete.
- [X] T009 [P] Crear el helper de tests `TEST/official/OfficialFixtures.java` como clase `public final` con constructor privado.
  - Métodos `public static` (también los usa `TEST/MetricNormalizerTest.java`, desde el paquete padre):
    - `stats(Integer minutes, Integer goals, Integer assists, Integer shots, Integer keyPasses, Integer dribbles, Integer tackles, String rating)` arma un `PlayerStats` con setters y acepta null en cualquier campo;
    - `entry(long id, PlayerStats stats, Position... positions)`;
    - `quote(String strategyCode, String value, Instant calculatedAt)` con `PlayerQuote.builder()` y estado `CALCULATED`;
    - `quote(String strategyCode, String value, QuoteStatus status, Instant calculatedAt)`.
  - Constantes con las poblaciones de `contracts/valuation-strategies.md` §5: A, B, C, X (MIDFIELDER y FORWARD), G1, G2, D1, F1 y F2, con los mismos minutos y totales de las tablas del contrato.

**Checkpoint**: compilan la interfaz, los records y el helper. T002 y T003 pasan.

---

## Phase 3: User Story 1 - Calcular score y valor según la fórmula de la consigna (Priority: P1) 🎯 MVP

**Goal**: Normalizar la población y calcular score y valor de jugadores con una sola posición, con los pesos de OFFICIAL v1.

**Independent Test**: `./mvnw.cmd "-Dtest=MetricNormalizerTest,OfficialPerformanceAlgorithmTest" test`. Los valores de §5.1 a §5.4 del contrato coinciden.

### Tests for User Story 1

- [X] T010 [P] [US1] Crear `TEST/MetricNormalizerTest.java` con `new MetricNormalizer(90)`. Tiene que verificar:
  - 5 goles en 450 minutos dan 1.0 por 90 antes de normalizar. Se comprueba con dos jugadores, donde el otro tiene 0 goles, y el normalizado es 1.
  - En la población A, B y C de MIDFIELDER, C da 0.5 en las 6 métricas de conteo, A da 1 y B da 0.
  - Con un solo jugador en la posición, o todos con el mismo valor, cada métrica de conteo vale 0.5.
  - Un null individual cuenta 0 y participa del min/max, y no aparece en `metricsWithoutData`.
  - El rating 7.0 da 0.70; un rating 11 da 1 y uno −1 da 0.
  - Un jugador MIDFIELDER + FORWARD aparece en `normalized(id, MIDFIELDER)` y en `normalized(id, FORWARD)` con valores distintos según cada población.
  - Un `playerId` duplicado lanza `IllegalArgumentException` con el id en el mensaje.
  - Población vacía: `normalize(List.of())` no lanza excepción y devuelve una `NormalizedPopulation` con `minMinutes() == 90`, `contains(anyId) == false` y `metricsWithoutData(p)` vacío para las 4 posiciones.
  - `minMinutes()` devuelve 90.
- [X] T011 [P] [US1] Crear `TEST/official/OfficialPerformanceAlgorithmTest.java` con `new OfficialPerformanceAlgorithm(90)` y `OfficialStrategyV1.DEFINITION`, y un helper privado que normaliza y calcula a cada jugador. Tiene que verificar:
  - `type()` es `OFFICIAL`;
  - MIDFIELDER: A da score 0.9600 y valor 1.0960, B da 0.1200 y 1.0120, C da 0.5400 y 1.0540, todos `CALCULATED` y con `excludedMetrics` vacío;
  - GOALKEEPER: G1 da 0.8040 y 1.0804, G2 da 0.4620 y 1.0462;
  - DEFENDER solo: D1 da 0.5600 y 1.0560;
  - FORWARD: F1 da 0.8560 y 1.0856, F2 da 0.2360 y 1.0236;
  - score y valor tienen `scale() == 4`;
  - una definición MOMENTUM (`MomentumStrategyV1.DEFINITION`) lanza `IllegalArgumentException`;
  - `population` null lanza `IllegalArgumentException`;
  - una población armada con `new OfficialPerformanceAlgorithm(270).normalize(...)` y calculada con el de 90 lanza `IllegalArgumentException`.
  
  Los valores se comparan con `isEqualByComparingTo` y la escala con `scale()`.

### Implementation for User Story 1

- [X] T012 [US1] Crear la clase final `MAIN/MetricNormalizer.java`.
  - Constructor `MetricNormalizer(int minMinutes)` y método `NormalizedPopulation normalize(Collection<PopulationEntry> entries)`:
    1. Rechaza ids duplicados.
    2. Elige como elegibles a los que tienen `stats != null`, `!positions.isEmpty()` y `minutesPlayed != null && minutesPlayed >= minMinutes`.
    3. Calcula los valores crudos por 90 de `GOALS`, `ASSISTS`, `SHOTS`, `KEY_PASSES`, `DRIBBLES` y `TACKLES` como `(valor ?? 0) × 90 / minutos`, con `MathContext.DECIMAL64`.
    4. Calcula `RATING` como `clamp((rating ?? 0) / 10, 0, 1)`.
    5. Por cada `Position`, sobre los elegibles que la tienen, calcula min y max por métrica de conteo y normaliza con `(x − min) / (max − min)`, o 0.5 si `max == min` (compara con `compareTo`).
    6. Por posición, registra en `metricsWithoutData` las métricas (las 6 de conteo y `RATING`) que son null para todos sus elegibles.
  - Sin estado mutable compartido.
  - Javadoc que remita a `specs/015-estrategia-oficial/contracts/valuation-strategies.md` §1.
  
  Debe pasar T010.
- [X] T013 [US1] Crear `MAIN/official/OfficialPerformanceAlgorithm.java` como `@Component` que implementa `ValuationAlgorithm`.
  - Constructor `OfficialPerformanceAlgorithm(@Value("${valuation.official.min-minutes:90}") int minMinutes)`.
  - `type()` devuelve `OFFICIAL`.
  - `normalize(Collection<PopulationEntry>)` delega en `new MetricNormalizer(minMinutes)`.
  - `calculate`:
    1. Valida que `strategy.type() == OFFICIAL`, que `input.population() != null` y que `population.minMinutes() == minMinutes`. Cualquier falla lanza `IllegalArgumentException`.
    2. Para la única posición del jugador, `score_p = Σ strategy.weight(p, m) × normalized(m)` sobre `strategy.metrics()`, recorridas en orden de enum.
    3. `score = score_p.setScale(4, HALF_UP)`, acotado a [0,1].
    4. `value = (baseValue + score × scaleFactor).setScale(4, HALF_UP)`.
    5. Devuelve `QuoteResult.calculated(value, score, Map.of())`.
  - Javadoc que remita al contrato.
  
  Debe pasar T011.

**Checkpoint**: la fórmula de la consigna y las 4 posiciones dan los números del contrato.

---

## Phase 4: User Story 2 - Excluir jugadores sin estadísticas suficientes (Priority: P1)

**Goal**: Marcar como NO_STATS, sin score ni valor, a los jugadores sin stats, sin posiciones o con menos minutos que el umbral de la población, sin que afecten la normalización de los demás.

**Independent Test**: `./mvnw.cmd "-Dtest=MetricNormalizerTest,OfficialPerformanceAlgorithmTest,OfficialMinMinutesPropertyTest" test`.

### Tests for User Story 2

- [X] T014 [P] [US2] Agregar a `TEST/MetricNormalizerTest.java` los siguientes casos:
  - con umbral 90, no son elegibles (`contains == false`) los jugadores con stats null, positions vacío, `minutesPlayed` null, 0 u 89;
  - con 90 minutos el jugador es elegible;
  - agregar a la población A, B y C un jugador con 89 minutos y 10 goles no cambia ningún valor normalizado de A, B y C.
- [X] T015 [P] [US2] Agregar a `TEST/official/OfficialPerformanceAlgorithmTest.java` los siguientes casos:
  - devuelven `NO_STATS`, con `value()` y `score()` null y `excludedMetrics` vacío, los jugadores con stats null, sin posiciones, con 0 minutos, con minutos null y con 89 minutos (umbral 90);
  - un jugador de 89 minutos con stats en el input da `NO_STATS` contra una población de umbral 90: el corte sale de `population.minMinutes()`;
  - un jugador con 90 minutos da `CALCULATED`;
  - población vacía: `calculate` de A (stats válidas, MIDFIELDER, 900 minutos) contra `algorithm.normalize(List.of())` da `NO_STATS` con valor y score null, sin lanzar excepción;
  - un jugador `NO_STATS` con cotización anterior sigue sin valor;
  - agregar jugadores `NO_STATS` con estadísticas extremas a la población de §5.1 no cambia los resultados de A, B y C.
- [X] T016 [P] [US2] Crear `TEST/official/OfficialMinMinutesPropertyTest.java` con `new ApplicationContextRunner().withUserConfiguration(OfficialPerformanceAlgorithm.class)`, siguiendo el estilo de `ValuationStrategyActivePropertyTest`. Tiene que verificar:
  - sin propiedad, la población que arma `normalize(...)` tiene `minMinutes() == 90`;
  - con `valuation.official.min-minutes=270`, el umbral es 270 y un jugador de 200 minutos da `NO_STATS`;
  - con `0` y con `-5`, `context` falla al arrancar (`hasFailed()`) y el mensaje menciona `min-minutes`.

### Implementation for User Story 2

- [X] T017 [US2] En `MAIN/official/OfficialPerformanceAlgorithm.java`:
  - en el constructor, si `minMinutes <= 0` lanza `IllegalArgumentException("valuation.official.min-minutes debe ser > 0: " + minMinutes)`;
  - en `calculate`, después de todas las validaciones de entrada (tipo, población y, desde T024, `previousQuotes`), devuelve `QuoteResult.noStats()` si `stats == null`, si `positions` está vacío, si `minutesPlayed` es null o menor que `input.population().minMinutes()` (nunca el campo del `@Value`), o si `!population.contains(playerId)`.
  
  Deben pasar T014–T016.

**Checkpoint**: los jugadores sin datos suficientes quedan NO_STATS y no alteran a los demás. El umbral es configurable y tiene una sola fuente dentro del cálculo.

---

## Phase 5: User Story 3 - Repartir el peso de métricas sin datos en toda la posición (Priority: P1)

**Goal**: Excluir las métricas con peso > 0 que no tienen dato en toda una posición, repartir su peso proporcionalmente e informarlas.

**Independent Test**: `./mvnw.cmd "-Dtest=MetricNormalizerTest,OfficialPerformanceAlgorithmTest" test`. El escenario sin gambetas da 1.0956, 1.0133 y 1.0544 con `{MIDFIELDER=[DRIBBLES]}`.

### Tests for User Story 3

- [X] T018 [P] [US3] Agregar a `TEST/MetricNormalizerTest.java` los siguientes casos:
  - con DRIBBLES null para A, B y C (MIDFIELDER) y presente en F1 y F2 (FORWARD), `metricsWithoutData(MIDFIELDER)` es `{DRIBBLES}` y `metricsWithoutData(FORWARD)` está vacío;
  - si al menos un jugador de la posición tiene el dato, la métrica no aparece;
  - si el rating es null para todos los jugadores de una posición, `RATING` aparece.
- [X] T019 [P] [US3] Agregar a `TEST/official/OfficialPerformanceAlgorithmTest.java` los siguientes casos:
  - MIDFIELDER sin gambetas: A da score 0.9556 y valor 1.0956, B da 0.1333 y 1.0133, C da 0.5444 y 1.0544, y en los tres `excludedMetrics == {MIDFIELDER=[DRIBBLES]}`;
  - en G1 y G2 (DRIBBLES null pero con peso 0 en GOALKEEPER), `excludedMetrics` está vacío y los valores siguen siendo 1.0804 y 1.0462;
  - si un solo mediocampista tiene gambetas, no se excluye nada;
  - si todos los jugadores de la posición tienen null en las 7 métricas (con minutos válidos), el jugador de una sola posición da `NO_STATS`.

### Implementation for User Story 3

- [X] T020 [US3] En `MAIN/official/OfficialPerformanceAlgorithm.java`, por posición:
  1. `excluded = { m ∈ population.metricsWithoutData(p) : strategy.weight(p, m).signum() > 0 }`.
  2. `remaining = Σ peso de las métricas no excluidas`.
  3. Si `remaining.signum() == 0`, la posición no produce score.
  4. Si no, `score_p = Σ_{m ∉ excluded} (peso / remaining) × normalized(m)`, con DECIMAL64.
  
  Las exclusiones se guardan en un `EnumMap<Position, Set<Metric>>` sólo para las posiciones donde no están vacías, y se pasan a `QuoteResult.calculated`. Si la posición del jugador no produce score, devuelve `noStats()`.
  
  Deben pasar T018 y T019.

**Checkpoint**: la falla de gambetas en una liga no comprime los scores y el resultado explica qué se excluyó.

---

## Phase 6: User Story 4 - Valuar jugadores con varias posiciones (Priority: P2)

**Goal**: Promediar los scores de cada posición del jugador, cada uno con su normalización y sus pesos.

**Independent Test**: `./mvnw.cmd "-Dtest=OfficialPerformanceAlgorithmTest" test`. X da score 0.4738 y valor 1.0474.

### Tests for User Story 4

- [X] T021 [US4] Agregar a `TEST/official/OfficialPerformanceAlgorithmTest.java` los siguientes casos:
  - con la población A, B, C, F1, F2 y X (X es MIDFIELDER + FORWARD), X da score 0.4738 y valor 1.0474;
  - A, B y C mantienen 1.0960, 1.0120 y 1.0540, y F1 y F2 mantienen 1.0856 y 1.0236;
  - un jugador MIDFIELDER + FORWARD cuya posición MIDFIELDER no produce score (todas las métricas null entre los mediocampistas) recibe sólo el score de FORWARD y `excludedMetrics` lista las exclusiones de MIDFIELDER.

### Implementation for User Story 4

- [X] T022 [US4] En `MAIN/official/OfficialPerformanceAlgorithm.java`, recorrer `input.positions()` en orden de `Position.values()` (no en el orden del `Set`) y juntar los `score_p` de las posiciones que producen uno. El score es el promedio, `suma.divide(cantidad, DECIMAL64)`, y después `setScale(4, HALF_UP)` y clamp [0,1]. Si ninguna posición produce score, devuelve `noStats()`. Debe pasar T021.

**Checkpoint**: los jugadores multiposición se valúan con las reglas de cada una de sus posiciones.

---

## Phase 7: User Story 5 - Acotar la variación respecto de la cotización anterior y redondear (Priority: P2)

**Goal**: Aplicar el tope de ±`maxVariation` contra la cotización vigente, redondear HALF_UP y validar `previousQuotes`.

**Independent Test**: `./mvnw.cmd "-Dtest=OfficialPerformanceAlgorithmTest,ValuationInputTest" test`. Los resultados son 0.9600, 1.2000, 1.0540 y 1.0565.

### Tests for User Story 5

- [X] T023 [US5] Agregar a `TEST/official/OfficialPerformanceAlgorithmTest.java` los siguientes casos (las cotizaciones se arman con `OfficialFixtures.quote`):
  - A con anterior OFFICIAL 0.80 da **0.9600**, con score 0.9600 sin cambios;
  - B con anterior 1.50 da **1.2000**;
  - C con anterior 1.00 da **1.0540** (dentro del rango);
  - sin cotización anterior no hay tope;
  - el tope usa `previousQuotes.get(0)` aunque su estado sea `NO_STATS`: con anteriores [0.80 NO_STATS más nueva, 1.00 CALCULATED más vieja], A da 0.9600;
  - empate HALF_UP: un único DEFENDER con rating 7.15 y el resto de las métricas informadas da score **0.5645** y valor **1.0565**, nunca 1.0564;
  - una cotización previa con `strategyCode` "MOMENTUM" lanza `IllegalArgumentException`;
  - una cotización más reciente con `value` null lanza `IllegalArgumentException` cuyo mensaje contiene el `playerId`;
  - **la validación de entrada corre antes de NO_STATS**:
    - un jugador que sería `NO_STATS` (stats null, MIDFIELDER) con una cotización anterior OFFICIAL de `value` null lanza `IllegalArgumentException` cuyo mensaje contiene el `playerId`, en lugar de devolver `noStats()`;
    - el mismo jugador con una cotización anterior de `strategyCode` "MOMENTUM" también lanza `IllegalArgumentException`.

### Implementation for User Story 5

- [X] T024 [US5] En `MAIN/official/OfficialPerformanceAlgorithm.java`:
  1. En las validaciones iniciales de `calculate`, junto a las de tipo y población y **antes** del chequeo de NO_STATS de T017 (que no depende del jugador), extraer un helper privado `validatePreviousQuotes(input, strategy)` que:
     - rechace con `IllegalArgumentException` cualquier `previousQuotes` cuyo `getStrategyCode()` no sea `strategy.code()`;
     - si la lista no está vacía y `previousQuotes.get(0).getValue()` es null, lance `IllegalArgumentException("La cotización anterior del jugador " + playerId + " no tiene valor; 016 debe guardar NO_STATS con el precio conservado")`.
     
     El orden descendente ya lo valida el constructor de `ValuationInput` (T006), así que también es anterior al NO_STATS.
  2. Después de calcular el score, `raw = baseValue + score × scaleFactor`.
  3. Si `previousQuotes` no está vacía, `prev = previousQuotes.get(0).getValue()` (ya validado no null) y `raw = min(max(raw, prev × (1 − maxVariation)), prev × (1 + maxVariation))`.
  4. `value = raw.setScale(4, HALF_UP)`.
  
  Debe pasar T023.

**Checkpoint**: ningún valor varía más de ±20% respecto del vigente, y los errores de integración de 016 fallan con un mensaje claro.

---

## Phase 8: User Story 6 - Resultado determinístico (Priority: P2)

**Goal**: Garantizar resultados idénticos sin importar el orden de la población ni la cantidad de ejecuciones.

**Independent Test**: `./mvnw.cmd "-Dtest=OfficialPerformanceAlgorithmTest" test`. Las 10 permutaciones dan resultados iguales.

### Tests for User Story 6

- [X] T025 [US6] Agregar a `TEST/official/OfficialPerformanceAlgorithmTest.java` un test con la población completa del contrato, incluido X y un jugador sin gambetas en otra posición, que genera 10 permutaciones con `Collections.shuffle(copy, new Random(seed))` para `seed` de 0 a 9. Tiene que verificar:
  - en cada permutación, cada jugador obtiene el mismo `QuoteResult` (`equals`, que incluye escala, estado y exclusiones);
  - pasar las posiciones de X como `LinkedHashSet` en orden FORWARD, MIDFIELDER y en orden MIDFIELDER, FORWARD da el mismo resultado;
  - calcular dos veces con las mismas entradas da resultados `equals`.

### Implementation for User Story 6

- [X] T026 [US6] Revisar `MAIN/MetricNormalizer.java` y `MAIN/official/OfficialPerformanceAlgorithm.java`. Ninguna suma o promedio puede recorrer un `HashSet` o un `HashMap` en un orden que afecte el resultado: siempre `EnumMap` u orden de `values()`. Min y max no dependen del orden. Si T025 falla, corregir el recorrido. Debe pasar T025.

**Checkpoint**: el cálculo es reproducible, como exige la trazabilidad de §1.2.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Validación final, alcance y calidad.

- [X] T027 [P] Revisar el Javadoc de las 7 clases nuevas de `MAIN/` y `MAIN/official/`, con la densidad de comentarios de `ValuationStrategyDefinition` y `StatsSnapshotService`. Tiene que explicar la fórmula, que el umbral sale de la población y la regla de `previousQuotes`, remitiendo a `specs/015-estrategia-oficial/contracts/valuation-strategies.md` sin duplicarlo.
- [X] T028 [P] Revisar posibles issues de Sonar en las clases nuevas: imports sin usar, constantes mágicas (`90`, `10`, `0.5` como constantes con nombre), métodos largos en `calculate` (extraer helpers privados por paso) y comparaciones de `BigDecimal` con `equals` en lugar de `compareTo`. Archivos: `MAIN/MetricNormalizer.java` y `MAIN/official/OfficialPerformanceAlgorithm.java`.
- [X] T029 Ejecutar `./mvnw.cmd test "-DexcludedGroups=e2e"` desde `backend/` y confirmar que todo pasa, incluido `PublishedStrategyVersionsTest` sin cambios.
- [X] T030 Recorrer las secciones 1–5 de `specs/015-estrategia-oficial/quickstart.md`. En particular, `git diff --stat main -- backend/src/main` sólo tiene que mostrar archivos nuevos: 6 bajo `service/valuation/` (`ValuationAlgorithm`, `ValuationInput`, `QuoteResult`, `PopulationEntry`, `NormalizedPopulation` y `MetricNormalizer`) y 1 bajo `service/valuation/official/` (`OfficialPerformanceAlgorithm`).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias.
- **Foundational (Phase 2)**: depende de T001. Bloquea todas las historias.
- **US1 (Phase 3)**: depende de la Phase 2. Es el MVP.
- **US2–US6 (Phases 4–8)**: dependen de US1, porque extienden `OfficialPerformanceAlgorithm` y sus tests. Conviene hacerlas en orden de fase, ya que todas editan los mismos dos archivos de producción.
- **Polish (Phase 9)**: depende de todas las historias.

### User Story Dependency Graph

```text
Phase 2 ──► US1 ──► US2 ──► US3 ──► US4 ──► US5 ──► US6 ──► Polish
```

US3 depende de US2 sólo porque una posición sin score termina en `noStats()`. US5 y US6 no dependen funcionalmente de US3 ni de US4, pero comparten archivo.

### Within Each User Story

- Los tests se escriben primero y tienen que fallar.
- `MetricNormalizer` antes que `OfficialPerformanceAlgorithm` cuando la historia toca los dos.
- La historia termina cuando pasa su Independent Test.

### Parallel Opportunities

- Phase 2: T002, T003, T004, T005, T006, T007 y T009 tocan archivos distintos. T008 va después de T007. T006 se escribe en paralelo, pero compila recién cuando existe `NormalizedPopulation` (T008).
- US1: T010 y T011 en paralelo.
- US2: T014, T015 y T016 en paralelo.
- US3: T018 y T019 en paralelo.
- Polish: T027 y T028 en paralelo.

---

## Parallel Example: Foundational

```text
Task: "Crear TEST/QuoteResultTest.java"                       (T002)
Task: "Crear TEST/ValuationInputTest.java"                    (T003)
Task: "Crear MAIN/ValuationAlgorithm.java"                    (T004)
Task: "Crear MAIN/QuoteResult.java"                           (T005)
Task: "Crear MAIN/ValuationInput.java"                        (T006)
Task: "Crear MAIN/PopulationEntry.java"              (T007)
Task: "Crear TEST/official/OfficialFixtures.java"             (T009)
```

## Parallel Example: User Story 2

```text
Task: "NO_STATS en MetricNormalizerTest"                      (T014)
Task: "NO_STATS en OfficialPerformanceAlgorithmTest"          (T015)
Task: "Crear OfficialMinMinutesPropertyTest"                  (T016)
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 y Phase 2.
2. Phase 3 (US1): la fórmula de la consigna y las 4 posiciones con los números del contrato.
3. **Validar**: correr el Independent Test de US1.

### Incremental Delivery

1. US2 (NO_STATS y umbral): con esto ya se puede valuar una población real sin divisiones por cero ni distorsiones.
2. US3 (reparto de pesos): tolera la falla de gambetas.
3. US4 (multiposición), US5 (tope, redondeo y validación de `previousQuotes`) y US6 (determinismo).
4. Polish.

### Suggested Commit Boundaries

Los commits los hace el equipo. Cortes sugeridos:

1. Phase 2 (contrato común y tipos).
2. US1.
3. US2 + US3.
4. US4 + US5 + US6.
5. Polish.

---

## Notes

- Los números esperados de cada test salen de `contracts/valuation-strategies.md` §5 y se verificaron con aritmética decimal.
- Si alguna tarea parece requerir un cambio en un archivo protegido, se detiene el trabajo y se registra `[NEEDS CLARIFICATION]` con el archivo, el cambio mínimo y una alternativa.
- `MOMENTUM` (019) y la persistencia de `PlayerQuote` (016) quedan fuera de alcance.
