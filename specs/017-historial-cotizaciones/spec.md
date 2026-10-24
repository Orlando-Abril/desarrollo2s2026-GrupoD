# Feature Specification: Historial de cotizaciones de un jugador

**Feature Branch**: `N/A (no se creó una rama: no hay hook before_specify)`

**Created**: 2026-10-24

**Status**: Draft

**Input**: User description: "Endpoint GET /players/{id}/quotes para consultar el historial de cotizaciones guardadas de un jugador o la cotización vigente en una fecha dada, con filtros por estrategia y fechas, autenticación, errores consistentes y documentación OpenAPI."

## Contexto y restricciones

La feature 013 ya creó el registro inmutable `PlayerQuote`, el catálogo de estrategias y las consultas de historial y de cotización vigente. La feature 016 ya creó el área de cotizaciones, su manejo acotado de errores y el formato común de error. Esta feature expone esas capacidades de lectura sin crear ni recalcular cotizaciones.

El contrato nuevo pertenece al área de cotizaciones aunque su ruta esté anidada bajo el recurso jugador. `PlayerController` y `GlobalExceptionHandler`, ambos de Entrega 1, no se modifican. Tampoco se modifica ningún otro archivo de Entrega 1 sin aprobación previa del equipo. El diseño previsto permite cumplir el alcance extendiendo los componentes creados por las features 013 y 016, por lo que no hay una necesidad de cambio protegido que requiera aclaración.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consultar el historial de cotizaciones (Priority: P1)

Como consumidor autenticado de la API, quiero obtener la evolución de la cotización de un jugador para una estrategia y, opcionalmente, un intervalo de fechas, para mostrar y analizar cómo cambió su valor en el tiempo.

**Why this priority**: El historial cronológico es el objetivo principal de la feature y habilita el gráfico de evolución exigido para el detalle de un jugador.

**Independent Test**: Con un jugador que tenga cotizaciones de dos estrategias en distintas fechas, consultar su historial sin filtros, por estrategia y por intervalos abiertos o cerrados, y verificar contenido, orden y exclusión de registros ajenos.

**Acceptance Scenarios**:

1. **Given** un jugador existente con tres cotizaciones de la estrategia activa y una de otra estrategia, **When** un consumidor autenticado consulta su historial sin parámetros, **Then** recibe sólo las tres cotizaciones de la estrategia activa, ordenadas desde la más antigua hasta la más reciente.
2. **Given** un jugador con cotizaciones OFFICIAL y MOMENTUM, **When** se consulta con `strategy=MOMENTUM`, **Then** se reciben sólo las cotizaciones MOMENTUM, incluyendo en cada registro valor, score, estado, estrategia, versión y fecha de cálculo.
3. **Given** cotizaciones antes, dentro y después de un intervalo, **When** se consulta con `from` y `to`, **Then** se reciben sólo las calculadas desde el inicio de `from` hasta el fin de `to`, inclusive, en orden cronológico ascendente.
4. **Given** un jugador existente sin cotizaciones para la estrategia y el intervalo elegidos, **When** se consulta el historial, **Then** la respuesta es exitosa y contiene una lista vacía.

---

### User Story 2 - Consultar la cotización vigente en una fecha (Priority: P1)

Como consumidor autenticado de la API, quiero conocer qué cotización estaba vigente para un jugador al terminar una fecha determinada, para reproducir una valuación histórica sin confundirla con cotizaciones posteriores.

**Why this priority**: La consulta puntual es un requisito explícito del sistema y será necesaria para valuaciones históricas y auditoría.

**Independent Test**: Preparar cotizaciones antes, durante y después de una fecha, consultar con `at` y verificar que se devuelve una sola cotización: la última calculada antes del inicio del día siguiente.

**Acceptance Scenarios**:

1. **Given** cotizaciones OFFICIAL de los días 1, 8 y 15, **When** se consulta con `at` igual al día 10, **Then** se devuelve como objeto único la cotización del día 8.
2. **Given** varias cotizaciones en el día consultado, **When** se usa `at`, **Then** se devuelve la última calculada durante ese día; ante igualdad en la fecha/hora de cálculo, gana la registrada más tarde.
3. **Given** una estrategia no indicada, **When** se consulta con `at`, **Then** la búsqueda usa la estrategia activa en ese momento.
4. **Given** que no existe ninguna cotización de la estrategia elegida hasta el fin del día indicado, **When** se consulta con `at`, **Then** se responde 404 con el formato de error común.

---

### User Story 3 - Recibir validaciones, seguridad y documentación consistentes (Priority: P2)

Como integrador de la API, quiero que las entradas inválidas, los recursos inexistentes y la falta de autenticación produzcan respuestas previsibles, y que el contrato esté completo en OpenAPI, para integrar la consulta sin depender de comportamiento implícito.

**Why this priority**: Un resultado correcto no alcanza si los consumidores no pueden autenticar, descubrir y manejar de forma uniforme todas las respuestas del endpoint.

**Independent Test**: Ejercitar el endpoint sin credenciales, con credenciales válidas de ambos tipos, con jugador inexistente, estrategia desconocida, fechas mal formadas, rango invertido y parámetros incompatibles; luego contrastar todas las respuestas y esquemas con OpenAPI.

**Acceptance Scenarios**:

1. **Given** un pedido sin credenciales o con credenciales inválidas, **When** se consulta el endpoint, **Then** se responde 401 y no se revelan datos del jugador ni de sus cotizaciones.
2. **Given** un identificador de jugador menor o igual a cero, **When** un consumidor autenticado consulta historial o vigencia, **Then** se responde 400 `validation_error` sin consultar cotizaciones.
3. **Given** un identificador positivo de jugador inexistente, **When** un consumidor autenticado consulta historial o vigencia, **Then** se responde 404 `player_not_found`.
4. **Given** una estrategia desconocida, una fecha que no cumple `YYYY-MM-DD`, un rango con `from` posterior a `to` o `at` combinado con `from` o `to`, **When** se realiza el pedido, **Then** se responde 400 con el formato de error común y un mensaje que permite corregirlo.
5. **Given** credenciales de un usuario con `Role.USER`, **When** se autentica mediante bearer token o API key, **Then** cualquiera de los dos mecanismos permite ejecutar la consulta con respuesta 200.
6. **Given** la documentación OpenAPI publicada, **When** un integrador inspecciona `GET /players/{id}/quotes`, **Then** encuentra parámetros, formas de éxito, respuestas 400/401/404, esquema común de error y las dos alternativas de seguridad.

### Edge Cases

- `from` sin `to` limita desde el comienzo de ese día y no impone límite superior; `to` sin `from` limita hasta el final de ese día y no impone límite inferior.
- Una cotización calculada exactamente al inicio de `from` o antes del inicio del día siguiente a `to` queda incluida.
- `from` igual a `to` consulta un único día completo.
- `at` se evalúa hasta el fin del día indicado, incluyendo toda cotización anterior al inicio del día siguiente.
- Fechas válidas de calendario, incluidos años bisiestos, se aceptan; fechas imposibles se rechazan con 400.
- `strategy` se compara como código exacto del catálogo; un valor vacío, con diferencias de mayúsculas/minúsculas o desconocido es inválido.
- El historial puede contener distintas versiones de la misma estrategia; cada registro conserva e informa su propia versión.
- En el historial, dos registros con la misma fecha/hora de cálculo se ordenan por su orden de registro, del más antiguo al más reciente.
- En una consulta con `at`, si dos registros empatan en la fecha/hora de cálculo, se elige el registrado más tarde.
- La existencia del jugador se valida antes de decidir si el resultado histórico está vacío o si falta una cotización vigente.
- Un `id` menor o igual a cero es inválido y produce 400 `validation_error`; sólo un `id` positivo sin jugador asociado produce 404 `player_not_found`.

## Requirements *(mandatory)*

### Functional Requirements

**Contrato y selección de modalidad**

- **FR-001**: El sistema MUST exponer `GET /players/{id}/quotes` para consumidores autenticados, sin alterar el comportamiento de las rutas existentes de `PlayerController`, en particular `GET /players`, y sin implementar ni modificar `GET /players/{id}`.
- **FR-002**: Sin el parámetro `at`, el endpoint MUST operar en modalidad historial y devolver una lista JSON; con `at`, MUST operar en modalidad vigencia y devolver un único objeto JSON.
- **FR-003**: El endpoint MUST aceptar los parámetros opcionales `strategy`, `from`, `to` y `at`.
- **FR-004**: `strategy` MUST usar, cuando está ausente, el código de la estrategia activa al procesar el pedido. Si está presente, MUST coincidir exactamente con un código del catálogo.
- **FR-005**: `from`, `to` y `at` MUST aceptar fechas ISO de calendario con formato estricto `YYYY-MM-DD`.
- **FR-006**: `at` MUST NOT combinarse con `from` ni con `to`. Una combinación incompatible MUST producir 400 y no ejecutar ninguna consulta de cotizaciones.

**Historial**

- **FR-007**: En modalidad historial, el sistema MUST devolver todas las cotizaciones guardadas del jugador que correspondan a la estrategia seleccionada y a los límites indicados.
- **FR-008**: El límite `from` MUST ser inclusivo desde las 00:00:00 del día indicado en UTC. El límite `to` MUST ser inclusivo para todo el día indicado, expresado como una frontera exclusiva a las 00:00:00 UTC del día siguiente.
- **FR-009**: Si sólo se indica uno de los límites, el intervalo MUST permanecer abierto hacia el extremo ausente. Si no se indica ninguno, MUST devolverse todo el historial de la estrategia seleccionada.
- **FR-010**: Si `from` es posterior a `to`, el endpoint MUST responder 400.
- **FR-011**: El historial MUST ordenarse por fecha/hora de cálculo ascendente y, ante empate, por orden de registro ascendente.
- **FR-012**: Un historial sin coincidencias para un jugador existente MUST responder 200 con `[]`.

**Vigencia en fecha**

- **FR-013**: En modalidad vigencia, el sistema MUST devolver la última cotización de la estrategia seleccionada calculada antes del comienzo UTC del día siguiente a `at`.
- **FR-014**: Si varias cotizaciones candidatas comparten fecha/hora de cálculo, la vigente MUST ser la registrada más tarde.
- **FR-015**: Si no existe una cotización candidata para el jugador y la estrategia hasta el fin de `at`, el endpoint MUST responder 404 con código `quote_not_found`.

**Representación de datos**

- **FR-016**: Cada cotización devuelta MUST incluir `value`, `score`, `status`, `strategy`, `version` y `calculatedAt`.
- **FR-017**: `value` MUST representar sin pérdida el valor persistido; `score` MUST ser nulo cuando la cotización guardada no tenga puntaje; `status` MUST conservar el estado guardado; `version` MUST corresponder a la versión de estrategia que generó ese registro.
- **FR-018**: `calculatedAt` MUST expresarse como fecha/hora ISO 8601 con referencia explícita a UTC, para que represente el mismo instante con independencia de la zona horaria del consumidor.

**Validación y errores**

- **FR-019**: Un `id` menor o igual a cero MUST responder 400 con código `validation_error` sin consultar cotizaciones. Si el `id` es positivo y el jugador no existe, ambas modalidades MUST responder 404 con código `player_not_found`, aun cuando tampoco existan cotizaciones.
- **FR-020**: Una estrategia ausente del catálogo MUST responder 400 con código `unknown_strategy` y un mensaje que indique el valor pedido y los códigos válidos.
- **FR-021**: Una fecha ausente cuando el parámetro fue enviado vacío, mal formada o imposible MUST responder 400 con código `validation_error`.
- **FR-022**: Un rango invertido o una combinación incompatible de parámetros MUST responder 400 con código `validation_error` y un mensaje que identifique la regla incumplida.
- **FR-023**: Todas las respuestas 400 y 404 MUST usar el formato de error existente, compuesto por `error` y `message`, sin exponer detalles internos.

**Seguridad y documentación**

- **FR-024**: Un pedido sin credenciales o con credenciales inválidas o vencidas MUST responder 401 antes de exponer si el jugador o sus cotizaciones existen.
- **FR-025**: El endpoint MUST admitir como alternativas una credencial bearer válida o una API key válida, con las mismas reglas generales de autenticación de los endpoints protegidos existentes. Una identidad con `Role.USER` MUST poder consultar exitosamente mediante cualquiera de los dos mecanismos; esta consulta no agrega una restricción de rol.
- **FR-026**: OpenAPI MUST documentar la ruta, el parámetro de path `id`, los cuatro parámetros de consulta, la exclusión entre `at` y el rango, el cuerpo de historial, el cuerpo de vigencia, las respuestas 200, 400, 401 y 404, y el esquema común de error.
- **FR-027**: OpenAPI MUST declarar `bearerAuth` y `apiKeyAuth` como alternativas de seguridad del endpoint.

**Alcance y archivos protegidos**

- **FR-028**: Esta feature MUST reutilizar las cotizaciones persistidas y las consultas existentes; MUST NOT crear cotizaciones, recalcularlas, cambiar el valor de mercado del jugador ni consultar fuentes externas.
- **FR-029**: Esta feature MUST NOT modificar `PlayerController`, `GlobalExceptionHandler` ni ningún otro archivo de Entrega 1. Si durante la planificación se concluye que uno es imprescindible, el trabajo MUST detenerse y registrar `[NEEDS CLARIFICATION]` con el archivo, el cambio mínimo y una alternativa que lo evite.

### Key Entities

- **Jugador**: Recurso existente identificado por `id`. Su existencia se valida antes de interpretar un historial vacío o una vigencia ausente.
- **Cotización de jugador (PlayerQuote)**: Registro histórico e inmutable existente. Aporta valor, score opcional, estado, código y versión de estrategia y fecha/hora de cálculo.
- **Estrategia de valuación**: Definición existente identificada por un código exacto. Una es la estrategia activa usada por defecto, pero el consumidor puede seleccionar otra.
- **Intervalo de historial**: Rango opcional de días definido por `from` y `to`, inclusivo para ambos días y abierto en cualquiera de sus extremos ausentes.
- **Fecha de vigencia**: Día indicado por `at`; selecciona la última cotización existente hasta el fin UTC de ese día.
- **Error de API**: Representación existente con los campos `error` y `message`, compartida por validación, autenticación y recursos no encontrados.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En el 100% de los casos de prueba, el historial devuelve exclusivamente registros del jugador, estrategia e intervalo solicitados, en orden cronológico ascendente y con los seis campos requeridos.
- **SC-002**: En el 100% de las fechas probadas, la modalidad `at` devuelve exactamente la cotización que estaba vigente al final del día o un 404 si todavía no existía ninguna.
- **SC-003**: El 100% de los ids no positivos, fechas, rangos, estrategias y combinaciones de parámetros inválidos contemplados responde 400 sin devolver cotizaciones.
- **SC-004**: El 100% de los pedidos rechazados por autenticación responde 401 sin revelar `player_not_found`; una identidad `Role.USER` obtiene 200 para la misma consulta tanto con bearer token como con API key.
- **SC-005**: Un integrador puede identificar en OpenAPI, sin consultar código interno, todos los parámetros, las dos formas de respuesta exitosa, las respuestas de error y ambos mecanismos de autenticación.
- **SC-006**: En el protocolo de rendimiento definido, las consultas de historial de un jugador con 2.500 cotizaciones alcanzan un p95 menor a 1 segundo y al menos 19 de 20 mediciones entregan la respuesta completa en menos de 1 segundo en el entorno de desarrollo.
- **SC-007**: El 100% de los tests existentes de `PlayerController`, del manejo global de errores y de los demás endpoints conserva sus resultados sin modificar archivos de Entrega 1.

## Assumptions

- Las fechas de consulta son días de calendario y se interpretan en UTC, en línea con las consultas creadas por la feature 013 y con el almacenamiento de la fecha de cálculo como instante absoluto.
- La estrategia activa puede cambiar por configuración; el valor por defecto se resuelve en cada pedido y no se fija en el contrato.
- Los códigos de estrategia se comparan exactamente, como en el catálogo existente. Los códigos actuales incluyen OFFICIAL y MOMENTUM, pero el contrato no limita futuras estrategias.
- El historial se devuelve completo y sin paginación porque el alcance solicitado no incluye paginación.
- La respuesta de historial es una lista y la respuesta con `at` es un objeto único; OpenAPI documenta ambas formas según la presencia del parámetro.
- Cualquier usuario autenticado puede leer cotizaciones; el rol ADMIN exigido por el recálculo de la feature 016 no se extiende a esta consulta de lectura.
- La consulta reutiliza datos persistidos localmente y no depende de la disponibilidad de fuentes externas.

## Out of Scope

- `GET /players/{id}` y cualquier cambio en el detalle del jugador.
- Creación, modificación o eliminación de cotizaciones.
- Recálculo manual o programado de cotizaciones.
- Ranking de jugadores, portfolio, operaciones de mercado o valuación histórica de portfolios.
- Paginación, agregación, estadísticas derivadas o exportación del historial.
- Cambios en el modelo o en las consultas de `PlayerQuote` creados por la feature 013, salvo que una futura aclaración del equipo los autorice.
