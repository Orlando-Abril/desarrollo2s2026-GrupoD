# Feature Specification: Pantalla Mi portfolio

**Feature Branch**: `feature/front-portfolio`

**Created**: 2026-10-30

**Status**: Draft

**Input**: User description: "Pantalla Mi portfolio: posición actual, rentabilidad e historial de operaciones. Resumen con saldo, invertido, valor actual y resultado; tabla de posiciones; gráficos de rendimiento y distribución; historial paginado; navegación al jugador; estados de carga, error y vacío; responsive y accesible. Autorizado: agregar la ruta en App.jsx y habilitar Mi portfolio en AppLayout.jsx."

## Contexto y límites

La aplicación ya ofrece un catálogo de jugadores, fichas de detalle y operación de tokens. El menú contiene el ítem "04 Mi portfolio", pero todavía está deshabilitado. El backend de la feature 028 ya permite consultar el portfolio valuado y el historial paginado del usuario autenticado, y la sesión puede identificar al usuario actual.

Esta feature incorpora la pantalla personal que reúne el estado patrimonial, las posiciones activas, dos visualizaciones comparativas y el historial de operaciones. Cada posición sirve también como acceso al detalle del jugador para continuar operando.

Quedan fuera de alcance los cambios en saldos, posiciones, cotizaciones u operaciones; la compra, venta o cancelación desde esta pantalla; los filtros y la exportación del historial; la rentabilidad histórica en el tiempo; y cualquier cambio de contrato en el backend 028.

**Archivos protegidos de Entrega 1**: no se modifican. **Excepción autorizada**: en `App.jsx`, sólo agregar la ruta protegida de Mi portfolio; en `layout/AppLayout.jsx`, sólo habilitar el ítem `portfolio` de `NAV_ITEMS` y asignarle su ruta. Si la planificación o implementación requiere otro cambio en archivos protegidos, se debe detener el trabajo y consultar al equipo indicando el archivo, el cambio mínimo y una alternativa.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Comprender la posición patrimonial actual (Priority: P1)

Como usuario autenticado, quiero ver en una sola pantalla mi saldo, cuánto tengo invertido, cuánto valen hoy mis posiciones y cuál es mi resultado, para comprender rápidamente mi situación patrimonial.

**Why this priority**: Es el propósito principal de Mi portfolio y permite interpretar la cuenta sin reconstruir cifras posición por posición.

**Independent Test**: Preparar una cuenta con dos posiciones de resultados distintos, abrir Mi portfolio y comparar los cuatro indicadores y cada fila contra los valores informados para esa cuenta.

**Acceptance Scenarios**:

1. **Given** un usuario con saldo 984,0000, costo total 16,0000, valor actual 19,5000 y resultado 3,5000, **When** abre Mi portfolio, **Then** ve esos cuatro valores en el resumen con formato consistente de créditos.
2. **Given** una posición de 15 tokens con precio promedio 1,0667, cotización 1,3000, valor actual 19,5000 y ganancia 3,5000, **When** consulta la tabla, **Then** ve el jugador y todos esos valores en la misma fila.
3. **Given** una posición con pérdida, una con ganancia y otra neutra, **When** se muestran sus resultados, **Then** cada estado se distingue por signo, texto o icono además del color; la pérdida y la ganancia usan tratamientos visuales diferentes y el resultado cero es neutral.
4. **Given** créditos reservados en órdenes activas, **When** se muestra el saldo, **Then** el indicador principal conserva el saldo total y además informa el saldo disponible y el monto reservado sin presentarlos como patrimonio adicional.

---

### User Story 2 - Analizar composición y rendimiento (Priority: P2)

Como usuario con posiciones, quiero comparar el precio promedio pagado contra la cotización vigente por jugador y ver cómo se distribuye el valor de mis activos, para identificar rendimientos y concentración.

**Why this priority**: Convierte los datos del portfolio en información visual para apoyar decisiones, pero depende del resumen y las posiciones de la historia principal.

**Independent Test**: Preparar tres posiciones con distintos costos y valores, y verificar que ambas visualizaciones representen exactamente los mismos datos que la tabla y sus totales.

**Acceptance Scenarios**:

1. **Given** varias posiciones activas, **When** se muestra el gráfico de rendimiento, **Then** cada jugador tiene dos valores comparables: precio promedio de compra y cotización actual, con leyenda e identificación inequívoca.
2. **Given** esas posiciones, **When** se muestra la distribución, **Then** cada segmento representa el valor actual de un jugador y el total de los segmentos equivale al valor actual total del portfolio.
3. **Given** una posición con valor actual 60 y otra con valor actual 40, **When** se consulta la distribución, **Then** se comunican proporciones de 60% y 40%, sujetas sólo al redondeo de presentación.
4. **Given** muchos jugadores o nombres largos, **When** se consultan las visualizaciones, **Then** siguen siendo legibles y permiten conocer el nombre y valor exacto de cada dato sin depender de una etiqueta truncada.

---

### User Story 3 - Revisar el historial de operaciones (Priority: P2)

Como usuario autenticado, quiero recorrer mis compras y ventas ejecutadas desde la más reciente, para entender los movimientos que originaron mi portfolio.

**Why this priority**: Aporta trazabilidad financiera y permite explicar posiciones y cambios de saldo, aunque no es necesario para calcular la foto actual.

**Independent Test**: Preparar más de 20 operaciones, abrir la pantalla y recorrer todas las páginas verificando contenido, orden, indicadores y ausencia de duplicados.

**Acceptance Scenarios**:

1. **Given** operaciones de compra y venta, **When** abre Mi portfolio, **Then** ve para cada una tipo, jugador, cantidad, precio unitario, total y fecha y hora, de la más nueva a la más vieja.
2. **Given** 25 operaciones y páginas de 20 elementos, **When** abre el historial, **Then** ve las 20 más recientes, la página actual, la cantidad total de páginas y controles para avanzar.
3. **Given** que está en la segunda página, **When** vuelve a la anterior, **Then** recupera las primeras 20 operaciones en el mismo orden, sin repeticiones ni omisiones.
4. **Given** la primera o la última página, **When** observa los controles, **Then** no puede retroceder o avanzar, respectivamente, fuera del rango válido.
5. **Given** una operación del historial, **When** activa el nombre del jugador, **Then** navega al detalle de ese jugador.

---

### User Story 4 - Continuar desde una posición al jugador (Priority: P2)

Como usuario, quiero abrir la ficha de cualquier jugador que poseo, para consultar su detalle y operar sin tener que buscarlo nuevamente en el catálogo.

**Why this priority**: Conecta el análisis del portfolio con la acción disponible en la ficha del jugador.

**Independent Test**: Activar una posición mediante mouse y teclado y comprobar que se abre la ficha correspondiente, sin confundirla con otra fila.

**Acceptance Scenarios**:

1. **Given** una posición activa, **When** el usuario activa el nombre o la acción de detalle del jugador, **Then** navega a la ficha del identificador correspondiente.
2. **Given** una tabla usada sólo con teclado, **When** el foco llega al enlace del jugador y se activa, **Then** realiza la misma navegación que con el mouse.

---

### User Story 5 - Recuperarse de carga, ausencia de datos y errores (Priority: P1)

Como usuario, quiero saber si la información está cargando, si todavía no tengo posiciones o si ocurrió un error, y poder reintentar, para no interpretar una pantalla incompleta como datos financieros reales.

**Why this priority**: La claridad frente a datos incompletos o fallidos es esencial en una pantalla financiera.

**Independent Test**: Simular carga lenta, portfolio vacío, historial vacío, falla del portfolio y falla del historial; verificar mensajes, reintentos y conservación de las secciones que sí respondieron.

**Acceptance Scenarios**:

1. **Given** una consulta en curso, **When** se abre la pantalla, **Then** se muestra un estado de carga perceptible y no se exhiben ceros como si fueran valores confirmados.
2. **Given** un usuario sin posiciones activas, **When** termina la carga, **Then** ve "Todavía no compraste tokens", una invitación a explorar jugadores y no ve gráficos vacíos ni una tabla sin explicación.
3. **Given** un usuario sin posiciones pero con operaciones de posiciones ya cerradas, **When** abre la pantalla, **Then** ve el estado vacío en posiciones y conserva el historial disponible.
4. **Given** que falla la consulta del portfolio, **When** aparece el error, **Then** se muestra un mensaje claro y un botón de reintento que vuelve a solicitar el portfolio.
5. **Given** que falla una página del historial mientras el portfolio está disponible, **When** aparece el error, **Then** el resumen, las posiciones y los gráficos permanecen visibles y el reintento afecta sólo al historial.
6. **Given** que la sesión venció, **When** una consulta recibe la respuesta correspondiente, **Then** se aplica el comportamiento existente de sesión vencida y no se muestran datos anteriores como actuales.

---

### User Story 6 - Usar Mi portfolio en distintos dispositivos y con tecnologías de asistencia (Priority: P3)

Como usuario, quiero consultar el portfolio con teclado, lector de pantalla y en una pantalla pequeña, para acceder a toda la información sin depender de un dispositivo o percepción del color específicos.

**Why this priority**: Es una calidad transversal que asegura que las historias anteriores puedan ser completadas por más personas y dispositivos.

**Independent Test**: Recorrer resumen, posiciones, gráficos, paginación y reintentos sólo con teclado y lector de pantalla, y repetir el flujo a 360 px de ancho.

**Acceptance Scenarios**:

1. **Given** un ancho de 360 px, **When** se recorre toda la pantalla, **Then** no hay desplazamiento horizontal de la página y todos los datos y controles siguen disponibles.
2. **Given** una pantalla angosta, **When** la tabla no cabe como tabla completa, **Then** cada posición y operación conserva sus etiquetas y relaciones, sin depender del orden visual para comprender los valores.
3. **Given** un usuario de teclado, **When** recorre enlaces, paginación y reintentos, **Then** todos los controles son alcanzables, tienen foco visible y siguen un orden coherente.
4. **Given** un usuario de teclado o lector de pantalla, **When** llega a cualquier gráfico, **Then** recibe su título y propósito y puede abrir mediante teclado una tabla alternativa visible con cada categoría y valor representado; la alternativa no queda disponible únicamente para lectores de pantalla.
5. **Given** un resultado positivo o negativo, **When** se percibe sin color, **Then** el signo y una etiqueta accesible comunican ganancia o pérdida.

### Edge Cases

- **Portfolio sin posiciones e historial con datos**: se muestra el mensaje "Todavía no compraste tokens" para la foto actual, pero el historial de compras y ventas anteriores permanece paginado.
- **Una sola posición**: el gráfico comparativo muestra sus dos valores y la distribución indica 100% para ese jugador.
- **Resultado exactamente cero**: se presenta como neutral, con signo o etiqueta comprensible, sin clasificarlo como ganancia ni pérdida.
- **Valor total actual cero**: si el contrato entrega posiciones válidas cuyo valor suma cero, la distribución no calcula porcentajes inválidos; muestra una explicación textual y conserva los valores absolutos.
- **Nombres repetidos**: cada enlace y dato se asocia por identificador al jugador correcto aunque dos jugadores compartan nombre.
- **Nombres extensos o muchas posiciones**: no se pierde información; las etiquetas visuales pueden abreviarse únicamente si el nombre completo y los valores están disponibles al foco, interacción o alternativa textual.
- **Cambio de página rápido**: sólo se presenta la respuesta de la página solicitada más recientemente; una respuesta anterior tardía no reemplaza el historial actual.
- **Página que deja de existir**: si el total cambia y la página actual queda fuera de rango, se vuelve a la última página válida sin mostrar un error engañoso.
- **Falla parcial**: portfolio e historial manejan carga, error y reintento de forma independiente para preservar toda información confirmada que todavía sea útil.
- **Formato numérico y temporal**: créditos y precios se muestran con 4 decimales; porcentajes de distribución pueden redondearse para lectura, sin alterar los valores base; las fechas se presentan en la zona horaria y formato coherentes con el resto de la aplicación.

## Requirements *(mandatory)*

### Functional Requirements

**Acceso y navegación**

- **FR-001**: El sistema MUST habilitar el ítem "04 Mi portfolio" del menú y dirigirlo a una ruta protegida de la pantalla personal de portfolio.
- **FR-002**: Un visitante sin sesión MUST NOT acceder al portfolio y MUST seguir el comportamiento existente de las rutas protegidas.
- **FR-003**: La pantalla MUST consultar exclusivamente el portfolio y las operaciones del usuario autenticado identificado por la sesión actual.
- **FR-004**: Cada posición y cada operación MUST ofrecer un enlace identificable al detalle del jugador correspondiente.

**Resumen y posiciones**

- **FR-005**: El resumen MUST mostrar saldo total, total invertido, valor actual total y resultado total, usando respectivamente los valores de saldo, costo, valor actual y ganancia o pérdida del portfolio.
- **FR-006**: Cuando existan créditos reservados, el resumen MUST informar además el saldo disponible y el monto reservado, sin sumarlos nuevamente al patrimonio.
- **FR-007**: La tabla de posiciones MUST mostrar por jugador: nombre, cantidad total de tokens, precio promedio de compra, cotización vigente, valor actual y ganancia o pérdida no realizada.
- **FR-008**: Los importes MUST mostrarse con 4 decimales y los tokens como cantidades enteras, sin recalcular ni redondear los valores financieros recibidos.
- **FR-009**: La ganancia, la pérdida y el resultado neutro MUST diferenciarse por signo y texto o iconografía además del color. Los colores MUST cumplir WCAG 2.2 nivel AA: contraste mínimo 4,5:1 para texto normal, 3:1 para texto grande y 3:1 para componentes de interfaz o elementos gráficos necesarios para comprender el estado.
- **FR-010**: Las posiciones MUST conservar el orden determinístico entregado por el portfolio y MUST NOT ocultar posiciones por rendimiento o tamaño.

**Visualizaciones**

- **FR-011**: La pantalla MUST incluir una visualización de rendimiento que compare, para cada posición, el precio promedio de compra con la cotización actual.
- **FR-012**: La pantalla MUST incluir una visualización de distribución en forma de dona donde cada jugador represente una proporción del valor actual total de las posiciones.
- **FR-013**: Las visualizaciones MUST derivar sus valores de las mismas posiciones mostradas en la tabla, de modo que cada valor y el total sean consistentes entre las tres representaciones.
- **FR-014**: Cada visualización MUST tener título, leyenda y acceso al nombre y valor exacto de cada dato. MUST incluir una tabla alternativa completa, visible o desplegable mediante un control operable con teclado, para tecnologías de asistencia e interacciones sin puntero; la tabla MUST NOT quedar oculta de forma permanente para usuarios videntes.
- **FR-015**: Si no hay posiciones, las visualizaciones MUST ser reemplazadas por el estado vacío; si no puede calcularse una proporción válida por un total cero, la dona MUST mostrar una explicación en vez de porcentajes inválidos.

**Historial paginado**

- **FR-016**: La pantalla MUST mostrar el historial de operaciones ejecutadas de la más nueva a la más vieja, con tipo de operación, jugador, cantidad, precio unitario, total y fecha y hora.
- **FR-017**: El historial MUST solicitar inicialmente la página 0 con 20 elementos y MUST usar los metadatos recibidos para mostrar página actual, total de páginas y total de operaciones.
- **FR-018**: El usuario MUST poder avanzar y retroceder entre páginas; los controles MUST quedar deshabilitados cuando no exista una página en esa dirección y mientras se carga la página solicitada para impedir solicitudes duplicadas.
- **FR-019**: Cambiar de página MUST actualizar sólo el historial y MUST conservar visibles el resumen, las posiciones y las visualizaciones.
- **FR-020**: El sistema MUST descartar respuestas tardías de páginas que ya no sean la selección vigente y MUST evitar que una misma operación aparezca duplicada por la navegación.
- **FR-021**: Si el historial no contiene operaciones, MUST mostrar un estado vacío específico que no oculte el portfolio disponible.

**Estados y errores**

- **FR-022**: Portfolio e historial MUST tener estados independientes de carga, contenido, vacío y error para que una falla parcial no elimine datos válidos de la otra sección.
- **FR-023**: Durante la carga inicial del portfolio, la pantalla MUST mostrar un indicador perceptible y MUST NOT presentar valores cero como datos confirmados.
- **FR-024**: Un portfolio sin posiciones MUST mostrar exactamente "Todavía no compraste tokens", una acción para explorar jugadores y MUST NOT mostrar gráficos o una tabla vacía sin explicación.
- **FR-025**: Cada error recuperable MUST mostrar un mensaje claro en español y un control de reintento que vuelva a solicitar únicamente el recurso fallido.
- **FR-026**: Una sesión vencida MUST seguir el comportamiento global existente y MUST retirar de la vista cualquier dato financiero anterior.
- **FR-027**: Si una nueva carga falla después de haber mostrado información, la pantalla MUST indicar que no pudo actualizarla y MUST NOT presentar los datos anteriores como recién confirmados.

**Responsive, accesibilidad y consistencia visual**

- **FR-028**: La pantalla MUST funcionar desde 360 px de ancho sin desplazamiento horizontal de la página; en pantallas angostas, cada posición y operación MUST conservar etiquetas explícitas para todos sus valores.
- **FR-029**: Todos los enlaces, controles de paginación y reintentos MUST ser operables con teclado, mostrar foco visible y tener nombres accesibles que expresen su acción y contexto.
- **FR-030**: Los cambios de carga, error y página del historial MUST anunciarse sin mover el foco de forma inesperada.
- **FR-031**: La pantalla MUST mantener la estética, componentes de estado, jerarquía visual, formatos y vocabulario existentes de la aplicación.

**Restricciones de alcance**

- **FR-032**: Esta feature MUST NOT crear, modificar ni cancelar órdenes, saldos, tokens, posiciones, operaciones o cotizaciones, ni modificar los contratos del backend 028.
- **FR-033**: Esta feature MUST NOT modificar archivos protegidos de Entrega 1, salvo agregar la ruta protegida de Mi portfolio en `App.jsx` y habilitar el ítem `portfolio` de `NAV_ITEMS` con su ruta en `layout/AppLayout.jsx`.

### Key Entities

- **Resumen de portfolio**: fotografía de los totales de la cuenta: saldo total, saldo reservado y disponible, costo de posiciones, valor actual, resultado y patrimonio.
- **Posición**: tenencia activa de tokens de un jugador, con cantidad, costo promedio, cotización, valor actual y resultado no realizado; se relaciona con una ficha de jugador.
- **Dato de rendimiento**: comparación por jugador entre precio promedio de compra y cotización vigente, derivada de una posición.
- **Porción de distribución**: participación de una posición en el valor actual total, identificada por jugador y expresada con valor absoluto y proporción.
- **Operación**: compra o venta ejecutada desde la perspectiva del usuario, con jugador, cantidad, precio, total y fecha de ejecución.
- **Página de operaciones**: conjunto ordenado de operaciones y metadatos para navegar por el historial completo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Al menos 9 de cada 10 usuarios de una prueba pueden identificar saldo, invertido, valor actual y resultado en menos de 10 segundos, sin ayuda.
- **SC-002**: En el 100% de los portfolios de prueba, los valores de resumen, tabla y gráficos coinciden con la misma fuente financiera a 4 decimales; la distribución suma 100% con una tolerancia visual máxima de 0,1 puntos porcentuales por redondeo.
- **SC-003**: Un usuario puede abrir la ficha de cualquier jugador desde una posición en no más de una interacción después de localizarla.
- **SC-004**: Al recorrer un historial de 1.000 operaciones, cada operación aparece exactamente una vez, en orden de la más nueva a la más vieja, y se puede llegar a cualquier página con controles cuyo estado coincide con los metadatos.
- **SC-005**: La pantalla comunica correctamente el 100% de los escenarios probados de carga, portfolio vacío, historial vacío, error de portfolio, error de historial y sesión vencida, sin confundir ausencia de datos con valores cero.
- **SC-006**: Resumen, posiciones, gráficos, historial, paginación y reintentos pueden consultarse y operarse sólo con teclado y lector de pantalla, y a 360 px de ancho sin desplazamiento horizontal de la página.
- **SC-007**: Ante una falla de sólo uno de los dos recursos, el 100% de las pruebas conserva visible y utilizable la información válida del otro recurso y permite reintentar exclusivamente el fallido.
- **SC-008**: La pantalla realiza cero cambios en saldos, tokens, órdenes, posiciones, operaciones y cotizaciones durante todos los flujos de consulta y navegación.

## Assumptions

- La sesión existente proporciona el identificador del usuario actual y aplica el manejo global de credenciales vencidas; la pantalla no permite elegir ni consultar otra cuenta.
- La feature consume sin cambios los contratos de `GET /users/{id}/portfolio` y `GET /users/{id}/transactions` definidos en la 028; la página inicial del historial es 0 y el tamaño es 20.
- "Saldo" en el resumen significa saldo total de la cuenta. Si hay reservas, se muestran como desglose junto con el saldo disponible para evitar que el usuario confunda saldo total con saldo operable.
- "Invertido" significa costo total remanente de las posiciones activas, no la suma histórica de todas las compras.
- "Resultado" significa ganancia o pérdida no realizada total: valor actual menos costo remanente. La ganancia realizada y la evolución temporal del patrimonio quedan fuera de alcance.
- El rendimiento solicitado compara precio promedio de compra y cotización actual por jugador; no representa una serie temporal ni un porcentaje de retorno.
- La distribución de activos se calcula por valor actual de cada posición, no por cantidad de tokens, porque tokens de jugadores distintos pueden tener cotizaciones diferentes.
- La tabla y los gráficos muestran la cantidad total de tokens, incluidos los reservados para venta, en coherencia con la valuación del portfolio. La cantidad disponible no es una columna requerida en esta pantalla.
- El historial permanece visible cuando no existen posiciones activas, ya que un usuario puede haber vendido todos sus tokens.
- Los estados y componentes visuales existentes se reutilizan conceptualmente para mantener consistencia; la elección de estructura interna queda para la planificación.
- Dependencias: sesión autenticada y rutas protegidas existentes; ficha del jugador; contratos y autorización del backend 028; capacidades de visualización ya disponibles en el frontend.

## Out of Scope

- Comprar, vender o cancelar órdenes directamente desde Mi portfolio.
- Mostrar órdenes abiertas o reservas por jugador como columnas adicionales.
- Filtros, búsqueda, ordenamiento manual, cambio de tamaño de página o exportación del historial.
- Rentabilidad realizada, rentabilidad porcentual, evolución histórica del portfolio o comparación contra índices.
- Modificaciones en endpoints, cálculos o modelos del backend 028.
