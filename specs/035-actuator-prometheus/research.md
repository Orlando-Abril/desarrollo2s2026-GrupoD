# Research: Monitoreo con Actuator y Prometheus

## R1. Registro Prometheus y metadatos del build

**Decision**: Agregar `io.micrometer:micrometer-registry-prometheus` sin versión explícita para usar el BOM de Spring Boot 4.1.1. Agregar el goal `build-info` al `spring-boot-maven-plugin` y habilitar `management.info.build.enabled=true`.

**Rationale**: Boot configura el endpoint Prometheus cuando el registro correspondiente está presente y el goal genera `META-INF/build-info.properties` con nombre, versión y fecha de build. Las propiedades de Actuator solicitadas están documentadas por Boot. [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/metrics.html), [Build Info](https://docs.spring.io/spring-boot/maven-plugin/build-info.html), [Propiedades](https://docs.spring.io/spring-boot/appendix/application-properties/index.html).

**Alternatives considered**: Codificar la versión de Micrometer o la fecha en properties. Se descartan para evitar divergencia respecto del build real.

## R2. Salud, probes y control de acceso

**Decision**: Usar `management.endpoint.health.show-details=when-authorized`, `management.endpoint.health.roles=ADMIN` y `management.endpoint.health.probes.enabled=true`. Mantener `/actuator/health` y agregar `/actuator/health/**` y `/actuator/info` a las rutas públicas; exigir `hasRole("ADMIN")` en el resto de `/actuator/**` antes de `anyRequest()`.

**Rationale**: El valor `show-components` hereda `show-details` si no se configura. Los probes se publican como grupos de health. Readiness refleja el estado de preparación y no agrega base de datos o Redis por defecto; el escenario del spec se ajustó a esa semántica. [Spring Boot Actuator endpoints](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html), [Propiedades](https://docs.spring.io/spring-boot/appendix/application-properties/index.html).

**Alternatives considered**: Agregar DB y Redis al grupo readiness. Se descarta porque el usuario pidió habilitar probes, no ampliar su criterio ni hacer que la app deje de anunciarse como lista por esas dependencias.

## R3. Histogramas y percentiles HTTP

**Decision**: Aplicar `management.metrics.distribution.percentiles-histogram.http.server.requests=true` y añadir `management.metrics.distribution.percentiles.http.server.requests=0.5,0.95,0.99`. Etiquetar todas las series con `management.metrics.tags.application=${spring.application.name}`.

**Rationale**: El histograma publica buckets para agregación y `histogram_quantile`, pero no emite por sí solo cuantiles locales explícitos. La propiedad adicional satisface literalmente “percentiles e histograma”; la tasa de errores se calcula a partir de conteos agrupados por ruta y estado. [Spring Boot metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html), [Micrometer histograms and percentiles](https://docs.micrometer.io/micrometer/reference/concepts/histogram-quantiles.html).

**Alternatives considered**: Sólo histograma y percentiles calculados en Prometheus. Es válido para análisis agregado, pero no ofrece percentiles explícitos en la métrica de la aplicación.

## R4. Scrape autenticado y secreto

**Decision**: Fijar `prom/prometheus:v3.7.0` y usar `scrape_configs[].http_headers.X-API-KEY.files` para leer el valor de `/run/secrets/scrape_api_key`. Declarar en Compose un secreto `scrape_api_key` cuyo origen es la variable de entorno `SCRAPE_API_KEY` del host. No copiar la clave a archivos versionados. Configurar `host.docker.internal:8080` y `scrape_interval: 15s`.

**Rationale**: Prometheus 3.7 admite `http_headers` con `files`; Docker Compose admite secretos desde variables de entorno. La sustitución de variables de Compose no ocurre dentro de `prometheus.yml`, por lo que el archivo secreto resuelve el problema sin plantilla generada. [Prometheus 3.7 configuration](https://prometheus.io/docs/prometheus/3.7/configuration/configuration/), [Compose secrets](https://docs.docker.com/reference/compose-file/secrets/), [Prometheus v3.7.0](https://github.com/prometheus/prometheus/releases/tag/v3.7.0).

**Alternatives considered**: Interpolación directa `${SCRAPE_API_KEY}` en `prometheus.yml`, que Prometheus no realiza; bearer token, que no coincide con `X-API-KEY`; clave escrita en YAML, que filtraría el secreto. No se requiere fallback de versión porque la elegida admite `http_headers`.

## R5. Grafana y alcance de la API key

**Decision**: Fijar `grafana/grafana:12.2.0` y provisionar un datasource Prometheus apuntando al servicio `prometheus` dentro de Compose. Para scrape usar una API key cuyo principal tenga `ROLE_ADMIN`.

**Rationale**: Grafana admite provisioning por archivo. El filtro actual de la aplicación asigna el rol del dueño a una API key asociada a un usuario y `ROLE_ADMIN` a una clave de servicio sin dueño. Por eso no toda clave válida permite el scrape; las pruebas deben usar una clave ADMIN. [Grafana provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/), [Grafana v12.2.0](https://github.com/grafana/grafana/releases/tag/v12.2.0), `backend/src/main/java/com/example/demo/security/ApiKeyAuthFilter.java`.

**Alternatives considered**: Tratar toda API key como ADMIN. Contradice el filtro actual y ampliaría permisos fuera del alcance autorizado.

## R6. Swagger UI y provisión reproducible de la clave de demo

**Decision**: Activar `springdoc.show-actuator=true`, que agrega un grupo Actuator a Swagger UI, y comprobar en una prueba que las cuatro familias aparecen, sus códigos y esquemas de respuesta están documentados y las operaciones protegidas declaran `bearerAuth` o `apiKeyAuth` como alternativas de OpenAPI. Ambos esquemas ya existen en `OpenApiConfig.java`; sólo si el grupo carece de esta documentación se incorporará un `GroupedOpenApi` nuevo y acotado. Para la demo local, generar una clave aleatoria, guardar únicamente su SHA-256 y prefijo en una fila `api_keys` sin dueño, y conservar el valor crudo sólo en `SCRAPE_API_KEY` de la sesión.

## R7. Autenticación opcional en health

**Decision**: Mantener health en `PUBLIC_ROUTES` para permitir anónimos y modificar únicamente `shouldNotFilter` de `JwtAuthFilter.java` y `ApiKeyAuthFilter.java`: en health y subrutas, procesar sólo `Authorization: Bearer <token>` o `X-API-KEY` no vacío, uno por solicitud. Un `Bearer` individual con token inválido/vencido o una API key individual, no vacía, inexistente/inactiva reciben 401 mediante la lógica existente. Otros esquemas `Authorization` y solicitudes con ambos mecanismos permanecen fuera del alcance y con la conducta previa. En cualquier otra ruta pública se conserva la omisión actual.

**Rationale**: Ambos filtros omiten actualmente cualquier ruta de `PUBLIC_ROUTES`, incluso cuando llega una credencial ADMIN; por eso `show-details=when-authorized` no puede reconocer el rol. La excepción limitada a health permite roles sin convertir el endpoint público en obligatorio de autenticar. `backend/src/main/java/com/example/demo/security/JwtAuthFilter.java`, `backend/src/main/java/com/example/demo/security/ApiKeyAuthFilter.java`, `backend/src/main/java/com/example/demo/config/SecurityConfig.java`.

**Alternatives considered**: Quitar health de `PUBLIC_ROUTES` rechazaría anónimos en `ApiKeyAuthFilter`; cambiar `doFilterInternal` o la lógica de rutas ajenas a health excedería el alcance. Las pruebas de health deben usar JWT/API keys reales por la cadena de filtros, nunca inyección simulada de un principal; una prueba separada comprueba la omisión para todos los patrones públicos existentes.

**Rationale**: springdoc documenta `show-actuator` para mostrar los endpoints en un grupo separado. El filtro existente da `ROLE_ADMIN` a una clave sin dueño; el registro público crea claves asociadas a usuarios que conservan su rol, por lo que no es un camino fiable para obtener una clave ADMIN. [Springdoc Actuator support](https://springdoc.org/), `backend/src/main/java/com/example/demo/config/OpenApiConfig.java`, `backend/src/main/java/com/example/demo/security/ApiKeyAuthFilter.java`.

**Alternatives considered**: Modificar `OpenApiConfig.java`, ampliar la exposición Actuator a `openapi`/`swagger-ui` o cambiar roles de claves existentes. Se descartan por exceder el alcance mínimo autorizado o modificar permisos de usuarios.
