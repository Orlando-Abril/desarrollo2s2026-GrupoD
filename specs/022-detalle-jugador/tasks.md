---

description: "Tareas de implementación para el detalle de un jugador"
---

# Tasks: Detalle de un jugador

**Input**: Design documents from `/specs/022-detalle-jugador/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [player-detail-api.yaml](contracts/player-detail-api.yaml), [quickstart.md](quickstart.md)

**Tests**: Las pruebas son obligatorias en esta feature: el pedido exige MockMvc con `@UnitTestProfile`, casos 200/400/401/404 y regresión de rutas.

**Organization**: Las tareas están agrupadas por historia para implementar y validar cada incremento de forma independiente. Las pruebas se escriben primero; las de US1 y US3 deben fallar por capacidades ausentes, mientras que US2 caracteriza y verifica desde el inicio la semántica nullable definida en T008.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo porque trabaja en archivos distintos y no depende de una tarea incompleta.
- **[Story]**: Historia cubierta (`US1`, `US2`, `US3`).
- Todas las tareas indican rutas exactas desde la raíz del repositorio.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirmar la línea base antes de modificar el catálogo protegido.

- [X] T001 Ejecutar desde `backend/` la línea base `./mvnw.cmd "-Dtest=PlayerControllerTest,PlayerOpenApiTest,RankingControllerTest,QuoteHistoryControllerTest" test` y comprobar que `backend/src/main/java/com/example/demo/dto/player/PlayerResponse.java` y `backend/src/main/java/com/example/demo/exception/GlobalExceptionHandler.java` no tienen cambios

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Preparar el contexto de pruebas compartido por las tres historias.

**⚠️ CRITICAL**: Las historias no comienzan hasta que el contexto integrado compile y pueda descubrir todos los controllers/advice reales.

- [X] T002 [P] Agregar `@UnitTestProfile` al contexto OpenAPI existente en `backend/src/test/java/com/example/demo/config/PlayerOpenApiTest.java` sin alterar sus assertions actuales
- [X] T003 [P] Crear el fixture base `@SpringBootTest`, `@AutoConfigureMockMvc`, `@UnitTestProfile` y `@Transactional`, con `MockMvc`, repositories, `JwtUtil`, `AuthFixtures`, `@MockitoBean FootballDataAdapter`, `@MockitoBean WhoScoredAdapter` y helpers locales de jugador/stats/quote en `backend/src/test/java/com/example/demo/controller/PlayerDetailControllerTest.java`

**Checkpoint**: El contexto de prueba compartido está listo; puede comenzar el desarrollo por historia.

---

## Phase 3: User Story 1 - Consultar el detalle completo (Priority: P1) 🎯 MVP

**Goal**: Un usuario autenticado obtiene el jugador, las estadísticas persistidas de temporada y exactamente la cotización más reciente de la estrategia activa.

**Independent Test**: Persistir un jugador con estadísticas, cotizaciones anteriores, dos cotizaciones activas empatadas en fecha y una cotización de otra estrategia; `GET /players/{id}` debe devolver los nueve campos de `player`, las diez métricas más `fetchedAt` y los seis campos de la última quote activa, ganando el id de quote mayor ante empate.

### Tests for User Story 1

> Escribir y ejecutar estas pruebas antes de implementar; deben fallar porque el endpoint todavía no existe.

- [X] T004 [US1] Agregar casos MockMvc fallidos para respuesta completa con `header().exists(CorrelationIdFilter.HEADER)`, exclusión de otra estrategia, última fecha y desempate por id descendente; configurar ambos adapters mock para fallar si se invocan, exigir 200 y comprobar `verifyNoInteractions(footballDataAdapter, whoScoredAdapter)` en `backend/src/test/java/com/example/demo/controller/PlayerDetailControllerTest.java`

### Implementation for User Story 1

- [X] T005 [P] [US1] Crear `PlayerStatsResponse` con `minutesPlayed`, `goals`, `assists`, `shots`, `keyPasses`, `tackles`, `dribbles`, `yellowCards`, `redCards` y `rating` como valores preservados de la entidad, y `fetchedAt` obligatorio, más `@Schema` y factory `from(PlayerStats)`, en `backend/src/main/java/com/example/demo/dto/player/PlayerStatsResponse.java`
- [X] T006 [P] [US1] Crear `CurrentQuoteResponse` con `value` no negativo, `score` nullable, `status` limitado a `CALCULATED`, `NO_STATS` o `INSUFFICIENT_HISTORY`, `strategy`, `version` ≥ 1 y `calculatedAt`, más `@Schema` y factory `from(PlayerQuote)`, en `backend/src/main/java/com/example/demo/dto/player/CurrentQuoteResponse.java`
- [X] T007 [US1] Crear `PlayerDetailResponse` con `player` obligatorio de tipo `PlayerResponse`, y campos presentes `stats` y `quote` tipados como `PlayerStatsResponse` y `CurrentQuoteResponse`, ambos nullable, con schemas OpenAPI estables en `backend/src/main/java/com/example/demo/dto/player/PlayerDetailResponse.java`
- [X] T008 [US1] Implementar `PlayerDetailService` con `@Transactional(readOnly = true)`: rechazar `id == null || id <= 0` mediante `InvalidQuoteQueryException("El id del jugador debe ser mayor a cero")`; para ids positivos usar `PlayerRepository.findById` o `PlayerNotFoundException`, `PlayerStatsRepository.findById(...).map(PlayerStatsResponse::from).orElse(null)`, `ValuationStrategyCatalog.active().code()` por pedido y `PlayerQuoteRepository.findFirstByPlayerIdAndStrategyCodeOrderByCalculatedAtDescIdDesc(...).map(CurrentQuoteResponse::from).orElse(null)`, construyendo el `PlayerResponse` existente sin adapters, clientes externos, defaults ni conversión de métricas nulas a cero, en `backend/src/main/java/com/example/demo/service/PlayerDetailService.java`
- [X] T009 [US1] Inyectar `PlayerDetailService` y agregar únicamente `@GetMapping("/{id}")` con `@PathVariable Long id`, delegación directa y respuesta `PlayerDetailResponse` en `backend/src/main/java/com/example/demo/controller/PlayerController.java`
- [X] T010 [US1] Adaptar el constructor del controller bajo prueba con un mock de `PlayerDetailService` y conservar sin cambios las cinco pruebas existentes de listado/sync en `backend/src/test/java/com/example/demo/controller/PlayerControllerTest.java`
- [X] T011 [US1] Ejecutar desde `backend/` `./mvnw.cmd "-Dtest=PlayerDetailControllerTest,PlayerControllerTest" test` y corregir exclusivamente `backend/src/test/java/com/example/demo/controller/PlayerDetailControllerTest.java`, `backend/src/test/java/com/example/demo/controller/PlayerControllerTest.java` y los archivos de implementación T005–T009 hasta que el detalle completo y las regresiones aprueben

**Checkpoint**: El detalle completo funciona como MVP con datos locales y selección determinista de la quote activa.

---

## Phase 4: User Story 2 - Consultar con datos complementarios ausentes (Priority: P1)

**Goal**: Un jugador existente sigue devolviendo 200 cuando no tiene stats, quote activa o métricas individuales.

**Independent Test**: Consultar un jugador sin filas complementarias y otro con stats parciales y sólo una quote de estrategia no activa; la respuesta debe incluir siempre `player`, usar `stats: null`/`quote: null` cuando falta cada sección y conservar métricas desconocidas como null, nunca como cero.

### Tests for User Story 2

> Agregar estos casos como caracterización y regresión de la semántica nullable implementada desde T008; no se exige que fallen antes de T013.

- [X] T012 [US2] Agregar casos MockMvc para ausencia conjunta e independiente de stats/quote, quote sólo de estrategia no activa y métricas internas nulas en `backend/src/test/java/com/example/demo/controller/PlayerDetailControllerTest.java`

### Implementation for User Story 2

- [X] T013 [US2] Verificar en `backend/src/main/java/com/example/demo/service/PlayerDetailService.java` y `backend/src/main/java/com/example/demo/dto/player/PlayerStatsResponse.java` que no existan `orElseThrow`, valores por defecto ni conversiones de métricas nulas a cero, corrigiendo únicamente las desviaciones detectadas por T012
- [X] T014 [US2] Ejecutar desde `backend/` `./mvnw.cmd "-Dtest=PlayerDetailControllerTest" test` sobre `backend/src/test/java/com/example/demo/controller/PlayerDetailControllerTest.java` y verificar que los casos completos de US1 siguen aprobando junto con todas las combinaciones nulas de US2

**Checkpoint**: US1 y US2 funcionan juntas; la ausencia de datos complementarios nunca impide abrir un jugador existente.

---

## Phase 5: User Story 3 - Recibir errores y documentación previsibles (Priority: P2)

**Goal**: El integrador distingue 400/401/404, usa JWT o API key, descubre el contrato completo y conserva las rutas de ranking e historial.

**Independent Test**: Con el contexto Spring real, comprobar 400 para id no convertible a `Long`, 401 sin credenciales sin filtrar existencia, 404 `player_not_found` para un Long inexistente, 200 con cada mecanismo de autenticación, rutas no ambiguas y OpenAPI con parámetro, esquemas, nulabilidad, respuestas y seguridad.

### Tests for User Story 3

> Escribir ambos grupos de pruebas antes de modificar el advice y las anotaciones.

- [X] T015 [US3] Agregar casos MockMvc explícitos `abc` → 400 `validation_error`, `9223372036854775808` → 400, `0` → 400 con `El id del jugador debe ser mayor a cero`, `-1` → 400 con el mismo mensaje, id positivo inexistente → 404 `player_not_found`, 401 sin fuga, 200 con API key USER, 200 con JWT USER y coexistencia de `/players/{id}`, `/players/ranking` y `/players/{id}/quotes` en `backend/src/test/java/com/example/demo/controller/PlayerDetailControllerTest.java`
- [X] T016 [P] [US3] Agregar assertions fallidas para `/players/{id}`, parámetro int64, `PlayerDetailResponse`, `PlayerStatsResponse`, `CurrentQuoteResponse`, campos nullable, header `X-Correlation-ID`, respuestas 200/400/401/404 y alternativas `bearerAuth`/`apiKeyAuth` en `backend/src/test/java/com/example/demo/config/PlayerOpenApiTest.java`

### Implementation for User Story 3

- [X] T017 [US3] Crear `PlayerDetailExceptionHandler` con `@RestControllerAdvice(assignableTypes = PlayerController.class)` y `@Order(Ordered.HIGHEST_PRECEDENCE)`, mapeando `PlayerNotFoundException` a 404 `ErrorResponse("player_not_found", ex.getMessage())` e `InvalidQuoteQueryException` a 400 `ErrorResponse("validation_error", ex.getMessage())`, en `backend/src/main/java/com/example/demo/exception/PlayerDetailExceptionHandler.java`
- [X] T018 [P] [US3] Verificar que `backend/src/main/java/com/example/demo/exception/QuoteExceptionHandler.java` conserve sin cambios su mapping actual de `PlayerNotFoundException` para `QuoteController` y que `backend/src/main/java/com/example/demo/exception/GlobalExceptionHandler.java` permanezca intacto
- [X] T019 [P] [US3] Documentar en el método nuevo el parámetro `id`, schema `PlayerDetailResponse`, header de correlación, respuestas 200/400/401/404 y confirmar las dos `@SecurityRequirement` alternativas heredadas, sin cambiar los métodos existentes, en `backend/src/main/java/com/example/demo/controller/PlayerController.java`
- [X] T020 [US3] Ejecutar desde `backend/` `./mvnw.cmd "-Dtest=PlayerDetailControllerTest,PlayerOpenApiTest,RankingControllerTest,QuoteHistoryControllerTest" test` sobre los tests de `backend/src/test/java/com/example/demo/controller/` y `backend/src/test/java/com/example/demo/config/PlayerOpenApiTest.java`, corrigiendo hasta conservar el 404 histórico y las tres rutas sin ambigüedad

**Checkpoint**: Las tres historias están completas, autenticadas, documentadas y libres de colisiones de routing.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Verificar resiliencia, rendimiento y Definition of Done sin ampliar el alcance funcional.

- [X] T021 [P] Ejecutar el protocolo de 2.500 jugadores, 5 warm-ups y 100 requests de [quickstart.md](quickstart.md), registrar las 100 duraciones y el cumplimiento de al menos 95 respuestas bajo 1 segundo en `specs/022-detalle-jugador/performance-results.md`
- [X] T022 Ejecutar desde `backend/` la verificación estructural `rg -n "adapter|FootballData|WhoScored|PlayerCatalogService|PlayerStatsService|QuoteCalculationService" src/main/java/com/example/demo/service/PlayerDetailService.java` y la suite focalizada de [quickstart.md](quickstart.md), registrando comandos y resultados en `specs/022-detalle-jugador/validation-results.md`
- [X] T023 Ejecutar desde `backend/` `./mvnw.cmd test`, revisar `git diff -- backend/src/main/java/com/example/demo/dto/player/PlayerResponse.java backend/src/main/java/com/example/demo/exception/GlobalExceptionHandler.java`, y registrar total de tests, fallas/omisiones y confirmación de archivos protegidos intactos en `specs/022-detalle-jugador/validation-results.md`
- [ ] T024 Verificar después del push el workflow de GitHub Actions y el Quality Gate de SonarCloud —SUCCESS/PASSED, cero vulnerabilidades y menos de 10 issues menores— y agregar enlaces y resultados en `specs/022-detalle-jugador/validation-results.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Sin dependencias; fija la línea base.
- **Foundational (Phase 2)**: Depende de T001 y bloquea las pruebas por historia.
- **US1 (Phase 3)**: Depende de T002–T003; entrega el MVP y la base compartida.
- **US2 (Phase 4)**: Depende de US1 porque amplía la semántica del mismo agregado y servicio.
- **US3 (Phase 5)**: Depende de US1; puede comenzar en paralelo con US2 si se coordinan las ediciones de `PlayerDetailControllerTest.java`.
- **Polish (Phase 6)**: Depende de US1, US2 y US3 completas.

### User Story Dependency Graph

```text
Setup → Foundation → US1 (MVP) ─┬→ US2 ─┐
                                 └→ US3 ─┴→ Polish
```

### Within Each User Story

- Escribir y ejecutar pruebas fallidas antes de la implementación correspondiente.
- En US1, crear DTOs antes del service y el service antes del endpoint.
- En US2, fijar las combinaciones nulas esperadas y auditar que el mapping implementado desde US1 no introduzca defaults.
- En US3, fijar errores/rutas/OpenAPI antes de extraer el handler y completar anotaciones.
- No avanzar al checkpoint siguiente con pruebas de historias anteriores fallando.

### Parallel Opportunities

- T002 y T003 pueden ejecutarse en paralelo.
- T005 y T006 pueden ejecutarse en paralelo; T007 espera ambos DTOs.
- Tras US1, US2 y US3 pueden avanzar en paralelo con coordinación sobre el archivo MockMvc compartido.
- En US3, T015 y T016 prueban archivos distintos; después de T017, T018 valida los handlers protegidos mientras T019 completa el controller.
- T021 puede ejecutarse en paralelo con la preparación documental de T022, pero T023 espera las correcciones resultantes.

---

## Parallel Example: User Story 1

```text
Task T005: Crear PlayerStatsResponse en backend/src/main/java/com/example/demo/dto/player/PlayerStatsResponse.java
Task T006: Crear CurrentQuoteResponse en backend/src/main/java/com/example/demo/dto/player/CurrentQuoteResponse.java
```

## Parallel Example: User Story 3

```text
Task T015: Agregar errores, autenticación y routing a backend/src/test/java/com/example/demo/controller/PlayerDetailControllerTest.java
Task T016: Agregar contrato a backend/src/test/java/com/example/demo/config/PlayerOpenApiTest.java

Después de T017:
Task T018: Verificar intactos backend/src/main/java/com/example/demo/exception/QuoteExceptionHandler.java y backend/src/main/java/com/example/demo/exception/GlobalExceptionHandler.java
Task T019: Completar OpenAPI en backend/src/main/java/com/example/demo/controller/PlayerController.java
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Setup y Foundation.
2. Escribir T004 y observar la falla esperada.
3. Completar T005–T010 en orden de dependencias.
4. Ejecutar T011 y detenerse para validar el detalle completo.
5. El MVP puede demostrarse con un jugador que tenga stats y quote activa.

### Incremental Delivery

1. **US1**: detalle completo y selección activa determinista.
2. **US2**: disponibilidad del detalle ante datos complementarios ausentes.
3. **US3**: errores, autenticación, OpenAPI y garantía de routing.
4. **Polish**: rendimiento, aislamiento externo, regresión total y calidad remota.

### Parallel Team Strategy

1. El equipo completa T001–T003.
2. Una persona implementa US1 hasta T011.
3. Tras el checkpoint de US1, una persona puede trabajar US2 y otra US3; deben coordinar los commits sobre `PlayerDetailControllerTest.java`.
4. La validación final integra ambos incrementos antes de rendimiento y suite completa.

---

## Notes

- `[P]` identifica únicamente tareas sobre archivos distintos sin dependencia incompleta.
- No modificar `PlayerResponse.java`, `GlobalExceptionHandler.java`, esquemas de datos, cache o adapters.
- `QuoteController.java`, `QuoteExceptionHandler.java`, `PlayerNotFoundException.java`, `GlobalExceptionHandler.java` y `SecurityConfig.java` se reutilizan sin cambios.
- Cada tarea de implementación debe mantener `GET /players` y `POST /players/sync` funcionalmente intactos.
- Detenerse y solicitar autorización si aparece una necesidad de modificar otro archivo de Entrega 1 fuera del método nuevo de `PlayerController`.
