# Quickstart: validar el ranking de alta frecuencia

## Registro de esta implementación (2026-11-17)

La línea de base de 14 pruebas y la suite focalizada de 27 pruebas se ejecutaron localmente sin fallos. `./mvnw.cmd test -q` registró 1.076 pruebas, 0 fallos, 0 errores y 49 omitidas por sus condiciones de entorno. Los tiempos de la primera solicitud y de las diez siguientes, el scrape real de `/actuator/prometheus` y el `EXPLAIN (ANALYZE, BUFFERS)` permanecen sin medir: no hay backend ni Redis local activos, falta una API key de prueba y no está disponible la contraseña de PostgreSQL de la demo. Ejecutar las secciones siguientes cuando estén los servicios y las credenciales. No se afirma que el objetivo de 50 ms esté cumplido hasta medir el HTTP completo.

Una pasada adicional de las pruebas de eventos y concurrencia, tras añadir dos casos, registró 8 pruebas sin fallos. En `tasks.md` quedan pendientes T024 (medición HTTP y scrape real), T025 (plan SQL) y T026 (índice condicional a ese plan), además de T029–T031, que son verificaciones manuales posteriores al PR.

Ejecutar desde la raíz del repositorio en PowerShell. Requiere backend levantado en `localhost:8080`, PostgreSQL y Redis de la demo disponibles, una API key válida en `$env:RANKING_API_KEY`, `curl.exe` y `redis-cli`. `/actuator/prometheus` requiere credencial. El contrato esperado está en [contracts/ranking-api.md](contracts/ranking-api.md).

## Pruebas focalizadas

Para volver a verificar la implementación, ejecutar:

```powershell
Set-Location backend
./mvnw.cmd "-Dtest=RankingHitBoundaryTest,RankingEmptyAliasTest,RankingHighFrequencyContractTest,RankingSingleFlightTest,RankingSingleFlightFailureTest,RankingWarmerEventTest,RankingWarmerRaceTest,RankingRedisFallbackTest,RankingCacheMetricsTest,QuoteQueryServiceCacheTest,QuoteQueryServiceRepositorySpyTest,QuoteRankingCacheIntegrationTest,QuoteCacheEvictorTest,RankingControllerTest,CacheConfigTest,ActiveStrategyRecalculationTest" test
./mvnw.cmd test
```

La ampliación de pruebas de esta feature debe demostrar que, después de completar la autenticación y entrar en `QuoteQueryService.ranking`, un hit no llama a repositorios, no abre transacción y no hace checkout de conexión atribuible al servicio. También debe cubrir 20 hilos de una misma instancia con la misma clave ausente y una sola consulta; publicación del mismo resultado bajo `active` y estrategia explícita, incluida página vacía; fallo del líder sin bloqueo de seguidores; página 0 recargada tras `QuoteRunCompletedEvent` con Awaitility; carrera determinista donde la carga vieja termina después de `clear()` y se elimina o queda inaccesible mientras se limpia; recarga tras cambio de estrategia; y respuesta desde base con Redis caído. `QuoteRankingCacheIntegrationTest` debe seguir pasando sin editarse. No se promete coordinación entre réplicas. Los filtros de autenticación pueden conservar sus consultas actuales a la base.

## Primera consulta y diez siguientes

Preparar una página fría de la ruta que usa el frontend. Las claves del alias llevan una generación dinámica: `quotes-ranking::active|-|-|0|20|gen=<id-arranque>:<contador>`. El patrón siguiente borra sólo las generaciones de esa página del ranking; no usa `FLUSHDB` ni toca otros cachés.

```powershell
redis-cli --scan --pattern 'quotes-ranking::active|-|-|0|20|gen=*' | ForEach-Object { redis-cli DEL $_ }
$url = 'http://localhost:8080/players/ranking'
$first = curl.exe -sS -o NUL -w '%{time_total}' -H "X-API-KEY: $env:RANKING_API_KEY" $url
Write-Host "primera=$first s"
1..10 | ForEach-Object {
    $elapsed = curl.exe -sS -o NUL -w '%{time_total}' -H "X-API-KEY: $env:RANKING_API_KEY" $url
    Write-Host "siguiente=$_ $elapsed s"
}
```

Comprobar HTTP 200 y el cuerpo completo al menos una vez, sin `-o NUL`. `curl -w '%{time_total}'` mide la solicitud HTTP completa, incluida la autenticación. Registrar los once tiempos por separado; objetivo local: cada solicitud cacheada posterior a la primera, menos de `0.050` s. La primera puede incluir la lectura y carga. La comprobación de cero repositorios/transacciones/conexiones se realiza por separado en el límite del servicio, no con estos tiempos HTTP. Si un precalentado repobló la página entre `DEL` y la primera solicitud, repetir en una ventana sin actualización.

## Aciertos, fallos, latencia y errores

```powershell
curl.exe -sS -H "X-API-KEY: $env:RANKING_API_KEY" 'http://localhost:8080/actuator/prometheus' | Select-String 'cache_gets_total.*quotes-ranking'
curl.exe -sS -H "X-API-KEY: $env:RANKING_API_KEY" 'http://localhost:8080/actuator/prometheus' | Select-String 'http_server_requests'
```

Esperado tras la medición: series diferenciadas para `result="miss"` y `result="hit"` del caché `quotes-ranking`, además de las métricas HTTP existentes. Las estadísticas son locales a la instancia consultada. Si el nombre exportado difiere, registrar el nombre real en [research.md](research.md) y ajustar esta guía.

## Actualización y resiliencia

Después de un recálculo exitoso y después de confirmar un cambio de estrategia, consultar la primera página sin parámetros y comprobar que refleja el estado confirmado. En la prueba automatizada, esperar con Awaitility a que la página vuelva al caché y verificar que la operación de cambio respondió antes del fin de la recarga. Cortar Redis en un entorno de prueba y repetir una consulta válida: debe responder desde PostgreSQL con el mismo contrato y orden. Restablecer Redis al terminar.

## EXPLAIN de la base de demo

Con acceso a la base de demo, ejecutar `EXPLAIN (ANALYZE, BUFFERS)` de la consulta completa de `PlayerQuoteRepository.findRanking` y de su `countQuery`, usando la misma población y parámetros antes y después de cualquier índice candidato. Guardar SQL, cantidad de filas, planes y tiempos en [research.md](research.md). No crear el índice si sólo aparece el sort final por valor calculado o si la comparación no muestra mejora.

## Verificaciones manuales posteriores a publicación

Permanecen pendientes hasta que la usuaria confirme, haga commit/push y abra el PR: comprobar GitHub Actions en `SUCCESS`, SonarCloud en `PASSED`, y probar `GET /players/ranking` en Swagger UI una vez con JWT y otra con API key. `$speckit-implement` no realiza esas acciones ni marca estas verificaciones como completas.
