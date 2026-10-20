# Quickstart: Validación del algoritmo OFFICIAL

## Prerrequisitos

- JDK 17.
- Rama `feature/estrategia-oficial` con la implementación terminada.
- Ejecutar los comandos desde `backend/`.
- No se necesitan PostgreSQL, Redis ni acceso a WhoScored: todas las pruebas son unitarias.

Las fórmulas y los números esperados están en [contracts/valuation-strategies.md](contracts/valuation-strategies.md), los records en [data-model.md](data-model.md) y las decisiones en [research.md](research.md).

## 1. Suite rápida

```powershell
./mvnw.cmd test "-DexcludedGroups=e2e"
```

Resultado esperado: todo pasa, incluido `PublishedStrategyVersionsTest` sin cambios (OFFICIAL v1 no se tocó).

## 2. Normalización

```powershell
./mvnw.cmd "-Dtest=MetricNormalizerTest" test
```

Resultado esperado:

- 5 goles en 450 minutos dan 1.0 por 90;
- min-max por posición: en el ejemplo de §5.1 del contrato, C da 0.5 en cada métrica;
- max = min (incluido un solo jugador) da 0.5;
- rating 7.0 da 0.70, acotado a [0,1];
- un null individual cuenta 0, y una métrica null en toda la posición aparece en `metricsWithoutData`;
- sin stats, sin posiciones, minutos null, 0 u 89 minutos quedan fuera de la población, y 90 minutos queda dentro;
- un jugador con dos posiciones aparece en ambas;
- un `playerId` duplicado se rechaza.

## 3. Algoritmo

```powershell
./mvnw.cmd "-Dtest=OfficialPerformanceAlgorithmTest,ValuationInputTest,QuoteResultTest" test
```

Resultado esperado: los valores coinciden con §5 del contrato.

| Caso | Valor esperado |
|------|----------------|
| MIDFIELDER A / B / C | 1.0960 / 1.0120 / 1.0540 |
| MIDFIELDER sin gambetas A / B / C | 1.0956 / 1.0133 / 1.0544, con `{MIDFIELDER=[DRIBBLES]}` |
| GOALKEEPER G1 / G2 | 1.0804 / 1.0462, sin exclusiones informadas |
| DEFENDER único | 1.0560 |
| FORWARD F1 / F2 | 1.0856 / 1.0236 |
| X (MIDFIELDER + FORWARD) | score 0.4738, valor 1.0474 |
| Tope con anterior 0.80 / 1.50 | 0.9600 / 1.2000 |
| DEFENDER único con rating 7.15 (empate HALF_UP) | score 0.5645, valor 1.05645 → 1.0565 |

Además:

- sin stats, sin posiciones, con 0 minutos y por debajo del umbral de la población, el resultado es `NO_STATS` con valor y score null;
- 10 permutaciones de la población producen resultados idénticos;
- se rechazan con `IllegalArgumentException`:
  - una definición MOMENTUM;
  - una población armada con otro umbral;
  - una cotización previa de otra estrategia;
  - una cotización más reciente con valor null;
  - `previousQuotes` fuera de orden descendente.

  Estos rechazos se producen también cuando el jugador sería NO_STATS. Por ejemplo, un jugador sin stats con una anterior de valor null lanza error en lugar de devolver NO_STATS.

## 4. Propiedad del umbral

```powershell
./mvnw.cmd "-Dtest=OfficialMinMinutesPropertyTest" test
```

Resultado esperado: sin la propiedad el umbral es 90; con `valuation.official.min-minutes=270`, 200 minutos da NO_STATS; con 0 o un número negativo el contexto no arranca.

## 5. Comprobaciones de alcance

```powershell
git diff --stat main -- backend/src/main
```

Resultado esperado:

- sólo archivos nuevos bajo `service/valuation/` (interfaz, records y normalización) y `service/valuation/official/` (únicamente `OfficialPerformanceAlgorithm.java`);
- ningún cambio en `model/`, `repository/`, `adapter/`, `OfficialStrategyV1.java`, `ValuationStrategyDefinition.java` ni en `application*.properties`.
