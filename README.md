# Sistema de Valoración de Jugadores y Mercado de Tokens

Trabajo práctico de **Desarrollo de Aplicaciones**, Universidad Nacional de Quilmes (UNQ).

**Integrantes:** Abril Orlando y Guadalupe Zitterkopf.

---

## Descripción

Plataforma web que modela un mercado financiero basado en el rendimiento de futbolistas de las 5 grandes ligas de Europa (Premier League, La Liga, Serie A, Bundesliga y Ligue 1).

A partir de estadísticas de Football-Data.org y WhoScored, el sistema calcula cotizaciones periódicas para cada jugador con estrategias de valuación configurables. Los usuarios compran y venden tokens de futbolistas y siguen su portfolio e historial de inversiones.

| Entrega | Contenido | Specs |
|---|---|---|
| 1 | Seguridad (JWT y API key), registro y login, catálogo de jugadores, estadísticas de WhoScored, perfiles de test y e2e, frontend base | 001–012 |
| 2 | Cotizaciones, estrategias OFFICIAL y MOMENTUM, recálculo manual y programado, historial, ranking, mercado, portfolio y feature extra | 013–032 |
| 3 | Tests de arquitectura, auditoría de web services, Actuator y Prometheus, métricas de negocio, ranking de alta frecuencia | 033–037 |

Cada funcionalidad tiene su spec, plan y quickstart en `specs/`.
---

## Arquitectura

```text
backend/          API REST Spring Boot (Java 17) + Maven
frontend/         React + Vite
observability/    Prometheus y Grafana para la demo (Docker Compose)
specs/            Especificaciones de cada funcionalidad
docs/             Release notes
```

El backend sigue un diseño en capas, validado por los tests de arquitectura:

- **Controllers:** endpoints REST y validación de entrada.
- **Services:** lógica de negocio (mercado, valuación, portfolio).
- **Repositories:** persistencia en PostgreSQL.
- **Adapters:** integración con Football-Data.org y el scraper de WhoScored.
- **Audit y metrics:** aspecto de auditoría y métricas de negocio.

Redis se usa como caché de las fuentes externas y del ranking. La API está documentada con OpenAPI en `http://localhost:8080/swagger-ui/index.html`.

---

## Dominio

1. **Tokens:** cada jugador tiene 100 tokens emitidos a nombre de un superusuario, con valor inicial de 1 crédito.
2. **Estrategias de valuación:**
  - **OFFICIAL:** performance ponderada por posición según la fórmula de la consigna.
  - **MOMENTUM:** sigue la forma reciente; compara las últimas 5 ventanas de estadísticas con el promedio de temporada del jugador y mueve el precio como máximo ±15 %.
3. **Cotizaciones:** cada recálculo (manual o programado) guarda las dos estrategias en un historial inmutable. La estrategia **activa** fija el precio de mercado.
4. **Mercado:** órdenes de compra y venta (MARKET y LIMIT) en un libro de órdenes, con validación de saldo, tenencia y banda de precio.
5. **Portfolio:** saldo, tokens por jugador, precio promedio de compra, valuación actual y resultado.

---

## Feature extra: comparador de estrategias y oportunidades

Como cada recálculo guarda las dos estrategias, se puede comparar la activa con la alternativa sin alterar el mercado:

- **Portfolio con otra estrategia:** `GET /users/{id}/portfolio?strategy=MOMENTUM` agrega, por posición y en los totales, cuánto valdría y qué resultado tendría con esa estrategia.
- **Oportunidades:** `GET /quotes/opportunities` lista jugadores subvaluados y sobrevaluados respecto de la estrategia alternativa, con filtros de liga y posición.
- **Cambio de estrategia activa:** `PUT /strategies/active` (sólo ADMIN). El nuevo precio se aplica en el próximo recálculo.

En el frontend, un selector en el encabezado alterna álbum, detalle y portfolio entre OFFICIAL y MOMENTUM. El ranking muestra las oportunidades y, al superusuario, el control para cambiar la estrategia activa.

---

## Endpoints principales

| Recurso | Endpoints |
|---|---|
| Autenticación | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `GET /auth/me` |
| Jugadores | `GET /players`, `/players/{id}`, `/players/ranking`, `/players/{id}/quotes`, `POST /players/sync` |
| Cotizaciones | `POST /quotes/recalculate`, `GET /quotes/latest`, `/quotes/opportunities` |
| Estrategias | `GET /strategies/active`, `PUT /strategies/active` |
| Mercado | `POST /orders/buy`, `/orders/sell`, `GET /orders/book`, `/orders/mine`, `DELETE /orders/{id}` |
| Usuarios | `GET /users/{id}/portfolio`, `/users/{id}/transactions` |

Para probarlos en Swagger: ejecutar `POST /auth/login`, copiar el `token` y pegarlo en **Authorize → bearerAuth**. La API key (`X-API-KEY`) queda como alternativa para scripts e integraciones.

---

## Ejecución local

Requisitos: Java 17, Node, PostgreSQL con la base `desarrollo2_grupod` y Redis.

```env
DB_PASSWORD=tu_contrasena_de_postgresql
JWT_SECRET=un_secreto_de_al_menos_32_bytes
FOOTBALL_DATA_TOKEN=token_de_football_data
```

Opcionales: `REDIS_HOST`, `REDIS_PORT`, `FOOTBALL_DATA_CACHE_TTL` (por defecto `6h`).

```bash
# Backend (http://localhost:8080)
cd backend
./mvnw spring-boot:run

# Frontend
cd frontend
npm install
npm run dev
```

**Datos iniciales:**

- El catálogo se carga a pedido con `POST /players/sync` (con `X-API-KEY`). `GET /players` lee sólo de PostgreSQL, así que funciona aunque falle la API externa.
- Las estadísticas de WhoScored se sincronizan los lunes a las 04:00 UTC, pero vienen deshabilitadas: se activan con `WHOSCORED_SYNC_ENABLED=true`. Necesitan Chromium de Playwright instalado una vez (ver [spec 005](specs/005-whoscored-player-stats/quickstart.md)).

---

## Monitoreo y métricas (Entrega 3)

| Endpoint | Acceso |
|---|---|
| `/actuator/health` (con `/liveness` y `/readiness`), `/actuator/info` | Público |
| `/actuator/metrics`, `/actuator/prometheus` | JWT ADMIN o API key con `ROLE_ADMIN` |

Además de las métricas HTTP (latencia y errores por endpoint), se publican métricas propias del negocio con prefijo `gdmarket_`:

| Área | Métricas |
|---|---|
| Mercado | órdenes creadas y rechazadas, trades, tokens y volumen en créditos |
| Cotizaciones | duración del recálculo, cotizaciones por estrategia, cambios de estrategia activa |
| Fuentes externas | llamadas a Football-Data y WhoScored (éxito, error o caché) |
| Usuarios | registros, logins y usuarios registrados |

Para la demo, `observability/` levanta Prometheus (`http://localhost:9090`) y Grafana (`http://localhost:3000`) con el dashboard **GD Market**:

```bash
docker compose -f observability/docker-compose.yml up -d
```

Prometheus necesita una API key de scrapeo en la variable `SCRAPE_API_KEY`. El paso a paso está en los quickstarts de la [035](specs/035-actuator-prometheus/quickstart.md) y la [036](specs/036-metricas-avanzadas/quickstart.md).

---

## Auditoría de web services (Entrega 3)

Cada llamada a un endpoint deja una línea de auditoría. La lógica está en un aspecto (`audit/WebServiceAuditAspect`) que se aplica a todos los `@RestController`, sin tocar ningún controller. Cada línea incluye:

- fecha y hora, y `correlationId`;
- usuario (del JWT, de la API key o `anonymous`);
- método HTTP, ruta y `Clase.método`;
- parámetros, con contraseñas y tokens enmascarados;
- duración en ms y resultado (`OK` o la excepción).

Las líneas salen por la consola y por `logs/audit.log` (rotación diaria, 7 días). Se apaga con `audit.web-services.enabled=false` y el nivel se ajusta con `logging.level.WEB_SERVICE_AUDIT`.

---

## Tests de arquitectura (Entrega 3)

Con ArchUnit, en `backend/src/test/java/com/example/demo/architecture/`:

| Test | Qué valida |
|---|---|
| `LayeredArchitectureTest` | Dependencias permitidas entre controller, service, repository, adapter, model y dto |
| `NamingConventionTest` | Sufijos y anotaciones de controllers y repositories; `@ControllerAdvice` en `exception` |
| `CodingRulesTest` | Sin ciclos entre paquetes, sin `@Transactional` en controllers, sin `System.out` |

No levantan Spring ni la base, y corren con el resto de los tests unitarios.

---

## Ranking para alta frecuencia (Entrega 3)

`GET /players/ranking` está optimizado para muchas consultas seguidas:

- cada página se cachea en Redis y una consulta cacheada no usa la base;
- si llegan consultas iguales juntas, sólo una calcula el ranking;
- después de cada recálculo o cambio de estrategia, la primera página se vuelve a precargar;
- si Redis falla, responde desde PostgreSQL.

En local, la primera consulta tarda unos 150 ms y las siguientes, 15 a 35 ms. Los aciertos y fallos del caché se ven en Prometheus (`cache_gets_total{cache="quotes-ranking"}`). Ver el [quickstart](specs/037-ranking-alta-frecuencia/quickstart.md).

---

## Tests

Desde `backend/`:

```bash
./mvnw test -DexcludedGroups=e2e   # sólo unitarios
./mvnw test -Dgroups=e2e           # sólo e2e
./mvnw verify                      # todo, con cobertura
```

En Windows se usa `.\mvnw.cmd` con los mismos parámetros.

Los e2e usan PostgreSQL y Redis reales y **borran todos sus datos** antes y después de cada test: úsenlos sólo con servicios de prueba. Si no encuentran los servicios, se omiten localmente; en el CI nunca se omiten. Detalle en [spec 010](specs/010-perfil-e2e/quickstart.md).

El CI (`.github/workflows/ci.yml`) corre en cada PR el build y los tests del backend, los tests, el lint y el build del frontend, y el análisis de SonarCloud.
