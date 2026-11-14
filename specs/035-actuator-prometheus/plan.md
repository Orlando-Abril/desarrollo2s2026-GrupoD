# Implementation Plan: Monitoreo con Actuator y Prometheus

**Branch**: `feature/actuator-prometheus` | **Date**: 2026-11-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/035-actuator-prometheus/spec.md`, including the limited authorization for optional health authentication and the manual CI/SonarCloud gate.

## Summary

Exponer health, info, metrics y prometheus con visibilidad por rol, identidad del build y métricas de solicitudes HTTP. Incorporar la dependencia Prometheus y el goal `build-info` al backend; configurar Actuator y Micrometer con propiedades `management.*` y activar el grupo Actuator de Swagger mediante `springdoc.show-actuator`. Ajustar las reglas de Actuator en `SecurityConfig` y únicamente la omisión de los filtros JWT/API key para autenticar health cuando se presente una credencial, sin exigirla. Agregar una demo opcional con Prometheus 3.7 y Grafana 12.2, y documentar su ejecución en README. El scrape usa una API key ADMIN tomada de una variable de entorno como secreto de Docker Compose.

## Technical Context

**Language/Version**: Java 17; Spring Boot 4.1.1; Maven Wrapper existente.

**Primary Dependencies**: `spring-boot-starter-actuator` existente, `io.micrometer:micrometer-registry-prometheus` administrado por el BOM de Boot; Spring Security existente; Prometheus `v3.7.0` y Grafana `12.2.0` para demo.

**Storage**: PostgreSQL y Redis existentes para la aplicación; ninguna persistencia nueva. Prometheus conserva datos sólo para la demo.

**Testing**: JUnit y MockMvc con `@SpringBootTest` existentes; `HealthEndpointTest` con JWT y API keys reales enviados por separado, prueba de omisión para todos los patrones públicos y pruebas nuevas de Actuator/OpenAPI que verifican códigos y esquemas de respuesta. Test manual de `docker compose config`, objetivo de scrape y datasource. CI y SonarCloud quedan como verificación manual posterior a la confirmación, publicación y PR del usuario.

**Target Platform**: backend local en puerto 8080; demo en Docker Compose con acceso al host por `host.docker.internal`.

**Project Type**: servicio web con demo local de observabilidad.

**Performance Goals**: recolección cada 15 s; métricas HTTP por ruta y resultado, con buckets y percentiles. No se fija un umbral de latencia nuevo.

**Constraints**: sólo cuatro familias Actuator expuestas; health e info públicos, métricas ADMIN. Cada solicitud autenticada usa sólo JWT o API key. En health, `Bearer` con token inválido/vencido o `X-API-KEY` no vacío inexistente/inactivo, enviado individualmente, responde 401; otros esquemas `Authorization` y credenciales simultáneas quedan fuera del alcance y conservan la conducta previa. Secretos fuera de Git; aplicación independiente de la demo; cambios previos limitados a los archivos autorizados en el spec, incluidas exclusivamente las condiciones de omisión de los dos filtros para health. `GroupedOpenApi` nuevo sólo si hiciera falta para completar documentación. Sin commit, push ni PR automáticos.

**Scale/Scope**: una instancia de backend y un objetivo de scrape; sin alertas, tracing, dashboards personalizados ni métricas de negocio.

## Constitution Check

*GATE: passed before Phase 0; re-checked after Phase 1 design.*

| Regla | Comprobación | Resultado |
|---|---|---|
| §2.1 Capas estrictas | Se usan capacidades de infraestructura existentes, sin lógica de negocio ni cruces entre controller, service, repository y adapter. | PASS |
| §4.1 Autenticación y autorización | JWT y API key existentes; health acepta credenciales opcionales y rechaza las inválidas; sólo rol ADMIN consulta métricas y detalles, y la clave de scrape se guarda fuera de Git. | PASS |
| §5.3 Observabilidad | Se conservan logs JSON y correlation ID; se agregan health y métricas de latencia/errores. | PASS |
| §3.1 Calidad/CI | Pruebas automatizadas de acceso y contrato, más validación local de la demo. | PASS |
| §6.1 y §7.4 Swagger UI | El grupo Actuator documentará las cuatro familias; una prueba verificará alternativas `bearerAuth`/`apiKeyAuth`, códigos y esquemas de respuesta. | PASS, sujeto a prueba |
| §7.2–7.3 CI y SonarCloud | El usuario confirmará y publicará los cambios y abrirá el PR; sólo entonces se verifican `SUCCESS` de GitHub Actions y Quality Gate `PASSED` con menos de 10 issues menores. | PENDIENTE, gate manual final |

**Reevaluación posterior al diseño**: contratos, modelo conceptual y quickstart respetan los gates. La conformidad final de Swagger, CI y SonarCloud queda sujeta a las pruebas y verificaciones de la fase final. No hay excepciones a la Constitution.

## Phase 0: Research

Las decisiones y alternativas están en [research.md](research.md). Los puntos determinantes son `http_headers` de Prometheus 3.7 con secreto leído de archivo, el goal `build-info`, la diferencia entre histogramas y cuantiles explícitos, y la semántica real de roles de API keys. No quedan decisiones abiertas.

## Phase 1: Design & Contracts

- [data-model.md](data-model.md): estados de salud, identidad del build y series HTTP; no se crean entidades persistidas.
- [contracts/actuator-monitoring.md](contracts/actuator-monitoring.md): rutas, acceso, respuestas y etiquetas esperadas.
- [quickstart.md](quickstart.md): validación automatizada y manual, incluida demo local.

### Cambios previstos

1. `backend/pom.xml`: registrar `micrometer-registry-prometheus` sin versión y ejecutar `build-info` desde `spring-boot-maven-plugin`.
2. `backend/src/main/resources/application.properties`: exponer `health,info,metrics,prometheus`; configurar `when-authorized`, `roles=ADMIN`, probes, etiqueta `application`, histograma y percentiles explícitos para `http.server.requests`, y build info. Activar `springdoc.show-actuator=true` para el grupo Swagger; conservar `management.health.redis.enabled=true` y las demás propiedades ajenas a `management.*`.
3. `backend/src/main/java/com/example/demo/config/SecurityConfig.java`: mantener `/actuator/health` y agregar `/actuator/health/**` y `/actuator/info` a rutas públicas; añadir `/actuator/**.hasRole("ADMIN")` antes de `anyRequest()`. En `JwtAuthFilter.java` y `ApiKeyAuthFilter.java`, cambiar únicamente `shouldNotFilter` para que health y sus subrutas procesen respectivamente `Authorization: Bearer <token>` y `X-API-KEY` no vacío cuando se presentan, y sigan pasando anónimos. Probar por separado `Bearer` inválido/vencido y API key no vacía inexistente/inactiva; no alterar `doFilterInternal`, otros esquemas de `Authorization`, credenciales simultáneas ni las demás rutas públicas.
4. `observability/docker-compose.yml`, `observability/prometheus.yml` y provisioning de datasource de Grafana: scrape cada 15 s de `host.docker.internal:8080/actuator/prometheus`; declarar `host-gateway` para Linux; secreto `SCRAPE_API_KEY` desde el entorno del host a un archivo montado sólo en Prometheus. Fijar versiones de imagen.
5. Pruebas de salud con JWT/API key reales a través de los filtros, siempre una credencial por solicitud (anónimo, ADMIN, USER y los casos inválidos definidos); prueba de omisión de todos los patrones `PUBLIC_ROUTES` ajenos a health; info, autorización de métricas, salida Prometheus y grupo Actuator de Swagger con códigos y esquemas de respuesta. Si el grupo automático carece de esquemas de autenticación, requisitos alternativos, códigos o esquemas de respuesta, agregar sólo un `GroupedOpenApi` nuevo con `OpenApiCustomizer` acotado, sin tocar `OpenApiConfig.java`. `backend/src/test/resources/application.properties` fija `show-details=never`; las pruebas de detalles ADMIN deben sobreescribirlo localmente con propiedades de la clase de test, sin editar ese archivo previo.
6. `README.md`: sección Monitoreo con requisito de API key ADMIN, comandos de Compose, URLs y carácter opcional de la demo.
7. Fase final manual: después de que el usuario confirme cambios, publique la rama y abra el PR, comprobar workflow de GitHub Actions `SUCCESS` y Quality Gate de SonarCloud `PASSED` con menos de 10 issues menores. `$speckit-implement` no realiza commit, push ni apertura de PR.

## Project Structure

### Documentation (this feature)

```text
specs/035-actuator-prometheus/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── actuator-monitoring.md
└── checklists/
    └── requirements.md
```

### Source Code (repository root)

```text
backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/example/demo/config/SecurityConfig.java
    │   ├── java/com/example/demo/config/ActuatorOpenApiConfig.java (sólo si hiciera falta)
    │   ├── java/com/example/demo/security/JwtAuthFilter.java (sólo shouldNotFilter)
    │   ├── java/com/example/demo/security/ApiKeyAuthFilter.java (sólo shouldNotFilter)
    │   └── resources/application.properties
    ├── test/java/com/example/demo/config/
        ├── HealthEndpointTest.java
        ├── ActuatorOpenApiTest.java
        └── [pruebas nuevas de monitoreo]
    └── test/java/com/example/demo/security/
        └── PublicRouteFilterTest.java
observability/
├── docker-compose.yml
├── prometheus.yml
└── grafana/provisioning/datasources/prometheus.yml
README.md
```

**Structure Decision**: Cambios de infraestructura en el backend existente y una carpeta nueva para la demo; no se agregan controllers, services, repositories ni adapters.

## Complexity Tracking

No hay violaciones de la Constitution que requieran justificación.
