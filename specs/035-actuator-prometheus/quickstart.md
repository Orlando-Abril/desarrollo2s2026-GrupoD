# Quickstart: validar monitoreo

## Prerrequisitos

- Java 17, Docker con Compose y los PostgreSQL/Redis que usa la aplicación, levantados según [README.md](../../README.md).
- Variables habituales del backend (`DB_PASSWORD`, `JWT_SECRET`, `FOOTBALL_DATA_TOKEN`) configuradas según README.
- Para la demo, una API key activa cuyo principal tenga `ROLE_ADMIN`. Una clave creada al registrar un usuario `USER` no sirve para el scrape. El procedimiento local siguiente genera una clave de servicio; requiere `psql` y acceso administrativo a la base de datos de **desarrollo local**. No usar una base compartida o con datos reales.

## Crear una API key ADMIN para la demo local

Primero compilar y arrancar el backend como indica **Verificación manual del backend** más abajo; eso crea el esquema en PostgreSQL local. Luego, desde PowerShell:

```powershell
$bytes = New-Object byte[] 24
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
$env:SCRAPE_API_KEY = 'sk_' + ([BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
$sha = [System.Security.Cryptography.SHA256]::Create()
$hash = ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($env:SCRAPE_API_KEY)))).Replace('-', '').ToLowerInvariant()
$sha.Dispose()
$prefix = $env:SCRAPE_API_KEY.Substring(0, 11)
psql -h localhost -U postgres -d desarrollo2_grupod -c "INSERT INTO api_keys (key_hash, key_prefix, user_id, active, created_at) VALUES ('$hash', '$prefix', NULL, true, CURRENT_TIMESTAMP)"
curl.exe -i -H "X-API-KEY: $env:SCRAPE_API_KEY" http://localhost:8080/actuator/prometheus
```

La última llamada debe responder 200 antes de iniciar Compose. `user_id=NULL` da `ROLE_ADMIN` según el filtro actual. Se guarda sólo el hash en PostgreSQL; el valor crudo queda en la variable de esta sesión. Al finalizar la demo, eliminar la fila de prueba por su `key_hash` desde una sesión administrativa y quitar la variable con `Remove-Item Env:SCRAPE_API_KEY`; no imprimir ni versionar la clave.

## Verificación automatizada

Desde `backend/`:

```powershell
./mvnw.cmd -Dtest=HealthEndpointTest,PublicRouteFilterTest,InfoEndpointTest,ActuatorMonitoringTest,ActuatorOpenApiTest test
```

`InfoEndpointTest`, `ActuatorMonitoringTest`, `ActuatorOpenApiTest` y una prueba de regresión de filtros públicos se agregan en implementación. `HealthEndpointTest` debe pasar JWT y API keys **reales** por los filtros, sin `@WithMockUser` ni autenticación simulada: health y probes anónimos, detalles sólo para ADMIN, USER sin detalles, un `Bearer` inválido/vencido y una API key no vacía inexistente/inactiva con 401, cada uno en una solicitud separada. La prueba de regresión recorre todos los patrones `PUBLIC_ROUTES` ajenos a health. Las otras pruebas cubren info público con `build.version`, metrics y prometheus anónimos 401, USER 403, ADMIN y API key ADMIN 200, todas las series Prometheus con `application="demo"`, conteos por ruta/resultado, y el grupo Actuator en Swagger UI con alternativas `bearerAuth`/`apiKeyAuth`, códigos y esquemas de respuesta.

Las solicitudes autenticadas de estas pruebas envían exactamente un mecanismo: JWT **o** API key. Headers `Authorization` con otros esquemas y peticiones que combinan ambos mecanismos no se prueban ni se modifican en esta feature.

## Verificación manual del backend

Compilar con los metadatos de build y arrancar el backend con las dependencias habituales activas:

```powershell
Set-Location backend
./mvnw.cmd package
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

En otra terminal:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8080/actuator/health/liveness
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
Invoke-RestMethod http://localhost:8080/actuator/info
Invoke-RestMethod http://localhost:8080/v3/api-docs/actuator
curl.exe -i http://localhost:8080/actuator/prometheus
curl.exe -i -H "X-API-KEY: $env:SCRAPE_API_KEY" http://localhost:8080/actuator/prometheus
```

Esperar `status` sin `components` en health anónimo, `build.name`, `build.version` y `build.time` en info, 401 sin clave y formato Prometheus con clave ADMIN. En solicitudes separadas, un `Bearer` con token inválido/vencido y una API key no vacía inexistente/inactiva en health deben recibir 401; JWT o API key ADMIN válidos deben mostrar detalles y una credencial USER válida no debe mostrarlos. Abrir `http://localhost:8080/swagger-ui/index.html`, seleccionar el grupo Actuator, confirmar las cuatro familias y sus códigos/esquemas de respuesta, y probar metrics/prometheus con `bearerAuth` y `apiKeyAuth` por separado. Generar algunas solicitudes exitosas y fallidas a rutas de la API antes de comprobar las series HTTP. Ver [contrato](contracts/actuator-monitoring.md).

## Demo con Prometheus y Grafana

Asignar `SCRAPE_API_KEY` en la sesión local sin mostrarla en la terminal ni guardarla en Git. Desde la raíz del proyecto:

```powershell
docker compose -f observability/docker-compose.yml config --quiet
docker compose -f observability/docker-compose.yml up -d
```

Abrir `http://localhost:9090/targets`: el objetivo de la aplicación debe figurar `UP` después del primer scrape de 15 s. Abrir `http://localhost:3000` y comprobar que Prometheus está disponible como datasource provisionado. Consultar una serie con `application="demo"`.

La aplicación debe iniciar y responder health e info también con la demo detenida:

```powershell
docker compose -f observability/docker-compose.yml down
```

No ejecutar la demo como requisito para arrancar el backend. Las URL y credenciales de la UI de Grafana se documentarán en la sección Monitoreo del README durante la implementación.

## Verificaciones de entrega

**Tarea manual final del usuario:** confirmar los cambios, publicar la rama y abrir una pull request hacia `main` para activar `.github/workflows/ci.yml`. Sólo después comprobar que el workflow `CI` terminó en `SUCCESS` y que SonarCloud muestra Quality Gate `PASSED` con menos de 10 issues menores. Guardar los enlaces y resultados en la revisión de `tasks.md`. `$speckit-implement` no realiza commit, push ni apertura de PR, y estas comprobaciones no requieren modificar la Constitution.
