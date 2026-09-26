# Contract: Configuración

Esta feature **no agrega endpoints** (FR-018). Su única interfaz externa es la configuración, en `backend/src/main/resources/application.properties` con variables de entorno, siguiendo el estilo de `football-data.*`. Se valida al arrancar con un `@ConfigurationProperties` record `WhoScoredProperties` (`@Validated`).

## Propiedades

| Propiedad | Variable de entorno | Default (main) | Test | Validación | Requisito |
|---|---|---|---|---|---|
| `whoscored.base-url` | `WHOSCORED_BASE_URL` | `https://www.whoscored.com` | `http://127.0.0.1:1` | URI no nula | — |
| `whoscored.client` | `WHOSCORED_CLIENT` | **`browser`** | **`http`** | `browser` \| `http` | R12 |
| `whoscored.browser.landing-path` | `WHOSCORED_BROWSER_LANDING_PATH` | `/regions/252/tournaments/2/england-premier-league` | igual | empieza con `/` | R12 |
| `whoscored.browser.navigation-timeout` | `WHOSCORED_BROWSER_NAVIGATION_TIMEOUT` | `60s` | `1s` | > 0 | R12 |
| `whoscored.user-agent` | `WHOSCORED_USER_AGENT` | UA de navegador de escritorio | `test-agent` | no vacío | R4 |
| `whoscored.connect-timeout` | `WHOSCORED_CONNECT_TIMEOUT` | `5s` | `100ms` | > 0 | FR-020 |
| `whoscored.read-timeout` | `WHOSCORED_READ_TIMEOUT` | `15s` | `100ms` | > 0 | FR-020 |
| `whoscored.request-delay` | `WHOSCORED_REQUEST_DELAY` | `2s` | `0s` | ≥ 0 | FR-016a |
| `whoscored.block-retry.enabled` | `WHOSCORED_BLOCK_RETRY_ENABLED` | `true` | `true` | boolean | R4 (único reintento ante `blocked`) |
| `whoscored.block-retry.delay` | `WHOSCORED_BLOCK_RETRY_DELAY` | `10s` | `0s` | ≥ 0 | R4, R10 |
| `whoscored.cache-ttl` | `WHOSCORED_CACHE_TTL` | `24h` | `1m` | > 0 | FR-013 |
| `whoscored.tournaments.PREMIER_LEAGUE` | `WHOSCORED_TOURNAMENT_PREMIER_LEAGUE` | `2` | id ficticio | entero > 0 | R1 |
| `whoscored.tournaments.LA_LIGA` | `WHOSCORED_TOURNAMENT_LA_LIGA` | `4` | ídem | ídem | R1 |
| `whoscored.tournaments.SERIE_A` | `WHOSCORED_TOURNAMENT_SERIE_A` | `5` | ídem | ídem | R1 |
| `whoscored.tournaments.BUNDESLIGA` | `WHOSCORED_TOURNAMENT_BUNDESLIGA` | `3` | ídem | ídem | R1 |
| `whoscored.tournaments.LIGUE_1` | `WHOSCORED_TOURNAMENT_LIGUE_1` | `22` | ídem | ídem | R1 |
| `whoscored.sync.enabled` | `WHOSCORED_SYNC_ENABLED` | **`false`** | `false` | boolean | FR-023 |
| `whoscored.sync.cron` | `WHOSCORED_SYNC_CRON` | `0 0 4 * * MON` (semanal) | — | cron Spring válido | FR-022, FR-023 |
| `whoscored.sync.zone` | `WHOSCORED_SYNC_ZONE` | `UTC` | — | zona válida | FR-022 |

Notas:

- Las claves de `whoscored.tournaments` son los valores del enum `League` existente (verificar nombres exactos contra `League.java` al implementar). Los valores son los **ids de torneo** de WhoScored (parámetro `tournamentOptions` del feed), verificados en V0 (ver [whoscored-source.md](./whoscored-source.md)); no cambian por temporada.
- `whoscored.sync.enabled` ausente o `false` ⇒ ni `SchedulingConfig` ni `PlayerStatsScheduler` se crean (R9). No hay otra forma de disparar la actualización en esta entrega.
- `whoscored.cache-ttl` debe ser **menor** que el período del cron para que cada ejecución programada obtenga datos frescos (Clarification 5). No se valida automáticamente (el cron es arbitrario); se documenta.
- No hay tokens ni secretos para WhoScored.
- `whoscored.client=browser` usa Playwright (R12): Chromium headless se abre en la primera consulta de una ejecución y se cierra al terminarla. **La app no descarga navegadores**: Chromium se instala una vez con el CLI de Playwright ([quickstart](../quickstart.md)). En tests se usa `http` y nunca se abre un navegador. `browser.navigation-timeout` limita la navegación inicial **y** cada `fetch` hecho desde el navegador (el primero tardó ~20 s en la prueba real, porque compite con los scripts de la página); `connect-timeout` y `read-timeout` sólo aplican a `http`.
- `whoscored.block-retry.*`: como máximo **un** reintento por consulta y sólo ante bloqueo (`403` o challenge de Cloudflare/Incapsula). La espera antes del reintento es `max(block-retry.delay, request-delay)`. El default de 10 s es una elección conservadora, **no validada** contra la fuente (V0 no midió cuánto dura un bloqueo). Con `enabled=false` el primer bloqueo hace fallar la consulta. Ver [whoscored-source.md §5](./whoscored-source.md).

## Logs (formato)

Mismo logger/patrón JSON existente (`logging.pattern.console`), mensajes `clave=valor` como en `PlayerCatalogService`:

| Evento | Nivel | Mensaje |
|---|---|---|
| Inicio de ejecución | INFO | `whoscored_stats_update_started` |
| Fin de ejecución | INFO | `whoscored_stats_update_finished processed= updated= fromCache= unmatched= failed=` |
| Equipo asociado sin igualdad exacta | INFO | `whoscored_stats_team_matched team= whoscoredTeam= rule=alias\|prefix\|contains` |
| Jugador sin coincidencia | INFO | `whoscored_stats_unmatched playerId= team= reason=team_not_found\|team_ambiguous\|player_not_found\|player_ambiguous` |
| Navegador listo (una vez por ejecución) | INFO | `whoscored_browser_ready challengePassed=true\|false` |
| Falla al cerrar el navegador / el transporte | WARN | `whoscored_browser_close_failed` / `whoscored_stats_end_run_failed` |
| Consulta bloqueada (cada intento) | WARN | `whoscored_stats_request_blocked league= category= attempt=1\|2` |
| Falla de liga (feed) | WARN | `whoscored_stats_league_failed league= code= detail=` (último campo, sin comillas: qué respondió la fuente, p. ej. `WhoScored respondió status 429`) |
| Falla de jugador | WARN | `whoscored_stats_player_failed playerId= code=` |
| Métrica inválida | WARN | `whoscored_stats_invalid_metric whoscoredPlayerId= metric=` |
| Fila del feed omitida | WARN | `whoscored_stats_row_skipped category= [whoscoredPlayerId=] reason=missing_identity\|duplicate_key` |
| Caché no disponible | WARN | `whoscored_stats_cache_unavailable operation=get\|put` |
| Error inesperado del scheduler | ERROR | `whoscored_stats_update_crashed` |

Códigos (`code`): `http_error`, `blocked` (403 o challenge de Cloudflare/Incapsula, después del reintento), `timeout`, `unexpected_structure`, `browser_error` (Chromium no instalado, no arranca o falla la navegación/`fetch`), `no_metrics`, `persistence_error`.
