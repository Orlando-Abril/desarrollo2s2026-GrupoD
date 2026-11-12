# Research: Auditoría de invocaciones a web services

## R1. Dependencia AOP en Spring Boot 4

- **Decision**: `org.springframework.boot:spring-boot-starter-aspectj` (versión gestionada por el parent 4.1.1).
- **Rationale**: En Boot 4 el starter `spring-boot-starter-aop` fue reemplazado por `spring-boot-starter-aspectj`; trae `aspectjweaver` y habilita `AopAutoConfiguration` (proxies CGLIB por defecto). Spring AOP con proxies alcanza: sólo interceptamos beans (controllers) desde fuera.
- **Alternatives considered**: `aspectjweaver` suelto + `@EnableAspectJAutoProxy` (más configuración manual, sin ganancia); weaving en compilación/carga (innecesario para beans Spring).

## R2. Pointcut

- **Decision**: `within(com.example.demo.controller..*) && @within(org.springframework.web.bind.annotation.RestController)`.
- **Rationale**: Cumple literalmente "método de un `@RestController` del paquete controller", cubre controllers futuros sin cambios y deja fuera actuator (otro paquete) y los `@RestControllerAdvice` de `exception`. `@within` no requiere anotaciones en los métodos.
- **Alternatives considered**: `execution(* com.example.demo.controller..*(..))` sin filtro de anotación (tomaría clases auxiliares del paquete); anotación propia `@Audited` por método (exige tocar controllers, lo que prohíbe el requerimiento y contradice el pedido del profesor).

## R3. Orden respecto de `@PreAuthorize`

- **Decision**: `@Order(Ordered.HIGHEST_PRECEDENCE)` en el aspecto.
- **Rationale**: `StrategyController` usa `@PreAuthorize` (spec 031, `MethodSecurityConfig`). Con el aspecto como advisor más externo, un acceso denegado por el método se audita con `outcome=AuthorizationDeniedException` y su duración; la excepción se relanza intacta y el 403 no cambia.
- **Alternatives considered**: orden por defecto (indeterminado frente al advisor de method security; podría perder esas líneas).

## R4. Qué se audita y qué no según dónde falla la validación

- **Decision**: Requests cortadas antes de invocar el método no generan línea: 401/403 de la cadena de filtros, 400 por `@Valid @RequestBody` (`MethodArgumentNotValidException`) o por conversión de tipos (ambos en la resolución de argumentos de MVC, antes de llamar al bean), 404 de ruta inexistente. En cambio, `PlayerController`, `OrderController` y `UserMarketController` tienen `@Validated` a nivel de clase: Spring MVC no aplica su validación de métodos integrada sobre ellos y las restricciones de `@RequestParam`/`@PathVariable` (`@Min`, `@Max`, `@Pattern`) las chequea el proxy AOP de `MethodValidationPostProcessor` durante la invocación. Como el aspecto tiene `@Order(HIGHEST_PRECEDENCE)` queda por fuera de ese interceptor, así que esas requests **sí** generan línea con `outcome=ConstraintViolationException`, y la respuesta sigue siendo el 400 de `GlobalExceptionHandler`.
- **Rationale**: El requerimiento es por invocación de método y debe resolverse con un aspecto sobre el controller; cubrir los casos previos a la invocación exigiría un filtro o interceptor HTTP, que es otro mecanismo y fuera de lo pedido. Queda documentado en la spec (Assumptions y US2 escenarios 4 y 5).
- **Alternatives considered**: `HandlerInterceptor` o filtro adicional (no es aspecto, duplica la lógica).

## R5. Usuario

- **Decision**: `Authentication#getName()` del `SecurityContextHolder`; `anonymous` si no hay autenticación, no está autenticada o es `AnonymousAuthenticationToken`.
- **Rationale**: `JwtAuthFilter` pone el username como principal; `ApiKeyAuthFilter` pone el username del dueño de la key o su prefijo, que es el "nombre de la API key". No hace falta leer headers ni decodificar tokens.

## R6. Representación y saneamiento de parámetros

- **Decision**: `ParameterSanitizer` arma un `ObjectNode` `{nombre: valor}` con `ObjectMapper#valueToTree` (Jackson 3, bean de Boot), recorre el árbol enmascarando claves sensibles y trunca la representación de cada parámetro a 200 caracteres; devuelve el JSON compacto.
- **Rationale**: Reutiliza la serialización que ya define la API (records, enums, `BigDecimal`), permite enmascarar campos anidados por nombre y da un formato estable y legible. Nombres de parámetros con `MethodSignature#getParameterNames()` (el parent de Boot compila con `-parameters`, del que ya dependen los `@PathVariable`/`@RequestParam` sin nombre explícito).
- **Sensibles**: nombre igual o que contenga, sin distinguir mayúsculas, `password` o `token` (cubre `refreshToken`, `accessToken`, `newPassword`). Se aplica tanto al nombre del parámetro (p. ej. el `@CookieValue String refreshToken` de `/auth/refresh` y `/auth/logout`) como a claves de objetos y mapas anidados. Hoy ningún DTO de entrada tiene un campo de negocio que contenga "token"; si apareciera, se enmascararía de más, lo que es seguro.
- **Exclusiones** (se omiten del objeto, no se informan): `ServletRequest`, `ServletResponse`, `HttpSession`, `org.springframework.validation.Errors` (incluye `BindingResult`), `java.security.Principal` (incluye `Authentication`, usado por varios controllers), `WebRequest`, `MultipartFile`, `InputStream`/`OutputStream`/`Reader`/`Writer`.
- **Truncado**: si el JSON de un parámetro supera 200 caracteres, se reemplaza por el string con los primeros 200 caracteres + `…(truncated)`. Al ser por parámetro, acota también listas y DTOs grandes.
- **Robustez**: si serializar un argumento falla, su valor es `"<unserializable:TipoSimple>"`; el sanitizer no lanza.
- **Alternatives considered**: `toString()` de los argumentos (los records exponen `password` en `toString`, imposible enmascarar anidados); enmascarar por regex sobre el texto (frágil).

## R7. Medición y garantía de no romper la request

- **Decision**: `System.nanoTime()` antes de `proceed()`; en `finally` se calcula `durationMs` y se llama a `audit(...)` envuelto en `try/catch (RuntimeException)` que sólo emite `WARN ws_audit_failed` con la operación y el tipo de error. El `catch (Throwable)` del advice sólo registra el tipo y relanza la misma instancia.
- **Rationale**: Una única línea por invocación sea cual sea el desenlace; la excepción original llega intacta a `GlobalExceptionHandler`/`StrategyExceptionHandler`, así que status y cuerpo no cambian (FR-015/FR-016).

## R8. Logging: logger, consola y archivo

- **Decision**: Logger con nombre fijo `WEB_SERVICE_AUDIT`, nivel INFO; `logback-spring.xml` que incluye `defaults.xml` y `console-appender.xml` de Boot, define `AUDIT_FILE` (`RollingFileAppender`, `logs/audit.log`, `TimeBasedRollingPolicy` diaria, `maxHistory=7`, encoder con `${CONSOLE_LOG_PATTERN}`) y lo asocia al logger con `additivity="true"`.
- **Rationale**: Incluir los recursos de Boot conserva `logging.pattern.console` sin moverlo ni tocar `application.properties`; el archivo usa exactamente el mismo JSON; additividad = la línea también va a la consola de siempre. El nivel se gobierna con la propiedad `logging.level.WEB_SERVICE_AUDIT` (en un properties o como argumento `--logging.level.WEB_SERVICE_AUDIT=WARN`), mecanismo estándar de Boot. No se usa variable de entorno: los nombres de logger distinguen mayúsculas y el binding relajado pasa la env var a minúsculas y cambia `_` por `.`, así que `LOGGING_LEVEL_WEB_SERVICE_AUDIT` configuraría el logger `web.service.audit`, no `WEB_SERVICE_AUDIT`.
- **Ruta**: relativa al directorio de trabajo (`backend/` al correr con Maven); `logs/` se agrega al `.gitignore` raíz (`*.log` ya ignora los archivos, pero la carpeta queda explícita).
- **Alternatives considered**: `logging.file.name` (mandaría *todos* los logs al archivo); encoder JSON de terceros (logstash) — dependencia no autorizada y cambiaría el formato.

## R9. Interruptor del aspecto

- **Decision**: `@ConditionalOnProperty(name = "audit.web-services.enabled", havingValue = "true", matchIfMissing = true)` en el aspecto; no se agrega la propiedad a `application.properties`.
- **Rationale**: Pedido en el plan para poder apagar el aspecto completo (p. ej. en un test aislado) sin depender del nivel de log. Sin recompilar: `--audit.web-services.enabled=false` o la variable de entorno `AUDIT_WEBSERVICES_ENABLED=false` (forma canónica: se saca el guion).

## R10. Estrategia de tests

- **Decision**: unitario de `ParameterSanitizer` con un `ObjectMapper` real; integración con `@SpringBootTest` + `@AutoConfigureMockMvc` + `OutputCaptureExtension` (Boot captura `System.out`, por donde escribe el `CONSOLE`).
- **Rationale**: `@WebMvcTest` no carga `@Component` fuera de la capa web ni la cadena de seguridad completa sin imports manuales; el contexto completo ya es el patrón del repo (`HealthEndpointTest`) y prueba el aspecto real sobre controllers reales. Para el JWT se genera un token con el servicio de JWT existente sobre un usuario creado en el test.
