# Feature 033: Validación de arquitectura del backend

**Rama**: `feature/test/arquitectura-archunit`
**Alcance**: reglas ArchUnit sobre producción. El único cambio autorizado en producción es trasladar `MarketPrincipal` a `repository.market` y actualizar sus referencias.

## Requisitos

- **FR-001**: Analizar clases de producción bajo `com.example.demo`, incluidos subpaquetes, desde tres clases: `LayeredArchitectureTest`, `NamingConventionTest` y `CodingRulesTest`.
- **FR-002**: Controller sólo puede depender de service, dto y exception. Se aceptan los usos existentes de `model.League` y `model.Position` para parámetros HTTP y de `CorrelationIdFilter.HEADER` para `@Header(name=...)` de OpenAPI. Otros tipos de model/filter y las dependencias a repository, adapter, config o security no se aceptan.
- **FR-003**: Service no accede a controller; repository no accede a service ni controller; adapter no accede a service, controller ni repository; model no accede a otros paquetes del backend.
- **FR-004**: `MarketPrincipal` reside en `repository.market`, conserva `id` y `username`, y sus referencias apuntan a la nueva ubicación.
- **FR-005**: Sólo los DTO de respuesta `QuoteRunResponse`, `ActiveStrategyResponse` y `StrategyRef` pueden depender de las vistas/resultados de service documentados.
- **FR-006**: Los controllers terminan en `Controller` y llevan `@RestController`.
- **FR-007**: Los repositorios Spring Data, identificados por herencia directa o indirecta de `Repository`, son interfaces con sufijo `Repository`. Auxiliares y proyecciones quedan fuera.
- **FR-008**: Los tipos con `@ControllerAdvice` residen en exception.
- **FR-009**: No hay ciclos entre controller, service, repository, adapter y model, incluidos subpaquetes. Dto, exception, filter, config, security y scheduler quedan fuera de esta regla de ciclos. No se usa `FreezingArchRule` ni baseline.
- **FR-010**: Los controllers no llevan `@Transactional` en clase ni método.
- **FR-011**: Producción no usa streams estándar ni `printStackTrace`.
- **FR-012**: Las tres clases se ejecutan con `./mvnw -B verify`, con al menos una regla descubierta por clase.
- **FR-013**: Se preservan los contratos funcionales y la observabilidad existentes; no se modifica frontend ni otros paquetes de producción.

## Desvíos encontrados y alcance aceptado

Los controllers actuales usan `League` y `Position` para binding/validación de parámetros HTTP. También reutilizan `CorrelationIdFilter.HEADER` en `@Header` de OpenAPI. Se documentan estas excepciones concretas sin cambiar controllers. La regla de ciclos se recorta a cinco capas porque `service ↔ dto` y `controller ↔ exception` son dependencias aceptadas; las reglas direccionales siguen vigentes.

## Criterios de éxito

`verify` pasa, los reportes de Surefire muestran reglas ejecutadas en las tres clases de arquitectura, y el diff de producción sólo contiene el traslado de `MarketPrincipal` y sus referencias.
