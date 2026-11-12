# Investigación: tests de arquitectura

## Versión e integración de ArchUnit

**Decision**: Usar `com.tngtech.archunit:archunit-junit6:1.5.1` en scope `test`, junto al JUnit 6.0.3 efectivo del proyecto.

**Rationale**: La [versión 1.5.1](https://github.com/TNG/ArchUnit/releases/tag/v1.5.1) pertenece a la serie estable solicitada. La [guía oficial de ArchUnit](https://www.archunit.org/userguide/html/000_Index.html) distingue sus artefactos JUnit 5 y JUnit 6; el usuario confirmó JUnit 6.0.3 como versión efectiva del proyecto.

**Alternatives considered**: `archunit-junit5` se descartó porque no corresponde al motor JUnit 6 confirmado. La ejecución real de `@ArchTest` se verificará durante la implementación.

## Importación y ejecución de reglas

**Decision**: Usar `@AnalyzeClasses(packages = "com.example.demo", importOptions = ImportOption.DoNotIncludeTests.class)` y reglas `@ArchTest` independientes.

**Rationale**: La [guía oficial](https://www.archunit.org/userguide/html/000_Index.html) documenta la importación, las reglas de capas con `consideringOnlyDependenciesInLayers()` y los ciclos con `slices()`. Importar sólo producción evita que fixtures y controllers de prueba falseen las convenciones.

**Alternatives considered**: Arrancar el contexto Spring o usar tests de integración; no aporta a reglas de bytecode y violaría la independencia de servicios.

## Cobertura de reglas de código

**Decision**: Usar `NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS` y reglas separadas de `@Transactional` para clases y métodos de controller.

**Rationale**: La [API oficial de GeneralCodingRules](https://javadoc.io/static/com.tngtech.archunit/archunit/1.5.1/com/tngtech/archunit/library/GeneralCodingRules.html) incluye streams estándar y `Throwable.printStackTrace()`; la [API de métodos](https://www.javadoc.io/static/com.tngtech.archunit/archunit/1.5.0/com/tngtech/archunit/lang/syntax/elements/MethodsShould.html) permite verificar anotaciones de métodos.

**Alternatives considered**: Revisar sólo anotaciones de clase o sólo `System.out`; dejaría escapar infracciones solicitadas.

## Constante de correlation ID

**Decision**: La regla ArchUnit prohíbe dependencias de bytecode de controller hacia filter. Una comprobación mínima de fuente cubre la excepción exacta de `CorrelationIdFilter.HEADER` en anotaciones OpenAPI.

**Rationale**: `HEADER` está declarado `public static final String HEADER = "X-Correlation-ID"`, por lo que el compilador inserta el literal y la referencia al campo no queda disponible como dependencia ArchUnit. Permitir toda la clase `CorrelationIdFilter` en una regla de bytecode abriría más acceso del autorizado.

**Alternatives considered**: Permitir el paquete filter completo o asumir que ArchUnit distingue el campo constante; ninguna garantiza la excepción acotada.

## Ciclos de la línea base

**Decision**: Aplicar `slices` sólo a los tipos de las cinco capas centrales controller, service, repository, adapter y model. Mantener dto, exception, filter, config, security y scheduler fuera de esa regla, sin `FreezingArchRule` ni baseline de infracciones.

**Rationale**: `service ↔ dto` y `controller ↔ exception` proceden de excepciones explícitamente aceptadas y no forman parte del análisis global cuando se excluyen dto y exception. También quedan fuera las relaciones con los otros paquetes auxiliares. El único enlace `repository → service` observado para las capas centrales es la proyección `MarketPrincipal`; su traslado autorizado lo elimina. Las reglas direccionales específicas siguen aplicándose aunque los auxiliares no participen en slices.

**Alternatives considered**: Incluir todos los paquetes de primer nivel habría mezclado ciclos de mecanismos auxiliares con la separación de las cinco capas centrales. Congelar infracciones se descartó expresamente; tampoco se crea una lista de ciclos tolerados.
