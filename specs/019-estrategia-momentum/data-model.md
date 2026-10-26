# Data Model: Algoritmo de la Estrategia 2 "MOMENTUM"

Esta feature no agrega tablas, columnas ni endpoints. Cambia una definición en código, agrega un algoritmo sin estado y amplía lo que la corrida de 016 le pasa a MOMENTUM.

## MomentumParameters (modificado)

`service/valuation/strategy/MomentumParameters.java`, record inmutable.

| Campo | Tipo | Antes | Ahora (v1) | Validación |
|-------|------|-------|------------|------------|
| `window` | `int` | 4 (cotizaciones) | 5 (ventanas de 014) | `≥ 1` |
| `minHistory` | `int` | 2 (cotizaciones) | 2 (snapshots) | `2 ≤ minHistory ≤ window + 1` |
| `recencyWeights` | `List<BigDecimal>` | — | `[5, 4, 3, 2, 1]` | tamaño `window`, sin nulos, todos `> 0`, estrictamente decrecientes; copia inmutable |
| `possibleMinutes` | `int` | — | 90 | `> 0` |
| `ratingBand` | `BigDecimal` | — | 1.5 | no nulo, `> 0` |

Una violación lanza `IllegalArgumentException` con todos los valores, como hoy.

## ValuationStrategyDefinition (modificado)

Sólo cambia `fingerprint()`: el tramo de `momentum` pasa de `window,minHistory` a `window,minHistory,[w0,w1,…],possibleMinutes,ratingBand`, con los decimales normalizados (`stripTrailingZeros().toPlainString()`). Con `momentum == null` sigue siendo `"-"`, así que la huella de OFFICIAL v1 no cambia.

## MOMENTUM v1 (redefinida)

`service/valuation/strategy/MomentumStrategyV1.DEFINITION`:

| Parámetro | Antes | Ahora |
|-----------|-------|-------|
| `baseValue` | 1.00 | 1.00 |
| `scaleFactor` | 0.15 | 0.15 |
| `maxVariation` | 0.30 | **0.15** |
| `momentum` | `(4, 2)` | **`(5, 2, [5,4,3,2,1], 90, 1.5)`** |
| pesos por posición | 013 §4 | sin cambios |

Huella: se recalcula y reemplaza `"MOMENTUM:1"` en `PublishedStrategyVersionsTest.PUBLISHED`. El motivo de la excepción a 013 §5 queda en el Javadoc de la clase y en el contrato de 013.

## MomentumFormAlgorithm (nuevo)

`service/valuation/momentum/MomentumFormAlgorithm.java`, `@Component`, sin estado.

- `type()` → `StrategyType.MOMENTUM`.
- `calculate(ValuationInput, ValuationStrategyDefinition)` → `QuoteResult` según [contracts/momentum-algorithm.md](contracts/momentum-algorithm.md).
- No sobrescribe `preparePopulation`, así que 016 guarda `population = null` para MOMENTUM.

Valores intermedios (no se persisten ni se exponen):

| Nombre | Rango | Significado |
|--------|-------|-------------|
| `rⱼ` | [0, 1] | rendimiento de la ventana j frente al promedio de temporada |
| `R` | [0, 1] | rendimiento reciente ponderado por recencia y minutos |
| `D` | [0, 1] | disponibilidad ponderada por recencia |
| `C` | ≥ 0 | penalización por tarjetas |
| `S` | [0, 1] | forma; se guarda como `score` |
| `v` | [−0.15, 0.15] | variación aplicada a la cotización anterior |

## Entradas que no cambian

- `ValuationInput`: ya tiene `windows`. Sólo se actualiza el Javadoc.

## StatsWindow (014, modificado)

Se agrega `UUID currentRunId` (no nulo): la corrida del snapshot que cierra la ventana, copiada de `PlayerStatsSnapshot.runId` (columna `run_id`, `nullable = false`) en `StatsSnapshotService.toWindow`. Sirve para detectar si hubo datos nuevos desde la última cotización MOMENTUM (`PlayerQuote.runId`, también no nulo; FR-028). No cambia ninguna tabla.
- `QuoteResult`, `QuoteStatus` y `PlayerQuote` no cambian. MOMENTUM usa `CALCULATED`, `NO_STATS` e `INSUFFICIENT_HISTORY`.

## QuoteCalculationService (016, modificado)

En `quote(player, stats, strategy, run)`:

```text
windows = definition.type() == MOMENTUM
        ? snapshotService.recentWindows(player.getId(), definition.momentum().window() + 1)
        : List.of()
```

`windows` se pasa a `ValuationInput`. El resto (cotización previa, persistencia, valor de mercado, resumen) no cambia.

## Transiciones de estado de la cotización MOMENTUM de un jugador

```text
(sin cotización) ──0 ventanas──▶ INSUFFICIENT_HISTORY (valor 1.0000)
(sin cotización) ──≥1 ventana──▶ CALCULATED (1.00 × (1 + v))
INSUFFICIENT_HISTORY ──≥1 ventana──▶ CALCULATED (valor anterior × (1 + v))
CALCULATED ──≥1 ventana, snapshot nuevo──▶ CALCULATED (valor anterior × (1 + v))
CALCULATED ──sin datos nuevos (FR-028)──▶ CALCULATED (valor anterior, v = 0)
cualquiera ──sin posiciones──▶ NO_STATS (conserva valor anterior)
```
