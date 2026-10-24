# Data Model: Recálculo de cotizaciones

**Feature**: [spec.md](spec.md) | **Research**: [research.md](research.md)

No se crean tablas. Cambia una columna existente y se agregan tipos de dominio y DTOs.

## Cambios en entidades existentes

### Player (`players`), archivo de Entrega 1, cambio autorizado

| Campo | Antes | Después |
|-------|-------|---------|
| `marketValue` (`market_value`) | `numeric` con la precisión por defecto de Hibernate (escala 2), `NOT NULL` | `numeric(12,4)`, `NOT NULL` |

- Sólo la corrida escribe `marketValue`, con el mismo `BigDecimal` (escala 4) que la cotización de la estrategia activa en esa corrida.
- Invariante después de una corrida que calculó la estrategia activa: para cada jugador procesado, `player.marketValue.compareTo(quote.value) == 0`, donde `quote` es su cotización de la estrategia activa con el `runId` de la corrida.
- Las bases existentes necesitan una migración manual (ver [quickstart.md](quickstart.md#0-migrar-una-base-existente)).

### PlayerQuote (`player_quotes`), sin cambios

La corrida crea cotizaciones así:

| Campo | Valor |
|-------|-------|
| `player` | jugador procesado (entidad administrada en la transacción del jugador) |
| `strategyCode`, `strategyVersion` | de la `ValuationStrategyDefinition` calculada |
| `value` | `CALCULATED`: valor del algoritmo; si no: valor de la última cotización de esa estrategia o `baseValue` con escala 4 |
| `score` | `CALCULATED`: score del algoritmo; si no: `null` |
| `status` | `QuoteResult.status()` |
| `calculatedAt` | instante único de la corrida |
| `runId` | id único de la corrida |
| `correlationId` | correlation id del MDC o el generado por la corrida |

### User (`users`), sin cambios

`role` (`USER` / `ADMIN`) ya existe. Sólo se lee para armar las authorities del JWT. Ningún endpoint lo cambia (ver quickstart).

## Contrato modificado (feature 015)

### ValuationAlgorithm

```java
default NormalizedPopulation preparePopulation(Collection<PopulationEntry> entries) { return null; }
```

- `OfficialPerformanceAlgorithm` lo sobrescribe y devuelve `normalize(entries)`.
- La corrida lo llama una vez por estrategia calculada, con un `PopulationEntry` por jugador del catálogo (`stats` null si no tiene).

## Tipos nuevos de dominio (`service/valuation`)

### QuoteRunResult (record)

| Campo | Tipo | Regla |
|-------|------|-------|
| `runId` | `UUID` | no null |
| `calculatedAt` | `Instant` | no null |
| `requestedStrategy` | `String` | null si se pidieron todas |
| `calculated` | `List<StrategyRunCounts>` | en el orden del catálogo |
| `skipped` | `List<SkippedStrategy>` | en el orden del catálogo |
| `playersProcessed` | `int` | ≥ 0 |
| `playersFailed` | `int` | ≥ 0; `processed + failed` = jugadores considerados |
| `activeStrategy` | `String` | código de la estrategia activa |
| `marketValueUpdated` | `boolean` | ver regla abajo |

`marketValueUpdated = activeCalculated && (playersProcessed > 0 || playersConsidered == 0)`.

### StrategyRunCounts (record)

`code`, `version`, `calculated`, `noStats`, `insufficientHistory` (enteros ≥ 0). Sólo cuentan jugadores procesados. La suma de los tres = `playersProcessed`.

### SkippedStrategy (record)

`code`, `version`, `reason`. El único motivo hoy es `no_algorithm`.

### Excepciones nuevas (`exception/`)

| Clase | Cuándo | HTTP |
|-------|--------|------|
| `RecalculationInProgressException` | la guardia ya está tomada | 409 `recalculation_in_progress` |
| `RecalculationFailedException` | el snapshot u otro paso general falla; envuelve la causa | 500 `recalculation_failed` |

`UnknownValuationStrategyException` (013) se reusa para 400 `unknown_strategy`.

## DTOs nuevos (`dto/quote`)

### RecalculateRequest

| Campo | Tipo | Regla |
|-------|------|-------|
| `strategy` | `String` | opcional. Ausente o `null` = todas; cualquier otro valor tiene que coincidir exactamente con un código del catálogo |

Todo el cuerpo es opcional (`@RequestBody(required = false)`).

### QuoteRunResponse

Es el mapeo 1:1 de `QuoteRunResult`, con `strategies` (calculadas) y `skippedStrategies`. El esquema y un ejemplo están en [contracts/quotes-api.yaml](contracts/quotes-api.yaml).

## Flujo de la corrida

```text
recalculate(Optional<String> code)
 ├─ definitions = code.map(catalog::find → List.of).orElse(catalog.all())   // 400 si no existe
 ├─ running.compareAndSet(false, true) o RecalculationInProgressException  // 409
 └─ try
     ├─ runId, calculatedAt = clock.instant(), correlationId (MDC)
     ├─ log started
     ├─ snapshotAll(runId)                       // falla → RecalculationFailedException
     ├─ [tx read-only]                           // falla → RecalculationFailedException
     │    players  = playerRepository.findAll()
     │    stats    = playerStatsRepository.findAll() → Map<playerId, PlayerStats>
     │    por definición con algoritmo:
     │       population = algorithm.preparePopulation(entries)
     │       latest     = quoteRepository.findLatestForAllPlayers(code) → Map<playerId, PlayerQuote>
     │    por definición sin algoritmo: skipped(no_algorithm)
     ├─ por jugador:
     │    try [tx] por estrategia calculable:
     │            result = algorithm.calculate(new ValuationInput(id, positions, stats, population,
     │                                                            latest?List.of(q):List.of(), List.of()), def)
     │            quote  = toQuote(result, latest, def, runId, calculatedAt, correlationId)
     │          saveAll(quotes); si activa calculada → player.setMarketValue(valor de la activa)
     │          processed++, sumar conteos
     │    catch RuntimeException → log player_failed, failed++
     ├─ armar QuoteRunResult                     // falla → RecalculationFailedException
     ├─ log finished
     └─ finally running.set(false), restaurar MDC
```

## Transiciones de estado

No hay estados persistentes nuevos. La guardia en memoria pasa de libre a ocupada y vuelve a libre en `finally`.
