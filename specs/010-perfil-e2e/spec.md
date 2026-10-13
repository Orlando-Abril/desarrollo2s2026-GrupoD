# Feature Specification: Perfil e2e con infraestructura real

**Feature Branch**: N/A — no se creó una rama para esta especificación

**Created**: 2026-10-07

**Status**: Draft

**Input**: User description: "Crear el perfil `e2e` para tests de punta a punta contra PostgreSQL y Redis reales, con la aplicación completa levantada en un puerto aleatorio y un primer test de humo. Los servicios deben ser configurables por variables de entorno con valores por defecto iguales a los del CI; los e2e se omiten localmente con un motivo claro si falta infraestructura, pero con `CI=true` deben correr y fallar ante servicios ausentes. Cada test debe comenzar con base y Redis limpios. El humo debe registrar y autenticar un usuario, consultar `/players` con Bearer, verificar `X-Correlation-ID` y comprobar salud `UP`. Documentar la ejecución por grupos en Windows y Linux, sin dependencias nuevas ni cambios no consultados en archivos de Entrega 1. Los tests de resiliencia pertenecen a Feature 012."

## Contexto y restricciones

El proceso de integración continua ya ejecuta la verificación completa con PostgreSQL 16 y Redis 7 disponibles en `localhost`; la base se llama `desarrollo2_grupod` y las credenciales predeterminadas son usuario `postgres` y clave `postgres`. Esta feature debe convertir esa infraestructura disponible en una validación e2e efectiva sin cambiar el comportamiento del perfil `test` ni su meta-anotación `UnitTestProfile` creados por la feature anterior.

El perfil `e2e` representa la aplicación completa operando por HTTP real en un puerto aleatorio y conectada a PostgreSQL y Redis reales. Football-Data y WhoScored permanecen aislados mediante destinos deliberadamente inválidos para que el recorrido de humo no dependa de Internet ni de terceros.

Son archivos protegidos de Entrega 1 y no pueden modificarse sin consulta previa: `pom.xml`, `ci.yml`, todo archivo existente llamado `application.properties`, `SecurityConfig`, filtros y controllers. Si durante planificación o implementación un cambio en cualquiera de ellos resulta imprescindible, el trabajo debe registrar `[NEEDS CLARIFICATION]` con el archivo, el cambio mínimo propuesto y una alternativa que evite modificarlo, y esperar la decisión del equipo. No se permiten dependencias nuevas.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Validar el recorrido crítico sobre el sistema completo (Priority: P1)

Como integrante del equipo, quiero ejecutar un test de humo por HTTP contra la aplicación completa y sus servicios de datos reales, para detectar fallas de configuración o integración que los tests aislados no pueden revelar.

**Why this priority**: Es la evidencia principal de que autenticación, autorización, catálogo, observabilidad, persistencia y caché funcionan juntas en un entorno equivalente al de CI.

**Independent Test**: Ejecutar únicamente el test de humo e2e con PostgreSQL y Redis disponibles; el test registra un usuario, inicia sesión, usa el token recibido para consultar jugadores, verifica el identificador de correlación y confirma que la salud general está en estado `UP`.

**Acceptance Scenarios**:

1. **Given** PostgreSQL y Redis accesibles y vacíos para el test, **When** se inicia el test e2e, **Then** la aplicación completa arranca con el perfil `e2e` en un puerto asignado aleatoriamente.
2. **Given** la aplicación iniciada, **When** el test envía `POST /auth/register` con un usuario válido y luego `POST /auth/login` con sus credenciales, **Then** el registro y el inicio de sesión son exitosos y el login entrega un token Bearer utilizable.
3. **Given** el token obtenido, **When** el test envía `GET /players` con `Authorization: Bearer <token>`, **Then** recibe estado HTTP 200 y una cabecera `X-Correlation-ID` presente y no vacía.
4. **Given** la aplicación conectada a sus dependencias reales, **When** el test consulta `/actuator/health`, **Then** la respuesta informa estado general `UP`.
5. **Given** el recorrido de humo, **When** se ejecuta de principio a fin, **Then** no intenta comunicarse con Football-Data ni WhoScored en destinos públicos.

---

### User Story 2 - Ejecutar e2e de forma predecible localmente y en CI (Priority: P1)

Como desarrollador, quiero que la suite e2e distinga entre una máquina local sin infraestructura y el entorno obligatorio de CI, para evitar falsos fallos locales sin ocultar problemas del pipeline.

**Why this priority**: Una suite que falla siempre sin servicios locales desalienta su uso, mientras que una suite que se omite en CI puede dar una señal de calidad engañosa.

**Independent Test**: Ejecutar la suite en cuatro condiciones controladas —servicios disponibles localmente, PostgreSQL ausente localmente, Redis ausente localmente y cualquiera ausente con `CI=true`— y comprobar respectivamente ejecución, omisión clara, omisión clara y fallo.

**Acceptance Scenarios**:

1. **Given** una ejecución local sin PostgreSQL o sin Redis, **When** se seleccionan los tests e2e, **Then** se omiten antes de iniciar el recorrido y el resultado identifica claramente cada servicio no disponible.
2. **Given** una ejecución con `CI=true`, **When** PostgreSQL o Redis no están disponibles, **Then** los tests e2e no se omiten y la verificación falla con un diagnóstico que identifica la dependencia ausente.
3. **Given** PostgreSQL y Redis disponibles, **When** se ejecutan los e2e localmente o en CI, **Then** los tests se ejecutan normalmente.
4. **Given** variables de entorno válidas para cualquiera de los servicios, **When** se inicia el perfil `e2e`, **Then** utiliza esos valores en lugar de los predeterminados.

---

### User Story 3 - Seleccionar grupos de prueba sin ambigüedad (Priority: P2)

Como integrante del equipo, quiero identificar los tests e2e mediante una única anotación y ejecutar sólo unitarios, sólo e2e o todos, para elegir el costo y la cobertura adecuados en cada ciclo de trabajo.

**Why this priority**: La selección explícita evita ejecutar infraestructura pesada por accidente y hace reproducible el mismo agrupamiento en estaciones de trabajo y CI.

**Independent Test**: Aplicar la meta-anotación a un test demostrativo y usar los comandos documentados para comprobar que el test pertenece al grupo `e2e`, activa sólo el perfil `e2e`, inicia en puerto aleatorio y aparece exactamente en las selecciones esperadas.

**Acceptance Scenarios**:

1. **Given** un test marcado con la meta-anotación e2e, **When** se inspecciona su configuración efectiva, **Then** activa el perfil `e2e`, solicita un puerto aleatorio y posee el tag `e2e` sin repetir esas declaraciones manualmente.
2. **Given** la documentación del proyecto, **When** un integrante consulta cómo probar el backend, **Then** encuentra comandos separados para ejecutar sólo unitarios, sólo e2e y la verificación completa.
3. **Given** esos tres grupos, **When** se consultan las instrucciones desde Windows o Linux, **Then** cada plataforma tiene un comando directamente ejecutable y con la misma semántica de selección.
4. **Given** una ejecución de sólo unitarios, **When** se selecciona ese grupo, **Then** no se inicia ningún test etiquetado `e2e`.

---

### User Story 4 - Aislar cada prueba e2e (Priority: P2)

Como desarrollador, quiero que cada test e2e comience sin datos residuales en persistencia ni caché, para que su resultado sea reproducible y no dependa del orden de ejecución.

**Why this priority**: La infraestructura compartida puede introducir falsos positivos, colisiones de usuarios y resultados distintos entre ejecuciones si no se restaura un estado conocido.

**Independent Test**: Ejecutar dos pruebas consecutivas que creen datos con los mismos identificadores y comprobar que ambas comienzan sin registros ni claves generados por la anterior.

**Acceptance Scenarios**:

1. **Given** datos y claves creados por un test e2e, **When** comienza el siguiente test e2e, **Then** no puede observar ningún dato persistente ni entrada de caché dejados por el test anterior.
2. **Given** varios tests e2e en distinto orden, **When** se repite la suite, **Then** todos parten del mismo estado vacío y mantienen el mismo resultado.
3. **Given** que la suite finaliza, **When** se cierra el contexto e2e, **Then** el esquema temporal se elimina conforme a la política `create-drop`.

### Edge Cases

- Si PostgreSQL está disponible pero Redis no, o viceversa, la omisión local o el fallo de CI debe nombrar específicamente el servicio ausente; una dependencia disponible no compensa la otra.
- Si `CI` está ausente, vacío o tiene un valor diferente de `true` sin distinción de mayúsculas y minúsculas, la ejecución se considera local; sólo el valor booleano verdadero activa la obligatoriedad de CI.
- Si el socket configurado no está disponible, la ejecución local se omite. Si el socket está disponible pero las credenciales o la base son inválidas, el contexto falla tanto localmente como en CI. Ningún diagnóstico debe exponer secretos.
- Si el puerto aleatorio asignado cambia entre ejecuciones, el cliente del test debe descubrir el puerto efectivo y no depender de uno fijo.
- Si ya existen datos en la base o claves en Redis antes de un test, la limpieza previa debe eliminarlos dentro del alcance del entorno e2e antes del primer request.
- Si el mismo contexto se reutiliza entre métodos de prueba, la limpieza debe ocurrir antes de cada test y no solamente al iniciar la suite.
- Si `/players` devuelve una colección vacía, el humo sigue siendo válido siempre que responda 200 con un `X-Correlation-ID` no vacío; poblar el catálogo queda fuera del recorrido solicitado.
- Si Football-Data o WhoScored se vuelven accesibles por herencia de configuración, el perfil no cumple el aislamiento exigido aunque el humo termine exitosamente.
- Si la configuración necesaria sólo pudiera lograrse modificando un archivo protegido, se aplica la consulta obligatoria antes de cualquier cambio.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El backend MUST ofrecer un perfil de pruebas llamado `e2e` que ejecute la aplicación completa mediante HTTP en un puerto aleatorio.
- **FR-002**: El perfil `e2e` MUST conectarse a instancias reales de PostgreSQL y Redis y MUST NOT sustituirlas por implementaciones en memoria o simuladas.
- **FR-003**: La conexión PostgreSQL MUST poder configurarse mediante `E2E_DB_URL`, `E2E_DB_USER` y `E2E_DB_PASSWORD`, con valores predeterminados `jdbc:postgresql://localhost:5432/desarrollo2_grupod`, `postgres` y `postgres`, respectivamente.
- **FR-004**: La conexión a Redis MUST poder configurarse mediante variables de entorno para host y puerto; sus valores predeterminados MUST ser `localhost` y `6379`, respectivamente.
- **FR-005**: Los nombres exactos de las variables de entorno MUST quedar definidos en la documentación y ser idénticos en Windows, Linux y CI.
- **FR-006**: El esquema de datos usado por el perfil `e2e` MUST configurarse con política `create-drop`.
- **FR-007**: Bajo el perfil `e2e`, Football-Data y WhoScored MUST apuntar exclusivamente a direcciones de loopback deliberadamente inválidas y MUST NOT apuntar a destinos públicos.
- **FR-008**: El backend MUST proporcionar una meta-anotación compartida para tests e2e que combine, en una única declaración, arranque de la aplicación en puerto aleatorio, activación exclusiva del perfil `e2e` y tag `e2e`.
- **FR-009**: La selección por tag MUST permitir ejecutar sólo tests no e2e, sólo tests e2e o ambos grupos en una verificación completa.
- **FR-010**: La convención obligatoria para tests e2e MUST garantizar la limpieza de todos los datos de aplicación en PostgreSQL y de todas las claves de la instancia Redis configurada antes de cada método de toda clase marcada con `@E2ETest`; la limpieza MUST NOT depender de que cada autor agregue manualmente un componente adicional.
- **FR-011**: La limpieza MUST ocurrir antes de cada método de prueba y MUST hacer que el resultado sea independiente del orden y de ejecuciones anteriores.
- **FR-012**: Antes de ejecutar un e2e local, la suite MUST comprobar la disponibilidad de PostgreSQL y Redis configurados.
- **FR-013**: Fuera de CI, si uno o ambos servicios no están disponibles, cada test e2e afectado MUST omitirse con un motivo visible que enumere los servicios ausentes.
- **FR-014**: Cuando `CI=true`, los tests e2e MUST ejecutarse sin omisión por disponibilidad; si PostgreSQL o Redis no están disponibles, la verificación MUST fallar e identificar la dependencia causante.
- **FR-015**: Debe existir un test de humo e2e que realice por HTTP real y en este orden: `POST /auth/register`, `POST /auth/login`, `GET /players` autenticado con el token Bearer obtenido y `GET /actuator/health`.
- **FR-016**: El test de humo MUST comprobar que el registro y el login son exitosos, que el login devuelve un token utilizable y que `GET /players` responde HTTP 200.
- **FR-017**: El test de humo MUST comprobar que la respuesta de `GET /players` incluye una cabecera `X-Correlation-ID` presente y no vacía.
- **FR-018**: El test de humo MUST comprobar que `/actuator/health` informa estado general `UP`.
- **FR-019**: La documentación MUST incluir comandos directamente ejecutables en Windows y Linux para: sólo unitarios/no e2e, sólo e2e y verificación completa.
- **FR-020**: La documentación MUST explicar las variables configurables, sus valores predeterminados, los servicios requeridos y la diferencia de comportamiento entre ejecución local y `CI=true`.
- **FR-021**: La feature MUST conservar sin cambios el perfil `test`, `UnitTestProfile` y el comportamiento de los tests existentes.
- **FR-022**: La feature MUST NOT modificar `pom.xml`, `ci.yml`, ningún `application.properties` existente, `SecurityConfig`, filtros ni controllers sin aprobación explícita del equipo.
- **FR-023**: Si un cambio en un archivo protegido resulta imprescindible durante planificación o implementación, el trabajo MUST registrar `[NEEDS CLARIFICATION]` con el archivo, el cambio mínimo propuesto y una alternativa que no lo modifique, y MUST esperar respuesta antes de aplicarlo.
- **FR-024**: La feature MUST NOT agregar dependencias.
- **FR-025**: La feature MUST NOT incluir pruebas de resiliencia ni escenarios de caída de Football-Data o WhoScored, reservados para Feature 012.

### Key Entities

- **Perfil `e2e`**: Entorno nominal de prueba que reúne aplicación completa, puerto aleatorio, servicios reales, esquema efímero y fuentes externas aisladas.
- **Meta-anotación e2e**: Declaración compartida que aplica de forma indivisible el arranque completo, el perfil `e2e` y el tag de selección.
- **Dependencia de infraestructura**: PostgreSQL o Redis configurado para la ejecución, con ubicación efectiva, disponibilidad y valores predeterminados compatibles con CI.
- **Estado limpio de prueba**: Ausencia de datos de aplicación en la base y de claves en Redis inmediatamente antes de cada método e2e.
- **Recorrido de humo**: Secuencia HTTP real de registro, login, consulta autenticada de jugadores y comprobación de salud.
- **Grupo de pruebas**: Selección reproducible de tests no e2e, e2e o todos, documentada para Windows y Linux.
- **Archivo protegido de Entrega 1**: Archivo o categoría de código cuya modificación exige consulta y aprobación explícitas.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los tests marcados con la meta-anotación inicia con exactamente el perfil `e2e`, un puerto no fijado por el test y el tag `e2e` efectivo.
- **SC-002**: Con ambos servicios disponibles, el recorrido de humo completa sus cuatro interacciones en orden y verifica el 100% de sus resultados obligatorios: registro exitoso, login exitoso con token, jugadores con estado 200 y correlation ID no vacío, y salud `UP`.
- **SC-003**: En ejecuciones locales controladas con una o ambas dependencias ausentes, el 100% de los tests e2e se omite y el motivo nombra todas las dependencias faltantes.
- **SC-004**: En ejecuciones con `CI=true` y una o ambas dependencias ausentes, el 100% de los e2e intenta ejecutarse y la verificación falla sin registrar tests omitidos por disponibilidad.
- **SC-005**: Dos tests consecutivos pueden crear los mismos datos y claves sin colisiones; el segundo observa cero residuos del primero al comenzar.
- **SC-006**: El quickstart lista los tres comandos para ejecutar sólo unitarios/no e2e, sólo e2e y la verificación completa.
- **SC-007**: Cada opción configurable de PostgreSQL y Redis tiene un valor predeterminado documentado y puede reemplazarse por entorno sin editar archivos del repositorio.
- **SC-008**: La feature introduce cero dependencias nuevas, cero cambios en archivos protegidos sin aprobación y cero cambios observables en el perfil `test` y sus tests existentes.
- **SC-009**: La verificación completa usada por CI incluye el test de humo e2e y finaliza exitosamente cuando PostgreSQL y Redis están disponibles.

## Assumptions

- `specs/010-perfil-e2e` es el directorio solicitado y es independiente del nombre de cualquier rama Git.
- El test de humo puede registrar un usuario generado de forma única y no necesita datos precargados de jugadores; una respuesta vacía de `GET /players` es aceptable si cumple estado, autenticación y trazabilidad.
- El tag `e2e` es la clasificación canónica; “unitarios” en los comandos solicitados significa todos los tests que no poseen ese tag, incluyendo las integraciones livianas cubiertas por el perfil `test`.
- Los valores predeterminados de conexión reflejan el CI descrito por el equipo: PostgreSQL 16 y Redis 7 en `localhost`, sin que la especificación requiera aprovisionar esos servicios.
- Sólo `CI=true`, comparado sin distinguir mayúsculas y minúsculas, deshabilita la omisión por falta de infraestructura.
- La limpieza de Redis comprende todas las claves de la instancia seleccionada para e2e; se asume que esa instancia o base lógica está dedicada a pruebas y no contiene datos que deban conservarse.
- La limpieza de PostgreSQL comprende todos los datos de aplicación del esquema e2e y debe preservar la estructura necesaria para que el test pueda ejecutarse.
- Los endpoints, contratos de autenticación, control de acceso, correlation ID y health check ya existen; esta feature los valida sin cambiar sus implementaciones protegidas.
- Pueden agregarse nuevos recursos de perfil, utilidades, anotaciones y tests fuera de los archivos protegidos, siempre que no se agreguen dependencias.

## Out of Scope

- Pruebas de resiliencia, reintentos, circuit breakers o comportamiento funcional ante caídas de Football-Data y WhoScored; corresponden a Feature 012.
- Cambios funcionales en registro, login, catálogo de jugadores, seguridad, filtros, correlation ID o health check.
- Aprovisionar PostgreSQL o Redis, crear contenedores o modificar el workflow de CI.
- Poblar un catálogo de jugadores como condición del test de humo.
- Pruebas de rendimiento, carga, concurrencia, resiliencia o recuperación.
- Migrar tests existentes al perfil `e2e` o modificar la semántica del perfil `test`.
- Modificar archivos protegidos de Entrega 1 o incorporar dependencias sin la consulta y aprobación requeridas.
