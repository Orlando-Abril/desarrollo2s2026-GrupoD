---

description: "Dependency-ordered implementation tasks for the valuation strategy comparator"
---

# Tasks: Comparador de estrategias de valuación

**Input**: Design documents from `specs/031-comparador-estrategias/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/strategy-comparison-api.yaml](contracts/strategy-comparison-api.yaml), [quickstart.md](quickstart.md)

**Tests**: El pedido del equipo requiere tests unitarios de las diferencias, tests MockMvc con datos cargados a mano y tests del cambio de estrategia activa (USER 403, ADMIN 200, el recálculo siguiente la usa). En cada historia se escriben primero y deben fallar antes de implementar.

**Organization**: Las tareas se agrupan por historia de usuario. No se modifica ningún archivo de Entrega 1 (`SecurityConfig`, `GlobalExceptionHandler`, `PlayerController`, `CacheConfig`). Si alguna tarea parece requerirlo, hay que detenerse y pedir autorización al equipo indicando el archivo, el cambio mínimo y la alternativa (FR-030).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo con otras tareas marcadas porque usa archivos distintos y no depende de trabajo incompleto.
- **[Story]**: historia de usuario de [spec.md](spec.md).
- Toda tarea incluye la ruta exacta del archivo afectado o validado.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: confirmar una base verde y despejar el riesgo de auditoría antes de cambiar archivos de producción.

- [X] T001 Ejecutar la suite base con `backend/mvnw.cmd test` desde `backend/pom.xml` y registrar cualquier fallo preexistente antes de cambiar archivos de producción
- [X] T002 Verificar contra la base PostgreSQL de desarrollo, con la consulta de [quickstart.md](quickstart.md) (`pg_constraint` sobre `audit_logs`, `contype = 'c'`), si existe un CHECK sobre `action`; registrar el resultado como nota en `specs/031-comparador-estrategias/research.md` (R10). Si existe, **detenerse y consultar al equipo** cómo regenerarlo antes de T011 y T039, que agregan `MARKET_STRATEGY_APPLIED` y `ACTIVE_STRATEGY_CHANGED`; no ejecutar DDL sin aprobación

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: estrategia activa persistida, estrategia de mercado, consulta de cotizaciones por lote y manejo de errores compartidos por todas las historias.

**⚠️ CRITICAL**: ninguna historia comienza hasta completar esta fase.

### Tests foundational

- [X] T003 [P] Crear `ValuationStrategyCatalogPersistedTest`. Debe cubrir que una fila con `activeCode` prevalece sobre `valuation.active-strategy`, que sin fila o con `activeCode` nulo se usa la propiedad, que `alternative()` devuelve la otra definición, y que un `activeCode` persistido inexistente hace fallar `afterSingletonsInstantiated()` con `UnknownValuationStrategyException`. Archivo: `backend/src/test/java/com/example/demo/service/valuation/strategy/ValuationStrategyCatalogPersistedTest.java`
- [X] T004 [P] Crear tests JPA de `findLatestForPlayers`: sólo devuelve la última cotización por jugador de la estrategia pedida, desempata por `calculatedAt` y después por `id` descendente, ignora ids sin cotización y ejecuta una cantidad constante de sentencias al pasar de 5 a 100 ids. Archivo: `backend/src/test/java/com/example/demo/repository/PlayerQuoteLatestForPlayersTest.java`
- [X] T005 [P] Crear `QuoteRunActiveSnapshotTest`. Si `active()` cambia durante una corrida (stub del repositorio de la fila que cambia de valor después del primer jugador), todos los jugadores toman `marketValue` de la estrategia leída al inicio, y el resumen informa esa misma estrategia (FR-024, R3). Con `marketValueUpdated=true` se registra `marketStrategyCode` y `marketStrategyAppliedAt = calculatedAt`, y un fallo al registrarlo no falla la corrida (R4). Auditoría de la estrategia de mercado: la primera corrida (sin fila) y una corrida que cambia `marketStrategyCode` generan exactamente un `AuditLog` por corrida con `action=MARKET_STRATEGY_APPLIED`, actor `SYSTEM`, `entityType=VALUATION_STRATEGY`, `entityId=market`, `before` `{strategy, version}` anterior (o nulo), `after` `{strategy, version, runId}` y el correlation id de la corrida. Una corrida sin cambio de estrategia de mercado no audita. No se audita el `marketValue` de cada jugador. Si la auditoría falla, la fila no cambia y la corrida no falla. Archivo: `backend/src/test/java/com/example/demo/service/valuation/QuoteRunActiveSnapshotTest.java`

### Implementación foundational

- [X] T006 [P] Crear la entidad `ActiveStrategySetting` en `backend/src/main/java/com/example/demo/model/ActiveStrategySetting.java`, tabla `active_strategy_settings`, con id fijo `1` de tipo `Short` y sin generador. Columnas: `active_code varchar(32)` nulo; `changed_at timestamptz` nulo; `changed_by_user_id bigint` nulo; `changed_by_actor_type varchar(16)` nulo (`USER`/`SYSTEM`, `AuditActorType`, `@Enumerated(STRING)`); `market_strategy_code varchar(32)` nulo; `market_strategy_applied_at timestamptz` nulo. Agregar una fábrica `empty()` y métodos `changeActive(code, actor, at)` y `recordMarket(code, at)`
- [X] T007 [P] Crear `ActiveStrategySettingRepository` en `backend/src/main/java/com/example/demo/repository/ActiveStrategySettingRepository.java`, con `findById(Short)` y `findLockedById(Short)` anotado con `@Lock(LockModeType.PESSIMISTIC_WRITE)`
- [X] T008 Modificar `backend/src/main/java/com/example/demo/service/valuation/strategy/ValuationStrategyCatalog.java`:
  - el constructor `@Autowired` recibe además `ActiveStrategySettingRepository`;
  - `active()` lee `findById((short) 1)` y, si `activeCode` no es nulo, devuelve `find(activeCode)`; si no, devuelve la definición de la propiedad;
  - agregar `alternative()`, que devuelve la única definición distinta de `active()`;
  - agregar `propertyDefault()`;
  - implementar `SmartInitializingSingleton`, que resuelve `active()` al arrancar;
  - conservar el constructor package-private `(List, String)` sin repositorio, con el comportamiento actual;
  - actualizar el Javadoc (depende de T006 y T007; hace pasar T003)
- [X] T009 [P] Agregar `findLatestForPlayers(@Param("code") String code, @Param("ids") Collection<Long> ids)` en `backend/src/main/java/com/example/demo/repository/PlayerQuoteRepository.java`, con el mismo `not exists` que `findLatestForAllPlayers`, más `and q.player.id in :ids` y `join fetch q.player` (hace pasar T004)
- [X] T010 [P] Crear el record `StrategyRef(code, version, label)` con `static StrategyRef of(ValuationStrategyDefinition)`, donde `label = code + " v" + version`, y anotaciones `@Schema` según el contrato, en `backend/src/main/java/com/example/demo/dto/strategy/StrategyRef.java`
- [X] T011 Crear `ActiveStrategyService` con la parte de lectura y la estrategia de mercado en `backend/src/main/java/com/example/demo/service/valuation/ActiveStrategyService.java`:
  - `view()` arma `ActiveStrategyView(active, alternative, marketStrategy, pendingRecalculation, changedAt, changedBy)`, con `marketStrategy = marketStrategyCode ?? active` y `pending = marketStrategy ≠ active`;
  - `marketStrategy()`;
  - `recordMarketStrategy(String code, Instant at, UUID runId)` es `@Transactional`: crea la fila con `activeCode` nulo si no existe y reintenta una vez ante `DataIntegrityViolationException`. Si `marketStrategyCode` cambia (incluido de nulo a un código), audita en la misma transacción con `TransactionAuditService.audit(AuditActor.system(), MARKET_STRATEGY_APPLIED, "VALUATION_STRATEGY", "market", {strategy, version} anterior o null, {strategy, version, runId})`. Si no cambia, sólo actualiza `marketStrategyAppliedAt`;
  - agregar `MARKET_STRATEGY_APPLIED` en `backend/src/main/java/com/example/demo/model/market/AuditAction.java` (24 caracteres; la columna admite 32). Antes, revisar el resultado de T002
- [X] T012 Modificar `backend/src/main/java/com/example/demo/service/valuation/QuoteCalculationService.java`:
  - resolver `catalog.active().code()` una vez al construir el `Plan` y usar ese código en el bucle por jugador (hoy cerca de la línea 216) y en `summarize` (cerca de la línea 274);
  - cuando la corrida devuelve `marketValueUpdated=true`, llamar una vez por corrida a `activeStrategyService.recordMarketStrategy(activeCode, run.calculatedAt(), run.id())`, con el correlation id de la corrida en el MDC, capturando `RuntimeException` con log `error` `market_strategy_record_failed runId= errorType=`;
  - depende de T011 y hace pasar T005
- [X] T013 [P] Crear `StrategyExceptionHandler` en `backend/src/main/java/com/example/demo/exception/StrategyExceptionHandler.java`, como `@RestControllerAdvice(assignableTypes = {UserMarketController.class, StrategyController.class})` con `@Order(Ordered.HIGHEST_PRECEDENCE)`. Maneja **sólo** `UnknownValuationStrategyException` (400 `unknown_strategy`, el mismo mensaje que `QuoteExceptionHandler`) y `HttpMessageNotReadableException` (400 `validation_error`). **No** debe manejar `AccessDeniedException` ni `Exception` (R1). Mientras no exista `StrategyController`, se declara sólo con `UserMarketController` y T044 lo agrega
- [X] T014 Ejecutar `backend/mvnw.cmd test` y confirmar que T003–T005 pasan y que los tests existentes de 013/016/018 (`ValuationStrategyActivePropertyTest`, `QuoteControllerTest`, `RankingControllerTest`) siguen en verde sin cambios

**Checkpoint**: la estrategia activa se resuelve desde la base, las corridas la fijan al inicio y se conoce la estrategia de mercado.

---

## Phase 3: User Story 1 - Valuar el portfolio con otra estrategia (Priority: P1) 🎯 MVP

**Goal**: `GET /users/{id}/portfolio?strategy=` agrega `strategyValuation` por posición y en los totales; sin el parámetro la respuesta es idéntica a la 028.

**Independent Test**: una cuenta con tres jugadores con cotizaciones OFFICIAL y MOMENTUM conocidas; comparar con el cálculo manual, con y sin `strategy`.

### Tests for User Story 1

- [X] T015 [P] [US1] Crear tests unitarios de `PortfolioStrategyValuator` en `backend/src/test/java/com/example/demo/service/market/PortfolioStrategyValuatorTest.java`:
  - 15 tokens, costo 16.0000 y MOMENTUM 1.1000 → `value` 16.5000 y `profitLoss` 0.5000;
  - totales iguales a la suma exacta, `totalEquity = balance + totalValue`;
  - una cotización `NO_STATS` 1.0450 se usa e informa su estado;
  - una posición sin cotización deja sus cinco campos en `null`, `totalValue`, `totalProfitLoss` y `totalEquity` en `null`, y `positionsWithoutQuote = 1`;
  - 4 decimales sin redondeos intermedios
- [X] T016 [P] [US1] Crear tests MockMvc con datos cargados a mano en `backend/src/test/java/com/example/demo/controller/UserMarketStrategyControllerTest.java`:
  - `?strategy=MOMENTUM` devuelve los campos agregados según el ejemplo del contrato;
  - sin `strategy`, el JSON no contiene la clave `strategyValuation` en ningún nivel;
  - `?strategy=FOO` devuelve 400 `unknown_strategy`, también para un USER sobre un id ajeno (la estrategia se valida antes, R11);
  - una estrategia válida sobre un id ajeno devuelve 403 `forbidden`, sin credenciales 401 y un ADMIN sobre un id inexistente 404;
  - la cantidad de sentencias es constante al pasar de 2 a 20 posiciones
  - los adapters `FootballDataAdapter` y `WhoScoredAdapter` son `@MockitoBean` y se verifica `verifyNoInteractions`; los conteos de cotizaciones, órdenes, trades, holdings y `audit_logs`, los saldos y los `marketValue` son iguales antes y después de las consultas (FR-028, SC-009)

### Implementation for User Story 1

- [X] T017 [P] [US1] Crear el record `PositionStrategyValuationResponse(quotePrice, quoteStatus, quoteCalculatedAt, value, profitLoss)` en `backend/src/main/java/com/example/demo/dto/market/PositionStrategyValuationResponse.java`. Los campos son "todos nulos juntos cuando no hay cotización"; si hay cotización, escalar a 4 y validar `profitLoss = value − costBasis` mediante una fábrica que recibe `costBasis`. Agregar `@Schema(nullable = true)` en cada campo
- [X] T018 [P] [US1] Crear el record `PortfolioStrategyValuationResponse(strategy: StrategyRef, totalValue, totalProfitLoss, totalEquity, positionsWithoutQuote)` en `backend/src/main/java/com/example/demo/dto/market/PortfolioStrategyValuationResponse.java`. Reglas: los totales son "nulo si `positionsWithoutQuote > 0`", `positionsWithoutQuote ≥ 0` y escala 4
- [X] T019 [US1] Agregar a `backend/src/main/java/com/example/demo/dto/market/PortfolioPositionResponse.java` el último componente `@JsonInclude(JsonInclude.Include.NON_NULL) PositionStrategyValuationResponse strategyValuation` y un constructor secundario con la firma anterior que pase `null`; validar `strategyValuation.profitLoss = strategyValuation.value − costBasis` cuando corresponda
- [X] T020 [US1] Agregar a `backend/src/main/java/com/example/demo/dto/market/PortfolioResponse.java` el último componente `@JsonInclude(JsonInclude.Include.NON_NULL) PortfolioStrategyValuationResponse strategyValuation` y un constructor secundario con la firma anterior. Cuando está presente y sus totales no son nulos, validar `totalValue` = suma de los `value` de las posiciones, `totalProfitLoss = totalValue − totalCost` y `totalEquity = balance + totalValue`
- [X] T021 [US1] Crear `PortfolioStrategyValuator`, sin Spring, en `backend/src/main/java/com/example/demo/service/market/PortfolioStrategyValuator.java`. Recibe las posiciones ya construidas, el saldo y un `Map<Long, PlayerQuote>`, y devuelve las posiciones con `strategyValuation` más el `PortfolioStrategyValuationResponse` (hace pasar T015)
- [X] T022 [US1] Modificar `backend/src/main/java/com/example/demo/service/market/PortfolioService.java`:
  - agregar `portfolio(Long, Authentication, Optional<String> strategy)`, que resuelve `catalog.find(code)` antes de `authorizeAndLoad` (R11) y, con estrategia, llama una vez a `quoteRepository.findLatestForPlayers(code, playerIds)` dentro de la misma transacción `REPEATABLE_READ` y delega en `PortfolioStrategyValuator`;
  - la firma actual `portfolio(Long, Authentication)` delega con `Optional.empty()`;
  - no aumentar la complejidad cognitiva de los métodos existentes
- [X] T023 [US1] Agregar `@RequestParam(required = false) String strategy` en `backend/src/main/java/com/example/demo/controller/UserMarketController.java` y pasarlo como `Optional.ofNullable(strategy)`. Actualizar la anotación OpenAPI del portfolio (descripción del parámetro, campos agregados y respuesta 400 `unknown_strategy`) según `contracts/strategy-comparison-api.yaml` (hace pasar T016)
- [X] T024 [US1] Ejecutar `backend/mvnw.cmd test -Dtest='PortfolioServiceTest,UserMarketControllerTest,UserMarketSecurityTest,UserMarketPerformanceTest,PortfolioStrategyValuatorTest,UserMarketStrategyControllerTest'` y confirmar que los tests de la 028 pasan **sin modificarlos** (FR-001, SC-002)

**Checkpoint**: portfolio comparativo funcional e independiente.

---

## Phase 4: User Story 2 - Detectar jugadores subvaluados y sobrevaluados (Priority: P1)

**Goal**: `GET /quotes/opportunities` con dos listas ordenadas por diferencia porcentual absoluta, con filtros de liga y posición y un `limit`.

**Independent Test**: jugadores con cotizaciones activas y alternativas conocidas, incluidos estados no calculados y diferencias nulas; verificar inclusión, separación, orden y porcentajes.

### Tests for User Story 2

- [X] T025 [P] [US2] Crear tests unitarios de `OpportunityCalculator` en `backend/src/test/java/com/example/demo/service/valuation/OpportunityCalculatorTest.java`:
  - `marketValue` 1.0000 y alternativa 1.2000 → subvaluado, `difference` 0.2000 y `differencePercent` 20.00;
  - 2.0000 contra 1.5000 → sobrevaluado, −0.5000 y −25.00;
  - diferencia cero → en ninguna lista;
  - exclusión si el estado de la alternativa o de la estrategia de mercado es `NO_STATS` o `INSUFFICIENT_HISTORY`, si no hay alternativa o si `marketValue` es nulo o ≤ 0;
  - orden por `|pct|` sin redondear, descendente, y desempate por nombre e id ascendentes, incluido un caso de empate tras redondear a 2 decimales;
  - corte en `limit`;
  - redondeo `HALF_EVEN` a 2 decimales sólo al presentar
- [X] T026 [P] [US2] Crear tests MockMvc con datos cargados a mano en `backend/src/test/java/com/example/demo/controller/OpportunityControllerTest.java`:
  - 200 con `activeStrategy`, `alternativeStrategy`, `marketStrategy` y `pendingRecalculation`;
  - `league=LA_LIGA&position=FORWARD` filtra ambas listas;
  - `limit=5` corta cada lista; `limit=0`, `limit=51` y `league=XX` devuelven 400 `validation_error`;
  - sin credenciales 401;
  - con MOMENTUM activa la alternativa es OFFICIAL;
  - con un cambio pendiente (fila con `activeCode=MOMENTUM` y `marketStrategyCode=OFFICIAL`) las listas quedan vacías;
  - la cantidad de sentencias es constante al pasar de 10 a 100 jugadores
  - los adapters `FootballDataAdapter` y `WhoScoredAdapter` son `@MockitoBean` y se verifica `verifyNoInteractions`; los conteos de cotizaciones, órdenes, trades, holdings y `audit_logs`, los saldos y los `marketValue` son iguales antes y después de las consultas (FR-028, SC-009)

### Implementation for User Story 2

- [X] T027 [P] [US2] Crear los records `OpportunityItemResponse(playerId, name, team, league, positions, marketValue, alternativeQuote, alternativeQuoteCalculatedAt, difference, differencePercent)` y `OpportunitiesResponse(activeStrategy, alternativeStrategy, marketStrategy, pendingRecalculation, undervalued, overvalued)`, con `@Schema` y ejemplos del contrato, en `backend/src/main/java/com/example/demo/dto/quote/OpportunitiesResponse.java` y `backend/src/main/java/com/example/demo/dto/quote/OpportunityItemResponse.java`
- [X] T028 [US2] Crear `OpportunityCalculator`, sin Spring, en `backend/src/main/java/com/example/demo/service/valuation/OpportunityCalculator.java`. Se basa en las reglas de research R7: `diff = alt − marketValue` (escala 4) y `pct = diff ÷ marketValue × 100` con `MathContext.DECIMAL128` (hace pasar T025)
- [X] T029 [US2] Crear `OpportunityService` en `backend/src/main/java/com/example/demo/service/valuation/OpportunityService.java`, `@Transactional(readOnly = true)`:
  - resolver la activa, la alternativa (`catalog.alternative()`) y la estrategia de mercado (`ActiveStrategyService.marketStrategy()`);
  - cargar jugadores con `PlayerRepository.findAll(spec)` usando `PlayerSpecifications` para la liga y la posición, sin modificar esos archivos;
  - llamar a `findLatestForAllPlayers(alternativa)` y a `findLatestForAllPlayers(mercado)`, indexar por `playerId` y delegar en `OpportunityCalculator`
- [X] T030 [US2] Agregar `GET /quotes/opportunities` en `backend/src/main/java/com/example/demo/controller/QuoteController.java`. Parámetros: `league`, `position` (con `parseEnum` como en el ranking) y `limit` (con `parseInteger("limit", raw, 1, 50, 10)`). Agregar las anotaciones OpenAPI del contrato con respuestas 200, 400 y 401 (hace pasar T026)

**Checkpoint**: oportunidades funcionales e independientes del portfolio.

---

## Phase 5: User Story 3 - Mostrar la cotización alternativa en el Catálogo (Priority: P2)

**Goal**: `GET /quotes/latest?playerIds=&strategy=` devuelve la última cotización de hasta 100 jugadores en una sola consulta.

**Independent Test**: tomar los ids de una página de `GET /players`, pedirlos y comparar con el historial.

### Tests for User Story 3

- [X] T031 [P] [US3] Crear tests MockMvc con datos cargados a mano en `backend/src/test/java/com/example/demo/controller/LatestQuotesControllerTest.java`:
  - un item por id en el orden pedido, con `value`, `status`, `calculatedAt` y `strategyVersion` de la última cotización;
  - sin `strategy` se usa la alternativa y se informa en `strategy`;
  - un id sin cotización devuelve `quote: null`;
  - los duplicados se informan una vez y los inexistentes se omiten;
  - 100 ids distintos devuelven 200; 101 ids, `playerIds` vacío, ausente, `a`, `0` o `-3`, y `strategy=FOO` devuelven 400;
  - sin credenciales 401;
  - la cantidad de sentencias es constante al pasar de 5 a 100 ids
  - los adapters `FootballDataAdapter` y `WhoScoredAdapter` son `@MockitoBean` y se verifica `verifyNoInteractions`; los conteos de cotizaciones, órdenes, trades, holdings y `audit_logs`, los saldos y los `marketValue` son iguales antes y después de las consultas (FR-028, SC-009)

### Implementation for User Story 3

- [X] T032 [P] [US3] Crear el record `LatestQuotesResponse(strategy: StrategyRef, items: List<Item(playerId, quote: Quote(value, status, calculatedAt, strategyVersion) | null)>)` con `@Schema(nullable = true)` en `quote`, en `backend/src/main/java/com/example/demo/dto/quote/LatestQuotesResponse.java`
- [X] T033 [US3] Crear `LatestQuoteService` en `backend/src/main/java/com/example/demo/service/valuation/LatestQuoteService.java`, `@Transactional(readOnly = true)`. Parsea `playerIds` (separado por comas, recortado; cada id es `Long` positivo; vacío, mal formado o más de 100 distintos lanza `InvalidQuoteQueryException`; elimina duplicados conservando el orden). Resuelve la estrategia (`catalog.find` o `catalog.alternative()`), filtra los ids existentes con una consulta de `PlayerRepository` y usa `findLatestForPlayers`
- [X] T034 [US3] Agregar `GET /quotes/latest` con `@RequestParam(required = false) String playerIds` y `String strategy` en `backend/src/main/java/com/example/demo/controller/QuoteController.java`, con las anotaciones OpenAPI del contrato (hace pasar T031)

**Checkpoint**: el Álbum puede pedir la cotización alternativa por página.

---

## Phase 6: User Story 4 - Cambiar la estrategia activa global (Priority: P2)

**Goal**: `PUT /strategies/active` sólo para ADMIN; el cambio persiste, se audita, vacía el caché del ranking y lo usa el próximo recálculo.

**Independent Test**: un USER recibe 403 y un ADMIN 200; queda la auditoría, `marketValue` no cambia, el recálculo siguiente usa la nueva estrategia y la elección sobrevive a un reinicio.

### Tests for User Story 4

- [X] T035 [P] [US4] Crear tests MockMvc del `PUT` en `backend/src/test/java/com/example/demo/controller/StrategyControllerTest.java`:
  - USER → 403 con el cuerpo `{"error":"forbidden","message":"No tenés permisos para esta operación"}`, sin cambios ni auditoría;
  - sin credenciales → 401;
  - ADMIN por JWT → 200 con `changed=true`, `active=MOMENTUM`, `alternative=OFFICIAL` y `pendingRecalculation=true`;
  - API key de servicio sin dueño → 200 con auditoría de actor `SYSTEM`;
  - API key cuyo dueño es ADMIN → 200 con auditoría de actor `USER` y el id de ese dueño;
  - `{"strategy":"FOO"}` → 400 `unknown_strategy` con los códigos válidos; cuerpo vacío, `{}` o JSON inválido → 400;
  - repetir el mismo código → 200 con `changed=false` y sin un `AuditLog` nuevo;
  - exactamente un `AuditLog` con `action=ACTIVE_STRATEGY_CHANGED`, `entityType=VALUATION_STRATEGY`, `entityId=active`, `before` `{strategy: OFFICIAL, version: 1}`, `after` `{strategy: MOMENTUM, version: 1}` y el correlation id del header;
  - `marketValue` de los jugadores no cambia
- [X] T036 [P] [US4] Crear `ActiveStrategyRecalculationTest` en `backend/src/test/java/com/example/demo/service/valuation/ActiveStrategyRecalculationTest.java`:
  - después de un cambio como ADMIN, `POST /quotes/recalculate` informa `activeStrategy=MOMENTUM` y `marketValue` es igual a la cotización MOMENTUM de esa corrida;
  - `GET /strategies/active` muestra `pendingRecalculation=false`;
  - un nuevo contexto con `valuation.active-strategy=OFFICIAL` y la fila persistida sigue resolviendo MOMENTUM (FR-021);
  - el ranking sin `strategy` consultado antes y después del cambio devuelve la estrategia nueva sin servir la entrada cacheada anterior;
  - después del cambio, `GET /players/{id}/quotes` sin `strategy` devuelve cotizaciones MOMENTUM;
  - una corrida disparada invocando `QuoteRecalculationScheduler` (en lugar del endpoint) también fija `marketValue` con MOMENTUM y registra `marketStrategy=MOMENTUM`
- [X] T037 [P] [US4] Crear un test de concurrencia en `backend/src/test/java/com/example/demo/service/valuation/ActiveStrategyServiceConcurrencyTest.java`: dos cambios simultáneos sobre una fila inexistente terminan sin error; cada cambio efectivo audita el `before` real leído bajo lock y la fila queda con una de las dos estrategias

### Implementation for User Story 4

- [X] T038 [P] [US4] Crear `MethodSecurityConfig` con `@Configuration @EnableMethodSecurity` en `backend/src/main/java/com/example/demo/config/MethodSecurityConfig.java`. No modificar `SecurityConfig`
- [X] T039 [P] [US4] Agregar `ACTIVE_STRATEGY_CHANGED` en `backend/src/main/java/com/example/demo/model/market/AuditAction.java` y actualizar su Javadoc con la spec 031. La columna admite 32 caracteres. Antes, revisar el resultado de T002
- [X] T040 [P] [US4] Crear el record `ChangeActiveStrategyRequest(@NotBlank String strategy)` en `backend/src/main/java/com/example/demo/dto/strategy/ChangeActiveStrategyRequest.java` y `ActiveStrategyChangedEvent(String from, String to)` en `backend/src/main/java/com/example/demo/service/valuation/ActiveStrategyChangedEvent.java`
- [X] T041 [US4] Agregar `change(String rawCode, Authentication auth)` a `backend/src/main/java/com/example/demo/service/valuation/ActiveStrategyService.java`, `@Transactional`:
  - `trim` y `catalog.find` (400 si no existe);
  - `findLockedById(1)`; si no existe, insertar `empty()` y reintentar una vez con lock ante `DataIntegrityViolationException`;
  - si el código coincide con `active()`, devolver `changed=false` sin escribir;
  - si no, resolver el actor: `AuditActor.user(id)` si `UserRepository.findByUsername(auth.getName())` existe; si no, `AuditActor.system()`;
  - aplicar `changeActive`, auditar con `TransactionAuditService.audit(actor, ACTIVE_STRATEGY_CHANGED, "VALUATION_STRATEGY", "active", {strategy, version} anterior, {strategy, version} nuevo)` y publicar `ActiveStrategyChangedEvent`;
  - registrar el log `active_strategy_changed from= to= actorType=`
- [X] T042 [US4] Agregar a `backend/src/main/java/com/example/demo/service/valuation/QuoteCacheEvictor.java` un `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` para `ActiveStrategyChangedEvent`, que vacíe `CacheConfig.QUOTES_RANKING_CACHE` con el mismo manejo de errores que hoy (log `warn` `quotes_ranking_cache_evict_failed`)
- [X] T043 [P] [US4] Crear el record `ActiveStrategyResponse(active, alternative, marketStrategy, pendingRecalculation, changedAt, changedBy{actorType, userId, username}, changed)` con `@JsonInclude(NON_NULL)` **sólo** en `changed`, en `backend/src/main/java/com/example/demo/dto/strategy/ActiveStrategyResponse.java`. `changedAt` y `changedBy` se serializan `null` si nunca hubo cambio
- [X] T044 [US4] Crear `StrategyController` en `backend/src/main/java/com/example/demo/controller/StrategyController.java`, con `@RequestMapping("/strategies")` y `PUT /active` anotado con `@PreAuthorize("hasRole('ADMIN')")` y `@Valid @RequestBody ChangeActiveStrategyRequest`. Agregar las anotaciones OpenAPI del contrato (seguridad y respuestas 200, 400, 401 y 403). Sumar `StrategyController.class` a `assignableTypes` de `backend/src/main/java/com/example/demo/exception/StrategyExceptionHandler.java` (hace pasar T035–T037)

**Checkpoint**: el superusuario cambia la estrategia; el cambio es persistente, auditado y lo usa el próximo recálculo.

---

## Phase 7: User Story 5 - Consultar la estrategia activa y la alternativa (Priority: P2)

**Goal**: `GET /strategies/active` para cualquier usuario autenticado.

**Independent Test**: consultar como USER y como ADMIN antes y después de un cambio y de un recálculo; sin credenciales, 401.

### Tests for User Story 5

- [X] T045 [P] [US5] Agregar tests del `GET` en `backend/src/test/java/com/example/demo/controller/StrategyControllerTest.java`:
  - sin fila → 200 con `active=OFFICIAL`, `alternative=MOMENTUM`, `marketStrategy=OFFICIAL`, `pendingRecalculation=false`, `changedAt=null` y `changedBy=null`, para USER y para ADMIN;
  - con la fila cargada a mano (`activeCode=MOMENTUM`, `marketStrategyCode=OFFICIAL`) → `pendingRecalculation=true` y `changedBy` con `username`;
  - sin credenciales → 401;
  - la respuesta del `GET` no incluye la clave `changed`

### Implementation for User Story 5

- [X] T046 [US5] Agregar `GET /active` en `backend/src/main/java/com/example/demo/controller/StrategyController.java`, sin `@PreAuthorize`. Devuelve `ActiveStrategyResponse` a partir de `ActiveStrategyService.view()` y resuelve `username` con una sola búsqueda por id cuando `changedByUserId` no es nulo. Agregar las anotaciones OpenAPI (200 y 401) (hace pasar T045)

**Checkpoint**: el frontend puede armar el selector y aclarar con qué precio se opera.

---

## Phase 8: User Story 6 - Consultar los contratos en OpenAPI (Priority: P3)

**Goal**: todas las operaciones y el parámetro nuevo documentados.

**Independent Test**: leer `/v3/api-docs` y comprobar que permite construir pedidos válidos e inválidos.

- [X] T047 [P] [US6] Crear un test que lea `/v3/api-docs` en `backend/src/test/java/com/example/demo/config/StrategyComparisonOpenApiTest.java`. Debe verificar:
  - `GET /quotes/opportunities` con `league`, `position` y `limit` (mínimo 1, máximo 50, por defecto 10) y respuestas 200, 400 y 401;
  - `GET /quotes/latest` con `playerIds` y `strategy` y respuestas 200, 400 y 401;
  - `GET /strategies/active` con 200 y 401, y `PUT` con 200, 400, 401 y 403;
  - el parámetro opcional `strategy` en `/users/{id}/portfolio` con respuesta 400;
  - los esquemas `StrategyRef`, `OpportunitiesResponse`, `LatestQuotesResponse` y `ActiveStrategyResponse`;
  - seguridad `bearerAuth`/`apiKeyAuth` en las cuatro operaciones
- [X] T048 [US6] Completar las anotaciones faltantes que detecte T047 en `backend/src/main/java/com/example/demo/controller/QuoteController.java`, `backend/src/main/java/com/example/demo/controller/StrategyController.java` y `backend/src/main/java/com/example/demo/controller/UserMarketController.java`, alineadas con `specs/031-comparador-estrategias/contracts/strategy-comparison-api.yaml`

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T049 [P] Crear `StrategyComparisonPerformanceIT` en `backend/src/test/java/com/example/demo/controller/StrategyComparisonPerformanceIT.java`, siguiendo el patrón de `PlayerDetailPerformanceIT`. Con 2.500 jugadores e historial de ambas estrategias, cada consulta de oportunidades, de `/quotes/latest` con 100 ids y de portfolio con estrategia responde en menos de 2 s, tras un calentamiento (SC-007)
- [ ] T050 [P] Agregar un escenario e2e en `backend/src/test/java/com/example/demo/e2e/ActiveStrategyE2ETest.java` contra PostgreSQL `create-drop`. Un ADMIN cambia la estrategia, se verifica que `ACTIVE_STRATEGY_CHANGED` y `MARKET_STRATEGY_APPLIED` se insertan en `audit_logs` con el CHECK del enum que genera Hibernate, y que el recálculo siguiente aplica la estrategia. **Aclaración**: como el esquema se crea de cero, el CHECK ya incluye las acciones nuevas; este test **no** reproduce el riesgo de bases existentes (R10), que sólo cubre T002
- [X] T051 Verificar con `git diff --name-only main` que no cambió ningún archivo de Entrega 1 (`SecurityConfig.java`, `GlobalExceptionHandler.java`, `PlayerController.java`, `CacheConfig.java`) ni los tests existentes de la 028
- [ ] T052 Ejecutar `backend/mvnw.cmd verify` con la suite completa y revisar el análisis de SonarCloud: menos de 10 issues, sin duplicaciones nuevas y sin subir la complejidad cognitiva de `PortfolioService` y `QuoteCalculationService`
- [ ] T053 Recorrer la validación manual de `specs/031-comparador-estrategias/quickstart.md` (pasos 1–6) contra el backend local y anotar las diferencias encontradas

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias. T002 bloquea T011 y T039 si aparece el CHECK.
- **Foundational (Phase 2)**: depende de Setup y bloquea todas las historias.
- **US1 (Phase 3)** y **US2 (Phase 4)**: dependen sólo de Foundational y son independientes entre sí.
- **US3 (Phase 5)**: depende de Foundational (T009, T010).
- **US4 (Phase 6)**: depende de Foundational (T008, T011–T013).
- **US5 (Phase 7)**: depende de US4 sólo porque comparte `StrategyController` y `ActiveStrategyResponse` (T043, T044). Sus tests cargan la fila a mano y no requieren el `PUT`.
- **US6 (Phase 8)**: depende de las historias cuyas operaciones documenta.
- **Polish (Phase 9)**: depende de todas.

### Within Each User Story

- Los tests van primero y deben fallar; después los DTOs, el service y el controller.
- `QuoteController.java` lo tocan T030, T034 y T048: esas tareas no van en paralelo entre sí.
- `StrategyController.java` lo tocan T044, T046 y T048, en secuencia.
- `ActiveStrategyService.java` lo tocan T011 y T041, en secuencia.

### Parallel Opportunities

- Foundational: T003, T004 y T005 (tests) y T006, T007, T009, T010 y T013 (archivos distintos).
- US1 y US2 pueden desarrollarse en paralelo después de la fase 2.
- Dentro de US4: T035, T036 y T037 (tests) y T038, T039, T040 y T043.

## Parallel Example: User Story 2

```text
Task: "T025 [US2] Tests unitarios de OpportunityCalculator en .../service/valuation/OpportunityCalculatorTest.java"
Task: "T026 [US2] Tests MockMvc en .../controller/OpportunityControllerTest.java"
Task: "T027 [US2] DTOs OpportunitiesResponse y OpportunityItemResponse en .../dto/quote/"
```

## Parallel Example: User Story 4

```text
Task: "T038 [US4] MethodSecurityConfig en .../config/MethodSecurityConfig.java"
Task: "T039 [US4] ACTIVE_STRATEGY_CHANGED en .../model/market/AuditAction.java"
Task: "T040 [US4] ChangeActiveStrategyRequest y ActiveStrategyChangedEvent"
Task: "T043 [US4] ActiveStrategyResponse en .../dto/strategy/ActiveStrategyResponse.java"
```

## Implementation Strategy

### MVP First (User Story 1)

1. Fases 1 y 2.
2. Fase 3 (US1): portfolio con `?strategy=`.
3. **Validar**: los tests de la 028 siguen sin cambios y los nuevos pasan. Demo con `quickstart.md` paso 2.

### Incremental Delivery

1. Fundaciones → US1 (MVP) → US2 (oportunidades): las dos historias P1 cierran el valor de comparar.
2. US3 (Catálogo) → US4 + US5 (cambio y consulta de la activa) → US6 (OpenAPI) → Polish.
3. Después de cada fase, `backend/mvnw.cmd test` en verde antes de seguir.

## Notes

- Ningún commit lo hace el agente: los commits los hace el equipo.
- Ninguna consulta calcula cotizaciones ni llama a adapters (FR-028); los tests MockMvc no deben requerir mocks de `FootballDataAdapter` ni de `WhoScoredAdapter`.
