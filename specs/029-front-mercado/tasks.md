---

description: "Task list for Operar en el mercado desde el frontend"
---

# Tasks: Operar en el mercado desde el frontend

**Input**: Design documents from `/specs/029-front-mercado/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: Obligatorios: los pidió el plan (validaciones, errores del backend, confirmación, cancelación y actualización del libro) y el endpoint de backend pide MockMvc de 200 y 401. En cada fase se escriben primero los tests (Vitest + Testing Library; `vi.mock('../../api/marketApi.js')` en los tests de `features/trading/`, `fetch` mockeado por URL en los de páginas, como `frontend/src/pages/PlayerDetailPage.test.jsx`), se confirma que fallan por la capacidad ausente y recién después se implementa.

**Organization**: Tareas agrupadas por historia de `spec.md`. Archivos protegidos de Entrega 1: sólo se tocan `frontend/src/App.jsx` (una ruta) y `frontend/src/layout/AppLayout.jsx` (el ítem `mercado`). En el backend no se tocan `User`, `UserRepository`, `application.properties`, `PlayerCatalogService` ni `GlobalExceptionHandler`, ni el comportamiento de `POST /orders/buy|sell`, `DELETE /orders/{id}` y `GET /orders/book`. Sin dependencias nuevas.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede correr en paralelo con otras tareas [P] del mismo bloque: toca archivos distintos y no depende de tareas incompletas.
- **[Story]**: Historia de usuario de `spec.md` (US1–US7).
- Rutas exactas desde la raíz del repositorio.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Línea base verde.

- [X] T001 Ejecutar `npm test`, `npm run lint` y `npm run build` en `frontend/` y `./mvnw test` en `backend/` sobre la rama `feature/front-mercado`, y registrar en el handoff cualquier fallo preexistente antes de tocar código

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Cliente de API, aritmética de precios, formato, mensajes de error, diálogo de confirmación y hook del libro, que usan todas las historias del frontend.

**⚠️ CRITICAL**: Ninguna historia del frontend empieza antes de terminar esta fase.

### Tests

- [X] T002 [P] Crear `frontend/src/api/marketApi.test.js` (mismo patrón que `frontend/src/api/quotesApi.test.js`, `fetch` mockeado) que verifique método, URL y body de cada función de `contracts/ui-contracts.md`: `buyOrder` → `POST /orders/buy` y `sellOrder` → `POST /orders/sell` con body `{ playerId, quantity, type, limitPrice }` donde `quantity` es número entero y `limitPrice` es número **sólo en LIMIT** (en MARKET la clave no viaja); `cancelOrder(id)` → `DELETE /orders/{id}`; `getOrderBook(playerId)` → `GET /orders/book?playerId={id}`; `getMyOrders()` → `GET /orders/mine`; y que todas pasan `signal`
- [X] T003 [P] Crear `frontend/src/features/trading/tradeMath.test.js` con casos de: `toUnits('1.2345')` = `12345`, `toUnits('1')` = `10000`, `toUnits` devuelve `null` para `''`, `'abc'`, `'-1'`, `'1.23456'` (regex `^\d+(\.\d{1,4})?$`); `bandFor(marketValue)` con mv `1.0000` → `{ min: 8000, max: 12000 }` y mv `1.2345` → `{ min: ceil(12345×80/100) = 9876, max: floor(12345×120/100) = 14814 }`, y `null` si `marketValue` es nulo; `isWithinBand` con extremos incluidos (mv 1.0000: `0.8000` y `1.2000` válidos, `0.7999` y `1.2001` no), comparando en enteros `mv × 80 ≤ precio × 100 ≤ mv × 120` (research R1)
- [X] T004 [P] Crear `frontend/src/features/trading/marketErrors.test.js` que verifique que `marketErrorMessage(error)` devuelve para cada `code` de la tabla "Mensajes de error" de `contracts/ui-contracts.md` exactamente su texto (`insufficient_balance`, `insufficient_tokens`, `price_out_of_band`, `player_not_quoted`, `player_not_found`, `superuser_not_allowed`, `user_required`, `order_not_found`, `order_forbidden`, `order_closed`, `validation_error`); que `NetworkError`, `ApiError` con `unexpected_error` y un código desconocido devuelven el genérico ("No pudimos enviar la orden. Probá de nuevo." por defecto y "No pudimos cancelar la orden. Probá de nuevo." con `{ action: 'cancel' }`); que nunca devuelve `error.message` del servidor; y que `staleTarget(error)` devuelve `'book'` para `price_out_of_band`, `'orders'` para `order_closed`/`order_not_found`/`order_forbidden` y `null` para el resto
- [X] T005 [P] Crear `frontend/src/components/ConfirmDialog.test.jsx` que verifique: con `open` se renderiza `role="dialog"` con `aria-modal="true"` y `aria-labelledby` apuntando al título; el foco inicial está en el botón de confirmar; Tab desde el último botón vuelve al primero y Shift+Tab desde el primero va al último; Escape llama `onCancel` (y no lo llama si `busy`); "Volver" llama `onCancel` y confirmar llama `onConfirm`; con `busy` ambos botones están deshabilitados; al pasar `open` a `false` el foco vuelve al botón que lo abrió
- [X] T006 [P] Crear `frontend/src/features/trading/useOrderBook.test.jsx` (con `vi.mock('../../api/marketApi.js')`) que verifique: arranca en `loading`, pasa a `ready` con el `book`; ante error pasa a `error` y `reload()` vuelve a pedir y llega a `ready`; `reload()` con libro ya cargado vuelve a llamar `getOrderBook`; al desmontar se aborta el pedido y su respuesta no actualiza estado

### Implementation

- [X] T007 [P] Crear `frontend/src/api/marketApi.js` con `buyOrder`, `sellOrder`, `cancelOrder`, `getOrderBook` y `getMyOrders` sobre `request` de `frontend/src/api/httpClient.js`, con la firma `(args, { signal } = {})` de `frontend/src/api/quotesApi.js`, `encodeURIComponent` en ids, y `limitPrice` omitido del body cuando `type` es `MARKET` (hace pasar T002)
- [X] T008 [P] Crear `frontend/src/features/trading/tradeMath.js` con `toUnits(text)` (texto del input), `priceToUnits(n) = Math.round(n * 10000)` (números que llegan de la API: precios del libro, `marketValue`, `limitPrice`, `averagePrice`), `fromUnits(units)` (número con 4 decimales para el body), `bandFor(marketValue)` (convierte con `priceToUnits`) y `isWithinBand(units, band)` en enteros de diezmilésimos según research R1, sin aritmética de punto flotante en las comparaciones (hace pasar T003)
- [X] T009 [P] Crear `frontend/src/features/trading/tradeFormat.js` con `formatPrice(units)` → 4 decimales `es-AR` + `' cr'` (p. ej. `'1,2000 cr'`), `sideLabel(side)` (`BUY` → "Compra", `SELL` → "Venta"), `typeLabel(type)` (`MARKET` → "Al mercado", `LIMIT` → "Límite") y `statusLabel(status)` (`OPEN` → "Abierta", `PARTIALLY_FILLED` → "Parcial", `FILLED` → "Ejecutada", `CANCELLED` → "Cancelada"); reusar `formatInteger` de `frontend/src/utils/format.js` sin modificarlo
- [X] T010 [P] Crear `frontend/src/features/trading/marketErrors.js` con `marketErrorMessage(error, { action } = {})` y `staleTarget(error)` según research R7, decidiendo sólo por `error.code` de `ApiError` o por `instanceof NetworkError` (hace pasar T004)
- [X] T011 [P] Crear `frontend/src/components/ConfirmDialog.jsx` y `frontend/src/components/ConfirmDialog.css` con props `open`, `title`, `children`, `confirmLabel`, `cancelLabel = 'Volver'`, `onConfirm`, `onCancel`, `busy`; overlay fijo, panel con `var(--borde-grueso)` y `var(--sombra-md)` como `frontend/src/components/Toast.css`, botones con `frontend/src/components/Button.jsx` (`primary` confirmar, `secondary` volver), foco atrapado, Escape y devolución de foco según research R4; sin `<dialog>` nativo ni `window.confirm` (hace pasar T005)
- [X] T012 Crear `frontend/src/features/trading/useOrderBook.js` (`(playerId)` → `{ status: 'loading' | 'ready' | 'error', book, reload }`) con el patrón `useEffect` + `AbortController` + `retryKey` de `frontend/src/features/ranking/useRanking.js` (depende de T007; hace pasar T006)

**Checkpoint**: `npm test` verde con T002–T006; todavía no hay cambios visibles en la app.

---

## Phase 3: User Story 1 - Comprar tokens desde la ficha del jugador (Priority: P1) 🎯 MVP

**Goal**: En la ficha del jugador, comprar con MARKET o LIMIT viendo el total estimado, confirmando antes de enviar y recibiendo un Toast con lo ejecutado y lo que quedó en el libro.

**Independent Test**: Con el libro del jugador con la venta del superusuario, comprar con MARKET y con LIMIT y verificar estimado, confirmación, Toast y que se pide el libro de nuevo.

### Tests for User Story 1

- [X] T013 [P] [US1] Agregar a `frontend/src/features/trading/tradeMath.test.js` casos de `priceToUnits` (`1.2` → `12000`, `1.1` → `11000`, `1.2345` → `12345`, `0.07` → `700`), de `estimateMarket(levels, quantity)` (los `price` de los niveles son números de la API y se convierten con `priceToUnits`) → `{ fillable, total, unfilled }` (niveles `[{price:1.1,quantity:3},{price:1.2,quantity:50}]`, cantidad 8 → `fillable 8`, `total 3×11000 + 5×12000 = 93000` unidades, `unfilled 0`; cantidad 60 → `fillable 53`, `unfilled 7`; niveles vacíos → `fillable 0`), `estimateLimit(quantity, priceUnits)` = cantidad × precio, y `summarizeOrder(order)` para cada caso de research R6: `FILLED` ("Compraste 10 tokens a 1,2000 cr promedio."), LIMIT `PARTIALLY_FILLED` (ejecutado + "Quedaron 6 en el libro a 1,3000 cr."), LIMIT `OPEN` ("No se ejecutó nada. Quedaron 10 en el libro a 1,1000 cr."), MARKET `CANCELLED` con y sin ejecutado ("Los 6 restantes se cancelaron por falta de contraparte.")
- [X] T014 [P] [US1] Crear `frontend/src/features/trading/TradePanel.test.jsx` (con `vi.mock('../../api/marketApi.js')`, renderizando `TradingSection` con `player={{ id: 42, fullName: 'Jugador Test' }}` y un libro con `marketValue: 1.2` y `sells: [{ price: 1.2, quantity: 100 }]`; como renderiza `TradingSection`, el test pasa recién con T018) que verifique: con `marketValue: null` en el libro el panel está deshabilitado con "Este jugador todavía no tiene cotización" y la sección del libro está presente; "Comprar" está presionado por defecto (`aria-pressed="true"`) y MARKET elegido sin campo de precio; cantidad 10 en MARKET muestra "12,0000 cr" con la palabra "estimado"; en LIMIT con precio `1.1` muestra "11,0000 cr"; "Revisar compra" abre el diálogo con lado, tipo, jugador, cantidad, precio (LIMIT) y total; "Volver" cierra sin llamar `buyOrder`; "Confirmar compra" llama `buyOrder({ playerId: 42, quantity: 10, type: 'MARKET' })` una sola vez aunque se haga clic dos veces (botones deshabilitados en `sending`); al resolver aparece el Toast con `summarizeOrder`, el resultado queda en el bloque `aria-live`, cantidad y precio se limpian conservando lado y tipo, y `getOrderBook` se vuelve a llamar; si la ficha se desmonta durante el envío no hay errores ni Toast

### Implementation for User Story 1

- [X] T015 [US1] Agregar a `frontend/src/features/trading/tradeMath.js` `estimateMarket(levels, quantity)`, `estimateLimit(quantity, priceUnits)` y `summarizeOrder(order)` (usando `tradeFormat.js`; `estimateMarket` convierte el `price` de cada nivel con `priceToUnits` y `summarizeOrder` convierte `averagePrice` y `limitPrice` con `priceToUnits` antes de `formatPrice`; el aviso usa siempre `filledQuantity`, `remainingQuantity`, `averagePrice` y `limitPrice` de la respuesta, nunca el estimado) (hace pasar T013)
- [X] T016 [US1] Crear `frontend/src/features/trading/useTrade.js` (`({ playerId, playerName, book, onExecuted, onStale })` → `{ draft, setField, errors, band, estimate, phase, submitError, lastResult, submit, confirm, cancelConfirm }`) con el ciclo `editing → confirming → sending → editing` de data-model.md: `draft` inicial `{ side: 'BUY', type: 'MARKET', quantity: '', limitPrice: '' }`; `band` desde `book.marketValue` (research R3); `submit` sólo pasa a `confirming` si no hay errores; `confirm` llama `buyOrder`/`sellOrder` según `side` con `quantity` entera y `limitPrice` vía `fromUnits` sólo en LIMIT; al resolver guarda `lastResult`, limpia `quantity` y `limitPrice` y llama `onExecuted(order)`; descarta la respuesta si se desmontó (research R8) (depende de T008, T015)
- [X] T017 [US1] Crear `frontend/src/features/trading/TradePanel.jsx` y `frontend/src/features/trading/TradePanel.css` según `contracts/ui-contracts.md`: grupo de botones "Comprar"/"Vender" con `aria-pressed`, radios "Al mercado (MARKET)"/"Con precio límite (LIMIT)", campos con `frontend/src/components/Field.jsx` ("Cantidad de tokens" con `inputMode="numeric"`; en LIMIT "Precio límite (cr)" con `inputMode="decimal"`), total estimado en `<p aria-live="polite">`, botón "Revisar compra"/"Revisar venta", `ConfirmDialog` con "Confirmar compra"/"Confirmar venta" y `busy` en `sending`, y bloque `aria-live="polite"` con `lastResult`; panel deshabilitado con motivo visible si el libro no está `ready` ("Esperando el libro del jugador…") o si `book.marketValue` es nulo ("Este jugador todavía no tiene cotización"), sin usar la `quote` del detalle (research R3, R9) (depende de T011, T016)
- [X] T018 [US1] Crear `frontend/src/features/trading/TradingSection.jsx` y `frontend/src/features/trading/TradingSection.css` con la única prop `player` (`{ id, fullName }`, sin prop `quote`): `<section>` con `h2` "Operar", `useOrderBook(player.id)`, `TradePanel` con `onExecuted` que muestra el `Toast` existente (`frontend/src/components/Toast.jsx`, estado `toast` local como `frontend/src/pages/AlbumPage.jsx`) y llama `reload()` del libro, y `onStale` que llama `reload()`; grilla de dos columnas desde 900 px y una columna debajo con el panel primero (research R9); por ahora el libro se muestra como placeholder que se reemplaza en T030 (depende de T012, T017; hace pasar T014)
- [X] T019 [US1] En `frontend/src/pages/PlayerDetailPage.jsx`, montar `<TradingSection player={player} />` en el estado `ready`, entre `PlayerHeader` y `QuoteChart`, sin cambiar los demás componentes; en `frontend/src/pages/PlayerDetailPage.test.jsx`, agregar al mock de `fetch` la respuesta de `/orders/book` (libro vacío `{ playerId, marketValue, sells: [], buys: [] }` por defecto) sin cambiar los asserts existentes (research R11), y un test de que la ficha `ready` muestra la sección "Operar" (depende de T018)

**Checkpoint**: Se puede comprar desde la ficha; `npm test` verde.

---

## Phase 4: User Story 2 - Vender tokens desde la ficha del jugador (Priority: P1)

**Goal**: El mismo panel permite vender con MARKET o LIMIT.

**Independent Test**: Con compras en el libro, vender MARKET y LIMIT; sin compras, ver el aviso de que la MARKET se cancelaría.

### Tests for User Story 2

- [X] T020 [P] [US2] Agregar a `frontend/src/features/trading/TradePanel.test.jsx`: al elegir "Vender" el estimado MARKET recorre `buys` de mayor a menor (`buys: [{ price: 1.15, quantity: 2 }, { price: 1.0, quantity: 5 }]`, cantidad 4 → `2×1,1500 + 2×1,0000 = 4,3000 cr`); con `buys: []` aparece "No hay compradores: la orden se cancelaría sin ejecutarse" y se puede enviar igual; con liquidez parcial aparece "Sólo hay N tokens del otro lado; los R restantes se cancelarían"; el botón dice "Revisar venta" y el diálogo "Confirmar venta"; confirmar llama `sellOrder` (no `buyOrder`); una venta LIMIT `OPEN` muestra en el Toast "No se ejecutó nada. Quedaron 6 en el libro a 1,2000 cr."

### Implementation for User Story 2

- [X] T021 [US2] En `frontend/src/features/trading/useTrade.js` y `frontend/src/features/trading/TradePanel.jsx`, usar `book.buys` para el estimado MARKET cuando `side` es `SELL` y `book.sells` cuando es `BUY` (precios convertidos con `priceToUnits` dentro de `estimateMarket`), mostrar los avisos de liquidez a partir de `estimate.fillable`/`estimate.unfilled` (con texto "compradores"/"vendedores" según el lado) y los textos de botón y diálogo según el lado (hace pasar T020)

**Checkpoint**: Compra y venta funcionan desde la ficha.

---

## Phase 5: User Story 3 - Validaciones y errores claros (Priority: P1)

**Goal**: El panel frena lo inválido antes de enviar y explica en lenguaje claro cada rechazo del mercado.

**Independent Test**: Cargar datos inválidos y ver los mensajes por campo; simular cada código de error y ver su mensaje.

### Tests for User Story 3

- [X] T022 [P] [US3] Agregar a `frontend/src/features/trading/tradeMath.test.js` casos de `validateDraft(draft, band)` → `{ quantity?, limitPrice? }`: cantidad `''`, `'0'`, `'-3'`, `'2.5'`, `'abc'` → "La cantidad debe ser un número entero mayor a 0" (regex `^[1-9]\d*$`); una cantidad cuyo total en diezmilésimos no cumple `Number.isSafeInteger` (p. ej. `'999999999999999'` a 1.2000) → "La cantidad es demasiado grande.", sin tope de negocio; en LIMIT precio `''` → "Ingresá el precio límite", `'0'` → "El precio debe ser mayor a 0", `'1.23456'` → "El precio puede tener hasta 4 decimales", fuera de banda con mv 1.0000 (`'0.7999'`, `'1.2001'`) → "El precio debe estar entre 0,8000 cr y 1,2000 cr", y `'0.8'`/`'1.2'` sin error; en MARKET el precio nunca se valida
- [X] T023 [P] [US3] Agregar a `frontend/src/features/trading/TradePanel.test.jsx`: con datos inválidos "Revisar compra" no abre el diálogo, cada campo inválido tiene `aria-invalid="true"` y `aria-describedby` apuntando a su mensaje, y el foco va al primer campo inválido; en LIMIT el hint "Rango permitido: 0,9600 cr – 1,4400 cr" (mv 1.2) se ve junto al campo; al pasar a MARKET desaparecen el precio y su error; para cada código (`insufficient_balance`, `insufficient_tokens`, `price_out_of_band`, `player_not_quoted`, `player_not_found`, `superuser_not_allowed`, `user_required`, `validation_error`) y para `NetworkError`, al rechazar `buyOrder` se muestra el mensaje de `marketErrorMessage` en `role="alert"`, la cantidad y el precio se conservan, y no aparece Toast; con `price_out_of_band` además se vuelve a llamar `getOrderBook`; nunca se muestra el `message` crudo del servidor

### Implementation for User Story 3

- [X] T024 [US3] Agregar `validateDraft(draft, band)` a `frontend/src/features/trading/tradeMath.js` con una función por campo (`validateQuantity`, `validateLimitPrice`) para mantener baja la complejidad cognitiva (Sonar, §3.2), `validateQuantity` rechazando también con "La cantidad es demasiado grande." un total en diezmilésimos que no cumple `Number.isSafeInteger` (sin tope de negocio), y los mensajes exactos de T022 con `formatPrice`; los mensajes de T022 y de la tabla "Mensajes de error" de `contracts/ui-contracts.md` son la fuente única de textos (hace pasar T022)
- [X] T025 [US3] En `frontend/src/features/trading/useTrade.js`, mostrar los errores de campo después del primer intento de envío o al salir del campo (`touched`), descartar `limitPrice` y su error al pasar a MARKET, y ante rechazo de `confirm` guardar `submitError = marketErrorMessage(error)`, volver a `editing` conservando el `draft` y llamar `onStale()` si `staleTarget(error) === 'book'`; el 401 no se trata acá (lo maneja `onUnauthorized` del `httpClient`, FR-012) (depende de T010, T024)
- [X] T026 [US3] En `frontend/src/features/trading/TradePanel.jsx`, pasar `error` y `hint` ("Rango permitido: {min} – {max}") a cada `Field`, mover el foco al primer campo inválido cuando `submit` falla, y mostrar `submitError` en un bloque `role="alert"` que se limpia al editar (hace pasar T023)

**Checkpoint**: Ninguna orden inválida se envía; todos los rechazos se explican.

---

## Phase 6: User Story 4 - Ver el libro de órdenes del jugador (Priority: P1)

**Goal**: La ficha muestra el libro con ventas y compras por precio, se actualiza después de operar y tiene estados de carga, vacío y error.

**Independent Test**: Con un libro de varios niveles, ver el orden y los lados vacíos; operar y ver la recarga; simular un error y reintentar.

### Tests for User Story 4

- [X] T027 [P] [US4] Crear `frontend/src/features/trading/OrderBook.test.jsx` que verifique: tabla con `<caption>` "Libro de órdenes de Jugador Test" y encabezados Lado / Precio (cr) / Tokens; con `sells: [{1.1,7},{1.2,50}]` y `buys: [{1.05,5}]` las filas salen en ese orden (ventas ascendentes, luego compras descendentes) con "Venta"/"Compra" en texto; cotización vigente visible; `sells: []` → "Sin órdenes de venta" y `buys: []` → "Sin órdenes de compra"; `status: 'loading'` muestra "Cargando libro…" con `aria-busy="true"`; `status: 'error'` muestra `EstadoPanel` con "Reintentar" que llama `onRetry`
- [X] T028 [P] [US4] Agregar a `frontend/src/features/trading/TradePanel.test.jsx` que, después de una compra exitosa, la tabla del libro muestra los niveles de la segunda respuesta de `getOrderBook` (`sells: [{1.2, 90}]`); que si el libro falla, el panel muestra "No pudimos cargar el libro" deshabilitado y el resto de la ficha sigue renderizada; y que con `marketValue: null` y `sells: [{ price: 1.0, quantity: 100 }]` el panel está deshabilitado con "Este jugador todavía no tiene cotización" y la tabla del libro muestra igual la fila de venta
- [X] T029 [P] [US4] Agregar a `frontend/src/pages/PlayerDetailPage.test.jsx` que, si `/orders/book` responde 500, la cabecera, el gráfico y las estadísticas se ven igual y la sección "Operar" muestra el error con "Reintentar"

### Implementation for User Story 4

- [X] T030 [US4] Crear `frontend/src/features/trading/OrderBook.jsx` y `frontend/src/features/trading/OrderBook.css` (props `playerName`, `status`, `book`, `onRetry`) según `contracts/ui-contracts.md`, con colores de compra/venta desde `frontend/src/styles/tokens.css` y el lado siempre en texto; convertir con `priceToUnits` antes de `formatPrice` el `price` de cada nivel y `marketValue`; reemplazar el placeholder de `frontend/src/features/trading/TradingSection.jsx` por `<OrderBook>` (hace pasar T027–T029)

**Checkpoint**: MVP completo de la ficha (US1–US4).

---

## Phase 7: User Story 6 - Listar mis órdenes activas (backend) (Priority: P2)

**Goal**: `GET /orders/mine` devuelve las órdenes activas del usuario autenticado. Va antes de US5 porque la pantalla de Mercado lo consume.

**Independent Test**: Con órdenes de dos usuarios en distintos estados, pedir `GET /orders/mine` como uno y ver sólo las propias y activas, en orden; sin credenciales, 401.

### Tests for User Story 6

- [X] T031 [US6] Crear `backend/src/test/java/com/example/demo/controller/OrderControllerMineTest.java` con `@MarketMockMvcTest` y el setup de `OrderControllerCancelTest` (`MarketTestData.clean`, `MarketSeed`, `AuthFixtures.apiKey`), con: (1) usuario A con órdenes `OPEN`, `PARTIALLY_FILLED` (con `seed.fill(..., 4)` y su operación), `FILLED` y `CANCELLED`, y usuario B con una orden activa → `GET /orders/mine` con la key de A responde 200 con exactamente las dos activas de A, la más nueva primero, cada una con `playerName` igual a `Player.fullName`, `trades` vacío, `averagePrice` nulo en la `OPEN` y con valor en la parcial; (2) usuario sin órdenes activas → 200 con `[]`; (3) sin credenciales → 401; antes de T032–T035 confirmar que falla el test de 200 (sin fijar con qué código falla)

### Implementation for User Story 6

- [X] T032 [P] [US6] En `backend/src/main/java/com/example/demo/repository/market/MarketOrderRepository.java`, agregar `@Query("select o from MarketOrder o join fetch o.player where o.user.id = :userId and o.status in :active order by o.createdAt desc, o.id desc") List<MarketOrder> findActiveByUserWithPlayer(Long userId, Collection<OrderStatus> active)` y un default method `findActiveByUserWithPlayer(Long userId)` sobre `OrderStatus.ACTIVE`, con Javadoc de una línea, sin tocar las consultas existentes (research R10)
- [X] T033 [P] [US6] Crear `backend/src/main/java/com/example/demo/dto/market/MyOrderResponse.java`: record con los mismos campos y `@Schema` que `OrderResponse` más `@Schema(example = "Lionel Messi") String playerName` después de `playerId`, y `static MyOrderResponse of(OrderResponse order, String playerName)`; sin cambiar `OrderResponse`
- [X] T034 [US6] En `backend/src/main/java/com/example/demo/service/market/OrderService.java`, agregar `@Transactional(readOnly = true) public List<MyOrderResponse> myOrders(String principalName)`: resuelve el usuario con `MarketUserResolver.resolve` (hereda `superuser_not_allowed`/`user_required`), llama `findActiveByUserWithPlayer`, y por orden arma `OrderResponse.of(order, executed, List.of())` con `executed` = `tradeRepository.findByBuyOrderIdOrSellOrderIdOrderByIdAsc(id, id)` sólo si `filledQuantity > 0` (si no, `List.of()`) y `MyOrderResponse.of(..., order.getPlayer().getFullName())` (depende de T032, T033)
- [X] T035 [US6] En `backend/src/main/java/com/example/demo/controller/OrderController.java`, agregar `@GetMapping("/mine") public List<MyOrderResponse> mine(Authentication authentication)` con `@Operation(summary = "Ver mis órdenes activas", description = ...)`, `@ApiResponse` 200 (`@ArraySchema(schema = @Schema(implementation = MyOrderResponse.class))`, header `X-Correlation-ID`), 401 y 403 (`superuser_not_allowed` o `user_required`) con `ErrorResponse`, como los demás métodos; los `@SecurityRequirement` de la clase cubren `bearerAuth` y `apiKeyAuth` (depende de T034; hace pasar T031)

**Checkpoint**: `./mvnw test` verde; `GET /orders/mine` visible en Swagger.

---

## Phase 8: User Story 5 - Ver y cancelar mis órdenes activas en Mercado (Priority: P2)

**Goal**: El ítem "03 Mercado" lleva a una pantalla con mis órdenes activas y permite cancelarlas.

**Independent Test**: Con órdenes activas, abrir Mercado, cancelar una y verificar que desaparece con su aviso; con `order_closed`, ver el mensaje y la recarga.

### Tests for User Story 5

- [X] T036 [P] [US5] Agregar a `frontend/src/features/trading/tradeMath.test.js` casos de `summarizeCancel(order)`: compra LIMIT con `remainingQuantity 6` y `limitPrice 1.1` (número de la API, convertido con `priceToUnits` a `11000`) → "Cancelaste tu compra: se liberaron 6,6000 cr."; venta con `remainingQuantity 6` → "Cancelaste tu venta: se liberaron 6 tokens." (se calcula con la fila de la lista, research R6)
- [X] T037 [P] [US5] Crear `frontend/src/pages/MarketPage.test.jsx` (`fetch` mockeado por URL, `SessionProvider` y `MemoryRouter` como `frontend/src/pages/RankingPage.test.jsx`) que verifique: encabezado "Mercado"; mientras carga, `aria-busy`; con dos órdenes muestra jugador (enlace a `/jugadores/{playerId}`), lado, tipo, precio, cantidad, ejecutado, pendiente, estado y fecha, en el orden recibido; con `[]` muestra "No tenés órdenes abiertas" y la acción "Ir al álbum"; con error muestra "Reintentar" y reintenta; "Cancelar" (con `aria-label="Cancelar orden de venta de {jugador}"`) abre el diálogo con jugador, lado, pendiente y precio; "Volver" no llama `DELETE`; confirmar llama `DELETE /orders/{id}` una sola vez (botón deshabilitado mientras `cancellingId`), quita la fila y muestra el Toast de `summarizeCancel`; con 409 `order_closed` muestra "Esta orden ya no está activa." y vuelve a pedir `GET /orders/mine`; con 404 `order_not_found` al cancelar muestra "Esta orden ya no existe." y vuelve a pedir `GET /orders/mine`; con `NetworkError` muestra "No pudimos cancelar la orden. Probá de nuevo." y la fila sigue; si `GET /orders/mine` responde 403 `superuser_not_allowed`, el `EstadoPanel` muestra "El superusuario no puede operar desde la aplicación." en lugar del texto de conexión
- [X] T038 [P] [US5] En `frontend/src/routes/routes.test.jsx`, agregar que para un usuario autenticado "Mercado" es un enlace a `/mercado` en `Header` y `BottomNav`, "Mi portfolio" sigue sin enlace, y `/mercado` sin sesión redirige a `/ingresar`

### Implementation for User Story 5

- [X] T039 [US5] Agregar `summarizeCancel(order)` a `frontend/src/features/trading/tradeMath.js`, convirtiendo `limitPrice` con `priceToUnits` antes de multiplicar y de `formatPrice` (hace pasar T036)
- [X] T040 [US5] Crear `frontend/src/features/trading/useMyOrders.js` (`()` → `{ status, orders, loadError, cancellingId, cancelError, reload, cancel }`) con el patrón de `useOrderBook`, guardando en `loadError` el error de `getMyOrders` cuando `status` es `error`; `cancel(order)` fija `cancellingId`, llama `cancelOrder(order.id)`, al resolver quita la orden de `orders` y devuelve `summarizeCancel(order)`, y al fallar guarda `cancelError = marketErrorMessage(error, { action: 'cancel' })` y llama `reload()` si `staleTarget(error) === 'orders'` (depende de T007, T010, T039)
- [X] T041 [US5] Crear `frontend/src/features/trading/MyOrdersList.jsx` y `frontend/src/features/trading/MyOrdersList.css` según `contracts/ui-contracts.md`: tabla desde 900 px y tarjetas apiladas debajo, mismos datos, nombre del jugador como `Link` a `/jugadores/{playerId}`, `formatPrice` (convertir `limitPrice` con `priceToUnits` antes de `formatPrice`), `sideLabel`, `typeLabel`, `statusLabel`, `formatDateTime` de `frontend/src/features/player-detail/quoteFormat.js`, y botón "Cancelar" (`Button` `secondary`) con `aria-label` y `disabled` cuando `cancellingId === order.id`
- [X] T042 [US5] Crear `frontend/src/pages/MarketPage.jsx` y `frontend/src/pages/MarketPage.css`: `page-content` + `page-heading` "Mercado" / "Tus órdenes abiertas", estados con `EstadoPanel` (error con "Reintentar": si `loadError` es un `ApiError` con un código de la tabla de `contracts/ui-contracts.md`, p. ej. 403 `superuser_not_allowed`/`user_required`, muestra `marketErrorMessage(loadError)`; si no, el texto de conexión de las demás páginas; vacío "No tenés órdenes abiertas" con acción "Ir al álbum" → `/album`), `MyOrdersList`, `ConfirmDialog` "Cancelar orden" con jugador, lado, pendiente y precio (`busy` mientras cancela), `cancelError` en `role="alert"` y `Toast` con el resumen (depende de T011, T040, T041; hace pasar T037)
- [X] T043 [US5] Excepción autorizada de Entrega 1: en `frontend/src/App.jsx` agregar sólo `import MarketPage from './pages/MarketPage.jsx'` y `<Route path="/mercado" element={<MarketPage />} />` dentro del bloque `ProtectedRoute`; en `frontend/src/layout/AppLayout.jsx` cambiar sólo el ítem a `{ id: 'mercado', label: 'Mercado', numero: '03', ruta: '/mercado', habilitado: true }`; confirmar con `git diff` que no hay otros cambios en esos archivos (hace pasar T038)

**Checkpoint**: Todo el flujo de la spec funciona: operar, ver el libro y cancelar.

---

## Phase 9: User Story 7 - Responsive y accesible (Priority: P3)

**Goal**: Panel, libro y Mercado se usan con teclado, lector de pantalla y desde 360 px.

**Independent Test**: Recorrer compra y cancelación sólo con teclado y a 360 px.

### Tests for User Story 7

- [X] T044 [P] [US7] Agregar a `frontend/src/features/trading/TradePanel.test.jsx` y `frontend/src/pages/MarketPage.test.jsx` el recorrido sólo con teclado: llegar con Tab a lado, tipo, campos y "Revisar…"; Enter abre el diálogo con el foco en confirmar; Escape lo cierra y el foco vuelve al botón que lo abrió; el Toast y los errores se exponen como `output`/`role="alert"`

### Implementation for User Story 7

- [X] T045 [US7] Revisar `frontend/src/features/trading/TradePanel.css`, `OrderBook.css`, `TradingSection.css`, `MyOrdersList.css`, `frontend/src/pages/MarketPage.css` y `frontend/src/components/ConfirmDialog.css`: sin anchos fijos que generen scroll horizontal a 360 px (tabla del libro con `width: 100%` y números con `font-variant-numeric: tabular-nums`), foco visible con el estilo de foco de `frontend/src/styles/base.css`, compra/venta y estados con texto además de color y contraste suficiente con los tokens de `frontend/src/styles/tokens.css`, y el diálogo usable a 360 px (ancho `min(100% - 32px, 440px)`) (hace pasar T044)

---

## Phase 10: Polish & Cross-Cutting Concerns

- [X] T046 Ejecutar `npm test`, `npm run lint` y `npm run build` en `frontend/` y `./mvnw test` en `backend/`, y corregir lo que falle
- [X] T047 Revisar duplicación y complejidad para SonarCloud (§3.2): un solo mapa de errores (`marketErrors.js`), un solo `ConfirmDialog`, sin `console.*`, sin código muerto ni imports sin uso, funciones de `tradeMath.js` y `useTrade.js` sin complejidad cognitiva alta; `MyOrderResponse` armado desde `OrderResponse` sin duplicar el cálculo del precio promedio
- [ ] T048 Recorrer la validación manual de `specs/029-front-mercado/quickstart.md` (pasos 1–9 y Swagger) con el backend levantado y anotar en el handoff cualquier diferencia; los tiempos de SC-001 y SC-005 y el ancho de 360 px sin scroll horizontal (FR-026, SC-007) se validan sólo a mano en este paso, porque jsdom no los puede medir
- [X] T049 Confirmar con `git diff main --stat` que de los archivos protegidos de Entrega 1 sólo cambiaron `frontend/src/App.jsx` y `frontend/src/layout/AppLayout.jsx` en las líneas autorizadas, que `frontend/package.json` no tiene dependencias nuevas, y que `OrderResponse` y los demás métodos de `OrderController` no cambiaron

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → **Foundational (Phase 2)** → historias del frontend.
- **US1 (Phase 3)** depende de Foundational. Es el MVP.
- **US2 (Phase 4)** y **US3 (Phase 5)** dependen de US1 (extienden `useTrade.js`, `TradePanel.jsx` y sus tests).
- **US4 (Phase 6)** depende de US1 (`TradingSection`); puede ir en paralelo con US2/US3 (archivos distintos salvo `TradePanel.test.jsx`).
- **US6 (Phase 7, backend)** no depende del frontend: puede hacerse en paralelo desde el principio.
- **US5 (Phase 8)** depende de Foundational y de US6 (consume `GET /orders/mine`); no depende de US1–US4.
- **US7 (Phase 9)** depende de US1–US5.
- **Polish (Phase 10)** al final.

### Within Each User Story

- Tests primero, fallando por la capacidad ausente; después la implementación.
- Lógica pura (`tradeMath.js`, `marketErrors.js`) antes que hooks; hooks antes que componentes; componentes antes de montarlos en páginas.

### Parallel Opportunities

- Phase 2: T002–T006 en paralelo; T007–T011 en paralelo; T012 después de T007.
- US6 entera (T031–T035) en paralelo con las fases 3–6; dentro de US6, T032 y T033 en paralelo.
- US5: T036, T037 y T038 en paralelo.
- US4: T027, T028 y T029 en paralelo.

---

## Parallel Example: Foundational

```text
Task: "T002 Crear frontend/src/api/marketApi.test.js"
Task: "T003 Crear frontend/src/features/trading/tradeMath.test.js"
Task: "T004 Crear frontend/src/features/trading/marketErrors.test.js"
Task: "T005 Crear frontend/src/components/ConfirmDialog.test.jsx"
Task: "T006 Crear frontend/src/features/trading/useOrderBook.test.jsx"
```

## Parallel Example: User Story 6 (backend) junto al frontend

```text
Task: "T032 [US6] findActiveByUserWithPlayer en MarketOrderRepository.java"
Task: "T033 [US6] Crear MyOrderResponse.java"
Task: "T015 [US1] estimateMarket / estimateLimit / summarizeOrder en tradeMath.js"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 y Phase 2.
2. Phase 3 (US1): comprar desde la ficha con estimado, confirmación y Toast.
3. **Validar**: comprar MARKET y LIMIT contra el superusuario.

### Incremental Delivery

1. US1 → MVP de compra.
2. US2 + US3 → venta y validaciones/errores (consola completa).
3. US4 → libro visible y actualizado.
4. US6 (backend, puede ir antes) → US5 → pantalla de Mercado con cancelación.
5. US7 + Polish → accesibilidad, responsive y calidad.

---

## Notes

- Los tests de `features/trading/` mockean `marketApi.js`; los de páginas mockean `fetch` por URL.
- Números siempre en diezmilésimos para comparar (research R1); `fromUnits` sólo para armar el body.
- No commitear: los commits los hace el equipo.
