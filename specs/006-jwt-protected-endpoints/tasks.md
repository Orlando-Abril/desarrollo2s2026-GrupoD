# Tasks: Autenticación JWT en endpoints protegidos

**Input**: Design documents from `specs/006-jwt-protected-endpoints/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/protected-authentication.md`, `quickstart.md`

**Tests**: Obligatorios. La especificación exige una prueba por cada criterio de aceptación, pruebas unitarias por filtro, integración MockMvc y validación OpenAPI.

**Organization**: Las tareas se agrupan por historia de usuario. Dentro de cada historia, los tests se escriben antes de la implementación correspondiente.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo porque trabaja en otro archivo y no depende de una tarea incompleta.
- **[Story]**: Relaciona la tarea con una historia de `spec.md`.
- Todas las tareas indican el archivo exacto que modifican o verifican.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirmar una línea base reproducible antes de tocar la seguridad.

- [X] T001 Ejecutar la suite backend actual con `backend/mvnw.cmd verify` (Windows) o `backend/mvnw verify` (Linux), confirmar que `backend/pom.xml` ya contiene JJWT/Spring Security Test/MockMvc/H2 y registrar cualquier falla de línea base en `specs/006-jwt-protected-endpoints/quickstart.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Preparar el único fixture integrado compartido por las historias sin agregar escenarios todavía.

**⚠️ CRITICAL**: Las historias que agregan escenarios MockMvc dependen de este fixture.

- [X] T002 Crear la estructura compartida de `backend/src/test/java/com/example/demo/security/JwtOrApiKeyAuthenticationIntegrationTest.java` con `@SpringBootTest`, `@AutoConfigureMockMvc`, acceso a `MockMvc`/`ObjectMapper`, limpieza de datos y helpers para registrar usuarios y extraer `apiKey`/`token`, reutilizando H2 y sin dependencias nuevas

**Checkpoint**: Fixture integrado listo; no se modificó código productivo.

---

## Phase 3: User Story 1 - Usar la aplicación con la sesión iniciada (Priority: P1) 🎯 MVP técnico

**Goal**: Un JWT válido emitido por el login autentica una ruta protegida con el username como principal y sin requerir API key.

**Independent Test**: Ejecutar registro → login → `GET /players` enviando sólo el JWT devuelto; debe responder `200`, y el test unitario debe demostrar que el principal es el subject.

### Tests for User Story 1

> Escribir y observar fallar estos tests antes de implementar la historia.

- [X] T003 [P] [US1] Crear `backend/src/test/java/com/example/demo/security/JwtAuthFilterTest.java` con contexto limpio por test y casos de Bearer válido que continúa la cadena, establece `Authentication.isAuthenticated() == true` y usa el subject como principal, además del caso sin Authorization que continúa sin autenticar
- [X] T004 [P] [US1] Agregar a `backend/src/test/java/com/example/demo/security/ApiKeyAuthFilterTest.java` únicamente el caso “SecurityContext ya autenticado → continúa sin X-API-KEY y sin consultar ApiKeyRepository”, conservando sin modificaciones los tests existentes
- [X] T005 [P] [US1] Agregar a `backend/src/test/java/com/example/demo/security/JwtOrApiKeyAuthenticationIntegrationTest.java` los casos `GET /players` con Bearer válido → `200` y flujo real register → login → extraer token de la respuesta → `GET /players` sólo con ese token → `200`

### Implementation for User Story 1

- [X] T006 [P] [US1] Crear `backend/src/main/java/com/example/demo/security/JwtAuthFilter.java` como `@Component`/`OncePerRequestFilter`, detectar el prefijo `Bearer `, dejar pasar Authorization ausente o no Bearer, validar con `JwtUtil`, extraer el username y establecer `UsernamePasswordAuthenticationToken(username, null, List.of())` para el camino válido
- [X] T007 [P] [US1] Agregar al inicio de `doFilterInternal` en `backend/src/main/java/com/example/demo/security/ApiKeyAuthFilter.java` la única salida temprana permitida: si el `SecurityContext` contiene una Authentication autenticada, continuar la cadena y retornar sin alterar ningún mensaje ni la lógica existente
- [X] T008 [US1] Inyectar `JwtAuthFilter` y configurar en `backend/src/main/java/com/example/demo/config/SecurityConfig.java` primero `ApiKeyAuthFilter` antes de `UsernamePasswordAuthenticationFilter` y después `JwtAuthFilter` antes de `ApiKeyAuthFilter`, preservando exactamente CORS, CSRF, STATELESS y `PUBLIC_ROUTES`

**Checkpoint**: El flujo web con JWT válido funciona de punta a punta. No es todavía un incremento liberable hasta completar el rechazo seguro de US2.

---

## Phase 4: User Story 2 - Rechazar tokens Bearer no confiables (Priority: P1)

**Goal**: Todo Bearer vencido, mal firmado o mal formado devuelve el `401` JWT exacto y nunca usa la API key como fallback.

**Independent Test**: Enviar cada tipo de Bearer inválido a `GET /players`, incluido uno acompañado por una API key válida; todos deben devolver `{"error":"unauthorized","message":"Token inválido o vencido"}` y detener la cadena.

### Tests for User Story 2

> Escribir y observar fallar estos tests antes de completar la rama inválida.

- [X] T009 [P] [US2] Ampliar `backend/src/test/java/com/example/demo/security/JwtAuthFilterTest.java` con JWT vencido generado mediante expiración negativa, JWT firmado con otro secreto de al menos 32 bytes y `Bearer abc`; verificar `401`, JSON UTF-8 exacto y que la cadena no se invoca
- [X] T010 [P] [US2] Ampliar `backend/src/test/java/com/example/demo/security/JwtOrApiKeyAuthenticationIntegrationTest.java` con `GET /players` para Bearer vencido, firma inválida, mal formado y Bearer inválido junto con una API key válida; comprobar en todos el mensaje JWT y, en el último, la ausencia de fallback

### Implementation for User Story 2

- [X] T011 [US2] Completar en `backend/src/main/java/com/example/demo/security/JwtAuthFilter.java` la rama de Bearer inválido para responder `401`, `application/json`, UTF-8 y body exacto `{"error":"unauthorized","message":"Token inválido o vencido"}`, retornando sin invocar la cadena ni la validación de API key

**Checkpoint**: La precedencia Bearer queda cerrada y segura para tokens válidos e inválidos.

---

## Phase 5: User Story 3 - Conservar el acceso programático por API key (Priority: P1)

**Goal**: Toda solicitud sin Bearer conserva exactamente el comportamiento previo de `ApiKeyAuthFilter`, incluido Authorization con esquema Basic.

**Independent Test**: `GET /players` con API key válida y sin Authorization responde `200`; sin credenciales conserva el `401` actual; Basic más API key válida responde `200`.

### Tests for User Story 3

- [X] T012 [US3] Agregar a `backend/src/test/java/com/example/demo/security/JwtOrApiKeyAuthenticationIntegrationTest.java` los casos API key válida sin Authorization → `200`, ninguna credencial → `401` con `Falta el header X-API-KEY`, y `Authorization: Basic xxx` más API key válida → `200`
- [X] T013 [US3] Ejecutar `backend/src/test/java/com/example/demo/security/ApiKeyAuthFilterTest.java` completo y resolver únicamente regresiones causadas por esta feature sin cambiar los casos, mensajes ni expectativas preexistentes

**Checkpoint**: JWT y API key funcionan como alternativas y la compatibilidad programática está demostrada.

---

## Phase 6: User Story 4 - Mantener públicas las rutas públicas (Priority: P1)

**Goal**: Registro, login, Swagger, documentación y salud omiten las dos validaciones, incluso si reciben un Bearer inválido.

**Independent Test**: Registro y login funcionan sin credenciales y con Bearer inválido, manteniendo sus contratos y sin respuestas de los filtros.

### Tests for User Story 4

> Escribir y observar fallar el test unitario antes de agregar `shouldNotFilter`.

- [X] T014 [P] [US4] Agregar a `backend/src/test/java/com/example/demo/security/JwtAuthFilterTest.java` un caso que invoque el método público del filtro sobre una ruta de `SecurityConfig.PUBLIC_ROUTES` con Bearer inválido y verifique que continúa sin validar ni responder `401`
- [X] T015 [P] [US4] Agregar a `backend/src/test/java/com/example/demo/security/JwtOrApiKeyAuthenticationIntegrationTest.java` los casos `POST /auth/register` y `POST /auth/login` sin credenciales, más al menos una ruta pública con Bearer inválido, verificando que conservan status, request y response contract actuales

### Implementation for User Story 4

- [X] T016 [US4] Implementar `shouldNotFilter` en `backend/src/main/java/com/example/demo/security/JwtAuthFilter.java` reutilizando `SecurityConfig.getPublicRoutes()` y `AntPathMatcher` con el mismo cálculo de path de `ApiKeyAuthFilter`, sin duplicar ni modificar la lista pública

**Checkpoint**: Las rutas públicas no pueden quedar bloqueadas por ninguno de los dos mecanismos.

---

## Phase 7: User Story 5 - Entender las alternativas desde la documentación (Priority: P2)

**Goal**: Swagger/OpenAPI comunica Bearer O API key en cada operación protegida y explica cómo usar el JWT.

**Independent Test**: `/v3/api-docs` muestra `bearerAuth` y `apiKeyAuth` como objetos separados para GET/POST de jugadores; registro y login no declaran seguridad.

### Tests for User Story 5

> Actualizar el contrato automático antes de cambiar las anotaciones.

- [X] T017 [P] [US5] Ampliar `backend/src/test/java/com/example/demo/config/PlayerOpenApiTest.java` para verificar que `bearerAuth` existe, que GET `/players` y POST `/players/sync` contienen `bearerAuth` y `apiKeyAuth` en objetos alternativos separados sin depender del orden, y que `/auth/register` y `/auth/login` no exigen seguridad

### Implementation for User Story 5

- [X] T018 [US5] Conservar `@SecurityRequirement(name = "apiKeyAuth")` y agregar una anotación separada `@SecurityRequirement(name = "bearerAuth")` en `backend/src/main/java/com/example/demo/controller/PlayerController.java`, sin modificar endpoints, responses ni `OpenApiConfig.java`
- [X] T019 [P] [US5] Actualizar la sección Backend de `README.md` con el flujo Swagger `POST /auth/login` → copiar token → **Authorize** → `bearerAuth`, aclarando que el usuario web no necesita API key y que `apiKeyAuth` permanece como alternativa programática

**Checkpoint**: Comportamiento, contrato generado y guía de uso describen la misma alternativa JWT/API key.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Cerrar cobertura, alcance, build, CI y calidad sin introducir trabajo nuevo.

- [X] T020 Auditar los diez criterios de aceptación de `specs/006-jwt-protected-endpoints/spec.md` contra métodos concretos de `backend/src/test/java/com/example/demo/security/JwtOrApiKeyAuthenticationIntegrationTest.java`, completar únicamente cualquier aserción faltante y confirmar que cada criterio tiene al menos un test
- [X] T021 Ejecutar todos los pasos aplicables de `specs/006-jwt-protected-endpoints/quickstart.md`, correr `backend/mvnw.cmd verify` o `backend/mvnw verify` hasta obtener `BUILD SUCCESS` y registrar el resultado de validación al final de ese archivo
- [X] T022 Verificar con el diff final que `backend/pom.xml` no cambió, que no se tocaron modelos/repositories/contracts anteriores y que todos los cambios están limitados a `backend/`, `specs/006-jwt-protected-endpoints/` y `README.md`; documentar cualquier desviación antes de continuar en `specs/006-jwt-protected-endpoints/quickstart.md`
- [ ] T023 Tras publicar la rama, comprobar que `.github/workflows/ci.yml` finaliza en `SUCCESS` y que SonarCloud no agrega issues ni falla el Quality Gate; registrar enlaces/resultados en `specs/006-jwt-protected-endpoints/quickstart.md` sin modificar el workflow

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: comienza inmediatamente.
- **Foundational (Phase 2)**: depende de T001 y bloquea los tests integrados de todas las historias.
- **US1 (Phase 3)**: depende de T002 y crea la cadena mínima JWT → API key.
- **US2 (Phase 4)**: depende de US1 porque endurece la rama inválida del filtro JWT ya conectado.
- **US3 (Phase 5)**: depende de US1 porque valida la compatibilidad del filtro de API key dentro de la cadena nueva; puede ejecutarse en paralelo con US2 si se coordinan las ediciones del test integrado.
- **US4 (Phase 6)**: depende de US1 porque completa el bypass público del filtro nuevo; puede avanzar en paralelo con US2/US3 evitando ediciones simultáneas del mismo test.
- **US5 (Phase 7)**: puede comenzar después de US1 y avanzar en paralelo con US2–US4 porque modifica archivos de OpenAPI/README separados.
- **Polish (Phase 8)**: depende de US1–US5 completas.

### User Story Dependencies

```text
Setup → Foundational → US1
                         ├──→ US2 ──┐
                         ├──→ US3 ──┤
                         ├──→ US4 ──┼──→ Polish
                         └──→ US5 ──┘
```

- **US1** es la base funcional para todas las demás historias.
- **US2**, **US3** y **US4** completan seguridad, regresión y rutas públicas sobre esa base.
- **US5** sólo depende de que exista el comportamiento que documenta.

### Within Each User Story

- Escribir los tests indicados antes del código que los hace pasar.
- Verificar primero el filtro aislado y luego la cadena integrada.
- No avanzar de checkpoint con tests rojos.
- Mantener los cambios mínimos definidos por el diseño cerrado.

### Parallel Opportunities

- En US1, T003, T004 y T005 pueden escribirse en paralelo; después T006 y T007 pueden implementarse en paralelo antes de T008.
- En US2, T009 y T010 pueden escribirse en paralelo antes de T011.
- En US4, T014 y T015 pueden escribirse en paralelo antes de T016.
- En US5, T017 y T019 pueden avanzar en paralelo; T018 depende del test T017.
- US5 puede desarrollarse en paralelo con US2–US4.
- Las tareas que editan `JwtOrApiKeyAuthenticationIntegrationTest.java` deben serializarse o coordinarse para evitar conflictos.

---

## Parallel Examples

### User Story 1

```text
Task T003: unit tests de JwtAuthFilter
Task T004: regresión de ApiKeyAuthFilter ya autenticado
Task T005: tests integrados de Bearer válido y flujo completo

Después, en paralelo:
Task T006: implementación de JwtAuthFilter
Task T007: bypass en ApiKeyAuthFilter
```

### User Story 2

```text
Task T009: unit tests de Bearer inválido
Task T010: tests integrados de Bearer inválido y no-fallback
```

### User Story 4

```text
Task T014: test unitario de ruta pública
Task T015: tests integrados de register/login públicos
```

### User Story 5

```text
Task T017: contrato OpenAPI automático
Task T019: documentación de uso en README
```

---

## Implementation Strategy

### MVP First

1. Completar Setup y Foundational.
2. Completar US1 para demostrar registro → login → JWT → `/players`.
3. Detenerse y validar el flujo principal.
4. No publicar ese incremento aislado: completar US2 antes de considerarlo seguro y liberable.

### Incremental Delivery

1. **US1**: habilita el JWT válido.
2. **US2**: cierra rechazo y precedencia sin fallback.
3. **US3**: demuestra compatibilidad completa de API key.
4. **US4**: protege el carácter público de registro/login/documentación/salud.
5. **US5**: alinea OpenAPI y README.
6. **Polish**: valida suite, alcance, CI y SonarCloud.

### Suggested Release Scope

Por tratarse de autenticación, el alcance mínimo liberable es **US1 + US2 + US3 + US4**. US1 solo sirve como checkpoint técnico; US5 y Polish son obligatorios para cumplir la Definition of Done completa.

## Notes

- `[P]` sólo aparece cuando las tareas trabajan en archivos diferentes y no dependen de una tarea incompleta.
- No se agregan dependencias, endpoints, modelos, repositories ni refactors.
- Los tests existentes de API key permanecen sin modificaciones; sólo se agrega el caso autorizado.
- Los cambios de implementación quedan limitados a `backend/`; fuera de él sólo se permiten esta feature y `README.md`.
