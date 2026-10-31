# Implementation Plan: Comparador de estrategias de valuación

**Branch**: `feature/comparador-estrategias` | **Date**: 2026-10-30 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/031-comparador-estrategias/spec.md`

## Summary

Se agregan lecturas comparativas sobre las cotizaciones ya guardadas y se permite cambiar la estrategia activa en tiempo de ejecución:

- `GET /users/{id}/portfolio?strategy=` valúa cada posición y los totales con la última cotización de otra estrategia. Para eso se extienden `PortfolioService` y sus DTOs con un objeto `strategyValuation` que se omite si no se pide `strategy`.
- El nuevo `service/valuation/OpportunityService` une `findLatestForAllPlayers` de la alternativa y de la estrategia de mercado con `Player.marketValue`. Lo expone `GET /quotes/opportunities` (subvaluados y sobrevaluados).
- `GET /quotes/latest?strategy&playerIds` devuelve la última cotización de hasta 100 jugadores para el Álbum.
- El nuevo `StrategyController` expone `GET /strategies/active` para cualquier usuario autenticado y `PUT /strategies/active` sólo para ADMIN, con `@PreAuthorize` y `@EnableMethodSecurity` en una configuración nueva. `SecurityConfig` no se toca.
- La activa se persiste en una entidad nueva de una fila. `ValuationStrategyCatalog.active()` la lee primero y, si no hay, usa `valuation.active-strategy`. El cambio se audita y vacía el caché del ranking. El recálculo fija la estrategia al empezar la corrida.

Las decisiones están en [research.md](research.md).

## Technical Context

**Language/Version**: Java 17, Spring Boot 4.1.1 (Hibernate 7, Spring Security 6/7)

**Primary Dependencies**: Spring Web MVC, Spring Data JPA, Spring Security (seguridad por método), springdoc-openapi, Spring Data Redis (caché)

**Storage**: PostgreSQL (`ddl-auto=update`); tabla nueva `active_strategy_settings`. H2 en tests y PostgreSQL `create-drop` en e2e.

**Testing**: JUnit 5 + AssertJ (unitarios), `@SpringBootTest` + MockMvc con datos cargados a mano, Testcontainers/PostgreSQL en e2e

**Target Platform**: Servidor Linux (backend REST)

**Project Type**: Aplicación web (backend + frontend). Esta feature sólo toca el backend.

**Performance Goals**: Menos de 2 s por consulta con 2.500 jugadores e historial completo (SC-007); cantidad de sentencias constante respecto de posiciones, jugadores o ids (FR-029)

**Constraints**:
- No modificar archivos de Entrega 1: `SecurityConfig`, `GlobalExceptionHandler`, `PlayerController`, `CacheConfig`.
- No calcular cotizaciones en memoria ni llamar a fuentes externas.
- El portfolio sin `strategy` debe ser idéntico al actual.

**Scale/Scope**: ~2.500 jugadores, 2 estrategias, 4 operaciones REST nuevas y 1 parámetro nuevo

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Estado | Cómo se cumple |
|-----------|--------|----------------|
| §1.2 Multiestrategia configurable y trazabilidad | PASS | La activa pasa a ser seleccionable en ejecución, persistida y auditada. Cada cotización sigue registrando código y versión. El recálculo fija la estrategia al inicio de la corrida (R3). |
| §1.1 Consistencia del portfolio | PASS | Los campos actuales no cambian. La valuación alternativa es aditiva y cumple las mismas invariantes de totales (R5). |
| §1.3 Resiliencia ante fuentes externas | PASS | Sólo se leen datos persistidos. Ninguna operación llama a adapters. |
| §2.1 Capas | PASS | Controllers (`QuoteController`, `StrategyController`, `UserMarketController`) → Services (`OpportunityService`, `ActiveStrategyService`, `PortfolioService`) → Repositories. La aritmética está aislada en `OpportunityCalculator` y `PortfolioStrategyValuator`. |
| §2.2 Frontend | N/A | El frontend queda para otra feature. Los contratos exponen lo que necesitan el selector, el Álbum y el Portfolio. |
| §2.3 Batch | PASS | `POST /quotes/recalculate` y el scheduler no cambian de contrato. Usan la activa vigente al comenzar. |
| §3.1–3.3 CI, Sonar, tests | PASS | Tests unitarios y MockMvc por historia. La lógica se extrae para no subir la complejidad de `PortfolioService`. |
| §4.1 Autorización | PASS | `@PreAuthorize("hasRole('ADMIN')")` en el `PUT`. 401 por la cadena de filtros existente. El 403 lo responde `GlobalExceptionHandler` con el mismo cuerpo que `JsonAccessDeniedHandler` (R1). |
| §4.2 Auditoría inmutable | PASS con riesgo | Se agrega `ACTIVE_STRATEGY_CHANGED` con actor, instante, correlation id, `before` y `after`, en la misma transacción. Cada recálculo que cambia la estrategia de mercado audita `MARKET_STRATEGY_APPLIED` con actor de sistema y `runId`, una vez por corrida (R4). El `marketValue` por jugador sigue sin auditarse, como en la 016. El riesgo del CHECK de enum en bases existentes está documentado (R10) y queda una tarea de verificación. |
| §4.3 Validación de entrada | PASS | `strategy`, `limit`, `league`, `position`, `playerIds` y el cuerpo del `PUT` se validan en el controller o el service con 400. |
| §5.1 Endpoints mínimos | PASS | `GET /users/:id/portfolio` y `GET /players/ranking` conservan sus contratos. |
| §5.2 Caché e índices | PASS | Se vacía `quotes-ranking` al cambiar la activa (R8). No se agregan cachés nuevos porque requieren tocar `CacheConfig` (Entrega 1); SC-007 se cumple sin ellos. Las lecturas usan `(player_id, strategy_code, calculated_at)` de `player_quotes` (013). |
| §5.3 Observabilidad | PASS | Logs estructurados en el cambio de estrategia (`active_strategy_changed from= to= actorType=`) y en los fallos de vaciado del caché o de registro de la estrategia de mercado. El correlation id se propaga a la auditoría. |
| §6.1 OpenAPI | PASS | Las cuatro operaciones y el parámetro nuevo se anotan con esquemas, ejemplos, seguridad y códigos ([contrato](contracts/strategy-comparison-api.yaml)). |
| Regla Entrega 1 | PASS | Ningún archivo de Entrega 1 cambia. `GlobalExceptionHandler` se reutiliza tal cual. |

**Re-check post diseño**: sin violaciones nuevas. El único punto abierto es el riesgo de R10, que es operativo y no de diseño.

## Project Structure

### Documentation (this feature)

```text
specs/031-comparador-estrategias/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── strategy-comparison-api.yaml
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/com/example/demo/
├── config/
│   └── MethodSecurityConfig.java                  # NUEVO: @EnableMethodSecurity
├── controller/
│   ├── QuoteController.java                       # + GET /quotes/opportunities, GET /quotes/latest
│   ├── StrategyController.java                    # NUEVO: GET/PUT /strategies/active
│   └── UserMarketController.java                  # + @RequestParam strategy (opcional) y OpenAPI
├── dto/
│   ├── market/PortfolioResponse.java              # + strategyValuation (NON_NULL) y constructor previo
│   ├── market/PortfolioPositionResponse.java      # + strategyValuation (NON_NULL) y constructor previo
│   ├── market/PortfolioStrategyValuationResponse.java   # NUEVO
│   ├── market/PositionStrategyValuationResponse.java    # NUEVO
│   ├── quote/OpportunitiesResponse.java           # NUEVO (+ OpportunityItemResponse)
│   ├── quote/LatestQuotesResponse.java            # NUEVO
│   └── strategy/                                  # NUEVO: StrategyRef, ActiveStrategyResponse, ChangeActiveStrategyRequest
├── exception/
│   ├── QuoteExceptionHandler.java                 # sin cambios (ya cubre QuoteController)
│   └── StrategyExceptionHandler.java              # NUEVO: unknown_strategy para UserMarketController y StrategyController
├── model/
│   ├── ActiveStrategySetting.java                 # NUEVO: entidad de una fila
│   └── market/AuditAction.java                    # + ACTIVE_STRATEGY_CHANGED
├── repository/
│   ├── ActiveStrategySettingRepository.java       # NUEVO (findLockedById con PESSIMISTIC_WRITE)
│   ├── ComparisonPlayerRepository.java            # NUEVO: jugadores con posiciones en una consulta (ajuste de implementación)
│   └── PlayerQuoteRepository.java                 # + findLatestForPlayers(code, ids)
└── service/
    ├── market/PortfolioService.java               # + Optional<String> strategy; delega en el valuator
    ├── market/PortfolioStrategyValuator.java      # NUEVO: aritmética de la valuación alternativa
    └── valuation/
        ├── OpportunityService.java                # NUEVO
        ├── OpportunityCalculator.java             # NUEVO: diferencias, clasificación y orden (puro)
        ├── LatestQuoteService.java                # NUEVO: lote de últimas cotizaciones
        ├── ActiveStrategyService.java             # NUEVO: consulta, cambio, auditoría, evento, estrategia de mercado
        ├── ActiveStrategyChangedEvent.java        # NUEVO
        ├── QuoteCacheEvictor.java                 # + listener AFTER_COMMIT de ActiveStrategyChangedEvent
        ├── QuoteCalculationService.java           # activa fijada al inicio de la corrida + recordMarketStrategy
        ├── MarketStrategyRecorder.java            # NUEVO: interfaz que implementa ActiveStrategyService
        └── strategy/ValuationStrategyCatalog.java # active() lee la fila; alternative(); validación al arrancar

backend/src/test/java/com/example/demo/
├── service/valuation/OpportunityCalculatorTest.java          # unitario: diferencias, %, redondeo, orden, exclusiones
├── service/valuation/strategy/ValuationStrategyCatalogPersistedTest.java  # fila > propiedad; código inválido no arranca
├── service/market/PortfolioStrategyValuatorTest.java          # unitario: valores, totales, sin cotización
├── controller/OpportunityControllerTest.java                  # MockMvc + datos a mano: filtros, limit, 400, 401
├── controller/LatestQuotesControllerTest.java                 # MockMvc: orden, duplicados, inexistentes, 100/101, 400
├── controller/UserMarketStrategyControllerTest.java           # MockMvc: ?strategy, identidad sin parámetro, 400/403
├── controller/StrategyControllerTest.java                     # GET (USER 200, 401); PUT (USER 403, ADMIN 200, 400, idempotente, auditoría)
└── service/valuation/ActiveStrategyRecalculationTest.java     # cambio → marketValue intacto → recálculo usa la nueva → pending=false
```

**Structure Decision**: Se usa el backend existente (`backend/`) con sus capas. Las piezas nuevas siguen los paquetes de las features 016, 018 y 028. La lógica numérica queda en clases sin Spring para testearla unitariamente y mantener baja la complejidad Sonar.

## Complexity Tracking

Sin violaciones de la Constitution que justificar.
