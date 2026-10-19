# Feature Specification: Historial de snapshots de estadísticas

**Feature Branch**: `feature/stats-snapshots`

**Created**: 2026-10-19

**Status**: Draft

**Input**: User description: "Guardar un historial append-only de las estadísticas acumuladas de cada jugador y derivar ventanas entre snapshots consecutivos para medir la forma reciente, sin consultar a WhoScored ni modificar los archivos protegidos de Entrega 1."

## Contexto y restricciones

Hoy cada jugador puede tener una única fila de estadísticas con los totales acumulados de la temporada. Cada sincronización reemplaza esos valores, por lo que se pierde la evolución necesaria para aproximar el rendimiento por fecha del campeonato. Esta feature conserva copias inmutables de esos totales y permite convertir snapshots consecutivos en ventanas de rendimiento.

La feature 013 ya aporta el historial inmutable de cotizaciones y MOMENTUM v1 continúa declarando su forma reciente sobre cotizaciones previas. Esta feature no modifica ese algoritmo ni sus parámetros; sólo genera el insumo estadístico que podrá consumir una versión posterior de la Estrategia 2.

Los archivos de Entrega 1 `PlayerStats`, `PlayerStatsService` y `PlayerStatsRepository` están protegidos y no se modifican en esta feature. Si durante la planificación o implementación un cambio allí resultara imprescindible, el trabajo debe detenerse y solicitarse una aclaración explícita que identifique el archivo, el cambio mínimo y una alternativa. El disparador previsto es el recálculo de cotización de la feature 016, mediante el nuevo servicio de snapshots; integrar el snapshot dentro de cada sincronización de WhoScored queda como alternativa que requiere aprobación previa.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Conservar el historial estadístico sin duplicados (Priority: P1)

Como proceso de recálculo de cotizaciones, quiero capturar en una sola ejecución las estadísticas persistidas de todos los jugadores que las tengan, para conservar su evolución sin depender nuevamente de WhoScored.

**Why this priority**: Sin un historial inmutable no existe materia prima para medir la forma reciente ni reconstruir qué estadísticas estaban disponibles en una corrida.

**Independent Test**: Preparar jugadores con y sin estadísticas persistidas, ejecutar una captura con un id de corrida y comprobar los registros, el resultado guardados/salteados y la ausencia de consultas externas.

**Acceptance Scenarios**:

1. **Given** tres jugadores con estadísticas persistidas y dos sin ellas, **When** se ejecuta la captura, **Then** se guarda un snapshot inmutable para cada uno de los tres primeros y el resultado informa 3 guardados y 0 salteados.
2. **Given** un jugador cuyo último snapshot tiene el mismo `fetchedAt` que sus estadísticas actuales, **When** se ejecuta otra captura, **Then** no se duplica el snapshot y el jugador cuenta como salteado.
3. **Given** dos corridas concurrentes que intentan capturar al mismo jugador con el mismo `fetchedAt`, **When** ambas terminan, **Then** existe un solo snapshot para ese jugador y origen; una captura lo cuenta como guardado y la otra como salteado.
4. **Given** estadísticas con métricas ausentes, **When** se captura el snapshot, **Then** las métricas permanecen ausentes y no se convierten en cero.
5. **Given** estadísticas persistidas con gambetas, **When** se captura el snapshot, **Then** las gambetas se copian junto con todas las demás métricas.
6. **Given** una captura con un correlation ID presente en el MDC, **When** la operación termina correctamente, **Then** conserva ese identificador y deja un log estructurado de éxito con correlation ID, id de corrida, guardados y salteados; si termina con error, deja un log estructurado de error con correlation ID, id de corrida y tipo de error, sin publicar conteos parciales. Si el MDC no tenía identificador, genera uno para la operación y lo limpia al finalizar.

---

### User Story 2 - Consultar los snapshots recientes de un jugador (Priority: P1)

Como componente de valuación, quiero obtener los últimos N snapshots de un jugador en orden cronológico, para analizar su evolución con un límite explícito y resultados determinísticos.

**Why this priority**: La consulta acotada es la base para calcular forma reciente de manera reproducible y evita leer todo el historial.

**Independent Test**: Registrar más de N snapshots con fechas conocidas para un jugador, mezclarlos con snapshots de otro jugador y comprobar que se seleccionan sólo los N más recientes del jugador solicitado y se devuelven del más antiguo al más nuevo.

**Acceptance Scenarios**:

1. **Given** cinco snapshots de un jugador, **When** se piden los últimos 3, **Then** se devuelven exactamente los tres más recientes, ordenados cronológicamente del más antiguo al más nuevo.
2. **Given** snapshots de varios jugadores, **When** se consulta uno de ellos, **Then** no aparece ningún snapshot de los demás.
3. **Given** un jugador sin snapshots, **When** se consultan sus últimos N, **Then** se devuelve una colección vacía.
4. **Given** un límite N igual o menor que cero, **When** se realiza la consulta, **Then** la solicitud se rechaza con un error claro.
5. **Given** dos snapshots con la misma fecha de captura, **When** se consultan, **Then** su orden se resuelve de forma determinística por el orden de registro.

---

### User Story 3 - Derivar ventanas de rendimiento entre snapshots (Priority: P1)

Como componente de valuación, quiero obtener las diferencias entre snapshots consecutivos, para aproximar las estadísticas logradas por el jugador en cada fecha del campeonato sin disponer de datos por partido.

**Why this priority**: Las ventanas convierten totales de temporada en el insumo útil para una futura evolución de la Estrategia 2.

**Independent Test**: Registrar una secuencia conocida de snapshots con incrementos, correcciones, valores ausentes y un reinicio de temporada, y comparar cada ventana derivada con sus valores esperados.

**Acceptance Scenarios**:

1. **Given** dos snapshots consecutivos con contadores crecientes, **When** se deriva la ventana, **Then** cada contador es la diferencia entre el snapshot nuevo y el anterior.
2. **Given** que un contador disminuye por una corrección de la fuente, **When** se deriva la ventana, **Then** ese contador vale 0 y los demás se calculan normalmente.
3. **Given** que una métrica es `null` en cualquiera de los dos extremos, **When** se deriva la ventana, **Then** esa métrica de la ventana es `null`.
4. **Given** minutos y rating presentes en ambos extremos y un aumento positivo de minutos, **When** se deriva la ventana, **Then** su rating representa exclusivamente los minutos agregados y queda acotado al intervalo [0,10].
5. **Given** que no aumentan los minutos o falta el rating o los minutos en algún extremo, **When** se deriva una ventana normal, **Then** el rating de la ventana es `null`.

---

### User Story 4 - Detectar un reinicio de temporada (Priority: P1)

Como componente de valuación, quiero distinguir cuando los minutos acumulados bajan entre snapshots, para no interpretar el comienzo de una temporada como una corrección negativa de la temporada anterior.

**Why this priority**: Sin esta regla, la primera ventana de una nueva temporada quedaría artificialmente en cero y perdería el rendimiento ya acumulado.

**Independent Test**: Crear dos snapshots donde los minutos bajan y verificar que la ventana se marca como reinicio y refleja los valores del snapshot nuevo.

**Acceptance Scenarios**:

1. **Given** un snapshot anterior con 1.800 minutos y uno nuevo con 90, **When** se deriva la ventana, **Then** se marca como reinicio y sus contadores coinciden con los totales del snapshot nuevo.
2. **Given** un reinicio con rating presente en el snapshot nuevo, **When** se deriva la ventana, **Then** el rating de la ventana es el rating nuevo acotado a [0,10].
3. **Given** un reinicio donde una métrica nueva es `null`, **When** se deriva la ventana, **Then** esa métrica permanece `null`.
4. **Given** minutos ausentes en cualquiera de los extremos, **When** se deriva la ventana, **Then** no se marca como reinicio sólo por esa ausencia y se aplican las reglas normales de valores ausentes.

### Edge Cases

- Una captura sin jugadores con estadísticas termina correctamente con 0 guardados y 0 salteados.
- Un jugador tiene estadísticas actuales más antiguas que su último snapshot: si su `fetchedAt` ya existe para ese jugador se saltea; si no existe, se conserva como un origen distinto sin alterar snapshots previos.
- La captura comienza sin correlation ID en el MDC: genera uno para que el log terminal siga siendo trazable y elimina únicamente ese valor generado al salir; si ya existía uno, lo preserva sin reemplazarlo.
- Dos snapshots comparten fecha de captura: la selección y el emparejamiento usan el orden de registro como desempate estable.
- Se solicitan más snapshots que los disponibles: se devuelven todos los disponibles sin error.
- Sólo hay un snapshot dentro del límite solicitado: se devuelve ese snapshot y ninguna ventana, porque no existe un par consecutivo.
- Un contador excede, disminuye o vuelve a aparecer después de un `null`: cada ventana se evalúa de manera independiente y nunca se rellena un valor ausente con información de otra ventana.
- `minutesPlayed` permanece igual mientras otros contadores aumentan: los deltas de contadores se informan, pero el rating de ventana es `null`.
- El cálculo ponderado del rating produce un valor menor que 0 o mayor que 10 por correcciones o redondeos de origen: se acota al límite más cercano.
- El nuevo snapshot de un reinicio tiene `minutesPlayed` igual a 0: la ventana se marca como reinicio, conserva los contadores nuevos y su rating es `null` por no representar minutos jugados.

## Requirements *(mandatory)*

### Functional Requirements

**Historial inmutable**

- **FR-001**: El sistema MUST conservar un snapshot como copia inmutable de las estadísticas persistidas de un jugador en un momento determinado.
- **FR-002**: Cada snapshot MUST contener: jugador, `minutesPlayed`, `goals`, `assists`, `shots`, `keyPasses`, `tackles`, `dribbles`, `yellowCards`, `redCards`, `rating`, fecha/hora de captura, `fetchedAt` de origen e id de corrida.
- **FR-003**: Jugador, fecha/hora de captura, `fetchedAt` de origen e id de corrida MUST ser obligatorios; todas las métricas MUST admitir `null` y conservarlo sin reemplazarlo por cero.
- **FR-004**: Un snapshot registrado MUST NOT poder modificarse ni borrarse mediante las operaciones de esta feature.
- **FR-005**: La fecha/hora de captura y el `fetchedAt` de origen MUST representar instantes absolutos independientes de la zona horaria del entorno.
- **FR-006**: Para un mismo jugador MUST existir como máximo un snapshot por cada `fetchedAt` de origen.

**Captura masiva**

- **FR-007**: El sistema MUST ofrecer una única operación que lea las estadísticas persistidas de todos los jugadores que las tengan e intente generar sus snapshots con un id de corrida común.
- **FR-008**: La operación MUST devolver un resultado con la cantidad de snapshots guardados y la cantidad de jugadores salteados; la suma MUST coincidir con la cantidad de jugadores con estadísticas considerada al iniciar la operación.
- **FR-009**: Un jugador MUST contarse como salteado cuando ya exista un snapshot suyo con el mismo `fetchedAt`, aunque haya sido creado por una corrida concurrente.
- **FR-010**: La captura concurrente MUST preservar FR-006 sin propagar como fallo la colisión esperada: entre intentos concurrentes para el mismo jugador y `fetchedAt`, exactamente uno se guarda y los demás se saltean.
- **FR-011**: Los jugadores sin estadísticas persistidas MUST NOT generar snapshots ni contarse como salteados.
- **FR-012**: La operación MUST copiar únicamente datos ya persistidos y MUST NOT consultar a WhoScored ni a ninguna otra fuente externa.
- **FR-013**: Cada ejecución MUST usar una sola fecha/hora de captura común para todos los snapshots que logre guardar.

**Consulta de snapshots**

- **FR-014**: El sistema MUST permitir consultar los últimos N snapshots de un jugador, con N estrictamente positivo.
- **FR-015**: La consulta MUST seleccionar los N snapshots más recientes y devolverlos en orden cronológico ascendente; si comparten fecha de captura, MUST desempatar por el orden de registro ascendente.
- **FR-016**: Si el jugador tiene menos de N snapshots, la consulta MUST devolver todos los disponibles; si no tiene ninguno, MUST devolver una colección vacía.

**Ventanas consecutivas**

- **FR-017**: El sistema MUST permitir derivar una ventana por cada par consecutivo de los últimos N snapshots seleccionados; por lo tanto, MUST devolver como máximo N−1 ventanas, en orden cronológico ascendente.
- **FR-018**: Cada ventana MUST identificar sus snapshots anterior y nuevo, el intervalo temporal, si corresponde a un reinicio y los valores derivados de todas las métricas incluidas en FR-002.
- **FR-019**: Para `goals`, `assists`, `shots`, `keyPasses`, `tackles`, `dribbles`, `yellowCards`, `redCards` y `minutesPlayed`, una ventana normal MUST calcular `max(0, valorNuevo − valorAnterior)` cuando ambos extremos estén presentes.
- **FR-020**: En una ventana normal, si cualquiera de los extremos de una métrica es `null`, esa métrica derivada MUST ser `null`; esta regla MUST aplicarse antes que la corrección de deltas negativos. Las ventanas de reinicio se rigen por FR-024 y FR-025 y no requieren el extremo anterior para copiar el valor nuevo.
- **FR-021**: En una ventana normal, si minutos y rating están presentes en ambos extremos y `minutosNuevos > minutosAnteriores`, el rating MUST calcularse como `(ratingNuevo × minutosNuevos − ratingAnterior × minutosAnteriores) / (minutosNuevos − minutosAnteriores)` y acotarse al intervalo [0,10].
- **FR-022**: En una ventana normal, el rating MUST ser `null` si no hubo un incremento positivo de minutos o si falta el rating o los minutos en cualquiera de los extremos.
- **FR-023**: Una ventana MUST marcarse como reinicio cuando `minutesPlayed` esté presente en ambos snapshots y el valor nuevo sea menor que el anterior.
- **FR-024**: En una ventana de reinicio, cada contador y los minutos MUST tomar directamente el valor del snapshot nuevo, conservando los `null`, sin calcular diferencias.
- **FR-025**: En una ventana de reinicio, el rating MUST tomar el rating del snapshot nuevo acotado a [0,10] cuando el rating y los minutos nuevos estén presentes y los minutos nuevos sean mayores que cero; en caso contrario MUST ser `null`.

**Integración y alcance**

- **FR-026**: El servicio de snapshots MUST poder ser invocado por el recálculo de cotización de la feature 016 sin requerir cambios en los archivos protegidos de Entrega 1.
- **FR-027**: Esta feature MUST NOT modificar `PlayerStats`, `PlayerStatsService` ni `PlayerStatsRepository`. Si durante planificación o implementación un cambio fuese imprescindible, MUST solicitarse una aclaración y aprobación explícitas indicando archivo, cambio mínimo y alternativa.
- **FR-028**: Esta feature MUST NOT cambiar el algoritmo MOMENTUM ni `MomentumParameters` de la feature 019.
- **FR-029**: Esta feature MUST NOT cambiar la sincronización de WhoScored ni hacer que ella dispare snapshots; esa alternativa requiere aprobación previa porque modifica `PlayerStatsService`.
- **FR-030**: `snapshotAll` MUST tomar el correlation ID de la clave usada por `CorrelationIdFilter` en el MDC; si falta o está vacío, MUST generar uno para la duración de la operación. En éxito MUST emitir mediante SLF4J un único log estructurado terminal con correlation ID, `runId`, guardados y salteados. En error MUST emitir un único log estructurado terminal con correlation ID, `runId` y tipo de error, sin informar conteos parciales, antes de propagar la excepción. En ambos caminos MUST restaurar el estado previo del MDC. Este requisito no agrega una tabla de auditoría.

### Key Entities

- **Snapshot de estadísticas de jugador (PlayerStatsSnapshot)**: Copia histórica e inmutable de todos los totales estadísticos persistidos para un jugador. Conserva las métricas, la captura, el origen (`fetchedAt`) y la corrida que lo generó. Existe una relación de muchos snapshots por jugador.
- **Resultado de captura**: Resumen de una ejecución con id de corrida, cantidad de snapshots guardados y cantidad de jugadores salteados.
- **Ventana de estadísticas**: Resultado derivado, no una nueva lectura externa, que compara dos snapshots consecutivos. Incluye el intervalo, la marca de reinicio y las métricas atribuibles a ese intervalo.
- **Jugador**: Entidad existente a la que pertenecen las estadísticas actuales y su historial de snapshots.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En una captura sin concurrencia, el 100% de los jugadores con estadísticas nuevas genera exactamente un snapshot con sus 10 métricas, `fetchedAt`, fecha de captura e id de corrida intactos.
- **SC-002**: Repetir una captura sin cambios, incluso con dos corridas simultáneas, produce 0 snapshots duplicados para cada combinación de jugador y `fetchedAt`, y todos los intentos no insertados se informan como salteados.
- **SC-003**: Para cualquier consulta válida, se devuelven como máximo N snapshots y N−1 ventanas, en el orden definido, con 100% de aislamiento respecto de otros jugadores.
- **SC-004**: El 100% de las métricas `null` de cualquiera de los extremos permanece `null` en la ventana correspondiente y ningún delta negativo de contador produce un valor menor que 0.
- **SC-005**: En todos los escenarios con datos suficientes, el rating de ventana coincide con la ponderación por minutos y queda entre 0 y 10; en todos los escenarios sin incremento de minutos o con extremos incompletos, resulta `null`.
- **SC-006**: El 100% de las disminuciones de minutos con ambos extremos presentes se identifica como reinicio y devuelve los totales nuevos, sin mezclar temporadas.
- **SC-007**: Una captura completa realiza 0 consultas a WhoScored u otras fuentes externas y modifica 0 archivos protegidos de Entrega 1.
- **SC-008**: Todos los escenarios de captura, consulta, concurrencia, valores ausentes, correcciones y reinicio definidos en esta especificación cuentan con verificación automatizada y producen resultados determinísticos.
- **SC-009**: El 100% de las invocaciones de captura emite exactamente un log terminal estructurado trazable por correlation ID y `runId`: las exitosas informan guardados/salteados y las fallidas informan el tipo de error sin conteos parciales. Una invocación sin correlation ID previo genera uno sin dejar residuos en el MDC al terminar.

## Assumptions

- `specs/014-stats-snapshots` es el directorio solicitado y es independiente del nombre de la rama Git.
- `PlayerStats` ya contiene `dribbles` en `main`; esta feature sólo lo copia y no cambia el modelo protegido.
- `fetchedAt` identifica la versión de estadísticas obtenida de la fuente para un jugador; por eso la identidad lógica de un snapshot es jugador + `fetchedAt`, no el id de corrida.
- Los últimos N snapshots se seleccionan desde el más reciente y se devuelven en orden ascendente para que cada ventana vaya naturalmente del snapshot anterior al nuevo.
- Una fecha de captura común por corrida hace que los snapshots de la misma ejecución sean comparables; el orden de registro sólo actúa como desempate determinístico.
- Las ventanas se derivan al consultar y no alteran el historial. Una consulta con un único snapshot no inventa una ventana inicial contra cero.
- En un reinicio, el rating nuevo ya representa el promedio de la nueva temporada; por eso se usa directamente (acotado) en lugar de ponderarlo contra la temporada anterior.
- El historial se conserva sin fecha de expiración dentro del alcance de esta feature.
- El recálculo de cotización de la feature 016 será responsable de invocar la captura. Esta feature aporta la operación, pero no modifica ni implementa ese recálculo.
- El log estructurado append-only del proceso satisface la auditoría operativa de esta captura; no se crea una tabla nueva y los snapshots continúan identificándose por `runId`.

## Out of Scope

- Cambiar el algoritmo MOMENTUM o cualquier parámetro de `MomentumParameters` (feature 019).
- Hacer que MOMENTUM consuma las nuevas ventanas.
- Implementar o modificar el recálculo de cotización que dispara la captura (feature 016).
- Tomar snapshots dentro de la sincronización de WhoScored o modificar cualquier archivo protegido de Entrega 1.
- Obtener estadísticas por partido o consultar nuevamente a WhoScored.
- Exponer endpoints públicos o interfaces de usuario para el historial de snapshots.
