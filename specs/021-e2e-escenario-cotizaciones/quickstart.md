# Quickstart: E2E del escenario de evolución de cotizaciones

**Feature**: `specs/021-e2e-escenario-cotizaciones`

Hay dos formas de validar el escenario:
- **§1–§2, el e2e automático:** es la verificación oficial.
- **§3, reproducirlo a mano en Swagger:** para la demo.

## 1. Requisitos

- **PostgreSQL** con una base **dedicada a pruebas**. El perfil e2e hace `create-drop` y `TRUNCATE` de todas las tablas, así que no hay que usar la base de trabajo.
- **Redis** en `localhost:6379`. En Windows es el servicio **Memurai**: si los e2e se saltean con "Redis ... unavailable", iniciarlo desde `services.msc` o con `Start-Service Memurai` en un PowerShell de administrador.

## 2. Correr el e2e

Desde `backend/`, en PowerShell:

```powershell
$env:E2E_DB_URL = "jdbc:postgresql://localhost:5432/desarrollo2_grupod_e2e"
$env:E2E_DB_PASSWORD = "<contraseña de postgres>"
.\mvnw.cmd "-Dtest=QuoteEvolutionE2ETest" test
```

**Resultado esperado**: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`. Si dice `Skipped: 1`, no corrió: revisar el motivo en `target/surefire-reports`.

En el log se ven tres líneas `quote_recalculation_finished`, una por fecha:
- **Primera:** MOMENTUM con `insufficientHistory=5`.
- **Segunda y tercera:** MOMENTUM con `calculated=5`.
- **Las tres:** `skipped=[]`, `failed=0` y `marketValueUpdated=true`.

Las aserciones están detalladas en [contracts/scenario-assertions.md](contracts/scenario-assertions.md) y los datos en [data-model.md](data-model.md).

Suite completa:

```powershell
.\mvnw.cmd test
```

## 3. Reproducir el escenario a mano en Swagger (demo)

El backend corre con el reloj real, así que las tres corridas caen en el mismo día. El historial muestra igual las tres cotizaciones y su evolución. La consulta por fecha (`at`) de cada día distinto sólo se puede mostrar si las corridas se hacen en días distintos; para eso está el e2e.

### 3.1 Levantar el backend

Con PostgreSQL y Memurai andando, desde `backend/`:

```powershell
$env:DB_PASSWORD = "<contraseña de postgres>"
$env:JWT_SECRET = "<secreto de al menos 32 bytes>"
$env:FOOTBALL_DATA_TOKEN = "<token>"
.\mvnw.cmd spring-boot:run
```

Abrir `http://localhost:8080/swagger-ui/index.html`.

### 3.2 Usuario ADMIN (igual que el quickstart de 016)

1. En Swagger, `POST /auth/register` con `{"username": "demo-admin", "email": "demo-admin@example.com", "password": "demo-admin-123"}`. Copiar la `apiKey` de la respuesta.
2. En la base:
   ```sql
   UPDATE users SET role = 'ADMIN' WHERE username = 'demo-admin';
   ```
3. En Swagger, botón **Authorize**: pegar la `apiKey` en `apiKeyAuth (X-API-KEY)`.

### 3.3 Elegir un jugador por liga

Con `GET /players?league=PREMIER_LEAGUE` (y lo mismo para `LA_LIGA`, `SERIE_A`, `BUNDESLIGA` y `LIGUE_1`), anotar un `id` por liga. Conviene que sea un jugador con estadísticas cargadas:

```sql
SELECT p.id, p.league, s.minutes_played, s.rating
FROM players p JOIN player_stats s ON s.player_id = p.id
WHERE p.id IN (<ids>) AND s.rating IS NOT NULL;
```

Elegir uno como **el que mejora** y otro como **el que deja de jugar**, igual que Premier y Bundesliga en el e2e.

### 3.4 Primera fecha

`POST /quotes/recalculate` sin cuerpo:
- `skippedStrategies` vacío y `playersFailed` 0;
- MOMENTUM en `insufficientHistory`, porque es el primer snapshot.

### 3.5 Simular una semana (repetir dos veces)

Cada actualización tiene que cambiar `fetched_at`. Si no, no se registra un snapshot nuevo y MOMENTUM conserva el valor.

```sql
-- El que mejora: juega, convierte y sube el rating
UPDATE player_stats SET minutes_played = minutes_played + 90, goals = goals + 2, assists = assists + 1,
       shots = shots + 4, key_passes = key_passes + 2, rating = LEAST(rating + 0.30, 10), fetched_at = now()
WHERE player_id = <id que mejora>;

-- El que deja de jugar: datos actualizados, mismos minutos
UPDATE player_stats SET fetched_at = now() WHERE player_id = <id que deja de jugar>;

-- El resto: un partido normal
UPDATE player_stats SET minutes_played = minutes_played + 90, fetched_at = now()
WHERE player_id IN (<otros ids>);
```

Después, otra vez `POST /quotes/recalculate`. MOMENTUM queda en `calculated`.

**Advertencia**: en la demo, el jugador que deja de jugar sólo baja en MOMENTUM si su forma reciente no venía muy por encima de su promedio. Lo explica [research.md](research.md) R4.

### 3.6 Mostrar la evolución

- **Historial:** `GET /players/{id}/quotes?strategy=OFFICIAL` y `?strategy=MOMENTUM` muestran 3 cotizaciones con `version` 1.
  - La primera MOMENTUM es `INSUFFICIENT_HISTORY` con valor 1.0000.
  - El que mejora sube; el que deja de jugar baja en MOMENTUM.
- **Cotización vigente:** `GET /players/{id}/quotes?strategy=MOMENTUM&at=<hoy>` devuelve la última del día. Con `at=<ayer>` da 404 `quote_not_found`.
- **Ranking:** `GET /players/ranking?strategy=MOMENTUM` y `GET /players/ranking` (OFFICIAL, la activa) ordenan por la última cotización.
- **Valor de mercado:** `GET /players?league=<liga>` muestra en el `marketValue` de cada jugador elegido la última cotización OFFICIAL. `GET /players/{id}` todavía no existe.

## 4. Resultados

Ejecución del 2026-10-26 en Windows, desde `backend/`, con Memurai y la base `desarrollo2_grupod_e2e`:

| Tarea | Comando | Resultado |
|-------|---------|-----------|
| T011, T013, T016 | `.\mvnw.cmd "-Dtest=QuoteEvolutionE2ETest" test` | `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`. Las aserciones de las tres historias están en el mismo método y pasaron en la primera ejecución. |
| Control de aserciones | Se invirtió a propósito la aserción "Bundesliga baja en MOMENTUM" | El test falló donde correspondía, con valor real 0.8469. Después se restauró. |
| T017 | 10 ejecuciones seguidas del mismo comando | 10/10 con `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`. |
| T018 | `.\mvnw.cmd test` | `Tests run: 607, Failures: 0, Errors: 0, Skipped: 2`. Los dos salteados son `FootballDataRedisIntegrationTest` y `WhoScoredRedisCacheIntegrationTest`, que usan Testcontainers y necesitan Docker. |

Log de las corridas (`quote_recalculation_finished`):
- **Primera:** OFFICIAL `calculated=5` y MOMENTUM `insufficientHistory=5`.
- **Segunda y tercera:** las dos estrategias con `calculated=5`.
- **Las tres:** `skipped=[]`, `failed=0` y `marketValueUpdated=true`.

El único valor exacto observado, 0.8469 (MOMENTUM de Bundesliga en la tercera fecha), coincide con la tabla de referencia de research R4.

**Pendiente**: verificar en el PR que el job de CI ejecutó `QuoteEvolutionE2ETest` sin saltearlo (SC-007). La demo manual de §3 no se ejecutó.
