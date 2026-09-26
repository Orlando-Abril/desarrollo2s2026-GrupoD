# Frontend HTTP Client Contract

Este contrato describe la interfaz interna obligatoria del frontend y su consumo del contrato externo de auth. No agrega endpoints.

## Configuration

- Base URL: `import.meta.env.VITE_API_BASE_URL`; nunca literal en código.
- Timeout: 10.000 ms por request mediante `AbortController`.
- Único emisor de `fetch`: `src/api/httpClient.js`.
- Configuración de sesión: accessor del token vigente y callback `onUnauthorized` registrables/limpiables.
- Una request debe declarar `publicRequest`; `/auth/register` y `/auth/login` son públicas.

## Request behavior

1. Unir base URL y path con un solo `/`.
2. Para body JSON, enviar `Content-Type: application/json` y serializar una vez.
3. Si el accessor devuelve token, agregar `Authorization: Bearer <token>`.
4. Crear controller/timer, pasar `signal` a fetch y limpiar el timer siempre.
5. Leer el cuerpo una sola vez y parsear JSON de forma segura.

No se escriben credenciales en logs, storage, cookies, URL ni navigation state.

## Success behavior

- Respuesta exitosa JSON válida: devuelve el payload normalizado por el adaptador de dominio.
- Cuerpo exitoso vacío: sólo se admite si el consumidor lo declara; auth espera JSON.
- JSON inválido cuando se espera JSON: `ApiError` inesperado seguro.

## Error behavior

### ApiError

Se lanza ante respuesta HTTP no exitosa y contiene `{status, code, message}`.

- Body válido `{error,message}`: conserva `error` como `code` y `message`.
- Body vacío/malformado o forma inesperada: `code = unexpected_error` y mensaje interno seguro.
- Un 401 no público invoca una vez `onUnauthorized` y después lanza `ApiError`.
- Un 401 público nunca invoca `onUnauthorized`.

### NetworkError

Se lanza ante rechazo de red/CORS o abort por timeout. La UI lo mapea a `✕ No se pudo conectar con el servidor. Intentá de nuevo.`.

### UI mapping

| Condition | Exact user message |
|---|---|
| 401 + `invalid_credentials` en login | `✕ Usuario o contraseña incorrectos.` |
| 409 + `duplicate_user` en register | `✕ El usuario o el email ya está registrado.` |
| 400 + `validation_error` | `message` del backend, presentado como banner de error |
| NetworkError | `✕ No se pudo conectar con el servidor. Intentá de nuevo.` |
| 5xx, malformed o inesperado | `✕ Algo salió mal. Intentá de nuevo en unos minutos.` |
| 401 protegido | logout, `/ingresar`, info `Tu sesión venció. Volvé a ingresar.` |

## Auth adapter

### `register(data)`

- Request: `POST /auth/register` pública.
- Body: `{username,email,password}`.
- Success: 201; normaliza a `{id,username,email,balance}`.
- `apiKey`: se omite en el borde; nunca se retorna a UI, se loguea ni se almacena.
- Errors: 400 `validation_error`, 409 `duplicate_user`.

### `login(data)`

- Request: `POST /auth/login` pública.
- Body: `{username,password}`.
- Success: 200 `{token,tokenType:"Bearer"}`.
- Errors: 400 `validation_error`, 401 `invalid_credentials`.

Fuente externa prevalente: `specs/002-auth-usuario/contracts/auth-api.md`.

## Test obligations

- 400, 401 público/protegido, 409, 5xx y body inválido.
- Red y abort exacto a 10.000 ms con timer limpiado.
- Header Bearer tomado del accessor vigente.
- Callback sólo para 401 no público.
- Normalización de registro sin `apiKey`.
