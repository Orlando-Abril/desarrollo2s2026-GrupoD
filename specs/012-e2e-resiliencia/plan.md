# Implementation Plan: Resiliencia e2e de fuentes externas

**Branch**: `feature/test/e2e-resiliencia` | **Date**: 2026-10-13 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/012-e2e-resiliencia/spec.md`

**Note**: Este plan termina en Phase 1. La descomposición ejecutable se generará después con `$speckit-tasks`.

## Summary

Agregar evidencia e2e de que el catálogo continúa respondiendo desde datos persistidos o cacheados cuando Football-Data y WhoScored son inaccesibles. La implementación se limita a dos archivos Java nuevos de test: `E2EFixtures`, que persiste con `PlayerRepository` un conjunto determinista y reutilizable de jugadores de las cinco ligas, y `ExternalSourcesDownE2ETest`, que usa `@E2ETest`, `RestClient` y un usuario autenticado para validar lecturas, filtros, sincronización fallida no destructiva y sincronización satisfecha por Redis dentro de los umbrales de 100 ms para GET y 500 ms para POST.

El caso de caché obtiene el caché `CacheConfig.COMPETITION_TEAMS_CACHE` desde el `CacheManager` real y escribe `FootballDataResponse` para los cinco códigos. De ese modo la escritura usa exactamente el `JacksonJsonRedisSerializer<FootballDataResponse>` configurado por `CacheConfig`, sin reproducir su configuración ni modificarla.

## Technical Context

**Language/Version**: Java 17

**Primary Dependencies**: Spring Boot 4.1.1, Spring Web MVC (`RestClient`), Spring Data JPA, Spring Cache/Data Redis, Spring Security, JUnit Jupiter y AssertJ; todas existentes

**Storage**: PostgreSQL 16 real para jugadores y asociaciones de posiciones; Redis 7 real para `football-data-competition-teams`; ambos limpiados por el soporte e2e vigente

**Testing**: `@E2ETest`, JUnit Jupiter, AssertJ, Mockito mediante `@MockitoSpyBean`, `RestClient`, `E2EFixtures`, `CacheManager`, DTOs productivos de autenticación/jugadores/Football-Data y Maven Surefire por tag `e2e`

**Target Platform**: GitHub Actions sobre Linux y ejecución local en Windows PowerShell o Linux/macOS con PostgreSQL y Redis accesibles

**Project Type**: Aplicación web con backend Spring Boot y frontend separado; esta feature sólo agrega soporte y pruebas e2e al backend

**Performance Goals**: Cada `GET /players` termina en 100 ms o menos tras una solicitud equivalente de calentamiento no medida, y cada `POST /players/sync` termina en 500 ms o menos tras dos sincronizaciones equivalentes de calentamiento no medidas, una vez listo el contexto; preparación, login y carga de datos quedan fuera de la medición

**Constraints**: Un test por comportamiento verificable; HTTP real con Bearer JWT; fuentes externas en `127.0.0.1:1`; cero dependencias nuevas; cero cambios en archivos productivos o protegidos; no modificar `CacheConfig`; no ejecutar e2e en paralelo sobre la misma base/Redis; comparar catálogos sin depender del orden de consulta

**Scale/Scope**: Dos clases Java nuevas, siete métodos e2e, cinco fixtures base (uno por liga, cuatro posiciones representadas), cinco entradas cacheadas y cuatro artefactos de diseño más dos contratos

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

### Pre-design gate

| Principle | Evaluation | Result |
|-----------|------------|--------|
| §1.3 Resiliencia y desacoplamiento | Las lecturas se prueban con Football-Data y WhoScored inaccesibles; la sincronización fallida preserva los jugadores; el caso con caché demuestra operación sin fuente. | PASS |
| §3.3.1 Construcción de la base de jugadores | El caso cacheado entrega respuestas con forma externa para las cinco ligas y verifica que la sincronización construye/actualiza el catálogo. Los fixtures locales proporcionan el estado previo para los casos de lectura y no destrucción. | PASS |
| §5.2 Caché Redis obligatoria | El test usa el `RedisCacheManager` real y la configuración tipada registrada por `CacheConfig`; no usa un reemplazo en memoria. | PASS |
| §5.3 Observabilidad | Las respuestas HTTP reales mantienen el recorrido por filtros y correlation ID existentes; la feature no modifica observabilidad. | PASS por preservación |
| §7.2 Build y CI | Los nuevos tests conservan el tag `e2e` y entran en el `verify` ya usado por CI. | Se verifica al implementar |
| §7.3 SonarCloud | No se agregan dependencias ni duplicación deliberada; se reutilizan helper y DTOs existentes. | Se verifica en el PR |
| §7.6 Resiliencia | Cada comportamiento solicitado queda cubierto por una prueba e2e que exige respuesta no-5xx, GET ≤100 ms tras calentamiento y sync ≤500 ms tras calentamiento. | PASS |
| Gobernanza / Entrega 1 | No se modifica constitución ni archivos protegidos: `pom.xml`, `ci.yml`, `application.properties`, `SecurityConfig`, filtros o controllers. | PASS |

No existen violaciones ni decisiones pendientes. La planificación puede avanzar.

### Post-design gate

El diseño Phase 1 conserva todos los gates: los contratos asignan una prueba concreta a §1.3, §3.3.1 y §7.6; el modelo de caché exige Redis real y el serializer tipado ya configurado; el quickstart conserva la ejecución `verify`; y la estructura sólo incorpora dos archivos de test nuevos. La construcción desde datos externos simulados queda demostrada por los cinco `FootballDataResponse` cacheados que atraviesan el endpoint real y producen cinco jugadores persistidos. No hay excepciones constitucionales que justificar.

## Project Structure

### Documentation (this feature)

```text
specs/012-e2e-resiliencia/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── e2e-fixtures-contract.md
│   └── resilience-http-contract.md
└── tasks.md                         # se generará con $speckit-tasks
```

### Source Code (repository root)

```text
backend/
├── src/
│   └── test/
│       └── java/com/example/demo/
│           ├── e2e/
│           │   └── ExternalSourcesDownE2ETest.java   # nuevo
│           └── support/
│               └── E2EFixtures.java                  # nuevo
├── pom.xml                                            # protegido, sin cambios
└── src/test/resources/application-e2e.properties      # existente, sin cambios

.github/workflows/ci.yml                               # protegido, sin cambios
backend/src/main/java/com/example/demo/config/CacheConfig.java # existente, sólo lectura
```

**Structure Decision**: Mantener toda la implementación en `backend/src/test/java`. `E2EFixtures` será un `@Component` público definido exclusivamente en test scope: encapsulará `PlayerRepository` mediante inyección por constructor y permitirá que las features futuras inyecten sólo el helper, sin conocer el repositorio ni operaciones de persistencia. `ExternalSourcesDownE2ETest` será la única clase e2e nueva, marcada con `@E2ETest`, y preparará cliente, fixtures, autenticación y caché según cada método después de la limpieza automática.

## Design Decisions

### Helper reutilizable de jugadores

`E2EFixtures` estará anotado con `@Component` dentro de `src/test`, recibirá internamente `PlayerRepository` por constructor y expondrá una operación `createPlayersFromFiveLeagues()` que usa `saveAllAndFlush`. Las pruebas consumidoras inyectarán `E2EFixtures`, nunca `PlayerRepository`. El helper devolverá un `FixtureSet` con una lista inmutable y búsquedas por `League`, equipo y `Position`; así encapsula la persistencia y evita que las pruebas repitan builders o consultas.

El conjunto base tendrá external IDs numéricos estables y únicos, `marketValue` positivo, nacionalidad y edad válidas, y estos cinco casos:

| Liga | Equipo estable | Posición principal |
|------|----------------|--------------------|
| `PREMIER_LEAGUE` | Arsenal FC | `FORWARD` |
| `LA_LIGA` | Real Madrid | `DEFENDER` y `MIDFIELDER` |
| `SERIE_A` | Inter | `GOALKEEPER` |
| `BUNDESLIGA` | Bayern München | `MIDFIELDER` |
| `LIGUE_1` | Paris Saint-Germain | `DEFENDER` |

La combinación Premier League + Arsenal FC + FORWARD tendrá exactamente una coincidencia. El jugador multi-posición prueba que el filtro representa pertenencia al conjunto y no una columna singular. IDs de base generados no forman parte de la identidad estable; las comparaciones usan `externalId` y campos funcionales.

### Cliente HTTP y autenticación

Cada método parte de la limpieza de `@E2ETest`. La clase inyecta `E2EFixtures` y `CacheManager`, pero no `PlayerRepository`. Un `@BeforeEach` crea `RestClient` contra `@LocalServerPort`, registra un usuario único de la clase, inicia sesión y conserva el Bearer JWT. Registro y login son preparación y no se incluyen en los límites de 100 ms para GET y 500 ms para POST. Todas las consultas y sincronizaciones del alcance se realizan con `RestClient`; la persistencia sólo se usa detrás de `E2EFixtures` para preparar el estado.

Las listas HTTP se deserializan como `List<PlayerResponse>` y se ordenan por `externalId` antes de comparar, porque el contrato no promete orden. El resumen se deserializa como `PlayerSyncResponse` y se valida separando HTTP 200 de su campo funcional `status`.

### Inventario: un test por requerimiento observable

1. `returnsLocalPlayersWithoutFiltersWhenSourcesAreDown`: primero GET sobre catálogo vacío y luego GET con los cinco fixtures; ambas respuestas 200 en ≤100 ms, con colección vacía y contenido exacto respectivamente.
2. `filtersLocalPlayersByLeagueWhenSourcesAreDown`: GET con `league`, coincidencia exacta y ≤100 ms.
3. `filtersLocalPlayersByTeamWhenSourcesAreDown`: GET con equipo conocido y GET con equipo inexistente; ambas respuestas 200 en ≤100 ms, con coincidencia exacta y colección vacía respectivamente.
4. `filtersLocalPlayersByPositionWhenSourcesAreDown`: GET con `position`, todas y sólo las pertenencias esperadas y ≤100 ms.
5. `combinesLeagueTeamAndPositionWhenSourcesAreDown`: GET con los tres parámetros, intersección exacta y ≤100 ms.
6. `failedSyncReturnsFailedAndPreservesExistingPlayers`: instantánea HTTP previa, POST con Redis vacío, `FAILED`/0 procesados/5 ligas fallidas en ≤500 ms, y GET posterior idéntico en ≤100 ms tras calentamiento.
7. `syncUsesTypedRedisCacheWithoutCallingFootballData`: cinco payloads cacheados, POST `COMPLETED`/5 procesados/0 fallas en ≤500 ms y GET que confirma los cinco jugadores construidos en ≤100 ms tras calentamiento.

El helper queda ejercitado por los primeros seis escenarios y por tanto es reutilizable sin requerir una clase de prueba exclusiva que duplicaría la cobertura.

### Precarga de Redis con el serializer productivo

La prueba inyectará `CacheManager`, obtendrá `getCache(CacheConfig.COMPETITION_TEAMS_CACHE)` y escribirá una instancia `FootballDataResponse` bajo cada código `PL`, `PD`, `SA`, `BL1` y `FL1`. No usará `RedisTemplate`, bytes manuales ni un `ObjectMapper` propio. El `Cache` retornado pertenece al `RedisCacheManager` creado por `CacheConfig`, por lo que aplica el TTL y `JacksonJsonRedisSerializer<FootballDataResponse>` reales.

Cada payload tendrá un equipo y un `SquadMember` válido con ID numérico, nombre, posición reconocida por `FootballDataMappings`, fecha de nacimiento y nacionalidad. La limpieza anterior garantiza que no existan claves residuales y la limpieza posterior elimina las insertadas.

### Evidencia de cero llamadas externas

`ExternalSourcesDownE2ETest` declarará un `@MockitoSpyBean` sobre el `FootballDataAdapter` real. El spy conserva el comportamiento productivo y la intercepción de caché, pero permite reiniciar y observar invocaciones al target sin agregar instrumentación productiva. El caso cacheado limpiará el registro del spy inmediatamente antes del POST y exigirá `verifyNoInteractions(footballDataAdapter)` después de recibir la respuesta.

La evidencia combina observación directa y resultado funcional: las cinco claves están presentes, `POST /players/sync` devuelve `COMPLETED`, `processed=5`, `failedLeagues=0`, se persisten los cinco jugadores exactos y el adapter registra cero interacciones. El perfil mantiene Football-Data en `http://127.0.0.1:1/v4` como segunda barrera; el test no reemplaza el adapter ni el cliente por un mock y no modifica código productivo.

### Medición y ausencia de 5xx

Una utilidad privada del test envolverá exclusivamente cada intercambio objetivo con `assertTimeout`: `Duration.ofMillis(100)` para GET, después de una solicitud equivalente de calentamiento no medida, y `Duration.ofMillis(500)` para POST, también después de dos sincronizaciones equivalentes de calentamiento no medidas (la sync es idempotente: con Redis vacío todas las llamadas devuelven `FAILED` sin modificar datos; con caché la primera inserta y las siguientes actualizan los mismos cinco jugadores sin consultar Football-Data, por lo que se calientan ambos caminos y la medición recorre el de actualización ya ejecutado). Después se exigirá el estado exacto 200; por lo tanto cualquier 5xx falla de forma explícita. Se usa timeout no preemptivo para no abandonar requests o transacciones en segundo plano: si se excede, la llamada termina y JUnit informa la duración real.

### Preservación del catálogo fallido

El escenario `FAILED` comienza con Redis vacío por la limpieza común. Obtiene por HTTP una instantánea ordenada antes del POST y otra después. La comparación de `PlayerResponse` cubre `externalId`, nombre, equipo, liga, posiciones, nacionalidad, edad y valor de mercado; el ID persistente también debe permanecer igual. Además valida `processed=0`, `failedLeagues=5` y `failedPlayers=0`.

## Implementation Sequence

1. Crear `E2EFixtures` como `@Component` de test con sus cinco builders, `PlayerRepository` encapsulado, persistencia única y API `FixtureSet` para búsquedas funcionales.
2. Crear la estructura de `ExternalSourcesDownE2ETest`: `@E2ETest`, puerto aleatorio, inyección de `E2EFixtures`/`CacheManager`, `@MockitoSpyBean` del adapter real, cliente, registro/login y wrapper de request con GET ≤100 ms tras calentamiento y POST ≤500 ms.
3. Implementar los cinco tests de lectura con preparación explícita mediante `E2EFixtures` y comparación sin depender del orden.
4. Implementar el test de sincronización fallida con instantáneas HTTP anterior/posterior y contadores completos.
5. Implementar los builders de `FootballDataResponse`, cargar los cinco códigos a través del `CacheManager` real e implementar el test cacheado con `verifyNoInteractions` sobre el adapter real.
6. Ejecutar primero la clase específica, luego todo el tag e2e y finalmente `verify`, comprobando que los archivos protegidos continúan sin cambios.

## Validation Strategy

- Compilar tests para validar la API pública del helper, los DTOs y la deserialización genérica.
- Ejecutar `ExternalSourcesDownE2ETest` con PostgreSQL y Redis reales y confirmar siete tests aprobados.
- Repetir la clase para comprobar aislamiento y ausencia de dependencia de IDs autogenerados u orden.
- Ejecutar todos los tests con tag `e2e` para verificar convivencia con el smoke y el limpiador común.
- Ejecutar `verify` para la regresión completa y el reporte único de cobertura.
- Revisar el diff explícito de todos los archivos protegidos y de `CacheConfig`; debe estar vacío.
- Ejecutar dos veces el caso cacheado y confirmar en cada método limpio `COMPLETED`, cinco procesados, cero fallas y cero interacciones con `FootballDataAdapter`.

## Complexity Tracking

No se registran violaciones constitucionales ni complejidad excepcional. Toda la solución queda en test scope y reutiliza infraestructura, contratos y serialización existentes.
