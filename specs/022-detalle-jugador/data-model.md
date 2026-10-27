# Data Model: Detalle de un jugador

No se agregan tablas ni se mutan entidades. La feature produce una vista agregada de sólo lectura sobre `players`, `player_stats` y `player_quotes`.

## PlayerDetailResponse

| Campo | Tipo | Regla |
|---|---|---|
| `player` | `PlayerResponse` | Obligatorio; copia exacta de la representación existente del catálogo |
| `stats` | `PlayerStatsResponse?` | Campo presente; `null` si no existe fila en `player_stats` |
| `quote` | `CurrentQuoteResponse?` | Campo presente; `null` si no existe cotización para la estrategia activa |

## PlayerResponse existente

| Campo | Tipo | Origen/regla |
|---|---|---|
| `id` | `Long` | `players.id` |
| `externalId` | `String` | `players.external_id`, conforme al contrato existente |
| `fullName` | `String` | Nombre persistido |
| `team` | `String` | Equipo persistido |
| `league` | `League` | Código de las cinco ligas soportadas |
| `positions` | `Set<Position>` | Copia inmutable; vacío si la entidad no aporta posiciones |
| `nationality` | `String?` | Nacionalidad opcional |
| `age` | `Integer?` | Edad opcional, no negativa cuando está presente |
| `marketValue` | `BigDecimal` | Valor de mercado persistido, no negativo |

`PlayerResponse` no se modifica; `PlayerDetailService` lo construye con su constructor actual.

## PlayerStatsResponse

| Campo | Tipo | Regla |
|---|---|---|
| `minutesPlayed` | `Integer?` | Minutos informados en la temporada |
| `goals` | `Integer?` | Goles informados |
| `assists` | `Integer?` | Asistencias informadas |
| `shots` | `Integer?` | Tiros informados |
| `keyPasses` | `Integer?` | Pases clave informados |
| `tackles` | `Integer?` | Tackles informados |
| `dribbles` | `Integer?` | Gambetas exitosas informadas |
| `yellowCards` | `Integer?` | Tarjetas amarillas informadas |
| `redCards` | `Integer?` | Tarjetas rojas informadas |
| `rating` | `BigDecimal?` | Rating con la precisión persistida |
| `fetchedAt` | `Instant` | Instante original de obtención del registro |

La existencia de la fila y la existencia de cada métrica son independientes: una fila puede producir un DTO con métricas nulas. `whoscoredPlayerId`, la entidad `Player` y detalles JPA no se exponen.

## CurrentQuoteResponse

| Campo | Tipo | Regla |
|---|---|---|
| `value` | `BigDecimal` | Valor no negativo, sin pérdida de escala |
| `score` | `BigDecimal?` | Nulo cuando el estado no tiene score |
| `status` | `QuoteStatus` | `CALCULATED`, `NO_STATS` o `INSUFFICIENT_HISTORY` |
| `strategy` | `String` | Código de la estrategia activa usada para buscar |
| `version` | `Integer` | Versión que produjo el registro, ≥ 1 |
| `calculatedAt` | `Instant` | Fecha efectiva de la cotización elegida |

No se exponen id interno, jugador, run id ni correlation id de la cotización.

## Relaciones y selección

```text
players (1) ─── (0..1) player_stats
   │
   └─────────── (0..N) player_quotes
                         │
                         └─ filtrar strategy_code = active().code()
                            ordenar calculated_at DESC, id DESC
                            tomar 0..1
```

1. `players.id` inexistente termina en 404 antes de componer el agregado.
2. `player_stats.player_id` comparte PK con el jugador; ausencia significa `stats: null`.
3. De `player_quotes` sólo se considera la serie de la estrategia activa al procesar el pedido.
4. La primera fila por `calculated_at DESC, id DESC` es `quote`; ausencia significa `quote: null`.
5. La operación no crea transiciones de estado: no persiste, actualiza ni invalida datos.

## Validación del identificador

- Texto no convertible a `Long`, overflow de `Long`, cero o negativo → 400 `validation_error`.
- Valor positivo convertible a `Long` sin fila en `players` → 404 `player_not_found`.
- Credenciales ausentes o inválidas → 401 antes de consultar o revelar existencia.
