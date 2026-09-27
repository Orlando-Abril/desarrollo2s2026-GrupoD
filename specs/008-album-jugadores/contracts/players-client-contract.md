# Players Client Contract

Este contrato define la interfaz interna del frontend hacia el contrato externo de jugadores. No agrega ni modifica endpoints.

## Source precedence

- Datos, query y respuestas: `specs/004-simplificar-catalogo-jugadores/contracts/players-api.yaml`.
- Autenticación protegida: `specs/006-jwt-protected-endpoints`; el frontend usa Bearer JWT y no `X-API-KEY`.
- Transporte: `src/api/httpClient.js` y el contrato de Feature 007.

## Interface

### `getPlayers(filters, options)`

Inputs:

- `filters = {league?: string, team?: string, position?: string}`.
- `options = {signal?: AbortSignal}`.

Output: promesa de `PlayerResponse[]`.

## Request construction

1. Partir de `/players`.
2. Agregar `league`, `team` y `position` sólo si su valor no está vacío.
3. Usar serialización estándar de query; un equipo como `Real Madrid CF` debe viajar codificado y ser recuperable como el mismo valor.
4. No agregar búsqueda, página, tamaño, orden ni parámetros desconocidos.
5. Delegar en `request(path, {signal})`; `playersApi` nunca invoca `fetch`.
6. El cliente compartido agrega `Authorization: Bearer <jwt>` desde la sesión. El adaptador nunca agrega `X-API-KEY`.

Examples:

| Input | Path |
|---|---|
| `{}` | `/players` |
| `{league:'LA_LIGA'}` | `/players?league=LA_LIGA` |
| `{team:'Real Madrid CF'}` | `/players?team=Real+Madrid+CF` o codificación URL equivalente |
| `{league:'LA_LIGA',team:'Real Madrid CF',position:'FORWARD'}` | Los tres parámetros, sin requerir un orden textual específico |
| `{league:'',team:'',position:''}` | `/players` |

## Cancellation and timeout

- `request` conserva el timeout interno de 10.000 ms.
- La señal externa aborta el `fetch` interno y se desconecta en `finally`.
- Cancelación por reemplazo: el propietario comprueba que su señal está abortada y no publica error ni resultados.
- Timeout: la señal externa no está abortada; se propaga `NetworkError` y la UI presenta el estado de conexión.
- Una respuesta de una request abortada nunca puede reemplazar el estado de una request posterior.

## Response and errors

- 200: devolver el array completo, incluido `[]`; no paginar ni ordenar en el adaptador.
- 400, 5xx o respuesta inesperada: propagar `ApiError`/`NetworkError`; la pantalla usa el error genérico del álbum.
- 401 protegido: `httpClient` invoca `onUnauthorized`, `SessionContext` cierra sesión y navega a `/ingresar` con el mensaje contractual; la página no reemplaza ese flujo.

## Test obligations

- Omite los tres parámetros vacíos.
- Incluye cada combinación con valor y codifica espacios de equipo.
- Reenvía la señal a `httpClient`.
- `httpClient` aborta por señal externa sin perder su timeout ni callback de 401.
