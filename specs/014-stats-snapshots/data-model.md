# Data Model: Historial de snapshots de estadísticas

## PlayerStatsSnapshot

Registro histórico e inmutable de los totales de temporada que estaban persistidos para un jugador al ejecutar una captura.

### Tabla

`player_stats_snapshots`

| Campo de dominio | Columna | Tipo Java | Nulabilidad | Reglas |
|------------------|---------|-----------|------------|--------|
| `id` | `id` | `Long` | No | Clave primaria autogenerada; desempate estable para capturas con igual instante. |
| `player` | `player_id` | `Player` | No | `ManyToOne` lazy, unidireccional, sin cascada; FK a `players`. |
| `minutesPlayed` | `minutes_played` | `Integer` | Sí | Total acumulado copiado sin normalización. |
| `goals` | `goals` | `Integer` | Sí | Total acumulado. |
| `assists` | `assists` | `Integer` | Sí | Total acumulado. |
| `shots` | `shots` | `Integer` | Sí | Total acumulado. |
| `keyPasses` | `key_passes` | `Integer` | Sí | Total acumulado. |
| `tackles` | `tackles` | `Integer` | Sí | Total acumulado. |
| `dribbles` | `dribbles` | `Integer` | Sí | Gambetas exitosas acumuladas. |
| `yellowCards` | `yellow_cards` | `Integer` | Sí | Total acumulado. |
| `redCards` | `red_cards` | `Integer` | Sí | Total acumulado. |
| `rating` | `rating` | `BigDecimal` | Sí | Promedio acumulado; precisión 4, escala 2, igual a `PlayerStats`. |
| `sourceFetchedAt` | `source_fetched_at` | `Instant` | No | Copia exacta de `PlayerStats.fetchedAt`; forma la identidad lógica. |
| `capturedAt` | `captured_at` | `Instant` | No | Instante común de la corrida, obtenido del `Clock`. |
| `runId` | `run_id` | `UUID` | No | Identifica la corrida solicitante; no participa de la unicidad. |

### Metadatos y restricciones

- Entidad marcada con `@org.hibernate.annotations.Immutable`.
- Todas las columnas, salvo el id generado, usan `updatable = false`.
- Constructor sin argumentos protegido; creación mediante builder/fábrica con todos los datos obligatorios.
- Restricción única `uk_player_stats_snapshots_player_source` sobre `(player_id, source_fetched_at)`.
- Índice `idx_player_stats_snapshots_player_captured` sobre `(player_id, captured_at)`.
- No existe colección inversa en `Player`.
- No hay transiciones de estado: `NEW → PERSISTED`; una vez persistido no se actualiza ni se borra mediante el repositorio.

### Identidad y orden

- Identidad técnica: `id`.
- Identidad lógica/idempotencia: `(player_id, source_fetched_at)`.
- Orden reciente: `captured_at DESC, id DESC` para seleccionar; el servicio invierte el subconjunto a ascendente.
- Dos jugadores pueden compartir `source_fetched_at`; un jugador no puede repetirlo aunque cambien `run_id` o `captured_at`.

## SnapshotResult

Record inmutable devuelto por `snapshotAll(UUID runId)`.

| Campo | Tipo | Regla |
|-------|------|-------|
| `saved` | `int` | Cantidad de claves que esta invocación insertó; no negativo. |
| `skipped` | `int` | Cantidad ya existente al prefiltrar o ganada por otra corrida; no negativo. |

Invariante: `saved + skipped` equivale a la cantidad de filas de `PlayerStats` consideradas por la invocación. Los jugadores sin estadísticas no aparecen en ninguno de los dos contadores.

## StatsWindow

Record inmutable derivado de dos snapshots consecutivos; no se persiste.

| Campo | Tipo | Nulabilidad / cálculo |
|-------|------|-----------------------|
| `previousSnapshotId` | `Long` | No; identifica el extremo anterior. |
| `currentSnapshotId` | `Long` | No; identifica el extremo nuevo. |
| `currentRunId` | `UUID` | No; corrida que capturó el extremo nuevo (agregado en la feature 019 para detectar datos nuevos en MOMENTUM). |
| `fromCapturedAt` | `Instant` | No; captura anterior. |
| `toCapturedAt` | `Instant` | No; captura nueva. |
| `seasonReset` | `boolean` | Verdadero sólo cuando ambos minutos existen y los nuevos son menores. |
| `minutesPlayed` | `Integer` | Delta con piso cero, o total nuevo durante reinicio; `null` si falta un extremo en ventana normal. |
| `goals` | `Integer` | Misma regla de contador. |
| `assists` | `Integer` | Misma regla de contador. |
| `shots` | `Integer` | Misma regla de contador. |
| `keyPasses` | `Integer` | Misma regla de contador. |
| `tackles` | `Integer` | Misma regla de contador. |
| `dribbles` | `Integer` | Misma regla de contador. |
| `yellowCards` | `Integer` | Misma regla de contador. |
| `redCards` | `Integer` | Misma regla de contador. |
| `rating` | `BigDecimal` | Rating ponderado de ventana o rating nuevo durante reinicio; siempre entre 0 y 10 cuando no es `null`. |

### Reglas de derivación

Para cada contador `x` en una ventana normal:

```text
window.x = null                         si previous.x o current.x es null
window.x = max(0, current.x-previous.x) en otro caso
```

Para el rating normal:

```text
deltaMinutes = current.minutesPlayed - previous.minutesPlayed
rawRating = (current.rating * current.minutesPlayed
             - previous.rating * previous.minutesPlayed) / deltaMinutes
window.rating = clamp(rawRating, 0, 10)
```

El rating es `null` si falta cualquiera de los cuatro extremos requeridos o si `deltaMinutes <= 0`.

Durante un reinicio, contadores y minutos copian el valor nuevo, incluidos los `null`. El rating copia y acota el nuevo sólo cuando `current.rating` no es `null` y `current.minutesPlayed > 0`; en otro caso es `null`.

## Relaciones

```text
Player 1 ──────── * PlayerStatsSnapshot

PlayerStats (estado actual, 1 por Player)
        │ copia al ejecutar snapshotAll
        ▼
PlayerStatsSnapshot (historial append-only)
        │ pares consecutivos, sin persistir
        ▼
StatsWindow
```

`PlayerStats` permanece sin cambios y no referencia al historial. `StatsWindow` no es entidad y no tiene ciclo de vida propio.
