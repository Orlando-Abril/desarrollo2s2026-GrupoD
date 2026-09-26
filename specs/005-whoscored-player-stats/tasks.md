---

description: "Task list for 005-whoscored-player-stats"
---

# Tasks: Estadísticas de rendimiento de jugadores desde WhoScored

**Input**: Design documents from `specs/005-whoscored-player-stats/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/whoscored-source.md](./contracts/whoscored-source.md), [contracts/configuration.md](./contracts/configuration.md), [quickstart.md](./quickstart.md)

**Tests**: **Incluidos.** La spec los exige (FR-028, FR-029, SC-007): sin Internet, con fixtures locales, y cubriendo interpretación de métricas, métricas ausentes, matching correcto, sin coincidencia, error de obtención, aislamiento, caché y delegación del scheduler.

**Organization**: tareas agrupadas por historia de usuario. Orden de fases: P1 en el orden de la spec (US1, US2, US5), luego P2 (US3, US4).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: se puede hacer en paralelo (otro archivo, sin dependencias pendientes)
- **[Story]**: historia de usuario de [spec.md](./spec.md) (US1–US5)
- Rutas relativas a la raíz del repo; el backend está en `backend/` (paquete `com.example.demo`)

## Decisiones vigentes que toda tarea debe respetar (de V0)

- Fuente: **feed JSON interno** `GET /statisticsfeed/1/getplayerstatistics` (ruta en minúsculas). **No oficial ni estable.** Sin parsing de HTML, sin Jsoup, sin navegador, sin proxy, **sin dependencias nuevas** (`backend/pom.xml` no cambia).
- Cliente: `RestClient` + `JdkClientHttpRequestFactory` + `java.net.http.HttpClient` (mismo patrón que `FootballDataConfig`) y el `ObjectMapper` de Jackson 3 (`tools.jackson`) ya provisto por Spring Boot.
- Obtención **por liga**: 4 consultas por liga (`summary/all/0`, `shots/zones/2`, `key-passes/length/2`, `tackles/success/2`). Tackles = `tackleWonTotal`.
- **Unidad de falla = liga, todo o nada**: sin las 4 respuestas válidas no se persiste nada de esa liga, se conservan los datos previos y se sigue con la próxima liga. Nunca se persiste un conjunto parcial de métricas.
- **Bloqueo** = status 403, o cuerpo con marcador de Cloudflare (`Just a moment...`, `challenges.cloudflare.com`, `_cf_chl_opt`) o de Incapsula (`_Incapsula_Resource`, `Incapsula incident ID`). Se detecta **antes** de leer JSON; un cuerpo de bloqueo nunca se parsea. **Un único reintento** por consulta y sólo ante bloqueo, tras esperar `max(block-retry.delay, request-delay)`.

---

## Phase 1: Setup (configuración compartida)

**Purpose**: propiedades, cliente HTTP y excepción de la integración, sin lógica todavía.

- [X] T001 [P] Crear `WhoScoredProperties` en `backend/src/main/java/com/example/demo/config/WhoScoredProperties.java`: record `@Validated @ConfigurationProperties(prefix = "whoscored")`, con el mismo estilo que `FootballDataProperties` (`@NotNull`/`@NotBlank` + `@AssertTrue` para duraciones). Campos:
  - `URI baseUrl` (`@NotNull`), `String userAgent` (`@NotBlank`).
  - `Duration connectTimeout` y `Duration readTimeout` (> 0), `Duration requestDelay` (≥ 0), `Duration cacheTtl` (> 0).
  - Record anidado `BlockRetry(boolean enabled, Duration delay)`, con `delay` ≥ 0.
  - `Map<League, Integer> tournaments`: las 5 claves de `League` presentes y valores > 0.
  - Record anidado `Sync(boolean enabled, String cron, String zone)`.
- [X] T002 [P] Crear `WhoScoredException` en `backend/src/main/java/com/example/demo/exception/WhoScoredException.java`, con el mismo patrón que `FootballDataException` (`code` + `message` + `cause`). Agregar constantes para los códigos de [configuration.md](./contracts/configuration.md): `http_error`, `blocked`, `timeout`, `unexpected_structure`, `no_metrics`, `persistence_error`.
- [X] T003 [P] Agregar las propiedades `whoscored.*` a `backend/src/main/resources/application.properties`, con variables de entorno y los defaults de [configuration.md](./contracts/configuration.md):
  - `base-url=${WHOSCORED_BASE_URL:https://www.whoscored.com}`, `user-agent` con un UA de navegador de escritorio, `connect-timeout=5s`, `read-timeout=15s`, `request-delay=2s`, `cache-ttl=24h`.
  - `block-retry.enabled=true`, `block-retry.delay=10s`.
  - `tournaments.PREMIER_LEAGUE=2`, `LA_LIGA=4`, `SERIE_A=5`, `BUNDESLIGA=3`, `LIGUE_1=22`.
  - `sync.enabled=${WHOSCORED_SYNC_ENABLED:false}`, `sync.cron=0 0 4 * * MON`, `sync.zone=UTC`.
- [X] T004 [P] Agregar las propiedades `whoscored.*` de test a `backend/src/test/resources/application.properties`:
  - `base-url=http://127.0.0.1:1`, `user-agent=test-agent`, `connect-timeout=100ms`, `read-timeout=100ms`.
  - `request-delay=0s`, `block-retry.enabled=true`, `block-retry.delay=0s`, `cache-ttl=1m`.
  - Ids de torneo ficticios (`1`–`5`), `sync.enabled=false`, `sync.cron=0 0 4 * * MON`, `sync.zone=UTC`.
- [X] T005 Crear `WhoScoredConfig` en `backend/src/main/java/com/example/demo/config/WhoScoredConfig.java` (depende de T001): `@EnableConfigurationProperties(WhoScoredProperties.class)` y el bean `RestClient whoScoredRestClient`, que:
  - arma un `HttpClient` con `connectTimeout` y **no** habilita `followRedirects` (el default `NEVER` hace visibles los 3xx);
  - usa `JdkClientHttpRequestFactory` con `setReadTimeout(readTimeout)`, `baseUrl` y headers por defecto `User-Agent` (propiedad) y `Accept: application/json`;
  - no envía cookie, `Referer` ni token.

---

## Phase 2: Foundational (prerrequisitos bloqueantes)

**Purpose**: entidad, DTOs, normalización y fixtures que usan todas las historias.

**⚠️ CRITICAL**: ninguna historia puede empezar hasta completar esta fase.

- [X] T006 [P] Crear la entidad `PlayerStats` en `backend/src/main/java/com/example/demo/model/PlayerStats.java`, tabla `player_stats`, según [data-model.md](./data-model.md):
  - `playerId` `Long` en la columna `player_id`: "`bigint` PK, FK → `players.id`", no nulo, "Clave compartida con el jugador (`@MapsId`)".
  - `player`: "`@OneToOne(fetch = LAZY, optional = false)` unidireccional". **No modificar `Player`.**
  - `whoscoredPlayerId` `String`: `varchar(32)`, nullable.
  - Nullables: `minutesPlayed`, `goals`, `assists`, `shots`, `keyPasses`, `tackles`, `yellowCards`, `redCards` como `Integer`/`integer`; `rating` `BigDecimal` `numeric(4,2)`.
  - `fetchedAt` `Instant` `timestamp with time zone`, **no nulo**: "Momento en que el dato se obtuvo **de WhoScored** (no el del guardado)".
  - Lombok como en las entidades existentes. Sin cascada.
- [X] T007 [P] Crear el record `WhoScoredPlayerStats` en `backend/src/main/java/com/example/demo/adapter/whoscored/dto/WhoScoredPlayerStats.java`, con la firma exacta de [data-model.md](./data-model.md): `(String whoscoredPlayerId, String name, String whoscoredTeamId, List<String> teamNames, Integer minutesPlayed, Integer goals, Integer assists, Integer shots, Integer keyPasses, Integer tackles, Integer yellowCards, Integer redCards, BigDecimal rating, Instant fetchedAt)`. Incluir el método `hasAnyMetric()` (true si al menos una de las 9 métricas no es nula). Debe poder serializarse con Jackson (es el valor que se cachea).
- [X] T008 [P] Crear los records Jackson de la respuesta del feed en `backend/src/main/java/com/example/demo/adapter/whoscored/dto/WhoScoredFeedResponse.java`:
  - `WhoScoredFeedResponse(List<Row> playerTableStats)` con `@JsonIgnoreProperties(ignoreUnknown = true)`.
  - Record anidado `Row` con `playerId`, `name`, `teamId`, `teamName`, `tournamentId`, `minsPlayed`, `goal`, `assistTotal`, `yellowCard`, `redCard`, `rating`, `shotsTotal`, `keyPassesTotal`, `tackleWonTotal`.
  - Los numéricos como `JsonNode` (o `Object`), para poder distinguir ausente, `null`, entero, decimal y no numérico.
- [X] T009 [P] Crear el record `StatsUpdateResult(int processed, int updated, int fromCache, int unmatched, int failed)` en `backend/src/main/java/com/example/demo/service/StatsUpdateResult.java`. Documentar los invariantes `processed = updated + unmatched + failed` y `fromCache ⊆ updated`. No se persiste.
- [X] T010 Crear `PlayerStatsRepository extends JpaRepository<PlayerStats, Long>` sin métodos adicionales, en `backend/src/main/java/com/example/demo/repository/PlayerStatsRepository.java` (depende de T006).
- [X] T011 [P] Crear la utilidad `NameNormalizer` en `backend/src/main/java/com/example/demo/service/NameNormalizer.java`, con dos funciones puras según [research.md R7](./research.md):
  - `person(String)`: `Normalizer.Form.NFD` y quitar `\p{M}`; transliterar `ø→o`, `æ→ae`, `ß→ss`, `đ→d`, `ł→l`, `ı→i`; minúsculas con `Locale.ROOT`; `'`, `-` y `.` → espacio; colapsar espacios; `trim`.
  - `team(String)`: lo mismo y además quitar los tokens completos `fc, cf, afc, sc, ac, as, ssc, sv, vfb, vfl, tsg, rc, rcd, ud, cd, sd, ogc, osc, stade, club, de, 1` (lista cerrada en código).
  - Sin similitud aproximada.
- [X] T012 [P] Crear `NameNormalizerTest` en `backend/src/test/java/com/example/demo/service/NameNormalizerTest.java`. Casos:
  - `Ødegaard`/`odegaard`, `N'Golo Kanté`/`n golo kante`, `Müller`, `Fernández`, espacios repetidos;
  - `Arsenal FC`/`Arsenal` y `Paris Saint-Germain FC`/`Paris Saint-Germain`;
  - un token que sólo aparece *dentro* de una palabra no se quita (p. ej. `Monaco` conserva `ac`).
- [X] T013 Crear `PlayerStatsRepositoryTest` (`@DataJpaTest`, H2) en `backend/src/test/java/com/example/demo/repository/PlayerStatsRepositoryTest.java` (depende de T010). Verificar:
  - un registro por jugador (la PK compartida);
  - el reemplazo total: guardar valores, luego `null` en algunas métricas, y comprobar que al releer quedan en `null`;
  - que `players` no cambia.
- [X] T014 Crear los fixtures en `backend/src/test/resources/whoscored/`, según la tabla de [contracts/whoscored-source.md](./contracts/whoscored-source.md#fixtures-de-test-derivados-de-v0). **Paso manual con red**: capturar con los comandos `curl` de [quickstart.md V0](./quickstart.md) y esperar ≥ 2 s entre consultas.
  - `league-summary.json`, `league-shots.json`, `league-key-passes.json`, `league-tackles.json`: respuestas reales de la Premier League (`tournamentOptions=2`), recortadas a pocas filas de 3–4 equipos. Deben incluir Man Utd (nombre corto en summary, `Manchester United` en las detalladas), jugadores con diacríticos (`Ødegaard`, `Fernández`) y un jugador transferido (dos filas con el mismo `playerId`). A `league-key-passes.json` quitarle la fila de un jugador.
  - `synthetic-summary-invalid-values.json` (editado): homónimos en el mismo equipo, `yellowCard: 1.5`, `rating: "abc"`, `goal` ausente, un valor negativo y una fila sin `playerId`.
  - `synthetic-unexpected-structure.json`: JSON válido sin `playerTableStats`.
  - `blocked-cloudflare.html`: challenge con `<title>Just a moment...</title>`, recortado de un 403 real o reconstruido mínimo con ese título.
  - `blocked-incapsula.html`: sintético, con `_Incapsula_Resource` y `Incapsula incident ID`.
  - Todos sin scripts ni recursos externos.

**Checkpoint**: la base está lista; las historias pueden empezar.

---

## Phase 3: User Story 1 - Obtener y guardar las métricas (Priority: P1) 🎯 MVP

**Goal**: descargar una liga del feed, combinar las 4 respuestas, asociar por nombre y equipo normalizados y persistir el conjunto completo de métricas por jugador.

**Independent Test**: con jugadores en la base y el adapter alimentado con los fixtures `league-*.json`, `updateAllStats()` deja en `player_stats` las 9 métricas con los valores de la fuente, `null` donde falta alguna, y `fetchedAt` tomado de la obtención.

### Tests for User Story 1 ⚠️ (escribirlos primero y verlos fallar)

- [X] T015 [P] [US1] Crear `WhoScoredStatsMapperTest` en `backend/src/test/java/com/example/demo/adapter/whoscored/WhoScoredStatsMapperTest.java`, con los fixtures `league-*.json`. Verificar:
  - mapeo de campos (`minsPlayed`, `goal`, `assistTotal`, `yellowCard` → `Integer`, `redCard`, `rating` redondeado a 2 decimales `HALF_UP`, `shotsTotal`, `keyPassesTotal`, `tackleWonTotal`);
  - `19.0` → `19`;
  - combinación por `(playerId, teamId)`, con `teamNames` igual a la unión (`Man Utd`, `Manchester United`);
  - jugador transferido → dos filas;
  - clave ausente en key-passes → `keyPasses == null` y el resto presente;
  - claves que sólo están en las consultas 2–4 → ignoradas;
  - un cuerpo con espacio inicial se lee bien.
- [X] T016 [P] [US1] Crear `WhoScoredAdapterTest` (camino feliz) en `backend/src/test/java/com/example/demo/adapter/whoscored/WhoScoredAdapterTest.java`, con `MockRestServiceServer.bindTo(builder)` como en `FootballDataAdapterTest`. Verificar:
  - exactamente 4 GET en orden a `/statisticsfeed/1/getplayerstatistics`, con los parámetros exactos del contrato (`category`, `subcategory`, `statsAccumulationType`, `tournamentOptions`, `isCurrent=true`, `field=Overall`, `includeZeroValues=true`…) y el header `User-Agent`;
  - respuestas con `Content-Type: text/html;charset=utf-8` y cuerpo JSON → filas combinadas;
  - con `requestDelay=50ms`, el tiempo total medido es ≥ 150 ms (sin `Thread.sleep` en el test).
- [X] T017 [P] [US1] Crear `PlayerStatsServiceTest` (casos US1) en `backend/src/test/java/com/example/demo/service/PlayerStatsServiceTest.java`, con Mockito (adapter y repositorios mockeados o `@DataJpaTest`, a elección, consistente en toda la clase). Verificar:
  - jugador con todas las métricas → guardadas con los valores exactos (escenario 1);
  - métricas no informadas → `null`, nunca 0 (escenario 2);
  - jugador con stats previas → reemplazo total, incluidos los `null`, y `fetchedAt` nuevo (escenario 3);
  - se usa sólo `playerRepository.findAll()`: nada se guarda ni se borra en `players` (FR-004);
  - `StatsUpdateResult` con `processed`/`updated` correctos.

### Implementation for User Story 1

- [X] T018 [US1] Implementar `WhoScoredStatsMapper` en `backend/src/main/java/com/example/demo/adapter/whoscored/WhoScoredStatsMapper.java` (depende de T007 y T008). Es el único lugar con nombres de campos JSON. Debe tener:
  - `WhoScoredFeedResponse parse(String body)`, con el `ObjectMapper` inyectado;
  - `List<WhoScoredPlayerStats> merge(WhoScoredFeedResponse summary, … shots, … keyPasses, … tackles, Instant fetchedAt)`, según las secciones 3 y 4 del contrato: la clave es `(playerId, teamId)`; la consulta 1 define las filas; las 2–4 completan su métrica o la dejan `null`; si una clave se repite, se toma la primera y se registra; `teamNames` son los distintos por `teamId`; los enteros se aceptan sólo si no tienen parte fraccionaria; `rating` en `[0,10]` redondeado a 2 decimales.
- [X] T019 [US1] Implementar `WhoScoredAdapter.fetchLeaguePlayers(League)` en `backend/src/main/java/com/example/demo/adapter/whoscored/WhoScoredAdapter.java` (depende de T005 y T018). Recibe el `RestClient whoScoredRestClient`, `WhoScoredProperties` y `WhoScoredStatsMapper`.
  - Arma la URI con `tournamentOptions = properties.tournaments().get(league)` y los parámetros vacíos del contrato.
  - Aplica la espera de R10 antes de cada consulta real: guarda el `Instant` de la última consulta, hace `Thread.sleep(max(0, requestDelay − transcurrido))` y, ante `InterruptedException`, re-interrumpe el hilo y lanza `WhoScoredException`.
  - Lee el cuerpo con `exchange(...)` como `byte[]`, lo decodifica como UTF-8 y, si el status es 200, lo pasa al mapper.
  - Un `fetchedAt` común para las 4 respuestas.
  - (La clasificación de errores y el reintento se completan en T030.)
- [X] T020 [US1] Implementar `PlayerStatsService.updateAllStats()` en `backend/src/main/java/com/example/demo/service/PlayerStatsService.java` (depende de T009, T010, T011 y T019).
  - `playerRepository.findAll()`, agrupado por `league` y `team`.
  - Por liga con jugadores, `adapter.fetchLeaguePlayers(league)` una sola vez.
  - Equipo: el `teamId` cuyos `teamNames` contienen `NameNormalizer.team(player.getTeam())` al comparar normalizados, si hay exactamente uno.
  - Jugador: la fila de ese `teamId` con `person()` igual, si hay exactamente una.
  - Filas con `hasAnyMetric()` → upsert: `findById(playerId)` o `new` con `@MapsId`; sobrescribir **todas** las métricas (incluidos `null`), `whoscoredPlayerId` y `fetchedAt` de la fila.
  - `updateAllStats` **sin** `@Transactional`: cada `save` va en su propia transacción.
  - Devuelve `StatsUpdateResult`.

**Checkpoint**: US1 funciona y se puede probar sola (MVP de datos).

---

## Phase 4: User Story 2 - Asociar sólo al jugador correcto (Priority: P1)

**Goal**: matching determinístico que prefiere no asociar antes que asociar mal, con el motivo en el log.

**Independent Test**: con fixtures que incluyen tildes, sufijos, nombres de equipo alternativos, homónimos, jugadores inexistentes y el mismo nombre en otro equipo, sólo quedan asociados los casos con una única coincidencia; el resto queda `unmatched` con su motivo en el log y conserva sus métricas previas.

### Tests for User Story 2 ⚠️

- [X] T021 [P] [US2] Agregar casos de matching a `backend/src/test/java/com/example/demo/service/PlayerStatsServiceTest.java`:
  - mayúsculas, tildes y signos (`Ødegaard`, `N'Golo Kanté`, `Fernández`) → asociado (escenario 1);
  - equipo local `Manchester United FC` ↔ `teamNames` `[Man Utd, Manchester United]` → asociado;
  - `Arsenal FC` ↔ `Arsenal` → asociado;
  - nombre inexistente → `unmatched`, `reason=player_not_found` y stats previas sin cambios (escenarios 2 y 5);
  - homónimos en el mismo equipo → `player_ambiguous`, sin asociar (escenario 3);
  - mismo nombre en otro equipo → no se asocia (escenario 4);
  - equipo sin correspondencia → `team_not_found` para todo el equipo;
  - dos `teamId` que normalizan igual → `team_ambiguous`;
  - jugador transferido: sólo se usa la fila de su equipo local.

### Implementation for User Story 2

- [X] T022 [US2] Completar el matching en `backend/src/main/java/com/example/demo/service/PlayerStatsService.java` (depende de T020):
  - distinguir `team_not_found`, `team_ambiguous`, `player_not_found` y `player_ambiguous`;
  - log INFO `whoscored_stats_unmatched playerId= team= reason=` (formato de [configuration.md](./contracts/configuration.md#logs-formato));
  - contar en `unmatched`, sin tocar `player_stats` del jugador;
  - sin similitud aproximada ni alias (FR-008).

**Checkpoint**: US1 + US2 cumplen SC-001 (0 % de asociaciones incorrectas).

---

## Phase 5: User Story 5 - Tolerancia a fallas (Priority: P1)

**Goal**: bloqueos, errores HTTP, timeouts y cambios de estructura no rompen la ejecución ni borran datos. La liga es la unidad de falla (todo o nada) y los errores de un jugador no afectan a los demás.

**Independent Test**: con el adapter devolviendo 403/challenge (con y sin recuperación en el reintento), 5xx, timeout o estructura inválida para una liga, esa liga no persiste nada y conserva lo previo; las demás ligas se actualizan; la ejecución termina sin excepción; `GET /players` sigue respondiendo.

### Tests for User Story 5 ⚠️

- [X] T023 [P] [US5] Agregar casos de error y reintento a `backend/src/test/java/com/example/demo/adapter/whoscored/WhoScoredAdapterTest.java`:
  - 403 con `blocked-cloudflare.html` → `blocked`;
  - **200** con `blocked-cloudflare.html` → `blocked`, sin llegar al `ObjectMapper`;
  - 403 con `blocked-incapsula.html` → `blocked`;
  - 403 seguido de 200 → éxito con exactamente 2 requests a esa URL;
  - 403 + 403 → `blocked` sin tercer request;
  - `block-retry.enabled=false` → falla al primer 403 con 1 request;
  - con `block-retry.delay=50ms` el reintento espera ≥ 50 ms (medido);
  - 302 hacia `/404.html` → `http_error`; 500 → `http_error`; excepción de socket/timeout → `timeout`; en los tres casos sin reintento;
  - 200 con cuerpo que no empieza con `{` → `unexpected_structure`, sin reintento;
  - `synthetic-unexpected-structure.json` → `unexpected_structure`;
  - falla sólo en la 4.ª consulta → excepción y ninguna fila devuelta (todo o nada).
- [X] T024 [P] [US5] Agregar casos inválidos a `backend/src/test/java/com/example/demo/adapter/whoscored/WhoScoredStatsMapperTest.java` con `synthetic-summary-invalid-values.json`:
  - `yellowCard 1.5`, `rating "abc"` y un valor negativo → `null`, con log `whoscored_stats_invalid_metric`;
  - `goal` ausente → `null`;
  - fila sin `playerId` → omitida;
  - todas las filas sin identidad → `unexpected_structure`;
  - el resto de las métricas de la fila se conserva.
- [X] T025 [P] [US5] Agregar casos de tolerancia a `backend/src/test/java/com/example/demo/service/PlayerStatsServiceTest.java`:
  - el adapter lanza `WhoScoredException(blocked)` para una liga → jugadores pendientes de esa liga `failed`, **ningún** `save` de esa liga, stats previas intactas, las otras ligas se actualizan y hay un log `whoscored_stats_league_failed league= code=blocked` (escenario 1 y SC-003);
  - todas las ligas fallan → termina sin excepción, todo `failed` y nada modificado (escenario 2);
  - fila asociada sin ninguna métrica → `failed` con `no_metrics` y lo previo conservado (escenario 3);
  - `save` lanza excepción para un jugador → ese jugador `failed` con `persistence_error` y los demás se guardan;
  - catálogo vacío → `processed=0` sin errores.

### Implementation for User Story 5

- [X] T026 [US5] Completar la clasificación, el reintento y la regla de todo o nada en `backend/src/main/java/com/example/demo/adapter/whoscored/WhoScoredAdapter.java` (depende de T019), según [contracts/whoscored-source.md §5](./contracts/whoscored-source.md):
  - Orden: 403 → `blocked`; marcador de Cloudflare/Incapsula en el cuerpo → `blocked`; otros 3xx/4xx/5xx → `http_error`; 200 cuyo cuerpo sin espacios iniciales no empieza con `{` → `unexpected_structure`; recién entonces el mapper. Los marcadores van como constantes.
  - `ResourceAccessException`/timeout → `timeout`; otra `RestClientException` → `http_error`.
  - **Un único reintento** sólo ante `blocked`, si `blockRetry.enabled`, tras esperar `max(blockRetry.delay, requestDelay)`.
  - Log WARN `whoscored_stats_request_blocked league= category= attempt=1|2`.
  - Si cualquiera de las 4 consultas falla, se propaga `WhoScoredException` y no se devuelve nada.
- [X] T027 [US5] Completar la validación en `backend/src/main/java/com/example/demo/adapter/whoscored/WhoScoredStatsMapper.java` (depende de T018):
  - valor no numérico, fraccionario, negativo o `rating` fuera de `[0,10]` → `null` + log WARN `whoscored_stats_invalid_metric whoscoredPlayerId= metric=`;
  - fila sin `playerId`/`name`/`teamId` → omitida con log;
  - JSON inválido, sin `playerTableStats` o sin ninguna fila con identidad → `WhoScoredException(unexpected_structure)`.
- [X] T028 [US5] Completar la tolerancia en `backend/src/main/java/com/example/demo/service/PlayerStatsService.java` (depende de T022):
  - `try/catch (WhoScoredException)` por liga → log WARN `whoscored_stats_league_failed league= code=`, pendientes a `failed` y continuar con la próxima liga;
  - fila sin métricas → log WARN `whoscored_stats_player_failed playerId= code=no_metrics`;
  - `try/catch` por jugador alrededor del `save` → `persistence_error`;
  - nunca borrar ni modificar `player_stats` ante una falla.

**Checkpoint**: las historias P1 (US1, US2 y US5) están completas.

---

## Phase 6: User Story 3 - Actualización periódica configurable (Priority: P2)

**Goal**: un scheduler semanal deshabilitado por defecto, que sólo delega y registra el inicio, el fin y el resumen en el log.

**Independent Test**: sin la propiedad o con `false` no existe el bean; con `true` existe y, al invocar el método programado, delega en `PlayerStatsService` y registra el resumen; al arrancar no se ejecuta nada.

### Tests for User Story 3 ⚠️

- [X] T029 [P] [US3] Crear `PlayerStatsSchedulerTest` en `backend/src/test/java/com/example/demo/scheduler/PlayerStatsSchedulerTest.java`.
  - Tests unitarios: `run()` llama una vez a `updateAllStats()` y loguea `whoscored_stats_update_started` y `whoscored_stats_update_finished processed= updated= fromCache= unmatched= failed=`; si el service lanza una excepción, no se propaga, se loguea `whoscored_stats_update_crashed` y el MDC queda limpio.
  - Con `ApplicationContextRunner`: sin la propiedad, y con `whoscored.sync.enabled=false`, no existen ni `PlayerStatsScheduler` ni `SchedulingConfig`; con `true` existen; al refrescar el contexto no se invoca el service (FR-024).

### Implementation for User Story 3

- [X] T030 [P] [US3] Crear `SchedulingConfig` en `backend/src/main/java/com/example/demo/config/SchedulingConfig.java`: `@Configuration @EnableScheduling @ConditionalOnProperty(name = "whoscored.sync.enabled", havingValue = "true")`. Sin locks ni un `TaskScheduler` propio: el de un solo hilo por defecto evita superposiciones (FR-027).
- [X] T031 [US3] Crear `PlayerStatsScheduler` en `backend/src/main/java/com/example/demo/scheduler/PlayerStatsScheduler.java` (depende de T020 y T030):
  - `@Component` con la misma `@ConditionalOnProperty` y `@Scheduled(cron = "${whoscored.sync.cron}", zone = "${whoscored.sync.zone:UTC}")`;
  - `MDC.put(CorrelationIdFilter.MDC_KEY, "whoscored-" + UUID)`, log de inicio, `updateAllStats()` y log de fin con el resumen;
  - `catch (RuntimeException)` → log ERROR `whoscored_stats_update_crashed`; `MDC.remove` en `finally`;
  - sin `ApplicationRunner` ni ninguna otra lógica.

**Checkpoint**: US3 funciona sobre US1, US2 y US5.

---

## Phase 7: User Story 4 - Evitar consultas repetidas (Priority: P2)

**Goal**: caché Redis por jugador con TTL de 24 h. Una liga sólo se descarga si alguno de sus jugadores no tiene un resultado vigente. Las fallas no se cachean y una caché caída no frena la ejecución.

**Independent Test**: dos ejecuciones seguidas dentro del TTL → la segunda no llama al adapter para ligas con todos sus jugadores cacheados (SC-005); vencido el TTL, se vuelve a consultar; con la caché caída se sigue guardando.

### Tests for User Story 4 ⚠️

- [X] T032 [P] [US4] Actualizar `CacheConfigTest` en `backend/src/test/java/com/example/demo/config/CacheConfigTest.java`: pasar también `WhoScoredProperties` y verificar que la caché `whoscored-player-stats` existe con el TTL de `cacheTtl` y que su serializador tipado hace el viaje completo de un `WhoScoredPlayerStats` (`BigDecimal`, `Instant`, `List<String>`) sin `LinkedHashMap`. La caché de Football-Data no debe cambiar.
- [X] T033 [P] [US4] Crear `WhoScoredRedisCacheIntegrationTest` (`@Testcontainers(disabledWithoutDocker = true)`) en `backend/src/test/java/com/example/demo/adapter/whoscored/WhoScoredRedisCacheIntegrationTest.java`, con el mismo patrón que `FootballDataRedisIntegrationTest`: put/get tipado de `WhoScoredPlayerStats` y expiración por TTL corto.
- [X] T034 [P] [US4] Agregar casos de caché a `backend/src/test/java/com/example/demo/service/PlayerStatsServiceTest.java`, con `ConcurrentMapCacheManager`:
  - todos los jugadores de una liga cacheados → el adapter no se llama para esa liga, se guardan con el `fetchedAt` cacheado y se cuentan en `fromCache` (escenario 1);
  - uno solo sin cachear → una descarga de la liga;
  - asociación exitosa → `cache.put` con clave `Player.id` **después** del `save`;
  - `unmatched`, `failed` y `no_metrics` no se cachean (escenario 4);
  - `cache.get` lanza excepción → log `whoscored_stats_cache_unavailable operation=get` y se consulta la fuente;
  - `cache.put` lanza excepción → `operation=put` y el dato queda guardado (US5 escenario 5);
  - segunda ejecución dentro del TTL → 0 llamadas al adapter (SC-005).

### Implementation for User Story 4

- [X] T035 [US4] Agregar la caché a `backend/src/main/java/com/example/demo/config/CacheConfig.java`:
  - constante `WHOSCORED_PLAYER_STATS_CACHE = "whoscored-player-stats"`;
  - parámetro `WhoScoredProperties` en `redisCacheManager`;
  - configuración con `entryTtl(properties.cacheTtl())` y `JacksonJsonRedisSerializer<>(objectMapper, WhoScoredPlayerStats.class)`, agregada a `withInitialCacheConfigurations`, sin cambiar la de Football-Data.
- [X] T036 [US4] Agregar el uso programático de la caché en `backend/src/main/java/com/example/demo/service/PlayerStatsService.java` (depende de T028 y T035):
  - `CacheManager.getCache(WHOSCORED_PLAYER_STATS_CACHE)`;
  - por liga, primero resolver desde la caché cada jugador (se persiste igual y se cuenta `updated` + `fromCache`) y descargar la liga sólo si queda alguno pendiente;
  - `put` sólo tras un `save` exitoso;
  - cada `get`/`put` envuelto en `try/catch` → log WARN `whoscored_stats_cache_unavailable operation=get|put`;
  - sin `@Cacheable` y sin un `CacheErrorHandler` global.

**Checkpoint**: todas las historias están completas.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T037 Ejecutar `./mvnw test` desde `backend/` sin Internet (V1 de [quickstart.md](./quickstart.md)): `BUILD SUCCESS`, y ningún test contacta `whoscored.com` (base-url de test `http://127.0.0.1:1`). Confirmar que siguen pasando `PlayerControllerTest`, `PlayerCatalogQueryServiceTest` y `PlayerCatalogServiceTest` sin cambios (FR-017, FR-018).
- [X] T038 Verificar el alcance con `git diff --stat main`: sin cambios en `backend/pom.xml`, `Player.java`, `League.java`, `Position.java`, `PlayerRepository.java`, `PlayerCatalogService.java`, `PlayerCatalogQueryService.java`, `PlayerController.java`, `FootballDataAdapter.java`, `FootballDataConfig.java` ni en `security/`. Las únicas modificaciones deben ser `CacheConfig.java`, `CacheConfigTest.java` y los dos `application.properties`.
- [X] T039 [P] Revisar reglas Sonar (Constitution 3.2) en los archivos nuevos de `backend/src/main/java/com/example/demo/`:
  - `InterruptedException` re-interrumpe el hilo (S2142);
  - no hay `Thread.sleep` en tests (S2925): las esperas se miden, no se fuerzan;
  - no hay configuración duplicada con Football-Data;
  - los logs usan el formato `clave=valor` de [configuration.md](./contracts/configuration.md).
- [X] T040 Validación manual V2 (sin scraping por defecto) y V5 (fallas con `WHOSCORED_BASE_URL=http://127.0.0.1:1` y Redis detenido), según [quickstart.md](./quickstart.md).
- [X] T041 Validación manual V3/V4 contra el WhoScored real, con `whoscored.client=browser` (default) y después de la Fase 9. Registrar qué JDK se usó. Revisar los logs `whoscored_stats_request_blocked`, `whoscored_stats_league_failed` y el resumen; en `player_stats`, que las ligas fallidas no hayan cambiado.
- [X] T042 Registrar V6 en [research.md](./research.md) (R4 y R7):
  - % `updated`/`unmatched`/`failed`, bloqueos en el primer intento, recuperados con el reintento, ligas completas y `team_not_found` por nombres abreviados;
  - si el bloqueo es sistemático o `team_not_found` es alto, llevarlo al equipo como limitación (sin agregar navegador, proxy ni dependencias).

---

## Phase 9: Transporte con Playwright (tras la primera ejecución real)

**Purpose**: Cloudflare bloqueó 10/10 consultas del cliente HTTP de Java y dejó pasar 20/20 a un Chromium real ([research R12](./research.md)). Se cambia **sólo el transporte** del feed; mapper, matching, caché, persistencia y scheduler no se tocan. Ningún test abre un navegador.

- [X] T043 [P] Agregar `com.microsoft.playwright:playwright` `1.63.0` a `backend/pom.xml` (única dependencia nueva, R12).
- [X] T044 [P] Crear la interfaz `WhoScoredFeedClient` en `backend/src/main/java/com/example/demo/adapter/whoscored/WhoScoredFeedClient.java`, con `FeedResponse get(String pathAndQuery)`, `void close()` y el record anidado `FeedResponse(int status, String body)`. Las implementaciones no interpretan el cuerpo.
- [X] T045 Extender la configuración (depende de T044):
  - en `WhoScoredProperties`, el enum `Client { BROWSER, HTTP }` y el record anidado `Browser(String landingPath, Duration navigationTimeout)`, con `landingPath` que empieza con `/` y `navigationTimeout` > 0;
  - en `WhoScoredException`, la constante `BROWSER_ERROR = "browser_error"`;
  - en `backend/src/main/resources/application.properties`: `whoscored.client=${WHOSCORED_CLIENT:browser}`, `whoscored.browser.landing-path=${WHOSCORED_BROWSER_LANDING_PATH:/regions/252/tournaments/2/england-premier-league}` y `whoscored.browser.navigation-timeout=${WHOSCORED_BROWSER_NAVIGATION_TIMEOUT:60s}`;
  - en `backend/src/test/resources/application.properties`: `whoscored.client=http`, el mismo `landing-path` y `navigation-timeout=1s`.
- [X] T046 Crear `HttpFeedClient` en `backend/src/main/java/com/example/demo/adapter/whoscored/HttpFeedClient.java`, moviendo ahí el `exchange` del `RestClient` que hoy está en `WhoScoredAdapter#request`: devuelve status + cuerpo UTF-8; `ResourceAccessException` → `timeout`; otra `RestClientException` → `http_error`; `close()` no hace nada.
- [X] T047 Crear `PlaywrightFeedClient` en `backend/src/main/java/com/example/demo/adapter/whoscored/PlaywrightFeedClient.java`, según [contrato §0](./contracts/whoscored-source.md):
  - Apertura perezosa en la primera `get()`: `Playwright.create` con `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`; Chromium headless con `--disable-blink-features=AutomationControlled`; contexto con el `user-agent` configurado y `en-US`.
  - Navegar a `baseUrl + landingPath` con `waitUntil=DOMCONTENTLOADED` y `navigationTimeout`; mientras el título contenga `Just a moment`, esperar hasta `navigationTimeout`.
  - `get()`: `page.evaluate` de un `fetch(pathAndQuery, {headers:{Accept:'application/json'}})` con `AbortController` limitado por `navigationTimeout` (el primer fetch tarda ~20 s), devolviendo `{status, body}`. Un abort → `timeout`; otra `PlaywrightException` → `browser_error`.
  - `close()` cierra el navegador y Playwright (idempotente) y también se ejecuta en `@PreDestroy`.
- [X] T048 En `backend/src/main/java/com/example/demo/config/WhoScoredConfig.java` (depende de T045–T047), registrar el `WhoScoredFeedClient` según `whoscored.client`: `browser` → `PlaywrightFeedClient` (y `matchIfMissing`), `http` → `HttpFeedClient` sobre `whoScoredRestClient`. El `PlaywrightFeedClient` no abre el navegador al crearse.
- [X] T049 Adaptar `backend/src/main/java/com/example/demo/adapter/whoscored/WhoScoredAdapter.java` (depende de T046 y T048):
  - recibir un `WhoScoredFeedClient` en lugar del `RestClient` y armar el `pathAndQuery` con los mismos parámetros;
  - `classify`, la espera, el reintento y el todo o nada no cambian;
  - agregar `endRun()`, que llama a `feedClient.close()`.
  - Actualizar `WhoScoredAdapterTest` para construir el adapter con un `HttpFeedClient` sobre el `RestClient` mockeado. Sus 15 tests tienen que seguir pasando sin cambiar las expectativas.
- [X] T050 En `backend/src/main/java/com/example/demo/service/PlayerStatsService.java`, llamar a `adapter.endRun()` en un `finally` de `updateAllStats()`, tragando y registrando cualquier excepción del cierre. Agregar a `PlayerStatsServiceTest` que `endRun()` se llama una vez, también cuando todas las ligas fallan.
- [X] T051 [P] Crear `WhoScoredFeedClientConfigTest` en `backend/src/test/java/com/example/demo/config/WhoScoredFeedClientConfigTest.java`, con `ApplicationContextRunner`: `whoscored.client=http` → un solo `WhoScoredFeedClient`, de tipo `HttpFeedClient`; `browser` o sin la propiedad → `PlaywrightFeedClient`, **sin** abrir el navegador.
- [X] T052 Instalar Chromium con el CLI de Playwright (comando de [quickstart](./quickstart.md), prerrequisitos) y correr `./mvnw test` completo sin Internet: `BUILD SUCCESS` y ningún test abre un navegador.

**Checkpoint**: la app descarga el feed con Chromium; T040–T042 se validan después de esta fase.

---

## Phase 10: Ajuste del matching de equipos (tras V6)

**Purpose**: la primera ejecución real asoció 1002/2649 jugadores; 1171 quedaron `team_not_found` en 42 equipos ([research V6](./research.md)). Se amplía la normalización de equipos dentro de FR-006, sin alias ni similitud.

- [X] T053 [US2] Agregar a `backend/src/test/java/com/example/demo/service/NameNormalizerTest.java` los casos reales de V6: prefijos y sufijos (`US Sassuolo Calcio`, `SS Lazio`, `AJ Auxerre`, `ES Troyes AC`, `CA Osasuna`, `Angers SCO`, `ACF Fiorentina`, `Atalanta BC`, `Genoa CFC`, `Real Betis Balompié`, `Real Sociedad de Fútbol`); tokens numéricos (`Como 1907`, `TSG 1899 Hoffenheim`, `1. FSV Mainz 05`, `Bayer 04 Leverkusen`); y casos que siguen **sin** coincidir (`Tottenham Hotspur FC`, `FC Bayern München`, `FC Internazionale Milano`). Verificados en rojo.
- [X] T054 [US2] En `backend/src/main/java/com/example/demo/service/NameNormalizer.java`, ampliar `TEAM_TOKENS` con `us, ss, aj, es, ca, sco, acf, bc, cfc, fsv, calcio, balompie, futbol` e ignorar los tokens sólo numéricos (reemplaza el token `1`).
- [X] T055 Correr `./mvnw test` completo: `BUILD SUCCESS` (183 tests). Medir la mejora en la próxima ejecución manual (T041).
- [X] T056 [US2] Tests en `backend/src/test/java/com/example/demo/service/PlayerStatsServiceTest.java` para la asociación de equipos por inclusión: prefijo (`Tottenham Hotspur FC`↔`Tottenham`, con log `rule=prefix`), prefijo por caracteres (`FC Internazionale Milano`↔`Inter` y no `AC Milan`), el prefijo tiene prioridad (`RCD Espanyol de Barcelona`↔`Espanyol` y no `Barcelona`), contiene todas las palabras (`Olympique de Marseille`↔`Marseille`, `rule=contains`), la igualdad exacta tiene prioridad, más de un candidato → `team_ambiguous`, nombre de menos de 4 letras (`PSV`) → `team_not_found`. Los ejemplos de "equipo sin coincidencia" pasan a usar `Wolverhampton Wanderers FC`↔`Wolves`. Verificados en rojo.
- [X] T057 [US2] Implementar en `backend/src/main/java/com/example/demo/service/PlayerStatsService.java` (`matchTeam`): exacto → "empieza con" → "contiene todas las palabras", con único candidato, `MIN_INCLUDED_TEAM_NAME = 4` y log `whoscored_stats_team_matched`. Actualizar FR-005 y Clarifications en `spec.md`.
- [X] T058 Correr `./mvnw test` completo: `BUILD SUCCESS` (190 tests).
- [X] T059 [US2] Tests en `backend/src/test/java/com/example/demo/service/PlayerStatsServiceTest.java`: las 3 equivalencias (`Borussia Mönchengladbach`↔`Borussia M.Gladbach`, `Olympique Lyonnais`↔`Lyon`, `Stade Rennais FC 1901`↔`Rennes`) con log `rule=alias`; comparación normalizada (mayúsculas, tildes, puntos); si el equipo equivalente no está en la liga, rigen las reglas habituales. Verificados en rojo.
- [X] T060 [US2] Crear `backend/src/main/java/com/example/demo/service/TeamAliases.java` (lista fija en código, nombre del catálogo → nombre en WhoScored, comparada normalizada) y consultarla primero en `PlayerStatsService#matchTeam`. Actualizar FR-008 y Clarifications en `spec.md`.
- [X] T061 Correr `./mvnw test` completo: `BUILD SUCCESS` (193 tests).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias. T005 depende de T001.
- **Foundational (Phase 2)**: depende de Setup y **bloquea** todas las historias. T010 depende de T006, y T013 de T010. T014 (fixtures) es manual y necesita red.
- **US1 (Phase 3)**: depende de Foundational.
- **US2 (Phase 4)**: depende de US1 (extiende el matching de `PlayerStatsService`, T020 → T022).
- **US5 (Phase 5)**: depende de US1 (adapter y mapper) y de US2 (T028 extiende el service después de T022).
- **US3 (Phase 6)**: sólo necesita `PlayerStatsService.updateAllStats()` (T020); se puede hacer en paralelo con US2, US5 y US4.
- **US4 (Phase 7)**: depende de US5 (T036 extiende el service después de T028).
- **Polish (Phase 8)**: al final de todo. T040–T042 dependen además de la **Fase 9** (transporte con Playwright).
- **Phase 9**: depende de US5 y US4 (modifica el adapter y el service ya terminados). T043, T044 y T051 son [P]; el resto va en orden T045 → T046 → T047 → T048 → T049 → T050 → T052.

### User Story Dependencies

- **US1 (P1)**: base del flujo; no depende de otras historias.
- **US2 (P1)**: refina el matching de US1; sus tests se pueden escribir en paralelo a la implementación de US1.
- **US5 (P1)**: agrega errores y reintento al adapter y al mapper de US1, y tolerancia al service.
- **US3 (P2)**: independiente del resto una vez que existe `updateAllStats()`.
- **US4 (P2)**: agrega la caché al service ya tolerante a fallas.

### Within Each User Story

- Tests primero, verificados en rojo, antes de implementar.
- DTOs/entidad → mapper → adapter → service.
- `PlayerStatsService.java` se modifica en secuencia: T020 → T022 → T028 → T036. `WhoScoredAdapter.java`: T019 → T026. `WhoScoredStatsMapper.java`: T018 → T027.

### Parallel Opportunities

- Setup: T001, T002, T003 y T004 en paralelo.
- Foundational: T006, T007, T008, T009, T011 y T012 en paralelo.
- Tests de cada historia marcados [P] entre sí (archivos distintos).
- US3 (T029–T031) en paralelo con US2, US5 o US4.

---

## Parallel Example: User Story 1

```bash
# Tests de US1 en paralelo (archivos distintos):
Task: "T015 WhoScoredStatsMapperTest en backend/src/test/java/com/example/demo/adapter/whoscored/WhoScoredStatsMapperTest.java"
Task: "T016 WhoScoredAdapterTest (camino feliz) en backend/src/test/java/com/example/demo/adapter/whoscored/WhoScoredAdapterTest.java"
Task: "T017 PlayerStatsServiceTest (casos US1) en backend/src/test/java/com/example/demo/service/PlayerStatsServiceTest.java"

# Luego, en secuencia: T018 (mapper) → T019 (adapter) → T020 (service)
```

## Parallel Example: User Story 5

```bash
Task: "T023 errores y reintento en WhoScoredAdapterTest.java"
Task: "T024 valores inválidos en WhoScoredStatsMapperTest.java"
Task: "T025 tolerancia en PlayerStatsServiceTest.java"
# Implementación: T026 (adapter) y T027 (mapper) en paralelo; T028 (service) después de T022
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 (Setup) → Phase 2 (Foundational, incluidos los fixtures reales).
2. Phase 3 (US1) → **validar**: con los fixtures, las métricas quedan en `player_stats`.
3. Sin scheduler, el MVP sólo se ejerce desde los tests (no hay endpoint de disparo por diseño).

### Incremental Delivery

1. US1 → datos correctos con los fixtures.
2. US2 → sin asociaciones incorrectas (SC-001).
3. US5 → bloqueos y fallas tolerados; liga todo o nada (SC-003). **Mínimo para habilitar contra la fuente real.**
4. US3 → ejecución semanal habilitable por variable de entorno.
5. US4 → caché por jugador (SC-005).
6. Polish → V1–V6, incluida la medición real con JDK 17.

---

## Notes

- Cobertura de FR-029 → tareas:

| Exigido por FR-029 | Tareas |
|---|---|
| Interpretación de métricas | T015, T016 |
| Métricas ausentes | T015, T017, T024 |
| Matching correcto | T021 |
| Sin coincidencia | T021 |
| Error de obtención | T023, T025 |
| Aislamiento entre jugadores | T025 |
| Caché | T032, T033, T034 |
| Delegación del scheduler | T029 |

- No se prueba el disparo real del cron (spec, sección 9).
- Ningún test usa red; T014 y T040–T042 son los únicos pasos manuales que tocan la fuente real.
- Commit después de cada tarea o grupo lógico, en la rama `feature/catalogo-whoscored`.
