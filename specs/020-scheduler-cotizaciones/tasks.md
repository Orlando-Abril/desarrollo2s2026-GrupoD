---

description: "Implementation tasks for the weekly quote recalculation scheduler"
---

# Tasks: Recálculo semanal de cotizaciones

**Input**: Design documents from `/specs/020-scheduler-cotizaciones/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/scheduler-contract.md`, `quickstart.md`

**Tests**: La feature exige tests unitarios con Mockito y captura de logs, más tests de contexto para la activación condicional. Las tareas de test preceden a la implementación correspondiente.

**Organization**: Las tareas se agrupan por historia de usuario y conservan el orden de la spec: programación semanal, independencia de WhoScored y observabilidad/resiliencia.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo con otras tareas marcadas, porque modifica otro archivo y no depende de trabajo incompleto.
- **[Story]**: Historia cubierta (`US1`, `US2`, `US3`).
- Todas las tareas identifican rutas concretas.

## Protected Files

No modificar durante ninguna tarea:

- `backend/src/main/java/com/example/demo/config/SchedulingConfig.java`
- `backend/src/main/java/com/example/demo/scheduler/PlayerStatsScheduler.java`
- cualquier archivo `application.properties`

Si un cambio en alguno pareciera imprescindible, detener la implementación y consultar al equipo antes de continuar.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirmar la línea base y la superficie protegida antes de crear archivos.

- [X] T001 Ejecutar la prueba base `backend/src/test/java/com/example/demo/scheduler/PlayerStatsSchedulerTest.java` y confirmar con `git diff --name-only` que `backend/src/main/java/com/example/demo/config/SchedulingConfig.java`, `backend/src/main/java/com/example/demo/scheduler/PlayerStatsScheduler.java` y todos los `application.properties` parten sin cambios de esta feature

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Preparar el único arnés de tests compartido por las tres historias.

**⚠️ CRITICAL**: Esta fase bloquea todas las historias.

- [X] T002 Crear `backend/src/test/java/com/example/demo/scheduler/QuoteRecalculationSchedulerTest.java` con `OutputCaptureExtension`, mock compartido de `QuoteCalculationService`, limpieza de `CorrelationIdFilter.MDC_KEY` después de cada test y un `ApplicationContextRunner` base, sin implementar todavía escenarios funcionales

**Checkpoint**: El arnés común está listo; a partir de aquí cada historia agrega primero sus pruebas.

---

## Phase 3: User Story 1 - Recalcular cotizaciones semanalmente (Priority: P1) 🎯 MVP

**Goal**: Disponer de un job apagado por defecto que, al habilitarse, use cron y zona configurables con defaults lunes 06:00 UTC y solicite una corrida para todas las estrategias.

**Independent Test**: Inspeccionar la metadata programada, habilitar el contexto con los defaults y ejecutar `run()` para comprobar una única llamada a `QuoteCalculationService.recalculate(Optional.empty())`, sin ejecución automática durante el arranque.

### Tests for User Story 1

> **NOTE: Write these tests first and confirm that they fail before implementation.**

- [X] T003 [US1] Agregar en `backend/src/test/java/com/example/demo/scheduler/QuoteRecalculationSchedulerTest.java` pruebas fallidas que verifiquen exactamente `${valuation.schedule.cron:0 0 6 * * MON}` y `${valuation.schedule.zone:UTC}` en `@Scheduled`, una única delegación a `recalculate(Optional.empty())`, ausencia de ejecución durante el arranque, inicialización exitosa con cron/zona personalizados válidos y fallo visible del contexto —sin fallback— ante un cron inválido o una zona inválida

### Implementation for User Story 1

- [X] T004 [P] [US1] Crear `backend/src/main/java/com/example/demo/config/QuoteSchedulingConfig.java` con `@Configuration`, `@EnableScheduling` y `@ConditionalOnProperty(name = "valuation.schedule.enabled", havingValue = "true")`, sin declarar defaults ni modificar archivos de propiedades
- [X] T005 [P] [US1] Crear `backend/src/main/java/com/example/demo/scheduler/QuoteRecalculationScheduler.java` como componente condicionado sólo por `valuation.schedule.enabled=true`, inyectar `QuoteCalculationService` por constructor y agregar `run()` con los placeholders exactos de cron/zona que delegue una vez en `recalculate(Optional.empty())`
- [X] T006 [US1] Ejecutar `QuoteRecalculationSchedulerTest` desde `backend/pom.xml` y ajustar únicamente `QuoteSchedulingConfig.java`, `QuoteRecalculationScheduler.java` o el test nuevo hasta validar metadata, defaults, delegación y ausencia de disparo al arrancar

**Checkpoint**: US1 funciona con el calendario solicitado y puede demostrarse sin esperar al reloj real.

---

## Phase 4: User Story 2 - Operar con independencia de WhoScored (Priority: P1)

**Goal**: Demostrar que la valuación puede habilitarse con WhoScored apagado y que ninguna combinación de propiedades acopla ambos schedulers.

**Independent Test**: Ejecutar contextos con la matriz de propiedades del contrato y verificar que los beans de valuación estén ausentes por defecto/con `false`, presentes con `valuation.schedule.enabled=true` y presentes aun con `whoscored.sync.enabled=false`, sin invocar el servicio al inicializar.

### Tests for User Story 2

- [X] T007 [US2] Completar en `backend/src/test/java/com/example/demo/scheduler/QuoteRecalculationSchedulerTest.java` la matriz de contexto de ambos schedulers, usando mocks separados de `QuoteCalculationService` y `PlayerStatsService`: con ambas propiedades ausentes o `false` no existe ningún scheduler; con sólo `valuation.schedule.enabled=true` existen únicamente `QuoteSchedulingConfig` y `QuoteRecalculationScheduler`; con sólo `whoscored.sync.enabled=true` existen únicamente `SchedulingConfig` y `PlayerStatsScheduler`; con ambas en `true` existen ambos schedulers y un único `ScheduledAnnotationBeanPostProcessor`; en todos los casos verificar cero invocaciones de servicios durante el arranque

**Checkpoint**: US2 queda demostrada sin modificar `SchedulingConfig.java`, `PlayerStatsScheduler.java` ni `application.properties`.

---

## Phase 5: User Story 3 - Trazar cada ejecución sin afectar el scheduler (Priority: P1)

**Goal**: Dar a cada intento un correlation id propio, registrar inicio y un único resultado terminal, absorber concurrencia/errores y limpiar siempre el MDC.

**Independent Test**: Invocar directamente `run()` con un resultado exitoso, `RecalculationInProgressException` y otra `RuntimeException`; verificar logs, campos, niveles, ausencia de propagación y MDC nulo al terminar cada camino.

### Tests for User Story 3

> **NOTE: Write these tests first and confirm that they fail before implementation.**

- [X] T008 [US3] Agregar en `backend/src/test/java/com/example/demo/scheduler/QuoteRecalculationSchedulerTest.java` pruebas con `OutputCaptureExtension` para correlation id `valuation-<uuid>`, evento de inicio, éxito con `runId`, listas y contadores de un `QuoteRunResult` no trivial, WARN salteado con `recalculation_in_progress`, ERROR únicamente con `errorType` y sin mensaje sensible ni stack trace, exactamente un evento terminal, ninguna excepción no comprobada propagada y MDC nulo en los tres caminos

### Implementation for User Story 3

- [X] T009 [US3] Implementar en `backend/src/main/java/com/example/demo/scheduler/QuoteRecalculationScheduler.java` la creación de `valuation-` + UUID, carga en `CorrelationIdFilter.MDC_KEY`, `quote_scheduled_recalculation_started` y `quote_scheduled_recalculation_finished` con `runId`, `calculated`, `skipped`, `playersProcessed`, `playersFailed`, `activeStrategy` y `marketValueUpdated`
- [X] T010 [US3] Implementar en `backend/src/main/java/com/example/demo/scheduler/QuoteRecalculationScheduler.java` el catch específico de `RecalculationInProgressException` como `quote_scheduled_recalculation_skipped` WARN sin reintento, el catch de cualquier otra `RuntimeException` como `quote_scheduled_recalculation_failed` ERROR que registre únicamente `errorType` —sin mensaje ni stack trace— y no propague la excepción, y el `finally` que elimina siempre `CorrelationIdFilter.MDC_KEY`
- [X] T011 [US3] Ejecutar `QuoteRecalculationSchedulerTest` desde `backend/pom.xml` y corregir sólo los archivos nuevos hasta que éxito, omisión y falla produzcan un inicio, exactamente un terminal, los campos contratados y MDC limpio

**Checkpoint**: Las tres historias quedan funcionales y cubiertas por tests específicos.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Validar regresiones, contrato y restricciones de alcance.

- [ ] T012 Ejecutar `.\mvnw.cmd verify` desde `backend/` según `specs/020-scheduler-cotizaciones/quickstart.md` y resolver cualquier regresión exclusivamente en `backend/src/main/java/com/example/demo/config/QuoteSchedulingConfig.java`, `backend/src/main/java/com/example/demo/scheduler/QuoteRecalculationScheduler.java` o `backend/src/test/java/com/example/demo/scheduler/QuoteRecalculationSchedulerTest.java`
- [X] T013 Validar `specs/020-scheduler-cotizaciones/contracts/scheduler-contract.md` y `specs/020-scheduler-cotizaciones/quickstart.md` contra la implementación, confirmar que no se agregó `valuation.schedule.*` a ningún `application.properties` y que `git diff --name-only` no muestra cambios en los tres grupos de archivos protegidos

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Sin dependencias.
- **Foundational (Phase 2)**: Depende de T001 y bloquea todas las historias.
- **US1 (Phase 3)**: Depende de T002. T003 debe escribirse antes de implementar; luego T004 y T005 pueden ejecutarse en paralelo; T006 depende de ambas.
- **US2 (Phase 4)**: Depende de US1 porque valida la activación de los dos archivos productivos ya creados.
- **US3 (Phase 5)**: Depende de US1; se ejecuta después de US2 para evitar edición simultánea del único archivo de tests.
- **Polish (Phase 6)**: Depende de las tres historias. T012 ejecuta y corrige la verificación completa; T013 se ejecuta después para auditar el resultado definitivo.

### User Story Dependency Graph

```text
Setup → Foundational → US1 ─┬→ US2 ─┐
                            └→ US3 ─┴→ Polish
```

- **US1** es el MVP y aporta las dos clases productivas.
- **US2** y **US3** dependen funcionalmente de US1, pero no dependen entre sí.
- Para una sola persona se recomienda US1 → US2 → US3; equipos separados sólo deben paralelizar US2/US3 si coordinan la edición de `QuoteRecalculationSchedulerTest.java`.

### Within Each User Story

- Escribir y observar fallar los tests antes de la implementación correspondiente.
- No duplicar locks, conteos ni lógica de valuación: delegar siempre en `QuoteCalculationService`.
- No agregar propiedades a recursos ni tocar archivos protegidos.
- Completar el checkpoint de una historia antes de avanzar en ejecución secuencial.

### Parallel Opportunities

- Después de T003, T004 y T005 pueden ejecutarse en paralelo porque crean archivos productivos distintos.
- Después de las historias, T012 debe finalizar antes de T013 para que la auditoría de alcance observe el resultado definitivo.
- US2 y US3 son conceptualmente independientes tras US1, pero el plan secuencial evita conflictos porque ambas agregan casos al mismo archivo de tests.

---

## Parallel Example: User Story 1

Después de escribir T003:

```text
Task T004: Crear backend/src/main/java/com/example/demo/config/QuoteSchedulingConfig.java
Task T005: Crear backend/src/main/java/com/example/demo/scheduler/QuoteRecalculationScheduler.java
```

## Parallel Example: User Story 2

No hay paralelismo interno útil: T007 es una ampliación cohesiva del mismo test de contexto.

## Parallel Example: User Story 3

No hay paralelismo interno seguro: T008, T009 y T010 modifican la misma suite o la misma clase productiva y deben preservar el orden test-first.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar T001–T002.
2. Escribir T003 y confirmar el fallo esperado.
3. Implementar T004–T005, potencialmente en paralelo.
4. Ejecutar T006 y detenerse para validar el MVP.

El MVP entrega el job configurable y apagado por defecto. US2 agrega evidencia explícita del desacoplamiento y US3 completa la operación segura/observable requerida para producción.

### Incremental Delivery

1. **Setup + Foundational** → línea base y arnés de tests.
2. **US1** → calendario y delegación semanal verificables.
3. **US2** → independencia de WhoScored demostrada por matriz de contexto.
4. **US3** → correlation id, logs, concurrencia y manejo de excepciones.
5. **Polish** → suite completa y protección de alcance.

### Parallel Team Strategy

1. El equipo completa T001–T003.
2. Una persona implementa T004 y otra T005.
3. Tras T006, US2 y US3 pueden analizarse en paralelo, pero sus cambios al test compartido deben integrarse en secuencia.
4. Después del build completo y de cualquier corrección, una persona audita propiedades y archivos protegidos.

---

## Notes

- `[P]` sólo marca trabajo seguro sobre archivos distintos o verificaciones de sólo lectura.
- `[US1]`, `[US2]` y `[US3]` mantienen trazabilidad con `spec.md`.
- No se crean entidades persistentes, endpoints, migraciones, configuraciones de recursos ni accesos externos.
- El caso de corrida en curso es un resultado salteado esperado, no una falla.
- Ante una necesidad de modificar un archivo protegido, detener el trabajo y solicitar autorización explícita.
