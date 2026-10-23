# Feature Specification: Algoritmo de la Estrategia 1 "OFFICIAL"

**Feature Branch**: `feature/estrategia-oficial`

**Created**: 2026-10-20

**Status**: Draft

**Input**: User description: "Algoritmo de la Estrategia 1 "OFFICIAL": performance ponderada por posición según la fórmula de la consigna. Sólo el cálculo, sin persistir ni exponer. (Contexto de 013, requerimientos 1–7, regla de archivos protegidos de Entrega 1 y fuera de alcance según el pedido del equipo.)"

## Contexto y restricciones

La feature 013 dejó declarada OFFICIAL v1 como datos: valor base 1.00, factor de escala 0.10, variación máxima 0.20 y pesos por posición (arquero, defensor, mediocampista, delantero) sobre goles, asistencias, tiros, pases clave, gambetas, tackles y rating. El perfil de mediocampista es exactamente la fórmula de la consigna. Esta feature agrega el algoritmo que interpreta esa definición: a partir de las estadísticas de temporada de una población de jugadores calcula, para cada uno, su puntaje (score) y el valor de su token, o indica que no tiene estadísticas suficientes.

Es un cálculo puro: recibe todo lo que necesita (definición de estrategia, población de jugadores con sus posiciones y estadísticas, y la cotización anterior de cada uno si la hay) y devuelve un resultado por jugador. No guarda cotizaciones, no actualiza el valor de mercado, no expone endpoints y no consulta fuentes externas.

Cobertura de la consigna:

| Consigna | Cómo lo cubre esta feature |
|----------|----------------------------|
| "score = 0.25·goles + 0.15·asistencias + 0.10·tiros + 0.10·pases clave + 0.10·gambetas + 0.10·tackles + 0.20·rating" | Es el cálculo aplicado a mediocampistas con los pesos de OFFICIAL v1. |
| "Ponderar distinto según posición" | Cada posición usa sus propios pesos y se normaliza contra los jugadores de su misma posición. |
| "Valor inicial de 1 crédito" | El valor parte del valor base 1.00 de la definición. |

Restricciones del equipo:

- La definición OFFICIAL v1 es una versión publicada e inmutable: esta feature la lee y no la modifica.
- Los archivos de Entrega 1 (modelos, repositorios y `application.properties`) y la definición OFFICIAL v1 no se modifican sin consulta previa. Si un cambio resultara imprescindible, debe registrarse `[NEEDS CLARIFICATION]` con el archivo, el cambio mínimo y una alternativa.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Calcular score y valor de una población según la fórmula de la consigna (Priority: P1)

Como corrida de recálculo de cotizaciones (feature 016), necesito entregarle al algoritmo OFFICIAL la población de jugadores a valuar y recibir, por cada uno, su score en [0,1] y el valor de su token, calculados con métricas por 90 minutos normalizadas dentro de su posición y ponderadas con los pesos de OFFICIAL v1.

**Why this priority**: Es el núcleo de la Estrategia 1 y la fórmula exigida por la consigna; sin él no hay cotizaciones OFFICIAL que guardar.

**Independent Test**: Armar una población de prueba con estadísticas conocidas, ejecutar el cálculo y comparar score y valor de cada jugador contra valores calculados a mano.

**Acceptance Scenarios**:

1. **Given** dos mediocampistas con 900 minutos cada uno, donde A tiene los valores por 90 más altos en goles, asistencias, tiros, pases clave, gambetas y tackles y rating 8.0, y B los más bajos y rating 6.0, **When** se calcula sin cotización anterior, **Then** A obtiene score 0.96 y valor 1.0960, y B score 0.12 y valor 1.0120, ambos con estado CALCULATED.
2. **Given** un jugador con 5 goles en 450 minutos, **When** se calculan sus métricas, **Then** su métrica de goles antes de normalizar es 1.0 por 90 minutos.
3. **Given** un jugador con rating 7.0, **When** se normaliza su rating, **Then** vale 0.70, independientemente del rating del resto de la población.
4. **Given** una posición donde todos los jugadores tienen el mismo valor por 90 en una métrica (incluido el caso de un único jugador), **When** se normaliza esa métrica, **Then** vale 0.5 para todos ellos.
5. **Given** un único defensor con rating 7.0 y todas las demás métricas informadas, **When** se calcula, **Then** su score es 0.56 (0.5 en cada métrica de conteo y 0.70 en rating, con los pesos de defensor) y su valor 1.0560.
6. **Given** un jugador con una métrica ausente (por ejemplo, tiros) mientras otros jugadores de su posición sí la tienen, **When** se calcula, **Then** esa métrica cuenta como 0 por 90 para él y participa de la normalización con ese valor.
7. **Given** cualquier resultado CALCULATED, **When** se inspecciona, **Then** el score está en [0,1] y el valor es igual a valor base + score × factor de escala antes de acotar y redondear.

---

### User Story 2 - Excluir jugadores sin estadísticas suficientes (Priority: P1)

Como corrida de recálculo, necesito que los jugadores sin estadísticas, sin posición o con menos minutos que un umbral configurable queden marcados como NO_STATS, sin score ni valor, y que no distorsionen la normalización del resto, para conservar su cotización anterior.

**Why this priority**: Evita que jugadores sin datos (o con muy pocos minutos y métricas por 90 extremas) generen precios absurdos o corran los extremos de la normalización de su posición.

**Independent Test**: Armar una población con jugadores válidos más uno sin estadísticas, uno sin posiciones y uno por debajo del umbral, y verificar sus estados y que los resultados de los válidos son idénticos a los obtenidos sin esos tres jugadores.

**Acceptance Scenarios**:

1. **Given** un jugador sin estadísticas, **When** se calcula, **Then** su resultado es NO_STATS, sin score ni valor.
2. **Given** un jugador con estadísticas pero sin ninguna posición, **When** se calcula, **Then** su resultado es NO_STATS, sin score ni valor.
3. **Given** el umbral por defecto (90 minutos) y un jugador con 89 minutos, **When** se calcula, **Then** su resultado es NO_STATS; con exactamente 90 minutos se calcula normalmente.
4. **Given** un jugador con minutos jugados ausentes, **When** se calcula, **Then** su resultado es NO_STATS.
5. **Given** el umbral configurado en 270 minutos, **When** se calcula una población con un jugador de 200 minutos, **Then** ese jugador es NO_STATS.
6. **Given** una población válida a la que se agregan jugadores NO_STATS con estadísticas extremas, **When** se calcula, **Then** los scores y valores de los jugadores válidos no cambian.

---

### User Story 3 - Repartir el peso de métricas sin datos en toda la posición (Priority: P1)

Como equipo, queremos que cuando una métrica no tenga dato para ningún jugador valuado de una posición (caso típico: falló la consulta opcional de gambetas de WhoScored para una liga), su peso se reparta proporcionalmente entre las demás métricas de esa posición y el resultado informe qué métricas se excluyeron, para no castigar a todos con un 0 artificial y poder explicar el precio.

**Why this priority**: La falla de gambetas es un escenario conocido y esperable (feature 013); sin esta regla el 10% del peso de los mediocampistas se perdería y los scores quedarían comprimidos.

**Independent Test**: Calcular la misma población de mediocampistas con y sin gambetas, y verificar que sin gambetas los pesos restantes se escalan para sumar 1.00, que el resultado lista "gambetas" como excluida y que, con al menos un jugador con gambetas, no se excluye nada.

**Acceptance Scenarios**:

1. **Given** el escenario 1 de la User Story 1 pero con gambetas ausentes para A y B, **When** se calcula, **Then** cada peso restante de mediocampista se divide por 0.90, A obtiene valor 1.0956 y ambos resultados indican que se excluyó gambetas en la posición mediocampista.
2. **Given** una posición en la que al menos un jugador valuado tiene gambetas, **When** se calcula, **Then** no se excluye ninguna métrica y los jugadores sin gambetas cuentan 0.
3. **Given** gambetas ausentes para todos los mediocampistas pero presentes para algunos delanteros, **When** se calcula, **Then** la exclusión aplica sólo a la posición mediocampista y los delanteros usan sus pesos completos.
4. **Given** que ningún jugador valuado de una posición tiene dato en ninguna métrica con peso mayor que cero, **When** se calcula, **Then** esa posición no produce score; si el jugador no tiene otra posición con score, su resultado es NO_STATS.

---

### User Story 4 - Valuar jugadores con varias posiciones (Priority: P2)

Como equipo, queremos que un jugador con varias posiciones reciba como score el promedio de sus scores en cada posición, cada uno calculado con la normalización y los pesos de esa posición, para no favorecer arbitrariamente una de ellas.

**Why this priority**: Es un caso real del catálogo pero menos frecuente que el de una sola posición.

**Independent Test**: Calcular un jugador mediocampista y delantero, y comparar su score con el promedio de los scores que obtendría como mediocampista y como delantero dentro de cada población.

**Acceptance Scenarios**:

1. **Given** un jugador con posiciones mediocampista y delantero, **When** se calcula, **Then** su score es el promedio simple de su score como mediocampista (normalizado contra los mediocampistas, pesos de mediocampista) y su score como delantero (normalizado contra los delanteros, pesos de delantero).
2. **Given** ese mismo jugador, **When** se normalizan las poblaciones, **Then** participa en la población de mediocampistas y en la de delanteros.
3. **Given** un jugador multiposición en el que una de sus posiciones no produce score (User Story 3, escenario 4), **When** se calcula, **Then** su score es el promedio de las posiciones que sí producen score.

---

### User Story 5 - Acotar la variación respecto de la cotización anterior y redondear (Priority: P2)

Como mercado, queremos que el valor de un jugador no varíe más que la variación máxima de la estrategia (±20% en OFFICIAL v1) respecto de su cotización anterior, y que se exprese con 4 decimales redondeando HALF_UP, para evitar saltos bruscos y obtener precios estables y reproducibles.

**Why this priority**: Protege la operatoria de tokens frente a cambios abruptos; sólo influye cuando existe una cotización anterior.

**Independent Test**: Calcular el mismo jugador con distintas cotizaciones anteriores y verificar el valor acotado y redondeado.

**Acceptance Scenarios**:

1. **Given** un valor calculado de 1.0960 y cotización anterior 0.80, **When** se acota, **Then** el valor es 0.9600 (0.80 + 20%).
2. **Given** un valor calculado de 1.0120 y cotización anterior 1.50, **When** se acota, **Then** el valor es 1.2000 (1.50 − 20%).
3. **Given** un valor calculado dentro de ±20% de la cotización anterior, **When** se acota, **Then** el valor no cambia.
4. **Given** un jugador sin cotización anterior, **When** se calcula, **Then** el valor no se acota.
5. **Given** un valor sin redondear de 1.05645, **When** se redondea, **Then** el valor es 1.0565 (HALF_UP, no 1.0564); un valor de 1.00005 se redondea a 1.0001.

---

### User Story 6 - Resultado determinístico (Priority: P2)

Como equipo y como auditoría, queremos que las mismas entradas produzcan exactamente el mismo resultado sin importar el orden en que llegue la población, para que cualquier cotización pueda reproducirse y explicarse.

**Why this priority**: La trazabilidad exigida por la constitución (1.2) sólo tiene valor si el cálculo es reproducible.

**Independent Test**: Calcular la misma población varias veces con el orden de jugadores permutado y verificar que cada jugador obtiene resultados idénticos.

**Acceptance Scenarios**:

1. **Given** una población, **When** se calcula dos veces con los jugadores en orden distinto, **Then** cada jugador obtiene exactamente el mismo estado, score, valor y métricas excluidas.
2. **Given** las mismas entradas, **When** se calcula repetidas veces, **Then** los resultados son idénticos en todas las ejecuciones.

### Edge Cases

- Población vacía: normalizarla no falla y produce una población sin jugadores; calcular a cualquier jugador contra esa población da NO_STATS, sin valor ni score y sin error.
- Población en la que todos son NO_STATS: todos los resultados son NO_STATS y no se normaliza nada.
- Todos los jugadores de una posición con métricas en cero: max = min, cada métrica de conteo vale 0.5.
- Métrica con peso cero en una posición (por ejemplo, goles de arquero): no influye en el score aunque esté normalizada; si no tiene dato en toda la posición no se informa como excluida, porque no había peso que repartir.
- Rating ausente para un jugador mientras otros de su posición lo tienen: cuenta 0 (rating normalizado 0). Rating ausente en toda la posición: se excluye y su peso se reparte como cualquier otra métrica.
- Rating fuera de la escala 0–10: su valor normalizado se acota a [0,1].
- Jugador con 0 minutos o minutos ausentes: es NO_STATS (por debajo del umbral), por lo que nunca se divide por cero.
- Cotización anterior presente para un jugador NO_STATS: no se usa para calcular; el resultado sigue sin valor y quien llama conserva la cotización anterior. Igual se valida (FR-004a): si es de otra estrategia, está fuera de orden o la más reciente no tiene valor, el cálculo falla aunque el jugador sea NO_STATS.
- El mismo jugador presente dos veces en la población: el cálculo se rechaza con un error claro que identifica al jugador.
- Definición de estrategia de tipo distinto de OFFICIAL (por ejemplo, MOMENTUM): el cálculo se rechaza con un error claro.
- Tarjetas y minutos jugados existen en las estadísticas pero OFFICIAL v1 no los pondera: no influyen en el score (los minutos sólo se usan para el cálculo por 90 y el umbral).

## Requirements *(mandatory)*

### Functional Requirements

**Entradas y salidas**

- **FR-001**: El cálculo MUST recibir una definición de estrategia de tipo OFFICIAL y una población de jugadores; por cada jugador, sus posiciones (cero o más), sus estadísticas de temporada (posiblemente ausentes) y, opcionalmente, su cotización anterior.
- **FR-002**: La población MUST normalizarse una vez y el cálculo MUST hacerse por jugador contra esa población: por cada jugador que se calcula, el sistema MUST devolver exactamente un resultado con estado (CALCULATED o NO_STATS), score, valor y, por cada posición con exclusiones, las métricas excluidas. Recorrer la población y juntar los resultados le corresponde a quien llama (016); no hay una operación que calcule toda la población de una vez.
- **FR-003**: El cálculo MUST tomar todos los pesos, el valor base, el factor de escala y la variación máxima de la definición recibida, sin constantes propias.
- **FR-004**: El cálculo MUST rechazar con un error claro una definición cuyo tipo no sea OFFICIAL y una población con jugadores repetidos.
- **FR-004a**: Las cotizaciones previas son parte del contrato de entrada y MUST validarse antes de evaluar si el jugador es NO_STATS. Tienen que ser todas de la estrategia que se calcula, estar ordenadas de la más nueva a la más vieja y, si hay alguna, la más reciente tiene que tener valor. Una entrada que no cumple estas condiciones MUST producir un error claro siempre, sea el jugador NO_STATS o no.

**Elegibilidad (NO_STATS)**

- **FR-005**: Un jugador MUST quedar NO_STATS, sin score ni valor, si no tiene estadísticas, si no tiene posiciones, o si sus minutos jugados están ausentes o son menores que el umbral mínimo de minutos.
- **FR-006**: El umbral mínimo de minutos MUST ser configurable, con valor por defecto 90 que vive en el código, sin editar los `application.properties` existentes. Un jugador con minutos iguales al umbral es elegible.
- **FR-007**: Los jugadores NO_STATS MUST excluirse de la población usada para normalizar.

**Métricas y normalización**

- **FR-008**: Goles, asistencias, tiros, pases clave, gambetas y tackles MUST expresarse por 90 minutos: valor × 90 / minutos jugados.
- **FR-009**: Una métrica ausente para un jugador MUST contar como 0 (por 90 o como rating), salvo el caso de FR-013.
- **FR-010**: Cada métrica por 90 MUST normalizarse con min-max a [0,1] dentro de cada posición, usando como población a los jugadores elegibles que tienen esa posición: (valor − min) / (max − min).
- **FR-011**: Si para una métrica de una posición max = min, el valor normalizado MUST ser 0.5 para todos los jugadores de esa posición.
- **FR-012**: El rating MUST normalizarse como rating / 10, acotado a [0,1], sin min-max ni conversión por 90.

**Score**

- **FR-013**: Si una métrica con peso mayor que cero no tiene dato para ningún jugador elegible de una posición, esa métrica MUST excluirse en esa posición y su peso repartirse proporcionalmente entre las métricas restantes de esa posición (cada peso restante dividido por la suma de los pesos restantes). El resultado de cada jugador de esa posición MUST informar la métrica como excluida.
- **FR-014**: El score de un jugador en una posición MUST ser la suma de peso(posición, métrica) × métrica normalizada sobre las métricas que pondera la definición, con los pesos ya repartidos según FR-013.
- **FR-015**: Si en una posición todas las métricas con peso mayor que cero quedan excluidas, esa posición MUST NOT producir score.
- **FR-016**: El score de un jugador MUST ser el promedio simple de sus scores en las posiciones que producen score. Si ninguna produce score, el jugador MUST quedar NO_STATS.
- **FR-017**: El score MUST estar en [0,1].

**Valor**

- **FR-018**: El valor MUST calcularse como valor base + score × factor de escala.
- **FR-019**: Si el jugador tiene cotización anterior, el valor MUST acotarse al rango [anterior × (1 − variación máxima), anterior × (1 + variación máxima)].
- **FR-020**: El valor final MUST redondearse a 4 decimales con HALF_UP, después de acotar. El score MUST conservar al menos 4 decimales; su precisión exacta se fija en el plan.

**Determinismo y alcance**

- **FR-021**: Las mismas entradas MUST producir resultados idénticos, sin importar el orden de la población ni la cantidad de ejecuciones.
- **FR-022**: Los cálculos intermedios MUST usar aritmética decimal con precisión fija, de modo que los resultados no dependan de errores de punto flotante.
- **FR-023**: La feature MUST NOT persistir cotizaciones, actualizar el valor de mercado, exponer endpoints, agendar procesos ni consultar fuentes externas.
- **FR-024**: La feature MUST NOT modificar la definición OFFICIAL v1 (el test de protección de versiones publicadas de 013 debe seguir pasando sin cambios) ni archivos de Entrega 1 sin aprobación del equipo.
- **FR-025**: El estado INSUFFICIENT_HISTORY MUST NOT ser producido por OFFICIAL; corresponde a MOMENTUM (feature 019).

### Key Entities

- **Jugador a valuar**: Elemento de la población; identificador del jugador, posiciones, estadísticas de temporada (o ausencia) y cotización anterior opcional.
- **Estadísticas de temporada**: Totales del jugador (minutos jugados, goles, asistencias, tiros, pases clave, gambetas, tackles, tarjetas) y rating promedio; cualquiera puede faltar.
- **Población de una posición**: Jugadores elegibles que tienen esa posición; define los mínimos, máximos y métricas excluidas de esa posición.
- **Resultado de valuación**: Por jugador: estado (CALCULATED o NO_STATS), score, valor y métricas excluidas por posición. En NO_STATS, score y valor están ausentes.
- **Definición OFFICIAL v1**: Definida en 013; aporta valor base, factor de escala, variación máxima y pesos por posición y métrica. Sólo se lee.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En el 100% de los escenarios de aceptación con valores calculados a mano, score y valor coinciden exactamente (valor a 4 decimales) con lo esperado.
- **SC-002**: Para mediocampistas, el score coincide con la fórmula de la consigna (7 coeficientes) en el 100% de los casos con todas las métricas disponibles.
- **SC-003**: El 100% de los jugadores sin estadísticas, sin posiciones o bajo el umbral de minutos queda NO_STATS, y agregarlos a una población no altera ningún resultado de los demás.
- **SC-004**: Cuando una métrica falta en toda una posición, el 100% de los resultados de esa posición la informan como excluida y los pesos efectivos de la posición suman 1.00.
- **SC-005**: Ningún valor calculado varía más de ±20% respecto de la cotización anterior, y todos tienen exactamente 4 decimales.
- **SC-006**: Al menos 10 permutaciones distintas del orden de una misma población producen resultados idénticos para el 100% de los jugadores.
- **SC-007**: La feature introduce 0 tablas, 0 endpoints, 0 jobs, 0 consultas externas y 0 cambios a OFFICIAL v1 o a archivos protegidos de Entrega 1.

## Assumptions

- `specs/015-estrategia-oficial` es el directorio solicitado y es independiente del nombre de la rama Git.
- La población la arma quien llama (feature 016): normalmente todos los jugadores con estadísticas a valuar en una corrida. Esta feature no decide quién entra en ella ni de dónde salen los datos.
- La cotización anterior la provee quien llama (normalmente la última cotización OFFICIAL del jugador); esta feature no la consulta.
- Las estadísticas usadas son los totales de temporada actuales (`PlayerStats`); los snapshots de la feature 014 no se usan en OFFICIAL.
- La exclusión de FR-013 se evalúa sobre los jugadores elegibles de la posición, después de aplicar NO_STATS.
- Las métricas con peso cero en una posición no se informan como excluidas aunque no tengan dato, porque no hay peso que repartir.
- El rating se toma como dato en escala 0–10 (WhoScored); valores fuera de esa escala se acotan al normalizar.
- El acotamiento a ±variación máxima se calcula sobre el valor sin redondear y el redondeo HALF_UP a 4 decimales se aplica al final.
- El nombre exacto de la propiedad del umbral de minutos y la precisión decimal intermedia se fijan en el plan.
- Los valores negativos en las estadísticas no se esperan desde la fuente; no se agrega validación especial para ellos.

## Out of Scope

- Persistir `PlayerQuote`, la corrida de recálculo, actualizar `Player.marketValue` y `POST /quotes/recalculate` (feature 016).
- Endpoints de consulta de cotizaciones y ranking (features 017 y 018).
- Algoritmo MOMENTUM (feature 019) y job semanal (feature 020).
- Cambios a los pesos o parámetros de OFFICIAL v1 o publicación de una versión nueva.
- Uso de tarjetas o minutos jugados como métricas ponderadas en OFFICIAL.
