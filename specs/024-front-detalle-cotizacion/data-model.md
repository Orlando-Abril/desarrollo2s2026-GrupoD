# Data Model: Detalle de jugador y evolución de su cotización (frontend)

Modelos de cliente. Los esquemas remotos se definen en los OpenAPI de la 022 (`specs/022-detalle-jugador/contracts/`) y la 017 (`specs/017-historial-cotizaciones/contracts/`); acá solo se fija cómo los usa el frontend.

## Entidades remotas (sin transformación)

### PlayerDetail (`GET /players/{id}`)

| Campo | Tipo | Uso |
|---|---|---|
| `player` | `Player` | Igual que en el Álbum: `id`, `fullName`, `team`, `league`, `positions[]`, `nationality?`, `age?`, `marketValue`. |
| `stats` | `SeasonStats \| null` | Panel de estadísticas. Si es `null`, todas valen "—". |
| `quote` | `Quote \| null` | Cotización actual de la estrategia activa. Si es `null`, la cabecera muestra "—" y el gráfico arranca en `OFFICIAL`. |

### SeasonStats

`minutesPlayed`, `goals`, `assists`, `shots`, `keyPasses`, `tackles`, `yellowCards`, `redCards` (enteros, cada uno puede ser `null`), `rating` (decimal o `null`), `fetchedAt` (ISO). `dribbles` existe en el contrato pero no se muestra, porque la spec no lo pide.

Regla: `null` o ausente se muestra como "—", nunca como `0` (FR-020).

### Quote (`PlayerQuoteResponse`)

| Campo | Tipo | Uso |
|---|---|---|
| `value` | number ≥ 0 | Valor en créditos (`formatCredits`). |
| `score` | number \| null | No se muestra. |
| `status` | `CALCULATED` \| `NO_STATS` \| `INSUFFICIENT_HISTORY` | No cambia el dibujo; todos los puntos se grafican igual (edge case). |
| `strategy` | string | Tooltip y tabla. |
| `version` | integer ≥ 1 | Tooltip y tabla ("v1"). |
| `calculatedAt` | ISO date-time | Eje X (`Date.parse`), fecha del tooltip y de la tabla, en UTC. |

## Modelos de cliente

### QuotePoint (derivado de `Quote` para gráfico y tabla)

`{ t: number /* Date.parse(calculatedAt) */, value, strategy, version, calculatedAt }`, ordenados por `t` de menor a mayor. **El mismo array alimenta el `LineChart` y la tabla alternativa** (paridad SC-004).

### ChartSelection

| Campo | Valores | Inicial |
|---|---|---|
| `strategy` | `'OFFICIAL' \| 'MOMENTUM'` (`STRATEGIES` en `features/player-detail/quoteCatalog.js`) | `detail.quote?.strategy` si está en `STRATEGIES`; si no, `'OFFICIAL'` |
| `range` | `'month' \| 'quarter' \| 'season'` (`QUOTE_RANGES`: "Último mes", "3 meses", "Toda la temporada") | `'month'` |

`rangeToDates(range, now)` → `{ from, to }` en `YYYY-MM-DD` UTC:
- `month`: `from = now − 29 d`, `to = now`.
- `quarter`: `from = now − 89 d`, `to = now`.
- `season`: `{}`.

### Variation

`variation(currentValue, previousValue)` (recibe números) → `{ delta, percent, direction: 'up' | 'down' | 'flat' } | null`.
- Devuelve `null` si `currentValue` o `previousValue` son `null`, o si `previousValue === 0`.
- `delta = currentValue − previousValue`.
- `percent = delta / previousValue × 100`.
- `direction` sale del signo de `delta` redondeado a 2 decimales, así las diferencias que no se ven en la UI cuentan como `flat`.

`findPrevious(history, current)` → la última cotización de `history` con `Date.parse(calculatedAt) < Date.parse(current.calculatedAt)`, o `null`. Se compara con `Date.parse` y no como texto, para tolerar distinta precisión de segundos fraccionarios.

### PlayerId

`parsePlayerId(raw)` → string normalizado o `null`. Es válido si coincide con `/^[1-9]\d*$/` y es ≤ `9223372036854775807` (comparando longitud y texto). Si devuelve `null`, el estado es `notFound` sin request.

## Estados

### Detalle (`detail.status`)

```text
          id inválido ──────────────────────────────► notFound
loading ──► 200 ─────────────────────────────────────► ready
        ├─► 400 | 404 ───────────────────────────────► notFound
        └─► red | timeout | 5xx | otros ─────────────► error ──(reintentar)──► loading
```

Un 401 lo maneja `httpClient` (`onUnauthorized`) y la app redirige al ingreso. El hook queda en `loading` hasta que la redirección lo desmonta; la página no muestra estado propio.

### Historial del gráfico (`history.status`), activo solo con `detail.status === 'ready'`

```text
loading ──► 200 [] ──► empty     ("Todavía sin cotizaciones", con los selectores activos)
        ├─► 200 [..] ─► ready
        └─► error ────► error ──(reintentar)──► loading
cambio de strategy o range ──► abort de la consulta anterior ──► loading
```

### Variación (`variation`)

- No hay `quote`: `null`, sin request.
- Hay `quote`: se pide el historial completo de `quote.strategy`, se aplica `findPrevious` y luego `variation`.
- Si la consulta falla: `null`, sin mostrar error.

### Consulta puntual (`atQuery.status`)

```text
idle ──(Consultar con fecha válida)──► loading ──► 200 ──► found(quote)
                                               ├─► 404 ──► empty     ("Sin cotización para esa fecha")
                                               └─► otro ─► error ──(reintentar)──► loading
cambio de strategy del gráfico ──► abort ──► idle (se descarta el resultado)
```

Validación: la fecha es obligatoria y ≤ hoy UTC. Si es futura, se bloquea con el `max` del input y además se valida al enviar, mostrando el mensaje en el campo sin hacer request.

## Catálogo de la feature en `features/player-detail/quoteCatalog.js`

```js
STRATEGIES = { OFFICIAL: { label: 'Oficial' }, MOMENTUM: { label: 'Momentum' } }
QUOTE_RANGES = { month: { label: 'Último mes', days: 30 }, quarter: { label: '3 meses', days: 90 }, season: { label: 'Toda la temporada', days: null } }
STAT_FIELDS = [minutesPlayed 'Minutos', goals 'Goles', assists 'Asistencias', shots 'Tiros',
               keyPasses 'Pases clave', tackles 'Tackles', yellowCards 'Amarillas', redCards 'Rojas', rating 'Rating']
```
