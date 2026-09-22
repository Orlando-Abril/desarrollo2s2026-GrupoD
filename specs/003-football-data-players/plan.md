# Implementation Plan: Football-Data Player Catalog

**Branch**: `003-football-data-players` | **Date**: 2026-09-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-football-data-players/spec.md`

## Summary

Implementar el catálogo de jugadores como una integración síncrona y en capas. Un
`FootballDataAdapter` basado en `RestClient` obtiene, por liga, los equipos con sus
planteles desde Football-Data.org v4 y cachea cada respuesta en Redis mediante
`@Cacheable`. `PlayerCatalogService` traduce los DTO externos al modelo existente,
realiza upsert por `externalId`, conserva el catálogo ante fallas externas y consulta
el repositorio con filtros combinables. `PlayerController` expone `GET /players`,
protegido por la API key ya configurada y documentado en OpenAPI. Los tests aíslan
cada capa con Mockito, MockMvc y `MockRestServiceServer`, sin tráfico real.

## Technical Context

**Language/Version**: Java 17  
**Primary Dependencies**: Spring Boot 4.1.1, Spring MVC/WebMVC (`RestClient`), Spring Data JPA, Spring Cache, `spring-boot-starter-data-redis`, Spring Security, springdoc-openapi 3.1.1  
**Storage**: PostgreSQL para `players` y su colección `player_positions`; Redis 7 para respuestas externas cacheadas  
**Testing**: JUnit 5, Mockito, MockMvc, Spring `MockRestServiceServer`, H2 para tests de persistencia existentes  
**Target Platform**: Servicio Spring Boot desplegable en Linux/CI, con Java 17, PostgreSQL y Redis  
**Project Type**: Aplicación web con backend Spring Boot y frontend independiente  
**Performance Goals**: `GET /players` se resuelve exclusivamente desde datos locales; una sincronización completa realiza como máximo 5 solicitudes externas cuando no hay entradas cacheadas y 0 mientras el TTL siga vigente  
**Constraints**: plan gratuito de Football-Data.org de 10 solicitudes/minuto; TTL configurable; sin modificar `Player`, `League` ni `Position`; no exponer el token externo; las lecturas deben continuar ante fallas de Football-Data.org  
**Scale/Scope**: 5 ligas, aproximadamente 100 clubes y algunos miles de jugadores; un endpoint público de catálogo y una operación interna de sincronización preparada para ser invocada por procesos de aplicación posteriores

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-design gate

| Gate constitucional | Evaluación |
|---|---|
| Fuentes oficiales y cinco grandes ligas (1.3) | PASS: Football-Data.org v4 con códigos `PL`, `PD`, `SA`, `BL1` y `FL1`. |
| Tolerancia a fallas externas (1.3) | PASS: el listado nunca consulta la fuente; una sincronización fallida no elimina ni reemplaza datos locales. |
| Capas Controller/Service/Repository/Adapter (2.1) | PASS: cada responsabilidad tiene paquete e interfaz explícitos. |
| Tests de construcción con datos mock (3.3) | PASS: adapter sin red real, service con Mockito y controller con MockMvc. |
| API key (4.1) | PASS: `GET /players` permanece detrás del filtro `X-API-KEY` existente. |
| `GET /players` y filtros (5.1) | PASS: contrato con filtros opcionales y combinables por liga, equipo y posición. |
| Redis e índices (5.2) | PASS: Redis obligatorio y reutilización de `JpaSpecificationExecutor`; se conserva el índice existente de `external_id`. No se cambia la estructura de las entidades. |
| Observabilidad (5.3) | PASS con alcance: se registran liga, estado HTTP y correlation ID en fallas, nunca el token. La infraestructura transversal de health/metrics no se redefine en este feature. |
| OpenAPI/Swagger (6.1) | PASS: operación, filtros, DTOs, respuestas y `apiKeyAuth` documentados. |
| DoD de resiliencia (7) | PASS: test de falla externa con catálogo previo y test de indisponibilidad sin datos. |

No hay violaciones que requieran excepción. La operación interna de sincronización no
agrega un endpoint no solicitado; queda disponible para el scheduler/proceso operativo
que corresponda en una feature posterior.

### Post-design re-check

PASS. `research.md`, `data-model.md`, `contracts/players-api.yaml` y `quickstart.md`
mantienen Redis, el fallback local, las cuatro capas, la protección por API key y las
pruebas aisladas. El contrato no introduce mutaciones públicas ni cambios al modelo
persistente existente.

## Project Structure

### Documentation (this feature)

```text
specs/003-football-data-players/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── players-api.yaml
└── tasks.md                 # generado posteriormente por $speckit-tasks
```

### Source Code (repository root)

```text
backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/example/demo/
    │   │   ├── adapter/footballdata/
    │   │   │   ├── FootballDataAdapter.java
    │   │   │   └── dto/
    │   │   ├── config/
    │   │   │   ├── CacheConfig.java
    │   │   │   └── FootballDataConfig.java
    │   │   ├── controller/PlayerController.java
    │   │   ├── dto/player/PlayerResponse.java
    │   │   ├── exception/
    │   │   ├── model/       # Player, League y Position existentes; sin cambios
    │   │   ├── repository/PlayerRepository.java
    │   │   └── service/PlayerCatalogService.java
    │   └── resources/application.properties
    └── test/
        └── java/com/example/demo/
            ├── adapter/footballdata/FootballDataAdapterTest.java
            ├── controller/PlayerControllerTest.java
            └── service/PlayerCatalogServiceTest.java
```

**Structure Decision**: Se extiende el único módulo backend existente bajo el paquete
raíz `com.example.demo`. Los DTO de Football-Data.org permanecen dentro del adapter
para impedir que el contrato externo contamine el dominio; el DTO público vive en
`dto/player`. El frontend no requiere cambios para esta fase de diseño.

## Design Decisions

### Flujo de sincronización

1. `PlayerCatalogService.synchronizeCatalog()` recorre el mapa estable de las cinco
   ligas y solicita una instantánea a `FootballDataAdapter.fetchCompetitionTeams(code)`.
2. El método público del adapter está anotado con `@Cacheable(cacheNames =
   "football-data-competition-teams", key = "#competitionCode", sync = true)`.
3. El adapter envía `X-Auth-Token` mediante configuración, aplica timeouts y convierte
   errores HTTP/transporte en una excepción de integración sin registrar secretos.
4. El service aplana equipo/plantel, descarta entradas sin id, nombre o equipo, mapea
   posición, calcula edad desde `dateOfBirth` cuando existe y hace upsert por
   `externalId`.
5. Para registros nuevos asigna `marketValue = 1.00`; para registros existentes lo
   conserva junto con cualquier dato ajeno al catálogo. Nunca borra jugadores porque
   falten en una respuesta parcial.
6. Una liga fallida se registra y no altera sus datos locales. Si todas fallan y el
   repositorio está vacío, el service informa indisponibilidad controlada; si existen
   datos, estos continúan disponibles.

### Flujo de consulta

`GET /players` acepta `league`, `team` y `position`. El controller valida y convierte
los enums; el service compone una `Specification<Player>` con AND entre filtros
presentes. `team` se compara sin distinguir mayúsculas/minúsculas y posición usa un
join sobre `positions` con resultados distintos. Sin filtros se listan todos los datos
locales. Un catálogo vacío válido responde `200 []`; la indisponibilidad `503` queda
reservada para una sincronización solicitada sin snapshot local utilizable.

### Configuración

- `football-data.base-url=https://api.football-data.org/v4`
- `football-data.token=${FOOTBALL_DATA_TOKEN}`
- `football-data.connect-timeout` y `football-data.read-timeout`
- `football-data.cache-ttl=${FOOTBALL_DATA_CACHE_TTL:PT6H}`
- propiedades estándar `spring.data.redis.*`
- `@EnableCaching` y `RedisCacheManager` con serialización JSON y TTL por caché

## Complexity Tracking

No aplica: el diseño no viola la constitución.
