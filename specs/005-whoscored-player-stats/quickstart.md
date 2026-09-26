# Quickstart / Validación: Estadísticas de rendimiento desde WhoScored

**Feature**: `005-whoscored-player-stats` | Referencias: [spec](./spec.md) · [data model](./data-model.md) · [configuración](./contracts/configuration.md) · [fuente WhoScored](./contracts/whoscored-source.md)

Guía de validación, no de implementación. Los comandos se ejecutan desde `backend/` salvo indicación.

## Prerrequisitos

- JDK 17, Maven wrapper (`./mvnw`).
- PostgreSQL y Redis locales (los mismos que usa el catálogo).
- Catálogo ya cargado desde Football-Data.org (`POST /players/sync`, feature 004), para que existan jugadores.
- Docker (opcional) para el test de integración de Redis con Testcontainers.
- **Chromium de Playwright** (sólo para correr la app con `whoscored.client=browser`, el default; **no** hace falta para los tests ni en CI). Se instala una vez desde `backend/`:

  ```bash
  # Windows (PowerShell)
  .\mvnw.cmd exec:java -e "-Dexec.mainClass=com.microsoft.playwright.CLI" "-Dexec.args=install chromium"
  # Linux (incluye dependencias del sistema)
  ./mvnw exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install --with-deps chromium"
  ```

  La app no descarga navegadores por su cuenta: si Chromium falta, cada liga falla con `code=browser_error`.

## V0 — Estructura real de WhoScored y acceso sin navegador — ✅ REALIZADO (2026-09-25)

Motivo: [research.md R4](./research.md). Se hizo sin navegador, primero con `curl` y después con el cliente HTTP real del adapter.

> ⚠️ **El feed es un endpoint interno de WhoScored: no es oficial ni estable.** Puede bloquear, cambiar o desaparecer sin aviso.

**Resultados**:
- Páginas de torneo y de equipo: **HTTP 200** sin bloqueo. Pero las páginas de equipo **no** traen las estadísticas en el HTML: la tabla está vacía y la completa JavaScript desde un feed interno.
- Feed `GET /statisticsfeed/1/getplayerstatistics` con `curl`: **HTTP 200 con JSON** en todas las consultas, sin cookie, sin `Referer` y sin token. La ruta debe ir en minúsculas (con mayúsculas responde `301`). La respuesta declara `Content-Type: text/html` aunque el cuerpo es JSON.
- Feed con el **cliente real del adapter** (Spring `RestClient` + `JdkClientHttpRequestFactory` + Jackson 3, JDK 21 con `--release 17`), 20 consultas (5 ligas × 4, con 2 s entre ellas): **15/20 respuestas 200 con JSON válido y 5/20 respuestas 403** con challenge de **Cloudflare** (`Just a moment...`). Los bloqueos fueron intermitentes y sin patrón; sin reintento, sólo 1 de 5 ligas habría quedado completa. Tabla detallada en research R4. **Pendiente**: repetir la prueba con JDK 17.
- Filtrado por torneo, el feed devuelve la liga completa sin paginación: 4 consultas por liga cubren las 9 métricas. Tiros, pases clave y tackles salen en **totales** de las consultas detalladas con `statsAccumulationType=2`. Tackles = `tackleWonTotal`.
- Ids de torneo: Premier League `2`, LaLiga `4`, Serie A `5`, Bundesliga `3`, Ligue 1 `22`.
- Detalle completo en [contracts/whoscored-source.md](./contracts/whoscored-source.md) (verificado) y en research R1, R4–R7.

**Decisión derivada (V0)**: Jackson existente, obtención por liga, fixtures JSON; sin Jsoup, sin parsing de HTML y sin proxy. Además:
- Detectar como bloqueo el `403` y los challenges de Cloudflare e Incapsula, antes de leer JSON y sin parsearlos nunca como JSON.
- Un único reintento configurable por consulta ante bloqueo (`whoscored.block-retry.*`, 10 s).
- Todo o nada por liga: sin las 4 respuestas válidas no se persiste nada de esa liga, se conservan los datos previos y se sigue con las demás.

**Pendiente para la implementación**: capturar y recortar los fixtures JSON en `src/test/resources/whoscored/` (lista en el contrato). Para re-capturar una respuesta (ejemplo: tiros totales de la Premier League):

```bash
curl -s --compressed -A "<UA configurado>" -o league-shots.json \
  "https://www.whoscored.com/statisticsfeed/1/getplayerstatistics?category=shots&subcategory=zones&statsAccumulationType=2&isCurrent=true&playerId=&teamIds=&matchId=&stageId=&tournamentOptions=2&sortBy=Rating&sortAscending=&age=&ageComparisonType=&appearances=&appearancesComparisonType=&field=Overall&nationality=&positionOptions=&timeOfTheGameEnd=&timeOfTheGameStart=&isMinApp=false&page=&includeZeroValues=true&numberOfPlayersToPick="
```

Las otras consultas cambian `category`/`subcategory`/`statsAccumulationType` según la tabla del contrato (`summary/all/0`, `key-passes/length/2`, `tackles/success/2`). Esperar al menos 2 s entre consultas.

**Riesgos abiertos** (se miden en V6): el feed no es oficial, ya bloquea de forma intermitente (25 % de las consultas en la prueba Java) y puede cambiar o empezar a exigir token/cookies. Además, varios nombres de equipo de WhoScored son abreviaturas (`Tottenham`, `Inter`, `Leeds`) que no coinciden con los nombres completos del catálogo bajo la normalización actual (research R7).

**Actualización tras la primera ejecución real (2026-09-25)**: con el cliente HTTP de Java, Cloudflare bloqueó **10/10** consultas (las 5 ligas, con sus reintentos); la misma consulta con `curl` respondió 200. Una prueba aparte con **Playwright** (Chromium headless) obtuvo **20/20** respuestas 200 con JSON válido. Se adopta Playwright como transporte del feed ([research R12](./research.md)).

## V1 — Suite automática sin Internet

```bash
./mvnw test
```

Esperado: `BUILD SUCCESS`. Los tests de esta feature (lista en [research.md R11](./research.md)) pasan sin red; `WhoScoredRedisCacheIntegrationTest` se omite si no hay Docker. Verificar que ningún test contacte `whoscored.com` (en tests `whoscored.base-url=http://127.0.0.1:1`).

Cobertura mínima exigida por la spec (FR-029): parsing correcto · métricas ausentes · matching correcto · sin coincidencia · error de scraping · aislamiento entre jugadores · caché · delegación del scheduler.

## V2 — Por defecto no se hace scraping

```bash
./mvnw spring-boot:run
```

Sin definir `WHOSCORED_SYNC_ENABLED`. Esperado:
- La aplicación arranca normalmente y no aparece `whoscored_stats_update_started` en el log.
- `GET /players` (con `X-API-KEY`) responde igual que antes de la feature (mismo JSON, sin campos nuevos).
- La tabla `player_stats` existe (creada por `ddl-auto=update`) y está vacía.

## V3 — Ejecución programada habilitada (entorno controlado)

Para no esperar al lunes, usar un cron cercano **sólo para esta prueba manual**:

```bash
WHOSCORED_SYNC_ENABLED=true WHOSCORED_SYNC_CRON="0 */5 * * * *" ./mvnw spring-boot:run
```

Esperado en el log, en el siguiente múltiplo de 5 minutos (y **no** al arrancar):
- `whoscored_stats_update_started`
- líneas `whoscored_stats_unmatched ...` / `..._failed ...` según corresponda
- `whoscored_stats_update_finished processed=N updated=... fromCache=... unmatched=... failed=...` con `processed` = cantidad de jugadores del catálogo.
- Con `whoscored.client=browser` (default): en la consola no hay errores de Playwright y no aparece `code=browser_error`. El navegador es headless (no se abre ninguna ventana) y se cierra al terminar la ejecución.
- Hasta 20 consultas reales (4 por liga), con ~2 s entre ellas (timestamps del log de requests), más como máximo un reintento por consulta bloqueada (`whoscored_stats_request_blocked ... attempt=1`, y `attempt=2` si el reintento también falla), unos 10 s después.
- Una liga con alguna consulta bloqueada tras el reintento aparece en `whoscored_stats_league_failed ... code=blocked`; sus jugadores cuentan como `failed` y **ninguno** de ellos cambia en `player_stats`.

En la base: `SELECT count(*), max(fetched_at) FROM player_stats;` → filas para los jugadores asociados. Métricas no informadas en `NULL`, no en 0.

## V4 — Caché

Con el mismo proceso de V3, dejar correr una segunda ejecución (5 minutos después, dentro de las 24 h):
- `fromCache` ≈ `updated` de la ejecución anterior; consultas externas sólo para las ligas que tengan jugadores sin coincidencia (4 por liga).
- `redis-cli --scan --pattern 'whoscored-player-stats*'` lista claves; `redis-cli ttl <clave>` ≤ 86400.

## V5 — Tolerancia a fallas

1. Con `WHOSCORED_BASE_URL=http://127.0.0.1:1` y el scheduler habilitado: la ejecución termina con todo en `failed`, sin excepción que tumbe la app; `player_stats` conserva los datos de V3; `GET /players` responde normalmente.
2. Detener Redis durante una ejecución: aparecen `whoscored_stats_cache_unavailable` y la ejecución sigue consultando la fuente y guardando.

## V6 — Conclusión sobre el acceso real

Registrar en `research.md` (R4 y R7) el resultado de V3 contra el WhoScored real, en el entorno de despliegue y con JDK 17: porcentaje de jugadores `updated`, `unmatched` y `failed`; cuántas consultas se bloquearon en el primer intento y cuántas se recuperaron con el reintento; cuántas ligas quedaron completas; y cuántos `unmatched` son `team_not_found` por nombres de equipo abreviados. Si `blocked` es sistemático o `team_not_found` es alto, comunicarlo como limitación de la entrega y abrir la decisión con el equipo.

## Checklist de aceptación rápida

- [x] V0 contrato de fuente verificado (2026-09-25)
- [ ] Fixtures JSON reales recortados en el repo (durante la implementación)
- [ ] V1 `./mvnw test` en verde sin Internet
- [ ] V2 sin scraping por defecto; `GET /players` sin cambios
- [ ] V3 ejecución programada con logs de inicio/fin y datos en `player_stats`
- [ ] V4 segunda ejecución usa la caché
- [ ] V5 fallas aisladas, sin pérdida de datos
- [ ] V6 resultado contra WhoScored real documentado
