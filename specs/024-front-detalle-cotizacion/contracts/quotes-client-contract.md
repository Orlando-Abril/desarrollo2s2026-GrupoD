# Player Detail & Quotes Client Contract

Interfaz interna del frontend hacia los contratos remotos. No agrega ni modifica endpoints.

## Source precedence

- Detalle: `specs/022-detalle-jugador/contracts/` (`GET /players/{id}`).
- Cotizaciones: `specs/017-historial-cotizaciones/contracts/` (`GET /players/{id}/quotes`).
- Autenticación: Bearer JWT de la sesión, que agrega `httpClient`. Los adaptadores nunca agregan `X-API-KEY`.
- Transporte: `src/api/httpClient.js` (`request(path, { signal })`, timeout de 10 s, `ApiError` y `NetworkError`).

## `getPlayerDetail(id, options)` — `src/api/playerDetailApi.js`

- Inputs: `id: string` (ya validado por `parsePlayerId`), `options = { signal?: AbortSignal }`.
- Path: `/players/${encodeURIComponent(id)}`.
- Output: promesa de `PlayerDetailResponse` sin transformar.
- Errores: propaga `ApiError` (`status` 400/404/5xx) y `NetworkError` sin reinterpretarlos; la clasificación la hace el hook.

## `getPlayerQuotes(id, params, options)` — `src/api/quotesApi.js`

- Inputs: `id: string`, `params = { strategy?, from?, to?, at? }` (fechas `YYYY-MM-DD`), `options = { signal? }`.
- Construcción:
  1. Partir de `/players/${encodeURIComponent(id)}/quotes`.
  2. Agregar cada parámetro solo si no está vacío, con `URLSearchParams`.
  3. Si `at` está presente junto con `from` o `to`, lanzar `TypeError` **sin request**, porque el backend los rechaza combinados.
  4. No agregar parámetros desconocidos.
- Output: con `at`, promesa de `PlayerQuoteResponse` (objeto). Sin `at`, promesa de `PlayerQuoteResponse[]` en orden cronológico, tal como lo devuelve el backend.

| Input params | Path |
|---|---|
| `{}` | `/players/42/quotes` |
| `{ strategy: 'MOMENTUM' }` | `/players/42/quotes?strategy=MOMENTUM` |
| `{ strategy: 'OFFICIAL', from: '2026-09-28', to: '2026-10-27' }` | Los tres parámetros, sin importar el orden textual |
| `{ strategy: 'OFFICIAL', at: '2026-10-10' }` | `/players/42/quotes?strategy=OFFICIAL&at=2026-10-10` |
| `{ from: '', to: '', at: '' }` | `/players/42/quotes` |
| `{ at: '2026-10-10', from: '2026-10-01' }` | `TypeError`, sin `fetch` |

## Clasificación de errores (en `usePlayerDetail`)

| Origen | Condición | Estado |
|---|---|---|
| Detalle | `ApiError` 400 o 404 | `notFound` |
| Detalle | `ApiError` 401 | queda en `loading` hasta que la redirección de `onUnauthorized` desmonta la página |
| Detalle | otro `ApiError`, `NetworkError` | `error` (con reintento) |
| Historial | `200 []` | `empty` |
| Historial | cualquier error | `error` en la sección |
| Variación | cualquier error | variación `null` ("—"), sin UI de error |
| `at` | `ApiError` 404 | `empty` |
| `at` | otro error | `error` en la sección |
| Cualquiera | abortado | se ignora (no cambia el estado) |

## Tests requeridos (`*.test.js`)

- Paths de la tabla de arriba, incluido el `encodeURIComponent` del id.
- `signal` llega a `request`.
- `at` combinado con `from`/`to` lanza error sin llamar a `fetch`.
- Header `Authorization: Bearer` presente y sin `X-API-KEY`, verificado en un caso a través del `httpClient` real.
