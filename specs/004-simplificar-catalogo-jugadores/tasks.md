---

description: "Task list for 004-simplificar-catalogo-jugadores"
---

# Tasks: Simplificación del catálogo de jugadores (alcance Entrega N.º 1)

**Input**: Documentos de diseño de `specs/004-simplificar-catalogo-jugadores/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/players-api.yaml, quickstart.md

**Tests**: Se incluyen, porque la spec los pide explícitamente (FR-028 y FR-029). Ningún test puede llamar a la API real de Football-Data.

**Organization**: Es una **resta de código**, así que la fase Foundational elimina lo retirado y deja el proyecto compilando. Después cada user story ajusta y prueba su comportamiento.

**Paths**: Todas las rutas Java son relativas a `backend/src/main/java/com/example/demo/` (código) o a `backend/src/test/java/com/example/demo/` (tests), salvo que se indique la ruta completa.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Se puede hacer en paralelo (archivos distintos, sin dependencias pendientes).
- **[Story]**: User story de spec.md (US1, US2, US3).

---

## Phase 1: Setup

**Purpose**: Confirmar el punto de partida antes de borrar código.

- [X] T001 Ejecutar `./mvnw.cmd test` en `backend/` y anotar el resultado de partida (tests en verde o fallas previas), para distinguir después las regresiones propias de esta simplificación

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Eliminar scheduler, tokens, superusuario, auditoría del catálogo y métricas custom, y dejar el proyecto compilando. Bloquea a todas las user stories, porque `PlayerCatalogService` y `PlayerCatalogQueryService` dependen de las clases eliminadas.

**⚠️ CRITICAL**: Al terminar esta fase, `./mvnw.cmd test-compile` tiene que compilar.

### Eliminar código de funcionalidades retiradas

- [X] T002 [P] Borrar `backend/src/main/java/com/example/demo/scheduler/PlayerCatalogScheduler.java`, y el directorio `scheduler/` si queda vacío (FR-009)
- [X] T003 [P] Borrar `backend/src/main/java/com/example/demo/service/PlayerTokenInitializationService.java`, `backend/src/main/java/com/example/demo/model/PlayerTokenAllocation.java` y `backend/src/main/java/com/example/demo/repository/PlayerTokenAllocationRepository.java` (FR-023)
- [X] T004 [P] Borrar `backend/src/main/java/com/example/demo/service/CatalogSyncAuditService.java`, `backend/src/main/java/com/example/demo/model/CatalogSyncAuditEvent.java` y `backend/src/main/java/com/example/demo/repository/CatalogSyncAuditEventRepository.java` (FR-024)
- [X] T005 [P] Borrar `backend/src/main/java/com/example/demo/exception/SuperuserUnavailableException.java` (FR-010) y `backend/src/main/java/com/example/demo/exception/CatalogUnavailableException.java` (FR-013)
- [X] T006 [P] Borrar `backend/src/main/java/com/example/demo/config/ObservabilityConfig.java` (los timers `football.data.request` y `catalog.synchronization`, FR-025)

### Quitar dependencias hacia lo eliminado

- [X] T007 [P] En `config/FootballDataProperties.java`: quitar los componentes `syncCron`, `bootstrapRetryInterval` y `enabled`, la constante `MIN_SYNC_INTERVAL` y el método `hasValidBootstrapRetryInterval()`. Conservar `baseUrl`, `token`, `connectTimeout`, `readTimeout`, `cacheTtl` y `hasValidDurations()` (FR-026)
- [X] T008 [P] En `adapter/footballdata/FootballDataAdapter.java`:
  - Dejar el constructor como `FootballDataAdapter(RestClient footballDataRestClient)`: quitar `Timer`, `Counter`, `MeterRegistry` y el `@Qualifier`.
  - Llamar al `RestClient` directamente, sin `requestTimer.record`, y quitar `errorCounter.increment()`.
  - Conservar `@Cacheable(cacheNames = CacheConfig.COMPETITION_TEAMS_CACHE, key = "#competitionCode", sync = true)`, el mapeo de `null` a `new FootballDataResponse(null)` y el `FootballDataException("external_source_error", "Football-Data no está disponible", ex)` (FR-019, FR-020, FR-025)
- [X] T009 En `service/PlayerCatalogService.java`, dejar sólo la lógica de carga (research R4 y R5):
  - Constructor `(FootballDataAdapter adapter, PlayerRepository playerRepository, TransactionOperations transactionOperations)`. Quitar `PlayerTokenInitializationService`, `CatalogSyncAuditService`, `FootballDataProperties`, `Timer`, `Counter` y `MeterRegistry`.
  - En `synchronizeCatalog()`, quitar `requireSuperuser()`, todas las llamadas `auditService.*`, `Timer.Sample` y el manejo de `MDC`/correlationId. Quitar los métodos `correlationId()` y `snapshot()`.
  - En `upsert(...)`, quitar `tokenService.initialize(...)`, `auditService.appendInCurrentTransaction(...)` y los parámetros `actor`/`correlationId`. Mantener `marketValue(new BigDecimal("1.00"))` sólo al crear.
  - En `persistPlayer(...)`, reemplazar el evento `PLAYER_FAILED` por el `log.warn` existente, que no debe incluir el token ni el payload.
  - Mantener el `try/catch` de `FootballDataException` por liga, la transacción por jugador y `terminalStatus(RunCounts)`.
  - Cambiar `SyncResult` a `record SyncResult(String status, int processed, int failedLeagues, int failedPlayers)`, sin `correlationId`.
- [X] T010 En `service/PlayerCatalogQueryService.java`: dejar el constructor como `PlayerCatalogQueryService(PlayerRepository playerRepository)`. Quitar `CatalogSyncAuditService` y el chequeo `count() == 0 && !hasSuccessfulSnapshot()` que lanzaba `CatalogUnavailableException`, y devolver siempre `findAll(PlayerSpecifications.withFilters(...))` mapeado (FR-013, research R7)
- [X] T011 En `exception/GlobalExceptionHandler.java`, quitar el método `handleCatalogUnavailable` y su import. No tocar los demás handlers
- [X] T012 En `controller/PlayerController.java`, quitar el `@ApiResponse(responseCode = "503", ...)` de `list(...)`. La firma y los filtros de `GET /players` no cambian

### Eliminar y adaptar tests afectados

- [X] T013 [P] Borrar los tests de funcionalidades retiradas (FR-029):
  - `backend/src/test/java/com/example/demo/scheduler/PlayerCatalogSchedulerTest.java`
  - `backend/src/test/java/com/example/demo/repository/CatalogSyncAuditIntegrationTest.java`
  - `backend/src/test/java/com/example/demo/repository/PlayerCatalogPersistenceIntegrationTest.java`
  - `backend/src/test/java/com/example/demo/service/CatalogSyncResilienceIntegrationTest.java`
  - `backend/src/test/java/com/example/demo/config/CatalogMetricsTest.java`
- [X] T014 [P] En `adapter/footballdata/FootballDataAdapterTest.java` y `security/FootballDataSecretLeakTest.java`, construir el adapter con `new FootballDataAdapter(builder.build())`, quitando `SimpleMeterRegistry` y los timers. Conservar todas las aserciones de sanitización y de no reintento
- [X] T015 [P] En `config/CacheConfigTest.java`, construir `FootballDataProperties` con el nuevo constructor de 5 argumentos `(baseUrl, token, connectTimeout, readTimeout, cacheTtl)`
- [X] T016 En `backend/src/test/resources/application.properties`, quitar `football-data.sync-cron`, `football-data.bootstrap-retry-interval`, `football-data.enabled` y `market.superuser-username`. Conservar por ahora `spring.flyway.enabled=false`; se quita en T031
- [X] T017 En `backend/src/main/resources/application.properties`, quitar `football-data.sync-cron`, `football-data.bootstrap-retry-interval`, `football-data.enabled` y `market.superuser-username` (FR-026)
- [X] T018 Ejecutar `./mvnw.cmd test-compile` en `backend/` y corregir cualquier referencia restante a las clases borradas. `PlayerCatalogServiceTest`, `PlayerCatalogQueryServiceTest` y `PlayerControllerTest` se reescriben en US1/US2; si bloquean la compilación, comentar temporalmente sólo sus métodos rotos

**Checkpoint**: El proyecto compila sin scheduler, tokens, superusuario, auditoría ni métricas custom.

---

## Phase 3: User Story 1 - Consultar el catálogo con filtros (Priority: P1) 🎯 MVP

**Goal**: `GET /players` lista los jugadores guardados localmente, con filtros por liga, equipo y posición, protegido con `X-API-KEY`. Devuelve `[]` si no hay datos y nunca consulta la fuente externa.

**Independent Test**: Con jugadores en la base, consultar sin filtros, con filtros combinados, con valores inválidos y sin API key. Verificar `200`, `[]`, `400` y `401` (quickstart §4).

### Tests for User Story 1

- [X] T019 [P] [US1] Reescribir `service/PlayerCatalogQueryServiceTest.java` sin el mock de `CatalogSyncAuditService`:
  - (a) mantener `mapsLocalResultsWithoutCallingAnAdapter`, adaptado al constructor de un argumento;
  - (b) agregar `emptyCatalogReturnsEmptyList`: con `findAll(any(Specification.class))` devolviendo `List.of()`, el resultado es vacío y no se lanza excepción (FR-013).
- [X] T020 [P] [US1] En `controller/PlayerControllerTest.java`:
  - borrar `distinguishesUnavailableCatalog` y el import de `CatalogUnavailableException`;
  - agregar `blankTeamReturnsValidationError` (`GET /players?team=%20` → `400`, `$.error == "validation_error"`) y `emptyCatalogReturnsEmptyArray` (`200`, `$` vacío);
  - conservar `acceptsCombinedFiltersAndReturnsCorrelationId` e `invalidEnumReturnsValidationError`.
- [X] T021 [P] [US1] En `config/PlayerOpenApiTest.java`: reemplazar la aserción `responses['503'].exists()` por `$.paths['/players'].get.responses['503']` que **no** exista, y conservar las aserciones de parámetros, `apiKeyAuth` (`X-API-KEY`) y `PlayerResponse`

### Implementation for User Story 1

- [X] T022 [US1] Verificar en `controller/PlayerController.java` que `GET /players` documenta sólo las respuestas `200`, `400` y `401`, según `specs/004-simplificar-catalogo-jugadores/contracts/players-api.yaml`, y que la descripción aclara que con el catálogo vacío devuelve `[]`
- [X] T023 [US1] Ejecutar `./mvnw.cmd test -Dtest="PlayerCatalogQueryServiceTest,PlayerControllerTest,PlayerOpenApiTest,CorrelationIdFilterTest,HealthEndpointTest"` en `backend/` y dejarlos en verde

**Checkpoint**: La consulta del catálogo funciona sola y no depende de la carga ni de la fuente externa.

---

## Phase 4: User Story 2 - Cargar o actualizar el catálogo desde Football-Data (Priority: P1)

**Goal**: `POST /players/sync`, protegido con ApiKey, ejecuta una carga de las 5 ligas, crea o actualiza por `externalId` sin duplicar, aísla fallas por liga y por jugador, no requiere superusuario y devuelve el resumen. No hay ninguna carga automática.

**Independent Test**: Con la fuente externa mockeada, disparar la carga sobre una base vacía y verificar los jugadores guardados y el resumen `COMPLETED`. Repetirla sin duplicados. Simular una liga caída → `PARTIAL_FAILURE` (quickstart §3).

### Tests for User Story 2

- [X] T024 [P] [US2] Reescribir `service/PlayerCatalogServiceTest.java` con mocks de `FootballDataAdapter` y `PlayerRepository`, y `TransactionOperations.withoutTransaction()`, sin `User`, `Role`, tokens, auditoría ni métricas. Casos:
  - (a) `synchronizesFiveLeagues`: consulta los 5 códigos `PL`, `PD`, `SA`, `BL1` y `FL1`, crea jugadores con `externalId`, nombre, equipo, liga, posición mapeada, nacionalidad, edad y `marketValue` 1.00, y devuelve `COMPLETED`.
  - (b) `updatesExistingPlayerWithoutDuplicating`: con `findByExternalId` devolviendo un jugador existente, se actualizan equipo y datos sobre la misma instancia (mismo `id`) y `marketValue` no cambia.
  - (c) `skipsIncompleteMembersAndTeams`: se omiten un miembro sin `id`, uno sin `name` y un equipo sin `name`.
  - (d) `leagueFailureKeepsOtherLeagues`: con el adapter lanzando `FootballDataException` para `PL`, se procesan las demás, `failedLeagues == 1`, el status es `PARTIAL_FAILURE` y no se llama a ningún `delete` del repositorio.
  - (e) `playerFailureIsSkipped`: con `saveAndFlush` lanzando `DataIntegrityViolationException` para un jugador, los demás se guardan, `failedPlayers == 1` y el status es `PARTIAL_FAILURE`.
  - (f) `everythingFailingEndsAsFailed`: con las 5 ligas fallando, el status es `FAILED` y `processed == 0`.
- [X] T025 [P] [US2] En `controller/PlayerControllerTest.java`, construir el controller con un mock de `PlayerCatalogService` además del de consulta, y agregar `syncReturnsSummary`: `POST /players/sync` → `200`, con `$.status`, `$.processed`, `$.failedLeagues`, `$.failedPlayers` y el header `X-Correlation-ID`
- [X] T026 [P] [US2] En `config/PlayerOpenApiTest.java`, agregar las aserciones de que existe `$.paths['/players/sync'].post`, que tiene seguridad `apiKeyAuth` y que existe el schema `PlayerSyncResponse`

### Implementation for User Story 2

- [X] T027 [P] [US2] Crear `dto/player/PlayerSyncResponse.java` como `record PlayerSyncResponse(String status, int processed, int failedLeagues, int failedPlayers)`, con `@Schema(name = "PlayerSyncResponse")`. Reglas de `data-model.md`:
  - `status`: `"COMPLETED | PARTIAL_FAILURE | FAILED"`, documentado con `allowableValues`.
  - `processed`: `"int ≥ 0"`.
  - `failedLeagues`: `"int, entre 0 y 5"`.
  - `failedPlayers`: `"int ≥ 0"`.
- [X] T028 [US2] En `service/PlayerCatalogService.java`, declarar `public synchronized SyncResult synchronizeCatalog()` para ejecutar en serie las cargas concurrentes y evitar duplicados por `externalId` (research R2)
- [X] T029 [US2] En `controller/PlayerController.java`:
  - inyectar `PlayerCatalogService` en el constructor, junto con `PlayerCatalogQueryService`;
  - agregar `@PostMapping("/sync") public PlayerSyncResponse sync()`, que llama a `synchronizeCatalog()` y mapea `SyncResult` → `PlayerSyncResponse`;
  - documentar con `@Operation` y `@ApiResponse` `200` (con los ejemplos de `contracts/players-api.yaml`) y `401`, además del header `X-Correlation-ID`.

  La ruta queda protegida por `ApiKeyAuthFilter` sin tocar `SecurityConfig` (FR-008, FR-016, FR-021, research R3).
- [X] T030 [US2] Ejecutar `./mvnw.cmd test -Dtest="PlayerCatalogServiceTest,PlayerControllerTest,PlayerOpenApiTest,FootballDataAdapterTest,FootballDataSecretLeakTest,CacheConfigTest"` en `backend/` y dejarlos en verde

**Checkpoint**: La carga se dispara sólo a pedido, sin superusuario, y la consulta de US1 la refleja.

---

## Phase 5: User Story 3 - Levantar el proyecto sin requisitos ajenos al catálogo (Priority: P2)

**Goal**: El esquema vuelve a manejarse como en `main` (`ddl-auto=update`), sin Flyway ni migraciones, y la configuración y la documentación no mencionan funcionalidades retiradas.

**Independent Test**: Levantar `DemoApplication` sobre una base vacía sólo con `DB_PASSWORD`, `JWT_SECRET` y `FOOTBALL_DATA_TOKEN`. Verificar que arranca y que no hay sincronización automática ni tablas de Flyway, tokens o auditoría (quickstart §2 y §6).

### Implementation for User Story 3

- [X] T031 [US3] En `backend/pom.xml`, quitar las dependencias `org.springframework.boot:spring-boot-starter-flyway`, `org.flywaydb:flyway-database-postgresql` y `org.testcontainers:postgresql`. Conservar `org.testcontainers:junit-jupiter` y la propiedad `testcontainers.version`, que usa el test de Redis. Después, quitar `spring.flyway.enabled=false` de `backend/src/test/resources/application.properties`
- [X] T032 [US3] Borrar `backend/src/main/resources/db/migration/V1__baseline_existing_schema.sql`, `V2__football_data_catalog.sql`, `V3__drop_legacy_players_position.sql` y los directorios `db/migration/` y `db/` si quedan vacíos (FR-022)
- [X] T033 [US3] En `backend/src/main/resources/application.properties`:
  - volver a `spring.jpa.hibernate.ddl-auto=update` y `spring.jpa.show-sql=true`, igual que en `main`;
  - quitar `spring.flyway.enabled`, `spring.flyway.baseline-on-migrate` y `spring.flyway.baseline-version`;
  - conservar `spring.data.redis.*`, `football-data.base-url`, `football-data.token`, `football-data.connect-timeout`, `football-data.read-timeout`, `football-data.cache-ttl`, `management.endpoints.web.exposure.include=health,metrics`, `management.endpoint.health.show-details=never`, `management.health.redis.enabled=true` y `logging.pattern.console` (FR-022, FR-025)
- [X] T034 [US3] En `README.md`:
  - quitar `MARKET_SUPERUSER_USERNAME`, `FOOTBALL_DATA_SYNC_CRON`, `FOOTBALL_DATA_BOOTSTRAP_RETRY_INTERVAL` y `FOOTBALL_DATA_ENABLED`, los párrafos "Primer arranque" y "Fallas parciales" y las menciones a Flyway (`V1`/`V2`, "Hibernate sólo lo valida") y a métricas custom de Football-Data y de sincronización;
  - documentar que la carga se dispara con `POST /players/sync` (con `X-API-KEY`), que devuelve un resumen, que no hay carga automática y que `GET /players` devuelve `[]` hasta la primera carga;
  - conservar lo de Redis, `X-Correlation-ID`, `/actuator/health`, `/actuator/metrics` y Swagger.
- [X] T035 [US3] Ejecutar desde la raíz del repo `git grep -nE "flyway|sync-cron|bootstrap-retry|superuser|TokenAllocation|CatalogSyncAudit|football\.data\.request|catalog\.synchronization|CatalogUnavailable" -- backend README.md`. Tiene que devolver 0 coincidencias; corregir cualquiera que aparezca (SC-007)

**Checkpoint**: El backend arranca como antes de la feature 003, sin pasos manuales en la base.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T036 [P] Agregar al inicio de `specs/003-football-data-players/spec.md`, debajo del título, una nota: "**Alcance reducido** por `specs/004-simplificar-catalogo-jugadores` (2026-09-24): se retiraron Flyway, scheduler, emisión de tokens, superusuario, auditoría del catálogo y métricas custom."
- [X] T037 Ejecutar `./mvnw.cmd verify` en `backend/`: build en verde y JaCoCo generado. Documentar cualquier ajuste en `specs/004-simplificar-catalogo-jugadores/quickstart.md`
- [ ] T038 Ejecutar a mano los escenarios §2 a §5 de `specs/004-simplificar-catalogo-jugadores/quickstart.md` (base vacía, `POST /players/sync`, filtros, `401`/`400`, API externa caída, correlation ID y health) y registrar el resultado al final de ese archivo. Recordar quitar `MARKET_SUPERUSER_USERNAME` de la run configuration `DemoApplication` de IntelliJ
- [ ] T039 Después de publicar la rama, confirmar GitHub Actions `SUCCESS` y SonarCloud `PASSED` (< 10 issues menores) y registrarlo en `specs/004-simplificar-catalogo-jugadores/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias.
- **Foundational (Phase 2)**: depende de Setup y bloquea todas las user stories. Hasta que termine, el proyecto no compila.
- **US1 (Phase 3)** y **US2 (Phase 4)**: dependen de Foundational. Son independientes entre sí, salvo que T020/T025 y T021/T026 editan los mismos archivos de test; hacerlas en orden (primero US1, después US2) o combinarlas.
- **US3 (Phase 5)**: depende de Foundational. Es independiente de US1 y US2 a nivel de archivos, salvo `application.properties`, que ya se tocó en T017.
- **Polish (Phase 6)**: depende de US1, US2 y US3.

### Within Phase 2

- T002 a T006 (borrados) en paralelo.
- T007 y T008 en paralelo.
- T009 y T010 después de T003 y T004, porque dejan de referenciar esas clases.
- T011 y T012 después de T005.
- T013 a T015 en paralelo.
- T016 y T017 después de T007.
- T018 al final.

### Within Each User Story

- Los tests (T019 a T021 y T024 a T026) se escriben antes de la implementación y tienen que fallar por las razones esperadas. Por ejemplo, T025 y T026 fallan hasta que existan T027 y T029.
- DTO (T027) → servicio (T028) → controller (T029) → ejecución de tests (T030).

---

## Parallel Example: Phase 2

```text
T002 Borrar scheduler
T003 Borrar tokens (service, model, repository)
T004 Borrar auditoría (service, model, repository)
T005 Borrar excepciones de superusuario y catálogo no disponible
T006 Borrar ObservabilityConfig
T013 Borrar tests de funcionalidades retiradas
```

## Parallel Example: User Story 2

```text
T024 Reescribir PlayerCatalogServiceTest
T027 Crear PlayerSyncResponse
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 y Phase 2: el proyecto compila sin lo retirado.
2. Phase 3 (US1): `GET /players` funciona con los jugadores que ya están en la base local.
3. **Validar**: quickstart §4.

### Incremental Delivery

1. US1: consulta del catálogo.
2. US2: `POST /players/sync` reemplaza al scheduler como único disparo.
3. US3: salida de Flyway y limpieza de la configuración y el README.
4. Polish: `verify`, validación manual y CI/Sonar.

Hacer un commit al final de cada fase para que cada paso quede revertible.

---

## Notes

- No modificar `Player`, `League`, `Position`, `PlayerRepository`, `SecurityConfig`, `CorrelationIdFilter`, `CacheConfig` ni la autenticación existente (FR-016, FR-027).
- No agregar funcionalidades para compensar lo que se elimina: ni índices nuevos, ni locks distribuidos, ni auditoría alternativa.
- Las tablas viejas (`player_token_allocations`, `catalog_sync_audit_events`, `flyway_schema_history`) pueden quedar en las bases locales. La limpieza es opcional (quickstart, "Limpieza opcional").
