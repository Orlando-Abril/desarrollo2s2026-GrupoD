# Implementation Plan: Operar en el mercado desde el frontend

**Branch**: `feature/front-mercado` | **Date**: 2026-10-29 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/029-front-mercado/spec.md`

## Summary

Consola de trading del frontend sobre los endpoints de la 027. En la ficha del jugador (`/jugadores/:id`) se monta una sección "Operar" con un panel de compra/venta (MARKET o LIMIT, validación local, total estimado, confirmación y Toast con el resultado) y el libro del jugador, que se recarga después de operar. Una pantalla nueva `/mercado` (ítem "03 Mercado") lista mis órdenes activas y permite cancelarlas. Para esa lista se agrega un único endpoint de backend, `GET /orders/mine`, en el `OrderController` existente.

Enfoque: un cliente `api/marketApi.js` sobre el `httpClient` existente; la lógica pura (validación con aritmética entera en diezmilésimos, banda de ±20%, total estimado, resumen del resultado, mensajes de error) en módulos de `features/trading/` testeables sin React; hooks `useOrderBook`, `useTrade` y `useMyOrders` con el patrón de `AbortController` de `useRanking`; un `ConfirmDialog` accesible propio (sin dependencias nuevas) y el `Toast` existente.

## Technical Context

**Language/Version**: JavaScript (ES2023, JSX) con React 19 en el frontend; Java 21 con Spring Boot 3 en el backend (sólo `GET /orders/mine`).

**Primary Dependencies**: React 19, React Router 7, el `httpClient` propio (`request`, `ApiError`, `NetworkError`). Backend: Spring Web, Spring Data JPA, Spring Security, springdoc-openapi. **Sin dependencias nuevas.**

**Storage**: N/A en el frontend (estado en memoria del componente). Backend: tablas existentes de la 026/027 (`market_orders`, `trades`), sin cambios de esquema.

**Testing**: Vitest 5 + Testing Library + jsdom en el frontend (`npm test`); JUnit 5 + MockMvc con `@MarketMockMvcTest` en el backend.

**Target Platform**: navegadores modernos, desde 360 px de ancho; backend Spring Boot existente.

**Project Type**: aplicación web (frontend + backend).

**Performance Goals**: el total estimado se recalcula en cada tecla sin demora perceptible (libro de pocos niveles: 100 tokens por jugador); la pantalla de Mercado carga con un único pedido.

**Constraints**: archivos protegidos de Entrega 1 sin cambios, salvo la ruta en `App.jsx` y el ítem `mercado` de `NAV_ITEMS` en `AppLayout.jsx`; precios a 4 decimales sin errores de punto flotante; SonarCloud < 10 issues; sin dependencias nuevas.

**Scale/Scope**: 1 cliente API, 1 componente compartido, 3 hooks, ~5 componentes de feature, 1 página nueva, 1 endpoint de backend con 1 consulta y 1 DTO.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Regla | Cómo se cumple | Estado |
|-------|----------------|--------|
| **§2.2 Frontend independiente** — web responsiva, sólo HTTP/REST contra la API documentada; vista obligatoria "consola de trading (compra/venta de tokens)" | Todo el acceso pasa por `marketApi.js` → `httpClient` contra los endpoints documentados en OpenAPI (027 + `GET /orders/mine`). La consola vive en la ficha del jugador y la pantalla de Mercado; layout responsive desde 360 px (FR-026). | ✅ |
| **§3.2 SonarCloud** — Quality Gate PASSED, < 10 issues, sin duplicación | Lógica pura separada de los componentes y cubierta por tests; un solo `ConfirmDialog` reutilizado por el panel y por Mercado; un solo mapa de errores (`marketErrors.js`); `MyOrderResponse` se arma a partir de `OrderResponse` en vez de duplicar su mapeo; sin `console.*`, sin código muerto, sin complejidad cognitiva alta (validación dividida por campo). | ✅ |
| §2.1 Capas del backend | `GET /orders/mine`: controller → `OrderService.myOrders` → `MarketOrderRepository`. | ✅ |
| §4.1 Autenticación | El endpoint nuevo hereda los `@SecurityRequirement` del `OrderController` y la seguridad existente (401 sin credenciales). El usuario sale de la credencial, nunca del pedido. | ✅ |
| §6.1 OpenAPI | `GET /orders/mine` anotado con `@Operation`, respuestas 200/401/403 y los esquemas `bearerAuth` y `apiKeyAuth`. | ✅ |
| §3.1 CI | `npm test`, `npm run lint` y los tests del backend en verde. | ✅ |
| Archivos protegidos de Entrega 1 | Sólo la excepción autorizada (`App.jsx`: una ruta; `AppLayout.jsx`: habilitar `mercado`). Backend: no se tocan `User`, `UserRepository`, `application.properties`, `PlayerCatalogService` ni `GlobalExceptionHandler`. | ✅ |

**Resultado**: pasa sin violaciones. **Re-check post-diseño**: pasa; el diseño de Phase 1 no agrega dependencias, archivos protegidos ni capas.

## Project Structure

### Documentation (this feature)

```text
specs/029-front-mercado/
├── plan.md              # Este archivo
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── orders-mine-api.md   # GET /orders/mine (backend)
│   └── ui-contracts.md      # marketApi, hooks, componentes y mensajes
└── tasks.md             # Phase 2 ($speckit-tasks)
```

### Source Code (repository root)

```text
backend/src/main/java/com/example/demo/
├── controller/OrderController.java                 # + GET /orders/mine
├── dto/market/MyOrderResponse.java                 # NUEVO: OrderResponse + playerName
├── repository/market/MarketOrderRepository.java    # + findActiveByUserWithPlayer
└── service/market/OrderService.java                # + myOrders(principalName)

backend/src/test/java/com/example/demo/controller/
└── OrderControllerMineTest.java                    # NUEVO: 200 (propias y activas, orden) y 401

frontend/src/
├── App.jsx                                         # + <Route path="/mercado"> (autorizado)
├── layout/AppLayout.jsx                            # mercado: ruta '/mercado', habilitado (autorizado)
├── api/
│   ├── marketApi.js                                # NUEVO: buyOrder, sellOrder, cancelOrder, getOrderBook, getMyOrders
│   └── marketApi.test.js
├── components/
│   ├── ConfirmDialog.jsx / .css                    # NUEVO: diálogo modal accesible (foco atrapado y devuelto)
│   └── ConfirmDialog.test.jsx
├── features/trading/
│   ├── tradeMath.js / .test.js                     # NUEVO: diezmilésimos, banda, validación, estimado, resumen
│   ├── marketErrors.js / .test.js                  # NUEVO: código de error → mensaje en español
│   ├── tradeFormat.js                              # NUEVO: precio a 4 decimales, lado, estado
│   ├── useOrderBook.js / .test.jsx                 # NUEVO
│   ├── useTrade.js                                 # NUEVO
│   ├── useMyOrders.js                              # NUEVO
│   ├── TradePanel.jsx / .css / .test.jsx           # NUEVO
│   ├── OrderBook.jsx / .css / .test.jsx            # NUEVO
│   ├── TradingSection.jsx / .css                   # NUEVO: panel + libro, toast de la ficha
│   └── MyOrdersList.jsx / .css                     # NUEVO
└── pages/
    ├── PlayerDetailPage.jsx                        # monta <TradingSection> en el estado 'ready'
    ├── PlayerDetailPage.test.jsx                   # el mock de fetch responde también /orders/book
    ├── MarketPage.jsx / .css                       # NUEVO
    └── MarketPage.test.jsx                         # NUEVO
frontend/src/routes/routes.test.jsx                 # ítem Mercado habilitado y ruta protegida
```

**Structure Decision**: aplicación web existente (`backend/` + `frontend/`). Siguiendo el pedido del equipo, el código nuevo del frontend va en `api/marketApi.js`, `features/trading/` y `pages/MarketPage.jsx`, con el mismo esquema que `features/ranking` y `features/player-detail` (hook + componentes + utilidades puras con sus tests al lado). Además de `buy`, `sell`, `cancel` y `getBook`, `marketApi.js` incluye `getMyOrders` para `GET /orders/mine`, que la pantalla de Mercado necesita. `ConfirmDialog` va en `components/` porque lo usan la ficha y Mercado.

## Complexity Tracking

Sin violaciones de la constitución; no aplica.
