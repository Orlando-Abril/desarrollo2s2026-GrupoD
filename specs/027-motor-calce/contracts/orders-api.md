# Contrato: API de órdenes

Todos los endpoints requieren credenciales: `Authorization: Bearer <jwt>` (`bearerAuth`) o `X-API-KEY: <key>` (`apiKeyAuth`). En OpenAPI se anotan con los dos `@SecurityRequirement`, como `QuoteController`. Los errores usan el `ErrorResponse` existente: `{"error": "<código>", "message": "<texto>"}`. El correlation id viaja en `X-Correlation-ID`, como en el resto de la API.

Precios y montos con 4 decimales.

---

## `POST /orders/buy` y `POST /orders/sell`

Crea una orden a nombre del usuario autenticado y la calza en el momento contra el lado opuesto del libro.

### Request

```json
{
  "playerId": 42,
  "quantity": 10,
  "type": "LIMIT",
  "limitPrice": 1.3000
}
```

| Campo | Tipo | Reglas |
|-------|------|--------|
| `playerId` | integer | obligatorio |
| `quantity` | integer | obligatorio, entero > 0 |
| `type` | `MARKET` \| `LIMIT` | obligatorio |
| `limitPrice` | number | obligatorio en `LIMIT`, prohibido en `MARKET`; > 0; hasta 4 decimales; dentro de marketValue × [0.80, 1.20] |

### 201 Created

```json
{
  "id": 1001,
  "playerId": 42,
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
  "trades": [
    { "id": 501, "quantity": 4, "price": 1.2000, "total": 4.8000, "executedAt": "2026-10-29T15:04:05.123Z" }
  ]
}
```

- `status` final: `FILLED`; `PARTIALLY_FILLED` u `OPEN` (sólo LIMIT, queda en el libro); `CANCELLED` (MARKET con resto, incluso con 0 ejecutados).
- `trades` en orden de ejecución; vacío si no se calzó nada.
- `averagePrice` nulo si `filledQuantity` es 0.

### Errores

| HTTP | `error` | Cuándo |
|------|---------|--------|
| 400 | `validation_error` | Falta un campo; `quantity` ≤ 0 o no entera; `limitPrice` ≤ 0 o con más de 4 decimales; LIMIT sin `limitPrice`; MARKET con `limitPrice`; body ilegible |
| 400 | `price_out_of_band` | `limitPrice` fuera de marketValue × [0.80, 1.20] |
| 401 | (seguridad existente) | Sin credenciales o credenciales inválidas |
| 403 | `superuser_not_allowed` | El usuario autenticado es el superusuario |
| 403 | `user_required` | La credencial no pertenece a un usuario (API key sin dueño) |
| 404 | `player_not_found` | El jugador no existe |
| 422 | `insufficient_balance` | (compra) saldo disponible < costo de lo que se ejecuta + reserva de lo que queda |
| 422 | `insufficient_tokens` | (venta) `quantity` > tokens disponibles del usuario para ese jugador |
| 422 | `player_not_quoted` | El jugador no tiene marketValue |

Ningún error deja órdenes, operaciones ni auditoría.

---

## `DELETE /orders/{id}`

Cancela una orden activa del usuario autenticado y libera lo reservado.

### 200 OK

El `OrderResponse` de la orden en estado `CANCELLED`, con `closedAt` y su `filledQuantity`; `trades` vacío.

### Errores

| HTTP | `error` | Cuándo |
|------|---------|--------|
| 400 | `validation_error` | `id` no numérico |
| 401 | (seguridad existente) | Sin credenciales |
| 403 | `order_forbidden` | La orden es de otro usuario (incluida la del creador de mercado) |
| 403 | `superuser_not_allowed` / `user_required` | Igual que en `POST` |
| 404 | `order_not_found` | La orden no existe |
| 409 | `order_closed` | La orden está `FILLED` o `CANCELLED` |

---

## `GET /orders/book?playerId={id}`

Libro agregado por precio del jugador y su cotización vigente.

### 200 OK

```json
{
  "playerId": 42,
  "marketValue": 1.2000,
  "sells": [
    { "price": 1.1000, "quantity": 7 },
    { "price": 1.2000, "quantity": 50 }
  ],
  "buys": [
    { "price": 1.0500, "quantity": 5 }
  ]
}
```

- `sells` de menor a mayor precio; `buys` de mayor a menor.
- `quantity` = suma de lo pendiente de las LIMIT activas a ese precio.
- Nunca incluye ids de órdenes ni de usuarios.
- Lados vacíos como `[]`.

### Errores

| HTTP | `error` | Cuándo |
|------|---------|--------|
| 400 | `validation_error` | Falta `playerId` o no es numérico |
| 401 | (seguridad existente) | Sin credenciales |
| 404 | `player_not_found` | El jugador no existe |

---

## Eventos internos

| Evento | Listener | Efecto |
|--------|----------|--------|
| `ApplicationReadyEvent` | `MarketMakerService` | Publica la venta del superusuario de cada jugador con tokens |
| `QuoteRunCompletedEvent` | `TokenIssuanceListener` (`@Order` TOKEN_ISSUANCE), luego `MarketMakerService` (`@TransactionalEventListener(fallbackExecution = true)`, `@Order` MARKET_MAKER) | Emite jugadores nuevos y después rehace la venta del superusuario a la nueva marketValue |
