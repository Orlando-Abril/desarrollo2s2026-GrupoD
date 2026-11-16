---

description: "Tareas para implementar las métricas propias de la aplicación"
---

# Tasks: Métricas propias de la aplicación

**Input**: `specs/036-metricas-avanzadas/spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/business-metrics.md` y `quickstart.md`.

**Tests**: Pedidos expresamente en el plan: unitarias de `BusinessMetrics` con `SimpleMeterRegistry`, de service con el incremento de cada escenario y una de contexto sobre `/actuator/prometheus`.

**Organization**: Una fase por historia de la spec. Las tareas con `[P]` tocan archivos distintos y no dependen de tareas incompletas de su fase.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: ejecutable en paralelo con otras `[P]` de la misma fase.
- **[Story]**: historia de la spec (US1 a US5).
- Rutas relativas a la raíz del repositorio. `…/demo/` abrevia `backend/src/main/java/com/example/demo/` y `…/test/demo/` abrevia `backend/src/test/java/com/example/demo/`.

## Reglas para todas las tareas

- Sólo se modifican los archivos previos autorizados en "Contexto y límites" de la spec. No se tocan controllers, `CacheConfig`, `SecurityConfig`, filtros, eventos existentes, la configuración de Prometheus o Grafana de la 035 ni la Constitution.
- Ningún service ni adapter importa `io.micrometer.*`: usan `BusinessMetrics`.
- Medir nunca cambia el resultado, la excepción ni la transacción de la operación (FR-010). Cada llamada a `BusinessMetrics` es una línea y no altera el flujo existente.
- Etiquetas: sólo los valores de `data-model.md`. Nunca ids, nombres de usuario, emails, jugadores ni mensajes.
- No hacer commit, push ni PR. No correr pruebas e2e ni SQL contra la base del usuario.
- Pruebas de backend en Windows desde la shell de Claude: `$env:JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:\udtmp"`, sin sandbox y siempre con `-DexcludedGroups=e2e` en la suite completa.

---

## Phase 1: Setup

**Purpose**: Confirmar el punto de partida y los límites.

- [X] T001 Revisar `…/demo/service/market/OrderService.java` (`place`, `applyFill`), `…/demo/service/market/MarketMakerService.java` (guardado de la orden, ~línea 174), `…/demo/service/valuation/QuoteCalculationService.java` (`recalculate`, `execute`), `…/demo/scheduler/QuoteRecalculationScheduler.java`, `…/demo/adapter/footballdata/FootballDataAdapter.java`, `…/demo/adapter/whoscored/WhoScoredAdapter.java`, `…/demo/service/PlayerStatsService.java` (`updateLeague`), `…/demo/service/AuthService.java` y `…/test/demo/architecture/{LayeredArchitectureTest,CodingRulesTest}.java`. Confirmar que no hay ningún `MeterRegistry` en `backend/src/main` y que `micrometer-registry-prometheus` ya está en `backend/pom.xml` (no se agregan dependencias).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: El paquete `metrics` con todos los meters del contrato y su regla de capas. Bloquea todas las historias.

- [X] T002 [P] Crear los enums de etiquetas en `…/demo/metrics/`: `OrderRejectionReason` (`insufficient_balance`, `insufficient_tokens`, `price_out_of_band`), `RecalculationTrigger` (`manual`, `scheduled`), `RecalculationOutcome` (`success`, `failure`), `ExternalSource` (`football_data`, `whoscored`), `ExternalCallOutcome` (`success`, `error`, `cache`) y `AuthOutcome` (`success`, `failure`). Cada constante expone su valor de etiqueta en minúsculas con un método `tag()`. Para lado y tipo se reutilizan `model.market.OrderSide` y `model.market.OrderType` con su `name()` (`BUY`/`SELL`, `MARKET`/`LIMIT`).
- [X] T003 Crear `…/demo/metrics/BusinessMetrics.java` (`@Component`, constructor con `MeterRegistry` y `UserRepository`) según `contracts/business-metrics.md`:
  - **Registro en el constructor**: cada meter con su nombre exacto y descripción (`# HELP`), y todas las combinaciones de etiquetas cerradas en 0. Son `gdmarket.orders.placed{side,type}` (4), `gdmarket.orders.rejected{reason}` (3), `gdmarket.trades.executed`, `gdmarket.trades.tokens`, `gdmarket.trades.volume.credits`, el timer `gdmarket.quotes.recalculation{trigger,outcome}` (4, con `publishPercentileHistogram()`), `gdmarket.strategy.changes`, `gdmarket.external.calls{source,outcome}` (6), `gdmarket.auth.registrations{outcome}` (2) y `gdmarket.auth.logins{outcome}` (2). Sin `baseUnit` en los contadores (research R9).
  - **Gauge**: `gdmarket.users.registered`, con función sobre `UserRepository.count()`. Si el conteo lanza, devuelve `Double.NaN` y escribe `log.warn("business_metric_failed metric=gdmarket.users.registered errorType={}")`.
  - **Última duración del recálculo**: `TimeGauge` `gdmarket.quotes.recalculation.last` (sin etiquetas, `TimeUnit.NANOSECONDS`, sobre un `AtomicLong` inicializado en 0), exportado como `gdmarket_quotes_recalculation_last_seconds`. `recalculationFinished` hace `set(duration.toNanos())` además de registrar en el timer. No alcanza con `_max` del timer: vuelve a 0 a los ~2 minutos y el recálculo programado es semanal.
  - **Métodos diferidos**: `orderCreated(OrderSide, OrderType)` y `tradeExecuted(int tokens, BigDecimal credits)`. Incrementan trades +1, tokens +`tokens` y volumen +`credits.doubleValue()`. Si `TransactionSynchronizationManager.isSynchronizationActive()`, registran una `TransactionSynchronization` cuyo `afterCommit` incrementa; si no, incrementan en el momento.
  - **Métodos inmediatos**: `orderRejected(OrderRejectionReason)`, `recalculationFinished(RecalculationTrigger, RecalculationOutcome, Duration)`, `quotesCalculated(String strategy, int count)` (contador `gdmarket.quotes.calculated{strategy}` creado al primer uso con `Counter.builder(...).register(registry)`; ignora `count <= 0`), `activeStrategyChanged()`, `externalCall(ExternalSource, ExternalCallOutcome)`, `registration(AuthOutcome)` y `login(AuthOutcome)`.
  - **Aislamiento**: el cuerpo de cada método y de cada `afterCommit` va en `try/catch (RuntimeException)` con `log.warn("business_metric_failed metric={} errorType={}", ...)`. Nunca relanza.
  - **Dependencias**: no importa nada de `service`, `controller` ni `adapter`.
- [X] T004 [P] Crear `…/test/demo/metrics/BusinessMetricsTest.java` con `SimpleMeterRegistry` y un `UserRepository` mockeado. Casos:
  - todas las series del contrato existen en 0 al construir, con descripción no vacía;
  - cada método inmediato incrementa sólo su combinación de etiquetas;
  - `tradeExecuted(3, 30.50)` suma 1, 3 y 30.5;
  - con `TransactionSynchronizationManager.initSynchronization()`, `orderCreated` y `tradeExecuted` no incrementan hasta llamar `afterCommit` de las sincronizaciones registradas, y no incrementan si sólo se llama `afterCompletion(STATUS_ROLLED_BACK)` (limpiar con `clearSynchronization()` en `@AfterEach`);
  - `quotesCalculated("OFFICIAL", 5)` crea `gdmarket.quotes.calculated{strategy=OFFICIAL}` en 5;
  - el gauge refleja `count()` y devuelve `NaN` si `count()` lanza;
  - `recalculationFinished(MANUAL, SUCCESS, Duration.ofSeconds(3))` deja `gdmarket.quotes.recalculation.last` en 3 s, y una segunda llamada con 1 s lo deja en 1 s;
  - con la medición fallando, ningún método propaga excepciones. Simularlo con `FailingMeterRegistry` (subclase de prueba de `SimpleMeterRegistry` en `…/test/demo/metrics/` que sobreescribe `newCounter` y `newTimer` para devolver meters que lanzan al medir), y construir `BusinessMetrics` con él. Un mock de Mockito no sirve: no puede stubbear los métodos internos de registro de Micrometer y `BusinessMetrics` fallaría al construirse. No usar un `SimpleMeterRegistry` cerrado: devuelve meters no-op y no lanza, así que el caso no probaría nada. Verificar también que se loguea `business_metric_failed`.
- [X] T005 [P] Actualizar `…/test/demo/architecture/LayeredArchitectureTest.java`:
  - agregar `.layer("Metrics").definedBy(ROOT + ".metrics..")` y `.whereLayer("Metrics").mayOnlyBeAccessedByLayers("Service", "Adapter")`;
  - cambiar `Repository` a `mayOnlyBeAccessedByLayers("Service", "Metrics")` y sumar `"Metrics"` a los accesos permitidos de `Model`;
  - mantener el `because` de Constitution §2.1.
  
  En `…/test/demo/architecture/CodingRulesTest.java`, sumar `ROOT + ".metrics"` a `importPackages` de `central_layers_have_no_cycles` y actualizar su comentario. No cambiar otras reglas.

**Checkpoint**: `./mvnw.cmd -Dtest="BusinessMetricsTest,LayeredArchitectureTest,CodingRulesTest" test` pasa. `BusinessMetrics` existe en el contexto, pero ningún service lo usa todavía.

---

## Phase 3: User Story 1 - Observar la actividad del mercado (Priority: P1) 🎯 MVP

**Goal**: Contar órdenes creadas por lado y tipo, rechazos por motivo, trades, tokens y volumen, sólo para operaciones confirmadas.

**Independent Test**: Crear órdenes que crucen y provocar los tres rechazos; consultar `/actuator/prometheus` como ADMIN antes y después (quickstart, pasos 4 y 5).

- [X] T006 [US1] En `…/demo/service/market/OrderService.java`:
  - agregar `BusinessMetrics` al constructor;
  - en `place`, después de `orderRepository.save(...)`, llamar a `metrics.orderCreated(side, request.type())`;
  - en el bucle de fills, por cada `Trade` devuelto por `applyFill`, llamar a `metrics.tradeExecuted(trade.getQuantity(), trade.getPrice().multiply(BigDecimal.valueOf(trade.getQuantity())))` (verificar los getters reales de `Trade`);
  - envolver el cuerpo de `place` en `try { … } catch (InsufficientBalanceException e) { metrics.orderRejected(INSUFFICIENT_BALANCE); throw e; }`, con el mismo patrón para `InsufficientTokensException` → `INSUFFICIENT_TOKENS` y `PriceOutOfBandException` → `PRICE_OUT_OF_BAND`.
  
  `InvalidOrderException`, `PlayerNotQuotedException` y otros errores no se cuentan. No cambiar logs, auditoría, locks ni el orden de las operaciones.
- [X] T007 [P] [US1] En `…/demo/service/market/MarketMakerService.java`, agregar `BusinessMetrics` al constructor y llamar a `metrics.orderCreated(OrderSide.SELL, OrderType.LIMIT)` inmediatamente después del `orderRepository.save(MarketOrder.limit(...))` (~línea 174). Nada más.
- [X] T008 [P] [US1] Ajustar el constructor de `OrderService` en `…/test/demo/service/market/OrderServiceNotQuotedTest.java`, pasando `new BusinessMetrics(new SimpleMeterRegistry(), mock(UserRepository.class))`. Sólo eso.
- [X] T009 [US1] Crear `…/test/demo/service/market/OrderMetricsTest.java` (`@SpringBootTest`, mismo setup y fixtures que `OrderServiceBuyTest`; leer meters con `MeterRegistry` inyectado y comparar diferencias antes y después). Casos:
  - compra MARKET que cruza: `orders.placed{BUY,MARKET}` +1, `trades.executed` +1, `trades.tokens` +cantidad y `trades.volume.credits` +cantidad×precio;
  - venta LIMIT sin cruce: `{SELL,LIMIT}` +1 y trades sin cambio;
  - una orden que cruza con dos órdenes del libro suma 2 trades y la suma de sus cantidades;
  - saldo insuficiente, tokens insuficientes y `limitPrice` fuera de banda: +1 en su `reason` y `orders.placed` sin cambio;
  - `PlayerNotQuotedException`: ningún contador cambia;
  - orden creada dentro de una transacción que luego se revierte (`TransactionTemplate` con `setRollbackOnly()`): `orders.placed` y `trades.*` sin cambio;
  - aislamiento (SC-003): en una clase aparte, `…/test/demo/service/market/OrderMetricsFailureTest.java`, con el mismo setup y un `@TestConfiguration` que declara un `@Primary BusinessMetrics` con otro nombre de bean (no hace falta habilitar bean overriding). Ese bean se construye sobre `FailingMeterRegistry` (como en T004). Verificar que una compra que cruza devuelve el mismo estado, cantidad ejecutada y trades persistidos que la misma compra con medición sana, incluido el `afterCommit`, y que una compra sin saldo sigue lanzando `InsufficientBalanceException`. Los services no agregan wrappers: la contención está en `BusinessMetrics` (T003).
- [X] T010 [P] [US1] En `…/test/demo/service/market/MarketMakerServiceTest.java`, agregar un caso: cuando el market maker publica su orden de venta, `gdmarket.orders.placed{side=SELL,type=LIMIT}` aumenta en la cantidad de órdenes publicadas.
- [X] T011 [US1] Crear `…/test/demo/config/BusinessMetricsPrometheusTest.java` (`@SpringBootTest`, `@AutoConfigureMockMvc` y el mismo `@TestPropertySource` y fixtures de API key que `…/test/demo/config/ActuatorMonitoringTest.java`). Casos:
  - con API key ADMIN, `/actuator/prometheus` responde 200 y contiene `# HELP gdmarket_orders_placed_total` con texto no vacío, `gdmarket_orders_placed_total{` con `application="demo"`, `side="BUY"` y `type="MARKET"`, y `gdmarket_users_registered`;
  - con API key USER responde 403 y sin credencial 401;
  - ninguna línea que empieza con `gdmarket_` contiene etiquetas fuera de `{application, side, type, reason, trigger, outcome, strategy, source, le}`.

**Checkpoint**: US1 completa; `OrderMetricsTest`, `OrderMetricsFailureTest`, `MarketMakerServiceTest`, `BusinessMetricsPrometheusTest` y las pruebas `OrderService*Test` existentes pasan.

---

## Phase 4: User Story 2 - Seguir los recálculos de cotizaciones (Priority: P2)

**Goal**: Duración del recálculo por disparador y resultado, cotizaciones por estrategia y cambios de estrategia activa.

**Independent Test**: Recálculo manual exitoso, uno programado, uno fallido y un cambio de estrategia; verificar las series (quickstart, pasos 3 y 6).

- [X] T012 [US2] En `…/demo/service/valuation/QuoteCalculationService.java`:
  - agregar `BusinessMetrics` al constructor;
  - agregar `public QuoteRunResult recalculate(Optional<String> strategyCode, RecalculationTrigger trigger)` con el cuerpo actual, y hacer que `recalculate(Optional<String>)` delegue con `RecalculationTrigger.MANUAL` (Javadoc de ambas);
  - pasar `trigger` a `execute`. En el camino exitoso, después de calcular `result`, llamar a `metrics.recalculationFinished(trigger, SUCCESS, Duration.ofNanos(System.nanoTime() - startedAt))` y, por cada `StrategyRunCounts c` de `result.calculated()`, a `metrics.quotesCalculated(c.code(), c.calculated())`. En el `catch`, antes de relanzar, llamar a `recalculationFinished(trigger, FAILURE, …)`.
  
  `RecalculationInProgressException` se lanza antes de `execute` y no mide. No cambiar el evento, los logs ni el manejo de MDC.
- [X] T013 [P] [US2] En `…/demo/scheduler/QuoteRecalculationScheduler.java`, reemplazar `calculationService.recalculate(Optional.empty())` por `calculationService.recalculate(Optional.empty(), RecalculationTrigger.SCHEDULED)`. Actualizar los stubs y `verify` de `…/test/demo/scheduler/QuoteRecalculationSchedulerTest.java` a la nueva firma, sin cambiar lo que verifican.
- [X] T014 [P] [US2] Crear `…/demo/service/valuation/ActiveStrategyMetricsListener.java` (`@Component`), con un método `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` sobre `ActiveStrategyChangedEvent` que llama a `metrics.activeStrategyChanged()`.
- [X] T015 [US2] Ajustar los constructores de `QuoteCalculationService` en `…/test/demo/service/valuation/QuoteCalculationServiceTest.java` (método de fábrica, ~línea 704) y en `…/test/demo/service/valuation/QuoteRunActiveSnapshotTest.java`, pasando un `BusinessMetrics` sobre `SimpleMeterRegistry` guardado en un campo. Agregar a `QuoteCalculationServiceTest` estos casos:
  - recálculo exitoso con `recalculate(Optional.empty())`: `gdmarket.quotes.recalculation{trigger=manual,outcome=success}` count 1, `gdmarket.quotes.recalculation.last` mayor que 0 y `gdmarket.quotes.calculated{strategy=<código>}` igual a `calculated` del resultado para cada estrategia;
  - `recalculate(Optional.empty(), SCHEDULED)` con falla del snapshot: `{scheduled,failure}` count 1, la misma `RecalculationFailedException` relanzada y sin cotizaciones contadas;
  - corrida concurrente rechazada con `RecalculationInProgressException`: ningún timer cambia.
- [X] T016 [P] [US2] Crear `…/test/demo/service/valuation/ActiveStrategyMetricsListenerTest.java` (`@SpringBootTest`, mismo setup que `ActiveStrategyRecalculationTest`). Casos:
  - `ActiveStrategyService.change` a otra estrategia: `gdmarket.strategy.changes` +1;
  - a la misma estrategia (devuelve `false`): sin cambio;
  - cambio dentro de una transacción revertida: sin cambio.

**Checkpoint**: US2 completa e independiente de US1; pasan `QuoteCalculationServiceTest`, `QuoteRunActiveSnapshotTest`, `QuoteRecalculationSchedulerTest` y `ActiveStrategyMetricsListenerTest`.

---

## Phase 5: User Story 3 - Vigilar las fuentes externas (Priority: P2)

**Goal**: Llamadas a Football-Data y WhoScored por resultado: éxito, error o caché.

**Independent Test**: Dos consultas a Football-Data dentro del TTL, errores simulados de cada fuente y una liga de WhoScored servida desde caché.

- [X] T017 [P] [US3] Crear `…/demo/metrics/MeteredCacheResolver.java` (`@Component("footballDataCacheResolver")`, implementa `CacheResolver`, recibe `CacheManager` y `BusinessMetrics`). `resolveCaches` resuelve los nombres de `context.getOperation().getCacheNames()` con el `CacheManager` y devuelve cada `Cache` envuelto en una clase privada `CountingCache implements Cache`:
  - delega todos los métodos;
  - en `get(Object key, Callable<T> valueLoader)` envuelve el loader con un flag; si `delegate.get(key, wrapped)` retorna sin ejecutarlo, llama a `metrics.externalCall(FOOTBALL_DATA, CACHE)`;
  - en `get(Object key)` y `get(Object key, Class<T>)` cuenta `CACHE` sólo cuando el valor no es nulo.
  
  No modificar `CacheConfig`.
- [X] T018 [US3] En `…/demo/adapter/footballdata/FootballDataAdapter.java`:
  - agregar `BusinessMetrics` al constructor;
  - cambiar la anotación a `@Cacheable(cacheNames = CacheConfig.COMPETITION_TEAMS_CACHE, cacheResolver = "footballDataCacheResolver", key = "#competitionCode", sync = true)`;
  - en `fetchCompetitionTeams`, llamar a `externalCall(FOOTBALL_DATA, SUCCESS)` antes del `return` y a `externalCall(FOOTBALL_DATA, ERROR)` en el `catch (RestClientException)`, antes de lanzar la misma `FootballDataException`;
  - agregar un segundo `catch (RuntimeException ex)` que llame a `externalCall(FOOTBALL_DATA, ERROR)` y relance `ex` sin envolverla (por ejemplo, una falla al deserializar que no sea `RestClientException`).
  
  Ajustar los constructores en `…/test/demo/adapter/footballdata/FootballDataAdapterTest.java` y `…/test/demo/security/FootballDataSecretLeakTest.java`.
- [X] T019 [P] [US3] En `…/demo/adapter/whoscored/WhoScoredAdapter.java`, agregar `BusinessMetrics` al constructor. En `fetchLeaguePlayers`, envolver el cuerpo para llamar a `externalCall(WHOSCORED, SUCCESS)` antes de devolver y a `externalCall(WHOSCORED, ERROR)` en `catch (RuntimeException e)` antes de relanzar exactamente la misma excepción. Así se cuentan `WhoScoredException` y también fallas inesperadas del cliente Playwright o del mapper. No contar reintentos internos ni `fetchOptional`. Ajustar el constructor en `…/test/demo/adapter/whoscored/WhoScoredAdapterTest.java`.
- [X] T020 [P] [US3] En `…/demo/service/PlayerStatsService.java`, agregar `BusinessMetrics` al constructor. En `updateLeague`, dentro de `if (pending.isEmpty())` y antes del `return`, llamar a `metrics.externalCall(ExternalSource.WHOSCORED, ExternalCallOutcome.CACHE)` sólo si `players` no estaba vacía. Ajustar los dos constructores en `…/test/demo/service/PlayerStatsServiceTest.java` (~líneas 63 y 678).
- [X] T021 [P] [US3] Crear `…/test/demo/metrics/MeteredCacheResolverTest.java` con `ConcurrentMapCacheManager`. Casos: primer `get(key, loader)` ejecuta el loader y no cuenta caché; segundo `get` no lo ejecuta y cuenta `{football_data,cache}` +1; los demás métodos delegan (`put`, `evict`, `clear`, `getName`).
- [X] T022 [US3] Agregar casos de métricas:
  - en `FootballDataAdapterTest`: éxito → `{football_data,success}` +1; error HTTP → `{football_data,error}` +1 con la misma `FootballDataException`; una `RuntimeException` que no sea `RestClientException` (por ejemplo, una respuesta que no se puede convertir y produce `IllegalStateException`, o un `RestClient` mockeado que la lanza) → `{football_data,error}` +1 y la misma excepción sin envolver;
  - en `WhoScoredAdapterTest`: liga completa → `{whoscored,success}` +1; falta de una respuesta obligatoria → `{whoscored,error}` +1, con un solo conteo aunque haya reintento por bloqueo; un `WhoScoredFeedClient` que lanza `IllegalStateException` → `{whoscored,error}` +1 y la misma excepción;
  - en `PlayerStatsServiceTest`, liga con todos los jugadores en caché → `{whoscored,cache}` +1 sin llamar al adapter.
- [X] T023 [US3] Crear `…/test/demo/adapter/footballdata/FootballDataCacheMetricsTest.java` con un contexto Spring mínimo (`@SpringJUnitConfig`, no `@SpringBootTest`). Motivo: el bean real `footballDataRestClient` de `…/demo/config/FootballDataConfig.java` apunta en tests a `http://127.0.0.1:1/v4`, no se puede reemplazar sin habilitar bean overriding, y un `@MockitoBean RestClient` exigiría stubs encadenados.
  - La `@Configuration` anidada declara `@EnableCaching`, `ConcurrentMapCacheManager(CacheConfig.COMPETITION_TEAMS_CACHE)`, `SimpleMeterRegistry`, `BusinessMetrics` (con `UserRepository` mockeado), `MeteredCacheResolver` con nombre `footballDataCacheResolver`, y `FootballDataAdapter` construido con un `RestClient` de un `RestClient.Builder` enlazado a `MockRestServiceServer` (como en `FootballDataAdapterTest`).
  - Caso: dos llamadas a `fetchCompetitionTeams("PL")` producen `{football_data,success}` +1 y `{football_data,cache}` +1. `server.expect(once(), ...)` y `server.verify()` confirman que hubo una sola solicitud HTTP.

**Checkpoint**: US3 completa; pasan `MeteredCacheResolverTest`, `FootballDataAdapterTest`, `FootballDataSecretLeakTest`, `FootballDataCacheMetricsTest`, `WhoScoredAdapterTest` y `PlayerStatsServiceTest`.

---

## Phase 6: User Story 4 - Medir registros, logins y usuarios (Priority: P3)

**Goal**: Registros y logins por resultado y cantidad de usuarios registrados.

**Independent Test**: Registro válido, registro duplicado, login correcto e incorrecto (quickstart, paso 2).

- [X] T024 [US4] En `…/demo/service/AuthService.java`, agregar `BusinessMetrics` al constructor.
  - En `register`: `registration(SUCCESS)` después del `save` exitoso, y `registration(FAILURE)` antes de cada `throw new DuplicateUserException(...)` (los dos `exists` y el `catch` de `DataIntegrityViolationException`).
  - En `login`: `login(FAILURE)` antes de `throw new InvalidCredentialsException(...)` y `login(SUCCESS)` antes del `return`. Mantener la comparación con `DUMMY_PASSWORD_HASH` sin cambios.
- [X] T025 [US4] En `…/test/demo/service/AuthServiceTest.java`, ajustar la fábrica (~línea 48) con un `BusinessMetrics` sobre `SimpleMeterRegistry` y el `userRepository` mock. Agregar casos:
  - registro válido → `registrations{success}` +1;
  - username y email duplicados → `{failure}` +1 cada uno, con la misma excepción;
  - login correcto → `logins{success}` +1;
  - usuario inexistente y contraseña incorrecta → `{failure}` +1;
  - el gauge `gdmarket.users.registered` devuelve el valor de `userRepository.count()` stubbeado;
  - aislamiento (SC-003): con un `BusinessMetrics` construido sobre `FailingMeterRegistry` (como en T004), `register` devuelve el mismo `RegisterResponse`, `login` el mismo token y el login inválido sigue lanzando `InvalidCredentialsException`.

**Checkpoint**: US4 completa; `AuthServiceTest` pasa.

---

## Phase 7: User Story 5 - Ver todo en un dashboard (Priority: P3)

**Goal**: Dashboard versionado y provisionado con las métricas nuevas y la latencia y los errores de la 035.

**Independent Test**: Levantar la demo y abrir **GD Market** sin importarlo a mano (quickstart, "Dashboard en Grafana").

- [X] T026 [P] [US5] Crear `observability/grafana/dashboards/gdmarket.json` con el formato de `observability/grafana/dashboards/backend-demo.json`: `uid` `gdmarket`, `title` `GD Market`, `schemaVersion` 41, `refresh` `15s` y datasource `{ "type": "prometheus", "uid": "prometheus" }` en cada panel. Cinco filas (Mercado, Cotizaciones, Fuentes externas, Usuarios, HTTP) con exactamente las consultas de la tabla "Consultas del dashboard" de `contracts/business-metrics.md`. Cada panel lleva `description` en español. `time.from` general es `now-30m`. Los paneles de la fila Cotizaciones (última duración, media, p95, recálculos por resultado, cotizaciones por estrategia y cambios de estrategia) fijan `"timeFrom": "7d"`. No usar `gdmarket_quotes_recalculation_seconds_max` en ningún panel: vuelve a 0 a los ~2 minutos (contrato, "Por qué 7 días"). El panel "Duración del último recálculo" usa `last_over_time((gdmarket_quotes_recalculation_last_seconds > 0)[7d:1m])` y no el valor actual del gauge, porque éste vuelve a 0 al reiniciar la aplicación. Validar que es JSON válido (`ConvertFrom-Json`). No modificar `backend-demo.json` ni el provisioning.
- [X] T027 [P] [US5] En `README.md`, sección `## Monitoreo`, agregar una subsección "Métricas de negocio" con la tabla de series del contrato (nombre en Prometheus, etiquetas y significado) o un enlace a `specs/036-metricas-avanzadas/contracts/business-metrics.md`. Incluir una línea indicando que `gdmarket_users_registered` hace un `count()` en cada scrape y cómo abrir el dashboard **GD Market** en la demo. No tocar otras secciones.

**Checkpoint**: `docker compose -f observability/docker-compose.yml config --quiet` sigue pasando y el JSON es válido.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T028 Ejecutar desde `backend/` `./mvnw.cmd -Dtest="BusinessMetricsTest,MeteredCacheResolverTest,BusinessMetricsPrometheusTest,OrderMetricsTest,OrderMetricsFailureTest,OrderService*Test,MarketMakerServiceTest,QuoteCalculationServiceTest,QuoteRunActiveSnapshotTest,QuoteRecalculationSchedulerTest,ActiveStrategyMetricsListenerTest,FootballDataAdapterTest,FootballDataSecretLeakTest,FootballDataCacheMetricsTest,WhoScoredAdapterTest,PlayerStatsServiceTest,AuthServiceTest,LayeredArchitectureTest,CodingRulesTest,NamingConventionTest" test` y luego la suite `./mvnw.cmd test -DexcludedGroups=e2e`, con el workaround de Windows de las reglas. Registrar los resultados en Notes.
- [X] T029 [P] Revisar con `grep -rn "io.micrometer" backend/src/main/java` que Micrometer sólo aparece en `…/demo/metrics/`, y con `grep -rn "gdmarket\." backend/src/main/java` que todos los nombres están en `BusinessMetrics` y coinciden con el contrato.
- [X] T030 [P] Revisar `git diff --stat main` contra la lista autorizada de la spec (SC-007) y confirmar que no cambiaron controllers, `CacheConfig`, eventos, `backend-demo.json`, el provisioning ni `application.properties`.
- [ ] T031 Marcar como tarea manual del usuario la verificación de `quickstart.md`, sección "Verificación manual: operar dos órdenes" y dashboard, porque usa la base local y Docker. Dejar los comandos listos sin ejecutarlos.
- [ ] T032 Gate manual final: después de que el usuario confirme los cambios, publique la rama y abra el PR, comprobar que `CI` termina en `SUCCESS` y que SonarCloud muestra Quality Gate `PASSED`. No marcar `[X]` antes de esa verificación.

---

## Dependencies & Execution Order

- **Setup (T001)** → **Foundational (T002–T005)** → historias.
- T003 depende de T002; T004 depende de T003; T005 es independiente.
- **US1 (T006–T011)**, **US2 (T012–T016)**, **US3 (T017–T023)** y **US4 (T024–T025)** dependen sólo de la fase 2 y son independientes entre sí. Comparten únicamente `BusinessMetrics`, que no cambia en estas fases.
- **US5 (T026–T027)** puede hacerse en paralelo con el código porque sólo usa los nombres del contrato. Para verlo con datos hacen falta US1 a US4.
- **Polish (T028–T032)** al final.

Dentro de cada historia:

- US1: T006 → T008 y T009 (T007 y T010 en paralelo); T011 después de T006.
- US2: T012 → T013 y T015; T014 → T016.
- US3: T017 → T018 → T023; T019 y T020 en paralelo; T021 después de T017; T022 después de T018–T020.
- US4: T024 → T025.

## Parallel Examples

```text
# Fase 2
T002 (enums) y T005 (ArchUnit) en paralelo; luego T003 → T004.

# US1
T006 OrderService  |  T007 MarketMakerService  |  T010 MarketMakerServiceTest
luego T008, T009 y T011

# US2
T012 QuoteCalculationService  |  T014 listener  |  T013 scheduler + test
luego T015 y T016

# US3
T017 MeteredCacheResolver  |  T019 WhoScoredAdapter  |  T020 PlayerStatsService
luego T018, T021, T022 y T023

# US5 en cualquier momento después de la fase 2
T026 dashboard  |  T027 README
```

## Implementation Strategy

### MVP (User Story 1)

1. T001–T005.
2. T006–T011: métricas de mercado visibles en `/actuator/prometheus`, con la prueba de contexto.
3. Validar con los pasos 4 y 5 del quickstart (usuario).

### Entrega incremental

1. US2: cotizaciones.
2. US3: fuentes externas.
3. US4: usuarios.
4. US5: dashboard y README.
5. T028–T030 automáticas; T031 y T032 manuales del usuario.

## Notes

- Implementación 2026-11-16:
  - **Nombre de la métrica de órdenes**: el cliente de Prometheus reserva el sufijo `_created` y exportaba `gdmarket.orders.created` como `gdmarket_orders_total`. Se renombró a `gdmarket.orders.placed` → `gdmarket_orders_placed_total` en código, pruebas, contrato, quickstart, README y research (R9). Lo detectó `BusinessMetricsPrometheusTest` (T011).
  - **Simulación de fallas (U4 del último análisis)**: se usa `FailingMeterRegistry` (`…/test/demo/metrics/`), una subclase de `SimpleMeterRegistry` cuyos contadores y timers lanzan al medir. La usan `BusinessMetricsTest`, `OrderMetricsFailureTest` y `AuthServiceTest`.
  - **T028**: suite de backend sin e2e en Windows (con `-Djdk.net.unixdomain.tmpdir=C:\udtmp`): 1002 casos, 0 fallas, 0 errores, 0 omitidos, `BUILD SUCCESS`. Las pruebas nuevas y modificadas de cada historia pasaron también por separado.
  - **T029**: `io.micrometer` sólo aparece en `…/demo/metrics/`, y los 13 nombres `gdmarket.` están en `BusinessMetrics` y coinciden con el contrato.
  - **T030**: los archivos previos modificados son sólo los autorizados en la spec. No cambiaron controllers, `CacheConfig`, eventos, `backend-demo.json`, el provisioning ni `application.properties`. `docker compose -f observability/docker-compose.yml config --quiet` sigue pasando y las 18 consultas de `gdmarket.json` coinciden exactamente con el contrato.
  - **T031 y T032**: quedan para el usuario (base local, Docker, PR, CI y SonarCloud).

- Revisión 2026-11-16 (`/speckit-analyze`): se agregaron el gauge `gdmarket.quotes.recalculation.last` y los paneles de cotizaciones a 7 días (U1); la falla de medición se simula con un registry que lanza al medir, y se prueba también en `OrderMetricsFailureTest` y `AuthServiceTest` (U2); los adapters cuentan `error` ante cualquier `RuntimeException` (C1); se alinearon las listas de pruebas (I1); se agregó el escenario de caché de WhoScored (I2); se corrigió la cantidad de series (I3); se decidió el contexto mínimo de T023 (A1); y se aclaró que `metrics` es un paquete de soporte (D1).

- Si un getter de `Trade` o el bean `footballDataRestClient` no coincide con lo supuesto, adaptar la tarea sin cambiar la clase existente y anotarlo aquí.
- `quotesCalculated` crea sus contadores al primer uso (research R9). Es la única serie que no aparece en 0 al arrancar.
- `$speckit-implement` no hace commit, push ni PR.
