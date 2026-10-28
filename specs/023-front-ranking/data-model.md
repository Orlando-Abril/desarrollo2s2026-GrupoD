# Data Model: Pantalla de Ranking

La feature no agrega persistencia. Este documento describe el contrato remoto y los modelos de estado de la interfaz.

## RankingPage

| Field | Type | Rules |
|-------|------|-------|
| `items` | `RankingEntry[]` | Orden del servidor; puede ser vacío. |
| `page` | integer | API 0-based, mayor o igual a 0. |
| `size` | integer | Entre 1 y 100; 20 por defecto en esta pantalla. |
| `totalElements` | integer | Mayor o igual a 0. |
| `totalPages` | integer | 0 cuando no hay elementos; coherente con total y size. |

Relación: una página contiene cero o más entradas. La UI deriva `humanPage = page + 1`, `from = totalElements === 0 ? 0 : page * size + 1` y `to = page * size + items.length`.

## RankingEntry

| Field | Type | Rules |
|-------|------|-------|
| `rank` | integer | Mayor o igual a 1; global al conjunto filtrado, no al índice de página. |
| `player` | `RankingPlayer` | Obligatorio. |
| `value` | decimal | No negativo; se muestra en créditos. |
| `percentageChange` | decimal \| null | Positivo=sube; negativo=baja; 0=sin cambios; null=sin dato comparable. |
| `strategy` | string | `OFFICIAL` o `MOMENTUM` para las estrategias actuales. |
| `version` | integer | Mayor o igual a 1; trazabilidad de la cotización. |

La colección conserva el orden recibido. No hay transición local de rango o valor.

## RankingPlayer

| Field | Type | Rules |
|-------|------|-------|
| `id` | integer | Identidad estable y key de fila. |
| `name` | string | No vacío. |
| `team` | string | No vacío. |
| `league` | enum | Clave presente en `LEAGUES`. |
| `positions` | enum[] | Una o más claves de `POSITIONS`, únicas; se muestran todas. |

## RankingCriteria

| Field | Type | Initial | Validation / mapping |
|-------|------|---------|----------------------|
| `strategy` | `'' \| 'OFFICIAL' \| 'MOMENTUM'` | `''` | Vacío significa activa y se omite del query. |
| `league` | `'' \| LeagueCode` | `''` | Vacío se omite; código exacto del catálogo. |
| `position` | `'' \| PositionCode` | `''` | Vacío se omite; código exacto del catálogo. |
| `page` | integer UI 1-based | `1` | Se envía como `page - 1`; vuelve a 1 al cambiar cualquier filtro. |
| `size` | integer | `20` | Fijo en esta entrega. |

Relación: los criterios identifican un pedido. Retry conserva todos los valores; un cambio de filtro cambia el pedido y reinicia page.

## RankingViewState

| Field | Type | Meaning |
|-------|------|---------|
| `status` | `loading \| ready \| error` | Estado terminal o en curso del pedido vigente. |
| `data` | `RankingPage` | Última página aceptada; no se presenta durante loading de criterios nuevos. |
| `error` | `Error \| null` | Sólo error del pedido vigente y no abortado. |
| `retryKey` | integer | Dispara nuevamente el mismo pedido. |
| `effectiveStrategy` | `OFFICIAL \| MOMENTUM \| null` | Derivada del primer item; null si no puede inferirse. |

### State transitions

```text
mount ───────────────────────────────> loading
loading + current success ───────────> ready
loading + current failure ───────────> error
loading + aborted/stale completion ──> unchanged
ready/error + filter change ─────────> loading, page=1
ready/error + page change ───────────> loading, filters preserved
error + retry ───────────────────────> loading, criteria preserved
success outside retained page range ─> loading, page=last valid page
```

## VariationPresentation

| Input | Variant | Visible meaning | Accessible meaning |
|-------|---------|-----------------|--------------------|
| `value > 0` | `up` | Flecha arriba, signo positivo, porcentaje verde | “Subió {x} por ciento” |
| `value < 0` | `down` | Flecha abajo, signo negativo, porcentaje rojo | “Bajó {x} por ciento” |
| `value === 0` | `neutral` | `0 %` y “Sin cambios” | “Sin cambios” |
| `value === null` | `unknown` | “Sin dato” neutro | “Sin dato” |

Null nunca se normaliza a cero.
