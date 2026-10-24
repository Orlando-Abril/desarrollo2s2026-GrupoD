# Research: Historial de cotizaciones de un jugador

**Feature**: 017-historial-cotizaciones  
**Date**: 2026-10-24

No quedan `NEEDS CLARIFICATION`. Las decisiones se tomaron contra el código presente en `feature/historial-cotizaciones`, las features 013/016 y la constitución 1.1.0.

## D1. Reutilizar el QuoteController de 016

**Decision**: Ampliar el `QuoteController` existente. Quitar su `@RequestMapping("/quotes")`, mover el path completo al método de recálculo (`POST /quotes/recalculate`) y agregar `GET /players/{id}/quotes` en la misma clase.

**Rationale**: La rama ya contiene el controller creado por 016. La nueva operación pertenece funcionalmente a cotizaciones, y esta adaptación permite exponer una ruta hermana bajo `/players` sin tocar el `PlayerController` protegido. El matcher de seguridad del POST usa el path completo y no cambia.

**Alternatives considered**:
- Agregar el GET a `PlayerController`: rechazado por la regla explícita de archivos protegidos y por mezclar responsabilidades.
- Crear `PlayerQuoteController`: técnicamente válido, pero duplica el área de cotizaciones y contradice la preferencia del equipo por `QuoteController` cuando 016 ya está integrada.
- Mantener el mapping de clase y usar un path absoluto en el método: rechazado porque Spring concatena los mappings de clase y método.

## D2. Un único método HTTP para dos shapes

**Decision**: Usar un método GET con retorno de borde `Object`; delegar en dos operaciones tipadas del servicio según la presencia de `at`. Documentar 200 como `oneOf` entre objeto `PlayerQuoteResponse` y array de ese esquema.

**Rationale**: El contrato exige lista para historial y objeto único para vigencia sobre la misma ruta. Un único mapping evita duplicidad de operaciones en OpenAPI y centraliza la regla de exclusión entre `at` y el rango. La falta de tipo está confinada al controller; servicio y DTO siguen tipados.

**Alternatives considered**:
- Dos métodos con `params="at"` y `params="!at"`: Spring puede resolverlos, pero springdoc debe fusionarlos en una sola operación y aumenta el riesgo de documentación incompleta.
- Envolver siempre en un objeto estable: más limpio para evolución, pero incompatible con la especificación aprobada de lista/objeto.
- Devolver siempre una lista: contradice la semántica solicitada para `at`.

## D3. Reutilizar las consultas de 013 sin cambiar el repositorio

**Decision**: `QuoteQueryService.history(...)` usa la consulta de rango ordenada de 013 con límites finitos cuando `from` o `to` faltan. Para un `to` presente consulta hasta el inicio del día siguiente y filtra el instante frontera, conservando el límite superior exclusivo. `quoteAt(...)` usa directamente `findEffectiveOn(...)`.

**Rationale**: La feature 013 ya cubrió las consultas, índices, desempates y compatibilidad PostgreSQL/H2. Consultar hasta la frontera y filtrar sólo la igualdad evita restar nanosegundos —operación que PostgreSQL podría redondear al día siguiente— y no pierde cotizaciones dentro del día. Los límites `0001-01-01` y `9999-12-31` son finitos y soportados por los motores del proyecto.

**Alternatives considered**:
- Agregar nuevas consultas para intervalos abiertos: innecesario para el alcance y obligaría a ampliar nuevamente la superficie de repositorio.
- Usar `Instant.MIN`/`Instant.MAX`: rechazado porque exceden el rango habitual de timestamps SQL/JDBC.
- Restar un nanosegundo al inicio del día siguiente: rechazado por el redondeo de precisión documentado en el propio repositorio.
- Cargar todo y filtrar todo en memoria: funcional, pero desaprovecha el índice y escala peor.

## D4. El servicio devuelve el DTO público

**Decision**: `QuoteQueryService.history(...)` devuelve `List<PlayerQuoteResponse>` y `quoteAt(...)` devuelve `PlayerQuoteResponse`. El record aporta un mapper `from(PlayerQuote)`.

**Rationale**: El patrón existente de `PlayerCatalogQueryService` ya devuelve DTOs desde una transacción de lectura. Mapear dentro del servicio evita exponer entidades y proxies JPA fuera de la transacción y deja el controller limitado a selección de modalidad.

**Alternatives considered**:
- Devolver entidades al controller: expone el modelo persistente y desplaza el mapeo fuera de la transacción.
- Crear proyecciones nuevas de repositorio: no aportan valor para un máximo de 2.500 registros y requerirían cambiar consultas ya probadas.

## D5. Orden de validación y errores acotados

**Decision**: Autenticación primero por la cadena de seguridad; dentro del servicio, validar `playerId > 0` antes de comprobar existencia y, después, resolver estrategia y consultar cotizaciones. Usar excepciones específicas para jugador ausente, cotización ausente y query inválida, manejadas por `QuoteExceptionHandler`.

**Rationale**: Este orden evita filtrar existencia sin credenciales, distingue un id inválido de un jugador positivo inexistente y separa ambos de un historial vacío. El advice de 016 ya está limitado a `QuoteController` y devuelve `GlobalExceptionHandler.ErrorResponse`, por lo que se conserva el shape sin tocar el archivo protegido.

**Alternatives considered**:
- Agregar handlers a `GlobalExceptionHandler`: prohibido sin consulta y ampliaría efectos a otros endpoints.
- Usar `ResponseStatusException`: no garantiza el shape `{error,message}` existente.
- Manejar todas las fallas como `IllegalArgumentException`: demasiado amplio; puede ocultar defectos internos como errores de cliente.

## D6. Seguridad existente sin cambios

**Decision**: No modificar `SecurityConfig`. El GET cae bajo `anyRequest().authenticated()` y admite JWT o API key para `Role.USER`; el matcher específico `POST /quotes/recalculate` continúa exigiendo ADMIN. La feature demuestra ambos caminos exitosos y el rechazo sin credenciales, pero reutiliza las pruebas existentes de los filtros para bearer vencido y API key inválida o revocada.

**Rationale**: La política vigente ya implementa exactamente la autorización pedida para una lectura protegida. Agregar otro matcher no cambia comportamiento y tocaría un archivo de Entrega 1 sin necesidad.

**Alternatives considered**:
- Declarar un matcher GET explícito: redundante y viola la regla de cambio mínimo.
- Requerir ADMIN: contradice la especificación, que habilita a cualquier consumidor autenticado.

## D7. MockMvc integrado en lugar de otro DataJpaTest

**Decision**: Crear una prueba `@SpringBootTest` + MockMvc + `@UnitTestProfile`, con H2 y cotizaciones persistidas manualmente mediante los repositorios. Mantener las pruebas de repositorio de 013 como cobertura de bajo nivel.

**Rationale**: Esta feature agrega orquestación, HTTP, validaciones, seguridad y mapeo; no agrega consultas JPA. La prueba integrada cubre el camino real controller → service → repositories y el requisito de evolución temporal de §3.3 con datos manuales para jugadores de las cinco ligas.

**Alternatives considered**:
- Nuevo `@DataJpaTest`: duplica 013 y no cubre el contrato HTTP.
- Controller standalone con servicio mockeado: no verifica límites UTC, estrategia activa ni integración con consultas reales.
- E2E PostgreSQL nuevo: 013 ya verifica esas consultas contra PostgreSQL; sería redundante para este cambio sin persistencia nueva.

## D8. Contrato OpenAPI verificable

**Decision**: Anotar el GET con todos los parámetros, respuestas, `oneOf`, header de correlación y los dos `SecurityRequirement`; ampliar `QuoteOpenApiTest` y conservar un contrato fuente en `contracts/quote-history-api.yaml`.

**Rationale**: La constitución §6.1 exige que Swagger sea completo y probado. El test de `/v3/api-docs` detecta divergencias entre anotaciones y contrato ejecutable.

**Alternatives considered**:
- Documentar sólo en YAML: no garantiza que Swagger UI del backend refleje el contrato.
- Confiar sólo en anotaciones sin contrato de diseño: dificulta revisar el `oneOf` y los ejemplos antes de implementar.

## D9. Aislamiento de fuentes externas sin E2E redundante

**Decision**: `QuoteQueryService` depende únicamente de `PlayerRepository`, `PlayerQuoteRepository` y `ValuationStrategyCatalog`. `QuoteHistoryControllerTest` usa sólo cotizaciones persistidas y mocks ya disponibles para comprobar cero interacciones con adaptadores externos; no configura Football-Data.org ni WhoScored. La caída de ambas fuentes se valida ejecutando el `ExternalSourcesDownE2ETest` existente de la feature 016.

**Rationale**: La consulta histórica es una lectura local y FR-028 prohíbe llamadas externas. Reutilizar el E2E existente conserva la evidencia de resiliencia sin duplicar infraestructura ni escenarios.

**Alternatives considered**:
- Crear otro E2E de fuentes caídas para el historial: rechazado por redundante con 016.
- Configurar clientes externos en `QuoteHistoryControllerTest`: rechazado porque agregaría dependencias ajenas a la consulta persistida.
