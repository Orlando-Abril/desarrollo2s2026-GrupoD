# Feature Specification: Ranking de jugadores por cotización

**Feature Branch**: `feature/ranking-jugadores`

**Created**: 2026-10-25

**Status**: Draft

**Input**: User description: "Endpoint GET /players/ranking: jugadores ordenados por su cotización actual según la estrategia activa, con filtros, paginación, variación porcentual, seguridad, OpenAPI y caché de una hora invalidada al terminar cada recálculo."

## Contexto y restricciones

La feature 013 creó `PlayerQuote`, el catálogo de estrategias y la consulta de la última cotización de todos los jugadores para una estrategia. La feature 016 creó el recálculo que genera nuevas cotizaciones. Esta feature expone esas capacidades como un ranking autenticado, sin calcular cotizaciones ni alterar su historial.

La cotización “actual” de un jugador es su registro más reciente para la estrategia seleccionada. La posición del ranking se calcula sobre todo el conjunto que satisface los filtros, antes de paginar. El contrato pertenece al área de cotizaciones aunque la ruta esté bajo `/players`.

El ranking se conserva temporalmente para evitar repetir consultas equivalentes. Por autorización expresa del equipo, esta feature puede registrar el caché `quotes-ranking` con serialización tipada en `CacheConfig`. `PlayerController` y `GlobalExceptionHandler`, ambos de Entrega 1, no se modifican sin consulta previa. El diseño previsto permite cumplir el alcance sin tocarlos.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consultar el ranking actual (Priority: P1)

Como usuario autenticado, quiero ver a los jugadores ordenados por su cotización vigente para la estrategia activa, para identificar rápidamente cuáles son los más valiosos.

**Why this priority**: El orden global por valor actual es el objetivo central de la feature y habilita la vista de ranking exigida por el producto.

**Independent Test**: Preparar jugadores con varias cotizaciones de la estrategia activa, consultar la primera página y comprobar que se usa sólo la última de cada jugador, que el orden y las posiciones son correctos y que se excluyen jugadores sin cotización.

**Acceptance Scenarios**:

1. **Given** cuatro jugadores con últimas cotizaciones actuales de 8, 15, 4 y 10 créditos para la estrategia activa, **When** un usuario autenticado consulta el ranking sin parámetros, **Then** recibe los jugadores con valores 15, 10, 8 y 4, con posiciones 1, 2, 3 y 4.
2. **Given** un jugador con cotizaciones de 5 y luego 9 créditos para la estrategia activa, **When** se consulta el ranking, **Then** el jugador aparece una sola vez con valor 9.
3. **Given** jugadores existentes sin cotización para la estrategia seleccionada, **When** se consulta el ranking, **Then** esos jugadores no aparecen ni se cuentan en el total.
4. **Given** dos jugadores con la misma cotización actual, **When** se consulta el ranking repetidas veces, **Then** ambos conservan un orden determinista por identificador de jugador ascendente y reciben posiciones consecutivas.

---

### User Story 2 - Comparar y filtrar el ranking (Priority: P1)

Como usuario autenticado, quiero seleccionar una estrategia y filtrar por liga o posición, con resultados paginados y la variación respecto de la cotización anterior, para comparar jugadores relevantes sin perder su contexto de evolución.

**Why this priority**: La estrategia, los filtros y la variación convierten una lista de precios en una herramienta de comparación útil y acotada al interés del usuario.

**Independent Test**: Consultar un conjunto conocido con distintas estrategias, ligas, posiciones y páginas; verificar el subconjunto, la posición global dentro de ese subconjunto, los metadatos de paginación y el cálculo de variación.

**Acceptance Scenarios**:

1. **Given** cotizaciones OFFICIAL y MOMENTUM con órdenes diferentes, **When** se consulta con `strategy=MOMENTUM`, **Then** el ranking usa exclusivamente las últimas cotizaciones MOMENTUM e informa esa estrategia y la versión propia de cada cotización.
2. **Given** jugadores de distintas ligas y posiciones, **When** se aplican `league` y `position`, **Then** sólo aparecen jugadores que cumplen ambos filtros y las posiciones se recalculan desde 1 sobre ese subconjunto.
3. **Given** 45 jugadores ordenados y `page=1&size=20`, **When** se consulta el ranking, **Then** se devuelven los jugadores en posiciones 21 a 40 y metadatos que informan página, tamaño, total de elementos y total de páginas.
4. **Given** un jugador cuya cotización anterior era 8 y la actual es 10 para la misma estrategia, **When** aparece en el ranking, **Then** su variación porcentual es 25%.
5. **Given** un jugador sin cotización anterior para la misma estrategia, **When** aparece en el ranking, **Then** su variación porcentual es nula y el jugador permanece en el resultado.

---

### User Story 3 - Recibir un contrato seguro y previsible (Priority: P2)

Como integrador de la API, quiero validaciones, errores de autenticación y documentación completos, para consumir el ranking sin depender de reglas implícitas.

**Why this priority**: El ranking sólo es integrable si sus parámetros, respuestas y mecanismos de seguridad son descubribles y consistentes con el resto de la API.

**Independent Test**: Ejercitar el endpoint sin credenciales y con cada entrada válida e inválida; luego contrastar parámetros, respuestas, esquemas y seguridad con la documentación publicada.

**Acceptance Scenarios**:

1. **Given** un pedido sin credenciales o con credenciales inválidas, **When** se consulta el ranking, **Then** se responde 401 sin devolver datos de jugadores o cotizaciones.
2. **Given** una estrategia, liga o posición desconocida o vacía, **When** un usuario autenticado consulta el ranking, **Then** se responde 400 con el formato de error común y un mensaje que permite corregir el filtro.
3. **Given** `page` negativo, `size` menor que 1, mayor que 100, vacío o no numérico, **When** se consulta el ranking, **Then** se responde 400.
4. **Given** la documentación OpenAPI publicada, **When** un integrador inspecciona `GET /players/ranking`, **Then** encuentra todos los parámetros, la respuesta paginada, los campos del elemento, las respuestas 200/400/401 y las alternativas de autenticación.

---

### User Story 4 - Mantener resultados rápidos y frescos (Priority: P2)

Como usuario autenticado, quiero que consultas idénticas del ranking respondan rápidamente sin conservar precios obsoletos después de un recálculo, para navegar con fluidez y confiar en el orden mostrado.

**Why this priority**: El ranking combina cotizaciones y datos de muchos jugadores; reutilizar temporalmente el resultado mejora la experiencia, pero nunca debe ocultar una nueva corrida.

**Independent Test**: Repetir una consulta idéntica, comprobar que se reutiliza durante una hora, variar uno de sus parámetros para obtener una entrada independiente y ejecutar recálculos con distintos desenlaces para verificar la invalidación total al terminar.

**Acceptance Scenarios**:

1. **Given** una consulta exitosa del ranking, **When** se repite con la misma estrategia efectiva, liga, posición, página y tamaño antes de una hora y sin recálculos intermedios, **Then** se obtiene el mismo resultado reutilizado.
2. **Given** una consulta ya conservada, **When** cambia cualquiera de estrategia, liga, posición, página o tamaño, **Then** se trata como una consulta diferente y no se reutiliza la entrada anterior.
3. **Given** una o más entradas conservadas, **When** termina un recálculo, exitoso o fallido, **Then** todas las entradas del ranking quedan invalidadas.
4. **Given** una entrada sin recálculos intermedios, **When** transcurre una hora desde su creación, **Then** la siguiente consulta vuelve a obtener el ranking actual.

### Edge Cases

- Si no hay cotizaciones para la estrategia y filtros seleccionados, la respuesta es exitosa, con contenido vacío, total de elementos 0 y total de páginas 0.
- Si `page` apunta más allá de la última página, la respuesta es exitosa y vacía, conservando los totales del conjunto filtrado.
- Si se omite `strategy`, la estrategia efectiva es la activa al procesar el pedido y esa identidad forma parte de la clave del resultado conservado.
- `league` y `position` se comparan con los códigos exactos del dominio. Un jugador con varias posiciones coincide si la posición solicitada está entre ellas.
- Los filtros `league` y `position` se combinan con condición conjunta.
- Si dos últimas cotizaciones tienen el mismo valor, el menor identificador de jugador aparece primero; la posición es ordinal, sin posiciones compartidas ni saltos.
- La cotización anterior es la inmediatamente previa del mismo jugador y código de estrategia, aunque pertenezca a otra versión de esa estrategia.
- Si el valor anterior es cero, la variación porcentual es nula porque el cociente no está definido.
- Una variación negativa conserva su signo; una variación exactamente nula se informa como 0.
- La versión informada pertenece a la cotización actual de cada jugador y puede diferir entre elementos del mismo ranking durante una migración de versión.
- Una respuesta obtenida antes de que termine un recálculo puede reflejar el estado previo; al terminar la corrida queda invalidada para la siguiente consulta.

## Requirements *(mandatory)*

### Functional Requirements

**Contrato y selección de datos**

- **FR-001**: El sistema MUST exponer `GET /players/ranking` para consumidores autenticados, sin modificar `PlayerController`.
- **FR-002**: El endpoint MUST aceptar los parámetros opcionales `strategy`, `league`, `position`, `page` y `size`.
- **FR-003**: Cuando `strategy` esté ausente, el sistema MUST usar la estrategia activa al procesar el pedido. Cuando esté presente, MUST coincidir exactamente con un código del catálogo de estrategias.
- **FR-004**: El ranking MUST considerar, para cada jugador, exclusivamente su cotización más reciente de la estrategia seleccionada.
- **FR-005**: Un jugador sin cotización para la estrategia seleccionada MUST NOT aparecer ni contarse en el ranking, aunque tenga cotizaciones de otra estrategia.
- **FR-006**: Los resultados MUST ordenarse por valor de cotización actual descendente y, ante igualdad, por identificador de jugador ascendente.
- **FR-007**: Cada resultado MUST recibir una posición ordinal consecutiva, comenzando en 1, calculada sobre todo el conjunto filtrado antes de aplicar la paginación.

**Filtros y paginación**

- **FR-008**: `league`, cuando esté presente, MUST seleccionar únicamente jugadores cuya liga coincida exactamente con el código solicitado.
- **FR-009**: `position`, cuando esté presente, MUST seleccionar únicamente jugadores que incluyan la posición solicitada entre sus posiciones.
- **FR-010**: Cuando se indiquen `league` y `position`, el jugador MUST cumplir ambos filtros.
- **FR-011**: `page` MUST ser un entero desde 0 y su valor por defecto MUST ser 0. `size` MUST ser un entero entre 1 y 100 inclusive y su valor por defecto MUST ser 20.
- **FR-012**: La respuesta MUST incluir los elementos de la página y metadatos con `page`, `size`, `totalElements` y `totalPages`.
- **FR-013**: Una página posterior a la última MUST responder 200 con elementos vacíos y los metadatos correspondientes al conjunto filtrado.

**Representación y variación**

- **FR-014**: Cada elemento MUST incluir `rank`; el jugador con `id`, `name`, `team`, `league` y todas sus `positions`; `value`; `percentageChange`; `strategy`; y `version`.
- **FR-015**: `value` MUST conservar sin pérdida el valor de la cotización actual; `strategy` y `version` MUST identificar la definición que generó esa cotización.
- **FR-016**: `percentageChange` MUST calcularse contra la cotización inmediatamente anterior del mismo jugador y código de estrategia como `((actual - anterior) / anterior) × 100`.
- **FR-017**: `percentageChange` MUST ser nula cuando no exista cotización anterior o cuando el valor anterior sea cero; estos casos MUST NOT excluir al jugador.
- **FR-018**: La variación porcentual MUST conservar el signo y MUST redondearse a dos decimales con criterio de mitad hacia arriba para que el contrato sea determinista.

**Validación, seguridad y errores**

- **FR-019**: Una estrategia desconocida o vacía MUST responder 400 con código `unknown_strategy` y un mensaje que indique el valor pedido y los códigos válidos.
- **FR-020**: Una liga o posición desconocida o vacía MUST responder 400 con código `validation_error` y un mensaje que identifique el filtro inválido y sus valores válidos.
- **FR-021**: Un `page` o `size` vacío, no entero o fuera de los límites de FR-011 MUST responder 400 con código `validation_error`.
- **FR-022**: Todas las respuestas 400 MUST usar el formato de error existente, compuesto por `error` y `message`, sin exponer detalles internos y sin modificar `GlobalExceptionHandler`.
- **FR-023**: Un pedido sin credenciales o con credenciales inválidas o vencidas MUST responder 401 antes de devolver datos del ranking.
- **FR-024**: El endpoint MUST admitir como alternativas una credencial bearer válida o una API key válida. Una identidad con `Role.USER` MUST poder consultar mediante cualquiera de los dos mecanismos; la lectura no agrega una restricción de rol.

**Documentación**

- **FR-025**: OpenAPI MUST documentar la ruta, los cinco parámetros con defaults y límites, el criterio de orden y desempate, la respuesta paginada, todos los campos de cada elemento y las respuestas 200, 400 y 401 con sus esquemas.
- **FR-026**: OpenAPI MUST declarar `bearerAuth` y `apiKeyAuth` como alternativas de seguridad del endpoint.

**Conservación temporal e invalidación**

- **FR-027**: Cada respuesta exitosa MUST conservarse en Redis durante una hora bajo el caché denominado `quotes-ranking`.
- **FR-028**: La identidad de una entrada conservada MUST incluir la estrategia efectiva, liga, posición, página y tamaño, usando una representación canónica que distinga toda combinación diferente.
- **FR-029**: Al terminar cada recálculo, tanto exitoso como fallido, el sistema MUST invalidar todas las entradas de `quotes-ranking` antes de considerar concluida la ejecución.
- **FR-030**: El sistema MUST registrar `quotes-ranking` en la configuración compartida de cachés con una representación tipada de la respuesta paginada. Este es el único cambio expresamente autorizado por esta feature en `CacheConfig`.
- **FR-031**: Una falla al leer o escribir el resultado conservado MUST NOT impedir la consulta: el sistema MUST obtener el ranking desde los datos persistidos y responder según el contrato.

**Alcance y archivos protegidos**

- **FR-032**: La feature MUST reutilizar `PlayerQuote`, el catálogo de estrategias, la consulta de últimas cotizaciones y el proceso de recálculo existentes; MUST NOT crear, modificar ni eliminar cotizaciones ni consultar fuentes externas.
- **FR-033**: La feature MUST NOT modificar `PlayerController` ni `GlobalExceptionHandler`. Si durante la planificación se concluye que alguno es imprescindible, el trabajo MUST detenerse y consultar al equipo, indicando el archivo, el cambio mínimo y una alternativa que lo evite.
- **FR-034**: Fuera del cambio autorizado en `CacheConfig`, la feature MUST NOT modificar archivos de Entrega 1 sin aprobación explícita del equipo.

### Key Entities

- **Elemento de ranking**: Posición ordinal y fotografía del jugador junto con su cotización actual, variación porcentual, estrategia y versión.
- **Jugador**: Recurso existente identificado por `id`, con nombre, equipo, liga y una o más posiciones. Sólo participa si tiene cotización para la estrategia seleccionada.
- **Cotización actual**: Último registro inmutable de un jugador para un código de estrategia; aporta valor, estrategia y versión.
- **Cotización anterior**: Registro inmediatamente anterior a la actual para el mismo jugador y código de estrategia; se usa únicamente para la variación porcentual.
- **Página de ranking**: Colección ordenada de elementos y metadatos de página, tamaño, total de elementos y total de páginas.
- **Consulta de ranking**: Combinación canónica de estrategia efectiva, liga, posición, página y tamaño que identifica un resultado reutilizable durante una hora.
- **Error de API**: Representación existente con `error` y `message`, compartida por validación y autenticación.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En el 100% de los escenarios de prueba, cada jugador elegible aparece como máximo una vez, usando su cotización más reciente para la estrategia seleccionada, y ningún jugador sin cotización aparece.
- **SC-002**: En el 100% de los conjuntos probados, los resultados están ordenados de mayor a menor valor, los empates siguen el mismo orden determinista y las posiciones son consecutivas sobre el conjunto filtrado completo.
- **SC-003**: El 100% de las variaciones con valor anterior positivo coincide con la fórmula definida a dos decimales; todos los casos sin anterior o con anterior cero informan valor nulo sin omitir al jugador.
- **SC-004**: El 100% de las combinaciones válidas de estrategia, liga, posición y paginación devuelve sólo los jugadores esperados, con metadatos coherentes; todas las entradas inválidas contempladas responden 400.
- **SC-005**: El 100% de los pedidos rechazados por autenticación responde 401 sin datos; una identidad `Role.USER` obtiene 200 para la misma consulta tanto con bearer token como con API key.
- **SC-006**: Sobre un conjunto de exactamente 2.500 jugadores, se ejecutan 20 solicitudes HTTP completas y consecutivas de una misma página —la primera puede ser cache miss y las 19 restantes cache hits—, se registran los 20 tiempos y al menos 19 respuestas completas terminan en menos de 1 segundo en el entorno de desarrollo.
- **SC-007**: Después de cada recálculo probado, exitoso o fallido, la primera consulta de cada combinación refleja los datos persistidos vigentes y reutiliza ese resultado por no más de una hora.
- **SC-008**: Un integrador puede identificar en OpenAPI, sin leer código interno, todos los parámetros, defaults, límites, campos, errores y mecanismos de autenticación del ranking.
- **SC-009**: El 100% de los tests existentes de `PlayerController` y `GlobalExceptionHandler` conserva sus resultados sin modificar esos archivos.

## Assumptions

- La estrategia activa se resuelve en cada pedido; si cambia la configuración, su código efectivo produce una identidad de consulta distinta.
- Los códigos de estrategia, liga y posición se comparan exactamente y distinguen mayúsculas y minúsculas, de acuerdo con los catálogos del dominio existentes.
- La respuesta paginada usa índices de página basados en cero, convención elegida para los endpoints del backend; la interfaz consumidora puede traducirlos a numeración humana.
- La posición del ranking es ordinal: los empates se desempatan, no comparten posición.
- La comparación de cotización anterior atraviesa versiones de una misma estrategia porque el código representa la misma serie histórica; cada resultado conserva la versión de su cotización actual.
- La invalidación al finalizar un recálculo incluye cualquier desenlace terminal. Invalidar tras una falla es seguro aunque los datos no hayan cambiado y simplifica la garantía de frescura.
- La consulta usa datos locales persistidos y no depende de la disponibilidad de fuentes externas.

## Out of Scope

- Crear, recalcular, modificar o eliminar cotizaciones.
- Cambiar la estrategia activa o administrar definiciones de estrategias.
- Ranking histórico para una fecha pasada, tendencias adicionales o comparación entre estrategias en una misma respuesta.
- Filtros por equipo, nombre, nacionalidad, edad u otros datos no solicitados.
- Exportación del ranking o actualizaciones en tiempo real durante una corrida.
- Cambios en `PlayerController`, `GlobalExceptionHandler` u otros archivos de Entrega 1 no autorizados expresamente.
