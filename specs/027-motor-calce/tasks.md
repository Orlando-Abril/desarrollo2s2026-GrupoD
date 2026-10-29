---

description: "Tareas de implementación de la feature 027: motor de calce y creador de mercado"
---

# Tasks: Motor de calce y creador de mercado

**Input**: Design documents from `specs/027-motor-calce/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/orders-api.md](contracts/orders-api.md), [quickstart.md](quickstart.md)

**Tests**: pedidos explícitamente en el plan (unitarios del motor, MockMvc de cada código de error, reprecio después de un recálculo, `@E2ETest` de concurrencia) y en research D13. Se escriben antes de la implementación de cada historia y tienen que fallar primero.

**Organization**: tareas agrupadas por historia de usuario de la spec. Las P1 van primero (US1, US2, US3, US4, US7, US8) y después las P2 (US5, US6).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: se puede hacer en paralelo (archivos distintos, sin dependencias pendientes).
- **[Story]**: historia de la spec (US1…US8).

## Convenciones

- `MAIN` = `backend/src/main/java/com/example/demo`
- `TEST` = `backend/src/test/java/com/example/demo`
- Comandos desde `backend/` con `.\mvnw.cmd` (Windows) o `./mvnw`.
- **Archivos protegidos de Entrega 1**: `User`, `UserRepository`, `application.properties`, `PlayerCatalogService`, `GlobalExceptionHandler`, `PlayerRepository`, `SecurityConfig`. Único cambio autorizado: la anotación de `User.balance` (T001). `UserRepository` sólo se usa para leer (`findByUsername`). Si alguna tarea parece necesitar otro cambio en esos archivos, **detenerse y consultar al equipo** con archivo, cambio mínimo y alternativa.
- No hacer commits: el equipo hace todos los commits.
- Logs con el estilo `evento key=value` del proyecto (research D12), nunca con credenciales.
- Montos y precios con `BigDecimal` escala 4; comparar en tests con `isEqualByComparingTo`.

---

## Phase 1: Setup

**Purpose**: saldo a 4 decimales y documentación de la migración.

- [X] T001 En `MAIN/model/User.java`, agregar sólo `@Column(precision = 38, scale = 4)` sobre el campo `balance` (queda `@Builder.Default` y `new BigDecimal("1000.00")` como están). Ningún otro cambio en el archivo (FR-035, research D5).
- [X] T002 [P] En `MAIN/repository/market/MarketUserRepository.java`, reemplazar en el javadoc de `addToBalance` la oración "Hoy {@code users.balance} tiene escala 2: un {@code delta} con más decimales se redondea en la base." por una que diga que `users.balance` tiene escala 4 (spec 027) y que el motor de calce modifica el saldo sobre la entidad bloqueada por `findLockedById` porque `addToBalance` vacía el contexto de persistencia (`clearAutomatically`). Sin cambios de código (FR-034).
- [X] T003 Correr `.\mvnw.cmd test "-DexcludedGroups=e2e"` y confirmar que los tests existentes siguen en verde con la escala 4 (en especial `AuthControllerTest`, `UserTest`, `UserRepositoryTest`, `MarketUserRepositoryTest`, `AuthServiceTest`). Si alguno compara un saldo leído de la base con `equals` de escala 2, cambiarlo a `isEqualByComparingTo` sin cambiar el valor esperado, y anotarlo en el reporte final (research D5).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: tipos, motor puro, locks, errores y DTOs que usan todas las historias.

**⚠️ CRITICAL**: ninguna historia empieza hasta terminar esta fase.

### Auditoría y snapshots

- [X] T004 En `MAIN/model/market/AuditAction.java`, agregar después de `TOKENS_ISSUED`: `ORDER_CREATED`, `ORDER_FILLED`, `ORDER_CANCELLED`, `TRADE_EXECUTED`, `BALANCE_CHANGED`, `HOLDING_CHANGED`. Actualizar el javadoc: ya no "las features siguientes agregan", sino qué registra cada grupo (026: superusuario y emisión; 027: órdenes, operaciones, saldos y tenencias). La columna `action` es `length = 32`: todos entran (research D6).
- [X] T005 [P] Crear `MAIN/service/market/OrderSnapshot.java`: `record OrderSnapshot(Long id, Long userId, Long playerId, OrderSide side, OrderType type, BigDecimal limitPrice, int quantity, int filledQuantity, OrderStatus status, Instant createdAt, Instant closedAt)` con `static OrderSnapshot of(MarketOrder order)`, usando `getUser().getId()` y `getPlayer().getId()` (no inicializan el proxy). Mismo estilo de javadoc que `HoldingSnapshot`.
- [X] T006 [P] Crear `MAIN/service/market/TradeSnapshot.java`: `record TradeSnapshot(Long id, Long playerId, Long buyerId, Long sellerId, Long buyOrderId, Long sellOrderId, int quantity, BigDecimal price, BigDecimal total, Instant executedAt)` con `static TradeSnapshot of(Trade trade)`.
- [X] T007 [P] Crear `MAIN/service/market/BalanceSnapshot.java`: `record BalanceSnapshot(Long userId, BigDecimal balance)` con `static BalanceSnapshot of(User user)`. Javadoc: nunca incluye email, hash ni API key (FR-024 de la 026).

### Motor de calce puro

- [X] T008 [P] Crear los records del motor en `MAIN/service/market/` (sin JPA ni Spring; data-model "Tipos del motor puro"):
  - `IncomingOrder.java`: `record IncomingOrder(Long userId, OrderSide side, OrderType type, BigDecimal limitPrice, int quantity)`; el constructor compacto valida `quantity > 0`, `limitPrice` no nulo y positivo si `type == LIMIT` y nulo si `type == MARKET` (`IllegalArgumentException`).
  - `BookEntry.java`: `record BookEntry(Long orderId, Long userId, BigDecimal limitPrice, int remaining)`, con `static BookEntry of(MarketOrder order)` (`order.remaining()`).
  - `Fill.java`: `record Fill(Long bookOrderId, Long counterpartyUserId, int quantity, BigDecimal price)`.
  - `MatchResult.java`: `record MatchResult(List<Fill> fills, int remaining)` con `List.copyOf` y helpers `filledQuantity()` y `total()` (suma de `price × quantity`, escala 4).
- [X] T009 [P] Crear `TEST/service/market/OrderMatchingEngineServiceTest.java` (JUnit 5 + AssertJ, sin Spring; instanciar `new OrderMatchingEngineService()`). Casos, cada uno con su `@Test` y nombre descriptivo:
  - **Precio de la orden existente**: compra LIMIT tope 1.3000 contra venta a 1.2000 → un fill a 1.2000; venta LIMIT tope 1.0000 contra compra a 1.1500 → fill a 1.1500 (FR-009).
  - **Prioridad precio-tiempo**: ventas `[C 3 @1.1000, D 3 @1.1000, SU 50 @1.2000]` (en ese orden de libro) y compra MARKET de 8 → fills `C 3 @1.1000`, `D 3 @1.1000`, `SU 2 @1.2000`, en ese orden (US3-1).
  - **Mejor precio primero en ventas MARKET**: compras `[1.1500, 1.0000]` → primero 1.1500 (US3-2).
  - **LIMIT que no cruza**: compra tope 1.1000 con ventas `[1.1000 ×2, 1.2000 ×5]` y cantidad 5 → un fill de 2 y `remaining` 3; se detiene en 1.2000 (US3-3).
  - **Parcial**: LIMIT de 10 contra 4 ofrecidos → un fill de 4, `remaining` 6 (US1-4); y una orden del libro con 10 contra compra de 3 → fill de 3 (el `BookEntry` queda con 7 pendientes, lo aplica el servicio).
  - **MARKET sin contraparte**: libro vacío → 0 fills, `remaining` = cantidad (US2-4).
  - **MARKET con poco libro**: 4 ofrecidos, compra 10 → fill 4, `remaining` 6 (US1-5).
  - **Salteo de órdenes propias**: libro `[propia 5 @1.1000, otra 5 @1.2000]` y compra MARKET 3 del mismo usuario → un fill de 3 con la otra a 1.2000 (US3-5).
  - **Banda ±20%** con marketValue 1.0000: `isWithinBand` true para 0.8000, 1.0000 y 1.2000; false para 0.7999 y 1.2001; y con marketValue 1.2345: límites 0.98760 y 1.48140 exactos (US4-3, FR-003).
  - **No muta la entrada**: la lista de `BookEntry` pasada queda igual.
- [X] T010 Crear `MAIN/service/market/OrderMatchingEngineService.java` (`@Service`, sin dependencias, sin estado) con:
  - `MatchResult match(IncomingOrder incoming, List<BookEntry> oppositeBook)`: recorre el libro en el orden recibido; saltea las entradas con `userId` igual a `incoming.userId()`; para LIMIT de compra se detiene en la primera entrada con `limitPrice > tope`, para LIMIT de venta en la primera con `limitPrice < tope`; MARKET no se detiene por precio; cada fill es `min(pendiente, entry.remaining())` al `entry.limitPrice()` (research D1, D2).
  - `boolean isWithinBand(BigDecimal price, BigDecimal marketValue)`: `marketValue × 0.80 ≤ price ≤ marketValue × 1.20`, con `compareTo` exacto (sin redondeo).
  - Constantes `BAND_LOWER = new BigDecimal("0.80")` y `BAND_UPPER = new BigDecimal("1.20")`.
  - Javadoc que explique por qué el precio es el de la orden del libro y que el libro llega ya ordenado por `MarketOrderRepository`. Hace pasar T009.

### Locks y repositorios

- [X] T011 [P] Crear `MAIN/repository/market/MarketPlayerRepository.java`: `interface MarketPlayerRepository extends Repository<Player, Long>` con `@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from Player p where p.id = :id") Optional<Player> findLockedById(@Param("id") Long id)`. Javadoc: existe para no tocar `PlayerRepository` (Entrega 1) y es el primer lock de todo trabajo sobre el libro de un jugador (research D3).
- [X] T012 [P] En `MAIN/repository/market/HoldingRepository.java`, agregar `@Query("select h.player.id from Holding h where h.user.id = :userId and h.quantity > 0 order by h.player.id") List<Long> findPlayerIdsWithTokens(@Param("userId") Long userId)` (research D7).
- [X] T013 [P] En `MAIN/repository/market/MarketOrderRepository.java`, agregar `@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from MarketOrder o where o.user.id = :userId and o.player.id = :playerId and o.side = :side and o.status in :active order by o.id") List<MarketOrder> lockActiveByUserAndPlayerAndSide(...)` y el `default` `findActiveSellsForUpdate(Long userId, Long playerId)` que lo llama con `OrderSide.SELL` y `OrderStatus.ACTIVE` (research D7).
- [X] T014 Crear `MAIN/service/market/MarketLocks.java` (`@Component`) que centraliza el orden fijo de locks de research D3, para que lo usen `OrderService` y `MarketMakerService`:
  - `Player lockPlayer(Long playerId)`: `MarketPlayerRepository.findLockedById`, o `PlayerNotFoundException` si no existe.
  - `List<MarketOrder> lockOppositeBook(Long playerId, OrderSide incomingSide)`: `findOpenSellsForUpdate` para una compra, `findOpenBuysForUpdate` para una venta.
  - `Map<Long, User> lockUsers(Collection<Long> userIds)`: bloquea con `MarketUserRepository.findLockedById` **en orden ascendente de id** (ordenar y deduplicar antes) y devuelve un `LinkedHashMap`. Es el único lugar donde se carga la entidad `User` en la transacción: ni `MarketUserResolver` (T020) ni otro paso previo la cargan, porque Hibernate devolvería la instancia ya cargada con el saldo leído antes del lock (research D3).
  - `Optional<Holding> lockHolding(Long userId, Long playerId)`: `HoldingRepository.findForUpdate`.
  - Javadoc con el orden obligatorio: jugador → órdenes → usuarios por id → tenencias, y la regla de no cargar ninguna entidad antes de su lock.

### Errores

- [X] T015 [P] Crear las excepciones en `MAIN/exception/` (todas `RuntimeException`, mensaje en español para el usuario, mismo estilo que `QuoteNotFoundException`): `InvalidOrderException`, `PriceOutOfBandException` (incluye en el mensaje los límites de la banda), `InsufficientBalanceException`, `InsufficientTokensException`, `PlayerNotQuotedException`, `OrderNotFoundException`, `OrderClosedException`, y `OrderAccessDeniedException` con un campo `String code` (`order_forbidden`, `superuser_not_allowed`, `user_required`) y constantes para cada código. Reutilizar la `PlayerNotFoundException` existente (research D9).
- [X] T016 Crear `MAIN/controller/OrderController.java` vacío: `@RestController @RequestMapping("/orders") @Validated @Tag(name = "Órdenes")`, con `@SecurityRequirement(name = "bearerAuth")` y `@SecurityRequirement(name = "apiKeyAuth")` a nivel de clase, como `PlayerController`, sin endpoints todavía. Existe para que `MarketExceptionHandler` se pueda acotar a él (research D9).
- [X] T017 Crear `MAIN/exception/MarketExceptionHandler.java`: `@RestControllerAdvice(assignableTypes = OrderController.class)` + `@Order(Ordered.HIGHEST_PRECEDENCE)`, con el `GlobalExceptionHandler.ErrorResponse` existente, siguiendo `QuoteExceptionHandler`. Mapeo exacto de la tabla de research D9:
  - 400 `validation_error`: `MethodArgumentNotValidException` (mensajes de los campos unidos con `"; "`), `HandlerMethodValidationException`, `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException`, `HttpMessageNotReadableException`, `InvalidOrderException`.
  - 400 `price_out_of_band`, 422 `insufficient_balance`, 422 `insufficient_tokens`, 422 `player_not_quoted`, 404 `player_not_found`, 404 `order_not_found`, 403 con `ex.getCode()`, 409 `order_closed`.
  - Cada rechazo loguea `market_order_rejected reason=<código>` en `INFO` (sin body ni credenciales) (research D12).
  - Depende de T015 y de T016 (`OrderController` vacío).

### DTOs

- [X] T018 [P] Crear `MAIN/dto/market/OrderRequest.java`: `record OrderRequest(@NotNull Long playerId, @NotNull @Positive Integer quantity, @NotNull OrderType type, @Positive @Digits(integer = 8, fraction = 4) BigDecimal limitPrice)` con `@Schema` en cada campo (ejemplos de [contracts/orders-api.md](contracts/orders-api.md); en `limitPrice`: "obligatorio en LIMIT, prohibido en MARKET; hasta 4 decimales").
- [X] T019 [P] Crear en `MAIN/dto/market/`: `OrderTradeResponse(Long id, int quantity, BigDecimal price, BigDecimal total, Instant executedAt)`; `OrderResponse(Long id, Long playerId, OrderSide side, OrderType type, BigDecimal limitPrice, int quantity, int filledQuantity, int remainingQuantity, OrderStatus status, BigDecimal averagePrice, Instant createdAt, Instant closedAt, List<OrderTradeResponse> trades)` con `static OrderResponse of(MarketOrder order, List<Trade> trades)`; `averagePrice` = suma de `total` / `filledQuantity` con escala 4 `HALF_EVEN`, nulo si `filledQuantity == 0` (sumando los trades de la orden que se pasan; el caso de `DELETE` lo resuelve T051); `BookLevelResponse(BigDecimal price, long quantity)`; `OrderBookResponse(Long playerId, BigDecimal marketValue, List<BookLevelResponse> sells, List<BookLevelResponse> buys)`. Todos con `@Schema` y ejemplos del contrato.

### Usuario autenticado

- [X] T020 Crear `MAIN/service/market/MarketUserResolver.java` (`@Component`) con `MarketPrincipal resolve(String principalName)`, donde `MarketPrincipal` es un record nuevo en el mismo paquete: `record MarketPrincipal(Long id, String username)`. Obtenerlo con una proyección, **sin cargar la entidad `User`**: agregar a `MAIN/repository/market/MarketUserRepository.java` `@Query("select new com.example.demo.service.market.MarketPrincipal(u.id, u.username) from User u where u.username = :username") Optional<MarketPrincipal> findPrincipalByUsername(@Param("username") String username)`. La entidad `User` se carga recién en `MarketLocks.lockUsers` (T014), con el lock; cargarla antes haría que el lock devuelva un saldo viejo (research D3). Si no existe → `OrderAccessDeniedException(user_required)`; si `principalName` es igual a `SuperuserAccount.username()` → `OrderAccessDeniedException(superuser_not_allowed)`. Comparar por username y no con `SuperuserAccount.id()`, para que funcione aunque el superusuario no exista (tests con `market.superuser.enabled=false`) (research D8).

### Helper de tests

- [X] T021 [P] En `TEST/service/market/MarketTestData.java` (helper de tests de la 026), hacer `public` la clase y sus métodos estáticos (`clean` y `player`), y cambiar `clean` para que borre en este orden: `trades`, `orders`, `holdings`, `audit_logs`, `api_keys`, `users` (así no falla por clave foránea cuando un test deja órdenes o trades). Lo usan los tests de servicio y de controller de esta feature.

**Checkpoint**: motor probado, locks, errores y DTOs listos.

---

## Phase 3: User Story 1 - Comprar tokens al creador de mercado (Priority: P1) 🎯 MVP

**Goal**: un usuario compra con MARKET o LIMIT contra las ventas del libro (en especial la del superusuario), con todos los efectos y la auditoría de cada calce.

**Independent Test**: con una venta LIMIT del superusuario de 100 tokens a 1.2000 sembrada en el test, un usuario con 1000 créditos compra MARKET y LIMIT; se verifican orden, trade, saldos, tenencias, reservas y auditoría.

### Tests for User Story 1 ⚠️

- [X] T022 [P] [US1] Crear `TEST/service/market/OrderServiceBuyTest.java` (`@SpringBootTest` + `@UnitTestProfile`, limpiar con `MarketTestData.clean` en `@BeforeEach`; sembrar superusuario ADMIN **con el username de `SuperuserAccount.username()`** (por defecto `superuser`; si no, `MarketUserResolver` no lo reconoce como superusuario), su tenencia de 100 con 100 reservados y su venta LIMIT de 100 a 1.2000 con repositorios, sin habilitar `market.superuser.enabled`; jugador con `marketValue` 1.2000). Casos de spec US1:
  - US1-1: MARKET 10 → `FILLED`, un trade 10 × 1.2000 = 12.0000, tenencia del comprador 10, saldo del comprador 988.0000, del superusuario 1012.0000, a la orden del superusuario le quedan 90 y su tenencia queda en 90 con 90 reservados.
  - US1-2: LIMIT 10 a 1.3000 → paga 12.0000 (precio 1.2000).
  - US1-3: LIMIT 10 a 1.1000 → `OPEN`, sin trades, saldo sin cambios, `sumReservedCredits` = 11.0000.
  - US1-4: superusuario con 4 → LIMIT 10 a 1.3000: 4 ejecutados, `PARTIALLY_FILLED`, reserva 7.8000.
  - US1-5: MARKET 10 con 4 en el libro → `CANCELLED` con 4 ejecutados y `closedAt`, reserva 0.
  - US1-6: por cada calce hay `TRADE_EXECUTED`, `ORDER_FILLED` ×2, `BALANCE_CHANGED` ×2 y `HOLDING_CHANGED` ×2 (la del comprador con `before_state` nulo al crearla), más `ORDER_CREATED` de la orden nueva; todas con el mismo `correlation_id` (poner un valor en el MDC con `CorrelationIdFilter.MDC_KEY` antes de llamar) y autor `USER` con el id del comprador.
  - Conservación: la suma de saldos de todos los usuarios no cambia y la suma de tenencias del jugador sigue en 100 (SC-002, SC-004).
  - Falla en el segundo calce (FR-013): con ventas de dos usuarios distintos en el libro, un `@MockitoSpyBean` de `TradeRepository` que lanza `RuntimeException` en el segundo `save`; una compra MARKET que barre las dos ventas lanza, y después no hay orden nueva, ni trades, ni auditoría, y los saldos, tenencias y órdenes del libro están como antes.
- [X] T023 [P] [US1] Crear `TEST/controller/OrderControllerBuyTest.java` (`@SpringBootTest` + `@AutoConfigureMockMvc` + `@UnitTestProfile`, como `PlayerDetailControllerTest`; limpiar en `@BeforeEach` con el mismo helper `MarketTestData.clean` (público desde T021); credenciales con `AuthFixtures.user` + `AuthFixtures.apiKey` y header `X-API-KEY`): `POST /orders/buy` MARKET 10 → 201 con el JSON del contrato (`status`, `filledQuantity`, `remainingQuantity`, `averagePrice` 1.2000, `trades[0].price` 1.2000, `trades[0].total` 12.0000); con JWT (`Authorization: Bearer`) también 201.

### Implementation for User Story 1

- [X] T024 [US1] Crear `MAIN/service/market/OrderService.java` (`@Service`) con `@Transactional public OrderResult placeBuy(String principalName, OrderRequest request)` y el pipeline común `place(MarketPrincipal principal, OrderSide side, OrderRequest request)` que reutilizará la venta (T032). Pasos, en este orden (research D3, D4):
  1. `MarketUserResolver.resolve` (T020): sólo id y username, sin cargar `User`.
  2. Coherencia tipo/precio: LIMIT sin `limitPrice` o MARKET con `limitPrice` → `InvalidOrderException`.
  3. `MarketLocks.lockPlayer`; `marketValue` nulo → `PlayerNotQuotedException`; LIMIT fuera de banda (`OrderMatchingEngineService.isWithinBand`) → `PriceOutOfBandException`.
  4. `MarketLocks.lockOppositeBook`, mapear a `BookEntry` y llamar a `match`.
  5. `MarketLocks.lockUsers` con el id del usuario y los `counterpartyUserId` de los fills. A partir de acá, el `User` del que manda la orden se toma del mapa bloqueado (también para crear la orden en el paso 7).
  6. Validación de compra: costo = `result.total()` + (LIMIT ? `remaining × limitPrice` : 0); disponible = `balance − MarketOrderRepository.sumReservedCredits(userId)`; si costo > disponible → `InsufficientBalanceException`.
  7. Crear y guardar la orden (`MarketOrder.limit` / `market`, `Instant.now(clock)`) y auditar `ORDER_CREATED` (antes nulo).
  8. Por cada `Fill`, en orden: aplicar el calce (T025).
  9. Resto: LIMIT queda activa (la reserva de créditos es implícita, research D6); MARKET con `remaining > 0` → `order.cancel(now)` y auditar `ORDER_CANCELLED`.
  10. Loguear `market_order_placed` (research D12) y devolver `OrderResult(MarketOrder order, List<Trade> trades)` (record nuevo en el mismo paquete).
  - Inyectar `Clock` (el bean existente de `ClockConfig`) y `TransactionAuditService`.
- [X] T025 [US1] En `OrderService`, implementar `applyFill(MarketOrder incoming, MarketOrder bookOrder, Fill fill, Map<Long, User> lockedUsers, Instant now)`, simétrico para compra y venta (lo reutiliza T032):
  - Determinar comprador/vendedor y orden de compra/venta según el lado de `incoming`.
  - Tenencias: bloquear con `MarketLocks.lockHolding` en orden ascendente de id de usuario. Vendedor: si su orden es la del libro → `consumeReserved(q)`; si es la que llega → `debit(q)`. Comprador: `credit(q)` o, si no tiene tenencia, `holdingRepository.save(Holding.issue(user, player, q, now))`.
  - Saldos sobre las entidades bloqueadas: `buyer.setBalance(buyer.getBalance().subtract(total))`, `seller.setBalance(seller.getBalance().add(total))`; nunca `addToBalance` (research D5).
  - `incoming.fill(q, now)`, `bookOrder.fill(q, now)`; `Trade.of(buyOrder, sellOrder, q, fill.price(), now)` guardado con `TradeRepository.save`.
  - Auditoría con `AuditActor.user(incoming.getUser().getId())`: `TRADE_EXECUTED` (`TRADE`), `ORDER_FILLED` de las dos órdenes (`ORDER`), `BALANCE_CHANGED` del comprador y del vendedor (`USER`, `BalanceSnapshot`), `HOLDING_CHANGED` de las dos tenencias (`HOLDING`; `before` nulo si se creó). Tomar los snapshots `before` antes de mutar.
  - Loguear `market_trade_executed` (research D12).
- [X] T026 [US1] En `MAIN/controller/OrderController.java` (creado vacío en T016), agregar `@PostMapping("/buy")` recibe `@Valid @RequestBody OrderRequest` y `Authentication authentication`, llama a `orderService.placeBuy(authentication.getName(), request)` y responde `ResponseEntity.status(201).body(OrderResponse.of(...))`. `@Operation` y `@ApiResponse` para 201, 400, 401, 403, 404 y 422 con `ErrorResponse` (contrato).
- [X] T027 [US1] Correr `OrderServiceBuyTest`, `OrderControllerBuyTest` y `OrderMatchingEngineServiceTest`; todo en verde.

**Checkpoint**: MVP: se puede comprar al creador de mercado.

---

## Phase 4: User Story 2 - Vender tokens a otros usuarios (Priority: P1)

**Goal**: un usuario vende con MARKET o LIMIT contra compras de otros usuarios, con reserva de tokens para lo que queda en el libro.

**Independent Test**: A con tokens y B con una compra LIMIT en el libro; A vende MARKET y LIMIT; se verifican calces, reservas y saldos.

### Tests for User Story 2 ⚠️

- [X] T028 [P] [US2] Crear `TEST/service/market/OrderServiceSellTest.java` (mismo setup que T022, sembrando la tenencia de A y la compra LIMIT de B con repositorios). Casos de spec US2:
  - US2-1: B compra LIMIT 5 a 1.1000; A (10 tokens) vende MARKET 5 → trade 5 × 1.1000, A +5.5000 y 5 tokens, B 5 tokens, `sumReservedCredits(B)` = 0.
  - US2-2: A vende LIMIT 6 a 1.2000 sin compradores → `OPEN`, tenencia de A con 6 reservados y `available()` 4; auditoría `HOLDING_CHANGED` por la reserva.
  - US2-3: A con 10 y 6 reservados vende 5 → `InsufficientTokensException` y nada cambia (ni órdenes, ni trades, ni auditoría).
  - US2-4: A vende MARKET 5 sin compradores → `CANCELLED` con 0 ejecutados; el superusuario no compra.
  - US2-5: con la venta LIMIT de A en el libro, B compra MARKET → B le compra a A al precio de A y la tenencia de A usa `consumeReserved`.
  - Usuario sin tenencia del jugador vende → `InsufficientTokensException`.
- [X] T029 [P] [US2] Crear `TEST/controller/OrderControllerSellTest.java` (setup de T023): `POST /orders/sell` LIMIT → 201 `OPEN`; MARKET contra una compra → 201 `FILLED`.

### Implementation for User Story 2

- [X] T030 [US2] En `MAIN/service/market/OrderService.java`, agregar `@Transactional public OrderResult placeSell(String principalName, OrderRequest request)` que usa el pipeline de T024 con `OrderSide.SELL`, y en el paso 6 la validación de venta: `MarketLocks.lockHolding(userId, playerId)`; si no existe o `quantity > holding.available()` → `InsufficientTokensException`. Las tenencias se bloquean después de los usuarios; su orden entre sí no importa porque el jugador está bloqueado (research D3).
- [X] T031 [US2] En `OrderService`, al terminar los fills de una venta LIMIT con `remaining > 0`: `holding.reserve(remaining, now)` y auditar `HOLDING_CHANGED`. Verificar que `applyFill` (T025) use `debit` para la venta que llega.
- [X] T032 [US2] En `MAIN/controller/OrderController.java`, agregar `@PostMapping("/sell")` igual que `/buy`, llamando a `placeSell`, con su `@Operation` y `@ApiResponse`.
- [X] T033 [US2] Correr `OrderServiceSellTest` y `OrderControllerSellTest`; en verde.

**Checkpoint**: compra y venta entre usuarios funcionan.

---

## Phase 5: User Story 3 - Prioridad del libro y calces parciales (Priority: P1)

**Goal**: el calce recorre el libro por mejor precio y antigüedad, con parciales que conservan la antigüedad de la orden del libro.

**Independent Test**: libro con varias ventas a distintos precios y a igual precio con distinta fecha; una compra que barre varias; se verifican orden y precio de cada trade persistido.

### Tests for User Story 3 ⚠️

- [X] T034 [P] [US3] Crear `TEST/service/market/OrderPriorityTest.java` (`@SpringBootTest` + `@UnitTestProfile`; fechas distintas sembrando con `Clock` o creando las órdenes en orden; mismo instante desempata por id, como en la 026):
  - US3-1: ventas de C (3 @1.1000, más vieja), D (3 @1.1000) y superusuario (50 @1.2000); compra MARKET 8 → tres `Trade` persistidos en ese orden y con esos precios (por id ascendente).
  - US3-2: compras a 1.0000 y 1.1500; venta MARKET 1 → se calza con la de 1.1500.
  - US3-3: compra LIMIT a 1.1000 sólo ejecuta contra ventas ≤ 1.1000; el resto queda `PARTIALLY_FILLED` en el libro.
  - US3-4: la orden del libro llenada del todo queda `FILLED` con `closedAt` y no aparece en `findOpenSells`; la parcial queda `PARTIALLY_FILLED` y conserva `createdAt`, y sigue primera a su precio.
  - US3-5: compra de un usuario con su propia venta en el libro → se saltea y se calza con la siguiente; ningún `Trade` con comprador = vendedor.

### Implementation for User Story 3

- [X] T035 [US3] Si algún caso de T034 falla, corregir `OrderMatchingEngineService` (T010) o `OrderService` (T024/T025) sin cambiar el orden del libro de `MarketOrderRepository` (que ya es el de la 026). Correr `OrderPriorityTest` en verde.

**Checkpoint**: prioridad precio-tiempo verificada de punta a punta.

---

## Phase 6: User Story 4 - Validaciones y errores (Priority: P1)

**Goal**: toda orden inválida se rechaza con el código y el shape correctos, sin dejar rastro.

**Independent Test**: MockMvc con cada dato inválido, sin credenciales, sin saldo y sin tokens; se verifican código, `{error, message}` y que no cambió nada.

### Tests for User Story 4 ⚠️

- [X] T036 [P] [US4] Crear `TEST/controller/OrderControllerErrorsTest.java` (setup de T023; el superusuario sembrado usa el username de `SuperuserAccount.username()`, por defecto `superuser`, para que el caso 403 `superuser_not_allowed` se produzca). Un `@Test` (o `@ParameterizedTest`) por fila, verificando `status`, `$.error` y que `$.message` no está vacío, y al final que `orders`, `trades` y `audit_logs` no tienen filas nuevas (contar con `JdbcTemplate` antes y después) (SC-005):
  - 400 `validation_error`: `quantity` 0, -1, `1.5` y faltante; sin `playerId`; sin `type`; `type` `"FOO"`; body `"{"` ilegible; LIMIT sin `limitPrice`; MARKET con `limitPrice`; `limitPrice` 0, negativo y `1.23456`.
  - 400 `price_out_of_band`: marketValue 1.0000, LIMIT a 0.7999 y a 1.2001; a 0.8000 y 1.2000 → 201.
  - 404 `player_not_found`: `playerId` inexistente.
  - 422 `insufficient_balance`: usuario con saldo 10.0000 compra MARKET 10 a 1.2000; y LIMIT que no cruza cuyo `quantity × limitPrice` supera el disponible (con otra LIMIT ya reservando).
  - 422 `insufficient_tokens`: venta sin tenencia.
  - 422 `player_not_quoted`: jugador con `marketValue` nulo (si `Player` lo permite; si la columna es no nula, documentar el caso con un test de servicio que use un mock).
  - 403 `superuser_not_allowed`: el superusuario (por username configurado) manda una compra.
  - 403 `user_required`: API key sin dueño (si `AuthFixtures` o `ApiKeyRepository` permiten crearla; si no, test de servicio de `MarketUserResolver`).
  - 401: sin header de credenciales y con `X-API-KEY` inválida, en `POST /orders/buy`, `POST /orders/sell`, `DELETE /orders/1` y `GET /orders/book?playerId=1`.

### Implementation for User Story 4

- [X] T037 [US4] Verificar `"quantity": 1.5` (research D11). Si Jackson lo convierte a `1` y el test de T036 da 201, crear `MAIN/dto/market/StrictIntegerDeserializer.java` (`ValueDeserializer<Integer>` de Jackson 3, `tools.jackson.databind`) que rechace tokens con parte decimal, y anotarlo con `@JsonDeserialize(using = StrictIntegerDeserializer.class)` sólo en `OrderRequest.quantity`. No cambiar la configuración global de Jackson. Si Jackson ya lo rechaza (400), no crear nada y dejar el test.
- [X] T038 [US4] Ajustar `MAIN/exception/MarketExceptionHandler.java` hasta que pasen todos los casos de T036 (en especial: `HttpMessageNotReadableException` por enum inválido o JSON roto → 400 `validation_error`, y que los 400 de Bean Validation sobre el body lleguen con `validation_error`). No tocar `GlobalExceptionHandler`.
- [X] T039 [US4] Correr `OrderControllerErrorsTest` en verde.

**Checkpoint**: todos los códigos de error de creación cubiertos.

---

## Phase 7: User Story 7 - El superusuario como creador de mercado (Priority: P1)

**Goal**: el superusuario tiene siempre una única venta LIMIT con todos sus tokens a la marketValue de cada jugador, al arrancar y después de cada recálculo.

**Independent Test**: arrancar con jugadores emitidos y verificar sus órdenes; vender tokens; recalcular con una marketValue nueva y verificar la orden rehecha con los tokens restantes.

### Tests for User Story 7 ⚠️

- [X] T040 [P] [US7] Crear `TEST/service/market/MarketMakerServiceTest.java` (`@SpringBootTest` + `@UnitTestProfile` + `@TestPropertySource(properties = "market.superuser.enabled=true")`, como los tests de la 026; limpiar y volver a sembrar en `@BeforeEach`). Casos de spec US7:
  - US7-1: después de `syncAll` (o del arranque), cada jugador emitido tiene exactamente una venta LIMIT activa del superusuario con 100 tokens a su marketValue y la tenencia con 100 reservados.
  - US7-2: el superusuario vendió 30; se cambia la marketValue de 1.2000 a 1.3500 y se llama a `syncAll` → la orden anterior `CANCELLED` con `closedAt`, una nueva de 70 a 1.3500, tenencia con 70 reservados; auditoría `ORDER_CANCELLED`, `ORDER_CREATED` y `HOLDING_CHANGED` con autor `SYSTEM`.
  - Sin cambios de precio ni de cantidad → no se crea orden ni auditoría (spec, Edge Cases).
  - US7-4: superusuario sin tokens de un jugador → no hay orden activa.
  - US7-5: marketValue que se movió 50% → la orden se rehace igual (sin banda).
  - US7-6: `syncAll` 3 veces seguidas → nunca más de una orden activa por jugador y ninguna de compra.
  - US7-7: un jugador cuyo `syncPlayer` falla (por ejemplo, con un `@MockitoSpyBean` de `MarketPlayerRepository` que lanza para ese id) → los demás se actualizan, `syncAll` no lanza y la orden anterior de ese jugador queda como estaba.
  - Jugador sin marketValue (si es posible) → no se publica orden.
  - US7-1 al arrancar (FR-019): sembrar jugadores con sus 100 tokens emitidos al superusuario, llamar a `marketMakerService.onApplicationReady()` y verificar que cada jugador tiene la venta LIMIT del superusuario con 100 tokens a su marketValue. Además, verificar por reflexión que `MarketMakerService#onApplicationReady` tiene `@EventListener` con `ApplicationReadyEvent.class` entre sus clases de evento (el contexto de test arranca con la base vacía, así que el disparo real al arrancar no se puede observar).
- [X] T041 [P] [US7] Crear `TEST/service/market/MarketMakerRecalculationTest.java` (mismo setup): **reprecio después de un recálculo real**. Sembrar jugadores con stats para que `QuoteCalculationService.recalculate(Optional.empty())` cambie su marketValue (reutilizar los fixtures de los tests de la 016/020); después del recálculo, la venta del superusuario de cada jugador está a la nueva `Player.marketValue`. Agregar un jugador nuevo sin tokens antes del recálculo → al terminar ese mismo recálculo tiene sus 100 tokens emitidos **y** su orden de venta (US7-3). Las auditorías del reprecio tienen el `correlation_id` de la corrida. Además (FR-024): una venta LIMIT de un usuario que queda fuera de la banda ±20% de la nueva marketValue después del recálculo sigue igual (mismo estado, precio, pendiente y reserva de tokens, sin auditoría nueva).
- [X] T042 [P] [US7] En `TEST/service/market/TokenIssuanceListenerTest.java`, agregar un test que verifique que `TokenIssuanceListener#onQuoteRunCompleted` tiene `@Order(MarketListenerOrder.TOKEN_ISSUANCE)` y que es menor que `MarketListenerOrder.MARKET_MAKER` (por reflexión).

### Implementation for User Story 7

- [X] T043 [P] [US7] Crear `MAIN/service/market/MarketListenerOrder.java`: clase final con constructor privado y `public static final int TOKEN_ISSUANCE = 0;` y `public static final int MARKET_MAKER = 100;`, con javadoc que explique que la emisión tiene que correr antes del reprecio (research D7).
- [X] T044 [US7] En `MAIN/service/market/TokenIssuanceListener.java`, agregar `@Order(MarketListenerOrder.TOKEN_ISSUANCE)` (de `org.springframework.core.annotation`) al método `onQuoteRunCompleted`. Ningún otro cambio. Es de la 026, no de Entrega 1.
- [X] T045 [US7] Crear `MAIN/service/market/MarketMakerService.java` (`@Service`) con dependencias `@Value("${market.superuser.enabled:true}") boolean enabled`, `SuperuserAccount`, `HoldingRepository`, `MarketOrderRepository`, `MarketLocks`, `TransactionAuditService`, `TransactionOperations`, `Clock`:
  - `public SyncResult syncAll()`: si `!enabled`, log `market_maker_disabled` y devuelve vacío; obtiene `superuserAccount.id()` (si lanza `IllegalStateException` con `SuperuserAccount.MISSING`, log `market_maker_skipped reason=superuser_missing` y termina, como `TokenIssuanceListener`); recorre `holdingRepository.findPlayerIdsWithTokens(superuserId)` y llama a `syncPlayer` de cada uno dentro de `transactionOperations.execute`, una transacción por jugador; cuenta `updated`, `unchanged` y `failed`; una `RuntimeException` de un jugador se loguea `market_maker_sync_failed playerId= errorType=` y sigue; al final `market_maker_sync_completed updated= unchanged= failed=`. `SyncResult` es un record nuevo en el mismo paquete.
  - `boolean syncPlayer(Long superuserId, Long playerId)` (requiere transacción): `MarketLocks.lockPlayer`; si `marketValue` es nulo, no hace nada; `lockHolding(superuserId, playerId)`; `findActiveSellsForUpdate(superuserId, playerId)`; si hay exactamente una con `limitPrice` igual (`compareTo`) a marketValue y `remaining()` igual a `holding.getQuantity()`, devuelve `false`; si no, cancela cada activa (`order.cancel(now)`, `holding.release(order.remaining(), now)`, auditar `ORDER_CANCELLED` y `HOLDING_CHANGED`) y, si `holding.getQuantity() > 0`, crea `MarketOrder.limit(superuser, player, SELL, holding.getQuantity(), marketValue, now)`, `holding.reserve(...)`, guarda y audita `ORDER_CREATED` y `HOLDING_CHANGED`; todo con `AuditActor.system()`; log `market_maker_repriced playerId= orderId= quantity= price=`; devuelve `true`. Sin banda (FR-021).
  - `@EventListener(ApplicationReadyEvent.class) public void onApplicationReady()`: pone un correlation id generado en el MDC (`CorrelationIdFilter.MDC_KEY`) durante la pasada, llama a `syncAll()` y lo quita en un `finally`. Nunca propaga excepciones (log `WARN`).
  - `@TransactionalEventListener(fallbackExecution = true) @Order(MarketListenerOrder.MARKET_MAKER) public void onQuoteRunCompleted(QuoteRunCompletedEvent event)`: llama a `syncAll()`; usa el correlation id que ya está en el MDC de la corrida; nunca propaga excepciones al recálculo (log `market_maker_after_quote_run_failed runId= errorType=`). Javadoc: por qué `fallbackExecution = true` (el evento se publica fuera de transacción en `QuoteCalculationService.recalculate`) y por qué el `@Order` (research D7).
- [X] T046 [US7] Correr `MarketMakerServiceTest`, `MarketMakerRecalculationTest`, `TokenIssuanceListenerTest` y los tests de la 026 (`SuperuserInitializerTest`, `TokenIssuanceServiceTest`) en verde.

**Checkpoint**: el creador de mercado publica y sigue a la cotización.

---

## Phase 8: User Story 8 - Órdenes simultáneas sin sobreventa (Priority: P1)

**Goal**: con órdenes simultáneas no hay sobreventa, saldos negativos ni tokens de más.

**Independent Test**: `@E2ETest` contra PostgreSQL con hilos simultáneos sobre un mismo jugador y sobre un mismo usuario.

### Tests for User Story 8 ⚠️

- [X] T047 [US8] Crear `TEST/e2e/OrderConcurrencyE2ETest.java` con `@E2ETest` y `@TestPropertySource(properties = "market.superuser.enabled=true")` (sin eso, `syncAll()` no hace nada y US8-4 no prueba nada). Sembrar con repositorios (o `E2EFixtures`) un jugador con marketValue 1.0000, el superusuario ADMIN con su tenencia y una venta LIMIT de **10** tokens (los otros 90 en manos de otro usuario sin órdenes, para que el total siga en 100), y 50 usuarios con API key (`AuthFixtures`). Usar el cliente HTTP de los E2E existentes contra el puerto aleatorio, un `ExecutorService` de 50 hilos y un `CountDownLatch` de arranque. Casos:
  - US8-1 / SC-003: 50 compras MARKET de 1 a la vez → exactamente 10 respuestas `FILLED` y 40 `CANCELLED` con 0 ejecutados; `HoldingRepository.sumQuantityByPlayerId` = 100; ninguna tenencia ni saldo negativo; suma de saldos de todos los usuarios igual a la inicial. Repetir el escenario 10 veces (`@RepeatedTest(10)` o bucle con limpieza), 10 de 10 en verde.
  - US8-2: un usuario con 10.0000 de saldo manda a la vez dos compras de 8 créditos en jugadores distintos (cada uno con su venta del superusuario a 1.0000) → una 201 y la otra 422 `insufficient_balance`; saldo final 2.0000.
  - US8-3: un usuario con 5 tokens disponibles manda a la vez dos ventas LIMIT de 5 → una 201 y la otra 422 `insufficient_tokens`; reservados 5.
  - US8-4: mientras 20 hilos compran, se llama a `MarketMakerService.syncAll()` con una marketValue nueva → al final, tokens vendidos por el superusuario + pendiente de su orden activa = sus tokens iniciales; nunca dos órdenes activas suyas.
  - Ningún deadlock: el test termina dentro de un timeout (`assertTimeoutPreemptively` o `latch.await` con límite).

### Implementation for User Story 8

- [X] T048 [US8] Correr `OrderConcurrencyE2ETest` con PostgreSQL y Redis levantados. Si aparece sobreventa, deadlock o un saldo negativo, revisar que `OrderService`, `OrderService.cancel` y `MarketMakerService.syncPlayer` tomen los locks sólo a través de `MarketLocks` y en el orden jugador → órdenes → usuarios por id → tenencias (research D3). No cambiar el nivel de aislamiento ni agregar locks en memoria.

**Checkpoint**: sin sobreventa bajo concurrencia real.

---

## Phase 9: User Story 5 - Cancelar una orden propia (Priority: P2)

**Goal**: el dueño cancela una orden activa y recupera lo reservado.

**Independent Test**: cancelar compras y ventas LIMIT abiertas y parciales; intentar cancelar inexistentes, ajenas y cerradas.

### Tests for User Story 5 ⚠️

- [X] T049 [P] [US5] Crear `TEST/service/market/OrderServiceCancelTest.java` (`@SpringBootTest` + `@UnitTestProfile`):
  - US5-1: compra LIMIT 10 a 1.1000 con 4 ejecutados → `CANCELLED`, `filledQuantity` 4, `closedAt`, `sumReservedCredits` baja 6.6000, no aparece en `findOpenBuys`.
  - US5-2: venta LIMIT abierta de 6 → `reservedQuantity` de la tenencia baja 6.
  - US5-6: auditoría `ORDER_CANCELLED` (antes y después) y, en la venta, `HOLDING_CHANGED`, con autor el dueño.
- [X] T050 [P] [US5] Crear `TEST/controller/OrderControllerCancelTest.java` (setup de T023): `DELETE /orders/{id}` propia → 200 con `status` `CANCELLED` y `trades` `[]`; inexistente → 404 `order_not_found`; de otro usuario → 403 `order_forbidden` y la orden no cambia; la venta del creador de mercado pedida por un usuario → 403 `order_forbidden`; `FILLED` o `CANCELLED` → 409 `order_closed`; id `abc` → 400 `validation_error`; el superusuario → 403 `superuser_not_allowed`.

### Implementation for User Story 5

- [X] T051 [US5] En `MAIN/service/market/OrderService.java`, agregar `@Transactional public OrderResult cancel(String principalName, Long orderId)`: `MarketUserResolver.resolve`; obtener el jugador con una proyección, **sin cargar la orden**: agregar a `MAIN/repository/market/MarketOrderRepository.java` `@Query("select o.player.id from MarketOrder o where o.id = :id") Optional<Long> findPlayerIdById(@Param("id") Long id)` (404 `OrderNotFoundException` si no existe); `MarketLocks.lockPlayer(playerId)`; cargar la orden recién con `findByIdForUpdate` (cargarla antes haría que el lock devuelva un estado viejo, research D3); si no es del usuario → `OrderAccessDeniedException(order_forbidden)`; si no está activa → `OrderClosedException`; `order.cancel(now)`; si es venta LIMIT, `lockUsers` del dueño, `lockHolding` y `holding.release(order.remaining(), now)` con `HOLDING_CHANGED`; auditar `ORDER_CANCELLED` con `AuditActor.user(...)`; log `market_order_cancelled orderId= userId= releasedCredits= releasedTokens=`; devolver la orden cancelada. **`averagePrice` en la respuesta de `DELETE`** (único lugar donde se resuelve): calcularlo con los trades de la orden, agregando `List<Trade> findByBuyOrderIdOrSellOrderIdOrderByIdAsc(Long buyOrderId, Long sellOrderId)` a `MAIN/repository/market/TradeRepository.java`; `OrderResponse` recibe esos trades para el promedio pero la respuesta lleva `trades` vacío (contrato). Ajustar `OrderResponse.of` (T019) si hace falta separar los trades del promedio de los que se muestran.
- [X] T052 [US5] En `MAIN/controller/OrderController.java`, agregar `@DeleteMapping("/{id}")` con `@PathVariable Long id` y `Authentication`, que llama a `cancel` y responde 200 con `OrderResponse`; `@Operation` y `@ApiResponse` para 200, 400, 401, 403, 404 y 409.
- [X] T053 [US5] Correr `OrderServiceCancelTest` y `OrderControllerCancelTest` en verde.

**Checkpoint**: cancelación con liberación de reservas.

---

## Phase 10: User Story 6 - Ver el libro de un jugador (Priority: P2)

**Goal**: ver precios de compra y venta con la cantidad total en cada uno, y la cotización vigente.

**Independent Test**: libro con varias órdenes por precio; `GET /orders/book?playerId=`.

### Tests for User Story 6 ⚠️

- [X] T054 [P] [US6] Crear `TEST/controller/OrderBookControllerTest.java` (setup de T023):
  - US6-1: ventas de 3 y 4 a 1.1000, 50 del superusuario a 1.2000 y compra de 5 a 1.0500 → `sells` `[{1.1000, 7}, {1.2000, 50}]`, `buys` `[{1.0500, 5}]`, `marketValue` del jugador.
  - US6-2: con una parcial, el nivel suma sólo lo pendiente.
  - US6-3: sin órdenes → `sells` y `buys` `[]`.
  - US6-4: la respuesta no tiene `orderId`, `userId` ni `username` en ningún nivel (verificar con `jsonPath` que no existen).
  - US6-5: sin `playerId` → 400 `validation_error`; `playerId=abc` → 400; inexistente → 404 `player_not_found`.
  - Las órdenes `FILLED`, `CANCELLED` y MARKET no aparecen.

### Implementation for User Story 6

- [X] T055 [P] [US6] En `MAIN/repository/market/MarketOrderRepository.java`, agregar dos consultas de agregación sobre las LIMIT activas del jugador y lado (con el mismo filtro que `BOOK_FILTER`), que devuelvan `BookLevelResponse` por constructor JPQL (`select new com.example.demo.dto.market.BookLevelResponse(o.limitPrice, sum(o.quantity - o.filledQuantity)) ... group by o.limitPrice order by o.limitPrice asc|desc`) y los `default` `findSellLevels(playerId)` (asc) y `findBuyLevels(playerId)` (desc) (research D10). Si el constructor JPQL con `long` da problemas de tipo en `sum`, usar un record de proyección en `repository/market` y mapear en el servicio.
- [X] T056 [US6] En `MAIN/service/market/OrderService.java`, agregar `@Transactional(readOnly = true) public OrderBookResponse book(Long playerId)`: lee el jugador sin lock (`PlayerRepository.findById`, sólo lectura; `PlayerNotFoundException` si no existe), y arma la respuesta con `findSellLevels`, `findBuyLevels` y `player.getMarketValue()`.
- [X] T057 [US6] En `MAIN/controller/OrderController.java`, agregar `@GetMapping("/book")` con `@RequestParam Long playerId`, que llama a `book` y responde 200; `@Operation` y `@ApiResponse` para 200, 400, 401 y 404.
- [X] T058 [US6] Correr `OrderBookControllerTest` en verde.

**Checkpoint**: todas las historias completas.

---

## Phase 11: Polish & Cross-Cutting Concerns

- [X] T059 [P] Crear `TEST/config/OrderOpenApiTest.java`, siguiendo `QuoteOpenApiTest`: en `/v3/api-docs`, `POST /orders/buy`, `POST /orders/sell`, `DELETE /orders/{id}` y `GET /orders/book` existen, cada uno con `security` que incluye `bearerAuth` y `apiKeyAuth`, y con las respuestas del contrato (`201/400/401/403/404/422` en buy y sell; `200/400/401/403/404/409` en delete; `200/400/401/404` en book); los schemas `OrderRequest`, `OrderResponse` y `OrderBookResponse` existen (FR-031).
- [X] T060 [P] Crear `TEST/service/market/MarketSimulationTest.java` (`@SpringBootTest` + `@UnitTestProfile`, constitución §3.3): 4 usuarios sobre 5 jugadores con la venta del superusuario sembrada en cada uno. Guion fijo: cada usuario compra al superusuario en 2 o 3 jugadores (MARKET y LIMIT), dos usuarios dejan ventas LIMIT a precios distintos, otros dos las compran en parte, uno cancela una orden. Al final, comparar contra valores calculados a mano en el test: tenencias de cada usuario por jugador, saldos, cantidad y precio de cada trade, reservas de créditos y tokens; y verificar que cada jugador suma 100 tokens y que la suma de saldos se conservó.
- [X] T061 [P] Crear `TEST/service/market/OrderLoggingTest.java` con `OutputCaptureExtension` (como `TokenIssuanceListenerTest`): una compra loguea `market_order_placed` y `market_trade_executed` con el correlation id del MDC; un rechazo loguea `market_order_rejected reason=insufficient_balance`; ningún log contiene la API key ni la contraseña usadas (FR-030).
- [X] T062 [P] Revisar SC-008 en `TEST/controller/OrderPerformanceIT.java` (como `PlayerDetailPerformanceIT`): una compra MARKET que barre 20 niveles responde en menos de 500 ms y `GET /orders/book` en menos de 200 ms (medir el promedio de varias corridas después de un calentamiento).
- [X] T063 Verificar que `averagePrice` en la respuesta de `DELETE` de una orden parcialmente ejecutada coincide con el de su respuesta de creación (lo implementa T051).
- [X] T064 Correr la suite completa: `.\mvnw.cmd test "-DexcludedGroups=e2e"` y, con PostgreSQL y Redis levantados, `.\mvnw.cmd test "-Dgroups=e2e"`. Todo en verde, incluidos los tests de Entrega 1 y de la 026 sin cambios de expectativa (salvo los ajustes de escala de T003, si hubo).
- [X] T065 Revisar con `git diff --stat main` que, de los archivos protegidos, sólo aparezca `backend/src/main/java/com/example/demo/model/User.java` con una única línea agregada (`@Column(precision = 38, scale = 4)`), y que `UserRepository.java`, `application.properties`, `PlayerCatalogService.java`, `GlobalExceptionHandler.java`, `PlayerRepository.java` y `SecurityConfig.java` no aparezcan. Los archivos de la 026 modificados tienen que ser sólo los de la tabla "Cambios en archivos existentes" de [plan.md](plan.md) (más `TradeRepository.java` si T051 lo necesitó).
- [X] T066 [P] Revisar issues de Sonar en el código nuevo: sin código duplicado entre `placeBuy` y `placeSell` (un único pipeline), sin `catch (Exception)` genérico salvo los de los listeners (con justificación como en `TokenIssuanceListener`), métodos de `OrderService` con complejidad cognitiva razonable (extraer pasos privados si `place` o `applyFill` superan el umbral de Sonar).
- [ ] T067 Seguir [quickstart.md](quickstart.md) secciones 0 a 4 (incluido el `ALTER TABLE users ALTER COLUMN balance TYPE numeric(38,4);` sobre una base existente) y anotar el resultado de cada paso en el reporte final.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias.
- **Foundational (Phase 2)**: depende de Setup. Bloquea todas las historias.
- **US1 (Phase 3)**: depende de Foundational. Es el MVP y crea `OrderService` y `OrderController`.
- **US2 (Phase 4)**: depende de US1 (reutiliza el pipeline y `applyFill` de `OrderService`).
- **US3 (Phase 5)** y **US4 (Phase 6)**: dependen de US1 y US2 (prueban compra y venta).
- **US7 (Phase 7)**: depende de Foundational (usa `MarketLocks` y la auditoría); no necesita los endpoints. Puede hacerse en paralelo con US2–US4.
- **US8 (Phase 8)**: depende de US1, US2 y US7 (concurrencia con compras, ventas y reprecio).
- **US5 (Phase 9)** y **US6 (Phase 10)**: dependen de US1 (`OrderService` y `OrderController`); independientes entre sí.
- **Polish (Phase 11)**: depende de todas las historias.

### Within Each User Story

- Tests primero, que fallen.
- Repositorios → servicio → controller.
- Mismo archivo (`OrderService.java`, `OrderController.java`, `MarketOrderRepository.java`): tareas en serie.

### Parallel Opportunities

- Phase 1: T002 en paralelo con T001.
- Phase 2: T005, T006, T007, T008, T009, T011, T012, T013, T015, T018 y T019 en paralelo (archivos distintos). T010 después de T008 y T009; T014 después de T011–T013; T016 (controller vacío) antes de T017; T017 después de T015 y T016; T020 independiente.
- Tests de cada historia marcados [P] en paralelo entre sí.
- US7 en paralelo con US2–US4; US5 en paralelo con US6.

---

## Parallel Example: Phase 2

```text
Task: "T005 OrderSnapshot en MAIN/service/market/OrderSnapshot.java"
Task: "T006 TradeSnapshot en MAIN/service/market/TradeSnapshot.java"
Task: "T007 BalanceSnapshot en MAIN/service/market/BalanceSnapshot.java"
Task: "T008 Records del motor en MAIN/service/market/"
Task: "T009 OrderMatchingEngineServiceTest en TEST/service/market/"
Task: "T011 MarketPlayerRepository en MAIN/repository/market/"
Task: "T015 Excepciones en MAIN/exception/"
Task: "T018 OrderRequest en MAIN/dto/market/"
```

## Parallel Example: User Story 7

```text
Task: "T040 MarketMakerServiceTest en TEST/service/market/"
Task: "T041 MarketMakerRecalculationTest en TEST/service/market/"
Task: "T042 Orden de listeners en TEST/service/market/TokenIssuanceListenerTest.java"
Task: "T043 MarketListenerOrder en MAIN/service/market/"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 (saldo a escala 4) y Phase 2 (motor, locks, errores, DTOs).
2. Phase 3: comprar con MARKET y LIMIT contra una venta del superusuario sembrada.
3. **Validar**: `OrderServiceBuyTest`, `OrderControllerBuyTest` y el motor en verde.

### Incremental Delivery

1. US1 → compra (MVP).
2. US2 → venta entre usuarios.
3. US3 + US4 → prioridad verificada y todos los errores.
4. US7 → el creador de mercado publica y sigue al recálculo: ahora el sistema funciona sin sembrar órdenes a mano.
5. US8 → concurrencia en PostgreSQL.
6. US5 + US6 → cancelación y libro.
7. Polish → OpenAPI, simulación §3.3, logs, performance, revisión de archivos protegidos y quickstart.

---

## Notes

- [P] = archivos distintos, sin dependencias pendientes.
- Cada historia se puede probar sola con sus tests.
- Los tests tienen que fallar antes de implementar.
- No hacer commits; el equipo hace los commits.
- Ante cualquier necesidad de tocar un archivo protegido de Entrega 1 que no sea la anotación de `User.balance`, detenerse y consultar.
