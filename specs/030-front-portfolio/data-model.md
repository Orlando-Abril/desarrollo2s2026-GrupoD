# Phase 1 Data Model: Pantalla Mi portfolio

Este modelo es exclusivamente de lectura y describe datos recibidos y estados de vista. No agrega persistencia ni cambia el modelo backend de la 028.

## 1. Cuenta actual

Fuente: `GET /auth/me`.

| Campo | Tipo | Regla |
|-------|------|-------|
| `id` | integer positivo | Identificador usado en los paths de portfolio e historial. |
| `username` | string no vacío | Sólo contexto; no reemplaza al id. |
| `email` | string | No se muestra en esta feature. |
| `role` | `USER` o `ADMIN` | No habilita selector de otras cuentas. |
| `balance` | decimal | La pantalla usa el saldo del portfolio como valor financiero definitivo. |

Relación: una Cuenta actual habilita exactamente un Portfolio y cero o más páginas de Operaciones propias.

## 2. Portfolio

Fuente: `GET /users/{id}/portfolio`.

| Campo | Tipo | Regla de vista |
|-------|------|----------------|
| `userId` | integer positivo | Debe corresponder a la cuenta resuelta. |
| `balance` | decimal, 4 posiciones | “Saldo” principal. |
| `reservedCredits` | decimal, 4 posiciones | Se muestra como desglose si es mayor que cero. |
| `availableBalance` | decimal, 4 posiciones | Saldo operable; no se suma al patrimonio. |
| `positions` | lista de Posición | Puede estar vacía. Se conserva el orden recibido. |
| `totalCost` | decimal, 4 posiciones | “Invertido”. |
| `totalCurrentValue` | decimal, 4 posiciones | “Valor actual”. |
| `totalProfitLoss` | decimal, 4 posiciones | “Resultado”. |
| `totalEquity` | decimal, 4 posiciones | Disponible para contexto, no es uno de los cuatro indicadores pedidos. |

Invariantes consumidas, no recalculadas:

- `availableBalance = balance - reservedCredits`.
- `totalProfitLoss = totalCurrentValue - totalCost`.
- `totalEquity = balance + totalCurrentValue`.
- Los totales de costo, valor y resultado son las sumas de posiciones.

## 3. Posición

| Campo contractual | Tipo | Uso de vista |
|-------------------|------|--------------|
| `playerId` | integer positivo | Key estable y link `/jugadores/{playerId}`. |
| `playerName` | string no vacío | Etiqueta completa en tabla, tooltip y alternativas. |
| `playerTeam` | string | Contexto opcional junto al jugador. |
| `quantity` | integer > 0 | Columna Tokens. |
| `reservedQuantity` | integer >= 0 | No se muestra como columna en esta feature. |
| `availableQuantity` | integer >= 0 | No se muestra como columna en esta feature. |
| `averagePurchasePrice` | decimal, 4 posiciones | PPC y serie del gráfico de rendimiento. |
| `costBasis` | decimal, 4 posiciones | Contribuye al total contractual; no es columna pedida. |
| `marketValue` | decimal, 4 posiciones | Cotización y segunda serie de rendimiento. |
| `currentValue` | decimal, 4 posiciones | Valor actual y peso de distribución. |
| `profitLoss` | decimal, 4 posiciones | Resultado por fila. |

Derivación visual `resultKind`:

- `profitLoss > 0` → `gain` / Ganancia / signo `+` / token verde.
- `profitLoss < 0` → `loss` / Pérdida / signo negativo / token rojo.
- `profitLoss = 0` → `neutral` / Sin variación / token tinta secundaria.

## 4. Dato de rendimiento

Proyección inmutable de Posición:

| Campo | Origen |
|-------|--------|
| `playerId` | `position.playerId` |
| `playerName` | `position.playerName` |
| `averagePurchasePrice` | `position.averagePurchasePrice` |
| `marketValue` | `position.marketValue` |

No se ordena ni se calcula una rentabilidad porcentual.

## 5. Porción de distribución

| Campo | Origen/regla |
|-------|--------------|
| `playerId` | `position.playerId` |
| `playerName` | `position.playerName` |
| `currentValue` | `position.currentValue` |
| `share` | `currentValue / portfolio.totalCurrentValue` si el total es finito y mayor que cero; en otro caso `null`. |
| `colorToken` | Paleta estable por índice: azul, rojo, verde, violeta, amarillo, tinta secundaria; se cicla. |

Invariante: si el total es positivo, la suma matemática de `share` es 1 dentro de la precisión de presentación. Los porcentajes mostrados pueden redondearse, pero nunca reemplazan los valores contractuales.

## 6. Operación

| Campo | Tipo | Regla de vista |
|-------|------|----------------|
| `id` | integer positivo | Key estable. |
| `type` | `COMPRA` o `VENTA` | Etiqueta textual; no depende sólo de color. |
| `playerId` | integer positivo | Link al detalle. |
| `playerName` | string no vacío | Nombre del enlace. |
| `quantity` | integer > 0 | Tokens ejecutados. |
| `price` | decimal, 4 posiciones | Precio unitario. |
| `total` | decimal, 4 posiciones | Total de ese calce. |
| `executedAt` | fecha-hora ISO | Se presenta con fecha y hora consistente con la aplicación. |

Orden: el frontend conserva el orden descendente estable recibido (fecha e id definidos por backend).

## 7. Página de operaciones

| Campo | Tipo | Regla |
|-------|------|-------|
| `items` | lista de Operación | 0..20 elementos para esta pantalla. |
| `page` | integer >= 0 | Base cero contractual. |
| `size` | integer | Siempre solicitado como 20. |
| `numberOfElements` | integer >= 0 | Debe coincidir con `items.length`. |
| `totalElements` | integer >= 0 | Total mostrado al usuario. |
| `totalPages` | integer >= 0 | Límite de navegación. |
| `first` | boolean | Deshabilita Anterior. |
| `last` | boolean | Deshabilita Siguiente. |

La UI expone `humanPage = page + 1`. Si la página solicitada deja de existir y `totalPages > 0`, transiciona a `totalPages`; si `totalPages = 0`, transiciona a página humana 1 con estado vacío.

## 8. Estado de `usePortfolio`

```text
identity: loading ──success──> ready
    │                        └──logout/unmount──> descartado
    └──failure──> error ──retry──> loading

portfolio: idle ──identity ready──> loading ──success──> ready|empty
                                  └──failure──> error ──retry──> loading

transactions: idle ──identity ready/page/retry──> loading ──success──> ready|empty
                                                └──failure──> error ──retry──> loading
```

- Portfolio e historial no comparten estado de error ni retry.
- La última respuesta confirmada puede conservarse durante una recarga, pero la UI debe marcarla como desactualizada y no como recién confirmada.
- Cleanup o reemplazo aborta la solicitud; un handler con `signal.aborted` no publica datos ni errores.
- Logout/401 desmonta la ruta y elimina el estado financiero local.
