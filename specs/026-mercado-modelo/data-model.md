# Data Model: Modelo de datos del mercado

Entidades nuevas en `backend/src/main/java/com/example/demo/model/market`. Las existentes (`User`, `Player`) no se modifican. Las decisiones están en [research.md](research.md).

## Diagrama

```text
users 1 ──< holdings >── 1 players
users 1 ──< orders   >── 1 players
orders (BUY)  1 ──< trades >── 1 orders (SELL)
users (buyer) 1 ──< trades >── 1 users (seller)
audit_logs (sin FK: entity_type + entity_id)
```

## Holding — tabla `holdings`

| Campo | Columna | Tipo | Reglas |
|-------|---------|------|--------|
| id | `id` | bigint identity | PK |
| user | `user_id` | FK `users` | obligatorio, no actualizable |
| player | `player_id` | FK `players` | obligatorio, no actualizable |
| quantity | `quantity` | int | ≥ 0 |
| reservedQuantity | `reserved_quantity` | int | ≥ 0 y ≤ `quantity` |
| version | `version` | bigint | `@Version` |
| createdAt | `created_at` | timestamp | no actualizable |
| updatedAt | `updated_at` | timestamp | se actualiza en cada cambio |

- **Unicidad**: `uk_holdings_user_player (user_id, player_id)`. **Índice**: `idx_holdings_user (user_id)`.
- **Check**: `quantity >= 0 AND reserved_quantity >= 0 AND reserved_quantity <= quantity`.
- **Derivado**: `available() = quantity - reservedQuantity`.
- **Operaciones de dominio** (todas lanzan `IllegalStateException` si violarían una regla y no cambian nada en ese caso):
  - `Holding.issue(user, player, quantity, now)`: tenencia nueva con 0 reservados.
  - `credit(n, now)`: suma `n` a `quantity`, con `n > 0`.
  - `reserve(n, now)`: suma `n` a `reservedQuantity`, con `n ≤ available()`.
  - `release(n, now)`: resta `n` de `reservedQuantity`, con `n ≤ reservedQuantity`.
  - `consumeReserved(n, now)`: resta `n` de `reservedQuantity` y de `quantity`, para una venta LIMIT calzada.
  - `debit(n, now)`: resta `n` de `quantity`, con `n ≤ available()`, para una venta MARKET.
- **Nunca se borra**: una tenencia en 0 se conserva.

## MarketOrder — tabla `orders`

| Campo | Columna | Tipo | Reglas |
|-------|---------|------|--------|
| id | `id` | bigint identity | PK |
| user | `user_id` | FK `users` | obligatorio, no actualizable |
| player | `player_id` | FK `players` | obligatorio, no actualizable |
| side | `side` | varchar(16) `OrderSide` | obligatorio, no actualizable |
| type | `type` | varchar(16) `OrderType` | obligatorio, no actualizable |
| limitPrice | `limit_price` | numeric(12,4) | LIMIT: > 0; MARKET: nulo |
| quantity | `quantity` | int | > 0, no actualizable |
| filledQuantity | `filled_quantity` | int | 0 ≤ x ≤ `quantity` |
| status | `status` | varchar(16) `OrderStatus` | ver transiciones |
| createdAt | `created_at` | timestamp | no actualizable |
| updatedAt | `updated_at` | timestamp | |
| closedAt | `closed_at` | timestamp | nulo salvo FILLED o CANCELLED |

- **Índices**: `idx_orders_book (player_id, side, status, limit_price, created_at)`, `idx_orders_user_created (user_id, created_at)`.
- **Check**: `quantity > 0 AND filled_quantity >= 0 AND filled_quantity <= quantity AND ((type = 'LIMIT' AND limit_price IS NOT NULL AND limit_price > 0) OR (type = 'MARKET' AND limit_price IS NULL))`.
- **Derivado**: `remaining() = quantity - filledQuantity`.
- **Creación**: `MarketOrder.limit(user, player, side, quantity, limitPrice, now)` y `MarketOrder.market(user, player, side, quantity, now)`. El precio se normaliza a escala 4.

### Transiciones de estado

```text
           fill(parcial)               fill(resto)
  OPEN ───────────────► PARTIALLY_FILLED ─────────► FILLED
   │  └──────────────── fill(todo) ────────────────►  ▲
   │                         │
   └── cancel ──► CANCELLED ◄┘ cancel
```

- `fill(n, now)`: requiere estado activo y `0 < n ≤ remaining()`. Suma `n` a `filledQuantity`. Si `remaining() == 0` pasa a `FILLED` con `closedAt = now`; si no, a `PARTIALLY_FILLED`.
- `cancel(now)`: requiere estado activo. Pasa a `CANCELLED` con `closedAt = now` y conserva `filledQuantity`.
- `FILLED` y `CANCELLED` son terminales: cualquier transición lanza `IllegalStateException`.
- **Créditos reservados** de una orden: `remaining() × limitPrice` si es BUY, LIMIT y activa; 0 en cualquier otro caso.

## Trade — tabla `trades` (inmutable)

| Campo | Columna | Tipo | Reglas |
|-------|---------|------|--------|
| id | `id` | bigint identity | PK |
| player | `player_id` | FK `players` | igual al de las dos órdenes |
| buyer | `buyer_id` | FK `users` | dueño de `buyOrder` |
| seller | `seller_id` | FK `users` | dueño de `sellOrder`, ≠ `buyer` |
| buyOrder | `buy_order_id` | FK `orders` | lado BUY |
| sellOrder | `sell_order_id` | FK `orders` | lado SELL |
| quantity | `quantity` | int | > 0 |
| price | `price` | numeric(12,4) | > 0 |
| total | `total` | numeric(16,4) | `price × quantity` exacto |
| executedAt | `executed_at` | timestamp | |

- Todas las columnas tienen `updatable = false`. La entidad es `@Immutable`, no tiene setters y un `@PreRemove` lanza `UnsupportedOperationException`. Las modificaciones JPQL masivas fallan por `ImmutableEntityConfig`; los `DELETE` JPQL masivos y el SQL nativo quedan fuera de la garantía (research D5).
- **Índices**: `idx_trades_buyer_executed`, `idx_trades_seller_executed`, `idx_trades_player_executed`.
- **Check**: `quantity > 0 AND price > 0 AND buyer_id <> seller_id`.
- **Creación**: `Trade.of(buyOrder, sellOrder, quantity, price, executedAt)`, que valida lados, jugador, comprador ≠ vendedor y positivos.

## AuditLog — tabla `audit_logs` (inmutable)

| Campo | Columna | Tipo | Reglas |
|-------|---------|------|--------|
| id | `id` | bigint identity | PK |
| actorType | `actor_type` | varchar(16) `AuditActorType` | `USER` o `SYSTEM` |
| actorUserId | `actor_user_id` | bigint | obligatorio si `USER`, nulo si `SYSTEM` (sin FK: la auditoría sobrevive a cualquier cambio en `users`) |
| action | `action` | varchar(32) `AuditAction` | obligatorio |
| entityType | `entity_type` | varchar(32) | obligatorio (`USER`, `HOLDING`, `ORDER`, `TRADE`) |
| entityId | `entity_id` | varchar(64) | obligatorio |
| beforeState | `before_state` | json / jsonb | nulo = no existía |
| afterState | `after_state` | json / jsonb | nunca nulo |
| occurredAt | `occurred_at` | timestamp | obligatorio |
| correlationId | `correlation_id` | varchar(36) | obligatorio |

- Tiene la misma protección que `Trade`: `@Immutable`, `updatable = false`, `@PreRemove` y un repositorio sin borrado.
- **Índices**: `idx_audit_logs_entity (entity_type, entity_id)`, `idx_audit_logs_occurred (occurred_at)`.
- **Check**: `(actor_type = 'SYSTEM' AND actor_user_id IS NULL) OR (actor_type = 'USER' AND actor_user_id IS NOT NULL)`.
- `AuditActor` es un record de valor: `AuditActor.system()` y `AuditActor.user(id)`.
- `AuditAction` en esta feature: `SUPERUSER_CREATED` y `TOKENS_ISSUED`.

### Snapshots serializados

| Record | Campos | Uso |
|--------|--------|-----|
| `UserSnapshot` | id, username, email, role, balance | `SUPERUSER_CREATED`. **Sin** `passwordHash` ni API key |
| `HoldingSnapshot` | id, userId, playerId, quantity, reservedQuantity | `TOKENS_ISSUED` y futuras operaciones sobre tenencias |

## Enums

- `OrderSide`: `BUY`, `SELL`.
- `OrderType`: `MARKET`, `LIMIT`.
- `OrderStatus`: `OPEN`, `PARTIALLY_FILLED`, `FILLED`, `CANCELLED`. Expone `ACTIVE = {OPEN, PARTIALLY_FILLED}` e `isActive()`.
- `AuditActorType`: `USER`, `SYSTEM`.
- `AuditAction`: `SUPERUSER_CREATED`, `TOKENS_ISSUED`.

## Invariantes globales

- Para cada jugador emitido, `Σ holdings.quantity = 100` (FR-008). En esta feature sólo se escribe al emitir; el motor de calce la preserva moviendo tokens entre tenencias.
- Para cada usuario, créditos reservados = `Σ remaining × limitPrice` de sus BUY LIMIT activas (FR-016).
