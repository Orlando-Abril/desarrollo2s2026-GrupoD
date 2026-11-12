# Plan de implementación: tests de arquitectura

**Rama**: `feature/test/arquitectura-archunit` | **Spec**: [spec.md](spec.md)

## Resumen

Agregar ArchUnit 1.5.1 con integración JUnit 6 al `verify` habitual. Tres clases analizan bytecode de producción: `LayeredArchitectureTest`, `NamingConventionTest` y `CodingRulesTest`. El único cambio de producción es mover `MarketPrincipal` a `repository.market` y actualizar sus referencias.

## Diseño

- `@AnalyzeClasses(packages = "com.example.demo", importOptions = ImportOption.DoNotIncludeTests.class)` delimita producción.
- `LayeredArchitectureTest` usa `layeredArchitecture().consideringOnlyDependenciesInLayers()` para Controller, Service, Repository, Adapter, Model y DTO. Reglas adicionales restringen controller a tipos backend permitidos, model a su propio paquete y dto a las vistas de service documentadas.
- `NamingConventionTest` comprueba sufijo y anotación de controllers, interfaz y sufijo de repositorios Spring Data, y ubicación de advice.
- `CodingRulesTest` comprueba transacciones en controllers, streams estándar y ciclos. La regla de ciclos importa sólo controller, service, repository, adapter y model, incluidos subpaquetes.
- Cada regla indica su fundamento constitucional con `.because(...)`. No se usa `FreezingArchRule` ni baseline.
- Validar con `./mvnw -B verify` y revisar los reportes Surefire de las tres clases para descartar cero tests descubiertos.

## Desvíos encontrados

- Algunos controllers usan `model.League` y `model.Position` en parámetros HTTP. La regla permite sólo esos dos tipos de model.
- Algunos controllers usan `CorrelationIdFilter.HEADER` como nombre de `@Header` de OpenAPI. `forbiddenControllerDependency` permite esa clase concreta.
- `service ↔ dto` y `controller ↔ exception` son dependencias aceptadas. Por eso la regla de ciclos sólo mira controller, service, repository, adapter y model; las reglas direccionales siguen aplicándose.

## Límite de cambios

No modificar controllers ni otros paquetes de producción. El traslado de `MarketPrincipal` conserva los componentes `id` y `username`, actualiza imports de service y la expresión JPQL de `MarketUserRepository`. La CI mantiene `./mvnw -B verify`.
