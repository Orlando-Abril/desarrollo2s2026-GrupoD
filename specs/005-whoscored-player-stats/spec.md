# Feature Specification: Estadísticas de rendimiento de jugadores desde WhoScored

**Feature Branch**: `feature/catalogo-whoscored`

**Created**: 2026-09-25

**Status**: Draft

**Input**: User description: "Enriquecimiento del catálogo existente de jugadores con datos crudos de rendimiento obtenidos mediante scraping de WhoScored. Los jugadores ya existen y están persistidos (cargados desde Football-Data.org). Objetivo único: obtener métricas de rendimiento desde WhoScored (minutos, goles, asistencias, tiros, pases clave, tackles, amarillas, rojas, rating), asociarlas con jugadores existentes mediante matching best-effort por nombre normalizado y equipo, persistirlas localmente, cachearlas con TTL configurable y actualizarlas periódicamente mediante un scheduler simple, configurable y deshabilitable. Errores aislados por jugador, sin borrar datos válidos previos. Sin valuación, cotizaciones, mercado, Flyway ni infraestructura adicional."

## Contexto

Esta feature **depende** del catálogo implementado en `003-football-data-players` y simplificado en `004-simplificar-catalogo-jugadores`. Los jugadores ya existen localmente con nombre, equipo, liga y posición; esta feature no los crea, no los modifica y no cambia cómo se consultan.

Su única responsabilidad es:

> Obtener desde WhoScored las métricas crudas de rendimiento de los jugadores ya existentes, asociarlas al jugador correcto cuando la coincidencia sea clara, guardarlas localmente y mantenerlas actualizadas de forma periódica.

Las métricas quedan disponibles como datos guardados. Su uso para valuación, cotizaciones, ranking o mercado corresponde a entregas posteriores.

## Clarifications

### Session 2026-09-25

- Q: Si una actualización trae sólo algunas métricas de un jugador que ya tenía un conjunto guardado, ¿qué pasa con las que faltan? → A: Se reemplaza el conjunto completo; las métricas no informadas quedan como ausentes (no se conservan valores anteriores por métrica).
- Q: ¿Las métricas guardadas se exponen en algún endpoint en esta entrega? → A: No. Sólo se persisten; `GET /players` no cambia y la exposición queda como trabajo futuro.
- Q: ¿La actualización periódica viene habilitada o deshabilitada por defecto fuera de los tests? → A: Deshabilitada por defecto (evita scraping accidental en desarrollo/CI). Se habilita por variable de entorno y, habilitada, se ejecuta una vez por semana por defecto; la frecuencia es configurable.
- Q: ¿El proceso debe espaciar las consultas a WhoScored? → A: Sí. Espera fija configurable entre consultas reales a WhoScored: 2 segundos por defecto, 0 en tests. Cuando el resultado se obtiene de la caché (sin consulta externa) no se aplica la espera.
- Q (2026-09-25, tras la primera ejecución real): ¿Cómo asociar equipos cuyo nombre en WhoScored es una versión corta del nombre del catálogo ("Tottenham" / "Tottenham Hotspur FC", "Inter" / "FC Internazionale Milano")? → A: Si no hay coincidencia exacta del equipo normalizado, se acepta por **inclusión**: el nombre local empieza con el de WhoScored o contiene todas sus palabras, siempre con un **único** candidato en la liga y un nombre de WhoScored de al menos 4 letras. Cada asociación por inclusión se registra en el log. El nombre del jugador sigue exigiendo igualdad exacta.
- Q (2026-09-25): ¿Qué hacer con los equipos cuyo nombre no se parece ni por inclusión ("Borussia Mönchengladbach" / "Borussia M.Gladbach", "Olympique Lyonnais" / "Lyon", "Stade Rennais FC 1901" / "Rennes")? → A: Una **lista fija de equivalencias de equipos en el código**, versionada y con tests (no en configuración ni en base de datos). Tiene prioridad sobre las demás reglas; si el equipo equivalente no está en la liga, se aplican las reglas habituales. No hay equivalencias de jugadores.
- Q: ¿Cuál es la vigencia por defecto de un resultado de WhoScored en caché? → A: 24 horas, configurable por variable de entorno (menor que el período semanal, para que cada ejecución programada obtenga datos frescos).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Obtener y guardar las métricas de rendimiento de los jugadores existentes (Priority: P1)

Al ejecutarse la actualización de estadísticas, el sistema recorre los jugadores ya guardados en el catálogo, obtiene sus métricas de rendimiento desde WhoScored y las guarda asociadas a cada jugador. Las métricas que WhoScored no informa quedan registradas como ausentes, nunca con valores inventados.

**Why this priority**: Es el objetivo central de la feature. Sin métricas guardadas localmente no hay datos de rendimiento que usar en entregas posteriores (el Documento de Visión exige estas métricas como base del motor de valuación).

**Independent Test**: Con jugadores guardados localmente y contenido de WhoScored simulado con datos locales representativos, ejecutar la actualización y verificar que cada jugador con coincidencia clara queda con sus métricas guardadas y que las métricas no informadas quedan como ausentes.

**Acceptance Scenarios**:

1. **Given** un jugador guardado cuya información en WhoScored incluye todas las métricas, **When** se ejecuta la actualización, **Then** quedan guardados para ese jugador minutos jugados, goles, asistencias, tiros, pases clave, tackles, tarjetas amarillas, tarjetas rojas y rating, con los mismos valores que informa la fuente.
2. **Given** un jugador cuya información en WhoScored no incluye una o más métricas, **When** se ejecuta la actualización, **Then** esas métricas quedan registradas como ausentes (no como cero ni con otro valor por defecto) y las demás se guardan normalmente.
3. **Given** un jugador que ya tenía métricas guardadas, **When** una nueva actualización obtiene métricas nuevas para él, **Then** las métricas guardadas se reemplazan por las nuevas y queda registrado cuándo se obtuvieron.
4. **Given** que la actualización terminó, **When** se consulta el catálogo de jugadores, **Then** el catálogo responde igual que antes de esta feature (mismos jugadores, mismos datos y mismos filtros).

---

### User Story 2 - Asociar las métricas sólo al jugador correcto (Priority: P1)

Como WhoScored y Football-Data.org no comparten un identificador, el sistema asocia cada jugador guardado con su ficha en WhoScored comparando el nombre normalizado y el equipo. Si la coincidencia no es suficientemente clara, prefiere no asociar nada antes que asociar métricas de otro jugador.

**Why this priority**: Una asociación incorrecta contamina los datos de rendimiento de un jugador y, en entregas posteriores, su valuación. Es preferible un jugador sin métricas que uno con métricas ajenas.

**Independent Test**: Con jugadores guardados y datos simulados de WhoScored que incluyen coincidencias exactas, nombres con tildes o mayúsculas distintas, jugadores inexistentes y homónimos, ejecutar la actualización y verificar qué jugadores quedan asociados y cuáles no.

**Acceptance Scenarios**:

1. **Given** un jugador guardado y una ficha de WhoScored con el mismo nombre (diferencias sólo de mayúsculas, tildes, signos o espacios) y el mismo equipo, **When** se ejecuta la actualización, **Then** las métricas de esa ficha se asocian al jugador.
2. **Given** un jugador guardado para el que no existe ninguna ficha en WhoScored con su nombre normalizado en su equipo, **When** se ejecuta la actualización, **Then** el jugador queda sin métricas de WhoScored, se registra el caso en el log y el proceso continúa con los demás jugadores.
3. **Given** un jugador guardado para el que existen dos o más fichas candidatas igualmente válidas (por ejemplo, homónimos en el mismo equipo), **When** se ejecuta la actualización, **Then** no se asocia ninguna, se registra el caso en el log y el proceso continúa.
4. **Given** una ficha de WhoScored con el mismo nombre pero de otro equipo, **When** se ejecuta la actualización, **Then** no se asocia al jugador guardado.
5. **Given** un jugador sin coincidencia que ya tenía métricas guardadas de una actualización anterior, **When** se ejecuta la actualización, **Then** sus métricas guardadas se conservan sin cambios.

---

### User Story 3 - Actualización periódica automática y configurable (Priority: P2)

La actualización automática viene deshabilitada por defecto para evitar scraping accidental en desarrollo y CI. Una persona del equipo la habilita explícitamente mediante una variable de entorno en el entorno donde debe correr; por defecto se ejecuta una vez por semana y la frecuencia es configurable. Cuando está habilitada, el sistema ejecuta la actualización según esa programación, registrando en el log el inicio, el fin y los errores relevantes.

**Why this priority**: El Documento de Visión exige que la actualización de estadísticas sea un proceso automático. Depende de que las historias 1 y 2 funcionen, pero sin ella los datos quedarían desactualizados.

**Independent Test**: Verificar que, al dispararse, el mecanismo periódico delega la actualización en el proceso de las historias 1 y 2; que no se activa cuando está deshabilitado por configuración; y que no se ejecuta ninguna actualización al arrancar la aplicación.

**Acceptance Scenarios**:

1. **Given** la configuración por defecto (sin la variable de entorno que la habilita) o la actualización deshabilitada explícitamente, **When** la aplicación funciona o se ejecutan los tests, **Then** la actualización periódica no se ejecuta nunca.
2. **Given** la actualización periódica habilitada por variable de entorno (frecuencia semanal por defecto o la frecuencia configurada), **When** llega el momento programado, **Then** se ejecuta la actualización de estadísticas para los jugadores guardados y se registra en el log su inicio y su fin con un resumen (procesados, asociados, sin coincidencia, fallidos).
3. **Given** la aplicación recién iniciada, **When** arranca, **Then** no se ejecuta ninguna actualización fuera de la programación configurada.
4. **Given** que una ejecución falla por completo (por ejemplo, WhoScored inaccesible), **When** llega la siguiente ejecución programada, **Then** esta se realiza normalmente.

---

### User Story 4 - Evitar consultas repetidas a WhoScored (Priority: P2)

Los resultados obtenidos de WhoScored para cada jugador se conservan en caché durante un período configurable. Mientras estén vigentes, una nueva actualización los reutiliza en lugar de volver a consultar WhoScored.

**Why this priority**: El scraping es lento, frágil y puede ser bloqueado por la fuente si se repite innecesariamente. La caché reduce la carga sobre WhoScored y el riesgo de bloqueo.

**Independent Test**: Ejecutar la actualización dos veces dentro del período de vigencia con la fuente simulada y verificar que la segunda no vuelve a consultarla; luego vencer la vigencia y verificar que se vuelve a consultar.

**Acceptance Scenarios**:

1. **Given** un resultado de WhoScored vigente en caché para un jugador, **When** se ejecuta la actualización, **Then** se usa el resultado cacheado, no se consulta WhoScored para ese jugador y las métricas se guardan igual.
2. **Given** un resultado cacheado cuya vigencia expiró, **When** se ejecuta la actualización, **Then** se vuelve a consultar WhoScored para ese jugador.
3. **Given** varios jugadores sin resultado vigente en caché, **When** se ejecuta la actualización, **Then** entre dos consultas reales consecutivas a WhoScored transcurre al menos la espera configurada; los jugadores resueltos desde la caché no agregan espera.
4. **Given** que la consulta a WhoScored para un jugador falló, **When** se ejecuta la siguiente actualización, **Then** se vuelve a intentar (los fallos no quedan cacheados).
5. **Given** una consulta del catálogo de jugadores, **When** se responde, **Then** no se consulta WhoScored.

---

### User Story 5 - Tolerancia a fallas por jugador (Priority: P1)

Si WhoScored no responde, cambia su estructura, omite datos o falla para un jugador concreto, el problema afecta sólo a ese jugador: el resto se procesa normalmente, el jugador sigue existiendo en el catálogo y sus métricas válidas guardadas previamente no se pierden.

**Why this priority**: El scraping es la integración más frágil del sistema. El Documento de Visión exige que ninguna falla externa afecte los endpoints de lectura ni borre datos locales.

**Independent Test**: Con varios jugadores y la fuente simulada fallando para algunos (error de conexión, contenido con estructura inesperada), ejecutar la actualización y verificar que los demás quedan actualizados, que los fallidos conservan sus métricas anteriores y que el catálogo sigue respondiendo.

**Acceptance Scenarios**:

1. **Given** varios jugadores y una falla de WhoScored sólo para uno de ellos, **When** se ejecuta la actualización, **Then** los demás se actualizan normalmente, la falla se registra en el log y el jugador fallido conserva sus métricas previas.
2. **Given** que WhoScored no responde en absoluto, **When** se ejecuta la actualización, **Then** el proceso termina sin interrumpir la aplicación, no se borra ni altera ninguna métrica guardada y el log informa la falla.
3. **Given** que WhoScored cambió la estructura de su contenido y no se pueden reconocer las métricas de un jugador, **When** se ejecuta la actualización, **Then** se trata como falla de ese jugador (se conserva lo anterior y se registra en el log), no como métricas ausentes.
4. **Given** que WhoScored está caído o inaccesible, **When** un usuario consulta el catálogo de jugadores, **Then** recibe normalmente los jugadores guardados.
5. **Given** que la caché no está disponible, **When** se ejecuta la actualización, **Then** el proceso continúa consultando WhoScored directamente y guardando las métricas.

---

### Edge Cases

- **Nombres con tildes, diéresis, guiones o apóstrofos** (por ejemplo, "Müller", "Ødegaard", "N'Golo Kanté"): la normalización los hace comparables; si aun así no hay coincidencia exacta del nombre normalizado, el jugador queda sin asociar.
- **Nombres abreviados o distintos entre fuentes** (por ejemplo, nombre completo en una fuente y nombre corto en la otra): pueden quedar sin coincidencia. Es una limitación conocida y aceptada de esta entrega.
- **Nombres de equipo con sufijos distintos entre fuentes** (por ejemplo, "Arsenal FC" y "Arsenal"): la normalización del equipo contempla sufijos comunes; si no alcanza, el jugador queda sin asociar y se registra.
- **Jugador que cambió de equipo** y cuyo equipo local no coincide con el de WhoScored: queda sin asociar en esa ejecución; conserva las métricas previas.
- **Jugador sin minutos en la temporada** (aparece en WhoScored sin estadísticas): las métricas no informadas quedan como ausentes; las informadas como cero se guardan como cero.
- **Contenido de WhoScored vacío o sin ninguna métrica reconocible para un jugador asociado**: se trata como falla de ese jugador, no como un conjunto de métricas todas ausentes, para no pisar datos válidos.
- **Valores con formato inesperado** (por ejemplo, un rating no numérico): esa métrica queda como ausente y se registra en el log; las demás métricas del jugador se guardan.
- **Catálogo sin jugadores**: la actualización termina sin errores y el log informa cero jugadores procesados.
- **Una ejecución programada se superpone con la anterior todavía en curso**: en una única instancia, la siguiente ejecución no comienza hasta que termine la anterior.
- **Jugadores eliminados o agregados al catálogo entre ejecuciones**: cada ejecución procesa los jugadores existentes en ese momento.

## Requirements *(mandatory)*

### Functional Requirements

**Obtención de métricas**

- **FR-001**: El sistema MUST obtener desde WhoScored, para los jugadores ya guardados en el catálogo, las siguientes métricas de rendimiento: minutos jugados, goles, asistencias, tiros, pases clave, tackles, tarjetas amarillas, tarjetas rojas y rating.
- **FR-002**: El sistema MUST registrar como ausente toda métrica que WhoScored no informe o que no pueda interpretarse, sin reemplazarla por cero ni por ningún otro valor inventado.
- **FR-003**: La obtención y la interpretación del contenido de WhoScored MUST estar aisladas en un componente de integración propio, separado de la integración existente con Football-Data.org.
- **FR-004**: El sistema MUST procesar únicamente jugadores que ya existen en el catálogo local; no MUST crear, modificar ni eliminar jugadores, ligas ni posiciones.

**Asociación (matching)**

- **FR-005**: El sistema MUST asociar un jugador guardado con una ficha de WhoScored sólo cuando el nombre normalizado coincide con exactamente una ficha candidata de su equipo. El equipo se asocia por igualdad del nombre normalizado o, si no hay ninguna, por inclusión (el nombre local empieza con el de WhoScored o contiene todas sus palabras) con un único candidato en la liga (ver Clarifications 2026-09-25).
- **FR-006**: La normalización de nombres y equipos MUST ignorar diferencias de mayúsculas, tildes y diacríticos, signos de puntuación y espacios repetidos; para equipos MUST además ignorar sufijos institucionales comunes (por ejemplo, "FC", "CF", "AFC").
- **FR-007**: Cuando no existe coincidencia, o existe más de una candidata, el sistema MUST dejar al jugador sin asociar, registrar el caso en el log (jugador y motivo) y continuar con los demás jugadores.
- **FR-008**: El matching MUST ser determinístico y simple: no MUST incluir coincidencias aproximadas por similitud ni resolución de identidades asistida. La única corrección manual permitida es una lista fija de equivalencias de **equipos** definida en el código (ver Clarifications 2026-09-25); no hay equivalencias de jugadores.

**Persistencia**

- **FR-009**: El sistema MUST guardar las métricas obtenidas asociadas al jugador correspondiente, junto con la fecha y hora en que se obtuvieron, en un registro separado de los datos propios del catálogo del jugador.
- **FR-010**: El sistema MUST mantener como máximo un conjunto de métricas vigente por jugador; una actualización exitosa MUST reemplazar el conjunto anterior de ese jugador en su totalidad, incluso si trae sólo algunas métricas (las no informadas quedan como ausentes; no se combinan con valores anteriores).
- **FR-011**: El sistema MUST conservar sin cambios las métricas guardadas previamente de un jugador cuando su actualización falla, no encuentra coincidencia o no obtiene ninguna métrica reconocible.
- **FR-012**: La incorporación del registro de métricas MUST usar el mismo mecanismo de gestión de esquema de base de datos que el proyecto usa actualmente, sin introducir herramientas de migración nuevas.

**Caché**

- **FR-013**: El sistema MUST cachear por jugador los resultados obtenidos de WhoScored durante un período de vigencia configurable por propiedades/variables de entorno, de 24 horas por defecto.
- **FR-014**: Mientras exista un resultado vigente en caché para un jugador, la actualización MUST usarlo en lugar de consultar WhoScored.
- **FR-015**: El sistema MUST NOT cachear fallas de obtención, para que el jugador se reintente en la siguiente ejecución.
- **FR-016**: Si la caché no está disponible, la actualización MUST continuar consultando WhoScored directamente.
- **FR-016a**: El sistema MUST esperar un tiempo fijo configurable entre dos consultas reales consecutivas a WhoScored (2 segundos por defecto; 0 en la configuración de tests). Cuando el resultado de un jugador se obtiene de la caché, sin consulta externa, MUST NOT aplicarse la espera.

**Consulta del catálogo**

- **FR-017**: La consulta del catálogo de jugadores MUST NOT disparar consultas a WhoScored y MUST seguir respondiendo con los datos locales aunque WhoScored esté inaccesible.
- **FR-018**: Esta feature MUST NOT modificar el contrato, los filtros, la seguridad (JWT/ApiKey) ni el comportamiento de la consulta del catálogo ni de la carga desde Football-Data.org, y MUST NOT agregar endpoints nuevos: las métricas sólo se persisten.

**Tolerancia a fallas**

- **FR-019**: El sistema MUST aislar los errores por jugador: una falla al obtener, interpretar, asociar o guardar las métricas de un jugador MUST NOT impedir el procesamiento de los demás.
- **FR-020**: Una falla total de WhoScored MUST NOT interrumpir la aplicación ni alterar datos guardados; la ejecución MUST terminar y quedar registrada en el log.
- **FR-021**: El sistema MUST registrar en el log, de forma simple, los errores por jugador, los casos sin coincidencia y las fallas generales, sin almacenar esos errores en tablas ni registros de auditoría.

**Actualización periódica**

- **FR-022**: El sistema MUST ejecutar la actualización de estadísticas de forma periódica, con una frecuencia configurable por propiedades/variables de entorno.
- **FR-023**: La actualización periódica MUST estar deshabilitada por defecto en todas las configuraciones (incluidas desarrollo, CI y tests) y MUST habilitarse explícitamente mediante una variable de entorno. Una vez habilitada, su frecuencia por defecto MUST ser semanal (una ejecución por semana), configurable por propiedades/variables de entorno.
- **FR-024**: El sistema MUST NOT ejecutar ninguna actualización al iniciar la aplicación ni fuera de la programación configurada.
- **FR-025**: El mecanismo periódico MUST limitarse a disparar la actualización y MUST delegar en el proceso de actualización toda la lógica de obtención, matching, caché y persistencia.
- **FR-026**: Cada ejecución MUST registrar en el log su inicio y su finalización con un resumen: jugadores procesados, asociados y actualizados, sin coincidencia y fallidos.
- **FR-027**: En una misma instancia, una ejecución MUST NOT comenzar mientras la anterior sigue en curso.

**Testing**

- **FR-028**: Los tests automáticos MUST usar contenido local representativo de WhoScored y MUST NOT depender de Internet ni consultar WhoScored real.
- **FR-029**: Los tests MUST cubrir como mínimo: interpretación correcta de métricas, métricas ausentes, matching correcto, jugador sin coincidencia, error durante la obtención, aislamiento de errores entre jugadores, uso de la caché y delegación del mecanismo periódico en el proceso de actualización, sin esperar a que transcurra la programación real.

### Key Entities *(include if feature involves data)*

- **Jugador (existente)**: Jugador del catálogo cargado desde Football-Data.org, con nombre, equipo, liga y posiciones. Esta feature sólo lo lee para hacer el matching; no lo modifica.
- **Estadísticas de rendimiento del jugador (nueva)**: Conjunto vigente de métricas crudas obtenidas desde WhoScored para un jugador. Pertenece a exactamente un jugador y cada jugador tiene como máximo uno. Atributos: minutos jugados, goles, asistencias, tiros, pases clave, tackles, tarjetas amarillas, tarjetas rojas, rating (cada uno puede estar ausente) y fecha/hora de obtención. Opcionalmente, la referencia de la ficha de WhoScored asociada, para trazabilidad.
- **Resultado cacheado de WhoScored (transitorio)**: Métricas obtenidas para un jugador, conservadas temporalmente en caché con vigencia configurable. No es un dato persistente del sistema.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Tras una actualización con datos de prueba representativos, el 100% de los jugadores con una única coincidencia clara queda con sus métricas guardadas, y el 0% de los jugadores queda asociado a métricas de otro jugador.
- **SC-002**: En el 100% de los casos, las métricas no informadas por la fuente quedan registradas como ausentes y nunca con un valor inventado.
- **SC-003**: Si la fuente falla para uno o más jugadores, el 100% de los demás jugadores se procesa normalmente y el 100% de las métricas previas de los jugadores fallidos se conserva.
- **SC-004**: Con WhoScored inaccesible, la consulta del catálogo de jugadores responde con el 100% de los jugadores guardados y sin demoras atribuibles a la fuente externa.
- **SC-005**: Dos actualizaciones consecutivas dentro del período de vigencia de la caché no generan ninguna consulta adicional a WhoScored para los jugadores ya obtenidos.
- **SC-006**: Con la configuración por defecto (actualización periódica deshabilitada), no se registra ninguna consulta a WhoScored durante el funcionamiento de la aplicación ni durante la ejecución de los tests.
- **SC-007**: La suite de tests completa se ejecuta sin acceso a Internet y cubre los ocho escenarios exigidos en FR-029.
- **SC-008**: Tras una ejecución, el log permite determinar cuántos jugadores fueron procesados, asociados, sin coincidencia y fallidos, e identificar cada jugador sin coincidencia o con falla.

## Assumptions

- Las métricas corresponden a los totales de la **temporada en curso** de la liga doméstica del jugador, tal como los publica WhoScored. No se combinan temporadas ni competiciones.
- Se guarda **sólo el último conjunto de métricas** por jugador (sin historial de estadísticas); el historial no fue pedido y se considera trabajo futuro.
- Una actualización que obtiene métricas parciales para un jugador asociado **reemplaza** el conjunto anterior, con las métricas no informadas como ausentes. Una actualización que no obtiene **ninguna** métrica reconocible se trata como falla y conserva lo anterior.
- El matching es **por nombre normalizado exacto dentro del mismo equipo normalizado**; no se usa similitud aproximada. Se acepta que algunos jugadores queden sin asociar.
- La aplicación corre en **una única instancia**; no se coordina la ejecución entre instancias.
- WhoScored puede limitar o bloquear el acceso automatizado. Esas respuestas se tratan como fallas de obtención (por jugador o generales) sin reintentos complejos; la caché, la espera entre consultas (FR-016a) y la frecuencia semanal reducen el riesgo.
- Se reutilizan la base de datos, la caché y la infraestructura de logging existentes. Se agrega únicamente el registro necesario para las estadísticas de rendimiento.
- No se agrega un disparo manual (endpoint) de la actualización de estadísticas; la ejecución responde sólo a la programación.

## Out of Scope / Trabajo futuro

- Estrategias de valuación, cálculo de scores, cotizaciones, recálculo e historial de cotizaciones, ranking.
- Emisión, compra y venta de tokens, superusuario, portfolio, transacciones y auditoría financiera.
- Auditoría inmutable del scraping, persistencia de errores o de ejecuciones del proceso periódico.
- Historial de estadísticas por jugador o por partido.
- Exposición de las métricas en endpoints (detalle del jugador, catálogo u otros).
- Matching aproximado, resolución de identidades o corrección manual de asociaciones.
- Coordinación entre múltiples instancias, colas, reintentos automáticos y herramientas de migración de esquema.
- Otras métricas del Documento de Visión no pedidas en esta feature (por ejemplo, gambetas o intercepciones).
