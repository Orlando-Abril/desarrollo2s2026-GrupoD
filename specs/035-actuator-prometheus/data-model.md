# Data Model: Monitoreo con Actuator y Prometheus

No se crean tablas ni entidades de dominio. Los datos son estados y mediciones efímeras del proceso.

| Concepto | Campos observables | Origen y reglas |
|---|---|---|
| Estado de salud | `status`; componentes `db`, `redis`, `diskSpace` para ADMIN | Salud general pública sin detalles. Liveness y readiness públicos; readiness usa el estado de preparación del proceso. |
| Identidad del build | `name`, `version`, `time` dentro de `build` | Metadatos del artefacto producido por Maven; no se calculan a partir de la hora de arranque. |
| Medición HTTP | ruta, método, estado, resultado, duración, conteo, buckets y cuantiles | `http.server.requests`; la ruta usa la etiqueta normalizada del framework cuando está disponible. Las series exportadas llevan `application=demo`. |
| Identidad de scrape | API key con rol ADMIN | Se lee de un secreto de Compose suministrado desde `SCRAPE_API_KEY` del host; no se persiste como parte de esta feature. |

## Relaciones y validación

- Cada medición HTTP pertenece a una instancia de aplicación identificada por la etiqueta `application`.
- Los estados de componentes se agregan al estado general; sólo se muestran sus detalles a ADMIN.
- Una API key asociada a un usuario hereda su rol. La clave de scrape debe autenticar como ADMIN.
- No hay transiciones de estado persistidas. Liveness y readiness cambian según el estado operativo del proceso; las mediciones se acumulan durante la vida de la instancia.
