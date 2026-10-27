# Quickstart: Validación del detalle de jugador

Ejecutar desde `backend/`. Los tests usan H2 y el perfil local; no requieren Football-Data.org, WhoScored, PostgreSQL ni Redis externos.

## Prerrequisitos

- Java 17 disponible.
- Usar el Maven Wrapper incluido (`mvnw.cmd` en Windows).
- No definir credenciales reales de fuentes externas para estas pruebas.

## Contrato, seguridad y rutas

```powershell
./mvnw.cmd "-Dtest=PlayerDetailControllerTest,PlayerControllerTest,PlayerOpenApiTest,RankingControllerTest,QuoteHistoryControllerTest" test
```

La corrida debe demostrar:

- 200 con `player`, `stats` y `quote` completos;
- 200 con `stats: null` y `quote: null`, y métricas individuales nulas conservadas;
- selección de la última cotización de la estrategia activa;
- 400 `validation_error` para id no numérico, overflow de `Long`, cero o negativo; 401 sin credenciales; y 404 `player_not_found` para un id positivo inexistente;
- acceso con API key y JWT de usuario, con `X-Correlation-ID` presente;
- 200 con `FootballDataAdapter` y `WhoScoredAdapter` reemplazados por mocks y sin interacciones con ellos;
- `/players/{id}`, `/players/ranking` y `/players/{id}/quotes` resuelven a contratos distintos sin mappings ambiguos;
- OpenAPI publica [player-detail-api.yaml](contracts/player-detail-api.yaml), los tres schemas, nulabilidad, 200/400/401/404, correlación y ambas alternativas de seguridad;
- listado y sincronización conservan sus pruebas anteriores.

## Verificación de aislamiento externo

El servicio nuevo sólo debe importar los tres repositories, `ValuationStrategyCatalog`, modelos y DTOs. Comprobar la dependencia estructural con:

```powershell
rg -n "adapter|FootballData|WhoScored|PlayerCatalogService|PlayerStatsService|QuoteCalculationService" src/main/java/com/example/demo/service/PlayerDetailService.java
```

Esperado: ninguna coincidencia. Los tests anteriores deben aprobar aun cuando las URLs de fuentes externas no sean alcanzables porque el endpoint no crea clientes ni ejecuta sincronización o recálculo.

## Smoke HTTP opcional

Con la aplicación iniciada y un jugador local conocido:

```powershell
curl.exe -H "X-API-KEY: <api-key>" "http://localhost:8080/players/42"
curl.exe -H "Authorization: Bearer <jwt>" "http://localhost:8080/players/42"
curl.exe -i -H "X-API-KEY: <api-key>" "http://localhost:8080/players/no-num"
curl.exe -i "http://localhost:8080/players/42"
```

Esperado: los dos primeros pedidos siguen el contrato; el tercero responde 400 y el cuarto 401. Repetir con un id numérico inexistente debe responder 404 `player_not_found`.

## Rendimiento local

Preparar 2.500 jugadores locales, elegir uno con estadísticas y cotización, realizar 5 pedidos de warm-up no medidos y luego 100 pedidos secuenciales autenticados a su detalle. Registrar las duraciones completas: al menos 95 deben terminar en menos de 1 segundo. No habilitar ni simular fuentes externas durante la medición.

## Suite completa

```powershell
./mvnw.cmd test
```

Esperado: build exitoso, sin regresiones en catálogo, ranking, historial o recálculo, y sin cambios en `PlayerResponse`, `GlobalExceptionHandler`, esquema de datos ni contratos existentes.
