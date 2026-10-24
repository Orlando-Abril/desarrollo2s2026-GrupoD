# Feature Specification: Recálculo de cotizaciones

**Feature Branch**: `feature/recalculo-cotizaciones`

**Created**: 2026-10-23

**Status**: Draft

**Input**: User description: "Corrida de recálculo de cotizaciones y endpoint POST /quotes/recalculate para dispararla a mano."

## Contexto y restricciones

Las features anteriores dejaron todas las piezas del motor de valuación, pero nada las une:

- **013** definió el historial inmutable de cotizaciones (`PlayerQuote`: jugador, código y versión de estrategia, valor obligatorio, score opcional, estado `CALCULATED` / `NO_STATS` / `INSUFFICIENT_HISTORY`, instante de cálculo, id de corrida y correlation id) y el catálogo de estrategias (OFFICIAL v1 y MOMENTUM v1, con una estrategia activa configurable, OFFICIAL por defecto).
- **014** definió la captura de snapshots de estadísticas de todos los jugadores con un id de corrida.
- **015** definió el contrato del algoritmo de valuación y el algoritmo de OFFICIAL. Un resultado distinto de `CALCULATED` no trae valor ni score: quien llama decide qué valor registrar. MOMENTUM todavía no tiene algoritmo (feature 019).

Esta feature define la **corrida de recálculo**: el proceso que, sin consultar fuentes externas, toma un snapshot, valúa a cada jugador con cada estrategia, registra las cotizaciones en el historial y actualiza el precio de mercado del jugador con la estrategia activa. También expone `POST /quotes/recalculate` para que un administrador dispare la corrida a mano.

**Archivos protegidos de Entrega 1**: `SecurityConfig`, `GlobalExceptionHandler`, `PlayerController`, `Player`, los filtros de seguridad y `PlayerRepository` no se modifican sin consultar al equipo. El equipo autorizó explícitamente sólo estos cambios:

1. Escribir `Player.marketValue` y cambiar su columna a 4 decimales (precisión 12, escala 4) y su javadoc.
2. Que el filtro JWT cargue el rol del usuario autenticado.
3. Que el filtro de API key asigne a la API key el rol de su usuario dueño (ADMIN si la key no tiene dueño).
4. Que la configuración de seguridad exija el rol ADMIN para `POST /quotes/recalculate`.

Cualquier otro cambio en esos archivos que resulte imprescindible durante la planificación o la implementación MUST detener el trabajo y consultarse, indicando archivo, cambio mínimo y alternativa.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Recalcular las cotizaciones de todos los jugadores (Priority: P1)

Como administrador del mercado, quiero disparar una corrida que valúe a todos los jugadores con las estrategias disponibles y registre el resultado en el historial, para que las cotizaciones reflejen las estadísticas que ya tiene el sistema.

**Why this priority**: Es el núcleo de la feature. Sin la corrida no existe ninguna cotización en el historial y el resto (precio de mercado, endpoint, resumen) no tiene sobre qué trabajar.

**Independent Test**: Preparar jugadores con estadísticas, jugadores sin estadísticas y jugadores con y sin cotizaciones previas; ejecutar una corrida para todas las estrategias y verificar las cotizaciones registradas, sus estados, valores, versión de estrategia, id de corrida, correlation id e instante de cálculo.

**Acceptance Scenarios**:

1. **Given** jugadores con estadísticas suficientes, **When** se ejecuta una corrida para OFFICIAL, **Then** cada uno queda con exactamente una cotización OFFICIAL nueva en estado `CALCULATED`, con el valor y el score que devolvió el algoritmo, la versión de OFFICIAL, el id de la corrida, el correlation id del pedido y el instante de cálculo de la corrida.
2. **Given** un jugador sin estadísticas y con una cotización OFFICIAL previa de 1.0450, **When** se ejecuta la corrida, **Then** se registra una cotización OFFICIAL `NO_STATS` sin score y con valor 1.0450.
3. **Given** un jugador sin estadísticas y sin cotizaciones OFFICIAL previas, **When** se ejecuta la corrida, **Then** se registra una cotización OFFICIAL `NO_STATS` sin score y con el valor base de la estrategia (1.0000).
4. **Given** un jugador cuya última cotización de MOMENTUM es más reciente que su última cotización de OFFICIAL, **When** se calcula OFFICIAL, **Then** el algoritmo recibe sólo las cotizaciones OFFICIAL del jugador, de la más nueva a la más vieja, y un estado no calculado conserva el último valor OFFICIAL, nunca el de MOMENTUM.
5. **Given** una corrida para todas las estrategias, **When** MOMENTUM no tiene algoritmo disponible, **Then** OFFICIAL se calcula normalmente, no se registra ninguna cotización MOMENTUM y el resumen informa MOMENTUM como salteada con su motivo.
6. **Given** cualquier corrida terminada, **When** se revisan las cotizaciones que registró, **Then** todas comparten el mismo id de corrida y el mismo instante de cálculo, y ese id no coincide con el de ninguna corrida anterior.
7. **Given** una corrida, **When** comienza, **Then** primero se toma un snapshot de estadísticas con el id de esa corrida y recién después se calculan las cotizaciones.
8. **Given** que el snapshot de estadísticas falla, **When** se ejecuta la corrida, **Then** la corrida termina con error, no se registra ninguna cotización y ningún precio de mercado cambia.
9. **Given** una corrida en curso, **When** se observa el tráfico saliente, **Then** no hay ninguna consulta a WhoScored, football-data ni otra fuente externa.

---

### User Story 2 - Precio de mercado según la estrategia activa (Priority: P1)

Como usuario que opera tokens, quiero que el precio de mercado de cada jugador sea la cotización más reciente de la estrategia activa, con la misma precisión que el historial, para operar al precio que el sistema calculó.

**Why this priority**: El precio de mercado es lo que consumen el catálogo y la futura operatoria de compra y venta. Con 2 decimales y OFFICIAL acotada a [1.00, 1.10], sólo existirían 11 precios posibles y la valuación perdería sentido.

**Independent Test**: Ejecutar corridas con distintas estrategias activas y distintos pedidos, y comparar el precio de mercado de cada jugador con su cotización de la estrategia activa en esa corrida.

**Acceptance Scenarios**:

1. **Given** OFFICIAL como estrategia activa, **When** termina una corrida que calculó OFFICIAL, **Then** el precio de mercado de cada jugador procesado es igual, con 4 decimales, al valor de su cotización OFFICIAL de esa corrida (incluidos los estados `NO_STATS` e `INSUFFICIENT_HISTORY`).
2. **Given** OFFICIAL como estrategia activa, **When** se pide una corrida sólo de MOMENTUM (o MOMENTUM se saltea), **Then** ningún precio de mercado cambia y el resumen indica que no se actualizó.
3. **Given** MOMENTUM como estrategia activa y sin algoritmo disponible, **When** se ejecuta una corrida para todas las estrategias, **Then** se registran las cotizaciones OFFICIAL, ningún precio de mercado cambia y el resumen indica que no se actualizó.
4. **Given** una cotización con valor 1.0437, **When** se actualiza el precio de mercado, **Then** el precio de mercado guardado y leído es 1.0437, sin redondeo a 2 decimales.
5. **Given** precios de mercado existentes con 2 decimales, **When** se aplica el cambio de precisión, **Then** conservan su valor (por ejemplo, 1.05 pasa a leerse 1.0500).

---

### User Story 3 - Disparar el recálculo a mano por API (Priority: P1)

Como administrador, quiero un endpoint que dispare la corrida para todas las estrategias o para una sola y me devuelva un resumen, para recalcular bajo demanda sin esperar al proceso programado.

**Why this priority**: Es el contrato obligatorio `POST /quotes/recalculate` de la constitución y la única forma de disparar la corrida hasta que exista el recálculo programado (017).

**Independent Test**: Llamar al endpoint autenticado como ADMIN con y sin cuerpo, con una estrategia válida, con una inexistente y mientras otra corrida está en curso; verificar el código de respuesta y el contenido del resumen.

**Acceptance Scenarios**:

1. **Given** un administrador autenticado, **When** llama a `POST /quotes/recalculate` sin cuerpo, **Then** recibe 200 con un resumen que incluye: id de corrida, instante de cálculo, estrategias calculadas, estrategias salteadas con su motivo, cantidad de jugadores procesados y fallidos, por cada estrategia calculada la cantidad de `CALCULATED`, `NO_STATS` e `INSUFFICIENT_HISTORY`, y si se actualizó el precio de mercado.
2. **Given** un administrador autenticado, **When** envía `{"strategy": "OFFICIAL"}`, **Then** sólo se calcula OFFICIAL y el resumen no lista otras estrategias como calculadas ni como salteadas.
3. **Given** un administrador autenticado, **When** envía `{}` o `{"strategy": null}`, **Then** se comporta igual que sin cuerpo.
4. **Given** un administrador autenticado, **When** envía `{"strategy": "INEXISTENTE"}`, **Then** recibe 400 con código `unknown_strategy`, no se toma snapshot y no se registra ninguna cotización.
5. **Given** una corrida en curso, **When** otro pedido intenta iniciar una corrida, **Then** recibe 409 con código `recalculation_in_progress` y la corrida en curso no se ve afectada.
6. **Given** que la corrida anterior terminó (bien o con error), **When** llega un nuevo pedido, **Then** la nueva corrida se acepta.
7. **Given** un administrador autenticado, **When** pide sólo MOMENTUM y no tiene algoritmo, **Then** recibe 200, MOMENTUM figura como salteada, no hay estrategias calculadas, no se registra ninguna cotización y el precio de mercado no se actualiza.
8. **Given** que el snapshot de estadísticas falla, **When** se llama al endpoint, **Then** recibe una respuesta de error del servidor con el formato de error del sistema y sin resumen de corrida.

---

### User Story 4 - Sólo administradores pueden recalcular (Priority: P1)

Como responsable del sistema, quiero que sólo un administrador o un cliente de servicio autorizado pueda disparar el recálculo, para que un usuario común no pueda alterar los precios de mercado.

**Why this priority**: El recálculo reescribe el precio de todos los jugadores. Exponerlo a cualquier usuario autenticado sería un riesgo de manipulación del mercado.

**Independent Test**: Llamar al endpoint sin credenciales, con credenciales inválidas, con un usuario USER, con un usuario ADMIN y con la API key de un ADMIN; y verificar que los demás endpoints protegidos siguen respondiendo igual que antes para usuarios USER.

**Acceptance Scenarios**:

1. **Given** un pedido sin credenciales, **When** llama al endpoint, **Then** recibe 401 y no se inicia ninguna corrida.
2. **Given** un token inválido o vencido, o una API key inválida o inactiva, **When** llama al endpoint, **Then** recibe 401 y no se inicia ninguna corrida.
3. **Given** un usuario autenticado por token con rol USER, **When** llama al endpoint, **Then** recibe 403 con código `forbidden` y no se inicia ninguna corrida.
4. **Given** un usuario autenticado por token con rol ADMIN, **When** llama al endpoint, **Then** la corrida se ejecuta.
5. **Given** una API key válida y activa de un usuario ADMIN o sin dueño, **When** llama al endpoint, **Then** la corrida se ejecuta.
6. **Given** la API key válida de un usuario USER, **When** llama al endpoint, **Then** recibe 403 forbidden y no se inicia ninguna corrida.
7. **Given** un usuario con rol USER, **When** llama a cualquier otro endpoint protegido existente (por ejemplo, el catálogo de jugadores), **Then** obtiene la misma respuesta que antes de esta feature.

---

### User Story 5 - Una falla en un jugador no arruina la corrida (Priority: P2)

Como administrador, quiero que un error al valuar o guardar a un jugador no detenga la corrida ni deje datos a medias, para que el resto del mercado se actualice igual y yo sepa cuántos jugadores fallaron.

**Why this priority**: Sin aislamiento por jugador, un solo dato inconsistente dejaría a todo el mercado sin actualizar; sin atomicidad por jugador, el historial y el precio podrían quedar desalineados.

**Independent Test**: Provocar una falla al valuar o al guardar a un jugador concreto en una corrida con varias estrategias y verificar que ese jugador no tiene ninguna cotización nueva ni cambio de precio, que el resto sí, y que el resumen y los logs lo informan.

**Acceptance Scenarios**:

1. **Given** una corrida con OFFICIAL y otra estrategia calculable, **When** el cálculo de la segunda estrategia falla para un jugador, **Then** ese jugador no queda con ninguna cotización de esa corrida (tampoco la de OFFICIAL) y su precio de mercado no cambia.
2. **Given** una falla al guardar el precio de mercado de un jugador, **When** termina la corrida, **Then** ese jugador tampoco queda con cotizaciones de esa corrida.
3. **Given** cinco jugadores donde uno falla, **When** termina la corrida, **Then** los otros cuatro quedan con sus cotizaciones y su precio actualizados, el resumen informa 4 procesados y 1 fallido, y los conteos por estado sólo cuentan a los procesados.
4. **Given** un jugador fallido, **When** se revisan los logs, **Then** hay un log estructurado de error con id de corrida, correlation id, id del jugador y tipo de error.
5. **Given** cualquier corrida, **When** empieza y cuando termina, **Then** hay un log estructurado de inicio (con id de corrida, correlation id y estrategias pedidas) y uno de fin (con id de corrida, correlation id, resultado y conteos); si termina con error, el log de fin informa el tipo de error.

---

### User Story 6 - Endpoint documentado en OpenAPI (Priority: P3)

Como desarrollador del frontend o integrador, quiero ver el endpoint documentado en Swagger UI con su cuerpo, respuestas y esquemas de seguridad, para poder probarlo y consumirlo sin leer el código.

**Why this priority**: Es parte de la definición de terminado de la constitución, pero no cambia el comportamiento de la corrida.

**Independent Test**: Abrir la especificación OpenAPI y verificar que el endpoint declara cuerpo opcional, las respuestas 200, 400, 401, 403 y 409 con sus esquemas, y los esquemas de seguridad bearer y API key; luego ejecutarlo desde Swagger UI con cada credencial.

**Acceptance Scenarios**:

1. **Given** la especificación OpenAPI publicada, **When** se busca `POST /quotes/recalculate`, **Then** declara cuerpo opcional con `strategy`, respuesta 200 con el esquema del resumen, y respuestas 400 `unknown_strategy`, 401, 403 `forbidden` y 409 `recalculation_in_progress` con el formato de error del sistema.
2. **Given** Swagger UI, **When** se autoriza con token de ADMIN o con la API key de un ADMIN, **Then** el endpoint puede ejecutarse desde la interfaz.

### Edge Cases

- **Catálogo sin jugadores**: la corrida toma el snapshot, no registra cotizaciones, informa 0 procesados y 0 fallidos y responde 200. El precio de mercado se informa como actualizado si la estrategia activa se calculó (no hubo jugadores que actualizar), y como no actualizado si no se calculó.
- **Ninguna estrategia calculable** (por ejemplo, sólo MOMENTUM pedida): igual se toma el snapshot (la captura es independiente de las cotizaciones y no duplica datos), no se registra ninguna cotización, todos los jugadores cuentan como procesados con conteos vacíos y el precio de mercado no cambia.
- **Todos los jugadores fallan**: la corrida igual responde 200 con 0 procesados y N fallidos; el precio de mercado se informa como no actualizado si ningún jugador pudo actualizarse.
- **Estado `INSUFFICIENT_HISTORY`**: se trata igual que `NO_STATS`: se registra con ese estado, sin score, con el valor de la última cotización de esa estrategia o el valor base.
- **Última cotización previa con valor fuera del rango actual de la estrategia**: se conserva tal cual; la corrida no corrige ni reinterpreta valores históricos.
- **Varias cotizaciones previas con el mismo instante de cálculo**: el orden de la más nueva a la más vieja se desempata de forma determinística por orden de registro, de modo que "la última cotización" sea siempre la misma.
- **Cotizaciones previas de una versión anterior de la misma estrategia**: cuentan como cotizaciones de esa estrategia (se agrupan por código, no por versión).
- **Jugador agregado al catálogo durante la corrida**: no forma parte de esa corrida; la lista de jugadores y la población se fijan al comenzar.
- **Estadísticas actualizadas por una sincronización durante la corrida**: no cambian la población ya armada; los resultados de la corrida son coherentes con el estado al comenzar.
- **Código de estrategia con espacios o en otra capitalización** (por ejemplo, `" official "`): se rechaza con 400 `unknown_strategy`; el código debe coincidir exactamente con el del catálogo.
- **`strategy` vacío (`""`)**: se rechaza con 400 `unknown_strategy`, a diferencia de ausente o `null`, que significan todas.
- **Cuerpo con JSON inválido o `strategy` no textual**: se rechaza con 400 usando el manejo de pedido inválido existente, sin iniciar la corrida.
- **Pedido sin correlation id en el header**: la corrida usa el correlation id que el sistema genera para ese pedido, y es el mismo que se devuelve en la respuesta y se registra en cotizaciones y logs.
- **La corrida falla por un error general fuera de un jugador** (por ejemplo, al armar la población): termina con error, se libera el bloqueo de corrida en curso, y las cotizaciones de jugadores ya confirmados permanecen (cada jugador es atómico, la corrida no).

## Requirements *(mandatory)*

### Functional Requirements

**Corrida**

- **FR-001**: Cada corrida MUST tener un id de corrida único y un único instante de cálculo, fijados al comenzar y compartidos por todas las cotizaciones que registre.
- **FR-002**: Antes de calcular, la corrida MUST tomar un snapshot de estadísticas con su id de corrida. Si el snapshot falla, la corrida MUST terminar con error sin registrar cotizaciones ni cambiar precios de mercado.
- **FR-003**: La corrida MUST valuar a cada jugador del catálogo existente al comenzar, con cada estrategia del catálogo o sólo con la estrategia pedida.
- **FR-004**: Una estrategia del catálogo sin algoritmo disponible MUST saltearse: no registra cotizaciones y figura en el resumen con el motivo `no_algorithm`. Las demás estrategias se calculan normalmente.
- **FR-005**: Por cada jugador procesado y cada estrategia calculada, la corrida MUST registrar exactamente una cotización nueva con: jugador, código y versión de la estrategia, valor, score (sólo en `CALCULATED`), estado, instante de cálculo de la corrida, id de corrida y correlation id del pedido.
- **FR-006**: La población que usa cada estrategia MUST armarse una sola vez por corrida, antes de calcular a cualquier jugador, a partir de las estadísticas persistidas al comenzar.
- **FR-007**: Las cotizaciones previas que recibe el algoritmo para un jugador y una estrategia MUST ser sólo las de ese código de estrategia (de cualquier versión), ordenadas de la más nueva a la más vieja por instante de cálculo y, ante empate, por orden de registro descendente. MUST NOT incluir cotizaciones de la corrida en curso. En esta feature el algoritmo recibe la última cotización de esa estrategia (o ninguna), que es lo que usan OFFICIAL y FR-009. La feature 019 amplía la carga a las últimas N cotizaciones y a las ventanas de estadísticas cuando MOMENTUM las necesite.
- **FR-008**: Si el algoritmo devuelve `CALCULATED`, la cotización MUST registrarse con el valor y el score devueltos.
- **FR-009**: Si el algoritmo devuelve `NO_STATS` o `INSUFFICIENT_HISTORY`, la cotización MUST registrarse con ese estado, sin score, y con el valor de la última cotización previa del jugador para esa estrategia o, si no tiene, con el valor base de la estrategia (1.0000).
- **FR-010**: La corrida MUST usar sólo datos ya persistidos y MUST NOT consultar a WhoScored, football-data ni ninguna otra fuente externa.
- **FR-011**: La corrida MUST NOT modificar ni borrar cotizaciones existentes; sólo agrega nuevas.

**Precio de mercado**

- **FR-012**: El precio de mercado de un jugador MUST almacenarse con 4 decimales (precisión total 12), igual que el valor de las cotizaciones; los valores existentes MUST conservarse sin pérdida al cambiar la precisión.
- **FR-013**: Si la estrategia activa se calculó en la corrida, el precio de mercado de cada jugador procesado MUST quedar igual al valor de su cotización de la estrategia activa en esa corrida, cualquiera sea su estado.
- **FR-014**: Si la estrategia activa no se calculó en la corrida (no se pidió o se salteó), ningún precio de mercado MUST cambiar y el resumen MUST indicar que no se actualizó.

**Atomicidad y fallas por jugador**

- **FR-015**: Todas las cotizaciones de un jugador en la corrida y la actualización de su precio de mercado MUST confirmarse juntas: o se guardan todas, o ninguna.
- **FR-016**: Una falla al valuar o guardar a un jugador MUST NOT abortar la corrida: ese jugador queda sin cotizaciones de la corrida y sin cambio de precio, cuenta como fallido, y la corrida sigue con el resto.
- **FR-017**: El resumen MUST informar la cantidad de jugadores procesados (confirmados) y fallidos; procesados + fallidos MUST igualar la cantidad de jugadores considerados al comenzar.
- **FR-018**: El precio de mercado MUST informarse como actualizado sólo si la estrategia activa se calculó y no todos los jugadores considerados fallaron (o no había jugadores).

**Concurrencia**

- **FR-019**: Como máximo una corrida MUST ejecutarse a la vez. Un pedido que llega mientras hay una corrida en curso MUST rechazarse sin esperar y sin afectar a la corrida en curso.
- **FR-020**: Al terminar una corrida, con éxito o con error, el sistema MUST aceptar una nueva.

**Endpoint**

- **FR-021**: El sistema MUST exponer `POST /quotes/recalculate` con un cuerpo opcional `{"strategy": "<código>"}`. Sin cuerpo, con cuerpo vacío o con `strategy` ausente o `null`, MUST calcular todas las estrategias del catálogo.
- **FR-022**: Si `strategy` no coincide exactamente con un código del catálogo, el endpoint MUST responder 400 con código `unknown_strategy` y la lista de códigos válidos en el mensaje, sin iniciar la corrida.
- **FR-023**: Si hay una corrida en curso, el endpoint MUST responder 409 con código `recalculation_in_progress`.
- **FR-024**: Una corrida iniciada desde el endpoint MUST ejecutarse dentro del mismo pedido y responder 200 al terminar, con: id de corrida, instante de cálculo, estrategias calculadas (código y versión), estrategias salteadas con su motivo, jugadores procesados y fallidos, por cada estrategia calculada la cantidad de cotizaciones `CALCULATED`, `NO_STATS` e `INSUFFICIENT_HISTORY`, y si se actualizó el precio de mercado (indicando la estrategia activa).
- **FR-025**: Si la corrida termina con error general (por ejemplo, falla del snapshot), el endpoint MUST responder con el error del servidor en el formato de error del sistema, sin exponer detalles internos.
- **FR-026**: Las respuestas de error del endpoint MUST usar el formato de error existente del sistema (código de error y mensaje).

**Seguridad**

- **FR-027**: Un pedido sin credenciales o con credenciales inválidas MUST recibir 401, igual que hoy en los demás endpoints protegidos.
- **FR-028**: El endpoint MUST aceptar sólo usuarios autenticados por token con rol ADMIN o pedidos autenticados con una API key autorizada como ADMIN según FR-029. Un usuario autenticado sin ese rol MUST recibir 403 con código `forbidden`.
- **FR-029**: El rol del usuario autenticado por token MUST tomarse de su usuario registrado. Si el usuario del token no existe, MUST autenticarse sin rol (los demás endpoints siguen respondiendo igual). Una API key válida y activa MUST autenticarse con el rol de su usuario dueño; una key sin dueño MUST autenticarse como ADMIN.
- **FR-030**: Todos los demás endpoints MUST conservar exactamente su comportamiento actual de autenticación y autorización.

**Observabilidad y documentación**

- **FR-031**: La corrida MUST emitir un log estructurado de inicio (id de corrida, correlation id, estrategias pedidas) y uno de fin (id de corrida, correlation id, resultado, estrategias calculadas y salteadas, procesados, fallidos, conteos por estado, si se actualizó el precio de mercado y duración); si termina con error, el log de fin MUST informar el tipo de error.
- **FR-032**: Cada jugador fallido MUST generar un log estructurado de error con id de corrida, correlation id, id del jugador, estrategia (si se conoce) y tipo de error.
- **FR-033**: El correlation id de la corrida MUST ser el del pedido; si el pedido no traía uno, el que el sistema generó para él. El mismo valor MUST registrarse en las cotizaciones y en los logs.
- **FR-034**: El endpoint MUST estar documentado en OpenAPI con su cuerpo opcional, el esquema del resumen, las respuestas 200, 400, 401, 403 y 409, y los esquemas de seguridad bearer (JWT) y API key.

**Alcance y archivos protegidos**

- **FR-035**: Esta feature MUST limitar los cambios en archivos protegidos de Entrega 1 a los cuatro autorizados en "Contexto y restricciones"; cualquier otro cambio en ellos requiere consulta previa.
- **FR-036**: La corrida MUST poder invocarse desde otro proceso interno (sin pasar por el endpoint) con las mismas reglas de exclusividad, para que la feature 017 la reutilice.

### Key Entities

- **Corrida de recálculo**: Ejecución única del proceso, identificada por un id de corrida y un instante de cálculo. Agrupa un snapshot de estadísticas y todas las cotizaciones que registra. No se persiste como entidad propia: se reconstruye desde las cotizaciones y snapshots con su id y desde los logs.
- **Cotización de jugador (PlayerQuote)**: Entidad existente (013). Una por jugador procesado y estrategia calculada en cada corrida.
- **Estrategia de valuación**: Entrada existente del catálogo (013) con código, versión, valor base y tipo. Puede tener o no algoritmo disponible; una de ellas es la activa.
- **Jugador (Player)**: Entidad existente. Su precio de mercado pasa a tener 4 decimales y queda igual a la última cotización de la estrategia activa.
- **Resumen de corrida**: Resultado devuelto por el endpoint con id, instante, estrategias calculadas y salteadas, procesados, fallidos, conteos por estado y actualización del precio de mercado.
- **Pedido de recálculo**: Cuerpo opcional del endpoint con el código de estrategia a calcular.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Tras una corrida sin fallas para todas las estrategias calculables, el 100% de los jugadores del catálogo tiene exactamente una cotización nueva por estrategia calculada, todas con el mismo id de corrida e instante de cálculo.
- **SC-002**: El 100% de las cotizaciones `NO_STATS` e `INSUFFICIENT_HISTORY` registradas tiene el valor de la última cotización previa de la misma estrategia o, si no había, 1.0000; ninguna tiene score.
- **SC-003**: Tras una corrida que calculó la estrategia activa, el 100% de los jugadores procesados tiene precio de mercado igual, con 4 decimales, a su cotización de la estrategia activa en esa corrida; tras una corrida que no la calculó, el 0% de los precios cambia.
- **SC-004**: En una corrida con fallas provocadas en jugadores concretos, el 100% de los jugadores fallidos queda con 0 cotizaciones de la corrida y su precio sin cambios, y el 100% de los demás queda completo.
- **SC-005**: De dos pedidos simultáneos, exactamente uno ejecuta la corrida y el otro recibe 409; nunca hay dos corridas en simultáneo.
- **SC-006**: El 100% de los pedidos sin credenciales recibe 401 y el 100% de los usuarios USER recibe 403 en el endpoint, sin iniciar corrida; los demás endpoints protegidos responden igual que antes para todos los perfiles.
- **SC-007**: Una corrida realiza 0 consultas a fuentes externas.
- **SC-008**: Una corrida manual sobre el catálogo completo de las 5 grandes ligas termina y responde en menos de 1 minuto en el entorno de desarrollo.
- **SC-009**: El 100% de las corridas emite exactamente un log de inicio y uno de fin trazables por id de corrida y correlation id, y cada jugador fallido emite su log de error.
- **SC-010**: El endpoint puede ejecutarse desde Swagger UI con token de ADMIN y con la API key de un ADMIN, y su documentación lista las cinco respuestas (200, 400, 401, 403, 409).

## Assumptions

- `specs/016-recalculo-cotizaciones` es el directorio pedido y es independiente del nombre de la rama Git.
- Las API keys sin dueño (de servicio) no las emite ningún endpoint: se crean a mano en la base. Hoy `AuthService.register` siempre asigna un dueño.
- La rama de trabajo debe incorporar lo último de `main` (merge de la feature 015) antes de planificar, porque el contrato del algoritmo y OFFICIAL viven ahí.
- "Algoritmo disponible" significa que existe una implementación registrada para el tipo de la estrategia. Hoy sólo OFFICIAL la tiene; cuando la feature 019 agregue MOMENTUM, se calculará sin cambiar esta feature.
- El valor base de las estrategias es 1.00 (1.0000 con 4 decimales), tomado del catálogo de cada estrategia y no fijo en la corrida.
- El código de estrategia del pedido se compara exactamente, como lo hace el catálogo existente.
- La exclusión de corridas simultáneas es dentro de una sola instancia de la aplicación; el despliegue de la materia es de una instancia.
- La corrida es sincrónica: el endpoint responde cuando termina. El volumen (jugadores de 5 ligas) lo permite.
- La corrida no se persiste como tabla propia; el id de corrida en cotizaciones y snapshots y los logs estructurados dan la trazabilidad. Esto no es una transacción financiera, por lo que no requiere el registro de auditoría de la sección 4.2 de la constitución.
- El cambio de precisión del precio de mercado lo aplica el mecanismo actual de esquema de la base de datos; si no lo aplica sobre una tabla existente, la planificación definirá cómo migrarlo.
- La respuesta 403 usa el formato de error existente (`error`, `message`) con código `forbidden`; agregar esa respuesta sólo puede hacerse dentro de los cambios autorizados en la configuración de seguridad o fuera de los archivos protegidos.
- El rol de un usuario se lee de su registro en el momento del pedido (no se agrega al token), así un cambio de rol aplica sin volver a iniciar sesión.

## Out of Scope

- Recálculo programado semanal (feature 017).
- Endpoints de consulta de cotizaciones, historial o ranking (feature 018).
- Algoritmo de MOMENTUM (feature 019).
- Gestión de roles: crear administradores, asignar o quitar roles por API.
- Corridas asíncronas, cancelación o consulta del estado de una corrida en curso.
- Recalcular sólo algunos jugadores o una liga.
- Cambios en la sincronización de estadísticas o del catálogo, y cualquier consulta a fuentes externas.
- Cambios en archivos protegidos de Entrega 1 fuera de los cuatro autorizados.
