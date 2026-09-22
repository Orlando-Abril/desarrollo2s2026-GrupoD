# Data Model: Football-Data Player Catalog

No se modifica la estructura Java de `Player`, `League` o `Position`. Se agregan tablas
separadas para asignación inicial de tokens y auditoría, además de índices/constraints
administrados por migraciones. Este documento describe el modelo completo de la feature.

## Player (entidad existente)

| Campo | Tipo | Regla de esta feature |
|---|---|---|
| `id` | `Long` | Identidad local autogenerada; se conserva en updates. |
| `externalId` | `String` | Id de persona de Football-Data; obligatorio para importar y clave lógica de upsert. |
| `fullName` | `String` | Obligatorio y no vacío; proviene del nombre del integrante del plantel. |
| `team` | `String` | Obligatorio y no vacío; nombre del equipo contenedor. |
| `league` | `League` | Una de las cinco ligas soportadas, derivada del código consultado. |
| `positions` | `Set<Position>` | Posiciones reconocidas sin duplicados; queda vacío si la fuente omite o entrega un valor no reconocido. |
| `nationality` | `String` | Opcional; se conserva `null` si la fuente no lo informa. |
| `age` | `Integer` | Opcional; años completos derivados de `dateOfBirth` al sincronizar. |
| `heightCm` | `Integer` | Fuera del alcance del adapter; se conserva en updates. |
| `marketValue` | `BigDecimal` | `1.00` para jugadores nuevos; nunca se sobrescribe en updates de catálogo. |

### Identidad y relaciones

- Identidad física: `players.id`.
- Identidad externa/idempotencia: `players.external_id`, consultada mediante el índice
  existente y protegida por una nueva constraint UNIQUE parcial para valores no nulos.
- `positions` se almacena en la colección existente `player_positions` relacionada por
  `player_id`.
- `League` y `Position` son enums, no entidades independientes.
- El equipo es el `String player.team` existente; no se crea una entidad `Team`.

### Reglas de actualización

- Coincidencia por `externalId`: actualizar nombre, equipo, liga, posiciones,
  nacionalidad y edad.
- Sin coincidencia: crear con valor de mercado inicial `1.00`.
- Ausencia de edad/nacionalidad: no invalida al jugador; se persiste como `null`.
- Posición ausente o no reconocida: no invalida al jugador; se persiste un conjunto vacío.
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
completo y el jugador se conserva con un conjunto de posiciones vacío.

## PlayerTokenAllocation (entidad nueva)

Representa la emisión constitucional inicial. No modifica la estructura de `Player`.

| Campo | Tipo | Regla |
|---|---|---|
| `id` | `Long` | Identidad autogenerada. |
| `playerId` | `Long` | FK a `players.id`, obligatoria y UNIQUE. |
| `totalSupply` | `Integer` | Obligatorio, CHECK exactamente `100`. |
| `ownerUserId` | `Long` | FK al superusuario inicial, obligatoria. |
| `ownerQuantity` | `Integer` | Obligatorio, CHECK exactamente `100`. |
| `basePrice` | `BigDecimal` | Obligatorio, CHECK exactamente `1.00`. |
| `createdAt` | `Instant` | Obligatorio e inmutable. |

La fila se crea en la misma transacción que un `Player` nuevo. La constraint UNIQUE por
`playerId` impide una segunda emisión durante reimportaciones o carreras concurrentes.

## CatalogSyncAuditEvent (entidad nueva append-only)

| Campo | Tipo | Regla |
|---|---|---|
| `id` | `Long` | Identidad autogenerada. |
| `correlationId` | `UUID/String` | Obligatorio; compartido por todos los eventos de una ejecución. |
| `eventType` | enum | `STARTED`, `COMPLETED`, `PARTIAL_FAILURE` o `FAILED`. |
| `occurredAt` | `Instant` | Obligatorio e inmutable. |
| `league` | `League/null` | Opcional para eventos generales; presente cuando el evento refiere una liga. |
| `receivedCount` | `Integer` | No negativo; cero por defecto. |
| `createdCount` | `Integer` | No negativo; cero por defecto. |
| `updatedCount` | `Integer` | No negativo; cero por defecto. |
| `skippedCount` | `Integer` | No negativo; cero por defecto. |
| `failureCode` | `String/null` | Código sanitizado, nunca stack trace, token ni payload completo. |

La aplicación sólo puede insertar eventos. La migración rechaza `UPDATE` y `DELETE` en
la tabla para garantizar inmutabilidad. Los eventos se escriben en transacciones
independientes para conservar intentos fallidos.

## Estado derivado del catálogo

No se agrega una entidad mutable de estado; se deriva de jugadores locales y del último
evento terminal:

| Condición | Estado | Respuesta `GET /players` |
|---|---|---|
| Existe `COMPLETED`/`PARTIAL_FAILURE` utilizable y hay jugadores | `AVAILABLE` | `200` con resultados. |
| Existe `COMPLETED` exitoso y no hay jugadores | `AVAILABLE_EMPTY` | `200 []`. |
| No existe snapshot exitoso, no hay jugadores y el arranque está pendiente o falló | `UNAVAILABLE` | `503 catalog_unavailable`. |

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
                                        + asignación inicial única (100 tokens)
fuente válida + externalId existente   -> campos catalogables actualizados
fuente incompleta sin identidad mínima -> entrada omitida
fuente incompleta con identidad mínima -> Player persistido con opcionales nulos/vacíos
fuente fallida + catálogo existente    -> catálogo sin cambios y aún consultable
fuente fallida + catálogo inexistente  -> indisponibilidad controlada
```

## Índices y constraints de migración

- UNIQUE parcial: `players(external_id)` cuando no es nulo.
- Índice de filtro: `players(league)`.
- Índice de filtro case-insensitive: `players(lower(team))`.
- Índice de filtro: `player_positions(position)`.
- UNIQUE y CHECKs de `player_token_allocations` descritos arriba.
- Protección append-only de `catalog_sync_audit_events`.
