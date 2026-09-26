# Feature Specification: Autenticación JWT en endpoints protegidos

**Feature Branch**: N/A — no se creó una rama para esta especificación

**Created**: 2026-09-26

**Status**: Draft

**Input**: User description: "Los endpoints protegidos aceptan como alternativas el JWT obtenido en el login o la API key existente, con precedencia estricta del Bearer, sin modificar los contratos de registro y login."

## Contexto y dependencias

Esta feature depende de `specs/001-seguridad-infraestructura` y `specs/002-auth-usuario`.

Actualmente, el inicio de sesión emite un JWT para que el usuario continúe usando la aplicación, pero las rutas protegidas sólo aceptan la API key. El usuario de la aplicación web queda obligado a conocer una credencial pensada para acceso programático y que, además, sólo se muestra una vez durante el registro.

Esta feature completa el flujo ya definido: el usuario inicia sesión con usuario y contraseña, recibe un JWT y el frontend lo presenta como `Authorization: Bearer <jwt>` en las solicitudes posteriores. La API key continúa siendo una alternativa independiente para Swagger, scripts y otras integraciones programáticas.

La inspección del código vigente confirma que:

- `POST /auth/login` genera el JWT y lo devuelve al cliente.
- Las rutas protegidas sólo pasan por la validación de `X-API-KEY`.
- Las capacidades existentes para validar el JWT y obtener su username todavía no participan en la autenticación de solicitudes.
- Los esquemas `bearerAuth` y `apiKeyAuth` ya están declarados para la documentación, pero los endpoints de jugadores sólo anuncian la API key.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Usar la aplicación con la sesión iniciada (Priority: P1)

Como usuario de la aplicación web, quiero acceder a las rutas protegidas mediante el JWT que obtuve al iniciar sesión, para usar la aplicación únicamente con mi usuario y contraseña sin ver, copiar ni administrar una API key.

**Why this priority**: Es el flujo principal para usuarios humanos. Sin esta capacidad, el JWT entregado por el login no permite realizar ninguna acción protegida y el flujo web queda incompleto.

**Independent Test**: Registrar un usuario, iniciar sesión con sus credenciales y consultar `GET /players` enviando únicamente el token retornado por el login como Bearer; la consulta debe responder `200` y reconocer como principal al username del token.

**Acceptance Scenarios**:

1. **Given** un usuario registrado que inició sesión correctamente, **When** solicita `GET /players` con `Authorization: Bearer <jwt>` usando el token recibido del login, **Then** obtiene `200` sin enviar `X-API-KEY`.
2. **Given** un JWT válido, **When** se autentica una solicitud protegida, **Then** el principal autenticado es el subject (username) contenido en el token.
3. **Given** un usuario recién registrado, **When** completa el flujo registro → login → `GET /players` con el JWT del login, **Then** las tres operaciones se completan exitosamente sin que el usuario tenga que utilizar la API key recibida durante el registro.

---

### User Story 2 - Rechazar tokens Bearer no confiables (Priority: P1)

Como responsable de la seguridad del sistema, quiero que todo Bearer inválido sea rechazado de manera uniforme y definitiva, para impedir que una credencial alternativa o un error de formato oculte un intento fallido de autenticación de usuario.

**Why this priority**: La precedencia inequívoca evita resultados ambiguos y asegura que un cliente que presenta un JWT inválido no pueda autenticarse accidentalmente por otro mecanismo en la misma solicitud.

**Independent Test**: Enviar a `GET /players` tokens vencidos, con firma inválida y mal formados, tanto solos como acompañados por una API key válida; todos deben recibir el mismo `401` de JWT y ninguno debe continuar hacia la validación por API key.

**Acceptance Scenarios**:

1. **Given** un JWT vencido, **When** se envía como `Authorization: Bearer <jwt>` a `GET /players`, **Then** la respuesta es `401` con `{"error":"unauthorized","message":"Token inválido o vencido"}`.
2. **Given** un JWT cuya firma no es válida, **When** se envía como Bearer a `GET /players`, **Then** la respuesta es `401` con el mismo cuerpo de error.
3. **Given** un valor mal formado como `Authorization: Bearer abc`, **When** se envía a `GET /players`, **Then** la respuesta es `401` con el mismo cuerpo de error.
4. **Given** un Bearer inválido y una `X-API-KEY` válida en la misma solicitud, **When** se solicita `GET /players`, **Then** la respuesta es `401` por el token y la API key no se evalúa como alternativa.

---

### User Story 3 - Conservar el acceso programático por API key (Priority: P1)

Como consumidor programático, quiero seguir accediendo a las rutas protegidas con mi API key, para que scripts, Swagger e integraciones existentes continúen funcionando sin cambios.

**Why this priority**: La nueva autenticación de usuarios no debe romper el mecanismo ya entregado ni sus mensajes de error.

**Independent Test**: Consultar `GET /players` sin un Bearer y con una API key válida, inválida y ausente; debe mantenerse exactamente el comportamiento previo de la API key.

**Acceptance Scenarios**:

1. **Given** una `X-API-KEY` válida y ningún header Authorization Bearer, **When** se solicita `GET /players`, **Then** la respuesta es `200`.
2. **Given** que no se envía ninguna credencial, **When** se solicita `GET /players`, **Then** la respuesta es `401` con el mensaje actual de ausencia de `X-API-KEY`.
3. **Given** `Authorization: Basic xxx` y una `X-API-KEY` válida, **When** se solicita `GET /players`, **Then** el esquema no Bearer se ignora y la respuesta es `200` por la API key.
4. **Given** un Authorization no Bearer y ninguna API key, **When** se solicita una ruta protegida, **Then** se aplica el mismo rechazo por API key ausente que existe actualmente.

---

### User Story 4 - Mantener públicas las rutas públicas (Priority: P1)

Como usuario nuevo o recurrente, quiero registrarme e iniciar sesión sin presentar credenciales previas, para poder obtener las credenciales que usaré posteriormente.

**Why this priority**: Si cualquiera de los mecanismos de autenticación intercepta estas rutas, se vuelve imposible entrar al sistema.

**Independent Test**: Ejecutar registro y login sin headers de autenticación, incluyendo solicitudes con un Bearer inválido, y verificar que conservan sus contratos y no son rechazados por los mecanismos de las rutas protegidas.

**Acceptance Scenarios**:

1. **Given** una solicitud sin JWT ni API key, **When** se invoca `POST /auth/register`, **Then** la ruta sigue siendo pública y conserva sin cambios su request, respuesta y errores.
2. **Given** una solicitud sin JWT ni API key, **When** se invoca `POST /auth/login`, **Then** la ruta sigue siendo pública y conserva sin cambios su request, respuesta y errores.
3. **Given** cualquier ruta incluida en la lista pública vigente, **When** recibe una solicitud, **Then** no exige ni valida JWT ni API key.

---

### User Story 5 - Entender las alternativas desde la documentación (Priority: P2)

Como persona que prueba o integra la API, quiero que la documentación de cada endpoint protegido muestre Bearer y API key como alternativas, para elegir una sola credencial apropiada sin interpretar erróneamente que ambas son obligatorias.

**Why this priority**: La documentación debe reflejar el comportamiento real y preservar el acceso programático, pero esta historia depende de que ambos mecanismos funcionen primero.

**Independent Test**: Inspeccionar la documentación de las rutas protegidas y comprobar que cada operación anuncia `bearerAuth` O `apiKeyAuth`, mientras que las rutas públicas no exigen ninguno.

**Acceptance Scenarios**:

1. **Given** la documentación de un endpoint protegido, **When** se consultan sus requisitos de seguridad, **Then** presenta `bearerAuth` y `apiKeyAuth` como alternativas y no como requisitos simultáneos.
2. **Given** la documentación de registro o login, **When** se consultan sus requisitos de seguridad, **Then** no exige ninguno de los dos esquemas.

### Edge Cases

- Un header `Authorization` cuyo valor comienza con `Bearer ` pero no contiene un token utilizable se considera un Bearer mal formado y recibe el error de token; no usa la API key como fallback.
- Un header `Authorization` con un esquema diferente de Bearer se ignora para esta decisión, incluso si su contenido parece un JWT; la solicitud continúa por la validación existente de API key.
- Una solicitud con Bearer válido y API key ausente, inválida o inactiva queda autenticada por el Bearer; no necesita una segunda credencial.
- Una solicitud con Bearer inválido y API key válida se rechaza por el Bearer, respetando la precedencia definida.
- Un JWT que vence antes de la solicitud se rechaza; no se agrega tolerancia temporal ni se modifica la vigencia configurada.
- Las rutas públicas omiten ambas validaciones incluso si reciben headers de autenticación ausentes, inválidos o de esquemas no soportados.
- La respuesta a un JWT inválido mantiene contenido JSON en UTF-8 y el formato común de errores de autenticación.

## Requirements *(mandatory)*

### Functional Requirements

**Autenticación alternativa**

- **FR-001**: Toda ruta que actualmente requiere autenticación MUST aceptar una de dos credenciales alternativas: un JWT Bearer válido o una `X-API-KEY` válida.
- **FR-002**: Una solicitud protegida con un header Authorization que comienza con `Bearer ` MUST evaluar primero y exclusivamente esa credencial.
- **FR-003**: Un Bearer con firma válida y que no está vencido MUST autenticar la solicitud y establecer como principal el subject (username) del token.
- **FR-004**: Un Bearer vencido, con firma inválida o mal formado MUST detener la solicitud y responder `401` con el cuerpo exacto `{"error":"unauthorized","message":"Token inválido o vencido"}`.
- **FR-005**: Cuando un Bearer es inválido, el sistema MUST NOT intentar autenticarse mediante `X-API-KEY`, aun cuando la misma solicitud incluya una API key válida.
- **FR-006**: Cuando el header Authorization está ausente o no usa el esquema Bearer, el sistema MUST aplicar la validación existente de `X-API-KEY` sin cambiar sus condiciones, mensajes ni cuerpos de error.
- **FR-007**: Un esquema de Authorization distinto de Bearer, incluido Basic, MUST ser ignorado por la validación de JWT y MUST permitir que la solicitud continúe hacia la validación de API key.
- **FR-008**: Cuando el Bearer autentica exitosamente la solicitud, la validación de API key MUST omitirse, aunque `X-API-KEY` esté ausente, sea inválida o esté inactiva.

**Rutas públicas**

- **FR-009**: Las rutas de registro, login, documentación y salud que integran la lista pública vigente MUST continuar públicas y MUST omitir ambas validaciones de credenciales.
- **FR-010**: `POST /auth/register` y `POST /auth/login` MUST conservar exactamente sus contratos vigentes, incluida la emisión de la API key en la respuesta de registro y del JWT en la respuesta de login.
- **FR-011**: La lista de rutas públicas MUST permanecer sin cambios como parte de esta feature.

**Compatibilidad y documentación**

- **FR-012**: La validación y el comportamiento de las API keys existentes MUST permanecer sin cambios para todas las solicitudes que no presenten un Bearer.
- **FR-013**: Todos los endpoints protegidos MUST documentar `bearerAuth` y `apiKeyAuth` como alternativas lógicas, de modo que una sola de las dos credenciales sea suficiente.
- **FR-014**: Las rutas públicas MUST continuar documentadas sin requisitos de Bearer ni API key.
- **FR-015**: La feature MUST reutilizar la emisión, validación, subject y vigencia del JWT existentes; MUST NOT cambiar el tiempo de expiración configurado, la firma ni el contrato del token.

**Verificación**

- **FR-016**: Los tests MUST cubrir individualmente los siguientes casos sobre `GET /players`: Bearer válido; Bearer vencido; Bearer con firma inválida; Bearer mal formado; Bearer inválido junto con API key válida; API key válida sin Bearer; ausencia de ambas credenciales; y Authorization Basic junto con API key válida.
- **FR-017**: Los tests MUST verificar que registro y login permanecen públicos y conservan sus contratos.
- **FR-018**: Los tests MUST cubrir el flujo integrado registro → login → consulta protegida con el JWT emitido y verificar una respuesta exitosa.
- **FR-019**: Los tests MUST verificar que la documentación de los endpoints protegidos representa Bearer y API key como alternativas, no como credenciales simultáneamente obligatorias.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las solicitudes a rutas protegidas con un JWT vigente y auténtico se completa sin requerir una API key y queda asociado al username del token.
- **SC-002**: El 100% de los JWT vencidos, mal formados o con firma inválida recibe `401` con el mensaje definido, incluso cuando la solicitud también incluye una API key válida.
- **SC-003**: El 100% de los casos previamente válidos de acceso mediante API key continúa produciendo el mismo resultado cuando no se presenta un Bearer.
- **SC-004**: Registro y login continúan disponibles sin credenciales previas en el 100% de los escenarios de aceptación y conservan sus contratos de entrada, éxito y error.
- **SC-005**: Un usuario puede completar el flujo registro → login → consulta de jugadores usando solamente usuario, contraseña y el JWT administrado por el frontend, sin copiar ni ingresar una API key.
- **SC-006**: El 100% de las operaciones protegidas publicadas permite identificar en la documentación que Bearer y API key son alternativas; ninguna comunica que ambas sean obligatorias.
- **SC-007**: Cada uno de los diez criterios de aceptación solicitados cuenta con al menos una prueba automatizada y la suite existente no presenta regresiones.

## Assumptions

- Los JWT aceptados son los emitidos por el login vigente, que siempre contienen como subject el username autenticado.
- El frontend es responsable de conservar temporalmente el JWT recibido y enviarlo como Bearer; el almacenamiento concreto del lado cliente no forma parte de esta feature.
- “Bearer válido” conserva la definición existente: firma correcta con el secreto configurado y fecha de expiración no superada.
- La API key sigue orientada a Swagger, scripts e integraciones programáticas; conservar su emisión en el registro satisface el requisito constitucional sin obligar al usuario web a manejarla.
- No se agregan permisos diferenciados: una autenticación exitosa por cualquiera de los dos mecanismos concede el mismo acceso que hoy concede una API key válida.
- El formato general de los errores de autenticación continúa siendo `{"error":"unauthorized","message":"<detalle>"}`.

## Out of Scope

- Cambios en requests, responses, validaciones o errores de `POST /auth/register` y `POST /auth/login`.
- Eliminación de la API key de la respuesta de registro.
- Refresh tokens, cierre de sesión del lado servidor, revocación o lista negra de JWT.
- Roles, permisos diferenciados o autorización por recurso.
- Cookies, sesiones de servidor o cambios en la forma en que el frontend almacena el JWT.
- Cambios en la vigencia del JWT, que continúa gobernada por la configuración existente y mantiene una hora por defecto.
- Nuevos endpoints, incluido cualquier endpoint de perfil como `/auth/me`.
- Cambios en los modelos, almacenamiento o repositorios de usuarios y API keys.
- Rotación, recuperación o nueva visualización de API keys.
- Modificación retroactiva de las especificaciones de seguridad y autenticación de las que depende esta feature.
