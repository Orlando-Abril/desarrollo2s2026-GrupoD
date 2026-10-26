---

description: "Tareas de implementación para el ranking de jugadores por cotización"
---

# Tasks: Ranking de jugadores por cotización

**Input**: Design documents from `/specs/018-ranking-jugadores/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/ranking-api.yaml](contracts/ranking-api.yaml), [quickstart.md](quickstart.md)

**Tests**: La especificación exige tests MockMvc, repositorio H2/PostgreSQL, seguridad, caché e invalidación. En cada historia se escriben primero y deben fallar antes de implementar.

**Organization**: Las tareas se agrupan por historia para entregar y probar incrementos independientes. No modificar `PlayerController` ni `GlobalExceptionHandler`; cualquier índice o cambio adicional de Entrega 1 requiere autorización.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo porque usa archivos distintos y no depende de otra tarea incompleta del mismo grupo.
- **[Story]**: Historia de usuario trazable (`US1`..`US4`).
- Todas las tareas incluyen rutas concretas.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Preparar fixtures reutilizables para los tests con datos persistidos manualmente.

- [X] T001 Inspeccionar anotaciones de entidades, migraciones y el esquema PostgreSQL efectivo para verificar índices de liga, equipo, posición e historial; registrar la evidencia en `specs/018-ranking-jugadores/quickstart.md` y, si falta alguno, dejar explícita la solicitud de autorización sin agregar índices ni modificar esquema
- [X] T002 Crear builders deterministas para jugadores y cotizaciones, parametrizados por repositorio y sin adapters externos, en `backend/src/test/java/com/example/demo/support/RankingFixtures.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Definir los tipos compartidos por consulta, servicio, endpoint y caché.

**⚠️ CRITICAL**: Ninguna historia comienza hasta completar esta fase.

- [X] T003 [P] Crear la proyección `RankingRowProjection` con `rank`, `playerId`, `name`, `team`, `league`, `currentValue`, `previousValue`, `strategy` y `version` en `backend/src/main/java/com/example/demo/repository/RankingRowProjection.java`
- [X] T004 [P] Crear `RankingEntryResponse` y su jugador anidado inmutable: `rank >= 1`; jugador con `id`, `name`, `team`, `league` y copia de todas las `positions`; `value` sin pérdida; `percentageChange` siempre serializado, required y nullable, calculado con escala 2/HALF_UP; `strategy`; `version >= 1`, en `backend/src/main/java/com/example/demo/dto/quote/RankingEntryResponse.java`
- [X] T005 [P] Crear `RankingPageResponse` inmutable con `items`, `page >= 0`, `size` entre 1 y 100, `totalElements >= 0` y `totalPages = ceil(totalElements/size)` o 0, en `backend/src/main/java/com/example/demo/dto/quote/RankingPageResponse.java`

**Checkpoint**: Tipos compartidos listos; las historias pueden avanzar sobre contratos estables.

---

## Phase 3: User Story 1 - Consultar el ranking actual (Priority: P1) 🎯 MVP

**Goal**: Consultar la estrategia activa y recibir una sola entrada por jugador cotizado, ordenada por última cotización descendente y desempate por id ascendente, con puestos consecutivos.

**Independent Test**: Cargar cuatro jugadores con valores actuales 8, 15, 4 y 10, múltiples cotizaciones y un jugador sin cotización; `GET /players/ranking` autenticado debe devolver 15/10/8/4 con ranks 1..4, sin duplicados ni jugador no cotizado.

### Tests for User Story 1

> Escribir estos tests primero y confirmar que fallan.

- [X] T006 [P] [US1] Agregar tests H2/PG mode para última cotización por jugador, empate temporal por id, exclusión sin cotización, orden `quote_value DESC, player_id ASC` y puestos globales en `backend/src/test/java/com/example/demo/repository/PlayerQuoteRepositoryTest.java`
- [X] T007 [P] [US1] Crear test MockMvc con `@UnitTestProfile`, autenticación USER y datos manuales para estrategia activa, orden, desempate, forma del DTO y respuesta vacía en `backend/src/test/java/com/example/demo/controller/RankingControllerTest.java`

### Implementation for User Story 1

- [X] T008 [US1] Agregar al `PlayerQuoteRepository` la consulta nativa base con CTE, `ROW_NUMBER` y `LEAD` particionados por jugador/estrategia, orden histórico `calculated_at DESC, id DESC`, ranking `quote_value DESC, player_id ASC`, `Pageable` sin sort y `countQuery` explícito en `backend/src/main/java/com/example/demo/repository/PlayerQuoteRepository.java`
- [X] T009 [US1] Implementar `QuoteQueryService.ranking(strategy, league, position, pageable)` para resolver la estrategia activa, ejecutar la consulta, cargar jugadores/posiciones de la página en bloque, preservar el orden y mapear `RankingPageResponse` en `backend/src/main/java/com/example/demo/service/valuation/QuoteQueryService.java`
- [X] T010 [US1] Agregar `GET /players/ranking` con defaults `page=0` y `size=20` al `QuoteController`, sin cambiar `PlayerController`, y verificar que convive con `/players/{id}/quotes` en `backend/src/main/java/com/example/demo/controller/QuoteController.java`
- [X] T011 [US1] Ejecutar los tests US1 desde `backend/pom.xml` y corregir sólo los archivos de US1 hasta validar ranking activo, vacío, orden, unicidad y rutas coexistentes

**Checkpoint**: MVP funcional y autenticado para la estrategia activa, independientemente de filtros/caché.

---

## Phase 4: User Story 2 - Comparar y filtrar el ranking (Priority: P1)

**Goal**: Seleccionar estrategia, filtrar por liga/posición, paginar con rank global al subconjunto y calcular la variación contra la cotización inmediatamente anterior.

**Independent Test**: Con estrategias, ligas, posiciones e historiales mezclados, verificar filtros AND, ranks recalculados, posiciones 21..40 para `page=1&size=20`, metadatos y variaciones positivas, negativas, cero y nulas.

### Tests for User Story 2

> Escribir estos tests primero y confirmar que fallan.

- [X] T012 [P] [US2] Ampliar tests H2 para estrategia explícita, filtros exactos de liga/posición mediante `EXISTS`, jugador multiposición sin duplicados, anterior entre versiones, paginación/count y página fuera de rango en `backend/src/test/java/com/example/demo/repository/PlayerQuoteRepositoryTest.java`
- [X] T013 [P] [US2] Ampliar MockMvc con datos manuales para filtros conjuntos, estrategia MOMENTUM, página 1/size 20, metadatos y variaciones `25.00`, negativas, `0.00` y null sin anterior/anterior cero en `backend/src/test/java/com/example/demo/controller/RankingControllerTest.java`
- [X] T014 [P] [US2] Crear E2E de la consulta nativa en PostgreSQL real con el mismo orden, anterior, filtros, multiposición, rank y paginación que H2, y confirmar cero llamadas a fuentes externas, en `backend/src/test/java/com/example/demo/e2e/RankingQueryE2ETest.java`

### Implementation for User Story 2

- [X] T015 [US2] Completar el SQL y `countQuery` con filtros nullable exactos de estrategia, liga y posición aplicados después de formar la serie histórica y antes del ranking global, sin unir `player_positions` a la página, en `backend/src/main/java/com/example/demo/repository/PlayerQuoteRepository.java`
- [X] T016 [US2] Completar el servicio con resolución de estrategia efectiva, reglas de negocio, metadatos de página y cálculo determinista `actual.subtract(anterior).multiply(100).divide(anterior, 2, HALF_UP)`, manteniendo null sin anterior o con anterior cero, en `backend/src/main/java/com/example/demo/service/valuation/QuoteQueryService.java`
- [X] T017 [US2] Inspeccionar las convenciones existentes y exponer `strategy`, `league`, `position` y paginación mediante el mecanismo estándar `Pageable` de Spring salvo patrón local distinto, preservando el orden fijo y dejando al controller sólo el contrato HTTP, en `backend/src/main/java/com/example/demo/controller/QuoteController.java`
- [ ] T018 [US2] Ejecutar suites H2 y PostgreSQL definidas para US2 desde `backend/pom.xml` y corregir únicamente diferencias funcionales portables, sin introducir SQL específico de un solo motor

**Checkpoint**: Ranking comparativo completo y verificable en H2 y PostgreSQL.

---

## Phase 5: User Story 3 - Recibir un contrato seguro y previsible (Priority: P2)

**Goal**: Rechazar entradas/credenciales inválidas con el formato común y publicar un contrato OpenAPI completo con JWT y API key como alternativas.

**Independent Test**: Ejercitar sin credenciales, token/key inválidos, USER por JWT y API key, filtros vacíos/desconocidos y límites de página; luego inspeccionar `/v3/api-docs` para parámetros, DTOs, 200/400/401 y seguridad OR.

### Tests for User Story 3

> Escribir estos tests primero y confirmar que fallan.

- [X] T019 [P] [US3] Ampliar MockMvc para comprobar 401 sin credenciales y con token o API key inválidos; 200 para `Role.USER` con JWT válido y con API key válida; y 400 `unknown_strategy`/`validation_error` para parámetros vacíos, desconocidos, no numéricos o fuera de rango, en `backend/src/test/java/com/example/demo/controller/RankingControllerTest.java`
- [X] T020 [P] [US3] Ampliar tests de `/v3/api-docs` para `/players/ranking`, cinco parámetros con defaults/límites, orden, esquemas paginados/campos —incluido `percentageChange` required y nullable—, respuestas 200/400/401 y requisitos alternativos `bearerAuth`/`apiKeyAuth` en `backend/src/test/java/com/example/demo/config/QuoteOpenApiTest.java`

### Implementation for User Story 3

- [X] T021 [US3] Implementar validación del contrato HTTP según las convenciones existentes para identificar parámetro, valor y códigos válidos, sin duplicar la resolución de estrategia efectiva ni reglas de negocio del servicio y sin modificar `GlobalExceptionHandler`, en `backend/src/main/java/com/example/demo/controller/QuoteController.java`
- [X] T022 [US3] Anotar operación, parámetros, orden/desempate, `RankingPageResponse`, errores 200/400/401, correlation id y seguridad JWT/API key alternativa; declarar `percentageChange` required, nullable y siempre serializado incluso en null, en `backend/src/main/java/com/example/demo/controller/QuoteController.java`, `backend/src/main/java/com/example/demo/dto/quote/RankingEntryResponse.java` y `backend/src/main/java/com/example/demo/dto/quote/RankingPageResponse.java`
- [X] T023 [US3] Ejecutar tests de seguridad y OpenAPI desde `backend/pom.xml`, confirmando que `SecurityConfig` protege ranking mediante `anyRequest().authenticated()` sin agregar restricción de rol ni modificar rutas públicas

**Checkpoint**: Contrato validado, seguro y descubrible sin leer implementación.

---

## Phase 6: User Story 4 - Mantener resultados rápidos y frescos (Priority: P2)

**Goal**: Reutilizar por una hora consultas equivalentes, aislar claves distintas y vaciar todo el ranking al concluir cualquier recálculo aceptado.

**Independent Test**: Una segunda llamada equivalente no consulta repositorios; cambiar cada componente genera miss; un evento y recálculos exitosos/fallidos eliminan todas las entradas; fallas Redis no impiden leer ni alteran el desenlace.

### Tests for User Story 4

> Escribir estos tests primero y confirmar que fallan.

- [X] T024 [P] [US4] Ampliar `CacheConfigTest` para round-trip tipado de `RankingPageResponse` y TTL exacto de una hora del caché `quotes-ranking` en `backend/src/test/java/com/example/demo/config/CacheConfigTest.java`
- [X] T025 [P] [US4] Crear test con `@MockitoSpyBean PlayerQuoteRepository` que pruebe hit sin segunda consulta, equivalencia entre estrategia omitida/activa explícita, miss por cada componente de clave y get/put fail-open en `backend/src/test/java/com/example/demo/service/valuation/QuoteQueryServiceCacheTest.java`
- [X] T026 [P] [US4] Crear tests que precarguen varias claves, publiquen `QuoteRunCompletedEvent`, verifiquen limpieza total síncrona y tolerancia a `CacheManager`/`Cache.clear()` fallidos en `backend/src/test/java/com/example/demo/service/valuation/QuoteCacheEvictorTest.java`
- [X] T027 [P] [US4] Ampliar tests del recálculo para evento en éxito y `RecalculationFailedException`, publicación antes de liberar la guardia, y ausencia de evento para estrategia inválida o intento 409, en `backend/src/test/java/com/example/demo/service/valuation/QuoteCalculationServiceTest.java`
- [X] T028 [US4] Crear un test integrado que caliente `quotes-ranking`, ejecute un recálculo exitoso y otro fallido, verifique invalidación al terminar ambos y vuelva a consultar para obtener los datos persistidos vigentes, en `backend/src/test/java/com/example/demo/service/valuation/QuoteRankingCacheIntegrationTest.java`

### Implementation for User Story 4

- [X] T029 [P] [US4] Registrar `QUOTES_RANKING_CACHE = "quotes-ranking"` con TTL `Duration.ofHours(1)` y `JacksonJsonRedisSerializer<RankingPageResponse>` en `backend/src/main/java/com/example/demo/config/CacheConfig.java`
- [X] T030 [US4] Agregar al ranking get/put fail-open con logs estructurados y clave canónica `strategy|league|-|position|-|page|size`, usando siempre la estrategia efectiva y sin incluir sort, en `backend/src/main/java/com/example/demo/service/valuation/QuoteQueryService.java`
- [X] T031 [P] [US4] Verificar nuevamente si `QuoteRunCompletedEvent` ya existe y cuál es su contrato; reutilizarlo si es compatible o, sólo si sigue ausente, crear `QuoteRunCompletedEvent(UUID runId)` en `backend/src/main/java/com/example/demo/service/valuation/QuoteRunCompletedEvent.java`
- [X] T032 [US4] Crear `QuoteCacheEvictor` con listener síncrono que limpie todas las entradas de `quotes-ranking` y capture/loguee fallas sin propagarlas en `backend/src/main/java/com/example/demo/service/valuation/QuoteCacheEvictor.java`
- [X] T033 [US4] Antes de modificar la publicación, confirmar el contrato verificado en T031; luego inyectar `ApplicationEventPublisher` y publicar el evento en el `finally` de cada corrida aceptada, antes de liberar la guardia y sin publicarlo por validación/409, preservando la excepción original, en `backend/src/main/java/com/example/demo/service/valuation/QuoteCalculationService.java`
- [X] T034 [US4] Ejecutar tests de caché/evento desde `backend/pom.xml` y confirmar hit sin repositorio, separación completa de claves, TTL, recorrido integrado tras recálculo exitoso/fallido y comportamiento fail-open

**Checkpoint**: Ranking rápido durante una hora y fresco inmediatamente después de cada corrida terminal.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Verificación de portabilidad, rendimiento, regresiones y documentación final.

- [X] T035 [P] Reconciliar el contrato implementado y ejemplos finales con `specs/018-ranking-jugadores/contracts/ranking-api.yaml` y `specs/018-ranking-jugadores/quickstart.md`, sin duplicar cuerpos de implementación
- [ ] T036 Ejecutar sobre exactamente 2.500 jugadores 20 solicitudes HTTP completas y consecutivas de la misma página —la primera puede ser cache miss y las 19 restantes cache hits—, registrar los 20 tiempos y comprobar que al menos 19 sean menores a un segundo; ejecutar además `EXPLAIN (ANALYZE, BUFFERS)` para la consulta sin caché y documentar toda la evidencia en `specs/018-ranking-jugadores/quickstart.md`, solicitando autorización antes de cualquier cambio de índice
- [ ] T037 Ejecutar la suite completa desde `backend/pom.xml`, verificar `git diff` para confirmar que `backend/src/main/java/com/example/demo/controller/PlayerController.java` y `backend/src/main/java/com/example/demo/exception/GlobalExceptionHandler.java` permanecen intactos, y resolver regresiones antes de cerrar
- [ ] T038 Verificar al finalizar la implementación que el workflow de GitHub Actions compile y ejecute toda la suite con estado SUCCESS, registrando la ejecución en `specs/018-ranking-jugadores/quickstart.md`
- [ ] T039 Verificar al finalizar la implementación que el Quality Gate de SonarCloud quede PASSED, sin vulnerabilidades nuevas y con menos de 10 issues menores, registrando el resultado en `specs/018-ranking-jugadores/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias.
- **Foundational (Phase 2)**: depende de completar la inspección T001 y los fixtures T002; T001 puede dejar una solicitud de autorización pendiente sin agregar índices, pero su evidencia debe quedar documentada antes de implementar.
- **US1 (Phase 3)**: depende de Phase 2; es el MVP.
- **US2 (Phase 4)**: depende de US1 porque amplía la misma consulta, servicio y endpoint.
- **US3 (Phase 5)**: depende del endpoint completo de US2; no depende de caché.
- **US4 (Phase 6)**: depende de US2; puede avanzar en paralelo con US3 después de Phase 4.
- **Polish (Phase 7)**: depende de las historias incluidas en la entrega.

### User Story Dependency Graph

```text
Setup -> Foundation -> US1 -> US2 -> US3
                              └----> US4
US3 + US4 -> Polish
```

### Within Each User Story

- Escribir y ejecutar los tests indicados antes de implementación; confirmar fallo relevante.
- Proyección/DTO preceden repositorio; repositorio precede servicio; servicio precede endpoint.
- En US4, T029 y T031 pueden avanzar en paralelo; T030 depende de T029, T032 depende de T029+T031 y T033 depende de T031. T028 se escribe antes de esas implementaciones y se valida al completar T034.
- Cada checkpoint exige que los tests anteriores continúen verdes.

### Parallel Opportunities

- Foundation: T003, T004 y T005.
- US1: T006 y T007.
- US2: T012, T013 y T014.
- US3: T019 y T020.
- US4: T024, T025, T026 y T027; luego T029 y T031.
- Después de US2: US3 y US4 pueden ejecutarse en paralelo por equipos distintos, coordinando sólo los archivos compartidos indicados.

---

## Parallel Examples

### User Story 1

```text
Task T006: tests de repositorio en PlayerQuoteRepositoryTest.java
Task T007: tests HTTP en RankingControllerTest.java
```

### User Story 2

```text
Task T012: cobertura SQL H2
Task T013: cobertura MockMvc
Task T014: cobertura PostgreSQL E2E
```

### User Story 3

```text
Task T019: seguridad y validación HTTP
Task T020: contrato OpenAPI generado
```

### User Story 4

```text
Task T024: serializer y TTL
Task T025: hit/miss y clave
Task T026: listener e invalidación
Task T027: publicación desde recalculate
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Completar Setup y Foundation.
2. Escribir T006/T007 y observar fallos.
3. Implementar T008..T010.
4. Ejecutar T011 y demostrar el ranking activo independiente.

### Incremental Delivery

1. **US1**: ranking actual ordenado y autenticado.
2. **US2**: estrategia, filtros, paginación y variación, validado en ambos motores.
3. **US3**: validaciones, seguridad y OpenAPI.
4. **US4**: caché tipado, clave canónica e invalidación terminal.
5. **Polish**: rendimiento PostgreSQL y regresión completa.

### Parallel Team Strategy

Tras US2, un equipo puede cerrar contrato/seguridad (US3) mientras otro implementa caché/eventos (US4). Dentro de cada historia, sólo se paralelizan tareas marcadas `[P]`; no editar simultáneamente `QuoteController`, `QuoteQueryService` o `PlayerQuoteRepository`.

## Notes

- `[P]` significa archivos distintos y dependencias satisfechas, no ejecución ciega sobre archivos compartidos.
- Los tests usan datos persistidos manualmente y no llaman Football-Data ni WhoScored.
- No agregar sort del cliente: el ranking tiene orden fijo.
- No agregar índices/migraciones ni tocar archivos protegidos sin aprobación explícita.
- H2 valida semántica; PostgreSQL E2E valida el motor de producción.
