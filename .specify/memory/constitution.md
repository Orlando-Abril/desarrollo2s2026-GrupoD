<!--
Sync Impact Report
==================
Version change: (unversioned) → 1.1.0
Rationale: MINOR bump — no prior explicit version footer existed, so this amendment also
establishes governance versioning (Ratified/Last Amended/Version). The substantive change
(firm decision on Redis, replacing an open "Redis o In-Memory" choice) tightens an existing
mandatory rule, without removing or redefining a principle — treated as materially expanded
guidance rather than a MAJOR incompatible change.

Modified sections:
- 5.2 Capa de Caché e Índices — replaced ambiguous "Redis o In-Memory" with a firm mandate
  for Redis as the sole caching layer.

Added sections:
- Governance: amendment procedure / Sync Impact Report requirement and semantic versioning
  policy (previously implicit/undocumented).
- Version/Ratified/Last Amended footer (previously absent).

Removed sections: none.

Follow-up TODOs: none.
This report is scratch material for review of this amendment and may be removed before/at
the next amendment.
-->

# Spec Kit Constitution — Football Player Market Valuation & Token Trading System

Proyecto: Desarrollo de Aplicaciones — Universidad Nacional de Quilmes (UNQ)
Integrantes: Abril Orlando | Guadalupe Zitterkopf

---

## 1. Principios Fundamentales

### 1.1 Dominio de Mercado Financiero y Reglas de Tokens (Obligatorio)

* **Emisión Inicial:** Todo jugador integrado al sistema posee exactamente 100 tokens emitidos.
* **Estado Inicial (Momento Cero):** Un único superusuario concentra el 100% de la tenencia inicial de todos los tokens, con un precio base inicial de 1 crédito por token.
* **Operativa del Mercado:**
  * Las compras iniciales de usuarios se realizan contra la tenencia del superusuario.
  * Una orden de compra (`POST /orders/buy`) debe validar disponibilidad de tokens y aplicar la cotización vigente.
  * Una orden de venta (`POST /orders/sell`) debe validar que el usuario posea la cantidad de tokens a vender, acreditar saldo y ajustar posición.
* **Consistencia del Portfolio:** El portfolio del usuario debe reflejar en todo momento: cantidad de tokens por jugador, Precio Promedio de Compra (PPC), valuación a cotización actual, ganancia/pérdida neta e historial completo de transacciones.

### 1.2 Motor de Valuación Dinámico y Versionado (Obligatorio)

* **Multiestrategia Configurable:** El backend debe implementar al menos dos (2) estrategias configurables de ponderación del valor de los jugadores basadas en métricas de rendimiento (goles, asistencias, tiros, pases clave, gambetas, tackles, rating, minutos, tarjetas).
* **Ponderación por Posición:** Las estrategias deben contemplar pesos diferenciados según la posición del jugador (ej. mayor peso de goles/tiros en delanteros, mayor peso de tackles/intercepciones en defensores).
* **Trazabilidad de Valuación:** Cada recálculo de cotización debe persistir en el historial de cotizaciones registrando de forma explícita la versión de la estrategia utilizada.

### 1.3 Resiliencia y Desacoplamiento de Fuentes Externas (Obligatorio)

* **Fuentes Oficiales:** El sistema obtendrá datos desde WhoScored (vía scraping) y Football-Data.org (vía API REST) para las 5 grandes ligas (Premier League, La Liga, Serie A, Bundesliga, Ligue 1).
* **Tolerancia a Fallas:** El sistema debe operar con datos persistidos/cacheados localmente si las fuentes externas fallan o no están disponibles. Ninguna caída de API externa debe congelar o tumbar los endpoints de lectura ni la operativa de mercado.

---

## 2. Arquitectura y Límites entre Componentes

### 2.1 Backend en Capas Estricto (Obligatorio)

El código backend debe estar estructurado de forma modular y desacoplada en cuatro capas obligatorias:

1. **Controllers:** Exposición de contratos REST, validación de entrada (`Input Validation`) y manejo de respuestas HTTP.
2. **Services:** Lógica de negocio (reglas de mercado, motor de cotizaciones, gestión de portfolios, orden de transacciones).
3. **Repositories:** Acceso a la base de datos persistente mediante consultas e índices optimizados.
4. **Adapters:** Aislamiento de clientes de APIs externas (Football-Data.org) y scrapers (WhoScored).

### 2.2 Frontend Independiente (Obligatorio)

* Aplicación Web responsiva comunicada con el backend exclusivamente mediante protocolo HTTP/REST consumiendo la especificación OpenAPI.
* Vistas obligatorias: Catálogo con filtros (liga, equipo, posición), detalle y gráfico de evolución de cotización, ranking de jugadores, consola de trading (compra/venta de tokens) y portfolio personal.

### 2.3 Procesos Batch y Asincronía (Obligatorio)

* **Scheduler:** Implementación de un job scheduler para tareas automáticas de recálculo semanal de cotizaciones y actualización de estadísticas.
* **Recálculo Manual:** Exposición del endpoint `POST /quotes/recalculate` para disparar el proceso batch de cotización bajo demanda.

---

## 3. Calidad, Testing y CI/CD

### 3.1 Integración Continua (Obligatorio)

* Workflow de GitHub Actions configurado para ejecutar el build y los tests automáticos en cada Push / Pull Request.
* El estado del build en GitHub Actions debe ser de forma permanente `SUCCESS`.

### 3.2 Control de Calidad en SonarCloud (Obligatorio)

* Proyecto registrado en SonarCloud integrado al pipeline de CI.
* El análisis de SonarCloud debe cumplir el Quality Gate y mantener la cantidad de issues por debajo del umbral exigido por la materia (máximo 10 issues menores).

### 3.3 Cobertura de Escenarios de Prueba de Dominio (Obligatorio)

Los suites de testing unitario e integración deben verificar los escenarios clave de la materia:

1. Construcción de la base de datos de jugadores a partir de datos mock/externos.
2. Evolución temporal de cotizaciones para jugadores de las 5 ligas.
3. Simulación transaccional con al menos 4 usuarios operando sobre 5 jugadores y cálculo correcto de valuación de portfolios.

---

## 4. Seguridad y Auditoría Financiera

### 4.1 Autenticación y Autorización (Obligatorio)

* **Manejo de Tokens / Claves:** Implementación de JWT para la autenticación de usuarios y generación de API Keys para consumo autorizado de los endpoints protegidos.
* **Creación de Usuarios:** Endpoint público para registro de usuario y emisión de API Key inicial.

### 4.2 Registro Inmutable de Auditoría (Obligatorio)

* Todas las transacciones financieras (compra y venta de tokens, ajustes de saldo) deben registrarse en una tabla/log de auditoría inmutable.
* **Campos obligatorios por auditoría:** Identificador del autor (User ID), marca de tiempo (timestamp), detalle del cambio, estado anterior y estado posterior.

### 4.3 Validación Estricta de Datos (Obligatorio)

* `Input Validation` estricto en el controller para prevenir inconsistencias financieras (ej. montos negativos, compras sin saldo, ventas de tokens no poseídos).

---

## 5. Gestión de Datos, Caché y APIs

### 5.1 Catálogo y Endpoints Mínimos Obligatorios (Obligatorio)

La API Backend debe exponer sin excepción los siguientes contratos:

* `GET /players` (Listado con filtros obligatorios por liga, equipo y posición).
* `GET /players/:id` (Detalle del jugador).
* `GET /players/:id/quotes` (Historial de cotizaciones).
* `GET /players/ranking` (Ranking según estrategia activa).
* `POST /quotes/recalculate` (Ejecución del job de recálculo).
* `POST /orders/buy` (Orden de compra de tokens).
* `POST /orders/sell` (Orden de venta de tokens).
* `GET /users/:id/portfolio` (Estado de portfolio y rendimiento).
* `GET /users/:id/transactions` (Historial de operaciones del usuario).

### 5.2 Capa de Caché e Índices (Obligatorio)

* **Caché Mandatorio (Redis):** El sistema debe implementar una capa de caché basada en **Redis** de forma obligatoria, para optimizar consultas frecuentes del catálogo y funcionar de fallback ante caídas externas.
* **Indexación:** Definición de índices en base de datos para optimizar búsquedas por liga, equipo, posición, historial de cotizaciones y transacciones.

### 5.3 Observabilidad (Obligatorio)

* **Logs Estructurados:** Formato uniforme de logs en JSON/estructurado.
* **Correlation IDs:** Propagación de ID de correlación en los headers HTTP para trazabilidad de cada request.
* **Health Check & Métricas:** Endpoint de salud (`/actuator/health` o equivalente) y monitoreo de latencia y tasa de errores.

---

## 6. Documentación

### 6.1 Especificación OpenAPI / Swagger (Obligatorio)

* Documentación interactiva mediante OpenAPI v3 / Swagger UI accesible desde el backend.
* Todos los endpoints, DTOs de entrada/salida, códigos de respuesta HTTP y esquemas de seguridad (Bearer JWT / API Key) deben estar completamente anotados.

---

## 7. Criterios de Aceptación (Definition of Done - DoD)

Una feature se considera terminada para este proyecto únicamente cuando:

1. **Código y Capas:** Respeta la separación Controllers-Services-Repositories-Adapters.
2. **Build y CI:** El pipeline de GitHub Actions compila y ejecuta los tests finalizando en `SUCCESS`.
3. **SonarCloud:** No introduce vulnerabilidades ni duplica código, manteniendo el análisis en `PASSED` y < 10 issues.
4. **Documentación:** El endpoint está visible y probado en Swagger UI con esquemas de autenticación funcionales.
5. **Auditoría / Logs:** Toda operación de estado/financiera genera log de auditoría inmutable con Correlation ID.
6. **Resiliencia:** Cuenta con tests que garanticen respuesta aunque falle la integración externa.

---

## 8. Gobernanza de la Constitution

* Esta Constitution es la norma suprema del repositorio. Cualquier cambio en sus reglas requiere la aprobación explícita de ambas integrantes del equipo (**Abril Orlando** y **Guadalupe Zitterkopf**).
* Toda enmienda debe documentarse mediante un Sync Impact Report (versión anterior → nueva, secciones/principios modificados, agregados o eliminados) y actualizar la fecha de última enmienda.
* Versionado semántico: MAJOR ante eliminación o redefinición incompatible de principios/secciones; MINOR ante agregado o expansión material de una regla o justificación; PATCH ante aclaraciones o correcciones de redacción sin cambio de fondo.

**Version**: 1.1.0 | **Ratified**: 2026-09-15 | **Last Amended**: 2026-09-15
