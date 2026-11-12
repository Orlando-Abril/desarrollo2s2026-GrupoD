---

description: "Task list for 034 - Auditoría de invocaciones a web services"
---

# Tasks: Auditoría de invocaciones a web services

**Input**: Design documents from `specs/034-auditoria-web-services/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/audit-log-line.md, quickstart.md

**Tests**: Incluidos — el plan pide `ParameterSanitizerTest` (unitario) y `WebServiceAuditAspectTest` (`@SpringBootTest` + `@AutoConfigureMockMvc` + `@ExtendWith(OutputCaptureExtension.class)`). Escribirlos antes de la implementación de cada historia y verlos fallar.

**Organization**: Una fase por historia de la spec (US1–US4). Rutas relativas a la raíz del repo.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede correr en paralelo (archivo distinto, sin dependencias pendientes)
- **[Story]**: US1–US4 de spec.md

## Restricciones para todas las tareas

- **No modificar** ningún archivo de `backend/src/main/java/com/example/demo/controller/`, ni `application.properties`, `SecurityConfig`, `TransactionAuditService`, ni los handlers de `exception/`. Archivos existentes tocados: sólo `backend/pom.xml` y `.gitignore`.
- Nada de `System.out`/`System.err` (regla ArchUnit de la 033): sólo SLF4J.
- Paquete nuevo `com.example.demo.audit`; no importar clases de `controller`, `service`, `repository` ni `adapter`.
- Tests de integración: seguir el patrón de `backend/src/test/java/com/example/demo/config/HealthEndpointTest.java` (`@SpringBootTest` + `@AutoConfigureMockMvc`, H2 del perfil de test) y para usuarios/JWT/API key el de `backend/src/test/java/com/example/demo/controller/OrderControllerBuyTest.java` (`MarketTestData.clean`, `MarketSeed`, `JwtUtil#generateToken(username)`, `AuthFixtures.apiKey`).

---

## Phase 1: Setup

**Purpose**: Dependencia AOP y carpeta de logs fuera de git.

- [X] T001 Agregar en `backend/pom.xml`, dentro de `<dependencies>` junto a los demás starters y sin versión (la gestiona el parent 4.1.1), la dependencia `org.springframework.boot:spring-boot-starter-aspectj` (en Boot 4 reemplaza a `spring-boot-starter-aop`). No cambiar ninguna otra parte del pom.
- [X] T002 [P] Agregar la línea `logs/` en `.gitignore` (raíz del repo), en la sección `### Local secrets and generated reports ###`.

---

## Phase 2: Foundational

**Purpose**: Esqueleto del sanitizer que usa el aspecto desde US1.

- [X] T003 Crear `backend/src/main/java/com/example/demo/audit/ParameterSanitizer.java`: `@Component`, constructor que recibe `tools.jackson.databind.ObjectMapper` (el bean de Boot), método público `String sanitize(String[] names, Object[] args)` que devuelve un objeto JSON compacto `{ "<nombre>": <valor> }` en el orden de la firma: `null` → `null`; otro valor → `objectMapper.valueToTree(arg)`; si la serialización lanza, el valor es el string `"<unserializable:" + arg.getClass().getSimpleName() + ">"`; si `names` es `null` o de distinto largo que `args`, usar `arg0`, `arg1`, …. Devuelve `{}` si no hay argumentos. Nunca lanza (envolver todo en try/catch de `RuntimeException` y devolver `"<unavailable>"`). Exclusiones, enmascarado y truncado se agregan en US3.

**Checkpoint**: compila (`./mvnw -q -DskipTests compile` desde `backend/`).

---

## Phase 3: User Story 1 - Rastrear cada invocación a la API (P1) 🎯 MVP

**Goal**: Una línea `ws_audit` por invocación de método de `@RestController` del paquete controller, con usuario, operación, parámetros, duración, outcome y correlationId.

**Independent Test**: Llamar un endpoint con JWT, uno con `X-API-KEY` y uno público sin credenciales; una sola línea por llamada con todos los campos y el `correlationId` igual al header `X-Correlation-ID` de la respuesta.

### Tests for User Story 1

- [X] T004 [US1] Crear `backend/src/test/java/com/example/demo/audit/WebServiceAuditAspectTest.java` (`@SpringBootTest`, `@AutoConfigureMockMvc`, `@ExtendWith(OutputCaptureExtension.class)`, datos con `MarketTestData`/`MarketSeed` limpiados en `@BeforeEach`/`@AfterEach`) con un helper que cuente las ocurrencias de `"ws_audit "` en `CapturedOutput#getOut()` posteriores a una marca tomada antes de cada request, y los casos: (a) `GET /players?league=PREMIER_LEAGUE&position=DEFENDER` con `Authorization: Bearer <jwtUtil.generateToken(username)>` → exactamente 1 línea, contiene `"logger":"WEB_SERVICE_AUDIT"`, `user=<username>`, `method=GET path=/players`, `operation=PlayerController.`, params `{"league":"PREMIER_LEAGUE","team":null,"position":"DEFENDER"}` (comillas escapadas en el `message`), `durationMs=` seguido de dígitos y `outcome=OK`; (b) la línea contiene `"correlationId":"<valor del header X-Correlation-ID de la respuesta>"`; (c) `GET /players/ranking` con `X-API-KEY` (`AuthFixtures.apiKey`, como `RankingControllerTest`) → 1 línea con `user=<username del dueño de la key>` y `operation=QuoteController.ranking(..)`; (d) `POST /auth/register` sin credenciales → `user=anonymous` y `operation=AuthController.register(..)`; (e) `GET /actuator/health` → 0 líneas; (f) `GET /users/{id}/portfolio` con el JWT del mismo usuario (como `UserMarketSecurityTest`) → 200 y 1 línea con `operation=UserMarketController.portfolio(..)` y `"id":<id>` en params; (g) `GET /orders/mine` con JWT → 1 línea con `operation=OrderController.` y params `{}` (sólo recibe `Authentication`). Cobertura por controller (SC-001): Player (a), Auth (d), Quote (c), UserMarket (f), Order (g), Strategy (T008 b).

### Implementation for User Story 1

- [X] T005 [US1] Crear `backend/src/main/java/com/example/demo/audit/WebServiceAuditAspect.java`: `@Aspect @Component @Order(Ordered.HIGHEST_PRECEDENCE)`, logger `private static final Logger AUDIT = LoggerFactory.getLogger("WEB_SERVICE_AUDIT")`, inyecta `ParameterSanitizer`, advice `@Around("within(com.example.demo.controller..*) && @within(org.springframework.web.bind.annotation.RestController)")` que declara `throws Throwable` (lo exige `ProceedingJoinPoint.proceed()`), con: `long start = System.nanoTime()`; `Object result = pjp.proceed(); outcome = "OK"; return result;` y en `finally` calcular `durationMs = (System.nanoTime() - start) / 1_000_000` y llamar a `audit(pjp, outcome, durationMs)`. `audit` sólo arma la línea si `AUDIT.isInfoEnabled()` y emite `AUDIT.info("ws_audit user={} method={} path={} operation={} params={} durationMs={} outcome={}", …)` en ese orden exacto (contrato `contracts/audit-log-line.md`).
- [X] T006 [US1] En `WebServiceAuditAspect.java`, resolver usuario: `SecurityContextHolder.getContext().getAuthentication()`; `"anonymous"` si es `null`, `!isAuthenticated()` o `instanceof AnonymousAuthenticationToken`; si no, `getName()`.
- [X] T007 [US1] En `WebServiceAuditAspect.java`, resolver operación: si `RequestContextHolder.getRequestAttributes()` es `ServletRequestAttributes`, `method = request.getMethod()` y `path = request.getRequestURI()` (sin query string); si no hay request, `"-"` para ambos. `operation = pjp.getSignature().toShortString()` (ej. `PlayerController.list(..)`). Parámetros: `sanitizer.sanitize(((MethodSignature) pjp.getSignature()).getParameterNames(), pjp.getArgs())`.

**Checkpoint**: T004 pasa; US1 funcional (la línea ya sale por consola con el appender por defecto).

---

## Phase 4: User Story 2 - Auditar también las invocaciones que fallan (P1)

**Goal**: Línea con `outcome=<TipoExcepcion>` cuando el método lanza, sin alterar la excepción ni la respuesta.

**Independent Test**: `GET /players/{id}` inexistente y `PUT /strategies/active` con un USER: una línea cada uno con el tipo de excepción y el mismo status HTTP que hoy.

### Tests for User Story 2

- [X] T008 [US2] Agregar a `backend/src/test/java/com/example/demo/audit/WebServiceAuditAspectTest.java`: (a) `GET /players/999999` con JWT → status 404 (el mismo que sin auditoría) y 1 línea con `outcome=PlayerNotFoundException` y `durationMs=`; (b) `PUT /strategies/active` con JWT de un USER (no ADMIN) y cuerpo válido → status 403 y 1 línea con `user=<username>` y `outcome=AuthorizationDeniedException`; (c) `POST /auth/login` con cuerpo inválido (`{"username":"","password":""}`) → status 400 y 0 líneas (falla de `@Valid @RequestBody` antes del método, spec US2 escenario 4); (d) ninguna línea `ws_audit` contiene `Exception:` ni `at com.` (sin mensaje ni stack trace); (e) `GET /users/0/portfolio` con JWT → status 400 (el de `GlobalExceptionHandler`) y 1 línea con `outcome=ConstraintViolationException` (`@Min(1)` de un controller `@Validated`, validado por el proxy AOP dentro del aspecto; spec US2 escenario 5).

### Implementation for User Story 2

- [X] T009 [US2] En `WebServiceAuditAspect.java`, agregar `catch (Throwable ex) { outcome = ex.getClass().getSimpleName(); throw ex; }` entre el `try` y el `finally` del advice (inicializar `outcome` antes del `try`); relanzar siempre la misma instancia, sin envolver ni loguear el mensaje. No partir el catch en `Exception`/`Error` (S1181 también marca `catch (Error)`). Si SonarCloud marca `java:S1181` o `java:S112` en el advice, dejar `// NOSONAR java:S1181` con la justificación en el comentario (idioma estándar de `@Around`, se relanza la misma instancia), como ya se hace en `SecurityConfig` con S4502.

**Checkpoint**: T008 pasa; US1 sigue verde.

---

## Phase 5: User Story 3 - Proteger datos sensibles (P1)

**Goal**: Contraseñas y tokens enmascarados, objetos técnicos omitidos y valores largos truncados.

**Independent Test**: Login, registro y refresh con credenciales conocidas: el literal de la contraseña y del token no aparece en la salida.

### Tests for User Story 3

- [X] T010 [P] [US3] Crear `backend/src/test/java/com/example/demo/audit/ParameterSanitizerTest.java` (unitario, `new ParameterSanitizer(JsonMapper.builder().build())`) con casos: `LoginRequest("ana","S3creta!")` como `request` → `{"request":{"username":"ana","password":"***"}}`; parámetro llamado `refreshToken` → `"***"` aunque el valor sea `null`; campos anidados `accessToken`, `newPassword`, `apiToken` dentro de un record, de una `List` y de un `Map` → `"***"`; un `String` de 300 caracteres → string de exactamente los primeros 200 + `…(truncated)`; argumentos `MockHttpServletRequest`, `MockHttpServletResponse`, `BeanPropertyBindingResult`, `UsernamePasswordAuthenticationToken`, `MockHttpSession`, `MockMultipartFile`, `InputStream` → omitidos (no aparecen sus nombres); `null` no sensible → `null`; un objeto cuyo getter lanza → `"<unserializable:Tipo>"` sin propagar; sin argumentos → `{}`.
- [X] T011 [US3] Agregar a `backend/src/test/java/com/example/demo/audit/WebServiceAuditAspectTest.java`: `POST /auth/login` con `{"username":"<u>","password":"S3creta!Audit"}` → la línea contiene `"password":"***"` escapado como `\"password\":\"***\"` y `CapturedOutput` no contiene `S3creta!Audit` en ningún lado; `POST /auth/refresh` con la cookie `refresh_token` (`AuthController.REFRESH_COOKIE`) de valor conocido → `refreshToken` como `***` y el valor no aparece en la salida.

### Implementation for User Story 3

- [X] T012 [US3] En `ParameterSanitizer.java`, exclusión previa a serializar: omitir el argumento si es instancia de `jakarta.servlet.ServletRequest`, `jakarta.servlet.ServletResponse`, `jakarta.servlet.http.HttpSession`, `org.springframework.validation.Errors` (incluye `BindingResult`), `java.security.Principal` (incluye `Authentication`), `org.springframework.web.context.request.WebRequest`, `org.springframework.web.multipart.MultipartFile`, `java.io.InputStream`, `java.io.OutputStream`, `java.io.Reader` o `java.io.Writer`.
- [X] T013 [US3] En `ParameterSanitizer.java`, enmascarado: método `isSensitive(String name)` = `name.toLowerCase(Locale.ROOT)` contiene `"password"` o `"token"`. Si el nombre del parámetro es sensible, el valor es el texto `"***"` sin serializar. Si no, tras `valueToTree`, recorrer recursivamente el árbol (`ObjectNode` y `ArrayNode`, a cualquier profundidad) reemplazando por `"***"` el valor de todo campo cuyo nombre sea sensible.
- [X] T014 [US3] En `ParameterSanitizer.java`, truncado: constante `MAX_VALUE_LENGTH = 200`; si `node.toString()` del valor de un parámetro ya enmascarado supera 200 caracteres, reemplazarlo por el texto `toString().substring(0, 200) + "…(truncated)"`.

**Checkpoint**: T010 y T011 pasan; US1 y US2 siguen verdes.

---

## Phase 6: User Story 4 - Auditoría separada, configurable y que nunca rompe la request (P2)

**Goal**: Logger propio a consola (mismo JSON) y a `logs/audit.log` rotativo; nivel e interruptor configurables; fallo de auditoría sin efecto en la respuesta.

**Independent Test**: Ver la línea en consola y en `backend/logs/audit.log`; con `logging.level.WEB_SERVICE_AUDIT=WARN` no hay líneas; con el sanitizer fallando el endpoint responde igual.

### Tests for User Story 4

- [X] T015 [US4] Agregar a `backend/src/test/java/com/example/demo/audit/WebServiceAuditAspectTest.java`: (a) tras un `GET /players` con JWT, `backend/logs/audit.log` (ruta `Path.of("logs/audit.log")`, relativa al working dir de Maven) existe y contiene una línea con `ws_audit` y el `correlationId` de la respuesta; (b) en la clase aparte `backend/src/test/java/com/example/demo/audit/WebServiceAuditFailureTest.java`, con `@MockitoBean ParameterSanitizer` cuyo `sanitize` lanza `IllegalStateException` → `GET /players` responde 200 con el mismo cuerpo que sin auditoría y la salida contiene `ws_audit_failed operation=PlayerController.` y `error=IllegalStateException`, sin línea `ws_audit `.
- [X] T016 [P] [US4] Crear `backend/src/test/java/com/example/demo/audit/WebServiceAuditDisabledTest.java`: `@SpringBootTest(properties = "audit.web-services.enabled=false")` + `@AutoConfigureMockMvc` + `OutputCaptureExtension`; verificar que no existe bean `WebServiceAuditAspect` en el contexto y que `GET /players` con JWT no produce `ws_audit`. Y `WebServiceAuditLevelTest.java` con `@SpringBootTest(properties = "logging.level.WEB_SERVICE_AUDIT=WARN")` → 0 líneas `ws_audit` y respuesta 200.

### Implementation for User Story 4

- [X] T017 [P] [US4] Crear `backend/src/main/resources/logback-spring.xml`: `<include resource="org/springframework/boot/logging/logback/defaults.xml"/>` y `<include resource="org/springframework/boot/logging/logback/console-appender.xml"/>`; appender `AUDIT_FILE` `ch.qos.logback.core.rolling.RollingFileAppender` con `<file>logs/audit.log</file>`, `TimeBasedRollingPolicy` con `<fileNamePattern>logs/audit.%d{yyyy-MM-dd}.log</fileNamePattern>` y `<maxHistory>7</maxHistory>`, encoder `<pattern>${CONSOLE_LOG_PATTERN}</pattern>` y `<charset>UTF-8</charset>`; `<logger name="WEB_SERVICE_AUDIT" additivity="true"><appender-ref ref="AUDIT_FILE"/></logger>` **sin** atributo `level`; `<root level="INFO"><appender-ref ref="CONSOLE"/></root>`. No mover ni duplicar `logging.pattern.console` de `application.properties`.
- [X] T018 [US4] En `WebServiceAuditAspect.java`, agregar `@ConditionalOnProperty(name = "audit.web-services.enabled", havingValue = "true", matchIfMissing = true)` a la clase.
- [X] T019 [US4] En `WebServiceAuditAspect.java`, envolver todo el cuerpo de `audit(...)` en `try { … } catch (RuntimeException e) { AUDIT.warn("ws_audit_failed operation={} error={}", safeOperation, e.getClass().getSimpleName()); }`, donde `safeOperation` se calcula con `pjp.getSignature().toShortString()` también protegido; nunca loguear parámetros ni el mensaje de `e`. Verificar que el `finally` del advice no puede lanzar.

**Checkpoint**: T015 y T016 pasan; todas las historias verdes.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T020 Confirmar que no hay cambios en `backend/src/main/java/com/example/demo/controller/` ni en `backend/src/main/resources/application.properties` (`git diff --stat main -- backend/src/main/java/com/example/demo/controller backend/src/main/resources/application.properties` vacío) y que los únicos archivos existentes modificados son `backend/pom.xml` y `.gitignore`.
- [X] T021 Correr desde `backend/` `./mvnw test` (suite completa): los tests de controllers existentes siguen verdes con el aspecto activo y los de OpenAPI no cambian. Si la rama de ArchUnit (033) está integrada, sus reglas también pasan.
- [X] T022 Verificar a mano el formato de consola: arrancar con `./mvnw spring-boot:run` y comparar una línea de log no relacionada con la auditoría contra el formato JSON de `logging.pattern.console` (sin cambios), y una línea `WEB_SERVICE_AUDIT` contra `specs/034-auditoria-web-services/contracts/audit-log-line.md`. Lo corre el usuario (requiere base y Redis de desarrollo); seguir `specs/034-auditoria-web-services/quickstart.md`.
- [ ] T023 Después del push (lo hace el usuario), revisar el análisis de SonarCloud de la rama `feature/auditoria-web-services`: los issues nuevos en `backend/src/main/java/com/example/demo/audit/` tienen que quedar en 0 o justificados con `// NOSONAR <regla>` y comentario (ver T009), sin superar el límite de 10 issues de la constitución §3.2.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (T001–T002)**: sin dependencias.
- **Foundational (T003)**: depende de T001 (compila con el starter presente; no lo usa aún).
- **US1 (T004–T007)**: depende de T003. Es el MVP.
- **US2 (T008–T009)**: depende de T005 (modifica el advice).
- **US3 (T010–T014)**: depende de T003; T011 depende de T005–T007 (necesita el aspecto). T010 puede escribirse en paralelo con US1.
- **US4 (T015–T019)**: T017 es independiente (archivo propio); T018–T019 dependen de T005; T015 depende de T017 y T019.
- **Polish (T020–T023)**: al final; T023 después del push.

### Within Each User Story

- Tests primero, verlos fallar, luego implementación.
- `WebServiceAuditAspect.java` y `WebServiceAuditAspectTest.java` los tocan varias historias: no paralelizar tareas sobre el mismo archivo.

### Parallel Opportunities

- T002 junto con T001.
- T010 (`ParameterSanitizerTest`) junto con cualquier tarea de US1/US2.
- T016 y T017 (archivos nuevos propios) junto con T015 o con tareas de US3.

---

## Parallel Example: tras Foundational

```text
Task: "T004 [US1] WebServiceAuditAspectTest casos de US1"
Task: "T010 [P] [US3] ParameterSanitizerTest"
Task: "T017 [P] [US4] logback-spring.xml"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. T001–T003.
2. T004–T007 → una línea por request con usuario, operación, params, duración, outcome y correlationId.
3. **Validar**: T004 verde.

### Incremental Delivery

1. US1 → MVP.
2. US2 → outcome de excepciones (incluye `@PreAuthorize`).
3. US3 → enmascarado/exclusiones/truncado (necesario antes de mostrarlo en una demo con login).
4. US4 → archivo propio, nivel, interruptor y blindaje ante fallos.
5. Polish → verificación de alcance y suite completa.

---

## Notes

- El usuario hace los commits; no commitear.
- No correr tests e2e ni SQL contra la base del usuario; T022 lo ejecuta el usuario.
