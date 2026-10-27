# Feature Specification: E2E del escenario de evolución de cotizaciones

**Feature Branch**: `feature/e2e-escenario-cotizaciones`

**Created**: 2026-10-26

**Status**: Draft

**Input**: User description: "Test e2e del escenario 2 de la consigna: obtener la cotización de un jugador de cada liga en distintas fechas, mostrando la evolución de su valor, con ambas estrategias. Con PostgreSQL y Redis reales, un jugador por liga (5) con estadísticas que cambian entre 3 corridas sucesivas (cargadas por repositorio, sin fuentes externas). Después de cada cambio se dispara POST /quotes/recalculate por HTTP como ADMIN. GET /players/{id}/quotes muestra 3 cotizaciones por estrategia con su versión, y el parámetro at devuelve la vigente de cada fecha. La evolución es coherente: un jugador que mejora sube en ambas estrategias; uno que deja de jugar baja en MOMENTUM. GET /players/ranking refleja el orden final y Player.marketValue coincide con la estrategia activa. Regla: no modificar archivos de Entrega 1 sin consultarnos."

## Contexto

La constitución (§3.3, escenario 2) exige verificar la **evolución temporal de cotizaciones para jugadores de las 5 ligas**. Las piezas ya existen por separado y cada una tiene sus propios tests:

- el perfil e2e con los cinco jugadores canónicos (010, 012);
- la corrida de recálculo (016);
- el historial y la cotización vigente por fecha (017);
- el ranking (018);
- las estrategias OFFICIAL y MOMENTUM (015, 019).

Ningún test recorre el escenario completo, como lo haría un usuario, a lo largo de varias fechas. Esta feature agrega ese recorrido: **es sólo un test**, no cambia el comportamiento del sistema.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Evolución del valor de un jugador por liga en tres fechas (Priority: P1)

El equipo quiere demostrar, con la aplicación completa levantada, que la cotización de un jugador de cada una de las 5 ligas cambia de una fecha a otra según sus estadísticas. Para eso:

1. Prepara un jugador por liga con estadísticas iniciales.
2. Corre el recálculo como administrador en tres fechas distintas, cambiando las estadísticas entre una corrida y la siguiente.
3. Consulta el historial de cada jugador para las dos estrategias.

**Why this priority**: es el núcleo del escenario 2 de la consigna. Sin él, la evolución temporal de las cotizaciones no está demostrada de punta a punta.

**Independent Test**: se ejecuta solo, con PostgreSQL y Redis disponibles. Después de las tres corridas, el historial de cada uno de los 5 jugadores muestra 3 cotizaciones por estrategia, en orden cronológico y con la versión de la estrategia.

**Acceptance Scenarios**:

1. **Given** un jugador por liga con estadísticas cargadas, **When** el administrador dispara el recálculo en tres fechas distintas y antes de la segunda y de la tercera corrida las estadísticas se actualizan con una nueva fecha de obtención (las cifras pueden repetirse, como en el jugador que deja de jugar), **Then** cada corrida responde con éxito, sin jugadores fallidos ni estrategias salteadas.
2. **Given** las tres corridas terminadas, **When** se consulta el historial de cualquiera de los 5 jugadores para OFFICIAL y para MOMENTUM, **Then** cada historial tiene exactamente 3 cotizaciones, en orden cronológico, una por corrida, cada una con su estrategia y versión 1.
3. **Given** las tres corridas terminadas, **When** se consulta la cotización vigente de un jugador en la fecha de cada corrida, **Then** se obtiene la cotización calculada en esa fecha y no la de otra corrida.
4. **Given** las tres corridas terminadas, **When** se consulta la cotización vigente en una fecha anterior a la primera corrida, **Then** la respuesta indica que no hay cotización vigente para esa fecha.

---

### User Story 2 - Evolución coherente con el rendimiento (Priority: P2)

El equipo quiere mostrar que los valores se mueven en la dirección esperada. Un jugador que mejora su rendimiento sube en las dos estrategias. Un jugador que deja de jugar baja en MOMENTUM, que es la estrategia que mide la forma reciente y penaliza la falta de minutos.

**Why this priority**: que existan 3 cotizaciones no alcanza: hay que ver que el valor refleja el rendimiento. Depende de la historia 1, porque usa las mismas corridas.

**Independent Test**: sobre el historial de la historia 1, se comparan las cotizaciones de la segunda y la tercera fecha de dos jugadores elegidos: el que mejora y el que deja de jugar.

**Acceptance Scenarios**:

1. **Given** un jugador cuyas estadísticas mejoran entre corridas, **When** se compara su cotización de la última fecha con la anterior, **Then** el valor sube tanto en OFFICIAL como en MOMENTUM.
2. **Given** un jugador que jugó en la primera ventana y no sumó minutos después, aunque sus estadísticas se actualizaron, **When** se compara su cotización MOMENTUM de la última fecha con la anterior, **Then** el valor baja.
3. **Given** las tres corridas, **When** se mira la primera cotización MOMENTUM de cada jugador, **Then** está en `INSUFFICIENT_HISTORY` con valor 1.0000, porque todavía no hay historial de estadísticas. Las siguientes están en `CALCULATED`.
4. **Given** cualquier par de cotizaciones MOMENTUM consecutivas de un jugador, **When** se comparan, **Then** la variación no supera el ±15%.

---

### User Story 3 - Ranking final y valor de mercado (Priority: P3)

Al terminar las tres fechas, el equipo quiere ver que el ranking muestra a los jugadores ordenados según su última cotización, y que el valor de mercado de cada jugador es el de la estrategia activa.

**Why this priority**: cierra el escenario con las vistas que consume el usuario final. Depende de las corridas de la historia 1.

**Independent Test**: después de la tercera corrida se consulta el ranking de cada estrategia y el valor de mercado de cada jugador, y se comparan con las últimas cotizaciones del historial.

**Acceptance Scenarios**:

1. **Given** las tres corridas terminadas, **When** se consulta el ranking de una estrategia, **Then** lista a los 5 jugadores ordenados por su última cotización de esa estrategia, de mayor a menor, y el valor de cada fila coincide con la última cotización de su historial.
2. **Given** las tres corridas terminadas, **When** se consulta el ranking sin indicar estrategia, **Then** se usa la estrategia activa (OFFICIAL).
3. **Given** las tres corridas terminadas, **When** se consulta el valor de mercado de cada jugador, **Then** coincide con su última cotización de la estrategia activa y no con la de MOMENTUM.
4. **Given** el ranking consultado antes de la tercera corrida, **When** se consulta otra vez después de ella, **Then** refleja los valores nuevos y no un resultado anterior guardado en caché.

---

### Edge Cases

- **Una corrida en la que el jugador no tiene estadísticas nuevas:** el escenario fuerza estadísticas nuevas para los 5 jugadores antes de la segunda y la tercera corrida. Así cada corrida aporta una ventana nueva a MOMENTUM, y ningún jugador repite el valor por falta de datos nuevos.
- **"Dejar de jugar":** se representa como una actualización de estadísticas, con una fecha de obtención nueva, en la que los minutos no aumentan. Sin esa actualización no se registra un snapshot nuevo y MOMENTUM conservaría el valor (019, FR-028).
- **Forma reciente muy buena:** puede compensar la falta de minutos. Un jugador que tuvo una ventana muy por encima de su promedio puede subir en MOMENTUM aunque después no juegue. Por eso el jugador que deja de jugar tiene una primera ventana por debajo de su promedio (FR-013).
- **Corridas en el mismo día UTC:** la cotización vigente por fecha se busca al fin de cada día UTC. Las tres corridas tienen que caer en días UTC distintos para poder distinguirlas con el parámetro de fecha.
- **OFFICIAL compara contra la población:** su valor depende de los otros jugadores de la misma posición. Los datos se eligen para que el jugador que mejora quede mejor ubicado en la última corrida que en la anterior, sin depender de redondeos ni de empates.
- **Jugador sin estadísticas en alguna corrida:** fuera de alcance. Los 5 jugadores tienen estadísticas desde la primera corrida.
- **Infraestructura no disponible:** sin PostgreSQL o Redis, el test se omite en local con el motivo, como el resto de los e2e. En CI falla.

## Requirements *(mandatory)*

### Functional Requirements

**Preparación**

- **FR-001**: El test MUST usar los cinco jugadores canónicos del perfil e2e, uno por liga (Premier League, La Liga, Serie A, Bundesliga y Ligue 1), sin crear otros jugadores.
- **FR-002**: Las estadísticas de los jugadores MUST cargarse y actualizarse directamente en la base, sin consultar Football-Data, WhoScored ni ninguna otra fuente externa.
- **FR-003**: El test MUST correr contra PostgreSQL y Redis reales, con la misma limpieza antes y después que el resto de los e2e.

**Corridas**

- **FR-004**: El test MUST ejecutar exactamente 3 corridas de recálculo, cada una en un día UTC distinto y conocido de antemano, para que las fechas del historial y de las consultas por fecha sean determinísticas.
- **FR-005**: Antes de la segunda y de la tercera corrida, las estadísticas de los 5 jugadores MUST actualizarse con una nueva fecha de obtención (las cifras pueden repetirse, como en el jugador que deja de jugar).
- **FR-006**: Cada corrida MUST dispararse por HTTP con `POST /quotes/recalculate` y credenciales de administrador.
- **FR-007**: Cada corrida MUST responder con éxito, sin jugadores fallidos y sin estrategias salteadas, incluyendo a OFFICIAL y a MOMENTUM.

**Historial y consulta por fecha**

- **FR-008**: Para cada uno de los 5 jugadores y cada estrategia, `GET /players/{id}/quotes` MUST devolver exactamente 3 cotizaciones en orden cronológico. Cada una MUST tener el código de estrategia, la versión 1 y la fecha de la corrida que la produjo.
- **FR-009**: Para cada jugador, estrategia y fecha de corrida, la consulta con el parámetro `at` MUST devolver la cotización calculada en esa fecha.
- **FR-010**: La consulta con `at` en una fecha anterior a la primera corrida MUST responder que no hay cotización vigente (404 `quote_not_found`).

**Evolución**

- **FR-011**: La primera cotización MOMENTUM de cada jugador MUST quedar en `INSUFFICIENT_HISTORY` con valor 1.0000. La segunda y la tercera MUST quedar en `CALCULATED`.
- **FR-012**: El escenario MUST incluir al menos un jugador que mejora su rendimiento entre la segunda y la tercera corrida. Su cotización de la tercera fecha MUST ser mayor que la de la segunda, tanto en OFFICIAL como en MOMENTUM.
- **FR-013**: El escenario MUST incluir al menos un jugador que juega en la primera ventana, con una primera ventana jugada por debajo de su promedio de temporada (research R4), y no suma minutos en la siguiente. Su cotización MOMENTUM de la tercera fecha MUST ser menor que la de la segunda.
- **FR-014**: Entre dos cotizaciones MOMENTUM consecutivas de un mismo jugador, la variación MUST quedar dentro de ±15%.

**Ranking y valor de mercado**

- **FR-015**: Después de la tercera corrida, `GET /players/ranking` de cada estrategia MUST listar a los 5 jugadores ordenados por su última cotización, de mayor a menor. Ante un empate de valor desempata el id ascendente. El valor de cada fila MUST coincidir con la última cotización del historial.
- **FR-016**: Sin estrategia indicada, el ranking MUST corresponder a la estrategia activa (OFFICIAL).
- **FR-017**: El ranking consultado después de la tercera corrida MUST reflejar los valores nuevos aunque se haya consultado antes de esa corrida, sin devolver un resultado viejo de la caché.
- **FR-018**: Después de cada corrida, el valor de mercado de cada jugador MUST coincidir con su cotización de la estrategia activa en esa corrida.

**Alcance**

- **FR-019**: La feature MUST NOT cambiar el comportamiento del sistema: sólo agrega código de test y la documentación de la spec. Si un archivo protegido de Entrega 1 tuviera que cambiar, se consulta antes al equipo.
- **FR-020**: El test MUST ser determinístico: con los mismos datos y fechas da el mismo resultado en cada ejecución.

### Key Entities

- **Jugador del escenario**: uno de los cinco jugadores canónicos del perfil e2e, con su liga, posiciones y valor de mercado.
- **Estadísticas del jugador**: las métricas de temporada y su fecha de obtención. Cada actualización con fecha nueva genera un snapshot en la corrida siguiente.
- **Corrida de recálculo**: una ejecución en una fecha determinada que produce una cotización por jugador y estrategia.
- **Cotización**: valor, estado, estrategia, versión y fecha de cálculo de un jugador en una corrida.
- **Ranking**: la última cotización de cada jugador para una estrategia, ordenada por valor.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El escenario completo (3 fechas, 5 jugadores de 5 ligas, 2 estrategias) se ejecuta de punta a punta en una sola corrida de tests, sin pasos manuales.
- **SC-002**: Al terminar, se verifican 30 cotizaciones en el historial (5 jugadores × 2 estrategias × 3 fechas), todas con su estrategia y versión.
- **SC-003**: Las 30 consultas por fecha (jugador × estrategia × fecha) devuelven la cotización de esa fecha, y la consulta previa a la primera fecha informa que no hay cotización.
- **SC-004**: El jugador que mejora sube en las dos estrategias y el que deja de jugar baja en MOMENTUM, en el 100% de las ejecuciones.
- **SC-005**: El orden del ranking de cada estrategia y los 5 valores de mercado coinciden al 100% con las últimas cotizaciones.
- **SC-006**: Diez ejecuciones seguidas dan el mismo resultado, sin fallas intermitentes.
- **SC-007**: El test corre en CI con la infraestructura del pipeline, y la suite completa sigue en verde.

## Assumptions

- El escenario 2 de la consigna corresponde a §3.3 punto 2 de la constitución: "Evolución temporal de cotizaciones para jugadores de las 5 ligas".
- Se reutilizan los cinco jugadores canónicos del perfil e2e (012), que ya cubren las 5 ligas y distintas posiciones, incluido uno con varias posiciones y un arquero.
- La estrategia activa es OFFICIAL, la configurada por defecto.
- El instante de cálculo de cada corrida sale del reloj de la aplicación. Para que las fechas sean conocidas, el test controla ese reloj desde su propia configuración, sin cambiar el código de producción.
- Se reutilizan los contratos existentes sin cambios: recálculo (016), historial y cotización vigente (017) y ranking (018).
- La autenticación como administrador usa una API key de un usuario ADMIN, como el quickstart de 016 (registro por HTTP y UPDATE del rol).
- Las cotizaciones de cada estrategia se calculan con las versiones publicadas (OFFICIAL v1 y MOMENTUM v1).
- Los valores exactos de las cotizaciones no son parte de la verificación, salvo el 1.0000 inicial de MOMENTUM. Se verifica la dirección de la evolución, los estados, las versiones, el orden y los límites. Así el test no se rompe por ajustes de fórmula que respeten la spec de cada estrategia.
- Fuera de alcance: el recálculo programado, la interfaz web, otras estrategias y las órdenes de compra y venta.
