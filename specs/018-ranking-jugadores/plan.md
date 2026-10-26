# Implementation Plan: Ranking de jugadores por cotización

**Branch**: `feature/ranking-jugadores` | **Date**: 2026-10-25 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/018-ranking-jugadores/spec.md`

## Summary

Exponer `GET /players/ranking` desde el `QuoteController` existente para devolver una página estable de jugadores ordenados por su última cotización de la estrategia efectiva, junto con la cotización inmediatamente anterior y su variación porcentual. La selección se resolverá en una consulta nativa paginada con CTE y funciones de ventana, compatible con PostgreSQL y H2 en modo PostgreSQL; el servicio completará las posiciones en bloque, construirá DTOs inmutables y conservará la página completa durante una hora en Redis.

El caché `quotes-ranking` tendrá una clave canónica por estrategia efectiva, liga, posición, página y tamaño. La lectura, escritura y limpieza serán fail-open. Un nuevo `QuoteCacheEvictor` escuchará de forma síncrona `QuoteRunCompletedEvent`; como la feature 016 aún no publica ese evento, `QuoteCalculationService.recalculate` lo emitirá al cerrar toda corrida aceptada, exitosa o fallida.

La entrada de negocio será `QuoteQueryService.ranking(strategy, league, position, pageable)`. `QuoteController` resolverá el contrato HTTP siguiendo las convenciones existentes del proyecto y `QuoteQueryService` resolverá la estrategia efectiva y las reglas de negocio. La paginación usará el mecanismo estándar de Spring salvo que la inspección de implementación encuentre un patrón local distinto.

## Technical Context

**Language/Version**: Java 17

**Primary Dependencies**: Spring Boot 4.1.1, Spring Web MVC, Spring Data JPA/Hibernate ORM 7.4.5.Final, Spring Security, Spring Cache/Data Redis, Jackson 3, springdoc-openapi 3.1.1

**Storage**: PostgreSQL en ejecución; H2 2.4.240 con `MODE=PostgreSQL` en tests; Redis para `quotes-ranking`

**Testing**: JUnit 5, Spring Boot Test, MockMvc, `@UnitTestProfile`, AssertJ, Mockito/`@MockitoSpyBean`; perfil `@E2ETest` contra PostgreSQL real

**Target Platform**: Backend HTTP desplegable en JVM/Linux y ejecutable localmente

**Project Type**: Aplicación web con frontend y backend separados; alcance limitado al backend

**Performance Goals**: Sobre exactamente 2.500 jugadores, registrar 20 solicitudes HTTP completas y consecutivas de la misma página —la primera puede ser cache miss y las 19 restantes cache hits— y obtener al menos 19 tiempos menores a 1 segundo; una consulta paginada de cotizaciones más carga en bloque de jugadores/posiciones por cache miss

**Constraints**: Puestos antes de paginar; orden fijo `value DESC, playerId ASC`; variación determinista como `(actual-anterior) * 100 / anterior` mediante `BigDecimal.divide(anterior, 2, HALF_UP)`, nula sin anterior o con anterior cero; tamaño 1..100; TTL una hora; caché fail-open; no tocar `PlayerController` ni `GlobalExceptionHandler`; no consultar fuentes externas

**Scale/Scope**: Aproximadamente 2.500 jugadores, dos estrategias, cinco ligas, cuatro posiciones y páginas de hasta 100 elementos

## Constitution Check

*GATE inicial y re-check posterior a Phase 1: aprobado para continuar la planificación, con la verificación de índices de §5.2 pendiente antes de implementar.*

| Regla | Estado | Evidencia de diseño |
|---|---|---|
| §2.1 Capas backend | PASS | Controller valida HTTP, servicio resuelve reglas/caché y repositorio ejecuta SQL. |
| §4.1 Autenticación | PASS | La ruta queda bajo `anyRequest().authenticated()` y admite JWT o API key. |
| §4.3 Validación estricta | PASS | Se validan estrategia, liga, posición, página y tamaño antes de consultar. |
| §5.1 `GET /players/ranking` | PASS | Mapping literal en `QuoteController`; no colisiona con `/players/{id}/quotes`, que tiene otro segmento. |
| §5.2 Redis e índices | PENDING | Caché tipado con TTL. Antes de implementar se inspeccionarán entidades, migraciones y esquema PostgreSQL para verificar índices de liga, equipo, posición e historial; si falta alguno, se documentará la evidencia y se solicitará autorización sin agregarlo todavía. |
| §5.3 Observabilidad | PASS | Las fallas de caché se registran y el correlation id existente cubre el endpoint. |
| §6.1 OpenAPI | PASS | Ruta, DTOs, límites, orden, errores y las dos alternativas de seguridad quedan documentados y probados. |
| §7 DoD | PASS | Se incluyen tests H2, PostgreSQL, MockMvc, seguridad, caché e invalidación, sin fuentes externas. |

**Criterio de índice**: la inspección inicial debe abarcar anotaciones de entidades, migraciones y los índices efectivos del esquema. No se agrega ningún índice en esta planificación. Si falta un índice constitucional o el E2E/plan de PostgreSQL no cumple el objetivo, se documenta la evidencia y se solicita autorización antes de modificar una entidad, migración o esquema protegido.

El diseño final no requiere modificar `PlayerController`, `GlobalExceptionHandler` ni el esquema de Entrega 1.

## Project Structure

### Documentation (this feature)

```text
specs/018-ranking-jugadores/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── ranking-api.yaml
└── tasks.md                 # posterior: $speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/com/example/demo/
├── config/CacheConfig.java
├── controller/QuoteController.java
├── dto/quote/
│   ├── RankingEntryResponse.java
│   └── RankingPageResponse.java
├── repository/
│   ├── PlayerQuoteRepository.java
│   └── RankingRowProjection.java
└── service/valuation/
    ├── QuoteCalculationService.java
    ├── QuoteCacheEvictor.java
    ├── QuoteQueryService.java
    └── QuoteRunCompletedEvent.java

backend/src/test/java/com/example/demo/
├── config/{CacheConfigTest,QuoteOpenApiTest}.java
├── controller/RankingControllerTest.java
├── e2e/RankingQueryE2ETest.java
├── repository/PlayerQuoteRepositoryTest.java
└── service/valuation/{QuoteCacheEvictorTest,QuoteCalculationServiceTest,QuoteQueryServiceCacheTest}.java
```

**Structure Decision**: Se amplía sólo el backend Spring Boot. El endpoint pertenece al área de cotizaciones aunque su URL esté bajo `/players`; el frontend queda fuera de alcance. El DTO de página evita cachear `PageImpl`, cuya forma/deserialización no es un contrato estable.

## Phase 0 — Research Decisions

Las decisiones y alternativas están en [research.md](research.md). No quedan `NEEDS CLARIFICATION`.

## Phase 1 — Design

- [data-model.md](data-model.md) define la fila de consulta, DTOs, clave y evento.
- [contracts/ranking-api.yaml](contracts/ranking-api.yaml) fija el contrato HTTP/OpenAPI.
- [quickstart.md](quickstart.md) describe validación en H2, PostgreSQL y Redis.

## Complexity Tracking

No aplica: no hay violaciones constitucionales.
