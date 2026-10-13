# Data Model: Perfil e2e con infraestructura real

Esta feature no agrega entidades persistentes de negocio. El modelo describe el estado de la infraestructura y los datos temporales que gobiernan una ejecución e2e.

## 1. E2E Profile

| Field | Type | Required | Validation / Default |
|-------|------|----------|----------------------|
| profileName | string | yes | Exactamente `e2e` |
| webEnvironment | enum | yes | `RANDOM_PORT` |
| testTag | string | yes | Exactamente `e2e` |
| schemaMode | enum | yes | `create-drop` |
| cacheType | enum | yes | `redis` |
| footballDataBaseUrl | URI | yes | Loopback inaccesible, nunca público |
| whoScoredBaseUrl | URI | yes | Loopback inaccesible, nunca público |

**Relationships**: Usa una PostgreSQL Endpoint, una Redis Endpoint y una Application Instance.

## 2. PostgreSQL Endpoint

| Field | Type | Required | Validation / Default |
|-------|------|----------|----------------------|
| jdbcUrl | JDBC URL | yes | `E2E_DB_URL`; default `jdbc:postgresql://localhost:5432/desarrollo2_grupod` |
| host | string | derived | Host extraído para sondeo TCP |
| port | integer | derived | 1..65535; default PostgreSQL 5432 si la URL no lo declara |
| username | string | yes | `E2E_DB_USER`; default `postgres` |
| password | secret | yes | `E2E_DB_PASSWORD`; default `postgres`; nunca se registra |
| driver | class name | yes | `org.postgresql.Driver`; no puede quedar `org.h2.Driver` |
| availability | enum | derived | `UNKNOWN`, `TCP_AVAILABLE`, `TCP_UNAVAILABLE`, `INVALID_CONFIGURATION` |

**Relationships**: Aloja cero o más Application Tables dentro del esquema actual.

## 3. Redis Endpoint

| Field | Type | Required | Validation / Default |
|-------|------|----------|----------------------|
| host | string | yes | `REDIS_HOST`; default `localhost` |
| port | integer | yes | `REDIS_PORT`; default `6379`; rango 1..65535 |
| logicalDatabase | integer | derived | La seleccionada por la configuración Spring; dedicada a e2e |
| availability | enum | derived | `UNKNOWN`, `TCP_AVAILABLE`, `TCP_UNAVAILABLE`, `INVALID_CONFIGURATION` |

**Relationships**: Contiene cero o más Cache Entries; su limpieza afecta sólo la base lógica seleccionada.

## 4. Availability Decision

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| ciRequired | boolean | yes | Verdadero sólo cuando `CI=true`, sin distinguir mayúsculas |
| postgresStatus | availability | yes | Resultado del parsing/sondeo, salvo bypass de CI |
| redisStatus | availability | yes | Resultado del parsing/sondeo, salvo bypass de CI |
| enabled | boolean | yes | Siempre true en CI; localmente true sólo con ambos sockets disponibles |
| reason | string | yes | Claro y sanitizado; enumera todas las dependencias ausentes; sin secretos |

### State transitions

```text
UNKNOWN
  ├─ CI=true ───────────────────────────────> ENABLED_FOR_CI
  ├─ config inválida ───────────────────────> DISABLED_LOCAL
  ├─ socket PG o Redis falla ───────────────> DISABLED_LOCAL
  └─ ambos sockets responden ───────────────> ENABLED_LOCAL

ENABLED_LOCAL / ENABLED_FOR_CI
  ├─ contexto o limpieza falla ─────────────> FAILED
  └─ contexto + smoke exitosos ─────────────> PASSED
```

La transición a `DISABLED_LOCAL` ocurre antes del contexto. Después de habilitar, cualquier caída es fallo y nunca se convierte retroactivamente en omisión.

## 5. Application Table Set

Conjunto dinámico de tablas base del esquema PostgreSQL actual.

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| schemaName | SQL identifier | yes | Esquema actual, identificado desde la conexión |
| tableNames | ordered set | yes | Sólo tablas base visibles del esquema; puede estar vacío |
| quotedNames | ordered set | derived | Identificadores citados/escapados antes de construir SQL |

Tablas conocidas actualmente: `users`, `api_keys`, `players`, `player_positions`, `player_stats`. La limpieza no depende de esta lista fija.

## 6. Clean Test State

| Field | Type | Required | Invariant |
|-------|------|----------|-----------|
| postgresRows | count | yes | 0 inmediatamente antes de cada test |
| postgresIdentities | state | yes | Reiniciadas por `RESTART IDENTITY` |
| redisKeys | count | yes | 0 después de `FLUSHDB SYNC` |
| cleanedAt | instant | yes | Anterior al primer request del método actual |

**Lifecycle**:

```text
DIRTY_OR_UNKNOWN
  -> TRUNCATING_POSTGRES
  -> FLUSHING_REDIS
  -> CLEAN
  -> TEST_MUTATIONS
  -> DIRTY
```

Si cualquier paso de limpieza falla, el test pasa a `FAILED`; nunca continúa con estado parcial.

## 7. Smoke User and Session

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| username | string | yes | No vacío; reutilizable entre métodos gracias a la limpieza |
| email | email | yes | Formato válido |
| password | string | yes | Al menos 8 caracteres |
| apiKey | secret string | response | Presente en registro; no se usa en este flujo |
| bearerToken | secret string | response | No vacío; obtenido del login; no se registra |
| tokenType | string | response | `Bearer` |

**Relationships**: El usuario posee una API key persistida; el token autoriza el request de catálogo. Ambos se descartan junto con el estado e2e.

## 8. Smoke Observation

| Field | Expected value |
|-------|----------------|
| registerStatus | 201 |
| loginStatus | 200 |
| playersStatus | 200 |
| playersBody | Array, vacío permitido |
| correlationId | Presente y no vacío |
| healthStatus | HTTP 200 y cuerpo `status=UP` |

El flujo es estrictamente secuencial: registro → login → catálogo autenticado → salud.
