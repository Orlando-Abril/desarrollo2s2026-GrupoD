# Tasks: Registro e Inicio de Sesión de Usuario

**Input**: Design documents from `/specs/002-auth-usuario/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/auth-api.md, quickstart.md

**Tests**: Explícitamente pedidos por el usuario (Mockito para Service, MockMvc para Controller) — se incluyen como tareas obligatorias, no opcionales.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

Monorepo web app ya existente: `backend/src/main/java/com/example/demo/...` y
`backend/src/test/java/com/example/demo/...` (ver `plan.md` → Project Structure).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirmar que no hace falta agregar dependencias nuevas antes de tocar código.

- [X] T001 Verificar en `backend/pom.xml` que ya están declaradas `spring-boot-starter-validation`, `spring-boot-starter-security` y `springdoc-openapi-starter-webmvc-ui`, y que `mockito-core`/`mockito-junit-jupiter`/`assertj-core` están disponibles transitivamente (vía `spring-boot-starter-webmvc-test`). No se espera ningún cambio (ver `plan.md` → Technical Context); si falta alguna, agregarla en `backend/pom.xml`.

**Checkpoint**: Dependencias confirmadas, no se requieren cambios de build.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Infraestructura compartida que ambas historias de usuario (US1 registro, US2 login) necesitan antes de poder implementarse.

**⚠️ CRITICAL**: Ninguna historia de usuario puede empezar hasta completar esta fase.

- [X] T002 [P] Escribir `ApiKeyHasherTest` en `backend/src/test/java/com/example/demo/security/ApiKeyHasherTest.java`: el mismo `rawValue` siempre produce el mismo hash hexadecimal SHA-256 en minúsculas sin separadores (mismo formato que ya usan implícitamente `ApiKeyTest`/`ApiKeyRepositoryTest`). El test debe FALLAR ahora (la clase todavía no existe).
- [X] T003 [P] Implementar `ApiKeyHasher.sha256Hex(String rawValue)` en `backend/src/main/java/com/example/demo/security/ApiKeyHasher.java`, extrayendo exactamente el algoritmo hoy privado en `ApiKeyAuthFilter.sha256Hex` (SHA-256 → hex minúscula, sin separadores). Debe satisfacer T002. Contrato obligatorio: `specs/001-seguridad-infraestructura/contracts/security-infrastructure.md` §2 (research.md §1).
- [X] T004 Refactorizar `backend/src/main/java/com/example/demo/security/ApiKeyAuthFilter.java` para delegar en `ApiKeyHasher.sha256Hex(...)` en vez de su método privado, sin cambiar su comportamiento externo (depende de T003). El test ya existente `ApiKeyAuthFilterTest` debe seguir pasando sin modificaciones, como regresión.
- [X] T005 [P] Crear `GlobalExceptionHandler` (`@RestControllerAdvice`) en `backend/src/main/java/com/example/demo/exception/GlobalExceptionHandler.java` con un `@ExceptionHandler(MethodArgumentNotValidException.class)` que devuelva `400 Bad Request` con cuerpo `{"error":"validation_error","message":"..."}` (compartido por `RegisterRequest` y `LoginRequest`, ver data-model.md → Excepciones de dominio nuevas).

**Checkpoint**: `ApiKeyHasher` disponible y usado por `ApiKeyAuthFilter`; manejo base de errores de validación listo. Las historias de usuario ya pueden empezar.

---

## Phase 3: User Story 1 - Registro de cuenta nueva (Priority: P1) 🎯 MVP

**Goal**: Un visitante sin cuenta se registra con username/email/password y recibe de inmediato su `ApiKey` en claro, quedando la cuenta creada con el saldo inicial estándar (1000.00).

**Independent Test**: `POST /auth/register` con datos únicos y válidos devuelve 201 con la `ApiKey` en el body; repetir el mismo username o email devuelve 409 sin crear una cuenta duplicada.

### Tests for User Story 1 ⚠️

> **NOTE: Escribir estos tests PRIMERO, confirmar que FALLAN antes de implementar**

- [X] T006 [P] [US1] Escribir tests de `AuthService.register(...)` en `backend/src/test/java/com/example/demo/service/AuthServiceTest.java` (Mockito puro, mockeando `UserRepository` y `PasswordEncoder`): (a) alta exitosa hashea el password con `passwordEncoder.encode`, genera una `ApiKey` cuyo `keyHash` coincide con `ApiKeyHasher.sha256Hex(rawApiKey)` devuelto, y el `RegisterResponse` incluye `balance == 1000.00` (default de `User`, sin setearlo explícitamente); (b) `existsByUsername` devuelve `true` → lanza `DuplicateUserException`; (c) `existsByEmail` devuelve `true` → lanza `DuplicateUserException`; (d) registrar con `username = "Abril"` persiste y devuelve `username = "abril"` (normalizado a minúsculas, research.md §7), y `existsByUsername`/`existsByEmail` se invocan con el valor ya normalizado.
- [X] T007 [P] [US1] Escribir tests de `POST /auth/register` en `backend/src/test/java/com/example/demo/controller/AuthControllerTest.java` (`@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional` + `@DirtiesContext`, mismo estilo que `ApiKeyAuthFilterTest`, con H2 real): alta exitosa → `201` con `apiKey` no vacío en el body; username duplicado → `409` `{"error":"duplicate_user",...}`; email duplicado → `409`; registrar `"abril"` y luego `"Abril"` → el segundo devuelve `409` (mismo username, sólo difiere el casing, research.md §7); `password` de menos de 8 caracteres o `email` con formato inválido → `400` `{"error":"validation_error",...}`.

### Implementation for User Story 1

- [X] T008 [P] [US1] Crear `RegisterRequest` en `backend/src/main/java/com/example/demo/dto/auth/RegisterRequest.java` con `username` (`@NotBlank`), `email` (`@NotBlank`, `@Email`), `password` (`@NotBlank`, `@Size(min = 8)`) — constraints exactas de data-model.md → DTOs nuevos → RegisterRequest.
- [X] T009 [P] [US1] Crear `RegisterResponse` en `backend/src/main/java/com/example/demo/dto/auth/RegisterResponse.java` con `id` (`Long`), `username` (`String`), `email` (`String`), `balance` (`BigDecimal`), `apiKey` (`String`, valor crudo, ver data-model.md → DTOs nuevos → RegisterResponse).
- [X] T010 [P] [US1] Crear `DuplicateUserException` (excepción unchecked con mensaje) en `backend/src/main/java/com/example/demo/exception/DuplicateUserException.java`.
- [X] T011 [US1] Agregar a `backend/src/main/java/com/example/demo/exception/GlobalExceptionHandler.java` un `@ExceptionHandler(DuplicateUserException.class)` que devuelva `409 Conflict` con `{"error":"duplicate_user","message":"..."}` (depende de T005 y T010).
- [X] T012 [US1] Implementar `AuthService.register(RegisterRequest request)` en `backend/src/main/java/com/example/demo/service/AuthService.java`: normalizar `username` a minúsculas (`username.toLowerCase()`, research.md §7 — todo `username` se guarda y se devuelve en minúsculas, sin preservar el casing original) antes de cualquier chequeo o persistencia; validar `existsByUsername`/`existsByEmail` con el valor ya normalizado (lanzar `DuplicateUserException` si corresponde); `passwordEncoder.encode(password)`; generar el valor crudo de la `ApiKey` con `SecureRandom` (24 bytes, Base64 URL-safe sin padding, prefijo literal `sk_`) y su `keyHash` vía `ApiKeyHasher.sha256Hex(...)`; completar `keyPrefix` con los primeros 11 caracteres (`sk_` + 8 chars); asociar `owner`/`apiKey` y persistir con `userRepository.save(user)` (cascada ya existente); capturar `DataIntegrityViolationException` de `save()` y relanzar como `DuplicateUserException` (fallback de condición de carrera, research.md §4); mapear a `RegisterResponse` incluyendo la `ApiKey` cruda. Depende de T003, T008, T009, T010.
- [X] T013 [US1] Implementar `POST /auth/register` en `backend/src/main/java/com/example/demo/controller/AuthController.java`: `@Valid @RequestBody RegisterRequest` → `201 Created` con `RegisterResponse`; anotar con `@Tag(name = "Auth")`, `@Operation` y `@ApiResponse` para `201`/`400`/`409` según `contracts/auth-api.md`. Depende de T012.

**Checkpoint**: User Story 1 completamente funcional y testeable de forma independiente (T006/T007 deben pasar en verde).

---

## Phase 4: User Story 2 - Inicio de sesión (Priority: P2)

**Goal**: Un usuario ya registrado se autentica con username/password y recibe un JWT válido; credenciales incorrectas o username inexistente devuelven el mismo error genérico.

**Independent Test**: Tras registrar una cuenta, `POST /auth/login` con las credenciales correctas devuelve 200 con un JWT; con password incorrecto o username inexistente devuelve 401 con el mismo mensaje.

### Tests for User Story 2 ⚠️

> **NOTE: Escribir estos tests PRIMERO, confirmar que FALLAN antes de implementar**

- [X] T014 [P] [US2] Escribir tests de `AuthService.login(...)` en `backend/src/test/java/com/example/demo/service/AuthServiceTest.java` (Mockito, mockeando `UserRepository`, `PasswordEncoder`, `JwtUtil`): login correcto devuelve `LoginResponse` con el token que retorna `jwtUtil.generateToken(username)`; login con `username = "Abril"` encuentra la cuenta guardada como `"abril"` (normalización, research.md §7); password incorrecto (`passwordEncoder.matches` → `false`) lanza `InvalidCredentialsException`; username inexistente lanza la misma `InvalidCredentialsException` con idéntico mensaje; verificar que `passwordEncoder.matches` se invoca igual aunque el username no exista (mitigación de timing, research.md §3); verificar que el `keyHash` de la `ApiKey` del usuario no cambia como efecto de `login(...)` (FR-011: login nunca rota/regenera la ApiKey).
- [X] T015 [P] [US2] Escribir tests de `POST /auth/login` en `backend/src/test/java/com/example/demo/controller/AuthControllerTest.java`: login correcto (tras registrar previamente) → `200` con `token` no vacío y `tokenType == "Bearer"`; password incorrecto → `401` `{"error":"invalid_credentials",...}`; username inexistente → `401` con el mismo shape; `username`/`password` vacíos → `400` `{"error":"validation_error",...}`.

### Implementation for User Story 2

- [X] T016 [P] [US2] Crear `LoginRequest` en `backend/src/main/java/com/example/demo/dto/auth/LoginRequest.java` con `username` (`@NotBlank`) y `password` (`@NotBlank`) — data-model.md → DTOs nuevos → LoginRequest.
- [X] T017 [P] [US2] Crear `LoginResponse` en `backend/src/main/java/com/example/demo/dto/auth/LoginResponse.java` con `token` (`String`) y `tokenType` (`String`, constante `"Bearer"`).
- [X] T018 [P] [US2] Crear `InvalidCredentialsException` (excepción unchecked con mensaje) en `backend/src/main/java/com/example/demo/exception/InvalidCredentialsException.java`.
- [X] T019 [US2] Agregar a `backend/src/main/java/com/example/demo/exception/GlobalExceptionHandler.java` un `@ExceptionHandler(InvalidCredentialsException.class)` que devuelva `401 Unauthorized` con `{"error":"invalid_credentials","message":"..."}` (depende de T005 y T018).
- [X] T020 [US2] Implementar `AuthService.login(LoginRequest request)` en `backend/src/main/java/com/example/demo/service/AuthService.java`: normalizar `username` a minúsculas (`username.toLowerCase()`, research.md §7 — debe coincidir con el valor normalizado guardado en el registro) antes de buscar; buscar con `userRepository.findByUsername`; verificar el password con `passwordEncoder.matches(rawPassword, hash)` contra el hash encontrado o, si el username no existe, contra un hash BCrypt "dummy" calculado una sola vez en runtime (`passwordEncoder.encode(...)` en un campo estático, **nunca un literal de hash hardcodeado**, para no disparar SonarCloud) para no revelar por timing cuál de los dos motivos fue (research.md §3); en cualquier fallo lanzar `InvalidCredentialsException` con el mismo mensaje; en éxito devolver `LoginResponse("Bearer", jwtUtil.generateToken(username))`. Depende de T016, T017, T018.
- [X] T021 [US2] Implementar `POST /auth/login` en `backend/src/main/java/com/example/demo/controller/AuthController.java`: `@Valid @RequestBody LoginRequest` → `200 OK` con `LoginResponse`; anotar con `@Operation` y `@ApiResponse` para `200`/`400`/`401` según `contracts/auth-api.md`. Depende de T020.

**Checkpoint**: User Stories 1 y 2 funcionan de forma independiente y en conjunto.

---

## Phase 5: User Story 3 - Documentación consultable de los endpoints (Priority: P3)

**Goal**: Una persona integradora puede ver en Swagger, sin leer código, qué espera cada endpoint y cada respuesta/error posible.

**Independent Test**: Abrir `swagger-ui` y confirmar que el tag **Auth** lista ambos endpoints con sus campos requeridos, la respuesta exitosa y cada condición de error documentada.

### Implementation for User Story 3

- [X] T022 [US3] Completar en `backend/src/main/java/com/example/demo/controller/AuthController.java` los `@ApiResponse` faltantes para cada código documentado en `contracts/auth-api.md` (400/409 en registro, 400/401 en login) que no hayan quedado cubiertos por T013/T021, incluyendo ejemplos de cuerpo JSON (`@Content(examples = ...)`) con el shape `{"error":"...","message":"..."}`, de forma que Swagger UI muestre el contrato completo sin necesidad de leer el código (FR-012/SC-006).
- [X] T023 [US3] Validación manual: levantar el backend localmente y abrir `http://localhost:8080/swagger-ui/index.html`; confirmar que el tag **Auth** lista `POST /auth/register` y `POST /auth/login` con schema completo de request/response y todos los códigos de error (quickstart.md §4). No se esperan cambios de código si T013/T021/T022 están completos.

**Checkpoint**: Las tres historias de usuario quedan funcionales de forma independiente.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Validación de regresión y de los criterios no funcionales de la spec (FR-004/FR-006, SonarCloud).

- [X] T024 [P] Ejecutar `mvn test` desde `backend/` y confirmar `BUILD SUCCESS`, incluyendo que los tests ya existentes (`SecurityConfigTest`, `JwtUtilTest`, `ApiKeyAuthFilterTest`, `ApiKeyRepositoryTest`, `UserRepositoryTest`, etc.) siguen en verde tras el refactor de `ApiKeyAuthFilter` (quickstart.md §2).
- [X] T025 [P] Ejecutar manualmente el recorrido `curl` end-to-end de `quickstart.md` §3 (registro → registro duplicado → login correcto → login inválido → uso del JWT) contra una instancia local.
- [X] T026 Revisar `AuthService`, `AuthController` y `GlobalExceptionHandler` para confirmar que ningún log, excepción o respuesta expone el password (crudo o hasheado) ni el valor crudo de la `ApiKey` fuera de `RegisterResponse` (FR-004/FR-006, research.md §5, constitution §3.2).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Sin dependencias — puede arrancar de inmediato.
- **Foundational (Phase 2)**: Depende de Setup. BLOQUEA las tres historias de usuario.
- **User Story 1 (Phase 3)**: Depende de Foundational. Sin dependencia de otras historias — es el MVP.
- **User Story 2 (Phase 4)**: Depende de Foundational. No depende de US1 en el código (usa `UserRepository`/`JwtUtil` directamente), pero para probarla de punta a punta hace falta una cuenta ya registrada (vía US1 o vía un fixture de test).
- **User Story 3 (Phase 5)**: Depende de que existan `AuthController`/DTOs de US1 y US2 (documenta lo que ya se implementó); no agrega lógica de negocio nueva.
- **Polish (Phase 6)**: Depende de que todas las historias deseadas estén completas.

### Dentro de cada historia

- Tests (US1: T006-T007; US2: T014-T015) se escriben y deben FALLAR antes de la implementación correspondiente.
- DTOs y excepciones antes que el Service.
- Service antes que el Controller.
- `GlobalExceptionHandler` se actualiza en el mismo paso en que se crea la excepción que mapea (T010→T011, T018→T019).

### Parallel Opportunities

- Foundational: T002 y T003 son secuenciales entre sí (T003 satisface el test T002), pero ambos son `[P]` respecto de T005 (archivo distinto).
- US1: T006 y T007 en paralelo (archivos de test distintos). T008, T009 y T010 en paralelo (archivos distintos); T011, T012, T013 son secuenciales.
- US2: T014 y T015 en paralelo. T016, T017 y T018 en paralelo; T019, T020, T021 son secuenciales.
- US1 y US2 pueden trabajarse en paralelo por personas distintas una vez completada la fase Foundational (ambas dependen sólo de Foundational, no entre sí).
- Polish: T024 y T025 en paralelo; T026 es una revisión manual sin dependencia de herramienta.

---

## Parallel Example: User Story 1

```bash
# Tests de User Story 1 en paralelo:
Task: "AuthServiceTest: alta exitosa + username/email duplicado (Mockito) en backend/src/test/java/com/example/demo/service/AuthServiceTest.java"
Task: "AuthControllerTest: alta exitosa 201 + duplicados 409 + validación 400 (MockMvc) en backend/src/test/java/com/example/demo/controller/AuthControllerTest.java"

# DTOs y excepción de User Story 1 en paralelo:
Task: "RegisterRequest en backend/src/main/java/com/example/demo/dto/auth/RegisterRequest.java"
Task: "RegisterResponse en backend/src/main/java/com/example/demo/dto/auth/RegisterResponse.java"
Task: "DuplicateUserException en backend/src/main/java/com/example/demo/exception/DuplicateUserException.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Phase 1: Setup.
2. Completar Phase 2: Foundational (bloqueante).
3. Completar Phase 3: User Story 1 (registro).
4. **STOP and VALIDATE**: correr T006/T007 y `quickstart.md` §1/§3 (sólo registro) de forma independiente.
5. Entregar como MVP: cualquier visitante ya puede crear una cuenta y obtener su `ApiKey`.

### Incremental Delivery

1. Setup + Foundational → base lista.
2. Agregar User Story 1 → testear independientemente → MVP.
3. Agregar User Story 2 → testear independientemente (login sobre cuentas ya registradas).
4. Agregar User Story 3 → validar documentación en Swagger UI.
5. Phase 6 (Polish) → regresión completa antes de abrir el PR.

### Parallel Team Strategy

Con dos personas (el equipo del proyecto):

1. Ambas completan Setup + Foundational juntas (es corto: T001-T005).
2. Una persona toma User Story 1 (P1/MVP), la otra toma User Story 2 (P2) en paralelo — ambas dependen sólo de Foundational, no entre sí.
   ⚠️ Ambas historias tocan los mismos archivos compartidos (`AuthServiceTest.java`, `AuthController.java`, `GlobalExceptionHandler.java`). Coordinar quién commitea primero en cada uno o trabajar en ramas cortas con merges frecuentes para evitar conflictos.
3. User Story 3 y Phase 6 (Polish) se hacen en conjunto al final, sobre el resultado integrado de ambas.

---

## Notes

- `[P]` = archivos distintos, sin dependencias entre sí.
- La etiqueta de historia (`[US1]`/`[US2]`/`[US3]`) mapea cada tarea a su historia de usuario en `spec.md` para trazabilidad.
- Verificar que los tests fallan antes de implementar (TDD explícito, pedido por el usuario).
- Hacer commit después de cada tarea o grupo lógico.
- Detenerse en cada checkpoint para validar la historia de forma independiente antes de seguir con la próxima.

---

## Mejoras pendientes (fuera de alcance de esta feature)

- **Agregar `/error` a `PUBLIC_ROUTES` en `SecurityConfig`.** Hoy, cuando un request llega mal formado (por ejemplo un `POST /auth/register` sin body), Spring reenvía el error a `/error`, que no es pública, y Spring Security lo transforma en un `401` vacío que oculta el `400`/`415` real. Detectado durante T025. Al hacerlo, revisar `SecurityConfigTest`.
