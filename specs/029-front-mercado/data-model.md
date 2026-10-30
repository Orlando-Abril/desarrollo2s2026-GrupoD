# Data Model: Operar en el mercado desde el frontend

No hay entidades persistentes nuevas. Se describen los datos que circulan entre la API y la UI, y el estado en pantalla.

## Datos de la API

### Order (respuesta de `POST /orders/buy|sell`, `DELETE /orders/{id}`)

Contrato de la 027 (`specs/027-motor-calce/contracts/orders-api.md`).

| Campo | Tipo | Notas |
|-------|------|-------|
| `id` | number | |
| `playerId` | number | |
| `side` | `BUY` \| `SELL` | |
| `type` | `MARKET` \| `LIMIT` | |
| `limitPrice` | number \| null | 4 decimales; nulo en MARKET |
| `quantity` | number | entero |
| `filledQuantity` | number | entero |
| `remainingQuantity` | number | entero |
| `status` | `OPEN` \| `PARTIALLY_FILLED` \| `FILLED` \| `CANCELLED` | |
| `averagePrice` | number \| null | nulo si `filledQuantity` = 0 |
| `createdAt` / `closedAt` | ISO-8601 / ISO-8601 \| null | |
| `trades` | `{ id, quantity, price, total, executedAt }[]` | vacío en DELETE y en `/orders/mine` |

### MyOrder (elemento de `GET /orders/mine`)

`Order` + `playerName: string`. Sólo `status` `OPEN` o `PARTIALLY_FILLED`. Ver [contracts/orders-mine-api.md](contracts/orders-mine-api.md).

### OrderBook (respuesta de `GET /orders/book?playerId=`)

| Campo | Tipo | Notas |
|-------|------|-------|
| `playerId` | number | |
| `marketValue` | number \| null | cotización vigente; referencia de la banda |
| `sells` | `{ price, quantity }[]` | precio ascendente |
| `buys` | `{ price, quantity }[]` | precio descendente |

### ApiError (del `httpClient` existente)

`{ status, code, message }`. La UI decide sólo por `code` (ver [contracts/ui-contracts.md](contracts/ui-contracts.md#mensajes-de-error)).

## Estado en pantalla

### OrderDraft (formulario del panel, `useTrade`)

| Campo | Tipo | Valor inicial | Reglas |
|-------|------|---------------|--------|
| `side` | `BUY` \| `SELL` | `BUY` | |
| `type` | `MARKET` \| `LIMIT` | `MARKET` | al pasar a MARKET se descarta el precio y su error |
| `quantity` | string (texto del input) | `''` | `^[1-9]\d*$` → "La cantidad debe ser un número entero mayor a 0"; además el total en diezmilésimos debe cumplir `Number.isSafeInteger` → "La cantidad es demasiado grande." (sin tope de negocio) |
| `limitPrice` | string | `''` | sólo LIMIT: obligatorio; `^\d+(\.\d{1,4})?$`; > 0; dentro de la banda |

Derivados (puros, `tradeMath.js`):

- `band` = `{ min, max }` en diezmilésimos a partir de `marketValue` (research R1); nulo si no hay `marketValue`.
- `errors` = `{ quantity?, limitPrice? }` (sólo se muestran después del primer intento de envío o al salir del campo).
- `estimate`:
  - LIMIT válida: `{ kind: 'limit', total }` = cantidad × precio.
  - MARKET: `{ kind: 'market', total, fillable, unfilled }` recorriendo `sells` (compra) o `buys` (venta); `unfilled > 0` dispara el aviso de liquidez insuficiente y, si `fillable = 0`, "no hay compradores/vendedores".

Mensajes de banda: "El precio debe estar entre {min} y {max}" (y como hint permanente: "Rango permitido: {min} – {max}").

### Ciclo del envío (`useTrade.phase`)

```text
editing ──submit (válido)──▶ confirming ──confirmar──▶ sending ──201──▶ editing (+ lastResult, toast, reload libro, limpia cantidad/precio)
   ▲                            │                         │
   └───────volver/Escape────────┘                         └──error──▶ editing (+ submitError; conserva datos;
                                                                        price_out_of_band → reload libro)
```

- En `sending` el botón de enviar y los de la confirmación quedan deshabilitados (FR-005).
- Si el componente se desmonta en `sending`, la respuesta se descarta.

### OrderBookState (`useOrderBook`)

`{ status: 'loading' | 'ready' | 'error', book: OrderBook | null, reload() }`.

### MyOrdersState (`useMyOrders`)

`{ status: 'loading' | 'ready' | 'error', orders: MyOrder[], loadError: Error | null, cancellingId: number | null, cancelError: string | null, reload(), cancel(order) }`

- `loadError`: el error de `GET /orders/mine` cuando `status` es `error` (p. ej. 403 `superuser_not_allowed`), para mostrar su mensaje.
- `cancelError`: el mensaje de `marketErrorMessage(error, { action: 'cancel' })` de la última cancelación fallida.

- `cancel(order)`: `cancellingId = order.id` → `DELETE` → éxito: se quita de `orders`, toast con lo liberado → error: mensaje; `order_closed`/`order_not_found`/`order_forbidden` → `reload()`.
- Mientras `cancellingId` no es nulo, el botón "Cancelar" de esa orden queda deshabilitado.

### Confirmación (`ConfirmDialog`)

`{ open, title, children (resumen), confirmLabel, onConfirm, onCancel, busy }`. Panel: lado, tipo, jugador, cantidad, precio (LIMIT) y total estimado. Mercado: jugador, lado, pendiente y precio.
