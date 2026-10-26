# Implementation Plan: Algoritmo de la Estrategia 2 "MOMENTUM"

**Branch**: `feature/estrategia-momentum` | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/019-estrategia-momentum/spec.md`

## Summary

Este plan agrega `MomentumFormAlgorithm`, la implementación de `ValuationAlgorithm` para el tipo MOMENTUM. El algoritmo toma las últimas 5 ventanas de estadísticas de 014. Mide el rendimiento por 90' de cada ventana contra el promedio de temporada del propio jugador, con los pesos de su posición y más peso a lo reciente. Le resta la falta de disponibilidad (proporcional al peso de minutos de la posición) y las tarjetas (con los pesos negativos de la definición). Con eso obtiene una forma `S ∈ [0, 1]` y aplica sobre la cotización MOMENTUM anterior una variación `2·(S − 0.5)·0.15`, acotada a ±15%.

MOMENTUM v1 se redefine en el lugar porque nunca produjo una cotización. Los parámetros nuevos son 5 ventanas, mínimo 2 snapshots, pesos de recencia 5..1, 90 minutos posibles, margen de rating 1.5 y ±15%. Se actualizan la huella y el contrato de 013. La corrida de 016 pasa a cargar las ventanas sólo para MOMENTUM, y OFFICIAL no cambia. Si no hubo snapshot nuevo desde la última cotización MOMENTUM, la variación es 0 (FR-028), y las ventanas anteriores a un reinicio de temporada se descartan.

La propuesta de fórmula del pedido se ajustó en dos puntos, justificados en [research.md](research.md) R4:

- La disponibilidad **resta** en lugar de multiplicar. Si multiplicara, un jugador sin minutos no bajaría.
- La normalización es **contra el propio jugador**, porque el algoritmo no arma población.

## Technical Context

**Language/Version**: Java 17

**Primary Dependencies**: Spring Boot 4.1.1 (sólo `@Component` para registrar el algoritmo), `java.math.BigDecimal` / `MathContext.DECIMAL64`

**Storage**: N/A. No agrega tablas ni columnas: usa `player_stats_snapshots` (014) en modo lectura y `player_quotes` (013) a través de 016, sin cambios.

**Testing**: JUnit 5, AssertJ, Mockito y tests parametrizados. El E2E de 016 usa `@E2ETest` contra PostgreSQL y Redis.

**Target Platform**: backend JVM (Linux/local)

**Project Type**: aplicación web (backend + frontend). El alcance es sólo el backend, `service/valuation`.

**Performance Goals**: el cálculo es O(ventanas × métricas) por jugador, despreciable. La corrida suma 1 consulta paginada de snapshots por jugador (~2.500) para MOMENTUM, lo que es aceptable para un batch semanal o manual.

**Constraints**: determinístico; cálculo puro; aritmética decimal; ±15% por corrida; sin parámetros fijos en el algoritmo; sin yml ni propiedades; sin tocar OFFICIAL v1 ni archivos protegidos de Entrega 1.

**Scale/Scope**: ~2.500 jugadores, 2 estrategias, hasta 5 ventanas por jugador.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Gate inicial y re-check posterior a la Phase 1: **PASS**.

| Regla | Estado | Evidencia |
|-------|--------|-----------|
| §1.2 Multiestrategia configurable | PASS | MOMENTUM pasa a ser la segunda estrategia con algoritmo. Usa goles, asistencias, tiros, pases clave, gambetas, quites, rating, minutos y tarjetas, y todos sus parámetros están declarados en la definición versionada. |
| §1.2 Ponderación por posición | PASS | Usa los pesos por posición de MOMENTUM v1 (013 §4): por ejemplo, quites 0.30 en defensores y goles 0.30 en delanteros. La disponibilidad pesa distinto por posición (arquero 0.30, defensor 0.20, el resto 0.10), y para varias posiciones se promedian los pesos. |
| §1.2 Trazabilidad de valuación | PASS | Cada cotización MOMENTUM se guarda con `strategyVersion = 1`. La redefinición de v1 no deja precios sin explicar porque no existe ninguna cotización MOMENTUM (research R2). La huella vuelve a proteger v1 desde la primera corrida. |
| §1.3 Desacoplamiento de fuentes externas | PASS | El algoritmo sólo usa datos persistidos (snapshots de 014 y estadísticas locales). No consulta WhoScored ni Football-Data, así que una caída externa no afecta la valuación. |
| §2.1 Capas | PASS | El algoritmo vive en la capa de servicios y la carga de ventanas pasa por `StatsSnapshotService`, que usa su repositorio. No hay controllers ni adapters nuevos. |
| §2.3 Batch | PASS | Se integra en la corrida existente (`POST /quotes/recalculate` y el scheduler) sin cambiar su orquestación. |
| §3.1 / §3.2 CI y Sonar | PASS | La feature incluye tests unitarios, de servicio y E2E actualizado. El código es pequeño y sin duplicación con OFFICIAL: no reusa `MetricNormalizer` porque normaliza distinto. |
| §3.3 Evolución temporal de cotizaciones | PASS | MOMENTUM agrega precios que evolucionan corrida a corrida según la forma. |
| §5.3 Observabilidad | PASS | Usa los logs de 016 por jugador y por corrida. El algoritmo puro no loguea. |
| §7 DoD | PASS | La feature no agrega endpoints. Los existentes no cambian de contrato: sólo deja de aparecer MOMENTUM en `skippedStrategies`. |

Excepción documentada (no viola la constitución): la regla de inmutabilidad de versiones es de 013 §5, no de la constitución. Se aplica una excepción única y justificada para MOMENTUM v1 (research R2).

## Project Structure

### Documentation (this feature)

```text
specs/019-estrategia-momentum/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── momentum-algorithm.md
├── checklists/
│   └── requirements.md
└── tasks.md             # $speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/com/example/demo/service/valuation/
├── momentum/
│   └── MomentumFormAlgorithm.java          # NUEVO: @Component, type() = MOMENTUM
├── strategy/
│   ├── MomentumParameters.java             # + recencyWeights, possibleMinutes, ratingBand; nueva validación
│   ├── MomentumStrategyV1.java             # maxVariation 0.15, (5, 2, [5..1], 90, 1.5), Javadoc con el motivo
│   └── ValuationStrategyDefinition.java    # fingerprint() incluye los campos nuevos de momentum
├── ValuationInput.java                     # sólo Javadoc
├── StatsWindow.java                        # + currentRunId (014; detecta datos nuevos, FR-028)
├── StatsSnapshotService.java               # toWindow copia el runId del snapshot actual
└── QuoteCalculationService.java            # carga recentWindows(id, window + 1) para MOMENTUM

backend/src/test/java/com/example/demo/service/valuation/
├── momentum/
│   ├── MomentumFixtures.java               # NUEVO: builders de ventanas, stats y cotizaciones
│   ├── MomentumFormAlgorithmTest.java      # NUEVO: escenarios de la spec + ejemplos del contrato
│   └── MomentumFormAlgorithmPropertyTest.java  # NUEVO: tope ±15% y determinismo
├── strategy/
│   ├── MomentumStrategyV1Test.java         # nuevos parámetros
│   ├── ValuationStrategyDefinitionTest.java    # MomentumParameters nuevos y huella
│   └── PublishedStrategyVersionsTest.java  # nueva huella "MOMENTUM:1"
└── QuoteCalculationServiceTest.java        # MOMENTUM no se saltea, recibe ventanas

backend/src/test/java/com/example/demo/e2e/
└── QuoteRecalculationE2ETest.java          # ya no espera MOMENTUM salteada; cotizaciones de ambas estrategias

specs/013-cotizacion-modelo/contracts/valuation-strategies.md   # §2, §4 y nota en §5
```

**Structure Decision**: se sigue el patrón de OFFICIAL (`service/valuation/official/OfficialPerformanceAlgorithm`) con un paquete hermano `momentum/`. No se tocan archivos protegidos de Entrega 1. `PlayerStats` se lee a través de `ValuationInput`, como ya hace OFFICIAL.

## Riesgos y notas para la implementación

- **Ejemplos numéricos**: el test debe reproducir exactamente los del contrato. Fueron calculados con un modelo de referencia independiente.
- **Ejemplos de la API**: `QuoteRunResponse` y `specs/016-recalculo-cotizaciones/contracts/quotes-api.yaml` muestran MOMENTUM como salteada en el ejemplo. Es documentación de ejemplo y se puede actualizar o dejar, porque el schema no cambia.
- **Contrato de 013**: la nota en §5 debe decir explícitamente que la excepción fue única y que, desde la primera cotización MOMENTUM, rige la regla.

## Complexity Tracking

Sin violaciones de la constitución que justificar.
