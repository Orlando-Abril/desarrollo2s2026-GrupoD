# Tasks: Football-Data Player Catalog

**Input**: Design documents from `/specs/003-football-data-players/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/players-api.yaml`, `quickstart.md`

**Tests**: La especificación exige tests. En cada historia se escriben primero, se confirma que fallen por la funcionalidad ausente y recién después se implementa.

**Organization**: Tareas agrupadas por historia para permitir entrega y validación incremental.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo por trabajar en archivos distintos y no depender de trabajo incompleto.
- **[Story]**: Historia cubierta (`US1`–`US4`); Setup, Foundational y Polish no llevan etiqueta.
- Todas las tareas incluyen rutas concretas desde la raíz del repositorio.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Incorporar dependencias y configuración base sin modificar `Player`, `League` ni `Position`.

- [X] T001 Agregar starters de Redis/Cache, Actuator/Micrometer, Flyway PostgreSQL y dependencias de test para integración con PostgreSQL/Redis conservando Spring Boot 4.1.1 y Java 17 en `backend/pom.xml`
- [X] T002 [P] Agregar propiedades sin secretos para Football-Data, TTL, cron, `MARKET_SUPERUSER_USERNAME`, Redis, logging/Actuator y configurar Flyway como autoridad con `ddl-auto=validate`, `baseline-on-migrate=true`, `baseline-version=1` en `backend/src/main/resources/application.properties`
- [X] T003 [P] Crear configuración de tests que use token ficticio, impida llamadas externas y permita inyectar PostgreSQL/Redis de integración en `backend/src/test/resources/application.properties`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Cliente, caché, migraciones, correlation ID, observabilidad y errores compartidos.

**⚠️ CRITICAL**: Ninguna historia comienza hasta completar esta fase.

- [X] T004 Crear propiedades tipadas y validadas para base URL, token, connect/read timeout, TTL, cron y enabled en `backend/src/main/java/com/example/demo/config/FootballDataProperties.java`
- [X] T005 Configurar `RestClient` síncrono con base URL, `X-Auth-Token`, timeouts y cero reintentos automáticos en `backend/src/main/java/com/example/demo/config/FootballDataConfig.java`
- [X] T006 [P] Habilitar Cache y configurar `RedisCacheManager` con JSON y TTL para `football-data-competition-teams` en `backend/src/main/java/com/example/demo/config/CacheConfig.java`
- [X] T007 Crear `V1` como baseline completo de todas las tablas JPA preexistentes para que una base limpia se cree por Flyway y una base no vacía se marque en versión 1 sin ejecutar el script en `backend/src/main/resources/db/migration/V1__baseline_existing_schema.sql`
- [X] T008 Crear `V2` ejecutable tanto después de V1 como después del baseline: detectar/fallar ante external IDs duplicados, agregar UNIQUE parcial/índices, y crear asignaciones más auditoría con actor/detail/before/after, CHECKs y bloqueo UPDATE/DELETE en `backend/src/main/resources/db/migration/V2__football_data_catalog.sql`
- [X] T009 [P] Escribir tests para propagar/generar `X-Correlation-ID`, incluirlo en respuesta/MDC y limpiar MDC al terminar en `backend/src/test/java/com/example/demo/filter/CorrelationIdFilterTest.java`
- [X] T010 Implementar filtro de correlation ID validando UUID, generando uno cuando falte y devolviéndolo en todas las respuestas en `backend/src/main/java/com/example/demo/filter/CorrelationIdFilter.java`
- [X] T011 [P] Configurar observaciones/medidores para latencia y errores del adapter y duración/resultado de sincronización en `backend/src/main/java/com/example/demo/config/ObservabilityConfig.java`
- [X] T012 [P] Crear excepciones sanitizadas `FootballDataException`, `CatalogUnavailableException` y `SuperuserUnavailableException` sin secretos ni payload externo en `backend/src/main/java/com/example/demo/exception/FootballDataException.java`, `backend/src/main/java/com/example/demo/exception/CatalogUnavailableException.java` y `backend/src/main/java/com/example/demo/exception/SuperuserUnavailableException.java`
- [X] T013 Exponer health agregado sin detalles sensibles y proteger endpoints Actuator detallados manteniendo las rutas existentes en `backend/src/main/java/com/example/demo/config/SecurityConfig.java`

**Checkpoint**: Infraestructura compartida lista y migraciones reproducibles.

---

## Phase 3: User Story 1 - Importar catálogo oficial (Priority: P1) 🎯 MVP

**Goal**: Sincronizar las cinco ligas, persistir jugadores idempotentes, emitir exactamente 100 tokens al superusuario y auditar cada ejecución.

**Independent Test**: Ejecutar sincronización con cinco respuestas simuladas; comprobar jugadores, opcionales, asignación constitucional, eventos append-only, cero duplicados/reemisiones y disparo al arranque/cron.

### Tests for User Story 1

> Escribir y ejecutar estos tests primero; deben fallar antes de la implementación.

- [X] T014 [P] [US1] Escribir tests con `MockRestServiceServer` para URI por código, GET, `X-Auth-Token`, deserialización, plantel vacío y ausencia de red real en `backend/src/test/java/com/example/demo/adapter/footballdata/FootballDataAdapterTest.java`
- [X] T015 [P] [US1] Escribir tests Mockito para cinco ligas, deduplicación, opcionales, upsert, preservación de `marketValue` y cero writes cuando el username configurado falta o no tiene Role.ADMIN en `backend/src/test/java/com/example/demo/service/PlayerCatalogServiceTest.java`
- [X] T016 [P] [US1] Escribir tests de arranque sin snapshot, omisión con snapshot, cron, intervalo mínimo y lock que impida ejecuciones superpuestas en `backend/src/test/java/com/example/demo/scheduler/PlayerCatalogSchedulerTest.java`
- [X] T017 [P] [US1] Escribir integración PostgreSQL para migración limpia V1→V2, upgrade de esquema existente baselined en 1, fallo diagnóstico por external IDs duplicados, UNIQUE concurrente, índices y emisión atómica sin duplicados en `backend/src/test/java/com/example/demo/repository/PlayerCatalogPersistenceIntegrationTest.java`
- [X] T018 [P] [US1] Escribir integración PostgreSQL que valide auditoría por Player creado/actualizado, emisión y transición, exigiendo actor User ID, correlation ID, detalle, before/after, y rechazo de UPDATE/DELETE en `backend/src/test/java/com/example/demo/repository/CatalogSyncAuditIntegrationTest.java`

### Implementation for User Story 1

- [X] T019 [P] [US1] Crear DTOs privados tolerantes a campos desconocidos con `id`, `name`, `position`, `dateOfBirth`, `nationality` y `squad` en `backend/src/main/java/com/example/demo/adapter/footballdata/dto/FootballDataResponse.java`
- [X] T020 [P] [US1] Implementar mapeos `PL/PD/SA/BL1/FL1` y posiciones externas, devolviendo vacío para posición ausente/no reconocida, en `backend/src/main/java/com/example/demo/adapter/footballdata/FootballDataMappings.java`
- [X] T021 [US1] Implementar `fetchCompetitionTeams` con `RestClient`, respuesta vacía y traducción sanitizada de fallas HTTP/transporte en `backend/src/main/java/com/example/demo/adapter/footballdata/FootballDataAdapter.java`
- [X] T022 [P] [US1] Crear `PlayerTokenAllocation` con “`playerId` obligatorio y UNIQUE”, “`totalSupply` exactamente 100”, “`ownerQuantity` exactamente 100”, “`basePrice` exactamente 1.00”, owner ADMIN configurado y `createdAt` inmutable en `backend/src/main/java/com/example/demo/model/PlayerTokenAllocation.java`
- [X] T023 [P] [US1] Crear `CatalogSyncAuditEvent` append-only con actorUserId, correlationId, occurredAt, action/detail, entityType/entityId, beforeState/afterState sanitizados, liga/conteos opcionales y failureCode sanitizado en `backend/src/main/java/com/example/demo/model/CatalogSyncAuditEvent.java`
- [X] T024 [US1] Crear repositorios sin operaciones de actualización/borrado para asignaciones y auditoría en `backend/src/main/java/com/example/demo/repository/PlayerTokenAllocationRepository.java` y `backend/src/main/java/com/example/demo/repository/CatalogSyncAuditEventRepository.java`
- [X] T025 [US1] Implementar inserción append-only en transacción independiente para cada cambio y transición, con actor/detalle/before/after, más consulta del último evento terminal en `backend/src/main/java/com/example/demo/service/CatalogSyncAuditService.java`
- [X] T026 [US1] Resolver `MARKET_SUPERUSER_USERNAME` mediante `UserRepository`, exigir usuario existente con `Role.ADMIN`, fallar antes de writes si no cumple, y emitir/auditar idempotentemente 100 tokens dentro de la transacción de alta en `backend/src/main/java/com/example/demo/service/PlayerTokenInitializationService.java`
- [X] T027 [US1] Implementar `synchronizeCatalog()` validando primero el superusuario, haciendo upsert concurrente para cinco ligas y auditando cada Player con actor, detalle y snapshots before/after, junto con valor inicial 1.00 y emisión atómica en `backend/src/main/java/com/example/demo/service/PlayerCatalogService.java`
- [X] T028 [US1] Implementar listener de `ApplicationReadyEvent`, cron configurable, validación de intervalo y lock de proceso en `backend/src/main/java/com/example/demo/scheduler/PlayerCatalogScheduler.java`
- [X] T029 [US1] Emitir logs estructurados y eventos de ciclo STARTED/terminal con actor superusuario, correlation ID, detalle, estado previo/posterior y conteos, sin secretos, en `backend/src/main/java/com/example/demo/service/PlayerCatalogService.java`

**Checkpoint**: MVP importable, idempotente, auditable y constitucionalmente válido.

---

## Phase 4: User Story 2 - Consultar jugadores con filtros protegidos (Priority: P2)

**Goal**: Exponer `GET /players` protegido con API key y filtros opcionales combinados.

**Independent Test**: Ejecutar la matriz de ocho combinaciones de filtros con snapshot exitoso y verificar 100% de resultados; sin API key debe devolver 401.

### Tests for User Story 2

- [X] T030 [P] [US2] Escribir MockMvc para matriz de filtros, `200`, lista vacía, `400`, `401` y header `X-Correlation-ID` en `backend/src/test/java/com/example/demo/controller/PlayerControllerTest.java`
- [X] T031 [P] [US2] Extender tests PostgreSQL para AND, liga, equipo case-insensitive, posición y distinct del join en `backend/src/test/java/com/example/demo/repository/PlayerRepositoryTest.java`
- [X] T032 [P] [US2] Escribir tests Mockito que prueben consulta local, `Specification` y conversión sin invocar el adapter en `backend/src/test/java/com/example/demo/service/PlayerCatalogQueryServiceTest.java`

### Implementation for User Story 2

- [X] T033 [P] [US2] Crear `PlayerResponse` con campos obligatorios `id/externalId/fullName/team/league/positions/marketValue`, posiciones únicas y nacionalidad/edad opcionales en `backend/src/main/java/com/example/demo/dto/player/PlayerResponse.java`
- [X] T034 [P] [US2] Crear specifications con AND, igualdad de liga, `lower(team)` y join distinct sobre posiciones en `backend/src/main/java/com/example/demo/repository/PlayerSpecifications.java`
- [X] T035 [US2] Implementar consulta exclusivamente local, matriz de filtros y mapping de entidades a DTO en `backend/src/main/java/com/example/demo/service/PlayerCatalogQueryService.java`
- [X] T036 [US2] Implementar `GET /players` con `league`, `team`, `position`, validación y `200/400` bajo seguridad global en `backend/src/main/java/com/example/demo/controller/PlayerController.java`
- [X] T037 [US2] Traducir errores de query params al shape público `validation_error` sin detalles internos en `backend/src/main/java/com/example/demo/exception/GlobalExceptionHandler.java`

**Checkpoint**: Catálogo local consultable y protegido, independiente de Football-Data.org.

---

## Phase 5: User Story 3 - Mantener servicio ante fallas externas (Priority: P3)

**Goal**: Usar Redis para limitar solicitudes, preservar snapshots locales y distinguir catálogo vacío de no inicializado.

**Independent Test**: Una carga fría hace hasta cinco llamadas; dentro del TTL hace cero adicionales; timeout/429/503 conserva datos previos y sin snapshot devuelve 503.

### Tests for User Story 3

- [X] T038 [P] [US3] Extender adapter tests para timeout, 429 y 5xx verificando excepción sanitizada y ausencia de reintentos en `backend/src/test/java/com/example/demo/adapter/footballdata/FootballDataAdapterTest.java`
- [X] T039 [P] [US3] Extender service tests para falla parcial, falla total con snapshot, conservación local y estado no disponible sin snapshot en `backend/src/test/java/com/example/demo/service/PlayerCatalogServiceTest.java`
- [X] T040 [P] [US3] Crear integración con Redis real para JSON, TTL, cache hit, evicción y una llamada por liga dentro del TTL en `backend/src/test/java/com/example/demo/adapter/footballdata/FootballDataRedisIntegrationTest.java`
- [X] T041 [P] [US3] Extender MockMvc para diferenciar snapshot vacío `200 []` de nunca inicializado `503 catalog_unavailable` con correlation ID en `backend/src/test/java/com/example/demo/controller/PlayerControllerTest.java`
- [X] T042 [P] [US3] Escribir tests de Micrometer para latencia/error externo y duración/resultado de sincronización en `backend/src/test/java/com/example/demo/config/CatalogMetricsTest.java`

### Implementation for User Story 3

- [X] T043 [US3] Anotar `fetchCompetitionTeams` con `@Cacheable(cacheNames="football-data-competition-teams", key="#competitionCode", sync=true)` en `backend/src/main/java/com/example/demo/adapter/footballdata/FootballDataAdapter.java`
- [X] T044 [US3] Manejar cada liga independientemente, conservar snapshot local, registrar resultado terminal y derivar AVAILABLE/AVAILABLE_EMPTY/UNAVAILABLE en `backend/src/main/java/com/example/demo/service/PlayerCatalogService.java`
- [X] T045 [US3] Hacer que la consulta lance `CatalogUnavailableException` sólo sin snapshot exitoso y sin jugadores locales en `backend/src/main/java/com/example/demo/service/PlayerCatalogQueryService.java`
- [X] T046 [US3] Mapear `CatalogUnavailableException` a `503` con `catalog_unavailable` y registrar fallo sanitizado/correlation ID en `backend/src/main/java/com/example/demo/exception/GlobalExceptionHandler.java`
- [X] T047 [US3] Instrumentar adapter y sincronización con observaciones de latencia, errores, duración y resultado en `backend/src/main/java/com/example/demo/adapter/footballdata/FootballDataAdapter.java` y `backend/src/main/java/com/example/demo/service/PlayerCatalogService.java`

**Checkpoint**: Lecturas disponibles durante fallas y límite externo verificable para una instancia.

---

## Phase 6: User Story 4 - Descubrir el contrato (Priority: P4)

**Goal**: Documentar filtros, seguridad, correlation ID, `200/400/401/503` y health en Swagger.

**Independent Test**: `/v3/api-docs` contiene `/players`, `/actuator/health`, parámetros, headers, DTOs, respuestas y `apiKeyAuth`; Swagger UI permite identificarlos en menos de dos minutos.

### Tests for User Story 4

- [X] T048 [P] [US4] Crear test MockMvc de `/v3/api-docs` para `/players`, filtros, `200/400/401/503`, correlation header, schemas y `apiKeyAuth` en `backend/src/test/java/com/example/demo/config/PlayerOpenApiTest.java`
- [X] T049 [P] [US4] Crear test de contrato de `/actuator/health` para `200/503`, estado agregado, ausencia de detalles sensibles y correlation header en `backend/src/test/java/com/example/demo/config/HealthEndpointTest.java`

### Implementation for User Story 4

- [X] T050 [US4] Anotar `PlayerController` con tag, parámetros, ejemplos, respuestas completas y `@SecurityRequirement(name="apiKeyAuth")` en `backend/src/main/java/com/example/demo/controller/PlayerController.java`
- [X] T051 [P] [US4] Anotar `PlayerResponse` y errores con schemas, enums, opcionales y ejemplos en `backend/src/main/java/com/example/demo/dto/player/PlayerResponse.java`
- [X] T052 [US4] Completar OpenAPI global con `apiKeyAuth`, header de correlación y metadata de health sin alterar `bearerAuth` en `backend/src/main/java/com/example/demo/config/OpenApiConfig.java`

**Checkpoint**: Contrato HTTP y operación observables desde Swagger/OpenAPI.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Cerrar documentación, seguridad, CI y Quality Gate.

- [X] T053 [P] Documentar variables, scheduler, Redis/PostgreSQL, health, métricas, correlation ID y ejecución de tests en `README.md`
- [X] T054 [P] Crear prueba de regresión que inspeccione logs/errores/auditoría y garantice que `FOOTBALL_DATA_TOKEN` y payloads completos nunca se exponen en `backend/src/test/java/com/example/demo/security/FootballDataSecretLeakTest.java`
- [X] T055 Verificar y ajustar servicios PostgreSQL/Redis, build, tests, JaCoCo y análisis Sonar en `.github/workflows/ci.yml`
- [X] T056 Ejecutar `./mvnw.cmd test` y `./mvnw.cmd verify`, corregir regresiones y documentar cualquier ajuste de validación en `specs/003-football-data-players/quickstart.md`
- [ ] T057 Ejecutar escenarios manuales de filtros, API key, `503`, scheduler, tokens, auditoría, caché, health, métricas y Swagger en `specs/003-football-data-players/quickstart.md`
- [X] T058 Comparar la implementación final con paths, headers, schemas y respuestas de `specs/003-football-data-players/contracts/players-api.yaml`
- [ ] T059 Confirmar GitHub Actions `SUCCESS` y SonarCloud `PASSED`, sin vulnerabilidades y con menos de 10 issues menores, registrando el resultado en `specs/003-football-data-players/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup**: sin dependencias.
- **Foundational**: depende de Setup y bloquea todas las historias.
- **US1**: depende de Foundational y constituye el MVP.
- **US2**: depende de Foundational; puede usar fixtures locales mientras US1 avanza.
- **US3**: depende del adapter/sincronización de US1 y la consulta de US2.
- **US4**: depende del contrato HTTP de US2 y la semántica 503/health de US3.
- **Polish**: depende de todas las historias incluidas.

### User Story Dependency Graph

```text
Setup → Foundational → US1 (MVP)
                    ├→ US2
                    └→ US1 + US2 → US3 → US4
US1 + US2 + US3 + US4 → Polish
```

### Within Each User Story

- Tests primero y fallando por funcionalidad ausente.
- Migraciones/modelos antes de repositorios y services.
- Services antes de scheduler/controller.
- Implementación antes de integración completa y checkpoint.

## Parallel Opportunities

- T002 y T003 pueden avanzar junto a T001.
- T006, T009, T011 y T012 trabajan en archivos distintos dentro de Foundational.
- T014–T018 pueden escribirse en paralelo; T019, T020, T022 y T023 también.
- T030–T032 pueden escribirse en paralelo; T033 y T034 pueden implementarse en paralelo.
- T038–T042 pueden escribirse en paralelo.
- T048 y T049 pueden escribirse en paralelo; T051 puede avanzar mientras T050 estabiliza el controller.
- Tras Foundational, US1 y US2 pueden avanzar simultáneamente usando fixtures locales para US2.

## Parallel Examples

### User Story 1

```text
T014 adapter HTTP tests | T015 service tests | T016 scheduler tests
T017 persistence integration | T018 audit integration
T019 external DTOs | T020 mappings | T022 token entity | T023 audit entity
```

### User Story 2

```text
T030 controller tests | T031 repository tests | T032 query service tests
T033 response DTO | T034 specifications
```

### User Story 3

```text
T038 adapter failures | T039 service fallback | T040 Redis integration
T041 HTTP availability | T042 metrics
```

### User Story 4

```text
T048 OpenAPI contract | T049 health contract | T051 DTO schemas
```

## Implementation Strategy

### MVP First (User Story 1)

1. Completar Setup y Foundational.
2. Escribir T014–T018 y confirmar fallos esperados.
3. Implementar T019–T029.
4. Validar importación de cinco ligas, unicidad, emisión de 100 tokens, auditoría y scheduler.
5. Detenerse y demostrar el MVP antes de exponer la consulta.

### Incremental Delivery

1. Setup + Foundational → infraestructura reproducible.
2. US1 → catálogo constitucionalmente válido.
3. US2 → lectura filtrable y protegida.
4. US3 → Redis, fallback, métricas y `503` coherente.
5. US4 → contrato descubrible.
6. Polish → CI/Sonar y validación integral.

### Parallel Team Strategy

- Persona A: adapter, sincronización, scheduler y resiliencia.
- Persona B: persistencia, tokens, auditoría y migraciones.
- Persona C: consulta, controller, OpenAPI y health.
- Integración conjunta: observabilidad, CI/Sonar y quickstart.

## Notes

- No modificar la estructura Java de `Player`, `League` ni `Position`.
- No agregar endpoint público de sincronización.
- El scheduler de catálogo no implementa ni acredita el scheduler semanal de cotizaciones/estadísticas ni `POST /quotes/recalculate`, que pertenecen a otra feature.
- Ningún test llama a Football-Data.org real.
- La garantía de 10 requests/minuto aplica a una sola instancia activa; no implementar escalado horizontal sin lock distribuido.
- Cada checkpoint debe pasar antes de avanzar a la siguiente prioridad.
