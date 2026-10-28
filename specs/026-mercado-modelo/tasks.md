---

description: "Task list for feature 026: Modelo de datos del mercado"
---

# Tasks: Modelo de datos del mercado

**Input**: Design documents from `specs/026-mercado-modelo/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/market-model-contracts.md, quickstart.md

**Tests**: se piden explícitamente en el plan: `@DataJpaTest` para la inmutabilidad de `Trade` y `AuditLog`, la tenencia no negativa y el orden del libro; un test del inicializador idempotente; y un `@E2ETest` de la consulta del libro en PostgreSQL. Dentro de cada historia, los tests van antes de la implementación y tienen que fallar primero.

**Organization**: una fase por historia del spec (US1–US5). Las entidades que comparten varias historias (`AuditLog`, `Holding`) y el servicio de auditoría están en la fase Foundational.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: se puede hacer en paralelo (archivo distinto, sin dependencias pendientes)
- **[Story]**: historia del spec (US1–US5)

## Path Conventions

- Producción: `backend/src/main/java/com/example/demo/` (abreviado **main/**)
- Tests: `backend/src/test/java/com/example/demo/` (abreviado **test/**)
- Comandos Maven desde `backend/` con `.\mvnw.cmd`
- Tests de repositorio: `@DataJpaTest` + `@UnitTestProfile`, como `test/repository/PlayerQuoteRepositoryTest.java`
- **No se modifican** `main/model/User.java`, `main/repository/UserRepository.java`, `backend/src/main/resources/application.properties` ni `main/service/PlayerCatalogService.java` (FR-027). Tampoco `Player`, `PlayerRepository`, `QuoteCalculationService`, `E2EDatabaseCleaner` ni `E2ETest`. Si alguno pareciera necesario, frenar y consultar al equipo indicando archivo, cambio mínimo y alternativa.
- No se modifica ningún archivo existente, ni de producción ni de tests. Los tests de Entrega 1 corren sin superusuario gracias a `backend/src/test/resources/market.properties` (T021).
- Todo test nuevo de la 026 que necesite el superusuario o la emisión lleva `@TestPropertySource(properties = "market.superuser.enabled=true")` (junto con sus otras propiedades si las tiene).
- **Restricciones `CHECK`**: donde una tarea dice "`CHECK`", se declara con la API que decida T003.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: tener una línea de base verde antes de agregar nada.

- [X] T001 Correr `.\mvnw.cmd test` desde `backend/` y anotar el total de tests, fallas y salteados como línea de base. Tiene que terminar sin fallas; si falla, no seguir.
- [X] T002 Crear los paquetes vacíos `main/model/market`, `main/repository/market`, `main/service/market`, `main/service/audit` y sus espejos en `test/` (research D1).
- [X] T003 Verificar en el jar de Hibernate del proyecto (`.\mvnw.cmd dependency:tree "-Dincludes=org.hibernate.orm"` y el javadoc o los fuentes de esa versión) si `@org.hibernate.annotations.Check` está deprecado (research D2). Si lo está, todas las restricciones `CHECK` de esta feature se declaran con `@Table(check = @CheckConstraint(name = "ck_<tabla>_<regla>", constraint = "..."))` de `jakarta.persistence`; si no, con `@Check(constraints = "...")`. Anotar la decisión en el PR.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: los enums, la auditoría y la tenencia, que usan varias historias.

**⚠️ CRITICAL**: ninguna historia empieza hasta terminar esta fase.

### Enums

- [X] T004 [P] Crear `OrderSide { BUY, SELL }` en `main/model/market/OrderSide.java`.
- [X] T005 [P] Crear `OrderType { MARKET, LIMIT }` en `main/model/market/OrderType.java`.
- [X] T006 [P] Crear `OrderStatus { OPEN, PARTIALLY_FILLED, FILLED, CANCELLED }` en `main/model/market/OrderStatus.java`. Debe exponer `public static final Set<OrderStatus> ACTIVE = EnumSet.of(OPEN, PARTIALLY_FILLED)` (inmodificable) y `boolean isActive()`.
- [X] T007 [P] Crear `AuditActorType { USER, SYSTEM }` en `main/model/market/AuditActorType.java` y `AuditAction { SUPERUSER_CREATED, TOKENS_ISSUED }` en `main/model/market/AuditAction.java`. Agregar un javadoc que diga que las features siguientes agregan `ORDER_CREATED`, `ORDER_FILLED`, `ORDER_CANCELLED`, `TRADE_EXECUTED` y `BALANCE_CHANGED`.
- [X] T008 [P] Crear el record `AuditActor(AuditActorType type, Long userId)` en `main/model/market/AuditActor.java`:
  - fábricas `system()` (userId nulo) y `user(Long id)`;
  - el constructor valida `USER ⇒ userId != null` y `SYSTEM ⇒ userId == null`, y lanza `IllegalArgumentException` si no se cumple.

### Auditoría

- [X] T009 Crear la entidad `AuditLog` en `main/model/market/AuditLog.java`, tabla `audit_logs` (data-model §AuditLog, research D6):
  - Anotaciones `@Entity`, `@Immutable`, `@Getter`, constructor protegido sin argumentos y sin setters, como en `PlayerQuote`.
  - Columnas, todas con `updatable = false`:
    - `id` bigint identity;
    - `actor_type` varchar(16) `@Enumerated(STRING)` obligatorio;
    - `actor_user_id` bigint nulo (sin FK);
    - `action` varchar(32) `@Enumerated(STRING)` obligatorio;
    - `entity_type` varchar(32) obligatorio;
    - `entity_id` varchar(64) obligatorio;
    - `before_state` String con `@JdbcTypeCode(SqlTypes.JSON)`, nulo;
    - `after_state` String con `@JdbcTypeCode(SqlTypes.JSON)`, obligatorio;
    - `occurred_at` `Instant` obligatorio;
    - `correlation_id` varchar(36) obligatorio.
  - Índices `idx_audit_logs_entity (entity_type, entity_id)` e `idx_audit_logs_occurred (occurred_at)`.
  - `CHECK`: `(actor_type = 'SYSTEM' AND actor_user_id IS NULL) OR (actor_type = 'USER' AND actor_user_id IS NOT NULL)`.
  - Fábrica estática `AuditLog.of(AuditActor, AuditAction, AuditTarget(String type, String id), String beforeJson, String afterJson, Instant occurredAt, String correlationId)`, que valida los obligatorios con `IllegalArgumentException`.
  - `@PreRemove void preventRemove()`, que lanza `UnsupportedOperationException("audit_log_is_immutable")`.
  - Javadoc que explique por qué hace falta `@PreRemove`: Hibernate permite borrar entidades `@Immutable`. Debe decir también que los `DELETE` JPQL masivos y el SQL nativo quedan fuera de la garantía.
- [X] T010 Crear `AuditLogRepository extends Repository<AuditLog, Long>` en `main/repository/market/AuditLogRepository.java`, con los métodos de contracts §AuditLogRepository: `save`, `findById`, `findByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc` y `findByActionOrderByIdAsc`. Sin ningún método `delete*`. Javadoc como el de `PlayerQuoteRepository`.
- [X] T011 Crear `TransactionAuditService` en `main/service/audit/TransactionAuditService.java` (contracts §TransactionAuditService, research D6):
  - Firma: `@Transactional(propagation = MANDATORY) AuditLog audit(AuditActor actor, AuditAction action, String entityType, Object entityId, Object before, Object after)`.
  - Serializa `before` y `after` con el `tools.jackson.databind.json.JsonMapper` inyectado. `before == null` se guarda como columna nula; `after == null` lanza `IllegalArgumentException`.
  - `entityId` se guarda con `String.valueOf`.
  - El correlation id sale de `MDC.get(CorrelationIdFilter.MDC_KEY)` o, si es nulo o vacío, de `UUID.randomUUID()`.
  - `occurredAt = Instant.now(clock)`.
- [X] T012 [P] Test `test/service/audit/TransactionAuditServiceTest.java` (`@DataJpaTest` + `@UnitTestProfile` + `@Import(TransactionAuditService.class)`, con un `Clock.fixed` y un `JsonMapper` de test). Casos:
  - (a) Guarda todos los campos.
  - (b) Toma el correlation id del MDC.
  - (c) Sin MDC genera un UUID válido.
  - (d) `before` nulo se guarda nulo y el JSON de `after` se puede leer de vuelta como objeto, no como texto JSON entre comillas. Si en H2 se lee como texto entre comillas, ajustar sólo la lectura en el perfil de test (research D6).
  - (e) Llamado sin transacción (`@Transactional(propagation = NOT_SUPPORTED)` en el test) lanza `IllegalTransactionStateException` y no guarda nada.
  - (f) `after` nulo lanza `IllegalArgumentException`.

### Tenencia

- [X] T013 Crear la entidad `Holding` en `main/model/market/Holding.java`, tabla `holdings` (data-model §Holding, research D2):
  - Columnas:
    - `id` bigint identity;
    - `user_id` FK `users`, `@ManyToOne(fetch = LAZY, optional = false)`, `updatable = false`;
    - `player_id` FK `players`, igual;
    - `quantity` int `@PositiveOrZero`;
    - `reserved_quantity` int `@PositiveOrZero`;
    - `version` bigint `@Version`;
    - `created_at` `Instant`, `updatable = false`;
    - `updated_at` `Instant`.
  - `@Table(uniqueConstraints = @UniqueConstraint(name = "uk_holdings_user_player", columnNames = {"user_id", "player_id"}), indexes = @Index(name = "idx_holdings_user", columnList = "user_id"))`.
  - `CHECK`: `quantity >= 0 AND reserved_quantity >= 0 AND reserved_quantity <= quantity`.
  - Sin setters.
  - Operaciones `issue`, `credit`, `reserve`, `release`, `consumeReserved` y `debit`, cada una con `Instant now`, con las reglas exactas de data-model. Todas lanzan `IllegalStateException` sin cambiar nada si violarían una regla, e `IllegalArgumentException` si `n <= 0`.
  - `int available()`.
- [X] T014 Crear `HoldingRepository extends Repository<Holding, Long>` en `main/repository/market/HoldingRepository.java`, con los métodos de contracts §HoldingRepository:
  - `save`, `findByUserIdAndPlayerId` y `findByUserIdOrderByPlayerIdAsc`;
  - `findForUpdate`, con `@Lock(PESSIMISTIC_WRITE)` y `@Query`;
  - `findPlayerIdsWithoutHolding(Long userId)`: `select p.id from Player p where not exists (select 1 from Holding h where h.player = p and h.user.id = :userId) order by p.id`;
  - `sumQuantityByPlayerId`, con `coalesce(sum(h.quantity), 0)`.
- [X] T015 [P] Crear los records `HoldingSnapshot(Long id, Long userId, Long playerId, int quantity, int reservedQuantity)` con `static HoldingSnapshot of(Holding)` y `UserSnapshot(Long id, String username, String email, Role role, BigDecimal balance)` con `static UserSnapshot of(User)`, en `main/service/market/HoldingSnapshot.java` y `main/service/market/UserSnapshot.java`. `UserSnapshot` **no** incluye `passwordHash` ni la API key (FR-024).

**Checkpoint**: `.\mvnw.cmd "-Dtest=TransactionAuditServiceTest" test` pasa y el contexto de Spring arranca con las tablas `audit_logs` y `holdings`.

---

## Phase 3: User Story 1 - El superusuario existe y es único (Priority: P1) 🎯 MVP

**Goal**: al arrancar existe exactamente un superusuario ADMIN, configurable, y su creación queda auditada.

**Independent Test**: ejecutar la inicialización sobre una base vacía, repetirla 5 veces, usar una configuración distinta de la por defecto y provocar los conflictos de username y email.

### Tests for User Story 1 ⚠️

- [X] T016 [P] [US1] Test `test/service/market/SuperuserInitializerTest.java` (`@SpringBootTest` + `@UnitTestProfile` + `@TestPropertySource(properties = "market.superuser.enabled=true")`, resto de la configuración por defecto):
  - **Limpieza antes y después de cada caso** (`@BeforeEach` y `@AfterEach`), con `JdbcTemplate` y en este orden: `holdings` → `audit_logs` → `api_keys` → `users`. La de `@AfterEach` evita dejar superusuario, `holdings` o `audit_logs` a los demás tests, que comparten la misma base H2. No usar `@DirtiesContext`. Después de limpiar en `@BeforeEach`, cada caso ejecuta la inicialización con `initializer.run(null)`; no depende de lo que creó el arranque del contexto.
  - Casos:
    - (a) Después de `run(null)` existe un único usuario `superuser`, rol ADMIN, email `superuser@futbol-market.local`, y `passwordEncoder.matches("Superuser.2026", hash)` es verdadero (US1-1).
    - (b) Ejecutar `run(null)` 5 veces más sigue dejando un único usuario y no cambia ni el hash ni el email (US1-3, SC-001).
    - (c) Hay exactamente un `AuditLog` `SUPERUSER_CREATED` con actor `SYSTEM`, `before_state` nulo y un `after_state` que no contiene `passwordHash`, `password_hash` ni el hash real (US1-4, SC-007).
    - (d) Un usuario `superuser` existente con rol USER hace que `run(null)` lance `IllegalStateException("superuser_username_taken")` y su rol siga siendo USER (US1-5).
    - (e) Un email configurado que ya usa otro usuario lanza `superuser_email_taken` y no se crea nada.
    - (f) Con la contraseña por defecto, `run(null)` loguea un `WARN` `market_superuser_default_password` que no contiene la contraseña. Capturarlo con `OutputCaptureExtension` / `CapturedOutput`.
- [X] T017 [P] [US1] Test `test/service/market/SuperuserInitializerDisabledTest.java` (`@SpringBootTest` + `@UnitTestProfile`, **sin** `@TestPropertySource`, así que toma `market.superuser.enabled=false` de `src/test/resources/market.properties`). Limpia **antes** de cada caso con el orden de T016, para no ver un superusuario que haya quedado de otro test. Casos:
  - `run(null)` loguea `market_superuser_disabled`, no crea el usuario `superuser` y no crea tenencias.
  - Publicar un `QuoteRunCompletedEvent` hace que el listener loguee `superuser_missing` sin lanzar.
- [X] T018 [P] [US1] Test `test/service/market/SuperuserInitializerCustomConfigTest.java`, en una clase aparte para tener su propio contexto: `@SpringBootTest` + `@UnitTestProfile` + `@TestPropertySource(properties = {"market.superuser.enabled=true", "market.superuser.username=mm", "market.superuser.email=mm@local", "market.superuser.password=Secret.123"})`, con la misma limpieza antes y después de cada caso que T016. Casos:
  - `run(null)` crea `mm` con esa contraseña (US1-2).
  - No se loguea el `WARN` de contraseña por defecto.

### Implementation for User Story 1

- [X] T019 [US1] Crear `backend/src/main/resources/market.properties` con `market.superuser.enabled=true`, `backend/src/test/resources/market.properties` con `market.superuser.enabled=false`, y `main/config/MarketConfig.java`: un `@Configuration` con `@PropertySource("classpath:market.properties")`. Cada archivo lleva un comentario de una línea que explique qué hace (el de test: "pisa al de main en los tests; los tests de 026 lo habilitan con @TestPropertySource"). Research D11.
- [X] T020 [US1] Crear `SuperuserAccount` en `main/service/market/SuperuserAccount.java`. Es un `@Component` que recibe `@Value("${market.superuser.username:superuser}")`, `@Value("${market.superuser.email:superuser@futbol-market.local}")` y `@Value("${market.superuser.password}")` y `@Value("${market.superuser.default-password}")`, y expone:
  - `username()`, `email()` y `password()`;
  - `boolean usesDefaultPassword()`;
  - `Long id()`, que busca con `UserRepository.findByUsername` y lanza `IllegalStateException("superuser_missing")` si no existe.

  La contraseña por defecto **no** va en el código (Sonar `java:S2068`): vive en `market.properties` (main y test) como `market.superuser.default-password=Superuser.2026` y `market.superuser.password=${market.superuser.default-password}`, con un comentario que diga que es para desarrollo y demo (research D11). El mensaje de error va en la constante `public static final String MISSING = "superuser_missing"`, y `id()` lanza `new IllegalStateException(MISSING)`. **No** agregar claves a `application.properties`.
- [X] T021 [US1] Crear `SuperuserInitializer implements ApplicationRunner` en `main/service/market/SuperuserInitializer.java` (research D11, contracts §SuperuserInitializer):
  - **Sin** `@ConditionalOnProperty` (research D11): recibe `@Value("${market.superuser.enabled:true}") boolean enabled`. Si es `false`, `run(...)` loguea `market_superuser_disabled` en `info` y termina sin hacer nada más.
  - `public User ensureSuperuser()` **no** es `@Transactional`: requiere una transacción activa. Sigue el flujo de D11:
    - Busca por username. Si existe con ADMIN, lo devuelve sin cambios. Si existe sin ADMIN, lanza `superuser_username_taken`.
    - Si no existe y `existsByEmail` da verdadero, lanza `superuser_email_taken`.
    - Si no, crea el usuario con `User.builder()`, `passwordEncoder.encode(...)`, `role(Role.ADMIN)` y el saldo por defecto del builder; guarda con `UserRepository.save`; y audita con `TransactionAuditService.audit(AuditActor.system(), SUPERUSER_CREATED, "USER", id, null, UserSnapshot.of(user))`.
  - `run(...)`:
    1. Ejecuta `ensureSuperuser()` dentro de `transactionOperations.execute(status -> ensureSuperuser())`, con el mismo patrón que `PlayerStatsService`.
    2. Ante una `DataIntegrityViolationException` (atrapada fuera de esa transacción, que ya se revirtió), relee el usuario en una transacción nueva. Si es ADMIN, continúa; si no, relanza.
    3. Si `superuserAccount.usesDefaultPassword()`, loguea en `warn` `market_superuser_default_password username=...`, sin la contraseña.
    4. Loguea `market_superuser_ready id=... created=...`.
    5. En US2, llama a la emisión (T030).
  - Después, correr `.\mvnw.cmd test` completo y comparar con T001. Con `src/test/resources/market.properties` en `false`, los tests existentes no tienen superusuario ni emisión. Si alguno falla igual, **no** modificarlo: frenar y revisar `MarketConfig` y el orden del classpath (`test-classes` antes que `classes`).
- [X] T022 [US1] Correr `.\mvnw.cmd "-Dtest=SuperuserInitializerTest,SuperuserInitializerCustomConfigTest,SuperuserInitializerDisabledTest" test` hasta que pase.

**Checkpoint**: el superusuario existe, es único y está auditado.

---

## Phase 4: User Story 2 - Emisión de los tokens de cada jugador (Priority: P1)

**Goal**: cada jugador tiene sus 100 tokens emitidos una sola vez a nombre del superusuario: al arrancar y, para los que se agreguen después, al terminar cada recálculo de cotizaciones. `PlayerCatalogService` no se modifica.

**Independent Test**: arrancar con N jugadores, agregar jugadores, publicar `QuoteRunCompletedEvent`, repetir, vender tokens del superusuario y volver a emitir; contar tenencias y sumas.

### Tests for User Story 2 ⚠️

- [X] T023 [P] [US2] Test `test/service/market/TokenIssuanceServiceTest.java` (`@SpringBootTest` + `@UnitTestProfile` + `@TestPropertySource(properties = "market.superuser.enabled=true")`, con la limpieza de T016 antes y después de cada caso, y `initializer.run(null)` después de la limpieza de `@BeforeEach`). **No borra `players`**: cada caso crea sus propios jugadores con `externalId` únicos (por ejemplo `"026-" + UUID.randomUUID()`) y sólo verifica esos jugadores. Casos:
  - (a) Con 3 jugadores nuevos, `issueMissing()` devuelve `issued = 3` (si había jugadores previos sin emitir, se toma `issued >= 3` y se verifican los 3 creados) y cada jugador tiene una tenencia del superusuario con `quantity = 100`, `reservedQuantity = 0` (US2-1).
  - (b) Una segunda llamada devuelve `issued = 0` y `sumQuantityByPlayerId` sigue en 100 (US2-3).
  - (c) Debitar 100 de la tenencia del superusuario en un jugador y volver a emitir no repone nada: queda en 0 (US2-4, FR-007).
  - (d) Hay un `AuditLog` `TOKENS_ISSUED` por jugador emitido, con actor SYSTEM, `before_state` nulo y un `after_state` con `quantity: 100` (US2-5).
  - (e) Si la emisión de un jugador falla (con un `@MockitoSpyBean` de `HoldingRepository` que lanza en el segundo `save`), los otros quedan emitidos, el resultado informa `failed = 1` y una nueva llamada emite el pendiente (US2-6, FR-009).
  - (f) Sin superusuario lanza `superuser_missing`.
- [X] T024 [P] [US2] Test `test/service/market/TokenIssuanceListenerTest.java` (unitario con Mockito):
  - Al recibir `QuoteRunCompletedEvent` llama una vez a `TokenIssuanceService.issueMissing()` (US2-2, SC-002).
  - Si `issueMissing()` lanza, el listener loguea y no propaga la excepción (FR-009).
  - Si lanza `IllegalStateException(SuperuserAccount.MISSING)`, sólo loguea `superuser_missing`, sin `warn`.
- [X] T025 [P] [US2] Agregar a `SuperuserInitializerTest` (T016) el caso (g): con 2 jugadores cargados antes de `run(null)`, después los 2 tienen 100 tokens del superusuario, y ejecutar `run(null)` otra vez no cambia nada. La limpieza `@AfterEach` de T016 se encarga de no dejar esas tenencias ni sus auditorías.

### Implementation for User Story 2

- [X] T026 [US2] Crear el record `IssuanceResult(int issued, int failed)` en `main/service/market/IssuanceResult.java`.
- [X] T027 [US2] Crear `TokenIssuanceService` en `main/service/market/TokenIssuanceService.java` (research D12):
  - Constante `public static final int TOKENS_PER_PLAYER = 100`.
  - `IssuanceResult issueMissing()`:
    1. Resuelve `superuserId` con `SuperuserAccount.id()`.
    2. Obtiene `findPlayerIdsWithoutHolding(superuserId)`.
    3. Emite cada jugador en `transactionOperations.executeWithoutResult(...)`: carga user y player con `EntityManager.getReference`, crea `Holding.issue(user, player, 100, Instant.now(clock))`, guarda y audita `TOKENS_ISSUED` (`"HOLDING"`, id, `null`, `HoldingSnapshot.of(h)`).
  - `DataIntegrityViolationException` se loguea en `debug` como ya emitido y no cuenta como falla. Cualquier otra `RuntimeException` se loguea en `warn` con `market_issuance_failed playerId=... code=...` y suma a `failed`.
  - Al final loguea `market_issuance_completed issued=... failed=...`.
- [X] T028 [US2] Crear `TokenIssuanceListener` en `main/service/market/TokenIssuanceListener.java` (research D13): un `@Component` con `@EventListener void onQuoteRunCompleted(QuoteRunCompletedEvent event)` que llama a `issueMissing()`.
  - Atrapa `RuntimeException` y no la propaga, para no cambiar el resultado del recálculo. Si es una `IllegalStateException` cuyo mensaje es igual a `SuperuserAccount.MISSING` (superusuario deshabilitado), sólo loguea `market_issuance_skipped reason=superuser_missing runId=...` en `info`. Cualquier otra se loguea como `market_issuance_after_quote_run_failed runId=...` en `warn`.
  - Javadoc con las consecuencias de D13: corre de forma sincrónica mientras la guardia de corrida sigue tomada, así que la primera emisión después de una sincronización grande alarga el recálculo; y las auditorías heredan el correlation id de la corrida.
- [X] T029 [US2] Confirmar con `git diff --stat main -- backend/src/main/java/com/example/demo/service/PlayerCatalogService.java backend/src/main/java/com/example/demo/service/valuation/QuoteCalculationService.java` que ninguno de los dos cambió.
- [X] T030 [US2] En `SuperuserInitializer.run(...)` (T021), llamar a `tokenIssuanceService.issueMissing()` después de `ensureSuperuser()`, fuera de la transacción del superusuario.
- [X] T031 [US2] Correr `.\mvnw.cmd "-Dtest=TokenIssuanceServiceTest,TokenIssuanceListenerTest,SuperuserInitializerTest" test` hasta que pase.

**Checkpoint**: todos los jugadores tienen 100 tokens emitidos, una sola vez; los nuevos, al terminar el siguiente recálculo.

---

## Phase 5: User Story 3 - Tenencias, órdenes y reservas consistentes (Priority: P1)

**Goal**: tenencias que nunca quedan negativas, órdenes coherentes y créditos reservados calculables, más el acceso al saldo sin tocar `UserRepository`.

**Independent Test**: guardar tenencias y órdenes en todos los estados y verificar invariantes, disponibles y reservas.

### Tests for User Story 3 ⚠️

- [X] T032 [P] [US3] Test unitario `test/model/market/HoldingTest.java`. Para cada operación de T013: el caso válido, el caso que violaría la regla (lanza `IllegalStateException` y no cambia `quantity` ni `reservedQuantity`) y `n <= 0` (lanza `IllegalArgumentException`). Incluir el caso US3-1: 10 tokens con 4 reservados tienen 6 disponibles.
- [X] T033 [P] [US3] Test unitario `test/model/market/MarketOrderTest.java`:
  - Las fábricas `limit` y `market` arrancan en `OPEN` con `filledQuantity = 0` y `closedAt` nulo.
  - `limit` normaliza el precio a escala 4 y rechaza precio nulo, cero o negativo; las dos fábricas rechazan cantidad ≤ 0.
  - `fill` parcial deja `PARTIALLY_FILLED`; `fill` del resto deja `FILLED` con `closedAt`; `fill` de más que `remaining()` lanza.
  - `cancel` desde OPEN o PARTIALLY_FILLED deja `CANCELLED` con `closedAt` y conserva `filledQuantity`.
  - `fill` o `cancel` desde FILLED o CANCELLED lanza `IllegalStateException` (US3-7, US3-8).
  - `reservedCredits()`: 10 a 1.0500 con 4 ejecutados da 6.3000; MARKET, SELL o terminal da 0.
- [X] T034 [P] [US3] Test `test/repository/market/HoldingRepositoryTest.java` (`@DataJpaTest` + `@UnitTestProfile`):
  - (a) Una segunda tenencia del mismo usuario y jugador viola la unicidad al hacer flush (US3-3).
  - (b) Un `UPDATE holdings SET quantity = -1` nativo, y otro con `reserved_quantity` mayor que `quantity`, fallan por el `CHECK` (SC-004).
  - (c) `findPlayerIdsWithoutHolding` devuelve sólo los jugadores sin tenencia del usuario.
  - (d) `sumQuantityByPlayerId` suma entre usuarios y da 0 sin tenencias.
  - (e) Dos actualizaciones con la misma versión producen `ObjectOptimisticLockingFailureException`, o se verifica que `version` se incrementa.
  - (f) El repositorio no tiene métodos `delete*` (por reflexión, como `PlayerQuoteRepositoryTest.repositoryOffersNoDeleteOperation`).
- [X] T035 [P] [US3] Test `test/repository/market/MarketOrderRepositoryTest.java` (`@DataJpaTest` + `@UnitTestProfile`):
  - (a) `sumReservedCredits`: con BUY LIMIT OPEN 10 a 1.0500 con 4 ejecutados (6.3000), BUY LIMIT PARTIALLY_FILLED 5 a 2.0000 con 2 ejecutados (6.0000), BUY LIMIT FILLED, BUY LIMIT CANCELLED, BUY MARKET y SELL LIMIT OPEN, da `12.3000` (US3-4, US3-5, SC-005).
  - (b) Un usuario sin órdenes da `0` (US3-6).
  - (c) Un `INSERT` nativo de una MARKET con `limit_price` o de una LIMIT sin precio falla por el `CHECK`.
  - (d) `findByUserIdOrderByCreatedAtDescIdDesc` pagina de la más nueva a la más vieja.

  El orden del libro va en US5 (T050).
- [X] T036 [P] [US3] Test `test/repository/market/MarketUserRepositoryTest.java` (`@DataJpaTest` + `@UnitTestProfile`):
  - `findBalanceById` devuelve 1000.00 para un usuario recién creado.
  - `addToBalance(id, 25.50)` devuelve 1 y deja 1025.50; `addToBalance` sobre un id inexistente devuelve 0.
  - `findLockedById` devuelve el usuario.
  - Un caso documenta el riesgo de D8: `addToBalance(id, 3.1311)` deja el saldo redondeado a 2 decimales. Comentar en el test que es el comportamiento actual de `users.balance` (`numeric(38,2)`) y que el motor de calce lo tiene que resolver con el equipo.

### Implementation for User Story 3

- [X] T037 [US3] Crear la entidad `MarketOrder` en `main/model/market/MarketOrder.java`, tabla `orders` (data-model §MarketOrder, research D3):
  - Columnas:
    - `id` bigint identity;
    - `user_id` y `player_id` FK, `@ManyToOne(fetch = LAZY, optional = false)`, `updatable = false`;
    - `side` y `type` varchar(16) `@Enumerated(STRING)`, `updatable = false`;
    - `limit_price` numeric(12,4), nulo;
    - `quantity` int, `updatable = false`;
    - `filled_quantity` int;
    - `status` varchar(16) `@Enumerated(STRING)`;
    - `created_at` `Instant`, `updatable = false`;
    - `updated_at` `Instant`;
    - `closed_at` `Instant`, nulo.
  - Índices `idx_orders_book (player_id, side, status, limit_price, created_at)` e `idx_orders_user_created (user_id, created_at)`.
  - `CHECK`: `quantity > 0 AND filled_quantity >= 0 AND filled_quantity <= quantity AND ((type = 'LIMIT' AND limit_price IS NOT NULL AND limit_price > 0) OR (type = 'MARKET' AND limit_price IS NULL))`.
  - Fábricas `limit(...)` y `market(...)`; transiciones `fill(int, Instant)` y `cancel(Instant)`; `remaining()` y `BigDecimal reservedCredits()`. Todo según data-model §Transiciones, sin setters.
- [X] T038 [US3] Crear `MarketOrderRepository extends Repository<MarketOrder, Long>` en `main/repository/market/MarketOrderRepository.java`, con todos los métodos de contracts §MarketOrderRepository:
  - Las consultas del libro con `@Query` JPQL: filtro `o.player.id = :playerId and o.side = :side and o.type = LIMIT and o.status in :active`, orden `limitPrice desc|asc, createdAt asc, id asc`. Las variantes `ForUpdate` llevan `@Lock(PESSIMISTIC_WRITE)`.
  - Exponer `findOpenBuys`, `findOpenSells`, `findOpenBuysForUpdate` y `findOpenSellsForUpdate` como métodos `default` que pasan `OrderStatus.ACTIVE` y el lado a dos consultas parametrizadas, para no repetir la JPQL.
  - `sumReservedCredits`: `select coalesce(sum((o.quantity - o.filledQuantity) * o.limitPrice), 0) from MarketOrder o where o.user.id = :userId and o.side = BUY and o.type = LIMIT and o.status in :active`. El método `default` devuelve `setScale(4)`.
- [X] T039 [US3] Crear `MarketUserRepository extends Repository<User, Long>` en `main/repository/market/MarketUserRepository.java` (research D8, FR-028):
  - `@Lock(PESSIMISTIC_WRITE) @Query("select u from User u where u.id = :id") Optional<User> findLockedById(Long id)`;
  - `@Query("select u.balance from User u where u.id = :id") Optional<BigDecimal> findBalanceById(Long id)`;
  - `@Modifying(clearAutomatically = true, flushAutomatically = true) @Query("update User u set u.balance = u.balance + :delta where u.id = :id") int addToBalance(Long id, BigDecimal delta)`.
  - Javadoc de la interfaz: existe para no modificar `UserRepository` (archivo protegido de Entrega 1).
  - Javadoc de `addToBalance`, con la regla de FR-028: sólo la usa el motor de calce, con el usuario bloqueado por `findLockedById`, y cada llamada se audita como `BALANCE_CHANGED` en la misma transacción. No valida saldo negativo.
- [X] T040 [US3] Correr `.\mvnw.cmd "-Dtest=HoldingTest,MarketOrderTest,HoldingRepositoryTest,MarketOrderRepositoryTest,MarketUserRepositoryTest" test` hasta que pase.

**Checkpoint**: tenencias y órdenes con todas sus invariantes, reservas calculables y saldo accesible.

---

## Phase 6: User Story 4 - Operaciones ejecutadas y auditoría inmutables (Priority: P1)

**Goal**: las operaciones y la auditoría no se pueden modificar ni borrar desde la aplicación, y se consultan por usuario.

**Independent Test**: registrar operaciones y entradas de auditoría, intentar modificarlas (por entidad y por JPQL masivo) y borrarlas por entidad, y consultarlas.

### Tests for User Story 4 ⚠️

- [X] T041 [P] [US4] Test unitario `test/model/market/TradeTest.java`. `Trade.of`:
  - con 3 × 1.0437 calcula total `3.1311` con escala 4 (US4-1);
  - toma jugador, comprador y vendedor de las órdenes;
  - rechaza con `IllegalArgumentException`: comprador = vendedor, jugadores distintos, lados invertidos, cantidad ≤ 0 y precio ≤ 0 (US4-3).
- [X] T042 [P] [US4] Test unitario `test/model/market/AuditLogTest.java`: `AuditLog.of` rechaza obligatorios nulos (`action`, `entityType`, `entityId`, `afterJson`, `occurredAt`, `correlationId`).
- [X] T043 [P] [US4] Test `test/config/ImmutableEntityConfigTest.java` (unitario): el customizer deja `hibernate.query.immutable_entity_update_query_handling_mode` en `exception` en el mapa de propiedades.
- [X] T044 [P] [US4] Test `test/repository/market/TradeRepositoryTest.java` (`@DataJpaTest` + `@UnitTestProfile` + `@Import(ImmutableEntityConfig.class)`, si `@DataJpaTest` no lo carga solo):
  - (a) Guardar y releer conserva todos los campos.
  - (b) **Inmutabilidad ante modificación**: cambiar `price` por reflexión (`ReflectionTestUtils.setField`), hacer flush y clear, y releer devuelve el precio original (US4-2).
  - (c) Un `update Trade t set t.price = ...` JPQL lanza excepción y la fila no cambia (C1).
  - (d) `entityManager.remove(trade)` lanza `UnsupportedOperationException` (envuelta o no) y la fila sigue existiendo.
  - (e) El repositorio no tiene métodos `delete*`.
  - (f) `findByUser` devuelve las operaciones como comprador y como vendedor, de la más nueva a la más vieja (US4-6).
  - (g) Un `INSERT` nativo con `buyer_id = seller_id` falla por el `CHECK`.
- [X] T045 [P] [US4] Test `test/repository/market/AuditLogRepositoryTest.java` (mismo setup que T044):
  - Los mismos casos (b), (c), (d) y (e) de T044 aplicados a `AuditLog` (US4-5, SC-003).
  - `findByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc` devuelve el historial en orden.
  - Un `INSERT` nativo con `actor_type = 'USER'` y `actor_user_id` nulo falla por el `CHECK`.

### Implementation for User Story 4

- [X] T046 [US4] Crear `ImmutableEntityConfig` en `main/config/ImmutableEntityConfig.java` (research D5, contracts §Configuración): un `@Configuration` con un bean `HibernatePropertiesCustomizer` que hace `properties.put("hibernate.query.immutable_entity_update_query_handling_mode", "exception")`. Javadoc que diga que se configura en código para no tocar `application.properties`, que es global (también protege a `PlayerQuote` y `PlayerStatsSnapshot`), y que los `DELETE` JPQL masivos y el SQL nativo quedan fuera de la garantía.
- [X] T047 [US4] Crear la entidad `Trade` en `main/model/market/Trade.java`, tabla `trades` (data-model §Trade, research D5):
  - Anotaciones `@Entity`, `@Immutable`, todas las columnas `updatable = false`, sin setters.
  - Columnas:
    - `id` bigint identity;
    - `player_id`, `buyer_id`, `seller_id`, `buy_order_id` y `sell_order_id` como FK `@ManyToOne(fetch = LAZY, optional = false)`;
    - `quantity` int;
    - `price` numeric(12,4);
    - `total` numeric(16,4);
    - `executed_at` `Instant`.
  - Índices `idx_trades_buyer_executed (buyer_id, executed_at)`, `idx_trades_seller_executed (seller_id, executed_at)` e `idx_trades_player_executed (player_id, executed_at)`.
  - `CHECK`: `quantity > 0 AND price > 0 AND buyer_id <> seller_id`.
  - Fábrica `Trade.of(MarketOrder buyOrder, MarketOrder sellOrder, int quantity, BigDecimal price, Instant executedAt)` con las validaciones de T041.
  - `@PreRemove`, que lanza `UnsupportedOperationException("trade_is_immutable")`.
- [X] T048 [US4] Crear `TradeRepository extends Repository<Trade, Long>` en `main/repository/market/TradeRepository.java`:
  - `save` y `findById`;
  - `@Query("select t from Trade t where t.buyer.id = :userId or t.seller.id = :userId order by t.executedAt desc, t.id desc") Page<Trade> findByUser(Long userId, Pageable)` con su `countQuery`;
  - `findByPlayerIdOrderByExecutedAtDescIdDesc`.
  - Sin `delete*`.
- [X] T049 [US4] Correr `.\mvnw.cmd "-Dtest=TradeTest,AuditLogTest,ImmutableEntityConfigTest,TradeRepositoryTest,AuditLogRepositoryTest" test` hasta que pase.

**Checkpoint**: las operaciones y la auditoría son inmutables y se pueden consultar.

---

## Phase 7: User Story 5 - Consultas del libro y del historial eficientes (Priority: P2)

**Goal**: el libro sale en el orden correcto y las consultas frecuentes usan índices, también en PostgreSQL.

**Independent Test**: orden del libro en H2 y, en PostgreSQL, índices, `CHECK`, `jsonb`, lock y tiempos.

### Tests for User Story 5 ⚠️

- [X] T050 [P] [US5] Test `test/repository/market/OrderBookQueryTest.java` (`@DataJpaTest` + `@UnitTestProfile`, con `Clock` de instantes fijos para `createdAt`). Con un jugador, LIMIT de venta a 1.10 (t1), 1.05 (t2), 1.05 (t1′ anterior a t2) y 1.20, y una de otro jugador:
  - `findOpenSells` devuelve `1.05 (t1′), 1.05 (t2), 1.10, 1.20`.
  - Con LIMIT de compra a 0.95, 1.00 (t2), 1.00 (t1) y 0.90, `findOpenBuys` devuelve `1.00 (t1), 1.00 (t2), 0.95, 0.90`.
  - Las órdenes FILLED, CANCELLED, MARKET y de otro jugador no aparecen.
  - Con el mismo `createdAt`, desempata el `id` menor (US5-2).
- [X] T051 [US5] Test E2E `test/e2e/MarketBookQueryE2ETest.java` (`@E2ETest` + `@TestPropertySource(properties = "market.superuser.enabled=true")`, PostgreSQL; quickstart §2). Como `E2EDatabaseCleaner` trunca después de cada test, el `@BeforeEach` llama a `SuperuserInitializer.run(null)` (research D14). Casos:
  - (a) `pg_indexes` contiene `idx_orders_book`, `idx_orders_user_created`, `idx_trades_buyer_executed`, `idx_trades_seller_executed`, `idx_trades_player_executed`, `uk_holdings_user_player`, `idx_holdings_user`, `idx_audit_logs_entity` e `idx_audit_logs_occurred` (US5-1, FR-025).
  - (b) Las restricciones `CHECK` de `holdings`, `orders`, `trades` y `audit_logs` existen y rechazan una fila inválida de cada tabla.
  - (c) `information_schema.columns` muestra `before_state` y `after_state` como `jsonb`.
  - (d) El mismo orden del libro que T050, en PostgreSQL.
  - (e) Con 10.000 órdenes LIMIT abiertas en 500 jugadores (insertadas por lote con `JdbcTemplate.batchUpdate`) y `ANALYZE orders`:
    - `findOpenSells` y `findOpenBuys` de un jugador tardan menos de 100 ms (medir la mediana de 5 ejecuciones);
    - `EXPLAIN` de la consulta del libro muestra `idx_orders_book` (SC-006).
  - (f) **Lock**: en un hilo con `TransactionTemplate`, `findOpenSellsForUpdate` y espera un latch. En otro hilo, en su propia transacción, ejecutar la misma JPQL con `EntityManager.createQuery(...)`, `setLockMode(PESSIMISTIC_WRITE)` y `setHint("jakarta.persistence.lock.timeout", 0)` (`NOWAIT`); falla con `jakarta.persistence.PessimisticLockException`. Al soltar el primero, el segundo puede tomar el lock.
  - (g) Con 3 jugadores y `TokenIssuanceService.issueMissing()`, la suma por jugador es 100 en PostgreSQL.

### Implementation for User Story 5

- [X] T052 [US5] Si T050 o T051 fallan por el orden o por el plan, ajustar sólo las consultas de `main/repository/market/MarketOrderRepository.java` o los `@Index` de `main/model/market/MarketOrder.java`, sin cambiar el contrato. Correr `.\mvnw.cmd "-Dtest=OrderBookQueryTest,MarketBookQueryE2ETest" test` y confirmar que el E2E da `Skipped: 0` (si da `Skipped`, falta la infraestructura local; no marcar la tarea hasta correrlo de verdad).

**Checkpoint**: libro ordenado, índices presentes y consultas rápidas en PostgreSQL.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T053 [P] Revisar que ningún log ni `AuditLog` incluya la contraseña o el hash del superusuario: `Grep` de `password` en `main/service/market` y `main/service/audit`. Sólo puede aparecer en `SuperuserAccount` (configuración y `defaultPassword`), en la comparación de `usesDefaultPassword()` y al llamar a `passwordEncoder.encode` (SC-007).
- [X] T054 [P] Confirmar con `git diff --stat main -- backend/src/main/java/com/example/demo/model/User.java backend/src/main/java/com/example/demo/repository/UserRepository.java backend/src/main/resources/application.properties backend/src/main/java/com/example/demo/service/PlayerCatalogService.java` que no hay cambios en los archivos protegidos (FR-027).
- [X] T055 [P] Revisar los javadocs de las clases nuevas: una línea de propósito y la referencia a la spec 026 donde aplique, con la densidad de `PlayerQuote` y `PlayerQuoteRepository`. Sin comentarios redundantes.
- [X] T056 Correr `.\mvnw.cmd test` completo y comparar con T001: no hay fallas nuevas, sólo se agregan tests y ningún test existente fue modificado (`git diff --stat main -- backend/src/test` sólo muestra archivos nuevos). Anotar el total.
- [X] T057 Ejecutar quickstart §3 (arranque manual con variables de entorno, consultas SQL, reinicio y login con `POST /auth/login`) y anotar el resultado. Repetir el arranque sin variables y verificar el `WARN` `market_superuser_default_password`.
- [X] T058 Sonar marcó `java:S2068` como vulnerabilidad (no hotspot) en `SuperuserAccount.java` y bloqueó el quality gate. Se resolvió sacando la contraseña por defecto del código a `market.properties` (ver T020).
- [ ] T059 Revisar los issues nuevos de SonarCloud en el PR (constitución §3.2) y resolverlos si superan el umbral.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias. T003 decide cómo se declaran los `CHECK` de todas las entidades.
- **Foundational (Phase 2)**: depende de Setup. Bloquea todas las historias.
- **US1 (Phase 3)**: depende de Foundational (usa `TransactionAuditService` y `UserSnapshot`).
- **US2 (Phase 4)**: depende de US1 (necesita `SuperuserAccount` y `SuperuserInitializer`) y de `Holding` (Foundational).
- **US3 (Phase 5)**: depende sólo de Foundational. Se puede hacer en paralelo con US1 y US2.
- **US4 (Phase 6)**: depende de US3 (`Trade` referencia `MarketOrder`). Los tests de `AuditLog` (T042, T045) y `ImmutableEntityConfig` (T043, T046) sólo dependen de Foundational.
- **US5 (Phase 7)**: depende de US3 (libro) y, para el E2E, de US1, US2 y US4 (índices y `CHECK` de todas las tablas).
- **Polish (Phase 8)**: depende de todas. T058 depende de que el PR tenga análisis de SonarCloud.

### Within Each User Story

- Tests primero, y tienen que fallar.
- Entidades → repositorios → servicios → integración (listener y llamada desde el inicializador).
- Cada historia cierra con su corrida de tests (T022, T031, T040, T049, T052).

### Parallel Opportunities

- T004–T008 (enums y `AuditActor`) en paralelo, y T015 en paralelo con T013 y T014.
- US3 completa en paralelo con US1 y US2 una vez terminada Foundational.
- Los tests marcados [P] de cada historia en paralelo entre sí.
- T042, T043, T045 y T046 (auditoría y customizer) en paralelo con US3.

## Parallel Example: User Story 3

```text
Task: "T032 HoldingTest en test/model/market/HoldingTest.java"
Task: "T033 MarketOrderTest en test/model/market/MarketOrderTest.java"
Task: "T034 HoldingRepositoryTest en test/repository/market/HoldingRepositoryTest.java"
Task: "T035 MarketOrderRepositoryTest en test/repository/market/MarketOrderRepositoryTest.java"
Task: "T036 MarketUserRepositoryTest en test/repository/market/MarketUserRepositoryTest.java"
```

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 + Phase 2.
2. Phase 3 (US1): el superusuario existe, es único y está auditado.
3. **Validar**: `SuperuserInitializerTest`, `SuperuserInitializerCustomConfigTest` y el suite completo sin regresiones.

### Incremental Delivery

1. Foundational → US1 → US2: los tokens están emitidos y la regla de emisión del enunciado se cumple.
2. US3 → US4: el modelo completo que necesita el motor de calce.
3. US5: rendimiento e índices verificados en PostgreSQL.

## Notes

- Las commits las hace el equipo; esta lista no incluye tareas de commit.
- Si alguna tarea exige modificar un archivo protegido de Entrega 1, frenar y consultar.
- Riesgo abierto que no se resuelve acá: `users.balance` tiene escala 2 (research D8, T036). Queda para la feature del motor de calce.
