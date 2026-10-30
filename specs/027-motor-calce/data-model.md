# Data Model: Motor de calce y creador de mercado

Esta feature no agrega tablas. Usa las entidades de la 026 (`orders`, `holdings`, `trades`, `audit_logs`) y cambia la escala de `users.balance`. Las decisiones están en [research.md](research.md).

## Cambios en el esquema

| Tabla | Cambio | Motivo |
|-------|--------|--------|
| `users` | `balance` pasa de `numeric(38,2)` a `numeric(38,4)` (`@Column(precision = 38, scale = 4)` en `User`, único cambio autorizado) | D5. Bases existentes: `ALTER TABLE users ALTER COLUMN balance TYPE numeric(38,4);` ([quickstart.md](quickstart.md)) |

No hay índices nuevos: el libro, las consultas por usuario y la agregación del libro usan `idx_orders_book` e `idx_orders_user_created` de la 026.

## Entidades existentes y cómo las usa esta feature

### `MarketOrder` (`orders`)

Sin cambios de código. Ciclo de vida en esta feature:

```text
                 crear (POST /orders/buy|sell)
                          │
                          ▼
                       OPEN ──fill parcial──► PARTIALLY_FILLED ──fill del resto──► FILLED
                        │  └────────────fill total────────────────────────────────►  ▲
                        │                         │
       MARKET sin llenar, DELETE, reprecio        │ MARKET con resto, DELETE, reprecio
                        ▼                         ▼
                    CANCELLED ◄───────────────────┘
```

- Una MARKET nunca queda activa al terminar el pedido: termina `FILLED` o `CANCELLED` (con lo ejecutado hasta ahí).
- Una LIMIT que no se llena termina `OPEN` o `PARTIALLY_FILLED` y queda en el libro.
- `FILLED` y `CANCELLED` son terminales (`cancel` desde ahí → `OrderClosedException`, 409).
- Las del superusuario son siempre `SELL` + `LIMIT`; a lo sumo una activa por jugador.

### `Holding` (`holdings`)

Sin cambios de código. Operaciones que usa el calce:

| Situación | Método |
|-----------|--------|
| Comprador recibe tokens (crea la tenencia si no existe) | `Holding.issue(user, player, n, now)` o `credit(n, now)` |
| Vendedor con orden en el libro (maker) | `consumeReserved(n, now)` |
| Vendedor que llega (taker, MARKET o LIMIT) | `debit(n, now)` |
| Venta LIMIT que queda en el libro | `reserve(pendiente, now)` |
| Cancelación de una venta LIMIT (DELETE, reprecio) | `release(pendiente, now)` |

`Holding.issue` exige cantidad positiva: la tenencia del comprador se crea con los tokens del primer fill. La unicidad `(user_id, player_id)` no puede chocar porque el jugador está bloqueado (D3).

### `Trade` (`trades`)

Sin cambios. `Trade.of(buyOrder, sellOrder, quantity, price, executedAt)` con `price` = precio límite de la orden del libro (D1).

### `User` (`users`)

Sólo cambia la escala de `balance`. El motor modifica el saldo sobre la entidad bloqueada (`MarketUserRepository.findLockedById` + `setBalance`) (D5).

- **Créditos reservados**: `MarketOrderRepository.sumReservedCredits(userId)` (026).
- **Saldo disponible**: `balance − créditos reservados`.

### `AuditLog` (`audit_logs`)

Sin cambios de tabla. `AuditAction` suma valores (D6):

| Acción | `entityType` | Antes | Después |
|--------|--------------|-------|---------|
| `ORDER_CREATED` | `ORDER` | nulo | `OrderSnapshot` |
| `ORDER_FILLED` | `ORDER` | `OrderSnapshot` | `OrderSnapshot` |
| `ORDER_CANCELLED` | `ORDER` | `OrderSnapshot` | `OrderSnapshot` |
| `TRADE_EXECUTED` | `TRADE` | nulo | `TradeSnapshot` |
| `BALANCE_CHANGED` | `USER` | `BalanceSnapshot` | `BalanceSnapshot` |
| `HOLDING_CHANGED` | `HOLDING` | `HoldingSnapshot` o nulo si se crea | `HoldingSnapshot` |

## Snapshots nuevos (`service/market`)

Records para la auditoría; nunca entidades, nunca credenciales.

| Record | Campos |
|--------|--------|
| `OrderSnapshot` | `id, userId, playerId, side, type, limitPrice, quantity, filledQuantity, status, createdAt, closedAt` |
| `TradeSnapshot` | `id, playerId, buyerId, sellerId, buyOrderId, sellOrderId, quantity, price, total, executedAt` |
| `BalanceSnapshot` | `userId, balance` |

## Tipos del motor puro (`service/market`)

Sin JPA ni Spring, para `OrderMatchingEngineService` (D2).

| Record | Campos | Notas |
|--------|--------|-------|
| `IncomingOrder` | `userId, side, type, limitPrice, quantity` | `limitPrice` nulo en MARKET |
| `BookEntry` | `orderId, userId, limitPrice, remaining` | En el orden del libro que da el repositorio |
| `Fill` | `bookOrderId, counterpartyUserId, quantity, price` | `price` = `limitPrice` del `BookEntry` |
| `MatchResult` | `List<Fill> fills, int remaining` | `remaining` = cantidad − suma de fills |

Reglas del motor:

- Recorre `BookEntry` en el orden dado. Saltea los de `userId` igual al de la orden que llega.
- LIMIT de compra: se detiene en el primer `limitPrice > tope`. LIMIT de venta: en el primer `limitPrice < tope`. MARKET: no se detiene por precio.
- Cada fill es `min(pendiente de la orden que llega, remaining del BookEntry)`.
- `isWithinBand(price, marketValue)`: `marketValue × 0.80 ≤ price ≤ marketValue × 1.20`, comparación exacta con `BigDecimal`.

## Repositorios: métodos nuevos

| Repositorio | Método | Uso |
|-------------|--------|-----|
| `MarketPlayerRepository` (nuevo, `Repository<Player, Long>`) | `findLockedById(id)` con `PESSIMISTIC_WRITE` | Lock del jugador (D3) sin tocar `PlayerRepository` |
| `MarketOrderRepository` | `findSellLevels(playerId)` (precio asc) y `findBuyLevels(playerId)` (precio desc) → `List<BookLevelResponse>` agrupado por `limitPrice` | `GET /orders/book` (D10) |
| `MarketOrderRepository` | `lockActiveByUserAndPlayerAndSide(userId, playerId, side, active)` y el `default` `findActiveSellsForUpdate(userId, playerId)` | Órdenes activas del superusuario para un jugador (D7) |
| `TradeRepository` | `findByBuyOrderIdOrSellOrderIdOrderByIdAsc(buyOrderId, sellOrderId)` (sólo si hace falta) | `averagePrice` en la respuesta de `DELETE` |
| `HoldingRepository` | `findPlayerIdsWithTokens(userId)` (`quantity > 0`, por id) | Jugadores que recorre el creador de mercado (D7) |

`MarketUserRepository.addToBalance`: sólo cambia su javadoc (escala 4 y aclaración de que el motor no lo usa) (FR-034).

## DTOs nuevos (`dto/market`)

| DTO | Campos |
|-----|--------|
| `OrderRequest` | `playerId` (Long, obligatorio), `quantity` (Integer, > 0), `type` (`MARKET`/`LIMIT`), `limitPrice` (BigDecimal, > 0, hasta 4 decimales; obligatorio en LIMIT, prohibido en MARKET) |
| `OrderResponse` | `id, playerId, side, type, limitPrice, quantity, filledQuantity, remainingQuantity, status, averagePrice, createdAt, closedAt, trades: List<OrderTradeResponse>` |
| `OrderTradeResponse` | `id, quantity, price, total, executedAt` |
| `OrderBookResponse` | `playerId, marketValue, sells: List<BookLevelResponse>, buys: List<BookLevelResponse>` |
| `BookLevelResponse` | `price, quantity` |

- `averagePrice`: suma de totales de los trades de la orden / `filledQuantity`, con escala 4 (`HALF_EVEN`); nulo si no se ejecutó nada.
- `trades` en la respuesta de `POST`: los ejecutados en ese pedido. En la de `DELETE`: vacía (la orden se devuelve en su estado final).
