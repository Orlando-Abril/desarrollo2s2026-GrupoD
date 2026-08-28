# Sistema de Valoración de Jugadores y Mercado de Tokens

Trabajo práctico para la materia **Desarrollo de Aplicaciones** — **Universidad Nacional de Quilmes (UNQ)**.

### Integrantes
* Abril Orlando
* Guadalupe Zitterkopf

---

## 📌 Descripción del Proyecto

El sistema es una plataforma web (Backend REST + Frontend) que modela un mercado financiero basado en el rendimiento deportivo de futbolistas de las 5 grandes ligas de Europa (Premier League, La Liga, Serie A, Bundesliga y Ligue 1).

A partir de datos estadísticos y resultados de partidos, el sistema calcula cotizaciones periódicas para cada jugador mediante estrategias de valuación configurables. Los usuarios pueden comprar y vender tokens de futbolistas, gestionando su portfolio e historial de inversiones en tiempo real.

---

## ⚙️ Arquitectura y Componentes

El backend sigue un diseño en capas para asegurar modularidad, mantenibilidad y tolerancia a fallos:

* **Controllers:** Exposición de endpoints REST y validación de entrada.
* **Services:** Lógica de negocio (mercado, cálculo de scores, valuación y gestión de portfolios).
* **Repositories:** Persistencia y acceso a datos con optimización de índices.
* **Adapters:** Integración con fuentes externas (API de Football-Data.org y scraper de WhoScored).

### Requisitos Técnicos y No Funcionales
* **Scheduler:** Tareas automáticas (jobs batch) para la actualización semanal de cotizaciones y estadísticas.
* **Caché:** Capa de almacenamiento en caché para mitigar latencia y permitir operación offline/resiliente ante caídas de APIs externas.
* **Auditoría y Observabilidad:** Registro inmutable de transacciones financieras, logging estructurado y Correlation IDs para trazabilidad.
* **Documentación:** Especificación de endpoints con OpenAPI / Swagger.

---

## ⚽ Dominio y Modelo de Negocio

1. **Emisión de Tokens:** Cada jugador posee un total inicial de 100 tokens emitidos en poder de un superusuario inicial, con un valor base inicial de 1 crédito.
2. **Estrategias de Valuación:** Cálculo de scores ponderados según métricas de rendimiento (goles, asistencias, tiros, pases clave, intercepciones, tackles, rating, minutos jugados, tarjetas). El sistema soporta múltiples estrategias configurables con trazabilidad de versión.
3. **Operaciones de Mercado:** 
   * **Compra:** Validación de liquidez y disponibilidad de tokens a la cotización vigente.
   * **Venta:** Validación de tenencia de tokens, liquidación de saldo y actualización de posición.
4. **Portfolio:** Visualización de saldo, cantidad de tokens por jugador, precio promedio de compra (PPC), valuación actual y resultado neto (ganancia/pérdida).

---

## 🚀 Instalación y Puesta en Marcha

### Pasos para ejecución local

1. **Clonar el repositorio:**

   ```bash
   git clone https://github.com/Orlando-Abril/desarrollo2s2026-GrupoD.git
   cd desarrollo2s2026-GrupoD
   ```

2. **Configurar variables de entorno:**

   Configurar localmente la contraseña de PostgreSQL mediante la variable de entorno:

   ```env
   DB_PASSWORD=tu_contraseña_de_postgresql
   ```

   En IntelliJ IDEA se puede configurar desde:

   ```text
   Run → Edit Configurations → DemoApplication → Modify options → Environment variables
   ```

   La aplicación utiliza esta variable desde `application.properties`:

   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/desarrollo2_grupod
   spring.datasource.username=postgres
   spring.datasource.password=${DB_PASSWORD}
   ```

3. **Instalar dependencias y levantar el servicio:**

   El proyecto utiliza **Java 22, Spring Boot, Maven y PostgreSQL**.

   Maven descargará automáticamente las dependencias definidas en `pom.xml`.


4. **Acceso al servicio:**

   Una vez iniciado el backend, estará disponible en:

   ```text
   http://localhost:8080
   ```

   La aplicación requiere tener creada previamente una base de datos PostgreSQL llamada:

   ```text
   desarrollo2_grupod
   ```
