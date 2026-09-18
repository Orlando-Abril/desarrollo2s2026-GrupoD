# Phase 1 Data Model: Infraestructura Transversal de Seguridad

Esta feature **no agrega entidades ni tablas nuevas**. Sólo lee una entidad ya existente y define
conceptos no persistidos (claims de JWT) y nueva configuración de aplicación.

## Entidades existentes reutilizadas (sin cambios de esquema)

### ApiKey (`backend/src/main/java/com/example/demo/model/ApiKey.java`, tabla `api_keys`)

| Campo | Tipo | Uso en esta feature |
|---|---|---|
| `id` | `Long` | No se usa directamente en el filtro. |
| `keyHash` | `String` | Clave de búsqueda: `ApiKeyAuthFilter` hashea el valor crudo del header `X-API-KEY` (ver research.md §1) y lo compara por igualdad exacta vía `ApiKeyRepository.findByKeyHash`. |
| `keyPrefix` | `String` | No se usa en esta feature (es para UI de gestión de keys, fuera de alcance). |
| `owner` | `User` | No se resuelve a un `Authentication` completo en esta feature; ver research.md §3. |
| `active` | `boolean` | Debe ser `true` para que la request continúe; si es `false`, el filtro responde 401 (FR-007). |
| `createdAt` / `lastUsedAt` | `LocalDateTime` | Fuera de alcance actualizar `lastUsedAt` en esta feature (evitaría un side-effect de escritura en un filtro de sólo lectura de autorización); queda como mejora futura. |

No se agregan repositorios ni métodos nuevos: `findByKeyHash` ya existe en `ApiKeyRepository`.

## Conceptos no persistidos

### Claims del JWT (emitidos/leídos por `JwtUtil`)

| Claim | Origen | Descripción |
|---|---|---|
| `sub` (subject) | Parámetro `username` recibido al generar el token | Identifica al usuario dueño del token. Es lo único que `JwtUtil` conoce; no valida que el username exista (esa responsabilidad es de la feature de login). |
| `iat` (issued at) | Generado automáticamente por `jjwt` al construir el token | Marca de emisión. |
| `exp` (expiration) | `iat` + `security.jwt.expiration-ms` | Un token con `exp` en el pasado se considera inválido al validar. |

No hay una tabla ni entidad de "sesiones" o "tokens emitidos": el JWT es autocontenido y
stateless; no se persiste ni se puede revocar en esta feature (fuera de alcance, ver Assumptions
de `spec.md`).

## Nueva configuración de aplicación (no es un dato de dominio, pero se documenta aquí por ser
"estado" que otras features consumirán)

Claves nuevas a agregar en `backend/src/main/resources/application.properties` (con soporte de
override por variable de entorno, ninguna hardcodeada en código):

| Propiedad | Ejemplo / default | Uso |
|---|---|---|
| `security.jwt.secret` | `${JWT_SECRET}` (sin default embebido en el repo) | Clave de firma HS256 usada por `JwtUtil`. |
| `security.jwt.expiration-ms` | `3600000` | Tiempo de vida del token emitido por `JwtUtil`. |
| `security.cors.allowed-origin` | `http://localhost:5173` | Origen permitido por el `CorsConfigurationSource`. |

## Diagrama de dependencias (conceptual)

```text
Request HTTP
   │
   ├─ Header X-API-KEY ──► ApiKeyAuthFilter ──► ApiKeyRepository.findByKeyHash(hash(rawKey))
   │                                              └─ activo? → continúa filtro / 401
   │
   └─ (futuro) Header Authorization: Bearer ──► [fuera de alcance en esta feature]
                                                  └─ usará JwtUtil.validate/extractUsername

SecurityFilterChain
   ├─ Rutas públicas: /auth/register, /auth/login, /swagger-ui/**, /v3/api-docs/**
   ├─ Resto: anyRequest().authenticated()  (401 si no hay Authentication en el contexto)
   ├─ CORS: sólo http://localhost:5173
   └─ Session: STATELESS

OpenApiConfig
   └─ Esquemas de seguridad: bearerAuth (HTTP Bearer/JWT), apiKeyAuth (header X-API-KEY)
      (documentales; los controllers futuros los referencian con @SecurityRequirement)
```
