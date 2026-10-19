# Quickstart: Validación de snapshots de estadísticas

## Prerrequisitos

- JDK 17.
- Docker o servicios locales de PostgreSQL 16 y Redis 7 para las pruebas e2e.
- Rama `014-stats-snapshots` con la implementación terminada.
- Ejecutar comandos desde `backend/`.

La estructura esperada de datos y las fórmulas están en [data-model.md](data-model.md); las decisiones y alternativas están en [research.md](research.md).

## 1. Compilar y ejecutar la suite rápida

En PowerShell:

```powershell
./mvnw.cmd test
```

Resultado esperado: compila con Java 17 y pasan las pruebas unitarias y JPA. Si PostgreSQL/Redis e2e no están disponibles, las pruebas marcadas `@E2ETest` se omiten mediante el soporte existente.

## 2. Validar el cálculo de ventanas

```powershell
./mvnw.cmd -Dtest=StatsSnapshotServiceTest test
```

Resultado esperado:

- un solo snapshot produce cero ventanas;
- `null` en cualquier extremo se conserva como `null`;
- un delta negativo de contador produce cero;
- un rating acumulado que baja se reconstruye por minutos, no se resta;
- el rating se acota a `[0,10]` y queda `null` sin incremento positivo de minutos;
- una baja de minutos marca reinicio y copia los totales nuevos.

## 3. Validar persistencia liviana e idempotencia

```powershell
./mvnw.cmd "-Dtest=PlayerStatsSnapshotRepositoryTest,StatsSnapshotPersistenceTest" test
```

Resultado esperado:

- todos los campos, incluido `dribbles`, se guardan y leen sin alterar `null`;
- dos llamadas de `snapshotAll` sobre el mismo `fetchedAt` informan primero guardado y luego salteado, manteniendo una sola fila;
- todos los snapshots de una corrida comparten el instante del `Clock.fixed`;
- los últimos N se seleccionan con orden determinístico;
- una segunda fila con el mismo `(player_id, source_fetched_at)` es rechazada;
- el repositorio no ofrece operaciones de actualización o borrado.

## 4. Validar PostgreSQL real

Levantar PostgreSQL y Redis según el entorno habitual del proyecto y definir, si difieren de los valores por defecto:

```powershell
$env:E2E_DB_URL = "jdbc:postgresql://localhost:5432/desarrollo2_grupod"
$env:E2E_DB_USER = "postgres"
$env:E2E_DB_PASSWORD = "postgres"
$env:REDIS_HOST = "localhost"
$env:REDIS_PORT = "6379"
./mvnw.cmd -Dtest=PlayerStatsSnapshotE2ETest test
```

Resultado esperado:

- `E2EFixtures` crea jugadores canónicos y la limpieza común aísla cada prueba;
- la consulta devuelve exactamente los últimos N snapshots en orden cronológico;
- PostgreSQL rechaza el duplicado de `(player_id, source_fetched_at)`;
- dos capturas concurrentes sincronizadas dejan una sola fila y resultados complementarios (una guardada y una salteada);
- existe `idx_player_stats_snapshots_player_captured`;
- no se realiza ninguna llamada a WhoScored.

## 5. Validación completa antes del merge

```powershell
./mvnw.cmd verify
```

Resultado esperado: build y tests en verde, sin cambios en `PlayerStats.java`, `PlayerStatsService.java`, `PlayerStatsRepository.java`, `MomentumParameters.java` ni en los algoritmos MOMENTUM.

Comprobación sugerida desde la raíz del repositorio:

```powershell
git diff -- backend/src/main/java/com/example/demo/model/PlayerStats.java backend/src/main/java/com/example/demo/service/PlayerStatsService.java backend/src/main/java/com/example/demo/repository/PlayerStatsRepository.java backend/src/main/java/com/example/demo/service/valuation/strategy/MomentumParameters.java
```

Resultado esperado: sin salida.

## Resultado de validación de esta implementación

- Las pruebas unitarias y JPA de la feature pasan.
- `PlayerStatsSnapshotE2ETest` compila y se descubre, pero sus 3 casos se omiten cuando PostgreSQL/Redis no están disponibles; incluye la carrera concurrente coordinada con `CountDownLatch`.
- En el entorno Windows usado para implementar, `verify` ejecutó 379 pruebas: 351 pasaron, 26 se omitieron por servicios externos y 2 pruebas preexistentes de `PlayerStatsServiceTest` fallaron por sustitución de caracteres UTF-8 en la captura global de consola. Ejecutar esa clase de forma aislada con encoding UTF-8 pasa sus 39 casos.
