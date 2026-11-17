# Quickstart: validar el ranking de alta frecuencia

## Registro de esta implementación (2026-11-17)

La línea de base de 14 pruebas y la suite focalizada de 27 pruebas se ejecutaron localmente sin fallos. `./mvnw.cmd test -q` registró 1.076 pruebas, 0 fallos, 0 errores y 49 omitidas por sus condiciones de entorno. En la reanudación, Redis, PostgreSQL y el backend estuvieron disponibles: se midieron las solicitudes HTTP, ambos planes SQL y las métricas reales de `/actuator/prometheus` con un JWT `ROLE_ADMIN`. Los valores de las credenciales no se registran en esta guía.

Una pasada adicional de las pruebas de eventos y concurrencia, tras añadir dos casos, registró 8 pruebas sin fallos. El estado actualizado de T024–T026 está en `tasks.md`; T029–T031 siguen reservadas para después del PR.

Tras declarar el índice medido en `PlayerQuote.java`, la pasada focalizada de `RankingRedisFallbackTest`, `RankingCacheMetricsTest`, `CacheConfigTest`, `QuoteRankingCacheIntegrationTest`, `RankingControllerTest`, `PlayerQuoteRepositoryTest` y `PlayerQuoteTest` registró 50 pruebas, 0 fallos y 0 errores. El `EXPLAIN` de página y conteo y la comparación antes/después están en [research.md](research.md); T025 y T026 quedan completadas.

Ejecutar desde la raíz del repositorio en PowerShell. Requiere backend levantado en `localhost:8080`, PostgreSQL y Redis de la demo disponibles, una API key válida como variable de usuario `RANKING_API_KEY` de Windows, `curl.exe` y `redis-cli` para repetir el borrado. `/actuator/prometheus` requiere una credencial con `ROLE_ADMIN`. El contrato esperado está en [contracts/ranking-api.md](contracts/ranking-api.md).

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
$rankingApiKey = [Environment]::GetEnvironmentVariable('RANKING_API_KEY', 'User')
$url = 'http://localhost:8080/players/ranking'
$first = curl.exe -sS -o NUL -w '%{time_total}' -H "X-API-KEY: $rankingApiKey" $url
Write-Host "primera=$first s"
1..10 | ForEach-Object {
    $elapsed = curl.exe -sS -o NUL -w '%{time_total}' -H "X-API-KEY: $rankingApiKey" $url
    Write-Host "siguiente=$_ $elapsed s"
}
```

**Medición local (2026-11-17)**: Se leyó la API key de la variable de usuario de Windows en memoria, sin imprimirla. Un `SCAN` de Redis eliminó exactamente una clave que coincidía con `quotes-ranking::active|-|-|0|20|gen=*`; no se borraron otros cachés. Se hicieron once `curl.exe -sS -o NUL -w '%{http_code} %{time_total}'` consecutivos contra `GET /players/ranking`, sin parámetros. Todas las respuestas fueron HTTP 200.

| Solicitud | 1 (fría) | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `time_total` (ms) | 143,329 | 33,338 | 24,100 | 24,850 | 25,488 | 23,927 | 23,530 | 17,893 | 19,112 | 19,818 | 23,994 |

Las diez solicitudes posteriores estuvieron por debajo de 50 ms (máximo 33,338 ms). El tiempo incluye la autenticación HTTP completa.

**Repetición con el índice ya aplicado**: Se volvió a eliminar sólo la clave `active` de la página 0 (una clave) y se hicieron otras once solicitudes HTTP 200. La primera tardó 375,535 ms. Las diez siguientes tardaron 25,628; 39,003; 59,945; 30,852; 22,306; 23,089; 28,054; 21,961; 25,259 y 26,705 ms. Hubo un hit por encima del objetivo de 50 ms (59,945 ms); no se oculta ese pico. Veinte hits consecutivos adicionales, sin vaciar el caché, dieron HTTP 200 y entre 12,713 y 23,340 ms cada uno. La evidencia local muestra 39 de 40 hits por debajo de 50 ms en estas dos tandas y la ampliación; el objetivo por solicitud no se cumple de forma absoluta en todos los intentos.

Comprobar HTTP 200 y el cuerpo completo al menos una vez, sin `-o NUL`. `curl -w '%{time_total}'` mide la solicitud HTTP completa, incluida la autenticación. Registrar los once tiempos por separado; objetivo local: cada solicitud cacheada posterior a la primera, menos de `0.050` s. La primera puede incluir la lectura y carga. La comprobación de cero repositorios/transacciones/conexiones se realiza por separado en el límite del servicio, no con estos tiempos HTTP. Si un precalentado repobló la página entre `DEL` y la primera solicitud, repetir en una ventana sin actualización.

## Aciertos, fallos, latencia y errores

```powershell
$adminJwt = [Environment]::GetEnvironmentVariable('RANKING_ADMIN_JWT', 'User')
if (-not $adminJwt) { throw 'Se requiere un JWT ROLE_ADMIN para Prometheus' }
curl.exe -sS -H "Authorization: Bearer $adminJwt" 'http://localhost:8080/actuator/prometheus' | Select-String 'cache_gets_total.*quotes-ranking'
curl.exe -sS -H "Authorization: Bearer $adminJwt" 'http://localhost:8080/actuator/prometheus' | Select-String 'http_server_requests'
```

**Scrape real (2026-11-17)**: Con el JWT administrativo, `/actuator/prometheus` devolvió HTTP 200. Se observaron `cache_gets_total{application="demo",cache="quotes-ranking",cache_manager="redis",name="quotes-ranking",result="hit"}` y las mismas etiquetas con `result="miss"` y `result="pending"`. Antes de una prueba controlada, `hit=30`, `miss=2`, `pending=0`. Se borró sólo la clave `active` de la primera página y se hicieron una carga fría (HTTP 200, 142,187 ms) y dos hits (HTTP 200, 13,973 y 15,219 ms). El scrape posterior dio `hit=32`, `miss=4`, `pending=0`. Una carga fría suma dos misses porque el servicio hace la lectura inicial y luego `Cache.get(key, Callable)`; los dos hits sumaron dos aciertos. Las estadísticas son locales a la instancia consultada. La API key de usuario usada en las mediciones HTTP recibió 403 en Prometheus, conforme al requisito `ROLE_ADMIN`.

## Actualización y resiliencia

Después de un recálculo exitoso y después de confirmar un cambio de estrategia, consultar la primera página sin parámetros y comprobar que refleja el estado confirmado. En la prueba automatizada, esperar con Awaitility a que la página vuelva al caché y verificar que la operación de cambio respondió antes del fin de la recarga. Cortar Redis en un entorno de prueba y repetir una consulta válida: debe responder desde PostgreSQL con el mismo contrato y orden. Restablecer Redis al terminar.

## EXPLAIN de la base de demo

Con acceso a la base de demo, ejecutar `EXPLAIN (ANALYZE, BUFFERS)` de la consulta completa de `PlayerQuoteRepository.findRanking` y de su `countQuery`, usando la misma población y parámetros antes y después de cualquier índice candidato. Guardar SQL, cantidad de filas, planes y tiempos en [research.md](research.md). No crear el índice si sólo aparece el sort final por valor calculado o si la comparación no muestra mejora.

## Verificaciones manuales posteriores a publicación

Permanecen pendientes hasta que la usuaria confirme, haga commit/push y abra el PR: comprobar GitHub Actions en `SUCCESS`, SonarCloud en `PASSED`, y probar `GET /players/ranking` en Swagger UI una vez con JWT y otra con API key. `$speckit-implement` no realiza esas acciones ni marca estas verificaciones como completas.
