# Data Model: El álbum de jugadores

La feature no persiste datos. El modelo describe DTOs consumidos, estado efímero y proyecciones derivadas.

## PlayerResponse

Fuente: `specs/004-simplificar-catalogo-jugadores/contracts/players-api.yaml`.

| Field | Type | Rules |
|---|---|---|
| `id` | integer | Obligatorio; identificador visible como N° |
| `externalId` | string | Obligatorio en respuesta; no se presenta |
| `fullName` | string | Obligatorio; clave de búsqueda y orden español ascendente |
| `team` | string | Obligatorio; nombre completo y opción de filtro |
| `league` | enum | Una clave de `LEAGUES` |
| `positions` | Position[] | Campo obligatorio, array de cero o más claves únicas de `POSITIONS`; vacío → `—`, lista muestra todas y carta aplica el orden compartido |
| `nationality` | string/null | `null` se presenta como `—` |
| `age` | integer/null | No negativo; `null` se presenta como `—` |
| `marketValue` | number | No negativo; se presenta con `formatCredits` |

## AlbumFilters

| Field | Type | Default | Effect |
|---|---|---|---|
| `league` | League/`""` | `""` | Query remota cuando no está vacío |
| `position` | Position/`""` | `""` | Query remota cuando no está vacío |
| `team` | string/`""` | `""` | Query remota cuando no está vacío; nunca se envía blanco |
| `search` | string | `""` | Filtro local normalizado sobre `fullName` |

**Invariants**:

- Cambiar `league` o `position` establece `team=""` y `page=1` en la misma acción.
- Cambiar `team` o `search` establece `page=1`.
- Limpiar establece todos los defaults y `page=1`.
- `search` no dispara una consulta remota.

## AlbumQueryState

| Field | Type | Meaning |
|---|---|---|
| `status` | `loading | ready | error` | Estado de la última consulta vigente |
| `responsePlayers` | PlayerResponse[] | Última respuesta remota vigente, incluso si está vacía |
| `teamSourcePlayers` | PlayerResponse[] | Último éxito cuya consulta omitió `team` |
| `initialTotal` | integer/null | Tamaño del primer éxito sin liga, posición ni equipo |
| `retryKey` | integer | Incremento que repite la consulta actual |
| `error` | null/error | Sólo para diagnóstico interno; UI usa mensaje contractual fijo |

**Request transitions**:

1. `idle/ready/error → loading` al montar, cambiar filtro remoto o reintentar.
2. Una nueva consulta aborta la anterior; la anterior no puede transicionar estado.
3. `loading → ready` ante 200, reemplazando `responsePlayers`.
4. Si la consulta exitosa no contiene equipo, también reemplaza `teamSourcePlayers`.
5. Si además no contiene liga ni posición y `initialTotal` es null, fija el total inicial.
6. `loading → error` ante red, timeout, 400 o 5xx.
7. Ante 401, `SessionContext` termina la sesión y navega; no se presenta un estado local definitivo.
8. Un abort externo por cleanup no produce transición visible.

## DerivedAlbumResults

No se almacena; se memoriza desde `responsePlayers`, `filters.search` y `page`.

| Field | Type | Derivation |
|---|---|---|
| `searchedPlayers` | PlayerResponse[] | Nombre normalizado incluye búsqueda normalizada |
| `sortedPlayers` | PlayerResponse[] | Copia ordenada por `fullName.localeCompare(..., 'es')` |
| `items` | PlayerResponse[] | Segmento de 24 correspondiente a página segura |
| `total` | integer | Cantidad posterior a búsqueda y filtros remotos |
| `totalPages` | integer | `ceil(total/24)`, o 0 para total 0 |
| `page` | integer | 1 para vacío; en otro caso limitada a `[1,totalPages]` |
| `from` | integer | 0 si vacío; en otro caso índice humano inicial |
| `to` | integer | 0 si vacío; en otro caso índice humano final |
| `pageWindow` | Array<number/ellipsis> | Primera, actual ±1, última y elipsis por hueco |

## TeamOption

`{value: string, label: string}` derivado de `teamSourcePlayers`.

- Unicidad por nombre exacto recibido.
- Orden por `localeCompare('es')`.
- `Todos` no forma parte del modelo derivado; `AlbumFilters` lo agrega como opción default con valor vacío.

## AlbumViewMode

Valores: `cards | list`. Default: `cards`.

- Vive sólo durante el montaje/carga actual de la aplicación.
- No cambia filtros, página ni resultados.
- Se proyecta a `aria-pressed` en los dos botones.

## AlbumPresentationState

La página elige exactamente una salida principal:

| Priority | Condition | Presentation |
|---|---|---|
| 1 | `status=loading` | 8 Casilleros cargando, sin paginador |
| 2 | `status=error` | EstadoPanel de conexión con Reintentar |
| 3 | `ready`, respuesta inicial sin filtros/búsqueda y total 0 | EstadoPanel Álbum vacío, sin botón |
| 4 | `ready`, filtros o búsqueda activos y resultado total 0 | EstadoPanel 0 resultados con Limpiar filtros |
| 5 | `ready`, total > 0 y mode cards | AlbumGrid + Pager |
| 6 | `ready`, total > 0 y mode list | AlbumTable + Pager |

Los filtros permanecen disponibles en estados `ready` y `error`; durante carga no existe paginador. Las cartas y filas son siempre no interactivas.
