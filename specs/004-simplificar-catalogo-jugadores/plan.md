# Implementation Plan: Simplificación del catálogo de jugadores (alcance Entrega N.º 1)

**Branch**: `feature/catalogo-football-data` | **Date**: 2026-09-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/004-simplificar-catalogo-jugadores/spec.md`

**Note**: No se crea una rama nueva. La simplificación se aplica sobre la misma rama donde vive la implementación de `003-football-data-players`.

## Summary

Reducir la implementación de `003-football-data-players` a una sola responsabilidad: traer jugadores de Football-Data.org, guardarlos localmente y exponerlos en `GET /players` con filtros y ApiKey.

Qué se retira:
- Flyway y sus migraciones (vuelve `ddl-auto=update`, como en `main`).
- El scheduler.
- La emisión de tokens y la dependencia del superusuario.
- La auditoría inmutable del catálogo.
- Las métricas custom.

Qué se agrega: el scheduler se reemplaza por un único disparo explícito, `POST /players/sync`, protegido con ApiKey.

Qué se conserva sin cambios: adapter, mappings, caché Redis, filtros, Swagger, correlation ID, health check y logs estructurados.

Es una resta de código. El único archivo nuevo es el DTO de respuesta de la carga.

## Technical Context

**Language/Version**: Java 17

**Primary Dependencies**: Spring Boot (Web, Data JPA, Security, Validation, Cache, Data Redis, Actuator), springdoc-openapi, Lombok, JJWT. Se retiran `spring-boot-starter-flyway` y `flyway-database-postgresql`.

**Storage**: PostgreSQL, con el esquema generado por Hibernate (`ddl-auto=update`), y Redis como caché de las respuestas de Football-Data.

**Testing**: JUnit 5, Mockito, Spring MockMvc, `MockRestServiceServer`. Testcontainers queda sólo para el test de Redis; se retira el módulo `postgresql`. En tests se usa H2 con `create-drop`, igual que hoy.

**Target Platform**: Servidor Linux/Windows con JVM 17. CI en GitHub Actions, con servicios de PostgreSQL y Redis.

**Project Type**: Web service (backend REST) dentro de un repositorio con `backend/` y `frontend/`.

**Performance Goals**: Una carga completa hace como máximo 5 requests externas, y 0 dentro del TTL de la caché. La consulta del catálogo nunca depende de la fuente externa.

**Constraints**:
- Plan free de Football-Data: 10 req/min.
- Sin reintentos HTTP automáticos.
- Una sola instancia del backend.
- No modificar la estructura de `Player`, `League`, `Position` ni de sus repositorios.

**Scale/Scope**: Unos 2.650 jugadores de 5 ligas. 2 endpoints de catálogo. Del lado de código principal: 11 archivos de main a eliminar (incluidas las 3 migraciones SQL), 9 a modificar y 1 nuevo. El detalle, incluidos los tests, está en el inventario.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Evaluación | Estado |
|---|---|---|
| 1.1 Tokens (100 tokens a 1 crédito para el superusuario) | Sigue siendo una regla del dominio de mercado. Se retira **del catálogo** y se implementará en la feature de mercado, antes de cualquier compra. No contradice la regla: la difiere | Diferido (justificado) |
| 1.3 Resiliencia ante fuentes externas | `GET /players` lee sólo de PostgreSQL. La carga aísla las fallas por liga y por jugador y nunca borra datos locales | PASS |
| 2.1 Capas Controllers / Services / Repositories / Adapters | Se mantienen `PlayerController`, `PlayerCatalogService`, `PlayerCatalogQueryService`, `PlayerRepository`/`PlayerSpecifications` y `FootballDataAdapter` | PASS |
| 2.3 Scheduler y `POST /quotes/recalculate` | Son de cotizaciones, no del catálogo. Esta feature no los implementa ni los contradice | N/A (diferido) |
| 3.1 / 3.2 CI y SonarCloud | Se retiran tests de funcionalidades eliminadas y se adaptan los demás. El pipeline no cambia | PASS (a verificar en la implementación) |
| 3.3 Escenario 1: base de jugadores desde datos externos | Se cubre con tests del servicio de carga (fuente mockeada) y con `POST /players/sync` | PASS |
| 4.1 JWT y ApiKey | Sin cambios. Los dos endpoints usan el `X-API-KEY` existente | PASS |
| 4.2 Auditoría inmutable de transacciones financieras | La carga del catálogo no es una transacción financiera | PASS |
| 4.3 Validación de entrada | Se mantiene la validación de filtros (400) | PASS |
| 5.1 `GET /players` con filtros | Se mantiene | PASS |
| 5.2 Caché Redis | Se mantiene | PASS |
| 5.2 Índices por liga, equipo y posición | Los creaba la migración `V2`, que se retira. Sin tocar `Player` sólo queda el índice existente sobre `external_id` | Desvío justificado (ver Complexity Tracking) |
| 5.3 Observabilidad | Se conservan correlation ID, logs JSON, health y métricas estándar de Actuator (latencia y errores HTTP en `http.server.requests`). Sólo se retiran los timers y contadores custom | PASS |
| 6.1 OpenAPI | Los dos endpoints documentados con `apiKeyAuth` | PASS |
| DoD 5: toda operación de estado genera auditoría inmutable con Correlation ID | La carga deja logs estructurados con correlation ID, pero no auditoría persistente | Desvío justificado (ver Complexity Tracking) |

**Resultado del gate (pre-research)**: PASS con 2 desvíos justificados y 1 diferimiento.

**Re-check post-design**: el diseño de la Fase 1 no agrega tablas, frameworks ni abstracciones nuevas. Sólo agrega un DTO de respuesta y un endpoint. Los desvíos no cambian. PASS.

## Project Structure

### Documentation (this feature)

```text
specs/004-simplificar-catalogo-jugadores/
├── plan.md              # Este archivo
├── research.md          # Fase 0: decisiones
├── data-model.md        # Fase 1: entidades conservadas y retiradas
├── quickstart.md        # Fase 1: guía de validación
├── contracts/
│   └── players-api.yaml # Fase 1: contrato de GET /players y POST /players/sync
├── checklists/
│   └── requirements.md
└── tasks.md             # Fase 2 (/speckit-tasks, todavía no creado)
```

### Source Code (repository root)

```text
backend/
├── pom.xml
├── src/main/java/com/example/demo/
│   ├── adapter/footballdata/        # FootballDataAdapter, FootballDataMappings, dto/
│   ├── config/                      # CacheConfig, FootballDataConfig, FootballDataProperties, OpenApiConfig, SecurityConfig
│   ├── controller/                  # PlayerController
│   ├── dto/player/                  # PlayerResponse, PlayerSyncResponse (nuevo)
│   ├── exception/                   # FootballDataException, GlobalExceptionHandler
│   ├── filter/                      # CorrelationIdFilter
│   ├── repository/                  # PlayerSpecifications (PlayerRepository ya existente)
│   └── service/                     # PlayerCatalogService, PlayerCatalogQueryService
├── src/main/resources/application.properties
└── src/test/...
```

**Structure Decision**: Se mantiene la estructura en capas existente de `backend/`. No se crean paquetes nuevos. Desaparecen el paquete `scheduler/` y el directorio `resources/db/migration/`.

### Inventario de archivos

#### Eliminar: código principal

| Archivo | Motivo |
|---|---|
| `backend/src/main/java/com/example/demo/scheduler/PlayerCatalogScheduler.java` | Scheduler (FR-009) |
| `backend/src/main/java/com/example/demo/service/PlayerTokenInitializationService.java` | Tokens y superusuario (FR-010, FR-023) |
| `backend/src/main/java/com/example/demo/service/CatalogSyncAuditService.java` | Auditoría del catálogo (FR-024) |
| `backend/src/main/java/com/example/demo/model/PlayerTokenAllocation.java` | Tokens (FR-023) |
| `backend/src/main/java/com/example/demo/model/CatalogSyncAuditEvent.java` | Auditoría (FR-024) |
| `backend/src/main/java/com/example/demo/repository/PlayerTokenAllocationRepository.java` | Tokens (FR-023) |
| `backend/src/main/java/com/example/demo/repository/CatalogSyncAuditEventRepository.java` | Auditoría (FR-024) |
| `backend/src/main/java/com/example/demo/exception/SuperuserUnavailableException.java` | Superusuario (FR-010) |
| `backend/src/main/java/com/example/demo/exception/CatalogUnavailableException.java` | El 503 dependía de la auditoría (FR-013) |
| `backend/src/main/java/com/example/demo/config/ObservabilityConfig.java` | Timers custom (FR-025) |
| `backend/src/main/resources/db/migration/V1__baseline_existing_schema.sql`, `V2__football_data_catalog.sql`, `V3__drop_legacy_players_position.sql` | Flyway (FR-022) |

#### Eliminar: tests

| Archivo | Motivo |
|---|---|
| `backend/src/test/java/com/example/demo/scheduler/PlayerCatalogSchedulerTest.java` | Scheduler |
| `backend/src/test/java/com/example/demo/repository/CatalogSyncAuditIntegrationTest.java` | Auditoría y trigger |
| `backend/src/test/java/com/example/demo/repository/PlayerCatalogPersistenceIntegrationTest.java` | Migraciones Flyway y unicidad de tokens |
| `backend/src/test/java/com/example/demo/service/CatalogSyncResilienceIntegrationTest.java` | Auditoría `FAILED`, superusuario y reintento de arranque |
| `backend/src/test/java/com/example/demo/config/CatalogMetricsTest.java` | Métricas custom |

#### Modificar

| Archivo | Cambio |
|---|---|
| `backend/pom.xml` | Quitar `spring-boot-starter-flyway`, `flyway-database-postgresql` y `org.testcontainers:postgresql` |
| `backend/src/main/resources/application.properties` | Volver a `ddl-auto=update` y `show-sql=true` (igual que `main`). Quitar `spring.flyway.*`, `football-data.sync-cron`, `football-data.bootstrap-retry-interval`, `football-data.enabled` y `market.superuser-username`. Conservar Redis, `football-data.base-url/token/connect-timeout/read-timeout/cache-ttl`, `management.*` y `logging.pattern.console` |
| `backend/src/test/resources/application.properties` | Quitar `spring.flyway.enabled`, `football-data.sync-cron`, `football-data.bootstrap-retry-interval`, `football-data.enabled` y `market.superuser-username` |
| `backend/src/main/java/com/example/demo/config/FootballDataProperties.java` | Quitar `syncCron`, `bootstrapRetryInterval`, `enabled`, `MIN_SYNC_INTERVAL` y su validación |
| `backend/src/main/java/com/example/demo/adapter/footballdata/FootballDataAdapter.java` | Quitar `Timer`/`Counter`/`MeterRegistry` del constructor y del método |
| `backend/src/main/java/com/example/demo/service/PlayerCatalogService.java` | Quitar superusuario, tokens, auditoría, métricas y manejo del MDC. Método `synchronized`. Devolver el resumen |
| `backend/src/main/java/com/example/demo/service/PlayerCatalogQueryService.java` | Quitar `CatalogSyncAuditService` y el chequeo de catálogo no disponible |
| `backend/src/main/java/com/example/demo/controller/PlayerController.java` | Agregar `POST /players/sync`. Quitar la documentación del 503 |
| `backend/src/main/java/com/example/demo/exception/GlobalExceptionHandler.java` | Quitar el handler de `CatalogUnavailableException` |
| `README.md` | Quitar superusuario, primer arranque, cron, reintentos, Flyway y métricas custom. Documentar `POST /players/sync` |
| `backend/src/test/java/.../adapter/footballdata/FootballDataAdapterTest.java` | Adaptar al constructor sin métricas |
| `backend/src/test/java/.../security/FootballDataSecretLeakTest.java` | Adaptar al constructor sin métricas |
| `backend/src/test/java/.../config/CacheConfigTest.java` | Adaptar al nuevo constructor de `FootballDataProperties` |
| `backend/src/test/java/.../service/PlayerCatalogServiceTest.java` | Reescribir sin superusuario, tokens ni auditoría. Cubrir alta, actualización sin duplicar, omisión de incompletos, falla por liga y falla por jugador |
| `backend/src/test/java/.../service/PlayerCatalogQueryServiceTest.java` | Quitar el mock de auditoría. Agregar el caso de catálogo vacío → lista vacía |
| `backend/src/test/java/.../controller/PlayerControllerTest.java` | Quitar el caso 503. Agregar `POST /players/sync` |
| `backend/src/test/java/.../config/PlayerOpenApiTest.java` | Quitar la aserción del 503. Verificar `/players/sync` |
| `specs/003-football-data-players/spec.md` | Nota al inicio indicando que el alcance fue reducido por `004-simplificar-catalogo-jugadores` |

#### Crear

| Archivo | Contenido |
|---|---|
| `backend/src/main/java/com/example/demo/dto/player/PlayerSyncResponse.java` | Record con el resumen de la carga (ver [data-model.md](data-model.md)) |

#### Mantener sin cambios

- **Adapter:** `adapter/footballdata/FootballDataMappings.java` y `adapter/footballdata/dto/FootballDataResponse.java`.
- **Config:** `config/CacheConfig.java`, `config/FootballDataConfig.java`, `config/OpenApiConfig.java` y `config/SecurityConfig.java`.
- **Filtro:** `filter/CorrelationIdFilter.java`.
- **DTO y excepción:** `dto/player/PlayerResponse.java` y `exception/FootballDataException.java`.
- **Repositorio:** `repository/PlayerSpecifications.java`.
- **Raíz:** `.gitignore`.
- **Tests:** `CorrelationIdFilterTest`, `HealthEndpointTest` y `FootballDataRedisIntegrationTest`.
- **Existentes antes de la rama, no se tocan:** `Player`, `League`, `Position`, `PlayerRepository`, `User`, `ApiKey`, `AuthController`, `ApiKeyAuthFilter`, etc.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Sin índices por liga, equipo y posición (constitución 5.2) | Los creaba la migración `V2`, que se retira. Agregarlos exigiría tocar las anotaciones de `Player`, que la spec pide no modificar (FR-027) | Declarar `@Index` en `Player`/`@CollectionTable` modifica el modelo. Mantener SQL suelto reintroduce el manejo de esquema retirado. Con unos 2.650 jugadores el impacto en rendimiento es despreciable. Se recomienda agregarlos cuando el equipo decida la estrategia de esquema definitiva |
| La carga del catálogo no genera auditoría persistente (DoD 5 de la constitución) | Decisión explícita de la persona usuaria (spec FR-024). El Documento de Visión, en su sección 5.2, limita la auditoría inmutable a transacciones financieras | Mantener la tabla de auditoría genera unas 2.650 filas no borrables por corrida sin valor para la Entrega 1. La trazabilidad queda cubierta por logs JSON con correlation ID |
