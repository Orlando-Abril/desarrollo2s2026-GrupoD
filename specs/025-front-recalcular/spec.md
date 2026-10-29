# Feature Specification: Recálculo manual desde Ranking

**Feature Branch**: `025-front-recalcular`

**Created**: 2026-10-28

**Status**: Draft

**Input**: User description: "Acción en la pantalla de Ranking para disparar el recálculo de cotizaciones, mostrar el resultado y recargar el ranking, pensada para la demo."

## Context and Scope

La pantalla de Ranking ya permite consultar y filtrar cotizaciones. Esta feature agrega una acción manual, orientada a la demostración, para que un administrador confirme el recálculo, vea su progreso y reciba un resumen al terminar. La capacidad de recálculo, su exclusividad y su autorización ya existen; esta entrega sólo incorpora su uso desde Ranking.

Los componentes compartidos Button y Toast se reutilizan sin modificarlos. Ningún archivo perteneciente a Entrega 1 puede modificarse sin una consulta y autorización explícita previa del equipo.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Recalcular y ver el ranking actualizado (Priority: P1)

Como administrador que presenta el sistema, quiero iniciar un recálculo desde Ranking y confirmar expresamente la acción, para demostrar el cambio de cotizaciones sin abandonar la pantalla.

**Why this priority**: Es el flujo central de la feature y entrega el valor completo de la demo: iniciar una operación sensible, conocer su resultado y observar los datos actualizados.

**Independent Test**: Ingresar a Ranking, activar "Recalcular cotizaciones", cancelar una vez y confirmar otra; verificar que sólo la confirmación inicia el proceso, que la acción queda bloqueada durante la espera, que al finalizar se muestra el resumen y que el ranking vuelve a consultarse con los criterios vigentes.

**Acceptance Scenarios**:

1. **Given** que el usuario ve Ranking y la acción está disponible, **When** selecciona "Recalcular cotizaciones", **Then** se solicita confirmación antes de iniciar cualquier recálculo.
2. **Given** que la confirmación está abierta, **When** el usuario cancela, **Then** no se inicia el recálculo y Ranking conserva sus datos y criterios.
3. **Given** que la confirmación está abierta, **When** el usuario confirma, **Then** se inicia un único recálculo para todas las estrategias y la acción queda deshabilitada con un indicador de progreso hasta obtener un resultado.
4. **Given** que el recálculo termina correctamente, **When** se recibe el resultado, **Then** se muestra una notificación con el resumen de la corrida y el ranking se recarga automáticamente conservando estrategia, filtros y página vigentes.
5. **Given** un resumen exitoso, **When** se muestra la notificación, **Then** informa identificador de corrida, estrategias calculadas, estrategias salteadas y sus motivos, jugadores procesados y fallidos, conteos por estado para cada estrategia calculada y si se actualizó el valor de mercado.

---

### User Story 2 - Comprender y superar fallas (Priority: P1)

Como usuario, quiero recibir mensajes claros si el recálculo no puede comenzar o falla, para entender qué ocurrió sin perder el ranking ni quedar atrapado en un estado de carga.

**Why this priority**: La demo debe seguir siendo utilizable ante concurrencia, permisos insuficientes o fallas imprevistas.

**Independent Test**: Simular una corrida ya activa, una falla general y luego un reintento exitoso; comprobar el texto correspondiente, la recuperación de la acción y que el ranking visible no se rompe ni desaparece.

**Acceptance Scenarios**:

1. **Given** que ya existe una corrida en curso, **When** el usuario confirma otro recálculo, **Then** ve exactamente "Ya hay un recálculo en curso", el indicador termina y puede volver a intentar más tarde.
2. **Given** que ocurre un error distinto de permisos insuficientes o corrida en curso, **When** finaliza el pedido, **Then** se muestra un mensaje comprensible y accionable, sin detalles internos, el indicador termina y el ranking continúa utilizable.
3. **Given** que un recálculo falla, **When** el usuario descarta el mensaje, **Then** conserva el ranking y sus criterios y puede iniciar un nuevo intento.
4. **Given** que el recálculo fue aceptado pero la recarga posterior del ranking falla, **When** se informa el resultado, **Then** el usuario conserva el resumen exitoso y Ranking aplica su recuperación de error habitual sin presentar los datos anteriores como recién actualizados.

---

### User Story 3 - Restringir la acción a administradores (Priority: P1)

Como responsable del sistema, quiero que una persona sin permiso de administrador deje de ver la acción cuando el sistema rechaza su uso, para no ofrecerle reiteradamente una operación que no puede ejecutar.

**Why this priority**: El recálculo puede alterar valores de mercado y su restricción debe reflejarse también en la experiencia de Ranking.

**Independent Test**: Hacer que un usuario autenticado reciba una denegación de permiso al intentar recalcular y comprobar que se informa la falta de autorización, la acción desaparece y no vuelve a mostrarse para la misma identidad durante la sesión.

**Acceptance Scenarios**:

1. **Given** que el sistema rechaza el recálculo por permisos insuficientes, **When** Ranking procesa la respuesta, **Then** muestra un mensaje claro de falta de autorización y oculta "Recalcular cotizaciones" para ese usuario.
2. **Given** que la acción fue ocultada por falta de autorización, **When** el mismo usuario vuelve a Ranking durante la misma sesión, **Then** la acción permanece oculta.
3. **Given** que cambia la identidad autenticada, **When** el nuevo usuario accede a Ranking, **Then** la visibilidad se vuelve a determinar para esa nueva identidad y no hereda la denegación anterior.

### Edge Cases

- Una activación repetida o doble confirmación mientras el pedido está en curso no inicia más de un recálculo.
- Si el usuario abandona Ranking durante el recálculo, una respuesta tardía no debe intentar actualizar una pantalla que ya no está activa ni provocar un error visible en la nueva pantalla.
- Un resumen puede contener cero estrategias calculadas, cero jugadores, estrategias salteadas o jugadores fallidos; la notificación debe representar esos ceros y listas vacías sin omitirlos ni tratarlos como error.
- Si el resumen excede el espacio habitual de una notificación, toda la información requerida debe seguir disponible y legible sin desbordar la pantalla.
- Si cambian filtros o página mientras corre el recálculo, la recarga final utiliza los criterios que estén vigentes al terminar, no una copia obsoleta de los criterios iniciales.
- Si la recarga deja la página actual fuera de los nuevos límites, se aplica el comportamiento de límites y recuperación ya definido por Ranking.
- Una respuesta de permisos insuficientes tiene precedencia sobre cualquier mensaje genérico de error y provoca que la acción se oculte.

## Requirements *(mandatory)*

### Functional Requirements

**Acción y confirmación**

- **FR-001**: Ranking MUST presentar una acción denominada exactamente "Recalcular cotizaciones" mientras no se haya determinado que la identidad vigente carece de permiso.
- **FR-002**: Activar la acción MUST solicitar una confirmación explícita que explique que se recalcularán las cotizaciones; cancelar MUST cerrar la confirmación sin iniciar la operación.
- **FR-003**: Confirmar MUST solicitar un recálculo completo mediante la operación existente `POST /quotes/recalculate`, sin restringirlo a la estrategia seleccionada en Ranking ni a sus filtros visibles.
- **FR-004**: Desde la confirmación y hasta una respuesta terminal, la acción MUST permanecer deshabilitada y MUST mostrar un indicador de progreso perceptible visualmente y por tecnologías de asistencia.
- **FR-005**: Una misma confirmación MUST iniciar como máximo una operación, aun ante activaciones repetidas.

**Resultado exitoso**

- **FR-006**: Al finalizar correctamente, Ranking MUST mostrar mediante el Toast existente un resumen que incluya: id de corrida; estrategias calculadas con código y versión; estrategias salteadas con su motivo; total de jugadores procesados y fallidos; conteos `CALCULATED`, `NO_STATS` e `INSUFFICIENT_HISTORY` por estrategia calculada; y si se actualizó el valor de mercado, junto con la estrategia activa informada.
- **FR-007**: El resumen MUST representar explícitamente listas vacías y valores cero para evitar que se interpreten como información faltante.
- **FR-008**: Después de un resultado exitoso, Ranking MUST recargarse automáticamente con la estrategia, filtros y página vigentes al momento de la recarga.
- **FR-009**: El estado de progreso MUST terminar al recibirse cualquier resultado terminal, con independencia de que la recarga posterior de Ranking tenga éxito o falle.

**Errores y autorización**

- **FR-010**: Si la operación responde 409 porque ya existe una corrida en curso, Ranking MUST mostrar exactamente "Ya hay un recálculo en curso", MUST NOT recargar el ranking por ese intento y MUST volver a habilitar la acción.
- **FR-011**: Si la operación responde 403 por permisos insuficientes, Ranking MUST mostrar un mensaje claro de falta de autorización y MUST ocultar la acción para esa identidad durante el resto de la sesión.
- **FR-012**: La ocultación por permisos MUST restablecerse cuando cambie la identidad autenticada, sin trasladar la decisión de una persona a otra.
- **FR-013**: Ante cualquier otro error, Ranking MUST mostrar mediante el Toast existente un mensaje claro y sin detalles internos, MUST finalizar el progreso, MUST mantener la acción disponible para reintentar y MUST NOT recargar el ranking por ese intento.
- **FR-014**: Ningún error del recálculo MUST eliminar los datos visibles, alterar los criterios de consulta ni impedir las funciones habituales de Ranking.

**Consistencia, accesibilidad y límites**

- **FR-015**: La confirmación, la acción, su progreso y las notificaciones MUST ser operables con teclado, tener foco visible, nombres accesibles y no depender exclusivamente de color o posición.
- **FR-016**: Si el usuario deja Ranking antes de recibir la respuesta, la finalización MUST NOT producir errores visibles ni modificar el contenido de otra pantalla.
- **FR-017**: La feature MUST reutilizar Button y Toast existentes sin modificar sus archivos ni duplicar su comportamiento compartido.
- **FR-018**: La feature MUST NOT modificar ningún archivo de Entrega 1. Si durante planificación o implementación un cambio en uno de esos archivos resulta imprescindible, el trabajo MUST detenerse y solicitar autorización al equipo indicando archivo, cambio mínimo y alternativa considerada.
- **FR-019**: La feature MUST limitarse a iniciar el recálculo completo desde Ranking, comunicar su resultado y actualizar la consulta visible; MUST NOT cambiar las reglas de cálculo, autorización, concurrencia, persistencia ni ranking.

### Key Entities

- **Acción de recálculo**: Control disponible en Ranking que atraviesa los estados disponible, pendiente de confirmación, en progreso y oculto por falta de permiso.
- **Resumen de corrida**: Resultado de una ejecución con identificador, estrategias calculadas y salteadas, jugadores procesados y fallidos, conteos por estado y actualización del valor de mercado.
- **Identidad vigente**: Usuario autenticado para quien se determina la visibilidad de la acción; una denegación no se comparte con identidades posteriores.
- **Criterios vigentes de Ranking**: Estrategia, liga, posición y página activos que deben conservarse y utilizarse en la recarga posterior.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En el 100% de las pruebas, cancelar la confirmación inicia cero recálculos y confirmar una vez inicia exactamente uno.
- **SC-002**: En el 100% de las corridas simuladas, la acción permanece deshabilitada y comunica progreso desde la confirmación hasta el resultado terminal, sin permitir solicitudes duplicadas.
- **SC-003**: El 100% de los resultados exitosos muestra estos ocho grupos: (1) identificador de corrida, (2) estrategias calculadas con código y versión, (3) estrategias salteadas con motivo, (4) jugadores procesados, (5) jugadores fallidos, (6) conteos por estado para cada estrategia, (7) si se actualizó el valor de mercado y (8) la estrategia activa; además dispara una recarga automática con los criterios vigentes.
- **SC-004**: El 100% de las respuestas de corrida en curso muestra exactamente "Ya hay un recálculo en curso" y permite volver a intentar sin recargar ni romper Ranking.
- **SC-005**: El 100% de las denegaciones de permiso oculta la acción para la misma identidad durante la sesión, y el 100% de los cambios de identidad restablece su evaluación.
- **SC-006**: En el 100% de los errores probados, los datos y criterios visibles de Ranking permanecen disponibles, el progreso finaliza y ninguna función de consulta queda inutilizable.
- **SC-007**: En una prueba moderada con al menos 10 participantes, al menos 9 completan sin asistencia el flujo de confirmar, reconocer el progreso e identificar en la notificación si hubo jugadores fallidos, en menos de 30 segundos más la duración propia del recálculo.
- **SC-008**: En pruebas a 320 px, 768 px y 1440 px de ancho, la acción, confirmación, progreso y resumen permanecen utilizables y legibles sin superposiciones ni desplazamiento horizontal global.
- **SC-009**: El 100% de las acciones de esta feature puede recorrerse y activarse sólo con teclado, conserva foco visible y comunica confirmación, progreso y resultado a tecnologías de asistencia.

## Assumptions

- La pantalla de Ranking de la feature 023 está disponible y conserva su comportamiento actual de filtros, paginación, carga, error y reintento.
- La capacidad de recálculo de la feature 016 ejecuta la corrida dentro del mismo pedido y entrega el resumen completo al terminar.
- La acción solicita todas las estrategias; la estrategia seleccionada en Ranking no limita el recálculo.
- No existe una consulta previa de permisos específica para esta acción. Por eso puede mostrarse inicialmente a cualquier usuario autenticado y se oculta si el sistema responde con falta de autorización.
- "Para ese usuario" significa durante la sesión autenticada vigente; un cierre de sesión o cambio de identidad permite volver a determinar la visibilidad.
- La notificación de éxito se muestra antes de iniciar la recarga y permanece independiente del resultado de esa recarga.
- Los textos visibles se presentan en español; los códigos y versiones de estrategias y estados se conservan como los informa la capacidad existente.

## Dependencies

- Pantalla de Ranking de la feature 023.
- Operación `POST /quotes/recalculate`, resumen, exclusividad y autorización provistos por la feature 016.
- Componentes compartidos Button y Toast, que se consumen sin modificaciones.
- Sesión autenticada y mecanismo existente para detectar cambios de identidad.

## Out of Scope

- Cambiar algoritmos, estrategias, historial, precio de mercado o reglas de la corrida.
- Elegir estrategias, ligas, jugadores o subconjuntos para recalcular.
- Mostrar porcentaje de avance, cancelar una corrida o consultar su estado en segundo plano.
- Agregar gestión de roles o una nueva consulta de permisos.
- Recargar automáticamente el ranking tras un intento fallido.
- Modificar Button, Toast o cualquier archivo de Entrega 1.
