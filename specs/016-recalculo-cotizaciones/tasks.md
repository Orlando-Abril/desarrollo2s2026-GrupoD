---

description: "Tareas de implementación de la feature 016: recálculo de cotizaciones"
---

# Tasks: Recálculo de cotizaciones

**Input**: Documentos de diseño en `specs/016-recalculo-cotizaciones/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/quotes-api.yaml](contracts/quotes-api.yaml), [quickstart.md](quickstart.md)

**Tests**: se incluyen porque el plan los pide explícitamente: unitarios del servicio, MockMvc del controller y de la seguridad, y un E2E con PostgreSQL.

**Organization**: las tareas se agrupan por historia de usuario de la spec (US1–US6).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: se puede hacer en paralelo (otro archivo, sin depender de tareas incompletas).
- **[Story]**: historia de usuario de la spec (US1–US6).
- Rutas relativas a la raíz del repo. Producción: `backend/src/main/java/com/example/demo/`. Tests: `backend/src/test/java/com/example/demo/`.

**Regla de archivos protegidos (Entrega 1)**: sólo se permiten los cambios de la tabla "Cambios en archivos protegidos" de [plan.md](plan.md):

- `Player.marketValue` con precision 12 / scale 4 y su javadoc;
- `JwtAuthFilter` con el rol;
- `ApiKeyAuthFilter` con el rol del dueño (`ROLE_ADMIN` si la key no tiene dueño);
- `SecurityConfig` con `hasRole("ADMIN")` y `accessDeniedHandler`.

`GlobalExceptionHandler`, `PlayerController` y `PlayerRepository` **no se tocan**. Cualquier otro cambio en un archivo protegido requiere detenerse y consultar.

**Commits**: los hace el equipo. No ejecutar `git commit` ni `git push`.

---

## Phase 1: Setup

**Purpose**: dejar la rama con el código de la feature 015 (research D1).

- [X] T001 Incorporar `origin/main` (merge `d4252c7`, feature 015) en la rama `feature/recalculo-cotizaciones` y verificar que existen `backend/src/main/java/com/example/demo/service/valuation/ValuationAlgorithm.java`, `ValuationInput.java`, `QuoteResult.java`, `PopulationEntry.java`, `NormalizedPopulation.java`, `MetricNormalizer.java` y `service/valuation/official/OfficialPerformanceAlgorithm.java`. Luego correr `cd backend && ./mvnw test -DexcludedGroups=e2e` y anotar si el build arranca en verde. Si el merge tiene conflictos, detenerse y consultar al equipo.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: contrato de algoritmo, tipos de dominio y excepciones que usan todas las historias.

**⚠️ CRITICAL**: ninguna historia puede empezar antes de terminar esta fase.

- [X] T002 Agregar a `backend/src/main/java/com/example/demo/service/valuation/ValuationAlgorithm.java` el método `default NormalizedPopulation preparePopulation(Collection<PopulationEntry> entries) { return null; }`. Javadoc: "Población de la corrida, armada una vez por estrategia antes de calcular (feature 016); null si el algoritmo no la usa." (research D2)
- [X] T003 En `backend/src/main/java/com/example/demo/service/valuation/official/OfficialPerformanceAlgorithm.java`, agregar `@Override public NormalizedPopulation preparePopulation(Collection<PopulationEntry> entries) { return normalize(entries); }`. `normalize` no cambia (depende de T002).
- [X] T004 [P] Agregar un test en `backend/src/test/java/com/example/demo/service/valuation/official/OfficialPerformanceAlgorithmTest.java` que verifique que `preparePopulation(entries)` devuelve una población equivalente a `normalize(entries)`: mismo `minMinutes`, mismos `contains` y mismos normalizados para los jugadores del fixture. Agregar otro test con una implementación anónima mínima de `ValuationAlgorithm` que verifique que el default devuelve `null`.
- [X] T005 [P] Crear el record `backend/src/main/java/com/example/demo/service/valuation/StrategyRunCounts.java` con `(String code, int version, int calculated, int noStats, int insufficientHistory)`. El constructor compacto rechaza `code` null o vacío y cualquier conteo < 0 con `IllegalArgumentException`.
- [X] T006 [P] Crear el record `backend/src/main/java/com/example/demo/service/valuation/SkippedStrategy.java` con `(String code, int version, String reason)` y la constante `public static final String NO_ALGORITHM = "no_algorithm";`. Los campos no pueden ser null.
- [X] T007 Crear el record `backend/src/main/java/com/example/demo/service/valuation/QuoteRunResult.java` con `(UUID runId, Instant calculatedAt, String requestedStrategy, List<StrategyRunCounts> calculated, List<SkippedStrategy> skipped, int playersProcessed, int playersFailed, String activeStrategy, boolean marketValueUpdated)`. Reglas del data-model: `runId`, `calculatedAt` y `activeStrategy` no null; `requestedStrategy` "null si se pidieron todas"; listas copiadas con `List.copyOf`; `playersProcessed` y `playersFailed` ≥ 0 (depende de T005 y T006).
- [X] T008 [P] Crear `backend/src/main/java/com/example/demo/exception/RecalculationInProgressException.java` (`RuntimeException`, mensaje fijo "Ya hay un recálculo de cotizaciones en curso").
- [X] T009 [P] Crear `backend/src/main/java/com/example/demo/exception/RecalculationFailedException.java` (`RuntimeException`) con constructor `(UUID runId, Throwable cause)`, mensaje "No se pudo completar el recálculo" y getter `runId()`.

**Checkpoint**: compila y los tests existentes siguen en verde.

---

## Phase 3: User Story 1 - Recalcular las cotizaciones de todos los jugadores (Priority: P1) 🎯 MVP

**Goal**: una corrida con `runId` e instante únicos que toma un snapshot y registra una `PlayerQuote` por jugador y estrategia con algoritmo. Saltea MOMENTUM con `no_algorithm`, conserva el valor en `NO_STATS` / `INSUFFICIENT_HISTORY` y no consulta fuentes externas.

**Independent Test**: `QuoteCalculationServiceTest` con mocks y `Clock.fixed`: tras `recalculate(Optional.empty())`, verificar las cotizaciones guardadas (estado, valor, score, versión, `runId`, `calculatedAt`, `correlationId`) y el `QuoteRunResult`.

### Tests for User Story 1

> Escribirlos primero y comprobar que fallan antes de implementar T011.

- [X] T010 [US1] Crear `backend/src/test/java/com/example/demo/service/valuation/QuoteCalculationServiceTest.java`: JUnit 5 + Mockito + AssertJ, sin contexto de Spring.
  - **Setup**:
    - `Clock.fixed(Instant.parse("2026-10-23T18:00:00Z"), UTC)`.
    - `TransactionOperations` que ejecuta el callback directo (`TransactionOperations.withoutTransaction()`).
    - Un `PlatformTransactionManager` mock (para el `TransactionTemplate` read-only) o un constructor package-private que reciba la `TransactionOperations` de lectura.
    - Mocks de `StatsSnapshotService`, `PlayerRepository`, `PlayerStatsRepository` y `PlayerQuoteRepository`.
    - `ValuationStrategyCatalog` real con activa `OFFICIAL`.
    - Algoritmo OFFICIAL como mock de `ValuationAlgorithm` con `type()` = `OFFICIAL`.
    - `ArgumentCaptor` sobre `saveAll`.
  - **Casos**:
    - (a) corrida feliz con 3 jugadores CALCULATED: 3 cotizaciones OFFICIAL con `strategyVersion` 1, el mismo `runId`, `calculatedAt` igual al instante fijo y `correlationId` igual al valor puesto en el MDC; valor y score los del `QuoteResult`. `snapshotAll` se llama una vez con ese `runId` y **antes** de `findAll` (usar `InOrder`).
    - (b) `NO_STATS` con última cotización OFFICIAL 1.0450: valor 1.0450, `score` null, estado `NO_STATS`.
    - (c) `NO_STATS` sin previa: valor `1.0000` (escala 4).
    - (d) `INSUFFICIENT_HISTORY` con previa 1.0200: valor 1.0200, sin score.
    - (e) `findLatestForAllPlayers` se llama sólo con `"OFFICIAL"`. El `ValuationInput.previousQuotes()` capturado contiene sólo esa cotización OFFICIAL, aunque el mock de MOMENTUM tenga una más nueva.
    - (f) catálogo con MOMENTUM y sin algoritmo MOMENTUM: `skipped` = [`MOMENTUM`, 1, `no_algorithm`] y ninguna cotización MOMENTUM.
    - (g) `preparePopulation` se llama exactamente una vez por estrategia calculada, con un `PopulationEntry` por jugador (`stats` null para los que no tienen).
    - (h) `snapshotAll` lanza `IllegalStateException`: `recalculate` lanza `RecalculationFailedException`, nunca hay `saveAll` ni `findAll` de jugadores.
    - (h2) `findLatestForAllPlayers` lanza `IllegalStateException`: `recalculate` lanza `RecalculationFailedException`, nunca se llama a `saveAll` ni a `setMarketValue`.
    - (i) `recalculate(Optional.of("FOO"))` lanza `UnknownValuationStrategyException` sin llamar a `snapshotAll`.
    - (j) sin correlation id en el MDC: las cotizaciones llevan uno generado y no vacío, y el MDC queda sin la clave al terminar. Con correlation id previo, se usa y se conserva.
    - (k) catálogo de jugadores vacío: `playersProcessed` 0, `playersFailed` 0 y `calculated` con conteos 0.
    - (l) `QuoteCalculationService` no tiene en su constructor ningún parámetro de tipo `WhoScoredAdapter`, `FootballDataAdapter` ni `PlayerStatsService` (verificar por reflexión sobre `getDeclaredConstructors()`).
    - (m) `recalculate(Optional.of("OFFICIAL"))` deja `requestedStrategy` = "OFFICIAL" y `skipped` vacío.
    - (y) `recalculate(Optional.of(""))`, `Optional.of("official")` y `Optional.of(" official ")` lanzan `UnknownValuationStrategyException` sin llamar a `snapshotAll`.

### Implementation for User Story 1

- [X] T011 [US1] Crear `backend/src/main/java/com/example/demo/service/valuation/QuoteCalculationService.java` (`@Service`) según el plan, "Diseño › QuoteCalculationService", y el flujo de [data-model.md](data-model.md#flujo-de-la-corrida).
  - **Constructor**: `ValuationStrategyCatalog`, `List<ValuationAlgorithm>`, `StatsSnapshotService`, `PlayerRepository`, `PlayerStatsRepository`, `PlayerQuoteRepository`, `Clock`, `TransactionOperations` y `PlatformTransactionManager`.
    - Arma un `Map<StrategyType, ValuationAlgorithm>` con `EnumMap`. Si hay dos algoritmos con el mismo `type()`, lanza `IllegalStateException`.
    - Crea un `TransactionTemplate` read-only.
  - **`public QuoteRunResult recalculate(Optional<String> strategyCode)`**:
    1. `definitions = strategyCode.map(c -> List.of(catalog.find(c))).orElse(catalog.all())`.
    2. Dejar el try/finally exterior donde T020 va a tomar y liberar la guardia. En esta tarea no se agrega la guardia.
    3. Correlation id del MDC (`CorrelationIdFilter.MDC_KEY`); si falta o está vacío, lo genera y al final restaura el MDC, igual que `StatsSnapshotService.snapshotAll`.
    4. `runId = UUID.randomUUID()` y `calculatedAt = clock.instant()`.
    5. `snapshotAll(runId)` y la carga del paso 6 van dentro de un try. Cualquier `RuntimeException` que salga de ellos, o del armado del resultado después del bucle de jugadores, se envuelve en `RecalculationFailedException(runId, e)`. Las fallas dentro de la transacción de un jugador no se envuelven: las maneja T021.
    6. Carga read-only: jugadores (`playerRepository.findAll()`) y `Map<Long, PlayerStats>` por `stats.getPlayer().getId()`. Por cada definición con algoritmo: `population = algorithm.preparePopulation(entries)` y `latest = findLatestForAllPlayers(code)` indexado por `quote.getPlayer().getId()`. Las definiciones sin algoritmo van a `SkippedStrategy(code, version, NO_ALGORITHM)`.
    7. Por jugador, una `transactionOperations.executeWithoutResult` con `playerRepository.findById(id).orElseThrow()`. Por definición calculable: `calculate(new ValuationInput(id, positions, stats, population, latest != null ? List.of(latest) : List.of(), List.of()), def)` → `toQuote`. Después `quoteRepository.saveAll(quotes)`.
    8. Conteos por estado: se suman sólo después de que la transacción vuelve sin excepción.
    9. Arma el `QuoteRunResult`.
  - **`toQuote`**:
    - `CALCULATED`: `value` y `score` del resultado.
    - En otro caso: `value = latest != null ? latest.getValue() : def.baseValue().setScale(4, RoundingMode.HALF_UP)` y `score = null`.
    - Siempre: `strategyCode`, `strategyVersion`, `status`, `calculatedAt`, `runId` y `correlationId`.
  - Depende de T002, T007 y T009. T010 tiene que pasar.
- [X] T012 [US1] Agregar a `QuoteCalculationService.java` los logs de research D13, con SLF4J y el formato `evento clave=valor` de `StatsSnapshotService`:
  - `quote_recalculation_started runId={} requestedStrategy={}` (info);
  - `quote_recalculation_finished runId={} calculated={} skipped={} processed={} failed={} marketValueUpdated={} durationMs={}` (info, con los códigos y conteos por estrategia);
  - `quote_recalculation_failed runId={} errorType={}` (error, con la excepción) cuando la corrida termina con `RecalculationFailedException` u otra `RuntimeException` general.

**Checkpoint**: `QuoteCalculationServiceTest` (casos a–m) en verde. La corrida funciona invocando el servicio directo.

---

## Phase 4: User Story 2 - Precio de mercado según la estrategia activa (Priority: P1)

**Goal**: `Player.marketValue` con 4 decimales, igual a la cotización de la estrategia activa cuando se calculó. Si no se calculó, no cambia y el resumen lo indica.

**Independent Test**: con OFFICIAL activa, el `marketValue` de cada jugador procesado es igual (`compareTo == 0`, escala 4) a su cotización OFFICIAL de la corrida. Con MOMENTUM activa o pedida sola, `setMarketValue` no se llama y `marketValueUpdated` es false.

### Tests for User Story 2

- [X] T013 [US2] Agregar a `backend/src/test/java/com/example/demo/service/valuation/QuoteCalculationServiceTest.java` (usar `Player` reales, no mocks, para leer `getMarketValue()`):
  - (n) OFFICIAL activa con un `CALCULATED` de 1.0437: el `marketValue` del jugador queda `1.0437` (`isEqualByComparingTo` y `scale()` == 4). Con un `NO_STATS` con previa 1.0450 queda `1.0450`. `marketValueUpdated` true y `activeStrategy` "OFFICIAL".
  - (o) catálogo construido con activa `MOMENTUM` (constructor package-private `ValuationStrategyCatalog(List, String)` desde un test del mismo paquete, o `new ValuationStrategyCatalog("MOMENTUM")`): se calculan las cotizaciones OFFICIAL, el `marketValue` no cambia y `marketValueUpdated` es false.
  - (p) OFFICIAL activa y `recalculate(Optional.of("MOMENTUM"))`: `calculated` vacío, `marketValue` sin cambios y `marketValueUpdated` false.
  - (q) catálogo vacío con OFFICIAL activa calculada: `marketValueUpdated` true.

### Implementation for User Story 2

- [X] T014 [US2] En `backend/src/main/java/com/example/demo/model/Player.java` (archivo protegido, **cambio autorizado**), cambiar únicamente la anotación de `marketValue` a `@Column(name = "market_value", nullable = false, precision = 12, scale = 4)`. Actualizar el javadoc del campo: "Precio de mercado: cotización de la estrategia activa en la última corrida (feature 016). Escala 4, igual que PlayerQuote.value." No tocar otros campos ni anotaciones.
- [X] T015 [US2] En `backend/src/main/java/com/example/demo/service/valuation/QuoteCalculationService.java`:
  - dentro de la transacción de cada jugador, si `catalog.active()` está entre las definiciones calculadas, llamar a `managed.setMarketValue(valueDeLaCotizaciónActiva)` con el mismo `BigDecimal` de la `PlayerQuote`;
  - calcular `marketValueUpdated = activeCalculated && (playersProcessed > 0 || playersConsidered == 0)` y `activeStrategy = catalog.active().code()`.
  - Depende de T011 y T014. T013 tiene que pasar.
- [X] T016 [P] [US2] En `backend/src/main/java/com/example/demo/dto/player/PlayerResponse.java`, cambiar sólo el `@Schema(example = "1.00")` de `marketValue` por `example = "1.0000"`.
- [X] T017 [P] [US2] En `backend/src/test/java/com/example/demo/e2e/ExternalSourcesDownE2ETest.java` (línea ~314, `new BigDecimal("1.00")` en los campos esperados), cambiar a `new BigDecimal("1.0000")`: PostgreSQL devuelve la escala de la columna y `BigDecimal.equals` distingue escala (research D9). No cambiar nada más del test.
- [X] T018 [P] [US2] Crear `backend/src/test/java/com/example/demo/repository/PlayerMarketValueScaleTest.java` (`@DataJpaTest` o `@SpringBootTest` + `@UnitTestProfile`, como `PlayerRepositoryTest`):
  - guardar un `Player` con `marketValue` `1.0437`, hacer flush y clear, releer y comprobar `isEqualByComparingTo("1.0437")`;
  - guardar `35000000` y comprobar que se relee igual (entra en `numeric(12,4)`).

**Checkpoint**: US1 + US2 en verde. El precio de mercado sigue a la estrategia activa.

---

## Phase 5: User Story 5 - Una falla en un jugador no arruina la corrida (Priority: P2)

> Se implementa antes que US3 y US4 porque toca el mismo servicio que US1 y US2. Así el servicio queda cerrado antes de exponerlo por HTTP.

**Goal**: atomicidad por jugador (cotizaciones + `marketValue` juntas), aislamiento de fallas, conteo de fallidos y log de error por jugador. También la guardia de corrida única (FR-019 y FR-020), que la expone el 409 de US3.

**Independent Test**: provocar una falla en un jugador concreto y verificar que ese jugador no tiene cotizaciones ni precio nuevo, los demás sí, y el resumen dice 4 procesados y 1 fallido. Con dos llamadas concurrentes, la segunda recibe `RecalculationInProgressException`.

### Tests for User Story 5

- [X] T019 [US5] Agregar a `backend/src/test/java/com/example/demo/service/valuation/QuoteCalculationServiceTest.java`:
  - (r) 5 jugadores, `saveAll` lanza `RuntimeException` para el 3.º: los otros 4 se guardan con su `marketValue`, el 3.º conserva su `marketValue` original, `playersProcessed` 4, `playersFailed` 1, y los conteos por estado suman 4.
  - (s) dos estrategias calculables (un segundo algoritmo stub con `type()` `MOMENTUM` y el catálogo real) donde `calculate` de la segunda falla para un jugador: para ese jugador no se llama a `saveAll`, ni siquiera con la cotización OFFICIAL, y su `marketValue` no cambia.
  - (t) concurrencia: la primera llamada queda bloqueada dentro de `snapshotAll` (un `CountDownLatch` en el mock) en otro hilo. Una segunda llamada desde el hilo de test lanza `RecalculationInProgressException` sin llamar a `snapshotAll`. Al liberar el latch, la primera termina bien (cubre FR-036: la corrida se invoca sin HTTP con la misma guardia).
  - (u) después de una corrida que terminó con `RecalculationFailedException` (snapshot falla), una nueva llamada se acepta (la guardia se liberó). Lo mismo cuando la corrida falla después del snapshot (`findLatestForAllPlayers` lanza `IllegalStateException`): una nueva llamada se acepta.
  - (v) un código inexistente con una corrida en curso lanza `UnknownValuationStrategyException`, no `RecalculationInProgressException`.
  - (w) el log `quote_recalculation_player_failed` se emite con `runId`, `playerId` y `errorType`. Capturar con un `ListAppender` de Logback, como `StatsSnapshotLoggingTest`.
  - (x) 3 jugadores y `saveAll` falla para los 3: `recalculate` termina sin excepción, `playersProcessed` 0, `playersFailed` 3, conteos en 0, ningún `setMarketValue` y `marketValueUpdated` false.

### Implementation for User Story 5

- [X] T020 [US5] En `backend/src/main/java/com/example/demo/service/valuation/QuoteCalculationService.java`:
  - agregar `private final AtomicBoolean running = new AtomicBoolean(false);`;
  - después de resolver las definiciones y **antes** del snapshot: `if (!running.compareAndSet(false, true)) throw new RecalculationInProgressException();`;
  - liberar con `running.set(false)` en el `finally` exterior (research D6).
  - Depende de T008 y T011.
- [X] T021 [US5] En `QuoteCalculationService.java`, envolver el procesamiento de cada jugador en `try { transactionOperations.executeWithoutResult(...) } catch (RuntimeException e)`:
  - en el catch: `log.error("quote_recalculation_player_failed runId={} playerId={} strategy={} errorType={}", ..., e)` y `failed++`;
  - el `strategy` es el código que se estaba calculando, o `null` si falló al guardar;
  - los conteos de estado del jugador se acumulan en variables locales y se suman al total sólo si la transacción terminó sin excepción (FR-015 a FR-017).

**Checkpoint**: el servicio cumple FR-015 a FR-020. `QuoteCalculationServiceTest` completo en verde.

---

## Phase 6: User Story 3 - Disparar el recálculo a mano por API (Priority: P1)

**Goal**: `POST /quotes/recalculate` con cuerpo opcional, resumen 200 y errores 400 / 409 / 500 en el formato `ErrorResponse`.

**Independent Test**: MockMvc como ADMIN contra el servicio mockeado: códigos y forma del JSON del contrato [quotes-api.yaml](contracts/quotes-api.yaml).

### Tests for User Story 3

- [X] T022 [P] [US3] Crear `backend/src/test/java/com/example/demo/controller/QuoteControllerTest.java` (`@SpringBootTest` + `@AutoConfigureMockMvc` + `@UnitTestProfile`, `@MockitoBean QuoteCalculationService`, pedidos con el header `X-API-KEY` de una key creada en `@BeforeEach` con `ApiKeyRepository` y `ApiKeyHasher`, cuyo dueño es un usuario `Role.ADMIN` creado con `UserRepository`, como `JwtOrApiKeyAuthenticationIntegrationTest`):
  - 200 sin body → `verify(service).recalculate(Optional.empty())`;
  - 200 con `{}` y con `{"strategy":null}` → `Optional.empty()`;
  - 200 con `{"strategy":"OFFICIAL"}` → `Optional.of("OFFICIAL")`;
  - JSON de respuesta con `runId`, `calculatedAt`, `requestedStrategy`, `strategies[0].code/version/calculated/noStats/insufficientHistory`, `skippedStrategies[0].code/version/reason`, `playersProcessed`, `playersFailed`, `activeStrategy` y `marketValueUpdated`;
  - 400 `{"error":"unknown_strategy"}` cuando el mock lanza `UnknownValuationStrategyException`, y `{"strategy":""}` se pasa como `Optional.of("")`;
  - 400 `validation_error` con body `{"strategy":` (JSON inválido) sin llamar al servicio;
  - 409 `recalculation_in_progress`;
  - 500 `recalculation_failed` con mensaje "No se pudo completar el recálculo" y sin el mensaje de la causa.

### Implementation for User Story 3

- [X] T023 [P] [US3] Crear `backend/src/main/java/com/example/demo/dto/quote/RecalculateRequest.java`: record `(@Schema(description = "Código exacto de la estrategia. Ausente o null = todas.", example = "OFFICIAL", nullable = true) String strategy)`.
- [X] T024 [P] [US3] Crear `backend/src/main/java/com/example/demo/dto/quote/QuoteRunResponse.java`: record con `@Schema` en cada campo según `QuoteRunResponse`, `StrategyRunCounts` y `SkippedStrategy` de [contracts/quotes-api.yaml](contracts/quotes-api.yaml). Los records anidados `StrategyRunCountsResponse(code, version, calculated, noStats, insufficientHistory)` y `SkippedStrategyResponse(code, version, reason)` van dentro del mismo archivo. Incluye `static QuoteRunResponse from(QuoteRunResult result)`, que mapea `calculated` → `strategies` y `skipped` → `skippedStrategies` (depende de T007).
- [X] T025 [US3] Crear `backend/src/main/java/com/example/demo/controller/QuoteController.java` (`@RestController`, `@Tag(name = "Quotes")`) con `@PostMapping("/quotes/recalculate")`, que recibe `@RequestBody(required = false) RecalculateRequest request` y devuelve `QuoteRunResponse.from(service.recalculate(Optional.ofNullable(request).map(RecalculateRequest::strategy)))`. Sin lógica de negocio (§2.1). Las anotaciones OpenAPI van en T034 (depende de T011, T023 y T024).
- [X] T026 [US3] Crear `backend/src/main/java/com/example/demo/exception/QuoteExceptionHandler.java` con `@RestControllerAdvice(assignableTypes = QuoteController.class)` y `@Order(Ordered.HIGHEST_PRECEDENCE)`. Devuelve `GlobalExceptionHandler.ErrorResponse` (sin modificar `GlobalExceptionHandler`):
  - `UnknownValuationStrategyException` → 400 `unknown_strategy` con `ex.getMessage()`;
  - `HttpMessageNotReadableException` → 400 `validation_error` "El cuerpo del pedido es inválido";
  - `RecalculationInProgressException` → 409 `recalculation_in_progress` con `ex.getMessage()`;
  - `RecalculationFailedException` → 500 `recalculation_failed` "No se pudo completar el recálculo".
  - Depende de T008, T009 y T025. T022 tiene que pasar.

**Checkpoint**: el endpoint responde según el contrato para un cliente autenticado (todavía sin la restricción ADMIN).

---

## Phase 7: User Story 4 - Sólo administradores pueden recalcular (Priority: P1)

**Goal**: JWT con `ROLE_<rol>` desde `users`, API key con el rol de su dueño (`ROLE_ADMIN` si no tiene dueño), `hasRole("ADMIN")` en el endpoint y 403 JSON `forbidden`. Los demás endpoints no cambian.

**Independent Test**: MockMvc con la cadena de seguridad real: 401 sin credenciales, 403 con JWT o API key de USER, 200 con JWT o API key de ADMIN, y `GET /players` igual que antes.

### Tests for User Story 4

- [X] T027 [P] [US4] Crear `backend/src/test/java/com/example/demo/security/QuoteRecalculationSecurityTest.java` (`@SpringBootTest` + `@AutoConfigureMockMvc` + `@UnitTestProfile`, `@MockitoBean QuoteCalculationService` que devuelve un `QuoteRunResult` fijo, usuarios creados con `UserRepository` y tokens con `JwtUtil.generateToken`):
  - `POST /quotes/recalculate` sin credenciales → 401 con `{"error":"unauthorized"}`;
  - con token inválido → 401;
  - con API key inválida → 401;
  - JWT de un usuario `Role.USER` → 403 con body exacto `{"error":"forbidden","message":...}` (mismo shape que el 401) y `verifyNoInteractions(service)`;
  - JWT de `Role.ADMIN` → 200;
  - JWT de un username que no existe en `users` → 403;
  - API key de un ADMIN → 200;
  - API key de un USER → 403 `forbidden` y `verifyNoInteractions(service)`;
  - `GET /players` con JWT de USER, con JWT de un usuario inexistente y con la API key de USER → 200 en los tres casos (FR-030).
- [X] T028 [P] [US4] Actualizar `backend/src/test/java/com/example/demo/security/JwtAuthFilterTest.java` para el constructor nuevo `new JwtAuthFilter(jwtUtil, userRepository)` (mock de `UserRepository`). Agregar casos: usuario USER → authorities `[ROLE_USER]`; ADMIN → `[ROLE_ADMIN]`; inexistente → autenticado con authorities vacías. Los casos existentes no cambian de expectativa.
- [X] T029 [P] [US4] Agregar a `backend/src/test/java/com/example/demo/security/ApiKeyAuthFilterTest.java` casos que verifiquen las authorities de una API key válida: key de un USER → `[ROLE_USER]`, key de un ADMIN → `[ROLE_ADMIN]`, key sin dueño → `[ROLE_ADMIN]`. No cambiar los casos existentes.

### Implementation for User Story 4

- [X] T030 [US4] En `backend/src/main/java/com/example/demo/security/JwtAuthFilter.java` (protegido, **cambio autorizado**):
  - agregar `UserRepository` al constructor;
  - después de `extractUsername`, armar `List<GrantedAuthority> authorities = userRepository.findByUsername(username).map(u -> List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name()))).orElse(List.of())`;
  - pasar `authorities` al `UsernamePasswordAuthenticationToken`.
  - No cambiar `shouldNotFilter`, el 401 ni `JwtUtil` (research D10).
- [X] T031 [US4] En `backend/src/main/java/com/example/demo/security/ApiKeyAuthFilter.java` (protegido, **cambio autorizado**), reemplazar `List.of()` por `List.of(new SimpleGrantedAuthority("ROLE_" + (owner != null ? owner.getRole().name() : "ADMIN")))` en el `UsernamePasswordAuthenticationToken`, usando el mismo owner que ya se lee para el principal. Nada más.
- [X] T032 [P] [US4] Crear `backend/src/main/java/com/example/demo/security/JsonAccessDeniedHandler.java`, que implementa `AccessDeniedHandler`. Responde 403, `application/json`, UTF-8, con body `{"error":"forbidden","message":"No tenés permisos para esta operación"}`, serializado con `ObjectMapper` igual que `respondUnauthorized` de los filtros.
- [X] T033 [US4] En `backend/src/main/java/com/example/demo/config/SecurityConfig.java` (protegido, **cambio autorizado**):
  - agregar `.requestMatchers(HttpMethod.POST, "/quotes/recalculate").hasRole("ADMIN")` entre `.requestMatchers(getPublicRoutes()).permitAll()` y `.anyRequest().authenticated()`;
  - en `exceptionHandling`, agregar `.accessDeniedHandler(new JsonAccessDeniedHandler())` junto al `authenticationEntryPoint` existente.
  - No cambiar CORS, rutas públicas ni el orden de filtros.
  - Depende de T032. T027, T028 y T029, más `JwtOrApiKeyAuthenticationIntegrationTest` sin cambios, tienen que pasar.

**Checkpoint**: sólo ADMIN (por JWT o por API key de un ADMIN o sin dueño) puede recalcular. La suite de seguridad existente sigue en verde.

---

## Phase 8: User Story 6 - Endpoint documentado en OpenAPI (Priority: P3)

**Goal**: Swagger muestra el endpoint con cuerpo opcional, los dos esquemas de seguridad y todas las respuestas.

**Independent Test**: `/v3/api-docs` contiene `/quotes/recalculate` con `bearerAuth`, `apiKeyAuth` y las respuestas 200/400/401/403/409/500.

- [X] T034 [US6] En `backend/src/main/java/com/example/demo/controller/QuoteController.java`, agregar al método:
  - `@Operation(summary = "Ejecuta una corrida de recálculo de cotizaciones", description = ...)`, con la descripción del contrato;
  - `@SecurityRequirement(name = "bearerAuth")` y `@SecurityRequirement(name = "apiKeyAuth")`;
  - `@io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, ...)`;
  - `@ApiResponse` para 200 (`QuoteRunResponse`) y para 400, 401, 403, 409 y 500 (schema `GlobalExceptionHandler.ErrorResponse`, con ejemplos `unknown_strategy`, `unauthorized`, `forbidden`, `recalculation_in_progress` y `recalculation_failed`), como en [contracts/quotes-api.yaml](contracts/quotes-api.yaml).
  - Seguir el estilo de anotaciones de `PlayerController` sin modificarlo.
- [X] T035 [P] [US6] Crear `backend/src/test/java/com/example/demo/config/QuoteOpenApiTest.java` (mismo patrón que `PlayerOpenApiTest`): `GET /v3/api-docs` tiene `paths./quotes/recalculate.post` con `security` que incluye `bearerAuth` y `apiKeyAuth`, `requestBody.required` false o ausente, y `responses` con claves `200`, `400`, `401`, `403` y `409`; además `components.schemas.QuoteRunResponse`.

**Checkpoint**: el endpoint está visible y documentado en Swagger UI.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T036 Crear `backend/src/test/java/com/example/demo/e2e/QuoteRecalculationE2ETest.java` (`@E2ETest`, PostgreSQL y Redis, fuentes externas en `127.0.0.1:1`):
  - **Datos**: 3 jugadores con `PlayerStats` de ≥ 90 minutos en la misma posición, 1 jugador sin stats con una `PlayerQuote` OFFICIAL previa de valor `1.0450` y 1 jugador sin stats ni previa (usar `E2EFixtures` o repositorios).
  - **Pedido**: `POST /quotes/recalculate` con `X-API-KEY` (key creada en el test con un dueño `Role.ADMIN`) y `X-Correlation-ID` fijo → 200.
  - **Verificaciones**:
    - 5 cotizaciones OFFICIAL con el `runId` de la respuesta, un solo `calculatedAt` y el `correlationId` del header;
    - el jugador con previa queda `NO_STATS` 1.0450 sin score, y el que no tiene previa queda `NO_STATS` 1.0000;
    - `marketValue` de cada jugador `isEqualByComparingTo` su cotización y con `scale()` 4;
    - hay snapshots de `player_stats_snapshots` con ese `runId` para los 3 jugadores con stats;
    - la respuesta trae MOMENTUM en `skippedStrategies` y `marketValueUpdated` true.
- [X] T037 [P] Revisar `backend/src/test/java/com/example/demo/**` en busca de otros `equals` sobre `marketValue` leídos de la base con escala 2 (`grep -rn "marketValue" backend/src/test`) y corregirlos a `isEqualByComparingTo` o escala 4 (research D9). Los tests que sólo construyen `Player` en memoria no cambian.
- [X] T038 Correr `cd backend && ./mvnw test -DexcludedGroups=e2e` y, con PostgreSQL y Redis levantados, `./mvnw test -Dgroups=e2e`. Todo tiene que quedar en verde, incluidos los tests de seguridad y de catálogo de Entrega 1 sin cambios de expectativa (salvo T017, T028 y T029).
- [X] T039 Seguir [quickstart.md](quickstart.md) §0–§4 en una base local: `ALTER TABLE`, usuario ADMIN, `curl` con JWT de ADMIN, JWT de USER, API key de ADMIN (200) y API key de USER (403), consultas SQL de verificación y logs `quote_recalculation_started` / `quote_recalculation_finished` con el mismo `runId` y `correlationId`. Probar el endpoint desde Swagger UI con ambos esquemas. Con el catálogo completo de las 5 ligas sincronizado en PostgreSQL (no H2), ejecutar una corrida de todas las estrategias y anotar el `durationMs` de `quote_recalculation_finished` y la cantidad de jugadores. Tiene que ser menor a 60.000 ms (SC-008). Si lo supera, registrar el resultado y consultar antes de optimizar.
- [X] T040 [P] Revisar con `git diff --stat main` que los únicos archivos protegidos modificados sean `Player.java`, `JwtAuthFilter.java`, `ApiKeyAuthFilter.java` y `SecurityConfig.java`, con sólo los cambios autorizados, y que `GlobalExceptionHandler.java`, `PlayerController.java` y `PlayerRepository.java` no aparezcan.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 bloquea todo (sin 015 no compila).
- **Foundational (Phase 2)**: depende de T001 y bloquea todas las historias.
- **US1 (Phase 3)**: depende de Foundational.
- **US2 (Phase 4)**: depende de US1 (T015 extiende el servicio de T011). T014, T016, T017 y T018 pueden ir en paralelo con US1.
- **US5 (Phase 5)**: depende de US1 (mismo servicio). Va antes de US3 para cerrar el servicio.
- **US3 (Phase 6)**: depende de US1 (T011) y de las excepciones de Foundational. T023, T024 y T022 pueden empezar en cuanto termina Foundational.
- **US4 (Phase 7)**: T030, T031 y T032 no dependen de las demás historias. T027 necesita el controller (T025) para el 200.
- **US6 (Phase 8)**: depende de T025.
- **Polish (Phase 9)**: depende de todas las historias.

### User Story Dependencies

- **US1** → base de US2, US5 y US3.
- **US2** y **US5** modifican `QuoteCalculationService.java`: hacerlas en secuencia (US1 → US2 → US5) para evitar conflictos en el mismo archivo.
- **US4** es independiente en producción (filtros y configuración de seguridad). Se puede hacer en paralelo con US1–US5 por otra persona.
- **US6** sólo agrega anotaciones al controller de US3.

### Within Each User Story

- Tests primero (tienen que fallar), después modelo, servicio, controller y configuración.
- No marcar [P] dos tareas que tocan el mismo archivo (`QuoteCalculationService.java` y `QuoteCalculationServiceTest.java` se editan en secuencia).

### Parallel Opportunities

- Foundational: T004, T005, T006, T008 y T009 en paralelo después de T002 y T003.
- US2: T016, T017 y T018 en paralelo entre sí y con T013.
- US3: T022, T023 y T024 en paralelo.
- US4: T027, T028, T029 y T032 en paralelo. T030 y T031 son archivos distintos y también se pueden hacer en paralelo.
- US4 completa en paralelo con US1–US5.

---

## Parallel Example: User Story 4

```text
Task: "T027 Crear QuoteRecalculationSecurityTest.java"
Task: "T028 Actualizar JwtAuthFilterTest.java para el constructor con UserRepository"
Task: "T029 Agregar casos de rol por dueño en ApiKeyAuthFilterTest.java"
Task: "T032 Crear JsonAccessDeniedHandler.java"
```

## Parallel Example: User Story 3

```text
Task: "T022 Crear QuoteControllerTest.java"
Task: "T023 Crear RecalculateRequest.java"
Task: "T024 Crear QuoteRunResponse.java"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 (merge de 015) y Phase 2 (contrato + tipos).
2. Phase 3 (US1): la corrida guarda cotizaciones correctas al invocar el servicio.
3. **Validar**: `QuoteCalculationServiceTest` en verde.

### Incremental Delivery

1. US1 → corrida.
2. US2 → precio de mercado con 4 decimales.
3. US5 → atomicidad, fallas y guardia.
4. US3 → endpoint.
5. US4 → sólo ADMIN. Hasta acá no conviene mergear a `main`: sin US4 cualquier usuario autenticado podría recalcular.
6. US6 → OpenAPI.
7. Polish → E2E, quickstart y revisión de archivos protegidos.

### Parallel Team Strategy

- Persona A: US1 → US2 → US5 → US3 (servicio y endpoint).
- Persona B: US4 (seguridad) en paralelo, y después US6.

---

## Notes

- [P] = otro archivo, sin depender de tareas incompletas.
- Cada historia se puede verificar con su test indicado.
- Los commits los hace el equipo; esta lista no incluye pasos de `git commit` ni `git push`.
- Ante cualquier necesidad de tocar un archivo protegido fuera de los 4 autorizados: detenerse y consultar.
