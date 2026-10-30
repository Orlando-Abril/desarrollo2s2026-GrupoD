# Data Model: Portfolio e historial de transacciones

La feature no agrega tablas ni modifica entidades persistentes. Define modelos de lectura, proyecciones y DTOs sobre `User`, `Player`, `Holding`, `MarketOrder` y `Trade` existentes.

## Relaciones fuente

```text
User 1 ──< Holding >── 1 Player
User (buyer)  1 ──< Trade >── 1 Player
User (seller) 1 ──< Trade >── 1 Player
User 1 ──< MarketOrder (BUY LIMIT activa)  # créditos reservados

Holding + Trade + Player.marketValue ──> PortfolioPositionResponse
Trade + Player ──> TransactionResponse
User ──> MeResponse
```

## Fuentes persistentes existentes

### User

Campos consumidos: `id`, `username`, `email`, `role`, `balance`.

- El actor se resuelve por username del principal.
- El objetivo se resuelve por id después de autorizar.
- `balance` es el saldo total; saldo disponible = balance − créditos reservados.

### Holding

Campos consumidos: `user.id`, `player.id`, `quantity`, `reservedQuantity`.

- Sólo se devuelven posiciones con `quantity > 0`.
- `availableQuantity = quantity - reservedQuantity`.
- La cantidad del holding es autoritativa; los trades aportan el costo.

### Trade

Campos consumidos: `id`, `player.id`, `buyer.id`, `seller.id`, `quantity`, `price`, `total`, `executedAt`.

- Es inmutable y representa un calce individual.
- Para costo se ordena `executedAt ASC, id ASC`.
- Para historial se ordena `executedAt DESC, id DESC`.

### Player

Campos consumidos: `id`, `fullName`, `team`, `marketValue`.

- `marketValue` es obligatorio y tiene escala 4.
- `currentValue = quantity × marketValue`.

### MarketOrder

No se carga por posición. Se usa la consulta agregada existente que suma `(quantity - filledQuantity) × limitPrice` para BUY LIMIT activas.

## Proyecciones nuevas

### PortfolioHoldingRow

Proyección escalar de una consulta JPQL sobre `Holding` y `Player`.

| Campo | Tipo | Fuente | Regla |
|-------|------|--------|-------|
| holdingId | Long | `Holding.id` | sólo diagnóstico/test; no se expone |
| playerId | Long | `Player.id` | obligatorio |
| playerName | String | `Player.fullName` | obligatorio |
| playerTeam | String | `Player.team` | obligatorio |
| marketValue | BigDecimal | `Player.marketValue` | escala 4, obligatorio |
| quantity | int | `Holding.quantity` | > 0 en la consulta |
| reservedQuantity | int | `Holding.reservedQuantity` | 0..quantity |

Orden: `playerName ASC, playerId ASC`. La proyección evita acceder a `Holding.player` fuera de la consulta.

### UserTradeRow

Proyección escalar reutilizada por costo e historial.

| Campo | Tipo | Regla |
|-------|------|-------|
| id | Long | id inmutable del trade |
| playerId | Long | obligatorio |
| playerName | String | obligatorio; evita lazy load |
| buyerId | Long | obligatorio |
| sellerId | Long | obligatorio, distinto de buyerId |
| quantity | int | > 0 |
| price | BigDecimal | > 0, escala 4 |
| total | BigDecimal | quantity × price, escala 4 |
| executedAt | Instant | obligatorio |

Consultas:

- `findCostBasisRows(userId)`: todas las filas donde buyer o seller sea el usuario, ascendente.
- `findTransactionRows(userId, pageable)`: página de las mismas filas, descendente, con count separado.

## Estado de cálculo interno

### PositionCostState

Objeto interno de `PortfolioService`, no persistido ni serializado.

| Campo | Tipo | Inicial | Transición |
|-------|------|---------|------------|
| quantity | int | 0; superusuario: 100 por jugador emitido | compra `+q`; venta `−q` |
| cost | BigDecimal | 0.0000; superusuario: 100.0000 | compra `+q×p`; venta `−q×averageExact` |

#### Transiciones

```text
SIN POSICIÓN --compra(q,p)--> ABIERTA(q, q×p)
ABIERTA(Q,C) --compra(q,p)--> ABIERTA(Q+q, C+q×p)
ABIERTA(Q,C) --venta(q<Q)--> ABIERTA(Q-q, C-(C/Q)×q)
ABIERTA(Q,C) --venta(Q)----> SIN POSICIÓN(0,0)
```

Una venta con `q > Q` o una cantidad final distinta del holding indica inconsistencia y no produce respuesta parcial.

## DTOs de salida nuevos

### PortfolioPositionResponse

| Campo JSON | Tipo | Regla |
|------------|------|-------|
| playerId | integer(int64) | requerido |
| playerName | string | requerido |
| playerTeam | string | requerido; equipo vigente de `Player.team` |
| quantity | integer | > 0 |
| reservedQuantity | integer | 0..quantity |
| availableQuantity | integer | quantity − reservedQuantity |
| averagePurchasePrice | decimal | costBasis / quantity, escala 4 |
| costBasis | decimal | costo remanente, escala 4 |
| marketValue | decimal | cotización vigente, escala 4 |
| currentValue | decimal | quantity × marketValue, escala 4 |
| profitLoss | decimal | currentValue − costBasis, escala 4; puede ser negativo |

Invariantes:

- `quantity = reservedQuantity + availableQuantity`.
- `profitLoss = currentValue - costBasis`.
- La respuesta se ordena por `playerName`, luego `playerId`.

### PortfolioResponse

| Campo JSON | Tipo | Regla |
|------------|------|-------|
| userId | integer(int64) | usuario consultado |
| balance | decimal | saldo total, escala 4 |
| reservedCredits | decimal | BUY LIMIT activas, escala 4 |
| availableBalance | decimal | balance − reservedCredits, escala 4 |
| positions | array | lista inmutable de posiciones |
| totalCost | decimal | suma costBasis, escala 4 |
| totalCurrentValue | decimal | suma currentValue, escala 4 |
| totalProfitLoss | decimal | totalCurrentValue − totalCost, escala 4 |
| totalEquity | decimal | balance + totalCurrentValue, escala 4 |

Portfolio vacío: posiciones vacías; costo, valor y resultado 0.0000; patrimonio igual al saldo.

### TransactionType

Enum de contrato:

- `COMPRA`: el usuario consultado es `buyerId`.
- `VENTA`: el usuario consultado es `sellerId`.

### TransactionResponse

| Campo JSON | Tipo | Regla |
|------------|------|-------|
| id | integer(int64) | trade id |
| type | TransactionType | COMPRA o VENTA |
| playerId | integer(int64) | requerido |
| playerName | string | requerido |
| quantity | integer | > 0 |
| price | decimal | escala 4 |
| total | decimal | escala 4 |
| executedAt | string(date-time) | UTC/offset ISO-8601 |

No expone contraparte ni ids de órdenes porque no forman parte del requisito y revelarían información ajena.

### TransactionPageResponse

| Campo JSON | Tipo | Regla |
|------------|------|-------|
| items | array<TransactionResponse> | nunca null |
| page | integer | >= 0 |
| size | integer | 1..100 |
| numberOfElements | integer | 0..size |
| totalElements | integer(int64) | >= 0 |
| totalPages | integer | ceil(totalElements / size), 0 si vacío |
| first | boolean | page == 0 |
| last | boolean | no existe página posterior; true para historial vacío |

### MeResponse

| Campo JSON | Tipo | Regla |
|------------|------|-------|
| id | integer(int64) | requerido |
| username | string | requerido |
| email | string(email) | requerido |
| role | string | `USER` o `ADMIN` |
| balance | decimal | saldo vigente a escala 4 |

## Validación y errores

| Condición | Resultado |
|-----------|-----------|
| Sin credencial o inválida | 401 `unauthorized` |
| `/auth/me` con API key sin dueño o principal sin User | 401 `authenticated_user_not_found` |
| USER consulta otro id | 403 `forbidden`, antes de comprobar existencia |
| ADMIN consulta id inexistente | 404 `user_not_found` |
| id < 1, page < 0, size fuera de 1..100 | 400 `validation_error` |
| Historia/holding inconsistentes, venta superior a posición o `marketValue` ausente | 500 `portfolio_inconsistent` con mensaje público genérico; detalle sólo en log estructurado |

## Consistencia de lectura

- Portfolio e historial se ejecutan en transacciones read-only con aislamiento `REPEATABLE_READ`.
- Usuario, holdings, trades y reservas deben pertenecer a la misma fotografía confirmada al comenzar la consulta.
- Un calce concurrente aparece completo en la consulta actual o recién en la siguiente; nunca parcialmente.
- La ausencia de escrituras se verifica comparando saldo, holdings, órdenes, trades y auditorías antes y después de los tres GET.

## Persistencia

- No hay entidades nuevas.
- No hay migración ni cambio de columnas/índices.
- Los índices existentes `idx_holdings_user`, `idx_trades_buyer_executed`, `idx_trades_seller_executed` e `idx_orders_user_created` sostienen las consultas.
