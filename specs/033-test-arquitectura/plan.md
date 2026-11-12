# Implementation Plan: Tests de arquitectura del backend

**Branch**: `feature/test/arquitectura-archunit` | **Date**: 2026-11-11 | **Spec**: [spec.md](spec.md)

**Input**: Especificación de `specs/033-test-arquitectura/spec.md` y decisiones de planificación del usuario.

## Summary

Agregar reglas de arquitectura sobre bytecode de producción con ArchUnit 1.5.1 y su integración JUnit 6, ejecutadas por el `verify` habitual. Tres clases de reglas cubrirán capas, convenciones y reglas de código; tests de contrato importarán fixtures negativos de test explícitamente para demostrar que las reglas fallan cuando corresponde. El traslado de `MarketPrincipal` y sus referencias es un prerrequisito compartido de las historias. La regla de ciclos se limita a controller, service, repository, adapter y model.

## Technical Context

**Language/Version**: Java 17.

**Primary Dependencies**: Spring Boot 4.1.1; JUnit 6.0.3 efectivo; `com.tngtech.archunit:archunit-junit6:1.5.1` en scope `test`.

**Storage**: No aplica a estas pruebas; el backend conserva su persistencia existente.

**Testing**: JUnit, ArchUnit `@AnalyzeClasses(packages = "com.example.demo", importOptions = ImportOption.DoNotIncludeTests.class)`, `@ArchTest`, Maven `verify`.

**Target Platform**: JVM en desarrollo local y CI de GitHub Actions.

**Project Type**: Backend de aplicación web; la feature es una suite interna de calidad.

**Performance Goals**: Una sola importación de clases por suite; sin arranque de Spring, contenedores ni base de datos.

**Constraints**: No usar `FreezingArchRule`. Cada regla llevará `.because("...")` con referencia a la constitución. No cambiar controllers ni otros paquetes de producción. Ante desvío no listado, reportar primero; corregir sólo si se trata de un import mal ubicado y está autorizado.

**Validation Fixtures**: Casos negativos exclusivamente en `backend/src/test/java/com/example/demo/architecture/fixtures/`; la importación normal con `ImportOption.DoNotIncludeTests` los excluye. Los tests de contrato los importan por clase explícita y comprueban la infracción esperada, sin alterar producción ni usar copias del repositorio.

**Scale/Scope**: Todos los tipos de producción bajo `com.example.demo` y sus subpaquetes; seis capas en la regla de capas. La regla de ciclos considera sólo controller, service, repository, adapter y model, incluidos sus subpaquetes; excluye dto, exception, filter, config, security y scheduler.

## Constitution Check

*GATE: Revisado antes de la investigación y nuevamente después del diseño.*

| Principio | Comprobación del diseño | Estado |
|-----------|-------------------------|--------|
| §2.1 Backend en capas estricto | Reglas de acceso entre Controller, Service, Repository, Adapter, Model y DTO, con excepciones concretas de la especificación; traslado de la proyección al paquete correcto; ciclos sólo entre las cinco capas centrales. | Diseño alineado. |
| §3.1 Integración continua | `archunit-junit6:1.5.1` corresponde al JUnit 6.0.3 efectivo; las pruebas se descubren y ejecutan en `backend/./mvnw -B verify`, el comando actual de CI. | Diseño alineado; el descubrimiento real se comprobará al implementar. |
| §5.3 Observabilidad | Prohibir streams estándar y `printStackTrace`, preservando logs estructurados y correlation ID existentes. | Diseño alineado. |
| Límite de cambios | Producción: sólo traslado de `MarketPrincipal` y actualización de referencias; no se modifican controllers. | Diseño alineado. |

**Resultado del gate, antes de la investigación y después del diseño**: Sin aclaraciones ni bloqueos de diseño. Las reglas direccionales siguen cubriendo los paquetes auxiliares según la especificación. No se introduce `FreezingArchRule` ni una baseline. El resultado de `verify` deberá comprobarse al implementar.

## Diseño de reglas

### LayeredArchitectureTest

- Definir `layeredArchitecture().consideringOnlyDependenciesInLayers()` sobre `..controller..`, `..service..`, `..repository..`, `..adapter..`, `..model..` y `..dto..`, incluidos subpaquetes. Expresar las restricciones de acceso entrante con `whereLayer(...).mayOnlyBeAccessedByLayers(...)`; una tabla de relaciones en [data-model.md](data-model.md) fija el contrato.
- La regla de capas por sí sola no expresa la restricción de miembros de `CorrelationIdFilter.HEADER` ni restringe `controller → model` a dos enums. Añadir reglas de dependencia específicas: sólo `League` y `Position` desde controller hacia model; prohibir dependencias de bytecode hacia filter desde controller. La constante `HEADER` es un `String` constante y Java la inserta en el bytecode, así que sus usos actuales pasan sin abrir el paquete filter. Inspeccionar el árbol sintáctico de fuentes Java de controllers con la API `JavacTask` del JDK 17: permitir el import de `CorrelationIdFilter` y el acceso `CorrelationIdFilter.HEADER` únicamente como valor del atributo `name` de una anotación `@Header`; rechazar cualquier otro uso real de `CorrelationIdFilter`. La inspección del árbol ignora comentarios y literales de texto, sin basarse sólo en búsqueda textual ni añadir dependencias. No abrir excepciones por paquete completo.
- Permitir `dto → service` sólo para `QuoteRunResponse`, `ActiveStrategyResponse` y `StrategyRef` hacia las vistas/resultados existentes; documentarlo con `.because(...)`. No permitir una excepción análoga a repository.
- Como prerrequisito Foundational, trasladar `MarketPrincipal` sin cambiar los componentes del record; actualizar imports en service y la expresión de constructor JPQL en `MarketUserRepository`. Un fixture negativo de repository → service reproduce el antiguo desvío para probar la regla, sin editar producción.

### NamingConventionTest

- Tipos de `..controller..` de producción: nombre terminado en `Controller` y `@RestController`.
- Sólo los tipos asignables a `org.springframework.data.repository.Repository` (herencia directa o indirecta) deben ser interfaces y terminar en `Repository`. `PlayerSpecifications`, `MarketPrincipal` y otras proyecciones o auxiliares no entran en esta selección.
- Todo tipo con `@ControllerAdvice` debe residir en `..exception..`.

### CodingRulesTest

- Ejecutar `slices().matching("com.example.demo.(*)..").should().beFreeOfCycles()` sobre el subconjunto importado de tipos de producción de controller, service, repository, adapter y model. Excluir de **esta regla únicamente** dto, exception, filter, config, security y scheduler. Las reglas direccionales independientes siguen evaluando los paquetes auxiliares pertinentes.
- `GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS` para `System.out`, `System.err` y `Throwable.printStackTrace()`.
- Controllers sin `@Transactional` a nivel clase **ni método**; la regla indicada para clases se complementa con una regla sobre métodos declarados en controllers.
- Cada regla termina en `.because("Constitution §2.1 ...")`, `§3.1` o `§5.3` según corresponda.

### Tests de contrato de las reglas

- Agregar `LayeredArchitectureContractTest`, `NamingAndCycleContractTest` y `CodingRulesContractTest`, con fixtures de test bajo `architecture/fixtures/`. Las tres clases de reglas siguen importando sólo producción; cada contrato importa sus fixtures explícitamente y evalúa las **mismas reglas**. Para slices, parametrizar únicamente la raíz del paquete: `com.example.demo` en producción y `com.example.demo.architecture.fixtures` en el contrato, conservando la selección de las cinco capas centrales.
- Cubrir con casos aislados las familias SC-001 (dependencias prohibidas, incluido el antiguo `repository → service`), SC-002 (enums y header permitidos frente a otros tipos/usos de model y filter), SC-003 (nombres, anotaciones, ubicación de advice y ciclo central) y SC-004 (`@Transactional` de clase/método, streams y `printStackTrace`). Los casos positivos y los negativos deben comprobar explícitamente presencia o ausencia de violaciones y origen/regla esperados.
- Los fixtures de fuente para la regla de `@Header` incluirán un uso permitido, un uso real prohibido y comentarios/literales que nombren `CorrelationIdFilter`; estos últimos no deben causar un falso positivo. No se crearán copias descartables ni modificaciones temporales de producción.

## Ciclos existentes fuera del alcance de slices

La revisión estática de imports muestra `service ↔ dto` y `controller ↔ exception`, derivados de excepciones explícitamente aceptadas. Quedan fuera del análisis de ciclos porque dto y exception no participan en la regla de slices; no se usa `FreezingArchRule` ni una baseline. También quedan fuera de esa regla relaciones con filter, config, security y scheduler, incluidos `adapter ↔ config` y `security ↔ config`. Se conservan las reglas direccionales específicas sobre estos paquetes, por ejemplo la prohibición de `repository → service` y de `controller → config/security`. Después de mover `MarketPrincipal`, la revisión de imports no muestra un ciclo entre las cinco capas centrales.

La versión efectiva JUnit 6.0.3 está confirmada. Usar `archunit-junit6:1.5.1` en scope `test`. El quickstart exige comprobar que `@ArchTest` se descubra y ejecute; esto es validación de implementación, no una decisión de diseño pendiente.

## Project Structure

### Documentation (this feature)

```text
specs/033-test-arquitectura/
├── spec.md
├── checklists/requirements.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── validation-results.md # se crea en la implementación tras ejecutar contratos y verify
```

### Source Code (repository root)

```text
backend/
├── pom.xml
├── src/main/java/com/example/demo/
│   ├── repository/market/MarketPrincipal.java   # traslado autorizado
│   ├── repository/market/MarketUserRepository.java
│   └── service/market/                         # referencias actualizadas
└── src/test/java/com/example/demo/architecture/
    ├── LayeredArchitectureTest.java
    ├── NamingConventionTest.java
    ├── CodingRulesTest.java
    ├── LayeredArchitectureContractTest.java
    ├── NamingAndCycleContractTest.java
    ├── CodingRulesContractTest.java
    └── fixtures/                 # fuentes de test importadas explícitamente en contratos
```

**Structure Decision**: Suite interna del backend, junto a las pruebas existentes. No se crean contratos HTTP ni artefactos de frontend.

## Complexity Tracking

No se solicita una desviación nueva de la constitución. El alcance de slices se define por las cinco capas centrales y las excepciones explícitas de la especificación; no hay bloqueos de diseño pendientes.
