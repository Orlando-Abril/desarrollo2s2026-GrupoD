# Implementation Plan: Recálculo de cotizaciones

**Branch**: `feature/recalculo-cotizaciones` | **Date**: 2026-10-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/016-recalculo-cotizaciones/spec.md`

## Summary

Esta feature une las piezas de 013, 014 y 015 en una corrida de recálculo y la expone en `POST /quotes/recalculate`:

- **Corrida** (`QuoteCalculationService.recalculate(Optional<String>)`):
  1. Resuelve la estrategia pedida con el catálogo (400 si no existe) y toma una guardia en memoria (409 si está ocupada).
  2. Fija `runId` y `calculatedAt` con el `Clock` de 014 y toma un snapshot de estadísticas.
  3. En una transacción de sólo lectura, carga jugadores y estadísticas, y por estrategia la población y la última cotización de cada jugador.
  4. Procesa cada jugador en su propia transacción: calcula todas las estrategias, guarda las `PlayerQuote` y actualiza `marketValue` con la estrategia activa.
  - Una falla en un jugador revierte sólo ese jugador. MOMENTUM se saltea con `no_algorithm` hasta 019.
- **Contrato 015**: `ValuationAlgorithm` gana `preparePopulation` (default `null`). OFFICIAL lo implementa con su `normalize`.
- **Precio de mercado**: `Player.marketValue` pasa a `numeric(12,4)`.
- **Seguridad**:
  - JWT con `ROLE_<rol>` leído de `users`; API key con el rol de su dueño (`ROLE_ADMIN` si no tiene dueño).
  - `hasRole("ADMIN")` en el endpoint y 403 JSON `forbidden`.
- **API**:
  - `QuoteController` y DTOs con OpenAPI.
  - Errores en un `ControllerAdvice` acotado al controller, sin tocar `GlobalExceptionHandler`.

Las decisiones están en [research.md](research.md) (D1–D14).

## Technical Context

**Language/Version**: Java 17

**Primary Dependencies**:
- Spring Boot 4.1.1: Web MVC, Data JPA, Security y `TransactionOperations` / `TransactionTemplate`.
- springdoc-openapi (ya en el proyecto).
- Lombok.
- No se agregan dependencias.

**Storage**:
- PostgreSQL. Tablas existentes `players`, `player_quotes`, `player_stats`, `player_stats_snapshots` y `users`.
- Cambia el tipo de `players.market_value` a `numeric(12,4)`, con migración manual para bases existentes (quickstart §0).
- H2 / create-drop en el perfil `test`.

**Testing**:
- JUnit 5, AssertJ y Mockito para el servicio, con `Clock.fixed` y `TransactionOperations` que ejecuta directo o un mock que simula rollback.
- `@SpringBootTest` + MockMvc con `@UnitTestProfile` para el controller y la seguridad.
- `@E2ETest` contra PostgreSQL y Redis.

**Target Platform**: backend web service en Linux o Windows.

**Project Type**: aplicación web (`backend/` y `frontend/`). Esta feature sólo toca `backend/`.

**Performance Goals**:
- Corrida sincrónica de menos de 1 minuto para unos 2.500 jugadores (SC-008).
- 1 consulta de jugadores, 1 de estadísticas y 1 de últimas cotizaciones por estrategia.
- Por jugador, 1 `findById` y 1 `saveAll`, cada uno en su transacción.

**Constraints**:
- Archivos protegidos de Entrega 1: sólo se hacen los cambios autorizados (ver tabla abajo).
- No hay consultas a fuentes externas.
- Una sola instancia (la guardia es en memoria).
- Las cotizaciones son inmutables; sólo se agregan.

**Scale/Scope**:
- 4 archivos de producción existentes se modifican con autorización: `Player`, `JwtAuthFilter`, `ApiKeyAuthFilter` y `SecurityConfig`.
- Otros 3 existentes no protegidos se modifican: `ValuationAlgorithm`, `OfficialPerformanceAlgorithm` y `PlayerResponse` (sólo el ejemplo).
- Unas 12 clases nuevas.
- 6 clases de test nuevas, y se ajustan 3 o 4 tests existentes.

No quedan `NEEDS CLARIFICATION`: la regla de la API key (FR-029) quedó decidida por el equipo en D10.

### Cambios en archivos protegidos de Entrega 1

| Archivo | Cambio | Autorización |
|---------|--------|--------------|
| `model/Player.java` | `@Column(..., precision = 12, scale = 4)` en `marketValue` y su javadoc; la corrida usa el setter existente | ✅ explícita |
| `security/JwtAuthFilter.java` | Inyecta `UserRepository` y agrega `ROLE_<rol>` (sin authorities si el usuario no existe) | ✅ explícita |
| `security/ApiKeyAuthFilter.java` | Authorities con el rol del dueño (`ROLE_ADMIN` si la key no tiene dueño) | ✅ explícita |
| `config/SecurityConfig.java` | `requestMatchers(POST, "/quotes/recalculate").hasRole("ADMIN")` + `accessDeniedHandler(new JsonAccessDeniedHandler())` | ✅ explícita (el handler es el 403 `forbidden` que pidió el equipo) |
| `GlobalExceptionHandler`, `PlayerController`, `PlayerRepository` | Sin cambios | — |

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Regla | Cómo se cumple | Estado |
|-------|----------------|--------|
| **§2.3 Recálculo manual** | `POST /quotes/recalculate` dispara la corrida bajo demanda. El método del servicio es invocable desde el scheduler de 017 con la misma guardia (FR-036). | ✅ |
| **§5.1 Endpoint obligatorio** | Se expone `POST /quotes/recalculate` tal como lo lista la constitución. | ✅ |
| **§1.2 Multiestrategia** | Calcula todas las estrategias del catálogo que tienen algoritmo; el mapa por `StrategyType` incorpora MOMENTUM en 019 sin cambios. La estrategia activa define `marketValue`. | ✅ |
| **§1.2 Trazabilidad** | Cada `PlayerQuote` guarda código y versión de estrategia, `runId`, `calculatedAt` y `correlationId`. | ✅ |
| **§1.1 Momento cero** | Sin cotización previa, un NO_STATS toma `baseValue` = 1.0000. | ✅ |
| **§1.3 Resiliencia** | La corrida usa sólo datos persistidos (estadísticas, snapshots, cotizaciones). No inyecta adapters; un test lo verifica y el E2E corre con las fuentes externas caídas. | ✅ |
| **§2.1 Capas** | Controller (`QuoteController`) → Service (`QuoteCalculationService`) → Repositories. No hay adapters. El controller sólo mapea DTOs. | ✅ |
| **§4.1 Autenticación / autorización** | JWT y API key siguen funcionando igual. El endpoint exige ADMIN: por JWT con el rol del usuario, o por API key con el rol de su dueño (ADMIN si no tiene dueño) (D10). | ✅ |
| **§4.2 Auditoría financiera** | No aplica: no hay compra, venta ni ajuste de saldo. La trazabilidad operativa es `runId` + logs. | ✅ N/A |
| **§4.3 Validación de entrada** | Código de estrategia validado contra el catálogo (400). JSON inválido da 400. | ✅ |
| **§5.2 Índices** | Las consultas usan `idx_player_quotes_strategy_time` y `idx_player_quotes_player_strategy_time` (013). No hacen falta índices nuevos. | ✅ |
| **§5.3 Observabilidad** | Logs estructurados de inicio, fin, jugador fallido y falla general, con `runId` y `correlationId` (MDC del `CorrelationIdFilter`). | ✅ |
| **§6.1 OpenAPI** | `@Operation`, `@SecurityRequirement` bearerAuth y apiKeyAuth, `@ApiResponse` 200/400/401/403/409/500 y `@Schema` en los DTOs. Contrato en [contracts/quotes-api.yaml](contracts/quotes-api.yaml). | ✅ |
| **§7 DoD** | Capas respetadas; tests unitarios, MockMvc y E2E; sin dependencias nuevas; endpoint visible y ejecutable en Swagger con ambos esquemas; logs con correlation id; test de que no hay llamadas externas. | ✅ |
| **§7.5 Auditoría / logs** | Cumple: cada cambio de precio queda en `PlayerQuote`, que es inmutable, lleva `correlationId` y `runId`, y es el valor que se copia a `marketValue`. | ✅ |
| **§3.1 / §3.2 CI y Sonar** | Los tests nuevos sin tag e2e corren en CI. Cada rama del servicio (salteada, fallida, NO_STATS con y sin previa, activa no calculada, guardia) tiene test para la cobertura. | ✅ |

**Re-check post-diseño (Phase 1)**: sin violaciones. El diseño no agrega tablas ni dependencias, y los únicos cambios en archivos protegidos son los de la tabla de arriba.

## Project Structure

### Documentation (this feature)

```text
specs/016-recalculo-cotizaciones/
├── plan.md                 # Este archivo
├── research.md             # Decisiones D1–D14
├── data-model.md           # Cambio en Player, tipos de dominio, DTOs, flujo
├── quickstart.md           # Migración, ADMIN, ejecución y verificación
├── contracts/
│   └── quotes-api.yaml     # POST /quotes/recalculate (017 y 018 lo amplían)
├── checklists/requirements.md
└── tasks.md                # Lo genera /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/com/example/demo/
├── model/Player.java                                  # MOD (autorizado): marketValue precision 12, scale 4
├── security/
│   ├── JwtAuthFilter.java                             # MOD (autorizado): ROLE_<rol> desde UserRepository
│   ├── ApiKeyAuthFilter.java                          # MOD (autorizado): rol del dueño, ROLE_ADMIN sin dueño
│   └── JsonAccessDeniedHandler.java                   # NUEVO: 403 {"error":"forbidden",...}
├── config/SecurityConfig.java                         # MOD (autorizado): hasRole("ADMIN") + accessDeniedHandler
├── controller/QuoteController.java                    # NUEVO: POST /quotes/recalculate + OpenAPI
├── dto/quote/
│   ├── RecalculateRequest.java                        # NUEVO
│   └── QuoteRunResponse.java                          # NUEVO (con StrategyRunCountsResponse / SkippedStrategyResponse anidados)
├── dto/player/PlayerResponse.java                     # MOD: @Schema example "1.0000"
├── exception/
│   ├── QuoteExceptionHandler.java                     # NUEVO: advice acotado a QuoteController
│   ├── RecalculationInProgressException.java          # NUEVO
│   └── RecalculationFailedException.java              # NUEVO
└── service/valuation/
    ├── ValuationAlgorithm.java                        # MOD (015): default preparePopulation → null
    ├── official/OfficialPerformanceAlgorithm.java     # MOD (015): @Override preparePopulation → normalize
    ├── QuoteCalculationService.java                   # NUEVO: la corrida
    ├── QuoteRunResult.java                            # NUEVO
    ├── StrategyRunCounts.java                         # NUEVO
    └── SkippedStrategy.java                           # NUEVO

backend/src/test/java/com/example/demo/
├── service/valuation/QuoteCalculationServiceTest.java # NUEVO: unitario con mocks
├── controller/QuoteControllerTest.java                # NUEVO: MockMvc 200/400/409/500 y body opcional
├── security/QuoteRecalculationSecurityTest.java       # NUEVO: 401, 403 USER (JWT y key), 200 ADMIN (JWT y key), otros endpoints sin cambio
├── e2e/QuoteRecalculationE2ETest.java                 # NUEVO: @E2ETest PostgreSQL
├── security/JwtAuthFilterTest.java                    # MOD: constructor con UserRepository, casos de rol
├── security/ApiKeyAuthFilterTest.java                 # MOD: casos de rol por dueño (USER, ADMIN, sin dueño)
├── service/valuation/official/OfficialPerformanceAlgorithmTest.java # MOD: preparePopulation == normalize
└── e2e/ExternalSourcesDownE2ETest.java                # MOD: marketValue esperado 1.0000
```

**Structure Decision**: la corrida vive en `service/valuation`, junto a `StatsSnapshotService` y los algoritmos, porque orquesta ese paquete. Los records de resultado van al lado del servicio y no en `dto`: son dominio y 017 los reusa sin pasar por HTTP. El controller, los DTOs y las excepciones siguen los paquetes existentes (`controller`, `dto/<área>`, `exception`). `JsonAccessDeniedHandler` va en `security` como clase nueva, para que el cambio en `SecurityConfig` sea mínimo.

## Diseño

### QuoteCalculationService

Dependencias (constructor):
- `ValuationStrategyCatalog`, `List<ValuationAlgorithm>`, `StatsSnapshotService`;
- `PlayerRepository`, `PlayerStatsRepository`, `PlayerQuoteRepository`;
- `Clock`, `TransactionOperations` (por jugador) y `PlatformTransactionManager`, del que sale un `TransactionTemplate` read-only para la carga.

El flujo completo está en [data-model.md](data-model.md#flujo-de-la-corrida). Puntos clave:

1. **Orden de validaciones**: `catalog.find(code)` va antes de la guardia, así un 400 nunca bloquea y nunca dispara snapshot. La guardia (`AtomicBoolean.compareAndSet`) va antes del snapshot.
2. **Snapshot y carga**: el snapshot y la carga read-only van dentro de un try. Cualquier `RuntimeException` que salga de ellos, o del armado del resultado después del bucle de jugadores, se envuelve en `RecalculationFailedException(runId, e)`. Las fallas dentro de la transacción de un jugador no se envuelven. En ese caso se loguea `quote_recalculation_failed` y se libera la guardia.
3. **Carga** (read-only): `Map<Long, PlayerStats>` desde `playerStatsRepository.findAll()`. Por cada definición con algoritmo:
   - `population = algorithm.preparePopulation(entries)`, con un `PopulationEntry(id, positions, statsOrNull)` por jugador;
   - `latest = findLatestForAllPlayers(code)` indexado por `player.getId()`.
   - Las posiciones son `EAGER` y los campos de `PlayerStats` son básicos, así que los objetos se usan detached después del commit.
4. **Por jugador**, dentro de `transactionOperations.executeWithoutResult`:
   - `Player managed = playerRepository.findById(id).orElseThrow()`.
   - Por definición calculable: `calculate(new ValuationInput(id, positions, stats, population, latestOrEmpty, List.of()), def)` y luego `toQuote(...)`.
   - `quoteRepository.saveAll(quotes)`.
   - Si la activa se calculó: `managed.setMarketValue(valorDeLaActiva)`.
   - Los conteos se suman **después** de que la transacción confirma, así un jugador revertido no suma estados.
   - `catch (RuntimeException e)`: se loguea `quote_recalculation_player_failed` y se hace `failed++`.
5. **Resultado**: `QuoteRunResult` con las reglas de [data-model.md](data-model.md#quoterunresult-record). El log `quote_recalculation_finished` incluye `durationMs`.
6. **`finally`**: `running.set(false)` y se restaura el MDC (igual que `StatsSnapshotService`).

### QuoteController

```text
@Tag(name = "Quotes")
@PostMapping("/quotes/recalculate")
@Operation(...) @SecurityRequirement(name = "bearerAuth") @SecurityRequirement(name = "apiKeyAuth")
@ApiResponse 200 (QuoteRunResponse) / 400 / 401 / 403 / 409 / 500 (ErrorResponse)
QuoteRunResponse recalculate(@RequestBody(required = false) RecalculateRequest request)
  → service.recalculate(Optional.ofNullable(request).map(RecalculateRequest::strategy))
  → QuoteRunResponse.from(result)
```

Las dos anotaciones `@SecurityRequirement` en el método generan dos requisitos alternativos (OR), como en el contrato.

### Seguridad

- `JwtAuthFilter`: `List<GrantedAuthority> authorities = userRepository.findByUsername(username).map(u -> List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name()))).orElse(List.of())`.
- `ApiKeyAuthFilter`: `List.of(new SimpleGrantedAuthority("ROLE_" + (owner != null ? owner.getRole().name() : "ADMIN")))`, con el mismo `owner` que ya se lee para el principal.
- `SecurityConfig`: el matcher de POST `/quotes/recalculate` va **antes** de `anyRequest()`. Las rutas públicas no cambian. Un pedido sin credenciales sigue recibiendo el 401 del `ApiKeyAuthFilter`, antes de llegar a la autorización.

### Estrategia de pruebas

**`QuoteCalculationServiceTest`** (unitario, Mockito, `Clock.fixed`, `TransactionOperations` que ejecuta el callback; para simular rollback, un `saveAll` que lanza excepción):
- corrida feliz: una cotización por jugador y estrategia, mismo `runId` / `calculatedAt` / `correlationId`, versión de la estrategia;
- `NO_STATS` con cotización previa: conserva el valor y no tiene score;
- `NO_STATS` sin previa: `1.0000`;
- `INSUFFICIENT_HISTORY` tratado igual (con un algoritmo stub);
- `previousQuotes` contiene sólo la última de esa estrategia, aunque haya una de MOMENTUM más nueva (`findLatestForAllPlayers` se llama con el código correcto);
- MOMENTUM sin algoritmo: salteada con `no_algorithm`, sin cotizaciones;
- activa MOMENTUM no calculada: `setMarketValue` nunca se llama y `marketValueUpdated` es false;
- `marketValue` igual a la cotización activa con 4 decimales (`1.0437`);
- falla de un jugador: los demás se guardan, conteos 4/1, log de error y no hay conteos del fallido;
- con dos estrategias calculables (stub), si falla la segunda no se guarda la primera de ese jugador;
- población preparada una vez por estrategia (`verify(times(1))`);
- snapshot que falla: `RecalculationFailedException`, 0 `saveAll`, guardia liberada;
- código inexistente: `UnknownValuationStrategyException`, sin snapshot;
- concurrencia: con la primera corrida bloqueada en el snapshot (latch), la segunda lanza `RecalculationInProgressException`. Después de una excepción, una nueva corrida se acepta;
- sin MDC: genera correlation id y lo limpia; con MDC: lo usa y lo conserva;
- catálogo vacío: 0/0 y `marketValueUpdated` true;
- `verifyNoInteractions(whoScoredAdapter, footballDataAdapter)`: se pasan como mocks en el test para demostrar que el servicio no los recibe ni los usa.

**`QuoteControllerTest`** (`@SpringBootTest` + MockMvc + `@UnitTestProfile`, servicio con `@MockitoBean`, autenticado como ADMIN):
- 200 sin body, con `{}`, con `{"strategy":null}` (→ `Optional.empty()`) y con `{"strategy":"OFFICIAL"}`;
- forma del JSON;
- 400 `unknown_strategy`, 400 por JSON inválido, 409 y 500 con `ErrorResponse`.

**`QuoteRecalculationSecurityTest`** (MockMvc con la cadena real y el servicio mockeado):
- 401 sin credenciales; 401 con token inválido y con API key inválida;
- 403 `forbidden` con JWT de USER y con API key de USER;
- 200 con JWT de ADMIN y con API key de ADMIN;
- `GET /players` con JWT de USER, de un usuario inexistente y con API key de USER: 200, igual que antes;
- OpenAPI: `/v3/api-docs` contiene el path con `bearerAuth`, `apiKeyAuth` y las respuestas 200/400/401/403/409.

**`QuoteRecalculationE2ETest`** (`@E2ETest`, PostgreSQL, fuentes externas apuntando a `127.0.0.1:1`):
- jugadores con y sin stats, uno con cotización OFFICIAL previa;
- `POST` con la API key de un ADMIN: 200;
- verifica cotizaciones por `runId`, valores `NO_STATS`, `marketValue` igual a la cotización (`compareTo`), escala 4 leída de la base y snapshots con el mismo `runId`.

**Ajustes de tests existentes**:
- `JwtAuthFilterTest`: constructor nuevo y casos de rol (USER, ADMIN, inexistente sin authorities).
- `ApiKeyAuthFilterTest`: casos nuevos de rol por dueño (USER → `ROLE_USER`, ADMIN y sin dueño → `ROLE_ADMIN`).
- `ExternalSourcesDownE2ETest`: `1.00` → `1.0000`.
- `OfficialPerformanceAlgorithmTest`: `preparePopulation` devuelve lo mismo que `normalize`.

## Complexity Tracking

No aplica: el Constitution Check no presenta violaciones.
