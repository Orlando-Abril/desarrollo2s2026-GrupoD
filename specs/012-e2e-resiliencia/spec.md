# Feature Specification: Resiliencia e2e de fuentes externas

**Feature Branch**: N/A — no se creó una rama para esta especificación

**Created**: 2026-10-13

**Status**: Draft

**Input**: User description: "Tests e2e que demuestran que la API sigue respondiendo cuando Football-Data y WhoScored están caídos (constitución §1.3 y §7 punto 6). En el perfil e2e las fuentes externas apuntan a una dirección inválida. Validar lecturas locales con y sin filtros, sincronización fallida sin pérdida de datos, respuesta desde Redis sin consultar la fuente, ausencia de 5xx, GET dentro de 100 ms tras calentamiento y sync dentro de 500 ms. Cargar jugadores de las cinco ligas mediante un helper reutilizable. No modificar archivos de Entrega 1 sin consulta."

## Contexto y restricciones

El perfil `e2e` existente ejecuta la aplicación completa por HTTP contra PostgreSQL y Redis reales, limpia ambos almacenes antes de cada prueba y configura Football-Data y WhoScored con destinos deliberadamente inválidos. Esta feature aporta la evidencia automática de que una caída de esas fuentes no interrumpe la consulta del catálogo, no destruye el estado persistido durante una sincronización fallida y permite reutilizar datos externos aún vigentes en caché.

Son archivos protegidos de Entrega 1 y no pueden modificarse sin consulta previa: `pom.xml`, `ci.yml`, todo archivo existente llamado `application.properties`, `SecurityConfig`, filtros y controllers. Si durante planificación o implementación un cambio en cualquiera de ellos resulta imprescindible, el trabajo debe registrar `[NEEDS CLARIFICATION]` con el archivo, el cambio mínimo propuesto y una alternativa que evite modificarlo, y esperar la decisión del equipo. La presente especificación no autoriza cambios en esos archivos.

El umbral de duración se aplica a cada interacción HTTP de los escenarios de resiliencia, una vez iniciada la aplicación y disponibles PostgreSQL y Redis. El tiempo de arranque y la preparación de datos no forman parte de esa medición.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consultar el catálogo durante una caída externa (Priority: P1)

Como persona usuaria del catálogo, quiero consultar los jugadores ya guardados aunque Football-Data y WhoScored estén caídos, para seguir navegando y filtrando información local sin interrupciones.

**Why this priority**: La lectura del catálogo es el recorrido principal y la constitución exige que ninguna caída externa congele ni tumbe los endpoints de lectura.

**Independent Test**: Precargar un conjunto conocido de jugadores de las cinco grandes ligas, mantener inaccesibles ambas fuentes y consultar el catálogo sin filtros, con cada filtro por separado y con los tres filtros combinados. Todas las consultas deben responder con los datos locales esperados.

**Acceptance Scenarios**:

1. **Given** jugadores precargados de Premier League, La Liga, Serie A, Bundesliga y Ligue 1, y ambas fuentes externas inaccesibles, **When** se consulta `GET /players` sin filtros, **Then** responde 200, devuelve exactamente los jugadores locales esperados, no devuelve 5xx y finaliza en no más de 100 ms.
2. **Given** el mismo catálogo local y las fuentes externas inaccesibles, **When** se consulta `GET /players` sólo con filtro de liga, sólo con filtro de equipo o sólo con filtro de posición, **Then** cada respuesta es 200, contiene exclusivamente las coincidencias locales esperadas y finaliza en no más de 100 ms.
3. **Given** el mismo catálogo local y las fuentes externas inaccesibles, **When** se consulta `GET /players` combinando liga, equipo y posición, **Then** responde 200, devuelve exclusivamente la intersección esperada y finaliza en no más de 100 ms.

---

### User Story 2 - Conservar el catálogo ante una sincronización fallida (Priority: P1)

Como integrante del equipo, quiero comprobar que una sincronización manual fallida informa su resultado sin alterar el catálogo existente, para que una indisponibilidad externa no provoque pérdida ni corrupción de datos.

**Why this priority**: La conservación del último estado válido es esencial para que las lecturas y la operativa posterior sigan siendo confiables.

**Independent Test**: Precargar y capturar una instantánea completa del catálogo, ejecutar la sincronización con Football-Data inaccesible y sin datos reutilizables en caché, y comparar el catálogo posterior con la instantánea previa.

**Acceptance Scenarios**:

1. **Given** jugadores existentes, Football-Data inaccesible y ausencia de entradas aplicables en caché, **When** se solicita `POST /players/sync`, **Then** responde 200 con estado `FAILED`, sin respuesta 5xx y en no más de 500 ms.
2. **Given** la misma ejecución fallida, **When** se vuelve a consultar el catálogo, **Then** conserva exactamente la misma cantidad de jugadores y los mismos valores de todos sus campos que antes de sincronizar.

---

### User Story 3 - Reutilizar datos externos cacheados (Priority: P2)

Como persona usuaria, quiero que los datos externos ya cacheados sigan disponibles durante una caída, para recibir una respuesta válida sin esperar ni depender de la fuente remota.

**Why this priority**: El caché es el mecanismo constitucional de respaldo ante caídas externas y debe demostrarse que evita efectivamente la consulta remota.

**Independent Test**: Preparar entradas vigentes de caché para las cinco ligas, dejar Football-Data inaccesible, solicitar la sincronización y verificar tanto el resultado esperado como la evidencia de que no hubo ningún intento de acceso a la fuente.

**Acceptance Scenarios**:

1. **Given** datos vigentes de las cinco ligas en Redis y Football-Data inaccesible, **When** se solicita `POST /players/sync`, **Then** responde 200 con estado `COMPLETED`, procesa los datos cacheados y finaliza en no más de 500 ms.
2. **Given** esa respuesta exitosa desde caché, **When** se inspecciona la evidencia de interacción externa de la prueba, **Then** se observan cero llamadas o intentos de conexión a Football-Data.

---

### User Story 4 - Reutilizar datos de prueba en futuros e2e (Priority: P3)

Como integrante del equipo, quiero un helper común para cargar jugadores representativos de las cinco ligas, para que los e2e actuales y siguientes compartan datos consistentes sin duplicar preparación.

**Why this priority**: Una base de datos de prueba uniforme reduce duplicación, hace legibles los escenarios y evita diferencias accidentales entre suites futuras.

**Independent Test**: Usar únicamente el helper en un entorno e2e limpio y comprobar que crea un conjunto determinista, consultable y representativo de las cinco ligas.

**Acceptance Scenarios**:

1. **Given** una base e2e vacía, **When** una prueba invoca el helper, **Then** queda disponible al menos un jugador de cada una de las cinco ligas, con equipo, posición y demás campos obligatorios válidos.
2. **Given** dos pruebas distintas que usan el helper después de la limpieza e2e correspondiente, **When** cargan el conjunto predeterminado, **Then** ambas reciben los mismos datos funcionales y pueden seleccionar jugadores por liga, equipo y posición sin conocer detalles de persistencia.

### Edge Cases

- Un catálogo vacío con fuentes caídas sigue respondiendo 200 con una colección vacía; no intenta completar la lectura desde una fuente externa.
- Un filtro válido sin coincidencias responde 200 con una colección vacía dentro del umbral.
- Las entradas de caché ausentes o ya vencidas no cuentan como respaldo. Para el consumidor del caché, una entrada vencida es indistinguible de una entrada ausente y no requiere un escenario e2e separado: si todas las consultas externas fallan y no se procesa ningún jugador, la sincronización informa `FAILED` y preserva el catálogo.
- El escenario de caché requiere cobertura completa de las cinco ligas; una mezcla de aciertos de caché y fallas externas pertenece a los comportamientos `PARTIAL_FAILURE` ya definidos por el contrato y no reemplaza la prueba de cero llamadas solicitada.
- La comparación antes y después de una sincronización fallida incluye asociaciones de posiciones y todos los campos expuestos del jugador, no sólo la cantidad de filas.
- Una respuesta 4xx causada por credenciales ausentes o filtros inválidos no demuestra resiliencia y no forma parte de los escenarios nominales de esta feature.
- Si una interacción supera 100 ms, devuelve 5xx o queda bloqueada esperando una fuente, el escenario falla aunque el contenido final sea correcto.
- Si demostrar un escenario exigiera modificar un archivo protegido, se aplica la consulta obligatoria antes de cualquier cambio.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: La suite MUST incluir pruebas e2e ejecutadas mediante HTTP real bajo el perfil `e2e` existente, con PostgreSQL y Redis reales y Football-Data y WhoScored inaccesibles.
- **FR-002**: Las pruebas MUST precargar jugadores locales representativos de Premier League, La Liga, Serie A, Bundesliga y Ligue 1 antes de validar las lecturas resilientes.
- **FR-003**: `GET /players` sin filtros MUST responder 200 con el conjunto local esperado cuando ambas fuentes externas estén inaccesibles.
- **FR-004**: `GET /players` MUST responder 200 y aplicar correctamente, de manera independiente, los filtros válidos de liga, equipo y posición sobre los datos locales cuando ambas fuentes estén inaccesibles.
- **FR-005**: `GET /players` MUST responder 200 y aplicar conjuntamente los filtros de liga, equipo y posición como una intersección sobre los datos locales cuando ambas fuentes estén inaccesibles.
- **FR-006**: Ninguna consulta de `GET /players` cubierta por esta feature MUST depender de una llamada a Football-Data o WhoScored.
- **FR-007**: Con Football-Data inaccesible y sin entradas aplicables en caché, `POST /players/sync` MUST responder 200 con `status` igual a `FAILED` cuando ninguna liga pueda procesarse.
- **FR-008**: Una sincronización con resultado `FAILED` MUST conservar sin altas, bajas ni modificaciones todos los jugadores y posiciones existentes antes de la solicitud.
- **FR-009**: Con datos vigentes de las cinco ligas precargados en Redis, `POST /players/sync` MUST reutilizar esos datos y responder 200 con `status` igual a `COMPLETED` aunque Football-Data esté inaccesible.
- **FR-010**: El escenario de caché MUST verificar de forma inequívoca que se realizaron cero llamadas o intentos de conexión a Football-Data durante la solicitud.
- **FR-011**: Cada `GET /players` de los escenarios MUST finalizar en no más de 100 ms después de una solicitud equivalente de calentamiento no medida, y cada `POST /players/sync` MUST finalizar en no más de 500 ms; ambos umbrales se miden después del arranque de la aplicación y excluyen la preparación y limpieza de datos.
- **FR-012**: Ningún escenario nominal de resiliencia MUST devolver una respuesta 5xx.
- **FR-013**: La suite MUST proporcionar un helper reutilizable que cargue un conjunto determinista de jugadores válidos de las cinco ligas y permita acceder a ellos por liga, equipo y posición.
- **FR-014**: El helper MUST poder ser utilizado por futuros e2e sin depender de una clase de prueba concreta ni requerir conocimiento de detalles de persistencia por parte de la prueba consumidora.
- **FR-015**: Cada prueba MUST conservar el aislamiento provisto por el perfil e2e y MUST preparar explícitamente sólo el estado local y de caché que necesita después de la limpieza común.
- **FR-016**: Las aserciones MUST distinguir el estado HTTP del campo funcional `status` del resumen de sincronización; tanto `FAILED` como `COMPLETED` se informan mediante HTTP 200 en los escenarios definidos.
- **FR-017**: La feature MUST NOT modificar `pom.xml`, `ci.yml`, ningún `application.properties` existente, `SecurityConfig`, filtros ni controllers sin aprobación explícita del equipo.
- **FR-018**: Si un cambio en un archivo protegido resulta imprescindible durante planificación o implementación, el trabajo MUST registrar `[NEEDS CLARIFICATION]` con el archivo, el cambio mínimo propuesto y una alternativa que no lo modifique, y MUST esperar respuesta antes de aplicarlo.

### Key Entities

- **Jugador de prueba**: Registro determinista con identificador, nombre, equipo, liga, una o más posiciones y restantes campos obligatorios; el conjunto compartido representa las cinco grandes ligas.
- **Instantánea del catálogo**: Estado completo y comparable de los jugadores y sus posiciones inmediatamente antes de una sincronización, usado para demostrar ausencia de cambios posteriores.
- **Resumen de sincronización**: Resultado funcional de `POST /players/sync`, incluido su estado `COMPLETED`, `PARTIAL_FAILURE` o `FAILED` y sus contadores.
- **Entrada externa cacheada**: Datos vigentes de una liga que pueden reutilizarse sin acceder a Football-Data.
- **Evidencia de interacción externa**: Observación verificable que permite afirmar que una solicitud produjo cero llamadas o intentos de conexión a la fuente.
- **Helper de fixtures e2e**: Componente compartido que crea y expone el conjunto predeterminado de jugadores para esta suite y futuros escenarios.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las consultas del catálogo sin filtros, con cada filtro individual y con los tres filtros combinados devuelve 200 y exactamente los jugadores locales esperados mientras ambas fuentes están caídas.
- **SC-002**: El 100% de los jugadores y valores precargados permanece idéntico después de una sincronización totalmente fallida; se observan cero altas, cero bajas y cero modificaciones.
- **SC-003**: Una sincronización sin respaldo disponible informa `FAILED`, mientras una sincronización con respaldo completo vigente informa `COMPLETED`; ambas responden 200.
- **SC-004**: En el escenario con respaldo completo, la medición de interacciones registra exactamente cero accesos a Football-Data.
- **SC-005**: El 100% de los `GET /players` cubiertos finaliza en 100 ms o menos tras su calentamiento, el 100% de los `POST /players/sync` cubiertos finaliza en 500 ms o menos y ninguna interacción devuelve 5xx.
- **SC-006**: El conjunto compartido contiene al menos cinco jugadores, incluye las cinco ligas obligatorias y permite obtener al menos una coincidencia conocida para cada dimensión de filtro: liga, equipo y posición.
- **SC-007**: Al menos dos clases o escenarios e2e pueden usar el mismo helper sin duplicar la construcción de jugadores ni depender de una clase de prueba específica.
- **SC-008**: La feature produce cero cambios no aprobados en archivos protegidos de Entrega 1.

## Assumptions

- El perfil `e2e`, su meta-anotación, su limpieza común, la autenticación de las solicitudes y la infraestructura PostgreSQL/Redis ya existen como resultado de la feature de perfil e2e; esta feature los reutiliza.
- Los destinos inválidos configurados por el perfil representan la caída simultánea de Football-Data y WhoScored sin depender de Internet.
- El umbral de 100 ms corresponde a cada solicitud HTTP individual y se mide dentro del proceso de prueba una vez que la aplicación está lista; no es una prueba de carga ni incluye arranque, autenticación, limpieza o carga de fixtures.
- El escenario `FAILED` comienza con Redis sin entradas de Football-Data para evitar que un acierto de caché cambie el resultado esperado.
- El escenario de caché prepara una entrada vigente por cada código de las cinco ligas y datos válidos suficientes para que todas puedan procesarse.
- La evidencia de cero llamadas puede obtenerse mediante soporte exclusivo de pruebas o instrumentación observable fuera de los archivos protegidos; la elección concreta se resolverá durante la planificación.
- El helper puede crear más de un jugador por liga si fuera necesario para que las combinaciones de filtros tengan resultados positivos y negativos inequívocos.
- WhoScored no participa en la sincronización manual del catálogo, pero permanece inaccesible para demostrar que las lecturas locales no dependen de ninguna de las dos fuentes oficiales.

## Out of Scope

- Cambiar el comportamiento productivo de `GET /players`, `POST /players/sync`, los filtros, la seguridad o los contratos de respuesta.
- Probar recuperación de la fuente, reintentos, circuit breakers, carga, concurrencia o degradación parcial de una sola liga.
- Validar la actualización de estadísticas de WhoScored o el scheduler correspondiente; esta feature sólo demuestra que su caída no afecta las lecturas locales solicitadas.
- Cambiar los timeouts productivos de clientes externos para satisfacer el umbral de prueba.
- Agregar dependencias o modificar archivos protegidos de Entrega 1 sin la consulta y aprobación requeridas.
