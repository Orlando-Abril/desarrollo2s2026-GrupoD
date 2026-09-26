# Data Model: Base frontend y acceso a LaFigu

La feature no persiste entidades. Este modelo describe estado efímero, DTOs consumidos y catálogos cerrados.

## Session

| Field | Type | Rules |
|---|---|---|
| `token` | string | Obligatorio tras login; nunca se muestra ni persiste |
| `tokenType` | string | Valor contractual `Bearer` |
| `username` | string | Identidad visible; requerido para sesión activa |

**Derived**: `isAuthenticated = Boolean(token)`.  
**Lifecycle**: `none → active` por login exitoso; `active → none` por logout, 401 protegido, desmontaje o recarga.  
**Forbidden fields**: password, email, balance, apiKey, mensajes UI.

## LoginFormState

| Field | Type | Validation |
|---|---|---|
| `username` | string | Obligatorio/no vacío según backend |
| `password` | string | Obligatorio/no vacío según backend |
| `submitting` | boolean | Impide doble submit |
| `banner` | UIMessage/null | Error o información recibida por navegación |

La password existe sólo mientras el formulario está montado y durante la request.

## RegisterFormState

| Field | Type | Validation |
|---|---|---|
| `username` | string | Obligatorio y no compuesto sólo por espacios |
| `email` | string | Obligatorio; input `type="email"`; inválido si `validity.typeMismatch` es `true`; sin regex propia. Casos válidos: `abril@example.com`, `a+b@example.com`. Casos inválidos: `abril`, `abril@`, `@example.com`, `abril example.com`. El backend conserva autoridad final. |
| `password` | string | Obligatorio; mínimo 8 caracteres |
| `errors` | map | Máximo un mensaje por campo; se limpia al editar ese campo |
| `submitting` | boolean | Cubre registro y login automático como una sola operación |
| `banner` | UIMessage/null | Error de registro o transporte |

**Transitions**:

1. `idle → validating` al submit.
2. `validating → invalid` si existen errores; foco al primer campo inválido.
3. `validating → registering` si es válido.
4. `registering → autoLoggingIn` ante 201; la respuesta se normaliza descartando `apiKey`.
5. `autoLoggingIn → authenticated` ante 200; crea Session, navega a álbum y muestra toast 4 s.
6. `autoLoggingIn → createdWithoutSession` ante cualquier fallo; navega a ingreso con username e info, sin password.
7. Fallo de registro `→ idleWithError`; conserva valores editables y habilita reintento.

## DTOs consumidos

### RegisterRequest

`{username: string, email: string, password: string}` según `specs/002-auth-usuario/contracts/auth-api.md`.

### RegisterResult (normalizado en frontend)

`{id, username, email, balance}`. La respuesta backend también contiene `apiKey`, pero el adaptador la omite antes de devolver el resultado.

### LoginRequest

`{username: string, password: string}`.

### LoginResponse / Session input

`{token: string, tokenType: "Bearer"}`; se combina con el username del formulario al crear Session.

### ErrorResponse

`{error: string, message: string}`. Se traduce a `ApiError {status, code, message}`.

## HTTP error types

### ApiError

| Field | Type | Meaning |
|---|---|---|
| `status` | number | Código HTTP |
| `code` | string | `error` contractual o `unexpected_error` seguro |
| `message` | string | Mensaje backend para 400 válido o fallback seguro |

### NetworkError

Representa red, CORS y timeout. No expone detalles internos; la UI usa el texto contractual de conexión.

## UIMessage

| Field | Values | Rules |
|---|---|---|
| `variant` | `error`, `info`, `status` | Determina semántica, no texto libre de estilo |
| `message` | string | Texto contractual exacto |
| `duration` | number/null | Sólo toast de bienvenida: 4000 ms |

## NavigationItem

`{id, label, numero, ruta, habilitado}` en este orden:

1. `album`, `Álbum`, `01`, `/album`, `true`.
2. `ranking`, `Ranking`, `02`, sin ruta operativa, `false`.
3. `mercado`, `Mercado`, `03`, sin ruta operativa, `false`.
4. `portfolio`, `Mi portfolio`, `04`, sin ruta operativa, `false`.

Sólo un ítem habilitado puede representarse como link. Los demás declaran `aria-disabled="true"`.

## Catalog domain maps

### LEAGUES

- `PREMIER_LEAGUE`: `{label: "Premier League", colorVar: "--liga-premier"}`
- `LA_LIGA`: `{label: "La Liga", colorVar: "--liga-laliga"}`
- `SERIE_A`: `{label: "Serie A", colorVar: "--liga-seriea"}`
- `BUNDESLIGA`: `{label: "Bundesliga", colorVar: "--liga-bundesliga"}`
- `LIGUE_1`: `{label: "Ligue 1", colorVar: "--liga-ligue1"}`

### POSITIONS (orden contractual)

1. `GOALKEEPER`: `{abbr: "ARQ", label: "Arquero"}`
2. `DEFENDER`: `{abbr: "DEF", label: "Defensor"}`
3. `MIDFIELDER`: `{abbr: "MED", label: "Mediocampista"}`
4. `FORWARD`: `{abbr: "DEL", label: "Delantero"}`

## FiguritaProps

| Field | Type | Presentation rule |
|---|---|---|
| `id` | integer | `String(id).padStart(3, '0')` |
| `fullName` | string | Uppercase visual, ellipsis, nombre completo en `title` |
| `team` | string | Ellipsis |
| `league` | League enum | Label y variable de color desde LEAGUES |
| `positions` | Position[] | Primera coincidencia según orden POSITIONS; vacío → `—` |
| `nationality` | string/null | null → `—` |
| `age` | integer/null | null → `—` |
| `marketValue` | number ≥ 0 | `formatCredits` con locale `es-AR` |

Compatible con `PlayerResponse` de feature 004; `externalId` se omite deliberadamente porque no participa de la presentación.
