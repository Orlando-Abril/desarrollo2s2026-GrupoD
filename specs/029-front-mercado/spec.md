# Feature Specification: Operar en el mercado desde el frontend

**Feature Branch**: `feature/front-mercado`

**Created**: 2026-10-29

**Status**: Draft

**Input**: User description: "Operar en el mercado desde el frontend: comprar y vender tokens, ver el libro de órdenes y cancelar órdenes abiertas. Desde el detalle del jugador (024), un panel para comprar o vender (MARKET o LIMIT, cantidad, precio en LIMIT, total estimado); validación antes de enviar (cantidad entera, precio dentro de ±20% de la cotización) y errores del backend en lenguaje claro; confirmación antes de enviar y Toast con lo ejecutado y lo que quedó en el libro; libro de órdenes del jugador que se actualiza después de operar; pantalla de Mercado (ítem '03 Mercado') con mis órdenes abiertas y el botón para cancelarlas; responsive y accesible, con la estética actual. Autorizado (Entrega 1): agregar la ruta en App.jsx y habilitar el ítem Mercado en NAV_ITEMS de AppLayout.jsx."

## Contexto y restricciones

**Lo que ya existe en main**:

- Frontend: la ficha del jugador en `/jugadores/:id` (024) con cabecera tipo figurita, cotización actual, gráfico y estadísticas. El menú (`NAV_ITEMS` en `layout/AppLayout.jsx`) tiene "03 Mercado" deshabilitado. Ya existen un componente `Toast` y los componentes base de la estética actual (botones, campos, paneles de estado).
- Backend (027): `POST /orders/buy`, `POST /orders/sell`, `DELETE /orders/{id}` y `GET /orders/book?playerId=`, con el contrato de `specs/027-motor-calce/contracts/orders-api.md`. La respuesta de una orden trae estado, cantidad ejecutada y pendiente, precio promedio y las operaciones ejecutadas. Los errores usan `{"error": "...", "message": "..."}` con códigos distinguibles (`validation_error`, `price_out_of_band`, `insufficient_balance`, `insufficient_tokens`, `player_not_quoted`, `player_not_found`, `superuser_not_allowed`, `user_required`, `order_not_found`, `order_forbidden`, `order_closed`).
- No existe un endpoint para listar las órdenes del usuario: `GET /orders/book` es agregado por precio y no identifica dueños.

**Decisión del equipo (2026-10-29)**: para la pantalla de Mercado, esta feature agrega un único endpoint de backend, `GET /orders/mine`, en el `OrderController` existente de la 027, con el alcance acotado de FR-020 a FR-025 y nada más.

**Alcance de esta feature**: el panel de compra/venta en la ficha del jugador, el libro de órdenes del jugador, la pantalla de Mercado con mis órdenes activas y su cancelación, la ruta de esa pantalla y la habilitación del ítem "03 Mercado", y el endpoint `GET /orders/mine`. **Fuera de alcance**: portfolio, historial de transacciones, saldo del usuario en pantalla, cambios al motor de calce o a los otros endpoints de la 027, y el ítem "04 Mi portfolio".

**Archivos protegidos de Entrega 1**: no se modifican. **Excepción autorizada**: en `App.jsx`, sólo agregar la ruta protegida de la pantalla de Mercado; en `layout/AppLayout.jsx`, sólo habilitar el ítem `mercado` de `NAV_ITEMS` con su ruta. Si durante la planificación o la implementación resulta imprescindible otro cambio en archivos protegidos, se detiene el trabajo y se consulta al equipo indicando archivo, cambio mínimo y alternativa.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Comprar tokens desde la ficha del jugador (Priority: P1)

Un usuario autenticado está en la ficha de un jugador. En un panel de operación elige "Comprar", el tipo de orden (MARKET o LIMIT), la cantidad y, si es LIMIT, el precio. Ve el total estimado antes de enviar, confirma la operación y recibe un aviso con lo que se ejecutó y lo que quedó en el libro.

**Why this priority**: Es la operación principal del mercado y la puerta de entrada para que el usuario tenga tokens; la consola de trading es una vista obligatoria de la constitución (§2.2).

**Independent Test**: Con un jugador cuyo libro tiene la venta del superusuario, comprar con MARKET y con LIMIT desde la ficha y verificar el total estimado, la confirmación, el aviso final y que el libro se actualiza.

**Acceptance Scenarios**:

1. **Given** la ficha de un jugador con cotización 1.2000, **When** el usuario elige Comprar, MARKET y cantidad 10, **Then** ve un total estimado calculado con los precios de venta del libro que alcanzan para esa cantidad (12.0000 si hay 10 o más a 1.2000), marcado como estimado.
2. **Given** el mismo jugador, **When** elige Comprar, LIMIT, cantidad 10 y precio 1.1000, **Then** el total estimado es 11.0000 (cantidad × precio).
3. **Given** un formulario válido, **When** el usuario pulsa enviar, **Then** se muestra una confirmación con lado, tipo, jugador, cantidad, precio (si es LIMIT) y total estimado, y la orden recién se envía al confirmar; si cancela la confirmación, no se envía nada.
4. **Given** una compra confirmada que se ejecuta entera, **When** termina, **Then** aparece un aviso con la cantidad ejecutada y el precio promedio, y el formulario se limpia.
5. **Given** una compra LIMIT que se ejecuta en parte o no se ejecuta, **When** termina, **Then** el aviso indica lo ejecutado (o que no se ejecutó nada) y la cantidad que quedó en el libro a su precio.
6. **Given** una compra MARKET con resto sin contraparte, **When** termina, **Then** el aviso indica lo ejecutado y que el resto se canceló (no quedó en el libro).
7. **Given** una orden que se está enviando, **When** el usuario intenta enviar de nuevo, **Then** no se puede: el envío está deshabilitado hasta recibir la respuesta.

---

### User Story 2 - Vender tokens desde la ficha del jugador (Priority: P1)

El mismo panel permite elegir "Vender", con MARKET o LIMIT, para que el usuario que tiene tokens cobre créditos cuando otro quiera comprar.

**Why this priority**: Es el otro endpoint obligatorio del mercado; sin él el usuario no puede salir de una posición.

**Independent Test**: Con un usuario que tiene tokens y otro con una compra LIMIT en el libro, vender con MARKET y con LIMIT desde la ficha y verificar total estimado, confirmación, aviso y libro.

**Acceptance Scenarios**:

1. **Given** el libro con compras del jugador, **When** el usuario elige Vender, MARKET y una cantidad, **Then** el total estimado usa los precios de compra del libro de mayor a menor.
2. **Given** el libro sin compras, **When** elige Vender MARKET, **Then** el panel avisa que no hay compradores y que la orden se cancelaría sin ejecutarse; puede enviarla igual y el aviso final lo confirma.
3. **Given** una venta LIMIT confirmada que queda abierta, **When** termina, **Then** el aviso indica que quedó en el libro con su cantidad y precio.

---

### User Story 3 - Validaciones y errores claros (Priority: P1)

Antes de enviar, el panel valida lo que puede validar y, si el mercado rechaza la orden, muestra el motivo en lenguaje claro.

**Why this priority**: Evita órdenes que van a fallar y que el usuario no entienda qué pasó con su dinero o sus tokens.

**Independent Test**: Cargar cantidades y precios inválidos y verificar los mensajes en el formulario; simular cada rechazo del mercado y verificar el mensaje mostrado.

**Acceptance Scenarios**:

1. **Given** una cantidad vacía, 0, negativa o con decimales, **When** el usuario intenta enviar, **Then** el campo muestra "La cantidad debe ser un número entero mayor a 0" y no se abre la confirmación.
2. **Given** una LIMIT sin precio, con precio 0 o negativo o con más de 4 decimales, **When** intenta enviar, **Then** el campo de precio muestra el motivo y no se envía.
3. **Given** un jugador con cotización 1.0000, **When** el precio LIMIT es 0.7999 o 1.2001, **Then** el campo muestra "El precio debe estar entre 0,8000 cr y 1,2000 cr"; con 0.8000 o 1.2000 se acepta. El rango permitido se muestra junto al campo.
4. **Given** el tipo MARKET, **When** se muestra el formulario, **Then** no hay campo de precio.
5. **Given** un rechazo por saldo insuficiente, **When** llega la respuesta, **Then** se muestra "No tenés saldo suficiente para esta compra.".
6. **Given** un rechazo por tokens insuficientes, **When** llega la respuesta, **Then** se muestra "No tenés tokens disponibles suficientes de este jugador (pueden estar reservados en órdenes abiertas).".
7. **Given** un rechazo por precio fuera de banda (por ejemplo, porque la cotización cambió), **When** llega la respuesta, **Then** se muestra "El precio está fuera del rango permitido (±20% de la cotización). Actualizamos la cotización: revisá el precio." y se vuelve a cargar el libro, que trae la cotización de referencia.
8. **Given** un rechazo por jugador sin cotización, jugador inexistente, superusuario o credencial sin usuario, **When** llega la respuesta, **Then** se muestra un mensaje claro específico para cada caso.
9. **Given** un error de red o un error inesperado, **When** llega, **Then** se muestra "No pudimos enviar la orden. Probá de nuevo." y los datos del formulario se conservan.
10. **Given** una sesión vencida (401), **When** llega la respuesta, **Then** se aplica el comportamiento existente de sesión vencida de la aplicación.

---

### User Story 4 - Ver el libro de órdenes del jugador (Priority: P1)

En la ficha del jugador, el usuario ve el libro: los precios de venta y de compra con la cantidad de tokens en cada precio, junto con la cotización vigente, y se actualiza después de operar.

**Why this priority**: Sin el libro el usuario opera a ciegas y el total estimado de una MARKET no puede calcularse.

**Independent Test**: Con un libro con varias órdenes por precio, abrir la ficha y verificar los niveles; operar y verificar que el libro refleja el resultado.

**Acceptance Scenarios**:

1. **Given** un libro con ventas `[1.1000 → 7, 1.2000 → 50]` y compras `[1.0500 → 5]`, **When** se abre la ficha, **Then** se ven las ventas de menor a mayor precio y las compras de mayor a menor, cada una con precio y cantidad, diferenciadas visual y textualmente (no sólo por color).
2. **Given** un lado sin órdenes, **When** se muestra, **Then** aparece "Sin órdenes de venta" o "Sin órdenes de compra" en ese lado.
3. **Given** una orden enviada con éxito o una cancelación desde esta ficha, **When** termina, **Then** el libro se vuelve a cargar y refleja el nuevo estado.
4. **Given** que el libro no se puede cargar, **When** falla, **Then** se muestra un error con botón de reintento, y el resto de la ficha (cabecera, gráfico, estadísticas) sigue funcionando.
5. **Given** el libro, **When** se muestra, **Then** existe una lectura accesible como tabla con encabezados de lado, precio y cantidad.

---

### User Story 5 - Ver y cancelar mis órdenes activas en Mercado (Priority: P2)

Desde el ítem "03 Mercado" del menú, el usuario llega a una pantalla con sus órdenes activas (abiertas o parcialmente ejecutadas) y puede cancelar cualquiera para liberar lo reservado.

**Why this priority**: Una LIMIT que nadie toma queda reservando créditos o tokens; sin esta pantalla el usuario no puede recuperarlos. Depende de que existan órdenes creadas por las historias anteriores.

**Independent Test**: Con un usuario con varias órdenes LIMIT activas (y otras cerradas), abrir Mercado y verificar que se listan sólo las activas propias; cancelar una y verificar que desaparece.

**Acceptance Scenarios**:

1. **Given** un usuario autenticado, **When** mira el menú, **Then** "03 Mercado" está habilitado y lleva a la pantalla de Mercado; "04 Mi portfolio" sigue deshabilitado.
2. **Given** un usuario con órdenes activas, **When** abre Mercado, **Then** ve una lista de la más nueva a la más vieja con: jugador (nombre, con enlace a su ficha), lado (compra/venta), tipo, precio límite, cantidad, ejecutado, pendiente, estado y fecha de creación.
3. **Given** un usuario sin órdenes activas, **When** abre Mercado, **Then** ve un estado vacío que explica que no tiene órdenes abiertas e invita a ir al Álbum para operar.
4. **Given** una orden de la lista, **When** el usuario pulsa "Cancelar", **Then** se pide confirmación indicando jugador, lado, pendiente y precio; al confirmar se cancela, desaparece de la lista y aparece un aviso con lo liberado (créditos para una compra, tokens para una venta).
5. **Given** una cancelación rechazada porque la orden ya se ejecutó o canceló, **When** llega la respuesta, **Then** se muestra "Esta orden ya no está activa." y se recarga la lista.
6. **Given** una cancelación en curso, **When** el usuario intenta cancelar la misma orden otra vez, **Then** no se puede hasta recibir la respuesta.
7. **Given** que la lista no se puede cargar, **When** falla, **Then** se muestra un error con reintento.
8. **Given** un visitante sin sesión, **When** abre la ruta de Mercado, **Then** es redirigido al ingreso, como en el resto de las rutas protegidas.

---

### User Story 6 - Listar mis órdenes activas (backend) (Priority: P2)

Como frontend, necesito obtener las órdenes activas del usuario autenticado para mostrar la pantalla de Mercado.

**Why this priority**: Es la única pieza que falta en el backend para la Historia 5.

**Independent Test**: Con órdenes de dos usuarios en distintos estados, pedir `GET /orders/mine` como uno de ellos y verificar que sólo vienen las propias y activas, en orden; pedirlo sin credenciales y verificar 401.

**Acceptance Scenarios**:

1. **Given** un usuario con órdenes OPEN, PARTIALLY_FILLED, FILLED y CANCELLED, y otro usuario con órdenes activas, **When** el primero pide `GET /orders/mine`, **Then** responde 200 sólo con sus órdenes OPEN y PARTIALLY_FILLED, de la más nueva a la más vieja.
2. **Given** cada orden de la respuesta, **When** se revisa, **Then** tiene el mismo formato que la respuesta de `POST /orders/buy` más el nombre del jugador.
3. **Given** un usuario sin órdenes activas, **When** pide `GET /orders/mine`, **Then** responde 200 con una lista vacía.
4. **Given** un pedido sin credenciales, **When** se manda, **Then** responde 401.

---

### User Story 7 - Responsive y accesible (Priority: P3)

El panel, el libro y la pantalla de Mercado se usan con mouse, teclado, lector de pantalla y en pantallas chicas, con la estética actual de la aplicación.

**Why this priority**: Es transversal y se verifica sobre las historias anteriores.

**Independent Test**: Recorrer el flujo de compra y de cancelación sólo con teclado y con un lector de pantalla, y en un ancho de 360 px.

**Acceptance Scenarios**:

1. **Given** un ancho de 360 px, **When** se usan el panel, el libro y la lista de órdenes, **Then** no hay scroll horizontal de la página y todo se puede operar.
2. **Given** sólo teclado, **When** se completa una compra y una cancelación, **Then** todos los controles son alcanzables, el foco es visible, la confirmación atrapa el foco y al cerrarse lo devuelve al control que la abrió.
3. **Given** un lector de pantalla, **When** aparece un error de validación, un aviso de resultado o un error del mercado, **Then** se anuncia, y cada campo inválido queda asociado a su mensaje.
4. **Given** compra/venta y suba/baja, **When** se muestran, **Then** se distinguen por texto además de color, con contraste suficiente.

### Edge Cases

- **Cotización no disponible en la ficha**: si el libro del jugador trae `marketValue` nulo, el panel queda deshabilitado con "Este jugador todavía no tiene cotización"; el libro se muestra igual. Si la cabecera no muestra cotización pero el libro sí la trae, se puede operar.
- **Total estimado de una MARKET sin liquidez suficiente**: si el lado opuesto del libro no alcanza para la cantidad, el estimado muestra el total de lo que alcanzaría y avisa cuántos tokens no tendrían contraparte y se cancelarían.
- **Libro cambiado entre estimar y enviar**: el resultado real puede diferir del estimado; el aviso final siempre muestra lo efectivamente ejecutado y el precio promedio real que informa el mercado.
- **Compra LIMIT a precio mayor que la mejor venta**: el estimado usa cantidad × precio límite (lo máximo que se reserva o paga); el aviso final muestra el precio real de ejecución.
- **Usuario que se calzaría consigo mismo**: el mercado saltea sus propias órdenes; el estimado de una MARKET no las distingue, porque el libro no identifica dueños, y el resultado real lo muestra el aviso.
- **Superusuario logueado**: el mercado rechaza sus órdenes y cancelaciones (403); el panel y la pantalla de Mercado muestran el motivo ("El superusuario no puede operar desde la aplicación.") y no cambia nada.
- **Cancelación de una orden ajena o inexistente** (lista desactualizada): se muestra el motivo y se recarga la lista.
- **Respuestas fuera de orden**: si el usuario navega a otro jugador mientras carga un libro, sólo se muestra el libro del jugador actual.
- **Navegar durante un envío**: si el usuario sale de la ficha mientras se envía una orden, no se muestran avisos ni errores en otra pantalla por esa orden.
- **Formato de números**: precios y totales con 4 decimales y el formato de créditos ya usado en la aplicación; cantidades enteras.

## Requirements *(mandatory)*

### Functional Requirements

**Panel de operación (ficha del jugador)**

- **FR-001**: La ficha del jugador MUST incluir un panel para operar con: lado (Comprar / Vender), tipo (MARKET / LIMIT), cantidad y, sólo en LIMIT, precio límite.
- **FR-002**: El panel MUST mostrar un total estimado que se actualiza al cambiar cualquier dato: en LIMIT, cantidad × precio límite; en MARKET, la suma recorriendo el lado opuesto del libro (ventas de menor a mayor para comprar, compras de mayor a menor para vender) hasta cubrir la cantidad, avisando si el libro no alcanza.
- **FR-003**: El panel MUST validar antes de enviar: cantidad entera mayor a 0; en LIMIT, precio presente, mayor a 0, con hasta 4 decimales y entre cotización × 0.80 y cotización × 1.20 (extremos incluidos), mostrando el rango permitido junto al campo.
- **FR-004**: El panel MUST pedir confirmación antes de enviar, mostrando lado, tipo, jugador, cantidad, precio (en LIMIT) y total estimado; MUST NOT enviar nada si el usuario no confirma.
- **FR-005**: El panel MUST impedir envíos duplicados mientras una orden está en curso.
- **FR-006**: Al recibir la orden resultante, el sistema MUST mostrar un aviso (Toast) con: cantidad ejecutada y precio promedio (o que no se ejecutó nada), y lo pendiente según el estado: quedó en el libro (cantidad y precio) para una LIMIT abierta o parcial, o se canceló para una MARKET con resto.
- **FR-007**: Tras un envío exitoso, el panel MUST limpiar la cantidad y el precio y conservar lado y tipo.
- **FR-008**: El panel MUST quedar deshabilitado, con el motivo "Este jugador todavía no tiene cotización", si el libro del jugador trae `marketValue` nulo. La cotización de la cabecera (024) no decide esto: puede faltar aunque el jugador tenga cotización vigente y se pueda operar (por ejemplo, un jugador nuevo a 1.00 antes del primer recálculo).

**Errores**

- **FR-009**: El sistema MUST traducir cada código de error del mercado a un mensaje claro en español: saldo insuficiente, tokens insuficientes (mencionando reservas), precio fuera de banda, jugador sin cotización, jugador inexistente, superusuario no habilitado, credencial sin usuario, orden inexistente, orden ajena, orden ya cerrada, y un mensaje genérico para errores de validación, de red o inesperados.
- **FR-010**: Ante un rechazo por precio fuera de banda, el sistema MUST volver a cargar el libro del jugador, que trae la cotización de referencia de la banda (`marketValue`). La cabecera de la 024 no se recarga.
- **FR-011**: Ante cualquier error del envío, el panel MUST conservar los datos cargados.
- **FR-012**: Una respuesta 401 MUST seguir el comportamiento existente de sesión vencida.

**Libro de órdenes (ficha del jugador)**

- **FR-013**: La ficha MUST mostrar el libro del jugador: ventas de menor a mayor precio y compras de mayor a menor, cada nivel con precio y cantidad, y la cotización vigente.
- **FR-014**: El libro MUST volver a cargarse después de cada orden enviada con éxito desde la ficha.
- **FR-015**: El libro MUST tener estados de carga, lado vacío y error con reintento, sin afectar al resto de la ficha.
- **FR-016**: El sistema MUST mostrar sólo la respuesta del libro del jugador que se está viendo, descartando respuestas de un jugador anterior.

**Pantalla de Mercado**

- **FR-017**: El ítem "03 Mercado" del menú MUST estar habilitado y llevar a una ruta protegida de la pantalla de Mercado; "04 Mi portfolio" MUST seguir deshabilitado.
- **FR-018**: La pantalla de Mercado MUST listar las órdenes activas del usuario de la más nueva a la más vieja, con jugador (nombre y enlace a su ficha), lado, tipo, precio límite, cantidad, ejecutado, pendiente, estado y fecha de creación, con estados de carga, vacío y error con reintento.
- **FR-019**: Cada orden de la lista MUST tener un botón "Cancelar" que pide confirmación, cancela la orden, la saca de la lista y muestra un aviso con lo liberado; mientras se cancela, MUST impedir cancelarla de nuevo. Si la orden ya no está activa, no existe o es ajena, MUST mostrar el motivo y recargar la lista.

**Endpoint `GET /orders/mine` (backend)**

- **FR-020**: El sistema MUST exponer `GET /orders/mine` en el `OrderController` existente de la 027, que devuelve las órdenes OPEN y PARTIALLY_FILLED del usuario autenticado, de la más nueva a la más vieja, sin paginar.
- **FR-021**: Cada orden MUST tener el mismo formato que la respuesta de `POST /orders/buy`, más el nombre del jugador.
- **FR-022**: El endpoint MUST NOT devolver nunca órdenes de otro usuario, y MUST responder 401 sin credenciales.
- **FR-023**: El endpoint MUST reusar las consultas existentes de `MarketOrderRepository` si ya cubren el caso; sólo si no, agregar la consulta mínima necesaria.
- **FR-024**: El endpoint MUST tener tests MockMvc de 200 (sólo las propias y sólo activas) y de 401, y estar documentado en OpenAPI con `bearerAuth` y `apiKeyAuth`.
- **FR-025**: El endpoint MUST NOT modificar archivos protegidos de Entrega 1 ni cambiar el comportamiento de los otros endpoints de la 027.

**Accesibilidad, responsive y estética**

- **FR-026**: El panel, el libro y la pantalla de Mercado MUST funcionar sin scroll horizontal de la página desde 360 px de ancho.
- **FR-027**: Todos los controles MUST poder usarse con teclado con foco visible; las confirmaciones MUST atrapar el foco y devolverlo al cerrarse.
- **FR-028**: Los errores de campo MUST estar asociados a su campo, y los avisos y errores del mercado MUST anunciarse a lectores de pantalla.
- **FR-029**: Compra/venta y estados MUST distinguirse por texto además de color, y la pantalla MUST reutilizar los componentes y tokens visuales existentes.

**Restricciones**

- **FR-030**: Esta feature MUST NOT modificar archivos protegidos de Entrega 1, salvo la excepción autorizada: agregar la ruta de Mercado en `App.jsx` y habilitar el ítem `mercado` de `NAV_ITEMS` en `layout/AppLayout.jsx`.

### Key Entities

- **Orden (vista)**: lo que devuelve el mercado al crear, cancelar o listar: jugador (y su nombre en la lista de Mercado), lado, tipo, precio límite, cantidad, ejecutado, pendiente, estado, precio promedio, fechas y operaciones ejecutadas.
- **Nivel del libro**: un lado, un precio y la cantidad pendiente total a ese precio.
- **Libro del jugador**: cotización vigente, niveles de venta y niveles de compra.
- **Borrador de orden** (sólo en pantalla): lado, tipo, cantidad, precio y total estimado, con sus errores de validación.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un usuario puede comprar tokens de un jugador desde su ficha, de abrir el panel a ver el resultado, en menos de 30 segundos y con no más de una confirmación.
- **SC-002**: El 100% de las órdenes con cantidad no entera o precio LIMIT fuera de ±20% se frenan antes de enviarse.
- **SC-003**: El 100% de los códigos de error del mercado listados en FR-009 muestran un mensaje en español específico; ninguno muestra un código técnico o el mensaje crudo del servidor.
- **SC-004**: En el 100% de las operaciones, el aviso final coincide con lo ejecutado y lo pendiente que informa el mercado, y el libro mostrado después refleja la operación.
- **SC-005**: Un usuario puede encontrar y cancelar una orden activa desde el menú en menos de 15 segundos.
- **SC-006**: La lista de Mercado nunca muestra órdenes de otro usuario ni órdenes ejecutadas o canceladas.
- **SC-007**: Los flujos de compra, venta y cancelación se completan sólo con teclado y a 360 px de ancho, sin scroll horizontal de la página.

## Assumptions

- **Saldo y tenencias**: no se muestran en el panel porque no hay endpoint de portfolio todavía; los rechazos de saldo o tokens insuficientes los informa el mercado con mensajes claros.
- **Cotización de referencia**: la banda de ±20% del panel se calcula con la cotización vigente que trae el libro del jugador (la misma contra la que valida el mercado); si difiere de la de la cabecera, manda la del libro.
- **Estimado**: el total estimado es orientativo y no incluye la exclusión de órdenes propias; el resultado real lo informa el aviso.
- **Actualización del libro**: se recarga al abrir la ficha, después de operar y con reintento manual; no hay actualización en tiempo real.
- **Ubicación del panel**: el panel y el libro se agregan en la ficha del jugador (024) sin cambiar el aspecto de la cabecera, el gráfico ni las estadísticas.
- **Toast**: se reutiliza el componente existente; el resultado también queda visible en el panel hasta el próximo cambio, para quien no alcance a leer el aviso.
- **Cancelación desde la ficha**: no forma parte de esta feature; se cancela desde la pantalla de Mercado.
- **403 heredado en `GET /orders/mine`**: el endpoint resuelve el usuario igual que el resto de los endpoints de órdenes de la 027, así que responde 403 `superuser_not_allowed` para el superusuario y 403 `user_required` para una credencial sin usuario. No es comportamiento nuevo ni requiere manejo de errores nuevo en el backend.
- **Dependencias**: endpoints y contrato de la 027; ficha del jugador de la 024; sesión, rutas protegidas y cliente HTTP existentes del frontend.
