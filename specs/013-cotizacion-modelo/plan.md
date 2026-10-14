# Implementation Plan: Modelo de datos de la cotización y catálogo de estrategias de valuación

**Branch**: `feature/cotizacion-modelo` | **Date**: 2026-10-14 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/013-cotizacion-modelo/spec.md`

## Summary

Esta feature crea la base del motor de cotizaciones y no calcula nada todavía. Incluye:

- **El registro de cotizaciones**: una entidad nueva e inmutable, `PlayerQuote`, con su enum `QuoteStatus`, y un repositorio nuevo, `PlayerQuoteRepository`, sin operaciones de modificación ni borrado. El repositorio tiene las 4 consultas del historial: última por estrategia, rango de fechas, vigente en una fecha y última de todos los jugadores. Cada consulta tiene un índice que la respalda.
- **Las estrategias**: un paquete nuevo, `service/valuation/strategy`, donde OFFICIAL v1 y MOMENTUM v1 son constantes Java de un record inmutable con pesos explícitos por posición y por métrica. El catálogo `ValuationStrategyCatalog` las expone, y la estrategia activa se elige con `valuation.active-strategy`, que por defecto es `OFFICIAL` y no se declara en ningún `.properties`.
- **La protección de versiones**: un test con huellas SHA-256 impide cambiar una versión publicada.
- **Bloque B (aprobado en la Pregunta 1 de la spec)**: el dato de gambetas en `PlayerStats`, recolectado desde WhoScored como consulta opcional.

## Technical Context

**Language/Version**: Java 17

**Primary Dependencies**:
- Spring Boot 4.1.1: Data JPA, Validation y Hibernate 7.
- Lombok.
- No se agregan dependencias nuevas. Para las huellas alcanza con `java.security.MessageDigest`.

**Storage**:
- PostgreSQL en dev y e2e, H2 en modo PostgreSQL en los tests unitarios.
- El esquema lo maneja Hibernate (`ddl-auto`): `update` en dev, `create-drop` en tests. No hay migraciones.

**Testing**:
- JUnit 5, AssertJ y `@DataJpaTest`.
- `ApplicationContextRunner` para probar la propiedad de la estrategia activa.
- `@UnitTestProfile` para los tests con contexto, y `@E2ETest` + `E2EFixtures` para el e2e.

**Target Platform**: backend web service en Linux o Windows.

**Project Type**: aplicación web (`backend/` y `frontend/`). Esta feature sólo toca `backend/`.

**Performance Goals**:
- Ninguna consulta recorre la tabla completa: cada una tiene un índice compuesto.
- Volumen esperado: unos 2.500 jugadores × 2 estrategias × 1 corrida por semana, es decir, alrededor de 260.000 filas por año.

**Constraints**:
- **Entrega 1**: no se tocan los modelos, repositorios ni `application.properties` de Entrega 1. La única excepción aprobada es la columna `PlayerStats.dribbles`.
- **Alcance**: no se calculan cotizaciones, no hay endpoints ni jobs, y no se cambia `Player`.

**Scale/Scope**:
- Clases de producción: 2 de modelo (`PlayerQuote`, `QuoteStatus`), 1 repositorio, 7 de estrategia (`StrategyType`, `Metric`, `MomentumParameters`, `ValuationStrategyDefinition` con su `fingerprint()`, `OfficialStrategyV1`, `MomentumStrategyV1` y `ValuationStrategyCatalog`) y 1 excepción.
- Bloque B: 6 archivos existentes modificados.

No quedan `NEEDS CLARIFICATION`: todas las decisiones están en [research.md](research.md).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Regla | Cómo se cumple | Estado |
|-------|----------------|--------|
| **§1.2 Multiestrategia configurable** | Hay dos estrategias, OFFICIAL v1 y MOMENTUM v1. La activa se elige con `valuation.active-strategy` y ambas quedan siempre disponibles en `all()`. | ✅ |
| **§1.2 Métricas** (goles, asistencias, tiros, pases clave, gambetas, tackles, rating, minutos, tarjetas) | El enum `Metric` las cubre todas. Las gambetas tienen además el dato real gracias al bloque B. | ✅ |
| **§1.2 Ponderación por posición** | `Map<Position, Map<Metric, BigDecimal>>` con las 4 posiciones obligatorias. FORWARD tiene más peso en goles y tiros, y DEFENDER en tackles ([contrato](contracts/valuation-strategies.md)). | ✅ |
| **§1.2 Trazabilidad de valuación** | Cada `PlayerQuote` guarda `strategyCode` y `strategyVersion`, ambos obligatorios. El test de huellas garantiza que una versión publicada siempre significa lo mismo. | ✅ |
| **§1.1 Momento cero = 1 crédito** | `baseValue = 1.00` en las dos estrategias. | ✅ |
| **§1.3 Resiliencia de fuentes** | Si falla la consulta de gambetas, no se pierde la sincronización de la liga: el dato queda en `null` (bloque B, FR-035). | ✅ |
| **§2.1 Capas** | La entidad va en `model`, el repositorio en `repository`, las definiciones y el catálogo en `service/valuation/strategy` (lógica de dominio, sin acceso a datos) y la consulta de gambetas en `adapter/whoscored`. No hay controllers en esta feature. | ✅ |
| **§4.2 Registro inmutable** (por analogía) | `@Immutable`, columnas `updatable = false` y un repositorio sin `delete*`. | ✅ |
| **§5.2 Índices del historial de cotizaciones** | `idx_player_quotes_player_strategy_time` e `idx_player_quotes_strategy_time`. | ✅ |
| **§5.3 Correlation IDs** | Cada cotización persiste un `correlationId` obligatorio. | ✅ |
| **§3.1 / §3.2 CI y Sonar** | No agrega dependencias. Los tests nuevos van separados del e2e por tag (`-DexcludedGroups=e2e`), como en las features anteriores. | ✅ |

**Re-check post-diseño (Phase 1)**: sin violaciones. La modificación de `PlayerStats` no viola la constitución, sólo la regla interna de Entrega 1, y está aprobada. Se documenta en Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/013-cotizacion-modelo/
├── plan.md                          # Este archivo
├── research.md                      # Trazabilidad consigna → requerimiento, decisiones D1–D9
├── data-model.md                    # PlayerQuote, índices, repositorio, PlayerStats.dribbles
├── quickstart.md                    # Cómo validar cada parte
├── contracts/
│   └── valuation-strategies.md      # OFFICIAL v1, MOMENTUM v1, tablas de pesos y regla de versionado
├── checklists/requirements.md
└── tasks.md                         # Lo genera /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/com/example/demo/
├── model/
│   ├── PlayerQuote.java                         # NUEVO
│   ├── QuoteStatus.java                         # NUEVO
│   └── PlayerStats.java                         # MODIFICADO (bloque B): + Integer dribbles
├── repository/
│   └── PlayerQuoteRepository.java               # NUEVO: extends Repository, 4 consultas
├── service/valuation/strategy/                  # PAQUETE NUEVO
│   ├── StrategyType.java
│   ├── Metric.java
│   ├── MomentumParameters.java
│   ├── ValuationStrategyDefinition.java         # record + invariantes + fingerprint()
│   ├── OfficialStrategyV1.java
│   ├── MomentumStrategyV1.java
│   └── ValuationStrategyCatalog.java            # @Component: all(), find(), active()
├── exception/
│   └── UnknownValuationStrategyException.java   # NUEVO
├── adapter/whoscored/                           # MODIFICADO (bloque B)
│   ├── WhoScoredAdapter.java                    # + consulta opcional "dribbles"
│   ├── WhoScoredStatsMapper.java                # merge recibe la respuesta de gambetas
│   └── dto/WhoScoredFeedResponse.java, dto/WhoScoredPlayerStats.java   # + dribbleWonTotal / dribbles
└── service/PlayerStatsService.java              # MODIFICADO (bloque B): setDribbles

backend/src/test/java/com/example/demo/
├── model/PlayerQuoteTest.java                                   # validación de score y estado
├── repository/PlayerQuoteRepositoryTest.java                    # @DataJpaTest + @UnitTestProfile: 4 consultas, inmutabilidad, sin delete
├── service/valuation/strategy/
│   ├── ValuationStrategyDefinitionTest.java                     # invariantes del record
│   ├── OfficialStrategyV1Test.java                              # MIDFIELDER = fórmula, sumas 1.00, 4 posiciones
│   ├── MomentumStrategyV1Test.java                              # tarjetas negativas, minutos positivos, sumas
│   ├── ValuationStrategyCatalogTest.java                        # all, find, código inexistente, duplicados
│   ├── ValuationStrategyActivePropertyTest.java                 # ApplicationContextRunner: default, MOMENTUM, inválida
│   └── PublishedStrategyVersionsTest.java                       # huellas de las versiones publicadas
├── e2e/PlayerQuoteQueriesE2ETest.java                           # @E2ETest + E2EFixtures: 4 consultas en PostgreSQL
└── adapter/whoscored/…, service/PlayerStatsServiceTest.java     # AMPLIADOS (bloque B)

backend/src/test/resources/whoscored/league-dribbles.json        # NUEVO fixture (bloque B)
```

**Structure Decision**:
- Se respeta la estructura en capas existente de `backend/`.
- Las estrategias van bajo `service/` porque son reglas de negocio que leen los algoritmos de 015 y 019. No van en `model/` porque no se persisten.
- `QuoteStatus` va en `model/` junto a su entidad, igual que `League` y `Position`.
- No se crea ningún archivo de configuración.

## Fases de implementación (orientativas para /speckit-tasks)

1. **A1. Estrategias** (no depende de nada): enums, `MomentumParameters`, el record con sus invariantes y la huella, las dos definiciones, el catálogo, la excepción y sus tests unitarios.
2. **A2. Protección de versiones**: calcular las huellas reales de v1 y fijarlas en `PublishedStrategyVersionsTest`.
3. **A3. Cotización**: `QuoteStatus`, `PlayerQuote`, `PlayerQuoteRepository` y `PlayerQuoteRepositoryTest`.
4. **A4. E2E**: `PlayerQuoteQueriesE2ETest` contra PostgreSQL.
5. **B. Gambetas**, independiente de A: fixture `league-dribbles.json`, DTO, mapper, adapter (consulta opcional), `PlayerStats.dribbles`, `PlayerStatsService` y sus tests.

A1–A2 y A3–A4 se pueden hacer en paralelo. B es independiente y se podría mover a otra feature sin afectar a A.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Se modifica `model/PlayerStats.java` (Entrega 1): + `Integer dribbles` nullable | La fórmula de la consigna pondera las gambetas con 0.10 y hoy no hay de dónde obtener el dato. El equipo aprobó la opción B (spec, Pregunta 1). | La opción A (peso declarado con el dato siempre ausente) dejaba el 10% de OFFICIAL sin datos reales. La C (redistribuir el peso) se apartaba de la fórmula de la consigna. |
| La consulta de gambetas a WhoScored es opcional, a diferencia de las otras cuatro | FR-035: si falla sólo esa consulta, no se puede perder la sincronización de toda la liga. | Hacerla obligatoria como las demás haría que una métrica nueva pudiera romper un flujo que hoy funciona. |