---

description: "Implementation tasks for append-only player statistics snapshots"
---

# Tasks: Historial de snapshots de estadísticas

**Input**: Design documents from `/specs/014-stats-snapshots/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `quickstart.md`

**Tests**: La feature solicita explícitamente pruebas unitarias, `@DataJpaTest` y `@E2ETest`. Los tipos fundacionales T003–T005 se crean primero para que la suite compile; dentro de cada historia, los tests del comportamiento del servicio deben escribirse antes de su implementación y comprobarse en rojo.

**Protected files**: No modificar `backend/src/main/java/com/example/demo/model/PlayerStats.java`, `backend/src/main/java/com/example/demo/service/PlayerStatsService.java` ni `backend/src/main/java/com/example/demo/repository/PlayerStatsRepository.java`. Tampoco modificar MOMENTUM ni `MomentumParameters`.

**Organization**: Las tareas se agrupan por historia de usuario. Todas las historias tienen prioridad P1, pero US2 depende del historial creado por US1, US3 depende de la consulta ordenada de US2 y US4 extiende las ventanas de US3.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo porque afecta archivos distintos y no depende de una tarea incompleta de la misma fase.
- **[Story]**: Historia de usuario cubierta (`US1`, `US2`, `US3`, `US4`).
- Todas las tareas incluyen una ruta concreta del repositorio.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Proveer una fuente temporal UTC reutilizable y comprobable sin tocar configuración protegida.

- [X] T001 Crear una prueba de contexto que exija un único bean `java.time.Clock` con zona UTC en `backend/src/test/java/com/example/demo/config/ClockConfigTest.java`
- [X] T002 Implementar `ClockConfig` con un bean `Clock.systemUTC()` reutilizable por esta feature y por 016 en `backend/src/main/java/com/example/demo/config/ClockConfig.java`

**Checkpoint**: El backend dispone de un reloj inyectable y los tests pueden reemplazarlo por `Clock.fixed`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Crear los tipos persistentes y de salida compartidos que bloquean todas las historias.

**⚠️ CRITICAL**: No comenzar las historias hasta completar esta fase.

- [X] T003 Crear `PlayerStatsSnapshot` siguiendo el patrón de `PlayerQuote` en `backend/src/main/java/com/example/demo/model/PlayerStatsSnapshot.java`: `@Immutable`, id `Long` autogenerado no nulo; `player` no nulo como `ManyToOne` lazy unidireccional sin cascada; `minutesPlayed`, `goals`, `assists`, `shots`, `keyPasses`, `tackles`, `dribbles`, `yellowCards`, `redCards` y `rating` nullable; rating con precisión 4 y escala 2; `sourceFetchedAt`, `capturedAt` y `runId` no nulos; todas las columnas salvo el id con `updatable=false`; índice `idx_player_stats_snapshots_player_captured` sobre `(player_id, captured_at)` y restricción `uk_player_stats_snapshots_player_source` sobre `(player_id, source_fetched_at)`
- [X] T004 Crear el repositorio append-only mínimo en `backend/src/main/java/com/example/demo/repository/PlayerStatsSnapshotRepository.java`, extendiendo `Repository` y exponiendo sólo inserción/flush, lectura por id y una única consulta por lote que proyecte claves existentes para los conjuntos candidatos de `playerId` y `sourceFetchedAt`; el servicio filtrará luego por el par exacto `(playerId, sourceFetchedAt)`, sin N consultas `exists`, y el repositorio no debe heredar ni declarar actualización o borrado
- [X] T005 Crear el record `SnapshotResult(int saved, int skipped)` con validación de valores no negativos en `backend/src/main/java/com/example/demo/service/valuation/SnapshotResult.java`

**Checkpoint**: El esquema histórico, la unicidad concurrente, el índice y el resultado de captura están disponibles sin modificar Entrega 1.

---

## Phase 3: User Story 1 - Conservar el historial estadístico sin duplicados (Priority: P1) 🎯 MVP

**Goal**: Capturar en una operación todas las estadísticas persistidas, copiar fielmente métricas y `null`, usar una hora/run comunes y devolver guardados/salteados sin duplicar ante repeticiones o carreras.

**Independent Test**: Preparar tres jugadores con estadísticas y dos sin ellas, ejecutar `snapshotAll(runId)` con `Clock.fixed`, verificar 3 guardados/0 salteados y todas las copias; repetir con el mismo `fetchedAt` y verificar 0 guardados/3 salteados y sólo tres filas, sin interacción con adapters externos.

### Tests for User Story 1

- [X] T006 [P] [US1] Escribir pruebas `@DataJpaTest` + `@UnitTestProfile` que persistan y relean las diez métricas, conserven `null`, `sourceFetchedAt`, `capturedAt` y `runId`, rechacen la clave duplicada `(player_id, source_fetched_at)` y verifiquen por reflexión que el repositorio no expone operaciones destructivas en `backend/src/test/java/com/example/demo/repository/PlayerStatsSnapshotRepositoryTest.java`
- [X] T007 [P] [US1] Escribir la prueba de persistencia `@DataJpaTest` + `@UnitTestProfile`, importando el servicio y un `Clock.fixed` y desactivando la transacción externa con `@Transactional(propagation = NOT_SUPPORTED)` para que los fixtures confirmados sean visibles a `REQUIRES_NEW`; cubrir captura vacía, guardado en lote, hora común, conteos `(saved, skipped)`, copia de `dribbles`/`null`, segunda ejecución idempotente y un `sourceFetchedAt` más antiguo pero distinto que debe guardarse sin alterar snapshots posteriores en `backend/src/test/java/com/example/demo/service/valuation/StatsSnapshotPersistenceTest.java`
- [X] T008 [P] [US1] Escribir pruebas unitarias con repositorios simulados para `runId` nulo y para una carrera donde el primer lote falla por unicidad, las claves existentes aumentan y el reintento devuelve la colisión como salteada; exigir que un fallo de integridad sin reducción del conjunto pendiente se propague en `backend/src/test/java/com/example/demo/service/valuation/StatsSnapshotServiceTest.java`

### Implementation for User Story 1

- [X] T009 [US1] Implementar en `backend/src/main/java/com/example/demo/service/valuation/StatsSnapshotService.java` la validación de `runId`, una única lectura de `clock.instant()`, lectura mediante el `PlayerStatsRepository` existente, una sola consulta por lote de claves existentes con filtrado en memoria por el par exacto, copia de las diez métricas y persistencia por lote de las claves nuevas, devolviendo `SnapshotResult` y excluyendo jugadores sin `PlayerStats`
- [X] T010 [US1] Completar en `backend/src/main/java/com/example/demo/service/valuation/StatsSnapshotService.java` el guardado con flush en transacción nueva y el retry portable ante `DataIntegrityViolation`: revertir el lote, refrescar claves, contar como salteadas las ganadas por otra corrida, reintentar sólo las pendientes y propagar el error si el conjunto no disminuye
- [X] T011 [US1] Escribir primero pruebas de logging con captura de eventos SLF4J para correlation ID existente, generación cuando falta, exactamente un evento terminal, éxito con correlation ID/`runId`/guardados/salteados, error con correlation ID/`runId`/`errorType` y sin conteos parciales, y restauración del MDC en ambos caminos en `backend/src/test/java/com/example/demo/service/valuation/StatsSnapshotLoggingTest.java`; luego implementar ese contrato y la limpieza en `finally` en `backend/src/main/java/com/example/demo/service/valuation/StatsSnapshotService.java`, sin tabla nueva, y ejecutar las pruebas US1

**Checkpoint**: US1 funciona como MVP independiente: historial append-only local, idempotente y trazable por corrida.

---

## Phase 4: User Story 2 - Consultar los snapshots recientes de un jugador (Priority: P1)

**Goal**: Seleccionar como máximo los últimos N snapshots de un jugador usando la base y devolverlos cronológicamente, con desempate determinístico.

**Independent Test**: Guardar cinco snapshots del jugador objetivo, snapshots de otro jugador y un empate de `capturedAt`; solicitar N=3 y comprobar que sólo vuelven los tres más recientes del objetivo en orden `capturedAt ASC, id ASC`, que un jugador sin historial devuelve vacío y que N<=0 se rechaza.

### Tests for User Story 2

- [X] T012 [P] [US2] Ampliar las pruebas JPA con selección limitada por jugador, aislamiento entre jugadores, menos de N disponibles y desempate por id cuando coincide `capturedAt` en `backend/src/test/java/com/example/demo/repository/PlayerStatsSnapshotRepositoryTest.java`
- [X] T013 [P] [US2] Agregar pruebas unitarias de `recentSnapshots(playerId,n)` para invertir sólo el subconjunto limitado, devolver vacío y rechazar N<=0 en `backend/src/test/java/com/example/demo/service/valuation/StatsSnapshotServiceTest.java`
- [X] T014 [P] [US2] Crear un único `@E2ETest` con `E2EFixtures` que valide en PostgreSQL los últimos N y su orden, el aislamiento por jugador, la restricción única y la existencia de `idx_player_stats_snapshots_player_captured`; sincronizar además con `CountDownLatch` dos llamadas concurrentes a `snapshotAll` sobre el mismo `(playerId, sourceFetchedAt)` y verificar una sola fila y resultados complementarios —una guardada y otra salteada— en `backend/src/test/java/com/example/demo/e2e/PlayerStatsSnapshotE2ETest.java`

### Implementation for User Story 2

- [X] T015 [US2] Implementar la consulta paginada por `player.id` ordenada por `capturedAt DESC, id DESC` en `backend/src/main/java/com/example/demo/repository/PlayerStatsSnapshotRepository.java`, de modo que el límite N se aplique en la base usando el índice histórico
- [X] T016 [US2] Implementar `recentSnapshots(playerId,n)` en `backend/src/main/java/com/example/demo/service/valuation/StatsSnapshotService.java`, validando N estrictamente positivo e invirtiendo el subconjunto a `capturedAt ASC, id ASC` sin leer el historial completo
- [X] T017 [US2] Ejecutar las pruebas US2 desde `backend/pom.xml` con `./mvnw.cmd "-Dtest=PlayerStatsSnapshotRepositoryTest,StatsSnapshotServiceTest,PlayerStatsSnapshotE2ETest" test` y confirmar los resultados tanto en H2 como en PostgreSQL cuando el entorno e2e esté disponible

**Checkpoint**: US2 es demostrable mediante consulta acotada y determinística, sin calcular todavía ventanas.

---

## Phase 5: User Story 3 - Derivar ventanas de rendimiento entre snapshots (Priority: P1)

**Goal**: Producir una ventana por par consecutivo con deltas null-safe, correcciones negativas en cero y rating de intervalo ponderado por minutos.

**Independent Test**: Proveer secuencias ordenadas con un solo snapshot, contadores crecientes/decrecientes, extremos `null`, minutos iguales y un rating acumulado que baja; comprobar N−1 ventanas, deltas correctos y rating calculado/clampado o `null` según corresponda.

### Tests for User Story 3

- [X] T018 [US3] Agregar pruebas unitarias para un solo snapshot sin ventanas, N snapshots → N−1 ventanas, todos los contadores, propagación independiente de `null`, delta negativo a cero, rating acumulado que baja, extremos faltantes, minutos sin aumento y clamps 0/10 en `backend/src/test/java/com/example/demo/service/valuation/StatsSnapshotServiceTest.java`

### Implementation for User Story 3

- [X] T019 [US3] Crear el record no persistente `StatsWindow` en `backend/src/main/java/com/example/demo/service/valuation/StatsWindow.java` con `previousSnapshotId` y `currentSnapshotId` no nulos, `fromCapturedAt` y `toCapturedAt` no nulos, `seasonReset`, las nueve métricas contador/minutos nullable y `rating` nullable pero acotado a `[0,10]` cuando existe
- [X] T020 [US3] Implementar `recentWindows(playerId,n)` y el emparejamiento consecutivo normal en `backend/src/main/java/com/example/demo/service/valuation/StatsSnapshotService.java`: `null` si falta cualquier extremo, `max(0,nuevo-anterior)` para contadores, rating `(nuevo×minutosNuevos−anterior×minutosAnteriores)/deltaMinutos` con `MathContext.DECIMAL64`, clamp `[0,10]` y `null` si falta un extremo o `deltaMinutos<=0`
- [X] T021 [US3] Ejecutar las pruebas de ventanas normales desde `backend/pom.xml` con `./mvnw.cmd -Dtest=StatsSnapshotServiceTest test` y verificar que un snapshot no inventa una ventana contra cero

**Checkpoint**: US3 deriva forma reciente normal de manera determinística sin persistir ventanas ni consultar fuentes externas.

---

## Phase 6: User Story 4 - Detectar un reinicio de temporada (Priority: P1)

**Goal**: Evitar mezclar temporadas cuando bajan los minutos y representar la primera ventana nueva mediante los totales del snapshot actual.

**Independent Test**: Comparar un snapshot de 1.800 minutos con uno de 90 y comprobar `seasonReset=true`, copia literal de contadores/`null` nuevos y rating nuevo clampado; comprobar también minutos nuevos cero y extremos de minutos ausentes.

### Tests for User Story 4

- [X] T022 [US4] Agregar pruebas unitarias de reinicio con baja 1.800→90, copia de cada total nuevo, conservación de `null`, rating nuevo y clamps, minutos nuevos cero y ausencia de minutos que no activa reinicio en `backend/src/test/java/com/example/demo/service/valuation/StatsSnapshotServiceTest.java`

### Implementation for User Story 4

- [X] T023 [US4] Extender la derivación en `backend/src/main/java/com/example/demo/service/valuation/StatsSnapshotService.java` para marcar reinicio sólo si ambos minutos existen y `nuevo<anterior`, copiar contadores/minutos nuevos sin restar y devolver rating nuevo clampado sólo cuando exista y los minutos nuevos sean positivos
- [X] T024 [US4] Ejecutar las pruebas completas del servicio desde `backend/pom.xml` con `./mvnw.cmd -Dtest=StatsSnapshotServiceTest test` y confirmar que correcciones de contadores aislados no se confunden con un reinicio

**Checkpoint**: Las cuatro historias quedan funcionales; las ventanas distinguen correcciones ordinarias de un cambio de temporada.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Verificar integración, alcance, documentación y Definition of Done.

- [X] T025 [P] Revisar JavaDoc, nombres de constraints/índices y ausencia de APIs destructivas en `backend/src/main/java/com/example/demo/model/PlayerStatsSnapshot.java`, `backend/src/main/java/com/example/demo/repository/PlayerStatsSnapshotRepository.java` y `backend/src/main/java/com/example/demo/service/valuation/StatsSnapshotService.java`
- [X] T026 [P] Ajustar los comandos y resultados esperados de validación a los nombres finales de tests en `specs/014-stats-snapshots/quickstart.md`
- [X] T027 Ejecutar `./mvnw.cmd verify` desde `backend/pom.xml`, revisar el diff para confirmar cero cambios en `PlayerStats.java`, `PlayerStatsService.java`, `PlayerStatsRepository.java`, `MomentumParameters.java` y algoritmos MOMENTUM, y documentar cualquier test e2e omitido por falta de PostgreSQL/Redis

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Sin dependencias; T001 debe quedar en rojo antes de T002.
- **Foundational (Phase 2)**: Depende de T002 y bloquea todas las historias.
- **US1 (Phase 3)**: Depende de Foundation; crea el MVP persistente y el servicio base.
- **US2 (Phase 4)**: Depende de US1 porque consulta el historial que US1 modela y persiste.
- **US3 (Phase 5)**: Depende de US2 porque `recentWindows` consume `recentSnapshots` ya limitado y ordenado.
- **US4 (Phase 6)**: Depende de US3 porque especializa su algoritmo de ventanas.
- **Polish (Phase 7)**: Depende de todas las historias seleccionadas.

### User Story Dependency Graph

```text
Setup → Foundation → US1 (captura) → US2 (últimos N) → US3 (ventanas) → US4 (reinicio) → Polish
```

### Within Each User Story

- Escribir los tests indicados y comprobar que fallen por el comportamiento ausente.
- Implementar el mínimo necesario para ponerlos en verde sin tocar los archivos protegidos.
- Ejecutar el checkpoint de la historia antes de avanzar.
- Mantener los errores de integridad inesperados visibles; sólo las colisiones comprobadas por unicidad se convierten en salteados.

### Parallel Opportunities

- En US1, T006, T007 y T008 pueden escribirse en paralelo porque afectan tres archivos de prueba distintos.
- En US2, T012, T013 y T014 pueden escribirse en paralelo después de completar US1.
- En Polish, T025 y T026 pueden ejecutarse en paralelo.
- La implementación central de `StatsSnapshotService.java` es deliberadamente secuencial entre historias para evitar ediciones concurrentes del mismo archivo.

---

## Parallel Example: User Story 1

```text
Task T006: Persistencia, campos y unicidad en PlayerStatsSnapshotRepositoryTest.java
Task T007: Captura integrada e idempotencia en StatsSnapshotPersistenceTest.java
Task T008: Carrera y retry transaccional en StatsSnapshotServiceTest.java
```

## Parallel Example: User Story 2

```text
Task T012: Consulta JPA de últimos N en PlayerStatsSnapshotRepositoryTest.java
Task T013: Orden y validación del servicio en StatsSnapshotServiceTest.java
Task T014: PostgreSQL, E2EFixtures, índice y unicidad en PlayerStatsSnapshotE2ETest.java
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Completar Setup y Foundation.
2. Completar los tests T006–T008 y observar los fallos esperados del comportamiento del servicio.
3. Implementar T009–T010.
4. Completar T011 para auditoría/correlation ID, ejecutar las pruebas US1 y detenerse para validar el historial append-only como MVP.
5. Integrar el disparador recién en feature 016; no ampliar el alcance de esta feature.

### Incremental Delivery

1. **US1**: Captura idempotente y trazable.
2. **US2**: Últimos N snapshots ordenados y validados en PostgreSQL.
3. **US3**: Ventanas normales con deltas y rating ponderado.
4. **US4**: Reinicios de temporada.
5. **Polish**: Suite completa, quickstart y auditoría de archivos protegidos.

### Suggested Commit Boundaries

1. `test/feat: add clock and snapshot persistence model`
2. `feat: capture player stats snapshots idempotently`
3. `feat: query recent player stats snapshots`
4. `feat: derive recent stats windows`
5. `feat: handle season resets in stats windows`
6. `test/docs: validate stats snapshots end to end`

---

## Notes

- `[P]` sólo aparece cuando las tareas modifican archivos diferentes y no dependen entre sí.
- No hay tareas de controller, OpenAPI, frontend, scheduler ni adapter porque están fuera de alcance.
- `PlayerStatsRepository` se consume sin modificarlo.
- `StatsWindow` se deriva en memoria y no se persiste.
- Si la implementación exigiera tocar un archivo protegido, detener el trabajo y solicitar aprobación indicando archivo, cambio mínimo y alternativa.
