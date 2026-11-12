# Data Model: Auditoría de invocaciones a web services

No hay entidades persistentes. Los modelos son valores en memoria que terminan en una línea de log.

## WebServiceAuditRecord (lógico, una línea por invocación)

| Campo | Origen | Regla |
|-------|--------|-------|
| `timestamp` | Patrón de consola (`%d{…XXX}`) | Lo pone Logback al emitir; ISO-8601 con offset. |
| `correlationId` | MDC `correlationId` (`CorrelationIdFilter`) | Lo pone el patrón; vacío si no hay. |
| `user` | `SecurityContextHolder` | `getName()`; `anonymous` si null / no autenticada / anónima. |
| `method` | `HttpServletRequest#getMethod()` | `-` si no hay request en curso. |
| `path` | `HttpServletRequest#getRequestURI()` | Sin query string; `-` si no hay request. |
| `operation` | `MethodSignature#toShortString()` | Ej. `PlayerController.list(..)`. |
| `params` | `ParameterSanitizer` | JSON compacto; ver abajo. `{}` si no hay parámetros auditables. |
| `durationMs` | `System.nanoTime()` | Entero ≥ 0, desde antes de `proceed()` hasta su fin (normal o excepción). |
| `outcome` | Desenlace de `proceed()` | `OK` o `getClass().getSimpleName()` de la excepción. Nunca mensaje ni stack trace. |

No se guarda ni se retiene en memoria: se emite y se descarta.

## SanitizedParameters (salida de `ParameterSanitizer`)

Objeto JSON `{ "<nombreParametro>": <valor> }` en el orden de la firma del método.

Reglas, en orden:

1. **Exclusión**: el argumento se omite si es instancia de `ServletRequest`, `ServletResponse`, `HttpSession`, `Errors` (incluye `BindingResult`), `Principal` (incluye `Authentication`), `WebRequest`, `MultipartFile`, `InputStream`, `OutputStream`, `Reader` o `Writer`.
2. **Enmascarado por nombre de parámetro**: si el nombre, en minúsculas, contiene `password` o `token`, el valor es `"***"` (aunque sea `null`).
3. **Serialización**: `null` → `null`; otro valor → árbol JSON con el `ObjectMapper` de la aplicación. Si falla → `"<unserializable:TipoSimple>"`.
4. **Enmascarado anidado**: en todo objeto del árbol (a cualquier profundidad, dentro de listas y mapas), cada campo cuyo nombre en minúsculas contiene `password` o `token` pasa a `"***"`.
5. **Truncado**: si el JSON compacto del valor supera 200 caracteres, se reemplaza por el string `<primeros 200 caracteres>…(truncated)`.

Ejemplos:

| Invocación | `params` |
|------------|----------|
| `POST /auth/login` con `{"username":"ana","password":"S3creta!"}` | `{"request":{"username":"ana","password":"***"}}` |
| `POST /auth/refresh` con cookie de renovación | `{"refreshToken":"***"}` |
| `GET /orders/mine` (sólo `Authentication`) | `{}` |
| `GET /players/42` | `{"id":42}` |

## Configuración

| Clave | Default | Efecto |
|-------|---------|--------|
| `audit.web-services.enabled` | `true` (en código) | `false` no registra el aspecto. |
| `logging.level.WEB_SERVICE_AUDIT` | hereda root (INFO) | `WARN` o superior silencia las líneas. |
| Archivo | `logs/audit.log` | Rotación diaria `logs/audit.yyyy-MM-dd.log`, 7 días. |
| Truncado | 200 caracteres por parámetro | Constante en `ParameterSanitizer`. |
