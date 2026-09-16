# Contratos internos: Infraestructura de Seguridad

Esta feature no expone endpoints HTTP de negocio, por lo que no hay un contrato REST/OpenAPI de
"business API". Los contratos que sí entrega — y que las features de registro/login, catálogo y
trading van a consumir directamente — son interfaces Java y convenciones de configuración. Se
documentan aquí para que esas features puedan integrarse sin leer la implementación.

## 1. `JwtUtil` (`com.example.demo.security.JwtUtil`)

Clase de utilidad, sin estado de negocio, inyectable como bean de Spring.

| Método | Firma | Contrato |
|---|---|---|
| Generar token | `String generateToken(String username)` | Devuelve un JWT firmado (HS256) con `sub=username`, `iat=ahora`, `exp=ahora + security.jwt.expiration-ms`. Lanza `IllegalArgumentException` si `username` es `null`/blank. |
| Extraer username | `String extractUsername(String token)` | Devuelve el `subject` del token si la firma es válida. Lanza una excepción no chequeada de `jjwt` (`JwtException` o subclase) si el token es inválido, está mal formado o expirado — el caller decide cómo mapearla a una respuesta HTTP. |
| Validar token | `boolean isTokenValid(String token)` | `true` si la firma es correcta y no expiró; `false` en cualquier otro caso (nunca lanza excepción). Pensado para checks rápidos sin necesitar el username. |

**Quién lo consume**: la feature de login (para emitir el token al autenticar) y, en el futuro,
un `JwtAuthFilter` propio de esa feature (para validar tokens en requests subsiguientes). Esta
feature no lo conecta al `SecurityFilterChain` (ver `research.md` §3).

## 2. `ApiKeyAuthFilter` (`com.example.demo.security.ApiKeyAuthFilter`)

`OncePerRequestFilter` registrado en `SecurityConfig` antes del filtro de autenticación estándar.

**Contrato de comportamiento**:

| Escenario | Resultado |
|---|---|
| Header `X-API-KEY` ausente | 401, la cadena de filtros no continúa hacia el recurso protegido. |
| Header presente, no coincide con ningún `keyHash` almacenado | 401. |
| Header presente, coincide con un `ApiKey` con `active = false` | 401. |
| Header presente, coincide con un `ApiKey` con `active = true` | La request continúa (se puebla el `SecurityContext` con una `Authentication` autenticada); no se modifica el body/response. |
| Endpoint público (según `SecurityConfig`) | El filtro no exige el header en absoluto. |

El cuerpo de la respuesta 401 es un JSON mínimo y consistente:
`{"error": "unauthorized", "message": "<motivo>"}` — para que futuras features de frontend puedan
parsear el mismo formato sin importar si el rechazo vino de ApiKey o (en el futuro) de JWT.

**Contrato para quien genere/rote una `ApiKey` (obligatorio para la futura feature de
registro/login)**: `ApiKeyAuthFilter` busca por igualdad exacta contra `ApiKey.keyHash`, por lo
que el valor persistido en ese campo debe calcularse siempre como
`Hex.encode(SHA-256(rawKey))` (SHA-256 en minúsculas hexadecimal, sin separadores). Cualquier
componente que emita una API Key nueva (por ejemplo, al registrar un usuario) **MUST** usar
exactamente ese mismo algoritmo y esa misma codificación al calcular `keyHash` antes de
persistirlo — nunca `BCryptPasswordEncoder` (ver `research.md` §1: BCrypt genera una sal
aleatoria distinta en cada llamada, por lo que dos hashes del mismo valor nunca son iguales entre
sí, y este filtro necesita un lookup por igualdad exacta). Si esta convención no se respeta, el
filtro nunca podrá encontrar la key aunque el valor crudo sea correcto, y el fallo será silencioso
(401 sin ningún error explícito de configuración).

## 3. `SecurityConfig` — convención de rutas públicas

Cualquier feature que agregue un endpoint que deba ser público debe agregarlo explícitamente a la
lista de patrones públicos en `SecurityConfig` (no hay una anotación de "hazme público" a nivel de
controller en esta infraestructura). Rutas públicas iniciales entregadas por esta feature:

- `/auth/register`, `/auth/login` (reservadas para la feature de login, aún no implementadas)
- `/swagger-ui/**`, `/v3/api-docs/**`

Todo lo no listado requiere autenticación (ApiKey válida, o en el futuro JWT válido una vez que la
feature de login conecte su propio filtro).

## 4. `OpenApiConfig` — nombres de esquema de seguridad

Dos esquemas de seguridad quedan registrados y disponibles para anotar controllers futuros:

| Nombre de esquema | Tipo | Cómo lo usa un controller futuro |
|---|---|---|
| `bearerAuth` | HTTP Bearer (JWT) | `@SecurityRequirement(name = "bearerAuth")` a nivel de clase o método. |
| `apiKeyAuth` | API Key en header `X-API-KEY` | `@SecurityRequirement(name = "apiKeyAuth")` a nivel de clase o método. |

Estos nombres son el contrato: cualquier feature que agregue un controller protegido debe
referenciarlos exactamente así (sin redefinir sus propios esquemas) para que Swagger UI muestre
consistentemente cómo autenticarse.
