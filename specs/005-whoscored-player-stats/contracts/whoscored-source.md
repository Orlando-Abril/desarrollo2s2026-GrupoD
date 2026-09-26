# Contract: Fuente WhoScored (lo que el adapter espera leer)

> **Estado: VERIFICADO en V0 (2026-09-25)** con consultas HTTP reales sin navegador a las 5 ligas (temporada `2026/2027`), primero con `curl` y después con el mismo stack del adapter (Spring `RestClient` + `JdkClientHttpRequestFactory` + Jackson 3). Ver [research.md R4–R7](../research.md). La fuente es un **feed JSON interno** de WhoScored; **no se parsea HTML**. Todo el conocimiento de la fuente (ruta, parámetros y nombres de campos) vive en el adapter y su mapper (`WhoScoredStatsMapper`).
>
> ⚠️ **El endpoint no es oficial ni estable.** No está documentado ni tiene garantías. Con el cliente HTTP de Java respondió 15/20 en una primera prueba y **0/10** en la primera ejecución real (403 de Cloudflare); con un Chromium real (Playwright) respondió **20/20**. Por eso el transporte es un navegador headless (sección 0 y [research R12](../research.md)). La estructura de abajo puede cambiar sin aviso.

## Interfaz del adapter (hacia el service)

```java
// com.example.demo.adapter.whoscored.WhoScoredAdapter
List<WhoScoredPlayerStats> fetchLeaguePlayers(League league); // 4 consultas al feed, combinadas
```

- Hace las 4 consultas de la liga (sección 2), aplicando la espera entre consultas (R10) antes de cada una; descarga con el `WhoScoredFeedClient` configurado (sección 0) y deserializa con el `ObjectMapper` de Jackson existente.
- `void endRun()`: libera el transporte al terminar una ejecución (cierra el navegador). El service lo llama en `finally`.
- Devuelve una fila por `(playerId, teamId)` con las métricas combinadas.
- **Todo o nada por liga:** la liga sólo se devuelve si las **4** respuestas se obtuvieron y son válidas. Si cualquiera falla (después del reintento, si corresponde), lanza `WhoScoredException` y no devuelve nada de esa liga. Nunca se devuelve un conjunto parcial de métricas.
- Errores ⇒ `WhoScoredException(code, message, cause)` con `code` de [configuration.md](./configuration.md#logs-formato); ver la sección 5 para el orden de clasificación:
  - bloqueo (403 o challenge de Cloudflare/Incapsula) ⇒ `blocked` (reintentable una vez)
  - redirección 3xx (p. ej. `302` a `/404.html` si cambia la ruta), otros 4xx/5xx ⇒ `http_error`; timeout/conexión ⇒ `timeout` (sin reintento)
  - cuerpo que no es JSON, JSON inválido, sin el arreglo `playerTableStats`, o filas sin `playerId`/`name`/`teamId`/`teamName` en **todas** las filas ⇒ `unexpected_structure` (sin reintento)
- Filas individuales mal formadas **no** hacen fallar la liga: se omiten (log) o sus métricas quedan `null`.

## 0. Transporte (`WhoScoredFeedClient`)

```java
// com.example.demo.adapter.whoscored.WhoScoredFeedClient
FeedResponse get(String pathAndQuery); // status HTTP + cuerpo como String UTF-8
void close();                           // libera recursos; la próxima consulta los vuelve a abrir
```

| `whoscored.client` | Implementación | Uso |
|---|---|---|
| `browser` (default) | `PlaywrightFeedClient` | App. Abre Chromium headless en la primera consulta, navega una vez a `{base-url}{browser.landing-path}` esperando `domcontentloaded` (la publicidad no termina de cargar) y, si aparece `Just a moment...`, espera a que Cloudflare lo resuelva (hasta `browser.navigation-timeout`). Cada consulta es un `fetch(pathAndQuery, {headers: {Accept: 'application/json'}})` ejecutado **dentro de la página**, con un `AbortController` limitado por `browser.navigation-timeout` (el primer `fetch` tardó ~20 s en la prueba real). |
| `http` | `HttpFeedClient` | Tests (`MockRestServiceServer`). `RestClient` sin seguir redirecciones. |

- Ambas devuelven el status y el cuerpo **sin interpretarlos**; la clasificación (sección 5) y el parseo siguen en el adapter y el mapper.
- Errores del transporte ⇒ `WhoScoredException`: `timeout` (HTTP sin respuesta a tiempo, o `fetch` abortado), `http_error` (otros errores HTTP del cliente), `browser_error` (Chromium no instalado, no arranca, falla la navegación o la evaluación del `fetch`). Ninguno se reintenta.
- La app **no descarga navegadores** (`PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`); Chromium se instala aparte ([quickstart](../quickstart.md)).

## 1. Endpoint

`GET {base-url}/statisticsfeed/1/getplayerstatistics?{parámetros}`

- La ruta va **en minúsculas**: con mayúsculas (`/StatisticsFeed/1/GetPlayerStatistics`) el servidor responde `301` a la versión en minúsculas.
- **No requiere** `Referer`, `X-Requested-With` ni el token `Model-last-Mode` (verificado en V0). Con el navegador, el `fetch` lleva además las cookies y la huella propias de Chromium, que es lo que evita el bloqueo de Cloudflare.
- La respuesta declara `Content-Type: text/html; charset=utf-8` aunque el cuerpo es JSON (con un espacio inicial). El adapter lee el cuerpo como `String` UTF-8 y lo deserializa explícitamente; no depende del `Content-Type`.
- Sin paginación: la respuesta trae **todas** las filas de la liga; el bloque `paging` viene en cero y se ignora.

### Parámetros comunes

| Parámetro | Valor |
|---|---|
| `tournamentOptions` | id de torneo de la liga (`whoscored.tournaments.<LEAGUE>`) |
| `isCurrent` | `true` (temporada en curso) |
| `teamIds`, `playerId`, `matchId`, `stageId`, `age`, `ageComparisonType`, `appearances`, `appearancesComparisonType`, `nationality`, `positionOptions`, `timeOfTheGameStart`, `timeOfTheGameEnd`, `page`, `numberOfPlayersToPick`, `sortAscending` | vacío |
| `field` | `Overall` |
| `sortBy` | `Rating` |
| `isMinApp` | `false` |
| `includeZeroValues` | `true` |

### Ids de torneo (verificados)

| `League` | `tournamentOptions` | Página de referencia |
|---|---|---|
| Premier League | `2` | `/regions/252/tournaments/2/england-premier-league` |
| LaLiga | `4` | `/regions/206/tournaments/4/spain-laliga` |
| Serie A | `5` | `/regions/108/tournaments/5/italy-serie-a` |
| Bundesliga | `3` | `/regions/81/tournaments/3/germany-bundesliga` |
| Ligue 1 | `22` | `/regions/74/tournaments/22/france-ligue-1` |

## 2. Las 4 consultas por liga

| # | `category` | `subcategory` | `statsAccumulationType` | Aporta |
|---|---|---|---|---|
| 1 | `summary` | `all` | `0` | identidad + minutos, goles, asistencias, tarjetas, rating |
| 2 | `shots` | `zones` | `2` (Total) | tiros |
| 3 | `key-passes` | `length` | `2` (Total) | pases clave |
| 4 | `tackles` | `success` | `2` (Total) | tackles |

En la consulta 1 `statsAccumulationType` no tiene efecto (V0). En las 2–4, `2` = totales de temporada y `0` = promedio por partido; se usa **siempre `2`**.

### Estructura de la respuesta

```json
{
  "playerTableStats": [
    { "playerId": 123761, "name": "Bruno Fernandes", "teamId": 32, "teamName": "Man Utd",
      "tournamentId": 2, "seasonName": "2026/2027",
      "minsPlayed": 450, "goal": 3, "assistTotal": 1, "yellowCard": 0.0, "redCard": 0.0,
      "rating": 7.3919999999999986, "...": "otros campos ignorados" }
  ],
  "paging": { "...": "ignorado" },
  "statColumns": ["..."]
}
```

Los campos desconocidos se ignoran (`@JsonIgnoreProperties(ignoreUnknown = true)`).

### Mapeo de campos

| Métrica (`WhoScoredPlayerStats`) | Consulta | Campo JSON | Tipo en la fuente | Tipo destino |
|---|---|---|---|---|
| `whoscoredPlayerId` | 1 | `playerId` | entero | `String` |
| `name` | 1 | `name` | texto UTF-8 | `String` |
| `whoscoredTeamId` | 1 | `teamId` | entero | `String` |
| `teamNames` | 1–4 | `teamName` | texto | `List<String>` (todos los distintos vistos para ese `teamId`) |
| `minutesPlayed` | 1 | `minsPlayed` | entero | `Integer` |
| `goals` | 1 | `goal` | entero | `Integer` |
| `assists` | 1 | `assistTotal` | entero | `Integer` |
| `yellowCards` | 1 | `yellowCard` | decimal (`1.0`) | `Integer` |
| `redCards` | 1 | `redCard` | decimal (`0.0`) | `Integer` |
| `rating` | 1 | `rating` | decimal (`7.3919…`) | `BigDecimal`, redondeado a 2 decimales (`HALF_UP`) |
| `shots` | 2 | `shotsTotal` | decimal (`19.0`) | `Integer` |
| `keyPasses` | 3 | `keyPassesTotal` | decimal (`13.0`) | `Integer` |
| `tackles` | 4 | `tackleWonTotal` | decimal (`5.0`) | `Integer` |

- **Tackles = `tackleWonTotal`**, correspondiente a "Tackles" en WhoScored (no `tackleTotalAttempted`).
- **No** se usan los campos por partido (`shotsPerGame`, `keyPassPerGame`, `tacklePerGame`) ni se multiplica un promedio por partidos.

## 3. Combinación de las 4 respuestas

- Clave de fila: `(playerId, teamId)`. Un jugador transferido dentro de la liga aparece **una vez por equipo**, con sus totales en ese equipo (V0: 0–6 casos por liga).
- La consulta 1 define las filas. Las consultas 2–4 completan su métrica por la misma clave; si la clave no aparece en una de ellas, esa métrica queda `null`.
- Filas de las consultas 2–4 cuya clave no está en la consulta 1 se ignoran.
- Clave repetida dentro de una misma respuesta: se usa la primera y se registra en el log.
- `teamNames`: el conjunto de `teamName` distintos vistos para el `teamId` en las 4 respuestas. V0 mostró que el resumen usa a veces un nombre corto y las detalladas uno largo: `Man Utd`/`Manchester United`, `Man City`/`Manchester City`, `Atletico`/`Atletico Madrid`, `Deportivo`/`Deportivo de A Coruna`, `Bayern`/`Bayern Munich`, `RBL`/`RB Leipzig`, `Stuttgart`/`VfB Stuttgart`, `Mainz`/`Mainz 05`, `Schalke`/`Schalke 04`, `Leverkusen`/`Bayer Leverkusen`, `Hamburg`/`Hamburger SV`, `PSG`/`Paris Saint-Germain`.
- Sólo aparecen jugadores con al menos un partido jugado en la temporada.

## 4. Reglas de interpretación

- Campo ausente o `null` ⇒ métrica `null` (FR-002). No hay celdas `-`: la fuente publica números.
- Enteros: se aceptan números JSON enteros o decimales **sin parte fraccionaria** (`19.0` ⇒ `19`). Con parte fraccionaria, negativos o no numéricos ⇒ `null` + log `whoscored_stats_invalid_metric`.
- `rating`: número en `[0, 10]`; fuera de rango o no numérico ⇒ `null` + log.
- Fila sin `playerId`, sin `name` o sin `teamId` ⇒ se omite (log).
- Filas con todas las métricas `null` se devuelven igual; el service las trata como `no_metrics`.

## 5. Bloqueos y reintento

**Clasificación de cada respuesta**, en este orden y **antes** de cualquier intento de leer JSON:

1. Status `403` ⇒ `blocked`, sin importar el cuerpo.
2. Cuerpo con un marcador de challenge ⇒ `blocked`, sin importar el status:
   - Cloudflare: `<title>Just a moment...</title>` (observado en V0), y además `challenges.cloudflare.com` o `_cf_chl_opt` (marcadores conocidos de Cloudflare, no observados todavía).
   - Incapsula: `_Incapsula_Resource`, `Incapsula incident ID`.
3. Otros 3xx/4xx/5xx ⇒ `http_error`.
4. Status 200 cuyo cuerpo, sin espacios iniciales, **no empieza con `{`** ⇒ `unexpected_structure`. No se pasa al `ObjectMapper`.
5. Recién entonces se deserializa el JSON.

Un cuerpo de bloqueo **nunca** se intenta parsear como JSON.

**Reintento** (sólo para `blocked`):

- Cada consulta tiene **como máximo un reintento**, si `whoscored.block-retry.enabled=true` (default). No es un sistema genérico de retries: sin backoff, sin librerías y sin reintentar `http_error`, `timeout` ni `unexpected_structure`.
- Antes del reintento se espera `max(whoscored.block-retry.delay, whoscored.request-delay)`: 10 s por defecto, 0 s en tests.
- Si el reintento vuelve a dar `blocked` (u otro error), la consulta falla y, por la regla de todo o nada, **la liga falla**. El service conserva los datos persistidos de sus jugadores pendientes y sigue con la siguiente liga.
- Log por cada bloqueo: `whoscored_stats_request_blocked league= category= attempt=1|2`.

## Fixtures de test (derivados de V0)

`backend/src/test/resources/whoscored/` — respuestas JSON **reales** recortadas a pocas filas (re-capturables con los comandos de [quickstart.md V0](../quickstart.md)), sin campos irrelevantes más allá de los necesarios para el mapeo. Los casos que no ocurren naturalmente (homónimos, valores inválidos) se obtienen **editando** una copia y se marcan como sintéticos en el nombre del archivo.

| Archivo | Contenido |
|---|---|
| `league-summary.json` | Consulta 1 recortada: 3–4 equipos, incluido uno con nombre corto (`Man Utd`), jugadores con diacríticos (`Fernández`, `Ødegaard`) y un jugador transferido (dos filas con el mismo `playerId`). |
| `league-shots.json` | Consulta 2 con las mismas claves (nombre largo `Manchester United`). |
| `league-key-passes.json` | Consulta 3 con las mismas claves; le falta la fila de un jugador (métrica `null`). |
| `league-tackles.json` | Consulta 4 con las mismas claves. |
| `synthetic-summary-invalid-values.json` | Consulta 1 editada: homónimos en el mismo equipo, `yellowCard` = `1.5`, `rating` = `"abc"`, campo `goal` ausente, fila sin `playerId`. |
| `synthetic-unexpected-structure.json` | JSON válido sin `playerTableStats`. |
| `blocked-cloudflare.html` | Cuerpo de challenge de Cloudflare (`<title>Just a moment...</title>`), recortado del 403 real de V0; se usa con status 403 y también con status 200 (detección por marcador). |
| `blocked-incapsula.html` | Cuerpo típico de challenge de Incapsula (`_Incapsula_Resource`), sintético. |
