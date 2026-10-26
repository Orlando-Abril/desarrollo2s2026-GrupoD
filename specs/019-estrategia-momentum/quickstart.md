# Quickstart: validación de MOMENTUM

Ejecutar desde `backend/`. Los tests unitarios y de servicio no necesitan base de datos externa. El E2E requiere PostgreSQL y Redis según `application-e2e.properties`. Ninguno consulta fuentes externas.

## 1. Definición y huella

```powershell
./mvnw.cmd "-Dtest=MomentumStrategyV1Test,ValuationStrategyDefinitionTest,PublishedStrategyVersionsTest,OfficialStrategyV1Test" test
```

Debe comprobar:

- MOMENTUM v1 con `maxVariation` 0.15, `scaleFactor` 0.15 y `momentum = (5, 2, [5,4,3,2,1], 90, 1.5)`. Los pesos por posición no cambian.
- Las validaciones de `MomentumParameters`: tamaño de los pesos de recencia, que sean decrecientes, `minHistory` en rango, minutos y margen de rating positivos.
- Que cambiar cualquier campo nuevo cambia la huella.
- Que la huella de OFFICIAL v1 sigue igual.
- Que la de MOMENTUM v1 coincide con la nueva entrada de `PUBLISHED`.

## 2. Algoritmo

```powershell
./mvnw.cmd "-Dtest=MomentumFormAlgorithmTest*" test
```

Debe reproducir a 4 decimales los ejemplos §5.1–§5.11 de [contracts/momentum-algorithm.md](contracts/momentum-algorithm.md). También debe cubrir los escenarios de la spec:

- alza, baja y neutro;
- recencia;
- tope de ±15%;
- amarilla contra roja, y tarjeta reciente contra tarjeta vieja;
- sin minutos, y menos minutos;
- menos de 2 snapshots y sin cotización previa;
- varias posiciones;
- métrica `null` y sin estadísticas de temporada;
- más de 5 ventanas y orden de entrada;
- errores de validación.

Resultado esperado: todos en verde y ningún valor fuera de ±15% en la prueba de propiedad.

## 3. Corrida de recálculo (016)

```powershell
./mvnw.cmd "-Dtest=QuoteCalculationServiceTest" test
```

Debe comprobar:

- Con `MomentumFormAlgorithm` registrado, MOMENTUM no aparece en `skipped`.
- `recentWindows(playerId, 6)` se llama una vez por jugador para MOMENTUM y nunca para OFFICIAL.
- OFFICIAL recibe `windows` vacío y da los mismos resultados que antes.
- Un `INSUFFICIENT_HISTORY` guarda la cotización anterior o 1.0000.

## 4. E2E

```powershell
./mvnw.cmd "-Dtest=QuoteRecalculationE2ETest" test
```

Primera corrida sin snapshots previos: `skippedStrategies` está vacío y cada jugador tiene una cotización OFFICIAL y una MOMENTUM. Las MOMENTUM quedan `INSUFFICIENT_HISTORY` con valor 1.0000, porque cada jugador tiene un solo snapshot. El valor de mercado sigue a OFFICIAL.

Para ver un `CALCULATED`, cambiar las estadísticas de un jugador y correr una segunda vez. MOMENTUM queda `CALCULATED` con `|valor − 1| ≤ 0.15`. Tres corridas seguidas más sin cambiar las estadísticas no capturan snapshots nuevos y dejan MOMENTUM `CALCULATED` con el mismo valor en las tres.

## 5. Smoke manual (opcional)

Con el backend levantado y una API key de admin:

```powershell
curl -X POST http://localhost:8080/quotes/recalculate -H "X-API-KEY: <admin-key>"
curl "http://localhost:8080/players/<id>/quotes?strategy=MOMENTUM" -H "X-API-KEY: <key>"
```

La respuesta de la corrida no lista MOMENTUM en `skippedStrategies`, y el historial MOMENTUM del jugador muestra la cotización nueva.

## 6. Suite completa

```powershell
./mvnw.cmd test
```

CI en `SUCCESS` y SonarCloud sin issues nuevos (§3.1 y §3.2).

## Resultados

Ejecución del 2026-10-26 en Windows, desde `backend/`:

| Comando | Resultado |
|---------|-----------|
| `./mvnw.cmd "-Dtest=MomentumStrategyV1Test,ValuationStrategyDefinitionTest,PublishedStrategyVersionsTest,OfficialStrategyV1Test" test` | En verde. La huella de OFFICIAL v1 no cambió; la nueva de MOMENTUM v1 es `e772d840…2fc1`. |
| `./mvnw.cmd "-Dtest=MomentumFormAlgorithmTest*" test` | 48 tests en verde (47 escenarios y ejemplos §5.1–§5.11 a 4 decimales, más 1 de propiedad con 500 casos). |
| `./mvnw.cmd "-Dtest=QuoteCalculationServiceTest" test` | 33 tests en verde, 6 nuevos de MOMENTUM. |
| `./mvnw.cmd "-Dtest=QuoteRecalculationE2ETest" test` | 3 tests en verde (0 salteados) contra la base dedicada `desarrollo2_grupod_e2e` (`E2E_DB_URL` y `E2E_DB_PASSWORD` definidas en la terminal) y Redis local. Primera corrida: MOMENTUM `INSUFFICIENT_HISTORY` en los 5 jugadores; segunda, con estadísticas nuevas, `CALCULATED`; tres corridas más sin sincronizar mantienen el valor. `skippedStrategies` vacío y 0 jugadores fallidos en todas. |
| `./mvnw.cmd test` | Con Redis levantado y la base `desarrollo2_grupod_e2e`: 606 tests, 0 fallas, 0 errores, 2 salteados (`FootballDataRedisIntegrationTest` y `WhoScoredRedisCacheIntegrationTest`, que usan Testcontainers y necesitan Docker). |

SonarCloud no se corrió localmente; se verifica en el CI del PR.
