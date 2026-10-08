# Contract: Perfil y soporte e2e

## Meta-anotación `E2ETest`

Toda clase e2e declara una única anotación que equivale conjuntamente a:

- aplicación completa con `SpringBootTest.WebEnvironment.RANDOM_PORT`;
- perfil activo exclusivo `e2e`;
- tag JUnit `e2e`;
- extensión `ExternalServicesAvailableCondition`.

La anotación se aplica a tipos, se conserva en runtime y registra automáticamente `ExternalServicesAvailableCondition` y `E2EDatabaseCleaner`. Toda clase marcada con `@E2ETest` recibe la comprobación de disponibilidad y la limpieza previa a cada método sin herencia ni configuración adicional.

## Environment contract

| Variable | Default | Consumer | Secret |
|----------|---------|----------|--------|
| `E2E_DB_URL` | `jdbc:postgresql://localhost:5432/desarrollo2_grupod` | Perfil y condición | no |
| `E2E_DB_USER` | `postgres` | Perfil | no |
| `E2E_DB_PASSWORD` | `postgres` | Perfil | yes |
| `REDIS_HOST` | `localhost` | Perfil y condición | no |
| `REDIS_PORT` | `6379` | Perfil y condición | no |
| `CI` | ausente/false | Condición | no |

`CI=true` se interpreta sin distinguir mayúsculas. Ningún diagnóstico puede incluir `E2E_DB_PASSWORD` ni una URL que pudiera contener credenciales.

## Availability outcomes

| Context | PostgreSQL TCP | Redis TCP | Outcome |
|---------|----------------|-----------|---------|
| Local | available | available | test enabled |
| Local | unavailable | any | test disabled; reason names PostgreSQL |
| Local | any | unavailable | test disabled; reason names Redis |
| Local | unavailable | unavailable | test disabled; one reason names both |
| `CI=true` | any | any | test enabled; real startup/use decides pass or fail |

Malformed JDBC URL, invalid host or port outside 1..65535 counts as unavailable locally. A listener TCP with invalid credentials counts as available at this stage; authentication failure belongs to context startup.

## Clean-state contract

`E2EDatabaseCleaner` implementa `BeforeEachCallback` y obtiene las dependencias de limpieza desde el contexto administrado por `SpringExtension`.

Before every test method:

1. Discover all base tables in the current PostgreSQL schema.
2. If the set is non-empty, truncate it in one operation using `RESTART IDENTITY CASCADE`.
3. Flush only the configured Redis logical database synchronously.
4. Continue only if both operations complete successfully.

The target services must be dedicated to e2e. Parallel methods/classes sharing the same PostgreSQL schema or Redis database are prohibited.

## Group selection contract

Run from `backend/`:

| Selection | Maven property |
|-----------|----------------|
| Non-e2e only | `-DexcludedGroups=e2e` |
| E2e only | `-Dgroups=e2e` |
| All | no group filter |

The filename `SmokeE2ETest.java` must remain discoverable by Surefire defaults.
