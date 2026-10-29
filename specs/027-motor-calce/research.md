# Research: Motor de calce y creador de mercado

Decisiones de diseño de la feature 027. Cada una indica qué se eligió, por qué y qué alternativas se descartaron. Se apoya en el modelo de la 026 (`MarketOrder`, `Holding`, `Trade`, `AuditLog`, `TransactionAuditService`, `MarketOrderRepository` con el libro ordenado y sus variantes `PESSIMISTIC_WRITE`).

## D1 — El calce se hace al crear la orden y al precio de la orden que ya estaba en el libro

- **Decision**:
  - **Cuándo**: la orden se calza dentro del mismo pedido `POST /orders/buy` o `POST /orders/sell` que la crea, en la misma transacción. No hay un proceso aparte que recorra el libro.
  - **A qué precio**: cada calce se ejecuta al precio límite de la orden que ya estaba en el libro (la "maker"), nunca al de la orden que llega (la "taker"). Una MARKET no tiene precio, así que siempre toma el del libro; una LIMIT que llega pone un tope, pero paga o cobra el precio del libro.
- **Rationale**:
  - **Calce al crear**:
    - El libro nunca queda cruzado entre usuarios distintos: si una orden que llega puede calzarse, se calza antes de que alguien más mire el libro. Un proceso periódico dejaría ventanas donde una compra a 1.30 y una venta a 1.20 conviven sin ejecutarse.
    - El usuario ve el resultado en la respuesta (SC-007): qué se ejecutó, a qué precio y qué quedó en el libro. Con un proceso asíncrono la respuesta sólo diría "recibida" y el resultado habría que consultarlo después.
    - Una MARKET necesita calce inmediato por definición: lo que no llena se cancela en ese momento. Con un proceso periódico no habría un "momento" en el que decidir qué cancelar.
    - Es más simple: no hay scheduler, ni estados intermedios "pendiente de calce", ni carreras entre el proceso y las cancelaciones.
    - El enunciado pide que comprar "usa la cotización vigente": en el momento de crear la orden, la cotización vigente es el precio del creador de mercado en el libro.
  - **Precio de la orden existente**:
    - Es la regla estándar de los libros de órdenes (prioridad precio-tiempo). Quien dejó su orden en el libro fijó un precio y lo respeta; quien llega acepta ese precio porque es igual o mejor que su tope.
    - Nadie paga más ni cobra menos de lo que pidió: el comprador con tope 1.30 que encuentra una venta a 1.20 paga 1.20, que es mejor para él; el vendedor que dejó su venta a 1.20 cobra exactamente 1.20.
    - Es determinístico: el precio sale de una sola orden, no de un promedio ni del punto medio, y se puede verificar a mano en los tests (SC-001).
    - Hace que el creador de mercado funcione como se pidió: el superusuario publica a marketValue, y todo el que le compra paga marketValue, aunque haya mandado una LIMIT más alta. Así la cotización vigente es el precio real de las compras al superusuario.
    - No requiere precio para la MARKET, que no lo tiene.
- **Alternatives considered**:
  - **Calce periódico** (por ejemplo, cada minuto, o un "batch auction"): da libro cruzado entre corridas, respuestas sin resultado y más estados; descartado.
  - **Calce asíncrono por evento después de confirmar la orden**: misma desventaja en la respuesta y agrega una segunda transacción que puede fallar después de que la orden ya se aceptó.
  - **Precio de la orden que llega**: el que llega con LIMIT 1.30 pagaría 1.30 aunque el superusuario venda a 1.20; el maker cobraría más de lo que pidió y el comprador menos favorecido. Para una MARKET no hay precio.
  - **Punto medio entre las dos órdenes**: no aplica a MARKET, genera precios con más decimales que los permitidos, y aleja el precio de compra al superusuario de marketValue.
  - **Siempre marketValue**: ignora los precios de las LIMIT de usuarios y hace que el libro no tenga sentido.

## D2 — Tres servicios: motor puro, órdenes y creador de mercado

- **Decision**:
  - `service/market/OrderMatchingEngineService`: calce puro. Recibe la orden que llega (lado, tipo, precio límite, cantidad, id de usuario) y la lista del lado opuesto **ya ordenada** por el repositorio, como records sin JPA (`BookEntry`). Devuelve un `MatchResult` con la lista de `Fill` (id de la orden del libro, cantidad, precio) en orden de ejecución y la cantidad pendiente. No toca la base, no tiene dependencias y no muta nada. También expone la regla de la banda (`isWithinBand(price, marketValue)`) para que se pruebe con el motor.
  - `service/market/OrderService`: el caso de uso transaccional. Valida, bloquea, llama al motor, aplica los efectos de cada `Fill` sobre las entidades (`MarketOrder.fill`, `Holding.credit/debit/consumeReserved/reserve`, saldos, `Trade.of`), audita, y cancela o deja en el libro el resto. También `cancel(orderId, user)` y `book(playerId)`.
  - `service/market/MarketMakerService`: la orden de venta del superusuario. Un método idempotente por jugador (`syncPlayer`) que se usa al arrancar y después de cada recálculo, cada uno en su propia transacción.
- **Rationale**: el pedido del equipo nombra el motor `OrderMatchingEngineService` (nombre del prompt maestro). Separar el calce de la persistencia permite probar la prioridad precio-tiempo, los parciales, el salteo de órdenes propias y la MARKET sin contraparte con tests unitarios rápidos, sin Spring ni base. `OrderService` queda con la parte que necesita transacción y locks.
- **Alternatives considered**:
  - Calce dentro de `OrderService` sobre entidades: obliga a probar el orden de calce con `@DataJpaTest`, más lento y mezclado con los locks.
  - Calce como métodos de `MarketOrder`: mezcla una regla del libro (varias órdenes) con la entidad de una orden.

## D3 — Locks y orden fijo para evitar sobreventa y deadlocks

- **Decision**: todas las operaciones que tocan el libro de un jugador toman los locks `PESSIMISTIC_WRITE` en este orden fijo:
  1. **El jugador** (fila de `players`), con un repositorio nuevo `MarketPlayerRepository.findLockedById` (`Repository<Player, Long>`, sin tocar `PlayerRepository`). Lo toman `OrderService.place*`, `OrderService.cancel` y `MarketMakerService.syncPlayer`.
  2. **Las órdenes activas del lado opuesto** del jugador, con `findOpenSellsForUpdate` / `findOpenBuysForUpdate` de la 026 (ya ordenadas por precio y antigüedad). En una cancelación, la orden a cancelar por id (`findByIdForUpdate`); el jugador de la orden se obtiene antes con una proyección (`select o.player.id from MarketOrder o where o.id = :id`), sin cargar la orden, y la orden se carga recién con `findByIdForUpdate`.
  3. **Los usuarios involucrados** (el que manda la orden y las contrapartes que devolvió el motor), con `MarketUserRepository.findLockedById`, **en orden ascendente de id**. Es el único lugar donde se carga la entidad `User` en la transacción: `MarketUserResolver` devuelve sólo id y username con una proyección.
  4. **Las tenencias** de esos usuarios para ese jugador, con `HoldingRepository.findForUpdate`, después de los usuarios. Su orden entre sí no importa porque el jugador está bloqueado: todo lo que toca las tenencias de ese jugador está serializado.
- **Rationale**:
  - El pedido indica locks sobre las órdenes del lado opuesto y sobre los usuarios en orden de id. Con eso solo quedan dos huecos, que cubre el lock del jugador:
    - **Libro vacío**: `SELECT ... FOR UPDATE` sobre cero filas no bloquea nada. Una compra y una venta simultáneas del mismo jugador (cada una bloqueando el lado opuesto, vacío) podrían quedar las dos en el libro cruzadas sin calzarse, porque ninguna ve la otra sin confirmar.
    - **Órdenes nuevas**: el lock de filas no impide que otra transacción inserte una orden nueva en el lado que se está leyendo.
  - Con el jugador bloqueado primero, todo el trabajo sobre el libro de un jugador queda serializado (FR-025), incluida la cancelación y el reprecio del superusuario (FR-027), y la marketValue leída para la banda es la confirmada en ese momento. Los locks de órdenes siguen siendo útiles como segunda barrera y son los que ya provee la 026.
  - Los usuarios en orden de id evitan deadlocks entre jugadores distintos: una compra de U1 a U2 en el jugador P1 y una de U2 a U1 en P2 bloquean `{U1, U2}` en el mismo orden. El lock del usuario que manda la orden serializa sus órdenes simultáneas sobre jugadores distintos, así el saldo disponible (saldo − créditos reservados) se lee y se usa sin que otra orden lo cambie en el medio (FR-026).
  - El orden jugador → órdenes → usuarios → tenencias es el mismo en todos los caminos, así no hay ciclos.
  - **Nada se carga antes de su lock**: si una entidad ya está en el contexto de persistencia, una consulta `PESSIMISTIC_WRITE` bloquea la fila pero Hibernate devuelve la instancia que ya tenía, con los datos leídos antes del lock. Un `User` cargado antes de `lockUsers` validaría con un saldo viejo y `setBalance` pisaría un cambio confirmado por otra orden; una orden cargada antes de `findByIdForUpdate` se podría cancelar ya llena. Por eso el usuario autenticado y el jugador de una cancelación se resuelven con proyecciones, y las entidades se cargan recién con su lock.
  - **Cómo saber las contrapartes antes de bloquearlas**: con el lado opuesto ya bloqueado (paso 2), el motor puro calcula los `Fill` sin tocar nada; de ahí salen los ids de las contrapartes. Recién entonces se bloquean los usuarios (paso 3) y se valida y aplica. Como el libro del jugador está bloqueado, el resultado del motor no cambia entre el cálculo y la aplicación.
- **Consecuencia aceptada**: el superusuario es contraparte de casi todas las compras de la etapa inicial, así que su fila de `users` queda bloqueada por cada compra, también entre jugadores distintos; esas compras se serializan. Con la escala del proyecto (decenas de usuarios) es aceptable. Si molestara, se podría acumular el saldo del superusuario fuera de la fila, pero no se hace ahora.
- **Alternatives considered**:
  - Sólo locks de órdenes y usuarios (sin jugador): deja los dos huecos de arriba.
  - Bloqueo optimista con reintentos: más código, reintentos visibles en la latencia y difícil de probar.
  - Un lock en memoria por jugador (`ConcurrentHashMap<Long, Lock>`): no funciona con más de una instancia y no cubre el recálculo en otra transacción.
  - `SERIALIZABLE`: reintentos por fallas de serialización en PostgreSQL y comportamiento distinto en H2.

## D4 — Validación de saldo: se simula el calce y se rechaza la orden entera

- **Decision**:
  - **Compra**: con el lado de venta bloqueado, el motor calcula los `Fill`. El costo es la suma de `cantidad × precio` de los fills más, si es LIMIT, `pendiente × precio límite` (lo que va a quedar reservado). Con el usuario bloqueado, el disponible es `balance − sumReservedCredits(userId)` (de la 026, sobre las compras LIMIT activas **antes** de crear esta). Si el costo supera el disponible → 422 `insufficient_balance` y no se persiste nada.
  - **Venta**: con la tenencia del usuario bloqueada, si la cantidad supera `available()` (o no tiene tenencia) → 422 `insufficient_tokens`.
  - Para una MARKET de compra, el costo es sólo el de los fills; lo que no se llena se cancela y no cuesta nada.
- **Rationale**: la spec (Edge Cases) pide rechazar la orden entera si no alcanza, no ejecutar "lo que alcance". Simular primero hace que la validación use los precios reales del libro, que pueden ser menores al tope de la LIMIT.
- **Alternatives considered**: validar con `cantidad × precio límite` (no sirve para MARKET y sobreestima para LIMIT); calzar y cortar cuando se acaba el saldo (lo descartó el equipo).

## D5 — Saldos con 4 decimales y cómo se modifican

- **Decision**:
  - `User.balance` pasa a `@Column(precision = 38, scale = 4)`. Es el único cambio en `User` (autorizado por el equipo el 2026-10-29). `ddl-auto=update` no cambia el tipo de una columna existente: las bases ya creadas ejecutan una vez `ALTER TABLE users ALTER COLUMN balance TYPE numeric(38,4);` (ver [quickstart.md](quickstart.md)). PostgreSQL conserva los valores (1000.00 → 1000.0000). Los perfiles `test` y `e2e` crean la tabla de cero.
  - El motor modifica el saldo sobre la entidad `User` **bloqueada** (`MarketUserRepository.findLockedById`) con su setter existente (`@Setter` de Lombok), y la auditoría guarda el saldo antes y después. No usa `MarketUserRepository.addToBalance`.
  - Se actualiza el javadoc de `MarketUserRepository.addToBalance`: ya no dice que la escala es 2 sino 4, y aclara que el motor de calce modifica el saldo sobre la entidad bloqueada (porque `addToBalance` vacía el contexto de persistencia).
- **Rationale**:
  - Precios y totales tienen 4 decimales (`Trade.total` es exactamente `price × quantity`); con saldos de 2 decimales se perdería plata en cada calce y la suma de saldos no se conservaría (SC-004).
  - `addToBalance` es `@Modifying(clearAutomatically = true)`: después de llamarlo, el contexto de persistencia se vacía y las órdenes, tenencias y el `User` cargados quedan desconectados. En medio de un calce con varias órdenes eso pierde cambios o fuerza a recargar todo. Modificar la entidad bloqueada se confirma con el resto al hacer flush.
  - Revisado: los tests existentes comparan saldos con `isEqualByComparingTo`, salvo `AuthControllerTest` (`jsonPath("$.balance").value(1000.00)`), que lee el saldo en memoria del usuario recién guardado y compara como número; no debería cambiar. Se corre igual toda la suite para confirmarlo.
- **Alternatives considered**: redondear cada movimiento a 2 decimales o limitar los precios a 2 decimales (opciones B y C de la spec, descartadas por el equipo); usar `addToBalance` al final del calce (deja entidades desconectadas para armar la respuesta).

## D6 — Auditoría de cada calce, creación y cancelación

- **Decision**:
  - `AuditAction` suma: `ORDER_CREATED`, `ORDER_FILLED`, `ORDER_CANCELLED`, `TRADE_EXECUTED`, `BALANCE_CHANGED`, `HOLDING_CHANGED`. El javadoc de `AuditAction` se actualiza.
  - Por cada `Fill` se registran, todas con el mismo correlation id del pedido:
    - `TRADE_EXECUTED` (antes nulo, después el trade).
    - `ORDER_FILLED` de la orden del libro y de la orden que llega (antes y después).
    - `BALANCE_CHANGED` del comprador y del vendedor (antes y después del saldo).
    - `HOLDING_CHANGED` del comprador (antes nulo si se crea la tenencia) y del vendedor.
  - Al crear la orden: `ORDER_CREATED`. Al dejar el resto en el libro (venta LIMIT): `HOLDING_CHANGED` por la reserva. Al cancelar (MARKET sin llenar, `DELETE`, reprecio): `ORDER_CANCELLED` y, si libera tokens, `HOLDING_CHANGED`.
  - **Autor**: el usuario que manda la orden o cancela (`AuditActor.user(id)`); en el reprecio y la publicación del creador de mercado, `AuditActor.system()`.
  - Snapshots nuevos (records, nunca entidades): `OrderSnapshot`, `TradeSnapshot`, `BalanceSnapshot(userId, balance)`. `HoldingSnapshot` ya existe. `BalanceSnapshot` no lleva email ni datos de cuenta.
  - Los créditos reservados de una compra LIMIT no se auditan como cambio de saldo porque el saldo no cambia; quedan reflejados en el snapshot de la orden (`limitPrice` y pendiente).
- **Rationale**: FR-014 y la constitución §4.2 piden estado anterior y posterior de saldos y tenencias por cada transacción financiera. `TransactionAuditService.audit` corre con `MANDATORY`, así cada entrada se confirma o se revierte con el calce (FR-012, FR-013).
- **Alternatives considered**: una sola entrada por calce con todo junto (más difícil de consultar por entidad con el índice `entity_type, entity_id`); auditar sólo el trade (no cumple "saldos y tenencias").

## D7 — Creador de mercado: un método idempotente por jugador

- **Decision**:
  - `MarketMakerService.syncPlayer(playerId)` en su propia transacción (`TransactionOperations`, igual que `TokenIssuanceService`):
    1. Bloquea el jugador (D3). Si no tiene marketValue, no hace nada.
    2. Bloquea la tenencia del superusuario. Si no existe o tiene 0 tokens, cancela su orden activa si queda alguna y termina.
    3. Busca las órdenes de venta activas del superusuario para ese jugador (bloqueadas; debería haber 0 o 1).
    4. Si hay exactamente una, con `limitPrice` igual a marketValue y pendiente igual a todos los tokens del superusuario, no hace nada (ni auditoría).
    5. Si no: cancela las activas (liberando su reserva en la tenencia) y crea una venta LIMIT nueva con todos los tokens a marketValue, reservándolos. Audita con autor sistema.
  - Recorre los jugadores en que el superusuario tiene tokens (`HoldingRepository` suma `findPlayerIdsWithTokens(userId)`), una transacción por jugador. Una falla en un jugador se loguea y sigue con los demás (FR-023).
  - **Disparadores**:
    - **Arranque**: `@EventListener(ApplicationReadyEvent.class)`. `ApplicationReadyEvent` se publica después de todos los `ApplicationRunner`, así que corre después de que `SuperuserInitializer` creó el superusuario y emitió los tokens, sin modificar `SuperuserInitializer`.
    - **Recálculo**: `@TransactionalEventListener(fallbackExecution = true)` sobre `QuoteRunCompletedEvent`, como pidió el equipo, con `@Order` posterior a la emisión. `QuoteCalculationService` publica el evento **fuera de una transacción** (en el `finally` de `recalculate`); sin `fallbackExecution = true` el listener no se ejecutaría nunca. Si en el futuro el evento se publicara dentro de una transacción, correría después del commit, que es lo correcto. Una transacción por jugador.
    - **Orden respecto de la emisión**: hoy `TokenIssuanceListener` no tiene `@Order` (equivale a `LOWEST_PRECEDENCE`). Se le agrega `@Order(MarketListenerOrder.TOKEN_ISSUANCE)` y el reprecio usa `@Order(MarketListenerOrder.MARKET_MAKER)`, mayor, así los jugadores emitidos en ese recálculo reciben su orden en el mismo recálculo (spec US7, escenario 3). `TokenIssuanceListener` es de la 026, no de Entrega 1.
  - Respeta `market.superuser.enabled`: deshabilitado, no hace nada. Si el superusuario no existe, loguea y no hace nada (mismo patrón que `TokenIssuanceListener`).
  - Correlation id: en el recálculo, el del MDC de la corrida (sigue puesto mientras se publica el evento); en el arranque, uno generado para toda la pasada, puesto en el MDC durante la pasada.
- **Rationale**: un único método idempotente cubre arranque, reprecio, jugadores nuevos, superusuario sin tokens y reintentos después de una falla. Comparar precio y cantidad evita rehacer y auditar sin cambios (spec, Edge Cases).
- **Alternatives considered**:
  - Modificar `SuperuserInitializer` para que llame al creador de mercado: funciona, pero `ApplicationReadyEvent` da el mismo orden sin tocarlo.
  - Actualizar el precio de la orden existente en lugar de cancelarla y crear otra: `limitPrice` es `updatable = false` en `MarketOrder` y la spec pide "se rehace".
  - Una sola transacción para todos los jugadores: una falla revertiría todos y bloquearía todos los jugadores mientras dura.

## D8 — Quién es el usuario de la orden

- **Decision**: el `principal` de la autenticación es el username, tanto con JWT como con API key de un usuario (`JwtAuthFilter`, `ApiKeyAuthFilter`). `OrderController` lo toma de `Authentication#getName()` y `MarketUserResolver` obtiene sólo id y username con una proyección, sin cargar la entidad `User` (ver D3); la entidad se carga recién en `MarketLocks.lockUsers`. Si no hay usuario con ese nombre (una API key sin dueño, cuyo principal es el prefijo de la key) → 403 `user_required`. Si el usuario es el superusuario → 403 `superuser_not_allowed`.
- **Rationale**: el usuario de la orden nunca viaja en el pedido (spec, Assumptions). No hace falta tocar la seguridad.

## D9 — Errores en un manejador acotado

- **Decision**: `exception/MarketExceptionHandler`, `@RestControllerAdvice(assignableTypes = OrderController.class)` con `@Order(Ordered.HIGHEST_PRECEDENCE)`, como `QuoteExceptionHandler`, con el `ErrorResponse` existente. Excepciones nuevas en `exception/`:

  | Excepción | HTTP | `error` |
  |-----------|------|---------|
  | Bean Validation del body o del query (`MethodArgumentNotValidException`, `HandlerMethodValidationException`, `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException`) | 400 | `validation_error` |
  | Body ilegible o tipo inválido (`HttpMessageNotReadableException`) | 400 | `validation_error` |
  | `InvalidOrderException` (LIMIT sin precio, MARKET con precio) | 400 | `validation_error` |
  | `PriceOutOfBandException` | 400 | `price_out_of_band` |
  | `InsufficientBalanceException` | 422 | `insufficient_balance` |
  | `InsufficientTokensException` | 422 | `insufficient_tokens` |
  | `PlayerNotQuotedException` | 422 | `player_not_quoted` |
  | `PlayerNotFoundException` (existente) | 404 | `player_not_found` |
  | `OrderNotFoundException` | 404 | `order_not_found` |
  | `OrderAccessDeniedException` (orden ajena, superusuario, API key sin usuario) | 403 | `order_forbidden`, `superuser_not_allowed`, `user_required` |
  | `OrderClosedException` | 409 | `order_closed` |

  El 401 lo sigue dando la cadena de seguridad existente, sin cambios.
- **Rationale**: `GlobalExceptionHandler` es de Entrega 1 y no se toca; un manejador acotado no cambia la respuesta de ningún otro endpoint. Los códigos `error` distintos para 400 permiten distinguir validación de banda (FR-029).
- **Alternatives considered**: `ResponseStatusException` (no da el shape `{error, message}`); un `@ExceptionHandler` dentro del controller (mezcla).

## D10 — Libro agregado por precio

- **Decision**: `GET /orders/book?playerId=` usa una consulta nueva en `MarketOrderRepository` que agrupa las LIMIT activas del jugador por lado y precio y suma `quantity − filledQuantity`, en una transacción de sólo lectura y sin locks. Las ventas se ordenan de menor a mayor precio, las compras de mayor a menor. Se responde la marketValue del jugador y nunca los dueños ni los ids de las órdenes.
- **Rationale**: la agregación en la base usa el índice `idx_orders_book` de la 026 y no trae las órdenes a memoria. Sin locks porque es una vista, y un valor apenas desactualizado no rompe nada.
- **Alternatives considered**: traer las órdenes con `findOpenSells`/`findOpenBuys` y agrupar en Java (más filas y más memoria, igual de correcto); cachear en Redis (el libro cambia en cada orden; invalidar sería más costoso que consultarlo).

## D11 — Validación de cantidad entera y precio con 4 decimales

- **Decision**:
  - `OrderRequest(Long playerId, Integer quantity, OrderType type, BigDecimal limitPrice)` con `@NotNull`, `@Positive` en `quantity` y `@Digits(integer = 8, fraction = 4)` + `@Positive` en `limitPrice` (la columna es `numeric(12,4)`). La coherencia tipo / precio (LIMIT con precio, MARKET sin precio) la valida `OrderService` y responde 400 `validation_error`.
  - **Cantidad no entera** (`1.5`): Jackson puede convertir un número con decimales a `Integer` truncando, según `DeserializationFeature.ACCEPT_FLOAT_AS_INT`. Al implementar, se verifica con un test MockMvc que `"quantity": 1.5` responda 400. Si Jackson lo acepta, el campo usa un deserializador propio (`@JsonDeserialize`) que rechaza decimales, acotado a este DTO, sin cambiar la configuración global de Jackson.
- **Rationale**: FR-002. Un precio con más de 4 decimales se rechaza en vez de redondearse (`MarketOrder.limit` haría `setScale(4, HALF_EVEN)` sin avisar).

## D12 — Logs estructurados

- **Decision**: logs `key=value` como el resto del proyecto, con el correlation id que ya pone `CorrelationIdFilter` en el MDC:
  - `market_order_placed orderId= userId= playerId= side= type= quantity= filled= status=`
  - `market_trade_executed tradeId= playerId= buyerId= sellerId= quantity= price=`
  - `market_order_rejected userId= playerId= side= reason=` (en el manejador, sin datos de credenciales)
  - `market_order_cancelled orderId= userId= releasedCredits= releasedTokens=`
  - `market_maker_repriced playerId= orderId= quantity= price=`, `market_maker_sync_failed playerId= errorType=`, `market_maker_sync_completed updated= unchanged= failed=`
- **Rationale**: constitución §5.3 y FR-030.

## D13 — Estrategia de tests

- **Decision**:
  - **Unitarios del motor** (`OrderMatchingEngineServiceTest`, sin Spring): precio de la orden existente (LIMIT que llega con tope mayor paga el del libro), prioridad precio-tiempo (mismo precio, la más vieja primero; mejor precio antes), parcial (llena una y deja la otra parcial; queda pendiente), MARKET sin contraparte (0 fills, todo pendiente), LIMIT que no cruza, salteo de órdenes propias, banda ±20% (0.8000 y 1.2000 aceptados con marketValue 1.0000; 0.7999 y 1.2001 rechazados).
  - **Servicio** (`OrderServiceTest`, `@SpringBootTest` + `@UnitTestProfile` sobre H2): efectos de cada calce en saldos, tenencias, órdenes, trades y auditoría; reservas; cancelación; rechazo sin rastros (SC-005); conservación de saldos (SC-004) y de 100 tokens (SC-002).
  - **MockMvc** (`OrderControllerTest`, `@SpringBootTest` + `@AutoConfigureMockMvc` + `@UnitTestProfile`, como `PlayerDetailControllerTest`): 201 de compra y venta, y cada código de error: 400 validación (cantidad 0, negativa, 1.5, faltante; LIMIT sin precio; MARKET con precio; precio con 5 decimales; sin `playerId` en el libro), 400 fuera de banda, 422 saldo, 422 tokens, 404 jugador, 404 orden, 403 orden ajena, 403 superusuario, 409 orden cerrada, 401 sin credenciales, con el shape `{error, message}`.
  - **Creador de mercado** (`MarketMakerServiceTest`, `@SpringBootTest` + `@UnitTestProfile` con `market.superuser.enabled=true`): orden al arrancar; reprecio después de un recálculo real (`QuoteCalculationService.recalculate`) que cambia marketValue, con los tokens restantes; sin cambios no rehace ni audita; sin tokens no crea; jugador nuevo emitido en el mismo recálculo recibe orden; falla en un jugador no frena los demás.
  - **Escenario §3.3** (`MarketSimulationTest`, `@SpringBootTest` + `@UnitTestProfile`): 4 usuarios operando sobre 5 jugadores con compras al superusuario, ventas LIMIT entre usuarios, calces parciales y cancelaciones; al final se verifican tenencias, saldos, trades y reservas contra un cálculo manual. La valuación del portfolio a marketValue la agrega la feature de portfolio sobre este mismo escenario.
  - **OpenAPI** (`OrderOpenApiTest`, como `QuoteOpenApiTest`): los cuatro endpoints con `bearerAuth` y `apiKeyAuth` y sus códigos de respuesta.
  - **E2E de concurrencia** (`OrderConcurrencyE2ETest`, `@E2ETest` contra PostgreSQL): varios hilos (por ejemplo 50) con usuarios distintos comprando a la vez el mismo jugador, arrancando juntos con un `CountDownLatch`; al final, nunca más de 100 tokens en total por jugador, la suma de tokens ejecutados igual a los ofrecidos, ningún saldo ni tenencia negativos, y la suma de saldos conservada. Un segundo caso: un mismo usuario con saldo justo mandando compras simultáneas en jugadores distintos; sólo las que alcanzan se aceptan. Se repite en un bucle (SC-003 pide 10 de 10).
- **Rationale**: los locks reales y su comportamiento sólo se prueban en PostgreSQL; H2 alcanza para la lógica.
