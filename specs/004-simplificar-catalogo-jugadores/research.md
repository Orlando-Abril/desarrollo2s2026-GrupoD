# Research: Simplificación del catálogo de jugadores

No quedaron marcadores `NEEDS CLARIFICATION` en el Technical Context. Las dos preguntas de alcance se resolvieron en la spec (sesión 2026-09-24). Este documento registra las decisiones técnicas que quedan para implementar la simplificación.

## R1. Manejo del esquema sin Flyway

**Decision**: Volver a `spring.jpa.hibernate.ddl-auto=update` y `spring.jpa.show-sql=true`, exactamente como en `main`. Se quitan todas las propiedades `spring.flyway.*`, las dependencias de Flyway y el directorio `db/migration`.

**Rationale**: La spec (FR-022) pide restaurar el mecanismo previo. `update` crea en una base vacía todas las tablas que necesita el catálogo (`players`, `player_positions`) a partir de las entidades existentes.

**Alternatives considered**:
- Mantener Flyway sólo con `V1`: sigue imponiendo migraciones al resto del equipo.
- Usar `ddl-auto=validate` sin migraciones: una base vacía no arrancaría.

**Notas sobre bases existentes**:
- Las bases que ya pasaron por Flyway conservan `flyway_schema_history`, `player_token_allocations` y `catalog_sync_audit_events`. Hibernate `update` no borra tablas y las ignora.
- El trigger de la tabla de auditoría sólo afecta a esa tabla.
- La columna vieja `players.position`, que borraba `V3`, ya no existe en esas bases.
- En una base nueva, `update` no crea esa columna, porque la entidad no la declara.
- La limpieza manual es opcional y se documenta en el quickstart.

## R2. Unicidad de `externalId` sin índice único

**Decision**: La unicidad se garantiza en la lógica de carga: `findByExternalId` y después crear o actualizar. Además, `PlayerCatalogService.synchronizeCatalog()` se declara `synchronized` para que dos `POST /players/sync` simultáneos se ejecuten uno detrás del otro.

**Rationale**: El índice único parcial lo creaba `V2`, que se retira, y FR-027 prohíbe tocar la estructura de `Player`. Con una sola instancia soportada, `synchronized` es el mecanismo más simple que evita la carrera entre "no existe" y "insertar", sin infraestructura nueva.

**Alternatives considered**:
- Un `AtomicBoolean` que rechace la segunda llamada con 409: agrega un estado de error y un código de respuesta más.
- Lock distribuido o de base de datos: es infraestructura nueva, prohibida por la spec.
- Agregar `unique = true` en `Player`: modifica el modelo.

## R3. Disparo explícito de la carga

**Decision**: Agregar `POST /players/sync` en el `PlayerController` existente, protegido por el `ApiKeyAuthFilter` actual (cualquier ruta no pública requiere `X-API-KEY`). Invoca `PlayerCatalogService.synchronizeCatalog()` de forma síncrona y responde `200` con `PlayerSyncResponse`.

**Rationale**: Es la respuesta A a la Q1 de la spec. Se puede demostrar desde Swagger para el escenario de prueba N.º 1 del Documento de Visión. No requiere tocar `SecurityConfig`, porque la ruta queda protegida por defecto.

**Alternatives considered**:
- Ejecución asíncrona que devuelva `202`: agrega estado y seguimiento.
- Controller separado: agrega una clase sin beneficio.

**Código de respuesta**: siempre `200` con el resumen, incluso si el `status` es `FAILED`. La solicitud en sí se procesó, y el resultado de negocio viaja en el cuerpo.
- Alternativa descartada: `502` cuando fallan todas las ligas, porque obliga a documentar otro esquema de error.

## R4. Aislamiento de fallas en la carga

**Decision**: Se conserva la estructura actual:
- `try/catch` de `FootballDataException` por liga.
- Transacción por jugador con `TransactionOperations.executeWithoutResult`.
- La falla de un jugador se registra con `log.warn` en lugar de un evento de auditoría.

El estado final se calcula como hoy:
- `COMPLETED`: sin fallas.
- `FAILED`: hubo fallas y no se procesó ningún jugador.
- `PARTIAL_FAILURE`: en cualquier otro caso.

**Rationale**: FR-006 y FR-007 lo exigen, y el código ya lo hace. Sólo se quitan las llamadas a auditoría, tokens y métricas.

**Alternatives considered**: Una única transacción para toda la carga. Un jugador inválido revertiría todo, lo que contradice FR-006.

## R5. Correlation ID en la carga

**Decision**: `PlayerCatalogService` deja de generar el correlation ID y de escribir o limpiar el MDC. Como la carga ahora siempre llega por HTTP, `CorrelationIdFilter` ya deja el ID en el MDC, y los logs JSON lo incluyen.

**Rationale**: El MDC propio sólo hacía falta para las ejecuciones del scheduler, que no tenían request. Con el scheduler retirado, ese código es redundante.

**Alternatives considered**: Mantener la generación en el servicio. Es código muerto.

## R6. Métricas

**Decision**:
- Se elimina `ObservabilityConfig` (los timers `football.data.request` y `catalog.synchronization`).
- Se eliminan los contadores `football.data.errors` y `catalog.synchronization.errors`.
- Se conserva `management.endpoints.web.exposure.include=health,metrics`.

**Rationale**: FR-025 retira sólo las métricas custom. Actuator sigue exponiendo `http.server.requests`, que da latencia y tasa de error por endpoint, suficiente para la sección 5.1 del Documento de Visión y la 5.3 de la constitución.

**Alternatives considered**: Quitar también `metrics` del exposure. La Q2 decidió conservar la observabilidad general.

## R7. Catálogo vacío

**Decision**: `GET /players` devuelve `200 []` cuando no hay jugadores. Se eliminan `CatalogUnavailableException`, su handler y la respuesta `503` del contrato.

**Rationale**: FR-013. El `503` dependía de `hasSuccessfulSnapshot()` sobre la tabla de auditoría retirada.

**Alternatives considered**: Responder `503` cuando `count() == 0`. Mezcla "nunca se cargó" con "se cargó pero no hay datos", y agrega un estado sin valor para la entrega.

## R8. Tests

**Decision**:
- Eliminar los 5 tests de funcionalidades retiradas.
- Adaptar al nuevo constructor del adapter y de las properties: `FootballDataAdapterTest`, `FootballDataSecretLeakTest` y `CacheConfigTest`.
- Reescribir `PlayerCatalogServiceTest` con mocks de adapter, repositorio y `TransactionOperations`.
- Actualizar los tests de controller, query service y OpenAPI.
- Retirar el módulo `org.testcontainers:postgresql`, porque sólo lo usaban los tests eliminados. Se conserva `org.testcontainers:junit-jupiter` para el test de Redis.

**Rationale**: FR-028 y FR-029. Ningún test llama a la API real (`MockRestServiceServer` y mocks).

**Alternatives considered**: Mantener un test de integración con PostgreSQL para la unicidad. La unicidad ya no depende de la base, sino del servicio, y se cubre con un test unitario.
