# Quickstart: Validación del catálogo Football-Data

Esta guía valida el diseño descrito en [plan.md](./plan.md), el modelo de
[data-model.md](./data-model.md) y el contrato [players-api.yaml](./contracts/players-api.yaml).

## Prerrequisitos

- Java 17.
- PostgreSQL disponible en `localhost:5432/desarrollo2_grupod`.
- Redis 7 disponible en `localhost:6379`.
- Token de Football-Data.org sólo para iniciar el backend; los tests usan respuestas simuladas.
- Variables `DB_PASSWORD`, `JWT_SECRET` y `FOOTBALL_DATA_TOKEN`; opcionalmente
  `FOOTBALL_DATA_CACHE_TTL` (por defecto `PT6H`).

Los tests unitarios no necesitan Internet. Los tests de integración requieren
PostgreSQL y Redis reales/de contenedor para validar migraciones, constraints, TTL y
serialización; ninguno llama a Football-Data.org.

## 1. Ejecutar la suite automatizada

Desde `backend/`:

```powershell
./mvnw.cmd test
```

Resultado esperado: todos los tests pasan. En particular:

- el adapter solicita `/v4/competitions/{code}/teams`, envía `X-Auth-Token` y traduce
  el JSON simulado por `MockRestServiceServer`;
- no se realiza ninguna llamada a la API real;
- el service crea y actualiza por `externalId` sin duplicar ni sobrescribir
  `marketValue`;
- una falla externa mantiene los jugadores previamente persistidos;
- el controller combina `league`, `team` y `position`, devuelve `400` para enums
  inválidos, `503` sin snapshot y exige `X-API-KEY`;
- las migraciones crean índices/unicidad, la emisión inicial es exactamente de 100
  tokens y la auditoría rechaza modificaciones;
- Redis evita nuevas solicitudes dentro del TTL y conserva serialización JSON;
- cada respuesta y job conserva un correlation ID.

## 2. Levantar dependencias y backend

Iniciar PostgreSQL y Redis según el entorno local. Luego, desde `backend/`:

```powershell
$env:DB_PASSWORD = '<password-local>'
$env:JWT_SECRET = '<secreto-de-al-menos-32-bytes>'
$env:FOOTBALL_DATA_TOKEN = '<token-football-data>'
$env:FOOTBALL_DATA_CACHE_TTL = 'PT6H'
$env:FOOTBALL_DATA_SYNC_CRON = '0 0 */6 * * *'
$env:FOOTBALL_DATA_BOOTSTRAP_RETRY_INTERVAL = 'PT5M'
$env:MARKET_SUPERUSER_USERNAME = 'admin'
./mvnw.cmd spring-boot:run
```

Resultado esperado: Flyway aplica tablas/índices, la aplicación conecta a PostgreSQL y
Redis, expone Swagger/Actuator e inicia una sincronización sólo si todavía no existe un
snapshot exitoso. El token externo no aparece en logs, auditoría ni respuestas.

Si el superusuario todavía no existe o no es `ADMIN`, el log muestra
`catalog_sync_attempt_failed code=superuser_unavailable` sin escribir jugadores ni
auditoría. Registrarlo (`POST /auth/register`), ejecutar
`UPDATE users SET role = 'ADMIN' WHERE username = 'admin';` y esperar el próximo
reintento de arranque (a lo sumo `FOOTBALL_DATA_BOOTSTRAP_RETRY_INTERVAL`): la
sincronización corre sola, sin reiniciar. Tras el primer snapshot exitoso
(`COMPLETED` o `PARTIAL_FAILURE`) los reintentos se detienen y sólo aplica el cron.

## 3. Obtener una API key

Registrar un usuario con el contrato existente:

```powershell
$body = @{ username = 'catalog-user'; email = 'catalog@example.com'; password = 'StrongPass123!' } | ConvertTo-Json
$registration = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/auth/register' -ContentType 'application/json' -Body $body
$apiKey = $registration.apiKey
```

Conservar la clave devuelta, ya que se muestra una sola vez.

## 4. Consultar el catálogo

Esperar la inicialización automática o el siguiente cron y consultar sin filtros:

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/players' -Headers @{ 'X-API-KEY' = $apiKey }
```

Consultar filtros combinados:

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/players?league=PREMIER_LEAGUE&team=Arsenal%20FC&position=FORWARD' -Headers @{ 'X-API-KEY' = $apiKey }
```

Resultado esperado: `200`; cada elemento satisface todos los filtros. Una combinación
sin coincidencias devuelve `[]`. La respuesta incluye `X-Correlation-ID`; si se envía
ese header con un UUID válido, el servidor propaga el mismo valor.

Sin credencial:

```powershell
Invoke-WebRequest -Uri 'http://localhost:8080/players' -SkipHttpErrorCheck
```

Resultado esperado: `401` sin datos del catálogo.

En una base nueva, si la inicialización falla antes de crear jugadores:

```powershell
Invoke-WebRequest -Uri 'http://localhost:8080/players' -Headers @{ 'X-API-KEY' = $apiKey } -SkipHttpErrorCheck
```

Resultado esperado: `503` con `error=catalog_unavailable`. Después de un snapshot
exitoso legítimamente vacío, la misma consulta devuelve `200 []`.

## 5. Verificar caché y resiliencia

1. Ejecutar los tests de scheduler/caché que disparan dos sincronizaciones dentro del TTL.
2. Verificar que la carga fría produce como máximo cinco requests y la segunda cero.
3. Después de una sincronización exitosa, simular `503` o timeout en
   `MockRestServiceServer` y volver a sincronizar.
4. Consultar `GET /players` con las mismas combinaciones de filtros.

Resultado esperado: los datos locales previos siguen respondiendo y no se borran. Si
la sincronización falla sin ningún dato local, la operación informa indisponibilidad de
forma controlada.

## 6. Verificar tokens, constraints y auditoría

Después de importar un jugador nuevo, inspeccionar PostgreSQL con las consultas de
validación provistas por los tests/migraciones.

Resultado esperado:

- una asignación por jugador, suministro `100`, cantidad del superusuario `100` y
  precio base `1.00`;
- repetir o solapar la importación no crea otro Player ni otra emisión;
- existen índices para `external_id`, liga, equipo y posición;
- cada ejecución posee `STARTED` y un evento terminal con el mismo correlation ID;
- una ejecución abortada por error interno conserva su evento `FAILED`;
- un jugador que no puede persistirse deja un `PLAYER_FAILED` (con su externalId) y ningún
  `PLAYER_CREATED`/`TOKENS_ALLOCATED` huérfano; los demás jugadores quedan guardados y la
  ejecución termina `PARTIAL_FAILURE`, o `FAILED` si no se guardó ninguno;
- intentos de `UPDATE` o `DELETE` sobre auditoría son rechazados.

## 7. Verificar salud, métricas y logs

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/actuator/health'
```

Resultado esperado: `UP` cuando aplicación, PostgreSQL y Redis están saludables. Los
tests de observabilidad verifican métricas de latencia/error externo y duración/resultado
de sincronización. Los logs son JSON y contienen correlation ID sin secretos.

## 8. Inspeccionar documentación

Abrir `http://localhost:8080/swagger-ui/index.html` y localizar **Players**.

Resultado esperado: aparecen los tres filtros, headers de correlación, esquemas de
respuesta/error, códigos `200/400/401/503`, health y `apiKeyAuth` con `X-API-KEY`.

## 9. Validar gates de entrega

Ejecutar `./mvnw.cmd verify`, publicar el branch y revisar el workflow y SonarCloud.

Resultado esperado: GitHub Actions `SUCCESS`; SonarCloud `PASSED`, sin vulnerabilidades
y con menos de 10 issues menores. La feature no se considera terminada antes de ambos.

## Registro de validación de implementación

- 2026-09-21: compilación Maven y suite local ejecutadas sobre Spring Boot 4.1.1.
- Los tests de cliente usan exclusivamente `MockRestServiceServer`; no se realizó tráfico a la API real.
- En este entorno Docker no estaba disponible: los seis casos Testcontainers de PostgreSQL/Redis quedaron `SKIPPED` de forma explícita mediante `disabledWithoutDocker` y deben ejecutarse en CI, donde el runner dispone de Docker.
- Los escenarios manuales que requieren credenciales reales, PostgreSQL/Redis locales y un superusuario `ADMIN` preaprovisionado quedan pendientes para el ambiente desplegado.
- La confirmación de GitHub Actions y SonarCloud sólo puede registrarse después de publicar el branch; no se declara éxito externo desde una ejecución local.
- 2026-09-23 (Phase 8: Convergence): `CatalogSyncResilienceIntegrationTest` (contexto Spring real sobre H2) cubre `FAILED` durable en `catalog_sync_audit_events`, jugador inválido entre válidos → `PARTIAL_FAILURE`, y arranque sin superusuario con recuperación en el siguiente reintento sin reiniciar. V3 se validó manualmente contra PostgreSQL 17 local con un esquema legacy (`players.position` NOT NULL) en una base descartable; el caso Testcontainers equivalente corre en CI.
