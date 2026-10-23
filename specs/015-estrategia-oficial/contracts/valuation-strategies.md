# Contrato: algoritmo OFFICIAL

**Feature**: `specs/015-estrategia-oficial` | **Fecha**: 2026-10-20

Este contrato explica cómo `OfficialPerformanceAlgorithm` interpreta la definición OFFICIAL v1. Los parámetros (`baseValue` 1.00, `scaleFactor` 0.10, `maxVariation` 0.20) y la tabla de pesos por posición están en [specs/013-cotizacion-modelo/contracts/valuation-strategies.md](../../013-cotizacion-modelo/contracts/valuation-strategies.md) (§2 y §3). Acá no se repiten: el algoritmo los lee de la definición.

## 1. Fórmula final

Para un jugador elegible *j* con posiciones *P(j)*:

```text
m90(j, x)        = (x(j) ?? 0) × 90 / minutos(j)                     x ∈ {goals, assists, shots, keyPasses, dribbles, tackles}
norm(j, p, x)    = (m90(j,x) − min_p(x)) / (max_p(x) − min_p(x))     min/max sobre los elegibles con posición p
                 = 0.5                                                si max_p(x) = min_p(x)
norm(j, p, rating) = clamp((rating(j) ?? 0) / 10, 0, 1)

E_p              = { x : peso(p,x) > 0  y  x es null para todos los elegibles de p }   ← métricas excluidas
w'(p, x)         = peso(p, x) / Σ_{y ∉ E_p} peso(p, y)                  para x ∉ E_p
score_p(j)       = Σ_{x ∉ E_p} w'(p, x) × norm(j, p, x)

score(j)         = round4( promedio_{p ∈ P(j) con score} score_p(j) )   ∈ [0, 1]
valor(j)         = round4( clamp( baseValue + score(j) × scaleFactor,
                                  anterior × (1 − maxVariation),
                                  anterior × (1 + maxVariation) ) )     ← clamp sólo si hay cotización anterior
```

`round4` es redondeo a 4 decimales HALF_UP. Los intermedios usan `MathContext.DECIMAL64` sin redondear. `anterior` es el valor de `previousQuotes.get(0)`, la cotización más reciente de la misma estrategia, sea cual sea su estado. Como `score` tiene 4 decimales y `scaleFactor` es 0.10, el valor sin redondear tiene 5 decimales, y el último `round4` decide el quinto con HALF_UP. Para MIDFIELDER, sin exclusiones, `score_p` es exactamente la fórmula de la consigna:
`0.25·goals + 0.15·assists + 0.10·shots + 0.10·keyPasses + 0.10·dribbles + 0.10·tackles + 0.20·rating`, con cada métrica normalizada.

## 2. Elegibilidad (NO_STATS)

Un jugador es `NO_STATS`, sin valor ni score, si se da cualquiera de estos casos:

| Caso | Ejemplo |
|------|---------|
| No tiene estadísticas | `stats == null` |
| No tiene posiciones | `positions` vacío |
| Minutos null o por debajo del umbral | `minutesPlayed` null, 0 u 89 con el umbral por defecto de 90. Con 90 es elegible. |
| Ninguna de sus posiciones produce score | Todas las métricas con peso > 0 están excluidas en cada una de sus posiciones. |

Los NO_STATS no participan del min/max. Quien llama (016) conserva su cotización anterior.

`calculate` evalúa el umbral del jugador con `population.minMinutes()`, el mismo corte con el que se armó la población. El `@Value` sólo se usa al armarla, y una población con otro umbral se rechaza.

El umbral es `valuation.official.min-minutes`: por defecto 90, tiene que ser mayor que 0 y no está declarado en ningún `.properties`. Se sobrescribe con `VALUATION_OFFICIAL_MIN_MINUTES=270` o con `-Dvaluation.official.min-minutes=270`.

## 3. Reparto de pesos excluidos

- Se excluye una métrica en una posición sólo si tiene peso > 0 y ningún jugador elegible de esa posición tiene el dato. El caso típico es `DRIBBLES` cuando falla su consulta opcional en WhoScored.
- Su peso se reparte proporcionalmente: cada peso restante se divide por la suma de los restantes, así que los pesos efectivos vuelven a sumar 1.00 y conservan sus proporciones.
- Si al menos un jugador de la posición tiene el dato, no se excluye nada y los que no lo tienen cuentan 0.
- Las métricas con peso 0 (por ejemplo `GOALS`, `SHOTS` y `DRIBBLES` en GOALKEEPER) nunca se informan como excluidas.
- `QuoteResult.excludedMetrics` informa las exclusiones por posición, por ejemplo `{MIDFIELDER=[DRIBBLES]}`.

## 4. Casos borde

| Caso | Resultado |
|------|-----------|
| Población vacía | No se produce ningún resultado y no hay error. |
| Todos los jugadores son NO_STATS | Todos los resultados son NO_STATS. |
| Un solo jugador en la posición, o todos con el mismo valor | Cada métrica de conteo vale 0.5. |
| Métrica null en un jugador, pero no en toda la posición | Cuenta 0 y participa del min/max. |
| Rating fuera de 0–10 | La normalización se acota a [0,1]. |
| `playerId` repetido en la población | `IllegalArgumentException` con el id. |
| Definición de tipo distinto de OFFICIAL | `IllegalArgumentException`. |
| Población armada con otro umbral | `IllegalArgumentException`. |
| `previousQuotes` fuera de orden descendente por `calculatedAt` | `IllegalArgumentException` al construir `ValuationInput`. Los empates se aceptan. |
| `previousQuotes` con una cotización de otra estrategia (por ejemplo MOMENTUM) | `IllegalArgumentException` en `calculate`. Si no se rechazara, el tope se calcularía contra otro precio. |
| Cotización más reciente con valor null | `IllegalArgumentException` que nombra al jugador. 016 guarda los NO_STATS con el precio conservado. |
| Cotización anterior válida de un jugador NO_STATS | No se usa para calcular. El resultado sigue sin valor. |
| Cotización anterior inválida de un jugador NO_STATS (otra estrategia, fuera de orden o la más reciente con valor null) | `IllegalArgumentException`, igual que para cualquier jugador. La validación de entrada corre antes de evaluar NO_STATS. |
| Tarjetas y minutos | OFFICIAL v1 no los pondera. Los minutos sólo sirven para el por 90 y el umbral. |
| Orden de la población | No cambia ningún resultado. |

## 5. Ejemplos por posición (defensa del modelo)

Todos los ejemplos usan el umbral por defecto y no tienen cotización anterior, salvo el §5.6. Los números se verificaron con aritmética decimal y los replica `OfficialPerformanceAlgorithmTest`.

### 5.1 MIDFIELDER: la fórmula de la consigna

| Jugador | Min. | Goles | Asist. | Tiros | P. clave | Gamb. | Tackles | Rating |
|---------|-----:|------:|-------:|------:|---------:|------:|--------:|-------:|
| A | 900 | 5 | 4 | 30 | 20 | 15 | 25 | 8.0 |
| B | 900 | 1 | 0 | 10 | 5 | 3 | 8 | 6.0 |
| C | 1800 | 6 | 4 | 40 | 25 | 18 | 33 | 7.0 |

- Valores por 90: A = (0.5, 0.4, 3.0, 2.0, 1.5, 2.5) es el máximo en todo y B = (0.1, 0, 1.0, 0.5, 0.3, 0.8) el mínimo en todo. C = (0.3, 0.2, 2.0, 1.25, 0.9, 1.65), que normalizado da 0.5 en cada métrica.
- A: `0.25+0.15+0.10+0.10+0.10+0.10 = 0.80` + `0.20×0.80` = **0.9600** → valor **1.0960**
- B: `0` + `0.20×0.60` = **0.1200** → valor **1.0120**
- C: `0.80×0.5` + `0.20×0.70` = **0.5400** → valor **1.0540**

**Sin gambetas** (DRIBBLES null para A, B y C):

- La suma de los pesos restantes es 0.90 y cada uno se divide por 0.90. Se informa `{MIDFIELDER=[DRIBBLES]}`.
- A: `0.70/0.90×1` + `0.20/0.90×0.80` = 0.95556 → **0.9556** → valor **1.0956**
- B: `0.20/0.90×0.60` = 0.13333 → **0.1333** → valor **1.0133**
- C: `0.70/0.90×0.5` + `0.20/0.90×0.70` = 0.54444 → **0.5444** → valor **1.0544**

### 5.2 GOALKEEPER: pesa el rating y las métricas con peso 0 no influyen

| Jugador | Min. | Goles | Asist. | Tiros | P. clave | Gamb. | Tackles | Rating |
|---------|-----:|------:|-------:|------:|---------:|------:|--------:|-------:|
| G1 | 1800 | 0 | 1 | 0 | 9 | — | 6 | 7.2 |
| G2 | 1800 | 0 | 0 | 0 | 3 | — | 2 | 6.6 |

- Goles y tiros valen 0.5 por max = min, y gambetas no tiene dato. Las tres pesan 0 en GOALKEEPER, así que no influyen y no se informan como excluidas.
- G1: `0.05×1 + 0.10×1 + 0.15×1` + `0.70×0.72` = **0.8040** → valor **1.0804**
- G2: `0` + `0.70×0.66` = **0.4620** → valor **1.0462**

### 5.3 DEFENDER: un solo jugador (max = min)

| Jugador | Min. | Goles | Asist. | Tiros | P. clave | Gamb. | Tackles | Rating |
|---------|-----:|------:|-------:|------:|---------:|------:|--------:|-------:|
| D1 | 1350 | 1 | 2 | 3 | 10 | 5 | 40 | 7.0 |

- Cada métrica de conteo vale 0.5, porque D1 es el único defensor. Los pesos de conteo de DEFENDER suman 0.70.
- D1: `0.70×0.5` + `0.30×0.70` = **0.5600** → valor **1.0560**

### 5.4 FORWARD: pesan los goles y los tiros

| Jugador | Min. | Goles | Asist. | Tiros | P. clave | Gamb. | Tackles | Rating |
|---------|-----:|------:|-------:|------:|---------:|------:|--------:|-------:|
| F1 | 1800 | 20 | 6 | 80 | 20 | 40 | 10 | 7.8 |
| F2 | 1800 | 4 | 2 | 30 | 30 | 20 | 20 | 6.8 |

- F1 es el máximo en goles, asistencias, tiros y gambetas: `0.30+0.15+0.15+0.10 = 0.70` + `0.20×0.78` = **0.8560** → valor **1.0856**
- F2 es el máximo en pases clave y tackles: `0.05+0.05 = 0.10` + `0.20×0.68` = **0.2360** → valor **1.0236**

### 5.5 Varias posiciones: MIDFIELDER + FORWARD

X tiene 1800 minutos, 6 goles, 4 asistencias, 40 tiros, 25 pases clave, 30 gambetas, 16 tackles y rating 7.5. Entra en la población de §5.1 y en la de §5.4. Sus valores por 90 caen dentro de los rangos de ambas, así que no cambian los resultados de los demás.

- Como MIDFIELDER: normalizado (0.5, 0.5, 0.5, 0.5, 1.0, 0.0, 0.75) → `0.125+0.075+0.05+0.05+0.10+0+0.15` = 0.5500
- Como FORWARD: normalizado (0.125, 0.5, 0.2, 0.5, 0.5, 0.6, 0.75) → `0.0375+0.075+0.03+0.025+0.05+0.03+0.15` = 0.3975
- Promedio: 0.47375 → **0.4738** → valor **1.0474**

### 5.6 Tope de variación (±20%) y redondeo

| Valor sin tope | Cotización anterior | Rango permitido | Valor final |
|---------------:|--------------------:|-----------------|------------:|
| 1.0960 (A) | 0.80 | [0.64, 0.96] | **0.9600** |
| 1.0120 (B) | 1.50 | [1.20, 1.80] | **1.2000** |
| 1.0540 (C) | 1.00 | [0.80, 1.20] | **1.0540** (sin cambios) |

El valor se acota sin redondear y se redondea al final.

**Redondeo HALF_UP del quinto decimal.** Un único DEFENDER con rating 7.15 da score `0.70×0.5 + 0.30×0.715` = **0.5645**. El valor sin redondear es 1.05645, y HALF_UP lo lleva a **1.0565** (HALF_EVEN daría 1.0564).

**Cómo verificar el valor guardado.** El valor siempre se calcula a partir del score ya redondeado. Por eso, cuando **no** actúa el tope, con lo que queda guardado en `PlayerQuote` se puede verificar `valor = round4(1.00 + score × 0.10)`. Cuando actúa el tope, esa verificación **no vale**: el valor es `round4(anterior × (1 ± 0.20))` (como 0.9600 y 1.2000 en la tabla) y para auditarlo hace falta la cotización anterior.

## 6. API

| Operación | Resultado | Error |
|-----------|-----------|-------|
| `type()` | `StrategyType.OFFICIAL` | — |
| `normalize(Collection<PopulationEntry>)` | `NormalizedPopulation` inmutable, con el umbral configurado | `IllegalArgumentException` si hay un `playerId` repetido |
| `calculate(ValuationInput, ValuationStrategyDefinition)` | `QuoteResult` con `CALCULATED` o `NO_STATS` | `IllegalArgumentException` si el tipo no es OFFICIAL, si falta la población, si la población usa otro umbral, si una cotización previa es de otra estrategia o si la más reciente tiene valor null |
| `new ValuationInput(...)` | Record inmutable | `IllegalArgumentException` si `previousQuotes` no está en orden descendente por `calculatedAt` |
