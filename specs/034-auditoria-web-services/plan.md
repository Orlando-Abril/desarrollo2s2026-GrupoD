# Implementation Plan: Auditoría de invocaciones a web services

**Branch**: `feature/auditoria-web-services` | **Date**: 2026-11-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/034-auditoria-web-services/spec.md`

## Summary

Cada invocación a un método de un `@RestController` de `com.example.demo.controller` deja exactamente una línea de auditoría (usuario, método HTTP, ruta, `Clase.método`, parámetros saneados, duración en ms, resultado y `correlationId`). Se resuelve con **un único aspecto** `@Around` (`WebServiceAuditAspect`) seleccionado por pointcut, sin tocar controllers. El saneamiento de parámetros (exclusión de objetos técnicos, enmascarado de claves sensibles, truncado a 200 caracteres) vive en un `ParameterSanitizer` aparte, testeable solo. Las líneas salen por el logger `WEB_SERVICE_AUDIT` a la consola (mismo JSON de hoy) y a un archivo rotativo `logs/audit.log`, configurado en un `logback-spring.xml` nuevo que reutiliza los defaults de Spring Boot. La auditoría nunca altera la respuesta: la línea se arma en un `finally` protegido y la excepción original siempre se relanza.

## Technical Context

**Language/Version**: Java 17

**Primary Dependencies**: Spring Boot 4.1.1 (webmvc, security), nuevo `org.springframework.boot:spring-boot-starter-aspectj` (sucesor de `spring-boot-starter-aop` en Boot 4), Jackson 3 (`tools.jackson.databind.ObjectMapper`, el bean que ya provee Boot), Logback (incluido por Boot).

**Storage**: N/A. Sólo salida de logs: consola + archivo `logs/audit.log` (rotación diaria, 7 días). No se persiste en base.

**Testing**: JUnit 5 + AssertJ; `@SpringBootTest` + `@AutoConfigureMockMvc` (perfil de test con H2, como `HealthEndpointTest`) + `OutputCaptureExtension` para leer la consola; test unitario puro de `ParameterSanitizer`.

**Target Platform**: Servidor Spring Boot (backend/), ejecutado desde `backend/` en desarrollo y demo.

**Project Type**: web-service (backend de la aplicación web).

**Performance Goals**: Sobrecosto despreciable frente a la request: una serialización del árbol de argumentos y una línea de log por invocación.

**Constraints**: No modificar controllers ni archivos de entregas anteriores fuera de lo autorizado (pom.xml para la dependencia; `logback-spring.xml` nuevo; `.gitignore` para `logs/`). La auditoría nunca rompe una request. No exponer secretos. Formato de consola idéntico al actual.

**Scale/Scope**: 6 controllers existentes y cualquiera futuro del paquete; 2 clases nuevas de producción, 1 archivo de configuración de logging, 2 clases de test.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo se cumple | Estado |
|-----------|----------------|--------|
| §2.1 Backend en capas estricto | La auditoría es transversal: paquete nuevo `com.example.demo.audit`, que no es capa ni depende de controller/service/repository/adapter (el pointcut referencia el paquete por expresión, sin importar clases). Los controllers no se tocan ni dependen de `audit`. Compatible con las reglas ArchUnit de la 033 (`LayeredArchitectureTest` considera sólo dependencias entre capas; `CodingRulesTest` prohíbe streams estándar → se usa logger). | PASS |
| §4.3 Validación estricta de datos | No cambia: la validación sigue en los controllers. El aspecto no modifica argumentos (sólo lee una copia serializada) ni el resultado. Las requests que fallan validación antes del método no se auditan (documentado en spec). | PASS |
| §5.3 Observabilidad | Refuerza: cada invocación queda trazada con `correlationId` (del MDC, que ya pone `CorrelationIdFilter`) y duración en ms; las líneas mantienen el formato JSON estructurado actual y el archivo de auditoría usa el mismo patrón. Health y métricas de actuator no cambian y quedan fuera del pointcut. | PASS |
| §4.2 Auditoría en base (Entrega 2) | No aplica a esta feature: `TransactionAuditService` no se toca; esta auditoría es sólo de logs. | N/A |
| Regla de alcance (nada que el enunciado no pida; no tocar entregas anteriores) | Cambios a archivos existentes sólo en `pom.xml` (dependencia autorizada) y `.gitignore` (`logs/`). `application.properties` no se modifica: `logging.pattern.console` sigue vigente porque el `logback-spring.xml` incluye `console-appender.xml` de Boot; el nivel se configura con `logging.level.WEB_SERVICE_AUDIT` y el interruptor `audit.web-services.enabled` tiene default `true` en código. | PASS |

**Post-design re-check**: PASS (sin cambios tras Phase 1; no hay violaciones a justificar).

## Project Structure

### Documentation (this feature)

```text
specs/034-auditoria-web-services/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── audit-log-line.md
├── checklists/
│   └── requirements.md
└── tasks.md             # $speckit-tasks
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                         # + spring-boot-starter-aspectj
├── src/main/java/com/example/demo/
│   └── audit/                                      # NUEVO
│       ├── WebServiceAuditAspect.java              # @Aspect @Component @Order(HIGHEST_PRECEDENCE)
│       │                                           # @ConditionalOnProperty(audit.web-services.enabled, matchIfMissing=true)
│       └── ParameterSanitizer.java                 # @Component: exclusiones, enmascarado, truncado
├── src/main/resources/
│   └── logback-spring.xml                          # NUEVO: defaults + console-appender de Boot + AUDIT_FILE
└── src/test/java/com/example/demo/audit/           # NUEVO
    ├── ParameterSanitizerTest.java                 # unitario
    └── WebServiceAuditAspectTest.java              # @SpringBootTest + MockMvc + OutputCaptureExtension

.gitignore                                          # + logs/
```

**Structure Decision**: Paquete `audit/` (nombre del dominio de la feature, no del mecanismo). No se agrega a ninguna capa de `LayeredArchitectureTest`; si en el futuro la regla de la 033 enumera paquetes permitidos, `audit` debe sumarse ahí como paquete transversal junto a `config`, `filter` y `security`.

### Diseño del aspecto

- **Pointcut**: `within(com.example.demo.controller..*) && @within(org.springframework.web.bind.annotation.RestController)`. Toma todo método público de un `@RestController` del paquete (los nuevos incluidos) y excluye actuator (otro paquete) y `GlobalExceptionHandler` (`exception`, `@RestControllerAdvice`).
- **Orden**: `@Order(Ordered.HIGHEST_PRECEDENCE)` para que el aspecto envuelva también el interceptor de `@PreAuthorize` de `StrategyController`; un 403 por método queda auditado con `outcome=AuthorizationDeniedException`.
- **Flujo `@Around`**: `start = System.nanoTime()`; `try { result = pjp.proceed(); outcome = "OK"; return result; } catch (Throwable ex) { outcome = ex.getClass().getSimpleName(); throw ex; } finally { audit(pjp, outcome, elapsedMs); }`. El advice declara `throws Throwable` porque `ProceedingJoinPoint.proceed()` lo declara, y se mantiene un único `catch (Throwable)` que relanza la misma instancia: partirlo en `Exception`/`Error` no evita la regla S1181 (también marca `catch (Error)`). Si SonarCloud marca `java:S1181`/`java:S112`, se justifica con `// NOSONAR` como `SecurityConfig` con S4502. `audit(...)` está entero dentro de `try { … } catch (RuntimeException e) { log.warn("ws_audit_failed operation=… error=<tipo>") }`, sin datos de argumentos ni mensaje.
- **Usuario**: `SecurityContextHolder.getContext().getAuthentication()`; `anonymous` si es `null`, `!isAuthenticated()` o `AnonymousAuthenticationToken`; si no, `getName()` (username del JWT, o el que `ApiKeyAuthFilter` pone como principal: username del dueño o prefijo de la key).
- **Operación**: `RequestContextHolder.getRequestAttributes()` → `HttpServletRequest.getMethod()` y `getRequestURI()` (sin query string); `-` si no hay request. Más `signature.toShortString()` → `PlayerController.list(..)`.
- **Parámetros**: `ParameterSanitizer.sanitize(String[] names, Object[] args)` → texto JSON (ver [data-model.md](data-model.md)).
- **Línea**: `LoggerFactory.getLogger("WEB_SERVICE_AUDIT").info(...)`; se construye sólo si `isInfoEnabled()`. Formato en [contracts/audit-log-line.md](contracts/audit-log-line.md).

### logback-spring.xml

- `<include resource="org/springframework/boot/logging/logback/defaults.xml"/>` y `console-appender.xml` → el appender `CONSOLE` usa `CONSOLE_LOG_PATTERN`, que Boot toma de `logging.pattern.console` (el JSON actual sigue igual).
- `AUDIT_FILE`: `RollingFileAppender` en `logs/audit.log`, `TimeBasedRollingPolicy` con `logs/audit.%d{yyyy-MM-dd}.log`, `maxHistory=7`, encoder con `${CONSOLE_LOG_PATTERN}` y charset UTF-8 → el archivo tiene el mismo JSON.
- `<logger name="WEB_SERVICE_AUDIT" additivity="true"><appender-ref ref="AUDIT_FILE"/></logger>` → va al archivo y, por additividad, también al `CONSOLE` de root. Sin `level` en el XML para que `logging.level.WEB_SERVICE_AUDIT` lo gobierne (default heredado INFO).
- `<root level="INFO"><appender-ref ref="CONSOLE"/></root>` (equivalente a la configuración por defecto actual).

### Tests

- `ParameterSanitizerTest`: enmascara `password`, `refreshToken`, `accessToken`, `newPassword` (raíz y anidados en DTO/lista/mapa); trunca a 200 + marca; excluye `HttpServletRequest`, `HttpServletResponse`, `BindingResult`, `Authentication`/`Principal`, `HttpSession`, `MultipartFile`, streams; `null` se informa como `null`; un argumento que falla al serializar se informa como `"<unserializable:Tipo>"` sin propagar.
- `WebServiceAuditAspectTest` (`@ExtendWith(OutputCaptureExtension.class)`): una línea por request (contar `ws_audit ` en la salida); login con `password=***`, la contraseña literal ausente, `user=anonymous`; request con JWT → `user=<username>`; endpoint que lanza excepción → `outcome=<Tipo>` y status igual al esperado sin auditoría; `durationMs` presente; `correlationId` de la línea = header de respuesta; `/actuator/health` sin línea; fallo forzado del sanitizer (`@MockitoBean` que lanza) → respuesta normal y `ws_audit_failed`.

## Complexity Tracking

Sin violaciones a la constitución; no aplica.
