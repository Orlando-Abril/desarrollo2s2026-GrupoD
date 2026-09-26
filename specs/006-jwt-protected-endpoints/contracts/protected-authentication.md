# Contrato: autenticación de rutas protegidas

Este contrato complementa, sin modificar, los contratos de `001-seguridad-infraestructura` y `002-auth-usuario`. Aplica a toda ruta no incluida en `SecurityConfig.PUBLIC_ROUTES`.

## Credenciales aceptadas

Una ruta protegida acepta **una** de estas alternativas:

1. `Authorization: Bearer <jwt>` válido.
2. `X-API-KEY: <api-key>` válida, cuando no se presentó un Bearer.

No es necesario presentar ambas credenciales.

## Reglas de decisión

| Authorization | X-API-KEY | Resultado |
|---|---|---|
| `Bearer <jwt>` válido | Ausente, válida o inválida | Autenticada con `principal = subject`; la API key no se evalúa |
| `Bearer <jwt>` vencido | Ausente, válida o inválida | `401` de JWT; sin fallback |
| `Bearer <jwt>` con firma inválida | Ausente, válida o inválida | `401` de JWT; sin fallback |
| `Bearer <valor mal formado>` | Ausente, válida o inválida | `401` de JWT; sin fallback |
| Ausente | Válida | Autenticada mediante API key |
| Ausente | Ausente, inválida o inactiva | Respuesta actual de `ApiKeyAuthFilter` |
| Esquema no Bearer, por ejemplo `Basic xxx` | Válida | Authorization se ignora; autenticada mediante API key |
| Esquema no Bearer | Ausente, inválida o inactiva | Authorization se ignora; respuesta actual de `ApiKeyAuthFilter` |

## Respuestas

### Bearer válido

- La solicitud continúa hacia el endpoint.
- El principal autenticado es el `subject` del token.
- No se consulta ni exige `X-API-KEY`.

### Bearer inválido, vencido o mal formado

Status: `401 Unauthorized`.

Content type: `application/json`, codificación UTF-8.

Body exacto:

```json
{
  "error": "unauthorized",
  "message": "Token inválido o vencido"
}
```

La cadena termina en esta respuesta, aun si `X-API-KEY` es válida.

### Sin Bearer

Se conserva íntegramente el contrato actual de `ApiKeyAuthFilter`:

- `401` y mensaje `Falta el header X-API-KEY` cuando falta la key.
- `401` y mensaje `API key inválida o inactiva` cuando no existe o no está activa.
- Continuación con principal autenticado cuando la API key es válida.

## Rutas públicas

Estas rutas omiten ambos mecanismos, incluso si reciben un Bearer inválido:

- `/auth/register`
- `/auth/login`
- `/swagger-ui/**`
- `/v3/api-docs/**`
- `/actuator/health`

La lista no se modifica en esta feature.

## OpenAPI

Cada operación protegida declara dos requisitos alternativos:

```yaml
security:
  - bearerAuth: []
  - apiKeyAuth: []
```

Dos objetos separados significan `bearerAuth` **O** `apiKeyAuth`. Agrupar ambos nombres dentro de un único objeto significaría que las dos credenciales son obligatorias simultáneamente.

Registro y login permanecen sin requisitos de seguridad y conservan `specs/002-auth-usuario/contracts/auth-api.md`.
