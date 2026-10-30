# Feature Specification: Motor de calce y creador de mercado

**Feature Branch**: `feature/motor-calce`

**Created**: 2026-10-29

**Status**: Draft

**Input**: User description: "Motor de calce, endpoints para comprar, vender, cancelar y ver el libro, y el superusuario como creador de mercado."

## Contexto y restricciones

**Enunciado**: cada jugador tiene 100 tokens emitidos. Al inicio todos pertenecen a un único superusuario y cada token vale 1 crédito. Comprar valida disponibilidad y usa la cotización vigente. Vender valida que el usuario tenga los tokens. Las compras se hacen inicialmente contra el superusuario. Los endpoints obligatorios son `POST /orders/buy`, `POST /orders/sell`, `GET /users/{id}/portfolio` y `GET /users/{id}/transactions`. Toda transacción financiera deja un registro inmutable con autor, detalle, fecha y hora, y estado anterior y posterior.

**Lo que ya existe en main**:

- Los usuarios se registran con `POST /auth/register`. Cada uno tiene un saldo (`User.balance`, 1000 créditos al registrarse) y un rol (`USER` o `ADMIN`).
- La cotización vigente de un jugador es `Player.marketValue` (4 decimales). La escribe el recálculo de la feature 016 con la estrategia activa, y al terminar cada recálculo se publica `QuoteRunCompletedEvent`. Los jugadores nuevos del catálogo arrancan con 1.00.
- La feature 026 dejó el modelo del mercado: el superusuario (creado al arrancar), la emisión de 100 tokens por jugador a su nombre (al arrancar y al terminar cada recálculo), y las entidades tenencia (con tokens reservados), orden, operación ejecutada y auditoría inmutable, con sus invariantes e índices del libro. Los créditos reservados de un usuario se calculan a partir de sus órdenes de compra LIMIT abiertas.

**Decisiones del equipo sobre el mercado**:

- El mercado es un libro de órdenes LIMIT y MARKET con motor de calce. `POST /orders/buy` y `POST /orders/sell` crean órdenes en el libro. Además existen `DELETE /orders/{id}` y `GET /orders/book`.
- El superusuario es el creador de mercado sólo del lado de la venta. Arranca ofreciendo sus 100 tokens de cada jugador a la cotización vigente (1.00 para un jugador nuevo) y, después de cada recálculo, su orden de venta pasa a la nueva marketValue. No compra: una orden que nadie quiere queda en el libro hasta que aparezca contraparte o su dueño la cancele.
- El calce se hace al crear la orden, al precio de la orden que ya estaba en el libro, con calces parciales. Lo que una MARKET no llena se cancela. Una LIMIT reserva créditos o tokens hasta llenarse o cancelarse.
- Las LIMIT de los usuarios deben estar dentro de ±20% de marketValue.
- El portfolio se valúa a marketValue.

**Alcance de esta feature**: el motor de calce, los endpoints `POST /orders/buy`, `POST /orders/sell`, `DELETE /orders/{id}` y `GET /orders/book`, y la orden de venta del superusuario como creador de mercado (publicación al arrancar y reprecio tras cada recálculo). **Fuera de alcance**: `GET /users/{id}/portfolio` y `GET /users/{id}/transactions` (siguiente feature del mercado; esta deja registradas las operaciones que ellos van a mostrar), la consola de trading del frontend, y cualquier cambio a la emisión de tokens de la 026.

**Archivos protegidos de Entrega 1**: `User`, `UserRepository`, `application.properties`, `PlayerCatalogService` y `GlobalExceptionHandler` no se modifican sin consultar al equipo. **Excepción autorizada** (2026-10-29): en `User`, sólo la anotación de `balance` pasa a `@Column(precision = 38, scale = 4)`, para que los saldos tengan los mismos 4 decimales que los precios (ver FR-032 a FR-035). Los errores nuevos se manejan con un manejador acotado a los controllers del mercado (como ya hace `QuoteExceptionHandler`) y la configuración nueva va en `market.properties`. Si durante la planificación o la implementación resulta imprescindible un cambio en esos archivos, se detiene el trabajo y se consulta al equipo indicando archivo, cambio mínimo y alternativa.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Comprar tokens al creador de mercado (Priority: P1)

Como usuario registrado, quiero comprar tokens de un jugador con una orden MARKET o LIMIT, para empezar a armar mi portfolio comprándole al superusuario a la cotización vigente.

**Why this priority**: Es la primera operación del mercado y la que pide el enunciado: las compras se hacen inicialmente contra el superusuario. Sin ella no hay tokens en manos de usuarios y nada más tiene sentido.

**Independent Test**: Con el superusuario ofreciendo 100 tokens de un jugador a su marketValue, un usuario con 1000 créditos compra con MARKET y con LIMIT; se verifican la orden, la operación, los saldos, las tenencias y la auditoría.

**Acceptance Scenarios**:

1. **Given** un jugador con marketValue 1.2000 y la orden de venta del superusuario por 100 tokens a 1.2000, **When** un usuario con 1000 créditos manda una compra MARKET de 10, **Then** la orden queda ejecutada con 10 tokens, se registra una operación de 10 a 1.2000 por 12.0000, el usuario tiene 10 tokens del jugador, su saldo baja 12.0000, el saldo del superusuario sube 12.0000 y a la orden del superusuario le quedan 90.
2. **Given** el mismo libro, **When** el usuario manda una compra LIMIT de 10 a 1.3000, **Then** se ejecuta a 1.2000 (el precio de la orden que estaba en el libro), no a 1.3000, y el usuario paga 12.0000.
3. **Given** el mismo libro, **When** el usuario manda una compra LIMIT de 10 a 1.1000, **Then** no se ejecuta nada, la orden queda abierta en el libro y reserva 11.0000 créditos del usuario; su saldo no cambia pero su saldo disponible baja 11.0000.
4. **Given** una compra LIMIT de 10 a 1.3000 y el superusuario con sólo 4 tokens a 1.2000, **When** se manda la orden, **Then** se ejecutan 4 a 1.2000, la orden queda parcialmente ejecutada con 6 pendientes en el libro y reserva 6 × 1.3000 = 7.8000 créditos.
5. **Given** una compra MARKET de 10 y sólo 4 tokens ofrecidos en todo el libro, **When** se manda la orden, **Then** se ejecutan 4 y los 6 restantes se cancelan: la orden queda cancelada con 4 ejecutados y no reserva nada.
6. **Given** cada calce, **When** se revisa la auditoría, **Then** hay entradas con el estado anterior y posterior de los saldos de comprador y vendedor, de sus tenencias y de las dos órdenes, todas con el mismo correlation id del pedido.

---

### User Story 2 - Vender tokens a otros usuarios (Priority: P1)

Como usuario con tokens, quiero vender con una orden MARKET o LIMIT, para cobrar créditos cuando otro usuario quiera comprar.

**Why this priority**: Es el otro endpoint obligatorio. Como el superusuario no compra, la venta sólo se ejecuta contra órdenes de compra de otros usuarios; es lo que convierte el sistema en un mercado entre usuarios.

**Independent Test**: Dos usuarios: A tiene tokens y B deja una compra LIMIT en el libro. A vende con MARKET y con LIMIT; se verifican calces, reservas de tokens y saldos.

**Acceptance Scenarios**:

1. **Given** B con una compra LIMIT abierta de 5 a 1.1000 y A con 10 tokens, **When** A manda una venta MARKET de 5, **Then** se ejecutan 5 a 1.1000, A recibe 5.5000 créditos y le quedan 5 tokens, B recibe 5 tokens y se libera su reserva de créditos.
2. **Given** A con 10 tokens y nadie comprando, **When** A manda una venta LIMIT de 6 a 1.2000, **Then** la orden queda abierta en el libro, A tiene 6 tokens reservados y 4 disponibles.
3. **Given** A con 10 tokens de los cuales 6 están reservados, **When** A manda una venta de 5, **Then** se rechaza con 422 por tokens insuficientes y nada cambia.
4. **Given** A sin compradores en el libro, **When** manda una venta MARKET de 5, **Then** no se ejecuta nada y la orden queda cancelada con 0 ejecutados: el superusuario nunca compra.
5. **Given** una venta LIMIT de A abierta, **When** B manda después una compra MARKET y la mejor venta es la de A, **Then** B le compra a A al precio de la orden de A.

---

### User Story 3 - Prioridad del libro y calces parciales (Priority: P1)

Como usuario que opera, quiero que mi orden se calce primero contra el mejor precio y, a igual precio, contra la orden más vieja, para que el mercado sea justo y predecible.

**Why this priority**: Es la regla central del motor de calce; sin ella el resultado de una orden no es reproducible ni verificable.

**Independent Test**: Armar un libro con varias ventas a distintos precios y a igual precio con distintas fechas, mandar una compra que barra varias, y verificar el orden y precio de cada operación.

**Acceptance Scenarios**:

1. **Given** ventas abiertas de C por 3 a 1.1000 (más vieja), de D por 3 a 1.1000 (más nueva) y del superusuario por 50 a 1.2000, **When** un usuario compra MARKET 8, **Then** se registran tres operaciones en este orden: 3 con C a 1.1000, 3 con D a 1.1000 y 2 con el superusuario a 1.2000.
2. **Given** compras abiertas a 1.0000 y 1.1500, **When** un usuario vende MARKET, **Then** primero se calza con la de 1.1500.
3. **Given** una compra LIMIT a 1.1000, **When** se calza, **Then** sólo se ejecuta contra ventas a 1.1000 o menos; lo demás queda en el libro.
4. **Given** una orden del libro que se llena del todo, **When** termina el calce, **Then** queda ejecutada con fecha de cierre y sale del libro; si se llena en parte, queda parcialmente ejecutada con su pendiente en el libro y conserva su antigüedad.
5. **Given** una compra de un usuario y una venta del mismo usuario en el libro, **When** se calzaría con ella, **Then** se saltea esa orden y se sigue con la siguiente: un usuario nunca opera consigo mismo.

---

### User Story 4 - Validaciones y errores (Priority: P1)

Como usuario, quiero que una orden inválida se rechace con un error claro y sin cambiar nada, para no terminar con saldos o tenencias inconsistentes.

**Why this priority**: Lo exige la constitución (4.3): ningún monto negativo, compra sin saldo ni venta de tokens no poseídos.

**Independent Test**: Mandar órdenes con cada tipo de dato inválido, sin credenciales, sin saldo y sin tokens, y verificar código de respuesta, shape del error y que nada cambió.

**Acceptance Scenarios**:

1. **Given** un pedido con cantidad 0, negativa o no entera, sin jugador o sin tipo, **When** se manda, **Then** responde 400 de validación.
2. **Given** una LIMIT sin precio límite, con precio cero o negativo, con más de 4 decimales, o una MARKET con precio límite, **When** se manda, **Then** responde 400 de validación.
3. **Given** un jugador con marketValue 1.0000, **When** se manda una LIMIT a 0.7999 o a 1.2001, **Then** responde 400 por precio fuera de banda; a 0.8000 o a 1.2000 se acepta.
4. **Given** un jugador inexistente, **When** se manda una orden, **Then** responde 404.
5. **Given** un usuario cuyo saldo disponible no cubre el costo de la compra, **When** la manda, **Then** responde 422 por saldo insuficiente y no se ejecuta ni se reserva nada.
6. **Given** un pedido sin credenciales o con credenciales inválidas, **When** se manda, **Then** responde 401.
7. **Given** cualquiera de estos errores, **When** se revisa la respuesta, **Then** tiene el shape `{"error": "...", "message": "..."}` del resto de la API, y no se crearon órdenes, operaciones ni auditorías.

---

### User Story 5 - Cancelar una orden propia (Priority: P2)

Como usuario con una orden LIMIT abierta, quiero cancelarla, para recuperar los créditos o tokens que tiene reservados.

**Why this priority**: Una orden que nadie quiere queda en el libro indefinidamente; sin cancelación, lo reservado queda bloqueado para siempre.

**Independent Test**: Crear órdenes LIMIT de compra y venta abiertas y parcialmente ejecutadas, cancelarlas, e intentar cancelar órdenes inexistentes, ajenas y cerradas.

**Acceptance Scenarios**:

1. **Given** una compra LIMIT propia de 10 a 1.1000 con 4 ejecutados, **When** el dueño la cancela, **Then** queda cancelada con 4 ejecutados y fecha de cierre, sale del libro, y se liberan los 6.6000 créditos reservados.
2. **Given** una venta LIMIT propia abierta de 6, **When** el dueño la cancela, **Then** los 6 tokens dejan de estar reservados.
3. **Given** un id de orden que no existe, **When** se cancela, **Then** responde 404.
4. **Given** una orden de otro usuario, **When** se intenta cancelar, **Then** responde 403 y la orden no cambia.
5. **Given** una orden ejecutada o cancelada, **When** su dueño la intenta cancelar, **Then** responde 409.
6. **Given** una cancelación, **When** se revisa la auditoría, **Then** hay entradas con el estado anterior y posterior de la orden y de lo que se liberó.

---

### User Story 6 - Ver el libro de un jugador (Priority: P2)

Como usuario, quiero ver a qué precios se compra y se vende un jugador y cuántos tokens hay en cada precio, junto con su cotización vigente, para decidir a qué precio poner mi orden.

**Why this priority**: Es la información que necesita la consola de trading; sin ella el usuario opera a ciegas.

**Independent Test**: Armar un libro con varias órdenes por precio y consultar el libro del jugador.

**Acceptance Scenarios**:

1. **Given** ventas abiertas de 3 y 4 tokens a 1.1000, y 50 del superusuario a 1.2000, y compras de 5 a 1.0500, **When** se consulta el libro del jugador, **Then** responde las ventas `[1.1000 → 7, 1.2000 → 50]` de menor a mayor precio, las compras `[1.0500 → 5]` de mayor a menor precio, y la marketValue del jugador.
2. **Given** órdenes parcialmente ejecutadas, **When** se consulta el libro, **Then** cada precio suma sólo las cantidades pendientes.
3. **Given** un jugador sin órdenes abiertas, **When** se consulta, **Then** responde los dos lados vacíos y la marketValue.
4. **Given** el libro, **When** se consulta, **Then** no revela quién es el dueño de cada orden.
5. **Given** un jugador inexistente o sin `playerId`, **When** se consulta, **Then** responde 404 o 400 respectivamente.

---

### User Story 7 - El superusuario como creador de mercado (Priority: P1)

Como equipo que opera el mercado, queremos que el superusuario tenga siempre una orden de venta con todos sus tokens a la cotización vigente de cada jugador, para que los usuarios puedan comprar desde el primer día y el precio de referencia siga al recálculo.

**Why this priority**: Sin esta orden el libro arranca vacío y nadie puede comprar, porque todos los tokens son del superusuario.

**Independent Test**: Arrancar con jugadores emitidos, verificar sus órdenes; vender algunos tokens; correr un recálculo que cambia marketValue y verificar que la orden se rehízo al nuevo precio con los tokens restantes.

**Acceptance Scenarios**:

1. **Given** jugadores con sus 100 tokens emitidos al superusuario, **When** arranca el sistema, **Then** el superusuario tiene, por cada jugador, una única venta LIMIT abierta con todos sus tokens disponibles a la marketValue del jugador, con sus tokens reservados.
2. **Given** el superusuario con 70 tokens restantes de un jugador cuya marketValue pasa de 1.2000 a 1.3500, **When** termina el recálculo, **Then** su orden anterior queda cancelada con fecha de cierre y hay una nueva venta LIMIT de 70 a 1.3500, con auditoría del cambio.
3. **Given** un jugador nuevo emitido al terminar un recálculo, **When** termina ese mismo recálculo, **Then** el superusuario tiene su orden de venta de 100 tokens a la marketValue del jugador.
4. **Given** el superusuario sin tokens de un jugador, **When** termina un recálculo, **Then** no se crea ninguna orden para ese jugador.
5. **Given** una marketValue que se movió más de 20%, **When** se rehace la orden del superusuario, **Then** se acepta igual: su orden no se rige por la banda.
6. **Given** cualquier arranque o recálculo repetido, **When** termina, **Then** el superusuario nunca tiene más de una orden abierta por jugador y nunca tiene órdenes de compra.
7. **Given** una falla al rehacer la orden de un jugador, **When** termina el recálculo, **Then** los demás jugadores se rehacen igual, el recálculo no falla y la orden anterior de ese jugador queda como estaba.

---

### User Story 8 - Órdenes simultáneas sin sobreventa (Priority: P1)

Como usuario y como equipo, queremos que dos órdenes que llegan al mismo tiempo no vendan el mismo token dos veces ni dejen saldos o tenencias negativos.

**Why this priority**: Es un error financiero; un solo caso de sobreventa rompe la regla de 100 tokens por jugador.

**Independent Test**: Lanzar en paralelo muchas compras sobre un jugador con pocos tokens ofrecidos, y muchas compras de un mismo usuario sobre jugadores distintos que en conjunto superan su saldo; verificar totales al final.

**Acceptance Scenarios**:

1. **Given** el superusuario ofreciendo 10 tokens, **When** 20 usuarios mandan al mismo tiempo una compra MARKET de 1, **Then** se ejecutan exactamente 10 tokens, las otras órdenes quedan canceladas sin ejecutar y la suma de tenencias del jugador sigue siendo 100.
2. **Given** un usuario con 10 créditos disponibles, **When** manda al mismo tiempo dos compras de 8 créditos cada una sobre jugadores distintos, **Then** una se acepta y la otra responde 422; su saldo nunca queda negativo.
3. **Given** un usuario con 5 tokens disponibles, **When** manda al mismo tiempo dos ventas de 5, **Then** sólo una se acepta.
4. **Given** una orden que se calza mientras el recálculo rehace la orden del superusuario del mismo jugador, **When** terminan ambos, **Then** los tokens del superusuario (vendidos más ofrecidos) nunca superan los que tenía.

---

### Edge Cases

- **Compra que no se puede pagar entera**: el costo se calcula recorriendo el libro con los precios que realmente se van a ejecutar (MARKET) o con el precio límite para la parte que quedaría reservada (LIMIT). Si el saldo disponible no cubre ese total, la orden entera se rechaza con 422; no se ejecuta "lo que alcance".
- **Diferencia de reserva en una compra LIMIT**: la reserva es pendiente × precio límite; cuando una parte se ejecuta a un precio menor, el usuario paga el precio de ejecución y la reserva de lo ejecutado se libera completa.
- **Libro cruzado por órdenes propias**: como un usuario no opera consigo mismo, puede tener una compra a un precio mayor o igual que su propia venta; ambas quedan en el libro y se calzan con otros usuarios.
- **Superusuario operando por los endpoints**: no puede crear órdenes de compra ni de venta por los endpoints (responde 403) ni cancelar su orden de creador de mercado (responde 403); su orden la administra sólo el sistema.
- **Jugador sin tokens emitidos todavía** (agregado después del último recálculo): no hay ventas en el libro, así que una compra MARKET queda cancelada sin ejecutar y una compra LIMIT queda en el libro.
- **Jugador sin marketValue**: no se aceptan órdenes (422) y el superusuario no publica orden para él. Es una validación defensiva: hoy no se puede dar, porque `Player.marketValue` es obligatorio en la base y los jugadores nuevos arrancan con 1.00.
- **Orden MARKET sin nada ejecutado**: se registra igual, cancelada con 0 ejecutados, y se responde 201 con ese estado.
- **Banda**: se compara contra la marketValue vigente al momento de recibir la orden; los extremos (exactamente ±20%) son válidos. Una LIMIT que ya estaba en el libro no se cancela si después del recálculo queda fuera de la banda.
- **Recálculo que no cambia el precio**: si la orden del superusuario ya tiene la marketValue y todos sus tokens disponibles, no se rehace ni se audita.
- **Saldo del superusuario**: crece con cada venta; nunca disminuye por esta feature.
- **Redondeo**: precios, totales y reservas usan 4 decimales; el total de cada operación es exactamente cantidad × precio.

## Requirements *(mandatory)*

### Functional Requirements

**Crear órdenes**

- **FR-001**: El sistema MUST exponer `POST /orders/buy` y `POST /orders/sell`, que reciben jugador, cantidad, tipo (MARKET o LIMIT) y precio límite (obligatorio en LIMIT, prohibido en MARKET), crean la orden a nombre del usuario autenticado y responden 201 con la orden resultante (estado, cantidad ejecutada, cantidad pendiente, precio promedio de ejecución) y las operaciones ejecutadas por ella.
- **FR-002**: El sistema MUST validar que el jugador exista (404), que la cantidad sea un entero mayor a 0 (400) y que el precio límite sea positivo con hasta 4 decimales (400).
- **FR-003**: El precio límite de una orden LIMIT de un usuario MUST estar entre marketValue × 0.80 y marketValue × 1.20, extremos incluidos; si no, MUST responder 400 por precio fuera de banda.
- **FR-004**: Una compra MUST rechazarse con 422 si el saldo disponible del usuario (saldo menos créditos reservados) no cubre el costo de la parte que se va a ejecutar más la reserva de la parte que va a quedar en el libro.
- **FR-005**: Una venta MUST rechazarse con 422 si la cantidad supera los tokens disponibles del usuario para ese jugador (tenencia menos tokens reservados).
- **FR-006**: El superusuario MUST NOT poder crear órdenes por los endpoints (403).

**Calce**

- **FR-007**: Al crear una orden, el sistema MUST calzarla en el momento contra el lado opuesto del libro del mismo jugador: primero el mejor precio (la venta más barata para una compra, la compra más cara para una venta) y, a igual precio, la orden más vieja.
- **FR-008**: Una LIMIT MUST calzarse sólo contra órdenes a su precio límite o mejor. Una MARKET MUST calzarse contra cualquier precio del lado opuesto.
- **FR-009**: Cada calce MUST ejecutarse al precio de la orden que ya estaba en el libro, por la menor de las dos cantidades pendientes.
- **FR-010**: El calce MUST saltear las órdenes del mismo usuario que manda la orden.
- **FR-011**: Lo que una MARKET no llena MUST cancelarse: la orden queda cancelada con lo ejecutado hasta ahí. Lo que una LIMIT no llena MUST quedar en el libro, reservando créditos (pendiente × precio límite, para una compra) o tokens (pendiente, para una venta).

**Efectos de cada calce**

- **FR-012**: Cada calce MUST, en una sola unidad que se aplica entera o no se aplica: mover los tokens del vendedor al comprador (liberando la reserva de tokens del vendedor si la orden vendedora estaba en el libro), mover los créditos del comprador al vendedor, actualizar la cantidad ejecutada y el estado de las dos órdenes, registrar la operación ejecutada y dejar auditoría.
- **FR-013**: La creación de la orden, todos sus calces y su reserva o cancelación final MUST aplicarse juntas: si algo falla, no queda nada de esa orden.
- **FR-014**: La auditoría MUST registrar, con el usuario que mandó la orden como autor y el correlation id del pedido, el estado anterior y posterior de: los saldos del comprador y del vendedor, las tenencias de ambos, las dos órdenes, y la operación ejecutada. Las acciones nuevas son creación de orden, ejecución de operación, cambio de saldo, cambio de tenencia y cancelación de orden.

**Cancelar**

- **FR-015**: El sistema MUST exponer `DELETE /orders/{id}`, que cancela una orden abierta o parcialmente ejecutada del usuario autenticado, la saca del libro, libera lo que tenía reservado, la deja cancelada con su cantidad ejecutada y fecha de cierre, audita el cambio y responde con la orden cancelada.
- **FR-016**: `DELETE /orders/{id}` MUST responder 404 si la orden no existe, 403 si es de otro usuario o es la orden de creador de mercado del superusuario, y 409 si ya está ejecutada o cancelada.

**Libro**

- **FR-017**: El sistema MUST exponer `GET /orders/book?playerId=`, que responde la marketValue del jugador, los precios de venta de menor a mayor y los de compra de mayor a menor, cada uno con la suma de cantidades pendientes de las órdenes abiertas o parcialmente ejecutadas a ese precio, sin identificar a los dueños.
- **FR-018**: `GET /orders/book` MUST responder 400 si falta `playerId` o no es válido y 404 si el jugador no existe.

**Creador de mercado**

- **FR-019**: Al arrancar, después de la emisión de tokens, el sistema MUST asegurar que el superusuario tenga, por cada jugador del que tenga tokens, exactamente una venta LIMIT abierta con todos sus tokens a la marketValue del jugador.
- **FR-020**: Al terminar cada recálculo (`QuoteRunCompletedEvent`), después de la emisión de jugadores nuevos, el sistema MUST rehacer la orden del superusuario de cada jugador: cancelar la anterior y crear una nueva con los tokens que le queden a la nueva marketValue, con auditoría. Si el precio y la cantidad no cambiaron, MUST NOT rehacerla. Si no le quedan tokens, MUST NOT crear orden.
- **FR-021**: La orden del superusuario MUST NOT validarse contra la banda de ±20%.
- **FR-022**: El superusuario MUST NOT tener nunca órdenes de compra ni más de una orden abierta por jugador.
- **FR-023**: Una falla al publicar o rehacer la orden de un jugador MUST NOT impedir los demás jugadores ni hacer fallar el arranque o el recálculo; ese jugador se reintenta en el próximo arranque o recálculo.
- **FR-024**: Un recálculo MUST NOT cancelar ni modificar las órdenes de los usuarios, aunque queden fuera de la nueva banda.

**Concurrencia**

- **FR-025**: Las órdenes sobre un mismo jugador MUST calzarse de a una: dos órdenes simultáneas nunca pueden ejecutar los mismos tokens del libro.
- **FR-026**: Las validaciones de saldo y de tokens disponibles MUST hacerse sobre el estado bloqueado del usuario y su tenencia, de forma que órdenes simultáneas del mismo usuario, sobre el mismo jugador o sobre jugadores distintos, nunca dejen saldos, tenencias ni disponibles negativos.
- **FR-027**: Rehacer la orden del superusuario MUST respetar el mismo orden que el calce del jugador, para no ofrecer tokens ya vendidos.

**Errores, seguridad y observabilidad**

- **FR-028**: Todos los endpoints de esta feature MUST requerir credenciales (JWT o API key) y responder 401 sin ellas.
- **FR-029**: Los errores MUST usar el shape existente `{"error": "...", "message": "..."}`: 400 de validación y de precio fuera de banda (con códigos de error distinguibles), 422 por saldo o tokens insuficientes o jugador sin cotización, 404 por jugador u orden inexistente, 403 por orden ajena o uso del superusuario, 409 por orden cerrada, 401 sin credenciales.
- **FR-030**: Cada orden creada, calzada o cancelada y cada reprecio del superusuario MUST dejar logs estructurados con el correlation id del pedido o de la corrida, sin datos de credenciales.
- **FR-031**: Los cuatro endpoints MUST estar documentados en OpenAPI con sus pedidos, respuestas, códigos de error y los esquemas de seguridad `bearerAuth` y `apiKeyAuth`.

**Saldo**

- **FR-032**: Los créditos que se debitan y acreditan en cada calce MUST ser exactamente el total de la operación (cantidad × precio, 4 decimales) y la suma de saldos de comprador y vendedor MUST conservarse en cada calce. Para eso el saldo de los usuarios MUST guardarse con 4 decimales.
- **FR-033**: Como `spring.jpa.hibernate.ddl-auto=update` no cambia el tipo de una columna existente, el quickstart de esta feature MUST documentar, igual que la 016 con `market_value`, que las bases ya creadas necesitan ejecutar una vez `ALTER TABLE users ALTER COLUMN balance TYPE numeric(38,4);`. Los perfiles `test` y `e2e` crean la tabla de cero y no lo necesitan.
- **FR-034**: La nota de `MarketUserRepository` que dice que `users.balance` tiene escala 2 y que un delta con más decimales se redondea MUST actualizarse para reflejar la escala 4.

**Restricciones**

- **FR-035**: Esta feature MUST NOT modificar `User`, `UserRepository`, `application.properties`, `PlayerCatalogService` ni `GlobalExceptionHandler`, con una única excepción autorizada por el equipo (2026-10-29): en `User`, la anotación de `balance` pasa a `@Column(precision = 38, scale = 4)`. Nada más de `User` cambia: ni el valor por defecto, ni otros campos, ni otras anotaciones.

### Key Entities

Se reutilizan las entidades de la 026; esta feature no agrega entidades nuevas.

- **Orden**: la orden que manda un usuario o la del creador de mercado. Las LIMIT abiertas o parcialmente ejecutadas forman el libro; una orden se identifica como del creador de mercado por ser del superusuario.
- **Tenencia**: tokens del usuario por jugador y su reserva por ventas abiertas; se crea al primer calce de compra de ese jugador.
- **Operación ejecutada**: un calce entre una compra y una venta, al precio de la orden que estaba en el libro. Es la base del historial de transacciones.
- **Entrada de auditoría**: cada cambio de orden, saldo, tenencia y cada operación, con estado anterior y posterior.
- **Nivel del libro** (vista, no se guarda): un precio, un lado y la cantidad pendiente total a ese precio.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En el 100% de los casos de prueba de calce, el orden de las operaciones y sus precios coinciden con el cálculo manual por precio y antigüedad.
- **SC-002**: Tras cualquier secuencia de órdenes, cancelaciones y recálculos, para el 100% de los jugadores emitidos la suma de tenencias es 100, ninguna tenencia ni saldo es negativo, y la reserva de cada tenencia coincide con las ventas abiertas de su dueño.
- **SC-003**: En una prueba de 50 órdenes simultáneas sobre un jugador con 10 tokens ofrecidos, se ejecutan exactamente 10 tokens, en 10 de 10 repeticiones.
- **SC-004**: La suma de saldos de todos los usuarios no cambia por ningún calce.
- **SC-005**: El 100% de los calces tiene su operación y sus entradas de auditoría de saldos, tenencias y órdenes; el 100% de las órdenes rechazadas no deja ningún rastro en órdenes, operaciones ni auditoría.
- **SC-006**: Después de cada recálculo, el 100% de los jugadores en que el superusuario tiene tokens muestran su orden de venta a la nueva marketValue.
- **SC-007**: Un usuario recién registrado puede comprar sus primeros tokens con un solo pedido y ver el resultado en la respuesta, sin pasos previos.
- **SC-008**: Crear una orden que barre hasta 20 niveles del libro responde en menos de 500 ms, y consultar el libro de un jugador, en menos de 200 ms.

## Assumptions

- **Credenciales**: los endpoints aceptan JWT o API key, igual que los de jugadores y cotizaciones; el usuario de la orden es el autenticado, nunca uno que viaje en el pedido.
- **Arranque del creador de mercado**: como la 026 emite los tokens a 1.00 y los jugadores nuevos arrancan con marketValue 1.00, "arranca ofreciendo a 1.00" y "a marketValue" coinciden para un jugador nuevo; si el jugador ya tenía una marketValue distinta, se usa marketValue.
- **Reprecio como cancelación y orden nueva**: rehacer la orden del superusuario la pone al final de la cola de su nuevo precio; las órdenes de usuarios a ese precio tienen prioridad, lo cual es aceptable.
- **Respuesta de una MARKET sin calce**: 201 con la orden cancelada y 0 ejecutados, no un error, porque la orden sí se registró.
- **Profundidad del libro**: se muestran todos los niveles de precio; con 100 tokens por jugador la cantidad de niveles es chica.
- **Habilitación**: el creador de mercado usa la misma habilitación que el superusuario (`market.superuser.enabled` en `market.properties`); con eso deshabilitado no hay órdenes del superusuario y los endpoints siguen funcionando contra las órdenes de usuarios.
- **Escala del saldo**: con `numeric(38,4)` PostgreSQL conserva los saldos existentes (1000.00 → 1000.0000) y los devuelve con 4 decimales. Las comparaciones de saldos leídos de la base en tests existentes que usen `BigDecimal.equals` con escala 2 pueden necesitar ajustarse a comparación por valor, como pasó en la 016 con `marketValue`.
- **Errores nuevos**: se manejan en un manejador acotado a los controllers del mercado, sin tocar `GlobalExceptionHandler`.
- **Portfolio y transacciones**: `GET /users/{id}/portfolio` y `GET /users/{id}/transactions` quedan para la siguiente feature; las operaciones ejecutadas y la auditoría de esta feature ya tienen lo que necesitan.
- **Dependencias**: modelo del mercado y emisión de la 026; recálculo y `QuoteRunCompletedEvent` de la 016; autenticación de la 002/006; correlation id existente.
