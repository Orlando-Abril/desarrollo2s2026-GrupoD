# Implementation Plan: Registro e Inicio de Sesión de Usuario

**Branch**: `feature/auth-usuario` | **Date**: 2026-09-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-auth-usuario/spec.md`

**Note**: This template is filled in by the `$speckit-plan` command; its definition describes the execution workflow.

## Summary

Agregar, dentro de `backend/`, las capas de Service y Controller que faltaban sobre la
infraestructura de seguridad ya existente (`JwtUtil`, `BCryptPasswordEncoder`, `SecurityConfig`,
`OpenApiConfig`) y sobre los modelos/repositories ya existentes (`User`, `ApiKey`,
`UserRepository`, `ApiKeyRepository`, sin tocar su estructura): un endpoint público
`POST /auth/register` que crea la cuenta con password hasheada, genera una `ApiKey` nueva
(hash SHA-256 persistido, valor crudo devuelto una única vez) y deja el saldo inicial que ya
define `User`; y un endpoint público `POST /auth/login` que valida username/password contra el
hash BCrypt almacenado y devuelve un JWT emitido con `JwtUtil`. Ambos endpoints se documentan en
Swagger con sus DTOs de request/response y sus códigos de error (409 duplicado, 401 credenciales
inválidas, 400 validación).

## Technical Context

**Language/Version**: Java 17, Spring Boot 4.1.1 (Maven), módulo `backend/`

**Primary Dependencies**: `spring-boot-starter-webmvc`, `spring-boot-starter-security` (bean
`PasswordEncoder` ya declarado en `PasswordEncoderConfig`), `spring-boot-starter-validation`
(Bean Validation, ya en el pom), `springdoc-openapi-starter-webmvc-ui` 3.1.1 (ya en el pom),
`JwtUtil` propio ya existente (`com.example.demo.security`), Lombok. No se agregan dependencias
nuevas al `pom.xml`.

**Storage**: PostgreSQL vía Spring Data JPA. Esta feature no crea entidades, tablas ni columnas
nuevas: usa únicamente `UserRepository` (`existsByUsername`, `existsByEmail`, `findByUsername`,
`save`, todos ya existentes). `AuthService` **no** inyecta `ApiKeyRepository`: la `ApiKey` se
persiste exclusivamente por la cascada ya declarada en `User.apiKey`
(`cascade = CascadeType.ALL`) al llamar `userRepository.save(user)` (ver data-model.md).

**Testing**: JUnit 5 + Mockito (`mockito-core`/`mockito-junit-jupiter`, ya disponibles
transitivamente por `spring-boot-starter-webmvc-test` en el pom) para `AuthServiceTest` (mockea
`UserRepository`, `ApiKeyRepository`, `PasswordEncoder`, `JwtUtil`, sin contexto de Spring).
`@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional` + `@DirtiesContext` con H2 real
(mismo estilo que `ApiKeyAuthFilterTest`/`SecurityConfigTest`) para `AuthControllerTest`
(alta exitosa, username/email duplicado, login correcto, login con credenciales inválidas).

**Target Platform**: API REST backend (Spring Boot) consumida por la SPA (Vite) del `frontend/`.

**Project Type**: Web application (monorepo `backend/` + `frontend/`). Esta feature sólo toca
`backend/`.

**Performance Goals**: Sin objetivos de performance específicos; registro/login son operaciones
de baja frecuencia relativa (no forman parte del loop de trading). El costo intencional de
`BCryptPasswordEncoder` en el hasheo/verificación de password es aceptable y deseado.

**Constraints**: El password nunca se persiste ni se loguea en texto plano (FR-004). El valor
crudo de la `ApiKey` sólo puede devolverse en la respuesta de `POST /auth/register`, nunca
persistirse ni loguearse (FR-006). El hash de la `ApiKey` nueva **MUST** calcularse con el mismo
algoritmo que ya usa `ApiKeyAuthFilter` para poder autenticarla luego
(`specs/001-seguridad-infraestructura/contracts/security-infrastructure.md` §2: SHA-256 en
hexadecimal minúscula, sin separadores — nunca BCrypt). El mensaje de error de login es genérico
e idéntico para username inexistente y para password incorrecto (FR-009). Las rutas
`/auth/register` y `/auth/login` ya están declaradas como públicas en
`SecurityConfig.PUBLIC_ROUTES`; esta feature no modifica esa configuración.

**Scale/Scope**: Dos endpoints nuevos. Aproximadamente 10 clases nuevas en `backend/`: 2 DTOs de
request, 2 DTOs de response, 1 `AuthService`, 1 `AuthController`, 2 excepciones de dominio
(`DuplicateUserException`, `InvalidCredentialsException`), 1 `GlobalExceptionHandler`
(`@RestControllerAdvice`), y una utilidad `ApiKeyHasher` extraída del hashing ya existente en
`ApiKeyAuthFilter` (ver Constitution Check y `research.md` §1) para no duplicar ese algoritmo.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio de la Constitution | Aplica | Evaluación |
|---|---|---|
| 2.1 Backend en capas estricto (Controllers/Services/Repositories/Adapters) | Sí | Esta feature agrega exactamente las capas Controller y Service que faltaban (`AuthController`, `AuthService`), reutilizando los Repositories ya existentes sin tocarlos. No se necesita capa Adapter (no hay integración externa). Cumple de forma directa. |
| 4.1 Autenticación y Autorización (JWT + API Keys, registro público con emisión de API Key inicial) | Sí | Esta feature **es** ese requisito: implementa el endpoint público de registro que emite la API Key inicial y el login que emite el JWT vía `JwtUtil`. Cumple de forma directa. |
| 4.3 Validación estricta de datos (Input Validation en el controller) | Sí | Bean Validation (`@Valid`, `@NotBlank`, `@Email`) en los DTOs de request, validado en `AuthController`. Cumple de forma directa. |
| 6.1 OpenAPI/Swagger (DTOs y códigos de respuesta anotados) | Sí | `AuthController` y sus DTOs se anotan con `@Operation`/`@ApiResponse` de springdoc, cubriendo 201/200, 400, 401 y 409. Cumple de forma directa. |
| 3.1 CI en GitHub Actions en `SUCCESS` | Sí (transversal) | Los tests nuevos (`AuthServiceTest`, `AuthControllerTest`) deben correr en el build Maven existente; no se agrega pipeline nuevo. |
| 3.2 SonarCloud (Quality Gate, <10 issues) | Sí (transversal) | El password y el valor crudo de la `ApiKey` no deben quedar logueados ni expuestos fuera de la única respuesta permitida; se resuelve en `research.md` §5. |
| 5.1 Catálogo y endpoints mínimos obligatorios | No | Esos endpoints (`/players`, `/orders/*`, etc.) pertenecen a otras features; esta feature no los toca. |
| 4.2 Auditoría inmutable de transacciones financieras | No | El saldo inicial que recibe la cuenta al registrarse es el estado inicial de la entidad, no una "transacción financiera" (compra/venta/ajuste) sobre un saldo preexistente; no aplica el log de auditoría de esta sección. |
| 7 (DoD, punto 5) "Toda operación de estado/financiera genera log de auditoría inmutable" | No (interpretación explícita) | Se interpreta que este punto del DoD reafirma el alcance de 4.2 (transacciones financieras: compra/venta/ajuste de saldo), no cualquier alta de entidad. La creación de una cuenta no es una operación de estado financiero sobre un saldo preexistente. Si el equipo prefiere una lectura más amplia, requiere una decisión explícita fuera de esta feature (no se resuelve agregando auditoría por goteo en cada plan). |
| 5.3 Observabilidad (logs estructurados, Correlation ID) | No (aún no aplica) | Todavía no existe infraestructura transversal de logging estructurado/Correlation ID en el proyecto; introducirla es una preocupación transversal de otra iteración, no algo que deba inventarse dentro del alcance de registro/login. |

**Resultado**: PASS. No hay violaciones que requieran justificación en Complexity Tracking.

**Re-check post Phase 1 (diseño)**: Con `research.md`, `data-model.md`, `contracts/` y
`quickstart.md` ya definidos, el diseño resultante mantiene la lógica de negocio exclusivamente en
`AuthService` (Controllers-Services-Repositories, sin mezclar capas), no modifica `User`, `ApiKey`
ni sus repositories, y reutiliza — vía la utilidad extraída `ApiKeyHasher` — el mismo algoritmo de
hash que `ApiKeyAuthFilter` ya usaba, evitando la duplicación de un contrato de seguridad crítico
(ver `research.md` §1). El punto 3.2 (SonarCloud) queda resuelto en `research.md` §5: ni el
password ni la ApiKey cruda se loguean, y el DTO de respuesta del registro es el único lugar
donde la ApiKey cruda existe fuera de memoria transitoria. **Resultado**: PASS, sin cambios
respecto del check inicial.

## Project Structure

### Documentation (this feature)

```text
specs/002-auth-usuario/
├── plan.md              # This file ($speckit-plan command output)
├── research.md          # Phase 0 output ($speckit-plan command)
├── data-model.md         # Phase 1 output ($speckit-plan command)
├── quickstart.md        # Phase 1 output ($speckit-plan command)
├── contracts/           # Phase 1 output ($speckit-plan command)
└── tasks.md             # Phase 2 output ($speckit-tasks command - NOT created by $speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/example/demo/
│   ├── controller/
│   │   └── AuthController.java             # POST /auth/register, POST /auth/login
│   ├── service/
│   │   └── AuthService.java                # lógica de registro y login
│   ├── dto/auth/
│   │   ├── RegisterRequest.java            # username, email, password (Bean Validation)
│   │   ├── RegisterResponse.java           # id, username, email, balance, apiKey (una vez)
│   │   ├── LoginRequest.java               # username, password (Bean Validation)
│   │   └── LoginResponse.java              # token, tokenType
│   ├── exception/
│   │   ├── DuplicateUserException.java     # username o email ya registrados (409)
│   │   ├── InvalidCredentialsException.java# login inválido (401)
│   │   └── GlobalExceptionHandler.java     # @RestControllerAdvice -> {error, message}
│   ├── security/
│   │   ├── ApiKeyHasher.java               # NUEVO: SHA-256 hex extraído de ApiKeyAuthFilter
│   │   ├── ApiKeyAuthFilter.java           # existente; se refactoriza para usar ApiKeyHasher
│   │   └── JwtUtil.java                    # existente, sin cambios (se inyecta en AuthService)
│   ├── model/                              # User, ApiKey (ya existen, SIN cambios de estructura)
│   └── repository/                         # UserRepository, ApiKeyRepository (ya existen, sin cambios)
│
└── src/test/java/com/example/demo/
    ├── service/
    │   └── AuthServiceTest.java            # Mockito puro: alta exitosa, duplicados, login ok/inválido
    ├── controller/
    │   └── AuthControllerTest.java         # MockMvc + H2 real (estilo ApiKeyAuthFilterTest)
    └── security/
        └── ApiKeyHasherTest.java           # unit test del hashing extraído

frontend/                                   # No se modifica en esta feature
```

**Structure Decision**: Monorepo `backend/` + `frontend/` ya existente (Opción "Web
application"). Esta feature agrega los paquetes `controller/`, `service/`, `dto/auth/` y
`exception/` (todos nuevos) dentro de `backend/src/main/java/com/example/demo/`, y una sola clase
nueva en el paquete `security/` ya existente (`ApiKeyHasher`, extraída de `ApiKeyAuthFilter` para
no duplicar el algoritmo de hash). No se toca `frontend/`, ni `model/`, ni `repository/`.

## Complexity Tracking

*No aplica: el Constitution Check no reportó violaciones que requieran justificación.*
