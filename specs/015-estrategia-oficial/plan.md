# Implementation Plan: Algoritmo de la Estrategia 1 "OFFICIAL"

**Branch**: `feature/estrategia-oficial` | **Date**: 2026-10-20 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/015-estrategia-oficial/spec.md`

## Summary

Esta feature agrega el cálculo de OFFICIAL como código puro de dominio, sin persistencia ni endpoints:

- **Contrato común de algoritmos**: interfaz nueva `ValuationAlgorithm` (`type()` + `calculate(ValuationInput, ValuationStrategyDefinition)`) y los records `ValuationInput` y `QuoteResult`. Están pensados para que MOMENTUM (019) implemente la misma interfaz: el input ya trae las cotizaciones previas en orden descendente y las `StatsWindow` de 014.
- **Normalización**: `MetricNormalizer` arma una vez por corrida una `NormalizedPopulation` inmutable. Calcula las métricas por 90', aplica min-max por posición sobre los jugadores elegibles (null = 0, max = min → 0.5), normaliza el rating como rating/10 y registra las métricas sin dato en toda una posición.
- **Algoritmo**: `OfficialPerformanceAlgorithm` (`@Component`) lee los pesos de `OfficialStrategyV1` y reparte proporcionalmente los pesos excluidos. Promedia los scores de las posiciones del jugador, redondea el score a 4 decimales, calcula `baseValue + score × scaleFactor`, lo acota a ±`maxVariation` respecto de la cotización anterior y redondea el valor a 4 decimales HALF_UP. El umbral de minutos sale de `valuation.official.min-minutes` (por defecto 90).
- **Documentación para la defensa**: [contracts/valuation-strategies.md](contracts/valuation-strategies.md) explica la fórmula, el reparto y los casos borde, con un ejemplo numérico por posición. Referencia la tabla de pesos de 013 sin duplicarla.

## Technical Context

**Language/Version**: Java 17

**Primary Dependencies**:
- Spring Boot 4.1.1: sólo `@Component` y `@Value`.
- No se agregan dependencias. La aritmética es `BigDecimal` con `MathContext.DECIMAL64`, como en `StatsSnapshotService` (014).

**Storage**: N/A. La feature no lee ni escribe la base.

**Testing**:
- JUnit 5 y AssertJ, con tests unitarios puros y sin contexto de Spring para el normalizador y el algoritmo.
- `ApplicationContextRunner` sólo para la propiedad del umbral, igual que `ValuationStrategyActivePropertyTest`.

**Target Platform**: backend web service en Linux o Windows.

**Project Type**: aplicación web (`backend/` y `frontend/`). Esta feature sólo toca `backend/`.

**Performance Goals**:
- La normalización es O(jugadores × posiciones × 7 métricas) y corre una sola vez por corrida.
- Cada `calculate` es O(posiciones del jugador × 7).
- Con unos 2.500 jugadores, la corrida completa queda muy por debajo de un segundo.

**Constraints**:
- No se modifican `OfficialStrategyV1` ni `ValuationStrategyDefinition` (`PublishedStrategyVersionsTest` tiene que seguir pasando sin cambios).
- No se modifican archivos de Entrega 1: `PlayerStats` sólo se lee mediante sus getters y `application.properties` no se toca, porque el default del umbral vive en el `@Value`.
- No se persiste, no hay endpoints, no hay jobs y no se consultan fuentes externas.

**Scale/Scope**:
- Producción: 7 clases nuevas.
  - En `service/valuation`: `ValuationAlgorithm`, `ValuationInput`, `QuoteResult`, `PopulationEntry`, `NormalizedPopulation` y `MetricNormalizer`.
  - En `service/valuation/official`: `OfficialPerformanceAlgorithm`.
- Tests: 5 clases de test nuevas (`MetricNormalizerTest`, `OfficialPerformanceAlgorithmTest`, `ValuationInputTest`, `QuoteResultTest` y `OfficialMinMinutesPropertyTest`) y el helper `OfficialFixtures`.
- No se modifica ningún archivo existente.

No quedan `NEEDS CLARIFICATION`: las decisiones están en [research.md](research.md).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Regla | Cómo se cumple | Estado |
|-------|----------------|--------|
| **§1.2 Ponderación por posición** | El score de cada posición usa `strategy.weight(position, metric)` de OFFICIAL v1 y se normaliza contra los jugadores de esa misma posición. Los jugadores con varias posiciones promedian sus scores, cada uno con sus propios pesos y su propia normalización. | ✅ |
| **§1.2 Multiestrategia configurable** | `ValuationAlgorithm.type()` permite que 016 elija la implementación según `definition.type()`. MOMENTUM (019) implementa la misma interfaz con el mismo `ValuationInput`. | ✅ |
| **§1.2 Métricas** | Usa goles, asistencias, tiros, pases clave, gambetas, tackles y rating, que son las que declara OFFICIAL v1. Los minutos sólo se usan para el cálculo por 90 y el umbral. | ✅ |
| **§1.2 Trazabilidad** | El algoritmo es determinístico (sin depender del orden, aritmética decimal fija) y `QuoteResult` informa las métricas excluidas por posición. 016 guarda código y versión. | ✅ |
| **§1.1 Momento cero = 1 crédito** | El valor parte de `baseValue` = 1.00 de la definición. | ✅ |
| **§1.3 Resiliencia** | Si falla la consulta opcional de gambetas en una liga, esa métrica se excluye y su peso se reparte, en lugar de valer 0 para todos. | ✅ |
| **§2.1 Capas** | Todo vive en `service/valuation` y es lógica de negocio sin acceso a repositorios ni adapters. Quien llama (016) carga los datos desde los repositorios. No hay controllers. | ✅ |
| **§3.1 / §3.2 CI y Sonar** | No hay dependencias nuevas. Los tests son unitarios y no tienen tag e2e. Cada rama del algoritmo tiene un test (NO_STATS, exclusión, max = min, tope, multiposición) para la cobertura de Sonar. | ✅ |

**Re-check post-diseño (Phase 1)**: sin violaciones. El diseño no agrega capas, tablas ni dependencias y no toca archivos protegidos.

## Project Structure

### Documentation (this feature)

```text
specs/015-estrategia-oficial/
├── plan.md                          # Este archivo
├── research.md                      # Decisiones D1–D10
├── data-model.md                    # Records, invariantes y flujo de cálculo
├── quickstart.md                    # Cómo validar
├── contracts/
│   └── valuation-strategies.md      # Fórmula final, reparto, casos borde y ejemplos por posición
├── checklists/requirements.md
└── tasks.md                         # Lo genera /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/com/example/demo/service/valuation/
├── ValuationAlgorithm.java              # NUEVO: interfaz común OFFICIAL / MOMENTUM
├── ValuationInput.java                  # NUEVO: record de entrada
├── QuoteResult.java                     # NUEVO: record de salida
├── StatsWindow.java                     # existente (014), se referencia desde ValuationInput
├── PopulationEntry.java                 # NUEVO: jugador + posiciones + stats para normalizar
├── NormalizedPopulation.java            # NUEVO: resultado inmutable de la normalización (constructor package-private)
├── MetricNormalizer.java                # NUEVO: por 90', min-max por posición, rating/10, exclusiones
└── official/
    └── OfficialPerformanceAlgorithm.java # NUEVO: @Component, implementa ValuationAlgorithm

backend/src/test/java/com/example/demo/service/valuation/
├── QuoteResultTest.java                 # NUEVO: invariantes del record
├── ValuationInputTest.java              # NUEVO: orden descendente de previousQuotes
├── MetricNormalizerTest.java            # NUEVO
└── official/
    ├── OfficialFixtures.java            # NUEVO: helper de tests con las poblaciones del contrato
    ├── OfficialPerformanceAlgorithmTest.java # NUEVO: casos de la consigna, bordes y orden
    └── OfficialMinMinutesPropertyTest.java   # NUEVO: default 90, override, valor inválido
```

**Structure Decision**: en `service/valuation`, junto a `StatsWindow` de 014, va todo lo que no es propio de OFFICIAL:

- la interfaz y los records compartidos;
- la normalización: `PopulationEntry`, `NormalizedPopulation` y `MetricNormalizer`.

`MetricNormalizer` no lee pesos ni parámetros de una estrategia, así que MOMENTUM (019) lo va a reusar. Además, `ValuationInput` referencia a `NormalizedPopulation` sin depender de un subpaquete de OFFICIAL. Como `MetricNormalizer` y `NormalizedPopulation` comparten paquete, el constructor de `NormalizedPopulation` queda package-private y sólo el normalizador puede crear poblaciones.

En `service/valuation/official` queda únicamente `OfficialPerformanceAlgorithm`, igual que las definiciones van en `strategy`. `OfficialFixtures` es público para que `MetricNormalizerTest` lo use desde el paquete padre. No se tocan `model`, `repository` ni `adapter`.

## Diseño

### Flujo esperado de la corrida (lo implementa 016, se documenta acá)

```text
016 carga jugadores + PlayerStats + últimas cotizaciones OFFICIAL
  └─► population = officialAlgorithm.normalize(List<PopulationEntry>)        // una vez
  └─► por jugador: officialAlgorithm.calculate(
          new ValuationInput(playerId, positions, stats, population, previousQuotesDesc, windows),
          catalog.find("OFFICIAL"))
  └─► guarda QuoteResult como PlayerQuote (016)
```

### Umbral de minutos en un solo lugar

- `OfficialPerformanceAlgorithm` recibe `@Value("${valuation.official.min-minutes:90}") int minMinutes` en el constructor. Si el valor no es mayor que 0, lanza `IllegalArgumentException` y la aplicación no arranca.
- `MetricNormalizer` (en `service/valuation`) es una clase pura que se construye con `minMinutes` y no es un bean. El algoritmo expone `NormalizedPopulation normalize(Collection<PopulationEntry>)`, que delega en `new MetricNormalizer(minMinutes)`. Así 016 no necesita conocer el umbral.
- `NormalizedPopulation` guarda el `minMinutes` con el que se construyó, y `calculate` rechaza una población armada con otro umbral (ver D3 en research).
- Dentro de `calculate`, el chequeo de NO_STATS del propio jugador compara sus minutos contra `population.minMinutes()`, no contra el campo del `@Value`. El `@Value` sólo se usa para armar la población en `normalize` y para el chequeo de consistencia. Así el corte del jugador que se valúa y el de la población salen de una sola fuente.

### Cálculo por jugador (`calculate`)

1. **Validación del contrato de entrada.** Va siempre primero y no depende del jugador. Cualquier falla lanza `IllegalArgumentException` con un mensaje claro, aunque el jugador vaya a ser NO_STATS:
   - `strategy.type()` es `OFFICIAL`;
   - la población no es null y su `minMinutes()` coincide con el del algoritmo;
   - todas las `previousQuotes` tienen `strategyCode` igual a `strategy.code()`;
   - si `previousQuotes` no está vacía, `previousQuotes.get(0).getValue()` no es null. El mensaje nombra al jugador y aclara que 016 tiene que guardar los NO_STATS con el precio conservado;
   - el orden descendente lo valida el constructor de `ValuationInput`, así que también se cumple antes de llegar a `calculate`.
2. Si el jugador no tiene stats, no tiene posiciones, tiene minutos null o menos que `population.minMinutes()`, o no está en la población, el resultado es `QuoteResult.noStats()`.
3. Para cada posición del jugador, en orden de `Position`:
   - `excluidas` = métricas con peso > 0 que no tienen dato en toda la posición.
   - Si no queda ninguna métrica con peso > 0, la posición no produce score.
   - Si queda alguna, el score de la posición es `Σ (peso / Σ pesos restantes) × normalizado`. Cuando no hay exclusiones, la suma de los pesos restantes es 1.00 y no se divide.
4. `score` = promedio de los scores de las posiciones que produjeron uno (DECIMAL64), redondeado a 4 decimales HALF_UP y acotado a [0,1]. Si ninguna posición produjo score, el resultado es `noStats()`.
5. `raw = baseValue + score × scaleFactor`. Con un score de 4 decimales y `scaleFactor` 0.10, `raw` tiene 5 decimales. Si hay cotización anterior, `prev = previousQuotes.get(0).getValue()` (que el paso 1 ya garantizó no null) y `raw` se acota a `[prev × (1 − maxVariation), prev × (1 + maxVariation)]`.
6. `value = raw.setScale(4, HALF_UP)`. El resultado es `QuoteResult.calculated(value, score, excluidasPorPosición)`.

Cuando no actúa el tope, `value = round4(baseValue + score × scaleFactor)` se puede verificar con lo guardado en `PlayerQuote`. Cuando actúa el tope, esa verificación no vale: el valor es `round4(prev × (1 ± maxVariation))`.

### Estrategia de pruebas

- `MetricNormalizerTest`:
  - conversión por 90';
  - min-max con 3 jugadores;
  - max = min (incluido el caso de un solo jugador) → 0.5;
  - null = 0 cuando otros jugadores tienen el dato;
  - métrica null en toda la posición → sin dato;
  - rating/10 acotado a [0,1];
  - exclusión de NO_STATS (sin stats, sin posiciones, minutos null, 0 y 89 minutos con umbral 90; 90 minutos es elegible);
  - un jugador con varias posiciones entra en cada población;
  - id duplicado → error;
  - población vacía: `normalize(List.of())` no falla y devuelve una población sin jugadores, con `contains` siempre false y `metricsWithoutData` vacío para las 4 posiciones;
  - la población es inmutable.
- `OfficialPerformanceAlgorithmTest`, con los números calculados a mano del [contrato](contracts/valuation-strategies.md):
  - MIDFIELDER, fórmula de la consigna (A 1.0960, B 1.0120, C 1.0540);
  - GOALKEEPER (1.0804 y 1.0462, con métricas de peso 0 que no se informan);
  - DEFENDER con un solo jugador, max = min (1.0560);
  - FORWARD (1.0856 y 1.0236);
  - dribbles null en toda la posición: reparto, A 1.0956, y DRIBBLES informada sólo en MIDFIELDER;
  - jugador MIDFIELDER + FORWARD (1.0474);
  - null individual;
  - población vacía: `calculate` de un jugador con stats válidas (A) contra la población que devuelve `normalize(List.of())` da `NO_STATS`, sin valor ni score y sin lanzar error;
  - 0 minutos, menos del umbral y sin posiciones → NO_STATS sin valor ni score;
  - tope superior (0.80 → 0.9600), tope inferior (1.50 → 1.2000) y sin tope;
  - redondeo HALF_UP del valor de 5 decimales: un solo defensor con rating 7.15 da score 0.5645 y valor 1.05645 → **1.0565** (HALF_EVEN daría 1.0564);
  - mismo resultado con 10 permutaciones del orden de la población;
  - definición MOMENTUM rechazada;
  - población armada con otro umbral rechazada;
  - un jugador de 89 minutos con stats en el input da NO_STATS contra una población de umbral 90. El corte sale de `population.minMinutes()`, y como `calculate` exige que coincida con el del algoritmo, no hay un segundo umbral que pueda divergir;
  - cotización anterior con valor null rechazada, también para un jugador que sería NO_STATS (por ejemplo, sin stats): la validación de entrada corre antes de NO_STATS;
  - cotización anterior de otra estrategia (MOMENTUM) rechazada.
- `ValuationInputTest`: cotizaciones previas fuera de orden descendente rechazadas; empate en `calculatedAt` aceptado; null en `previousQuotes` o `windows` se trata como lista vacía.
- `QuoteResultTest`: CALCULATED exige valor y score; NO_STATS no admite valor ni score; las exclusiones son inmutables.
- `OfficialMinMinutesPropertyTest`: el default es 90; `valuation.official.min-minutes=270` cambia el umbral; 0 o un número negativo impiden arrancar.

## Complexity Tracking

No aplica: el Constitution Check no presenta violaciones.
