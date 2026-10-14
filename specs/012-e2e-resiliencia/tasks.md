---

description: "Tareas de implementación para tests e2e de resiliencia ante fuentes externas caídas"
---

# Tasks: Resiliencia e2e de fuentes externas

**Input**: Design documents from `/specs/012-e2e-resiliencia/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Esta feature consiste en tests e2e; los siete métodos de `ExternalSourcesDownE2ETest` son entregables obligatorios, no pruebas opcionales de una implementación productiva nueva.

**Organization**: Las tareas se agrupan por historia. El helper compartido se implementa en Fundacional porque bloquea US1 y US2; US4 completa su contrato de reutilización después de que dos historias lo consuman.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo porque usa otro archivo y no depende de trabajo incompleto.
- **[Story]**: Historia trazada desde `spec.md` (`US1`–`US4`).
- Cada tarea incluye la ruta exacta del archivo que crea, modifica, inspecciona o ejecuta.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirmar el punto de partida y fijar el límite de sólo dos archivos Java nuevos.

- [X] T001 Verificar que `backend/src/test/java/com/example/demo/support/E2ETest.java`, `backend/src/test/java/com/example/demo/support/E2EDatabaseCleaner.java`, `backend/src/test/java/com/example/demo/e2e/SmokeE2ETest.java`, `backend/src/test/resources/application-e2e.properties` y `backend/src/main/java/com/example/demo/config/CacheConfig.java` ofrecen puerto aleatorio, limpieza PostgreSQL/Redis, login por HTTP, fuentes en `127.0.0.1:1` y caché tipado; registrar cualquier impedimento protegido según FR-018 antes de editarlo

**Checkpoint**: El perfil e2e y la caché existente cubren todas las precondiciones sin cambios productivos ni dependencias nuevas.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Crear el helper común y el armazón HTTP/autenticado que necesitan todas las historias.

**⚠️ CRITICAL**: Ninguna historia puede implementarse hasta completar esta fase.

- [X] T002 Crear `backend/src/test/java/com/example/demo/support/E2EFixtures.java` como `@Component` público disponible sólo en test scope, con `PlayerRepository` encapsulado mediante inyección por constructor y `createPlayersFromFiveLeagues()` que ejecute `saveAllAndFlush` para exactamente cinco jugadores: un valor por cada `League`, external IDs únicos `91001`–`91005`, nombres/equipos deterministas, `marketValue` positivo, nacionalidad y edad válidas, y al menos una `Position` por jugador
- [X] T003 Completar `backend/src/test/java/com/example/demo/support/E2EFixtures.java` con el fixture `LA_LIGA` multi-posición `DEFENDER` + `MIDFIELDER`, cobertura total de las cuatro posiciones y un `FixtureSet` de listas inmutables con `all()`, `forLeague(League)`, `forTeam(String)` y `withPosition(Position)` que opere en memoria, nunca retorne `null` y falle claramente ante coincidencias ausentes o ambiguas
- [X] T004 [P] Crear `backend/src/test/java/com/example/demo/e2e/ExternalSourcesDownE2ETest.java` con `@E2ETest`, `@LocalServerPort`, `RestClient`, inyección de `E2EFixtures` y `CacheManager` sin exponer `PlayerRepository`, un `@MockitoSpyBean` sobre el `FootballDataAdapter` real, y `@BeforeEach` que registre e inicie sesión por HTTP para conservar un Bearer JWT no vacío por método
- [X] T005 Integrar en `backend/src/test/java/com/example/demo/e2e/ExternalSourcesDownE2ETest.java` el `E2EFixtures` inyectado, helpers de GET/POST autenticados, deserialización a `List<PlayerResponse>` y `PlayerSyncResponse`, orden estable por `externalId` y un wrapper no preemptivo `assertTimeout(...)` que mida sólo cada intercambio de resiliencia con GET ≤100 ms tras calentamiento y POST sync ≤500 ms, y exija HTTP 200

**Checkpoint**: El helper persiste datos visibles entre hilos y la clase e2e puede emitir requests Bearer cronometrados sin duplicar preparación.

---

## Phase 3: User Story 1 — Consultar el catálogo durante una caída externa (Priority: P1) 🎯 MVP

**Goal**: Demostrar que las lecturas sin filtros, con cada filtro individual y con los tres filtros combinados usan exclusivamente los cinco jugadores locales mientras Football-Data y WhoScored están caídos.

**Independent Test**: Ejecutar los cinco métodos GET de `ExternalSourcesDownE2ETest`; cada uno debe responder 200 en ≤100 ms y devolver exactamente los external IDs esperados sin asumir orden.

### Tests and evidence for User Story 1

- [X] T006 [US1] Implementar `returnsLocalPlayersWithoutFiltersWhenSourcesAreDown` en `backend/src/test/java/com/example/demo/e2e/ExternalSourcesDownE2ETest.java`: antes de crear fixtures ejecutar GET `/players` y exigir 200 + colección vacía en ≤100 ms; luego crear el `FixtureSet`, repetir GET `/players`, exigir 200 en ≤100 ms y comparar los cinco `PlayerResponse` completos por `externalId`
- [X] T007 [US1] Implementar `filtersLocalPlayersByLeagueWhenSourcesAreDown` en `backend/src/test/java/com/example/demo/e2e/ExternalSourcesDownE2ETest.java`: GET `/players?league=PREMIER_LEAGUE`, 200 en ≤100 ms y sólo external ID `91001`
- [X] T008 [US1] Implementar `filtersLocalPlayersByTeamWhenSourcesAreDown` en `backend/src/test/java/com/example/demo/e2e/ExternalSourcesDownE2ETest.java`: GET `/players?team=Real%20Madrid` construido por URI segura, 200 en ≤100 ms y sólo external ID `91002`; después consultar un equipo inexistente y exigir 200 + colección vacía en ≤100 ms
- [X] T009 [US1] Implementar `filtersLocalPlayersByPositionWhenSourcesAreDown` en `backend/src/test/java/com/example/demo/e2e/ExternalSourcesDownE2ETest.java`: GET `/players?position=MIDFIELDER`, 200 en ≤100 ms y exactamente external IDs `91002` y `91004`, incluida la pertenencia multi-posición
- [X] T010 [US1] Implementar `combinesLeagueTeamAndPositionWhenSourcesAreDown` en `backend/src/test/java/com/example/demo/e2e/ExternalSourcesDownE2ETest.java`: combinar `PREMIER_LEAGUE`, `Arsenal FC` y `FORWARD`, exigir 200 en ≤100 ms y únicamente external ID `91001`
- [X] T011 [US1] Ejecutar los cinco métodos de US1 definidos en `backend/src/test/java/com/example/demo/e2e/ExternalSourcesDownE2ETest.java` con el selector Maven de métodos y corregir únicamente los dos archivos Java nuevos hasta que todos prueben datos locales, filtros exactos, ausencia de 5xx y límite de 100 ms

**Checkpoint**: US1 es demostrable por sí sola con ambas fuentes externas inaccesibles y constituye el MVP.

---

## Phase 4: User Story 2 — Conservar el catálogo ante una sincronización fallida (Priority: P1)

**Goal**: Demostrar que `POST /players/sync` informa `FAILED` por HTTP 200 y no crea, elimina ni modifica jugadores existentes cuando Football-Data está caído y Redis no tiene respaldo.

**Independent Test**: Ejecutar sólo `failedSyncReturnsFailedAndPreservesExistingPlayers`; debe comparar instantáneas HTTP completas anterior/posterior, validar contadores, mantener cada GET en ≤100 ms tras calentamiento y el POST sync en ≤500 ms.

### Tests and evidence for User Story 2

- [X] T012 [US2] Implementar `failedSyncReturnsFailedAndPreservesExistingPlayers` en `backend/src/test/java/com/example/demo/e2e/ExternalSourcesDownE2ETest.java`: crear las cinco fixtures, capturar GET ordenado previo, mantener Redis vacío, ejecutar POST `/players/sync`, exigir HTTP 200 + `FAILED` + `processed=0` + `failedLeagues=5` + `failedPlayers=0`, capturar GET posterior y comparar igualdad de todos los campos incluidos IDs y posiciones; cronometrar cada GET a ≤100 ms tras calentamiento y el POST a ≤500 ms
- [X] T013 [US2] Ejecutar el método US2 de `backend/src/test/java/com/example/demo/e2e/ExternalSourcesDownE2ETest.java` de forma aislada y dos veces consecutivas para comprobar limpieza por método, catálogo idéntico y ausencia de dependencia del orden o de IDs generados

**Checkpoint**: US2 prueba de forma independiente la preservación exacta del último catálogo válido.

---

## Phase 5: User Story 3 — Reutilizar datos externos cacheados (Priority: P2)

**Goal**: Demostrar que cinco entradas Redis vigentes satisfacen la sincronización completa con cero acceso a Football-Data.

**Independent Test**: Ejecutar sólo `syncUsesTypedRedisCacheWithoutCallingFootballData`; debe producir `COMPLETED`, cinco jugadores y cero ligas/jugadores fallidos con el POST en ≤500 ms y el GET posterior en ≤100 ms tras calentamiento, con la fuente configurada en el destino inválido.

### Tests and evidence for User Story 3

- [X] T014 [US3] Agregar en `backend/src/test/java/com/example/demo/e2e/ExternalSourcesDownE2ETest.java` builders privados de `FootballDataResponse` para `PL`, `PD`, `SA`, `BL1` y `FL1`, cada uno con un `Team` no vacío y un `SquadMember` de ID Long único, nombre no vacío, posición reconocida por `FootballDataMappings`, fecha de nacimiento anterior a hoy y nacionalidad no vacía
- [X] T015 [US3] Implementar `syncUsesTypedRedisCacheWithoutCallingFootballData` en `backend/src/test/java/com/example/demo/e2e/ExternalSourcesDownE2ETest.java`: obtener `CacheConfig.COMPETITION_TEAMS_CACHE` del `CacheManager`, escribir los cinco `FootballDataResponse` mediante `Cache.put` sin `RedisTemplate`, bytes, JSON ni serializer alternativo, reiniciar el registro del `@MockitoSpyBean`, ejecutar POST `/players/sync` y exigir HTTP 200 + `COMPLETED` + `processed=5` + `failedLeagues=0` + `failedPlayers=0` en ≤500 ms + `verifyNoInteractions(footballDataAdapter)`; luego GET en ≤100 ms tras calentamiento y comparar los cinco datos cacheados persistidos
- [X] T016 [US3] Ejecutar dos veces `syncUsesTypedRedisCacheWithoutCallingFootballData` de `backend/src/test/java/com/example/demo/e2e/ExternalSourcesDownE2ETest.java` y confirmar en ambas ejecuciones `COMPLETED`, cinco procesados, cero fallas, cero interacciones con `FootballDataAdapter` y limpieza Redis efectiva entre métodos

**Checkpoint**: US3 demuestra Redis real, serialización tipada y construcción de la base desde datos externos simulados para §1.3, §3.3.1 y §7.6.

---

## Phase 6: User Story 4 — Reutilizar datos de prueba en futuros e2e (Priority: P3)

**Goal**: Dejar un helper estable y explícitamente reutilizable por las features 017, 018 y 021, ya probado en más de un escenario.

**Independent Test**: Inspeccionar y ejecutar la clase e2e completa; al menos US1 y US2 deben crear sus datos mediante la misma API pública y no contener builders de `Player` duplicados.

### Reusability for User Story 4

- [X] T017 [P] [US4] Documentar y finalizar el contrato público en `backend/src/test/java/com/example/demo/support/E2EFixtures.java`: `@Component` exclusivo de test, `PlayerRepository` encapsulado y nunca requerido por consumidores, llamada única después de limpieza, external IDs y combinación estable, listas inmutables, IDs PostgreSQL no estables, propagación de errores y reutilización prevista por features 017, 018 y 021
- [X] T018 [US4] Revisar `backend/src/test/java/com/example/demo/e2e/ExternalSourcesDownE2ETest.java` para que todos los escenarios persistidos de US1 y US2 usen exclusivamente el `E2EFixtures` inyectado y consulten expectativas mediante `FixtureSet`, sin inyectar `PlayerRepository` ni duplicar builders o queries de fixtures en la clase
- [X] T019 [US4] Ejecutar `backend/src/test/java/com/example/demo/e2e/ExternalSourcesDownE2ETest.java` completo y verificar que el mismo helper sirve a por lo menos dos escenarios, crea exactamente una fixture por `League`, cubre las cuatro `Position` y conserva la combinación única Premier League + Arsenal FC + FORWARD

**Checkpoint**: US4 entrega el helper reutilizable como `@Component` de test, sin modificar `@E2ETest` ni agregar una tercera clase Java.

---

## Phase 7: Polish & Cross-Cutting Validation

**Purpose**: Validar compilación, aislamiento, regresión, umbrales y protección de Entrega 1.

- [X] T020 Compilar todo el test scope mediante el Maven Wrapper de `backend/pom.xml` para detectar incompatibilidades de Java 17, DTOs, genéricos de `RestClient`, AssertJ y API de caché antes de ejecutar infraestructura
- [X] T021 Ejecutar dos veces el comando de clase de `specs/012-e2e-resiliencia/quickstart.md` y confirmar siete tests aprobados, limpieza PostgreSQL/Redis entre métodos, respuestas 200, GET ≤100 ms tras calentamiento y sync ≤500 ms
- [X] T022 Ejecutar el grupo completo `e2e` documentado en `specs/012-e2e-resiliencia/quickstart.md` y confirmar convivencia de `ExternalSourcesDownE2ETest` con `SmokeE2ETest`, `E2EProfileTest` y `E2EDatabaseCleanerE2ETest`
- [X] T023 Ejecutar `verify` según `specs/012-e2e-resiliencia/quickstart.md` y confirmar build completo exitoso, cero regresiones no-e2e y reporte JaCoCo generado por el ciclo existente
- [X] T024 Verificar con `git diff --exit-code` que permanecen sin cambios `backend/pom.xml`, `.github/workflows/ci.yml`, `backend/src/main/resources/application.properties`, `backend/src/test/resources/application.properties`, `backend/src/test/resources/application-e2e.properties`, `backend/src/main/java/com/example/demo/config/CacheConfig.java`, `backend/src/main/java/com/example/demo/config/SecurityConfig.java`, `backend/src/main/java/com/example/demo/filter/` y `backend/src/main/java/com/example/demo/controller/`; si cualquier cambio resulta imprescindible, detenerse y aplicar FR-018

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Sin dependencias; confirma que no hace falta tocar archivos protegidos.
- **Foundational (Phase 2)**: Depende de Setup y bloquea todas las historias.
- **US1 (Phase 3)**: Depende de Foundational; es el MVP.
- **US2 (Phase 4)**: Depende de Foundational y puede implementarse sin US1, aunque comparte la misma clase Java.
- **US3 (Phase 5)**: Depende de Foundational y puede implementarse sin US1/US2; usa la base vacía y su propio estado Redis.
- **US4 (Phase 6)**: Su helper base nace en Foundational; T018–T019 dependen de al menos dos escenarios consumidores, concretamente US1 y US2.
- **Polish (Phase 7)**: Depende de todas las historias seleccionadas.

### User Story Dependency Graph

```text
Setup
  └── Foundational: E2EFixtures + HTTP/auth/timing
       ├── US1: lecturas locales (MVP)
       ├── US2: sync FAILED no destructivo
       ├── US3: sync COMPLETED desde Redis
       └── US4 base: helper disponible

US1 + US2 ──> US4 cierre: reutilización demostrada
US1 + US2 + US3 + US4 ──> Polish / verify
```

### Within Each User Story

- Preparar sólo el estado requerido después de la limpieza común.
- Ejecutar el request HTTP real con Bearer y timeout individual.
- Exigir primero HTTP 200; luego validar payload y estado funcional.
- Comparar colecciones sin depender del orden.
- Completar y ejecutar el escenario aislado antes de avanzar.

### Parallel Opportunities

- T002–T003 (`E2EFixtures.java`) y T004 (`ExternalSourcesDownE2ETest.java`) pueden iniciarse en paralelo; T005 espera ambos lados.
- Después de Foundational, US1, US2 y US3 son conceptualmente independientes, pero editan `ExternalSourcesDownE2ETest.java`; no deben aplicarse en paralelo sobre el mismo worktree.
- T017 está marcado [P] porque sólo documenta/finaliza `E2EFixtures.java` después de Foundational y puede ejecutarse mientras otro trabajo modifica la clase e2e.
- Las ejecuciones que comparten PostgreSQL/Redis no se paralelizan.

---

## Parallel Example: Foundational

```text
Task T002/T003: "Crear E2EFixtures y FixtureSet en backend/src/test/java/com/example/demo/support/E2EFixtures.java"
Task T004: "Crear el armazón HTTP autenticado en backend/src/test/java/com/example/demo/e2e/ExternalSourcesDownE2ETest.java"

Después de ambos: Task T005 integra helper, DTOs, orden y timeout.
```

## Parallel Example: User Story 4

```text
Task T017: "Finalizar contrato y documentación en backend/src/test/java/com/example/demo/support/E2EFixtures.java"
En paralelo, tras Foundational: avanzar escenarios HTTP en backend/src/test/java/com/example/demo/e2e/ExternalSourcesDownE2ETest.java
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Completar T001–T005 (Setup + Foundational).
2. Completar T006–T011 (US1).
3. Detenerse y ejecutar los cinco GET de US1 de forma aislada.
4. Entregar una primera evidencia de §1.3 y §7.6: catálogo local utilizable con ambas fuentes caídas.

### Incremental Delivery

1. Setup + Foundational → fixtures y cliente e2e disponibles.
2. US1 → lecturas resilientes y filtros (MVP).
3. US2 → protección del catálogo ante sincronización fallida.
4. US3 → fallback Redis tipado y construcción desde datos cacheados.
5. US4 → contrato reusable consolidado para features futuras.
6. Polish → clase repetida, grupo e2e, `verify` y diff protegido.

### Team Strategy

Con dos personas, una puede implementar `E2EFixtures.java` mientras la otra crea el armazón de `ExternalSourcesDownE2ETest.java`. Después de T005, las historias deben integrarse secuencialmente en esa clase compartida para evitar conflictos y ejecuciones concurrentes sobre la misma infraestructura.

---

## Notes

- `[P]` sólo marca tareas realmente separables por archivo y dependencia.
- No agregar dependencias, clases productivas, configuración ni una tercera clase de test.
- No modificar `CacheConfig`; escribir datos a través del `Cache` que expone su `CacheManager`.
- `@MockitoSpyBean` observa el `FootballDataAdapter` real; no lo sustituye por un mock ni habilita accesos externos.
- Registro/login y preparación de datos quedan fuera de los límites; cada GET se mide individualmente en ≤100 ms tras calentamiento y cada POST sync en ≤500 ms.
- `FAILED` y `COMPLETED` son estados del payload sobre HTTP 200.
- Cualquier necesidad de tocar un archivo protegido detiene la implementación hasta resolver FR-018.
