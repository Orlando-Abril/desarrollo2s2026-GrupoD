# Quickstart: Perfil e2e

## Safety first

Los e2e ejecutan `TRUNCATE ... RESTART IDENTITY CASCADE` sobre todas las tablas del esquema configurado y `FLUSHDB` sobre Redis antes de cada método. Use exclusivamente una base PostgreSQL y una base lógica Redis dedicadas a pruebas. No apunte estas variables a datos de desarrollo que necesite conservar.

Los comandos se ejecutan desde `backend/`.

## Prerequisites

- Java 17.
- PostgreSQL 16 accesible; la base predeterminada es `desarrollo2_grupod`.
- Redis 7 accesible.
- Usuario PostgreSQL con permiso para crear/eliminar esquema, truncar tablas y reiniciar identidades.
- Servicios sin otros procesos que dependan de sus datos durante la suite.

Defaults usados por CI:

| Variable | Default |
|----------|---------|
| `E2E_DB_URL` | `jdbc:postgresql://localhost:5432/desarrollo2_grupod` |
| `E2E_DB_USER` | `postgres` |
| `E2E_DB_PASSWORD` | `postgres` |
| `REDIS_HOST` | `localhost` |
| `REDIS_PORT` | `6379` |

Si esos valores sirven localmente, no es necesario exportar variables.

## Windows PowerShell

Configuración opcional:

```powershell
cd backend
$env:E2E_DB_URL = 'jdbc:postgresql://localhost:5432/desarrollo2_grupod'
$env:E2E_DB_USER = 'postgres'
$env:E2E_DB_PASSWORD = 'postgres'
$env:REDIS_HOST = 'localhost'
$env:REDIS_PORT = '6379'
```

Sólo tests no e2e:

```powershell
.\mvnw.cmd test '-DexcludedGroups=e2e'
```

Sólo e2e:

```powershell
.\mvnw.cmd test '-Dgroups=e2e'
```

Todos los tests y verificaciones:

```powershell
.\mvnw.cmd verify
```

Simular obligatoriedad de CI:

```powershell
$env:CI = 'true'
.\mvnw.cmd test '-Dgroups=e2e'
```

Quite la variable al terminar si no quiere conservarla en la sesión:

```powershell
Remove-Item Env:CI
```

## Linux / macOS shell

Configuración opcional:

```bash
cd backend
export E2E_DB_URL='jdbc:postgresql://localhost:5432/desarrollo2_grupod'
export E2E_DB_USER='postgres'
export E2E_DB_PASSWORD='postgres'
export REDIS_HOST='localhost'
export REDIS_PORT='6379'
```

Sólo tests no e2e:

```bash
./mvnw test -DexcludedGroups=e2e
```

Sólo e2e:

```bash
./mvnw test -Dgroups=e2e
```

Todos los tests y verificaciones:

```bash
./mvnw verify
```

Simular obligatoriedad de CI:

```bash
CI=true ./mvnw test -Dgroups=e2e
```

## Expected outcomes

### Servicios disponibles

- El contexto usa el perfil `e2e` y un puerto aleatorio.
- El smoke registra y autentica al usuario.
- `/players` devuelve 200 con `X-Correlation-ID`.
- `/actuator/health` devuelve 200 con estado `UP`.

### Servicio ausente localmente

El test e2e se muestra como omitido/deshabilitado. El motivo enumera PostgreSQL, Redis o ambos según corresponda. Los tests no e2e siguen ejecutándose.

### Servicio ausente con `CI=true`

El e2e no se omite. El arranque, la limpieza o el smoke falla, haciendo fallar Maven. Este es el comportamiento esperado para impedir falsos verdes.

### Credenciales incorrectas con socket abierto

La condición TCP habilita el test y el contexto falla al autenticar. Esto es intencional: el sondeo sólo comprueba que existe un listener; no valida credenciales.

## Validation matrix

| Scenario | Local expected | `CI=true` expected |
|----------|----------------|--------------------|
| PostgreSQL + Redis disponibles | smoke PASS | smoke PASS |
| PostgreSQL ausente | e2e SKIPPED con motivo | build FAIL |
| Redis ausente | e2e SKIPPED con motivo | build FAIL |
| Ambos ausentes | e2e SKIPPED nombrando ambos | build FAIL |
| Puerto abierto, credenciales DB inválidas | build FAIL al crear contexto | build FAIL |

## Coverage and CI

`./mvnw -B verify` es la ejecución canónica: corre ambos grupos en un solo ciclo y produce el reporte JaCoCo consumido por SonarCloud. Los comandos filtrados son para ciclos locales y sólo reflejan cobertura del grupo ejecutado.

No se cambia `.github/workflows/ci.yml`: ya ejecuta `./mvnw -B verify` con PostgreSQL 16 y Redis 7. GitHub Actions expone `CI=true`, por lo que una dependencia ausente falla en lugar de omitir el smoke.

## Optional alternatives requiring approval

- Separar e2e con Failsafe requiere modificar `backend/pom.xml`, adaptar descubrimiento/tags y coordinar reportes JaCoCo.
- Separar unitarios y e2e en pasos visibles requiere modificar `.github/workflows/ci.yml` y preservar la cobertura conjunta.
- Un fallback con Testcontainers puede levantar PostgreSQL 16 localmente sin instalarlo, pero requiere Docker y una decisión equivalente para Redis; no forma parte de esta implementación.

Véanse [research.md](research.md), [contrato del perfil](contracts/e2e-profile-contract.md) y [contrato HTTP](contracts/smoke-http-contract.md).
