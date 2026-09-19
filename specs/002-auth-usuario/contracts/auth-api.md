# Contrato REST: `/auth/register` y `/auth/login`

Ambos endpoints son públicos (ya declarados en `SecurityConfig.PUBLIC_ROUTES`, sin cambios en
esta feature) y quedan documentados en Swagger UI / `/v3/api-docs` vía anotaciones springdoc en
`AuthController` y Bean Validation en los DTOs de request. Todo error usa el mismo shape:

```json
{ "error": "<código>", "message": "<detalle legible>" }
```

## POST /auth/register

Crea una cuenta nueva y emite su credencial de acceso inicial (`ApiKey`).

**Request body** (`RegisterRequest`):

```json
{
  "username": "abril",
  "email": "abril@example.com",
  "password": "unPasswordSeguro123"
}
```

| Campo | Tipo | Reglas |
|---|---|---|
| `username` | string | obligatorio, no vacío |
| `email` | string | obligatorio, formato de email válido |
| `password` | string | obligatorio, mínimo 8 caracteres |

**Respuesta exitosa — `201 Created`** (`RegisterResponse`):

```json
{
  "id": 1,
  "username": "abril",
  "email": "abril@example.com",
  "balance": 1000.00,
  "apiKey": "sk_9f3a1c7b2e4d6f8a0c1b3d5e7f9a1c3e"
}
```

> `apiKey` es el valor en texto plano. Se devuelve **únicamente en esta respuesta**; el sistema
> nunca vuelve a mostrarlo ni lo persiste en claro (sólo su hash SHA-256).

**Errores**:

| Código | `error` | Cuándo |
|---|---|---|
| `400 Bad Request` | `validation_error` | `username`/`email`/`password` ausentes o inválidos (Bean Validation). |
| `409 Conflict` | `duplicate_user` | El `username` o el `email` ya pertenecen a otra cuenta. |

## POST /auth/login

Autentica una cuenta existente y emite una credencial de sesión (JWT).

**Request body** (`LoginRequest`):

```json
{
  "username": "abril",
  "password": "unPasswordSeguro123"
}
```

| Campo | Tipo | Reglas |
|---|---|---|
| `username` | string | obligatorio, no vacío |
| `password` | string | obligatorio, no vacío |

**Respuesta exitosa — `200 OK`** (`LoginResponse`):

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer"
}
```

> `token` es el JWT emitido por `JwtUtil.generateToken(username)`. Se usa en requests
> posteriores como `Authorization: Bearer <token>`.

**Errores**:

| Código | `error` | Cuándo |
|---|---|---|
| `400 Bad Request` | `validation_error` | `username`/`password` ausentes (Bean Validation). |
| `401 Unauthorized` | `invalid_credentials` | El `username` no existe **o** el `password` no coincide. Mismo mensaje en ambos casos (no revela cuál de los dos motivos fue). |

## Qué NO cubre este contrato

- Rotación o recuperación de una `ApiKey` perdida: fuera de alcance (ver `spec.md` → Assumptions).
- Bloqueo de cuenta tras intentos fallidos repetidos de login: fuera de alcance.
- Cualquier endpoint que consuma el JWT o la ApiKey emitidos aquí (ya cubiertos por
  `ApiKeyAuthFilter` y el esquema `bearerAuth` de `OpenApiConfig`, ambos de la feature de
  seguridad transversal): no se modifican en esta feature.
