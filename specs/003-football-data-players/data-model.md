# Data Model: Football-Data Player Catalog

No se agregan tablas ni se modifica la estructura de `Player`, `League` o `Position`.
Este documento describe cómo la feature usa el modelo existente y sus DTOs de borde.

## Player (entidad existente)

| Campo | Tipo | Regla de esta feature |
|---|---|---|
| `id` | `Long` | Identidad local autogenerada; se conserva en updates. |
| `externalId` | `String` | Id de persona de Football-Data; obligatorio para importar y clave lógica de upsert. |
| `fullName` | `String` | Obligatorio y no vacío; proviene del nombre del integrante del plantel. |
| `team` | `String` | Obligatorio y no vacío; nombre del equipo contenedor. |
| `league` | `League` | Una de las cinco ligas soportadas, derivada del código consultado. |
| `positions` | `Set<Position>` | Al menos una posición reconocida para datos importados; valores desconocidos se registran y se omiten o normalizan según la tabla de mapeo. |
| `nationality` | `String` | Opcional; se conserva `null` si la fuente no lo informa. |
| `age` | `Integer` | Opcional; años completos derivados de `dateOfBirth` al sincronizar. |
| `heightCm` | `Integer` | Fuera del alcance del adapter; se conserva en updates. |
| `marketValue` | `BigDecimal` | `1.00` para jugadores nuevos; nunca se sobrescribe en updates de catálogo. |

### Identidad y relaciones

- Identidad física: `players.id`.
- Identidad externa/idempotencia: `players.external_id`, consultada mediante el índice
  existente `idx_players_external_id`.
- `positions` se almacena en la colección existente `player_positions` relacionada por
  `player_id`.
- `League` y `Position` son enums, no entidades independientes.
- El equipo es el `String player.team` existente; no se crea una entidad `Team`.

### Reglas de actualización

- Coincidencia por `externalId`: actualizar nombre, equipo, liga, posiciones,
  nacionalidad y edad.
- Sin coincidencia: crear con valor de mercado inicial `1.00`.
- Ausencia de edad/nacionalidad: no invalida al jugador.
- Ausencia de id, nombre o equipo: omitir esa entrada y producir log diagnóstico.
- Una respuesta parcial o vacía no borra jugadores existentes.
- Duplicados dentro de una misma respuesta se consolidan por `externalId` antes de
  guardar.

## League (enum existente)

| Football-Data | Dominio |
|---|---|
| `PL` | `PREMIER_LEAGUE` |
| `PD` | `LA_LIGA` |
| `SA` | `SERIE_A` |
| `BL1` | `BUNDESLIGA` |
| `FL1` | `LIGUE_1` |

## Position (enum existente)

| Valor fuente típico | Dominio |
|---|---|
| `Goalkeeper` | `GOALKEEPER` |
| `Defence`, `Defender` | `DEFENDER` |
| `Midfield`, `Midfielder` | `MIDFIELDER` |
| `Offence`, `Forward` | `FORWARD` |

Un valor no reconocido no crea enums nuevos. El caso se registra sin el payload
completo y el integrante se omite si no queda ninguna posición válida.

## DTOs privados del adapter

### CompetitionTeamsSnapshot

| Campo | Tipo | Regla |
|---|---|---|
| `competitionCode` | `String` | Código usado como clave de caché. |
| `teams` | `List<FootballDataTeam>` | Lista vacía aceptada. |

### FootballDataTeam

| Campo | Tipo | Regla |
|---|---|---|
| `id` | `Long` | Sólo diagnóstico; no se persiste en Player. |
| `name` | `String` | Identidad de equipo requerida para jugadores asociados. |
| `squad` | `List<FootballDataPerson>` | Puede faltar o estar vacía. |

### FootballDataPerson

| Campo | Tipo | Regla |
|---|---|---|
| `id` | `Long` | Se convierte a `externalId`; requerido. |
| `name` | `String` | Requerido. |
| `position` | `String` | Debe mapear a un valor local. |
| `dateOfBirth` | `LocalDate` | Opcional; origen de `age`. |
| `nationality` | `String` | Opcional. |

## DTO público PlayerResponse

| Campo | Tipo JSON | Regla |
|---|---|---|
| `id` | integer | Id local. |
| `externalId` | string | Id de Football-Data para correlación. |
| `fullName` | string | Nombre completo. |
| `team` | string | Equipo actual según última sincronización. |
| `league` | string | Valor de `League`. |
| `positions` | array[string] | Valores de `Position`, sin duplicados. |
| `nationality` | string/null | Opcional. |
| `age` | integer/null | Opcional y no negativo. |
| `marketValue` | number | Valor vigente, no modificado por esta sincronización. |

## Estados y transiciones

```text
fuente válida + externalId nuevo       -> Player creado (marketValue 1.00)
fuente válida + externalId existente   -> campos catalogables actualizados
fuente incompleta sin identidad mínima -> entrada omitida
fuente fallida + catálogo existente    -> catálogo sin cambios y aún consultable
fuente fallida + catálogo inexistente  -> indisponibilidad controlada
```

No se modela un estado persistente adicional para la sincronización.
