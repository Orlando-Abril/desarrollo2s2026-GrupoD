# Feature Specification: Validación de arquitectura del backend

**Feature Branch**: `feature/test/arquitectura-archunit`

**Created**: 2026-11-11

**Status**: Ready for task generation

**Input**: Tests de arquitectura para las capas y convenciones del backend, integrados a la verificación habitual. Sólo está autorizado trasladar `MarketPrincipal` y actualizar sus referencias.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Detectar dependencias entre capas (Priority: P1)

Como integrante del equipo quiero detectar dependencias prohibidas entre capas para impedir nuevos acoplamientos y cumplir la constitución.

**Why this priority**: La separación de capas es el objetivo principal.

**Independent Test**: Introducir aisladamente cada relación prohibida y comprobar que la verificación falla e identifica origen y destino.

**Acceptance Scenarios**:

1. **Given** un controller que referencia un repository, **When** se ejecuta la verificación, **Then** falla e identifica la dependencia.
2. **Given** un service que referencia controller, un repository que referencia service o controller, o un adapter que referencia service, controller o repository, **When** se verifica, **Then** falla por cada relación prohibida.
3. **Given** una clase de model que referencia otra capa de primer nivel, **When** se verifica, **Then** falla; las referencias internas a model se permiten.
4. **Given** la proyección de mercado, **When** se verifica repository, **Then** `MarketPrincipal` reside en repository/market, conserva `id` y `username`, y repository no depende de service para esa proyección.
5. **Given** un controller que usa `model.League` o `model.Position` para binding o validación de parámetros HTTP, o `CorrelationIdFilter.HEADER` para documentación OpenAPI, **When** se verifica, **Then** esas referencias concretas se aceptan; una referencia a cualquier otro tipo de model o filter falla.

---

### User Story 2 - Proteger convenciones y evitar ciclos (Priority: P1)

Como integrante del equipo quiero detectar nombres, anotaciones, ubicación de componentes y ciclos incorrectos para mantener la estructura comprensible.

**Why this priority**: Estas reglas permiten revisar la dirección de las dependencias.

**Independent Test**: Introducir aisladamente un incumplimiento de cada convención y un ciclo entre dos capas centrales; comprobar que se detectan.

**Acceptance Scenarios**:

1. **Given** una clase controller sin sufijo `Controller` o sin `@RestController`, **When** se verifica, **Then** falla con la clase identificada.
2. **Given** un repositorio de persistencia Spring Data que extiende `org.springframework.data.repository.Repository` directa o indirectamente, **When** se verifica, **Then** debe ser interfaz y terminar en `Repository`; de lo contrario falla con el tipo identificado.
3. **Given** un auxiliar de consulta o una proyección bajo repository, **When** se verifica la convención de repositorios, **Then** queda fuera de la regla de interfaz y sufijo.
4. **Given** un `@ControllerAdvice` fuera de exception, **When** se verifica, **Then** falla.
5. **Given** un ciclo entre controller, service, repository, adapter o model, **When** se verifica, **Then** falla e identifica el ciclo.
6. **Given** una relación con dto, exception, filter, config, security o scheduler, **When** se verifica la regla de ciclos, **Then** ese paquete auxiliar queda fuera de esa regla, sin eximirlo de las reglas direccionales que le correspondan.

---

### User Story 3 - Mantener transacciones y observabilidad coherentes (Priority: P2)

Como integrante del equipo quiero impedir transacciones en controllers y salidas directas a consola para mantener esas responsabilidades en servicios y en el registro estructurado.

**Why this priority**: Respalda la separación de responsabilidades y la observabilidad requerida.

**Independent Test**: Añadir aisladamente `@Transactional` a un controller y llamadas a `System.out` y `printStackTrace`; confirmar que cada caso falla.

**Acceptance Scenarios**:

1. **Given** un controller con `@Transactional`, **When** se verifica, **Then** falla.
2. **Given** código de producción que llama a `System.out` o `printStackTrace`, **When** se verifica, **Then** falla.
3. **Given** el backend sin infracciones, **When** se ejecuta la verificación habitual, **Then** las reglas corren sin iniciar la aplicación ni conectarse a una base de datos.

### Edge Cases

- Las dependencias transitivas no deben confundirse con referencias directas; cada regla debe precisar qué relación prohíbe.
- Los subpaquetes pertenecen a su capa central de primer nivel; un ciclo entre capas centrales que los atraviesa debe detectarse.
- Los ciclos existentes `service ↔ dto` y `controller ↔ exception` quedan fuera de la regla de ciclos entre capas centrales por las excepciones aceptadas, no por una lista de infracciones congeladas. Lo mismo ocurre con otros paquetes auxiliares fuera de esa regla; sus restricciones direccionales siguen vigentes.
- Los DTO de respuesta pueden construir su forma a partir de vistas de service; esto no autoriza dependencias de repository hacia service.
- Los auxiliares de consulta y proyecciones bajo repository, como `PlayerSpecifications` y `MarketPrincipal`, no son repositorios de persistencia Spring Data y quedan fuera de la convención de interfaz y sufijo.
- La verificación no debe requerir servidor, credenciales ni datos de prueba.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: La verificación MUST cubrir el código de producción bajo `com.example.demo`, incluidos los subpaquetes de cada paquete de primer nivel.
- **FR-002**: Entre los paquetes del backend, los controllers MUST depender directamente sólo de service, dto y exception, con estas dos excepciones estrictas: MAY referenciar `model.League` y `model.Position` para binding y validación de parámetros HTTP; MAY referenciar `filter.CorrelationIdFilter.HEADER` exclusivamente para reutilizar el nombre del header en documentación OpenAPI. MUST NOT depender de otros tipos de model o filter, ni de repository, adapter, config o security. Estas excepciones no autorizan cambios en controllers existentes.
- **FR-003**: Service MUST NOT depender de controller; repository MUST NOT depender de service ni controller; model MUST NOT depender de ninguna otra capa de primer nivel; adapter MUST NOT depender de service, controller ni repository.
- **FR-004**: `MarketPrincipal` MUST residir en repository/market como proyección con los mismos componentes (`id`, `username`); todas sus referencias, incluida la consulta que lo construye, MUST apuntar a la nueva ubicación sin cambiar su comportamiento.
- **FR-005**: Los DTO de respuesta `QuoteRunResponse`, `ActiveStrategyResponse` y `StrategyRef` MAY depender de vistas o resultados de service para construir su forma. La excepción MUST explicarse en la regla y limitarse a DTO de respuesta, sin relajar las prohibiciones sobre repository, model o adapter.
- **FR-006**: Las clases de controller MUST terminar en `Controller` y llevar `@RestController`.
- **FR-007**: Todo repositorio de persistencia Spring Data que extienda `org.springframework.data.repository.Repository`, de forma directa o indirecta, MUST ser una interfaz y terminar en `Repository`. Los auxiliares de consulta y las proyecciones bajo repository MUST quedar fuera de esta convención.
- **FR-008**: Todo tipo con `@ControllerAdvice` MUST residir bajo exception.
- **FR-009**: Las capas arquitectónicas centrales controller, service, repository, adapter y model MUST estar libres de ciclos de dependencias entre sí, incluidos sus subpaquetes. La regla de ciclos MUST excluir dto, exception, filter, config, security y scheduler, sin desactivar las reglas direccionales específicas que los afecten; MUST NOT usar `FreezingArchRule` ni una baseline de infracciones.
- **FR-010**: Los controllers MUST NOT usar `@Transactional`; el límite transaccional corresponde a service.
- **FR-011**: El código de producción MUST NOT usar `System.out` ni `printStackTrace`; los mensajes MUST seguir el registro estructurado existente.
- **FR-012**: Todas las reglas MUST ejecutarse en el flujo habitual de `mvn verify` del backend, sin iniciar Spring ni una base de datos.
- **FR-013**: La feature MUST conservar JSON de logs, `correlationId`, JWT/API key, rutas públicas, respuestas 401/403 JSON, salud y métricas existentes; no requiere cambios funcionales en esos mecanismos.
- **FR-014**: Los únicos cambios autorizados en código de producción previo MUST ser el traslado de `MarketPrincipal` y la actualización necesaria de sus referencias. No se incluyen reglas del frontend ni cambios en otros paquetes.
- **FR-015**: La suite MUST usar `com.tngtech.archunit:archunit-junit6:1.5.1` en scope `test`, compatible con JUnit 6.0.3 efectivo del proyecto.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las relaciones prohibidas en FR-002 y FR-003 produce un fallo identificable al introducir una infracción aislada.
- **SC-002**: Las tres referencias concretas permitidas en FR-002 pasan la verificación, mientras que una referencia aislada a otro tipo de model o filter desde controller falla.
- **SC-003**: El 100% de las convenciones de FR-006 a FR-008 y de los ciclos entre capas centrales de FR-009 produce un fallo identificable al introducir una infracción aislada; los paquetes auxiliares no producen falsos fallos en la regla de ciclos.
- **SC-004**: El 100% de los usos aislados de transacciones en controllers, `System.out` y `printStackTrace` se detecta.
- **SC-005**: Una ejecución completa de la verificación habitual ejecuta todas las reglas sin iniciar servicios ni disponer de datos externos.
- **SC-006**: El reporte de la verificación muestra ejecución real de las tres clases de arquitectura bajo JUnit 6.0.3; cero reglas descubiertas no cuenta como éxito.
- **SC-007**: La revisión del cambio confirma cero modificaciones de comportamiento en autenticación, observabilidad y contratos, y sólo el traslado autorizado en código de producción.

## Assumptions

- Las reglas cubren el código de producción del backend; pruebas y bibliotecas externas no son capas del proyecto.
- Una dependencia es una referencia de código entre tipos o miembros; las reglas también cubren subpaquetes.
- La excepción de DTO permite construir respuestas desde vistas de service; no constituye una excepción general.
- La convención de repositorios se determina por la relación de herencia con el contrato de persistencia Spring Data, no por residir en el paquete repository.
- La exclusión de paquetes auxiliares se limita al análisis de ciclos. En particular, los accesos permitidos de dto a service y de exception a controller no cambian las prohibiciones direccionales de controller, service, repository, adapter y model.
- Los mecanismos actuales de logs, seguridad, salud y métricas son contexto y no requieren pruebas de comportamiento adicionales.

## Dependencies

- Constitución del proyecto, §§2.1 y 5.3.
- Estructura de paquetes y verificación habitual del backend.

## Out of Scope

- Reglas de arquitectura del frontend.
- Cambios funcionales en seguridad, observabilidad, endpoints, persistencia o negocio.
- Refactorizaciones de código de producción distintas del traslado autorizado de `MarketPrincipal` y sus referencias.
