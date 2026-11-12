# Contract: línea de auditoría de web services

La feature no expone endpoints nuevos. Su interfaz externa es la línea de log, que consumen operadores y herramientas de búsqueda.

## Logger

- Nombre: `WEB_SERVICE_AUDIT`
- Nivel de emisión: `INFO`
- Destinos: appender `CONSOLE` (por additividad) y `AUDIT_FILE` (`logs/audit.log`).

## Formato

Envoltorio: el mismo JSON de consola que el resto de la aplicación (definido por `logging.pattern.console`):

```json
{"timestamp":"<ISO-8601>","level":"INFO","logger":"WEB_SERVICE_AUDIT","correlationId":"<id>","message":"<mensaje>"}
```

`message`, con el estilo `clave=valor` de los logs actuales, en este orden fijo y separado por un espacio:

```text
ws_audit user=<user> method=<HTTP> path=<uri> operation=<Clase.metodo(..)> params=<json> durationMs=<n> outcome=<OK|TipoExcepcion>
```

- El prefijo `ws_audit` identifica la línea para búsquedas (`grep ws_audit`).
- `params` es JSON compacto (ver [data-model.md](../data-model.md)); dentro de `message` sus comillas salen sin escapar.
- Ningún campo contiene mensajes de excepción, stack traces, contraseñas ni tokens.

Ejemplo (consola y archivo), tal como sale:

```text
{"timestamp":"2026-11-12T10:15:03.120-03:00","level":"INFO","logger":"WEB_SERVICE_AUDIT","correlationId":"6f1c…","message":"ws_audit user=anonymous method=POST path=/auth/login operation=AuthController.login(..) params={"request":{"username":"ana","password":"***"}} durationMs=87 outcome=OK"}
```

> **Nota:** el patrón de consola heredado de Entrega 1 no escapa las comillas del mensaje, así que la línea no es JSON estricto; se mantiene por FR-014 y queda como problema conocido.

## Falla de la auditoría

Si armar o escribir la línea falla, en lugar de la línea se emite (mismo logger, nivel `WARN`):

```text
ws_audit_failed operation=<Clase.metodo(..)> error=<TipoExcepcion>
```

La respuesta del endpoint no cambia.

## Garantías

- Exactamente una línea `ws_audit` (o una `ws_audit_failed`) por invocación de método de `@RestController` de `com.example.demo.controller`.
- Ninguna línea para `/actuator/**` ni para requests que no llegan al método.
