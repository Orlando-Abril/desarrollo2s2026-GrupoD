# Quickstart: Validación del catálogo Football-Data

Esta guía valida el diseño descrito en [plan.md](./plan.md), el modelo de
[data-model.md](./data-model.md) y el contrato [players-api.yaml](./contracts/players-api.yaml).

## Prerrequisitos

- Java 17.
- PostgreSQL disponible en `localhost:5432/desarrollo2_grupod`.
- Redis 7 disponible en `localhost:6379`.
- Token de Football-Data.org sólo para la prueba manual de sincronización.
- Variables `DB_PASSWORD`, `JWT_SECRET` y `FOOTBALL_DATA_TOKEN`; opcionalmente
  `FOOTBALL_DATA_CACHE_TTL` (por defecto `PT6H`).

Los tests automatizados del adapter no necesitan token, Redis, PostgreSQL ni acceso a
Internet.

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
  inválidos y exige `X-API-KEY`.

## 2. Levantar dependencias y backend

Iniciar PostgreSQL y Redis según el entorno local. Luego, desde `backend/`:

```powershell
$env:DB_PASSWORD = '<password-local>'
$env:JWT_SECRET = '<secreto-de-al-menos-32-bytes>'
$env:FOOTBALL_DATA_TOKEN = '<token-football-data>'
$env:FOOTBALL_DATA_CACHE_TTL = 'PT6H'
./mvnw.cmd spring-boot:run
```

Resultado esperado: la aplicación inicia, conecta a PostgreSQL y Redis y expone
Swagger UI. El token externo no aparece en logs ni respuestas.

## 3. Obtener una API key

Registrar un usuario con el contrato existente:

```powershell
$body = @{ username = 'catalog-user'; email = 'catalog@example.com'; password = 'StrongPass123!' } | ConvertTo-Json
$registration = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/auth/register' -ContentType 'application/json' -Body $body
$apiKey = $registration.apiKey
```

Conservar la clave devuelta, ya que se muestra una sola vez.

## 4. Consultar el catálogo

Una vez ejecutada la operación interna de sincronización por el proceso de aplicación,
consultar sin filtros:

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/players' -Headers @{ 'X-API-KEY' = $apiKey }
```

Consultar filtros combinados:

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/players?league=PREMIER_LEAGUE&team=Arsenal%20FC&position=FORWARD' -Headers @{ 'X-API-KEY' = $apiKey }
```

Resultado esperado: `200`; cada elemento satisface todos los filtros. Una combinación
sin coincidencias devuelve `[]`.

Sin credencial:

```powershell
Invoke-WebRequest -Uri 'http://localhost:8080/players' -SkipHttpErrorCheck
```

Resultado esperado: `401` sin datos del catálogo.

## 5. Verificar caché y resiliencia

1. Ejecutar dos sincronizaciones dentro del TTL.
2. Verificar con el test/spy del adapter o métricas de prueba que por liga sólo la
   primera carga alcanza el servidor simulado; la segunda usa Redis.
3. Después de una sincronización exitosa, simular `503` o timeout en
   `MockRestServiceServer` y volver a sincronizar.
4. Consultar `GET /players` con las mismas combinaciones de filtros.

Resultado esperado: los datos locales previos siguen respondiendo y no se borran. Si
la sincronización falla sin ningún dato local, la operación informa indisponibilidad de
forma controlada.

## 6. Inspeccionar documentación

Abrir `http://localhost:8080/swagger-ui/index.html` y localizar **Players**.

Resultado esperado: aparecen los tres filtros, los esquemas de respuesta y error, los
códigos `200/400/401` y el esquema `apiKeyAuth` con header `X-API-KEY`.
