# Contratos de UI: consola de trading

## `api/marketApi.js`

Todas las funciones usan `request` del `httpClient` existente (token, timeout de 10 s, `ApiError`/`NetworkError`, `onUnauthorized` en 401) y aceptan `{ signal }` opcional.

| Función | Pedido | Devuelve |
|---------|--------|----------|
| `buyOrder({ playerId, quantity, type, limitPrice }, opts)` | `POST /orders/buy` | `Order` |
| `sellOrder({ playerId, quantity, type, limitPrice }, opts)` | `POST /orders/sell` | `Order` |
| `cancelOrder(id, opts)` | `DELETE /orders/{id}` | `Order` |
| `getOrderBook(playerId, opts)` | `GET /orders/book?playerId=` | `OrderBook` |
| `getMyOrders(opts)` | `GET /orders/mine` | `MyOrder[]` |

Body de buy/sell: `quantity` como número entero; `limitPrice` como número sólo en LIMIT (se omite en MARKET, porque el backend rechaza MARKET con precio).

## Hooks (`features/trading/`)

| Hook | Firma | Expone |
|------|-------|--------|
| `useOrderBook` | `(playerId)` | `{ status, book, reload }` |
| `useTrade` | `({ playerId, playerName, book, onExecuted, onStale })` | `{ draft, setField, errors, band, estimate, phase, submitError, lastResult, submit, confirm, cancelConfirm }`. `onExecuted(order)` lo usa `TradingSection` para el toast y para `reload()` del libro; `useTrade` llama `onStale()` ante `price_out_of_band`. |
| `useMyOrders` | `()` | `{ status, orders, loadError, cancellingId, cancelError, reload, cancel }` |

## Componentes

### `components/ConfirmDialog`

Props: `open`, `title`, `children`, `confirmLabel`, `cancelLabel = 'Volver'`, `onConfirm`, `onCancel`, `busy`.
Accesibilidad: `role="dialog"`, `aria-modal="true"`, título como `aria-labelledby`; foco inicial en confirmar; Tab atrapado; Escape = `onCancel` (salvo `busy`); al cerrar devuelve el foco al elemento previo.

### `features/trading/TradingSection`

Props: `player` (`{ id, fullName }`). Sin prop `quote`: la cotización sale de `book.marketValue`. Arma `useOrderBook` + `TradePanel` + `OrderBook` y el `Toast`. Encabezado `h2` "Operar".

### `features/trading/TradePanel`

- Lado: grupo de dos botones con `aria-pressed` ("Comprar" / "Vender").
- Tipo: radios "Al mercado (MARKET)" / "Con precio límite (LIMIT)".
- Campos con `Field` existente: "Cantidad de tokens" (`inputMode="numeric"`) y, en LIMIT, "Precio límite (cr)" (`inputMode="decimal"`) con hint del rango permitido.
- Total estimado en un `<p aria-live="polite">`, con la palabra "estimado"; avisos de liquidez.
- Botón "Revisar compra" / "Revisar venta" → `ConfirmDialog` con "Confirmar compra" / "Confirmar venta".
- Error del envío en un bloque `role="alert"`; resultado de la última orden en `aria-live="polite"`.
- Deshabilitado (con motivo visible) si el libro no está `ready` o si `book.marketValue` es nulo ("Este jugador todavía no tiene cotización").

### `features/trading/OrderBook`

Tabla accesible: `<caption>` "Libro de órdenes de {jugador}", columnas Lado / Precio (cr) / Tokens; filas de venta (precio ascendente) y luego de compra (precio descendente), con el lado en texto ("Venta"/"Compra") además del color. Cotización vigente visible. Estados: cargando, lado vacío ("Sin órdenes de venta" / "Sin órdenes de compra"), error con `EstadoPanel` + "Reintentar".

### `features/trading/MyOrdersList`

En ancho ≥ 900 px tabla (Jugador, Lado, Tipo, Precio, Cantidad, Ejecutado, Pendiente, Estado, Creada, acción); debajo, tarjetas apiladas con los mismos datos. El nombre del jugador enlaza a `/jugadores/{playerId}`. Botón "Cancelar" con `aria-label="Cancelar orden de {lado} de {jugador}"`.

### `pages/MarketPage` (ruta protegida `/mercado`)

`page-heading` "Mercado" / "Tus órdenes abiertas". Estados: cargando, error (`EstadoPanel` + "Reintentar"), vacío (`EstadoPanel` "No tenés órdenes abiertas" + acción "Ir al álbum"), lista. `ConfirmDialog` para cancelar y `Toast` con lo liberado.

## Navegación

- `AppLayout.jsx`: `{ id: 'mercado', label: 'Mercado', numero: '03', ruta: '/mercado', habilitado: true }`.
- `App.jsx`: `<Route path="/mercado" element={<MarketPage />} />` dentro de `ProtectedRoute`.

## Mensajes de error

`marketErrorMessage(error)`:

| `code` | Mensaje |
|--------|---------|
| `insufficient_balance` | No tenés saldo suficiente para esta compra. |
| `insufficient_tokens` | No tenés tokens disponibles suficientes de este jugador (pueden estar reservados en órdenes abiertas). |
| `price_out_of_band` | El precio está fuera del rango permitido (±20% de la cotización). Actualizamos la cotización: revisá el precio. |
| `player_not_quoted` | Este jugador todavía no tiene cotización, así que no se puede operar. |
| `player_not_found` | Este jugador ya no existe en el mercado. |
| `superuser_not_allowed` | El superusuario no puede operar desde la aplicación. |
| `user_required` | Tu credencial no está asociada a un usuario. Ingresá de nuevo. |
| `order_not_found` | Esta orden ya no existe. |
| `order_forbidden` | No podés cancelar esta orden. |
| `order_closed` | Esta orden ya no está activa. |
| `validation_error` | Revisá los datos de la orden. |
| (validación local) total de la cantidad fuera de `Number.isSafeInteger` | La cantidad es demasiado grande. |
| `NetworkError`, `unexpected_error` u otro | No pudimos enviar la orden. Probá de nuevo. (Mercado: "No pudimos cancelar la orden. Probá de nuevo.") |

## Formato

- Precios y totales: `formatPrice(units)` → 4 decimales `es-AR` + " cr" (p. ej. "1,2000 cr").
- Cantidades: `formatInteger` existente.
- Fechas: `formatDateTime` existente de `player-detail/quoteFormat.js`.
