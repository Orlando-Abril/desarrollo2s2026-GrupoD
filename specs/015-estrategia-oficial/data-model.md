# Data Model: Algoritmo de la Estrategia 1 "OFFICIAL"

No hay entidades JPA ni tablas nuevas: todo son records y clases inmutables en memoria. Las fórmulas y los ejemplos numéricos están en [contracts/valuation-strategies.md](contracts/valuation-strategies.md) y las decisiones en [research.md](research.md).

## Paquete `service/valuation` (compartido con MOMENTUM)

### `ValuationAlgorithm` (interfaz)

| Método | Descripción |
|--------|-------------|
| `StrategyType type()` | Tipo de definición que interpreta. 016 la elige según `definition.type()`. |
| `QuoteResult calculate(ValuationInput input, ValuationStrategyDefinition strategy)` | Calcula un jugador. Lanza `IllegalArgumentException` si `strategy.type() != type()`. |

### `ValuationInput` (record)

| Campo | Tipo | Regla |
|-------|------|-------|
| `playerId` | `Long` | Obligatorio. |
| `positions` | `Set<Position>` | Obligatorio y puede estar vacío. Se copia de forma inmutable. |
| `stats` | `PlayerStats` | Puede ser null (jugador sin estadísticas). Sólo se lee. |
| `population` | `NormalizedPopulation` | Obligatoria para OFFICIAL. MOMENTUM puede ignorarla. |
| `previousQuotes` | `List<PlayerQuote>` | Sólo cotizaciones de la estrategia que se calcula, de la más nueva a la más vieja por `calculatedAt`. El constructor rechaza una lista fuera de orden y acepta empates. El código de estrategia lo valida `calculate`, porque el record no conoce la estrategia. Si es null se trata como lista vacía. Se copia de forma inmutable. |
| `windows` | `List<StatsWindow>` | Ventanas de 014 para MOMENTUM. OFFICIAL las ignora. Si es null se trata como lista vacía. |

### `QuoteResult` (record)

| Campo | Tipo | Regla |
|-------|------|-------|
| `value` | `BigDecimal` | Escala 4. Obligatorio si es CALCULATED y null en los demás estados. |
| `score` | `BigDecimal` | Escala 4, en [0,1]. Obligatorio si es CALCULATED y null en los demás estados. |
| `status` | `QuoteStatus` | Obligatorio. OFFICIAL sólo produce `CALCULATED` o `NO_STATS`. |
| `excludedMetrics` | `Map<Position, Set<Metric>>` | Métricas con peso > 0 sin dato en toda la posición. Sólo incluye posiciones con exclusiones. Es inmutable y vacío en NO_STATS. |

Fábricas: `calculated(value, score, excludedMetrics)`, `noStats()` e `insufficientHistory()`.

### `PopulationEntry` (record)

`(Long playerId, Set<Position> positions, PlayerStats stats)`: un jugador que se ofrece para normalizar. `stats` puede ser null.

### `NormalizedPopulation` (clase inmutable)

| Elemento | Descripción |
|----------|-------------|
| `minMinutes()` | Umbral con el que se construyó. |
| `contains(playerId)` | Indica si el jugador es elegible: tiene stats, al menos una posición y minutos ≥ umbral. |
| `normalized(playerId, position)` | `Map<Metric, BigDecimal>` con las 7 métricas en [0,1]. Si el jugador no es elegible o no tiene esa posición, devuelve vacío. |
| `metricsWithoutData(position)` | Métricas sin dato para ningún jugador elegible de la posición. Si la posición no tiene jugadores, devuelve vacío. |

Estructura interna: `Map<Long, EnumMap<Position, EnumMap<Metric, BigDecimal>>>` y `EnumMap<Position, Set<Metric>>`, todas sin modificación posible.

El constructor es **package-private**: sólo `MetricNormalizer`, que está en el mismo paquete, puede crear poblaciones. Así ninguna población llega a `calculate` sin haber pasado por la normalización.

Una población vacía es válida: `contains` da siempre false y `metricsWithoutData` está vacío para las 4 posiciones.

### `MetricNormalizer`

Es una clase pura, no un bean, y no lee pesos ni parámetros de ninguna estrategia. La usa OFFICIAL y la va a reusar MOMENTUM (019). Expone `new MetricNormalizer(int minMinutes)` y `NormalizedPopulation normalize(Collection<PopulationEntry>)`. Una colección vacía no es un error y devuelve una población sin jugadores. El método sigue estos pasos:

1. Lanza un error si hay un `playerId` duplicado.
2. Filtra a los jugadores elegibles: tienen stats, al menos una posición y `minutesPlayed != null && minutesPlayed ≥ minMinutes`.
3. Por jugador elegible calcula los valores crudos:
   - las métricas de conteo como `(valor ?? 0) × 90 / minutos`;
   - el rating como `clamp((rating ?? 0) / 10, 0, 1)`.
4. Por posición, sobre los elegibles que la tienen:
   - las métricas de conteo se normalizan con `(x − min) / (max − min)`, o 0.5 si `max = min`;
   - el rating queda como en el paso 3;
   - se registran las métricas en las que todos los jugadores tienen null.

## Paquete `service/valuation/official`

### `OfficialPerformanceAlgorithm` (`@Component`, implementa `ValuationAlgorithm`)

| Elemento | Descripción |
|----------|-------------|
| Constructor | `@Value("${valuation.official.min-minutes:90}") int minMinutes`. Si no es mayor que 0, lanza `IllegalArgumentException`. |
| `type()` | `StrategyType.OFFICIAL`. |
| `normalize(Collection<PopulationEntry>)` | Delega en `new MetricNormalizer(minMinutes)`. |
| `calculate(input, strategy)` | Flujo descrito en [plan.md](plan.md#cálculo-por-jugador-calculate). El NO_STATS del jugador se evalúa con `population.minMinutes()`. Lanza `IllegalArgumentException` si el tipo no es OFFICIAL, si falta la población o tiene otro umbral, si alguna cotización previa es de otra estrategia o si la más reciente tiene valor null. Estas validaciones corren antes de evaluar NO_STATS, así que fallan aunque el jugador sea NO_STATS. |

## Invariantes

- El score está en [0,1] y el valor es ≥ 0, ambos con escala 4.
- En cada posición evaluada, la suma de los pesos efectivos es 1.00 (dentro de la precisión DECIMAL64).
- Las mismas entradas, en cualquier orden, producen el mismo resultado.
- Ninguna clase de esta feature accede a repositorios, adapters ni a la base.

## Relación con features vecinas

| Feature | Relación |
|---------|----------|
| 013 | Lee `OfficialStrategyV1.DEFINITION`, `Metric`, `StrategyType`, `QuoteStatus` y `PlayerQuote`, sin modificarlos. |
| 014 | `ValuationInput.windows` usa `StatsWindow`. OFFICIAL no lo consume. |
| 016 | Arma `PopulationEntry` y `ValuationInput`, llama a `normalize` y a `calculate` y guarda `QuoteResult` como `PlayerQuote`. En NO_STATS guarda la cotización con el precio conservado (nunca con valor null) y pasa en `previousQuotes` sólo las cotizaciones de la misma estrategia, de la más nueva a la más vieja. |
| 019 | Implementa `ValuationAlgorithm` para MOMENTUM con el mismo input y puede reusar `MetricNormalizer`, `PopulationEntry` y `NormalizedPopulation` de `service/valuation` sin importar nada de `official`. |
