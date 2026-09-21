# Feature Specification: Football-Data Player Catalog

**Feature Branch**: `003-football-data-players`

**Created**: 2026-09-21

**Status**: Draft

**Input**: User description: "Feature: catálogo de jugadores a partir de Football-Data.org. Alcance: 1. Adapter que consuma la API oficial de Football-Data.org para las 5 ligas (Premier League, La Liga, Serie A, Bundesliga, Ligue 1): equipos y su plantel (nombre, equipo, posición, nacionalidad, edad si está disponible). 2. Persistir esos jugadores en la tabla players existente, guardando el id de Football-Data en el campo externalId (se va a necesitar después para correlacionar con WhoScored). 3. GET /players: lista jugadores con filtros por liga, equipo y posición, protegido con ApiKey. 4. La consulta a Football-Data.org se cachea con Redis (TTL configurable) porque el plan free tiene rate limit de 10 req/min. Si la API externa falla, el catálogo se sigue sirviendo desde los datos locales/cacheados. 5. Documentado en Swagger con esquema de seguridad ApiKey. El modelo Player, League, Position y su repository YA EXISTEN, no modificar su estructura. Esta feature agrega Service, Controller y Adapter, respetando la arquitectura en capas."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Importar catálogo oficial de jugadores (Priority: P1)

Como operadora del sistema, quiero construir y actualizar el catálogo de jugadores de las cinco ligas principales desde la fuente oficial definida, para que el mercado tenga jugadores reales asociados a sus equipos, ligas, posiciones y nacionalidades.

**Why this priority**: Sin un catálogo confiable y persistido no se pueden listar jugadores ni preparar la correlación futura con datos de rendimiento.

**Independent Test**: Puede probarse ejecutando una actualización del catálogo con datos disponibles de las cinco ligas y verificando que los jugadores queden disponibles en el repositorio local con su identificador externo conservado.

**Acceptance Scenarios**:

1. **Given** que la fuente oficial responde con equipos y planteles de las cinco ligas soportadas, **When** se actualiza el catálogo, **Then** el sistema registra jugadores con nombre, equipo, posición, nacionalidad, edad cuando esté disponible e identificador externo.
2. **Given** que un jugador ya existe con el mismo identificador externo, **When** se vuelve a actualizar el catálogo, **Then** el sistema actualiza sus datos catalogables sin duplicar el jugador.
3. **Given** que la fuente oficial omite la edad de un jugador, **When** se actualiza el catálogo, **Then** el jugador se conserva con sus demás datos y la ausencia de edad no bloquea la importación.

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

---

### User Story 4 - Descubrir el contrato en documentación interactiva (Priority: P4)

Como desarrolladora consumidora de la API, quiero ver el listado de jugadores, sus filtros, respuestas y esquema de seguridad en la documentación interactiva, para integrarme sin depender de conocimiento informal del backend.

**Why this priority**: La documentación verificable reduce errores de integración y forma parte del Definition of Done del proyecto.

**Independent Test**: Puede probarse abriendo la documentación interactiva y verificando que el contrato del catálogo muestre filtros, respuestas y credencial requerida.

**Acceptance Scenarios**:

1. **Given** que la documentación de la API está disponible, **When** una desarrolladora consulta la sección de jugadores, **Then** puede ver el contrato del listado, sus filtros y los campos devueltos.
2. **Given** que el listado requiere credencial de API, **When** una desarrolladora revisa la documentación, **Then** ve claramente el esquema de seguridad necesario para probar el endpoint.

### Edge Cases

- La fuente oficial devuelve equipos sin plantel o planteles vacíos para una liga soportada.
- La fuente oficial devuelve jugadores con posición, nacionalidad o edad incompleta.
- La fuente oficial devuelve jugadores repetidos entre solicitudes o actualizaciones.
- El valor de liga, equipo o posición solicitado no existe en el catálogo local.
- El periodo de validez del cache expira mientras la fuente externa no está disponible.
- El cliente presenta una credencial ausente, inválida o revocada.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST support catalog synchronization for Premier League, La Liga, Serie A, Bundesliga and Ligue 1.
- **FR-002**: System MUST retrieve teams and squad members for each supported league from the official Football-Data.org source.
- **FR-003**: System MUST capture each imported player's name, team, position, nationality and age when age is provided by the source.
- **FR-004**: System MUST persist imported players in the existing player catalog without changing the existing Player, League or Position data structures.
- **FR-005**: System MUST store the Football-Data player identifier in the existing external identifier field so players can be correlated with future sources.
- **FR-006**: System MUST prevent duplicate player records when the same Football-Data player is encountered in repeated imports.
- **FR-007**: System MUST allow authorized clients to list players through `GET /players`.
- **FR-008**: System MUST allow the player list to be filtered by league, team and position, independently or in combination.
- **FR-009**: System MUST require a valid API key before returning player catalog data.
- **FR-010**: System MUST cache source lookups with a configurable validity period to respect the source's low request allowance.
- **FR-011**: System MUST continue serving player listings from local or cached data when the external source is unavailable.
- **FR-012**: System MUST provide a controlled unavailable response when no local or cached catalog data exists and the external source cannot be reached.
- **FR-013**: System MUST expose the player listing contract, filters, response fields and API key security requirement in interactive API documentation.
- **FR-014**: System MUST preserve the established layered responsibility boundaries for source access, catalog rules, HTTP exposure and persistence access.
- **FR-015**: System MUST record enough operational information to diagnose failed external synchronization attempts without exposing secrets.

### Key Entities *(include if feature involves data)*

- **Player**: A football player available in the marketplace catalog. Key catalog attributes are name, team, position, nationality, optional age and external source identifier.
- **League**: One of the supported competitions used to group teams and filter catalog results.
- **Team**: A football club participating in a supported league and associated with imported players.
- **Position**: A player's football role used for filtering and future valuation behavior.
- **Catalog Source Snapshot**: The latest retrievable state from the official source, used to avoid excessive external requests and to keep local catalog reads available during source failures.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Authorized clients can retrieve the player list filtered by league, team and position in at least 95% of valid catalog queries during normal operation.
- **SC-002**: A full catalog update covers all five supported leagues and persists every player record that contains the minimum required identity and team information.
- **SC-003**: Re-running catalog synchronization with unchanged source data creates zero duplicate player records.
- **SC-004**: When the external source is unavailable after a successful prior update, authorized clients can still retrieve the last available catalog for 100% of supported filter combinations.
- **SC-005**: Repeated source refresh attempts within the configured validity period do not exceed the source allowance of 10 external requests per minute.
- **SC-006**: A developer can identify the required credential scheme, available filters and response fields for the player listing from the interactive API documentation in under 2 minutes.

## Assumptions

- The feature covers catalog population and listing only; player detail, quote history, ranking and trading behavior remain outside this feature unless already provided elsewhere.
- League, position and player repository structures already exist and are reused as-is.
- Team information can be represented through existing relationships or fields without changing the existing Player, League or Position model structures.
- Filters are optional and combinable; an omitted filter means "all values" for that dimension.
- If a player's age is unavailable from the source, the player remains valid without age.
- Local persisted data is the authoritative fallback for catalog reads when external data cannot be refreshed.
- The cache validity period is configurable by deployment or runtime configuration.
- API key validation uses the project's existing security conventions.
