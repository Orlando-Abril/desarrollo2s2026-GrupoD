# Quickstart: Auditoría de invocaciones a web services

## Tests automáticos

Desde `backend/`:

```bash
./mvnw test -Dtest='ParameterSanitizerTest,WebServiceAuditAspectTest'
./mvnw test        # suite completa: controllers sin cambios de comportamiento
```

Esperado: todo verde; `WebServiceAuditAspectTest` verifica una línea por request, enmascarado, usuario, outcome, duración y correlationId.

## Demo manual

1. Levantar el backend desde `backend/` como siempre (`./mvnw spring-boot:run` con la base y Redis de desarrollo). Se crea `backend/logs/audit.log` con la primera invocación.
2. En otra terminal, seguir el archivo de auditoría:

   ```bash
   tail -f backend/logs/audit.log                         # Git Bash / Linux / macOS
   Get-Content backend\logs\audit.log -Wait -Tail 20      # PowerShell
   ```

3. Invocar endpoints y observar una línea por llamada (ver formato en [contracts/audit-log-line.md](contracts/audit-log-line.md)):

   | Acción | Qué mirar en la línea |
   |--------|-----------------------|
   | Login (`POST /auth/login`) | `user=anonymous`, `"password":"***"`, `outcome=OK`; la contraseña no aparece en ningún lado. |
   | `GET /players` con el JWT | `user=<tu usuario>`, `method=GET path=/players`, `params` con los filtros, `durationMs`. |
   | `GET /players/999999` (inexistente) | `outcome=<TipoExcepcion>`; la respuesta sigue siendo el 404 de siempre. |
   | `PUT /strategies/active` con un USER | `outcome=AuthorizationDeniedException`; respuesta 403. |
   | Endpoint de admin con `X-API-KEY` | `user=<dueño o prefijo de la key>`. |
   | `GET /actuator/health` | No genera línea. |

4. Copiar el header `X-Correlation-ID` de una respuesta y buscarlo: `grep <id> backend/logs/audit.log` → la línea de esa request.
5. La consola muestra las mismas líneas con `"logger":"WEB_SERVICE_AUDIT"` y el resto de los logs con el formato JSON de siempre.

## Configuración en la demo

Cualquier cambio de propiedad requiere reiniciar la app, pero no recompilar.

- Silenciar la auditoría sin recompilar, con la propiedad `logging.level.WEB_SERVICE_AUDIT`:

  ```bash
  ./mvnw spring-boot:run -Dspring-boot.run.arguments=--logging.level.WEB_SERVICE_AUDIT=WARN
  ```

  También sirve `logging.level.WEB_SERVICE_AUDIT=WARN` en un `application-local.properties` o el argumento `--logging.level.WEB_SERVICE_AUDIT=WARN` al correr el jar. No usar una variable de entorno: Boot la pasa a minúsculas y configuraría otro logger (ver research.md R8).
- Desactivar el aspecto sin recompilar: variable de entorno `AUDIT_WEBSERVICES_ENABLED=false` (forma canónica de `audit.web-services.enabled`, sin el guion) o el argumento `--audit.web-services.enabled=false`.
- Rotación: a medianoche `audit.log` pasa a `audit.yyyy-MM-dd.log`; se conservan 7 días.
