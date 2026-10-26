# Contrato: algoritmo MOMENTUM v1 (`MomentumFormAlgorithm`)

Este contrato explica cómo `MomentumFormAlgorithm` interpreta la definición MOMENTUM v1. Los parámetros y la tabla de pesos por posición están en [specs/013-cotizacion-modelo/contracts/valuation-strategies.md](../../013-cotizacion-modelo/contracts/valuation-strategies.md) (§2 y §4) y en `MomentumStrategyV1`. Acá no se repiten salvo en los ejemplos: el algoritmo los lee de la definición. Las decisiones están justificadas en [research.md](../research.md).

## 1. Interfaz

```java
@Component
public class MomentumFormAlgorithm implements ValuationAlgorithm {
    StrategyType type();                                   // MOMENTUM
    QuoteResult calculate(ValuationInput input, ValuationStrategyDefinition strategy);
    // preparePopulation: no se sobrescribe (default null)
}
```

Paquete: `com.example.demo.service.valuation.momentum`. Sin estado, sin dependencias inyectadas, sin I/O.

Campos de `ValuationInput` que usa:

| Campo | Uso |
|-------|-----|
| `playerId` | mensajes de error |
| `positions` | pesos (promedio si hay varias); vacío → `NO_STATS` |
| `stats` | promedio de temporada (puede ser `null`) |
| `previousQuotes` | sólo MOMENTUM, la más nueva primero; `get(0)` es la anterior |
| `windows` | ventanas de 014, cualquier orden; cada una trae `currentRunId`, la corrida del snapshot que la cierra |
| `population` | no se usa (llega `null`) |

## 2. Validación (lanza `IllegalArgumentException`)

1. `strategy.type() != MOMENTUM` → error que nombra el código y el tipo.
2. Alguna cotización de `previousQuotes` con `strategyCode` distinto del de la definición → error que nombra al jugador y el código.
3. `previousQuotes.get(0).getValue() == null` → error que nombra al jugador.

El orden de `previousQuotes` ya lo valida `ValuationInput`.

## 3. Estados

En este orden:

1. `positions` vacío → `QuoteResult.noStats()`.
2. `windows.size() < minHistory − 1` (con v1: 0 ventanas, es decir menos de 2 snapshots) → `QuoteResult.insufficientHistory()`.
3. En otro caso → `QuoteResult.calculated(valor, score, Map.of())`.

## 4. Cálculo

Notación: `MC = MathContext.DECIMAL64` para todos los intermedios; `round4` = escala 4 `HALF_UP`; `clamp(x, a, b) = max(a, min(b, x))`.

### 4.1 Ventanas consideradas

Se ordenan por `toCapturedAt` descendente y, a igualdad, por `currentSnapshotId` descendente. Si alguna tiene `seasonReset = true`, se descartan las posteriores en ese orden (las más viejas que la ventana de reinicio más reciente); la de reinicio se conserva. De las que quedan se toman las primeras `k = min(window, size)`. `j = 0` es la más reciente. `ρⱼ = recencyWeights[j]`.

### 4.1.1 Datos nuevos

```text
anterior        = previousQuotes.get(0)
datosNuevos     = previousQuotes.isEmpty()
                  || (!ventanaMásReciente.currentRunId().equals(anterior.getRunId())
                      && ventanaMásReciente.toCapturedAt().isAfter(anterior.getCalculatedAt()))
sinDatosNuevos  = !datosNuevos
```

`ventanaMásReciente` es la primera del orden anterior (antes de recortar), es decir la que cierra en el snapshot más reciente del jugador; su `toCapturedAt` es el `capturedAt` de ese snapshot. Hacen falta las dos condiciones:

- **`runId` distinto**: 016 fija `calculatedAt` antes de `snapshotAll`, así que el snapshot de una corrida siempre es posterior a la cotización de esa misma corrida; sólo el `runId` la reconoce como ya usada.
- **`capturedAt` posterior**: desde la segunda corrida repetida sin sincronización, la última cotización es de otra corrida que el snapshot; sólo la comparación de tiempos detecta que el snapshot es anterior a ella.

| Corrida | Snapshot más reciente | Última cotización MOMENTUM | Resultado |
|---|---|---|---|
| R2, sin sync | S1 (R1) | Q1 (R1) | mismo `runId` → sin datos nuevos |
| R3, sin sync | S1 (R1, capturado antes de R2) | Q2 (R2) | `capturedAt` anterior → sin datos nuevos |
| R4, con sync | S4 (R4) | Q3 (R3) | `runId` distinto y posterior → datos nuevos |

### 4.2 Pesos del jugador

Para cada métrica `m`: `w(m) = Σₚ weight(p, m) / |positions|`. Métricas de rendimiento `P = {GOALS, ASSISTS, SHOTS, KEY_PASSES, DRIBBLES, TACKLES, RATING}`; sólo cuentan las de `w(m) > 0`. `w_MIN = w(MINUTES)`, `w_Y = |w(YELLOW_CARDS)|`, `w_R = |w(RED_CARDS)|`.

### 4.3 Por ventana

```text
minutosⱼ = minutesPlayed ?? 0
aⱼ       = min(minutosⱼ / possibleMinutes, 1)

si minutosⱼ > 0:
  para cada m ∈ P con dato:
    contador: ratio = (valorⱼ × minutosTemporada) / (minutosⱼ × valorTemporada)
              n(m)  = min(ratio / 2, 1)
    rating:   n(m)  = clamp(0.5 + (ratingⱼ − ratingTemporada) / (2 × ratingBand), 0, 1)
  rⱼ = Σ w(m)·n(m) / Σ w(m)          sobre las métricas con dato; 0.5 si no hay ninguna
```

"Con dato" para un contador: `valorⱼ != null`, `stats != null`, `minutosTemporada > 0` y `valorTemporada > 0`. Para el rating: `ratingⱼ != null` y `ratingTemporada > 0`.

### 4.4 Forma, variación y valor

```text
R = Σ ρⱼ·aⱼ·rⱼ / Σ ρⱼ·aⱼ                  sobre ventanas con minutosⱼ > 0; 0.5 si no hay
D = Σ ρⱼ·aⱼ / Σ ρⱼ                          sobre las k ventanas
C = Σ (ρⱼ / ρ₀)·(w_Y·amarillasⱼ + w_R·rojasⱼ)     tarjetas null → 0
S = clamp(R − w_MIN·(1 − D) − C, 0, 1)
v = sinDatosNuevos ? 0 : clamp(2·(S − 0.5)·scaleFactor, −maxVariation, +maxVariation)
anterior = previousQuotes.isEmpty() ? baseValue : previousQuotes.get(0).getValue()
valor = round4(anterior × (1 + v))
score = round4(S)
```

Propiedades: `S = 0.5` → `v = 0`; `S = 1` → `v = +0.15`; `S = 0` → `v = −0.15`; con igual `R` y `C`, `∂S/∂D = w_MIN > 0`.

## 5. Ejemplos (MOMENTUM v1: `scaleFactor` 0.15, `maxVariation` 0.15, `recencyWeights` 5,4,3,2,1, `possibleMinutes` 90, `ratingBand` 1.5)

Cada ejemplo es un test que compara a 4 decimales. Salvo 5.10, la cotización anterior (si hay) es de una corrida distinta de la de la ventana más reciente.

### 5.1 Mediocampista en alza con una amarilla reciente

Temporada: 900', 2 goles, 1 asistencia, 10 tiros, 15 pases clave, 10 gambetas, 20 quites, rating 7.00 (por 90': 0.2 / 0.1 / 1 / 1.5 / 1 / 2). Anterior MOMENTUM = 1.2000.

| Ventana | min | G | A | T | PC | Gam | Q | Rating | Amarillas |
|---------|----:|--:|--:|--:|---:|----:|--:|-------:|----------:|
| j=2 (vieja) | 90 | 0 | 0 | 1 | 1 | 1 | 2 | 6.50 | 0 |
| j=1 | 90 | 1 | 0 | 2 | 2 | 1 | 2 | 7.50 | 0 |
| j=0 (reciente) | 90 | 1 | 1 | 2 | 3 | 2 | 3 | 8.00 | 1 |

Pesos MIDFIELDER de rendimiento: 0.20, 0.15, 0.05, 0.15, 0.10, 0.10, 0.15 (suma 0.90).

- j=0: n = 1, 1, 1, 1, 1, 0.75, 0.8333… → r₀ = 0.85 / 0.90 = 0.944444…
- j=1: n = 1, 0, 1, 0.6667, 0.5, 0.5, 0.6667 → r₁ = 0.55 / 0.90 = 0.611111…
- j=2: n = 0, 0, 0.5, 0.3333, 0.5, 0.5, 0.3333 → r₂ = 0.225 / 0.90 = 0.25
- ρ = 5, 4, 3 y a = 1 en las tres ventanas.
- R = (5·0.944444 + 4·0.611111 + 3·0.25) / 12 = 0.659722…
- D = 1; C = (5/5)·0.05·1 = 0.05
- S = 0.609722… → **score 0.6097**
- v = 2·0.109722…·0.15 = 0.032916…
- **valor = round4(1.2000 × 1.032916…) = 1.2395**

### 5.2 Rinde exactamente su promedio

Mediocampista. Temporada: 450', 5 goles, 5 asistencias, 10 tiros, 10 pases clave, 5 gambetas, 10 quites, rating 7.00. 5 ventanas de 90' con 1 gol, 1 asistencia, 2 tiros, 2 pases clave, 1 gambeta, 2 quites, rating 7.00 (todos los cocientes valen 1). Sin anterior.

R = 0.5, D = 1, C = 0 → **score 0.5000**, v = 0, **valor 1.0000**.

### 5.3 Sin minutos (mediocampista)

5 ventanas con 0 minutos; sin anterior. R = 0.5, D = 0, C = 0, S = 0.5 − 0.10 = 0.40 → **score 0.4000**, v = −0.03, **valor 0.9700**.

### 5.4 Sin minutos (arquero) con anterior 1.1000

Temporada: 900', 5 pases clave, 5 quites, rating 6.80. 5 ventanas con 0 minutos. S = 0.5 − 0.30 = 0.20 → **score 0.2000**, v = −0.09, **valor round4(1.1000 × 0.91) = 1.0010**.

### 5.5 Delantero en el tope

Temporada: 900', 5 goles, 2 asistencias, 20 tiros, 5 pases clave, 10 gambetas, 1 quite, rating 7.00. 5 ventanas de 90' con 2 goles, 1 asistencia, 5 tiros, 2 pases clave, 3 gambetas, 0 quites, rating 10.00. Sin anterior.

Todas las métricas con peso > 0 valen 1 (los quites tienen peso 0 en FORWARD) → R = 1, D = 1, C = 0, S = 1 → **score 1.0000**, v = +0.15, **valor 1.1500**.

### 5.6 Delantero en el tope con 45' por ventana

Misma temporada que 5.5; 5 ventanas de 45' con 1 gol, 1 asistencia, 2 tiros, 1 pase clave, 1 gambeta, 0 quites, rating 10.00 (todas las métricas siguen en el tope). R = 1, D = 0.5, S = 1 − 0.10·0.5 = 0.95 → **score 0.9500**, v = 0.135, **valor 1.1350**.

### 5.7 Delantero en el tope con una roja reciente

Igual que 5.5 con 1 roja en j=0. C = 0.15, S = 0.85 → **score 0.8500**, v = 0.105, **valor 1.1050**.

### 5.8 Jugador con dos posiciones (MIDFIELDER + FORWARD)

Mismas ventanas y anterior que 5.1. Pesos promedio: G 0.25, A 0.15, T 0.075, PC 0.10, Gam 0.10, Q 0.05, Rating 0.175, MIN 0.10, Y −0.05, R −0.15.

R = 0.670138…, D = 1, C = 0.05, S = 0.620138… → **score 0.6201**, **valor 1.2433**.

### 5.9 Historial insuficiente

0 ventanas → `INSUFFICIENT_HISTORY`, sin valor ni score; 016 registra la anterior o 1.0000.

### 5.10 Sin datos nuevos

Mismas ventanas y anterior que 5.1 (1.2000), pero la ventana más reciente tiene `currentRunId` igual al `runId` de la cotización anterior (dos corridas seguidas sin sincronización). S = 0.609722… → **score 0.6097**, v = 0, **valor 1.2000**, estado `CALCULATED`.

### 5.11 Reinicio de temporada

Delantero con la temporada de 5.5 y 5 ventanas (de la más vieja a la más reciente): 3 ventanas de 90' con 0 de todo y rating 4.00 (temporada anterior), luego la ventana de reinicio y una más, ambas como en 5.5 (en el tope); la de reinicio con `seasonReset = true`. Sólo cuentan las 2 últimas → R = 1, D = 1 → **score 1.0000**, **valor 1.1500**. Sin el descarte, las 3 viejas bajarían la forma.

## 6. Garantías

- Pura y determinística: mismas entradas → mismo resultado, sin depender del orden de `windows`, de la hora ni de aleatoriedad.
- `|valor / anterior − 1| ≤ maxVariation` salvo el redondeo a 4 decimales.
- `score ∈ [0, 1]`.
- `excludedMetrics` siempre vacío: las exclusiones son por ventana y no se informan.
