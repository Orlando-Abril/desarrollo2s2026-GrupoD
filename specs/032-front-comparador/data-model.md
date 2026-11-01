# Data Model: estado de comparación del frontend

No se crea persistencia local ni una entidad financiera nueva. Los siguientes modelos son proyecciones de UI sobre las respuestas de la 031.

## StrategyViewState

| Campo | Tipo | Regla |
|-------|------|-------|
| `viewStrategy` | `MARKET_CURRENT \| MOMENTUM` | Inicial `MARKET_CURRENT`, visible como «Mercado actual»; elige precio persistido o cotización Momentum durante la navegación. |
| `activeConfig` | `ActiveStrategyResponse \| null` | Última respuesta confirmada de la 031. |
| `configStatus` | `idle \| loading \| ready \| error` | Error no crea etiquetas ni precios supuestos. |
| `retry` | acción | Vuelve a consultar configuración. |
| `refresh` | acción | Consulta después de PUT o recálculo. |

**Invariante**: Cambiar `viewStrategy` no modifica `activeConfig` ni envía mutación. `MARKET_CURRENT` muestra siempre `marketValue`, aunque `marketStrategy=MOMENTUM`; no equivale a OFFICIAL. En logout o cambio de identidad, el contexto vuelve a `MARKET_CURRENT` y borra la configuración anterior.

## ActiveStrategyResponse

Reutiliza [contrato 031](../031-comparador-estrategias/contracts/strategy-comparison-api.yaml).

| Campo | Significado para la UI |
|-------|------------------------|
| `active` | Estrategia elegida globalmente para el próximo recálculo. |
| `alternative` | La otra estrategia en el catálogo de dos. |
| `marketStrategy` | Estrategia que fijó el precio de mercado vigente. |
| `pendingRecalculation` | Exige explicar que el cambio global aún no actualizó precios. |
| `changedAt`, `changedBy` | Contexto informativo; no necesario para autorizar. |
| `changed` | Sólo en respuesta PUT; `false` permite evitar aviso de cambio efectivo. |

Cada referencia de estrategia contiene `code`, `version` y `label`. «Mercado actual» es el nombre fijo del modo de precio persistido; la etiqueta de `marketStrategy` identifica qué estrategia lo generó. La opción Momentum y las curvas históricas usan los códigos y etiquetas reales de las estrategias. Si `pendingRecalculation=true`, `active` puede diferir de `marketStrategy`: el precio de operación sigue siendo `marketValue` hasta la próxima corrida.

## LatestPageQuoteState

| Campo | Tipo | Regla |
|-------|------|-------|
| `pageIds` | lista de 1–100 ids distintos | Se deriva de `album.items`, no de todos los resultados. |
| `strategy` | `MOMENTUM` | Parámetro explícito para que un cambio de activa no altere el significado del selector. |
| `status` | `idle \| loading \| ready \| error` | En modo Mercado actual queda `idle`. |
| `quotesByPlayerId` | mapa id → quote/null | `null` significa jugador sin cotización; ausencia de mapa por error no se interpreta como `null`. |

La cotización incluye valor, estado `CALCULATED \| NO_STATS \| INSUFFICIENT_HISTORY`, instante y versión. Diferencia absoluta = cotización MOMENTUM − `marketValue`; porcentual = diferencia / `marketValue` × 100 si éste es positivo. MOMENTUM puede coincidir con Mercado actual cuando ya fijó ese precio.

## ChartComparisonState

| Campo | Tipo | Regla |
|-------|------|-------|
| `range` | rango existente del gráfico | Se aplica por igual a ambas consultas. |
| `official`, `momentum` | series de puntos fechados | Se conservan independientes para estados de carga/error. |
| `joinedPoints` | puntos ordenados por instante con valor opcional de cada serie | No se crean valores cero ni se emparejan puntos por posición. |

En modo Mercado actual se conserva el modelo de una sola serie del componente actual, identificada con el código real de la estrategia histórica seleccionada. En modo Momentum, tooltip, leyenda y tabla identifican los historiales OFFICIAL y MOMENTUM por sus códigos reales y fecha; ninguna curva se denomina «Mercado actual».

## PortfolioComparison

`GET /users/{id}/portfolio?strategy=MOMENTUM` extiende cada posición con `strategyValuation`: `quotePrice`, `quoteStatus`, `quoteCalculatedAt`, `value`, `profitLoss`. El total incluye estrategia, `totalValue`, `totalProfitLoss`, `totalEquity` y `positionsWithoutQuote`.

**Validación**: Si `positionsWithoutQuote > 0`, los totales comparativos son `null` y la UI muestra «No disponible». Los campos de mercado, saldo y tenencia se muestran siempre desde la respuesta base.

## OpportunitiesView

La respuesta 031 trae `activeStrategy`, `alternativeStrategy`, `marketStrategy`, `pendingRecalculation`, `undervalued[]` y `overvalued[]`. Cada elemento tiene `playerId`, nombre, liga, posiciones, `marketValue`, `alternativeQuote`, diferencia y porcentaje.

Los filtros `league` y `position` se almacenan sólo en la sección de oportunidades. Una selección cambia su consulta, sin alterar filtros, página o estrategia del ranking principal. `limit` usa el valor por defecto del contrato 031 salvo que la UI exponga otro valor.

## ActiveStrategyControlState

```text
loadingIdentity → hidden(USER) | loadingConfig(ADMIN) | identityError
loadingConfig → ready | configError
ready → confirming → ready(cancel) | submitting(confirm)
submitting → success(changed true/false) | denied(403) | invalid(400) | uncertain(network)
denied → hidden for same identity
invalid → ready with correction message
uncertain → loadingConfig → ready | configError
```

La identidad se basa en `GET /auth/me`, con `role=ADMIN`. `submitting` bloquea dobles envíos. Un cambio de identidad invalida los estados de autorización anteriores. Después de PUT exitoso se refrescan configuración y oportunidades; después del recálculo se repiten esas consultas y se actualizan los datos de mercado. El recálculo de la 025 es una transición independiente: no se inicia como efecto del PUT.
