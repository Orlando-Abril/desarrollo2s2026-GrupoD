# Feature Specification: Simplificación del catálogo de jugadores (alcance Entrega N.º 1)

**Feature Branch**: `feature/catalogo-football-data`

**Created**: 2026-09-24

**Status**: Draft

**Input**: User description: "Simplificar la implementación actual de la feature de catálogo de jugadores (003-football-data-players) para que quede estrictamente alineada con la Entrega N.º 1 y con el Documento de Visión, sin adelantar funcionalidades de etapas posteriores. Mantener: obtención de jugadores de las 5 ligas desde Football-Data.org, mapeo al modelo existente, persistencia por externalId, GET /players con filtros por liga, equipo y posición protegido con X-API-KEY, caché Redis con TTL configurable, tolerancia a fallas de la fuente externa, Swagger y tests. Eliminar: Flyway y sus migraciones (restaurando el manejo de esquema previo), scheduler de sincronización, emisión de tokens, dependencia de superusuario, auditoría inmutable del catálogo y observabilidad agregada exclusivamente por la feature. No agregar funcionalidades nuevas."

## Contexto

Esta feature **reduce el alcance** de la feature `003-football-data-players`, ya implementada en esta rama. No agrega capacidades: define qué parte de esa implementación se conserva y cuál se retira, para que el catálogo tenga una única responsabilidad:

> Obtener jugadores desde Football-Data.org, persistirlos localmente y exponer el catálogo mediante `GET /players` con filtros y ApiKey.

Todo lo relacionado con mercado, tokens, cotizaciones, scraping, auditoría financiera y automatizaciones periódicas queda para entregas posteriores.

## Clarifications

### Session 2026-09-24

- Q: ¿Mediante qué mecanismo se dispara la carga explícita desde Football-Data, una vez retirado el scheduler? → A: Endpoint `POST /players/sync` protegido con ApiKey, que ejecuta la carga una vez y devuelve un resumen del resultado.
- Q: ¿Se retiran también el identificador de correlación, el health check y los logs estructurados que agregó la rama? → A: No. Se conservan, porque son generales y el Documento de Visión los exige en 5.1. Sólo se retiran las métricas custom de la carga y de Football-Data.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consultar el catálogo de jugadores con filtros (Priority: P1)

Un usuario registrado, identificándose con su ApiKey, consulta el listado de jugadores de las 5 ligas y puede acotarlo por liga, equipo y/o posición. La consulta se responde siempre con los datos guardados localmente.

**Why this priority**: Es el contrato `GET /players` exigido por el Documento de Visión y la funcionalidad central del catálogo. Sin él la feature no aporta valor.

**Independent Test**: Con jugadores ya guardados localmente, consultar el listado con y sin filtros usando una ApiKey válida y verificar resultados, códigos de respuesta y rechazo sin ApiKey.

**Acceptance Scenarios**:

1. **Given** jugadores guardados de varias ligas, **When** un usuario con ApiKey válida consulta el catálogo sin filtros, **Then** recibe todos los jugadores guardados.
2. **Given** jugadores guardados, **When** consulta con uno o más filtros (liga, equipo, posición), **Then** recibe sólo los jugadores que cumplen todos los filtros indicados.
3. **Given** un filtro de equipo escrito con otras mayúsculas/minúsculas, **When** consulta, **Then** el filtro coincide igual con el nombre del equipo.
4. **Given** filtros que no coinciden con ningún jugador, **When** consulta, **Then** recibe una lista vacía y no un error.
5. **Given** un valor de liga o posición inexistente, o un equipo en blanco, **When** consulta, **Then** recibe un error de validación claro.
6. **Given** que no envía ApiKey o la ApiKey es inválida o está inactiva, **When** consulta, **Then** la solicitud es rechazada por falta de autorización.
7. **Given** que la fuente externa no está disponible, **When** consulta el catálogo, **Then** recibe normalmente los jugadores ya guardados, sin demoras ni errores atribuibles a la fuente externa.

---

### User Story 2 - Cargar o actualizar el catálogo desde Football-Data (Priority: P1)

Una persona del equipo dispara de forma explícita y a pedido la carga de jugadores de las 5 ligas desde Football-Data. Los jugadores nuevos se agregan y los existentes se actualizan, identificados por su id externo, sin depender de usuarios especiales ni de reglas de mercado.

**Why this priority**: Sin una carga no hay jugadores que consultar. Es el escenario de prueba N.º 1 del Documento de Visión: construir la base de jugadores a partir de las fuentes externas.

**Independent Test**: Con la base de jugadores vacía y la fuente externa simulada, disparar la carga y verificar que quedan guardados los jugadores de las 5 ligas con sus datos y su id externo. Repetirla y verificar que no se duplican.

**Acceptance Scenarios**:

1. **Given** una base sin jugadores, **When** se dispara la carga, **Then** se guardan los jugadores de las 5 ligas con nombre, equipo, liga, posición, nacionalidad, edad (si está disponible) e id externo.
2. **Given** jugadores ya guardados, **When** se vuelve a disparar la carga, **Then** los jugadores existentes se actualizan (por ejemplo, cambio de equipo) y no se duplican.
3. **Given** que no existe ningún usuario administrador ni superusuario, **When** se dispara la carga, **Then** la carga se completa igual.
4. **Given** que la fuente externa falla para una liga, **When** se dispara la carga, **Then** se cargan las demás ligas, los jugadores ya guardados de la liga fallida se conservan y el resultado informa la falla.
5. **Given** que la fuente externa no está disponible en absoluto, **When** se dispara la carga, **Then** la carga informa la falla sin borrar ni alterar los jugadores ya guardados.
6. **Given** que una misma liga se volvió a pedir dentro del período de vigencia de la caché, **When** se dispara la carga, **Then** los datos de esa liga se toman de la caché sin volver a consultar la fuente externa.
7. **Given** que la carga no se disparó, **When** el sistema arranca o pasa el tiempo, **Then** no se ejecuta ninguna carga automática ni programada.

---

### User Story 3 - Levantar el proyecto sin requisitos ajenos al catálogo (Priority: P2)

Una persona del equipo levanta el backend en su máquina o en CI con la misma configuración de base de datos que usaba el proyecto antes de esta feature, sin tener que crear usuarios especiales, escribir migraciones ni configurar parámetros de scheduling o de mercado.

**Why this priority**: Reduce la fricción del equipo y evita que el catálogo imponga decisiones globales (manejo de esquema, superusuario) que corresponden a otras entregas.

**Independent Test**: Levantar el backend sobre una base vacía sólo con las variables de entorno de base de datos, JWT y token de Football-Data, y verificar que arranca y que el catálogo se puede cargar y consultar.

**Acceptance Scenarios**:

1. **Given** una base vacía, **When** se levanta el backend, **Then** el esquema necesario se genera con el mecanismo que el proyecto usaba antes de esta feature y la aplicación arranca.
2. **Given** una base creada por la versión anterior de la rama (con las tablas de tokens y auditoría del catálogo), **When** se levanta el backend, **Then** la aplicación arranca y funciona normalmente, aunque esas tablas sigan existiendo.
3. **Given** la configuración del proyecto, **When** se la revisa, **Then** no existen parámetros de superusuario, cron, reintentos de arranque, emisión de tokens ni habilitación de sincronización automática.

### Edge Cases

- Un jugador externo sin id, sin nombre o de un equipo sin nombre: se omite sin interrumpir la carga.
- Una posición externa que no se reconoce: el jugador se guarda sin posición.
- Un jugador sin fecha de nacimiento: se guarda sin edad.
- Un jugador sin nacionalidad o con nacionalidad en blanco: se guarda sin nacionalidad.
- Un jugador que no puede guardarse: se descarta ese jugador, se registra en el log y la carga continúa con los demás.
- Dos cargas disparadas casi al mismo tiempo: el resultado final es el mismo que el de una sola carga; los jugadores no se duplican.
- La fuente externa responde con límite de consultas excedido o error de servidor: se trata como falla de esa liga, sin reintentos automáticos.
- La caché no está disponible: la carga consulta directamente la fuente externa, y la consulta del catálogo no se ve afectada porque lee de la base local.
- Mensajes de error y logs nunca exponen el token de Football-Data ni el contenido completo de las respuestas externas.
- Catálogo todavía vacío (nunca se cargó): la consulta devuelve una lista vacía.

## Requirements *(mandatory)*

### Functional Requirements

**Obtención y persistencia del catálogo**

- **FR-001**: El sistema MUST obtener desde Football-Data.org los equipos y planteles de las 5 ligas: Premier League, La Liga, Serie A, Bundesliga y Ligue 1.
- **FR-002**: El sistema MUST mapear cada jugador externo al modelo de jugador existente: nombre, equipo, liga, posición (arquero, defensor, mediocampista o delantero), nacionalidad y edad calculada a partir de la fecha de nacimiento cuando esté disponible.
- **FR-003**: El sistema MUST guardar el identificador de Football-Data de cada jugador como su id externo, y usarlo para crear el jugador si no existe o actualizarlo si ya existe, sin generar duplicados.
- **FR-004**: El sistema MUST asignar a los jugadores nuevos el valor de mercado base que el modelo existente requiere, sin ejecutar ninguna otra lógica de mercado.
- **FR-005**: El sistema MUST omitir los jugadores externos incompletos (sin id o sin nombre, o de equipos sin nombre) sin interrumpir la carga.
- **FR-006**: El sistema MUST aislar las fallas por liga y por jugador: una liga que falla no impide cargar las demás, y un jugador que no puede guardarse no impide guardar los demás.
- **FR-007**: El sistema MUST NOT borrar ni modificar jugadores ya guardados cuando la fuente externa falla.

**Disparo de la carga**

- **FR-008**: El sistema MUST ofrecer una única forma explícita de disparar la carga a pedido: el endpoint `POST /players/sync`, protegido con ApiKey, que ejecuta la carga una vez y devuelve un resumen del resultado (jugadores procesados y ligas o jugadores fallidos).
- **FR-009**: El sistema MUST NOT ejecutar la carga en forma automática: ni al arrancar, ni periódicamente, ni mediante reintentos programados.
- **FR-010**: La carga MUST NOT requerir la existencia de un superusuario, usuario administrador ni ninguna otra configuración de mercado.

**Consulta del catálogo**

- **FR-011**: El sistema MUST exponer `GET /players`, que lista los jugadores guardados localmente.
- **FR-012**: `GET /players` MUST aceptar filtros opcionales por liga, equipo y posición, combinados entre sí (todos deben cumplirse). El filtro de equipo MUST ser exacto sin distinguir mayúsculas/minúsculas, y el de posición MUST coincidir con cualquiera de las posiciones del jugador.
- **FR-013**: `GET /players` MUST devolver una lista vacía cuando no hay coincidencias o cuando el catálogo todavía no fue cargado.
- **FR-014**: `GET /players` MUST rechazar con un error de validación los valores inválidos de liga o posición y los equipos en blanco.
- **FR-015**: `GET /players` MUST leer exclusivamente de los datos locales y MUST NOT consultar la fuente externa, de modo que siga funcionando si Football-Data no está disponible.
- **FR-016**: `GET /players` y el disparo de la carga MUST estar protegidos por la ApiKey existente enviada en el header `X-API-KEY`, sin modificar el mecanismo actual de registro, login, JWT y ApiKey.

**Caché**

- **FR-017**: El sistema MUST cachear en Redis la respuesta de Football-Data de cada liga, con un tiempo de vigencia configurable, para no volver a consultar la fuente externa dentro de ese período.
- **FR-018**: Una falla o indisponibilidad de la caché MUST NOT impedir la consulta del catálogo local.

**Seguridad y documentación**

- **FR-019**: Los errores y logs MUST NOT exponer el token de Football-Data ni el contenido completo de las respuestas externas.
- **FR-020**: El sistema MUST NOT reintentar automáticamente las consultas fallidas a Football-Data.
- **FR-021**: `GET /players` y el disparo de la carga MUST estar documentados en Swagger/OpenAPI con sus parámetros, respuestas, códigos de error y el esquema de seguridad ApiKey.

**Retiro de alcance**

- **FR-022**: El sistema MUST volver a manejar el esquema de base de datos con el mecanismo que el proyecto usaba antes de esta feature, sin herramientas de migración versionada ni migraciones asociadas.
- **FR-023**: El sistema MUST NOT emitir, asignar ni registrar tokens de jugadores, ni fijar precios de tokens, como parte del catálogo.
- **FR-024**: El sistema MUST NOT registrar una auditoría persistente propia de la carga del catálogo. Los resultados y fallas de la carga se informan sólo en logs y en la respuesta del disparo.
- **FR-025**: El sistema MUST NOT conservar las métricas custom de la carga ni de Football-Data agregadas por la feature `003-football-data-players`. El sistema MUST conservar la observabilidad general agregada por esa feature, porque no depende del catálogo y la exige el Documento de Visión (5.1):
  - identificador de correlación propagado o generado en cada request y devuelto en cada respuesta;
  - health check público con estado agregado;
  - formato de logs estructurado que incluye el identificador de correlación.
- **FR-026**: La configuración del sistema MUST NOT contener parámetros asociados a funcionalidades retiradas: superusuario, cron, intervalo de reintento, habilitación de sincronización automática.
- **FR-027**: Los modelos existentes de jugador, liga y posición y sus repositorios MUST NOT modificarse en su estructura.

**Pruebas**

- **FR-028**: Las pruebas automáticas MUST cubrir el mapeo, la carga (alta, actualización, fallas por liga y por jugador), la consulta con filtros, la validación, la protección con ApiKey, la caché y la no exposición de secretos, y MUST NOT llamar a la API real de Football-Data.
- **FR-029**: Las pruebas que existían exclusivamente para funcionalidades retiradas (migraciones, scheduler, tokens, superusuario, auditoría, triggers y métricas custom) MUST eliminarse. Las pruebas del identificador de correlación y del health check MUST conservarse.

### Key Entities *(include if feature involves data)*

- **Jugador** (existente, sin cambios de estructura): persona del catálogo, con id externo de Football-Data, nombre, equipo, liga, una o más posiciones, nacionalidad, edad y valor de mercado base.
- **Liga** (existente): una de las 5 ligas soportadas.
- **Posición** (existente): arquero, defensor, mediocampista o delantero.
- **Resultado de carga** (no persistido): resumen devuelto al disparar la carga, con cantidad de jugadores procesados y ligas o jugadores fallidos.

Se retiran del alcance del catálogo las entidades **asignación de tokens** y **evento de auditoría de sincronización**.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Partiendo de una base vacía, una sola carga disparada a pedido deja guardados jugadores de las 5 ligas, sin necesidad de crear ningún usuario especial.
- **SC-002**: Repetir la carga 2 o más veces no genera ningún jugador duplicado.
- **SC-003**: Con la fuente externa no disponible, el 100% de las consultas al catálogo responde con los datos ya guardados.
- **SC-004**: Una carga completa hace como máximo 5 consultas a la fuente externa, y una segunda carga dentro del período de vigencia de la caché no hace ninguna.
- **SC-005**: El 100% de las consultas al catálogo sin ApiKey válida es rechazado.
- **SC-006**: Una persona del equipo puede levantar el backend sobre una base vacía configurando sólo base de datos, secreto JWT y token de Football-Data, sin pasos manuales en la base.
- **SC-007**: No queda en el proyecto código, configuración, tablas nuevas ni pruebas asociadas a scheduler, tokens, superusuario, auditoría del catálogo, migraciones versionadas ni métricas custom de la carga o de Football-Data.
- **SC-009**: El 100% de las respuestas incluye un identificador de correlación, y el health check sigue respondiendo públicamente con el estado agregado.
- **SC-008**: El pipeline de CI termina en verde y el análisis de calidad mantiene el umbral exigido por la materia.

## Assumptions

- El mecanismo de manejo de esquema previo a la feature es la generación y actualización automática del esquema a partir de las entidades, tal como estaba configurado en `main`.
- Sin migraciones, la unicidad del id externo se garantiza desde la lógica de carga (crear o actualizar por id externo), porque la estructura del modelo de jugador no puede modificarse.
- Las tablas creadas por la versión anterior de la rama (tokens y auditoría) pueden quedar en las bases locales existentes. No se borran automáticamente y no afectan el funcionamiento; limpiarlas es una tarea manual opcional.
- El valor de mercado base de los jugadores nuevos se mantiene en el valor actual de la implementación (1.00), porque el modelo existente lo exige no nulo. No implica emisión de tokens.
- El catálogo vacío se informa con una lista vacía. Se retira la respuesta de "catálogo no disponible", que dependía de la auditoría eliminada.
- La consulta del catálogo sigue devolviendo todos los jugadores que cumplen los filtros, sin paginación, como en la implementación actual.
- Se mantiene una única instancia del backend. Dos cargas simultáneas no se coordinan entre sí, pero la lógica de crear o actualizar por id externo evita resultados distintos a los de una sola carga en el uso normal del equipo.
- El scheduler general, la auditoría financiera y la emisión de tokens del Documento de Visión se implementarán en entregas posteriores.
- La autenticación existente (registro, login, JWT y ApiKey) no se modifica.
