# Contract: Actuator monitoring

Base URL local: `http://localhost:8080`. Todos los endpoints son `GET`. `X-API-KEY` admite una clave con rol ADMIN; JWT ADMIN también puede usar los endpoints protegidos.

Cada solicitud autenticada de monitoreo usa **un solo mecanismo**: `Authorization: Bearer <token>` o `X-API-KEY`. Health e info también admiten solicitudes anónimas. Un header `Authorization` con otro esquema o una solicitud con ambos mecanismos queda fuera de esta feature y mantiene el comportamiento previo de la cadena de filtros.

| Ruta | Anónimo | USER | ADMIN / API key ADMIN | Contenido esperado |
|---|---:|---:|---:|---|
| `/actuator/health` | 200 si UP | 200 si UP | 200 si UP | `status`; componentes `db`, `redis`, `diskSpace` sólo para ADMIN. Estado no UP puede devolver 503. |
| `/actuator/health/liveness` | 200 si UP | 200 si UP | 200 si UP | Estado de vida; sin detalles para no ADMIN. |
| `/actuator/health/readiness` | 200 si UP | 200 si UP | 200 si UP | Estado de preparación; sin detalles para no ADMIN. |
| `/actuator/info` | 200 | 200 | 200 | `build.name`, `build.version`, `build.time`. |
| `/actuator/metrics` y `/actuator/metrics/http.server.requests` | 401 | 403 | 200 | Nombres de métricas y mediciones HTTP por etiquetas. |
| `/actuator/prometheus` | 401 | 403 | 200 | Formato de texto Prometheus; familias `http_server_requests_seconds_*`; etiqueta `application="demo"` en las series. |

En health y sus subrutas, la falta de credenciales deja el acceso público. Un JWT o `X-API-KEY` válidos establecen el rol para decidir los detalles visibles; USER no obtiene detalles, ADMIN sí. `Authorization: Bearer <token>` individual con token inválido o vencido responde 401; un `X-API-KEY` individual, no vacío, inexistente o inactivo también responde 401. Todos los patrones de las demás rutas públicas conservan su comportamiento anterior.

Las rutas no incluidas en `health,info,metrics,prometheus` no se exponen como endpoints Actuator. En particular, `/actuator/env`, `/actuator/beans` y `/actuator/heapdump` no devuelven su información aunque la solicitud esté autenticada como ADMIN.

## Contrato Swagger UI

El grupo Actuator de Swagger UI incluye las cuatro familias expuestas: health (con liveness/readiness), info, metrics y prometheus. `components.securitySchemes` contiene `bearerAuth` para JWT y `apiKeyAuth` para el header `X-API-KEY`. Las operaciones de metrics y prometheus declaran **dos requisitos alternativos** (OR de OpenAPI): uno con `bearerAuth` y otro con `apiKeyAuth`; ninguno exige ambos a la vez. Health e info no declaran un requisito obligatorio de seguridad, aunque health acepta credenciales opcionales para mostrar detalles ADMIN. La documentación no agrega endpoints a `management.endpoints.web.exposure.include`.

La descripción OpenAPI debe representar las respuestas que el servicio expone:

| Familia | Códigos documentados | Esquema de respuesta requerido |
|---|---|---|
| Health, liveness y readiness | 200, 401 y 503 | JSON con `status` para 200/503; `components` es visible sólo para ADMIN cuando corresponde. Un `Bearer` o una API key inválidos, según la definición de esta feature, reciben JSON con `error` y `message` en 401. |
| Info | 200 | JSON con `build.name`, `build.version` y `build.time`. |
| Metrics | 200, 401 y 403 | JSON de nombres/mediciones en 200; JSON con `error` y `message` para los 401/403 producidos por los filtros y el handler existentes. |
| Prometheus | 200, 401 y 403 | Texto Prometheus en 200; JSON con `error` y `message` para los 401/403 producidos por los filtros y el handler existentes. |

No se inventan cuerpos de error si un caso de la cadena de seguridad existente no los entrega. La prueba del grupo Actuator verifica los códigos y los esquemas o tipos de contenido que correspondan a cada operación.

## Ejemplos mínimos de respuesta

Health anónimo con dependencias sanas:

```json
{"status":"UP"}
```

Info:

```json
{"build":{"name":"demo","version":"0.0.1-SNAPSHOT","time":"<fecha de build>"}}
```

Prometheus: los nombres de timer se transforman al formato Prometheus; comprobar al menos una serie de `http_server_requests_seconds_count` y buckets `http_server_requests_seconds_bucket` tras generar tráfico, con `application="demo"`. Los cuantiles explícitos del timer se verifican según la salida efectiva del registro.
