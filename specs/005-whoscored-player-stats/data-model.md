# Data Model: Estadísticas de rendimiento de jugadores desde WhoScored

**Feature**: `005-whoscored-player-stats` | **Date**: 2026-09-25 | **Research**: [research.md](./research.md)

## Entidades existentes (sin cambios)

### Player — tabla `players`

Sólo se **lee** para el matching: `id`, `fullName`, `team`, `league`. No se agregan campos, relaciones ni índices. `PlayerRepository` no cambia (se usa `findAll()`).

## Entidad nueva

### PlayerStats — tabla `player_stats`

Conjunto vigente de métricas crudas de WhoScored para un jugador (temporada en curso, totales).

| Campo (Java) | Columna | Tipo Java | Tipo SQL | Null | Descripción |
|---|---|---|---|---|---|
| `playerId` | `player_id` | `Long` | `bigint` PK, FK → `players.id` | no | Clave compartida con el jugador (`@MapsId`). |
| `player` | — | `Player` | — | no | `@OneToOne(fetch = LAZY, optional = false)` unidireccional. |
| `whoscoredPlayerId` | `whoscored_player_id` | `String` | `varchar(32)` | sí | Id del jugador en WhoScored, para trazabilidad. |
| `minutesPlayed` | `minutes_played` | `Integer` | `integer` | sí | Minutos jugados. |
| `goals` | `goals` | `Integer` | `integer` | sí | Goles. |
| `assists` | `assists` | `Integer` | `integer` | sí | Asistencias. |
| `shots` | `shots` | `Integer` | `integer` | sí | Tiros totales. |
| `keyPasses` | `key_passes` | `Integer` | `integer` | sí | Pases clave totales. |
| `tackles` | `tackles` | `Integer` | `integer` | sí | Tackles ganados totales (`tackleWonTotal`, "Tackles" en WhoScored). |
| `yellowCards` | `yellow_cards` | `Integer` | `integer` | sí | Tarjetas amarillas. |
| `redCards` | `red_cards` | `Integer` | `integer` | sí | Tarjetas rojas. |
| `rating` | `rating` | `BigDecimal` | `numeric(4,2)` | sí | Rating de WhoScored (p. ej. `7.35`). |
| `fetchedAt` | `fetched_at` | `Instant` | `timestamp with time zone` | no | Momento en que el dato se obtuvo **de WhoScored** (no el del guardado: un valor servido desde caché conserva su `fetchedAt` original). |

**Reglas de validación / integridad**

- **Ausente = `null`** (FR-002). Cero sólo si la fuente publica cero. La fuente es JSON numérico: campo ausente o `null` ⇒ `null` (ver [contracts/whoscored-source.md](./contracts/whoscored-source.md)).
- Enteros ≥ 0; `rating` en `[0, 10]`. Un valor fuera de rango o no numérico se registra en el log y se guarda como `null` (edge case "valor con formato inesperado").
- Al menos una métrica no nula para poder guardarse; si todas son `null` la fila se trata como falla (FR-011, R8).
- Uno por jugador, garantizado por la PK compartida (FR-010).
- Borrado: no hay borrado desde esta feature. Si en el futuro se elimina un jugador, su fila de estadísticas debe eliminarse antes (no se agrega cascada para no modificar `Player`).

**Ciclo de vida**

```text
(sin registro) --actualización exitosa--> [vigente v1]
[vigente vN]   --actualización exitosa--> [vigente vN+1]   (reemplazo total, incluidos null)
[vigente vN]   --falla / sin coincidencia / sin métricas--> [vigente vN]   (sin cambios)
(sin registro) --falla / sin coincidencia--> (sin registro)
```

**Repository**: `PlayerStatsRepository extends JpaRepository<PlayerStats, Long>` (sin métodos adicionales).

## Objetos transitorios (no persistidos)

### WhoScoredFeedResponse (adapter DTO)

Records Jackson de la respuesta del feed (`playerTableStats` con los campos del [contrato](./contracts/whoscored-source.md)); campos desconocidos ignorados. Transitorio: no se cachea.

### WhoScoredPlayerStats (adapter DTO; valor cacheado)

`record WhoScoredPlayerStats(String whoscoredPlayerId, String name, String whoscoredTeamId, List<String> teamNames, Integer minutesPlayed, Integer goals, Integer assists, Integer shots, Integer keyPasses, Integer tackles, Integer yellowCards, Integer redCards, BigDecimal rating, Instant fetchedAt)`

- Una fila `(playerId, teamId)` de una liga, combinada de las 4 respuestas del feed. `teamNames` son los nombres del equipo vistos en esas respuestas (corto y largo), usados para el matching de equipo.
- `hasAnyMetric()` indica si al menos una métrica es no nula.
- Es el valor guardado en la caché `whoscored-player-stats` con clave `Player.id` y TTL `whoscored.cache-ttl` (24 h por defecto). Se serializa como JSON tipado (mismo patrón que `FootballDataResponse`).

### StatsUpdateResult (service)

`record StatsUpdateResult(int processed, int updated, int fromCache, int unmatched, int failed)` — resumen de una ejecución, devuelto al scheduler para el log (FR-026). `processed = updated + unmatched + failed`; `fromCache ⊆ updated`. **No se persiste** (spec: sin tabla de ejecuciones).

## Relaciones

```text
players (1) ──── (0..1) player_stats      [player_stats.player_id = players.id]
```
