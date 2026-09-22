# Implementation Plan: Football-Data Player Catalog

**Branch**: `003-football-data-players` | **Date**: 2026-09-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-football-data-players/spec.md`

## Summary

Implementar el catálogo de jugadores como una integración síncrona y en capas. Un
`FootballDataAdapter` basado en `RestClient` obtiene, por liga, los equipos con sus
planteles desde Football-Data.org v4 y cachea cada respuesta en Redis mediante
`@Cacheable`. `PlayerCatalogService` traduce los DTO externos al modelo existente,
realiza upsert transaccional por `externalId`, crea la emisión inicial de 100 tokens
para cada jugador nuevo y conserva el catálogo ante fallas externas. Una inicialización
al arrancar cuando no hay snapshot exitoso y un scheduler configurable proporcionan el
disparador operativo. `PlayerController` expone `GET /players`, protegido por API key,
con semántica explícita de catálogo vacío frente a no disponible. Migraciones SQL
agregan unicidad e índices sin alterar `Player`, `League` ni `Position`; eventos append-only,
correlation IDs, Actuator y métricas completan auditoría y observabilidad. Los tests
aíslan la API externa con `MockRestServiceServer`, sin tráfico real.

## Technical Context

**Language/Version**: Java 17  
**Primary Dependencies**: Spring Boot 4.1.1, Spring MVC/WebMVC (`RestClient`), Spring Data JPA, Spring Cache, `spring-boot-starter-data-redis`, Spring Security, Spring Scheduling, Flyway, Spring Boot Actuator/Micrometer, springdoc-openapi 3.1.1
**Storage**: PostgreSQL para `players`, `player_positions`, asignación inicial de tokens y auditoría append-only; Redis 7 para respuestas externas cacheadas
**Testing**: JUnit 5, Mockito, MockMvc, Spring `MockRestServiceServer`, tests de migración/constraints con PostgreSQL y test de integración Redis para TTL/serialización
**Target Platform**: Servicio Spring Boot desplegable en Linux/CI, con Java 17, PostgreSQL y Redis  
**Project Type**: Aplicación web con backend Spring Boot y frontend independiente  
**Performance Goals**: `GET /players` se resuelve exclusivamente desde PostgreSQL; una sincronización fría realiza como máximo 5 solicitudes externas, no tiene reintentos automáticos y realiza 0 solicitudes adicionales dentro del TTL
**Constraints**: plan gratuito de 10 solicitudes/minuto; scheduler con intervalo mínimo de un minuto; TTL configurable; una única instancia activa; sin modificar las estructuras Java `Player`, `League` ni `Position`; no exponer secretos; las lecturas continúan ante fallas externas
**Scale/Scope**: 5 ligas, aproximadamente 100 clubes y algunos miles de jugadores; un endpoint público de catálogo, inicialización al arrancar si falta snapshot y scheduler interno configurable; el escalado horizontal requiere un lock distribuido y queda fuera de este feature

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-design gate

| Gate constitucional | Evaluación |
|---|---|
| Fuentes oficiales y cinco grandes ligas (1.3) | PASS: Football-Data.org v4 con códigos `PL`, `PD`, `SA`, `BL1` y `FL1`. |
| Tolerancia a fallas externas (1.3) | PASS: el listado nunca consulta la fuente; una sincronización fallida no elimina ni reemplaza datos locales. |
| Emisión y tenencia inicial (1.1) | PASS: el alta transaccional crea exactamente 100 tokens, asignados al superusuario a precio base 1; el upsert no reemite. |
| Capas Controller/Service/Repository/Adapter (2.1) | PASS: cada responsabilidad tiene paquete e interfaz explícitos. |
| Scheduler de cotizaciones/estadísticas (2.3) | OUT OF SCOPE: el scheduler de catálogo no satisface ni pretende sustituir el recálculo semanal o `POST /quotes/recalculate`; esta feature no modifica ese flujo separado. |
| Tests de construcción con datos mock (3.3) | PASS: adapter sin red real, service con Mockito y controller con MockMvc. |
| API key (4.1) | PASS: `GET /players` permanece detrás del filtro `X-API-KEY` existente. |
| `GET /players` y filtros (5.1) | PASS: contrato con filtros opcionales y combinables por liga, equipo y posición. |
| Redis e índices (5.2) | PASS: Redis con TTL/JSON y migraciones Flyway para índices de liga, equipo, posición y UNIQUE parcial de `external_id`, sin cambiar las estructuras Java existentes. |
| Observabilidad (5.3) | PASS: logging estructurado, filtro de correlation ID, Actuator health e instrumentación de latencia/duración/errores forman parte del feature. |
| OpenAPI/Swagger (6.1) | PASS: operación, filtros, DTOs, `200/400/401/503` y `apiKeyAuth` documentados. |
| Auditoría de estado/financiera (4.2, 7) | PASS: cada cambio de jugador, emisión inicial y transición de sincronización genera un evento append-only con actor User ID, correlation ID, detalle y estados anterior/posterior. |
| Build y SonarCloud (3.1, 3.2, 7) | PASS como gate de entrega: GitHub Actions SUCCESS y Quality Gate PASSED con menos de 10 issues menores. |
| DoD de resiliencia (7) | PASS: tests de falla externa con catálogo previo, Redis real de test y no disponibilidad sin snapshot. |

No hay violaciones que requieran excepción. La sincronización tiene disparadores dentro
de esta feature: se ejecuta al recibir `ApplicationReadyEvent` sólo si no existe un
snapshot exitoso y después mediante un cron configurable. No se agrega un endpoint de
mutación público.

### Post-design re-check

PASS. `research.md`, `data-model.md`, `contracts/players-api.yaml` y `quickstart.md`
cubren emisión inicial, fallback local/Redis, capas, índices, auditoría, observabilidad,
seguridad y gates de entrega sin modificar las estructuras Java existentes. `tasks.md`
fue regenerado y contiene el trabajo ejecutable para `503`, tokens, auditoría, índices,
scheduler de catálogo, observabilidad y CI/Sonar.

## Project Structure

### Documentation (this feature)

```text
specs/003-football-data-players/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── players-api.yaml
└── tasks.md                 # generado posteriormente por $speckit-tasks
```

### Source Code (repository root)

```text
backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/example/demo/
    │   │   ├── adapter/footballdata/
    │   │   │   ├── FootballDataAdapter.java
    │   │   │   └── dto/
    │   │   ├── config/
    │   │   │   ├── CacheConfig.java
    │   │   │   ├── FootballDataConfig.java
    │   │   │   └── ObservabilityConfig.java
    │   │   ├── controller/PlayerController.java
    │   │   ├── dto/player/PlayerResponse.java
    │   │   ├── exception/
    │   │   ├── filter/CorrelationIdFilter.java
    │   │   ├── model/
    │   │   │   ├── Player.java, League.java, Position.java  # existentes; sin cambios
    │   │   │   ├── PlayerTokenAllocation.java
    │   │   │   └── CatalogSyncAuditEvent.java
    │   │   ├── repository/
    │   │   │   ├── PlayerRepository.java
    │   │   │   ├── PlayerTokenAllocationRepository.java
    │   │   │   └── CatalogSyncAuditEventRepository.java
    │   │   ├── scheduler/PlayerCatalogScheduler.java
    │   │   └── service/
    │   │       ├── PlayerCatalogService.java
    │   │       └── PlayerTokenInitializationService.java
    │   └── resources/
    │       ├── application.properties
    │       └── db/migration/
    │           ├── V1__baseline_existing_schema.sql
    │           └── V2__football_data_catalog.sql
    └── test/
        └── java/com/example/demo/
            ├── adapter/footballdata/FootballDataAdapterTest.java
            ├── controller/PlayerControllerTest.java
            └── service/PlayerCatalogServiceTest.java
```

**Structure Decision**: Se extiende el único módulo backend bajo `com.example.demo`.
Los DTO de Football-Data.org permanecen dentro del adapter. Las nuevas estructuras de
asignación inicial y auditoría se almacenan en tablas propias y no agregan campos ni
relaciones a `Player`, `League` o `Position`. Flyway administra constraints e índices.
El frontend no requiere cambios.

## Design Decisions

### Flujo de sincronización

1. `PlayerCatalogService.synchronizeCatalog()` resuelve primero el username configurado
   por `MARKET_SUPERUSER_USERNAME` mediante `UserRepository`, exige exactamente el
   usuario único provisto por la constraint de username y verifica `Role.ADMIN`. Si no
   existe o no es ADMIN, termina de forma controlada antes de escribir jugadores/tokens.
2. El service recorre el mapa estable de las cinco ligas y solicita una instantánea a
   `FootballDataAdapter.fetchCompetitionTeams(code)`.
3. El método público del adapter está anotado con `@Cacheable(cacheNames =
   "football-data-competition-teams", key = "#competitionCode", sync = true)`.
4. El adapter envía `X-Auth-Token` mediante configuración, aplica timeouts y convierte
   errores HTTP/transporte en una excepción de integración sin registrar secretos.
5. El service aplana equipo/plantel, descarta entradas sin id, nombre o equipo, mapea
   posición cuando es reconocida, calcula edad desde `dateOfBirth` y hace upsert por
   `externalId`. Nacionalidad ausente queda nula y posición ausente/desconocida produce
   un conjunto vacío; sólo se descartan personas sin id, nombre o equipo.
6. Una constraint UNIQUE parcial sobre `players.external_id` protege importaciones
   concurrentes. El service resuelve una colisión releyendo la fila existente.
7. Para cada jugador nuevo, una única transacción crea el jugador con
   `marketValue = 1.00`, una emisión total de 100 tokens y una asignación de los 100 al
   superusuario. Una reimportación conserva `marketValue` y no vuelve a emitir tokens.
8. Cada creación/actualización de Player, emisión inicial y transición de ejecución
   agrega un evento inmutable. Todos incluyen el ID del superusuario como actor del
   sistema, correlation ID, timestamp, acción/detalle, tipo/id de entidad y snapshots
   anterior/posterior sanitizados; los eventos terminales agregan ligas y conteos.
9. Una liga fallida no altera sus datos locales. Si todas fallan y nunca hubo snapshot
   exitoso, el estado del catálogo queda `UNAVAILABLE`; si hay snapshot previo, las
   lecturas continúan desde PostgreSQL.

### Disparo de sincronización y rate limit

- `PlayerCatalogScheduler` escucha `ApplicationReadyEvent` y sincroniza sólo cuando no
  existe snapshot exitoso.
- `@Scheduled` ejecuta actualizaciones posteriores con cron configurable y validado; el
  intervalo efectivo mínimo es un minuto.
- Un lock de proceso impide superposición entre el arranque y el cron en la única
  instancia soportada. No hay reintentos HTTP automáticos.
- Cada ejecución fría realiza una solicitud por código de liga (máximo 5) y las
  ejecuciones dentro del TTL reutilizan Redis. Un despliegue horizontal deberá agregar
  un lock distribuido antes de considerarse soportado.

### Flujo de consulta

`GET /players` acepta `league`, `team` y `position`. El controller valida y convierte
los enums; el service compone una `Specification<Player>` con AND entre filtros
presentes. `team` se compara sin distinguir mayúsculas/minúsculas y posición usa un
join sobre `positions` con resultados distintos. Sin filtros se listan todos los datos
locales. El service consulta el último evento terminal de sincronización para distinguir:

- snapshot exitoso y cero jugadores: `200 []`;
- snapshot exitoso con datos: `200` con la lista filtrada;
- ningún snapshot exitoso, cero jugadores e inicialización pendiente/fallida: `503`
  con `{"error":"catalog_unavailable","message":"..."}`.

El contrato OpenAPI documenta `200/400/401/503`. La lectura no intenta sincronizar y
por lo tanto no queda bloqueada por Football-Data.org.

### Persistencia, índices y transacciones

- Flyway pasa a ser la autoridad del esquema y Hibernate usa `ddl-auto=validate`.
- `V1__baseline_existing_schema.sql` representa todas las tablas JPA preexistentes. En
  una base limpia V1 crea el esquema; en una base existente, `baseline-on-migrate=true`
  con `baseline-version=1` registra ese estado sin ejecutar V1.
- `V2__football_data_catalog.sql` se ejecuta en ambos caminos y crea el UNIQUE parcial
  de `players.external_id`, índices de liga/equipo/posición, asignaciones y auditoría.
  La migración falla con diagnóstico explícito si encuentra `external_id` duplicados;
  nunca elimina ni fusiona datos existentes automáticamente.
- Las tablas nuevas de emisión/asignación y auditoría se relacionan por identificadores
  sin modificar la estructura Java de `Player`, `League` o `Position`.
- `player_token_allocations` aplica UNIQUE por jugador y CHECKs de suministro/cantidad
  igual a 100 y precio base igual a 1; referencia al superusuario existente.
- `catalog_sync_audit_events` incluye `actor_user_id`, `correlation_id`, `occurred_at`,
  `action`, `detail`, `entity_type`, `entity_id`, `before_state` y `after_state`, además
  de liga/conteos opcionales. Es append-only: el repositorio sólo ofrece inserción y la
  migración rechaza `UPDATE`/`DELETE` para garantizar inmutabilidad en base de datos.
- La creación de jugador y sus 100 tokens iniciales es atómica. Los eventos de auditoría
  se anexan en transacciones independientes para conservar también intentos fallidos.

### Observabilidad y auditoría

- `CorrelationIdFilter` acepta un `X-Correlation-ID` válido o genera UUID, lo coloca en
  MDC y lo devuelve en el mismo header; jobs no HTTP generan su propio ID.
- Spring Boot structured logging emite JSON uniforme con correlation ID.
- Actuator expone `/actuator/health` con indicadores de aplicación, PostgreSQL y Redis.
- Micrometer registra latencia/errores del adapter y duración/resultado de sincronización.
- Los health details y `/actuator/metrics` quedan protegidos según la seguridad existente;
  sólo el estado agregado de health puede configurarse como público.

### Configuración

- `football-data.base-url=https://api.football-data.org/v4`
- `football-data.token=${FOOTBALL_DATA_TOKEN}`
- `football-data.connect-timeout` y `football-data.read-timeout`
- `football-data.cache-ttl=${FOOTBALL_DATA_CACHE_TTL:PT6H}`
- `football-data.sync.cron` y `football-data.sync.enabled`
- `market.superuser.username=${MARKET_SUPERUSER_USERNAME}`
- propiedades estándar `spring.data.redis.*`
- `spring.jpa.hibernate.ddl-auto=validate`, `spring.flyway.baseline-on-migrate=true` y
  `spring.flyway.baseline-version=1`
- `@EnableCaching` y `RedisCacheManager` con serialización JSON y TTL por caché
- propiedades de Actuator/Micrometer y logging estructurado

### Gates de entrega

- Tests unitarios: service con Mockito y controller con MockMvc.
- Adapter: `MockRestServiceServer`, sin acceso a Football-Data.org.
- Integración: PostgreSQL para migraciones/constraints y Redis para TTL/serialización.
- GitHub Actions debe terminar `SUCCESS` en push/PR.
- SonarCloud debe reportar `PASSED`, sin vulnerabilidades y con menos de 10 issues
  menores antes de declarar completa la feature.

## Complexity Tracking

Las tablas nuevas de asignación inicial y auditoría, Flyway, Actuator y el scheduler
amplían el alcance originalmente previsto, pero son necesarios para satisfacer mandatos
constitucionales explícitos. No se registra ninguna excepción a la constitución.
