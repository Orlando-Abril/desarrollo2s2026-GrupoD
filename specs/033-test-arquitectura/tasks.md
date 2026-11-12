---

description: "Tareas para los tests de arquitectura del backend"
---

# Tasks: Tests de arquitectura del backend

**Input**: `specs/033-test-arquitectura/spec.md`, `plan.md`, `research.md`, `data-model.md` y `quickstart.md`.

**Prerequisites**: JDK 17, Maven Wrapper y JUnit efectivo 6.0.3.

**Tests**: Las tres suites normales analizan sólo producción. Los tests de contrato importan explícitamente fixtures negativos bajo `backend/src/test/java/com/example/demo/architecture/fixtures/`; `ImportOption.DoNotIncludeTests` los excluye del análisis normal.

**Organization**: Setup y Foundational preceden a historias independientes. Sólo el traslado de `MarketPrincipal` y sus referencias modifica producción.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Tarea paralelizable en archivos diferentes y sin dependencia pendiente.
- **[Story]**: Historia de usuario de la especificación.
- Todas las rutas son relativas a la raíz del repositorio.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Agregar la integración ArchUnit para JUnit 6.

- [X] T001 Agregar `com.tngtech.archunit:archunit-junit6:1.5.1` con scope `test` en `backend/pom.xml`, sin alterar la versión efectiva de JUnit ni el comando Maven de CI.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Confirmar descubrimiento de reglas y corregir la proyección compartida antes de las historias.

- [X] T002 Crear `backend/src/test/java/com/example/demo/architecture/LayeredArchitectureTest.java` con `@AnalyzeClasses(packages = "com.example.demo", importOptions = ImportOption.DoNotIncludeTests.class)` y una regla `@ArchTest` mínima con `.because("Constitution §2.1 ...")`; ejecutar `./mvnw -B -Dtest=LayeredArchitectureTest test` desde `backend/` y comprobar en `backend/target/surefire-reports/` que JUnit 6.0.3 ejecutó la regla sin arrancar Spring.
- [X] T003 Mover el record sin cambiar `Long id` ni `String username` desde `backend/src/main/java/com/example/demo/service/market/MarketPrincipal.java` a `backend/src/main/java/com/example/demo/repository/market/MarketPrincipal.java`.
- [X] T004 Actualizar el import y la expresión de constructor JPQL en `backend/src/main/java/com/example/demo/repository/market/MarketUserRepository.java`, y actualizar las referencias/imports en `backend/src/main/java/com/example/demo/service/market/MarketUserResolver.java`, `backend/src/main/java/com/example/demo/service/market/OrderService.java` y `backend/src/test/java/com/example/demo/service/market/OrderServiceNotQuotedTest.java` para usar la nueva ubicación de `MarketPrincipal`, sin cambiar comportamiento (depende de T003).
- [X] T005 Compilar tests y ejecutar `OrderServiceNotQuotedTest` desde `backend/` con Maven; comprobar `backend/target/surefire-reports/` y las referencias a `MarketPrincipal` en `backend/src/main/java/com/example/demo/` para confirmar que el traslado T003–T004 quedó completo antes de las historias.

**Checkpoint**: JUnit 6 descubre ArchUnit y `MarketPrincipal` reside en repository/market con todas sus referencias actualizadas. La infracción antigua `repository → service` se comprobará mediante fixture negativo, sin editar producción para recrearla.

---

## Phase 3: User Story 1 - Detectar dependencias entre capas (Priority: P1) 🎯 MVP

**Goal**: Validar la matriz de seis capas, las excepciones concretas de controller y DTO y la prohibición de dependencias inversas.

**Independent Test**: Ejecutar `LayeredArchitectureTest` y `LayeredArchitectureContractTest`; la suite normal pasa y los fixtures explícitos muestran violaciones de cada relación prohibida de FR-002/FR-003, incluido el antiguo `repository → service`.

### Tests for User Story 1

- [X] T006 [US1] Completar en `backend/src/test/java/com/example/demo/architecture/LayeredArchitectureTest.java` `layeredArchitecture().consideringOnlyDependenciesInLayers()` para Controller, Service, Repository, Adapter, Model y DTO con `whereLayer(...).mayOnlyBeAccessedByLayers(...)` según `specs/033-test-arquitectura/data-model.md`; cada regla debe llevar `.because("Constitution §2.1 ...")`.
- [X] T007 [US1] Agregar en `backend/src/test/java/com/example/demo/architecture/LayeredArchitectureTest.java` reglas para permitir desde controller sólo `model.League` y `model.Position`, prohibir otros tipos de model y las dependencias hacia filter, repository, adapter, config y security, y limitar `dto → service` a `QuoteRunResponse`, `ActiveStrategyResponse` y `StrategyRef` con sus vistas/resultados actuales. Para `CorrelationIdFilter`, inspeccionar el AST con `JavacTask`: aceptar únicamente el import de la clase y `CorrelationIdFilter.HEADER` como valor de `name` dentro de `@Header`; ignorar comentarios y literales y rechazar cualquier otro uso real. Citar Constitution §2.1/§5.3 en `.because(...)`.
- [X] T008 [P] [US1] Crear fixtures de test en `backend/src/test/java/com/example/demo/architecture/fixtures/controller/ForbiddenControllerDependencies.java`, `backend/src/test/java/com/example/demo/architecture/fixtures/service/ForbiddenServiceDependencies.java`, `backend/src/test/java/com/example/demo/architecture/fixtures/repository/ForbiddenRepositoryDependencies.java`, `backend/src/test/java/com/example/demo/architecture/fixtures/adapter/ForbiddenAdapterDependencies.java`, `backend/src/test/java/com/example/demo/architecture/fixtures/model/ForbiddenModelDependencies.java`, `backend/src/test/java/com/example/demo/architecture/fixtures/dto/ForbiddenDtoDependencies.java` y `backend/src/test/java/com/example/demo/architecture/fixtures/controller/HeaderUsageSamples.java`. Cubrir de forma aislada cada dirección prohibida de FR-002/FR-003, el antiguo `repository → service`, un model/filter no permitido, los enums/header permitidos y los casos de `@Header` válido, uso real inválido y nombres sólo en comentarios/literales. Mantener todos los fixtures bajo `src/test/java`.
- [X] T009 [US1] Crear `backend/src/test/java/com/example/demo/architecture/LayeredArchitectureContractTest.java` para importar explícitamente, por clases, los fixtures de T008 y evaluar las mismas reglas de `LayeredArchitectureTest.java`: cada caso negativo debe identificar origen y destino esperados; los casos permitidos no deben producir violaciones. Incluir contratos del analizador AST para `@Header` que confirmen ausencia de falsos positivos por comentarios o literales (depende de T006–T008).
- [X] T010 [US1] Ejecutar `./mvnw -B -Dtest=LayeredArchitectureTest,LayeredArchitectureContractTest test` desde `backend/` y revisar `backend/target/surefire-reports/`: confirmar ejecución de la suite normal y contratos de SC-001/SC-002, que los fixtures no entran en el análisis normal y que ningún test requiere Spring o BD; reportar cualquier desvío no listado antes de actuar.

**Checkpoint**: US1 pasa independientemente sobre la base Foundational; las relaciones prohibidas se detectan con fixtures de test.

---

## Phase 4: User Story 2 - Proteger convenciones y evitar ciclos (Priority: P1)

**Goal**: Validar nombres, anotaciones, ubicación de advice y ausencia de ciclos entre las cinco capas centrales.

**Independent Test**: Ejecutar `NamingConventionTest`, la regla de slices de `CodingRulesTest` y `NamingAndCycleContractTest`; los casos negativos fallan por su regla y los paquetes auxiliares quedan fuera de slices.

### Tests for User Story 2

- [X] T011 [P] [US2] Crear `backend/src/test/java/com/example/demo/architecture/NamingConventionTest.java` con `@AnalyzeClasses(packages = "com.example.demo", importOptions = ImportOption.DoNotIncludeTests.class)` y reglas `@ArchTest` para clases de controller terminadas en `Controller` con `@RestController`, interfaces que extienden `org.springframework.data.repository.Repository` directa o indirectamente terminadas en `Repository`, y `@ControllerAdvice` sólo en `..exception..`; excluir auxiliares/proyecciones de la selección Spring Data y citar Constitution §2.1 mediante `.because(...)`.
- [X] T012 [P] [US2] Crear `backend/src/test/java/com/example/demo/architecture/CodingRulesTest.java` con `@AnalyzeClasses(packages = "com.example.demo", importOptions = ImportOption.DoNotIncludeTests.class)` y `slices().matching("com.example.demo.(*)..").should().beFreeOfCycles()` sobre sólo controller, service, repository, adapter y model; parametrizar la raíz para reutilizar la misma regla con fixtures bajo `com.example.demo.architecture.fixtures.(*)..`; excluir dto, exception, filter, config, security y scheduler únicamente de slices y citar Constitution §2.1 con `.because(...)`.
- [X] T013 [P] [US2] Crear fixtures separados en `backend/src/test/java/com/example/demo/architecture/fixtures/controller/WrongName.java`, `backend/src/test/java/com/example/demo/architecture/fixtures/controller/MissingRestAnnotationController.java`, `backend/src/test/java/com/example/demo/architecture/fixtures/repository/ClassBasedRepository.java`, `backend/src/test/java/com/example/demo/architecture/fixtures/repository/WrongSuffixSpringData.java`, `backend/src/test/java/com/example/demo/architecture/fixtures/advice/MisplacedAdvice.java`, `backend/src/test/java/com/example/demo/architecture/fixtures/controller/CycleController.java` y `backend/src/test/java/com/example/demo/architecture/fixtures/service/CycleService.java`; cubrir nombre, `@RestController`, interfaz/sufijo Spring Data, ubicación de advice y ciclo controller↔service por separado. Agregar el auxiliar permitido `backend/src/test/java/com/example/demo/architecture/fixtures/repository/AllowedRepositoryHelper.java`.
- [X] T014 [US2] Crear `backend/src/test/java/com/example/demo/architecture/NamingAndCycleContractTest.java` que importe explícitamente los fixtures de T013, evalúe las mismas reglas de `NamingConventionTest.java` y `CodingRulesTest.java`, compruebe violación identificable de cada convención y del ciclo central, y confirme que `AllowedRepositoryHelper` no viola la convención Spring Data (depende de T011–T013).
- [X] T015 [US2] Ejecutar `./mvnw -B -Dtest=NamingConventionTest,NamingAndCycleContractTest,CodingRulesTest test` desde `backend/`; revisar `backend/target/surefire-reports/` para confirmar contratos SC-003, exclusión de fixtures de la suite normal, que los ciclos existentes `service ↔ dto` y `controller ↔ exception` no causan fallos de slices, y ausencia de `FreezingArchRule` o baseline.

**Checkpoint**: US2 pasa independientemente sobre la base Foundational, sin requerir reglas de US1.

---

## Phase 5: User Story 3 - Mantener transacciones y observabilidad coherentes (Priority: P2)

**Goal**: Prohibir transacciones en controllers y salidas a streams estándar, preservando la observabilidad existente.

**Independent Test**: Ejecutar `CodingRulesTest` y `CodingRulesContractTest`; cada tipo de infracción de SC-004 se detecta en un fixture aislado.

### Tests for User Story 3

- [X] T016 [US3] Agregar en `backend/src/test/java/com/example/demo/architecture/CodingRulesTest.java` reglas para prohibir `@Transactional` en clases controller y en sus métodos declarados, más `GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS` para `System.out`, `System.err` y `Throwable.printStackTrace()`; cada regla debe citar Constitution §2.1 o §5.3 mediante `.because(...)` (depende de T012).
- [X] T017 [P] [US3] Crear fixtures negativos separados en `backend/src/test/java/com/example/demo/architecture/fixtures/controller/TransactionalClassController.java`, `backend/src/test/java/com/example/demo/architecture/fixtures/controller/TransactionalMethodController.java`, `backend/src/test/java/com/example/demo/architecture/fixtures/service/StandardOutCall.java`, `backend/src/test/java/com/example/demo/architecture/fixtures/service/StandardErrCall.java` y `backend/src/test/java/com/example/demo/architecture/fixtures/service/PrintStackTraceCall.java`, cada uno con una sola infracción de SC-004.
- [X] T018 [US3] Crear `backend/src/test/java/com/example/demo/architecture/CodingRulesContractTest.java` que importe explícitamente cada fixture de T017 y confirme que la regla correspondiente reporta la infracción esperada; ejecutar `./mvnw -B -Dtest=CodingRulesTest,CodingRulesContractTest test` desde `backend/` y revisar `backend/target/surefire-reports/` para comprobar contratos SC-004 y exclusión de fixtures del análisis normal. Registrar en `specs/033-test-arquitectura/validation-results.md` los resultados de contratos de SC-001 a SC-004, con el fixture, la regla, la infracción esperada y el reporte correspondiente (depende de T016–T017 y de T010/T015).

**Checkpoint**: US3 pasa independientemente una vez creada la regla de slices en T012; no modifica logs, correlation ID, salud ni métricas.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Ejecutar toda la suite, registrar los resultados de los contratos y revisar el alcance.

- [X] T019 Ejecutar `./mvnw -B '-Dtest=LayeredArchitectureTest,LayeredArchitectureContractTest,NamingConventionTest,NamingAndCycleContractTest,CodingRulesTest,CodingRulesContractTest' test` y `./mvnw -B verify` desde `backend/`; completar `specs/033-test-arquitectura/validation-results.md` con los casos positivos, número de reglas descubiertas, resultado final, referencias a `backend/target/surefire-reports/` y ausencia de arranque de Spring/BD. Confirmar que `.github/workflows/ci.yml` conserva `./mvnw -B verify`.
- [X] T020 Revisar `git status --short` y `git diff` contra `specs/033-test-arquitectura/spec.md` y `backend/src/main/java/com/example/demo/`: confirmar que producción sólo cambia por el traslado de `MarketPrincipal` y sus referencias, que fixtures y contratos residen bajo `backend/src/test/java/com/example/demo/architecture/`, y que no se modificaron controllers, frontend, seguridad, logs ni contratos externos. Reportar cualquier desvío no listado antes de corregirlo.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup**: T001 inicia sin dependencias.
- **Foundational**: T002 depende de T001; T003 depende de T002; T004 depende de T003; T005 valida T003–T004. Completar T005 antes de cualquier historia.
- **US1**: T006–T007 definen reglas; T008 puede avanzar en paralelo porque sólo crea fixtures; T009 depende de T006–T008; T010 valida.
- **US2**: T011, T012 y T013 pueden avanzar en paralelo tras T005; T014 depende de esos tres; T015 valida.
- **US3**: T016 depende de T012 porque amplía `CodingRulesTest.java`; T017 puede avanzar en paralelo; T018 depende de T016–T017 y de los resultados de T010/T015 para registrar SC-001 a SC-004.
- **Polish**: T019–T020 dependen de las tres historias.

### User Story Dependencies

- **US1 (P1)**: Independiente después de Foundational; MVP.
- **US2 (P1)**: Independiente después de Foundational; el traslado de `MarketPrincipal` ya no pertenece a US1.
- **US3 (P2)**: Reutiliza el archivo `CodingRulesTest.java` creado en US2, pero sus reglas y contratos se verifican por separado.

### Parallel Opportunities

- T008 puede preparar fixtures mientras T006–T007 implementan las reglas de capas.
- T011, T012 y T013 trabajan en archivos distintos tras T005.
- T017 puede preparar fixtures mientras T016 agrega reglas de código.
- Ejecutar comandos Maven secuencialmente en un mismo workspace para evitar escrituras simultáneas en `backend/target/`.

## Parallel Example: User Story 1

```text
Tras T005:
T006–T007 → backend/src/test/java/com/example/demo/architecture/LayeredArchitectureTest.java
T008      → backend/src/test/java/com/example/demo/architecture/fixtures/
T009      → backend/src/test/java/com/example/demo/architecture/LayeredArchitectureContractTest.java
```

## Parallel Example: User Story 2

```text
Tras T005:
T011 → backend/src/test/java/com/example/demo/architecture/NamingConventionTest.java
T012 → backend/src/test/java/com/example/demo/architecture/CodingRulesTest.java
T013 → backend/src/test/java/com/example/demo/architecture/fixtures/
```

## Parallel Example: User Story 3

```text
Tras T012:
T016 → backend/src/test/java/com/example/demo/architecture/CodingRulesTest.java
T017 → backend/src/test/java/com/example/demo/architecture/fixtures/
T018 → backend/src/test/java/com/example/demo/architecture/CodingRulesContractTest.java
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar T001–T005, incluido el traslado autorizado compartido.
2. Completar T006–T010 y validar las reglas normales y los contratos negativos de US1.
3. Avanzar a US2 sólo cuando los resultados de US1 sean identificables y reproducibles.

### Incremental Delivery

1. Agregar US2 y probar convenciones/ciclos sin depender de la implementación de US1.
2. Agregar US3 y probar transacciones/streams con fixtures aislados.
3. Ejecutar T019–T020; sólo `verify` con reglas y contratos descubiertos y sin infracciones satisface Constitution §3.1.

## Notes

- Los fixtures son exclusivamente de test y no se incluyen en el análisis normal de producción. Los contratos los importan explícitamente.
- Para slices, la única variación entre producción y fixtures es la raíz de paquete; se prueba el mismo constructor de regla.
- Todos los casos negativos permanecen en `src/test/java`; no se hacen copias del repositorio ni cambios temporales a producción.
- Cada regla cita Constitution §2.1, §3.1 o §5.3 mediante `.because(...)` según su propósito.
- No crear `FreezingArchRule`, baseline ni excepciones generales para paquetes auxiliares.
- Si surge un desvío distinto de los documentados, reportarlo primero; sólo corregir un import mal ubicado dentro del alcance autorizado.
