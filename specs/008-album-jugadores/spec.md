# Feature Specification: El álbum de jugadores

**Feature Branch**: N/A — no se creó una rama para esta especificación

**Created**: 2026-09-26

**Status**: Draft

**Input**: User description: "Frontend: pantalla El álbum (catálogo de jugadores), con filtros, búsqueda local, vistas de cartas y lista, paginación cliente y estados completos, reutilizando la base visual y funcional existente."

## Contexto y dependencias

Esta feature reemplaza el contenido provisorio de la ruta protegida `/album` por el catálogo navegable de jugadores de las cinco grandes ligas.

Depende de:

- `specs/004-simplificar-catalogo-jugadores`, cuyo contrato vigente define los datos, filtros y errores de `GET /players` y reemplaza al contrato de Feature 003.
- `specs/006-jwt-protected-endpoints`, que amplía la seguridad histórica del contrato 004 para permitir JWT Bearer en endpoints protegidos. La aplicación web usa exclusivamente el JWT de la sesión; no expone ni envía `X-API-KEY`.
- `specs/007-frontend-app-base-auth`, que aporta la ruta protegida, la sesión en memoria, el cliente HTTP, el sistema visual de álbum impreso y los componentes compartidos.

La pantalla debe conservar la identidad visual normativa de Feature 007 y reutilizar sus tokens, componentes, modelo de catálogo y formatos. Esta entrega no incorpora navegación hacia un jugador ni funciones de ranking, mercado o portfolio.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Recorrer el álbum completo (Priority: P1)

Como usuario con sesión iniciada, quiero abrir el álbum y recorrer todas las figuritas ordenadas por nombre, para explorar el catálogo completo de jugadores de las cinco ligas.

**Why this priority**: Mostrar el catálogo protegido es el propósito central de la pantalla y entrega valor aun sin usar filtros ni cambiar la presentación.

**Independent Test**: Iniciar sesión, entrar a `/album` con un catálogo de más de 24 jugadores y verificar la carga, el total inicial, el orden alfabético, la primera página de 24 figuritas y la navegación por páginas.

**Acceptance Scenarios**:

1. **Given** una sesión válida y un catálogo con jugadores, **When** el usuario abre `/album`, **Then** ve el título `El álbum`, el total de la primera carga con formato es-AR, la leyenda de 5 ligas y las primeras 24 figuritas ordenadas por nombre ascendente.
2. **Given** más de 24 resultados, **When** el usuario avanza a otra página, **Then** ve el tramo correcto sin repetir ni omitir jugadores y el inicio de los resultados vuelve a quedar visible.
3. **Given** un catálogo de hasta 24 resultados, **When** finaliza la carga, **Then** se informa el tramo visible y no se ofrecen páginas inexistentes.
4. **Given** jugadores con nacionalidad o edad nulas, **When** se muestran sus figuritas, **Then** los datos faltantes aparecen como `—` y nunca como valores técnicos.

---

### User Story 2 - Encontrar jugadores con filtros y búsqueda (Priority: P1)

Como usuario, quiero combinar liga, posición, equipo y nombre para reducir rápidamente el catálogo a los jugadores que me interesan.

**Why this priority**: Un catálogo de aproximadamente 2.600 jugadores no resulta práctico sin mecanismos de reducción y búsqueda.

**Independent Test**: Seleccionar una liga y una posición, elegir un equipo disponible, escribir un nombre con distintas mayúsculas o tildes y comprobar que los filtros remotos se combinan, la búsqueda local se aplica sobre la última respuesta y cada cambio vuelve a la primera página.

**Acceptance Scenarios**:

1. **Given** el álbum cargado, **When** el usuario elige liga, posición y equipo, **Then** se solicitan únicamente los filtros con valor y el resultado satisface los tres criterios simultáneamente.
2. **Given** una respuesta actual, **When** el usuario busca por nombre, **Then** se filtra esa respuesta sin una nueva consulta y sin distinguir mayúsculas ni tildes.
3. **Given** opciones de equipo obtenidas sin filtro de equipo, **When** cambia la liga o la posición, **Then** el equipo vuelve a `Todos` y sus opciones se recalculan a partir de la nueva respuesta.
4. **Given** filtros, búsqueda o una página posterior activos, **When** el usuario elige `Limpiar filtros`, **Then** liga, posición, equipo y búsqueda vuelven a sus valores iniciales y se muestra la primera página del catálogo completo.
5. **Given** una consulta de filtros todavía en curso, **When** el usuario cambia nuevamente un filtro, **Then** sólo la consulta más reciente puede actualizar la pantalla.

---

### User Story 3 - Elegir entre cartas y lista (Priority: P2)

Como usuario, quiero alternar entre figuritas y una tabla compacta para elegir la presentación más útil durante mi exploración actual.

**Why this priority**: Las cartas refuerzan la identidad del producto y la tabla facilita comparar muchos atributos; ambas presentan los mismos resultados y orden.

**Independent Test**: Alternar los botones `▦ Cartas` y `☰ Lista` sobre un mismo conjunto filtrado y verificar que los datos, el orden, el tramo y la página no cambian, que el control anuncia el modo activo y que ninguna fila ni carta es accionable.

**Acceptance Scenarios**:

1. **Given** que el usuario abre el álbum, **When** finaliza la primera carga, **Then** la vista predeterminada es `▦ Cartas`.
2. **Given** resultados visibles, **When** el usuario activa `☰ Lista`, **Then** ve las columnas `N°`, `Jugador`, `Equipo`, `Liga`, `Posición`, `Nacionalidad`, `Edad` y `Valor` con el mismo orden y paginación.
3. **Given** la vista de lista, **When** un jugador tiene varias posiciones, **Then** todas se muestran como etiquetas diferenciadas.
4. **Given** que el usuario alterna la vista, **When** continúa navegando en la misma carga de la aplicación, **Then** el modo elegido se mantiene; al iniciar una carga nueva de la aplicación vuelve a cartas.
5. **Given** cualquier carta o fila, **When** el usuario intenta interactuar con ella, **Then** no navega ni abre un detalle.

---

### User Story 4 - Comprender y recuperar estados excepcionales (Priority: P2)

Como usuario, quiero recibir mensajes claros durante la carga, ante resultados vacíos o frente a una falla, para saber qué está ocurriendo y cómo continuar.

**Why this priority**: El catálogo depende de una consulta protegida y debe distinguir una ausencia real de datos, filtros sin coincidencias y una indisponibilidad temporal.

**Independent Test**: Simular carga lenta, catálogo vacío, filtros sin coincidencias, error 400, error 5xx, red/timeout y 401, y verificar el estado, texto y acción exactos de cada caso.

**Acceptance Scenarios**:

1. **Given** una consulta en curso, **When** todavía no hay respuesta, **Then** se muestran 8 casilleros con `Cargando figuritas…`, pulso, estado ocupado accesible y sin paginador.
2. **Given** que la primera consulta sin filtros devuelve una lista vacía, **When** termina la carga, **Then** se muestra `Álbum vacío`, `Todavía no hay figuritas` y `El catálogo de jugadores aún no fue cargado. Probá de nuevo más tarde.`, sin botón.
3. **Given** filtros o búsqueda activos sin coincidencias, **When** se obtiene el conjunto vacío, **Then** se muestra `0 resultados`, `No hay figuritas con esos filtros`, `Probá con otra liga, equipo o posición.` y la acción `Limpiar filtros`.
4. **Given** una falla de red, timeout o respuesta 5xx, **When** la consulta no puede completarse, **Then** se muestra `Sin conexión`, `No pudimos abrir el álbum`, `No se pudo conectar con el servidor. Intentá de nuevo en unos segundos.` y la acción `Reintentar`.
5. **Given** un error 400 inesperado, **When** la consulta falla, **Then** se presenta el mismo estado recuperable de error genérico.
6. **Given** una sesión vencida, **When** el catálogo recibe 401, **Then** la sesión se cierra y el usuario es llevado a `/ingresar` con `Tu sesión venció. Volvé a ingresar.`.
7. **Given** un estado de conexión fallida, **When** el usuario elige `Reintentar`, **Then** se repite la última consulta de catálogo con los filtros remotos vigentes.

---

### User Story 5 - Usar el álbum en pantallas pequeñas (Priority: P3)

Como usuario móvil, quiero filtrar y recorrer el álbum desde 360 px de ancho sin perder controles ni provocar desplazamiento horizontal de toda la página.

**Why this priority**: La aplicación base establece soporte móvil desde 360 px y el catálogo debe conservarlo con controles densos y una tabla amplia.

**Independent Test**: Probar la pantalla a 360 px con filtros, cartas, estados, paginador y tabla; verificar dos cartas por fila, controles legibles y desplazamiento horizontal limitado al panel de tabla.

**Acceptance Scenarios**:

1. **Given** una pantalla móvil, **When** se muestran los filtros, **Then** quedan apilados y los campos se distribuyen de a dos por fila cuando el espacio lo permite.
2. **Given** una pantalla móvil en vista de cartas, **When** hay resultados, **Then** se muestran dos figuritas por fila.
3. **Given** una pantalla móvil en vista de lista, **When** la tabla supera el ancho disponible, **Then** sólo el contenedor de tabla se desplaza horizontalmente y la página completa no lo hace.
4. **Given** una pantalla de escritorio en vista de cartas, **When** cambia el ancho disponible, **Then** la grilla completa filas automáticamente con cartas de al menos 180 px.

### Edge Cases

- El total del subtítulo se fija con la cantidad de jugadores de la primera respuesta exitosa sin filtros y no disminuye al filtrar o buscar.
- Una respuesta puede contener equipos repetidos con la misma escritura; las opciones de equipo los muestran una sola vez y en orden alfabético español.
- El equipo nunca se envía vacío: `Todos` representa ausencia del parámetro.
- Una búsqueda compuesta sólo por espacios se comporta como búsqueda vacía.
- Los nombres con tildes deben coincidir con la misma secuencia escrita sin tildes, y viceversa.
- Si un cambio de liga o posición invalida el equipo elegido, éste se restablece antes de emitir la nueva consulta.
- Si la cantidad filtrada reduce el número de páginas, el usuario vuelve a la página 1 y nunca queda en una página vacía fuera de rango.
- En la primera o última página, los controles anterior o siguiente correspondientes permanecen visibles pero deshabilitados.
- Si existe una sola página, el tramo se informa correctamente y los controles que no conducen a otra página quedan deshabilitados.
- El contrato exige que `positions` esté presente, pero permite un array vacío; sin posiciones, cartas y lista muestran `—`. Con una o varias posiciones, las cartas usan la precedencia ya definida por el catálogo compartido y la lista muestra todas.
- Nacionalidad y edad nulas se representan con `—` en ambas vistas.
- Una consulta cancelada por un filtro posterior no se presenta como error ni reemplaza resultados más recientes.
- Al reintentar después de un error se conservan liga, posición, equipo, búsqueda, vista y página, salvo que la respuesta válida obligue a ajustar el rango visible.

## Requirements *(mandatory)*

### Functional Requirements

**Acceso, datos y orden**

- **FR-001**: La ruta protegida `/album` MUST reemplazar el contenido provisorio por la pantalla funcional del catálogo y MUST requerir la sesión ya definida por la aplicación base.
- **FR-002**: La pantalla MUST obtener jugadores mediante `GET /players` y MUST aceptar la forma `{id, externalId, fullName, team, league, positions[], nationality|null, age|null, marketValue}` establecida por el contrato vigente.
- **FR-003**: Las consultas del frontend a `GET /players` MUST usar exclusivamente `Authorization: Bearer <jwt>` provisto por la sesión compartida y MUST NOT enviar `X-API-KEY`.
- **FR-004**: El título MUST ser `El álbum` y el subtítulo MUST ser `{N} jugadores · 5 ligas`, donde `N` es el total de la primera respuesta exitosa sin filtros, con agrupación numérica es-AR.
- **FR-005**: Todos los resultados MUST ordenarse por `fullName` ascendente conforme al orden alfabético español antes de paginarse.
- **FR-006**: La pantalla MUST procesar correctamente la lista completa devuelta por el catálogo, esperada en aproximadamente 2.600 jugadores, sin requerir paginación del servicio.

**Filtros y búsqueda**

- **FR-007**: La pantalla MUST ofrecer un componente controlado con filtros `Liga`, `Posición`, `Equipo`, `Buscar por nombre` y la acción `Limpiar filtros`.
- **FR-008**: `Liga` MUST ofrecer `Todas` y, usando los labels compartidos, `PREMIER_LEAGUE`, `LA_LIGA`, `SERIE_A`, `BUNDESLIGA` y `LIGUE_1`.
- **FR-009**: `Posición` MUST ofrecer `Todas` y, usando los labels compartidos, `GOALKEEPER`, `DEFENDER`, `MIDFIELDER` y `FORWARD`.
- **FR-010**: `Equipo` MUST ofrecer `Todos` más los nombres únicos, ordenados con comparación alfabética española, de la última respuesta obtenida sin filtro de equipo pero con la liga y posición vigentes.
- **FR-011**: Cambiar liga o posición MUST restablecer equipo a `Todos` antes de consultar y MUST recalcular sus opciones con la respuesta resultante.
- **FR-012**: Liga, posición y equipo MUST enviarse como parámetros de consulta sólo cuando tengan un valor distinto del predeterminado y MUST combinarse con AND.
- **FR-013**: La búsqueda por nombre MUST aplicarse en el cliente sobre la respuesta remota actual, MUST ignorar mayúsculas y tildes y MUST NOT iniciar una consulta al servicio.
- **FR-014**: Cambiar cualquier filtro remoto o la búsqueda MUST restablecer la página actual a 1.
- **FR-015**: `Limpiar filtros` MUST restablecer liga, posición, equipo y búsqueda, volver a la página 1 y recuperar o presentar el catálogo completo.
- **FR-016**: Cuando cambien filtros mientras haya una consulta en curso, la consulta anterior MUST cancelarse y sus resultados o errores MUST NOT modificar el estado de la consulta más reciente.

**Presentación y sistema visual**

- **FR-017**: La pantalla MUST reutilizar los tokens y componentes compartidos de Feature 007, incluidos `Figurita`, `Casillero`, `EstadoPanel`, `Sello`, `Field`, `Button`, el cliente HTTP, la sesión, el catálogo de ligas/posiciones y los formatos; MUST NOT duplicarlos.
- **FR-018**: Todo estilo agregado MUST usar las variables visuales compartidas y respetar la estética normativa de álbum impreso: sin bordes redondeados, sin sombras difusas y sin colores o degradados nuevos fuera de las excepciones ya autorizadas.
- **FR-019**: La barra de filtros MUST tener fondo blanco, borde visual compartido y 12 px de espacio interior; sus campos MUST disponerse en fila con ajuste de línea y los controles MUST conservar el aspecto de los campos existentes a 14 px.
- **FR-020**: `Limpiar filtros` MUST mostrarse como botón de texto monoespaciado, bold, de 12 px, subrayado y sin borde.
- **FR-021**: La barra MUST incluir a la derecha un selector con dos botones unidos, `▦ Cartas` y `☰ Lista`, con borde compartido y texto monoespaciado bold de 12 px.
- **FR-022**: El modo activo MUST usar fondo tinta y texto papel, MUST exponer `aria-pressed` y MUST mantenerse sólo durante la carga actual de la aplicación; `Cartas` MUST ser el modo inicial.
- **FR-023**: La vista de cartas MUST presentar una grilla del componente `Figurita`; las cartas MUST NOT ser enlaces, botones ni disparar navegación.
- **FR-024**: La vista de lista MUST presentar una tabla dentro de un panel blanco con borde grueso, sombra mediana y desplazamiento horizontal propio.
- **FR-025**: La tabla MUST incluir, en este orden, `N°`, `Jugador`, `Equipo`, `Liga`, `Posición`, `Nacionalidad`, `Edad` y `Valor`.
- **FR-026**: Los encabezados de tabla MUST ser monoespaciados, bold, de 10 px y mayúsculas, con fondo papel secundario y separación inferior; las celdas MUST tener 10 px verticales y 12 px horizontales, separadores de `rgba(27,26,23,.18)` y hover de fila `rgba(244,194,27,.13)`.
- **FR-027**: La celda `Jugador` MUST mostrar un rectángulo de 22 × 26 px con el color de la liga junto al nombre en bold.
- **FR-028**: La celda `Posición` MUST mostrar todas las posiciones como etiquetas amarillas monoespaciadas de 10 px con borde de 1,5 px.
- **FR-029**: Las columnas `N°`, `Edad` y `Valor` MUST alinearse a la derecha y usar tipografía monoespaciada; los valores nulos MUST mostrarse como `—`.
- **FR-030**: Las filas MUST NOT ser enlaces, botones ni disparar navegación.

**Paginación**

- **FR-031**: La pantalla MUST paginar localmente el conjunto ordenado y buscado en grupos de 24 jugadores.
- **FR-032**: El paginador centrado MUST presentar anterior `‹`, primera página, elipsis cuando corresponda, página actual y sus adyacentes, última página y siguiente `›`, sin duplicar números.
- **FR-033**: Los controles de página MUST tener 36 px de alto, borde compartido, fondo blanco y texto monoespaciado bold de 13 px; la página actual MUST usar fondo tinta, texto papel y `aria-current="page"`.
- **FR-034**: Los controles sin destino MUST permanecer deshabilitados con opacidad `.35`.
- **FR-035**: La pantalla MUST anunciar `Mostrando {desde}–{hasta} de {total}` en una región `aria-live="polite"` usando el total posterior a filtros y búsqueda.
- **FR-036**: Cambiar de página MUST desplazar la vista al inicio de los resultados.
- **FR-037**: Durante la carga y cuando no existan resultados MUST ocultarse el paginador.

**Estados y recuperación**

- **FR-038**: Durante una consulta MUST mostrarse una región con `aria-busy` y exactamente 8 `Casillero` en variante de carga, cada uno con `Cargando figuritas…` y pulso compatible con la preferencia de movimiento reducido.
- **FR-039**: Una primera respuesta vacía sin filtros ni búsqueda MUST mostrar mediante `EstadoPanel` y `Sello` neutral: sello `Álbum vacío`, título `Todavía no hay figuritas`, texto `El catálogo de jugadores aún no fue cargado. Probá de nuevo más tarde.` y ningún botón.
- **FR-040**: Cero coincidencias con filtros o búsqueda MUST mostrar mediante `EstadoPanel` y `Sello` neutral: sello `0 resultados`, título `No hay figuritas con esos filtros`, texto `Probá con otra liga, equipo o posición.` y botón `Limpiar filtros`.
- **FR-041**: Una falla de red, timeout, respuesta 5xx o error 400 MUST mostrar mediante `EstadoPanel` y `Sello` de error: sello `Sin conexión`, título `No pudimos abrir el álbum`, texto `No se pudo conectar con el servidor. Intentá de nuevo en unos segundos.` y botón `Reintentar`.
- **FR-042**: `Reintentar` MUST repetir la última consulta remota con los filtros vigentes y MUST impedir que una consulta anterior sobrescriba su resultado.
- **FR-043**: Una respuesta 401 MUST delegarse al manejo compartido de sesión, que cierra la sesión y redirige a `/ingresar` con el mensaje exacto `Tu sesión venció. Volvé a ingresar.`.

**Adaptación y límites**

- **FR-044**: Desde 360 px, la página MUST evitar desplazamiento horizontal global; en móvil los filtros MUST apilarse, sus campos MUST organizarse de a dos por fila cuando el ancho lo permita y las figuritas MUST formar dos columnas.
- **FR-045**: En escritorio, la grilla MUST completar automáticamente cada fila con figuritas de al menos 180 px.
- **FR-046**: El desplazamiento horizontal de la tabla MUST quedar limitado a su propio panel.
- **FR-047**: Esta feature MUST NOT agregar detalle de jugador, gráfico de cotización, ranking, mercado, portfolio, variación de valor, cinta de cotizaciones, saldo en el encabezado, sincronización del catálogo, filtros en URL, persistencia de preferencias, búsqueda o paginación del servicio ni cambios en backend.

### Key Entities

- **Jugador**: Elemento del catálogo identificado por `id` y `externalId`; contiene nombre completo, equipo, liga, una o más posiciones, nacionalidad y edad opcionales, y valor de mercado.
- **Filtros del catálogo**: Estado controlado de liga, posición, equipo y búsqueda por nombre. Liga, posición y equipo determinan la consulta remota; la búsqueda reduce localmente la respuesta actual.
- **Opciones de equipo**: Conjunto único y ordenado de equipos derivado de la última respuesta correspondiente a liga y posición sin aplicar equipo.
- **Vista del catálogo**: Preferencia efímera entre cartas y lista; no altera resultados, orden ni página y no se persiste.
- **Página de resultados**: Segmento de hasta 24 jugadores del conjunto ordenado posterior a filtros y búsqueda, acompañado por rango, total y controles de navegación.
- **Estado de carga del catálogo**: Situación observable de consulta en curso, catálogo vacío, resultados visibles, cero coincidencias o falla recuperable; determina el contenido y las acciones disponibles.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un usuario con sesión válida puede abrir `/album` y ver el primer tramo del catálogo completo de aproximadamente 2.600 jugadores, ordenado alfabéticamente, dentro de 2 segundos después de recibida la respuesta del servicio.
- **SC-002**: El 100% de las combinaciones válidas de liga, posición y equipo presenta únicamente jugadores que cumplen todos los filtros seleccionados, y cada cambio vuelve a la primera página.
- **SC-003**: El 100% de las búsquedas por nombre devuelve el mismo conjunto independientemente de mayúsculas y tildes equivalentes y no genera consultas adicionales al servicio.
- **SC-004**: En el 100% de los cambios rápidos de filtros, ninguna respuesta cancelada o anterior reemplaza el resultado de la consulta más reciente.
- **SC-005**: Para cualquier conjunto de resultados, cartas y lista muestran los mismos jugadores, en el mismo orden y tramo, y el usuario puede alternarlas en una sola acción.
- **SC-006**: El 100% de los conjuntos con más de 24 jugadores puede recorrerse sin duplicaciones ni omisiones; el rango anunciado coincide con los elementos visibles en cada página.
- **SC-007**: Los seis estados verificables —carga, resultados, catálogo vacío, cero coincidencias, falla recuperable y sesión vencida— presentan el texto, acción y comportamiento definidos en todos los escenarios de aceptación.
- **SC-008**: A 360 px de ancho, todas las funciones permanecen utilizables, se muestran dos cartas por fila y no existe desplazamiento horizontal de la página; cualquier desplazamiento de la tabla queda contenido en su panel.
- **SC-009**: El 100% de controles de filtros, vista y paginación puede operarse con teclado y comunica programáticamente selección, página actual, ocupación y actualizaciones de rango.
- **SC-010**: Ninguna carta ni fila abre una pantalla de detalle, y ninguna función excluida aparece disponible en esta entrega.

## Assumptions

- El contrato de Feature 004 continúa siendo la fuente de verdad para parámetros, enumeraciones, respuesta y errores de `GET /players`; Feature 006 lo complementa únicamente en autenticación y permite al frontend usar el JWT Bearer de la sesión.
- La primera respuesta exitosa sin filtros se obtiene al abrir la pantalla y establece el total del subtítulo durante esa carga de `/album`.
- La opción de equipo se deriva de una respuesta sin filtro de equipo para no perder alternativas por la selección previa; al elegir equipo puede realizarse una nueva consulta con los tres filtros remotos.
- La búsqueda vacía incluye una cadena compuesta sólo por espacios después de normalizarla.
- El orden alfabético español se aplica tanto a jugadores como a equipos; cuando dos nombres comparan como iguales, se conserva un orden estable.
- La página se considera móvil bajo el criterio responsive ya establecido por la base del frontend; el requisito observable prioritario es la correcta presentación desde 360 px.
- Los formatos de liga, posición y créditos existentes son los labels y formatos canónicos del producto.

## Out of Scope

- Detalle del jugador y cualquier navegación desde cartas o filas.
- Gráficos o historial de cotización, ranking, mercado, compra o venta de tokens y portfolio.
- Variaciones visuales de valor, cinta de cotizaciones y saldo del usuario en el encabezado.
- Sincronización manual mediante `POST /players/sync`.
- Paginación o búsqueda por nombre en backend.
- Filtros reflejados en la URL o persistencia de filtros, vista o página.
- Modificaciones en el backend o en el contrato de jugadores.
