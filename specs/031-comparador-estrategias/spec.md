# Feature Specification: Comparador de estrategias de valuación

**Feature Branch**: `feature/comparador-estrategias`

**Created**: 2026-10-30

**Status**: Draft

**Input**: User description: "SPECIFY_FEATURE_DIRECTORY=specs/031-comparador-estrategias. Ver el catálogo y el portfolio con la estrategia alternativa, detectar jugadores subvaluados y sobrevaluados, y que el superusuario pueda cambiar la estrategia activa global. Portfolio con `?strategy=`; oportunidades subvaluados/sobrevaluados con filtros de liga y posición, excluyendo NO_STATS e INSUFFICIENT_HISTORY; última cotización de una estrategia para los jugadores de una página del Álbum; cambio de estrategia activa por ADMIN, persistido, auditado y usado por el próximo recálculo; consulta de estrategia activa y alternativa por cualquier usuario autenticado; 400 con estrategia inexistente, 401, 403; OpenAPI. Regla: no modificar archivos de Entrega 1 sin consultar; la restricción a ADMIN se hace sin tocar SecurityConfig."

## Contexto y límites

Cada corrida de recálculo calcula y guarda las dos estrategias del catálogo, OFFICIAL v1 y MOMENTUM v1 (specs 016 y 019), en el historial inmutable de cotizaciones. Cada cotización tiene valor obligatorio con 4 decimales y estado `CALCULATED`, `NO_STATS` o `INSUFFICIENT_HISTORY`; las dos últimas conservan el valor previo de la estrategia o el valor base 1.0000. La estrategia **activa** es la única que fija `Player.marketValue`, el precio con que se valúan los portfolios y opera el mercado. Hoy se elige con la propiedad `valuation.active-strategy` (OFFICIAL por defecto) y sólo cambia reiniciando la aplicación. El ranking y el historial de cotizaciones de un jugador ya aceptan una estrategia explícita.

Esta feature permite **comparar** la estrategia activa con la **alternativa** (la otra estrategia del catálogo) sin alterar el mercado: valuar el portfolio con otra estrategia, detectar los jugadores cuyo precio de mercado más se aparta de la alternativa y mostrar la cotización alternativa en el Catálogo. Además permite que el superusuario cambie la estrategia activa en tiempo de ejecución.

Todas las lecturas usan cotizaciones ya guardadas: ninguna calcula cotizaciones en memoria, dispara un recálculo ni consulta fuentes externas. Cambiar la estrategia activa no modifica `marketValue` de inmediato; el nuevo precio de mercado se aplica en el próximo recálculo.

Archivos de Entrega 1 (entre otros `PlayerController`, `GlobalExceptionHandler`, `SecurityConfig` y `CacheConfig`) no se modifican sin autorización explícita del equipo. Por eso la cotización alternativa del Catálogo se obtiene mediante una consulta nueva y complementaria a `GET /players`, y la restricción a ADMIN se verifica fuera de `SecurityConfig`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Valuar el portfolio con otra estrategia (Priority: P1)

Como usuario autenticado, quiero ver mi portfolio valuado con la última cotización de otra estrategia, junto al valor de mercado actual, para saber cuánto valdría mi patrimonio y cuál sería mi resultado si esa estrategia fijara los precios.

**Why this priority**: Es la comparación con impacto directo en las decisiones del usuario y extiende una consulta existente sin cambiar su contrato.

**Independent Test**: Preparar una cuenta con posiciones en tres jugadores con cotizaciones OFFICIAL y MOMENTUM conocidas, consultar el portfolio con y sin estrategia y comparar cada valor contra un cálculo manual.

**Acceptance Scenarios**:

1. **Given** un usuario con 15 tokens de un jugador, costo remanente 16.0000, `marketValue` 1.3000 y última cotización MOMENTUM 1.1000, **When** consulta su portfolio con estrategia MOMENTUM, **Then** la posición conserva todos sus campos actuales (valor actual 19.5000, ganancia 3.5000) y agrega la estrategia consultada, cotización 1.1000, valor 16.5000 y resultado 0.5000.
2. **Given** posiciones en varios jugadores y un saldo de 100.0000, **When** consulta con estrategia MOMENTUM, **Then** los totales agregan valor total con la estrategia (suma exacta de las posiciones), resultado total con la estrategia (valor menos costo total) y patrimonio total con la estrategia (saldo total más valor con la estrategia).
3. **Given** el mismo usuario, **When** consulta su portfolio sin estrategia, **Then** recibe exactamente la misma respuesta que antes de esta feature, sin campos agregados.
4. **Given** que la última cotización MOMENTUM de un jugador tiene estado `NO_STATS` con valor conservado 1.0450, **When** se consulta con MOMENTUM, **Then** la posición se valúa a 1.0450 e informa el estado de esa cotización.
5. **Given** un jugador en portfolio sin ninguna cotización de la estrategia pedida, **When** se consulta con esa estrategia, **Then** los valores con la estrategia de esa posición se informan vacíos, los totales con la estrategia se informan vacíos y se indica cuántas posiciones no tienen cotización; los campos actuales no cambian.
6. **Given** una estrategia inexistente, **When** se consulta el portfolio con ella, **Then** recibe 400 con el error de estrategia desconocida y sin datos del portfolio.
7. **Given** un USER autenticado, **When** consulta con estrategia el portfolio de otra cuenta, **Then** recibe 403, igual que sin estrategia.

---

### User Story 2 - Detectar jugadores subvaluados y sobrevaluados (Priority: P1)

Como usuario autenticado, quiero ver los jugadores cuyo precio de mercado más se aparta de la cotización de la estrategia alternativa, separados en subvaluados y sobrevaluados, para encontrar oportunidades de compra o de venta.

**Why this priority**: Es la funcionalidad nueva de mayor valor para el trading y la que justifica guardar dos estrategias.

**Independent Test**: Preparar jugadores con cotizaciones activas y alternativas conocidas, incluyendo estados no calculados y diferencias nulas, consultar las oportunidades con y sin filtros y verificar inclusión, separación, orden y porcentajes.

**Acceptance Scenarios**:

1. **Given** OFFICIAL activa, un jugador con `marketValue` 1.0000 y última MOMENTUM `CALCULATED` 1.2000, **When** se consultan las oportunidades, **Then** aparece en subvaluados con precio de mercado 1.0000, cotización alternativa 1.2000, diferencia 0.2000 y diferencia porcentual 20.00%.
2. **Given** un jugador con `marketValue` 2.0000 y MOMENTUM `CALCULATED` 1.5000, **When** se consultan las oportunidades, **Then** aparece en sobrevaluados con diferencia -0.5000 y -25.00%.
3. **Given** varios jugadores subvaluados, **When** se consultan, **Then** se ordenan por diferencia porcentual absoluta descendente y, ante empate, por nombre e id ascendentes.
4. **Given** un jugador cuya última cotización de la estrategia de mercado o de la alternativa tiene estado `NO_STATS` o `INSUFFICIENT_HISTORY`, **When** se consultan, **Then** no aparece en ninguna lista.
5. **Given** un jugador cuya cotización alternativa es igual a su precio de mercado, **When** se consultan, **Then** no aparece en ninguna lista.
6. **Given** filtros de liga LA_LIGA y posición FORWARD, **When** se consultan, **Then** ambas listas contienen sólo delanteros de La Liga; un valor de filtro inválido produce 400.
7. **Given** un límite de 5, **When** se consultan, **Then** cada lista contiene como máximo los 5 jugadores de mayor diferencia porcentual absoluta.
8. **Given** MOMENTUM como estrategia activa, **When** se consultan, **Then** la alternativa usada es OFFICIAL y la respuesta lo indica.

---

### User Story 3 - Mostrar la cotización alternativa en el Catálogo (Priority: P2)

Como frontend del Álbum, quiero obtener en una sola consulta la última cotización de una estrategia para todos los jugadores de la página que estoy mostrando, para presentarla junto al precio de mercado sin una consulta por jugador.

**Why this priority**: El listado de jugadores sólo trae `marketValue` y no puede modificarse; sin esta consulta el Catálogo no puede comparar estrategias de forma eficiente.

**Independent Test**: Obtener una página del listado de jugadores, pedir la última cotización MOMENTUM de sus ids y verificar un elemento por jugador con valor, estado y fecha coincidentes con el historial.

**Acceptance Scenarios**:

1. **Given** una página de 20 jugadores con cotizaciones MOMENTUM, **When** se pide la última MOMENTUM de esos 20 ids, **Then** se recibe un elemento por jugador con id, estrategia y versión, valor, estado e instante de cálculo de su cotización más reciente.
2. **Given** que la consulta no indica estrategia, **When** se realiza, **Then** se usa la estrategia alternativa y la respuesta indica cuál fue.
3. **Given** un id existente sin cotizaciones de esa estrategia, **When** se consulta, **Then** su elemento aparece con la cotización vacía.
4. **Given** ids repetidos o ids inexistentes, **When** se consulta, **Then** los repetidos se informan una sola vez y los inexistentes se omiten sin error.
5. **Given** una lista vacía, con más de 100 ids o con un id mal formado, o una estrategia inexistente, **When** se consulta, **Then** recibe 400.

---

### User Story 4 - Cambiar la estrategia activa global (Priority: P2)

Como superusuario (ADMIN), quiero cambiar la estrategia activa sin reiniciar la aplicación, para decidir con qué estrategia se fijarán los precios de mercado a partir del próximo recálculo.

**Why this priority**: Habilita el cierre del ciclo de comparación (comparar y luego adoptar) y es una operación sensible que requiere control y trazabilidad.

**Independent Test**: Como ADMIN cambiar de OFFICIAL a MOMENTUM, verificar la auditoría, que `marketValue` no cambia, ejecutar un recálculo y verificar que `marketValue` toma los valores MOMENTUM; reiniciar y verificar que la elección persiste. Repetir como USER y con un código inexistente.

**Acceptance Scenarios**:

1. **Given** OFFICIAL activa y un ADMIN autenticado, **When** cambia la estrategia activa a MOMENTUM, **Then** recibe 200 con la nueva activa, la alternativa, quién y cuándo hizo el cambio, y la indicación de que el precio de mercado se actualizará en el próximo recálculo.
2. **Given** ese cambio, **When** se consulta un portfolio o el precio de un jugador antes de recalcular, **Then** `marketValue` conserva los valores fijados por OFFICIAL.
3. **Given** ese cambio, **When** termina el próximo recálculo (manual o programado), **Then** `marketValue` de cada jugador procesado es su cotización MOMENTUM de esa corrida y el resumen de la corrida informa MOMENTUM como estrategia activa.
4. **Given** ese cambio, **When** la aplicación se reinicia, **Then** la estrategia activa sigue siendo MOMENTUM aunque la propiedad de configuración indique OFFICIAL.
5. **Given** el cambio, **When** se consulta la auditoría, **Then** existe un registro inmutable con el autor, la fecha y hora, el correlation id, el estado anterior (OFFICIAL) y el posterior (MOMENTUM).
6. **Given** un usuario con rol USER, **When** intenta cambiar la estrategia activa, **Then** recibe 403 y no cambia nada ni se audita un cambio.
7. **Given** un ADMIN, **When** intenta activar un código inexistente o envía un cuerpo inválido, **Then** recibe 400 con la lista de estrategias válidas y no cambia nada.
8. **Given** MOMENTUM ya activa, **When** un ADMIN la activa otra vez, **Then** recibe 200 sin cambios y no se genera un nuevo registro de auditoría.
9. **Given** una solicitud sin credenciales, **When** intenta cambiar la estrategia, **Then** recibe 401.

---

### User Story 5 - Consultar la estrategia activa y la alternativa (Priority: P2)

Como usuario autenticado, quiero saber cuál es la estrategia activa y cuál la alternativa, para que el frontend arme el selector de estrategia y aclare con qué precio se opera.

**Why this priority**: Todas las vistas comparativas dependen de conocer ambas estrategias; sin esto el frontend tendría que suponerlas.

**Independent Test**: Consultar como USER y como ADMIN antes y después de un cambio de estrategia y de un recálculo; repetir sin credenciales.

**Acceptance Scenarios**:

1. **Given** OFFICIAL activa, **When** un usuario autenticado consulta, **Then** recibe la activa OFFICIAL y la alternativa MOMENTUM, cada una con código, versión y etiqueta (p. ej. `OFFICIAL v1`), y la estrategia con que está fijado el precio de mercado vigente.
2. **Given** que un ADMIN cambió la activa a MOMENTUM y todavía no hubo recálculo, **When** se consulta, **Then** la activa es MOMENTUM, la alternativa OFFICIAL, y se indica que el precio de mercado vigente sigue fijado por OFFICIAL hasta el próximo recálculo.
3. **Given** que luego terminó un recálculo, **When** se consulta, **Then** se indica que el precio de mercado vigente está fijado por MOMENTUM.
4. **Given** una solicitud sin credenciales válidas, **When** se consulta, **Then** recibe 401.

---

### User Story 6 - Consultar los contratos en OpenAPI (Priority: P3)

Como persona integradora, quiero encontrar documentadas todas las consultas y el cambio de estrategia con sus parámetros, respuestas, seguridad y errores, para consumirlos sin leer el código fuente.

**Why this priority**: La documentación es obligatoria y el frontend depende de estos contratos.

**Independent Test**: Revisar la documentación publicada y construir pedidos válidos e inválidos para cada operación.

**Acceptance Scenarios**:

1. **Given** la documentación, **When** se revisa el portfolio, **Then** describe el parámetro opcional de estrategia, los campos agregados y que sin el parámetro la respuesta no cambia.
2. **Given** la documentación, **When** se revisan oportunidades, cotizaciones por lote y estrategia activa, **Then** describen parámetros, límites, esquemas, ejemplos, seguridad, la restricción a ADMIN del cambio y las respuestas 200, 400, 401 y 403 que correspondan.

### Edge Cases

- **Cambio de estrategia durante un recálculo en curso**: la corrida en curso termina con la estrategia activa que tenía al comenzar; el cambio se aplica en la corrida siguiente.
- **Cambio de estrategia y precio de mercado**: entre el cambio y el próximo recálculo, el precio de mercado sigue fijado por la estrategia anterior; la consulta de estrategia activa lo informa para que el frontend no muestre un precio con una estrategia equivocada.
- **Consultas sin estrategia explícita después de un cambio**: ranking e historial de cotizaciones sin estrategia pasan a usar la nueva activa inmediatamente, sin devolver resultados guardados en caché para la activa anterior.
- **Oportunidades con la alternativa que fijó el precio vigente**: si la activa cambió y aún no hubo recálculo, la alternativa es la estrategia que fijó `marketValue`; las diferencias pueden ser nulas y esos jugadores no aparecen. No es un error.
- **Precio de mercado no positivo o ausente**: un jugador sin `marketValue` positivo no puede tener diferencia porcentual y se excluye de las oportunidades.
- **Jugador sin cotización alternativa**: se excluye de las oportunidades; en el portfolio y la consulta por lote se informa como sin cotización.
- **Estado no calculado en el portfolio**: a diferencia de las oportunidades, el portfolio valúa con la última cotización de la estrategia sea cual sea su estado, porque ese valor es el que fijaría el precio; se informa el estado.
- **Estrategia pedida igual a la activa**: está permitida en portfolio y consulta por lote; el resultado normalmente coincide con el precio de mercado.
- **Mayúsculas/minúsculas y espacios en el código de estrategia**: se validan igual que en el ranking y el historial de cotizaciones vigentes.
- **Valor persistido inválido al arrancar**: si la estrategia activa guardada ya no existe en el catálogo, la aplicación no arranca, igual que hoy con un código de propiedad inexistente.
- **API key de servicio sin dueño**: cuenta como ADMIN; puede cambiar la estrategia y la auditoría registra un actor de sistema identificable.
- **Cambios concurrentes de dos ADMIN**: se aplican en orden; cada cambio efectivo genera su propio registro de auditoría con el estado anterior real.

## Requirements *(mandatory)*

### Functional Requirements

**Portfolio con estrategia**

- **FR-001**: `GET /users/{id}/portfolio` MUST aceptar un parámetro opcional `strategy`. Sin él, la respuesta MUST ser idéntica a la actual (spec 028).
- **FR-002**: Con `strategy`, cada posición MUST conservar todos sus campos actuales y agregar: código y versión de la estrategia, última cotización de esa estrategia para el jugador, su estado e instante de cálculo, valor con la estrategia (cantidad total × cotización) y resultado con la estrategia (valor con la estrategia − costo remanente).
- **FR-003**: Con `strategy`, los totales MUST conservar sus campos actuales y agregar valor total, resultado total y patrimonio total con la estrategia. Valor total MUST ser la suma exacta de las posiciones; resultado total MUST ser valor total con la estrategia − costo total; patrimonio MUST ser saldo total + valor total con la estrategia.
- **FR-004**: La cotización usada MUST ser la más reciente de esa estrategia para el jugador, cualquiera sea su estado. Si un jugador no tiene ninguna, sus campos con la estrategia MUST informarse vacíos, los totales con la estrategia MUST informarse vacíos y la respuesta MUST indicar la cantidad de posiciones sin cotización. La consulta MUST NOT fallar ni inventar un valor por esa causa.
- **FR-005**: Los valores agregados MUST tener 4 decimales y MUST NOT haber redondeos intermedios que alteren los totales.
- **FR-006**: Una estrategia inexistente MUST producir 400 con el error de estrategia desconocida, antes de leer el portfolio. Las reglas de acceso (401, 403 y 404 de spec 028) MUST aplicarse igual con o sin `strategy`.

**Oportunidades**

- **FR-007**: El sistema MUST exponer una consulta autenticada de oportunidades que compare el `marketValue` de cada jugador con su última cotización de la estrategia alternativa.
- **FR-008**: La diferencia MUST ser cotización alternativa − `marketValue` (4 decimales) y la diferencia porcentual MUST ser diferencia ÷ `marketValue` × 100 (2 decimales, calculada sin redondear la diferencia).
- **FR-009**: Un jugador con diferencia positiva MUST aparecer en subvaluados; con diferencia negativa, en sobrevaluados; con diferencia cero, en ninguna.
- **FR-010**: MUST excluirse los jugadores cuya última cotización alternativa o última cotización de la estrategia que fijó el precio de mercado vigente tenga estado `NO_STATS` o `INSUFFICIENT_HISTORY`, los que no tengan cotización alternativa y los que no tengan `marketValue` positivo.
- **FR-011**: Cada elemento MUST incluir jugador (id, nombre, equipo, liga y posiciones), `marketValue`, última cotización alternativa con instante de cálculo, diferencia y diferencia porcentual. La respuesta MUST incluir el código y la versión de la estrategia activa, de la alternativa y de la estrategia de mercado.
- **FR-012**: Cada lista MUST ordenarse por diferencia porcentual absoluta descendente y, ante empate, por nombre e id ascendentes, y MUST contener como máximo `limit` elementos (por defecto 10, rango 1 a 50; fuera de rango produce 400).
- **FR-013**: La consulta MUST aceptar filtros opcionales de liga y posición con los mismos valores válidos que el ranking; un valor inválido MUST producir 400.
- **FR-014**: La respuesta MUST indicar qué estrategia es la activa y cuál la alternativa usada.

**Última cotización por lote (Catálogo)**

- **FR-015**: El sistema MUST exponer una consulta autenticada que reciba una lista de ids de jugador y una estrategia opcional (por defecto la alternativa) y devuelva, por cada id existente, su última cotización de esa estrategia: código y versión, valor, estado e instante de cálculo, o vacía si no tiene.
- **FR-016**: La lista MUST tener entre 1 y 100 ids; ids repetidos MUST informarse una vez; ids inexistentes MUST omitirse sin error; lista vacía, más de 100 ids, ids mal formados o estrategia inexistente MUST producir 400.
- **FR-017**: La respuesta MUST indicar la estrategia usada. Esta consulta MUST NOT modificar el contrato de `GET /players`.

**Estrategia activa**

- **FR-018**: El sistema MUST exponer una consulta, disponible para cualquier usuario autenticado, que devuelva la estrategia activa, la alternativa (código, versión y una etiqueta formada por código y versión, p. ej. `MOMENTUM v1`, de cada una; el nombre para mostrar lo resuelve el frontend en la feature 032), la estrategia que fijó el precio de mercado vigente, si hay un cambio pendiente de aplicar en el próximo recálculo, y el autor e instante del último cambio si existió. Sin credenciales MUST responder 401.
- **FR-019**: El sistema MUST exponer una operación para cambiar la estrategia activa, permitida sólo a ADMIN (JWT de un ADMIN, API key de un dueño ADMIN o API key de servicio sin dueño). Un USER MUST recibir 403; sin credenciales, 401. La verificación MUST hacerse sin modificar `SecurityConfig`.
- **FR-020**: El código recibido en el cuerpo MUST recortarse (espacios al inicio y al final) antes de validarse. Un código inexistente o un cuerpo inválido MUST producir 400 con la lista de códigos válidos, sin cambiar nada ni auditar.
- **FR-021**: Un cambio efectivo MUST persistir y sobrevivir reinicios; la estrategia persistida MUST prevalecer sobre `valuation.active-strategy`, que pasa a ser sólo el valor inicial cuando nunca hubo un cambio.
- **FR-022**: Un cambio efectivo MUST registrarse en la auditoría inmutable con autor (usuario o actor de sistema), instante, correlation id, estado anterior y estado posterior, en la misma transacción que el cambio: si la auditoría falla, el cambio MUST NOT aplicarse. Además, cada recálculo que cambia la estrategia de mercado MUST registrar una única auditoría por corrida, con actor de sistema, estado anterior y posterior, id de corrida y correlation id; el `marketValue` de cada jugador no se audita, como en la feature 016.
- **FR-023**: Activar la estrategia que ya está activa MUST responder 200 sin cambios y sin nuevo registro de auditoría.
- **FR-024**: El cambio MUST NOT modificar `marketValue`, cotizaciones, órdenes, saldos ni tenencias, y MUST NOT disparar un recálculo. El próximo recálculo que comience después del cambio MUST usar la nueva estrategia activa para fijar `marketValue`; una corrida ya iniciada MUST terminar con la estrategia con que comenzó.
- **FR-025**: Inmediatamente después de un cambio, todas las consultas que usan la estrategia activa por defecto (ranking, historial de cotizaciones, oportunidades, consulta de estrategia activa) MUST reflejar la nueva activa; ningún resultado guardado en caché MUST devolver datos de la activa anterior como si fueran de la nueva.

**Errores, documentación y alcance**

- **FR-026**: Todas las operaciones de esta feature MUST requerir credenciales válidas (401 sin ellas) y usar el formato de error común; el error de estrategia inexistente MUST ser el mismo que ya usan ranking e historial.
- **FR-027**: Todas las operaciones nuevas y el parámetro agregado al portfolio MUST estar documentados en OpenAPI con parámetros, límites, esquemas, ejemplos, seguridad y respuestas 200, 400, 401 y 403 (y 404 en portfolio).
- **FR-028**: Las consultas MUST leer únicamente cotizaciones y datos persistidos; MUST NOT calcular cotizaciones en memoria, disparar recálculos ni consultar fuentes externas, y MUST NOT modificar datos.
- **FR-029**: La cantidad de consultas de datos de portfolio, oportunidades y cotización por lote MUST ser constante respecto de la cantidad de posiciones, jugadores o ids.
- **FR-030**: La feature MUST NOT modificar archivos de Entrega 1 (entre otros `PlayerController`, `GlobalExceptionHandler`, `SecurityConfig` y `CacheConfig`). Si un cambio en uno de ellos resultara imprescindible, el trabajo MUST detenerse y pedir autorización indicando archivo, cambio mínimo y alternativa considerada.

### Key Entities

- **Estrategia de valuación**: definición del catálogo (código y versión, con una etiqueta derivada de ambos). Existen OFFICIAL v1 y MOMENTUM v1.
- **Configuración de estrategia activa**: valor global persistido que indica qué estrategia fija el precio de mercado en los próximos recálculos, con autor e instante del último cambio.
- **Estrategia alternativa**: la estrategia del catálogo distinta de la activa; se deriva, no se guarda.
- **Estrategia de mercado**: la estrategia con que está fijado el precio de mercado vigente (`marketValue`), es decir, la activa en el último recálculo que actualizó precios. Coincide con la activa salvo cuando hay un cambio pendiente de aplicar en el próximo recálculo.
- **Cotización**: registro inmutable del historial por jugador y estrategia, con valor, estado e instante; la más reciente por estrategia es la que se compara.
- **Posición valuada con estrategia**: vista calculada de una posición del portfolio con la cotización de otra estrategia; no es una operación financiera.
- **Oportunidad**: jugador con la diferencia entre su precio de mercado y su cotización alternativa, clasificado como subvaluado o sobrevaluado.
- **Registro de auditoría**: entrada inmutable con autor, instante, correlation id y estados anterior y posterior del cambio de estrategia activa o del cambio de estrategia de mercado al terminar un recálculo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En el 100% de los escenarios de prueba, los valores y resultados con estrategia del portfolio coinciden a 4 decimales con el cálculo manual, y los totales cumplen valor total = suma de posiciones, resultado = valor − costo y patrimonio = saldo + valor.
- **SC-002**: El 100% de los portfolios consultados sin estrategia es idéntico campo por campo a la respuesta previa a la feature.
- **SC-003**: En un conjunto de prueba con jugadores calculados, no calculados, sin cotización alternativa y con diferencia cero, el 100% de los jugadores queda clasificado, excluido y ordenado según las reglas, con porcentajes correctos a 2 decimales.
- **SC-004**: El Catálogo obtiene la cotización alternativa de una página completa de jugadores (hasta 100) con una sola consulta adicional.
- **SC-005**: Después de un cambio de estrategia por ADMIN, el 100% de los recálculos siguientes fija el precio de mercado con la nueva estrategia, la elección sobrevive a un reinicio y existe exactamente un registro de auditoría por cambio efectivo.
- **SC-006**: El 100% de los intentos de cambio por USER recibe 403, el 100% de los pedidos sin credenciales recibe 401 y el 100% de los códigos de estrategia inexistentes recibe 400, sin efectos sobre datos.
- **SC-007**: Con 2.500 jugadores y su historial de cotizaciones de ambas estrategias, cada consulta de oportunidades, de cotización por lote y de portfolio con estrategia responde en menos de 2 segundos en el entorno de integración del proyecto.
- **SC-008**: Una persona integradora identifica en la documentación, sin leer código, el 100% de los parámetros, campos, reglas de acceso y respuestas de las operaciones de la feature.
- **SC-009**: Las consultas de la feature producen cero cambios de datos, cero cálculos de cotizaciones y cero llamadas a fuentes externas.

## Assumptions

- El catálogo tiene exactamente dos estrategias, por lo que la alternativa es siempre la otra. Si en el futuro hubiera más, la definición de alternativa deberá revisarse; el portfolio y la consulta por lote ya aceptan cualquier estrategia explícita.
- "Las dos cotizaciones" de una oportunidad son el precio de mercado (fijado por la estrategia de mercado) y la última cotización alternativa; se informan además la estrategia activa y la de mercado para dar contexto.
- La exclusión de `NO_STATS` e `INSUFFICIENT_HISTORY` en oportunidades se aplica a la última cotización de la alternativa y de la estrategia de mercado, porque un valor conservado no refleja rendimiento actual y produciría falsas oportunidades. El portfolio, en cambio, valúa con cualquier estado porque ese valor es el que fijaría el precio.
- El precio de mercado vigente se informa como fijado por la estrategia activa en la última corrida que actualizó precios; si nunca hubo corrida, se considera fijado por la activa inicial.
- Las reglas de acceso, formato de error, paginación existente y escala de 4 decimales se reutilizan de las specs 016, 018 y 028.
- La auditoría reutiliza el registro inmutable del mercado (spec 026) incorporando una acción nueva para el cambio de estrategia activa.
- El frontend que consume estas consultas (selector, Álbum, Portfolio y vista de oportunidades) se especifica en una feature aparte.

## Out of Scope

- Cálculo de cotizaciones en memoria o simulaciones con estrategias o parámetros no guardados.
- Recalcular automáticamente al cambiar la estrategia activa o modificar `marketValue` en el momento del cambio.
- Reprecio de órdenes abiertas del superusuario tras un cambio de estrategia.
- Crear, editar o versionar estrategias y sus parámetros.
- Modificar el contrato de `GET /players` u otros archivos de Entrega 1.
- Vistas de frontend.
- Historial de oportunidades o alertas.
