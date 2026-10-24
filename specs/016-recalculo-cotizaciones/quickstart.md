# Quickstart: Recálculo de cotizaciones

**Feature**: [spec.md](spec.md) | **Contrato**: [contracts/quotes-api.yaml](contracts/quotes-api.yaml) | **Modelo**: [data-model.md](data-model.md)

## Prerrequisitos

- La rama incluye la feature 015 (`git merge origin/main`, ver research D1).
- PostgreSQL y Redis locales, como en las features anteriores. Variables `DB_PASSWORD` y `JWT_SECRET` definidas.
- Hay jugadores en el catálogo y, para ver valores `CALCULATED`, estadísticas sincronizadas. La corrida no consulta WhoScored ni football-data.

## 0. Migrar una base existente

`ddl-auto=update` no cambia la escala de `market_value`. En una base creada antes de esta feature, ejecutar una vez:

```sql
ALTER TABLE players ALTER COLUMN market_value TYPE numeric(12,4);
```

Para verificar: `SELECT market_value FROM players LIMIT 1;` muestra 4 decimales (por ejemplo, `1.0000`). Los perfiles `test` y `e2e` crean la tabla de cero y no lo necesitan.

## 1. Dejar un usuario como ADMIN

Ningún endpoint cambia roles. Registrar un usuario con `POST /auth/register` y después:

```sql
UPDATE users SET role = 'ADMIN' WHERE username = 'admin';
```

El rol se lee de la base en cada pedido, así que no hace falta volver a iniciar sesión.

La API key hereda el rol de su dueño. Para probar el recálculo con API key, usar la key que `POST /auth/register` le emitió a ese usuario ADMIN. Las API keys sin dueño (de servicio) no las emite ningún endpoint: se crean a mano en la base. Hoy `AuthService.register` siempre asigna un dueño.

## 2. Ejecutar

```bash
cd backend && ./mvnw spring-boot:run
```

Obtener un token con `POST /auth/login`. Después:

```bash
# Todas las estrategias
curl -i -X POST http://localhost:8080/quotes/recalculate -H "Authorization: Bearer $TOKEN"

# Sólo OFFICIAL
curl -i -X POST http://localhost:8080/quotes/recalculate -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" -d '{"strategy":"OFFICIAL"}'

# Con la API key de un ADMIN
curl -i -X POST http://localhost:8080/quotes/recalculate -H "X-API-KEY: $API_KEY"
```

**Resultado esperado** (todas las estrategias, OFFICIAL activa):
- 200 con `strategies` = [OFFICIAL], `skippedStrategies` = [MOMENTUM `no_algorithm`], `playersFailed` 0 y `marketValueUpdated` true.
- El header `X-Correlation-ID` coincide con el `correlation_id` de las cotizaciones.

## 3. Verificar en la base

```sql
-- Una cotización OFFICIAL por jugador en la corrida, con instante único
SELECT count(*), count(DISTINCT player_id), count(DISTINCT calculated_at)
FROM player_quotes WHERE run_id = '<runId>';

-- Precio de mercado = cotización de la estrategia activa
SELECT count(*) FROM players p JOIN player_quotes q ON q.player_id = p.id
WHERE q.run_id = '<runId>' AND q.strategy_code = 'OFFICIAL' AND p.market_value <> q.quote_value;  -- 0

-- NO_STATS sin score
SELECT count(*) FROM player_quotes WHERE run_id = '<runId>' AND status <> 'CALCULATED' AND score IS NOT NULL;  -- 0

-- Snapshot de la misma corrida
SELECT count(*) FROM player_stats_snapshots WHERE run_id = '<runId>';
```

En los logs: `quote_recalculation_started` y `quote_recalculation_finished` con el mismo `runId` y `correlationId`.

## 4. Casos de error

| Pedido | Esperado |
|--------|----------|
| Sin `Authorization` ni `X-API-KEY` | 401 `unauthorized` |
| JWT de un usuario `USER` | 403 `forbidden` |
| API key de un usuario `USER` | 403 `forbidden` |
| `{"strategy":"FOO"}` | 400 `unknown_strategy`, sin snapshot nuevo |
| `{"strategy":"MOMENTUM"}` | 200, `strategies` vacío, MOMENTUM salteada, `marketValueUpdated` false |
| Dos pedidos simultáneos | uno 200 y el otro 409 `recalculation_in_progress` |
| `GET /players` con JWT de `USER` | 200, igual que antes |

## 5. Tests

```bash
cd backend
./mvnw test -DexcludedGroups=e2e   # unitarios + MockMvc (perfil test)
./mvnw test -Dgroups=e2e           # @E2ETest contra PostgreSQL y Redis
```

Clases relevantes:
- `QuoteCalculationServiceTest`
- `QuoteControllerTest`
- `QuoteRecalculationSecurityTest`
- `QuoteRecalculationE2ETest`
- los tests de seguridad existentes (`JwtAuthFilterTest`, `ApiKeyAuthFilterTest`, `JwtOrApiKeyAuthenticationIntegrationTest`), que tienen que seguir pasando.
