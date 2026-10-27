# Feature Specification: Modelo de datos del mercado

**Feature Branch**: `feature/mercado-modelo`

**Created**: 2026-10-27

**Status**: Draft

**Input**: User description: "Modelo de datos del mercado: el superusuario con sus tokens, las tenencias, las órdenes, las operaciones ejecutadas y la auditoría inmutable. Sin motor de calce ni endpoints."

## Contexto y restricciones

**Enunciado**: cada jugador tiene 100 tokens emitidos. Al inicio todos pertenecen a un único superusuario y cada token vale 1 crédito. Comprar valida disponibilidad y usa la cotización vigente. Vender valida que el usuario tenga los tokens. Las compras se hacen inicialmente contra el superusuario. Los endpoints obligatorios son `POST /orders/buy`, `POST /orders/sell`, `GET /users/{id}/portfolio` y `GET /users/{id}/transactions`. Toda transacción financiera deja un registro inmutable con autor, detalle, fecha y hora, y estado anterior y posterior.

**Lo que ya existe en main**:

- Los usuarios se registran con `POST /auth/register`. Cada uno tiene un saldo (`User.balance`, 1000 créditos al registrarse) y un rol (`USER` o `ADMIN`).
- La cotización vigente de un jugador es su precio de mercado (`Player.marketValue`, 4 decimales). Lo escribe el recálculo de la feature 016 con la estrategia activa, y al terminar cada recálculo se publica `QuoteRunCompletedEvent`.
- La sincronización del catálogo agrega jugadores nuevos con precio de mercado inicial 1.00.

**Decisiones del equipo sobre el mercado** (contexto común de las features del mercado; esta feature sólo implementa el modelo):

- El mercado es un libro de órdenes LIMIT y MARKET con motor de calce. `POST /orders/buy` y `POST /orders/sell` crean órdenes en el libro. Además existen `DELETE /orders/{id}` y `GET /orders/book`.
- El superusuario es el creador de mercado sólo del lado de la venta. Arranca ofreciendo sus 100 tokens de cada jugador a 1.00 y, después de cada recálculo, su orden de venta pasa a marketValue. No compra: una orden que nadie quiere queda en el libro hasta que aparezca contraparte o su dueño la cancele.
- El calce se hace al crear la orden, al precio de la orden que ya estaba en el libro, con calces parciales. Lo que una MARKET no llena se cancela. Una LIMIT reserva créditos o tokens hasta llenarse o cancelarse.
- Las LIMIT de los usuarios deben estar dentro de ±20% de marketValue.
- El portfolio se valúa a marketValue.

**Alcance de esta feature**: el superusuario, la emisión de sus tokens, y las entidades tenencia, orden, operación ejecutada y auditoría con sus invariantes e índices. **Fuera de alcance**: el motor de calce, la publicación y el reprecio de las órdenes de venta del superusuario, la validación de ±20%, la valuación del portfolio y todos los endpoints. Quedan para las features siguientes del mercado.

**Archivos protegidos de Entrega 1**: `User`, `UserRepository`, `application.properties` y `PlayerCatalogService` no se modifican sin consultar al equipo. Esta feature se diseña para no tocarlos: la configuración del superusuario tiene sus valores por defecto en el código, los créditos reservados se calculan a partir de las órdenes sin agregar campos al usuario, y los jugadores nuevos reciben sus tokens al terminar el siguiente recálculo de cotizaciones en lugar de engancharse a la sincronización. Si durante la planificación o la implementación resulta imprescindible un cambio en esos archivos, se detiene el trabajo y se consulta al equipo indicando archivo, cambio mínimo y alternativa.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - El superusuario existe y es único (Priority: P1)

Como equipo que opera el mercado, queremos que al arrancar el sistema exista siempre un único superusuario con rol ADMIN, para que haya un dueño de los tokens emitidos y una contraparte para las primeras compras.

**Why this priority**: Sin superusuario no hay quién tenga los tokens emitidos, y nada del resto del mercado puede funcionar.

**Independent Test**: Arrancar el sistema contra una base vacía, contra una base que ya tiene el superusuario y con usuario, email y contraseña configurados distintos de los valores por defecto; verificar la cantidad de superusuarios, su rol y sus datos.

**Acceptance Scenarios**:

1. **Given** una base sin superusuario y sin configuración explícita, **When** arranca el sistema, **Then** existe exactamente un usuario con el usuario, email y contraseña por defecto, con rol ADMIN.
2. **Given** usuario, email y contraseña configurados por entorno, **When** arranca el sistema sobre una base sin superusuario, **Then** el superusuario se crea con esos valores y se puede autenticar con esa contraseña.
3. **Given** una base donde el superusuario ya existe, **When** el sistema arranca otra vez (una o varias veces), **Then** sigue existiendo un único superusuario y sus datos no cambian.
4. **Given** la creación del superusuario, **When** se revisa la auditoría, **Then** hay un registro de su creación con autor sistema, estado anterior vacío y estado posterior sin la contraseña ni su hash.
5. **Given** un usuario ya registrado con el nombre de usuario configurado para el superusuario pero con rol USER, **When** arranca el sistema, **Then** el arranque falla con un mensaje claro y no se promueve ni se modifica ese usuario.

---

### User Story 2 - Emisión de los tokens de cada jugador (Priority: P1)

Como equipo que opera el mercado, queremos que cada jugador del catálogo tenga sus 100 tokens emitidos a nombre del superusuario, incluidos los jugadores que se agregan después, para que todo jugador sea negociable a más tardar desde el siguiente recálculo de cotizaciones.

**Why this priority**: Es la regla de emisión del enunciado. Sin tokens emitidos no hay nada que vender.

**Independent Test**: Arrancar con un catálogo de jugadores, agregar jugadores nuevos, terminar un recálculo de cotizaciones, repetir arranques y recálculos, y contar las tenencias y tokens por jugador.

**Acceptance Scenarios**:

1. **Given** un catálogo con N jugadores y ningún token emitido, **When** arranca el sistema, **Then** el superusuario tiene una tenencia de 100 tokens, sin reservas, de cada uno de los N jugadores.
2. **Given** un jugador agregado por sincronización después del arranque, **When** termina el siguiente recálculo de cotizaciones (con o sin fallas), **Then** el superusuario tiene una tenencia de 100 tokens de ese jugador. Hasta entonces el jugador no tiene tokens emitidos.
3. **Given** jugadores con tokens ya emitidos, **When** se repiten el arranque o el recálculo, **Then** no se emiten tokens adicionales: la suma de las tenencias de cada jugador entre todos los usuarios sigue siendo 100.
4. **Given** un jugador cuyos tokens del superusuario ya se vendieron en parte o del todo, **When** se repite la emisión, **Then** no se le reponen tokens al superusuario.
5. **Given** cada emisión, **When** se revisa la auditoría, **Then** hay un registro por jugador emitido con autor sistema, la tenencia anterior (inexistente) y la posterior (100 tokens).
6. **Given** que falla la emisión de un jugador, **When** se reintenta en el próximo arranque o recálculo, **Then** se emite ese jugador sin duplicar los ya emitidos.

---

### User Story 3 - Tenencias, órdenes y reservas consistentes (Priority: P1)

Como futuro motor de calce y como usuario que opera, necesitamos registrar cuántos tokens tiene cada usuario de cada jugador, qué órdenes hizo y qué créditos y tokens están reservados por órdenes abiertas, para que nunca se venda lo que no se tiene ni se gaste lo que ya está comprometido.

**Why this priority**: Es la base sobre la que se construyen la compra, la venta, la cancelación y el portfolio.

**Independent Test**: Guardar tenencias y órdenes en distintos estados y verificar las invariantes, los disponibles y las reservas calculadas.

**Acceptance Scenarios**:

1. **Given** una tenencia de 10 tokens con 4 reservados, **When** se consulta, **Then** los tokens disponibles para vender son 6.
2. **Given** una tenencia, **When** se intenta dejar su cantidad o su reserva en negativo, o la reserva por encima de la cantidad, **Then** el cambio se rechaza y la tenencia queda como estaba.
3. **Given** un usuario y un jugador, **When** se intenta crear una segunda tenencia del mismo usuario para el mismo jugador, **Then** se rechaza: hay como máximo una tenencia por usuario y jugador.
4. **Given** una orden LIMIT de compra de 10 tokens a 1.0500 con 4 ejecutados, abierta, **When** se consultan los créditos reservados del usuario, **Then** son 6 × 1.0500 = 6.3000.
5. **Given** un usuario con órdenes de compra LIMIT abiertas y parcialmente ejecutadas, y otras ejecutadas, canceladas o MARKET, **When** se consultan sus créditos reservados, **Then** sólo suman las LIMIT de compra abiertas o parcialmente ejecutadas, por lo que les falta ejecutar a su precio límite.
6. **Given** un usuario sin órdenes de compra LIMIT abiertas, **When** se consultan sus créditos reservados, **Then** son 0.
7. **Given** una orden, **When** se intenta guardar con cantidad cero o negativa, con cantidad ejecutada mayor que la cantidad, una LIMIT sin precio límite o con precio no positivo, o una MARKET con precio límite, **Then** se rechaza.
8. **Given** una orden, **When** su cantidad ejecutada llega a la cantidad, **Then** su estado es ejecutada y tiene fecha de cierre; una orden cancelada también tiene fecha de cierre y conserva su cantidad ejecutada.

---

### User Story 4 - Operaciones ejecutadas y auditoría inmutables (Priority: P1)

Como usuario y como auditor, queremos que cada operación ejecutada y cada cambio financiero quede registrado de forma que no se pueda alterar ni borrar, para poder reconstruir el historial de transacciones de cualquier usuario.

**Why this priority**: El registro inmutable es obligatorio por el enunciado y la constitución (4.2), y `GET /users/{id}/transactions` se construirá sobre las operaciones ejecutadas.

**Independent Test**: Registrar operaciones y entradas de auditoría, intentar modificarlas y borrarlas, y consultarlas por usuario y por fecha.

**Acceptance Scenarios**:

1. **Given** una operación de 3 tokens a 1.0437, **When** se registra, **Then** queda con jugador, comprador, vendedor, cantidad 3, precio 1.0437, total 3.1311 y fecha de ejecución.
2. **Given** una operación registrada, **When** se intenta modificar cualquiera de sus datos o borrarla, **Then** se rechaza y la operación queda intacta.
3. **Given** una operación cuyo comprador y vendedor son el mismo usuario, o con cantidad o precio no positivos, **When** se intenta registrar, **Then** se rechaza.
4. **Given** una entrada de auditoría, **When** se registra, **Then** queda con autor, acción, tipo de entidad e id, estado anterior, estado posterior, fecha y hora y correlation id.
5. **Given** una entrada de auditoría registrada, **When** se intenta modificarla o borrarla, **Then** se rechaza y la entrada queda intacta.
6. **Given** varias operaciones de un usuario como comprador y como vendedor, **When** se consultan sus operaciones, **Then** aparecen todas, de la más nueva a la más vieja.

---

### User Story 5 - Consultas del libro y del historial eficientes (Priority: P2)

Como futuro motor de calce y endpoints del mercado, necesitamos que las consultas más frecuentes estén indexadas, para que el libro y los historiales respondan rápido aunque crezcan.

**Why this priority**: No cambia el comportamiento, pero el motor de calce consultará el libro en cada orden y la constitución (5.2) exige índices para transacciones.

**Independent Test**: Inspeccionar el esquema generado y verificar que existen los índices; ejecutar las consultas del libro y del historial con volumen y verificar que usan esos índices.

**Acceptance Scenarios**:

1. **Given** el esquema, **When** se inspecciona, **Then** existen índices para: el libro por jugador, lado y estado, ordenado por precio y antigüedad; las órdenes por usuario y fecha; las operaciones por comprador y fecha, por vendedor y fecha y por jugador y fecha; las tenencias por usuario; y la auditoría por entidad y por fecha.
2. **Given** las órdenes abiertas de venta de un jugador, **When** se consultan para el libro, **Then** salen de menor a mayor precio y, a igual precio, de la más vieja a la más nueva; las de compra salen de mayor a menor precio y, a igual precio, de la más vieja a la más nueva.

---

### Edge Cases

- **Jugador sin precio de mercado o recién sincronizado**: la emisión no depende del precio; se emiten igual los 100 tokens en el siguiente recálculo o arranque.
- **Arranques concurrentes o recálculo en paralelo con el arranque**: la unicidad de superusuario y de tenencia por usuario y jugador garantiza que no se dupliquen; el segundo intento no falla el arranque ni el recálculo, simplemente no emite.
- **Email configurado ya usado por otro usuario**: el arranque falla con un mensaje claro y no se modifica ese usuario.
- **Superusuario existente con datos distintos de la configuración actual**: no se actualizan; la configuración sólo se usa al crearlo.
- **Tenencia en cero**: la tenencia que queda en 0 tokens se conserva (no se borra), para no perder la relación del usuario con el jugador.
- **Orden de compra LIMIT parcialmente ejecutada y luego cancelada**: deja de reservar créditos; la parte ejecutada queda en sus operaciones.
- **Redondeo**: los precios y los totales se guardan con 4 decimales; el total de una operación es exactamente cantidad × precio, sin redondeo intermedio.
- **Auditoría de una creación**: el estado anterior es vacío. **Auditoría de una cancelación**: el estado posterior refleja el estado final, nunca se omite.
- **Contraseña del superusuario**: nunca aparece en la auditoría ni en los logs, ni en texto plano ni como hash.

## Requirements *(mandatory)*

### Functional Requirements

**Superusuario**

- **FR-001**: El sistema MUST garantizar al arrancar que exista exactamente un superusuario, identificado por el nombre de usuario configurado, con rol ADMIN.
- **FR-002**: El nombre de usuario, el email y la contraseña del superusuario MUST ser configurables por entorno y MUST tener valores por defecto en el código, sin agregar claves a `application.properties`.
- **FR-003**: Si el superusuario ya existe, el sistema MUST NOT crearlo otra vez ni modificar sus datos.
- **FR-004**: Si existe un usuario con el nombre de usuario configurado que no tiene rol ADMIN, o si el email configurado ya pertenece a otro usuario, el arranque MUST fallar con un mensaje que lo explique y MUST NOT modificar ningún usuario.
- **FR-005**: La contraseña del superusuario MUST guardarse con el mismo hash que usan los usuarios registrados.

**Emisión de tokens**

- **FR-006**: Cada jugador del catálogo MUST tener 100 tokens emitidos a nombre del superusuario. La emisión MUST hacerse al arrancar y al terminar cada recálculo de cotizaciones (`QuoteRunCompletedEvent`), para los jugadores que todavía no los tengan. La sincronización del catálogo no se modifica.
- **FR-007**: La emisión MUST ser idempotente: un jugador se emite una sola vez en toda su vida, aunque se repitan el arranque o el recálculo y aunque el superusuario ya haya vendido sus tokens.
- **FR-008**: Para cada jugador, la suma de los tokens de todas las tenencias MUST ser siempre 100 una vez emitido.
- **FR-009**: Una falla al emitir un jugador MUST NOT impedir la emisión de los demás ni cambiar el resultado del recálculo; el jugador pendiente MUST emitirse en el próximo arranque o recálculo.

**Tenencias**

- **FR-010**: Una tenencia MUST registrar usuario, jugador, cantidad de tokens y cantidad reservada por órdenes de venta abiertas. MUST existir como máximo una tenencia por usuario y jugador.
- **FR-011**: La cantidad y la reserva de una tenencia MUST ser enteros no negativos y la reserva MUST NOT superar la cantidad. Los tokens disponibles son la cantidad menos la reserva.

**Órdenes**

- **FR-012**: Una orden MUST registrar usuario, jugador, lado (compra o venta), tipo (MARKET o LIMIT), precio límite, cantidad, cantidad ejecutada, estado (abierta, parcialmente ejecutada, ejecutada, cancelada), fecha de creación, fecha de última actualización y fecha de cierre.
- **FR-013**: Una orden LIMIT MUST tener precio límite positivo con 4 decimales; una orden MARKET MUST NOT tener precio límite.
- **FR-014**: La cantidad de una orden MUST ser un entero positivo, y la cantidad ejecutada MUST estar entre 0 y la cantidad.
- **FR-015**: El estado de una orden MUST ser coherente con su ejecución: abierta con 0 ejecutados, parcialmente ejecutada con algo ejecutado y algo pendiente, ejecutada con todo ejecutado. Una orden ejecutada o cancelada MUST tener fecha de cierre y MUST NOT volver a abrirse.

**Créditos reservados**

- **FR-016**: El sistema MUST poder calcular los créditos reservados de un usuario como la suma, sobre sus órdenes de compra LIMIT abiertas o parcialmente ejecutadas, de la cantidad pendiente por el precio límite.
- **FR-017**: El cálculo de créditos reservados MUST NOT requerir cambios en el usuario ni en su saldo: el saldo sigue siendo el total de créditos del usuario y lo disponible para comprar es el saldo menos los reservados.

**Operaciones ejecutadas**

- **FR-018**: Una operación ejecutada MUST registrar jugador, comprador, vendedor, cantidad, precio, total y fecha de ejecución, y MUST referenciar la orden de compra y la orden de venta que la originaron.
- **FR-019**: El total MUST ser exactamente cantidad × precio con 4 decimales. Comprador y vendedor MUST ser distintos, y cantidad y precio MUST ser positivos.
- **FR-020**: Una operación ejecutada MUST ser inmutable: el sistema MUST rechazar cualquier modificación o borrado hecho por la aplicación, incluidas las modificaciones JPQL masivas. Los `DELETE` JPQL masivos y el SQL nativo quedan fuera de esta garantía (ver Assumptions).

**Auditoría**

- **FR-021**: Una entrada de auditoría MUST registrar autor (id de usuario, o sistema cuando la acción no la inicia un usuario), acción, tipo de entidad, id de entidad, estado anterior, estado posterior, fecha y hora, y correlation id.
- **FR-022**: La auditoría MUST ser inmutable: el sistema MUST rechazar cualquier modificación o borrado de una entrada hecho por la aplicación, con el mismo alcance que FR-020.
- **FR-023**: El sistema MUST ofrecer una forma única de registrar entradas de auditoría que tome el correlation id del pedido en curso, o genere uno para los procesos sin pedido (arranque, recálculo programado).
- **FR-024**: La creación del superusuario y cada emisión de tokens MUST dejar su entrada de auditoría. Los estados MUST NOT incluir contraseñas, hashes ni API keys.

**Índices**

- **FR-025**: El esquema MUST tener índices para: el libro (jugador, lado, estado, precio y fecha de creación); las órdenes por usuario y fecha de creación; las operaciones por comprador y fecha, por vendedor y fecha y por jugador y fecha; las tenencias por usuario (además de la unicidad por usuario y jugador); y la auditoría por entidad (tipo e id) y por fecha.

**Restricciones**

- **FR-026**: Esta feature MUST NOT exponer endpoints ni implementar el calce de órdenes.
- **FR-027**: Esta feature MUST NOT modificar `User`, `UserRepository`, `application.properties` ni `PlayerCatalogService` sin autorización del equipo.

**Saldo**

- **FR-028**: El saldo de un usuario sólo lo modifica el motor de calce, con la fila del usuario bloqueada, y cada cambio MUST auditarse como `BALANCE_CHANGED` en la misma transacción. Esta feature sólo provee el acceso al saldo con lock; no modifica saldos.

### Key Entities

- **Superusuario**: un usuario existente del sistema con rol ADMIN, identificado por el nombre de usuario configurado. Es el dueño inicial de todos los tokens y el creador de mercado del lado de la venta.
- **Tenencia**: los tokens de un jugador que tiene un usuario. Atributos: usuario, jugador, cantidad, cantidad reservada, fechas de creación y actualización. Única por usuario y jugador.
- **Orden**: una intención de compra o venta de tokens de un jugador. Atributos: usuario, jugador, lado, tipo, precio límite (sólo LIMIT), cantidad, cantidad ejecutada, estado, fechas de creación, actualización y cierre. Las LIMIT abiertas forman el libro.
- **Operación ejecutada**: un calce entre una orden de compra y una de venta. Atributos: jugador, comprador, vendedor, orden de compra, orden de venta, cantidad, precio, total, fecha de ejecución. Inmutable.
- **Entrada de auditoría**: el registro de un cambio de estado financiero. Atributos: autor, acción, tipo e id de entidad, estado anterior, estado posterior, fecha y hora, correlation id. Inmutable.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Tras 5 ejecuciones de la inicialización sobre la misma base, hay exactamente 1 superusuario y, para el 100% de los jugadores del catálogo, la suma de tokens entre todas las tenencias es 100.
- **SC-002**: El 100% de los jugadores sin tokens emitidos tienen sus 100 tokens al terminar el siguiente arranque o recálculo de cotizaciones.
- **SC-003**: El 100% de los intentos de modificar o borrar una operación ejecutada o una entrada de auditoría se rechazan y los datos quedan intactos.
- **SC-004**: El 100% de los intentos de dejar una tenencia en negativo, con reserva mayor que la cantidad, o una orden con datos incoherentes, se rechazan.
- **SC-005**: El cálculo de créditos reservados coincide con el cálculo manual en el 100% de los casos de prueba, incluidos usuarios sin órdenes y con órdenes en todos los estados.
- **SC-006**: Con 10.000 órdenes abiertas repartidas entre 500 jugadores, consultar el libro de un jugador y lado tarda menos de 100 ms y no recorre toda la tabla.
- **SC-007**: Ningún registro de auditoría ni log contiene la contraseña del superusuario ni su hash.

## Assumptions

- **Precisión de créditos**: precios, totales y créditos reservados usan 4 decimales, como `Player.marketValue`. Hoy `User.balance` no declara precisión y se guarda con 2 decimales; acreditar o debitar un total como 3.1311 lo redondearía. Esta feature no mueve saldos, así que no lo resuelve, pero la feature del motor de calce MUST decidirlo con el equipo antes de debitar o acreditar saldos (cambiar la escala de `User.balance` toca un archivo protegido).
- **Habilitación**: la creación del superusuario y la emisión de tokens se pueden deshabilitar con `market.superuser.enabled` (archivo `market.properties`); en los tests están deshabilitadas, salvo en los de la 026 que las habilitan.
- **Superusuario y otros ADMIN**: puede haber otros usuarios ADMIN; el superusuario es sólo el identificado por el nombre de usuario configurado.
- **Saldo del superusuario**: se crea con el saldo por defecto de cualquier usuario (1000 créditos). Como no compra, su saldo sólo crece con sus ventas.
- **Credenciales por defecto**: los valores por defecto son para desarrollo y demo; en un despliegue real se configuran por entorno. Si el sistema arranca con la contraseña por defecto, lo advierte en el log. Cambiar la configuración después de creado el superusuario no lo modifica.
- **Órdenes del superusuario**: esta feature sólo le emite las tenencias. Publicar su orden de venta a 1.00 y actualizar su precio tras cada recálculo es de la feature del motor de mercado.
- **Inmutabilidad**: se garantiza en la aplicación. Las entidades no permiten actualizarse, los repositorios no exponen borrado ni modificación, un borrado por entidad se rechaza y una modificación JPQL masiva falla. Quedan **fuera de la garantía** los `DELETE` JPQL masivos y el SQL nativo, que la aplicación no usa sobre estas tablas. No se agregan triggers de base de datos.
- **Estados en la auditoría**: se guardan como una representación textual estructurada de la entidad (por ejemplo JSON) antes y después del cambio.
- **Emisión de jugadores nuevos**: un jugador agregado por sincronización queda sin tokens hasta el siguiente recálculo de cotizaciones o el siguiente arranque.
