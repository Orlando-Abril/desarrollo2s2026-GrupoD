# Data Model: Métricas propias de la aplicación

No hay entidades persistidas nuevas ni migraciones. El modelo describe los meters en memoria, los dominios cerrados de sus etiquetas y el punto donde se registra cada uno. Los nombres exportados están en [contracts/business-metrics.md](contracts/business-metrics.md).

## Dominios de etiquetas (enums en `com.example.demo.metrics`)

| Enum | Valores (etiqueta) | Usado en |
|---|---|---|
| `model.market.OrderSide` (existente) | `BUY`, `SELL` | `side` de órdenes creadas |
| `model.market.OrderType` (existente) | `MARKET`, `LIMIT` | `type` de órdenes creadas |
| `OrderRejectionReason` | `insufficient_balance`, `insufficient_tokens`, `price_out_of_band` | `reason` de órdenes rechazadas |
| `RecalculationTrigger` | `manual`, `scheduled` | `trigger` del recálculo |
| `RecalculationOutcome` | `success`, `failure` | `outcome` del recálculo |
| `ExternalSource` | `football_data`, `whoscored` | `source` de llamadas externas |
| `ExternalCallOutcome` | `success`, `error`, `cache` | `outcome` de llamadas externas |
| `AuthOutcome` | `success`, `failure` | `outcome` de registros y logins |
| código de estrategia | códigos del catálogo (hoy `OFFICIAL`, `MOMENTUM`) | `strategy` de cotizaciones calculadas |

Regla: ninguna etiqueta recibe un `String` libre, salvo `strategy`, que sale de `StrategyRunCounts.code()` (un código del catálogo configurado, no un dato de usuario).

## Meters

| Meter | Tipo | Etiquetas | Se registra en | Momento |
|---|---|---|---|---|
| órdenes creadas | Counter | `side`, `type` | `OrderService.place` y `MarketMakerService` al guardar la orden | después del commit |
| órdenes rechazadas | Counter | `reason` | `OrderService.place`, al capturar las tres excepciones | inmediato, antes de relanzar |
| trades ejecutados | Counter | — | `OrderService.place`, una vez por trade | después del commit |
| tokens operados | Counter (suma de cantidades) | — | junto con el trade | después del commit |
| volumen en créditos | Counter (suma de cantidad × precio) | — | junto con el trade | después del commit |
| duración del recálculo | Timer con histograma | `trigger`, `outcome` | `QuoteCalculationService.execute` | al terminar `execute` |
| duración del último recálculo | TimeGauge (`AtomicLong` en nanos) | — | `BusinessMetrics.recalculationFinished` | al terminar `execute`, con éxito o fallo |
| cotizaciones calculadas | Counter | `strategy` | `QuoteCalculationService.execute`, por cada `StrategyRunCounts` | al terminar con éxito |
| cambios de estrategia activa | Counter | — | `ActiveStrategyMetricsListener` (nuevo) | evento, después del commit |
| llamadas externas | Counter | `source`, `outcome` | `FootballDataAdapter`, `WhoScoredAdapter`, `MeteredCacheResolver`, `PlayerStatsService` | al terminar la consulta |
| registros | Counter | `outcome` | `AuthService.register` | al guardar o al rechazar |
| logins | Counter | `outcome` | `AuthService.login` | al emitir el token o al rechazar |
| usuarios registrados | Gauge (función) | — | `BusinessMetrics`, sobre `UserRepository.count()` | en cada lectura del registry |

## Ciclo de vida

- **Arranque**: `BusinessMetrics` registra todos los meters y todas las combinaciones de etiquetas cerradas en 0. Los contadores por estrategia se crean al primer recálculo que calcula esa estrategia.
- **Operación confirmada**: se incrementa el meter que corresponde. En una transacción, órdenes creadas y trades esperan al `afterCommit`. Si la transacción se revierte, no cambian.
- **Operación rechazada**: el rechazo se cuenta en el momento y la excepción original sigue su curso sin cambios.
- **Fallo al medir**: se registra `business_metric_failed` en el log y la operación sigue. El gauge devuelve `NaN` si el conteo falla.
- **Reinicio**: los contadores y el timer vuelven a 0; el gauge de usuarios vuelve a leer la base. El gauge de la última duración del recálculo vuelve a 0 hasta la próxima corrida. Para no perder el dato, el panel del dashboard toma de Prometheus la última muestra mayor que 0 de los últimos 7 días (ver el contrato).

## Invariantes verificables

- Para cada trade confirmado: `Δ trades = 1`, `Δ tokens = cantidad`, `Δ volumen = cantidad × precio`.
- `Δ órdenes creadas` por (lado, tipo) es igual a la cantidad de órdenes confirmadas de esa combinación.
- Una operación rechazada no cambia órdenes creadas, trades, tokens ni volumen.
- `Δ cotizaciones calculadas{strategy}` es igual a `StrategyRunCounts.calculated()` de esa estrategia en el resumen de la corrida.
