# Quickstart: Operar en el mercado desde el frontend

## Prerrequisitos

- Backend con la 027 (motor de calce y creador de mercado) y `GET /orders/mine` de esta feature.
- Base con jugadores emitidos: el superusuario publica su venta de 100 tokens por jugador al arrancar.
- Dos usuarios registrados (A y B), cada uno con 1000 créditos.

## Tests automáticos

```bash
cd frontend && npm test && npm run lint
cd backend && ./mvnw test -Dtest=OrderControllerMineTest
```

Cubren (ver [contracts/ui-contracts.md](contracts/ui-contracts.md) y [contracts/orders-mine-api.md](contracts/orders-mine-api.md)):

| Área | Archivo | Qué verifica |
|------|---------|--------------|
| Validaciones | `features/trading/tradeMath.test.js` | cantidad entera > 0; precio con ≤ 4 decimales y > 0; banda con extremos exactos (mv 1.0000 → 0.8000 y 1.2000 válidos, 0.7999 y 1.2001 no; mv 1.2345); estimado MARKET con y sin liquidez; resumen por estado |
| Errores | `features/trading/marketErrors.test.js` | cada `code` → su mensaje; `NetworkError` y código desconocido → genérico; nunca el `message` del servidor |
| API | `api/marketApi.test.js` | método, URL y body de cada función; MARKET sin `limitPrice` |
| Confirmación | `components/ConfirmDialog.test.jsx`, `TradePanel.test.jsx` | no se envía sin confirmar; Escape/Volver no envía; foco atrapado y devuelto; doble envío bloqueado |
| Libro | `OrderBook.test.jsx`, `useOrderBook.test.jsx` | orden de niveles, lados vacíos, error y reintento; recarga después de operar y ante `price_out_of_band` |
| Cancelación | `pages/MarketPage.test.jsx` | lista sólo lo que devuelve la API; vacío; confirmación; quita la fila y toast; `order_closed` → mensaje + recarga |
| Navegación | `routes/routes.test.jsx` | "03 Mercado" habilitado → `/mercado`; ruta protegida |
| Backend | `OrderControllerMineTest` | 200 sólo propias y activas, más nueva primero, con `playerName`; `[]`; 401 |

## Validación manual (end-to-end)

1. Ingresar como A → **03 Mercado** está habilitado y muestra "No tenés órdenes abiertas"; **04 Mi portfolio** sigue deshabilitado.
2. Abrir la ficha de un jugador (cotización 1.0000): la sección **Operar** muestra el libro con la venta del superusuario `1,0000 cr → 100`.
3. Comprar MARKET 10 → estimado `10,0000 cr` → "Revisar compra" → confirmar → Toast "Compraste 10 tokens a 1,0000 cr promedio." → el libro pasa a `→ 90`.
4. Vender LIMIT 6 a `1.3000` → error en el campo "El precio debe estar entre 0,8000 cr y 1,2000 cr"; con `1.2000` → confirmar → Toast "No se ejecutó nada. Quedaron 6 en el libro a 1,2000 cr." → el libro muestra la venta a 1,2000.
5. Vender LIMIT 6 de nuevo → mensaje "No tenés tokens disponibles suficientes de este jugador (pueden estar reservados en órdenes abiertas).".
6. Cantidad `2.5` o `0` → "La cantidad debe ser un número entero mayor a 0" y no se abre la confirmación.
7. **03 Mercado** → la venta LIMIT aparece con jugador, 6 pendientes y "Abierta" → Cancelar → confirmar → desaparece; Toast "Cancelaste tu venta: se liberaron 6 tokens.".
8. Ingresar como B: **03 Mercado** no muestra órdenes de A.
9. Repetir 3 y 7 sólo con teclado y a 360 px: sin scroll horizontal, foco visible, la confirmación devuelve el foco al botón que la abrió.

## Swagger

`GET /orders/mine` aparece en el tag "Órdenes" con `bearerAuth` y `apiKeyAuth`; "Try it out" con una API key devuelve las órdenes activas del dueño de la key.
