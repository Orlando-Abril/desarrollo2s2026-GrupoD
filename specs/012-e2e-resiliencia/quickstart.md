# Quickstart: Tests e2e de resiliencia

## Safety

`@E2ETest` vacía todas las tablas del esquema PostgreSQL configurado y ejecuta `FLUSHDB` sobre Redis antes y después de cada método. Use únicamente infraestructura dedicada a pruebas. No apunte las variables a datos que necesite conservar.

Los comandos se ejecutan desde `backend/`.

## Prerequisites

- Java 17.
- PostgreSQL 16 accesible y dedicado a e2e.
- Redis 7 accesible y dedicado a e2e.
- La implementación contiene `E2EFixtures` y `ExternalSourcesDownE2ETest` según este plan.
- No se necesita Internet: Football-Data y WhoScored permanecen en destinos loopback inválidos.

Defaults del perfil:

| Variable | Default |
|----------|---------|
| `E2E_DB_URL` | `jdbc:postgresql://localhost:5432/desarrollo2_grupod` |
| `E2E_DB_USER` | `postgres` |
| `E2E_DB_PASSWORD` | `postgres` |
| `REDIS_HOST` | `localhost` |
| `REDIS_PORT` | `6379` |

## Run only the resilience class

### Windows PowerShell

```powershell
cd backend
.\mvnw.cmd test '-Dtest=ExternalSourcesDownE2ETest'
```

### Linux / macOS

```bash
cd backend
./mvnw test -Dtest=ExternalSourcesDownE2ETest
```

Expected result: siete tests aprobados. Si PostgreSQL o Redis están ausentes fuera de CI, la condición e2e vigente puede omitir la clase con el servicio faltante indicado.

## Run all e2e tests

### Windows PowerShell

```powershell
cd backend
.\mvnw.cmd test '-Dgroups=e2e'
```

### Linux / macOS

```bash
cd backend
./mvnw test -Dgroups=e2e
```

Expected result: el smoke existente, las verificaciones del perfil/limpieza y los siete escenarios de resiliencia conviven sin residuos entre métodos.

## Full verification

### Windows PowerShell

```powershell
cd backend
.\mvnw.cmd verify
```

### Linux / macOS

```bash
cd backend
./mvnw verify
```

Esta es la validación final para CI y cobertura conjunta.

## Expected resilience outcomes

| Scenario | Expected result |
|----------|-----------------|
| GET sin filtros con fuentes caídas | 200, cinco fixtures locales, ≤100 ms |
| GET por liga | 200, sólo Premier League, ≤100 ms |
| GET por equipo | 200, sólo Real Madrid, ≤100 ms |
| GET por posición | 200, los dos midfielders esperados, ≤100 ms |
| GET con liga + equipo + posición | 200, una coincidencia exacta, ≤100 ms |
| POST sync con Redis vacío | 200 + `FAILED`, cinco ligas fallidas, catálogo idéntico, ≤500 ms |
| POST sync con cinco entradas cacheadas | 200 + `COMPLETED`, cinco procesados, cero fallas, ≤500 ms |

Ningún escenario acepta respuestas 5xx.

## Cache validation

El escenario cacheado debe preparar los valores mediante el `CacheManager` del contexto y `CacheConfig.COMPETITION_TEAMS_CACHE`. No escriba JSON o bytes directamente en Redis y no instancie un serializer alternativo. El uso del caché configurado es lo que garantiza compatibilidad con `JacksonJsonRedisSerializer<FootballDataResponse>`.

Una ejecución exitosa debe mostrar:

- `processed=5`.
- `failedLeagues=0`.
- Cinco jugadores persistidos con los datos cacheados.
- Duración del POST no mayor a 500 ms.

Con el destino externo inválido, esos resultados no pueden obtenerse si alguna clave falta o si el valor no puede deserializarse con el tipo esperado.

## Troubleshooting

- **Clase omitida**: revise PostgreSQL y Redis. Fuera de CI, la condición e2e omite cuando no puede abrir alguno de los sockets.
- **Contexto falla con credenciales**: el socket está abierto, pero las credenciales o la base son incorrectas; ajuste `E2E_DB_*`.
- **Sync cacheado devuelve `PARTIAL_FAILURE` o `FAILED`**: confirme las cinco claves, sus códigos exactos y que fueron escritas mediante el caché configurado.
- **Sync fallido devuelve `COMPLETED`**: hay entradas residuales en Redis o el escenario agregó caché; confirme que la limpieza ocurre antes de cada método.
- **Sólo falla un límite de duración**: registre la duración y revise saturación local/CI. El límite es 100 ms para GET tras calentamiento y 500 ms para POST sync. No aumente los límites ni cambie timeouts productivos sin modificar primero la especificación aprobada.
- **Orden distinto de jugadores**: el endpoint no promete orden; compare por `externalId` después de ordenar.

## Protected-files check

Antes de finalizar la implementación, desde la raíz:

```powershell
git diff --exit-code -- backend/pom.xml .github/workflows/ci.yml backend/src/main/resources/application.properties backend/src/test/resources/application.properties backend/src/test/resources/application-e2e.properties backend/src/main/java/com/example/demo/config/CacheConfig.java backend/src/main/java/com/example/demo/config/SecurityConfig.java backend/src/main/java/com/example/demo/filter backend/src/main/java/com/example/demo/controller
```

El comando debe terminar sin diferencias. Si aparece una necesidad real de modificar uno de esos archivos, detenga la implementación y aplique FR-018.

## References

- [Decisiones de investigación](research.md)
- [Modelo de datos de prueba](data-model.md)
- [Contrato del helper](contracts/e2e-fixtures-contract.md)
- [Contrato HTTP de resiliencia](contracts/resilience-http-contract.md)
