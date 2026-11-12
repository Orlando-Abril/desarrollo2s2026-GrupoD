# Tareas: feature 033

- [X] T001 Agregar `archunit-junit6:1.5.1` en scope test y confirmar JUnit 6 efectivo.
- [X] T002 Trasladar `MarketPrincipal` a `repository.market` conservando `id` y `username`; actualizar imports y JPQL.
- [X] T003 Crear `LayeredArchitectureTest` con capas y restricciones concretas de controller, model y DTO.
- [X] T004 Crear `NamingConventionTest` para controllers, repositorios Spring Data y advice.
- [X] T005 Crear `CodingRulesTest` para transacciones, streams estándar y ciclos entre las cinco capas centrales.
- [X] T006 Documentar los desvíos existentes: `League` y `Position` en controllers, `CorrelationIdFilter.HEADER` en OpenAPI y el recorte de ciclos por `service ↔ dto` y `controller ↔ exception`.
- [X] T007 Ejecutar `./mvnw -B verify` desde `backend/` y registrar el resultado y los conteos de las tres clases en [validation-results.md](validation-results.md).
- [X] T008 Revisar el diff final: producción sólo incluye el traslado de `MarketPrincipal` y sus referencias; `DemoApplicationTests` coincide exactamente con `origin/main`.
