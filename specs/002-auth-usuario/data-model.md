# Phase 1 Data Model: Registro e Inicio de Sesión de Usuario

Esta feature **no agrega entidades, tablas ni columnas nuevas**. Reutiliza `User` y `ApiKey` tal
cual existen hoy, y define DTOs no persistidos (frontera HTTP) más dos excepciones de dominio.

## Entidades existentes reutilizadas (sin cambios de esquema)

### User (`backend/src/main/java/com/example/demo/model/User.java`, tabla `users`)

| Campo | Tipo | Uso en esta feature |
|---|---|---|
| `id` | `Long` | Se devuelve en `RegisterResponse`. |
| `username` | `String` (unique) | Entrada de registro y de login. `AuthService` normaliza a minúsculas (`toLowerCase()`, research.md §7) antes de comparar o persistir, valida unicidad con `existsByUsername` antes de crear, y busca con `findByUsername` en login usando el mismo valor normalizado. |
| `email` | `String` (unique) | Entrada de registro. `AuthService` valida unicidad con `existsByEmail` antes de crear. No se usa en login (spec: login es sólo username/password). |
| `passwordHash` | `String` | `AuthService` la completa con `passwordEncoder.encode(rawPassword)` en el registro; se compara con `passwordEncoder.matches(...)` en el login. Nunca se expone en ningún DTO de response. |
| `role` | `Role` | No se setea explícitamente; se deja el default `Role.USER` que ya define la entidad (`@Builder.Default`). |
| `balance` | `BigDecimal` | No se setea explícitamente; se deja el default `1000.00` que ya define la entidad. Se refleja en `RegisterResponse` tal cual queda tras `save()`. |
| `createdAt` | `LocalDateTime` | Autogenerado por el `@PrePersist` ya existente; no se toca. |
| `apiKey` | `ApiKey` | Relación `OneToOne` ya existente; `AuthService` construye la `ApiKey` con `owner = user` (o la persiste con `save()` en cascada, ver más abajo) para completarla. |

### ApiKey (`backend/src/main/java/com/example/demo/model/ApiKey.java`, tabla `api_keys`)

| Campo | Tipo | Uso en esta feature |
|---|---|---|
| `id` | `Long` | No se expone (el usuario no necesita el id numérico de su key). |
| `keyHash` | `String` (unique) | `AuthService` la completa con `ApiKeyHasher.sha256Hex(rawApiKey)` (ver `research.md` §1). **Nunca** con `BCryptPasswordEncoder`. |
| `keyPrefix` | `String` | `AuthService` la completa con los primeros 11 caracteres del valor crudo (`sk_` + 8 chars, ver `research.md` §2). |
| `owner` | `User` | Se asocia a la cuenta recién creada. |
| `active` | `boolean` | Se deja el default `true` que ya define la entidad (`@Builder.Default`); una key recién emitida nace activa. |
| `createdAt` / `lastUsedAt` | `LocalDateTime` | `createdAt` autogenerado por el `@PrePersist` ya existente; `lastUsedAt` no se toca en esta feature (no aplica: la key recién se genera, todavía no se usó). |

**Persistencia**: gracias a `@OneToOne(mappedBy = "owner", cascade = CascadeType.ALL, ...)` ya
declarado en `User.apiKey`, `AuthService` guarda ambas entidades con un único
`userRepository.save(user)` tras setear `user.setApiKey(apiKey)` y `apiKey.setOwner(user)`
(cascada ya existente, no requiere un `apiKeyRepository.save()` adicional).

No se agregan repositorios ni métodos nuevos: `existsByUsername`, `existsByEmail`,
`findByUsername` y `save` ya existen en `UserRepository`.

## DTOs nuevos (no persistidos, capa de transporte HTTP)

### RegisterRequest

| Campo | Tipo | Validación |
|---|---|---|
| `username` | `String` | `@NotBlank` |
| `email` | `String` | `@NotBlank`, `@Email` |
| `password` | `String` | `@NotBlank`, `@Size(min = 8)` |

### RegisterResponse

| Campo | Tipo | Origen |
|---|---|---|
| `id` | `Long` | `user.getId()` tras `save()` |
| `username` | `String` | `user.getUsername()` |
| `email` | `String` | `user.getEmail()` |
| `balance` | `BigDecimal` | `user.getBalance()` (default `1000.00` de la entidad) |
| `apiKey` | `String` | Valor crudo generado en memoria (ver `research.md` §2). **Única vez que existe fuera de la variable local de `AuthService`**; no se persiste en texto plano en ningún lado. |

### LoginRequest

| Campo | Tipo | Validación |
|---|---|---|
| `username` | `String` | `@NotBlank` |
| `password` | `String` | `@NotBlank` |

### LoginResponse

| Campo | Tipo | Origen |
|---|---|---|
| `token` | `String` | `jwtUtil.generateToken(user.getUsername())` |
| `tokenType` | `String` | Constante `"Bearer"` |

## Excepciones de dominio nuevas (no persistidas)

| Excepción | Cuándo se lanza | Mapeo HTTP (`GlobalExceptionHandler`) |
|---|---|---|
| `DuplicateUserException` | `AuthService` detecta username o email ya registrados (chequeo previo o `DataIntegrityViolationException` de la constraint `unique`, ver `research.md` §4) | 409 Conflict, `{"error":"duplicate_user","message":"..."}` |
| `InvalidCredentialsException` | `AuthService` no encuentra el username o el password no matchea (mismo mensaje en ambos casos, ver `research.md` §3) | 401 Unauthorized, `{"error":"invalid_credentials","message":"..."}` |

Los fallos de `@Valid` (Bean Validation) se mapean a 400 Bad Request con el mismo shape
`{"error":"validation_error","message":"..."}` vía el `MethodArgumentNotValidException` que
Spring ya lanza automáticamente; no requiere una excepción propia.

## Diagrama de flujo (conceptual)

```text
POST /auth/register
   │
   ├─ @Valid RegisterRequest (400 si falla Bean Validation)
   ├─ AuthService.register(...)
   │    ├─ existsByUsername / existsByEmail  ──► true ──► DuplicateUserException (409)
   │    ├─ passwordEncoder.encode(password)
   │    ├─ generar ApiKey cruda (SecureRandom) + ApiKeyHasher.sha256Hex(...)
   │    ├─ userRepository.save(user)  ──► DataIntegrityViolationException ──► DuplicateUserException (409)
   │    └─ RegisterResponse (incluye la ApiKey cruda, única vez)
   └─ 201 Created

POST /auth/login
   │
   ├─ @Valid LoginRequest (400 si falla Bean Validation)
   ├─ AuthService.login(...)
   │    ├─ userRepository.findByUsername(username)
   │    ├─ passwordEncoder.matches(password, hashEncontrado_o_dummy)
   │    │    └─ no coincide o no existe ──► InvalidCredentialsException (401), mismo mensaje
   │    └─ jwtUtil.generateToken(username) ──► LoginResponse
   └─ 200 OK
```
