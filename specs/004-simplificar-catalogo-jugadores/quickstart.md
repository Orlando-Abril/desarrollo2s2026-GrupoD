# Quickstart: validar el catálogo simplificado

Guía para verificar que la simplificación cumple la [spec](spec.md). El contrato está en [contracts/players-api.yaml](contracts/players-api.yaml) y el modelo en [data-model.md](data-model.md).

## Prerrequisitos

- PostgreSQL con la base `desarrollo2_grupod`.
- Redis en `localhost:6379` (en Windows sirve Memurai).
- Variables de entorno de la run configuration `DemoApplication` de IntelliJ. **Sólo** estas:
  - `DB_PASSWORD`
  - `JWT_SECRET`
  - `FOOTBALL_DATA_TOKEN`

  `MARKET_SUPERUSER_USERNAME` ya no se usa y conviene borrarla de la run configuration.
- Un usuario común con su API key, que devuelve `POST /auth/register`. No hace falta un superusuario ni el rol ADMIN.

## 1. Tests automáticos

```powershell
cd backend
./mvnw.cmd verify
```

Resultado esperado:
- Build en verde. Ningún test llama a Football-Data.
- El test de Redis se omite si no hay Docker.
- No existen tests de scheduler, tokens, auditoría, Flyway ni métricas custom.

## 2. Arranque sobre una base vacía (SC-006)

1. Crear una base descartable vacía y apuntar el datasource a ella.
2. Correr `DemoApplication`.

Resultado esperado:
- La app arranca y Hibernate crea `players` y `player_positions`, entre otras tablas.
- No se crean `flyway_schema_history`, `player_token_allocations` ni `catalog_sync_audit_events`.
- En el log no aparece ninguna sincronización automática.

## 3. Carga explícita (User Story 2)

En Swagger (`http://localhost:8080/swagger-ui/index.html`) → **Authorize** → `apiKeyAuth` → ejecutar `POST /players/sync`.

| Caso | Resultado esperado |
|---|---|
| Sin API key | `401` |
| Primera carga | `200` con `status: COMPLETED` y `processed` > 0 (unos 2.650) |
| Segunda carga dentro del TTL de la caché | `200` con los mismos totales. No aumenta la cantidad de filas en `players` (SC-002). No hay requests nuevas a Football-Data |
| `FOOTBALL_DATA_TOKEN` inválido y caché vacía (`redis-cli FLUSHALL`) | `200` con `status: FAILED` y `failedLeagues: 5`. Los jugadores existentes no cambian. El log no muestra el token |

Chequeo en la base:

```sql
SELECT COUNT(*), COUNT(DISTINCT external_id) FROM players;  -- los dos valores iguales
```

## 4. Consulta del catálogo (User Story 1)

| Request | Resultado esperado |
|---|---|
| `GET /players` sin API key | `401` |
| `GET /players` antes de la primera carga | `200 []` |
| `GET /players?league=LA_LIGA` | Sólo jugadores de La Liga |
| `GET /players?team=arsenal fc&position=FORWARD` | Delanteros de "Arsenal FC" (sin distinguir mayúsculas) |
| `GET /players?league=INVALIDA` o `?team=%20` | `400 validation_error` |
| Con Football-Data inaccesible (token inválido o sin red) | `GET /players` sigue devolviendo los datos guardados (SC-003) |

## 5. Observabilidad conservada (SC-009)

- Toda respuesta trae `X-Correlation-ID`. Si mandás uno válido en la request, vuelve el mismo.
- `GET /actuator/health`, sin API key, responde `UP` o `DOWN`, sin detalles.
- Los logs de consola salen en JSON y con `correlationId`.
- `GET /actuator/metrics`, con API key, no lista `football.data.*` ni `catalog.synchronization*`, pero sí `http.server.requests`.

## 6. Configuración limpia (SC-007)

```powershell
git grep -nE "flyway|sync-cron|bootstrap-retry|superuser|TokenAllocation|CatalogSyncAudit|football\.data\.request|catalog\.synchronization" -- backend README.md
```

Resultado esperado: sin coincidencias.

## Limpieza opcional de bases existentes

Las bases que ya corrieron la versión anterior de la rama conservan tablas sin uso. No afectan el funcionamiento. Para borrarlas:

```sql
DROP TABLE IF EXISTS catalog_sync_audit_events;
DROP FUNCTION IF EXISTS reject_catalog_audit_mutation();
DROP TABLE IF EXISTS player_token_allocations;
DROP TABLE IF EXISTS flyway_schema_history;
```

## Registro de validación

- 2026-09-24 (T001): antes de simplificar, `./mvnw.cmd test` → 124 tests, 0 fallas, 7 omitidos (Testcontainers sin Docker).
- 2026-09-24 (T037): `./mvnw.cmd clean verify` → 105 tests, 0 fallas, 1 omitido (Redis con Testcontainers sin Docker). JaCoCo generado.
  - Hace falta `clean` después de borrar tests: sin él, quedan clases compiladas de los tests eliminados en `target/test-classes` y surefire falla al descubrirlas. En CI no pasa, porque parte de un checkout limpio.
  - Ajuste fuera del plan: se agregó `HandlerMethodValidationException` al handler `validation_error` de `GlobalExceptionHandler`, para que un `team` en blanco devuelva `400 validation_error` sin importar qué mecanismo de validación de Spring lo detecte (FR-014). `PlayerControllerTest` aplica `MethodValidationInterceptor` para reproducir el proxy de `@Validated`.
- Pendiente: validación manual (T038) y CI/Sonar (T039).
