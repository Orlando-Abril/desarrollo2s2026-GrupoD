# Feature Specification: Pantalla de ranking de jugadores

**Feature Branch**: `feature/front-ranking`

**Created**: 2026-10-27

**Status**: Draft

**Input**: User description: "Pantalla protegida de Ranking en el frontend, accesible desde la navegacion principal, con jugadores ordenados por cotizacion actual, estrategia y filtros de liga y posicion, paginado, estados completos, responsive y accesible."

## Context and Scope

Esta entrega incorpora al producto una vista de consulta del ranking de cotizaciones para usuarios autenticados. La vista permite entender de un vistazo la posicion, el valor actual y la evolucion reciente de cada jugador, y acotar la comparacion por estrategia, liga y posicion.

La clasificacion y sus datos provienen de `GET /players/ranking`, capacidad ya entregada por la feature 018. Esta entrega no calcula cotizaciones, no cambia el orden recibido y no incorpora todavia la navegacion al detalle del jugador. La integracion futura con la feature 024 queda prevista, pero las filas de esta entrega no presentan comportamiento ni apariencia de enlace.

Por autorizacion expresa para la Entrega 1, los unicos archivos existentes que pueden modificarse son `frontend/src/App.jsx`, para registrar la ruta; `frontend/src/layout/AppLayout.jsx`, para habilitar el item Ranking; y `frontend/src/routes/routes.test.jsx`, exclusivamente para cubrir la nueva ruta protegida y su navegacion. Toda la capacidad restante se incorpora en archivos nuevos. Esta es una restriccion de alcance de entrega, no una decision de diseño abierta.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consultar el ranking actual (Priority: P1)

Como usuario autenticado, quiero abrir el Ranking desde la navegacion principal y ver a los jugadores ordenados por cotizacion, para identificar rapidamente quienes tienen mayor valor actual.

**Why this priority**: Es el objetivo principal de la feature y completa una de las vistas obligatorias del producto.

**Independent Test**: Iniciar sesion, acceder a Ranking desde la navegacion de escritorio o movil y comprobar que la vista muestra, en el orden recibido, el puesto y los datos de cotizacion de cada jugador.

**Acceptance Scenarios**:

1. **Given** un usuario autenticado y un ranking con resultados, **When** accede a Ranking desde el menu de escritorio, **Then** llega a la vista protegida `/ranking` y ve los jugadores ordenados con puesto, nombre, equipo, liga, posiciones, cotizacion y variacion.
2. **Given** un usuario autenticado en una pantalla movil, **When** selecciona Ranking en la navegacion inferior, **Then** llega a la misma vista y puede consultar todos los datos sin perder contenido ni funcionalidad.
3. **Given** una persona no autenticada, **When** intenta abrir `/ranking`, **Then** se aplica el mismo flujo de acceso protegido que en las demas vistas privadas y no se muestran datos del ranking.
4. **Given** resultados de distintas ligas, **When** se muestran, **Then** cada liga se identifica por texto y por su color visual correspondiente, sin depender exclusivamente del color para comunicar su identidad.
5. **Given** una fila de jugador en esta entrega, **When** el usuario la selecciona o navega con teclado, **Then** la fila no se comporta ni se anuncia como enlace en esta entrega (la feature 024 agrego despues un enlace en el nombre del jugador hacia `/jugadores/{id}`; ver FR-009).

---

### User Story 2 - Interpretar valor y variacion (Priority: P1)

Como usuario autenticado, quiero distinguir la cotizacion actual y si subio, bajo, se mantuvo o no tiene comparacion previa, para comprender la evolucion reciente de cada jugador.

**Why this priority**: El valor aislado no permite interpretar tendencia; la variacion aporta el contexto esencial para comparar jugadores.

**Independent Test**: Preparar resultados con variaciones positiva, negativa, cero y nula, y comprobar el valor, el indicador visual y la alternativa accesible mostrados en cada caso.

**Acceptance Scenarios**:

1. **Given** una variacion porcentual positiva, **When** se muestra la fila, **Then** la variacion conserva su signo y se presenta como suba con tratamiento verde y una indicacion textual o accesible equivalente.
2. **Given** una variacion porcentual negativa, **When** se muestra la fila, **Then** la variacion conserva su signo y se presenta como baja con tratamiento rojo y una indicacion textual o accesible equivalente.
3. **Given** una variacion igual a cero, **When** se muestra la fila, **Then** se presenta como sin cambios con tratamiento neutro.
4. **Given** que no existe una variacion anterior disponible, **When** se muestra la fila, **Then** se presenta como sin dato con tratamiento neutro y no se confunde con una variacion de cero.
5. **Given** una cotizacion vigente, **When** se muestra, **Then** el valor se identifica expresamente en creditos con un formato consistente en todas las filas.

---

### User Story 3 - Acotar la comparacion (Priority: P2)

Como usuario autenticado, quiero elegir la estrategia y filtrar por liga y posicion, para comparar solamente los jugadores relevantes para mi analisis.

**Why this priority**: Los filtros y la estrategia convierten el ranking general en una herramienta de consulta dirigida.

**Independent Test**: Cambiar individualmente y en conjunto la estrategia, liga y posicion, y verificar que cada seleccion actualiza los resultados, conserva los otros criterios y vuelve al comienzo del conjunto filtrado.

**Acceptance Scenarios**:

1. **Given** que el usuario abre el ranking sin una estrategia elegida, **When** se realiza la consulta inicial, **Then** se omite el criterio de estrategia y el selector muestra "Activa"; si existen resultados, la vista tambien comunica el codigo efectivo informado por ellos sin convertirlo en una seleccion explicita.
2. **Given** las estrategias disponibles, **When** el usuario selecciona OFFICIAL o MOMENTUM, **Then** se consulta y muestra el ranking correspondiente a esa estrategia.
3. **Given** que el usuario selecciona una liga y una posicion, **When** se actualizan los resultados, **Then** aparecen solamente jugadores que cumplen ambos filtros y los controles mantienen ambas selecciones.
4. **Given** que el usuario esta en una pagina posterior a la primera, **When** cambia la estrategia, la liga o la posicion, **Then** la vista vuelve a la primera pagina del nuevo conjunto.
5. **Given** una nueva consulta por cambio de criterio, **When** esta se encuentra en curso, **Then** la vista comunica el estado de carga sin presentar los resultados anteriores como si pertenecieran al nuevo criterio.

---

### User Story 4 - Navegar paginas y recuperarse de estados alternativos (Priority: P2)

Como usuario autenticado, quiero recorrer las paginas y recibir estados claros de carga, error o ausencia de datos, para completar mi consulta sin quedar en una pantalla ambigua.

**Why this priority**: La lista puede superar una pagina y la experiencia debe seguir siendo util ante demoras, fallas o conjuntos vacios.

**Independent Test**: Probar un ranking de varias paginas y simular carga, error recuperable, reintento exitoso y respuesta vacia, verificando mensaje, controles y criterios preservados.

**Acceptance Scenarios**:

1. **Given** un ranking con mas de una pagina, **When** el usuario avanza o retrocede, **Then** se muestra la pagina solicitada y se comunica la ubicacion actual y el total disponible.
2. **Given** la primera o la ultima pagina, **When** se muestran los controles, **Then** no se permite retroceder o avanzar fuera de los limites respectivamente.
3. **Given** una consulta en curso, **When** aun no hay respuesta, **Then** se muestra un estado de carga identificable, desaparecen la tabla y el paginador anteriores, y los filtros permanecen habilitados para reemplazar y abortar la consulta vigente.
4. **Given** una falla al obtener el ranking, **When** se informa el error, **Then** se ofrece un reintento claro, sin exponer detalles internos.
5. **Given** un error con filtros seleccionados, **When** el usuario reintenta, **Then** se repite la misma consulta y se conservan estrategia, liga, posicion y pagina.
6. **Given** una respuesta exitosa sin jugadores, **When** finaliza la consulta, **Then** se muestra el mensaje exacto "Todavía no hay cotizaciones" y no se presenta como error.

### Edge Cases

- El ranking puede devolver menos elementos que el tamano de pagina o una unica pagina; los controles deben reflejar los limites reales.
- Un jugador puede tener mas de una posicion; se muestran todas las posiciones recibidas sin duplicarlas ni elegir arbitrariamente una sola.
- Nombres de jugador, equipo o liga largos deben seguir siendo legibles y no solaparse con valores o controles.
- Una cotizacion o variacion con decimales debe conservar su significado con un formato uniforme, sin cambiar el signo ni confundir cero con dato ausente.
- Un cambio rapido y sucesivo de filtros puede hacer que respuestas anteriores lleguen tarde; solamente la consulta correspondiente a los criterios vigentes puede determinar lo que ve el usuario.
- Si la estrategia activa cambia entre consultas, los resultados siguientes reflejan la nueva configuracion. El codigo efectivo solo puede mostrarse cuando la respuesta contiene al menos un elemento; una respuesta vacia conserva la etiqueta "Activa" sin inferir OFFICIAL ni MOMENTUM.
- Si una pagina deja de existir por un cambio en los datos, la vista no debe ofrecer navegacion fuera de los limites informados.
- En anchos reducidos, todos los campos siguen disponibles y la pagina no produce desplazamiento horizontal global ni superposicion de controles.

## Requirements *(mandatory)*

### Functional Requirements

**Acceso y navegacion**

- **FR-001**: El sistema MUST ofrecer una vista protegida de Ranking en `/ranking` y aplicar el mecanismo de proteccion ya utilizado por las demas vistas privadas.
- **FR-002**: El elemento "02 Ranking" MUST estar habilitado y conducir a `/ranking` tanto en la navegacion de escritorio como en la navegacion inferior movil.
- **FR-003**: La navegacion MUST identificar Ranking como la seccion activa mientras el usuario se encuentre en `/ranking`.

**Contenido del ranking**

- **FR-004**: La vista MUST presentar los elementos en el orden y con el puesto entregados por la fuente de ranking, sin recalcular ni reordenar la clasificacion localmente.
- **FR-005**: Cada elemento MUST mostrar puesto, nombre del jugador, equipo, liga, todas sus posiciones, cotizacion actual en creditos y variacion porcentual respecto de la cotizacion anterior.
- **FR-006**: La liga MUST mostrar su denominacion y el color visual asociado en el producto; el color MUST NOT ser el unico medio para identificarla.
- **FR-007**: Una variacion positiva MUST presentarse como suba con tratamiento verde; una negativa, como baja con tratamiento rojo; una variacion cero, como sin cambios con tratamiento neutro; y una variacion ausente, como sin dato con tratamiento neutro.
- **FR-008**: Los cuatro estados de variacion MUST ser distinguibles mediante texto, signo, icono o descripcion accesible, ademas del color.
- **FR-009**: En la entrega original de esta feature las filas eran no interactivas. La navegacion al detalle se agrego en la feature 024: el nombre de cada jugador es un enlace a `/jugadores/{player.id}`; el resto de la fila no es interactivo.

**Estrategia, filtros y paginacion**

- **FR-010**: La vista MUST permitir seleccionar OFFICIAL o MOMENTUM y MUST usar por defecto la estrategia activa cuando el usuario no haya elegido otra.
- **FR-011**: Cuando el usuario no elija una estrategia explicita, el selector MUST conservar la opcion "Activa" y la consulta MUST omitir el criterio de estrategia. Si la respuesta contiene elementos, la vista MUST comunicar el codigo efectivo informado por ellos sin convertirlo en una seleccion explicita. Si la respuesta esta vacia, la vista MUST conservar "Activa" sin inferir OFFICIAL ni MOMENTUM.
- **FR-012**: La vista MUST permitir filtrar por liga y por posicion usando los mismos valores de dominio y convenciones visuales que el catalogo existente.
- **FR-013**: Los filtros de liga y posicion MUST poder aplicarse por separado o en conjunto, y MUST conservar sus selecciones mientras el usuario pagina o reintenta.
- **FR-014**: Cambiar la estrategia, liga o posicion MUST iniciar una consulta con los criterios actualizados y volver a la primera pagina.
- **FR-015**: La vista MUST ofrecer paginacion cuando el resultado tenga mas de una pagina, indicando pagina actual y total de paginas.
- **FR-016**: La paginacion visible MUST usar numeracion humana desde 1, aunque la fuente de datos identifique internamente la primera pagina desde 0.
- **FR-017**: Los controles anterior y siguiente MUST impedir navegacion antes de la primera pagina o despues de la ultima.

**Estados y recuperacion**

- **FR-018**: La vista MUST comunicar un estado de carga en la consulta inicial y cada vez que cambien estrategia, filtros o pagina. Durante la carga, los filtros MUST permanecer habilitados para que una nueva seleccion pueda reemplazar y abortar la consulta vigente.
- **FR-019**: Durante una consulta nueva, la vista MUST NOT presentar la tabla ni el paginador anteriores como si correspondieran a los criterios vigentes.
- **FR-020**: Ante una falla, la vista MUST mostrar un mensaje comprensible y una accion de reintento, sin revelar detalles tecnicos internos.
- **FR-021**: El reintento MUST repetir los mismos criterios que fallaron y conservar estrategia, filtros y pagina.
- **FR-022**: Una respuesta exitosa sin elementos MUST mostrar exactamente "Todavía no hay cotizaciones" como estado vacio diferenciado de carga y error.
- **FR-023**: Si varias consultas se superponen, solamente la respuesta correspondiente a los criterios mas recientes MUST actualizar el estado visible.

**Responsive, consistencia y accesibilidad**

- **FR-024**: La vista MUST conservar la linea visual del producto y presentar estrategia, filtros, estados y paginacion de forma consistente con la experiencia del Album.
- **FR-025**: La presentacion MUST adaptarse desde 320 px de ancho hasta escritorio sin perdida de informacion, superposiciones ni desplazamiento horizontal de toda la pagina.
- **FR-026**: En presentacion tabular, cada columna MUST tener un encabezado asociado semanticamente a sus celdas. Si en pantallas pequenas se usa una presentacion no tabular, cada valor MUST conservar una etiqueta accesible equivalente.
- **FR-027**: Todos los selectores, filtros, controles de pagina y acciones de reintento MUST tener nombre accesible, foco visible y ser operables solamente con teclado.
- **FR-028**: Los estados de carga, error y vacio MUST poder ser identificados por tecnologias de asistencia sin depender de color o posicion visual.
- **FR-029**: El texto y los indicadores visuales MUST cumplir WCAG 2.2 nivel AA: contraste minimo de 4.5:1 para texto normal y 3:1 para texto grande y componentes o controles visuales.

**Limites de entrega**

- **FR-030**: Esta entrega MUST limitar los cambios sobre archivos existentes a `frontend/src/App.jsx`, para registrar la ruta protegida; `frontend/src/layout/AppLayout.jsx`, para habilitar el item de navegacion Ranking; y `frontend/src/routes/routes.test.jsx`, exclusivamente para cubrir la ruta protegida y su navegacion. El resto de la capacidad MUST incorporarse mediante archivos nuevos.
- **FR-031**: Esta entrega MUST reutilizar la autenticacion, los valores de dominio y la capacidad de ranking existentes; MUST NOT modificar el calculo, persistencia ni historial de cotizaciones.
- **FR-032**: Esta entrega MUST NOT incorporar el detalle de jugador, acciones de compra o venta, recalculo de cotizaciones, ordenamiento manual, busqueda por texto ni exportacion.

### Key Entities

- **Elemento de ranking**: Representa la posicion de un jugador en una estrategia determinada. Contiene puesto, jugador, cotizacion actual, variacion porcentual, estrategia y version de cotizacion.
- **Jugador resumido**: Identidad y datos descriptivos necesarios para la fila: identificador, nombre, equipo, liga y una o mas posiciones.
- **Estrategia de cotizacion**: Criterio que determina el ranking consultado. Sus alternativas visibles son OFFICIAL y MOMENTUM; una de ellas es la activa por defecto.
- **Criterios de ranking**: Combinacion vigente de estrategia, liga, posicion y pagina que determina el conjunto visible y debe preservarse durante paginacion o reintento.
- **Pagina de ranking**: Coleccion ordenada de elementos junto con pagina actual, tamano, cantidad total de elementos y cantidad total de paginas.
- **Variacion de cotizacion**: Comparacion porcentual con el valor anterior; puede ser positiva, negativa, cero o ausente, con significados visuales y accesibles diferentes.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los accesos de usuarios autenticados desde ambos menus llega a `/ranking`, y el 100% de los accesos no autenticados queda sujeto al flujo de proteccion existente.
- **SC-002**: En el 100% de los escenarios con datos conocidos, cada resultado muestra correctamente los siete grupos de informacion requeridos: puesto, jugador, equipo, liga, posiciones, cotizacion y variacion.
- **SC-003**: El 100% de los casos de variacion positiva, negativa, cero y ausente se distingue correctamente sin depender solo del color, y cero nunca se presenta como dato ausente.
- **SC-004**: En el 100% de las combinaciones probadas de estrategia, liga y posicion, los controles reflejan los criterios vigentes, el cambio reinicia la paginacion y el resultado visible corresponde a la consulta mas reciente.
- **SC-005**: El 100% de los recorridos de paginacion probados impide salir de los limites e informa correctamente la pagina humana actual y el total.
- **SC-006**: Los estados de carga, error, reintento exitoso y respuesta vacia se reconocen y completan correctamente en el 100% de los escenarios de aceptacion, conservando los criterios cuando corresponde.
- **SC-007**: En pruebas a 320 px, 768 px y 1440 px de ancho, toda la informacion y todos los controles permanecen disponibles, sin solapamientos ni desplazamiento horizontal global.
- **SC-008**: El 100% de los controles puede recorrerse y activarse con teclado, tiene foco visible y nombre accesible; los encabezados o etiquetas permiten identificar el significado de cada dato con lector de pantalla.
- **SC-009**: **PENDING - validacion externa final**. Antes de cerrar la feature, el equipo del proyecto MUST realizar y registrar evidencia de una prueba con al menos 10 participantes; al menos 9 MUST identificar al jugador de mayor cotizacion y aplicar un filtro de liga o posicion en menos de 30 segundos, sin asistencia. Mientras no se complete la muestra minima y se adjunte evidencia al pull request, este criterio MUST permanecer PENDING y MUST NOT considerarse cumplido.
- **SC-010**: En un benchmark de 20 consultas consecutivas contra el backend local, con sesion autenticada, cache caliente y paginas de hasta 20 elementos, al menos 19 MUST mostrar la tabla o un estado terminal comprensible en menos de 2 segundos. Cada medicion comienza con la accion del usuario que inicia o reemplaza la consulta y termina cuando aparece la tabla o el estado terminal; los 20 tiempos MUST registrarse como evidencia.

## Assumptions

- La feature 018 esta disponible mediante `GET /players/ranking?strategy&league&position&page&size` y entrega una pagina autenticada con puesto, jugador resumido, valor, variacion porcentual nullable, estrategia y version.
- Si no se especifica estrategia, la capacidad de ranking resuelve la estrategia activa; la interfaz adopta la estrategia efectiva informada por los resultados.
- Los codigos y etiquetas de liga y posicion, junto con los colores de liga, ya tienen una fuente de dominio compartida en el frontend.
- Los filtros de liga y posicion se combinan de forma conjunta. Un jugador con varias posiciones coincide si cualquiera de ellas es la seleccionada.
- El puesto ya esta calculado sobre el conjunto filtrado completo antes de paginar y no debe inferirse a partir del indice visible.
- La primera pagina se identifica como 0 en el contrato de datos y como 1 para el usuario. El tamano por defecto lo determina el contrato existente.
- La sesion y la respuesta ante acceso no autenticado reutilizan el comportamiento ya establecido por las rutas protegidas del producto.
- Los textos visibles se presentan en espanol; los codigos de estrategia se conservan como OFFICIAL y MOMENTUM.
- La validacion externa de SC-009 es responsabilidad compartida del equipo del proyecto y constituye un gate de cierre, no un bloqueo para planificar o implementar.

## Dependencies

- Capacidad backend de ranking entregada por la feature 018, incluyendo filtros, estrategia activa y metadatos de paginacion.
- Autenticacion y proteccion de rutas existentes.
- Catalogos de ligas y posiciones y convenciones de color ya utilizados por el Album.
- La navegacion al detalle depende de la feature 024 y queda expresamente diferida.

## Out of Scope

- Detalle del jugador y navegacion desde las filas en esta entrega.
- Modificar, recalcular o administrar cotizaciones y estrategias.
- Compra, venta o cualquier otra operacion financiera desde el ranking.
- Ordenamiento local, busqueda libre, filtros adicionales o exportacion.
- Cambios en archivos existentes distintos de `frontend/src/App.jsx`, `frontend/src/layout/AppLayout.jsx` y `frontend/src/routes/routes.test.jsx` bajo los fines limitados por FR-030.
