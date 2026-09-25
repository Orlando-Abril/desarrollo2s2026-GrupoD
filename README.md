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
```

También se pueden configurar `REDIS_HOST`, `REDIS_PORT`, `FOOTBALL_DATA_CACHE_TTL` (por
defecto `6h`), `FOOTBALL_DATA_CONNECT_TIMEOUT` y `FOOTBALL_DATA_READ_TIMEOUT`.

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

Los tests HTTP usan `MockRestServiceServer` y nunca llaman a Football-Data.org. El test
de Redis usa Testcontainers y se omite automáticamente cuando Docker no está disponible.

Catálogo de jugadores:

* La carga desde Football-Data.org se dispara a pedido con `POST /players/sync` (con
  `X-API-KEY`). Trae las 5 ligas, crea o actualiza jugadores por `externalId` y devuelve un
  resumen (`COMPLETED`, `PARTIAL_FAILURE` o `FAILED`). No hay carga automática.
* `GET /players` acepta `league`, `team` y `position`, exige `X-API-KEY` y consulta
  exclusivamente PostgreSQL, así que sigue funcionando ante fallas externas. Devuelve `[]`
  hasta la primera carga.
* Redis cachea una respuesta por liga con TTL configurable.

Operación y diagnóstico:

* `GET /actuator/health` expone únicamente salud agregada de aplicación, PostgreSQL y Redis.
* `/actuator/metrics` permanece protegido y expone las métricas estándar (por ejemplo `http.server.requests`).
* Todas las respuestas incluyen `X-Correlation-ID`, que se incluye en los logs estructurados.
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
