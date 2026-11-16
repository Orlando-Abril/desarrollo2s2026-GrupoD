# Contract: métricas de negocio en `/actuator/prometheus`

**Acceso**: igual que la 035. JWT ADMIN o `X-API-KEY` con `ROLE_ADMIN` → 200; usuario sin ADMIN → 403; anónimo → 401. No hay endpoints nuevos.

**Formato**: texto de Prometheus. Cada familia tiene `# HELP` con la descripción y `# TYPE`. Todas las series llevan además `application="<spring.application.name>"` (035).

## Series

| Nombre en Micrometer | Series en Prometheus | Tipo | Etiquetas | Descripción (`# HELP`) |
|---|---|---|---|---|
| `gdmarket.orders.placed` | `gdmarket_orders_placed_total` | counter | `side` ∈ {BUY, SELL}, `type` ∈ {MARKET, LIMIT} | Órdenes de mercado aceptadas y confirmadas |
| `gdmarket.orders.rejected` | `gdmarket_orders_rejected_total` | counter | `reason` ∈ {insufficient_balance, insufficient_tokens, price_out_of_band} | Órdenes rechazadas por saldo, tokens o banda de precio |
| `gdmarket.trades.executed` | `gdmarket_trades_executed_total` | counter | — | Trades ejecutados y confirmados |
| `gdmarket.trades.tokens` | `gdmarket_trades_tokens_total` | counter | — | Tokens operados en trades confirmados |
| `gdmarket.trades.volume.credits` | `gdmarket_trades_volume_credits_total` | counter | — | Volumen operado en créditos (cantidad × precio) |
| `gdmarket.quotes.recalculation` | `gdmarket_quotes_recalculation_seconds_count`, `_sum`, `_max`, `_bucket{le}` | histogram (Timer) | `trigger` ∈ {manual, scheduled}, `outcome` ∈ {success, failure} | Duración de cada recálculo de cotizaciones |
| `gdmarket.quotes.recalculation.last` | `gdmarket_quotes_recalculation_last_seconds` | gauge (TimeGauge) | — | Duración de la última corrida de recálculo terminada, con éxito o con fallo; 0 hasta la primera |
| `gdmarket.quotes.calculated` | `gdmarket_quotes_calculated_total` | counter | `strategy` ∈ códigos del catálogo | Cotizaciones calculadas por estrategia |
| `gdmarket.strategy.changes` | `gdmarket_strategy_changes_total` | counter | — | Cambios confirmados de la estrategia activa |
| `gdmarket.external.calls` | `gdmarket_external_calls_total` | counter | `source` ∈ {football_data, whoscored}, `outcome` ∈ {success, error, cache} | Consultas a fuentes externas por resultado |
| `gdmarket.auth.registrations` | `gdmarket_auth_registrations_total` | counter | `outcome` ∈ {success, failure} | Registros de usuarios por resultado |
| `gdmarket.auth.logins` | `gdmarket_auth_logins_total` | counter | `outcome` ∈ {success, failure} | Logins por resultado |
| `gdmarket.users.registered` | `gdmarket_users_registered` | gauge | — | Cuentas de usuario registradas |

Con la aplicación recién iniciada, todas las combinaciones de las etiquetas cerradas aparecen en 0: 23 series de contadores y gauges, más las 4 combinaciones del timer (cada una con `_count`, `_sum`, `_max` y sus `_bucket`). `gdmarket_quotes_calculated_total{strategy=...}` aparece después del primer recálculo exitoso que calcula esa estrategia.

## Prohibiciones

- Ninguna serie con prefijo `gdmarket_` tiene etiquetas con ids, nombres de usuario, emails, nombres o ids de jugador, ids de orden o de corrida, ni mensajes de error.
- No aparecen otras etiquetas que las listadas (más `application`).

## Consultas del dashboard (`observability/grafana/dashboards/gdmarket.json`)

| Panel | Consulta |
|---|---|
| Órdenes creadas por minuto | `sum by (side, type) (increase(gdmarket_orders_placed_total[1m]))` |
| Órdenes rechazadas por minuto | `sum by (reason) (increase(gdmarket_orders_rejected_total[1m]))` |
| Trades por minuto | `sum(increase(gdmarket_trades_executed_total[1m]))` |
| Tokens y volumen (1 h) | `sum(increase(gdmarket_trades_tokens_total[1h]))`, `sum(increase(gdmarket_trades_volume_credits_total[1h]))` |
| Duración del último recálculo | `last_over_time((gdmarket_quotes_recalculation_last_seconds > 0)[7d:1m])` |
| Duración media del recálculo (7 d) | `sum by (trigger) (increase(gdmarket_quotes_recalculation_seconds_sum[7d])) / sum by (trigger) (increase(gdmarket_quotes_recalculation_seconds_count[7d]))` |
| Duración del recálculo p95 (7 d) | `histogram_quantile(0.95, sum by (le, trigger) (increase(gdmarket_quotes_recalculation_seconds_bucket[7d])))` |
| Recálculos por resultado (7 d) | `sum by (trigger, outcome) (increase(gdmarket_quotes_recalculation_seconds_count[7d]))` |
| Cotizaciones por estrategia (7 d) | `sum by (strategy) (increase(gdmarket_quotes_calculated_total[7d]))` |
| Cambios de estrategia (7 d) | `sum(increase(gdmarket_strategy_changes_total[7d]))` |
| Llamadas externas | `sum by (source, outcome) (increase(gdmarket_external_calls_total[1h]))` |
| Usuarios registrados | `max(gdmarket_users_registered)` |
| Registros y logins | `sum by (outcome) (increase(gdmarket_auth_registrations_total[1h]))`, `sum by (outcome) (increase(gdmarket_auth_logins_total[1h]))` |
| Latencia HTTP p95 (035) | `histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket[5m])))` |
| Tasa de error 5xx (035) | `sum by (uri) (rate(http_server_requests_seconds_count{outcome="SERVER_ERROR"}[5m])) / sum by (uri) (rate(http_server_requests_seconds_count[5m]))` |

**Por qué 7 días en cotizaciones**: el recálculo programado corre una vez por semana (`valuation.schedule.cron`, lunes 06:00 UTC). `gdmarket_quotes_recalculation_seconds_max` no sirve para "la última corrida", porque Micrometer lo calcula sobre una ventana móvil de unos 2 minutos y después vuelve a 0. Con `rate` sobre 1 h, los paneles quedarían vacíos casi toda la semana. Por eso la última duración sale de un gauge propio y la media y el p95 usan `increase(...[7d])`, que además tolera los reinicios del contador. El gauge de la última duración vive en memoria y vuelve a 0 al reiniciar la aplicación. Por eso el panel no lee su valor actual: toma la última muestra mayor que 0 que Prometheus guardó en los 7 días anteriores (subconsulta con paso de 1 minuto). Así sigue mostrando la corrida del lunes aunque el backend se haya reiniciado después. Si no hubo ninguna corrida en esos 7 días, el panel queda sin datos, lo que es correcto. Esos paneles fijan `"timeFrom": "7d"` (tiempo relativo por panel de Grafana); el resto del dashboard conserva el rango general de 30 minutos.
