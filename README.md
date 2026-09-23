# Sistema de Valoracion de Jugadores y Mercado de Tokens

Trabajo practico para la materia **Desarrollo de Aplicaciones** - **Universidad Nacional de Quilmes (UNQ)**.

### Integrantes

* Abril Orlando
* Guadalupe Zitterkopf

---

## Descripcion del proyecto

El sistema es una plataforma web que modela un mercado financiero basado en el rendimiento deportivo de futbolistas de las 5 grandes ligas de Europa (Premier League, La Liga, Serie A, Bundesliga y Ligue 1).

A partir de datos estadisticos y resultados de partidos, el sistema calcula cotizaciones periodicas para cada jugador mediante estrategias de valuacion configurables. Los usuarios pueden comprar y vender tokens de futbolistas, gestionando su portfolio e historial de inversiones en tiempo real.

---

## Estructura del repositorio

```text
.
+-- backend/              # API REST Spring Boot + Maven
|   +-- pom.xml
|   +-- mvnw
|   +-- mvnw.cmd
|   +-- src/
+-- frontend/             # React + Vite
|   +-- package.json
|   +-- src/
+-- .github/workflows/    # CI backend y frontend
+-- .agents/
+-- .claude/
+-- .specify/
+-- README.md
```

---

## Arquitectura y componentes

El backend sigue un diseno en capas para asegurar modularidad, mantenibilidad y tolerancia a fallos:

* **Controllers:** Exposicion de endpoints REST y validacion de entrada.
* **Services:** Logica de negocio (mercado, calculo de scores, valuacion y gestion de portfolios).
* **Repositories:** Persistencia y acceso a datos con optimizacion de indices.
* **Adapters:** Integracion con fuentes externas (API de Football-Data.org y scraper de WhoScored).

### Requisitos tecnicos y no funcionales

* **Scheduler:** Tareas automaticas para la actualizacion semanal de cotizaciones y estadisticas.
* **Cache:** Capa de almacenamiento en cache para mitigar latencia y permitir operacion resiliente ante caidas de APIs externas.
* **Auditoria y observabilidad:** Registro inmutable de transacciones financieras, logging estructurado y Correlation IDs para trazabilidad.
* **Documentacion:** Especificacion de endpoints con OpenAPI / Swagger.

---

## Dominio y modelo de negocio

1. **Emision de tokens:** Cada jugador posee un total inicial de 100 tokens emitidos en poder de un superusuario inicial, con un valor base inicial de 1 credito.
2. **Estrategias de valuacion:** Calculo de scores ponderados segun metricas de rendimiento. El sistema soporta multiples estrategias configurables con trazabilidad de version.
3. **Operaciones de mercado:** Compra y venta de tokens con validacion de liquidez, disponibilidad, tenencia y actualizacion de posiciones.
4. **Portfolio:** Visualizacion de saldo, cantidad de tokens por jugador, precio promedio de compra, valuacion actual y resultado neto.

---

## Ejecucion local

### Backend

El backend utiliza Java, Spring Boot, Maven, PostgreSQL y Redis.

Configurar localmente la contrasena de PostgreSQL mediante la variable de entorno:

```env
DB_PASSWORD=tu_contrasena_de_postgresql
JWT_SECRET=un_secreto_de_al_menos_32_bytes
FOOTBALL_DATA_TOKEN=token_de_football_data
MARKET_SUPERUSER_USERNAME=admin_preexistente
```

La cuenta indicada por `MARKET_SUPERUSER_USERNAME` debe existir previamente y tener rol
`ADMIN`; el importador nunca crea usuarios ni contraseñas por defecto. También se pueden
configurar `REDIS_HOST`, `REDIS_PORT`, `FOOTBALL_DATA_CACHE_TTL` (por defecto `6h`),
`FOOTBALL_DATA_CONNECT_TIMEOUT`, `FOOTBALL_DATA_READ_TIMEOUT`,
`FOOTBALL_DATA_SYNC_CRON`, `FOOTBALL_DATA_BOOTSTRAP_RETRY_INTERVAL` (por defecto `PT5M`,
mínimo un minuto) y `FOOTBALL_DATA_ENABLED`.

**Primer arranque.** Para registrar el superusuario por `POST /auth/register` el backend
tiene que estar corriendo, así que el orden es: levantar el backend (la primera
sincronización falla con un WARN `superuser_unavailable`), registrar el usuario, pasarlo a
`ADMIN` con `UPDATE users SET role = 'ADMIN' WHERE username = '<usuario>';` y esperar: mientras
no exista una sincronización exitosa se reintenta cada `FOOTBALL_DATA_BOOTSTRAP_RETRY_INTERVAL`,
sin reiniciar. Después del primer éxito sólo aplica `FOOTBALL_DATA_SYNC_CRON`.

**Fallas parciales.** Cada jugador se guarda en su propia transacción: si uno no puede
persistirse se descarta sólo ese, se audita `PLAYER_FAILED` en `catalog_sync_audit_events`
y la sincronización sigue (`PARTIAL_FAILURE`). Si no se guardó ningún jugador, termina `FAILED`.

La aplicacion utiliza esta variable desde `backend/src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/desarrollo2_grupod
spring.datasource.username=postgres
spring.datasource.password=${DB_PASSWORD}
```

La base de datos PostgreSQL debe existir con el nombre:

```text
desarrollo2_grupod
```

Para compilar y verificar el backend:

```bash
cd backend
./mvnw verify
```

Los tests HTTP usan `MockRestServiceServer` y nunca llaman a Football-Data.org. Los tests
de PostgreSQL y Redis usan Testcontainers y se omiten automáticamente cuando Docker no
está disponible. Flyway es la autoridad del esquema (`V1` baseline y `V2` catálogo),
mientras Hibernate sólo lo valida.

El catálogo se sincroniza al arrancar si no existe un snapshot exitoso y luego mediante
el cron configurado. `GET /players` acepta `league`, `team` y `position`, exige
`X-API-KEY` y sigue consultando exclusivamente PostgreSQL ante fallas externas. Redis
cachea una respuesta por liga con TTL configurable.

Operación y diagnóstico:

* `GET /actuator/health` expone únicamente salud agregada de aplicación, PostgreSQL y Redis.
* `/actuator/metrics` permanece protegido y contiene latencia/error de Football-Data y duración/error de sincronización.
* Todas las respuestas incluyen `X-Correlation-ID`; los jobs generan el suyo y lo incluyen en logs estructurados y auditoría append-only.
* Swagger UI está en `http://localhost:8080/swagger-ui/index.html` y documenta `apiKeyAuth`.

Una vez iniciado, el backend queda disponible en:

```text
http://localhost:8080
```

### Frontend

El frontend esta creado con React + Vite.

```bash
cd frontend
npm install
npm run dev
```

Para generar el build:

```bash
cd frontend
npm run build
```
