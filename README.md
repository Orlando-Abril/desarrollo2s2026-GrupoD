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
```

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
