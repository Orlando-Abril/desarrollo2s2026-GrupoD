# Feature Specification: Infraestructura Transversal de Seguridad

**Feature Branch**: `001-seguridad-infraestructura`

**Created**: 2026-09-16

**Status**: Draft

**Input**: User description: "Es una feature técnica interna (sin pantallas propias), que deja lista la infraestructura de seguridad y documentación que van a usar las demás features de esta entrega. Alcance: 1) Configuración base de Spring Security con SecurityFilterChain (endpoints públicos vs protegidos) y CORS habilitado para http://localhost:5173. 2) Utilidad para generar y validar JWT (sin lógica de usuarios todavía). 3) Filtro de autorización por ApiKey vía header X-API-KEY contra hash en tabla api_keys. 4) Bean de BCryptPasswordEncoder. 5) Configuración base de OpenAPI/Swagger v3 con esquemas de seguridad Bearer (JWT) y ApiKey. No implementa endpoints de negocio (registro, login, catálogo)."

## User Scenarios & Testing *(mandatory)*

<!--
  Esta es una feature técnica interna sin pantallas propias: sus "usuarios" son los equipos
  de desarrollo que construyen las features de negocio (registro/login, catálogo, trading,
  portfolio) sobre esta infraestructura. El valor se mide en que esas features puedan
  apoyarse en piezas de seguridad ya listas, probadas y documentadas, sin reconstruirlas.
-->

### User Story 1 - Habilitar rutas públicas y protegidas con CORS (Priority: P1)

Como equipo que va a implementar el registro y el login, necesito que el sistema ya distinga qué endpoints son públicos (registro, login, documentación) de cuáles requieren autenticación, y que las peticiones del frontend (http://localhost:5173) no sean bloqueadas por CORS, para poder exponer esos endpoints sin tener que configurar la seguridad de base desde cero.

**Why this priority**: Sin esta pieza, ninguna otra feature puede exponer un endpoint HTTP de forma segura ni ser consumida por el frontend. Es la base sobre la que se apoyan todas las demás.

**Independent Test**: Se puede verificar de forma independiente registrando un endpoint de prueba marcado como protegido y otro como público en la configuración, y comprobando que una request sin credenciales es rechazada en el protegido y aceptada en el público, y que una request OPTIONS/GET desde http://localhost:5173 recibe los headers CORS correctos mientras que desde otro origen no.

**Acceptance Scenarios**:

1. **Given** un endpoint configurado como público, **When** se le hace una request sin ningún tipo de credencial, **Then** la request se procesa normalmente (no se devuelve 401).
2. **Given** un endpoint configurado como protegido, **When** se le hace una request sin credenciales válidas, **Then** el sistema responde 401 y no ejecuta la lógica del endpoint.
3. **Given** una request originada en http://localhost:5173, **When** se envía a cualquier endpoint del backend, **Then** la respuesta incluye los headers CORS que permiten que el navegador la acepte.
4. **Given** una request originada en un origen distinto a http://localhost:5173, **When** se envía al backend, **Then** el navegador no recibe autorización CORS para leer la respuesta.

---

### User Story 2 - Emitir y validar tokens JWT (Priority: P1)

Como equipo que va a implementar el login, necesito una utilidad ya lista que, a partir de un nombre de usuario, emita un token firmado y que luego permita validar ese token en requests posteriores, para no tener que resolver la generación y verificación de tokens dentro de la feature de login.

**Why this priority**: El login (feature separada) depende directamente de esta capacidad para poder devolver un token de sesión; sin ella, no hay forma de mantener autenticado a un usuario entre requests.

**Independent Test**: Se puede probar de forma independiente y aislada de cualquier lógica de usuarios: se invoca la utilidad con un username arbitrario, se obtiene un token, y se verifica que la misma utilidad lo valida correctamente y extrae el username original; también se verifica que un token alterado o vencido es rechazado.

**Acceptance Scenarios**:

1. **Given** un nombre de usuario válido, **When** se solicita la emisión de un token, **Then** se obtiene un token firmado que identifica a ese usuario.
2. **Given** un token emitido por el sistema, **When** se valida antes de su expiración, **Then** la validación es exitosa y se puede recuperar el username original.
3. **Given** un token expirado o con la firma alterada, **When** se intenta validar, **Then** la validación falla de forma explícita.

---

### User Story 3 - Autorizar requests de servicio mediante ApiKey (Priority: P2)

Como equipo que va a exponer endpoints que deben ser consumidos por servicios o integraciones (no por un usuario logueado), necesito un filtro que valide el header X-API-KEY contra las claves activas registradas, para poder proteger esos endpoints sin depender del flujo de sesión de usuario.

**Why this priority**: Es necesaria para las features que exponen accesos de tipo servicio-a-servicio, pero depende de que ya exista la base de Spring Security (US1), por lo que se prioriza justo después.

**Independent Test**: Se puede probar generando un hash de prueba en la tabla `api_keys` y verificando que una request con el valor en claro correspondiente en el header `X-API-KEY` es aceptada, mientras que una request sin el header, con un valor incorrecto, o con una key marcada como inactiva, es rechazada con 401.

**Acceptance Scenarios**:

1. **Given** una ApiKey activa registrada en el sistema, **When** se envía una request con el header `X-API-KEY` con el valor correspondiente, **Then** la request continúa su procesamiento normal.
2. **Given** una request a un endpoint protegido por ApiKey, **When** no se incluye el header `X-API-KEY`, **Then** el sistema responde 401.
3. **Given** un valor de `X-API-KEY` que no coincide con ningún hash almacenado, **When** se envía la request, **Then** el sistema responde 401.
4. **Given** una ApiKey marcada como inactiva, **When** se envía una request con su valor en claro, **Then** el sistema responde 401.

---

### User Story 4 - Documentación de seguridad autodescriptiva (Priority: P3)

Como equipo que va a implementar cualquier endpoint de negocio, necesito que la documentación interactiva de la API ya tenga configurados los esquemas de seguridad (Bearer/JWT y ApiKey) y la información general del proyecto, para poder simplemente anotar mis controllers y que la documentación refleje correctamente cómo autenticarse.

**Why this priority**: Mejora la experiencia de integración y consumo de la API, pero no bloquea la construcción de endpoints de negocio como sí lo hacen las piezas anteriores.

**Independent Test**: Se puede verificar accediendo a la documentación interactiva generada y comprobando que expone la información del proyecto y que los esquemas de seguridad "Bearer" y "ApiKey" están disponibles para ser referenciados, sin necesidad de que exista todavía ningún endpoint de negocio documentado.

**Acceptance Scenarios**:

1. **Given** la aplicación en ejecución, **When** se accede a la documentación interactiva, **Then** se muestra la información general del proyecto (nombre, versión, descripción).
2. **Given** la documentación interactiva, **When** se inspeccionan los esquemas de seguridad disponibles, **Then** figuran definidos los esquemas "Bearer" (JWT) y "ApiKey".

---

### Edge Cases

- ¿Qué sucede si una request llega sin el header `Authorization` ni `X-API-KEY` a un endpoint protegido? El sistema debe responder 401 sin exponer detalles internos del motivo del rechazo.
- ¿Qué sucede si el token JWT tiene una firma válida pero corresponde a otro emisor/secreto? Debe ser rechazado igual que un token inválido.
- ¿Qué sucede si se intenta hashear un password nulo o vacío? El sistema debe rechazar la operación de forma controlada en lugar de persistir un hash inválido.
- ¿Qué sucede con una request preflight (`OPTIONS`) desde un origen no autorizado? No debe recibir los headers CORS que le permitirían continuar desde el navegador.
- ¿Qué sucede si coexisten en la misma request un `X-API-KEY` válido y un JWT inválido (o viceversa) en un endpoint que sólo exige uno de los dos mecanismos? Debe evaluarse únicamente el mecanismo requerido por ese endpoint.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST definir explícitamente, mediante una única configuración central, qué endpoints son públicos (al menos registro, login y la documentación interactiva) y cuáles requieren autenticación.
- **FR-002**: El sistema MUST rechazar con código 401 toda request a un endpoint protegido que no incluya credenciales válidas (JWT o ApiKey, según el mecanismo que ese endpoint requiera).
- **FR-003**: El sistema MUST permitir solicitudes cross-origin únicamente desde el origen del frontend (http://localhost:5173), incluyendo el manejo correcto de requests preflight.
- **FR-004**: El sistema MUST proveer una capacidad para emitir un token de sesión firmado a partir de un nombre de usuario, con una expiración definida.
- **FR-005**: El sistema MUST proveer una capacidad para validar un token de sesión emitido previamente, determinando si es válido (firma correcta y no expirado) y permitiendo recuperar el nombre de usuario asociado.
- **FR-006**: El sistema MUST exponer un filtro que, para los endpoints que lo requieran, valide el header `X-API-KEY` contra las claves registradas (comparando contra su valor hasheado) y permita continuar la request sólo si la clave existe, coincide y está activa.
- **FR-007**: El sistema MUST responder 401 cuando el header `X-API-KEY` esté ausente, no coincida con ninguna clave registrada, o corresponda a una clave inactiva.
- **FR-008**: El sistema MUST proveer un mecanismo reutilizable para generar el hash de un password en texto plano, de forma que ningún componente necesite implementar su propio hashing.
- **FR-009**: El sistema MUST exponer documentación interactiva de la API que incluya la información general del proyecto (nombre, versión, descripción) y los esquemas de seguridad "Bearer" (JWT) y "ApiKey" disponibles para que otras features los referencien.
- **FR-010**: Esta feature MUST NOT incluir endpoints de negocio propios (registro, login, catálogo u otros): sólo provee la infraestructura que dichas features consumirán.
- **FR-011**: El mecanismo de hashing provisto por esta feature (FR-008) MUST producir un hash que sea seguro de persistir o loguear (nunca el password en texto plano). La responsabilidad de invocar ese mecanismo antes de persistir o loguear un password —y de no manejar el valor en texto plano en ningún otro punto— recae en la feature que efectivamente reciba y almacene passwords (registro/login), que está fuera del alcance de esta entrega.

### Key Entities

- **ApiKey**: Credencial de acceso a nivel de servicio. Se identifica por un hash (no se guarda en texto plano), un prefijo visible para identificación humana, un propietario, un estado activo/inactivo y metadatos de uso (creación, último uso). Es la entidad contra la que se valida el header `X-API-KEY`.
- **Token de sesión (JWT)**: Representación no persistida de una sesión autenticada; contiene como mínimo el nombre de usuario y una fecha de expiración, y está firmado para garantizar su integridad.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las requests sin credenciales válidas contra un endpoint protegido reciben una respuesta 401, sin excepción.
- **SC-002**: El 100% de las requests contra un endpoint público se procesan sin exigir ningún tipo de credencial.
- **SC-003**: Un equipo que implemente la feature de login puede emitir y validar un token de sesión reutilizando esta infraestructura sin escribir lógica propia de firma o verificación criptográfica.
- **SC-004**: Las solicitudes originadas en http://localhost:5173 se completan sin ser bloqueadas por política de CORS, mientras que las originadas en cualquier otro origen no reciben autorización para ser leídas por el navegador.
- **SC-005**: Un equipo que implemente un nuevo endpoint puede indicar que requiere autenticación Bearer o ApiKey agregando una única anotación, sin tener que redefinir los esquemas de seguridad en la documentación.
- **SC-006**: El 100% de las requests con un `X-API-KEY` inválido, ausente o inactivo son rechazadas con 401 antes de ejecutar cualquier lógica de negocio del endpoint.

## Assumptions

- El registro, el login y el catálogo se implementan en features separadas que consumirán esta infraestructura; esta feature no crea esos endpoints.
- La tabla `api_keys` (y su hash de clave) ya existe como parte del modelo de datos; esta feature sólo agrega la validación de esa clave en tiempo de request, no su generación ni rotación.
- Además de registro y login, la documentación interactiva de la API (Swagger/OpenAPI) también se considera pública por defecto, siguiendo la práctica estándar de dejarla accesible sin autenticación.
- El origen permitido para CORS en esta entrega es únicamente el de desarrollo del frontend (http://localhost:5173); la incorporación de otros orígenes (staging, producción) queda fuera de este alcance y se resolverá en configuración futura.
- El tiempo de expiración del token de sesión y el secreto usado para firmarlo se definen como configuración de la aplicación (no como valores fijos en el código), pero la gestión avanzada de secretos (rotación automática, almacenamiento en un vault externo) está fuera de alcance.
- Cada endpoint de negocio define, al momento de implementarse, si requiere autenticación Bearer (JWT) o ApiKey (o ninguna); esta feature no decide esa asignación para features futuras, sólo deja disponibles ambos mecanismos.
- Esta feature no maneja passwords en texto plano en ningún momento (no persiste ni loguea ninguno): sólo entrega el mecanismo de hashing reutilizable (FR-008/FR-011). La garantía de que un password nunca circule ni se guarde en texto plano depende de que la feature de registro/login invoque ese mecanismo antes de cualquier persistencia o logging.
