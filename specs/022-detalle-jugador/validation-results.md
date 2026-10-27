# Resultados de validación: detalle de jugador

**Fecha**: 2026-10-27

## Aislamiento de fuentes externas

Comando ejecutado desde `backend/`:

```powershell
rg -n "adapter|FootballData|WhoScored|PlayerCatalogService|PlayerStatsService|QuoteCalculationService" src/main/java/com/example/demo/service/PlayerDetailService.java
```

Resultado: ninguna coincidencia. `PlayerDetailService` depende exclusivamente de repositories locales, DTOs, modelos y `ValuationStrategyCatalog`.

## Suite focalizada del quickstart

```powershell
./mvnw.cmd "-Dtest=PlayerDetailControllerTest,PlayerControllerTest,PlayerOpenApiTest,RankingControllerTest,QuoteHistoryControllerTest" test
```

Resultado: **BUILD SUCCESS**; 39 tests ejecutados, 0 fallas, 0 errores y 0 omitidos.

La suite confirma datos completos y opcionales, selección de estrategia activa, validación 400/401/404, autenticación por API key/JWT, correlación, aislamiento externo, OpenAPI y coexistencia de detalle, ranking e historial.

## Suite completa del backend

```powershell
./mvnw.cmd test
```

Resultado local: **BUILD FAILURE**; 629 tests ejecutados, 2 fallas, 0 errores y 32 omitidos. Las dos fallas pertenecen a `PlayerStatsServiceTest` (`knownEquivalenceMatchesTeamsWhoseNamesDoNotResemble` y `leagueFailureLogIncludesWhatTheSourceAnswered`) y son ajenas al detalle de jugador: después de inicializar un contexto Spring en Windows, la captura de consola reemplaza caracteres no ASCII por `?`. El mismo test aislado pasa sus 39 casos; al ejecutarlo detrás de `HealthEndpointTest` reproduce las dos fallas. No se modificó código fuera del alcance para ocultar esta limitación ambiental preexistente.

`git diff --check` finalizó sin errores. `PlayerResponse.java` y `GlobalExceptionHandler.java` no presentan diferencias respecto de `HEAD`; también permanecen intactos `QuoteExceptionHandler.java` y los métodos existentes de `PlayerController` salvo la inyección necesaria del nuevo servicio.

## Validación remota

Pendiente de un push: T024 requiere comprobar el workflow de GitHub Actions y el Quality Gate de SonarCloud sobre el commit que contenga estos cambios. No se generaron enlaces ni resultados antes de que exista esa ejecución remota.
