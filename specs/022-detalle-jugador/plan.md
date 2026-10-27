# Implementation Plan: Detalle de un jugador

**Branch**: `022-detalle-jugador` | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/022-detalle-jugador/spec.md`

## Summary

Agregar `GET /players/{id}` a `PlayerController` para devolver una vista compuesta por el `PlayerResponse` existente, estadísticas de temporada opcionales y la última cotización opcional de la estrategia activa. Un nuevo `PlayerDetailService` ejecutará una consulta de sólo lectura contra `PlayerRepository`, `PlayerStatsRepository` y `PlayerQuoteRepository`, resolverá la estrategia mediante `ValuationStrategyCatalog.active()` y mapeará el resultado al nuevo contrato `PlayerDetailResponse`. La implementación no invoca adaptadores ni procesos externos, conserva intactos `PlayerResponse` y `GlobalExceptionHandler`, y prueba que el mapping variable no captura las rutas literales `/players/ranking` ni `/players/{id}/quotes`.

## Technical Context

**Language/Version**: Java 17

**Primary Dependencies**: Spring Boot 4.1.1; Spring Web MVC, Data JPA, Security y Validation; springdoc-openapi 3.1.1

**Storage**: PostgreSQL en ejecución normal; H2 2.4.x en modo compatible con PostgreSQL para tests. Se reutilizan `players`, `player_stats` y `player_quotes`; no hay migraciones ni cambios de esquema.

**Testing**: JUnit 5, Spring Boot Test, MockMvc, Spring Security Test y AssertJ; perfil `@UnitTestProfile`, transacciones de prueba y fixtures locales persistidos.

**Target Platform**: Servicio HTTP stateless ejecutable en JVM, desplegado como backend del proyecto.

**Project Type**: Aplicación web con backend Spring Boot y frontend independiente; esta feature modifica sólo el backend y documentación de diseño.

**Performance Goals**: Al menos 95 de 100 consultas consecutivas sobre un catálogo local de 2.500 jugadores completan en menos de 1 segundo.

**Constraints**: Sin llamadas a Football-Data.org, WhoScored, sincronización o recálculo; `PlayerResponse` y `GlobalExceptionHandler` no se modifican; sólo se agrega un método a `PlayerController`; `stats` y `quote` son campos presentes y nullable; autenticación alternativa JWT/API key; 400 `validation_error` para texto no numérico, overflow de `Long`, cero o negativos; 401 sin credenciales; y 404 `player_not_found` sólo para un `Long` positivo sin jugador.

**Scale/Scope**: Una operación de lectura, un servicio, tres DTOs de respuesta, un advice focalizado para compartir el 404 existente, ampliaciones de tests/OpenAPI y ningún cambio de persistencia.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Evaluación previa a Phase 0

| Regla | Estado | Evidencia del plan |
|---|---|---|
| §5.1 — `GET /players/:id` obligatorio | PASS | Se agrega exactamente `GET /players/{id}` a `PlayerController`. |
| §1.3 — resiliencia y desacoplamiento externo | PASS | El servicio depende sólo de tres repositorios locales y del catálogo de estrategias en memoria; no depende de adapters, feeds ni clientes externos. |
| §6.1 — OpenAPI/Swagger completo | PASS | El método y los tres DTOs documentan campos, nulabilidad, 200/400/401/404 y las alternativas `bearerAuth`/`apiKeyAuth`; `PlayerOpenApiTest` verifica el documento generado. |
| §2.1 — backend en capas | PASS | Controller limita su función al borde HTTP, service compone la lectura y repositories acceden a persistencia. |
| §4.1 — autenticación | PASS | La cadena existente protege cualquier ruta no pública y admite JWT o API key; no se cambia `SecurityConfig`. |
| §5.2 — caché Redis e índices | PASS | La infraestructura Redis obligatoria permanece activa. Esta lectura acotada usa índices/PK existentes y no necesita esquema ni caché nuevos para cumplir el objetivo; nunca usa una fuente externa como fallback. |
| §5.3 — trazabilidad | PASS | `CorrelationIdFilter` ya cubre la ruta y el 200 documenta `X-Correlation-ID`. |
| §7 — DoD de calidad | PASS | Se planifican pruebas MockMvc, OpenAPI, regresión de rutas y suite completa; CI/Sonar quedan como validaciones de cierre tras implementación. |

No hay violaciones ni decisiones abiertas; se habilita Phase 0.

### Reevaluación posterior a Phase 1

PASS. El modelo no agrega estado ni mutaciones; el contrato declara las dos secciones opcionales, ambas alternativas de autenticación y los cuatro códigos HTTP; el quickstart valida datos locales, rutas coexistentes y regresión. Un advice nuevo y focalizado en `PlayerController` reproduce el mapping 404 de `PlayerNotFoundException` sin modificar `QuoteExceptionHandler` ni `GlobalExceptionHandler`.

## Project Structure

### Documentation (this feature)

```text
specs/022-detalle-jugador/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── player-detail-api.yaml
└── tasks.md                    # generado posteriormente por $speckit-tasks
```

### Source Code (repository root)

```text
backend/
├── pom.xml
└── src/
    ├── main/java/com/example/demo/
    │   ├── controller/
    │   │   ├── PlayerController.java                 # agregar únicamente GET /{id}
    │   │   └── QuoteController.java                  # sin cambios
    │   ├── dto/player/
    │   │   ├── PlayerResponse.java                   # sin cambios
    │   │   ├── PlayerDetailResponse.java             # nuevo
    │   │   ├── PlayerStatsResponse.java              # nuevo
    │   │   └── CurrentQuoteResponse.java             # nuevo
    │   ├── exception/
    │   │   ├── PlayerNotFoundException.java          # reutilizado sin cambios
    │   │   ├── PlayerDetailExceptionHandler.java     # nuevo advice focalizado para 400/404
    │   │   ├── QuoteExceptionHandler.java            # sin cambios
    │   │   └── GlobalExceptionHandler.java           # sin cambios
    │   └── service/
    │       └── PlayerDetailService.java               # nuevo
    └── test/java/com/example/demo/
        ├── controller/
        │   ├── PlayerDetailControllerTest.java       # nuevo MockMvc integrado
        │   └── PlayerControllerTest.java             # adaptar constructor, conservar tests
        └── config/
            └── PlayerOpenApiTest.java                # ampliar contrato de detalle
```

**Structure Decision**: Mantener la arquitectura backend actual por capas y ubicar los DTOs bajo `dto/player`, porque el contrato pertenece al recurso jugador. No se modifica el frontend, la persistencia ni los adapters. `PlayerDetailControllerTest` usa el contexto real para probar conjuntamente routing, seguridad, repositories, service, advice y serialización.

## Design

### PlayerDetailService

Crear `service/PlayerDetailService` con dependencias explícitas en `PlayerRepository`, `PlayerStatsRepository`, `PlayerQuoteRepository` y `ValuationStrategyCatalog`. Su única operación pública, transaccional de sólo lectura, recibe `Long id` y:

1. rechaza `null`, cero o negativos con `InvalidQuoteQueryException("El id del jugador debe ser mayor a cero")` y, para un id positivo, obtiene el jugador con `PlayerRepository.findById`; si falta, lanza el `PlayerNotFoundException` existente;
2. consulta `PlayerStatsRepository.findById(id)` y conserva la ausencia como `null`;
3. resuelve `strategyCatalog.active().code()` para cada pedido;
4. consulta `findFirstByPlayerIdAndStrategyCodeOrderByCalculatedAtDescIdDesc(id, code)` y conserva la ausencia como `null`;
5. construye `PlayerDetailResponse` mediante factories de los DTOs, mapeando desde el inicio cada `Optional.empty()` de stats o quote a `null`, sin modificar entidades ni reemplazar métricas nulas por cero.

No se reutiliza `QuoteQueryService`: esa clase modela historial/ranking, incorpora caché de ranking y reglas de fechas que no pertenecen al detalle. El servicio nuevo mantiene la dependencia y responsabilidad solicitadas.

### DTOs

- `PlayerDetailResponse`: record con `PlayerResponse player`, `PlayerStatsResponse stats` nullable y `CurrentQuoteResponse quote` nullable. La factory del detalle construye un `PlayerResponse` con los mismos nueve campos existentes.
- `PlayerStatsResponse`: record con las diez métricas y `fetchedAt`; no expone `whoscoredPlayerId` ni la asociación JPA.
- `CurrentQuoteResponse`: record con `value`, `score`, `status`, `strategy`, `version` y `calculatedAt`; no expone ids, `runId`, `correlationId` ni la asociación JPA.

Cada record tendrá `@Schema` y factories `from(...)` donde corresponda. Los DTOs serán tipos públicos separados para que OpenAPI genere esquemas estables con esos nombres y mantenga clara la nulabilidad anidada.

### Controller, seguridad y OpenAPI

Inyectar `PlayerDetailService` en `PlayerController` y agregar un único método:

```text
GET /players/{id}
PathVariable Long id
return PlayerDetailResponse
```

La conversión estándar de Spring deja los ids no numéricos o fuera del rango de `Long` en el mapping 400 existente de `GlobalExceptionHandler`. El controller rechaza además cero y negativos con 400 `validation_error` y el mensaje `El id del jugador debe ser mayor a cero`, igual que `GET /players/{id}/quotes`. Sólo un `Long` positivo sin fila asociada produce 404 `player_not_found`.

El método documentará `@Operation`, el parámetro `id`, `@ApiResponse` 200/400/401/404, el header de correlación y el esquema `PlayerDetailResponse`. Las dos `@SecurityRequirement` de clase ya alcanzan al método; se mantienen y el test confirma que OpenAPI materializa ambas alternativas.

### Reutilización segura del 404 existente

`PlayerNotFoundException` ya existe, pero su handler está dentro de `QuoteExceptionHandler`, cuyo `@RestControllerAdvice` sólo alcanza a `QuoteController`. Crear `PlayerDetailExceptionHandler` como un `@RestControllerAdvice` nuevo con `assignableTypes = PlayerController.class` y `@Order(Ordered.HIGHEST_PRECEDENCE)`: traduce `PlayerNotFoundException` a 404 con `ErrorResponse("player_not_found", ex.getMessage())` y `InvalidQuoteQueryException` a 400 con `ErrorResponse("validation_error", ex.getMessage())`. `QuoteExceptionHandler` conserva sin cambios sus mappings actuales para `QuoteController`; `GlobalExceptionHandler` tampoco se modifica.

Esta duplicación acotada del mapping mantiene el contrato existente en ambas áreas sin ampliar el alcance de los demás handlers de cotizaciones.

### Coexistencia de rutas

Spring selecciona rutas literales/más específicas antes que `/{id}`, pero la garantía se prueba sobre el contexto real. El test nuevo ejecutará con la misma credencial:

- `/players/{id}` → objeto `PlayerDetailResponse`;
- `/players/ranking` → objeto paginado con `items`, nunca 400 por intentar convertir `ranking` a `Long`;
- `/players/{id}/quotes` → lista de historial, nunca el detalle.

El arranque exitoso más las tres respuestas demuestra ausencia de mappings ambiguos. Los tests existentes de ranking e historial permanecen como regresión adicional.

### Tests

Crear `PlayerDetailControllerTest` con `@SpringBootTest`, `@AutoConfigureMockMvc`, `@UnitTestProfile` y `@Transactional`. Persistir jugador, stats, cotizaciones de estrategia activa/no activa y credenciales mediante repositories/fixtures existentes. Cobertura mínima:

- 200 con los nueve campos de `player`, las diez métricas y `fetchedAt`, la última quote activa con sus seis campos y `X-Correlation-ID` presente;
- selección por estrategia activa, fecha descendente e id descendente frente a cotizaciones anteriores y de otra estrategia;
- 200 con `stats: null` y `quote: null`;
- 200 con métricas internas nulas, sin convertirlas a cero;
- 400 `validation_error` para `abc`, `9223372036854775808`, `0` y `-1`;
- 401 sin credenciales, incluso sobre un id inexistente;
- 404 para un id `Long` positivo inexistente con `player_not_found`;
- acceso exitoso con API key USER y JWT USER;
- coexistencia con `/players/ranking` y `/players/{id}/quotes` en el mismo contexto;
- `FootballDataAdapter` y `WhoScoredAdapter` reemplazados por `@MockitoBean`: la consulta responde 200 y `verifyNoInteractions` confirma que ninguna integración externa participa.

Actualizar `PlayerControllerTest` sólo para inyectar un mock del nuevo servicio al constructor, preservando sus cinco pruebas existentes. Ampliar `PlayerOpenApiTest` para comprobar path, parámetro, esquemas, campos nullable, correlación, 200/400/401/404 y ambas alternativas de seguridad. No se necesita otro `@DataJpaTest`: los tres repositories y el desempate de quotes ya tienen cobertura específica; MockMvc prueba su integración para este caso de uso.

## Complexity Tracking

No hay violaciones constitucionales que justificar.
