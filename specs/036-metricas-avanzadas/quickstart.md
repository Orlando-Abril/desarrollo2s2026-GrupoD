# Quickstart: validar las métricas de negocio

## Prerrequisitos

- Lo mismo que en la [quickstart de la 035](../035-actuator-prometheus/quickstart.md): Java 17, PostgreSQL y Redis locales según el [README](../../README.md), variables del backend y una API key ADMIN en `$env:SCRAPE_API_KEY` (la 035 explica cómo crearla en la base de **desarrollo local**).
- Al menos un jugador cotizado y con órdenes de venta del market maker. Ocurre después de un recálculo (ver el paso 3).

## Verificación automatizada

Desde `backend/`:

```powershell
./mvnw.cmd -Dtest="BusinessMetricsTest,MeteredCacheResolverTest,BusinessMetricsPrometheusTest,OrderMetricsTest,OrderMetricsFailureTest,OrderService*Test,MarketMakerServiceTest,QuoteCalculationServiceTest,QuoteRunActiveSnapshotTest,QuoteRecalculationSchedulerTest,ActiveStrategyMetricsListenerTest,FootballDataAdapterTest,FootballDataSecretLeakTest,FootballDataCacheMetricsTest,WhoScoredAdapterTest,PlayerStatsServiceTest,AuthServiceTest,LayeredArchitectureTest,CodingRulesTest,NamingConventionTest" test
./mvnw.cmd test -DexcludedGroups=e2e
```

Resultado esperado: todas las pruebas en verde. Las pruebas cubren los nombres y las etiquetas del [contrato](contracts/business-metrics.md), los incrementos de cada escenario de la spec, que nada se cuente si la transacción se revierte, que un fallo al medir (simulado con un meter que lanza) no cambie el resultado de la operación, que una excepción inesperada de una fuente externa cuente como `error`, que la segunda consulta a Football-Data salga de la caché sin solicitud HTTP y que `/actuator/prometheus` muestre `gdmarket_orders_placed_total` a una API key ADMIN y responda 403 a una USER. Las pruebas e2e las corre el usuario por separado.

## Verificación manual: operar dos órdenes

Con el backend iniciado (`./mvnw.cmd spring-boot:run` o el jar), en PowerShell:

```powershell
$api = 'http://localhost:8080'
$admin = @{ 'X-API-KEY' = $env:SCRAPE_API_KEY }
function Show-Gdmarket {
  (curl.exe -s -H "X-API-KEY: $env:SCRAPE_API_KEY" "$api/actuator/prometheus") -split "`n" |
    Select-String '^gdmarket_(orders|trades|quotes_recalculation_seconds_count|quotes_recalculation_last|quotes_calculated|users|auth|external)'
}
```

1. **Línea de base**: `Show-Gdmarket` muestra todas las combinaciones de `gdmarket_orders_placed_total`, `gdmarket_orders_rejected_total` y `gdmarket_external_calls_total` en 0 (si la app recién inició), y `gdmarket_users_registered` igual a la cantidad de cuentas.
2. **Usuario**: registrar uno y hacer login.
   ```powershell
   $u = @{ username = 'metricas-demo'; email = 'metricas-demo@example.com'; password = 'Password123!' } | ConvertTo-Json
   Invoke-RestMethod -Method Post "$api/auth/register" -ContentType 'application/json' -Body $u
   $login = Invoke-RestMethod -Method Post "$api/auth/login" -ContentType 'application/json' -Body (@{ username = 'metricas-demo'; password = 'Password123!' } | ConvertTo-Json)
   $user = @{ Authorization = "Bearer $($login.token)" }
   ```
   Esperado: `gdmarket_auth_registrations_total{outcome="success"}` +1, `gdmarket_auth_logins_total{outcome="success"}` +1 y `gdmarket_users_registered` +1. Repetir el registro produce `outcome="failure"` +1 y no cambia el gauge.
3. **Recálculo manual**: `Invoke-RestMethod -Method Post "$api/quotes/recalculate" -Headers $admin`. Esperado: `gdmarket_quotes_recalculation_seconds_count{trigger="manual",outcome="success"}` +1, `gdmarket_quotes_recalculation_last_seconds` mayor que 0 (y sigue mostrando ese valor minutos después, a diferencia de `_max`) y `gdmarket_quotes_calculated_total{strategy=...}` con la cantidad `calculated` de la respuesta para cada estrategia.
4. **Orden 1, compra MARKET que cruza**: elegir un `playerId` con ventas en `GET $api/orders/book?playerId=...` y comprar 1 token.
   ```powershell
   Invoke-RestMethod -Method Post "$api/orders/buy" -Headers $user -ContentType 'application/json' -Body (@{ playerId = $playerId; type = 'MARKET'; quantity = 1 } | ConvertTo-Json)
   ```
   Esperado: `gdmarket_orders_placed_total{side="BUY",type="MARKET"}` +1, `gdmarket_trades_executed_total` +1, `gdmarket_trades_tokens_total` +1 y `gdmarket_trades_volume_credits_total` aumentado en el precio del trade.
5. **Orden 2, venta LIMIT fuera de banda**: vender ese token con `limitPrice` mayor al 120 % de la cotización. Esperado: respuesta de error de banda, `gdmarket_orders_rejected_total{reason="price_out_of_band"}` +1 y `gdmarket_orders_placed_total` sin cambios.
6. **Cambio de estrategia** (opcional): `PUT $api/strategies/active` como ADMIN con la otra estrategia. Esperado: `gdmarket_strategy_changes_total` +1.
7. **Etiquetas**: en `Show-Gdmarket`, ninguna serie `gdmarket_` contiene ids, nombres de usuario ni de jugador. Sin `X-API-KEY`, `/actuator/prometheus` responde 401; con la API key del usuario `metricas-demo`, 403.

## Dashboard en Grafana

Desde la raíz del proyecto, con `$env:SCRAPE_API_KEY` asignada:

```powershell
docker compose -f observability/docker-compose.yml up -d
```

Abrir `http://localhost:3000`, carpeta **Demo**, dashboard **GD Market**. Después de uno o dos scrapes (15 s cada uno) se ven los pasos anteriores: órdenes y trades por minuto, volumen, duración del último recálculo y media y p95 de los últimos 7 días (esos paneles usan su propio rango de 7 días), llamadas externas por resultado, usuarios, y la latencia p95 y la tasa de error 5xx HTTP de la 035. Sin actividad, los paneles muestran 0, no "No data" (salvo cotizaciones por estrategia antes del primer recálculo). Al terminar, `docker compose -f observability/docker-compose.yml down`.

## Verificaciones de entrega

Igual que en la 035: el usuario confirma los cambios, publica la rama y abre el PR; después se comprueba `CI` en `SUCCESS` y el Quality Gate de SonarCloud en `PASSED`. `$speckit-implement` no hace commit, push ni PR.
