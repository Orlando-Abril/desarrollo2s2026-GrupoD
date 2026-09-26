# Implementation Plan: Estadísticas de rendimiento de jugadores desde WhoScored

**Branch**: `feature/catalogo-whoscored` | **Date**: 2026-09-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/005-whoscored-player-stats/spec.md`

## Summary

Enriquecer los jugadores ya persistidos con métricas crudas de rendimiento de WhoScored (minutos, goles, asistencias, tiros, pases clave, tackles, amarillas, rojas, rating), sin tocar el catálogo ni agregar endpoints.

Enfoque técnico (ver [research.md](./research.md)):

- **Adapter nuevo** `adapter/whoscored`, separado del de Football-Data. Consume el **feed JSON interno** de WhoScored (`/statisticsfeed/1/getplayerstatistics`, verificado en V0) descargándolo con un **Chromium headless (Playwright)** y leyéndolo con el Jackson ya usado por el proyecto; **no parsea HTML**. El transporte está detrás de una interfaz (`WhoScoredFeedClient`): Playwright en la app y el `RestClient` en los tests, así que ningún test abre un navegador (research R12). Trae los datos **por liga**: 4 consultas por liga (resumen, tiros, pases clave y tackles en totales) devuelven todos los jugadores con su equipo. Son 20 consultas por ejecución, con una espera configurable de 2 s entre consultas reales. Detecta como bloqueo el `403` y los challenges de Cloudflare e Incapsula (sin parsearlos como JSON) y hace **un único reintento** configurable por consulta bloqueada (10 s). Devuelve una liga sólo si obtuvo sus 4 respuestas válidas (todo o nada).
- **Service** `PlayerStatsService`: recorre los jugadores locales agrupados por liga y equipo. Primero resuelve desde la caché Redis **por jugador** (TTL 24 h). Si quedan jugadores sin resultado en una liga, descarga esa liga con el adapter y hace el matching determinístico por nombre y equipo normalizados. Persiste cada jugador en su propia transacción y aísla los errores por liga y por jugador.
- **Persistencia**: una tabla nueva `player_stats` (1:0..1 con `players`, PK compartida), generada con el `ddl-auto` existente. Cada guardado reemplaza el conjunto completo; las métricas ausentes quedan en `NULL`.
- **Scheduler** `PlayerStatsScheduler`: `@Scheduled` con cron semanal configurable. Solo existe si `whoscored.sync.enabled=true` (por defecto `false`) y únicamente delega en el service y registra el inicio y el fin en el log.
- **Riesgos** (ver research R4, R7 y R12): el feed es un endpoint interno, **no oficial ni estable**. Con el cliente HTTP de Java, Cloudflare bloqueó 10/10 consultas en la primera ejecución real; con Playwright se obtuvieron 20/20. Si el navegador también llegara a ser bloqueado, la feature cumple igual la spec (tolera la falla, conserva datos y no inventa) pero actualiza menos ligas o ninguna. Los proxies siguen excluidos. Además, los nombres de equipo abreviados de WhoScored pueden dejar equipos sin asociar; se mide en V6.

## Technical Context

**Language/Version**: Java 17

**Primary Dependencies**: Spring Boot 4.1.1 (webmvc, data-jpa, data-redis, cache, validation, actuator), Lombok, Jackson 3 (`tools.jackson`, ya presente; se usa para leer el feed JSON, R6). **Nueva**: `com.microsoft.playwright:playwright` (sólo como transporte del feed, R12). Requiere Chromium instalado en la máquina que ejecuta la app (no en CI).

**Storage**: PostgreSQL con esquema gestionado por `spring.jpa.hibernate.ddl-auto=update` (sin cambios de mecanismo). Redis como caché, a través del `RedisCacheManager` existente.

**Testing**: JUnit 5, AssertJ, Mockito, `MockRestServiceServer`, `@DataJpaTest` sobre H2 (modo PostgreSQL), `ApplicationContextRunner` y Testcontainers para Redis (`disabledWithoutDocker`). Todo sin Internet y con fixtures JSON locales.

**Target Platform**: servidor Linux/JVM (backend Spring Boot) y CI en GitHub Actions (servicios Postgres 16 y Redis 7).

**Project Type**: web-service (backend de la aplicación web `backend/` + `frontend/`). El frontend no cambia.

**Performance Goals**: una ejecución completa hace 20 consultas externas (4 por liga), con 2 s entre ellas, en ~1 min; en el peor caso, 40 (un reintento por consulta, cada uno 10 s después), en ~4 min. Una re-ejecución dentro del TTL hace 0 consultas para las ligas cuyos jugadores están todos vigentes. `GET /players` no cambia de latencia (no depende de WhoScored).

**Constraints**:
- Sin endpoints nuevos, sin Flyway, sin cambios en `Player`, `League`, `Position` ni en los repositories existentes.
- Sin locks, colas, Quartz ni tablas de ejecuciones.
- El scheduler viene deshabilitado por defecto.
- Una sola instancia.

**Scale/Scope**: 5 ligas, ~96 equipos y ~2.500 jugadores. Se agrega 1 tabla, 1 caché, 1 dependencia (Playwright) y unas 15 clases de producción.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio (Constitution v1.1.0) | Evaluación | Estado |
|---|---|---|
| 1.3 Fuentes oficiales: WhoScored por scraping | Adapter dedicado a WhoScored. | ✅ |
| 1.3 Tolerancia a fallas: operar con datos locales | `GET /players` no consulta WhoScored. Las fallas no borran datos. Los errores se aíslan por liga y por jugador. | ✅ |
| 2.1 Capas Controllers / Services / Repositories / Adapters | El adapter solo hace I/O y mapeo del JSON. El service se encarga de matching, caché y persistencia. Hay un repository nuevo. El scheduler es un disparador sin lógica de negocio. Sin controllers nuevos. | ✅ |
| 2.3 Scheduler para actualización de estadísticas | Se implementa con `@Scheduled` y es configurable. El recálculo de cotizaciones queda fuera de alcance, según la spec. | ✅ (parcial por alcance, justificado en la spec) |
| 3.1 CI en SUCCESS | Los tests no dependen de la red. El test de Redis se omite sin Docker. | ✅ |
| 3.2 SonarCloud < 10 issues | Hay que manejar `InterruptedException` re-interrumpiendo el hilo (S2142), no usar `Thread.sleep` en tests (S2925) salvo en el test de TTL, que ya sigue el patrón existente, y evitar duplicar la configuración de Football-Data. | ✅ (a vigilar) |
| 3.3 Escenario 1: base de jugadores desde datos mock/externos | Suma las métricas de rendimiento a partir de fixtures locales. | ✅ |
| 4.1 JWT / ApiKey | No se modifican. | ✅ |
| 4.2 Auditoría inmutable financiera | No aplica: no hay operaciones financieras. La spec excluye la auditoría del scraping. | N/A |
| 5.1 Endpoints mínimos | Ninguno nuevo. Los existentes no cambian. | ✅ |
| 5.2 Caché Redis obligatoria + índices | Caché Redis con TTL configurable. La PK `player_id` indexa el acceso por jugador. | ✅ |
| 5.3 Observabilidad: logs estructurados y correlation id | Se usa el patrón JSON existente. El scheduler pone en el MDC un `correlationId` por ejecución (`whoscored-<uuid>`) para que el log de la corrida se pueda seguir. No hay métricas custom. | ✅ |
| 6.1 OpenAPI | Sin endpoints nuevos, por lo que Swagger no cambia. | N/A |
| 7 DoD: tests de resiliencia ante fallas externas | Los tests cubren 403 y challenges de Cloudflare/Incapsula con reintento único, timeout, estructura cambiada, liga incompleta sin persistencia parcial, caché caída y aislamiento. | ✅ |

**Resultado del gate (pre-Phase 0)**: PASA. Se descartó Jsoup. La única dependencia nueva es Playwright, agregada tras la primera ejecución real (research R12, ver Complexity Tracking).

**Re-check post-Phase 1**: PASA. El diseño ([data-model.md](./data-model.md), [contracts/](./contracts/)) no agrega endpoints, no modifica entidades existentes, agrega una sola tabla y una sola caché, y mantiene el scheduler sin lógica. El riesgo R4 (cambio o bloqueo del feed de WhoScored) no viola la Constitution: 1.3 exige justamente operar ante fallas de la fuente. Queda documentado como riesgo de entrega.

## Project Structure

### Documentation (this feature)

```text
specs/005-whoscored-player-stats/
├── plan.md                      # Este archivo
├── research.md                  # Phase 0: decisiones R1–R11 y riesgo R4
├── data-model.md                # Phase 1: PlayerStats + DTOs transitorios
├── quickstart.md                # Phase 1: validación V0–V6
├── contracts/
│   ├── configuration.md         # Propiedades/env vars y formato de logs
│   └── whoscored-source.md      # Feed JSON de WhoScored (VERIFICADO en V0)
├── checklists/requirements.md
└── tasks.md                     # Phase 2 ($speckit-tasks, no lo crea este comando)
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                            # MODIFICADO: + com.microsoft.playwright:playwright (R12)
└── src/
    ├── main/
    │   ├── java/com/example/demo/
    │   │   ├── adapter/whoscored/
    │   │   │   ├── WhoScoredAdapter.java              # NUEVO: 4 consultas por liga + espera entre consultas + mapeo de errores
    │   │   │   ├── WhoScoredStatsMapper.java          # NUEVO: único lugar con campos JSON del feed; combina las 4 respuestas
│   │   │   ├── WhoScoredFeedClient.java           # NUEVO: interfaz del transporte (status + cuerpo)
│   │   │   ├── HttpFeedClient.java                # NUEVO: transporte RestClient (whoscored.client=http, tests)
│   │   │   ├── PlaywrightFeedClient.java          # NUEVO: transporte Chromium headless (whoscored.client=browser, app)
    │   │   │   └── dto/
    │   │   │       ├── WhoScoredFeedResponse.java     # NUEVO (records Jackson de la respuesta del feed)
    │   │   │       └── WhoScoredPlayerStats.java      # NUEVO (record, valor cacheado)
    │   │   ├── config/
    │   │   │   ├── CacheConfig.java                   # MODIFICADO: + caché whoscored-player-stats (TTL propio, serializador tipado)
    │   │   │   ├── WhoScoredConfig.java               # NUEVO: RestClient + selección del WhoScoredFeedClient
    │   │   │   ├── WhoScoredProperties.java           # NUEVO: @ConfigurationProperties("whoscored")
    │   │   │   └── SchedulingConfig.java              # NUEVO: @EnableScheduling condicional a whoscored.sync.enabled
    │   │   ├── exception/
    │   │   │   └── WhoScoredException.java            # NUEVO (mismo patrón que FootballDataException)
    │   │   ├── model/
    │   │   │   └── PlayerStats.java                   # NUEVO: entidad player_stats
    │   │   ├── repository/
    │   │   │   └── PlayerStatsRepository.java         # NUEVO
    │   │   ├── scheduler/
    │   │   │   └── PlayerStatsScheduler.java          # NUEVO: @Scheduled → PlayerStatsService
    │   │   └── service/
    │   │       ├── NameNormalizer.java                # NUEVO: normalización de nombres/equipos
    │   │       └── PlayerStatsService.java            # NUEVO: caché + matching + persistencia + resumen
    │   └── resources/application.properties           # MODIFICADO: + whoscored.* (sync.enabled=false)
    └── test/
        ├── java/com/example/demo/
        │   ├── adapter/whoscored/
        │   │   ├── WhoScoredAdapterTest.java
        │   │   ├── WhoScoredFeedClientConfigTest.java
        │   │   ├── WhoScoredStatsMapperTest.java
        │   │   └── WhoScoredRedisCacheIntegrationTest.java
        │   ├── repository/PlayerStatsRepositoryTest.java
        │   ├── scheduler/PlayerStatsSchedulerTest.java
        │   └── service/
        │       ├── NameNormalizerTest.java
        │       └── PlayerStatsServiceTest.java
        └── resources/
            ├── application.properties                 # MODIFICADO: + whoscored.* de test (delay 0s, sync off, base-url local)
            └── whoscored/*.json                       # NUEVO: respuestas reales recortadas (ver contrato)
```

**Structure Decision**: se usa la estructura existente del backend (`com.example.demo` organizado por capa). Se agregan un subpaquete `adapter/whoscored/`, análogo a `adapter/footballdata/`, y un paquete `scheduler/` para el único disparador periódico. No se toca `frontend/`.

**Archivos existentes que se modifican**: solo `pom.xml` (Playwright), `CacheConfig.java` y los dos `application.properties`. `Player`, `League`, `Position`, `PlayerRepository`, `PlayerCatalogService`, `PlayerController`, `FootballDataAdapter` y la seguridad quedan intactos.

## Flujo de una ejecución

```text
PlayerStatsScheduler.run()                        [cron semanal, sólo si sync.enabled]
  └─ MDC correlationId, log started
  └─ PlayerStatsService.updateAllStats()
       players = playerRepository.findAll() agrupados por league → team
       por liga:
         resolver cada jugador desde caché → persistir (updated, fromCache)
         si quedan pendientes:
           rows = adapter.fetchLeaguePlayers(league)    ── 4 consultas, c/u con ≤1 reintento si blocked;
                                                           falta cualquiera ⇒ nada persistido, pendientes de la liga = failed,
                                                           log league_failed, sigue con la próxima liga
           por equipo local con pendientes:
             teamId WS cuyos teamNames contienen team() normalizado: 0/>1 ⇒ pendientes = unmatched
             por pendiente: match person() en las filas de ese teamId
               0/>1 ⇒ unmatched (log)
               fila sin métricas ⇒ failed(no_metrics)
               ok ⇒ persistir en su transacción (falla ⇒ failed(persistence_error)), luego cache.put
       finally: adapter.endRun()                   ── cierra el navegador de esta ejecución
       return StatsUpdateResult
  └─ log finished con resumen; catch inesperado ⇒ log crashed; limpiar MDC
```

Persistir antes de `cache.put`: si la base falla, el jugador no queda en caché como resuelto y se reintenta en la próxima ejecución.

## Complexity Tracking

| Adición | Por qué se necesita | Alternativa más simple rechazada porque |
|---|---|---|
| Dependencia `com.microsoft.playwright:playwright` + Chromium en la máquina de ejecución | Cloudflare bloquea al cliente HTTP de Java (10/10 en la primera ejecución real) y deja pasar a un Chromium real (20/20). Sin esto la feature no obtiene datos. | Ajustar headers del cliente Java: no cambia la huella TLS que usa Cloudflare. Selenium: depende del Chrome y el driver instalados y hace menos directo el `fetch` desde la página. |
| Paquete `scheduler/` | La spec exige un scheduler separado de la lógica, y ninguna capa existente es un disparador. | Poner `@Scheduled` en el service mezclaría la programación con la lógica, y la spec lo prohíbe explícitamente. |
