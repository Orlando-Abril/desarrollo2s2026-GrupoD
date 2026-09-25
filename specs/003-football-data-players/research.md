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
la fecha de sincronización y omitir únicamente jugadores sin `externalId`, nombre o
equipo. Nacionalidad ausente queda nula y posición ausente/no reconocida produce un
conjunto vacío para no perder una identidad válida.

**Rationale**: `externalId` y su índice ya existen para correlación. El upsert garantiza
cero duplicados en reimportaciones. La fecha de nacimiento es más estable que una edad
transportada y permite completar el campo opcional. En updates se preservan `id`,
`marketValue`, altura y demás datos que esta fuente no administra.

**Alternatives considered**: Dedupe por nombre/equipo es inestable ante transferencias y
homónimos; reemplazar filas completas arriesga valores de mercado y relaciones futuras;
crear posiciones nuevas violaría el modelo existente.

## Integridad concurrente, índices y migraciones

**Decision**: Incorporar Flyway y una migración PostgreSQL con UNIQUE parcial para
`players.external_id WHERE external_id IS NOT NULL`, índices sobre `players.league`,
`players.team` y `player_positions.position`, y las tablas nuevas de asignación inicial
y auditoría.

**Rationale**: El `findByExternalId` previo al save no evita carreras. La constraint es
la autoridad final de idempotencia y el service puede releer ante conflicto. Flyway
permite añadir índices sin modificar `Player`, `League` ni `Position`, satisfaciendo la
constitución y manteniendo cambios de esquema reproducibles.

**Alternatives considered**: Anotaciones JPA fueron descartadas porque cambiarían las
estructuras Java declaradas fuera de alcance; confiar sólo en transacciones o locks de
JVM no protege el dato frente a concurrencia ni procesos separados.

## Emisión inicial de tokens

**Decision**: Crear `PlayerTokenAllocation`, separado de `Player`, con UNIQUE por
jugador y CHECKs que fijen suministro/cantidad inicial en 100 y precio base en 1. La
creación del jugador y la asignación completa al superusuario ocurren en una única
transacción; un jugador existente nunca vuelve a emitir.

**Rationale**: La constitución exige que todo jugador integrado nazca con esa emisión y
tenencia. Separar la tabla respeta la prohibición de modificar la estructura de
`Player` y permite una garantía idempotente a nivel de base.

**Alternatives considered**: Diferir la emisión a una feature de trading dejaría
jugadores constitucionalmente inválidos; guardar cantidad/propietario en `Player`
violaría el alcance; emitir en una transacción posterior permitiría estados parciales.

## Scheduler y limitación de solicitudes

**Decision**: Ejecutar una sincronización en `ApplicationReadyEvent` sólo cuando no
existe snapshot exitoso y luego mediante `@Scheduled` configurable, con intervalo
mínimo de un minuto, lock de proceso y sin reintentos automáticos. El despliegue
soportado tiene una única instancia activa.

**Rationale**: La feature queda operable sin añadir un endpoint de mutación. Una carga
fría hace cinco solicitudes; el intervalo mínimo y la ausencia de reintentos mantienen
el máximo bajo 10 por minuto, mientras Redis elimina llamadas dentro del TTL.

**Alternatives considered**: Un endpoint administrativo exige un modelo de autorización
no solicitado; sincronizar desde `GET /players` añade latencia y efectos laterales; el
escalado horizontal sin lock distribuido no puede garantizar el límite y queda
explícitamente fuera del alcance.

## Auditoría y observabilidad

**Decision**: Persistir eventos `CatalogSyncAuditEvent` append-only (`STARTED`,
`COMPLETED`, `PARTIAL_FAILURE`, `FAILED`) con correlation ID y conteos. Implementar un
filtro `X-Correlation-ID` respaldado por MDC, logging JSON, Actuator health para
aplicación/PostgreSQL/Redis y métricas Micrometer para latencia, duración y errores.

**Rationale**: Cubre trazabilidad de requests y jobs, diagnóstico sin secretos,
auditoría inmutable de cambios de estado y health/métricas exigidos por la constitución.
Una protección en base rechaza UPDATE/DELETE sobre la tabla de auditoría.

**Alternatives considered**: Logs sin persistencia no son auditoría inmutable; aceptar
correlation ID sólo en HTTP deja jobs sin trazabilidad; checks ad hoc duplican las
capacidades estándar de Actuator/Micrometer.

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
consultas válidas después de un snapshot exitoso y `503 catalog_unavailable` cuando
nunca hubo snapshot exitoso y tampoco existen jugadores locales.

**Rationale**: Las lecturas quedan desacopladas de la red y respetan el contrato pedido.
La sincronización se dispara dentro de esta feature al arrancar si falta snapshot y por
cron; la lectura nunca invoca la fuente. El último evento terminal permite distinguir
un catálogo exitosamente vacío (`200 []`) de uno nunca inicializado (`503`).

**Alternatives considered**: Sincronizar en cada GET introduce latencia, efectos
laterales y dependencia externa; agregar `POST /players/sync` amplía la superficie y
requiere reglas de autorización no especificadas.

## Estrategia de pruebas

**Decision**: `PlayerCatalogServiceTest` usa Mockito; `PlayerControllerTest` usa MockMvc;
`FootballDataAdapterTest` enlaza `MockRestServiceServer`; y tests de integración validan
PostgreSQL/Flyway (constraints, índices, auditoría) y Redis (JSON, TTL y cache hit).

**Rationale**: Ninguna suite llama a Football-Data.org. Los tests unitarios aíslan cada
capa, mientras las integraciones prueban las garantías que un cache manager en memoria
o H2 no pueden demostrar: TTL/serialización Redis y constraints/migraciones PostgreSQL.

**Alternatives considered**: Llamar al sandbox del proveedor sería lento y no
determinista; WireMock funciona, pero es una dependencia extra para este cliente simple;
un único `@SpringBootTest` no aislaría responsabilidades.
