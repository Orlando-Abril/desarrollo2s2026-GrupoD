# Contract: Smoke e2e HTTP

**Base URL**: `http://localhost:{local.server.port}`  
**Transport**: HTTP real contra la aplicación iniciada por el test  
**Precondition**: PostgreSQL y Redis están limpios y la salud de dependencias puede alcanzar `UP`

## Step 1 — Register

```http
POST /auth/register
Content-Type: application/json

{
  "username": "e2e-smoke",
  "email": "e2e-smoke@example.com",
  "password": "e2e-password-123"
}
```

Expected:

- HTTP `201 Created`.
- Response contiene `id`, `username`, `email`, `balance` y `apiKey` no vacía.
- El test no registra ni reutiliza la API key fuera de esta ejecución.

## Step 2 — Login

```http
POST /auth/login
Content-Type: application/json

{
  "username": "e2e-smoke",
  "password": "e2e-password-123"
}
```

Expected:

- HTTP `200 OK`.
- `token` presente y no vacío.
- `tokenType` igual a `Bearer`.

## Step 3 — Authenticated players query

```http
GET /players
Authorization: Bearer {token}
```

Expected:

- HTTP `200 OK`.
- Body JSON array; `[]` es válido.
- Header `X-Correlation-ID` presente y no vacío.
- No se llama Football-Data ni WhoScored.

## Step 4 — Health

```http
GET /actuator/health
```

Expected:

- HTTP `200 OK`.
- Body agregado contiene `{"status":"UP"}`.
- No se requieren detalles internos de componentes.

## Failure semantics

- Cualquier status distinto del esperado falla el test.
- Token o correlation ID ausente/vacío falla el test.
- Salud `DOWN` o HTTP 503 falla el test.
- Un error después de superar la condición de disponibilidad falla; no se convierte en skip.
