# Phase 0 Research: Football-Data Player Catalog

## Cliente HTTP

**Decision**: Usar `RestClient` provisto por Spring MVC/Spring Framework en el proyecto
Spring Boot 4.1.1.

**Rationale**: El flujo de sincronización es síncrono y el proyecto ya usa
`spring-boot-starter-webmvc`; `RestClient` ofrece API fluida, conversión Jackson,
configuración central de headers/timeouts y no requiere Reactor. La documentación de
Spring lo presenta como el cliente síncrono actual y permite enlazar
`MockRestServiceServer` directamente a su builder.

**Alternatives considered**: `WebClient`, descartado porque agregaría el stack reactivo
sin beneficio para este job síncrono; `RestTemplate`, descartado frente a la API moderna
`RestClient`; WireMock, válido pero innecesario cuando el servidor mock de Spring prueba
el cliente con menos infraestructura.

Fuentes: [Spring REST Clients](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html),
[MockRestServiceServer](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/test/web/client/MockRestServiceServer.html).

## Endpoints y autenticación de Football-Data.org

**Decision**: Consultar `GET /competitions/{code}/teams` para cada código `PL`, `PD`,
`SA`, `BL1` y `FL1`, enviando el token sólo en el header `X-Auth-Token`. Modelar sólo
los campos necesarios de equipo y `squad`.

**Rationale**: El subrecurso oficial devuelve equipos filtrados por competición y el
recurso de equipo contiene el plantel. Una llamada por liga permite completar las cinco
ligas con cinco solicitudes en frío, dentro del límite indicado, y genera una clave de
caché natural por código.

**Alternatives considered**: Consultar primero equipos y después cada `/teams/{id}`
provocaría más de cien solicitudes; usar `/persons` no ofrece el agrupamiento requerido;
top scorers omite gran parte del plantel.

Fuentes: [Competition team subresource](https://docs.football-data.org/general/v4/competition.html),
[Team resource and squad](https://docs.football-data.org/general/v4/team.html),
[Football-Data lookup headers](https://docs.football-data.org/general/v4/lookup_tables.html).

## Estrategia de caché y resiliencia

**Decision**: Añadir `spring-boot-starter-data-redis`, habilitar Spring Cache y anotar
`fetchCompetitionTeams(String competitionCode)` con `@Cacheable`. Configurar un
`RedisCacheManager` con TTL ISO-8601 configurable y clave por liga. La base local es el
fallback de lectura; no se usa la caché Redis como repositorio de entidades.

**Rationale**: Cinco entradas independientes evitan repetir llamadas dentro del TTL y
permiten reintentar sólo la liga vencida. `sync=true` evita una estampida dentro de una
instancia. Una entrada expirada no constituye un fallback fiable ante una caída, por lo
que la garantía de disponibilidad proviene de `players` persistido: la sincronización es
aditiva/upsert y nunca limpia datos antes de completar la obtención.

**Alternatives considered**: Una clave para las cinco ligas haría fallar toda la carga
por una sola liga y complica actualizaciones parciales; caché en memoria incumple la
constitución; servir directamente los DTO cacheados acoplaría el endpoint al tercero y
perdería el fallback tras expirar el TTL.

## Mapeo e idempotencia

**Decision**: Usar el id numérico de Football-Data convertido a `String` como
`Player.externalId`, y `PlayerRepository.findByExternalId` para upsert. Mapear las
posiciones externas a los cuatro valores locales, calcular edad desde `dateOfBirth` en
la fecha de sincronización y omitir únicamente jugadores sin identidad mínima.

**Rationale**: `externalId` y su índice ya existen para correlación. El upsert garantiza
cero duplicados en reimportaciones. La fecha de nacimiento es más estable que una edad
transportada y permite completar el campo opcional. En updates se preservan `id`,
`marketValue`, altura y demás datos que esta fuente no administra.

**Alternatives considered**: Dedupe por nombre/equipo es inestable ante transferencias y
homónimos; reemplazar filas completas arriesga valores de mercado y relaciones futuras;
crear posiciones nuevas violaría el modelo existente.

## Filtrado del catálogo

**Decision**: Componer `Specification<Player>` para `league`, equipo case-insensitive y
pertenencia a `positions`, aplicando AND a todos los query params presentes y `distinct`
cuando se une la colección.

**Rationale**: `PlayerRepository` ya extiende `JpaSpecificationExecutor`, por lo que el
filtrado dinámico no requiere nuevos métodos combinatorios ni cambios de entidad.

**Alternatives considered**: Filtrar en memoria escala peor y carga filas innecesarias;
crear un método de repository por cada combinación genera ocho variantes; QueryDSL
agrega una dependencia no necesaria.

## Contrato, seguridad y errores

**Decision**: Mantener sólo `GET /players` como interfaz pública de esta feature. Usar
el filtro de API key existente y documentar `apiKeyAuth`. Responder `400` ante enums no
válidos, `401` ante clave ausente/inválida, `200` con lista (posiblemente vacía) para
consultas válidas y `503` únicamente cuando una operación que necesita inicializar el
catálogo no tiene datos locales ni fuente disponible.

**Rationale**: Las lecturas quedan desacopladas de la red y respetan el contrato pedido.
La especificación no autoriza un endpoint administrativo de sincronización, así que el
service ofrece la operación interna para un job o disparador posterior.

**Alternatives considered**: Sincronizar en cada GET introduce latencia, efectos
laterales y dependencia externa; agregar `POST /players/sync` amplía la superficie y
requiere reglas de autorización no especificadas.

## Estrategia de pruebas

**Decision**: `PlayerCatalogServiceTest` usa Mockito para adapter/repository;
`PlayerControllerTest` usa MockMvc con configuración de seguridad controlada; y
`FootballDataAdapterTest` enlaza `MockRestServiceServer` a `RestClient.Builder`.

**Rationale**: Cada suite verifica su límite sin Redis, PostgreSQL ni Internet. El test
del adapter valida URI, método, header y deserialización; el del service cubre upsert,
preservación de mercado, filtros y fallas; el controller cubre query params, DTOs,
errores y API key.

**Alternatives considered**: Llamar al sandbox del proveedor sería lento y no
determinista; WireMock funciona, pero es una dependencia extra para este cliente simple;
un único `@SpringBootTest` no aislaría responsabilidades.
