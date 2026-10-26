# Data Model: Ranking de jugadores por cotización

No se agregan tablas ni se mutan entidades. Es una proyección de `players`, `player_positions` y `player_quotes`.

## RankingRowProjection (interna)

| Campo | Tipo | Origen/regla |
|---|---|---|
| `rank` | `long` | Puesto por valor DESC/id ASC antes de paginar |
| `playerId` | `long` | `players.id` |
| `name`, `team` | `String` | Fotografía de `players` |
| `league` | `String` | Código exacto |
| `currentValue` | `BigDecimal` | Fila histórica 1 |
| `previousValue` | `BigDecimal?` | Fila histórica 2 mediante `LEAD` |
| `strategy` | `String` | Código de la actual |
| `version` | `int` | Versión de la actual |

Las posiciones se cargan en bloque fuera de esta proyección para no duplicar filas.

## RankingEntryResponse (DTO nuevo)

| Campo | Tipo | Semántica |
|---|---|---|
| `rank` | `long` | ≥1, ordinal |
| `player` | `RankingPlayerResponse` | id, name, team, league y todas las positions |
| `value` | `BigDecimal` | Actual sin pérdida |
| `percentageChange` | `BigDecimal?` | Escala 2/HALF_UP; nulo sin anterior o anterior cero |
| `strategy` | `String` | Código efectivo |
| `version` | `int` | Versión actual ≥1 |

`RankingPlayerResponse` puede ser record anidado para mantener un único DTO nuevo de elemento; sus posiciones son una copia inmutable.

## RankingPageResponse

| Campo | Tipo | Regla |
|---|---|---|
| `items` | `List<RankingEntryResponse>` | Inmutable y ordenada |
| `page` | `int` | ≥0 |
| `size` | `int` | 1..100 |
| `totalElements` | `long` | Total filtrado antes de paginar |
| `totalPages` | `int` | `ceil(total/size)`, o 0 |

Es el valor exacto almacenado en `quotes-ranking`.

## RankingCacheKey

Componentes canónicos: estrategia efectiva, liga o `-`, posición o `-`, página y tamaño. No incluye sort. Estrategia omitida y activa explícita generan la misma clave.

## QuoteRunCompletedEvent

Contiene `UUID runId` de una corrida aceptada en estado terminal.

```text
aceptada -> RUNNING -> SUCCESS | FAILED -> evento -> limpiar quotes-ranking -> liberar guardia
```

La limpieza es total y fail-open.

## Reglas de selección

1. Particionar por jugador/estrategia y ordenar por fecha DESC/id DESC.
2. Fila 1 actual; fila 2 anterior.
3. Aplicar liga y posición conjuntamente.
4. Ordenar actual DESC/id de jugador ASC y asignar puesto.
5. Paginar al final.

## Índices existentes

- `idx_player_quotes_player_strategy_time(player_id, strategy_code, calculated_at)`
- `idx_player_quotes_strategy_time(strategy_code, calculated_at)`

No se agrega esquema. El quickstart exige pruebas funcionales en ambos motores y medición PostgreSQL.
