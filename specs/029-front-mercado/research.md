# Research: Operar en el mercado desde el frontend

Sin `NEEDS CLARIFICATION` abiertos en el Technical Context. Estas son las decisiones de diseño y sus razones.

## R1 — Aritmética de precios

- **Decision**: precios y totales se manejan como enteros en diezmilésimos (`"1.2345"` → `12345`), parseados desde el texto del input con una regex (`^\d+(\.\d{1,4})?$`). La banda se compara en enteros: `mv × 80 ≤ precio × 100 ≤ mv × 120`. Para mostrar el rango: mínimo = `ceil(mv × 80 / 100)` y máximo = `floor(mv × 120 / 100)` diezmilésimos. Los totales son `cantidad × precio` en diezmilésimos. Los números de la API se convierten con `Math.round(n × 10000)`.
- **Rationale**: el backend valida la banda con `BigDecimal` y los extremos son válidos (027 FR-003). `0.8 * 1.2` en `Number` da `0.96` pero `1.0 * 1.2` da `1.2` y `0.7 * 1.2` da `0.84` con error; trabajar en enteros evita que un precio en el extremo se rechace en el front y se acepte en el back (o al revés). Con 100 tokens por jugador los enteros quedan muy por debajo de `Number.MAX_SAFE_INTEGER`.
- **Alternatives considered**: `Number` con epsilon (frágil en los extremos); una librería decimal (dependencia nueva para algo que resuelven enteros).

## R2 — Total estimado de una MARKET

- **Decision**: función pura `estimateMarket(levels, quantity)` que recorre el lado opuesto del libro ya ordenado como lo devuelve el backend (ventas ascendentes para comprar, compras descendentes para vender) y devuelve `{ fillable, total, unfilled }`. En LIMIT, el estimado es `cantidad × precio límite`.
- **Rationale**: es exactamente el recorrido del motor de calce (027 FR-007/FR-009) salvo la exclusión de órdenes propias, que el libro no permite conocer (no expone dueños); se documenta como estimado (spec, Assumptions). En LIMIT, `cantidad × límite` es lo máximo que se paga o reserva, que es lo que valida el backend para el saldo.
- **Alternatives considered**: estimar la LIMIT con el recorrido del libro hasta el límite (más preciso para la parte que se ejecuta, pero confunde porque la parte que queda reserva al precio límite).

## R3 — Cotización de referencia para la banda

- **Decision**: la banda se calcula con `book.marketValue` de `GET /orders/book`. Si el libro todavía no cargó o falló, el panel queda deshabilitado con el motivo ("Esperando el libro del jugador…" / "No pudimos cargar el libro").
- **Rationale**: es la misma cotización contra la que valida el backend al recibir la orden; la de la cabecera viene de `GET /players/{id}` y puede haber quedado vieja. Además, sin libro no se puede estimar una MARKET. Ante `price_out_of_band` se recarga el libro, y con él la cotización (FR-010); la cabecera de la 024 no se toca.
- **Alternatives considered**: usar `detail.quote.value` (puede diferir y no evita el rechazo); recargar también el detalle (cambia el comportamiento de la 024 sin necesidad).

## R4 — Confirmación accesible

- **Decision**: componente propio `components/ConfirmDialog.jsx`: overlay + `role="dialog"`, `aria-modal="true"`, `aria-labelledby`/`aria-describedby`; al abrirse enfoca el botón de confirmar, atrapa Tab/Shift+Tab, Escape cancela, y al cerrarse devuelve el foco al elemento que tenía el foco al abrir. Botones con `Button` existente (`primary` confirmar, `secondary` volver). Mientras `busy`, los botones se deshabilitan.
- **Rationale**: FR-004, FR-019 y FR-027 piden atrapar y devolver el foco. `<dialog>.showModal()` no está implementado de forma confiable en jsdom, lo que complicaría los tests de Vitest; un diálogo propio es chico, testeable y sin dependencias.
- **Alternatives considered**: `<dialog>` nativo (polyfill en tests); `window.confirm` (no es accesible ni sigue la estética, y bloquea la automatización del navegador).

## R5 — Toast y anuncio del resultado

- **Decision**: reusar `components/Toast.jsx` (ya es `<output>`, que los lectores anuncian, y se descarta a los 4 s). `TradingSection` y `MarketPage` tienen su propio estado `toast`. El resultado de la última orden queda además como texto en el panel (`aria-live="polite"`) hasta el próximo cambio del formulario.
- **Rationale**: FR-006 pide Toast; la spec (Assumptions) pide que el resultado no se pierda si el usuario no llega a leerlo en 4 s.
- **Alternatives considered**: un sistema global de toasts (más alcance del pedido; el Álbum ya usa el patrón local).

## R6 — Resumen del resultado de una orden

- **Decision**: función pura `summarizeOrder(order)` → texto según `status` y `type`:
  - `FILLED`: "Compraste/Vendiste N tokens a P promedio."
  - `PARTIALLY_FILLED` / `OPEN` (LIMIT): lo ejecutado (o "No se ejecutó nada") + "Quedaron R en el libro a L."
  - `CANCELLED` (MARKET con resto): lo ejecutado (o "No se ejecutó nada") + "Los R restantes se cancelaron por falta de contraparte."
  - Cancelación desde Mercado: `summarizeCancel(order)` → "Cancelaste tu compra/venta: se liberaron X cr / R tokens." (compra: `remainingQuantity × limitPrice` de la orden antes de cancelar; venta: `remainingQuantity`).
- **Rationale**: FR-006 y FR-019; usa sólo datos de la respuesta (`filledQuantity`, `remainingQuantity`, `averagePrice`, `limitPrice`), así el aviso refleja lo real y no lo estimado. Lo liberado se toma de la fila de la lista, porque la respuesta del DELETE trae `remainingQuantity` sin cambios.
- **Alternatives considered**: armar el texto en el componente (no testeable de forma aislada, más complejidad en JSX).

## R7 — Errores del mercado

- **Decision**: `marketErrors.js` exporta `marketErrorMessage(error)`: si es `NetworkError` o `ApiError` con `unexpected_error` → genérico; si es `ApiError`, un mapa por `code` (`insufficient_balance`, `insufficient_tokens`, `price_out_of_band`, `player_not_quoted`, `player_not_found`, `superuser_not_allowed`, `user_required`, `order_not_found`, `order_forbidden`, `order_closed`, `validation_error`); código desconocido → genérico. Nunca se muestra `error.message` del servidor. `isStale(error)` indica cuándo recargar (`price_out_of_band` → libro; `order_closed`/`order_not_found`/`order_forbidden` → lista).
- **Rationale**: FR-009, SC-003. El `httpClient` ya normaliza a `ApiError(status, code, message)` y llama `onUnauthorized` en 401 (FR-012), así que el front no maneja el 401 por su cuenta.
- **Alternatives considered**: mapear por status HTTP (dos códigos distintos comparten 400, 403 y 422).

## R8 — Hooks y respuestas fuera de orden

- **Decision**: `useOrderBook(playerId)` y `useMyOrders()` siguen el patrón de `useRanking`: `useEffect` con `AbortController`, `status` (`loading`/`ready`/`error`), `retryKey` para `reload()`; respuestas de un pedido abortado se descartan. `useTrade` guarda un `mountedRef`/`AbortController` del envío y descarta el resultado si la ficha se desmontó (edge case "navegar durante un envío"). `PlayerDetailPage` ya monta `PlayerDetail` con `key={id}`, así que cambiar de jugador resetea todo el estado (FR-016).
- **Rationale**: consistencia con el código existente y sus tests.
- **Alternatives considered**: una librería de datos (React Query): dependencia nueva.

## R9 — Ubicación en la ficha

- **Decision**: `TradingSection` (panel + libro) se monta en el estado `ready` de `PlayerDetail`, después de `PlayerHeader` y antes de `QuoteChart`. Grilla de dos columnas desde 900 px (panel | libro) y una columna debajo (panel primero). Si el libro trae `marketValue` nulo, el panel muestra "Este jugador todavía no tiene cotización" y queda deshabilitado (FR-008, research R3); el libro se muestra igual. La `quote` del detalle no se usa: sale del historial y puede ser nula aunque el jugador tenga `marketValue` y se pueda operar.
- **Rationale**: operar es la acción principal de la ficha a partir de esta feature; 900 px es el corte que ya usa el layout (Toast, BottomNav).
- **Alternatives considered**: un modal "Operar" (esconde el libro mientras se opera); al final de la ficha (poco visible).

## R10 — `GET /orders/mine` en el backend

- **Decision**:
  - **Repositorio**: la consulta existente `findByUserIdOrderByCreatedAtDescIdDesc(userId, Pageable)` no filtra por estado ni trae el jugador, así que no alcanza. Se agrega una única consulta `findActiveByUserWithPlayer(userId)` (`join fetch o.player`, `o.status in :active`, `order by o.createdAt desc, o.id desc`), con default method sobre `OrderStatus.ACTIVE`, como las demás del repositorio.
  - **Precio promedio**: para las órdenes `PARTIALLY_FILLED` se reusa `TradeRepository.findByBuyOrderIdOrSellOrderIdOrderByIdAsc` (la misma que usa la cancelación); las `OPEN` no tienen operaciones (`averagePrice` nulo) y no consultan nada.
  - **Respuesta**: `MyOrderResponse` = todos los campos de `OrderResponse` + `playerName`, construido con `MyOrderResponse.of(OrderResponse, String)`. `trades` va vacío, igual que en `DELETE /orders/{id}`: el formato es el mismo y la lista no muestra operaciones.
  - **Servicio**: `OrderService.myOrders(principalName)` resuelve el usuario con el `MarketUserResolver` existente, igual que el resto de los endpoints de órdenes (por eso hereda 403 `superuser_not_allowed`/`user_required`, sin código nuevo de errores).
- **Rationale**: alcance pedido por el equipo (FR-020 a FR-025). Una sola consulta nueva, sin N+1 sobre el jugador; las consultas de operaciones se limitan a las parciales, que son pocas.
- **Alternatives considered**: agregar `playerName` a `OrderResponse` (cambiaría las respuestas de buy/sell/cancel, contra FR-025); `@JsonUnwrapped` (el esquema de springdoc queda confuso); paginar (descartado por el equipo).

## R11 — Tests de páginas existentes

- **Decision**: `PlayerDetailPage.test.jsx` mockea `fetch` por URL; se agrega la respuesta de `/orders/book` (libro vacío por defecto) para que los tests existentes sigan pasando sin cambiar sus asserts. Los tests nuevos del panel y del libro se escriben aparte en `features/trading/` con `vi.mock('../../api/marketApi.js')`.
- **Rationale**: no reescribir tests de la 024; aislar la consola.
