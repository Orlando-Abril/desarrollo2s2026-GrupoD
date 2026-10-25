---

description: "Tareas de implementación del historial de cotizaciones de un jugador"
---

# Tasks: Historial de cotizaciones de un jugador

**Input**: Design documents from `/specs/017-historial-cotizaciones/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/quote-history-api.yaml](contracts/quote-history-api.yaml), [quickstart.md](quickstart.md)

**Tests**: Solicitados explícitamente. En cada historia, escribir y ejecutar primero los tests indicados, comprobar que fallan por la funcionalidad ausente y recién después implementar.

**Organization**: Las tareas se agrupan por historia para entregar primero historial, luego vigencia en fecha y finalmente el contrato robusto de validación, seguridad y OpenAPI.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo porque afecta archivos distintos y no depende de trabajo incompleto.
- **[Story]**: Historia cubierta (`US1`, `US2`, `US3`).
- Todas las rutas son relativas a la raíz del repositorio.

## Protected Files and Scope

`backend/src/main/java/com/example/demo/controller/PlayerController.java`, `backend/src/main/java/com/example/demo/exception/GlobalExceptionHandler.java`, `backend/src/main/java/com/example/demo/repository/PlayerRepository.java`, `backend/src/main/java/com/example/demo/repository/PlayerQuoteRepository.java` y los demás archivos de Entrega 1 no se modifican. Si una tarea pareciera exigirlo, detener la implementación y consultar al equipo con archivo, cambio mínimo y alternativa.

No agregar migraciones, dependencias, caché, llamadas externas ni otro `@DataJpaTest`. Las consultas de repositorio y sus índices ya fueron implementados y probados en 013.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirmar que la base 013/016 está verde y fijar el guard de archivos protegidos antes de editar.

- [X] T001 Ejecutar la regresión base de `backend/src/test/java/com/example/demo/controller/QuoteControllerTest.java`, `backend/src/test/java/com/example/demo/config/QuoteOpenApiTest.java` y `backend/src/test/java/com/example/demo/repository/PlayerQuoteRepositoryTest.java` antes de iniciar cambios
- [X] T002 [P] Verificar con `git diff --name-only` que no existan cambios previos en `backend/src/main/java/com/example/demo/controller/PlayerController.java`, `backend/src/main/java/com/example/demo/exception/GlobalExceptionHandler.java`, `backend/src/main/java/com/example/demo/repository/PlayerRepository.java` y `backend/src/main/java/com/example/demo/repository/PlayerQuoteRepository.java`

**Checkpoint**: La base compila, las pruebas existentes pasan y el conjunto protegido parte limpio.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Crear los tipos compartidos y preparar `QuoteController` sin cambiar el contrato de recálculo.

**⚠️ CRITICAL**: Completar esta fase antes de implementar cualquier historia.

- [X] T003 [P] Crear `backend/src/main/java/com/example/demo/dto/quote/PlayerQuoteResponse.java` como record con mapper `from(PlayerQuote)` y OpenAPI: `value` requerido y no negativo; `score` requerido pero nullable; `status` requerido y limitado a `CALCULATED`, `NO_STATS`, `INSUFFICIENT_HISTORY`; `strategy` requerido; `version` requerido y mínimo 1; `calculatedAt` requerido como instante ISO 8601 UTC; no exponer id, jugador, runId ni correlationId
- [X] T004 [P] Crear las excepciones sin anotaciones HTTP `backend/src/main/java/com/example/demo/exception/PlayerNotFoundException.java`, `backend/src/main/java/com/example/demo/exception/QuoteNotFoundException.java` y `backend/src/main/java/com/example/demo/exception/InvalidQuoteQueryException.java`, con mensajes que incluyan los valores necesarios para los errores del contrato
- [X] T005 Quitar el `@RequestMapping("/quotes")` de clase y cambiar el método existente a `@PostMapping("/quotes/recalculate")` en `backend/src/main/java/com/example/demo/controller/QuoteController.java`, sin alterar cuerpo, respuestas OpenAPI ni autorización del recálculo
- [X] T006 Ejecutar `backend/src/test/java/com/example/demo/controller/QuoteControllerTest.java` y `backend/src/test/java/com/example/demo/config/QuoteOpenApiTest.java` para demostrar que `POST /quotes/recalculate` conserva path, seguridad y contrato después de T005

**Checkpoint**: DTO y excepciones compartidos disponibles; el controller admite rutas hermanas y el POST de 016 sigue verde.

---

## Phase 3: User Story 1 - Consultar el historial de cotizaciones (Priority: P1) 🎯 MVP

**Goal**: Devolver el historial cronológico de un jugador para la estrategia activa o explícita, con rangos UTC abiertos/cerrados y lista vacía cuando no hay coincidencias.

**Independent Test**: Con cotizaciones OFFICIAL y MOMENTUM persistidas en distintas fechas, `GET /players/{id}/quotes` devuelve sólo la estrategia activa; `strategy`, `from` y `to` filtran correctamente, los bordes del día son inclusivos y el resultado queda ordenado por fecha e id ascendentes.

### Tests for User Story 1 ⚠️

> Escribir T007–T008 primero y verificar que fallen porque el servicio y el GET todavía no existen.

- [X] T007 [US1] Crear en `backend/src/test/java/com/example/demo/controller/QuoteHistoryControllerTest.java` el contexto `@SpringBootTest` + MockMvc + `@UnitTestProfile` + `@Transactional`, usar `PlayerRepository`/`PlayerQuoteRepository` reales, `@MockitoBean QuoteCalculationService` y mocks de `FootballDataAdapter`/`WhoScoredAdapter` sin configurar nuevamente sus fuentes; cargar manualmente secuencias temporales para al menos un jugador de cada liga y probar estrategia activa, estrategia explícita, aislamiento por jugador/estrategia, orden `(calculatedAt ASC, id ASC)` y los seis campos del DTO incluido `score: null`; comprobar que `calculatedAt` representa el instante UTC persistido y termina en `Z` sin fijar fracciones del formateador, y usar `verifyNoInteractions` sobre ambos adaptadores para demostrar que el historial usa sólo cotizaciones persistidas
- [X] T008 [US1] Agregar en `backend/src/test/java/com/example/demo/controller/QuoteHistoryControllerTest.java` casos de `from` solo, `to` solo, ambos límites, `from=to`, cotizaciones al inicio UTC y justo antes/después del fin del día, historial completo sin filtros de fecha y 200 con `[]` para un jugador existente sin coincidencias

### Implementation for User Story 1

- [X] T009 [US1] Crear `history(Long playerId, String strategy, LocalDate from, LocalDate to)` y helpers comunes en `backend/src/main/java/com/example/demo/service/valuation/QuoteQueryService.java`: transacción read-only; constructor limitado a `PlayerRepository`, `PlayerQuoteRepository` y `ValuationStrategyCatalog`, sin clientes externos; rechazar primero `playerId <= 0` con `InvalidQuoteQueryException`, después validar `PlayerRepository.existsById` para ids positivos, resolver estrategia activa o exacta, rechazar `from > to`, convertir días a UTC, usar límites SQL finitos para extremos ausentes, llamar la consulta `Between...OrderByCalculatedAtAscIdAsc`, excluir la frontera exacta del día siguiente cuando hay `to` y mapear a `PlayerQuoteResponse`
- [X] T010 [US1] Inyectar `QuoteQueryService` y agregar la modalidad historial de `GET /players/{id}/quotes` en `backend/src/main/java/com/example/demo/controller/QuoteController.java`, ligando `id`, `strategy`, `from` y `to` (`LocalDate` ISO) y devolviendo `List<PlayerQuoteResponse>` cuando `at` está ausente, sin agregar lógica de repositorio al controller; agregar el `@MockitoBean QuoteQueryService` requerido por el nuevo constructor al fixture existente de `backend/src/test/java/com/example/demo/controller/QuoteControllerTest.java`
- [X] T011 [US1] Ejecutar los casos US1 de `backend/src/test/java/com/example/demo/controller/QuoteHistoryControllerTest.java` y confirmar historial activo/explícito, cinco ligas, rangos UTC, orden, shape y lista vacía

**Checkpoint**: US1 es demostrable por sí sola y constituye el MVP; no requiere la modalidad `at`.

---

## Phase 4: User Story 2 - Consultar la cotización vigente en una fecha (Priority: P1)

**Goal**: Con `at`, devolver un único objeto con la última cotización vigente al fin UTC del día o 404 si todavía no existía ninguna.

**Independent Test**: Con cotizaciones antes, durante y después de una fecha, `GET /players/{id}/quotes?at=YYYY-MM-DD` devuelve exactamente la última anterior al inicio del día siguiente y resuelve empates por el registro más nuevo.

### Tests for User Story 2 ⚠️

> Escribir T012 primero y verificar que falle antes de implementar `quoteAt` y la rama `at`.

- [X] T012 [US2] Agregar en `backend/src/test/java/com/example/demo/controller/QuoteHistoryControllerTest.java` casos `at` entre cotizaciones, varias cotizaciones dentro del mismo día, empate de `calculatedAt` resuelto por id descendente, estrategia activa por defecto, estrategia explícita, exclusión de cotizaciones posteriores y 404 `quote_not_found` antes de la primera cotización

### Implementation for User Story 2

- [X] T013 [US2] Agregar `quoteAt(Long playerId, String strategy, LocalDate at)` en `backend/src/main/java/com/example/demo/service/valuation/QuoteQueryService.java`, reutilizando validación de jugador/estrategia y `PlayerQuoteRepository.findEffectiveOn`, mapeando a `PlayerQuoteResponse` y lanzando `QuoteNotFoundException` con jugador, estrategia y fecha cuando no hay resultado
- [X] T014 [US2] Agregar el parámetro ISO `at` y la rama de respuesta objeto en `backend/src/main/java/com/example/demo/controller/QuoteController.java`, y mapear `QuoteNotFoundException` a 404 `quote_not_found` con `GlobalExceptionHandler.ErrorResponse` en `backend/src/main/java/com/example/demo/exception/QuoteExceptionHandler.java`
- [X] T015 [US2] Ejecutar los casos US2 de `backend/src/test/java/com/example/demo/controller/QuoteHistoryControllerTest.java` y confirmar objeto único, fin de día UTC, desempate, estrategia activa/explícita y 404 previo a la primera cotización

**Checkpoint**: US2 funciona sobre la misma ruta sin cambiar el comportamiento del historial de US1.

---

## Phase 5: User Story 3 - Validaciones, seguridad y documentación consistentes (Priority: P2)

**Goal**: Completar 400/401/404, los dos mecanismos de autenticación, la coexistencia con rutas de jugadores y el contrato OpenAPI ejecutable.

**Independent Test**: MockMvc produce los códigos y shapes esperados para entradas inválidas, recursos ausentes y credenciales; `/v3/api-docs` publica cinco parámetros, lista/objeto, todas las respuestas y `bearerAuth`/`apiKeyAuth`; `/players` y `/players/{id}/quotes` llegan a sus controllers correctos.

### Tests for User Story 3 ⚠️

> T016 y T017 pueden escribirse en paralelo y deben fallar en sus expectativas nuevas antes de completar los handlers/anotaciones.

- [X] T016 [P] [US3] Agregar en `backend/src/test/java/com/example/demo/controller/QuoteHistoryControllerTest.java`: 400 `validation_error` para `id=0` e id negativo, fecha vacía, `2026-1-1`, `2025-02-29`, rango invertido y estrategia vacía/con case distinto; aceptación de `2024-02-29`; 400 `unknown_strategy` para código desconocido; 404 `player_not_found` sólo para id positivo inexistente; 401 sin credenciales sobre ese mismo id sin que el body filtre `player_not_found`; 200 con JWT de `Role.USER` y 200 con API key de `Role.USER`; y regresión conjunta de `/players` y `/players/{id}/quotes`. No duplicar bearer vencido ni API key inválida/revocada, cubiertos por `JwtAuthFilterTest` y `ApiKeyAuthFilterTest`. En `backend/src/test/java/com/example/demo/controller/QuoteControllerTest.java`, probar `at` combinado con `from` o `to` y verificar 400 más `verifyNoInteractions(queryService)` usando el mock de T010, sin spy ni contexto adicional
- [X] T017 [P] [US3] Ampliar `backend/src/test/java/com/example/demo/config/QuoteOpenApiTest.java` para exigir `GET /players/{id}/quotes`, parámetros `id`, `strategy`, `from`, `to`, `at`, `minimum: 1` para `id`, respuesta 200 `oneOf` array/`PlayerQuoteResponse`, respuestas 400/401/404 con ejemplo de id no positivo, header `X-Correlation-ID`, esquema `PlayerQuoteResponse` y alternativas separadas `bearerAuth`/`apiKeyAuth`, conservando todas las aserciones de `POST /quotes/recalculate`

### Implementation for User Story 3

- [X] T018 [US3] Completar en `backend/src/main/java/com/example/demo/controller/QuoteController.java` la validación que rechaza `at` combinado con `from` o `to` antes de consultar cotizaciones y agregar anotaciones OpenAPI del contrato `specs/017-historial-cotizaciones/contracts/quote-history-api.yaml`, incluyendo `oneOf`, cinco parámetros, 200/400/401/404, correlación y ambos `@SecurityRequirement`
- [X] T019 [US3] Ampliar `backend/src/main/java/com/example/demo/exception/QuoteExceptionHandler.java` con `PlayerNotFoundException` → 404 `player_not_found`, `InvalidQuoteQueryException` → 400 `validation_error` y `MethodArgumentTypeMismatchException` → 400 `validation_error`, conservando handlers de 016 y sin modificar `backend/src/main/java/com/example/demo/exception/GlobalExceptionHandler.java`
- [X] T020 [US3] Ejecutar `backend/src/test/java/com/example/demo/controller/QuoteHistoryControllerTest.java`, `backend/src/test/java/com/example/demo/config/QuoteOpenApiTest.java`, `backend/src/test/java/com/example/demo/controller/QuoteControllerTest.java` y `backend/src/test/java/com/example/demo/controller/PlayerControllerTest.java`, confirmando errores, autenticación, OpenAPI y ausencia de colisiones/regresiones

**Checkpoint**: Las tres historias quedan completas, documentadas, autenticadas e independientemente verificables.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Validación integral, rendimiento y control final del alcance.

- [X] T021 Ejecutar desde `backend/` la suite normal con `./mvnw test -DexcludedGroups=e2e` (o `mvnw.cmd` en Windows) y corregir sólo fallas atribuibles a los archivos de esta feature listados en `specs/017-historial-cotizaciones/plan.md`; además ejecutar explícitamente `backend/src/test/java/com/example/demo/e2e/ExternalSourcesDownE2ETest.java` en el entorno E2E existente de 016, sin crear ni reconfigurar otro escenario de Football-Data.org/WhoScored
- [X] T022 Seguir las secciones 3–7 de `specs/017-historial-cotizaciones/quickstart.md` contra una aplicación local: historial, rangos, `at`, 400/401/404, API key/JWT, coexistencia de `/players` y verificación de `/v3/api-docs`
- [X] T023 Ejecutar el protocolo de la sección 8 de `specs/017-historial-cotizaciones/quickstart.md`: exactamente 2.500 cotizaciones, 5 warmups no medidos y 20 consultas HTTP secuenciales en el mismo host y sin carga concurrente; exigir p95 menor a 1 segundo y al menos 19 de 20 mediciones menores a 1 segundo, y registrar commit, fecha, entorno, dataset, las 20 duraciones, p95 y pass/fail en `specs/017-historial-cotizaciones/performance-results.md`; si falla, agregar volumen y plan de ejecución antes de proponer cambios
- [X] T024 [P] Revisar `git diff --name-only` y `git diff --check` contra `specs/017-historial-cotizaciones/plan.md`, confirmando que no cambiaron `PlayerController.java`, `GlobalExceptionHandler.java`, `PlayerRepository.java`, `PlayerQuoteRepository.java`, `SecurityConfig.java`, modelos de Entrega 1 ni archivos fuera del alcance
- [ ] T025 Verificar que el workflow de GitHub Actions correspondiente al commit de la feature finaliza en `SUCCESS` y registrar commit, workflow, ejecución y resultado en `specs/017-historial-cotizaciones/validation-results.md`
- [ ] T026 Verificar en SonarCloud que el análisis del mismo commit obtiene Quality Gate `PASSED`, cero vulnerabilidades y menos de 10 issues de severidad menor, y registrar proyecto, análisis, métricas y resultado en `specs/017-historial-cotizaciones/validation-results.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 — Setup**: sin dependencias; comenzar aquí.
- **Phase 2 — Foundational**: depende de Phase 1 y bloquea las historias.
- **Phase 3 — US1**: depende de Phase 2; entrega el MVP.
- **Phase 4 — US2**: depende de Phase 2 en términos funcionales, pero se recomienda después de US1 porque modifica los mismos archivos de servicio, controller y test.
- **Phase 5 — US3**: depende de US1 y US2 para probar el contrato completo y todas sus ramas.
- **Phase 6 — Polish**: depende de todas las historias incluidas en la entrega; T025 y T026 requieren que el commit final esté disponible en los servicios remotos.

### User Story Dependency Graph

```text
Setup → Foundation → US1 (historial/MVP)
                   └→ US2 (vigencia)
US1 + US2 → US3 (validación, seguridad, OpenAPI) → Polish
```

US1 y US2 son verificables por separado después de Foundation, pero el orden US1 → US2 evita conflictos de edición en `QuoteQueryService.java`, `QuoteController.java` y `QuoteHistoryControllerTest.java`.

### Within Each User Story

- Tests de la historia primero y en rojo.
- DTO/excepciones compartidos antes del servicio.
- Servicio antes de endpoint.
- Endpoint/handler antes de validación del checkpoint.
- No avanzar a la siguiente historia mientras el checkpoint actual esté rojo.

### Parallel Opportunities

- T001 y T002 pueden ejecutarse en paralelo.
- T003 y T004 pueden ejecutarse en paralelo porque crean archivos distintos.
- T016 y T017 pueden ejecutarse en paralelo porque modifican suites distintas.
- Después de T021, T022/T023 y T024 pueden repartirse, siempre que nadie siga editando código durante la revisión de alcance.
- T025 y T026 pueden verificarse en paralelo una vez publicado el mismo commit final.

---

## Parallel Example: Foundational

```text
Task: "T003 Crear PlayerQuoteResponse.java con mapper y schema"
Task: "T004 Crear PlayerNotFoundException, QuoteNotFoundException e InvalidQuoteQueryException"
```

## Parallel Example: User Story 3

```text
Task: "T016 Agregar pruebas MockMvc de validación, seguridad y rutas"
Task: "T017 Ampliar QuoteOpenApiTest con el contrato del GET"
```

---

## Implementation Strategy

### MVP First — User Story 1

1. Completar Setup y Foundational.
2. Escribir T007–T008 y confirmar rojo.
3. Implementar T009–T010.
4. Ejecutar T011 y detenerse si no queda verde.
5. Demostrar historial activo/explícito, rangos y lista vacía sin depender de `at`.

### Incremental Delivery

1. **US1**: historial cronológico filtrable → MVP.
2. **US2**: cotización vigente con `at` y 404 específico.
3. **US3**: validación completa, autenticación dual, coexistencia y OpenAPI.
4. **Polish**: suite total, E2E existente de fuentes caídas, quickstart, rendimiento, revisión de protegidos, GitHub Actions y SonarCloud.

### Parallel Team Strategy

Con varias personas, completar Foundation en conjunto. Luego una persona puede preparar las pruebas US1/US2 secuencialmente sobre `QuoteHistoryControllerTest.java`, mientras otra prepara T017 en `QuoteOpenApiTest.java`. Las implementaciones de US1 y US2 deben coordinarse porque comparten service y controller; no se editan esos archivos en paralelo.

## Notes

- `[P]` significa archivos diferentes y ausencia de dependencia sobre una tarea incompleta.
- No marcar tareas completadas hasta ejecutar su validación asociada.
- El contrato fuente es `specs/017-historial-cotizaciones/contracts/quote-history-api.yaml`.
- Los tests de repositorio de 013 se reutilizan; esta feature prueba integración HTTP y no duplica `@DataJpaTest`.
- No hacer cambios oportunistas en archivos protegidos o fuera de alcance.
