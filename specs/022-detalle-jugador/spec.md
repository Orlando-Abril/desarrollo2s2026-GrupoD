# Feature Specification: Detalle de un jugador

**Feature Branch**: `feature/detalle-jugador`

**Created**: 2026-10-26

**Status**: Draft

**Input**: User description: "Endpoint GET /players/{id}: detalle de un jugador del catálogo con sus estadísticas de temporada y cotización actual para la estrategia activa, sin consultar fuentes externas, protegido y documentado en OpenAPI."

## Contexto y restricciones

El catálogo ya permite listar jugadores y sincronizarlos. Esta feature completa la consulta individual que necesita la pantalla de detalle del frontend, combinando los datos locales del jugador con las estadísticas de su temporada y, cuando exista, su cotización más reciente para la estrategia activa.

La consulta es exclusivamente de lectura y debe operar con datos ya persistidos. Está autorizado agregar `GET /{id}` a `PlayerController`, sin alterar el comportamiento ni el contrato de `GET /players` o `POST /players/sync`. `PlayerResponse` y `GlobalExceptionHandler` no se modifican.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consultar el detalle completo (Priority: P1)

Como usuario autenticado, quiero consultar un jugador del catálogo con sus estadísticas de temporada y cotización actual, para ver en una sola pantalla su identidad, rendimiento y valor vigente.

**Why this priority**: Es el objetivo central de la feature y habilita la pantalla obligatoria de detalle del frontend.

**Independent Test**: Preparar un jugador con estadísticas y varias cotizaciones para la estrategia activa, consultar su identificador y comprobar que la respuesta contiene el jugador, sus métricas y únicamente la cotización activa más reciente.

**Acceptance Scenarios**:

1. **Given** un jugador existente con estadísticas de temporada y dos cotizaciones para la estrategia activa, **When** un usuario autenticado consulta su detalle, **Then** recibe los datos del jugador, sus estadísticas y la cotización más reciente con valor, score, estado, estrategia, versión y fecha de cálculo.
2. **Given** un jugador con cotizaciones de la estrategia activa y de otra estrategia, **When** se consulta su detalle, **Then** la cotización devuelta corresponde exclusivamente a la estrategia activa.
3. **Given** dos cotizaciones de la estrategia activa con la misma fecha de cálculo, **When** se consulta el detalle, **Then** se devuelve la registrada más recientemente según el orden determinista existente del historial.

---

### User Story 2 - Consultar un jugador con datos complementarios ausentes (Priority: P1)

Como usuario autenticado, quiero poder abrir el detalle de un jugador aunque todavía no tenga estadísticas o cotización, para que la ausencia de datos complementarios no bloquee la navegación del catálogo.

**Why this priority**: Las fuentes y procesos que producen estadísticas y cotizaciones pueden no haber procesado aún a todos los jugadores; el detalle del jugador debe seguir siendo utilizable.

**Independent Test**: Consultar jugadores existentes sin estadísticas, sin cotización activa o sin ambas, y verificar una respuesta exitosa con los datos del jugador y valores nulos en las secciones ausentes.

**Acceptance Scenarios**:

1. **Given** un jugador existente sin estadísticas de temporada, **When** se consulta su detalle, **Then** la respuesta es exitosa, incluye sus datos y presenta `stats` como nulo.
2. **Given** un jugador existente sin cotización para la estrategia activa, **When** se consulta su detalle, **Then** la respuesta es exitosa, incluye sus datos y presenta `quote` como nulo, aunque existan cotizaciones de otra estrategia.
3. **Given** estadísticas existentes con alguna métrica desconocida, **When** se consulta el detalle, **Then** la sección `stats` se devuelve y conserva esa métrica como nula, sin reemplazarla por cero.

---

### User Story 3 - Recibir errores y documentación previsibles (Priority: P2)

Como integrador de la API, quiero autenticación, validación, errores y documentación completos, para consumir el detalle sin depender de reglas implícitas ni exponer información a pedidos no autorizados.

**Why this priority**: El frontend y otros consumidores necesitan distinguir de forma segura un identificador inválido, un jugador inexistente y la falta de credenciales.

**Independent Test**: Consultar con ambas formas válidas de autenticación, sin credenciales, con un identificador no numérico y con uno numérico inexistente; luego contrastar respuestas y esquema con la documentación publicada.

**Acceptance Scenarios**:

1. **Given** un pedido sin credenciales válidas, **When** se consulta cualquier detalle, **Then** se responde 401 sin devolver datos ni revelar si el jugador existe.
2. **Given** un identificador de ruta no numérico y credenciales válidas, **When** se consulta el detalle, **Then** se responde 400 con el formato de error existente.
3. **Given** un identificador numérico que no pertenece a ningún jugador y credenciales válidas, **When** se consulta el detalle, **Then** se responde 404 con código `player_not_found`.
4. **Given** la documentación OpenAPI publicada, **When** un integrador inspecciona la operación de detalle, **Then** encuentra su parámetro, respuesta completa, posibles errores y las alternativas `bearerAuth` y `apiKeyAuth`.

### Edge Cases

- Si el jugador existe pero no tiene estadísticas ni cotización activa, la respuesta es 200 con `stats` y `quote` nulos.
- Si sólo existen cotizaciones de estrategias no activas, `quote` es nulo.
- Si hay varias cotizaciones de la estrategia activa, se selecciona la de fecha de cálculo más reciente; ante empate, prevalece la registrada más recientemente según el orden estable del historial.
- Las métricas individuales ausentes dentro de un registro de estadísticas permanecen nulas y no convierten en nula toda la sección.
- Una edad o nacionalidad ausente y un conjunto vacío de posiciones conservan la representación ya definida para el jugador del catálogo.
- La autenticación se evalúa antes de revelar la existencia del jugador o sus datos; un pedido sin credenciales recibe 401 incluso si el identificador sería inválido o inexistente.
- Un identificador no numérico, fuera del rango de `Long`, igual a cero o negativo se rechaza con 400 `validation_error`; únicamente un identificador positivo sin jugador asociado responde 404 `player_not_found`.

## Requirements *(mandatory)*

### Functional Requirements

**Contrato y selección de datos**

- **FR-001**: El sistema MUST exponer `GET /players/{id}` para consultar un único jugador del catálogo local.
- **FR-002**: Una respuesta exitosa MUST contener tres secciones: `player`, siempre presente; `stats`, presente o nula; y `quote`, presente o nula.
- **FR-003**: La sección `player` MUST conservar sin cambios la representación existente del catálogo: `id`, `externalId`, `fullName`, `team`, `league`, `positions`, `nationality`, `age` y `marketValue`.
- **FR-004**: Si existen estadísticas del jugador, `stats` MUST incluir `minutesPlayed`, `goals`, `assists`, `shots`, `keyPasses`, `tackles`, `dribbles`, `yellowCards`, `redCards`, `rating` y `fetchedAt`.
- **FR-005**: Si el jugador no tiene registro de estadísticas, `stats` MUST ser nulo. Si el registro existe pero una métrica no fue informada, esa métrica MUST conservarse nula y MUST NOT sustituirse por cero.
- **FR-006**: El sistema MUST resolver la estrategia activa al procesar cada pedido y seleccionar la cotización más reciente del jugador para el código de esa estrategia.
- **FR-007**: Si existen varias cotizaciones candidatas, la selección MUST priorizar la fecha de cálculo descendente y, ante igualdad, el orden de registro descendente, de manera consistente con el historial existente.
- **FR-008**: Si existe una cotización actual para la estrategia activa, `quote` MUST incluir `value`, `score`, `status`, `strategy`, `version` y `calculatedAt`, preservando los valores registrados.
- **FR-009**: Si el jugador no tiene cotizaciones para la estrategia activa, `quote` MUST ser nulo, incluso cuando tenga cotizaciones para otras estrategias.

**Seguridad, validación y errores**

- **FR-010**: El endpoint MUST aceptar como alternativas una credencial bearer válida o una API key válida, conforme a las reglas de acceso vigentes para las lecturas del catálogo.
- **FR-011**: Un pedido sin credenciales o con credenciales inválidas o vencidas MUST responder 401 antes de devolver datos o revelar la existencia del jugador.
- **FR-012**: El parámetro `id` MUST aceptar únicamente enteros positivos representables como `Long`; un texto no numérico, un número fuera del rango de `Long`, cero o un valor negativo MUST responder 400 con código `validation_error` y el formato de error existente.
- **FR-013**: Un identificador positivo representable como `Long` sin jugador asociado MUST responder 404 mediante el error existente con código `player_not_found`.

**Resiliencia y documentación**

- **FR-014**: La consulta MUST usar exclusivamente datos locales ya persistidos y MUST NOT invocar, directa ni indirectamente, fuentes externas, procesos de sincronización, scrapers ni recálculos.
- **FR-015**: La ausencia de estadísticas o cotización MUST NOT transformar en error la consulta de un jugador existente.
- **FR-016**: OpenAPI MUST documentar la ruta, el parámetro `id`, las tres secciones y todos sus campos, su nulabilidad, y las respuestas 200, 400, 401 y 404 con sus esquemas.
- **FR-017**: OpenAPI MUST declarar `bearerAuth` y `apiKeyAuth` como alternativas de seguridad para la operación.

**Alcance protegido**

- **FR-018**: La feature MUST limitar el cambio autorizado en `PlayerController` a agregar el método `GET /{id}` y MUST conservar sin cambios funcionales los métodos existentes `GET /players` y `POST /players/sync`.
- **FR-019**: La feature MUST NOT modificar `PlayerResponse` ni `GlobalExceptionHandler`.
- **FR-020**: Si la planificación concluye que cumplir el contrato exige modificar un archivo de Entrega 1 fuera del método autorizado, el trabajo MUST detenerse y solicitar aprobación explícita, indicando el archivo, el cambio mínimo y una alternativa que lo evite.

### Key Entities

- **Detalle de jugador**: Vista agregada de lectura formada por un jugador obligatorio, sus estadísticas opcionales y su cotización actual opcional.
- **Jugador**: Recurso persistido del catálogo identificado por `id`; conserva exactamente los datos ya publicados por la representación existente del catálogo.
- **Estadísticas de temporada**: Último conjunto local de métricas del jugador para la temporada en curso. La sección completa y cada métrica individual pueden estar ausentes por causas diferentes.
- **Cotización actual**: Último registro inmutable del jugador para el código de la estrategia activa; aporta valor, score, estado, estrategia, versión y fecha de cálculo.
- **Estrategia activa**: Definición vigente que determina qué serie de cotizaciones representa el valor actual del jugador.
- **Error de API**: Representación de error ya existente, utilizada para validación, autenticación y jugador inexistente.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En el 100% de los escenarios con un jugador existente, la consulta devuelve los nueve datos del catálogo sin alterarlos y representa correctamente como presentes o nulas las secciones de estadísticas y cotización.
- **SC-002**: En el 100% de los escenarios con múltiples estrategias o cotizaciones, la respuesta incluye exactamente la última cotización de la estrategia activa o `quote` nulo cuando esa serie no existe.
- **SC-003**: En el 100% de los escenarios con métricas ausentes, cada ausencia se conserva como nula y ningún valor desconocido se presenta como cero.
- **SC-004**: El 100% de los pedidos autenticados con identificador no numérico, fuera del rango de `Long`, igual a cero o negativo responde 400 `validation_error`; el 100% de los identificadores positivos inexistentes responde 404 `player_not_found`; y el 100% de los pedidos sin credenciales válidas responde 401 sin datos del jugador.
- **SC-005**: Al menos el 95% de 100 consultas consecutivas sobre un catálogo local de 2.500 jugadores entrega el detalle completo en menos de 1 segundo en el entorno de validación, aun cuando las fuentes externas estén indisponibles.
- **SC-006**: Un integrador puede identificar en OpenAPI, sin leer código interno, el parámetro, todos los campos y valores nulos posibles, los cuatro resultados HTTP y ambas alternativas de autenticación.
- **SC-007**: El 100% de las pruebas existentes del listado y la sincronización de jugadores conserva su resultado, y no hay cambios en los contratos existentes ni en el manejo global de errores.

## Assumptions

- La respuesta de detalle agrupa el contrato en las secciones de primer nivel `player`, `stats` y `quote`; esto permite reutilizar sin modificar la representación existente del jugador y distinguir con claridad las ausencias parciales.
- Las estadísticas persistidas representan el último estado disponible de la temporada en curso; la consulta no recibe una temporada como parámetro ni reconstruye estadísticas históricas.
- `fetchedAt` se expone para que el consumidor pueda reconocer la antigüedad del dato; el identificador específico del proveedor de estadísticas no forma parte del detalle funcional.
- La cotización “actual” significa el último registro de la estrategia activa al momento del pedido, sin exigir que su versión coincida con la versión actual de la definición; la respuesta informa la versión que efectivamente produjo el registro.
- Los mecanismos y reglas de autenticación ya existentes para el catálogo se reutilizan sin introducir permisos adicionales.
- El formato común de error existente es suficiente para expresar el 400 por conversión de identificador y el 404 `player_not_found` sin modificar `GlobalExceptionHandler`.

## Out of Scope

- Sincronizar el catálogo, actualizar estadísticas o recalcular cotizaciones durante la consulta.
- Consultar fuentes externas o usar su disponibilidad para decidir la respuesta.
- Consultar estadísticas históricas, otra temporada o cotizaciones de una estrategia elegida por el consumidor.
- Devolver el historial o la evolución de cotizaciones, cubiertos por el endpoint específico de historial.
- Modificar los contratos de listado o sincronización, `PlayerResponse` o `GlobalExceptionHandler`.
- Implementar o modificar la pantalla del frontend que consumirá este detalle.
