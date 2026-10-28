# Feature Specification: Detalle de jugador y evolución de su cotización (frontend)

**Feature Branch**: `feature/front-detalle-cotizacion`

**Created**: 2026-10-27

**Status**: Draft

**Input**: User description: "Pantalla de detalle de un jugador con el gráfico de evolución de su cotización, consumiendo GET /players/{id} y GET /players/{id}/quotes. Ruta protegida /jugadores/:id; cabecera tipo figurita con cotización actual y variación; gráfico de línea con selector de estrategia (OFFICIAL / MOMENTUM) y rango (último mes, 3 meses, toda la temporada) y tooltip con fecha, valor, estrategia y versión; consulta de cotización en una fecha puntual; estadísticas de temporada con '—' si faltan; estados de carga, error con reintento, 404 y sin cotizaciones; responsive y accesible con tabla alternativa al gráfico; cada figurita del Álbum y cada fila del Ranking llevan al detalle. Autorizado: agregar recharts, la ruta /jugadores/:id en App.jsx y el cambio mínimo en Figurita o AlbumGrid sin cambiar su aspecto."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver la ficha del jugador desde el Álbum (Priority: P1)

Un usuario autenticado está mirando el Álbum, elige una figurita y llega a la ficha de ese jugador. Ve una cabecera con la misma estética de figurita (número, nombre, equipo, liga con su color, posición, nacionalidad, edad), su cotización actual y cuánto varió respecto de la cotización anterior, y debajo las estadísticas de la temporada.

**Why this priority**: Es la puerta de entrada al resto de la pantalla: sin poder llegar al detalle y ver los datos básicos del jugador, el gráfico no tiene contexto. Por sí sola ya entrega valor (la vista "detalle" es obligatoria en la constitución, §2.2).

**Independent Test**: Con un jugador existente que tiene estadísticas y cotización, hacer clic en su figurita del Álbum y verificar que se muestra la cabecera con sus datos, cotización actual, variación y las estadísticas.

**Acceptance Scenarios**:

1. **Given** un usuario autenticado en el Álbum, **When** activa (clic, toque o teclado) la figurita de un jugador, **Then** navega a `/jugadores/{id}` de ese jugador.
2. **Given** un jugador con cotización actual y al menos una cotización anterior de la misma estrategia, **When** se abre su detalle, **Then** la cabecera muestra la cotización actual en créditos y la variación (absoluta y porcentual) con indicación visual y textual de suba, baja o sin cambio.
3. **Given** un jugador con una única cotización, **When** se abre su detalle, **Then** se muestra la cotización actual y la variación aparece como "—".
4. **Given** un jugador con estadísticas de temporada, **When** se abre su detalle, **Then** se ven minutos, goles, asistencias, tiros, pases clave, tackles, tarjetas (amarillas y rojas) y rating.
5. **Given** un jugador sin estadísticas, o con algún valor faltante, **When** se abre su detalle, **Then** cada dato faltante se muestra como "—" y el resto de la pantalla funciona igual.
6. **Given** un visitante sin sesión, **When** abre `/jugadores/{id}`, **Then** es redirigido a la pantalla de ingreso, igual que en el resto de las rutas protegidas.

---

### User Story 2 - Ver la evolución de la cotización en un gráfico (Priority: P1)

En la ficha, el usuario ve un gráfico de línea con la evolución de la cotización del jugador en el tiempo. Puede cambiar la estrategia de valuación (OFFICIAL o MOMENTUM) y el rango (último mes, últimos 3 meses o toda la temporada). Al pasar el puntero o enfocar un punto, ve un tooltip con fecha, valor, estrategia y versión de la estrategia.

**Why this priority**: Es el objetivo central de la feature y una vista obligatoria de la materia ("detalle y gráfico de evolución de cotización").

**Independent Test**: Con un jugador que tiene historial en ambas estrategias, abrir el detalle, cambiar estrategia y rango y verificar que el gráfico y su tabla alternativa muestran exactamente los puntos del historial correspondiente.

**Acceptance Scenarios**:

1. **Given** un jugador con historial de cotizaciones, **When** se abre su detalle, **Then** el gráfico muestra la evolución de la estrategia de su cotización actual (o OFFICIAL si no tiene cotización actual) en el rango "último mes", ordenada cronológicamente.
2. **Given** el gráfico visible, **When** el usuario elige otra estrategia, **Then** el gráfico se actualiza con el historial de esa estrategia manteniendo el rango elegido.
3. **Given** el gráfico visible, **When** el usuario elige "3 meses" o "toda la temporada", **Then** el gráfico se actualiza con los puntos de ese rango manteniendo la estrategia elegida.
4. **Given** un punto del gráfico, **When** el usuario pasa el puntero, toca o enfoca ese punto, **Then** un tooltip muestra fecha, valor en créditos, estrategia y versión.
5. **Given** cualquier combinación de estrategia y rango, **When** se muestra el gráfico, **Then** existe una tabla alternativa accesible para lectores de pantalla con las mismas filas (fecha, valor, estrategia, versión) que los puntos del gráfico.
6. **Given** un jugador sin cotizaciones en la estrategia y rango elegidos, **When** se muestra la sección, **Then** aparece el estado "todavía sin cotizaciones" en lugar de un gráfico vacío, y los selectores siguen disponibles.
7. **Given** que se cambia de estrategia o rango varias veces rápido, **When** las respuestas llegan en otro orden, **Then** el gráfico refleja siempre la última selección del usuario.

---

### User Story 3 - Consultar la cotización en una fecha puntual (Priority: P2)

El usuario elige una fecha en un selector y ve cuál era la cotización vigente del jugador al final de ese día, para la estrategia elegida en el gráfico.

**Why this priority**: Suma valor de análisis, pero el gráfico ya cubre la lectura general de la evolución.

**Independent Test**: Elegir una fecha posterior a la primera cotización y verificar que se muestra el valor vigente con su fecha de cálculo, estrategia y versión; elegir una fecha anterior a toda cotización y verificar el mensaje de "sin cotización para esa fecha".

**Acceptance Scenarios**:

1. **Given** el detalle de un jugador con cotizaciones, **When** el usuario elige una fecha y confirma la consulta, **Then** se muestra la cotización vigente al fin de ese día: valor, fecha de cálculo, estrategia y versión.
2. **Given** una fecha anterior a la primera cotización del jugador en esa estrategia, **When** se consulta, **Then** se informa que no había cotización vigente para esa fecha, sin tratarlo como error del sistema.
3. **Given** el selector de fecha, **When** el usuario intenta elegir una fecha futura, **Then** no puede hacerlo (o la consulta no se envía y se le indica el motivo).
4. **Given** una consulta mostrada, **When** el usuario cambia la estrategia del gráfico, **Then** el resultado de la consulta puntual se descarta o se recalcula para la nueva estrategia, nunca queda mostrando un valor de otra estrategia sin aclararlo.

---

### User Story 4 - Estados de carga, error e inexistencia (Priority: P2)

Mientras se cargan los datos el usuario ve un estado de carga; si algo falla ve un mensaje claro con la opción de reintentar; si el jugador no existe ve un estado específico con un camino de vuelta al Álbum.

**Why this priority**: Es necesario para que la pantalla sea robusta y coherente con el resto de la app, pero no agrega funcionalidad nueva por sí mismo.

**Independent Test**: Simular respuestas lentas, fallidas y 404 del servidor y verificar cada estado y la acción de reintento.

**Acceptance Scenarios**:

1. **Given** que los datos del jugador todavía no llegaron, **When** se abre el detalle, **Then** se muestra un estado de carga anunciado a lectores de pantalla.
2. **Given** que la carga del jugador falla por un error del servidor o de red, **When** el usuario ve el error, **Then** puede reintentar y, si la reintentona funciona, ve el detalle completo.
3. **Given** que falla solo la carga del historial, **When** se muestra la pantalla, **Then** la cabecera y las estadísticas se ven igual y solo la sección del gráfico muestra el error con su reintento.
4. **Given** un identificador que no corresponde a ningún jugador, o que no es un número entero positivo, **When** se abre `/jugadores/{id}`, **Then** se muestra el estado "jugador inexistente" con un enlace para volver al Álbum, sin opción de reintento.
5. **Given** que la sesión venció durante la carga, **When** el servidor rechaza las credenciales, **Then** se aplica el mismo comportamiento de sesión vencida que en el resto de la app.

---

### User Story 5 - Llegar al detalle desde el Ranking (Priority: P3)

Desde cada fila del Ranking de jugadores, el usuario puede ir al detalle de ese jugador.

**Why this priority**: La pantalla de Ranking la construyó la feature 023 con filas no interactivas. Al integrarse con la 023 (ya en `main`), esta feature agrega el enlace desde el Ranking hacia `/jugadores/:id`; no modifica el ítem "Ranking" del menú.

**Independent Test**: Renderizar el Ranking y comprobar que el nombre de cada jugador es un enlace a `/jugadores/{id}` de ese jugador (`RankingPage.test.jsx`); que esa ruta resuelve el detalle está cubierto por las User Stories 1 y 4.

**Acceptance Scenarios**:

1. **Given** la pantalla de Ranking de la feature 023 con jugadores, **When** el usuario activa el nombre de un jugador, **Then** navega a `/jugadores/{id}` de ese jugador y ve el detalle definido en esta spec.

---

### Edge Cases

- Jugador sin cotización actual y sin historial: la cabecera muestra la cotización como "—" y la sección del gráfico muestra "todavía sin cotizaciones".
- Historial con un único punto: se muestra el punto (y su fila en la tabla), sin línea que inventar.
- Varias cotizaciones el mismo día: se muestran todas en orden cronológico; el tooltip distingue la hora además de la fecha.
- Puntos con estado "sin estadísticas" (cotización conservada sin recálculo): se muestran igual, con el mismo valor que el sistema informa.
- Cambio de versión de la estrategia dentro del rango: cada punto muestra su propia versión en tooltip y tabla.
- Jugador sin posición, nacionalidad o edad, o con liga desconocida: la cabecera usa "—" y el color neutro, como la figurita del Álbum.
- Identificador inválido en la URL (texto, cero, negativo, demasiado grande): se trata como jugador inexistente, sin pedir reintento.
- El usuario navega a otro jugador estando en un detalle: no se mezclan datos del jugador anterior.
- Pantalla angosta (celular): cabecera, controles, gráfico, consulta por fecha y estadísticas se apilan sin scroll horizontal de la página.
- Usuario que navega solo con teclado: puede activar figuritas, cambiar estrategia y rango, recorrer los puntos del gráfico y usar el selector de fecha.

## Requirements *(mandatory)*

### Functional Requirements

**Navegación y acceso**

- **FR-001**: El sistema MUST ofrecer la pantalla de detalle en la ruta `/jugadores/:id`, accesible solo con sesión iniciada; sin sesión, MUST redirigir al ingreso igual que las demás rutas protegidas.
- **FR-002**: Cada figurita del Álbum MUST llevar al detalle de su jugador, activable con puntero, toque y teclado, y anunciada como enlace a lectores de pantalla, sin cambiar su aspecto visual actual.
- **FR-003**: La vista en tabla del Álbum no se modifica en esta feature (la autorización de la Entrega 1 cubre solo la figurita o la grilla); se llega al detalle desde la vista de figuritas.
- **FR-004**: La ruta `/jugadores/:id` MUST ser el destino estable para enlazar el detalle desde otras pantallas. En el Ranking (feature 023), el nombre de cada jugador MUST ser un enlace a `/jugadores/{player.id}`; este enlace se agregó en esta feature al integrarse con la 023. Esta feature no modifica el ítem "Ranking" del menú.
- **FR-005**: La pantalla MUST ofrecer un camino visible para volver al Álbum.

**Cabecera del jugador**

- **FR-006**: La cabecera MUST seguir la estética de figurita existente y mostrar número, nombre completo, equipo, liga (con su color), posición, nacionalidad y edad, usando "—" para cada dato faltante.
- **FR-007**: La cabecera MUST mostrar la cotización actual del jugador en créditos, con el mismo formato que el resto de la app; si no hay cotización actual, MUST mostrar "—".
- **FR-008**: La cabecera MUST mostrar la variación de la cotización actual respecto de la cotización inmediatamente anterior de la misma estrategia, en créditos y en porcentaje, indicando suba, baja o sin cambio mediante texto/símbolo además del color; si no hay cotización anterior, MUST mostrar "—".

**Gráfico de evolución**

- **FR-009**: La pantalla MUST mostrar un gráfico de línea con la evolución de la cotización del jugador en el tiempo, en orden cronológico, con el valor en créditos en el eje vertical y la fecha en el horizontal.
- **FR-010**: El usuario MUST poder elegir la estrategia del gráfico entre OFFICIAL y MOMENTUM; la estrategia inicial MUST ser la de la cotización actual del jugador, u OFFICIAL si no la tiene.
- **FR-011**: El usuario MUST poder elegir el rango entre "último mes" (30 días hasta hoy), "3 meses" (90 días hasta hoy) y "toda la temporada" (todo el historial disponible); el rango inicial MUST ser "último mes".
- **FR-012**: Cada punto del gráfico MUST ofrecer un tooltip con fecha (y hora si hay más de un punto en el mismo día), valor en créditos, estrategia y versión, accesible por puntero, toque y foco de teclado.
- **FR-013**: El gráfico MUST tener una tabla alternativa con exactamente los mismos datos (fecha, valor, estrategia, versión) disponible para lectores de pantalla, y el gráfico en sí MUST tener una descripción textual (estrategia, rango, cantidad de puntos).
- **FR-014**: Si no hay cotizaciones para la estrategia y el rango elegidos, la sección MUST mostrar el estado "todavía sin cotizaciones" manteniendo disponibles los selectores.
- **FR-015**: Al cambiar estrategia o rango, el gráfico MUST mostrar solo los datos de la última selección, descartando respuestas de selecciones anteriores.

**Consulta en fecha puntual**

- **FR-016**: El usuario MUST poder elegir una fecha (no futura) y consultar la cotización vigente al final de ese día para la estrategia elegida en el gráfico, viendo valor, fecha de cálculo, estrategia y versión.
- **FR-017**: Si no existe cotización vigente para esa fecha, la pantalla MUST informarlo como resultado vacío ("sin cotización para esa fecha"), no como error del sistema.
- **FR-018**: Si la estrategia del gráfico cambia, el resultado de la consulta puntual MUST descartarse o actualizarse para la nueva estrategia.

**Estadísticas de temporada**

- **FR-019**: La pantalla MUST mostrar las estadísticas de la temporada del jugador: minutos jugados, goles, asistencias, tiros, pases clave, tackles, tarjetas amarillas, tarjetas rojas y rating, con etiquetas en castellano.
- **FR-020**: Cada estadística faltante (o todas, si el jugador no tiene estadísticas) MUST mostrarse como "—", nunca como 0.

**Estados**

- **FR-021**: Mientras cargan los datos del jugador, la pantalla MUST mostrar un estado de carga anunciado a tecnologías asistivas.
- **FR-022**: Ante un error de servidor o de red al cargar el jugador, la pantalla MUST mostrar un mensaje de error con acción de reintento.
- **FR-023**: Ante un error al cargar el historial o la consulta puntual, solo la sección afectada MUST mostrar el error con su reintento; el resto de la pantalla MUST seguir visible.
- **FR-024**: Si el jugador no existe o el identificador de la URL no es un entero positivo válido, la pantalla MUST mostrar el estado "jugador inexistente" con enlace al Álbum y sin reintento.
- **FR-025**: Los estados de carga, error, inexistencia y "sin cotizaciones" MUST usar los mismos componentes visuales de estado que el resto de la app.
- **FR-026**: Si el servidor rechaza las credenciales, MUST aplicarse el manejo de sesión vencida existente de la app.

**Diseño y accesibilidad**

- **FR-027**: La pantalla MUST ser usable desde 360 px de ancho (el piso actual de la app) sin scroll horizontal de la página, apilando las secciones en pantallas angostas.
- **FR-028**: Todos los controles (selectores de estrategia y rango, selector de fecha, enlaces y reintentos) MUST ser operables con teclado, tener nombre accesible y foco visible.
- **FR-029**: La información transmitida por color (liga, suba/baja) MUST transmitirse también por texto o símbolo.
- **FR-030**: El título del documento y el encabezado principal de la pantalla MUST identificar al jugador.

### Key Entities

- **Jugador (detalle)**: identificador, nombre completo, equipo, liga, posiciones, nacionalidad, edad, valor de mercado; puede venir acompañado de sus estadísticas de temporada y de su cotización actual, ambas opcionales.
- **Estadísticas de temporada**: minutos, goles, asistencias, tiros, pases clave, tackles, tarjetas amarillas y rojas, rating y fecha de obtención; cualquiera puede faltar.
- **Cotización**: valor en créditos, puntaje, estado (calculada o sin estadísticas), estrategia, versión de la estrategia y momento de cálculo. El historial es una lista cronológica de cotizaciones de una estrategia en un rango; la consulta puntual devuelve una sola (o ninguna).
- **Selección del gráfico**: estrategia (OFFICIAL / MOMENTUM) y rango (último mes / 3 meses / toda la temporada); estado de pantalla, no se persiste.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un usuario llega desde el Álbum a la ficha de cualquier jugador con una sola acción (un clic, toque o Enter).
- **SC-002**: Con conexión normal, la cabecera, las estadísticas y el gráfico inicial se ven en menos de 2 segundos desde que se abre el detalle (verificación manual, quickstart V3).
- **SC-003**: Cambiar estrategia o rango actualiza el gráfico en menos de 1 segundo con conexión normal (verificación manual, quickstart V4), y en el 100 % de los casos el gráfico corresponde a la última selección.
- **SC-004**: En el 100 % de las combinaciones de estrategia y rango, la tabla alternativa contiene las mismas filas que los puntos del gráfico.
- **SC-005**: Todas las tareas de la pantalla (navegar desde el Álbum, cambiar estrategia y rango, leer valores de puntos, consultar una fecha, reintentar) pueden completarse solo con teclado y con lector de pantalla.
- **SC-006**: La pantalla no presenta scroll horizontal de página en anchos de 360 px, 768 px y 1280 px.
- **SC-007**: El aspecto de las figuritas del Álbum no cambia (comparación visual antes/después sin diferencias).
- **SC-008**: Cada uno de los estados (carga, error con reintento, jugador inexistente, sin cotizaciones, sin cotización en la fecha, estadísticas faltantes) tiene al menos un test automatizado que lo verifica.

## Assumptions

- Los contratos del servidor son los ya publicados: detalle de jugador (feature 022, con estadísticas y cotización actual opcionales) e historial de cotizaciones (feature 017, con estrategia opcional, rango por fechas y consulta puntual que devuelve una sola cotización o "no encontrada").
- "Toda la temporada" se interpreta como todo el historial disponible del jugador para esa estrategia, sin límite inferior de fecha, porque el sistema no tiene una fecha de inicio de temporada configurada.
- Los rangos "último mes" y "3 meses" se calculan como 30 y 90 días corridos hasta hoy (UTC, igual que el servidor).
- La variación se calcula sobre el historial de la estrategia de la cotización actual, comparando la última cotización con la anterior.
- Las estrategias disponibles son exactamente OFFICIAL y MOMENTUM; no se descubren dinámicamente.
- Una respuesta "no encontrada" de la consulta puntual significa "sin cotización para esa fecha" y no "jugador inexistente", porque el jugador ya se cargó antes.
- La selección de estrategia y rango no se guarda entre visitas ni en la URL.
- Se reutilizan la estética, los colores por liga, el formato de créditos, los componentes de estado y el cliente HTTP existentes del frontend.
- Por autorización de la Entrega 1 se puede agregar una librería de gráficos, la ruta nueva y el cambio mínimo en la figurita o la grilla del Álbum para volverla enlazable sin cambiar su aspecto; no se modifica el backend.
- La compra/venta de tokens desde el detalle queda fuera de alcance (corresponde a la consola de trading).
- `VariationBadge` se crea en esta feature y la 023 lo reutiliza.
- La pantalla de Ranking (feature 023) queda fuera de alcance: no se construye ni se habilita su ítem en el menú. Al integrarse con la 023, esta feature agregó el enlace del nombre de cada jugador del Ranking hacia `/jugadores/:id`.

## Clarifications

### Session 2026-10-27

- Q: ¿Esta feature construye la pantalla de Ranking? → A: No. El Ranking es la feature 023, que todavía no existe. Esta feature deja lista la ruta `/jugadores/:id` y el enlace desde las figuritas del Álbum; la User Story 5 y FR-004 quedan como dependencia documentada para la 023. No se toca el ítem del menú.
