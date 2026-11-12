# Feature Specification: Auditoría de invocaciones a web services

**Feature Branch**: `feature/auditoria-web-services`

**Created**: 2026-11-12

**Status**: Draft

**Input**: User description: "SPECIFY_FEATURE_DIRECTORY=specs/034-auditoria-web-services. Cada invocación a un endpoint REST deja una línea de auditoría en el log con timestamp, usuario, operación, parámetros y tiempo de ejecución, resuelta con un aspecto. Toda invocación a un método de un @RestController del paquete controller genera exactamente una línea, también cuando termina en excepción; la línea tiene timestamp, usuario (username del JWT, nombre de la API key o "anonymous"), operación (método HTTP, ruta y Clase.método), parámetros, tiempo en ms, resultado (OK o tipo de excepción) y correlationId; los parámetros enmascaran password, refreshToken y cualquier token, truncan valores largos y no incluyen HttpServletRequest/HttpServletResponse ni similares; logger propio de auditoría con nivel configurable, al console en el mismo JSON y a un archivo rotativo logs/audit.log; si falla armar la línea el endpoint responde igual; no se modifica ningún controller. Autorizado: dependencia de AspectJ y logback-spring.xml que mantenga el JSON actual de consola. Fuera de alcance: persistir en base y auditar actuator."

## Contexto y límites

El backend expone su API REST mediante seis controllers (`AuthController`, `OrderController`, `PlayerController`, `QuoteController`, `StrategyController`, `UserMarketController`). Hoy los logs de consola ya salen en JSON con `timestamp`, `level`, `logger`, `correlationId` y `message`, y el `correlationId` de cada request está disponible durante toda su atención (Constitution §5.3). No existe un registro uniforme de quién invocó qué endpoint, con qué parámetros, cuánto tardó y cómo terminó.

Esta feature agrega una **auditoría transversal de web services**: cada invocación a un endpoint deja exactamente una línea de auditoría. El profesor pidió explícitamente que se resuelva como **aspecto**: la lógica de auditoría se escribe una sola vez y se aplica a todos los endpoints por una regla de selección (pointcut), sin tocar ningún método ni controller existente. Para eso quedan autorizadas dos únicas incorporaciones de infraestructura: la dependencia de AspectJ y un archivo de configuración de logging (`logback-spring.xml`) que conserve el formato JSON actual de la consola.

Esta auditoría es **sólo de logs**. No reemplaza ni modifica la auditoría de transacciones del mercado que se guarda en base (`TransactionAuditService`, Entrega 2), que es otra cosa y no se toca. Tampoco se auditan los endpoints de actuator.

Archivos de entregas anteriores no se modifican, salvo lo autorizado: agregar la dependencia en el descriptor de build y crear la configuración de logging (lo que puede implicar mover `logging.pattern.console` a esa configuración sin cambiar el formato resultante).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Rastrear cada invocación a la API (Priority: P1)

Como operador del sistema, quiero que cada llamada a un endpoint REST deje una línea de auditoría con quién la hizo, qué operación invocó, con qué parámetros, cuánto tardó y cómo terminó, para poder investigar incidentes y reconstruir lo que pasó en una request a partir de su correlation id.

**Why this priority**: Es el núcleo del requerimiento; sin esta línea no hay auditoría.

**Independent Test**: Invocar un endpoint autenticado con JWT, uno con API key y uno público sin credenciales; verificar en la salida de log una sola línea de auditoría por invocación, con todos los campos y el mismo `correlationId` que devuelve la respuesta.

**Acceptance Scenarios**:

1. **Given** un usuario `ana` autenticado con JWT, **When** consulta `GET /players?league=PREMIER_LEAGUE&position=DEFENDER`, **Then** se registra exactamente una línea de auditoría con timestamp, usuario `ana`, operación `GET /players PlayerController.<método>`, los parámetros `{"league":"PREMIER_LEAGUE","team":null,"position":"DEFENDER"}` (los ausentes como `null`), tiempo de ejecución en milisegundos (entero ≥ 0), resultado `OK` y el `correlationId` de esa request.
2. **Given** una request con API key válida en `X-API-KEY`, **When** invoca un endpoint de administración, **Then** el usuario de la línea es el nombre asociado a esa API key.
3. **Given** una request sin credenciales a una ruta pública (por ejemplo login o registro), **When** se invoca, **Then** el usuario de la línea es `anonymous`.
4. **Given** un endpoint con variable de ruta (por ejemplo `/players/{id}`), **When** se invoca con `id=42`, **Then** la operación informa el método HTTP y la ruta invocada, y los parámetros se ven como `{"id":42}`.
5. **Given** dos requests concurrentes de distintos usuarios, **When** ambas terminan, **Then** cada una tiene su propia línea con su usuario y su `correlationId`, sin mezclarse.

---

### User Story 2 - Auditar también las invocaciones que fallan (Priority: P1)

Como operador, quiero que las invocaciones que terminan en error también queden auditadas, indicando el tipo de error, para medir fallas y no perder justamente las requests más relevantes para investigar.

**Why this priority**: Una auditoría que sólo registra éxitos es engañosa; el requerimiento exige la línea también ante excepción.

**Independent Test**: Provocar un error de negocio lanzado por el método (por ejemplo una estrategia desconocida, 400), un recurso inexistente (404) y un acceso denegado por `@PreAuthorize` (403); verificar una línea por cada uno con el tipo de excepción como resultado y la respuesta HTTP sin cambios respecto de hoy.

**Acceptance Scenarios**:

1. **Given** un endpoint que lanza una excepción de negocio (por ejemplo jugador inexistente), **When** se invoca, **Then** se registra exactamente una línea con resultado igual al nombre simple del tipo de excepción y el tiempo transcurrido hasta la excepción, y el cliente recibe la misma respuesta de error que antes de esta feature.
2. **Given** una excepción, **When** se audita, **Then** la línea no incluye el stack trace ni el mensaje de la excepción si puede contener datos sensibles; sólo el tipo.
3. **Given** una request rechazada por seguridad antes de llegar al controller (401/403 de la cadena de seguridad), **When** ocurre, **Then** no se genera línea de auditoría de web service, porque ningún método de controller fue invocado.
4. **Given** una request cuyo cuerpo falla `@Valid @RequestBody` o cuyos parámetros fallan la conversión de tipos (400 antes de invocar el método), **When** ocurre, **Then** no se genera línea, por el mismo motivo.
5. **Given** un controller anotado con `@Validated` (Player, Order, UserMarket) y un `@RequestParam`/`@PathVariable` que viola una restricción (`@Min`, `@Max`, `@Pattern`), por ejemplo `GET /users/0/portfolio`, **When** se invoca, **Then** la validación ocurre dentro de la invocación interceptada, se registra una línea con `outcome=ConstraintViolationException` y la respuesta sigue siendo el 400 de siempre.
6. **Given** un USER autenticado que invoca un endpoint restringido a ADMIN por anotación de seguridad en el método, **When** se le deniega el acceso, **Then** se registra una línea con su usuario y como resultado el tipo de la excepción de acceso denegado, y la respuesta sigue siendo 403.

---

### User Story 3 - Proteger datos sensibles en la auditoría (Priority: P1)

Como responsable de seguridad, quiero que la auditoría nunca exponga contraseñas, tokens ni objetos técnicos de la request, y que los valores largos se trunquen, para que el archivo de auditoría no se convierta en una fuga de credenciales ni en un log ilegible.

**Why this priority**: Registrar parámetros sin enmascarar expondría credenciales en texto plano; es un requisito de seguridad, no opcional.

**Independent Test**: Invocar login, registro y renovación de sesión con credenciales conocidas y buscar en los logs los valores literales de contraseña y tokens: no deben aparecer.

**Acceptance Scenarios**:

1. **Given** un login con `{"username":"ana","password":"S3creta!"}`, **When** se audita, **Then** los parámetros muestran `{"request":{"username":"ana","password":"***"}}`, y la cadena `S3creta!` no aparece en ningún log.
2. **Given** una renovación de sesión que recibe un `refreshToken` (en el cuerpo o en cookie), **When** se audita, **Then** el valor aparece como `***`.
3. **Given** un parámetro o campo cuyo nombre contiene `token` o `password` sin distinguir mayúsculas (por ejemplo `accessToken`, `apiToken`, `newPassword`), **When** se audita, **Then** su valor se enmascara.
4. **Given** un parámetro cuyo valor representado supera el límite de longitud, **When** se audita, **Then** se trunca a ese límite y se marca como truncado.
5. **Given** un método que recibe objetos técnicos del contenedor web (request, response, sesión, flujo de entrada/salida, archivo subido, `Authentication`/principal), **When** se audita, **Then** esos argumentos se omiten de los parámetros.

---

### User Story 4 - Auditoría separada, configurable y que nunca rompe la request (Priority: P2)

Como operador, quiero que las líneas de auditoría salgan por un logger propio con nivel configurable, tanto en la consola habitual como en un archivo rotativo propio, y que un fallo de la auditoría nunca afecte la respuesta al cliente.

**Why this priority**: Hace operable la auditoría (filtrar, apagar, retener) y garantiza que no degrade el servicio; depende de que las historias anteriores existan.

**Independent Test**: Levantar la aplicación, invocar endpoints y verificar las líneas en consola (mismo JSON que hoy) y en `logs/audit.log`; subir el nivel del logger de auditoría para desactivarla y comprobar que deja de escribir; forzar un fallo al armar la línea y comprobar que el endpoint responde igual.

**Acceptance Scenarios**:

1. **Given** la aplicación en ejecución, **When** se invoca un endpoint, **Then** la línea de auditoría aparece en la consola con el mismo formato JSON que el resto de los logs (`timestamp`, `level`, `logger`, `correlationId`, `message`) y el `logger` identifica a la auditoría.
2. **Given** la misma invocación, **When** se revisa `logs/audit.log`, **Then** contiene la misma línea, y ese archivo contiene sólo líneas de auditoría.
3. **Given** el nivel del logger de auditoría configurado por encima del nivel con que se emiten las líneas, **When** se invocan endpoints, **Then** no se escribe ninguna línea de auditoría y los endpoints funcionan igual.
4. **Given** un argumento cuya representación lanza un error al armar la línea, **When** se invoca el endpoint, **Then** el cliente recibe la respuesta normal del endpoint y, a lo sumo, se registra un aviso de que la auditoría falló, sin datos sensibles.
5. **Given** un endpoint que lanza una excepción, **When** se audita, **Then** la excepción que recibe el manejo de errores existente es exactamente la original (la auditoría no la reemplaza ni la envuelve).
6. **Given** el resto de los logs de la aplicación, **When** se agrega esta feature, **Then** su formato en consola no cambia.

### Edge Cases

- Una ruta de actuator (`/actuator/health`, métricas) no genera línea de auditoría.
- Un método de controller que llama a otro método del mismo controller produce una sola línea (la de la invocación HTTP).
- Un endpoint con cuerpo grande o lista larga (por ejemplo una página del Álbum con muchos IDs) se trunca y no vuelca el contenido completo.
- Un endpoint protegido con anotación de seguridad en el método (por ejemplo el cambio de estrategia activa) que deniega el acceso se audita con el tipo de excepción de acceso denegado.
- Parámetros nulos u opcionales ausentes se informan como `null`, sin fallar.
- Un DTO anidado con un campo sensible (por ejemplo un objeto que contiene `password`) también se enmascara.
- Si no hay `correlationId` en el contexto, la línea se registra igual con el campo vacío.
- Si no hay autenticación en el contexto, o es anónima, el usuario es `anonymous`.
- Si no se puede determinar la ruta HTTP (invocación fuera de una request), la operación se informa con lo disponible y la línea se registra igual.
- El directorio `logs/` no existe al arrancar: se crea al escribir el primer registro.
- El archivo de auditoría alcanza el límite de rotación: se rota sin perder líneas ni bloquear requests.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Toda invocación a un método público de una clase anotada como controller REST del paquete de controllers MUST generar exactamente una línea de auditoría, tanto si termina normalmente como si termina con excepción.
- **FR-002**: La auditoría MUST resolverse como un aspecto único: la lógica se define una sola vez y se aplica a todos los endpoints mediante una regla de selección, sin modificar ningún controller ni agregar anotaciones a sus métodos. Un controller nuevo en ese paquete MUST quedar auditado sin cambios adicionales.
- **FR-003**: La línea MUST contener: timestamp, usuario, operación, parámetros, tiempo de ejecución en milisegundos, resultado y `correlationId`.
- **FR-004**: El usuario MUST ser el username del JWT si la request se autenticó con JWT, el nombre de la API key si se autenticó con `X-API-KEY`, o `anonymous` si no hay autenticación o es anónima.
- **FR-005**: La operación MUST incluir el método HTTP, la ruta invocada y el identificador `Clase.método` del controller.
- **FR-006**: El resultado MUST ser `OK` cuando el método termina sin excepción, o el nombre simple del tipo de excepción en caso contrario; nunca el mensaje ni el stack trace.
- **FR-007**: El tiempo de ejecución MUST medirse desde que comienza hasta que termina la invocación del método del controller, en milisegundos enteros.
- **FR-008**: Los parámetros MUST representarse como un objeto JSON `{nombre: valor}` en el orden de la firma del método; los objetos (DTOs) se representan con sus campos y los ausentes como `null`.
- **FR-009**: Todo parámetro o campo, a cualquier nivel de anidamiento, cuyo nombre sea `password`, `refreshToken` o contenga `password` o `token` (sin distinguir mayúsculas) MUST mostrarse enmascarado como `***`.
- **FR-010**: La representación de cada parámetro (incluidos DTOs y colecciones completos) que supere 200 caracteres MUST truncarse a ese límite e indicar que fue truncada.
- **FR-011**: Los argumentos técnicos del contenedor web (request, response, sesión, flujos de entrada/salida, archivos subidos, objetos de binding/validación, principal o autenticación) MUST omitirse de los parámetros.
- **FR-012**: Las líneas MUST emitirse por un logger propio de auditoría, distinto de los loggers de las clases, cuyo nivel MUST ser configurable por propiedad sin cambiar código.
- **FR-013**: Las líneas MUST salir por la consola con el mismo formato JSON actual de la aplicación y, además, a un archivo rotativo propio `logs/audit.log` que contenga sólo auditoría.
- **FR-014**: El formato de consola de todos los demás logs MUST mantenerse idéntico al actual.
- **FR-015**: Si falla la construcción o escritura de la línea, la request MUST completarse con la misma respuesta (o la misma excepción original) que tendría sin auditoría; el fallo se registra a lo sumo como aviso sin datos sensibles.
- **FR-016**: La auditoría MUST NOT alterar el valor de retorno, la excepción, el código HTTP ni el cuerpo de respuesta de ningún endpoint.
- **FR-017**: Los endpoints de actuator MUST NOT auditarse por este mecanismo.
- **FR-018**: Esta auditoría MUST NOT persistirse en base de datos y MUST NOT modificar `TransactionAuditService` ni su auditoría en base.
- **FR-019**: Ningún controller MUST modificarse; las únicas modificaciones a archivos existentes autorizadas son agregar la dependencia de AspectJ y crear la configuración de logging (incluido trasladar a ella el patrón JSON de consola si hace falta, sin cambiar su resultado).

### Key Entities

- **Registro de auditoría de web service**: una línea por invocación. Atributos: timestamp, usuario, método HTTP, ruta, `Clase.método`, parámetros saneados (enmascarados, truncados, sin objetos técnicos), duración en ms, resultado (`OK` o tipo de excepción), `correlationId`. No se persiste; vive sólo en la salida de logs.
- **Política de saneamiento de parámetros**: reglas que deciden qué argumentos se omiten, qué nombres se enmascaran y el límite de longitud por parámetro.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las invocaciones a endpoints de los seis controllers existentes, exitosas o con error, producen exactamente una línea de auditoría (verificado en pruebas con al menos un endpoint por controller y al menos un caso de error).
- **SC-002**: En pruebas de login, registro y renovación de sesión con credenciales conocidas, el 0% de las líneas de log contiene el valor literal de una contraseña o un token.
- **SC-003**: Ninguna línea de auditoría tiene un valor de parámetro de más de 200 caracteres más la marca de truncado.
- **SC-004**: El 100% de las líneas de auditoría de una request comparten el `correlationId` devuelto al cliente en esa request.
- **SC-005**: Con un fallo forzado al armar la línea, el 100% de las requests de prueba devuelven la misma respuesta que sin auditoría.
- **SC-006**: Cero controllers modificados y cero cambios en el formato de los logs de consola no relacionados con auditoría.
- **SC-007**: Un operador puede desactivar la auditoría o cambiar su nivel modificando una sola propiedad, sin recompilar.

## Assumptions

- La línea de auditoría se emite con nivel INFO; el nivel por defecto del logger de auditoría es INFO, configurable por propiedad.
- Rotación de `logs/audit.log`: diaria, conservando 7 días.
- El límite de truncado es 200 caracteres por parámetro; es un valor por defecto razonable, no exigido por el enunciado.
- Un interruptor `audit.web-services.enabled` (verdadero por defecto) permite apagar el aspecto completo; el nivel del logger de auditoría sigue siendo la forma estándar de silenciar las líneas.
- "Toda invocación a un método" se entiende literalmente: si la request no llega a invocar el método (rechazo de la cadena de seguridad, `@Valid @RequestBody` inválido, fallo de conversión de tipos, ruta inexistente), no hay línea de auditoría de web service. En cambio, las restricciones sobre `@RequestParam`/`@PathVariable` de controllers `@Validated` se chequean en la propia invocación (proxy de validación de métodos, por dentro del aspecto) y sí generan línea con `outcome=ConstraintViolationException`.
- La "ruta" es la URI de la request invocada (sin query string, ya que los parámetros se informan aparte), no la plantilla del mapeo.
- El `message` de la línea en consola contiene los campos de auditoría en un formato clave=valor legible y estable; el timestamp, el nivel, el logger y el `correlationId` los aporta el formato JSON existente. Si el formato elegido lo requiere, el timestamp también puede incluirse en el mensaje.
- Las requests rechazadas por la cadena de seguridad antes de llegar al controller (401/403 del filtro) no se auditan aquí; ya quedan registradas por los mecanismos existentes.
- El manejo de errores existente (`GlobalExceptionHandler`) no se considera un endpoint y no se audita por separado.
- La configuración de logging nueva usa los mismos destinos de consola actuales; no se agregan otros destinos que los pedidos.
