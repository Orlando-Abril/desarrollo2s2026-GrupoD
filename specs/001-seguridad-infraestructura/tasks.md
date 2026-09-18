---

description: "Task list template for feature implementation"
---

# Tasks: Infraestructura Transversal de Seguridad

**Input**: Design documents from `/specs/001-seguridad-infraestructura/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/security-infrastructure.md, quickstart.md

**Tests**: Se incluyen tareas de test para JwtUtil y el filtro de ApiKey (pedido explícito del
usuario al correr `/speckit-plan`), y además una prueba automatizada de `SecurityConfig`/CORS
(agregada tras `/speckit-analyze` para cerrar el hallazgo G2: SC-001/SC-002/SC-004 no tenían
ninguna cobertura automática, sólo verificación manual). No se agregan tests para `OpenApiConfig`
(US4): se sigue validando manualmente según `quickstart.md`.

**Organization**: Las tareas están agrupadas por historia de usuario (spec.md) para poder
implementar y verificar cada una de forma independiente.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: A qué historia de usuario pertenece (US1, US2, US3, US4)
- Se incluye la ruta de archivo exacta en cada descripción

## Path Conventions

Monorepo `backend/` + `frontend/` (ver `plan.md` → Project Structure). Esta feature sólo toca
`backend/src/main/java/com/example/demo/{config,security}` y sus tests espejo en
`backend/src/test/java/com/example/demo/{config,security}`.

---

## Phase 1: Setup

**Purpose**: Configuración compartida que las historias de usuario necesitan leer.

- [X] T001 Agregar las nuevas propiedades de seguridad a `backend/src/main/resources/application.properties`: `security.jwt.secret=${JWT_SECRET}`, `security.jwt.expiration-ms=${JWT_EXPIRATION_MS:3600000}` y `security.cors.allowed-origin=${CORS_ALLOWED_ORIGIN:http://localhost:5173}` (ver `data-model.md` → "Nueva configuración de aplicación"). No se agregan dependencias nuevas al `pom.xml`: `spring-boot-starter-security`, `jjwt-*` y `springdoc-openapi-starter-webmvc-ui` ya están declaradas.

**Checkpoint**: Las propiedades existen y son leíbles vía `@Value`/`Environment` antes de escribir cualquier bean que las use.

---

## Phase 2: Foundational

**Purpose**: Pieza de infraestructura compartida (bean de hashing de passwords) que no pertenece a ninguna historia de usuario puntual de `spec.md`, pero forma parte del alcance (FR-006/FR-008/FR-011). No bloquea a US1-US4 (son independientes entre sí); se agrupa acá por no tener una historia propia.

- [X] T002 [P] Crear el bean `PasswordEncoder` (`BCryptPasswordEncoder`) en `backend/src/main/java/com/example/demo/config/PasswordEncoderConfig.java`, expuesto como `@Bean` inyectable para que futuras features (registro/login) lo usen para hashear passwords sin implementar su propio hashing.

**Checkpoint**: El bean está disponible para inyección; ninguna historia de usuario depende de él para ser probada.

---

## Phase 3: User Story 1 - Habilitar rutas públicas y protegidas con CORS (Priority: P1) 🎯 MVP

**Goal**: Que el sistema distinga endpoints públicos de protegidos y que sólo el origen del frontend (`http://localhost:5173`) reciba autorización CORS.

**Independent Test**: Verificar con un test automatizado que una ruta no listada como pública responde 401 sin credenciales, que una ruta pública real (`/v3/api-docs`) responde sin necesitar credenciales, y que sólo las requests con `Origin: http://localhost:5173` reciben los headers CORS necesarios (ver también `quickstart.md` paso 3 para el smoke-test manual post-deploy).

### Tests for User Story 1 ⚠️

> **Escribir primero, deben FALLAR hasta completar la implementación (T004-T005).**

- [X] T003 [P] [US1] Crear `backend/src/test/java/com/example/demo/config/SecurityConfigTest.java` (`@SpringBootTest` + `@AutoConfigureMockMvc`, estilo MockMvc consistente con el resto de la suite) cubriendo SC-001/SC-002/SC-004: (a) `GET` a una ruta arbitraria no listada como pública (ej. `/cualquier-cosa-protegida`) sin credenciales → 401 (SC-001) — no requiere que exista un controller real, porque `SecurityFilterChain` rechaza la request antes de llegar al `DispatcherServlet`; (b) `GET /v3/api-docs` (ruta pública real, expuesta automáticamente por springdoc) sin credenciales → 200 (SC-002); (c) `OPTIONS` preflight a `/v3/api-docs` con header `Origin: http://localhost:5173` → la respuesta incluye `Access-Control-Allow-Origin: http://localhost:5173`; (d) el mismo preflight con `Origin: http://evil.example` → la respuesta NO incluye ese header (SC-004).

### Implementation for User Story 1

- [X] T004 [US1] Crear `backend/src/main/java/com/example/demo/config/SecurityConfig.java` con `@Configuration` + `@EnableWebSecurity` y el bean `SecurityFilterChain`: sesión `STATELESS`, CSRF deshabilitado, rutas públicas explícitas `/auth/register`, `/auth/login`, `/swagger-ui/**`, `/v3/api-docs/**` (`permitAll()`), y `anyRequest().authenticated()` para el resto (FR-001, FR-002; ver `research.md` §3-4 y `contracts/security-infrastructure.md` §3).
- [X] T005 [US1] En el mismo `SecurityConfig.java`, agregar el bean `CorsConfigurationSource` leyendo el origen desde `security.cors.allowed-origin`, con métodos `GET, POST, PUT, PATCH, DELETE, OPTIONS` y headers permitidos `Authorization, X-API-KEY, Content-Type`, y conectarlo a la `SecurityFilterChain` vía `.cors(...)` (FR-003; depende de T004 por ser el mismo archivo y de T001 por la propiedad).

**Checkpoint**: User Story 1 queda funcional y verificable de forma independiente (público vs protegido + CORS), sin depender de JWT, ApiKey ni OpenAPI.

---

## Phase 4: User Story 2 - Emitir y validar tokens JWT (Priority: P1) 🎯 MVP

**Goal**: Una utilidad standalone que emite un JWT a partir de un username y lo valida después, sin lógica de usuarios (ver `research.md` §2-3).

**Independent Test**: Invocar la utilidad con un username arbitrario, validar el token resultante y confirmar que se recupera el mismo username; confirmar que un token expirado o con firma alterada es rechazado. No requiere que exista `SecurityConfig` ni ningún endpoint.

### Tests for User Story 2 ⚠️

> **Escribir primero, deben FALLAR hasta completar la implementación (T007-T008).**

- [X] T006 [P] [US2] Crear `backend/src/test/java/com/example/demo/security/JwtUtilTest.java` (JUnit5 + AssertJ, estilo de `ApiKeyTest`) cubriendo: generación de un token a partir de un username válido, `generateToken(null)`/`generateToken("")` lanzando `IllegalArgumentException`, validación exitosa (`isTokenValid` = true) y extracción correcta del username (`extractUsername`) antes de expirar, `isTokenValid` = false para un token expirado, y `extractUsername` lanzando la excepción de `jjwt` para un token con firma alterada.

### Implementation for User Story 2

- [X] T007 [US2] Crear `backend/src/main/java/com/example/demo/security/JwtUtil.java` con `generateToken(String username)`: firma HS256 con clave derivada de `security.jwt.secret`, `subject = username`, `issuedAt = ahora`, `expiration = ahora + security.jwt.expiration-ms`; lanza `IllegalArgumentException` si `username` es `null` o blank (contrato en `contracts/security-infrastructure.md` §1).
- [X] T008 [US2] En el mismo `JwtUtil.java`, implementar `extractUsername(String token)` (devuelve el `subject`; propaga la excepción de `jjwt` si el token es inválido/expirado) e `isTokenValid(String token)` (`true`/`false`, nunca lanza excepción) (depende de T007, mismo archivo).

**Checkpoint**: User Story 2 queda funcional y verificable de forma totalmente independiente (no requiere SecurityConfig, ApiKey ni OpenAPI). Junto con US1, completa el MVP P1.

---

## Phase 5: User Story 3 - Autorizar requests de servicio mediante ApiKey (Priority: P2)

**Goal**: Un filtro que valida el header `X-API-KEY` contra el hash persistido en `api_keys` y deja pasar sólo si la key existe, coincide y está activa.

**Independent Test**: Generar un hash de prueba en `api_keys` y verificar con MockMvc que una request con el valor en claro correspondiente pasa, y que faltante/incorrecto/inactivo devuelven 401 (ver `contracts/security-infrastructure.md` §2).

**Dependencies**: Necesita que `SecurityConfig` (US1, T004) ya exista para registrar el filtro en la cadena.

### Tests for User Story 3 ⚠️

> **Escribir primero, deben FALLAR hasta completar la implementación (T010-T011).**

- [X] T009 [P] [US3] Crear `backend/src/test/java/com/example/demo/security/ApiKeyAuthFilterTest.java` con MockMvc cubriendo los 4 escenarios de `contracts/security-infrastructure.md` §2: header `X-API-KEY` ausente → 401; header con valor que no matchea ningún `keyHash` → 401; header que matchea una `ApiKey` con `active=false` → 401; header que matchea una `ApiKey` con `active=true` → la request continúa (200 en un endpoint protegido de prueba).

### Implementation for User Story 3

- [X] T010 [US3] Crear `backend/src/main/java/com/example/demo/security/ApiKeyAuthFilter.java` extendiendo `OncePerRequestFilter`: lee el header `X-API-KEY`, lo hashea como `Hex.encode(SHA-256(rawKey))` (hexadecimal en minúsculas, nunca compara en texto plano — ver `research.md` §1 y `contracts/security-infrastructure.md` §2), busca vía `ApiKeyRepository.findByKeyHash(hash)`, y si existe y `active=true` puebla el `SecurityContext` con una `Authentication` autenticada y continúa la cadena; en cualquier otro caso responde 401 con el body JSON `{"error":"unauthorized","message":"<motivo>"}` (FR-006, FR-007).
- [X] T011 [US3] Registrar `ApiKeyAuthFilter` en `backend/src/main/java/com/example/demo/config/SecurityConfig.java` vía `addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)` (depende de T004/T005 y T010).

**Checkpoint**: User Story 3 queda funcional y verificable de forma independiente sobre la base de US1 (SecurityConfig ya registrado).

---

## Phase 6: User Story 4 - Documentación de seguridad autodescriptiva (Priority: P3)

**Goal**: Documentación interactiva con la info del proyecto y los esquemas `bearerAuth`/`apiKeyAuth` disponibles para que futuros controllers los referencien con una sola anotación.

**Independent Test**: Acceder a `/v3/api-docs` y comprobar que incluye la info del proyecto y ambos esquemas de seguridad definidos (ver `quickstart.md` paso 3). Requiere que esas rutas ya estén marcadas como públicas por US1 (T004) para ser alcanzables sin credenciales.

### Implementation for User Story 4

- [X] T012 [P] [US4] Crear `backend/src/main/java/com/example/demo/config/OpenApiConfig.java` con un bean `OpenAPI` que defina `Info` (nombre, versión, descripción del proyecto) y dos `SecurityScheme` registrados como `bearerAuth` (HTTP Bearer, formato JWT) y `apiKeyAuth` (`API_KEY` en el header `X-API-KEY`), tal como se documentan en `contracts/security-infrastructure.md` §4 (FR-009).

**Checkpoint**: Las cuatro historias de usuario (US1-US4) quedan completas e independientemente verificables.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Validación final de que la infraestructura completa funciona en conjunto, no rompe nada existente, y cumple los gates de calidad obligatorios del proyecto.

- [X] T013 [P] Ejecutar la validación de `quickstart.md`: `mvn -Dtest=SecurityConfigTest,JwtUtilTest,ApiKeyAuthFilterTest test` y los `curl` manuales contra `/v3/api-docs` (ruta pública y CORS permitido/denegado según origen) como smoke-test post-deploy adicional a la cobertura automatizada de T003/T006/T009.
- [X] T014 Ejecutar `mvn test` completo desde `backend/` y confirmar `BUILD SUCCESS`, verificando que los tests ya existentes (`ApiKeyTest`, `ApiKeyRepositoryTest`, `UserTest`) siguen pasando junto a los nuevos.
- [ ] T015 Abrir el PR de esta feature contra `main` y confirmar en GitHub Actions (`.github/workflows/ci.yml`) que el job `build` termina en `SUCCESS` y que el análisis de SonarCloud pasa el Quality Gate (menos de 10 issues), según la Definition of Done de la constitution (`.specify/memory/constitution.md` §7, puntos 2-3).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Sin dependencias — puede arrancar de inmediato.
- **Foundational (Phase 2)**: Depende de Setup (usa las propiedades de T001 sólo si las necesitara; en la práctica T002 no depende de ninguna). No bloquea a ninguna historia de usuario.
- **User Stories (Phase 3-6)**: US1 y US2 pueden arrancar en paralelo apenas termina Setup. US3 depende de que exista `SecurityConfig` (US1, T004). US4 depende de que las rutas públicas ya estén definidas en `SecurityConfig` (US1, T004) para ser alcanzable sin credenciales, aunque el bean `OpenApiConfig` en sí (T012) se puede codear en paralelo.
- **Polish (Phase 7)**: Depende de que todas las historias que se vayan a entregar en esta ronda estén completas.

### User Story Dependencies

- **US1 (P1)**: Sin dependencias de otras historias.
- **US2 (P1)**: Sin dependencias de otras historias — completamente standalone (ver `research.md` §3).
- **US3 (P2)**: Depende de US1 (necesita `SecurityConfig` para registrar el filtro).
- **US4 (P3)**: Depende de US1 para que sus rutas sean alcanzables sin autenticación; el código de `OpenApiConfig` no depende de ninguna otra clase.

### Parallel Opportunities

- T001 (Setup) no tiene tareas hermanas paralelas (una sola propiedad de archivo).
- T002 (Foundational) puede correr en paralelo con toda la Phase 3/4 (US1/US2), al ser un archivo distinto sin dependencias.
- US1 y US2 se pueden implementar en paralelo por dos personas distintas apenas termina Setup.
- T003 (test de US1) y T006 (test de US2) se pueden escribir en paralelo entre sí y con T002 (Foundational).
- T009 (test de US3) puede escribirse apenas está definido el contrato del filtro (no necesita esperar a T004/T005), aunque su implementación (T010-T011) sí depende de US1.
- T012 (US4) puede codearse en paralelo con US2/US3; sólo su *validación end-to-end* espera a US1.

---

## Parallel Example: Foundational + User Story 1 + User Story 2

```bash
# Estas tareas tocan archivos distintos y no tienen dependencias entre sí:
Task: "Crear el bean PasswordEncoder en backend/src/main/java/com/example/demo/config/PasswordEncoderConfig.java"
Task: "Crear SecurityConfigTest.java en backend/src/test/java/com/example/demo/config/SecurityConfigTest.java"
Task: "Crear JwtUtilTest.java en backend/src/test/java/com/example/demo/security/JwtUtilTest.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 + User Story 2, ambas P1)

1. Completar Phase 1: Setup (T001).
2. Completar Phase 2: Foundational (T002) — no bloqueante, puede ir en paralelo.
3. Completar Phase 3: User Story 1 (T003-T005).
4. Completar Phase 4: User Story 2 (T006-T008).
5. **DETENER y VALIDAR**: correr `quickstart.md` — con esto ya hay rutas públicas/protegidas, CORS y emisión/validación de JWT funcionando de forma aislada y con cobertura automatizada.

### Incremental Delivery

1. Setup + Foundational → base lista.
2. US1 + US2 (P1) → MVP de esta feature: cualquier otra feature ya puede apoyarse en rutas públicas/protegidas, CORS y JWT.
3. US3 (P2) → suma autorización por ApiKey para accesos de servicio.
4. US4 (P3) → suma documentación autodescriptiva en Swagger.
5. Phase 7 (Polish) → validación de regresión completa y verificación de CI/SonarCloud antes de dar la feature por terminada.

### Parallel Team Strategy

Con dos personas (equipo declarado en la constitution):

1. Ambas completan Setup (T001) juntas.
2. Persona A: User Story 1 (T003-T005) → luego User Story 3 (T009-T011, depende de su propio T004).
3. Persona B: User Story 2 (T006-T008) en paralelo → luego User Story 4 (T012) y Foundational (T002) si quedó libre.
4. Ambas corren Phase 7 (T013-T015) juntas al final.

---

## Notes

- [P] = archivos distintos, sin dependencias pendientes.
- Las etiquetas [US1]-[US4] trazan cada tarea a su historia de usuario en `spec.md`.
- No se agregan tareas de test para `OpenApiConfig` (US4) porque el usuario no las pidió al especificar el plan; se valida manualmente vía `quickstart.md` (Phase 7).
- No se crean tablas ni migraciones nuevas: `ApiKeyRepository.findByKeyHash` ya existe y se reutiliza tal cual (`data-model.md`).
- Confirmar con el equipo el límite de alcance documentado en `research.md` §3: `JwtUtil` no se conecta todavía a `SecurityFilterChain` (eso queda para la feature de login).
- FR-011 (nunca persistir/loguear un password en texto plano) se satisface en esta feature únicamente proveyendo el mecanismo de hashing (T002); su cumplimiento efectivo depende de que la feature de registro/login lo invoque siempre antes de persistir o loguear (ver Assumptions de `spec.md`).
