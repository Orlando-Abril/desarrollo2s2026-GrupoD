# Feature Specification: Monitoreo con Actuator y Prometheus

**Feature Branch**: `feature/actuator-prometheus`

**Created**: 2026-11-12

**Status**: Draft

**Input**: User description: "SPECIFY_FEATURE_DIRECTORY=specs/035-actuator-prometheus. Exponer health, info, metrics y prometheus; health público con liveness y readiness, detalles sólo para ADMIN; info con nombre, versión y fecha de build; metrics y prometheus sólo para ADMIN o API key; métricas HTTP con percentiles e histograma y etiqueta application; demo en observability/ con Prometheus y Grafana."

## Contexto y límites

La Entrega 3 necesita que quien opera la aplicación pueda comprobar su estado, identificar el build desplegado y observar latencia y errores por endpoint. Se conserva la autenticación existente mediante JWT o `X-API-KEY`; la clave destinada al scrape debe tener `ROLE_ADMIN`. Los logs JSON y el ID de correlación existentes continúan formando parte de la observabilidad definida por la Constitution §5.3.

Esta feature se limita a las cuatro familias de endpoints de monitoreo solicitadas y a una demo local opcional. No incluye alertas, tracing ni métricas de negocio (feature 036). La implementación sólo puede modificar `backend/src/main/resources/application.properties` en propiedades `management.*` y `springdoc.show-actuator`, las reglas de `/actuator/**` en `SecurityConfig`, `backend/pom.xml`, `README.md` para la sección Monitoreo y `HealthEndpointTest` si cambia lo que verifica, además de agregar archivos nuevos bajo `observability/` y pruebas de esta feature. Se autoriza modificar exclusivamente `shouldNotFilter` de `JwtAuthFilter.java` y `ApiKeyAuthFilter.java` para autenticar de forma opcional `/actuator/health` y sus subrutas cuando se presenta la credencial correspondiente; sin credenciales continúan públicas. Si la propiedad de springdoc no basta para documentar los esquemas de JWT/API key como alternativas, los códigos de respuesta y los esquemas de respuesta, queda autorizado únicamente un `GroupedOpenApi` nuevo y acotado a `/actuator/**` en `backend/src/main/java/com/example/demo/config/`. No altera otros archivos de entregas anteriores ni la Constitution.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consultar estado y disponibilidad (Priority: P1)

Como operador, quiero consultar el estado general y los estados de vida y preparación sin credenciales para saber si la aplicación está activa y lista, mientras los detalles de sus dependencias quedan reservados a administradores.

**Why this priority**: La comprobación de disponibilidad es la necesidad mínima de monitoreo y debe funcionar aun sin una sesión de usuario.

**Independent Test**: Consultar `/actuator/health`, `/actuator/health/liveness` y `/actuator/health/readiness` sin credenciales y repetir la consulta general con JWT y API keys reales de ADMIN y USER, además de un `Bearer` inválido y una API key inválida en solicitudes separadas; no usar autenticación simulada.

**Acceptance Scenarios**:

1. **Given** la aplicación iniciada y sus dependencias disponibles, **When** se consulta cualquiera de las tres rutas de salud sin credenciales, **Then** se recibe su estado y no se revelan detalles de base de datos, Redis ni disco.
2. **Given** un JWT con rol ADMIN o una API key válida con `ROLE_ADMIN`, **When** se consulta `/actuator/health`, **Then** se muestran el estado general y los detalles de base de datos, Redis y disco.
3. **Given** un JWT de usuario sin rol ADMIN, **When** se consulta `/actuator/health`, **Then** se muestra el estado general sin detalles de componentes.
4. **Given** la aplicación en estado de no preparada, **When** se consulta la ruta de readiness, **Then** se informa ese estado sin divulgar detalles a quien no sea ADMIN.
5. **Given** sólo `Authorization: Bearer <token>` con token inválido o vencido, o sólo un `X-API-KEY` no vacío que no existe o está inactivo, **When** se consulta una ruta de health, **Then** se responde 401 sin revelar detalles de componentes.
6. **Given** una ruta pública diferente de health, **When** se consulta con o sin credenciales, **Then** conserva el comportamiento de autenticación y respuesta anterior a esta feature.

---

### User Story 2 - Identificar el build desplegado (Priority: P2)

Como integrante del equipo, quiero conocer el nombre, la versión y la fecha de build de la aplicación para verificar qué entrega está ejecutándose.

**Why this priority**: La identidad del build da contexto a los estados y métricas observados.

**Independent Test**: Consultar `/actuator/info` sin credenciales y verificar los tres datos.

**Acceptance Scenarios**:

1. **Given** una solicitud sin credenciales, **When** se consulta `/actuator/info`, **Then** la respuesta incluye nombre, versión y fecha de build no vacíos correspondientes al artefacto ejecutado.

---

### User Story 3 - Analizar latencia y errores por endpoint (Priority: P2)

Como operador ADMIN, quiero consultar métricas de solicitudes y una salida que pueda recolectar Prometheus para identificar latencia y tasa de errores por endpoint.

**Why this priority**: Cumple la observabilidad de latencia y errores exigida por la Constitution §5.3.

**Independent Test**: Generar solicitudes exitosas y fallidas a una ruta de la API; consultar `/actuator/metrics` y `/actuator/prometheus` con JWT ADMIN y API key, y verificar restricciones con usuario común y anónimo.

**Acceptance Scenarios**:

1. **Given** solicitudes exitosas y fallidas a una misma ruta, **When** ADMIN consulta las métricas HTTP, **Then** puede distinguir la ruta y el resultado de las solicitudes y observar distribución de duración mediante percentiles e histograma.
2. **Given** una API key válida con `ROLE_ADMIN`, **When** consulta `/actuator/metrics` o `/actuator/prometheus`, **Then** recibe las métricas autorizadas.
3. **Given** un JWT de usuario sin rol ADMIN, **When** consulta cualquiera de esas dos rutas, **Then** recibe 403; sin credenciales recibe 401.
4. **Given** una consulta autorizada a `/actuator/prometheus`, **When** se recibe la respuesta, **Then** tiene formato Prometheus y cada serie expuesta incluye la etiqueta `application` con el nombre de la aplicación.

---

### User Story 4 - Demostrar recolección y visualización local (Priority: P3)

Como integrante del equipo, quiero levantar una demo con Prometheus y Grafana ya enlazados para comprobar la recolección y explorar las métricas sin preparar manualmente el datasource.

**Why this priority**: Facilita la demostración de la feature después de comprobar los endpoints de la aplicación.

**Independent Test**: Iniciar la aplicación y la demo local, verificar que Prometheus recolecta métricas con una credencial autorizada y que Grafana dispone de Prometheus como datasource; iniciar luego la aplicación sin la demo.

**Acceptance Scenarios**:

1. **Given** la aplicación y la demo activas con credenciales válidas, **When** Prometheus recolecta las métricas, **Then** el objetivo de la aplicación figura disponible y las series se pueden consultar en Grafana mediante el datasource preconfigurado.
2. **Given** la demo detenida, **When** se inicia la aplicación, **Then** ésta puede arrancar y atender sus endpoints sin depender de Prometheus ni Grafana.

### Edge Cases

- Una API key ausente o inválida no da acceso a métricas ni detalles administrativos.
- Un `Bearer` con token inválido o vencido o una API key no vacía inexistente o inactiva, enviados individualmente a health, producen 401; sin credenciales health sigue siendo público.
- Headers `Authorization` con otros esquemas y solicitudes que combinan JWT y API key están fuera del alcance de esta feature y conservan el comportamiento previo de la cadena de filtros.
- La salud general puede mostrar un estado degradado cuando falla una dependencia; los detalles de esa dependencia siguen ocultos para usuarios no ADMIN.
- Una ruta de Actuator diferente de health, info, metrics o prometheus, incluidas `env`, `beans` y `heapdump`, no se expone aunque el solicitante sea ADMIN.
- Las solicitudes HTTP con error también contribuyen a las métricas necesarias para calcular la tasa de errores por endpoint.
- La demo no puede recolectar `/actuator/prometheus` sin una credencial válida y no debe exigir que Prometheus o Grafana estén activos para iniciar la aplicación.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: La aplicación MUST exponer únicamente `/actuator/health` (incluidas sus rutas liveness y readiness), `/actuator/info`, `/actuator/metrics` y `/actuator/prometheus` como endpoints de monitoreo.
- **FR-002**: `/actuator/health`, `/actuator/health/liveness` y `/actuator/health/readiness` MUST ser accesibles sin credenciales y mostrar su estado sin detalles de componentes a solicitantes no ADMIN; un `Bearer` con token inválido o vencido o una API key no vacía inexistente o inactiva, enviados individualmente, MUST recibir 401.
- **FR-003**: `/actuator/health` MUST mostrar detalles de base de datos, Redis y disco sólo cuando la solicitud esté autenticada con rol ADMIN, incluido el acceso mediante API key válida con ese rol.
- **FR-004**: `/actuator/info` MUST ser público y mostrar nombre, versión y fecha de build de la aplicación.
- **FR-005**: `/actuator/metrics` y `/actuator/prometheus` MUST permitir acceso sólo a JWT o API key con rol ADMIN; solicitudes anónimas y de usuarios sin ese rol MUST recibir 401 y 403, respectivamente.
- **FR-006**: `/actuator/prometheus` MUST entregar métricas en formato Prometheus y todas las series expuestas MUST incluir una etiqueta `application` con el nombre de la aplicación.
- **FR-007**: Las métricas de solicitudes HTTP MUST permitir distinguir endpoint y resultado, calcular tasa de errores y observar latencia mediante percentiles e histograma de `http.server.requests`.
- **FR-008**: La demo local bajo `observability/` MUST incluir Prometheus configurado para recolectar la aplicación con acceso autorizado y Grafana con Prometheus preconfigurado como datasource.
- **FR-009**: La aplicación MUST poder iniciar y operar sin que Prometheus ni Grafana estén activos.
- **FR-010**: La feature MUST conservar los logs JSON y correlation ID existentes y respetar el límite de archivos autorizados indicado en Contexto y límites.
- **FR-011**: Las cuatro familias de endpoints de monitoreo MUST aparecer en Swagger UI; las operaciones protegidas de métricas y Prometheus MUST declarar `bearerAuth` o `apiKeyAuth` como alternativas, mientras health e info no exigen autenticación. La documentación MUST incluir códigos de respuesta y esquemas de respuesta de cada familia sin exponer rutas Actuator adicionales.
- **FR-012**: La autenticación opcional en health MUST procesar JWT y API keys válidos para distinguir ADMIN de USER, conservar el acceso anónimo y no cambiar el comportamiento de las demás rutas públicas.

### Key Entities

- **Estado de salud**: Estado general y estados de vida y preparación; los componentes detallados son base de datos, Redis y disco.
- **Identidad del build**: Nombre, versión y fecha de construcción del artefacto ejecutado.
- **Métrica HTTP**: Duración y resultado de solicitudes agrupables por endpoint; incluye percentiles, histograma y la etiqueta `application` en su exposición.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un operador sin credenciales puede consultar las tres rutas de salud y conocer su estado en 3 de 3 intentos, sin ver detalles de dependencias.
- **SC-002**: Un ADMIN puede ver los estados de base de datos, Redis y disco; un usuario común y uno anónimo no pueden ver ninguno de esos tres detalles.
- **SC-003**: Las cuatro familias de monitoreo solicitadas están disponibles para sus usuarios autorizados y 0 de las rutas `env`, `beans` y `heapdump` están expuestas.
- **SC-004**: Con solicitudes exitosas y fallidas de prueba, un operador puede identificar en la salida de métricas el endpoint, la duración y la proporción de errores, así como percentiles e histograma; el 100 % de las series expuestas lleva `application`.
- **SC-005**: En una demo local, el equipo puede confirmar 1 objetivo de recolección disponible y 1 datasource de métricas listo para consultar; la aplicación también arranca con ambos servicios de demo detenidos.
- **SC-006**: En una revisión de los cambios, 0 archivos previos fuera de los autorizados en Contexto y límites presentan modificaciones por esta feature.
- **SC-007**: En Swagger UI se pueden identificar y probar las cuatro familias de endpoints solicitadas, con códigos y esquemas de respuesta visibles, y las operaciones protegidas declaran JWT o API key como alternativas.
- **SC-008**: En las tres rutas de health, las solicitudes anónimas conservan acceso; un `Bearer` inválido o vencido y una API key no vacía inválida, cada uno enviado por separado, reciben 401; las credenciales válidas de USER y ADMIN sólo reciben el detalle autorizado para su rol. Todos los patrones de las demás rutas públicas conservan su comportamiento previo.

## Assumptions

- Los probes liveness y readiness son públicos como parte de health; sus detalles siguen la misma regla de visibilidad que el estado general.
- Cada solicitud autenticada de monitoreo usa exactamente un mecanismo: JWT o API key. Health e info admiten solicitudes sin ninguno. Un `Authorization` con esquema distinto de `Bearer` o una solicitud que envía JWT y API key simultáneamente quedan fuera del alcance y mantienen la conducta previa; las pruebas de esta feature envían como máximo una credencial por solicitud.
- `/actuator/info` es público según el plan solicitado; la restricción ADMIN aplica específicamente a métricas, Prometheus y detalles de salud.
- La API key de servicio sin dueño tiene rol ADMIN; una clave asociada a usuario conserva el rol de su dueño. La demo usa una clave autorizada con `ROLE_ADMIN`.
- Readiness representa el estado de preparación de la aplicación; no se asume que una caída de base de datos o Redis altere ese estado sin configuración adicional.
- La base de datos y Redis ya se levantan por separado conforme al README; la demo de observabilidad no los administra.
- La fecha de build describe el artefacto construido, no el momento en que arrancó el proceso.
