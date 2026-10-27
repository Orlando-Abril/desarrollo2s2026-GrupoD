# Implementation Plan: E2E del escenario de evolución de cotizaciones

**Branch**: `021-e2e-escenario-cotizaciones` | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/021-e2e-escenario-cotizaciones/spec.md`

## Summary

Se agrega un único test e2e, `QuoteEvolutionE2ETest`, que recorre el escenario 2 de la consigna con la aplicación completa, PostgreSQL y Redis reales:

1. Usa los cinco jugadores canónicos, uno por liga.
2. Corre tres recálculos por HTTP como ADMIN, en tres días UTC fijos.
3. Antes de la segunda y la tercera corrida cambia las estadísticas desde el repositorio.
4. Verifica sobre el JSON de las respuestas:
   - el historial (3 cotizaciones por estrategia, con su versión);
   - la cotización vigente de cada fecha;
   - que la evolución vaya en la dirección esperada;
   - el ranking final.
5. El valor de mercado se verifica con el repositorio.

Las fechas se controlan con un `Clock` ajustable que el test registra como `@Primary` desde su propia `@TestConfiguration`, sin tocar `ClockConfig`.

Los datos de cada jugador se eligieron y verificaron con el modelo de referencia de MOMENTUM (research R4) para que la dirección de cada cambio sea segura. En particular, el jugador que deja de jugar tiene una primera ventana por debajo de su promedio. Con una primera ventana muy buena, MOMENTUM sube aunque después no juegue, y la aserción sería falsa.

No hay cambios de código de producción ni de contratos.

## Technical Context

**Language/Version**: Java 17

**Primary Dependencies**: Spring Boot 4.1.1 (`spring-boot-starter-test`, `RestClient`), JUnit 5, AssertJ. No se agregan dependencias.

**Storage**: PostgreSQL y Redis reales del perfil `e2e` (`application-e2e.properties`), con la limpieza de `E2EDatabaseCleaner` antes y después de cada test.

**Testing**: `@E2ETest`. Se omite en local sin infraestructura y falla en CI (`ExternalServicesAvailableCondition`).

**Target Platform**: backend Spring Boot; CI de GitHub Actions con servicios `postgres` y `redis:7`.

**Project Type**: web-service (backend). La feature es sólo de test.

**Performance Goals**: N/A.

**Constraints**:
- Determinístico (FR-020).
- Sin fuentes externas: el perfil e2e ya apunta a un puerto cerrado.
- Sin tocar archivos protegidos de Entrega 1 (`pom.xml`, `ci.yml`, los `application.properties`, `SecurityConfig`, filtros y controllers) ni código de producción.
- Aserciones sobre el JSON HTTP, salvo `marketValue`.

**Scale/Scope**:
- 5 jugadores × 2 estrategias × 3 corridas = 30 cotizaciones.
- 30 consultas `at` más 2 consultas negativas.
- 3 consultas de ranking más 1 previa a la tercera corrida.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo se cumple | Estado |
|-----------|----------------|--------|
| **§3.3 escenario 2**: evolución temporal de cotizaciones para jugadores de las 5 ligas | Un jugador por liga, tres fechas y las dos estrategias. Se verifican el historial, la vigente por fecha, la dirección de la evolución y el ranking final. Es el escenario que pedía la constitución y que ningún test recorría completo. | ✅ |
| **§1.2** Motor de valuación multiestrategia, ponderación por posición, trazabilidad por versión | Se ejercitan OFFICIAL y MOMENTUM con jugadores de posiciones distintas: delantero, defensor+mediocampista, arquero, mediocampista y defensor. Cada cotización del historial se verifica con `strategy` y `version` = 1 (FR-008). | ✅ |
| **§5.1** Endpoints mínimos | Se consumen por HTTP `POST /quotes/recalculate`, `GET /players/{id}/quotes` (con y sin `at`) y `GET /players/ranking` (con y sin `strategy`). No se cambia ningún contrato. | ✅ |
| §1.3 Resiliencia | El perfil e2e mantiene las fuentes externas en un puerto cerrado, y las estadísticas se cargan por repositorio (FR-002). | ✅ |
| §2.1 Capas | Sin cambios de producción. | ✅ |
| §3.1 CI en `SUCCESS` y §3.2 SonarCloud | El test usa la infraestructura que el CI ya levanta. No hay código de producción nuevo que cubrir. | ✅ |
| §5.2 Redis | Se verifica que el ranking cacheado se invalida después de la corrida (FR-017). | ✅ |
| Regla "no modificar archivos de Entrega 1" | Sólo se agregan archivos nuevos de test y de la spec. `ClockConfig` no se toca: el reemplazo vive en la configuración del test. | ✅ |

**Re-check post-diseño**: sin violaciones. El diseño de Phase 1 no requiere ningún archivo protegido ni cambios de producción. Complexity Tracking queda vacío.

## Project Structure

### Documentation (this feature)

```text
specs/021-e2e-escenario-cotizaciones/
├── plan.md                    # Este archivo
├── research.md                # Phase 0: reloj, admin, datos del escenario, aserciones
├── data-model.md              # Phase 1: datos del escenario por jugador y corrida
├── quickstart.md              # Phase 1: correr el e2e y reproducir el escenario en Swagger para la demo
├── contracts/
│   └── scenario-assertions.md # Phase 1: pedidos HTTP y aserciones del escenario
├── checklists/requirements.md
└── tasks.md                   # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
backend/src/test/java/com/example/demo/
└── e2e/
    └── QuoteEvolutionE2ETest.java       # NUEVO
        ├── @E2ETest
        ├── static @TestConfiguration EvolutionClockConfig
        │     └── @Bean @Primary MutableClock evolutionClock()   # nombre distinto de "clock" (R1)
        ├── @BeforeEach: E2EFixtures.createPlayersFromFiveLeagues(), ADMIN como en el quickstart de 016 (R2),
        │                estadísticas de la corrida 1 por PlayerStatsRepository
        └── @Test evolutionOfOnePlayerPerLeagueAcrossThreeDates()
              corrida 1 → stats 2 → corrida 2 → ranking previo → stats 3 → corrida 3
              → historial, at, evolución, ranking, marketValue
```

Archivos existentes que se reutilizan sin cambios:
- `support/E2ETest`, `support/E2EFixtures` y `support/E2EDatabaseCleaner`;
- `config/ClockConfig`, que sigue declarando `Clock.systemUTC()`;
- los controllers de 016, 017 y 018.

**Structure Decision**: un solo test con un único método de escenario. Las tres corridas son pasos encadenados del mismo recorrido: partirlo en varios `@Test` obligaría a repetir las corridas en cada uno, porque `E2EDatabaseCleaner` limpia antes y después de cada método. Para que el reporte siga siendo legible:
- se usan helpers con nombre (`recalculateOn`, `history`, `effectiveAt`, `ranking`);
- se usa `SoftAssertions` en los bloques de 30 aserciones;
- cada fallo indica el jugador, la estrategia y la fecha.

El reloj ajustable es una clase `MutableClock` anidada en el test, porque ningún otro test la necesita todavía.

## Complexity Tracking

Sin violaciones de la constitución que justificar.
