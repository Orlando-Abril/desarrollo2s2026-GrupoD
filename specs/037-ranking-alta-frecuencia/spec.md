# Feature Specification: Ranking de alta frecuencia

**Feature Branch**: `feature/ranking-alta-frecuencia`  
**Created**: 2026-11-17  
**Status**: Draft  
**Input**: Optimizar `GET /players/ranking` según la estrategia activa para consultas frecuentes, con evidencia medible y sin cambiar la respuesta ni el orden.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consultar el ranking repetidamente (Priority: P1)

Una persona abre el ranking del frontend y vuelve a consultarlo. Tras la primera carga, recibe la misma página rápidamente. Cuando la consulta sale del caché, la ejecución de `QuoteQueryService.ranking`, una vez completada la autenticación, no consulta repositorios, no abre una transacción ni adquiere conexiones de base atribuibles al servicio de ranking. La autenticación conserva sus consultas actuales.

**Why this priority**: Es la ruta de lectura frecuente y el ahorro de recursos que motiva la mejora.

**Independent Test**: Consultar dos veces el ranking sin parámetros con la misma estrategia activa; verificar contenido y orden, medir la duración HTTP completa de la consulta cacheada y comprobar en el límite de `QuoteQueryService.ranking` que no se invocan repositorios, no hay transacción activa y no se adquieren conexiones de base atribuibles al servicio. No contar las consultas previas de autenticación como trabajo del ranking.

**Acceptance Scenarios**:

1. **Given** la primera página de la estrategia activa está en caché y la autenticación terminó, **When** se ejecuta `QuoteQueryService.ranking` para `GET /players/ranking` sin parámetros, **Then** se devuelve esa página sin invocar repositorios, abrir transacción ni adquirir conexión de base desde el servicio de ranking.
2. **Given** una combinación válida de estrategia, liga, posición, página y tamaño ya está en caché, **When** se repite la misma solicitud, **Then** se obtiene la misma respuesta y orden sin acceso a base atribuible al servicio de ranking.
3. **Given** no hay una página válida en caché, **When** se solicita el ranking, **Then** se calcula desde los datos vigentes, se devuelve y queda disponible para consultas posteriores.
4. **Given** una consulta sin estrategia carga una página, incluso vacía, **When** se consulta después esa misma combinación con el código de la estrategia activa explícito en la misma instancia y generación, **Then** se reutiliza el mismo resultado sin repetir el cálculo.

---

### User Story 2 - Atender consultas simultáneas (Priority: P1)

Varias personas piden al mismo tiempo a una misma instancia la misma página ausente del caché. Comparten el resultado de un único cálculo y todas reciben la página correcta. No se promete coordinación distribuida entre réplicas.

**Why this priority**: Evita multiplicar el trabajo más costoso durante una ráfaga.

**Independent Test**: Vaciar la página, lanzar 20 solicitudes simultáneas con los mismos parámetros a una misma instancia y comprobar un solo cálculo y respuestas equivalentes.

**Acceptance Scenarios**:

1. **Given** el caché no contiene una página y llegan solicitudes idénticas simultáneas a una misma instancia, **When** se calcula esa página, **Then** una sola realiza el cálculo y las demás esperan y reciben ese resultado.
2. **Given** llegan simultáneamente a una misma instancia solicitudes de páginas o filtros distintos, **When** faltan en caché, **Then** cada combinación conserva su resultado correspondiente, sin mezclar páginas.

---

### User Story 3 - Ver el ranking actualizado después de cambios (Priority: P2)

Después de un recálculo de cotizaciones o de confirmar una nueva estrategia activa, la página inicial que usa el frontend se prepara de nuevo con datos vigentes. El proceso que originó el cambio termina sin esperar esa preparación.

**Why this priority**: Mantiene ágil la vista principal después de cambios.

**Independent Test**: Completar cada tipo de cambio, comprobar que su respuesta no espera el cálculo de la página y que la primera página sin filtros de la estrategia activa queda cargada con contenido posterior al cambio.

**Acceptance Scenarios**:

1. **Given** termina un recálculo, **When** el cambio ya es visible, **Then** se invalida el ranking anterior y se recarga en segundo plano la página 0, tamaño 20, sin filtros, de la estrategia activa.
2. **Given** se confirma un cambio de estrategia activa, **When** el cambio ya es visible, **Then** se invalida el ranking anterior y se recarga en segundo plano la misma página de la nueva estrategia.
3. **Given** cualquiera de esos cambios acaba de completarse, **When** responde la operación que lo produjo, **Then** esa respuesta no espera el cálculo del ranking.

---

### User Story 4 - Mantener servicio y medir la mejora (Priority: P2)

La persona sigue consultando el ranking si el caché está fuera de servicio. El equipo observa aciertos y fallos y repite una medición local de la primera consulta y las siguientes.

**Why this priority**: La optimización debe ser verificable y conservar la disponibilidad.

**Independent Test**: Hacer fallar el caché y verificar la respuesta desde los datos persistidos; comprobar los contadores publicados y seguir el quickstart.

**Acceptance Scenarios**:

1. **Given** el caché no está disponible, **When** se solicita una página válida, **Then** el ranking responde desde la base con el contrato y el orden habituales.
2. **Given** se producen aciertos y fallos, **When** se consulta `/actuator/prometheus`, **Then** los contadores del caché del ranking son visibles y reflejan ambas situaciones.
3. **Given** un entorno local preparado según el quickstart, **When** se ejecuta su medición, **Then** se informan por separado el tiempo de la primera consulta y los de las posteriores.

### Edge Cases

- Un ranking vacío se conserva como resultado válido y evita nuevos cálculos mientras siga vigente.
- Si falla el cálculo compartido, quienes esperan no quedan bloqueados indefinidamente; el error no se guarda como página válida y una solicitud posterior puede reintentar.
- Si el caché falla durante una ráfaga, las solicitudes siguen pudiendo obtener la página desde la base.
- Si se solapan una recarga y otra actualización, el caché no debe quedar con una página anterior al último cambio confirmado.
- Si una carga anterior termina después de vaciar el caché, sólo puede escribir una clave de su generación vieja; la clave vigente conserva datos posteriores al evento y la vieja se elimina o queda inaccesible mientras se limpia.
- Los parámetros inválidos y las respuestas de autorización conservan el comportamiento actual.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST conservar el contrato de `GET /players/ranking`, incluidos parámetros, estructura, valores, paginación y orden; la estrategia omitida MUST usar la activa.
- **FR-002**: Después de completar la autenticación, para una página servida desde caché, `QuoteQueryService.ranking` MUST no invocar repositorios, no abrir una transacción y no adquirir conexiones de base atribuibles al servicio de ranking, incluso al resolver la estrategia activa si se omitió el parámetro. La petición HTTP completa MAY conservar las consultas actuales de autenticación.
- **FR-003**: Ante una página ausente, el sistema MUST obtenerla desde los datos persistidos y hacerla disponible para consultas posteriores, identificada por estrategia o alias, liga, posición, página, tamaño y generación local capturada.
- **FR-004**: Para solicitudes simultáneas dirigidas a una misma instancia y a la misma página ausente, el sistema MUST ejecutar un solo cálculo y entregar el resultado a quienes esperan, distinguiendo combinaciones diferentes. No se exige coordinación distribuida entre réplicas.
- **FR-005**: Si el cálculo compartido falla, el sistema MUST liberar a los solicitantes en espera, no conservar el fallo como resultado y permitir un intento posterior.
- **FR-006**: Al terminar un recálculo, el sistema MUST invalidar páginas antiguas y recargar en segundo plano la página 0 de tamaño 20, sin filtros, de la estrategia activa, desde datos confirmados.
- **FR-007**: Al confirmarse un cambio de estrategia activa, el sistema MUST invalidar páginas antiguas y recargar en segundo plano esa página para la nueva estrategia, desde datos confirmados.
- **FR-008**: En una misma instancia, el servicio MUST mantener una generación local monotónica para el ranking. Cada clave MUST incluir la generación capturada al iniciar la carga; cada evento MUST incrementarla antes de vaciar el caché. Una carga anterior MUST escribir sólo en su clave vieja y, si al terminar detecta una generación obsoleta, eliminar esa clave y no publicar su resultado como vigente. El precalentado y toda solicitud nueva MUST usar sólo la generación actual.
- **FR-009**: La recarga MUST no demorar la respuesta del recálculo ni la del cambio de estrategia; un fallo de recarga MUST no hacer fallar esas operaciones.
- **FR-010**: Si Redis no está disponible para leer o escribir, el ranking MUST seguir respondiendo desde la base, como hoy.
- **FR-011**: Los aciertos y fallos del caché del ranking MUST ser visibles como métricas diferenciables en `/actuator/prometheus`.
- **FR-012**: El quickstart de la feature MUST indicar cómo medir en local la duración HTTP completa de la primera solicitud y de las siguientes por separado, con objetivo de menos de 50 ms por solicitud cacheada, y cómo comprobar las métricas.
- **FR-013**: La mejora MUST conservar la seguridad, los logs estructurados, el ID de correlación y la observabilidad existentes.
- **FR-014**: Los cambios de código MUST limitarse a `QuoteQueryService`, `QuoteCacheEvictor`, los nuevos componentes `RankingLoader` y `RankingWarmer`, la habilitación de ejecución asíncrona si falta, y `CacheConfig` únicamente para activar estadísticas del caché. Sólo se puede añadir un índice si una medición de la consulta con la base de la demo demuestra una operación evitable y se documenta la comparación. Se pueden añadir documentación y pruebas propias de esta feature sin modificar otros archivos de entregas anteriores.
- **FR-015**: Cuando la carga del alias `active` resuelve la estrategia efectiva, el sistema MUST publicar el mismo `RankingPageResponse` bajo la clave `active` y la clave explícita de esa estrategia en la misma generación, sin repetir la consulta, incluso para páginas vacías. No se exige la equivalencia inversa cuando la clave explícita se carga primero.

### Key Entities

- **Página de ranking**: Lista ordenada y metadatos de paginación para una combinación de estrategia, liga, posición, página y tamaño.
- **Estrategia activa**: Estrategia aplicada cuando la consulta no especifica una; puede cambiar mediante una operación confirmada.
- **Resultado en caché**: Copia temporal de una página vigente, identificada por su combinación de consulta.
- **Evento de actualización**: Finalización de un recálculo o confirmación de un cambio de estrategia que vuelve obsoletos resultados previos.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En una prueba local, cada solicitud HTTP completa posterior servida desde caché de la página inicial tarda menos de 50 ms. En el límite de `QuoteQueryService.ranking`, ese hit registra cero llamadas a repositorios, cero transacciones abiertas y cero conexiones de base adquiridas por el servicio; las consultas de autenticación quedan fuera de esta última comprobación.
- **SC-002**: Ante 20 consultas simultáneas idénticas a una instancia con la página ausente, se efectúa exactamente un cálculo y las 20 respuestas son equivalentes.
- **SC-003**: Tras cada uno de los dos tipos de actualización, la primera página de la estrategia activa vuelve a estar disponible en caché sin que la operación espere su cálculo.
- **SC-004**: Con el caché no disponible, las consultas válidas siguen devolviendo la misma estructura y el mismo orden que con caché disponible.
- **SC-005**: Un operador puede observar por separado al menos un acierto y un fallo del caché del ranking y reproducir con el quickstart los tiempos de primera consulta y consultas posteriores.

## Assumptions

- El frontend sigue solicitando el ranking sin parámetros: estrategia activa, página 0, tamaño 20 y sin filtros.
- Las garantías de coordinación e invalidación por generación se limitan a una instancia; la generación local conserva sólo un número de versión, no páginas.
- La identidad lógica de cada página, el vencimiento de una hora y la política de invalidación general del caché existente se mantienen.
- Los datos de cotizaciones confirmados son la referencia para la recarga posterior a una actualización.
- La medición local del objetivo de latencia se hace con servicios disponibles y una página ya cargada; el primer acceso se informa por separado.
- Se reutilizan el contrato, la autenticación, los logs y la infraestructura de métricas existentes.
- No se añaden cachés en memoria, no se cambia el TTL ni se introduce otra paginación.
