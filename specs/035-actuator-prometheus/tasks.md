---

description: "Tareas para implementar monitoreo con Actuator y Prometheus"
---

# Tasks: Monitoreo con Actuator y Prometheus

**Input**: `specs/035-actuator-prometheus/spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/actuator-monitoring.md` y `quickstart.md`.

**Tests**: Solicitados expresamente para health, info, seguridad y formato Prometheus.

**Organization**: Una fase por historia. Las tareas con `[P]` afectan archivos distintos y pueden ejecutarse en paralelo después de sus prerrequisitos.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: tarea ejecutable en paralelo con otras marcadas `[P]` de la misma fase.
- **[Story]**: historia correspondiente del spec.
- Todas las rutas son relativas a la raíz del repositorio.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirmar el estado inicial y los límites de edición antes de modificar infraestructura existente.

- [X] T001 Revisar `backend/pom.xml`, `backend/src/main/resources/application.properties`, `backend/src/main/java/com/example/demo/config/SecurityConfig.java`, `backend/src/test/java/com/example/demo/config/HealthEndpointTest.java` y `README.md`; comprobar que los cambios posteriores respeten las autorizaciones de `specs/035-actuator-prometheus/spec.md`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Habilitar los cuatro endpoints y sus reglas de acceso compartidas. Esta fase precede a todas las historias.

- [X] T002 [P] Agregar `io.micrometer:micrometer-registry-prometheus` sin versión explícita y el goal `build-info` de `spring-boot-maven-plugin` en `backend/pom.xml`; fijar el nombre del proyecto en `demo` para que `build.name` no quede vacío, y conservar la dependencia Actuator existente.
- [X] T003 [P] Cambiar sólo propiedades `management.*` y agregar `springdoc.show-actuator=true` en `backend/src/main/resources/application.properties` para exponer exactamente `health,info,metrics,prometheus` y mostrar su grupo en Swagger UI; conservar `management.health.redis.enabled=true`.
- [X] T004 [P] Ajustar únicamente reglas de `/actuator/**` en `backend/src/main/java/com/example/demo/config/SecurityConfig.java`: conservar `/actuator/health`, añadir `/actuator/health/**` y `/actuator/info` a `PUBLIC_ROUTES`, y exigir `hasRole("ADMIN")` para `/actuator/**` antes de `anyRequest()`; no alterar el resto de rutas.

**Checkpoint**: Registro y metadatos disponibles; seguridad base aplicada. Cada historia puede completar su propio contrato sin modificar otras capas.

---

## Phase 3: User Story 1 - Consultar estado y disponibilidad (Priority: P1) 🎯 MVP

**Goal**: Health, liveness y readiness públicos; componentes `db`, `redis` y `diskSpace` visibles sólo para ADMIN.

**Independent Test**: Consultar las tres rutas sin credenciales y comprobar `status` sin `components`; consultar health con JWT/API keys reales de USER y ADMIN, comprobar visibilidad por rol y verificar 401 para credenciales inválidas sin cambiar otras rutas públicas.

### Tests for User Story 1

- [X] T005 [US1] Actualizar `backend/src/test/java/com/example/demo/config/HealthEndpointTest.java` para pasar JWT y API keys reales por `JwtAuthFilter` y `ApiKeyAuthFilter`, sin `@WithMockUser` ni autenticación simulada y con una sola credencial por solicitud: verificar 200 anónimo en health/liveness/readiness sin `components`, detalles `db`, `redis`, `diskSpace` sólo con JWT/API key ADMIN, USER sin detalles, `Authorization: Bearer <token>` inválido o vencido con 401 y `X-API-KEY` no vacío inexistente o inactivo con 401; hacer que el indicador Redis de la prueba informe UP sin Redis externo y sobreescribir `management.endpoint.health.show-details` en la clase porque `backend/src/test/resources/application.properties` fija `never`.
- [X] T006 [US1] Crear `backend/src/test/java/com/example/demo/security/PublicRouteFilterTest.java` con casos parametrizados para **todos** los patrones de `SecurityConfig.PUBLIC_ROUTES` ajenos a health (`/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `/swagger-ui/**`, `/v3/api-docs/**`, `/error` y el nuevo `/actuator/info`): comprobar que `shouldNotFilter` de ambos filtros conserva la omisión previa, con un único header de credencial por solicitud de prueba; comprobar aparte que health/subrutas omiten ambos filtros sin header y procesan el filtro correspondiente con su único header. No usar `@WithMockUser` ni simular autenticación.

### Implementation for User Story 1

- [X] T007 [US1] Modificar únicamente `shouldNotFilter` en `backend/src/main/java/com/example/demo/security/JwtAuthFilter.java` para que `/actuator/health` y sus subrutas procesen `Authorization: Bearer <token>` cuando está presente y se omitan sin ese header; conservar intacto `doFilterInternal`, otros esquemas `Authorization` y todas las demás rutas públicas.
- [X] T008 [US1] Modificar únicamente `shouldNotFilter` en `backend/src/main/java/com/example/demo/security/ApiKeyAuthFilter.java` para que `/actuator/health` y sus subrutas procesen `X-API-KEY` cuando su valor no está vacío y se omitan sin ese valor; conservar intacto `doFilterInternal`, solicitudes con ambos mecanismos y todas las demás rutas públicas.
- [X] T009 [US1] Configurar `management.endpoint.health.show-details=when-authorized`, `management.endpoint.health.roles=ADMIN` y `management.endpoint.health.probes.enabled=true` en `backend/src/main/resources/application.properties`; no cambiar propiedades de logging ni grupos de readiness.

**Checkpoint**: US1 se prueba con credenciales reales y sin que estén configurados aún info, histogramas o la demo.

---

## Phase 4: User Story 2 - Identificar el build desplegado (Priority: P2)

**Goal**: `/actuator/info` público con nombre, versión y fecha del build real.

**Independent Test**: Consultar info sin credenciales y verificar `build.name`, `build.version` y `build.time` tras compilar con Maven.

### Tests for User Story 2

- [X] T010 [US2] Crear `backend/src/test/java/com/example/demo/config/InfoEndpointTest.java` para comprobar 200 anónimo y campos no vacíos `build.name`, `build.version` y `build.time`; verificar que la versión coincide con el artefacto Maven `0.0.1-SNAPSHOT`.

### Implementation for User Story 2

- [X] T011 [US2] Añadir `management.info.build.enabled=true` en `backend/src/main/resources/application.properties`; verificar que el goal de T002 genera `META-INF/build-info.properties` mediante `backend/pom.xml`.

**Checkpoint**: US2 funciona sin la configuración de métricas HTTP ni los contenedores de demo.

---

## Phase 5: User Story 3 - Analizar latencia y errores por endpoint (Priority: P2)

**Goal**: Metrics y Prometheus sólo para ADMIN; series HTTP con etiqueta `application`, percentiles, buckets y conteos por ruta/resultado.

**Independent Test**: Generar solicitudes con éxito y error; verificar 401 anónimo, 403 USER, 200 con JWT/API key ADMIN, `http_server_requests_seconds_*` y `application="demo"` en la respuesta Prometheus.

### Tests for User Story 3

- [X] T012 [US3] Crear `backend/src/test/java/com/example/demo/config/ActuatorMonitoringTest.java` con pruebas de `/actuator/metrics`, `/actuator/metrics/http.server.requests` y `/actuator/prometheus`: 401 sin credenciales, 403 con USER, 200 con ADMIN y API key ADMIN activa; tras solicitudes exitosas y fallidas a una misma ruta, afirmar conteos separados por etiqueta de ruta y estado/resultado, `http_server_requests_seconds_count`, buckets y percentiles, y comprobar que **cada serie de muestra** del texto Prometheus lleva `application="demo"`.
- [X] T013 [US3] Ampliar `backend/src/test/java/com/example/demo/config/ActuatorMonitoringTest.java` para verificar que `/actuator/env`, `/actuator/beans` y `/actuator/heapdump` no exponen datos incluso con ADMIN, y que una API key USER no obtiene métricas.

### Implementation for User Story 3

- [X] T014 [US3] Añadir en `backend/src/main/resources/application.properties` `management.metrics.tags.application=${spring.application.name}`, `management.metrics.distribution.percentiles-histogram.http.server.requests=true` y `management.metrics.distribution.percentiles.http.server.requests=0.5,0.95,0.99`; comprobar etiquetas de ruta/estado sin agregar instrumentación de negocio.

**Checkpoint**: US3 se valida con MockMvc sin Prometheus ni Grafana activos.

---

## Phase 6: User Story 4 - Demostrar recolección y visualización local (Priority: P3)

**Goal**: Prometheus recolecta el backend cada 15 s con clave ADMIN; Grafana tiene el datasource disponible; el backend arranca sin ambos.

**Independent Test**: Ejecutar Compose con `SCRAPE_API_KEY` ADMIN, observar objetivo `UP` y datasource provisionado; detener Compose y volver a consultar la aplicación.

### Implementation for User Story 4

- [X] T015 [P] [US4] Crear `observability/prometheus.yml` con scrape de `host.docker.internal:8080/actuator/prometheus` cada 15 s y `http_headers.X-API-KEY.files` leyendo `/run/secrets/scrape_api_key`; no incluir la clave literal ni interpolación de entorno en este YAML.
- [X] T016 [P] [US4] Crear `observability/grafana/provisioning/datasources/prometheus.yml` con un datasource Prometheus que use la URL interna del servicio `prometheus:9090`.
- [X] T017 [US4] Crear `observability/docker-compose.yml` con `prom/prometheus:v3.7.0` y `grafana/grafana:12.2.0`, volúmenes de configuración, puertos locales, `host.docker.internal:host-gateway` y secreto `scrape_api_key` originado en `SCRAPE_API_KEY` del host y montado sólo en Prometheus; no definir dependencia del backend hacia estos servicios.
- [X] T018 [US4] Agregar sección Monitoreo a `README.md` con prerrequisitos y el procedimiento reproducible de `specs/035-actuator-prometheus/quickstart.md` para generar una clave de servicio ADMIN sólo en PostgreSQL local, guardar su hash y prefijo, probar 200 en `/actuator/prometheus`, usar `SCRAPE_API_KEY` sin guardarla en Git, ejecutar `docker compose -f observability/docker-compose.yml up -d` y `down`, abrir las URLs de Prometheus/Grafana y detener la demo sin afectar el backend.

**Checkpoint**: US4 está listo cuando la demo recolecta y visualiza métricas y el backend sigue iniciando sin la demo.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Validación integral del contrato y del límite de cambios.

- [X] T019 Crear `backend/src/test/java/com/example/demo/config/ActuatorOpenApiTest.java` para verificar en `/v3/api-docs/actuator` las cuatro familias de endpoints, `components.securitySchemes.bearerAuth` para JWT y `apiKeyAuth` con `X-API-KEY`, dos requisitos de seguridad separados (OR) en metrics/prometheus y ningún requisito obligatorio en health/info; comprobar códigos y esquemas/tipos de respuesta según `specs/035-actuator-prometheus/contracts/actuator-monitoring.md` (health/probes 200/401/503 con JSON `status` o `error`/`message`, info 200 con `build`, metrics 200/401/403 con JSON, prometheus 200 en texto y 401/403 en JSON) y que Swagger UI ofrece el grupo Actuator.
- [X] T020 Si T019 detecta que faltan esquemas `bearerAuth`/`apiKeyAuth`, requisitos alternativos, códigos o esquemas/tipos de respuesta, crear únicamente `backend/src/main/java/com/example/demo/config/ActuatorOpenApiConfig.java` con un `GroupedOpenApi` y `OpenApiCustomizer` acotados a `/actuator/**` para completar lo faltante sin inventar cuerpos de error; no tocar `OpenApiConfig.java` ni añadir exposición Actuator adicional. Si T019 ya pasa, registrar que no se necesita este archivo.
- [X] T021 Ejecutar las pruebas de `backend/src/test/java/com/example/demo/config/HealthEndpointTest.java`, `backend/src/test/java/com/example/demo/security/PublicRouteFilterTest.java`, `InfoEndpointTest.java`, `ActuatorMonitoringTest.java` y `ActuatorOpenApiTest.java` con `backend/mvnw.cmd`, y después la suite de backend; corregir sólo fallas introducidas por esta feature en archivos autorizados.
- [ ] T022 Validar `observability/docker-compose.yml` con `docker compose config --quiet` usando `SCRAPE_API_KEY` local; seguir `specs/035-actuator-prometheus/quickstart.md` para verificar scrape, datasource y arranque del backend sin la demo.
- [X] T023 Revisar `backend/src/main/resources/application.properties`, `backend/src/main/java/com/example/demo/config/SecurityConfig.java`, `backend/src/main/java/com/example/demo/security/JwtAuthFilter.java`, `backend/src/main/java/com/example/demo/security/ApiKeyAuthFilter.java`, `backend/pom.xml`, `README.md`, `observability/` y el posible `backend/src/main/java/com/example/demo/config/ActuatorOpenApiConfig.java` contra `specs/035-actuator-prometheus/spec.md`: sólo cuatro familias Actuator expuestas, acceso por rol correcto, otras rutas públicas intactas, Swagger UI documentado, logs JSON/correlation ID conservados, ningún secreto versionado y Constitution sin cambios.
- [ ] T024 TAREA FINAL MANUAL: sólo después de que el usuario confirme los cambios, publique la rama y abra la PR hacia `main`, verificar `SUCCESS` de GitHub Actions en `.github/workflows/ci.yml` y Quality Gate SonarCloud `PASSED` con menos de 10 issues menores; registrar enlaces y resultados en `specs/035-actuator-prometheus/tasks.md`. `$speckit-implement` no hace commit, push ni abre la PR, y deja esta tarea pendiente hasta que el usuario complete esos pasos.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Sin dependencias.
- **Foundational (Phase 2)**: Después de T001; T002, T003 y T004 pueden avanzar en paralelo porque afectan archivos distintos.
- **US1 (Phase 3)**: Después de Phase 2; T005 y T006 preceden a T007 y T008; T009 se aplica después de ambos filtros.
- **US2 (Phase 4)**: Después de Phase 2; T010 precede a T011.
- **US3 (Phase 5)**: Después de Phase 2; T012 y T013 preceden a T014.
- **US4 (Phase 6)**: Requiere US3 para un scrape útil; T015 y T016 pueden avanzar en paralelo, T017 los integra, T018 documenta el resultado.
- **Polish (Phase 7)**: Después de US1–US4; T019 comprueba Swagger y T020 sólo incorpora configuración si esa prueba revela una falta; después se ejecutan T021–T023. T024 es exclusivamente manual y depende de confirmación, publicación y PR hechos por el usuario.

### User Story Dependencies

- **US1 (P1)**: Sólo base común; MVP independiente.
- **US2 (P2)**: Sólo base común; independiente de US1.
- **US3 (P2)**: Sólo base común; independiente de US1 y US2 en comportamiento, aunque comparte `application.properties`.
- **US4 (P3)**: Depende del endpoint Prometheus y su autorización de US3; no modifica el backend.

### Within Each User Story

- Escribir pruebas solicitadas antes de implementar cada historia y observar su fallo esperado.
- Al editar `backend/src/main/resources/application.properties` en US1, US2 y US3, aplicar cambios secuencialmente para evitar conflictos de archivo.
- Validar cada checkpoint antes de pasar a una historia que dependa de ella.

### Parallel Opportunities

- T002, T003 y T004 en archivos distintos tras T001.
- T005 (health), T006 (regresión de rutas públicas) y T010 (info) usan archivos de prueba distintos una vez completada la base; T007 y T008 afectan filtros diferentes pero siguen a T005 y T006; T012/T013 comparten archivo entre sí y se ejecutan en orden.
- T015 y T016 en archivos de configuración independientes antes de T017.

## Parallel Example: User Story 4

```text
Task T015: observability/prometheus.yml — scrape y lectura de secreto.
Task T016: observability/grafana/provisioning/datasources/prometheus.yml — datasource.
Después T017: observability/docker-compose.yml — integración de ambos servicios.
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar T001–T004.
2. Completar T005–T009 y ejecutar `HealthEndpointTest` con credenciales reales y `PublicRouteFilterTest` para todos los patrones públicos.
3. Confirmar health, liveness y readiness públicos, con detalles sólo para ADMIN.

### Incremental Delivery

1. Agregar US2 para identificar el build.
2. Agregar US3 para métricas protegidas y latencia/errores por endpoint.
3. Agregar US4 para la demo opcional y documentación.
4. Ejecutar T019–T023 para verificar Swagger, el contrato completo y los límites de edición. T024 queda como gate manual posterior a confirmación, publicación y PR del usuario.

## Notes

- Validación local 2026-11-14: las cinco clases de prueba de monitoreo pasan juntas (23 casos). `docker compose config --quiet` pasa con una variable temporal. T022 sigue pendiente porque Docker Desktop no ofrece daemon y `psql` no está disponible en esta sesión; faltan scrape, datasource y arranque real de demo.
- T021 ejecutada el 2026-11-14: con autorización adicional se ajustó sólo el caso `/actuator/health` de `JwtAuthFilterTest` para esperar 401 y JSON de error. Esa clase y las cinco clases de monitoreo pasan juntas (39 casos: 16 + 23). La suite de backend sin grupo E2E destructivo, con `-Dfile.encoding=UTF-8`, ejecutó 951 casos: 2 fallas, 0 errores y 2 omitidos. Fallan sólo `PlayerStatsServiceTest.knownEquivalenceMatchesTeamsWhoseNamesDoNotResemble` y `PlayerStatsServiceTest.leagueFailureLogIncludesWhatTheSourceAnswered`: `CapturedOutput` contiene `Borussia M�nchengladbach` y `respondi�`, mientras las aserciones esperan `Mönchengladbach` y `respondió`. Los 39 casos de esa clase pasan aislados con el mismo `-Dfile.encoding=UTF-8`; se reproducen las dos fallas combinando únicamente las clases anteriores `SecurityConfigTest` (5/5 pasan) y `PlayerStatsServiceTest` (37/39 pasan), sin ninguna prueba de esta feature. El contexto Spring reconfigura el logging global antes de la captura: aislado se obtiene el formato simple de Logback y combinado el formato de consola de Boot, donde los caracteres acentuados aparecen como `�`. El patrón `logging.pattern.console` no fue editado. Las pruebas nuevas no cambian `SecurityContextHolder`, MDC, appenders, propiedades de sistema ni configuración estática directamente; sus `@TestPropertySource` son de clase y los datos persistidos se revierten con `@Transactional`. No se modifican archivos ajenos para resolver estas dos fallas.

- Revisión 2026-11-14 (continuación): (1) con sólo el grupo `actuator`, `/v3/api-docs/swagger-config` listaba únicamente ese grupo y la API principal desaparecía del selector de Swagger UI; `ActuatorOpenApiConfig` agrega un grupo `api` (todo menos `/actuator/**`) que queda primero, y `ActuatorOpenApiTest` lo verifica. `/v3/api-docs` sin grupo sigue respondiendo igual para las pruebas OpenAPI existentes. (2) Las dos fallas de `PlayerStatsServiceTest` se deben a que Boot toma por defecto el charset de la consola (`System.console().charset()`, cp1252/cp850 en Windows) para el appender de consola, mientras `OutputCapture` decodifica con el charset por defecto de la JVM (UTF-8). Se reproducen en Linux forzando `-Dlogging.charset.console=windows-1252`. Se fija `logging.charset.console=UTF-8` en `backend/src/test/resources/application.properties` (sólo tests) y, como Java 17 en Windows usa `Cp1252` como charset por defecto, `pom.xml` configura surefire con `-Dfile.encoding=UTF-8` (`@{argLine}` conserva el agente de JaCoCo). Suite sin E2E en Windows (2026-11-15): 951 casos, 0 fallas, 0 errores, 2 omitidos.

- No se crean entidades persistidas, controllers ni métricas de negocio.
- Las pruebas de salud que necesitan detalles deben sobreescribir localmente la configuración de test existente, sin editar `backend/src/test/resources/application.properties`.
- El valor crudo de `SCRAPE_API_KEY` nunca aparece en archivos versionados.
- `$speckit-implement` no realiza commit, push ni abre una PR; T024 no se marca `[X]` antes de la verificación manual posterior del usuario.
