# Feature Specification: Modelo de datos de la cotización y catálogo de estrategias de valuación

**Feature Branch**: `feature/cotizacion-modelo`

**Created**: 2026-10-14

**Status**: Draft

**Input**: User description: "Modelo de datos de la cotización: el registro inmutable de cada cotización y la definición en código de las dos estrategias de valuación, con pesos declarados y versión. Sin cálculo, sin recálculo, sin job y sin endpoints todavía. (Requerimientos 1–9, regla de archivos protegidos de Entrega 1 y fuera de alcance según el pedido del equipo.)"

## Contexto y restricciones

Hoy no existe nada de cotización en el sistema: cada jugador tiene un valor de mercado inicial de 1.00 crédito y no hay historial. Esta feature establece la base sobre la que se apoyarán las features 015–020: el registro histórico e inmutable de cotizaciones y la definición de las dos estrategias de valuación (OFFICIAL y MOMENTUM) con sus pesos explícitos y versionados. No calcula ni guarda cotizaciones reales, no expone endpoints y no agenda procesos.

Cobertura de la consigna:

| Consigna | Cómo lo cubre esta feature |
|----------|----------------------------|
| "Al menos dos estrategias configurables de ponderación" | Se definen OFFICIAL y MOMENTUM. |
| "Cada métrica deberá tener un peso configurable dentro de la estrategia" / "ponderar distinto según posición" | Cada estrategia declara sus pesos por métrica y por posición como datos explícitos, no escondidos en el algoritmo; los pesos pueden ser positivos o negativos. |
| "Seleccionarse, configurarse y dejar traza de cuál fue utilizada" | Una estrategia activa única, seleccionable por configuración; cada cotización guarda código y versión de la estrategia que la generó. |
| "Almacenarse el historial" / "consultar cotización actual, histórica y para una fecha dada" | Cotizaciones inmutables con consultas por jugador, estrategia y fecha. |
| "En el momento cero cada token tendrá un valor inicial de 1 crédito" | Ambas estrategias tienen valor base 1.00. |

Decisión del equipo: las estrategias se definen en código, no en archivos de configuración ni en la base de datos. No se espera modificarlas; si algún día cambian sus pesos o parámetros, se publica una versión nueva.

Los archivos de Entrega 1 (modelos, repositorios y `application.properties`) están protegidos: no pueden modificarse sin consulta previa. Si un cambio fuese imprescindible, debe registrarse `[NEEDS CLARIFICATION]` con el archivo, el cambio mínimo y una alternativa.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar cotizaciones como historial inmutable y trazable (Priority: P1)

Como equipo que construye el motor de cotizaciones (features 016–020), necesitamos un registro donde cada cotización calculada quede guardada para siempre con el jugador, el precio del token, el puntaje, la estrategia y versión que la produjo, el momento del cálculo, la corrida y el correlation id, de modo que cualquier precio pueda auditarse y explicarse después.

**Why this priority**: Sin este registro no hay historial de cotizaciones ni trazabilidad de estrategia, ambos obligatorios por la consigna y la constitución (1.2 Trazabilidad de Valuación). Todas las features posteriores dependen de él.

**Independent Test**: Guardar cotizaciones de prueba para un jugador y verificar que se recuperan con todos sus datos intactos, y que el sistema no ofrece ninguna operación para modificarlas o borrarlas.

**Acceptance Scenarios**:

1. **Given** un jugador existente, **When** se registra una cotización con estado CALCULATED, valor, puntaje, estrategia OFFICIAL versión 1, fecha/hora de cálculo, id de corrida y correlation id, **Then** al recuperarla todos esos datos son idénticos a los registrados.
2. **Given** una cotización ya registrada, **When** se intenta modificarla o borrarla a través del sistema, **Then** no existe operación disponible para hacerlo y el registro permanece igual.
3. **Given** un jugador sin estadísticas, **When** se registra una cotización con estado NO_STATS, **Then** se guarda con su valor y sin puntaje, y queda distinguible de una cotización calculada.
4. **Given** una cotización a la que le falta un dato obligatorio (jugador, valor, estrategia, versión, fecha/hora, id de corrida, correlation id o estado), **When** se intenta registrarla, **Then** el registro es rechazado.

---

### User Story 2 - Consultar cotización actual, histórica y vigente en una fecha (Priority: P1)

Como features consumidoras (endpoints 017–018, mercado y portfolio), necesitamos consultar la última cotización de un jugador para una estrategia, su historial en un rango de fechas, la cotización vigente en una fecha dada y la última cotización de todos los jugadores para una estrategia.

**Why this priority**: Son las consultas que pide la consigna ("cotización actual e histórica", "para una fecha dada") y las que habilitan ranking, gráfico de evolución y valuación de portfolio.

**Independent Test**: Con un conjunto de cotizaciones de varios jugadores, ambas estrategias y distintas fechas, ejecutar cada una de las cuatro consultas y comparar con el resultado esperado, tanto en el entorno de pruebas liviano como contra la base de datos real del entorno e2e.

**Acceptance Scenarios**:

1. **Given** un jugador con cotizaciones OFFICIAL del 1, 8 y 15 de un mes y MOMENTUM del 16, **When** se pide su última cotización OFFICIAL, **Then** se obtiene la del 15.
2. **Given** el mismo jugador, **When** se pide su historial OFFICIAL entre el 1 y el 8 inclusive, **Then** se obtienen las del 1 y 8, ordenadas de la más antigua a la más reciente, y ninguna de MOMENTUM.
3. **Given** el mismo jugador, **When** se pide la cotización OFFICIAL vigente el día 10, **Then** se obtiene la del 8 (la última calculada en o antes de esa fecha/hora).
4. **Given** el mismo jugador, **When** se pide la cotización OFFICIAL vigente en una fecha anterior a su primera cotización, **Then** el resultado es vacío.
5. **Given** tres jugadores con varias cotizaciones OFFICIAL y un cuarto sin ninguna, **When** se pide la última cotización OFFICIAL de todos los jugadores, **Then** se obtiene exactamente una cotización por cada uno de los tres jugadores (la más reciente) y ninguna para el cuarto.
6. **Given** un jugador sin cotizaciones, **When** se pide su última cotización o su historial, **Then** el resultado es vacío y no un error.

---

### User Story 3 - Estrategias declaradas en código, con pesos explícitos y versión (Priority: P1)

Como equipo, queremos que OFFICIAL y MOMENTUM existan como definiciones explícitas con código, versión, tipo de algoritmo, valor base, factor de escala, variación máxima y pesos por métrica y por posición, disponibles en un catálogo, para que los algoritmos (015, 019) sólo lean esos datos y cualquiera pueda ver qué pondera cada estrategia.

**Why this priority**: La consigna exige pesos configurables por métrica y por posición; tenerlos como datos declarados es lo que los hace visibles, testeables y versionables.

**Independent Test**: Consultar el catálogo y verificar que devuelve exactamente OFFICIAL v1 y MOMENTUM v1 con sus parámetros, que la búsqueda por código funciona y que un código inexistente produce un error claro.

**Acceptance Scenarios**:

1. **Given** el catálogo de estrategias, **When** se piden todas, **Then** se obtienen exactamente OFFICIAL v1 y MOMENTUM v1.
2. **Given** el catálogo, **When** se busca "OFFICIAL", **Then** se obtiene la definición OFFICIAL v1 con valor base 1.00 y pesos para las cuatro posiciones (arquero, defensor, mediocampista, delantero).
3. **Given** la definición OFFICIAL v1, **When** se leen los pesos del perfil de referencia, **Then** coinciden exactamente con la fórmula de la consigna: goles 0.25, asistencias 0.15, tiros 0.10, pases clave 0.10, gambetas 0.10, tackles 0.10, rating 0.20.
4. **Given** la definición MOMENTUM v1, **When** se leen sus pesos, **Then** las tarjetas tienen peso negativo y los minutos jugados (disponibilidad) tienen peso positivo.
5. **Given** el catálogo, **When** se busca un código inexistente, **Then** se obtiene un error claro que nombra el código pedido y los códigos válidos.

---

### User Story 4 - Seleccionar la estrategia activa del sistema (Priority: P2)

Como operador del sistema, quiero que exista una única estrategia activa —la que define el precio de mercado—, que sea OFFICIAL por defecto y que pueda cambiarse por configuración sin editar los archivos de configuración existentes.

**Why this priority**: La consigna exige que las estrategias puedan seleccionarse; el valor por defecto garantiza que el sistema funcione sin configuración adicional.

**Independent Test**: Levantar el sistema sin configuración y verificar que la activa es OFFICIAL; levantarlo con la propiedad apuntando a MOMENTUM y verificar que la activa es MOMENTUM; levantarlo con un código inexistente y verificar que no arranca y explica por qué.

**Acceptance Scenarios**:

1. **Given** que no se configuró la estrategia activa, **When** se pide la activa al catálogo, **Then** se obtiene OFFICIAL.
2. **Given** la propiedad de estrategia activa configurada en MOMENTUM, **When** se pide la activa, **Then** se obtiene MOMENTUM, y el catálogo sigue devolviendo ambas estrategias.
3. **Given** la propiedad configurada con un código inexistente, **When** el sistema arranca, **Then** el arranque falla con un mensaje que nombra el valor inválido y los códigos válidos.

---

### User Story 5 - Proteger las versiones publicadas (Priority: P2)

Como equipo, queremos que la suite de tests falle si alguien cambia los pesos o parámetros de una versión de estrategia ya publicada sin subir su número de versión, para que el historial de cotizaciones nunca quede asociado a una versión cuyo significado cambió.

**Why this priority**: La trazabilidad de la versión sólo tiene valor si "OFFICIAL v1" significa siempre lo mismo.

**Independent Test**: Alterar un peso de OFFICIAL v1 sin cambiar la versión y verificar que la suite falla; alterar el mismo peso y subir la versión (registrando la nueva) y verificar que pasa.

**Acceptance Scenarios**:

1. **Given** OFFICIAL v1 publicada, **When** se cambia un peso, el valor base, el factor de escala, la variación máxima o el tipo de algoritmo manteniendo la versión 1, **Then** el test de protección de versiones falla e indica la estrategia y versión afectadas.
2. **Given** el mismo cambio acompañado de una versión nueva registrada como publicada, **When** se ejecuta la suite, **Then** el test pasa y la huella de la versión 1 queda intacta.
3. **Given** que no hubo cambios en las definiciones, **When** se ejecuta la suite, **Then** el test pasa.

---

### User Story 6 - Recolectar gambetas para la fórmula OFFICIAL (Priority: P2)

Como equipo, queremos que las estadísticas de cada jugador incluyan las gambetas exitosas, recolectadas de la misma fuente que el resto, para que el término `0.10·gambetas` de la fórmula de la consigna tenga datos reales.

**Why this priority**: Sin este dato, el 10% del peso de OFFICIAL v1 no tendría de dónde salir. Se necesita antes de que la feature 015 calcule, pero no bloquea el registro ni las consultas.

**Independent Test**: Ejecutar la recolección de estadísticas con una respuesta simulada de la fuente que incluya gambetas y verificar que quedan guardadas; repetir con una respuesta sin gambetas y verificar que el dato queda ausente y el resto se guarda.

**Acceptance Scenarios**:

1. **Given** una respuesta de la fuente con 12 gambetas exitosas para un jugador, **When** se recolectan sus estadísticas, **Then** quedan registradas 12 gambetas junto con el resto de sus estadísticas.
2. **Given** una respuesta de la fuente sin dato de gambetas para un jugador, **When** se recolectan sus estadísticas, **Then** las gambetas quedan ausentes (no 0) y goles, asistencias, tiros, pases clave, tackles, rating, minutos y tarjetas se guardan igual que hoy.
3. **Given** estadísticas guardadas antes de esta feature, **When** se aplica el cambio, **Then** esos registros siguen siendo válidos, con las gambetas ausentes.

### Edge Cases

- Falla sólo la consulta de gambetas a la fuente: el resto de las estadísticas se recolecta igual y las gambetas quedan ausentes; a diferencia de tiros, pases clave y tackles, la falla de esta consulta no hace fallar la liga.

- Dos cotizaciones del mismo jugador y estrategia con la misma fecha/hora de cálculo: la "última" y la "vigente" se resuelven de forma determinística, eligiendo la registrada más tarde.
- Consulta de historial con fecha inicio posterior a fecha fin: resultado vacío, sin error.
- Consultas en los bordes del rango: una cotización calculada exactamente en la fecha/hora de inicio o de fin queda incluida.
- Cotización vigente pedida exactamente en la fecha/hora de una cotización: se devuelve esa cotización.
- Cotizaciones de distintas versiones de la misma estrategia (por ejemplo, v1 y una futura v2): las consultas por estrategia las consideran parte del mismo historial y cada registro conserva su versión.
- Diferencias de comportamiento de fechas y zonas horarias entre la base de pruebas liviana y la base real: las cuatro consultas se verifican también contra la base real.
- La limpieza total de tablas entre tests e2e no puede romper el catálogo de estrategias, porque ninguna estrategia depende de datos sembrados en la base.
- Jugadores con varias posiciones: las definiciones declaran pesos para las cuatro posiciones; elegir qué perfil aplicar a un jugador multiposición es responsabilidad de los algoritmos (015, 019).
- Métricas sin dato en la fuente: la definición declara el peso igual; cómo tratar el dato ausente es responsabilidad de los algoritmos.

## Requirements *(mandatory)*

### Functional Requirements

**Registro de cotización (PlayerQuote)**

- **FR-001**: El sistema MUST registrar cada cotización con: jugador, valor (precio del token en créditos), puntaje (score), código de estrategia, versión de estrategia, fecha/hora de cálculo, id de corrida, correlation id y estado.
- **FR-002**: El estado MUST ser uno de CALCULATED, NO_STATS o INSUFFICIENT_HISTORY.
- **FR-003**: Jugador, valor, código y versión de estrategia, fecha/hora de cálculo, id de corrida, correlation id y estado MUST ser obligatorios; el puntaje MUST estar presente cuando el estado es CALCULATED y MAY estar ausente en los demás estados.
- **FR-004**: El valor MUST ser no negativo y conservar al menos 4 decimales sin redondeo.
- **FR-005**: Una cotización registrada MUST ser inmutable: el sistema MUST NOT ofrecer operaciones para modificarla ni borrarla.
- **FR-006**: La fecha/hora de cálculo MUST almacenarse como un instante absoluto, independiente de la zona horaria del servidor o de la base.

**Consultas del historial**

- **FR-007**: El sistema MUST permitir obtener la última cotización de un jugador para una estrategia (por código).
- **FR-008**: El sistema MUST permitir obtener el historial de cotizaciones de un jugador para una estrategia en un rango de fechas inclusivo en ambos extremos, ordenado cronológicamente ascendente.
- **FR-009**: El sistema MUST permitir obtener la cotización vigente de un jugador para una estrategia en una fecha/hora dada, definida como la última calculada en o antes de esa fecha/hora.
- **FR-010**: El sistema MUST permitir obtener, para una estrategia, la última cotización de cada jugador que tenga al menos una, exactamente una por jugador.
- **FR-011**: Ante empates en la fecha/hora de cálculo, las consultas de "última" y "vigente" MUST resolver de forma determinística a favor del registro más reciente.
- **FR-012**: Cada una de las cuatro consultas MUST estar respaldada por un índice de la base de datos (constitución 5.2: índices para el historial de cotizaciones).

**Definición de estrategias**

- **FR-013**: Cada estrategia MUST definirse en código con: código, versión, tipo de algoritmo (OFFICIAL o MOMENTUM), valor base, factor de escala, variación máxima y pesos por métrica y por posición; además, para MOMENTUM, sus parámetros de forma reciente (ventana de cotizaciones previas e historial mínimo).
- **FR-014**: Los pesos MUST ser datos declarados y legibles de la definición, no constantes ocultas dentro de un algoritmo, y MUST admitir valores positivos y negativos.
- **FR-015**: Cada estrategia MUST declarar un conjunto de pesos para cada una de las cuatro posiciones: arquero, defensor, mediocampista y delantero.
- **FR-016**: Las métricas ponderables MUST cubrir como mínimo: goles, asistencias, tiros, pases clave, gambetas, tackles, rating, minutos jugados, tarjetas amarillas y tarjetas rojas.
- **FR-017**: El sistema MUST NOT persistir las estrategias en una tabla ni depender de datos sembrados en la base.

**Definiciones iniciales**

- **FR-018**: OFFICIAL v1 MUST tener valor base 1.00 y usar como perfil de referencia los pesos de la fórmula de la consigna: score = 0.25·goles + 0.15·asistencias + 0.10·tiros + 0.10·pases clave + 0.10·gambetas + 0.10·tackles + 0.20·rating, con variantes por posición que den a goles/tiros en delanteros y a tackles en defensores más peso que en el perfil de referencia (mediocampista), conservando en cada posición pesos no negativos que sumen 1.00. Las gambetas se obtienen según FR-033 a FR-035.
- **FR-019**: MOMENTUM v1 MUST tener valor base 1.00, ponderar la forma reciente del jugador, asignar peso negativo a tarjetas amarillas y rojas (penalización mayor para la roja) y peso positivo a minutos jugados (disponibilidad).
- **FR-020**: Ambas estrategias MUST tener factor de escala mayor que cero y variación máxima por recálculo mayor que cero y no superior al 100%.

**Catálogo y estrategia activa**

- **FR-021**: Un catálogo MUST devolver todas las estrategias, buscar una por código y devolver la estrategia activa.
- **FR-022**: Buscar un código inexistente MUST producir un error claro que nombre el código pedido y los códigos válidos.
- **FR-023**: Debe existir exactamente una estrategia activa para todo el sistema; es la que define el precio de mercado.
- **FR-024**: La estrategia activa MUST ser OFFICIAL por defecto y MUST poder cambiarse mediante una propiedad de configuración cuyo valor por defecto vive en el código, sin editar los `application.properties` existentes.
- **FR-025**: Si la propiedad de estrategia activa indica un código inexistente, el sistema MUST fallar al arrancar con un mensaje que nombre el valor inválido y los códigos válidos.
- **FR-026**: Seleccionar la estrategia activa MUST NOT excluir a ninguna estrategia del catálogo: ambas siguen disponibles para calcularse y guardarse (feature 016); la activa sólo decide el precio de mercado.

**Protección de versiones**

- **FR-027**: Debe existir un test que registre una huella de los parámetros de cada versión publicada (tipo de algoritmo, valor base, factor de escala, variación máxima, parámetros de MOMENTUM y todos los pesos por posición y métrica) y falle si la definición vigente con esa misma versión difiere de la huella.
- **FR-028**: El catálogo MUST NOT contener dos definiciones con el mismo código.

**Pruebas y alcance**

- **FR-029**: Los tests de esta feature que levanten contexto de aplicación MUST usar el perfil de pruebas unitarias del proyecto.
- **FR-030**: Las cuatro consultas MUST verificarse además con un test e2e contra la base de datos real del entorno e2e.
- **FR-031**: La feature MUST NOT modificar modelos, repositorios ni `application.properties` de Entrega 1 sin aprobación explícita del equipo; si un cambio resultara imprescindible, MUST registrarse como `[NEEDS CLARIFICATION]` con archivo, cambio mínimo y alternativa.
- **FR-032**: La feature MUST NOT calcular cotizaciones, ejecutar recálculos, actualizar el valor de mercado de los jugadores, agendar procesos ni exponer endpoints.

**Métrica de gambetas (cambio aprobado a Entrega 1)**

- **FR-033**: Las estadísticas del jugador MUST incluir la cantidad de gambetas exitosas como dato opcional. Este es el único cambio aprobado por el equipo a un modelo de Entrega 1 (`PlayerStats`), y es aditivo: no modifica ni elimina datos existentes.
- **FR-034**: La recolección de estadísticas desde WhoScored MUST completar las gambetas de cada jugador con la misma fuente y el mismo cruce por jugador y equipo que tiros, pases clave y tackles.
- **FR-035**: Si la fuente no informa gambetas para un jugador, o no responde, el dato MUST quedar ausente (no 0), y el resto de las estadísticas del jugador MUST guardarse igual que hoy.

### Key Entities

- **Cotización de jugador (PlayerQuote)**: Registro histórico e inmutable del precio del token de un jugador según una estrategia y versión en un instante. Atributos: jugador, valor, puntaje, código y versión de estrategia, fecha/hora de cálculo, id de corrida, correlation id, estado. Relación: muchas cotizaciones por jugador; referencia a la estrategia por código y versión (no por clave de tabla).
- **Estado de cotización**: CALCULATED (calculada con datos suficientes), NO_STATS (el jugador no tenía estadísticas), INSUFFICIENT_HISTORY (no había historial suficiente para la estrategia).
- **Definición de estrategia**: Código, versión, tipo de algoritmo, valor base, factor de escala, variación máxima, tabla de pesos por posición y métrica y, sólo en MOMENTUM, parámetros de forma reciente (ventana e historial mínimo). Vive en código; nunca en la base.
- **Tipo de algoritmo**: OFFICIAL o MOMENTUM; indica qué algoritmo (015 o 019) interpreta los pesos.
- **Métrica ponderable**: Cada estadística que puede recibir peso (goles, asistencias, tiros, pases clave, gambetas, tackles, rating, minutos jugados, tarjetas amarillas, tarjetas rojas).
- **Catálogo de estrategias**: Conjunto de definiciones disponibles, con búsqueda por código y la estrategia activa.
- **Huella de versión publicada**: Registro de los parámetros de cada versión ya publicada, usado por el test de protección.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El catálogo expone exactamente 2 estrategias (OFFICIAL v1 y MOMENTUM v1), cada una con pesos declarados para 4 posiciones y valor base 1.00.
- **SC-002**: Los pesos del perfil de referencia de OFFICIAL v1 coinciden con los 7 coeficientes de la fórmula de la consigna, con 0 diferencias.
- **SC-003**: El 100% de las cotizaciones registradas en los tests se recupera con sus 9 datos idénticos a los registrados, y 0 operaciones de modificación o borrado están disponibles.
- **SC-004**: Las 4 consultas devuelven el resultado esperado en el 100% de los escenarios de aceptación, tanto en el entorno de pruebas liviano como en el e2e contra la base real.
- **SC-005**: Cambiar cualquier parámetro de una versión publicada sin subir la versión hace fallar la suite en el 100% de los casos; sin cambios, la suite pasa.
- **SC-006**: Sin configuración adicional la estrategia activa es OFFICIAL; con la propiedad configurada cambia a la indicada; con un código inválido el sistema no arranca y el mensaje permite identificar el error sin leer código.
- **SC-007**: La feature introduce 0 tablas de estrategias, 0 datos sembrados, 0 endpoints, 0 jobs, y un único cambio a archivos protegidos de Entrega 1: el dato aditivo de gambetas aprobado por el equipo.
- **SC-008**: Cuando la fuente informa gambetas, se guardan para el 100% de los jugadores recolectados; cuando no las informa, el 100% del resto de las estadísticas se guarda igual que antes de esta feature.

## Assumptions

- `specs/013-cotizacion-modelo` es el directorio solicitado y es independiente del nombre de cualquier rama Git.
- `@UnitTestProfile`, `@E2ETest`, `E2EDatabaseCleaner` y `E2EFixtures` ya están en `main` (features 009, 010 y 012) y se reutilizan sin cambios.
- Las estadísticas del jugador son hoy una única fila por jugador (sin serie temporal). Cómo obtiene MOMENTUM la "forma reciente" a partir de eso es responsabilidad de la feature 019; esta feature sólo declara los pesos.
- El historial se consulta por jugador **y estrategia**, porque mezclar precios de estrategias distintas en una misma serie no tiene sentido; la consulta incluye todas las versiones de esa estrategia.
- El rango de fechas es inclusivo en ambos extremos y las fechas son instantes absolutos (UTC).
- En cotizaciones NO_STATS e INSUFFICIENT_HISTORY se registra igualmente un valor (qué valor exactamente lo decide la feature 016); el puntaje puede faltar.
- La cotización referencia al jugador existente sin modificar la entidad `Player`; los jugadores no se borran en el flujo normal.
- El catálogo expone una sola versión vigente por código; las versiones anteriores sólo se conservan como huellas en el test de protección y como dato en las cotizaciones ya guardadas.
- En OFFICIAL v1 el perfil de referencia (fórmula de la consigna tal cual) se aplica a mediocampistas; delanteros, defensores y arqueros redistribuyen esos pesos manteniendo la suma 1.00. Los valores exactos por posición, el factor de escala y la variación máxima de cada estrategia se fijan en el plan.
- Los pesos de MOMENTUM v1 (incluido cómo se refleja la "forma reciente") se fijan en el plan; la interpretación temporal de "forma reciente" corresponde al algoritmo de la feature 019.
- El nombre exacto de la propiedad de estrategia activa se fija en el plan; su valor por defecto vive en el código.

## Out of Scope

- Algoritmos de cálculo de score y precio (features 015 y 019).
- Corrida de recálculo, persistencia de cotizaciones reales, actualización de `Player.marketValue` y `POST /quotes/recalculate` (feature 016).
- Job semanal de recálculo (feature 020).
- Endpoints de consulta de cotizaciones y ranking (features 017 y 018).
- Tablas, archivos YAML o administración en tiempo de ejecución de estrategias.
- Agregar a las estadísticas del jugador otras métricas además de las gambetas.
