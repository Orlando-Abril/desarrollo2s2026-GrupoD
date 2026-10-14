# Data Model: Resiliencia e2e de fuentes externas

## Purpose

Este documento describe los datos de prueba y estados observables de la feature. No introduce tablas ni cambios al modelo productivo: reutiliza `Player`, su colección de posiciones, los DTOs existentes y el caché configurado.

## 1. Fixture Player

Representa un jugador persistido por `E2EFixtures` antes de un escenario de lectura o sincronización fallida.

| Field | Type | Rules |
|-------|------|-------|
| `id` | Long generado | No se fija; debe permanecer igual antes/después de un sync fallido |
| `externalId` | String | Numérico, estable, único y no nulo dentro del set |
| `fullName` | String | No vacío y determinista |
| `team` | String | No vacío; equipo estable usado por filtros |
| `league` | `League` | Exactamente una de las cinco ligas soportadas |
| `positions` | Set de `Position` | Al menos una; una fixture contiene dos para probar pertenencia |
| `nationality` | String | No vacía |
| `age` | Integer | No negativa y estable |
| `heightCm` | Integer opcional | No forma parte de `PlayerResponse`; puede omitirse |
| `marketValue` | Decimal | Positivo y estable, escala compatible con el catálogo |

### Canonical fixture set

| externalId | fullName | team | league | positions |
|------------|----------|------|--------|-----------|
| `91001` | E2E Premier Forward | Arsenal FC | `PREMIER_LEAGUE` | `FORWARD` |
| `91002` | E2E Liga Utility | Real Madrid | `LA_LIGA` | `DEFENDER`, `MIDFIELDER` |
| `91003` | E2E Serie Goalkeeper | Inter | `SERIE_A` | `GOALKEEPER` |
| `91004` | E2E Bundesliga Midfielder | Bayern München | `BUNDESLIGA` | `MIDFIELDER` |
| `91005` | E2E Ligue Defender | Paris Saint-Germain | `LIGUE_1` | `DEFENDER` |

Los nombres son claramente sintéticos. Los valores restantes (nacionalidad, edad y cotización) deben definirse una vez en el helper y no repetirse en tests consumidores.

### Validation invariants

- Hay exactamente una fixture por cada valor de `League`.
- Los cinco `externalId` son únicos.
- Las cuatro posiciones aparecen al menos una vez.
- Premier League + Arsenal FC + FORWARD identifica exactamente `91001`.
- El set se persiste en una sola operación de alto nivel y se fuerza el flush antes del primer request.
- El helper se invoca una vez por escenario después de la limpieza e2e; una segunda invocación en el mismo escenario no es parte del contrato.

## 2. FixtureSet

Valor retornado por `E2EFixtures` para desacoplar a los tests del armado y de consultas directas al repositorio.

| Field/operation | Result | Rules |
|-----------------|--------|-------|
| `all` | Lista de cinco `Player` persistidos | Copia inmutable; no promete orden de IDs generados |
| `forLeague(league)` | Un `Player` | Falla de forma clara si la liga no está en el set |
| `forTeam(team)` | Un `Player` | Comparación exacta sobre el nombre canónico |
| `withPosition(position)` | Lista no vacía | Incluye jugadores multi-posición; resultado inmutable |

### Relationships

- `E2EFixtures` crea un `FixtureSet`.
- `FixtureSet` contiene cinco `Player` ya persistidos.
- Los escenarios transforman esos jugadores a `PlayerResponse` únicamente a través de `GET /players`.

## 3. Cached Competition Response

Representa la entrada que `@Cacheable` recupera para una liga sin ejecutar el cliente Football-Data.

| Field | Type | Rules |
|-------|------|-------|
| cache name | String | Exactamente `football-data-competition-teams`, referenciado mediante la constante de `CacheConfig` |
| key | String | Uno de `PL`, `PD`, `SA`, `BL1`, `FL1` |
| value | `FootballDataResponse` | Instancia tipada, nunca `Map` ni JSON manual |
| value.teams | Lista de `Team` | Una entrada válida por código en el escenario base |
| team.name | String | No vacío |
| team.squad | Lista de `SquadMember` | Un integrante válido |
| member.id | Long | Único entre las cinco entradas; se convierte a `externalId` |
| member.name | String | No vacío |
| member.position | String | Valor reconocido por `FootballDataMappings` |
| member.dateOfBirth | Fecha | Válida y anterior a hoy |
| member.nationality | String | No vacía |

### Cache key set

| Code | League produced by sync |
|------|-------------------------|
| `PL` | `PREMIER_LEAGUE` |
| `PD` | `LA_LIGA` |
| `SA` | `SERIE_A` |
| `BL1` | `BUNDESLIGA` |
| `FL1` | `LIGUE_1` |

### Lifecycle

```text
Absent (after E2E cleanup)
  -> Present and valid (test writes through configured CacheManager)
  -> Read without adapter body execution (POST /players/sync)
  -> Removed (E2E cleanup after test)
```

TTL and binary/JSON representation belong to `CacheConfig`; the test does not recreate them.

## 4. Authenticated Test Session

Estado efímero preparado antes de cada método.

| Field | Type | Rules |
|-------|------|-------|
| username | String | Estable dentro de la clase; la limpieza permite reutilizarlo por método |
| email | String | Formato válido |
| password | String | Cumple longitud mínima |
| token | String | No vacío, obtenido de `POST /auth/login` |
| tokenType | String | `Bearer` |

El token sólo se usa para los requests del método actual y no se comparte entre tests.

## 5. Catalog Snapshot

Lista ordenada de `PlayerResponse` obtenida por HTTP para comparar estado visible.

| Property | Rule |
|----------|------|
| Ordering | Por `externalId`, aplicado en el test |
| Equality | Igualdad de record sobre todos los campos expuestos |
| Before failed sync | Exactamente cinco respuestas correspondientes al fixture set |
| After failed sync | Igual a la instantánea anterior, incluidos IDs y posiciones |

### Failed-sync state transition

```text
Five persisted fixtures + empty Redis
  -> POST /players/sync
  -> HTTP 200 / status FAILED / processed 0 / failedLeagues 5 / failedPlayers 0
  -> Same catalog snapshot
```

No se permite transición a catálogo vacío, parcial o modificado.

## 6. Synchronization Summary

Reutiliza `PlayerSyncResponse`.

| Scenario | HTTP | status | processed | failedLeagues | failedPlayers |
|----------|------|--------|-----------|---------------|---------------|
| Fuente caída, caché vacío | 200 | `FAILED` | 0 | 5 | 0 |
| Cinco cache hits válidos | 200 | `COMPLETED` | 5 | 0 | 0 |

`PARTIAL_FAILURE` es un estado productivo válido, pero queda fuera de los dos estados preparados por esta feature.

## 7. Timed HTTP Observation

Valor lógico formado por respuesta y duración del intercambio objetivo.

| Field | Rule |
|-------|------|
| endpoint | `GET /players` o `POST /players/sync` |
| HTTP status | Exactamente 200 |
| duration | GET: menor o igual a 100 ms tras calentamiento; POST sync: menor o igual a 500 ms |
| included time | Desde invocación del `RestClient` hasta entidad de respuesta disponible |
| excluded time | Arranque, limpieza, fixture, Redis seed, registro y login |

El test debe fallar tanto por estado incorrecto como por superar el límite.
