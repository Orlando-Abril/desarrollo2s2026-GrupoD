---

description: "Task list for feature 021: E2E del escenario de evolución de cotizaciones"
---

# Tasks: E2E del escenario de evolución de cotizaciones

**Input**: Design documents from `specs/021-e2e-escenario-cotizaciones/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/scenario-assertions.md, quickstart.md

**Tests**: la feature **es** un test e2e. Las tareas construyen ese test; no hay código de producción.

**Organization**: todo vive en un archivo, `backend/src/test/java/com/example/demo/e2e/QuoteEvolutionE2ETest.java`, con un único `@Test` (plan, Structure Decision). Cada historia agrega su bloque de aserciones al mismo método, así que casi no hay tareas paralelas.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)

## Path Conventions

- Test: `backend/src/test/java/com/example/demo/e2e/QuoteEvolutionE2ETest.java` (abreviado **QE2E**)
- Comandos Maven desde `backend/`
- **No se modifican**: `ClockConfig`, `E2EFixtures`, `E2EDatabaseCleaner`, `E2ETest`, ni archivos protegidos de Entrega 1 (`pom.xml`, `ci.yml`, los `application.properties`, `SecurityConfig`, filtros y controllers). Si alguno pareciera necesario, frenar y consultar al equipo (FR-019).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: confirmar que la infraestructura e2e corre de verdad antes de escribir el test.

- [X] T001 Verificar la infraestructura local según `specs/021-e2e-escenario-cotizaciones/quickstart.md` §1–§2:
  - Memurai iniciado y `Test-NetConnection localhost -Port 6379` en `True`;
  - base dedicada `desarrollo2_grupod_e2e`;
  - `E2E_DB_URL` y `E2E_DB_PASSWORD` definidas.

  Correr `.\mvnw.cmd "-Dtest=QuoteRecalculationE2ETest" test` como línea de base. Tiene que dar `Skipped: 0`. Si da `Skipped`, no seguir hasta resolverlo.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: esqueleto del test, reloj, ADMIN, datos y helpers HTTP. Lo usan las tres historias.

**⚠️ CRITICAL**: ninguna historia puede empezar hasta terminar esta fase.

- [X] T002 Crear `QuoteEvolutionE2ETest` en QE2E:
  - `@E2ETest`, `@LocalServerPort int port`;
  - `@Autowired` de `E2EFixtures`, `PlayerStatsRepository`, `PlayerRepository` (sólo para `marketValue`), `JdbcTemplate` y `Clock`;
  - Javadoc que cite §3.3 escenario 2 y la spec 021.

  Constantes de data-model.md:
  - instantes `RUN_1 = 2026-10-05T18:00:00Z`, `RUN_2 = 2026-10-12T18:00:00Z`, `RUN_3 = 2026-10-19T18:00:00Z`;
  - `fetchedAt` `2026-10-04T10:00Z`, `2026-10-11T10:00Z` y `2026-10-18T10:00Z`;
  - fechas `at`: `2026-10-05`, `2026-10-12` y `2026-10-19`, y `BEFORE_FIRST = 2026-10-04`.
- [X] T003 Agregar en QE2E la clase anidada `static final class MutableClock extends Clock`:
  - zona UTC;
  - `instant()` devuelve el instante actual, que arranca en `RUN_1`;
  - `set(Instant)`;
  - `withZone(zone)` devuelve un reloj que delega en el `MutableClock` original: su `instant()` lee el del original, así un `set()` posterior también lo afecta;
  - `getZone()` devuelve UTC.

  Agregar también `@TestConfiguration static class EvolutionClockConfig` con `@Bean @Primary MutableClock evolutionClock()`. El nombre del bean **no** puede ser `clock` (research R1: el override por nombre está deshabilitado). Al empezar el test, verificar que el `Clock` inyectado es el mismo objeto que `evolutionClock`. Así un fallo de cableado se detecta enseguida.
- [X] T004 Agregar en QE2E el helper `registerAdmin()`, igual que el quickstart de 016 (research R2):
  1. `POST /auth/register` con `{"username":"e2e-admin","email":"e2e-admin@example.com","password":"e2e-admin-pass"}`, verificando 201 y `apiKey` no vacía;
  2. `jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE username = ?", "e2e-admin")`, verificando que devuelva 1;
  3. guardar la `apiKey` en un campo.
- [X] T005 Agregar en QE2E:
  - el `record StatsRow(int minutes, int goals, int assists, int shots, int keyPasses, int dribbles, int tackles, String rating)`;
  - un mapa `League → List<StatsRow>` con las tres columnas de data-model.md, copiadas literal;
  - el helper `loadStats(Player, StatsRow, Instant fetchedAt)`.

  El helper crea `PlayerStats` si no existe o actualiza el existente, con amarillas y rojas en 0, y lo guarda con `PlayerStatsRepository.saveAndFlush`. Bundesliga, en la columna 3, repite los valores de la columna 2 con `fetchedAt` nueva: tiene que guardarse igual (data-model.md).
- [X] T006 Agregar en QE2E los helpers HTTP con `RestClient.create("http://localhost:" + port)` y header `X-API-KEY`. Ninguno lanza excepción ante 4xx: devuelven `ResponseEntity` o usan `onStatus` sin error.
  - `recalculateOn(Instant)`: ajusta el reloj y hace `POST /quotes/recalculate`. Devuelve el cuerpo como `Map<String,Object>`.
  - `history(Player, String strategy)`: hace `GET /players/{id}/quotes?strategy=`. Devuelve `List<Map<String,Object>>`.
  - `effectiveAt(Player, String strategy, LocalDate at)`: devuelve `ResponseEntity<Map<String,Object>>`.
  - `ranking(String strategyOrNull)`: hace `GET /players/ranking`, con `?strategy=` sólo si no es null.
  - `decimal(Object)`: convierte a `BigDecimal` con `new BigDecimal(value.toString())`. Las comparaciones se hacen siempre con `compareTo` (research R3).

**Checkpoint**: el test compila. Un `@Test` vacío que llame a `registerAdmin()` y a `recalculateOn(RUN_1)` pasa contra PostgreSQL y Redis reales.

---

## Phase 3: User Story 1 - Evolución del valor de un jugador por liga en tres fechas (Priority: P1) 🎯 MVP

**Goal**: tres corridas por HTTP en tres fechas. Historial de 3 cotizaciones por estrategia, con su versión, y la vigente por fecha.

**Independent Test**: `.\mvnw.cmd "-Dtest=QuoteEvolutionE2ETest" test` en verde con las aserciones de contracts §2.1–§2.3, §3 y §4.

- [X] T007 [US1] En QE2E, `@BeforeEach setUp()`:
  1. `fixtures.createPlayersFromFiveLeagues()`, guardando los jugadores por liga con `forLeague`;
  2. `registerAdmin()`;
  3. `loadStats` de la columna 1 para los 5 jugadores con `fetchedAt` `2026-10-04T10:00Z`.
- [X] T008 [US1] En QE2E, `@Test void evolutionOfOnePlayerPerLeagueAcrossThreeDates()` arma la secuencia de contracts §2:
  1. `recalculateOn(RUN_1)`;
  2. `loadStats` columna 2 con `fetchedAt` `2026-10-11T10:00Z`, y después `recalculateOn(RUN_2)`;
  3. `loadStats` columna 3 con `fetchedAt` `2026-10-18T10:00Z`, y después `recalculateOn(RUN_3)`.

  En cada respuesta, verificar contracts §2.1:
  - `calculatedAt` igual al instante de la corrida;
  - `playersProcessed` 5, `playersFailed` 0 y `skippedStrategies` vacío;
  - `strategies` con `OFFICIAL` y `MOMENTUM` en `version` 1;
  - `activeStrategy` `OFFICIAL` y `marketValueUpdated` true.

  Verificar los conteos de §2.2 y §2.3:
  - corrida 1: OFFICIAL `calculated` 5 y MOMENTUM `insufficientHistory` 5;
  - corridas 2 y 3: los dos en `calculated` 5.

  Dejar marcado con un comentario dónde va la consulta previa del ranking (T014) antes de la corrida 3.
- [X] T009 [US1] En QE2E, después de la corrida 3, verificar el historial de contracts §3 con `SoftAssertions`, etiquetando cada aserción con `"<LIGA> <ESTRATEGIA>"`. Para los 5 jugadores × {OFFICIAL, MOMENTUM}, `history(...)`:
  - tiene 3 elementos, con `calculatedAt` = [RUN_1, RUN_2, RUN_3] en ese orden;
  - todos con `strategy` igual a la pedida y `version` 1;
  - OFFICIAL: los 3 en `CALCULATED`;
  - MOMENTUM: [0] en `INSUFFICIENT_HISTORY` con `value` 1.0000 por `compareTo`, y [1] y [2] en `CALCULATED` (FR-008, FR-011).

  Guardar los historiales en un `Map<League, Map<String, List<Map<String,Object>>>>` para T010 a T015.
- [X] T010 [US1] En QE2E, verificar la vigente por fecha de contracts §4 con `SoftAssertions`, etiquetando `"<LIGA> <ESTRATEGIA> <fecha>"`.
  - Para los 5 jugadores × 2 estrategias × 3 fechas, `effectiveAt(...)` da 200 y su `value` (por `compareTo`), `status`, `version` y `calculatedAt` son los del elemento del historial de esa fecha (FR-009).
  - Para el jugador Premier, con OFFICIAL y con MOMENTUM, `effectiveAt(..., BEFORE_FIRST)` da 404 con `error` `quote_not_found` (FR-010).
- [X] T011 [US1] Correr `.\mvnw.cmd "-Dtest=QuoteEvolutionE2ETest" test`. Tiene que dar `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.
  - Si una aserción falla, analizar la causa con la tabla de research R4. No se ajusta la aserción para que pase.
  - Si el fallo muestra que los datos no producen la evolución esperada, corregir los datos en data-model.md y research.md antes de tocar el test.

**Checkpoint**: el escenario 2 queda demostrado: 30 cotizaciones con versión y 30 consultas por fecha.

---

## Phase 4: User Story 2 - Evolución coherente con el rendimiento (Priority: P2)

**Goal**: que el valor se mueva en la dirección del rendimiento: el que mejora sube en las dos estrategias y el que deja de jugar baja en MOMENTUM.

**Independent Test**: el mismo comando, con las aserciones de contracts §5 en verde.

- [X] T012 [US2] En QE2E, sobre los historiales de T009, verificar contracts §5 con etiquetas `"<LIGA> <ESTRATEGIA>"`:
  - **Premier, el que mejora:** `OFFICIAL[2].value > OFFICIAL[1].value` y `MOMENTUM[2].value > MOMENTUM[1].value` (FR-012).
  - **Bundesliga, el que deja de jugar:** `MOMENTUM[2].value < MOMENTUM[1].value` (FR-013).
  - **Los 5 jugadores:** para i = 1 y 2, `|MOMENTUM[i] / MOMENTUM[i−1] − 1| ≤ 0.15005`. Ese es ±15% más el margen de redondeo a 4 decimales, y se calcula con `MathContext.DECIMAL64` (FR-014).

  Agregar un comentario que remita a research R4 y explique por qué la primera ventana de Bundesliga está por debajo de su promedio.
- [X] T013 [US2] Correr `.\mvnw.cmd "-Dtest=QuoteEvolutionE2ETest" test`, que tiene que quedar en verde, con el mismo criterio de T011 ante fallos.

**Checkpoint**: la evolución es coherente con el rendimiento en las dos estrategias.

---

## Phase 5: User Story 3 - Ranking final y valor de mercado (Priority: P3)

**Goal**: el ranking muestra el orden final por estrategia, sin un resultado viejo de la caché, y `marketValue` sigue a la estrategia activa.

**Independent Test**: el mismo comando, con las aserciones de contracts §2.4, §2.5 y §6 en verde.

- [X] T014 [US3] En QE2E, en el lugar marcado en T008, antes de `recalculateOn(RUN_3)`: guardar la respuesta de `ranking("MOMENTUM")` y verificar que sea 200 (contracts §2.5).

  Después de cada una de las tres corridas, verificar `marketValue` (contracts §2.4, FR-018): para los 5 jugadores, `playerRepository.findById(id).orElseThrow().getMarketValue()` es igual, por `compareTo`, al `value` del último elemento de `history(player, "OFFICIAL")`. Es la única aserción contra un repositorio (plan).
- [X] T015 [US3] En QE2E, después de la corrida 3, verificar contracts §6.
  - `ranking("OFFICIAL")` y `ranking("MOMENTUM")`:
    - `totalElements` 5 y `rank` de 1 a 5;
    - `items[k].player.id` en el orden de los últimos `value` del historial de esa estrategia, de mayor a menor y con desempate por id ascendente;
    - `items[k].value` igual al último `value` del historial;
    - `strategy` la pedida y `version` 1 (FR-015).
  - `ranking(null)`: mismos `items` que con OFFICIAL (FR-016).
  - En `ranking("MOMENTUM")`, el `value` del jugador Premier es distinto del guardado en T014 e igual al de la corrida 3 (FR-017).
- [X] T016 [US3] Correr `.\mvnw.cmd "-Dtest=QuoteEvolutionE2ETest" test`, que tiene que quedar en verde, con el mismo criterio de T011 ante fallos.

**Checkpoint**: el escenario completo está verificado: historial, fechas, evolución, ranking y valor de mercado.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T017 Determinismo (SC-006): correr el test 10 veces seguidas desde `backend/` en la misma terminal con las variables de T001:
  ```powershell
  1..10 | % { .\mvnw.cmd -q "-Dtest=QuoteEvolutionE2ETest" test; $LASTEXITCODE }
  ```
  Las 10 corridas tienen que dar 0. Si hay alguna falla intermitente, investigarla antes de seguir.
- [X] T018 Correr la suite completa, `.\mvnw.cmd test`, con Memurai y la base e2e. Esperado: 0 fallas y 0 errores, con los salteados limitados a los tests de Testcontainers que necesitan Docker.
- [ ] T019 [P] Completar la sección "4. Resultados" de `specs/021-e2e-escenario-cotizaciones/quickstart.md` con:
  - los resultados de T011, T013, T016, T017 y T018, con fecha y la línea `Tests run: ...`;
  - qué valores se observaron en el log contra la tabla de research R4, sólo como referencia.

  Si se hace la demo manual de §3, registrar también sus resultados.

  Verificar en el PR que el job de CI ejecutó `QuoteEvolutionE2ETest` sin saltearlo (SC-007).
- [X] T020 Revisar con `git status` que los únicos cambios sean QE2E y `specs/021-e2e-escenario-cotizaciones/`, más `.specify/feature.json`. No debe haber cambios en código de producción ni en archivos protegidos de Entrega 1 (FR-019, plan Constitution Check). Verificar también que el test no tenga imports sin usar ni código comentado, para no sumar issues en SonarCloud (§3.2).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (T001)**: sin dependencias.
- **Foundational (T002–T006)**: dependen de T001. Van en secuencia porque todas editan QE2E. T003 a T006 pueden escribirse en cualquier orden después de T002.
- **US1 (T007–T011)**: depende de Foundational.
- **US2 (T012–T013)**: depende de T009, porque usa los historiales guardados.
- **US3 (T014–T016)**: depende de T008, por la consulta previa y `marketValue` por corrida, y de T009 por los historiales.
- **Polish (T017–T020)**: depende de las tres historias.

### User Story Dependencies

- **US1 (P1)**: independiente; es el MVP.
- **US2 (P2)** y **US3 (P3)**: usan las corridas y los historiales de US1, porque el escenario es uno solo. Entre sí son independientes y se pueden hacer en cualquier orden.

### Within Each User Story

- Escribir las aserciones, correr el test y analizar los fallos contra research R4 antes de cambiar datos o aserciones.

### Parallel Opportunities

- Casi todo edita QE2E, así que no hay paralelismo real en el código.
- T019, que es documentación, puede ir en paralelo con T017 y T018.
- US2 y US3 se pueden repartir entre dos personas si se coordinan los cambios en el mismo archivo: son bloques distintos al final del método.

---

## Parallel Example: Phase 6

```text
Task: "T017 Determinismo: 10 corridas seguidas"
Task: "T019 Completar Resultados en quickstart.md"   # [P]: otro archivo
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. T001: confirmar que los e2e corren y no se saltean.
2. T002–T006: esqueleto, reloj, ADMIN, datos y helpers.
3. T007–T011: tres corridas, historial y vigente por fecha. Con esto el escenario 2 de la consigna queda demostrado.
4. **STOP and VALIDATE**: el test en verde con `Skipped: 0`.

### Incremental Delivery

1. Con el MVP (US1) ya hay evidencia del escenario 2.
2. US2 agrega la verificación de que el valor sigue al rendimiento.
3. US3 agrega el ranking, la invalidación de la caché y `marketValue`.
4. Polish: determinismo, suite completa, resultados y revisión de alcance.

---

## Notes

- Las aserciones de valores no son exactas, salvo el 1.0000 inicial de MOMENTUM. La tabla de research R4 es sólo una referencia para diagnosticar.
- Las comparaciones decimales se hacen siempre con `compareTo`.
- El equipo hace los commits.
