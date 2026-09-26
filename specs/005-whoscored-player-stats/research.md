# Research: Estadísticas de rendimiento de jugadores desde WhoScored

**Feature**: `005-whoscored-player-stats` | **Date**: 2026-09-25 | **Spec**: [spec.md](./spec.md)

Cada decisión sigue el formato *Decision / Rationale / Alternatives considered*. Criterio rector: la solución más simple que cumpla la spec, reutilizando la infraestructura existente.

> **Actualizado tras V0 (2026-09-25).** La validación V0 de [quickstart.md](./quickstart.md) cambió la fuente: WhoScored **no** publica las estadísticas en el HTML, sino en un feed JSON interno. R1, R2, R4, R5, R6, R7 y R11 reflejan ese resultado. No se parsea HTML.
>
> **Actualizado tras la primera ejecución real (2026-09-25).** Cloudflare bloqueó **10/10** consultas del cliente HTTP de Java, mientras `curl` y un Chromium real (Playwright) obtuvieron 200. El transporte del feed pasa a ser un **navegador headless con Playwright** ([R12](#r12-transporte-del-feed-navegador-headless-con-playwright)). El formato de datos, el mapper, el matching, la caché, la persistencia y el scheduler no cambian.

---

## R1. Granularidad de las consultas a WhoScored

**Decision**: Obtener los datos **por liga**. Por cada liga se hacen **4 consultas** al feed JSON `getplayerstatistics` filtrado por torneo (resumen, tiros, pases clave, tackles; ver [contracts/whoscored-source.md](./contracts/whoscored-source.md)). Cada respuesta trae **todos** los jugadores de la liga con `playerId`, `name`, `teamId` y `teamName`. El adapter combina las 4 respuestas por `(playerId, teamId)` y el matching se hace localmente dentro de las filas de cada equipo.

**Rationale**:
- Volumen: 5 ligas × 4 consultas = **20 consultas por ejecución**, contra ~100 del diseño por equipo y ~5.000 del diseño por jugador. Con la espera de 2 s (FR-016a) una ejecución completa hace ~40 s de esperas.
- Verificado en V0: el feed filtrado por torneo devuelve la liga completa en una sola respuesta, sin paginación (PL 426 filas / 20 equipos, LaLiga 463 / 20, Serie A 454 / 20, Bundesliga 368 / 18, Ligue 1 394 / 18).
- El matching exige "mismo equipo" (FR-005): las filas traen `teamId`/`teamName`, así que se agrupan por equipo y se detectan homónimos dentro del mismo equipo (FR-007).
- No hace falta una lista de equipos previa: sin página de torneo y sin parsing de HTML.

**Unidad de actualización y de falla: la liga (todo o nada).** Una liga se actualiza sólo si sus **4** respuestas se obtuvieron y son válidas (cada consulta con a lo sumo un reintento ante bloqueo, R4). Si falta cualquiera, **no se persiste nada** de esa liga: sus jugadores pendientes quedan `failed` y conservan sus datos previos (FR-011), y el proceso sigue con las demás ligas (FR-019, FR-020). Nunca se persiste un conjunto parcial de métricas (p. ej. resumen sin tackles), para que cada `player_stats` sea un conjunto coherente de una misma actualización (FR-010). Dentro de una liga descargada con éxito, los errores siguen aislándose por equipo (`team_not_found`/`team_ambiguous`) y por jugador (matching, `no_metrics`, `persistence_error`).

**Alternatives considered**:
- *Por equipo (`teamIds={id}`)*: también funciona (verificado con Manchester United y Real Madrid), pero necesita conocer los ids de equipo. Sin HTML, la única fuente de esos ids es el mismo feed por liga, así que agrega ~96×4 consultas sin beneficio. Rechazada.
- *Por jugador (búsqueda + ficha)*: ~50× más consultas. Rechazada por volumen y riesgo de bloqueo.

## R2. Caché "por jugador" con obtención por liga

**Decision**: Cachear en Redis **un resultado por jugador local** (clave = `Player.id`, valor = métricas + id de WhoScored + fecha de obtención). En cada liga, primero se buscan en caché todos sus jugadores; sólo si queda **al menos uno sin resultado vigente** se descarga la liga (las 4 consultas, una vez por liga y por ejecución). Sólo se cachean jugadores **asociados con éxito**; ni fallas ni jugadores sin coincidencia se cachean (FR-015).

**Rationale**: Cumple literalmente "cachear por jugador" (FR-013) y evita consultas cuando toda la liga está vigente (FR-014, SC-005). La espera entre consultas vive en el adapter, así que un jugador resuelto desde caché nunca la dispara (FR-016a).

**Consecuencia aceptada**: si una liga tiene un jugador sin coincidencia, una segunda ejecución dentro de las 24 h vuelve a descargar esa liga (4 consultas). Con frecuencia semanal esto es irrelevante; se documenta como limitación.

**Alternatives considered**: cachear la respuesta completa de la liga (clave = liga). Más eficiente para re-ejecuciones, pero no es "por jugador" y obliga a cachear filas de jugadores que no están en el catálogo. Rechazada por no ajustarse al requerimiento.

## R3. Uso de Redis y tolerancia a su caída

**Decision**: Reutilizar el `CacheManager` existente (`CacheConfig`) agregando **una** caché nueva `whoscored-player-stats` con TTL propio (`whoscored.cache-ttl`, 24 h por defecto) y serializador tipado (mismo patrón que `football-data-competition-teams`, que ya resolvió el problema de `LinkedHashMap` en los cache hits). El service accede a la caché de forma programática (`Cache#get` / `Cache#put`) envolviendo cada acceso en `try/catch`: si Redis falla, se registra un warning y se sigue consultando la fuente (FR-016).

**Rationale**: `@Cacheable` sobre el adapter no encaja porque la obtención es por liga y la caché es por jugador. El acceso programático es explícito, fácil de testear con `spring.cache.type=simple` (ya usado en tests) y no requiere `CacheErrorHandler` global, que cambiaría el comportamiento de la caché de Football-Data.

**Alternatives considered**: `RedisTemplate` directo (duplica configuración de serialización); `CacheErrorHandler` global (afecta la feature existente). Rechazadas.

## R4. Fuente real de WhoScored y acceso sin navegador — VERIFICADO EN V0 (acceso intermitente; ver R12)

> **El feed es un endpoint interno: no es oficial, no está documentado y no es estable.** Con el cliente Java real se bloquearon 5 de 20 consultas (detalle abajo).

**Hallazgos de V0 con `curl` (2026-09-25)**:
- Las páginas de torneo y de equipo responden **HTTP 200** a un cliente HTTP simple. **No** apareció el 403/challenge de Incapsula observado durante la planificación. El sitio carga un script de fingerprinting (`cx-resources.oddschecker.com/fingerprint/verify-client.js`) que hoy no bloquea.
- Las páginas de equipo **no** contienen las estadísticas: el contenedor `#statistics-table-summary` llega vacío y no hay enlaces `/players/...`. La tabla la completa JavaScript (`tables-team.js`) a partir del feed interno `/statisticsfeed/1/getplayerstatistics`.
- Ese feed responde **HTTP 200 con JSON** a `curl` **sin cookie, sin `Referer`, sin `X-Requested-With` y sin el token `Model-last-Mode`** (el token existe en el HTML de la página pero no fue necesario). Probado también con `User-Agent: Java-http-client/17`.
- Particularidades: la ruta debe ir en **minúsculas** (con mayúsculas responde `301` a la versión en minúsculas); la respuesta declara `Content-Type: text/html; charset=utf-8` aunque el cuerpo es JSON (con un espacio inicial); el texto viene en UTF-8 con diacríticos (`Mbappé`, `Güler`). Un endpoint inexistente (p. ej. `getteamstatistics`) responde `302` a `/404.html`.
- Todas las consultas hechas con `curl` (~40) respondieron 200. Ojo: una prueba con `User-Agent: Java-http-client/17` también se hizo con `curl`, así que **no** representaba al cliente Java.

**Prueba con el cliente real del adapter (2026-09-25)**:
- Stack: Spring `RestClient` 7.0.9 + `JdkClientHttpRequestFactory` + `java.net.http.HttpClient` (connect timeout 5 s, read timeout 15 s, igual que `FootballDataConfig`), `User-Agent` de navegador, `Accept: application/json`, y `ObjectMapper` de Jackson 3.1.5. Librerías tomadas del classpath de Maven del proyecto, sin modificar el proyecto. Se ejecutó con **JDK 21.0.6** compilando con `--release 17`, porque no había un JDK 17 instalado. **Queda pendiente repetirla con JDK 17** (p. ej. en CI o en el entorno de despliegue).
- 20 consultas (5 ligas × 4), secuenciales, con 2 s entre ellas:

| Liga | summary | shots | key-passes | tackles |
|---|---|---|---|---|
| Premier League | 200 | 200 | 200 | **403** |
| LaLiga | **403** | 200 | 200 | **403** |
| Serie A | 200 | 200 | **403** | 200 |
| Bundesliga | 200 | 200 | 200 | 200 |
| Ligue 1 | **403** | 200 | 200 | 200 |

- **15/20 respuestas 200** con JSON válido. Cada una leída como `String` y parseada con Jackson; en todas, las filas traen la métrica esperada, con las mismas cantidades de filas y equipos que con `curl`.
- **5/20 respuestas 403**, intermitentes y sin patrón por liga ni por categoría. El cuerpo es un challenge de **Cloudflare** (`<title>Just a moment...</title>`, ~7,7 KB de HTML), no de Incapsula.
- Con la regla de todo o nada por liga (R1) y **sin** reintento, en esta corrida sólo se habría actualizado 1 de 5 ligas (Bundesliga).
- No se determinó si el bloqueo depende del cliente (la huella TLS/HTTP de Java frente a la de `curl`), del volumen o del azar. Para no insistir contra el sitio, no se repitieron consultas.

**Decision**:
1. ~~Implementar el adapter con el cliente HTTP ya usado por el proyecto y sin navegador automatizado.~~ **Reemplazado por [R12](#r12-transporte-del-feed-navegador-headless-con-playwright)**: el cliente HTTP (`HttpFeedClient`) queda sólo para tests; en la app el feed se descarga con Playwright.
2. Consumir el **feed JSON** con el **Jackson ya presente** en el proyecto (ver R6). No se parsea HTML en ningún punto.
3. Concentrar **todo** el conocimiento de la fuente (ruta del feed, parámetros, nombres de campos JSON) en el adapter y su mapper, documentado en [contracts/whoscored-source.md](./contracts/whoscored-source.md) (verificado).
4. Fixtures de test: respuestas JSON reales recortadas en `src/test/resources/whoscored/` (ver contrato).
5. **Bloqueo** = status `403` o cuerpo con marcador de challenge de **Cloudflare** (`Just a moment...`) o de **Incapsula** (`_Incapsula_Resource`, `Incapsula incident ID`). Se detecta **antes** de leer JSON y un cuerpo de bloqueo nunca se parsea como JSON. Criterios exactos en [contracts/whoscored-source.md §5](./contracts/whoscored-source.md).
6. **Un único reintento** por consulta, sólo ante bloqueo, con espera configurable (`whoscored.block-retry.enabled`, `whoscored.block-retry.delay`, 10 s por defecto). Se implementa en el adapter con el mismo mecanismo de espera de R10: sin librerías, sin backoff y sin política genérica de retries.
7. Si el reintento también falla, o hay cualquier otro error (redirección, 5xx, timeout, JSON inválido), la **liga** falla según R1: se registra, no se persiste nada de esa liga, se conservan los datos previos, el proceso sigue con las demás ligas y la próxima ejecución reintenta.

**Rationale**: El feed da exactamente los campos numéricos requeridos, en totales, sin scraping de HTML ni navegador. Se reutilizan cliente HTTP y Jackson, sin tecnologías nuevas. El reintento único apunta a los 403 intermitentes observados sin agregar complejidad. Si los bloqueos fueran independientes, con la tasa observada (25 %) una consulta fallaría después del reintento ~6 % de las veces, y una liga completa se obtendría ~77 % de las veces (0,9375⁴). Es una estimación gruesa sobre 20 muestras; V6 mide el valor real.

**Riesgo residual (a comunicar)**: el feed **no es oficial ni estable**. Ya bloquea de forma intermitente (Cloudflare) y puede cambiar de ruta o de campos, o empezar a exigir el token `Model-last-Mode`, cookies o fingerprinting. Si los bloqueos aumentan, la feature sigue cumpliendo la spec (tolera la falla, conserva datos, no inventa) pero actualiza menos ligas o ninguna. El mapper aislado permite ajustar sin tocar service ni scheduler. Leer el token del HTML queda como contingencia **no implementada** (implicaría parsear HTML). V6 mide el resultado real.

**Alternatives considered**:
- *Parsear el HTML de las páginas de equipo*: imposible, las métricas no están en el HTML (V0).
- *Navegador headless (Selenium/Playwright), proxies*: inicialmente excluidos. **La primera ejecución real mostró bloqueo sistemático del cliente Java y el equipo aprobó Playwright: ver [R12](#r12-transporte-del-feed-navegador-headless-con-playwright).** Los proxies siguen excluidos.
- *Retries múltiples con backoff o una librería de resiliencia*: rechazados; un único reintento cubre el caso observado sin complejidad ni dependencias.

## R5. Totales vs. promedios por partido — VERIFICADO EN V0

**Hallazgo**:
- En la consulta de **resumen** (`category=summary&subcategory=all`) el parámetro `statsAccumulationType` **no tiene efecto**: tiros, pases clave y tackles sólo aparecen por partido (`shotsPerGame`, `keyPassPerGame`, `tacklePerGame`). Minutos, goles, asistencias y tarjetas sí son totales; el rating es un promedio.
- En las consultas **detalladas** con `statsAccumulationType=2` los valores son **totales de temporada**: `shots/zones` → `shotsTotal`, `key-passes/length` → `keyPassesTotal`, `tackles/success` → `tackleWonTotal` (y `tackleTotalAttempted`). Con `0` devuelven promedios. Ejemplo (Bruno Fernandes, 5 PJ): `shotsTotal` 19 vs. 3.8 por partido; `keyPassesTotal` 13 vs. 2.6; `tackleWonTotal` 5 vs. 1.0.

**Decision**: Persistir **totales de temporada** como enteros (minutos, goles, asistencias, tiros, pases clave, tackles, amarillas, rojas) y el rating como decimal. Tiros, pases clave y tackles se leen de las consultas detalladas con `statsAccumulationType=2`. **Tackles = `tackleWonTotal`**, que es la métrica que WhoScored muestra como "Tackles" (su promedio coincide con `tacklePerGame` del resumen). **No** se convierte un promedio en total (FR-002).

**Rationale**: Métricas crudas y homogéneas (todas son totales publicados de la temporada en curso), comparables entre jugadores.

## R6. Interpretación de la respuesta (JSON con Jackson existente)

**Decision**: Obtener el cuerpo como `String` (con el transporte de [R12](#r12-transporte-del-feed-navegador-headless-con-playwright)) y deserializarlo con el **`ObjectMapper` de Jackson 3 (`tools.jackson`) ya provisto por Spring Boot** a records propios del adapter (`@JsonIgnoreProperties(ignoreUnknown = true)`), en un único mapper del adapter. Se descarta Jsoup. La única dependencia nueva es Playwright (R12), sólo como transporte.

- Se lee como `String` y se deserializa explícitamente porque el servidor responde `Content-Type: text/html`: los message converters JSON del `RestClient` no lo aceptarían.
- Los campos numéricos se leen como `Double`/`BigDecimal` (tarjetas y totales detallados llegan como `1.0`, `19.0`) y se convierten a entero **sólo si no tienen parte decimal**; si no, `null` + log.

**Rationale**: El feed es JSON; Jackson ya está en el proyecto y en el `CacheConfig`. Menos código y más robusto que cualquier parsing de HTML.

**Alternatives considered**: Jsoup/regex sobre HTML (innecesario: los datos no están en el HTML); `Map<String,Object>` sin records (menos tipado, más casts). Rechazadas.

## R7. Normalización y matching

**Decision**: Una clase utilitaria `NameNormalizer` con dos funciones puras:
- `person(String)`: `Normalizer.Form.NFD` → quitar diacríticos (`\p{M}`); transliterar letras sin descomposición (`ø→o`, `æ→ae`, `ß→ss`, `đ→d`, `ł→l`, `ı→i`); minúsculas (`Locale.ROOT`); signos (`'`, `-`, `.`) → espacio; colapsar espacios; `trim`.
- `team(String)`: lo mismo y además quitar tokens institucionales completos: `fc, cf, afc, sc, ac, as, ssc, sv, vfb, vfl, tsg, rc, rcd, ud, cd, sd, ogc, osc, stade, club, de` y, **tras V6**, `us, ss, aj, es, ca, sco, acf, bc, cfc, fsv, calcio, balompie, futbol` (lista cerrada, en código), más cualquier token **sólo numérico** (años de fundación como `1907`, `04`, `1.`).

**Nombres de equipo en la fuente (V0)**: para un mismo `teamId`, el feed de resumen usa a veces un nombre corto y los detallados uno más largo (p. ej. `Man Utd`/`Manchester United`, `Atletico`/`Atletico Madrid`, `RBL`/`RB Leipzig`, `Bayern`/`Bayern Munich`, `PSG`/`Paris Saint-Germain`). Un equipo WhoScored tiene entonces **un conjunto de nombres** (todos los `teamName` vistos para su `teamId` en las 4 respuestas de la liga).

Matching (en el service): equipo local ↔ equipo WhoScored cuando `team(local)` es igual a `team()` de **alguno** de los nombres de ese `teamId`, dentro de la misma liga; **tras V6**, si no hay ninguna igualdad, por inclusión (ver V6: "empieza con" y luego "contiene todas las palabras", nombre de WhoScored ≥ 4 letras, único candidato); exactamente 1 `teamId` → ok; 0 o >1 → todos los jugadores de ese equipo quedan "sin coincidencia" (motivo `team_not_found`/`team_ambiguous`). Jugador ↔ fila por igualdad de `person()` entre las filas de ese `teamId`; exactamente 1 → asociado; 0 → `player_not_found`; >1 → `player_ambiguous`. Sin similitud aproximada (FR-008): es igualdad exacta contra nombres publicados por la fuente, no un alias manual.

**Riesgo abierto (medir en V6)**: el catálogo guarda el nombre completo de Football-Data (p. ej. con sufijo `FC`), y varios nombres de WhoScored son abreviaturas o variantes (`Tottenham`, `Leeds`, `Inter`, `Brighton`, `Borussia M.Gladbach`). Esos equipos quedarán `team_not_found` con la regla actual. Mejorarlo (alias, similitud) requiere cambiar FR-006/FR-008 y queda fuera de esta entrega salvo decisión del equipo.

**Otros hallazgos de V0**:
- Sólo aparecen jugadores con al menos 1 partido en la temporada (`includeZeroValues=true` no incluye a quienes no jugaron). Los demás quedan `player_not_found`.
- Un jugador transferido dentro de la misma liga aparece **una vez por equipo**, con sus totales en cada uno (p. ej. `Grealish` en Everton y en Man City). Por eso la clave de una fila es `(playerId, teamId)` y el matching usa sólo las filas del equipo local.
- No hubo homónimos dentro de un mismo equipo en las 5 ligas.

**Rationale**: Determinístico, sin dependencias, cubre los casos del spec (tildes, `Ødegaard`, `N'Golo Kanté`, `Arsenal FC`/`Arsenal`).

**Alternatives considered**: Levenshtein/Jaro-Winkler: fuera de alcance. Alias manuales: **inicialmente excluidos; tras V6 se aprobó una lista fija de equivalencias de equipos en el código** (`TeamAliases`, ver V6), descartando `application.properties` y una tabla en la base (sin endpoint para editarla).

## R8. Persistencia y esquema

**Decision**: Nueva entidad JPA `PlayerStats` → tabla `player_stats`, relación `@OneToOne` **unidireccional** hacia `Player` con clave primaria compartida (`@MapsId`, columna `player_id`). `Player` no se modifica. El esquema se genera con el mecanismo vigente (`spring.jpa.hibernate.ddl-auto=update` en runtime, `create-drop` en tests); sin Flyway (FR-012).

**Rationale**: PK compartida garantiza a nivel de base "como máximo un conjunto por jugador" (FR-010) sin índice único adicional, y un upsert trivial (`findById(playerId)` → actualizar o crear). Unidireccional evita tocar `Player` y el catálogo (FR-018).

**Alternatives considered**: columnas nuevas en `players` (mezcla catálogo y rendimiento; lo desaconseja la spec); tabla con historial (fuera de alcance).

**Reemplazo total**: cada guardado sobrescribe **todas** las columnas de métricas (incluidas las `null`), según la Clarification 1. Si la fila del jugador no trae **ninguna** métrica reconocible, el adapter la marca como no válida y el service la trata como falla (no se guarda, no se cachea).

## R9. Scheduler

**Decision**:
- `@EnableScheduling` en una `@Configuration` propia (`SchedulingConfig`) anotada con `@ConditionalOnProperty(name = "whoscored.sync.enabled", havingValue = "true")`, y el bean `PlayerStatsScheduler` con la misma condición. Por defecto `false` en todas las configuraciones (FR-023).
- `@Scheduled(cron = "${whoscored.sync.cron}", zone = "${whoscored.sync.zone:UTC}")` con valor por defecto semanal `0 0 4 * * MON` (lunes 04:00).
- El método del scheduler sólo loguea inicio, llama a `PlayerStatsService.updateAllStats()`, loguea el resumen devuelto y captura cualquier excepción inesperada para loguearla (FR-025, FR-026).
- Sin ejecución al arrancar (cron no dispara en el arranque; no hay `ApplicationRunner`) (FR-024).
- No superposición (FR-027): el `TaskScheduler` por defecto de Spring Boot tiene **un solo hilo** y un trigger cron no se vuelve a disparar mientras la ejecución anterior sigue en curso. No se agregan locks.

**Rationale**: Mecanismo estándar pedido por la spec; condicional por propiedad evita que en tests/dev exista siquiera el bean.

**Alternatives considered**: `fixedDelay` (semántica de "cada N después de terminar", menos legible para "una vez por semana"); flag `enabled` chequeado dentro del método (el bean existiría igual y el test de "no se ejecuta" sería menos directo); ShedLock/Quartz (prohibidos).

## R10. Espera entre consultas

**Decision**: El adapter guarda el instante de su última consulta real y, antes de cada nueva consulta, espera `max(0, requestDelay − transcurrido)` con `Thread.sleep`. `whoscored.request-delay` = `2s` por defecto, `0s` en tests (FR-016a). Como el adapter sólo se invoca ante un cache miss, los hits no esperan. La espera aplica también entre las 4 consultas de una misma liga. Antes del único reintento ante bloqueo (R4) la espera es `max(block-retry.delay, request-delay)`: 10 s por defecto, 0 s en tests.

**Rationale**: Una ejecución a la vez (R9), por lo que no hay concurrencia que coordinar. Ubicarlo en el adapter mantiene al service sin conocimiento del protocolo externo.

## R11. Estrategia de tests

**Decision** (todos sin Internet, FR-028):

| Test | Tipo | Cubre |
|---|---|---|
| `WhoScoredStatsMapperTest` | unit + fixtures JSON | mapeo correcto y combinación de las 4 respuestas por `(playerId, teamId)`, métricas ausentes, valor no entero/negativo/no numérico, JSON sin `playerTableStats`, cuerpo HTML (challenge), fila sin métricas, jugador transferido (dos filas) |
| `NameNormalizerTest` | unit | tildes, `ø`, apóstrofos, sufijos de equipo |
| `WhoScoredAdapterTest` | `MockRestServiceServer` + fixtures JSON/HTML | URLs y parámetros pedidos (4 por liga), respuesta con `Content-Type: text/html` leída como JSON, 403 y challenge de Cloudflare/Incapsula (con status 403 y con 200) → `blocked` sin parsear JSON; 403 seguido de 200 → éxito con exactamente 1 reintento; 403 + 403 → falla sin tercer intento; reintento deshabilitado → falla al primer 403; 3xx/5xx/timeout/cuerpo no JSON → sin reintento; espera entre consultas; una consulta fallida de las 4 → la liga no devuelve filas |
| `PlayerStatsServiceTest` | Mockito + `ConcurrentMapCacheManager` | matching correcto (incluido nombre de equipo alternativo), sin coincidencia, homónimos, equipo no encontrado, liga fallida (`blocked` tras el reintento) → nada persistido y datos previos intactos, las demás ligas se procesan igual, aislamiento entre jugadores, cache hit sin consulta, no cachear fallas, caché caída, resumen |
| `PlayerStatsRepositoryTest` | `@DataJpaTest` (H2) | un registro por jugador, reemplazo total con `null`, `Player` intacto |
| `PlayerStatsSchedulerTest` | unit + `ApplicationContextRunner` | delegación en el service; bean ausente con `enabled=false`/por defecto, presente con `true` |
| `WhoScoredRedisCacheIntegrationTest` | Testcontainers (`disabledWithoutDocker`) | serialización tipada del valor cacheado y expiración por TTL |

| `WhoScoredFeedClientConfigTest` | `ApplicationContextRunner` | `whoscored.client=http` crea `HttpFeedClient`; `browser` crea `PlaywrightFeedClient` **sin** abrir el navegador (apertura perezosa) |

No se prueba el disparo real del cron (spec, sección 9). **Ningún test abre un navegador** (R12): los tests usan `whoscored.client=http` con `MockRestServiceServer`; el `PlaywrightFeedClient` se valida manualmente (quickstart V3).

## R12. Transporte del feed: navegador headless con Playwright

**Hallazgo (2026-09-25)**:
- **Primera ejecución real de la app** (JDK 22, `RestClient` + `java.net.http.HttpClient`): **10/10 consultas bloqueadas** con 403 de Cloudflare, en los 5 primeros intentos y en los 5 reintentos. Las 5 ligas quedaron `league_failed code=blocked` (2.649 jugadores `failed`), sin datos modificados.
- Inmediatamente después, la misma consulta con `curl` desde la misma conexión: **200**. El bloqueo depende del **cliente** (huella TLS/HTTP de Java), no de la IP.
- **Prueba con Playwright** 1.63.0 (Chromium 153, headless, sin tocar el proyecto): navegar a la página del torneo (esperando sólo `domcontentloaded`, porque la publicidad no termina de cargar), pasar Cloudflare y pedir el feed con `fetch` desde la página: **20/20 consultas 200 con JSON válido**, con las mismas filas que en V0 (426 / 463 / 454 / 368 / 394).

**Decision**:
1. El adapter delega la descarga en una interfaz `WhoScoredFeedClient` (`FeedResponse get(String pathAndQuery)` → status + cuerpo) con dos implementaciones, elegidas por `whoscored.client`:
   - `PlaywrightFeedClient` (`browser`, **default en la app**): abre Chromium headless **perezosamente** en la primera consulta de una ejecución, navega una vez a `whoscored.browser.landing-path` (la página de un torneo) esperando `domcontentloaded` y el fin del challenge, y hace cada consulta con `fetch` desde la página (mismas cookies y huella del navegador). Cierra el navegador al terminar la ejecución (`WhoScoredAdapter#endRun()`, llamado por el service en `finally`) y al apagar la app.
   - `HttpFeedClient` (`http`, **usado en tests**): el `RestClient` actual.
2. **Todo lo demás no cambia**: URL y parámetros del feed, clasificación de bloqueos (403 / Cloudflare / Incapsula, antes de leer JSON), único reintento, todo o nada por liga, mapper, matching, caché, persistencia, scheduler.
3. La app **no descarga navegadores sola**: se crea Playwright con `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` y Chromium se instala una vez con el CLI de Playwright (quickstart). Si falta el navegador o no arranca, la consulta falla con `browser_error` y la liga queda `failed` (tolerancia a fallas, FR-020).
4. Playwright se usa **sólo en el hilo del scheduler** (un hilo, R9), porque sus objetos no son thread-safe.

**Rationale**: Es el cambio mínimo que resuelve el bloqueo observado: el navegador sólo reemplaza al cliente HTTP y el resto del diseño y de los tests se conserva. Un Chromium real pasa Cloudflare donde el cliente Java no.

**Costos aceptados**:
- Una dependencia nueva (`com.microsoft.playwright:playwright`). Su jar incluye el driver de Node para varias plataformas, así que el artefacto de la app crece de forma notoria.
- Chromium instalado en la máquina que ejecuta la app (~700 MB con su variante headless; en Linux, además, dependencias del sistema con `install --with-deps`).
- Más memoria y unos segundos extra mientras dura una ejecución semanal.
- Sigue siendo un endpoint no oficial: el navegador no garantiza que Cloudflare no lo bloquee en el futuro (una sola prueba 20/20).

**Alternatives considered**:
- *Selenium*: también funcionaría, pero depende del Chrome instalado y del driver, y el `fetch` desde la página es menos directo. Rechazada frente a Playwright.
- *Ajustar headers del cliente Java*: sin garantía; el bloqueo probablemente es por la huella TLS, que los headers no cambian. Rechazada.
- *Usar el navegador para renderizar y leer el HTML de las tablas*: innecesario; el `fetch` del feed JSON es más simple y reutiliza el mapper.
- *Proxies*: excluidos.

## V6. Resultado de la ejecución real con Playwright (2026-09-25)

Ejecución manual de la app (IntelliJ, `whoscored.client=browser`, cron cada 5 min sólo para la prueba; JDK del proyecto en IntelliJ). Log: `whoscored-test.log` (local, no versionado).

| Ejecución | Navegador | `processed` | `updated` | `fromCache` | `unmatched` | `failed` | Duración |
|---|---|---|---|---|---|---|---|
| 18:00 | `challengePassed=true` | 2649 | 1002 | 0 | 1647 | 0 | ~45 s |
| 18:05 | `challengePassed=true` | 2649 | 1002 | 1002 | 1647 | 0 | ~40 s |
| 18:10 | `challengePassed=true` | 2649 | 1002 | 1002 | 1647 | 0 | ~47 s |

- **Acceso (R4/R12)**: 0 bloqueos (`request_blocked`), 0 `league_failed`, 0 `browser_error`. Las 5 ligas se obtuvieron completas en las 3 ejecuciones.
- **Caché (R2)**: desde la segunda ejecución los 1002 asociados salen de la caché. Las ligas igual se vuelven a descargar porque tienen jugadores sin coincidencia (consecuencia aceptada en R2).
- **Asociación (R7)**: 1002/2649 (**37,8 %**) asociados; 1647 (62,2 %) sin coincidencia:
  - `team_not_found`: **1171 jugadores de 42 equipos** (de ~96). Es el riesgo abierto de R7 materializado: el catálogo guarda el nombre completo de Football-Data y la normalización actual no lo iguala con los nombres de WhoScored. Ejemplos: `US Sassuolo Calcio`, `SS Lazio`, `AJ Auxerre`, `Como 1907`, `Hull City AFC`, `Newcastle United FC`, `Tottenham Hotspur FC`, `Brighton & Hove Albion FC`, `FC Bayern München`, `FC Internazionale Milano`, `Olympique de Marseille`, `Borussia Mönchengladbach`, `Bayer 04 Leverkusen`, `RC Deportivo La Coruña`.
  - `player_not_found`: 476 jugadores en 54 equipos (hasta 17 por equipo). Esperable en parte: el feed sólo incluye jugadores con al menos un partido en la temporada (R7), y algunos nombres difieren entre fuentes.
  - `player_ambiguous` / `team_ambiguous`: 0.
- **Conclusión**: el transporte con Playwright resuelve el bloqueo. La limitación principal de la entrega pasa a ser el **matching de equipos** (44 % de los jugadores). Mejorarlo exige cambiar la regla de FR-006/FR-008 (p. ej. ampliar la lista cerrada de tokens institucionales o asociar equipos por coincidencia de jugadores): queda como decisión del equipo.
- **Ajuste aplicado tras V6 (opción 1, aprobada)**: se amplió la lista cerrada de tokens de equipo (R7) y se ignoran los tokens numéricos. Sigue siendo igualdad exacta tras normalizar, dentro de lo que permite FR-006 ("sufijos institucionales comunes"); no hay alias ni similitud (FR-008). Verificado sobre los nombres reales: ninguna ambigüedad nueva entre equipos de una misma liga, y los equipos que ya coincidían siguen coincidiendo (se quitan los mismos tokens de ambos lados). Simulación sobre los 42 equipos sin coincidencia: **22 pasan a coincidir (~627 jugadores)**. Los 20 restantes necesitan equivalencias que no son sufijos (`Tottenham Hotspur`↔`Tottenham`, `Newcastle United`↔`Newcastle`, `Bayern München`↔`Bayern Munich`, `Internazionale`↔`Inter`, `Olympique de Marseille`↔`Marseille`, etc.) y quedan como limitación. A medir en la próxima ejecución real.
- **Segundo ajuste tras V6 (aprobado): equipos por inclusión.** Si no hay igualdad exacta del equipo normalizado, se prueba en orden: (1) el nombre local **empieza con** el de WhoScored (por caracteres: `tottenham hotspur`↔`tottenham`, `internazionale milano`↔`inter`, `stade brestois`→`brestois`↔`brest`); (2) si eso no da un único candidato, el nombre local **contiene todas las palabras** del de WhoScored (`olympique marseille`↔`marseille`, `racing lens`↔`lens`). Sólo con **un único** candidato en la liga y nombres de WhoScored de al menos 4 letras normalizadas (evita siglas). La igualdad exacta tiene prioridad y el paso (1) evita falsos positivos como `espanyol barcelona`↔`barcelona` o `internazionale milano`↔`milan`. Cada asociación por inclusión se registra (`whoscored_stats_team_matched ... rule=prefix|contains`) para auditarla. Simulado con los nombres reales: **39 de los 42 equipos** sin coincidencia quedan asociados, todos correctamente; siguen sin asociar `Borussia Mönchengladbach` (`Borussia M.Gladbach`), `Olympique Lyonnais` (`Lyon`) y `Stade Rennais` (`Rennes`). Cambia la redacción de FR-005 (spec, Clarifications 2026-09-25); el nombre del jugador sigue siendo igualdad exacta.
- **Tercer ajuste tras V6 (aprobado): equivalencias fijas de equipos.** Para los 3 equipos que ninguna regla resuelve se agregó `service/TeamAliases` (nombre en el catálogo → nombre en WhoScored): `Borussia Mönchengladbach`→`Borussia M.Gladbach`, `Olympique Lyonnais`→`Lyon`, `Stade Rennais FC 1901`→`Rennes`. Se comparan nombres normalizados, tiene prioridad sobre las demás reglas y, si el equipo equivalente no está en la liga, siguen las reglas habituales. Se registra como `whoscored_stats_team_matched ... rule=alias`. Con esto, según la simulación, **los 42 equipos** sin coincidencia de la primera ejecución quedan asociados. Mantenimiento: cada equipo nuevo con nombre muy distinto requiere agregar una línea (y un test) y volver a desplegar.
- **Medición real tras los tres ajustes (21:00)**: `processed=2649 updated=1794 fromCache=0 unmatched=855 failed=0` → **67,7 % asociados** (antes 37,8 %). **0 `team_not_found` y 0 `team_ambiguous`: los 96 equipos asociados**, 20 de ellos por las reglas nuevas (14 `prefix`, 3 `contains`, 3 `alias`), todos revisados y correctos. 0 bloqueos y 0 ligas fallidas. Los 855 restantes son `player_not_found` (~9 por equipo): el feed tiene ~2.105 jugadores (sólo los que jugaron al menos un partido) contra 2.649 del catálogo, así que al menos ~544 no pueden asociarse; el resto son nombres de jugador escritos distinto entre fuentes, que se mantienen sin asociar (FR-005: nombre de jugador exacto).
- **Falla pasajera observada (19:20)**: en una ejecución, 4 de 5 ligas fallaron con `code=http_error`; el diagnóstico inmediato posterior dio 20/20 respuestas 200 y la ejecución de las 21:00 no tuvo fallas. Probable limitación por volumen de pruebas (cron cada 5 min). Se agregó `detail=` al log `whoscored_stats_league_failed` con lo que respondió la fuente, para diagnosticar la próxima vez.
- **V5 (tolerancia a fallas, manual)**: (a) con `WHOSCORED_BASE_URL=http://127.0.0.1:1` y la caché de WhoScored vaciada, las 5 ligas fallaron con `code=browser_error`, la ejecución terminó (`updated=0 failed=2649`) sin errores `ERROR` y **ningún registro de `player_stats` cambió** (comparación contra una copia previa: 0 diferencias); (b) con Redis (Memurai) detenido durante la ejecución, se registró `whoscored_stats_cache_unavailable operation=get` y se siguió consultando la fuente y guardando (`updated=391` en las ligas obtenidas). Ambas pasan.
- Observación menor: en el archivo de log de IntelliJ los caracteres acentuados aparecen como `�`; es sólo la codificación del archivo de consola, no afecta los datos ni el matching.
