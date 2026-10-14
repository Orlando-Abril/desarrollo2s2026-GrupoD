# Contract: HTTP e2e con fuentes externas caídas

## Common preconditions

- La clase está anotada con `@E2ETest`.
- PostgreSQL y Redis reales están disponibles y dedicados a la suite.
- La limpieza automática terminó antes del método.
- Football-Data y WhoScored apuntan a `127.0.0.1:1` mediante el perfil existente.
- Un usuario fue registrado y autenticado por HTTP; cada request incluye `Authorization: Bearer <token>`.
- Cada GET descrito se completa en 100 ms o menos tras una solicitud equivalente de calentamiento no medida; cada POST sync se completa en 500 ms o menos tras dos solicitudes equivalentes de calentamiento no medidas; ningún intercambio acepta un 5xx.

## GET /players — catálogo completo

### Request

```http
GET /players
Authorization: Bearer <token>
```

### Response

- HTTP 200.
- Cuerpo: cinco `PlayerResponse`, uno por fixture canónica.
- Comparación por `externalId`, sin asumir orden de respuesta.
- No se consulta Football-Data ni WhoScored.

## GET /players — filtros individuales

Cada variante es un test independiente.

### League

```http
GET /players?league=PREMIER_LEAGUE
```

Resultado: HTTP 200 y únicamente el fixture `91001`.

### Team

```http
GET /players?team=Real%20Madrid
```

Resultado: HTTP 200 y únicamente el fixture `91002`.

### Position

```http
GET /players?position=MIDFIELDER
```

Resultado: HTTP 200 y exactamente los fixtures `91002` y `91004`, demostrando inclusión en posiciones multivaluadas.

## GET /players — filtros combinados

```http
GET /players?league=PREMIER_LEAGUE&team=Arsenal%20FC&position=FORWARD
```

Resultado: HTTP 200 y únicamente el fixture `91001`.

## POST /players/sync — fuente caída y caché vacío

### Additional preconditions

- Se crearon las cinco fixtures locales.
- Redis permanece vacío después de la limpieza e2e.
- Se obtuvo una instantánea HTTP ordenada del catálogo.

### Request

```http
POST /players/sync
Authorization: Bearer <token>
```

### Response

HTTP 200 con:

```json
{
  "status": "FAILED",
  "processed": 0,
  "failedLeagues": 5,
  "failedPlayers": 0
}
```

### Postconditions

- Un GET posterior retorna una instantánea exactamente igual a la anterior.
- Ningún jugador ni posición fue creado, eliminado o modificado.
- El POST cumple el límite de 500 ms tras calentamiento y el GET posterior cumple el límite de 100 ms tras calentamiento.

## POST /players/sync — cinco cache hits

### Additional preconditions

- La base de jugadores comienza vacía.
- Redis contiene un `FootballDataResponse` válido bajo cada clave `PL`, `PD`, `SA`, `BL1`, `FL1` en `CacheConfig.COMPETITION_TEAMS_CACHE`.
- Las entradas fueron escritas con el `Cache` obtenido del `CacheManager` productivo.

### Request

```http
POST /players/sync
Authorization: Bearer <token>
```

### Response

HTTP 200 con:

```json
{
  "status": "COMPLETED",
  "processed": 5,
  "failedLeagues": 0,
  "failedPlayers": 0
}
```

### Postconditions and proof

- Un GET posterior devuelve cinco jugadores con los external IDs y atributos de los payloads cacheados.
- No hubo intento de conexión externo: cualquier ejecución del cuerpo del adapter contra el destino inválido produciría al menos una liga fallida y haría imposible el resumen exigido.
- El POST cumple el límite de 500 ms tras calentamiento y el GET posterior cumple el límite de 100 ms tras calentamiento.

## Error policy

- Un 401 indica falla de preparación/autenticación, no resiliencia.
- Un 4xx por parámetros indica que el test construyó mal la solicitud.
- Cualquier 5xx incumple el contrato.
- Un 200 con payload o `status` incorrecto también incumple el contrato.
- Superar 100 ms en un GET medido tras calentamiento o 500 ms en un POST sync medido tras calentamiento incumple el contrato aunque HTTP y payload sean correctos.
