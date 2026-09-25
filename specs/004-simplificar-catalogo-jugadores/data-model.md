# Data Model: Simplificación del catálogo de jugadores

No se crean tablas ni entidades persistentes. El modelo existente se usa sin cambios de estructura (FR-027).

## Entidades persistentes (existentes, sin cambios)

### Player (`players`)

| Campo | Tipo | Regla en la carga |
|---|---|---|
| `id` | Long, autogenerado | — |
| `externalId` | String (`external_id`) | Id de Football-Data como texto. Es la clave para crear o actualizar; no puede repetirse, y lo garantiza el servicio (research R2) |
| `fullName` | String, obligatorio | `name` del jugador externo. Si viene vacío o nulo, el jugador se omite |
| `team` | String, obligatorio | `name` del equipo. Si viene vacío o nulo, se omite el equipo entero |
| `league` | `League`, obligatorio | Según el código de competición: `PL`, `PD`, `SA`, `BL1`, `FL1` |
| `positions` | `Set<Position>` (`player_positions`) | 0 o 1 posición, según `FootballDataMappings.position` |
| `nationality` | String, opcional | Si viene en blanco, se guarda `null` |
| `age` | Integer, opcional | Años entre `dateOfBirth` y hoy (UTC). `null` si no hay fecha |
| `heightCm` | Integer, opcional | No lo toca la carga |
| `marketValue` | BigDecimal, obligatorio | `1.00` al crear; no se modifica al actualizar. No implica tokens |

**Transiciones**: un jugador inexistente se crea; uno existente, identificado por `externalId`, se actualiza. La carga nunca borra jugadores.

### League / Position (enums existentes)

Sin cambios. Los valores de `League` son `PREMIER_LEAGUE`, `LA_LIGA`, `SERIE_A`, `BUNDESLIGA` y `LIGUE_1`. Los de `Position` son `GOALKEEPER`, `DEFENDER`, `MIDFIELDER` y `FORWARD`.

## Objetos no persistentes

### PlayerResponse (existente, sin cambios)

Es la proyección de `Player` que devuelve `GET /players`: `id`, `externalId`, `fullName`, `team`, `league`, `positions`, `nationality`, `age` y `marketValue`.

### PlayerSyncResponse (nuevo)

Es el resumen que devuelve `POST /players/sync`.

| Campo | Tipo | Significado |
|---|---|---|
| `status` | String: `COMPLETED`, `PARTIAL_FAILURE` o `FAILED` | `COMPLETED`: sin fallas. `FAILED`: hubo fallas y 0 jugadores procesados. `PARTIAL_FAILURE`: en cualquier otro caso |
| `processed` | int ≥ 0 | Jugadores creados o actualizados |
| `failedLeagues` | int, entre 0 y 5 | Ligas cuya consulta externa falló |
| `failedPlayers` | int ≥ 0 | Jugadores que no se pudieron guardar |

### FootballDataResponse (existente, sin cambios)

Es la respuesta externa (`teams[].name`, `teams[].squad[]` con `id`, `name`, `position`, `dateOfBirth` y `nationality`). Se cachea en Redis por código de liga, con TTL `football-data.cache-ttl`.

## Retiradas del alcance

| Entidad | Tabla | Estado |
|---|---|---|
| `PlayerTokenAllocation` | `player_token_allocations` | Se elimina el código. La tabla puede quedar en bases existentes, sin uso |
| `CatalogSyncAuditEvent` | `catalog_sync_audit_events` (con trigger) | Se elimina el código. La tabla y el trigger pueden quedar en bases existentes, sin uso |
| — | `flyway_schema_history` | Puede quedar en bases existentes, sin uso |
