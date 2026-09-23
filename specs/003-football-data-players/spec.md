# Feature Specification: Football-Data Player Catalog

**Feature Branch**: `003-football-data-players`

**Created**: 2026-09-21

**Status**: Draft

**Input**: User description: "Feature: catálogo de jugadores a partir de Football-Data.org. Alcance: 1. Adapter que consuma la API oficial de Football-Data.org para las 5 ligas (Premier League, La Liga, Serie A, Bundesliga, Ligue 1): equipos y su plantel (nombre, equipo, posición, nacionalidad, edad si está disponible). 2. Persistir esos jugadores en la tabla players existente, guardando el id de Football-Data en el campo externalId (se va a necesitar después para correlacionar con WhoScored). 3. GET /players: lista jugadores con filtros por liga, equipo y posición, protegido con ApiKey. 4. La consulta a Football-Data.org se cachea con Redis (TTL configurable) porque el plan free tiene rate limit de 10 req/min. Si la API externa falla, el catálogo se sigue sirviendo desde los datos locales/cacheados. 5. Documentado en Swagger con esquema de seguridad ApiKey. El modelo Player, League, Position y su repository YA EXISTEN, no modificar su estructura. Esta feature agrega Service, Controller y Adapter, respetando la arquitectura en capas."

## Clarifications

### Session 2026-09-23

- Q: Cuando una sincronización falla y sus cambios se revierten, ¿cómo tiene que quedar guardado el evento de auditoría `FAILED`? → A: Los eventos `STARTED` y `FAILED` se persisten en una transacción independiente que se confirma aunque la sincronización se revierta.
- Q: Si falla el guardado de un jugador puntual durante la sincronización, ¿qué parte del trabajo se tiene que descartar? → A: Solo ese jugador: cada jugador y su asignación de tokens se persisten en su propia transacción; la falla se audita como `PLAYER_FAILED`, la sincronización continúa y el resultado final es `PARTIAL_FAILURE` si falló al menos un jugador o una liga.
- Q: Mientras el catálogo todavía no tuvo ninguna sincronización exitosa, ¿cada cuánto se tiene que reintentar la sincronización? → A: Ante cualquier falla, reintentar cada 5 minutos (configurable, mínimo 1 minuto) con un WARN explícito en cada intento, hasta la primera sincronización exitosa; después solo aplica el cron normal.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Importar catálogo oficial de jugadores (Priority: P1)

Como operadora del sistema, quiero construir y actualizar el catálogo de jugadores de las cinco ligas principales desde la fuente oficial definida, para que el mercado tenga jugadores reales asociados a sus equipos, ligas, posiciones y nacionalidades.

**Why this priority**: Sin un catálogo confiable y persistido no se pueden listar jugadores ni preparar la correlación futura con datos de rendimiento.

**Independent Test**: Puede probarse ejecutando una actualización del catálogo con datos disponibles de las cinco ligas y verificando que los jugadores queden disponibles en el repositorio local con su identificador externo conservado.

**Acceptance Scenarios**:

1. **Given** que la fuente oficial responde con equipos y planteles de las cinco ligas soportadas, **When** se actualiza el catálogo, **Then** el sistema registra jugadores con nombre, equipo, posición, nacionalidad, edad cuando esté disponible e identificador externo.
2. **Given** que un jugador ya existe con el mismo identificador externo, **When** se vuelve a actualizar el catálogo, **Then** el sistema actualiza sus datos catalogables sin duplicar el jugador.
3. **Given** que la fuente oficial omite la edad de un jugador, **When** se actualiza el catálogo, **Then** el jugador se conserva con sus demás datos y la ausencia de edad no bloquea la importación.
4. **Given** que la fuente oficial omite la nacionalidad o devuelve una posición ausente/no reconocida, **When** se actualiza el catálogo, **Then** el jugador se persiste si conserva identificador, nombre y equipo, usando nacionalidad nula y un conjunto de posiciones vacío según corresponda.
5. **Given** que se importa por primera vez un jugador, **When** la transacción finaliza, **Then** quedan emitidos exactamente 100 tokens para ese jugador, asignados íntegramente al superusuario y con precio base inicial de 1 crédito por token.
6. **Given** que el catálogo local está vacío al iniciar la aplicación o vence el cron configurado, **When** se dispara la actualización automática, **Then** el sistema ejecuta una única sincronización a la vez sin requerir un endpoint administrativo.
7. **Given** que un jugador del lote no puede persistirse (por ejemplo, viola una restricción de la base), **When** se actualiza el catálogo, **Then** solo ese jugador y su asignación de tokens se descartan, se registra un evento `PLAYER_FAILED`, los demás jugadores válidos quedan persistidos y la sincronización finaliza como `PARTIAL_FAILURE`.
8. **Given** que la aplicación arranca sin una sincronización exitosa previa y el superusuario configurado todavía no existe o no es `ADMIN`, **When** se lo provisiona mientras la aplicación sigue corriendo, **Then** el siguiente reintento de arranque (a más tardar un intervalo configurado después) sincroniza el catálogo sin reiniciar la aplicación.

---

### User Story 2 - Consultar jugadores con filtros protegidos (Priority: P2)

Como consumidor autorizado de la API, quiero listar jugadores filtrando por liga, equipo y posición, para encontrar rápidamente los activos disponibles en el catálogo.

**Why this priority**: El listado filtrable es el contrato mínimo del catálogo y habilita la experiencia de búsqueda exigida por el proyecto.

**Independent Test**: Puede probarse solicitando el listado con una credencial válida y combinaciones de filtros, verificando que los resultados correspondan solo al criterio pedido.

**Acceptance Scenarios**:

1. **Given** que existen jugadores persistidos para varias ligas, equipos y posiciones, **When** un cliente autorizado solicita el listado sin filtros, **Then** recibe el catálogo disponible.
2. **Given** que existen jugadores de Premier League y Bundesliga, **When** un cliente autorizado filtra por Premier League, **Then** recibe solo jugadores asociados a esa liga.
3. **Given** que existen jugadores de distintos equipos y posiciones, **When** un cliente autorizado combina filtros por liga, equipo y posición, **Then** recibe solo jugadores que cumplen todos los filtros.
4. **Given** que un cliente no presenta una credencial válida, **When** solicita el listado de jugadores, **Then** el sistema rechaza la consulta sin exponer el catálogo.

---

### User Story 3 - Mantener servicio ante fallas externas (Priority: P3)

Como consumidor autorizado del catálogo, quiero que el listado siga funcionando aunque la fuente externa falle o alcance su límite de uso, para que las pantallas y procesos internos no dependan de la disponibilidad inmediata de terceros.

**Why this priority**: La resiliencia ante fuentes externas es obligatoria para sostener los endpoints de lectura y evitar interrupciones del mercado.

**Independent Test**: Puede probarse cargando datos locales previos, simulando indisponibilidad de la fuente externa y verificando que el listado siga respondiendo desde datos persistidos o cacheados.

**Acceptance Scenarios**:

1. **Given** que hay datos locales o cacheados del catálogo, **When** la fuente externa falla durante una actualización, **Then** el sistema conserva el catálogo consultable con la última información disponible.
2. **Given** que la fuente externa limita la cantidad de solicitudes permitidas, **When** se consultan equipos y planteles repetidamente dentro del periodo de validez configurado, **Then** el sistema reutiliza información cacheada para evitar solicitudes innecesarias.
3. **Given** que no existen datos locales ni cacheados todavía, **When** la fuente externa falla, **Then** el sistema responde de forma controlada indicando que el catálogo no está disponible temporalmente.
4. **Given** que una sincronización crea o actualiza un jugador, emite tokens o cambia el estado del proceso, **When** se persiste el cambio, **Then** el sistema agrega un evento de auditoría inmutable con el ID del superusuario configurado como actor del sistema, correlation ID, timestamp, detalle y estados anterior/posterior sanitizados.

---

### User Story 4 - Descubrir el contrato en documentación interactiva (Priority: P4)

Como desarrolladora consumidora de la API, quiero ver el listado de jugadores, sus filtros, respuestas y esquema de seguridad en la documentación interactiva, para integrarme sin depender de conocimiento informal del backend.

**Why this priority**: La documentación verificable reduce errores de integración y forma parte del Definition of Done del proyecto.

**Independent Test**: Puede probarse abriendo la documentación interactiva y verificando que el contrato del catálogo muestre filtros, respuestas y credencial requerida.

**Acceptance Scenarios**:

1. **Given** que la documentación de la API está disponible, **When** una desarrolladora consulta la sección de jugadores, **Then** puede ver el contrato del listado, sus filtros y los campos devueltos.
2. **Given** que el listado requiere credencial de API, **When** una desarrolladora revisa la documentación, **Then** ve claramente el esquema de seguridad necesario para probar el endpoint.
3. **Given** que el catálogo nunca fue inicializado o que la inicialización falló sin datos locales, **When** una desarrolladora revisa la documentación, **Then** encuentra documentada la respuesta `503 catalog_unavailable`.

### Edge Cases

- La fuente oficial devuelve equipos sin plantel o planteles vacíos para una liga soportada.
- La fuente oficial devuelve jugadores con posición, nacionalidad o edad incompleta.
- La fuente oficial devuelve jugadores repetidos entre solicitudes o actualizaciones.
- El valor de liga, equipo o posición solicitado no existe en el catálogo local.
- El periodo de validez del cache expira mientras la fuente externa no está disponible.
- El cliente presenta una credencial ausente, inválida o revocada.
- Dos disparadores de sincronización intentan ejecutarse simultáneamente en la única instancia soportada.
- Dos registros o procesos intentan persistir el mismo `externalId` de forma concurrente.
- La sincronización aborta por un error interno después de registrar `STARTED`: el evento `FAILED` queda persistido aunque el resto del trabajo se revierta.
- Un jugador individual no puede persistirse: se descarta solo ese jugador (y sus tokens), se audita `PLAYER_FAILED` y la sincronización continúa con el resto.
- Todos los jugadores intentados fallan al persistir aunque las ligas respondieron: la ejecución termina `FAILED` (no cuenta como snapshot exitoso), los reintentos de arranque continúan y `GET /players` sigue devolviendo `503` si no hay jugadores locales.
- La aplicación arranca antes de que el superusuario exista o tenga rol `ADMIN`: la sincronización falla sin escribir jugadores ni tokens, se loguea un WARN y se reintenta en cada intervalo de arranque hasta el primer éxito.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST support catalog synchronization for Premier League, La Liga, Serie A, Bundesliga and Ligue 1.
- **FR-002**: System MUST retrieve teams and squad members for each supported league from the official Football-Data.org source.
- **FR-003**: System MUST capture each imported player's name and team, capture position, nationality and age when available, and preserve otherwise valid players when any optional attribute is absent or unrecognized.
- **FR-004**: System MUST persist imported players in the existing player catalog without changing the existing Player, League or Position data structures.
- **FR-005**: System MUST store the Football-Data player identifier in the existing external identifier field so players can be correlated with future sources.
- **FR-006**: System MUST prevent duplicate player records for the same Football-Data identifier in repeated and concurrent imports through application-level upsert plus a database uniqueness constraint.
- **FR-007**: System MUST allow authorized clients to list players through `GET /players`.
- **FR-008**: System MUST allow the player list to be filtered by league, team and position, independently or in combination.
- **FR-009**: System MUST require a valid API key before returning player catalog data.
- **FR-010**: System MUST cache source lookups with a configurable validity period to respect the source's low request allowance.
- **FR-011**: System MUST continue serving player listings from local or cached data when the external source is unavailable.
- **FR-012**: System MUST return `503 catalog_unavailable` from `GET /players` when the catalog has never completed a successful synchronization and no local players exist; a successfully synchronized but legitimately empty catalog MUST return `200 []`.
- **FR-013**: System MUST expose the player listing contract, filters, response fields, `200/400/401/503` responses and API key security requirement in interactive API documentation.
- **FR-014**: System MUST preserve the established layered responsibility boundaries for source access, catalog rules, HTTP exposure and persistence access.
- **FR-015**: System MUST record enough operational information to diagnose failed external synchronization attempts without exposing secrets.
- **FR-016**: System MUST trigger catalog synchronization once on application readiness when no successful catalog snapshot exists and subsequently through a configurable scheduler, without exposing a public synchronization endpoint. While no successful catalog snapshot exists, any failed run (including a missing or non-`ADMIN` configured superuser) MUST be retried on a configurable bootstrap interval (default 5 minutes, minimum 1 minute), logging a WARN on each failed attempt that states the cause and, for the superuser case, that `MARKET_SUPERUSER_USERNAME` must reference an existing `ADMIN` user; once a successful snapshot exists, bootstrap retries stop and only the regular cron applies. A run that fails because the superuser is missing or not `ADMIN` has no valid actor and is therefore logged but not audited.
- **FR-017**: System MUST atomically initialize every newly imported player with exactly 100 issued tokens, all assigned to the unique pre-provisioned `ADMIN` user whose username is configured by `MARKET_SUPERUSER_USERNAME`, at a base price of 1 credit per token; synchronization MUST fail in a controlled manner before persisting players when that configured user is missing or is not `ADMIN`, and repeated imports MUST NOT issue additional tokens.
- **FR-018**: System MUST create database indexes for player league, player team and player position, and MUST enforce uniqueness of non-null Football-Data external identifiers without changing the existing `Player`, `League` or `Position` Java structures.
- **FR-019**: System MUST append immutable audit events for every created/updated player, initial token allocation and synchronization lifecycle transition; each event MUST contain actor User ID, correlation ID, timestamp, action/detail, entity identity and sanitized previous/next state, without secrets or full external payloads. `STARTED` and `FAILED` lifecycle events MUST be persisted in an independent transaction that commits even when the synchronization work is rolled back, so every failed run leaves a durable `FAILED` event.
- **FR-020**: System MUST propagate or generate a correlation ID for every HTTP request, return it in the response header and include it in structured logs; non-HTTP synchronization jobs MUST generate their own correlation ID.
- **FR-021**: System MUST expose health information for the application, PostgreSQL and Redis and metrics for external-call latency, synchronization duration and error counts.
- **FR-022**: System MUST pass the repository GitHub Actions build and test workflow and the configured SonarCloud Quality Gate with fewer than 10 minor issues before the feature is considered complete.
- **FR-023**: System MUST isolate synchronization failures per player: each player upsert together with its initial token allocation and its audit events MUST run in its own transaction; a failure persisting one player MUST roll back only that player, append a `PLAYER_FAILED` audit event in an independent transaction (with the player's external identifier and a sanitized failure code) and continue with the remaining players. The run's terminal status MUST be `COMPLETED` when no player or league failed; `FAILED` when at least one player or league failed and no player was persisted (created or updated) in the run, which includes every league failing; and `PARTIAL_FAILURE` otherwise. A "successful catalog snapshot" (FR-012, FR-016) means at least one run whose terminal status is `COMPLETED` or `PARTIAL_FAILURE`.

### Key Entities *(include if feature involves data)*

- **Player**: A football player available in the marketplace catalog. Key catalog attributes are name, team, position, nationality, optional age and external source identifier.
- **League**: One of the supported competitions used to group teams and filter catalog results.
- **Team**: A football club participating in a supported league and associated with imported players.
- **Position**: A player's football role used for filtering and future valuation behavior.
- **Catalog Source Snapshot**: The latest retrievable state from the official source, used to avoid excessive external requests and to keep local catalog reads available during source failures.
- **Player Token Allocation**: The immutable initial issuance state for a player: total supply 100, initial owner the configured pre-provisioned `ADMIN` superuser and base price 1 credit. It is stored outside the existing `Player` structure and created atomically with a new catalog player.
- **Catalog Audit Event**: Append-only record for player creation/update/failure (`PLAYER_CREATED`, `PLAYER_UPDATED`, `PLAYER_FAILED`), initial token allocation or synchronization lifecycle (`STARTED`, `COMPLETED`, `PARTIAL_FAILURE`, `FAILED`), containing actor User ID, correlation ID, timestamp, action/detail, entity identity, sanitized previous/next state and optional aggregate counts.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An automated query matrix covering no filters, each individual filter, every two-filter combination and all three filters returns the expected local results in 100% of cases.
- **SC-002**: A full catalog update covers all five supported leagues and persists every player record that contains the minimum required identity and team information; in a batch where one player fails to persist, 100% of the remaining valid players are persisted, exactly one `PLAYER_FAILED` event is recorded for the failed player and the run ends as `PARTIAL_FAILURE`.
- **SC-003**: Re-running catalog synchronization with unchanged source data creates zero duplicate player records.
- **SC-004**: When the external source is unavailable after a successful prior update, authorized clients can still retrieve the last available catalog for 100% of supported filter combinations.
- **SC-005**: Repeated source refresh attempts within the configured validity period do not exceed the source allowance of 10 external requests per minute.
- **SC-006**: A developer can identify the required credential scheme, available filters and response fields for the player listing from the interactive API documentation in under 2 minutes.
- **SC-007**: Every newly imported player has exactly 100 tokens assigned to the configured `ADMIN` superuser at a base price of 1 credit, a repeated/concurrent import creates zero additional supply, and a missing or non-ADMIN configured user causes zero player/token writes.
- **SC-008**: Database inspection confirms indexes for league, team and position plus a unique constraint for every non-null `externalId`.
- **SC-009**: Every tested HTTP response contains a correlation ID; scheduled synchronization logs and immutable audit events contain the same job correlation ID; `/actuator/health` reports application, PostgreSQL and Redis status.
- **SC-010**: A cold full synchronization from the single supported application instance makes at most five external requests, automatic HTTP-level retries are disabled, scheduled and bootstrap-retry runs are separated by at least one minute and repeated calls within the TTL make zero additional external requests.
- **SC-011**: GitHub Actions finishes successfully and SonarCloud reports a passed Quality Gate with fewer than 10 minor issues.

## Assumptions

- The feature covers catalog population, constitution-required initial token issuance and listing only; player detail, quote history, ranking and subsequent trading behavior remain outside this feature unless already provided elsewhere.
- League, position and player repository structures already exist and are reused as-is.
- Team information can be represented through existing relationships or fields without changing the existing Player, League or Position model structures.
- Filters are optional and combinable; an omitted filter means "all values" for that dimension.
- If a player's age is unavailable from the source, the player remains valid without age.
- If nationality is unavailable it remains null; if position is absent or unrecognized the player remains valid with an empty position set, provided `externalId`, name and team are present.
- Local persisted data is the authoritative fallback for catalog reads when external data cannot be refreshed.
- The cache validity period is configurable by deployment or runtime configuration.
- API key validation uses the project's existing security conventions.
- This feature supports one active backend instance. Horizontal scaling requires a distributed scheduler lock and is outside this feature; the rate-limit guarantee is scoped to the supported single-instance topology.
- Token allocation and catalog audit use new persistence structures and do not alter the existing `Player`, `League` or `Position` Java structures.
- The superuser is provisioned outside this feature, has `Role.ADMIN`, is selected uniquely by the username in `MARKET_SUPERUSER_USERNAME`, and is never created with a default password by this feature.
- The catalog refresh scheduler in this feature does not implement or claim compliance with the constitution's separate weekly quote/statistics scheduler or `POST /quotes/recalculate`; those remain outside this feature's scope.
