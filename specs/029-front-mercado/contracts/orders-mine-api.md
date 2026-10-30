# Contrato: `GET /orders/mine`

Endpoint nuevo en el `OrderController` existente de la 027. Hereda sus `@SecurityRequirement` (`bearerAuth`, `apiKeyAuth`), el `ErrorResponse` `{"error": "...", "message": "..."}` y el header `X-Correlation-ID`.

Devuelve las órdenes activas (`OPEN` y `PARTIALLY_FILLED`) del usuario autenticado, de la más nueva a la más vieja (`createdAt` desc y, a igual instante, `id` desc), sin paginar. El usuario sale de la credencial; no hay parámetros.

## 200 OK

```json
[
  {
    "id": 1002,
    "playerId": 42,
    "playerName": "Lionel Messi",
    "side": "SELL",
    "type": "LIMIT",
    "limitPrice": 1.2500,
    "quantity": 6,
    "filledQuantity": 0,
    "remainingQuantity": 6,
    "status": "OPEN",
    "averagePrice": null,
    "createdAt": "2026-10-29T16:00:00.000Z",
    "closedAt": null,
    "trades": []
  },
  {
    "id": 1001,
    "playerId": 7,
    "playerName": "Kylian Mbappé",
    "side": "BUY",
    "type": "LIMIT",
    "limitPrice": 1.3000,
    "quantity": 10,
    "filledQuantity": 4,
    "remainingQuantity": 6,
    "status": "PARTIALLY_FILLED",
    "averagePrice": 1.2000,
    "createdAt": "2026-10-29T15:04:05.123Z",
    "closedAt": null,
    "trades": []
  }
]
```

- Mismos campos que `OrderResponse` de `POST /orders/buy`, más `playerName` (`Player.fullName`).
- `averagePrice` resume las operaciones ya ejecutadas (nulo si `filledQuantity` es 0); `trades` va vacío, como en `DELETE /orders/{id}`.
- Sin órdenes activas: `[]`.
- Nunca incluye órdenes de otro usuario ni órdenes `FILLED` o `CANCELLED`.

## Errores

| HTTP | `error` | Cuándo |
|------|---------|--------|
| 401 | (seguridad existente) | Sin credenciales o credenciales inválidas |
| 403 | `superuser_not_allowed` / `user_required` | Igual que el resto de los endpoints de órdenes (resolución de usuario existente) |

## Implementación acordada

- `MarketOrderRepository`: una consulta nueva `findActiveByUserWithPlayer(userId)` con `join fetch o.player`, filtro `o.status in OrderStatus.ACTIVE` y `order by o.createdAt desc, o.id desc` (la existente `findByUserIdOrderByCreatedAtDescIdDesc` no filtra por estado).
- Precio promedio de las parciales con la consulta existente `TradeRepository.findByBuyOrderIdOrSellOrderIdOrderByIdAsc`.
- `OrderService.myOrders(principalName)` → `List<MyOrderResponse>`; `MyOrderResponse.of(OrderResponse, playerName)`.
- OpenAPI: `@Operation` + `@ApiResponse` 200 (array de `MyOrderResponse`), 401 y 403.

## Tests (`OrderControllerMineTest`, `@MarketMockMvcTest`)

1. **200 sólo propias y activas**: el usuario A tiene órdenes `OPEN`, `PARTIALLY_FILLED`, `FILLED` y `CANCELLED`, y B tiene órdenes activas → la respuesta trae sólo las dos activas de A, la más nueva primero, con `playerName` y `averagePrice` de la parcial.
2. **200 vacío**: usuario sin órdenes activas → `[]`.
3. **401**: sin credenciales.
