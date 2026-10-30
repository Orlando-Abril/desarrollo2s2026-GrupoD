# Feature Specification: Portfolio e historial de transacciones

**Feature Branch**: `feature/portfolio`

**Created**: 2026-10-29

**Status**: Draft

**Input**: User description: "SPECIFY_FEATURE_DIRECTORY=specs/028-portfolio. Endpoints GET /users/{id}/portfolio y GET /users/{id}/transactions; portfolio por jugador con cantidad, precio promedio de compra, valor actual y ganancia o pérdida, saldo y totales; historial paginado; acceso propio o de ADMIN; GET /auth/me; documentación OpenAPI."

## Contexto y límites

El mercado ya modela usuarios, jugadores, tenencias y operaciones ejecutadas. Cada jugador tiene 100 tokens emitidos inicialmente al superusuario. Las órdenes de compra y venta se procesan mediante un libro LIMIT/MARKET; cada calce genera una operación ejecutada inmutable con comprador, vendedor, jugador, cantidad, precio, total y fecha. La cotización vigente para valuar una posición es `Player.marketValue`, con 4 decimales, actualizada por el recálculo de cotizaciones.

Esta feature expone las lecturas necesarias para que un usuario consulte su patrimonio y actividad, y para que un administrador consulte los de cualquier cuenta. También permite que el frontend conozca la identidad, rol y saldo de la sesión mediante `GET /auth/me`, ya que el inicio de sesión sólo devuelve la credencial de sesión.

Quedan fuera de alcance la creación, el calce y la cancelación de órdenes; el movimiento de saldos o tokens; el reprecio de órdenes del superusuario; y cualquier modificación de operaciones o auditorías existentes. Para Entrega 1 está autorizado agregar `GET /me` a `AuthController` sin cambiar sus operaciones existentes.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consultar el portfolio valuado (Priority: P1)

Como usuario autenticado, quiero ver mis posiciones por jugador y sus resultados a la cotización vigente, junto con mi saldo y los totales de la cuenta, para conocer mi patrimonio actual.

**Why this priority**: Es la vista principal de la inversión del usuario y concentra la información necesaria para decidir futuras operaciones.

**Independent Test**: Preparar una cuenta con compras y ventas de varios jugadores, consultar su portfolio y comparar cantidades, costos promedio, valores actuales, resultados y totales contra un cálculo manual.

**Acceptance Scenarios**:

1. **Given** un usuario que compró 10 tokens de un jugador a 1.0000 y 5 a 1.2000, **When** consulta su portfolio, **Then** la posición muestra 15 tokens y un precio promedio de compra de 1.0667.
2. **Given** esa posición y un precio de mercado vigente de 1.3000, **When** consulta su portfolio, **Then** muestra un valor actual de 19.5000, un costo de 16.0000 y una ganancia de 3.5000.
3. **Given** que el usuario vende 6 de esos 15 tokens, **When** consulta nuevamente, **Then** conserva el precio promedio de compra de 1.0667 para los 9 tokens restantes; la venta no recalcula el promedio.
4. **Given** posiciones en varios jugadores y un saldo de créditos, **When** consulta el portfolio, **Then** los totales son la suma exacta del costo, valor actual y ganancia o pérdida de las posiciones, y el patrimonio total es saldo más valor actual total.
5. **Given** un usuario sin tokens, **When** consulta su portfolio, **Then** recibe saldo y patrimonio total igual al saldo, una lista de posiciones vacía y totales de posiciones en cero.
6. **Given** los 100 tokens iniciales de un jugador emitidos al superusuario, **When** se consulta su portfolio antes o después de ventas parciales, **Then** esos tokens tienen costo base y precio promedio de 1.0000 mientras quede una cantidad positiva.

---

### User Story 2 - Consultar el historial de operaciones (Priority: P1)

Como usuario autenticado, quiero recorrer mis compras y ventas ejecutadas de la más nueva a la más vieja, para revisar qué negocié, cuándo y a qué precio.

**Why this priority**: El historial explica los cambios del portfolio y da trazabilidad de la actividad financiera del usuario.

**Independent Test**: Preparar más operaciones que el tamaño de una página, con el usuario alternando como comprador y vendedor, y verificar contenido, orden, paginación y metadatos.

**Acceptance Scenarios**:

1. **Given** un usuario que fue comprador en una operación y vendedor en otra, **When** consulta su historial, **Then** ambas aparecen con tipo COMPRA o VENTA, jugador, cantidad, precio, total y fecha correspondientes.
2. **Given** varias operaciones en fechas diferentes, **When** consulta una página, **Then** aparecen de la más nueva a la más vieja.
3. **Given** 25 operaciones y el tamaño por defecto, **When** consulta la primera página, **Then** recibe las 20 más recientes y metadatos que indican 25 elementos, 2 páginas, página 0 y tamaño 20.
4. **Given** ese mismo historial, **When** consulta la página 1, **Then** recibe las 5 operaciones restantes sin repetir ni omitir ninguna.
5. **Given** un usuario sin operaciones, **When** consulta su historial, **Then** recibe una página vacía con cero elementos y metadatos coherentes.

---

### User Story 3 - Respetar la privacidad por cuenta (Priority: P1)

Como usuario, quiero que sólo yo y los administradores puedan consultar mi portfolio e historial, para mantener privada mi información financiera.

**Why this priority**: El saldo, las posiciones y las operaciones son información financiera sensible; el control de acceso es inseparable de las consultas.

**Independent Test**: Ejecutar ambas consultas sin credenciales, como dueño, como otro usuario y como administrador, incluyendo un identificador inexistente.

**Acceptance Scenarios**:

1. **Given** una solicitud sin credenciales válidas, **When** intenta consultar un portfolio o historial, **Then** recibe 401 y no se revela ningún dato.
2. **Given** un usuario autenticado, **When** consulta su propio identificador, **Then** recibe la información solicitada.
3. **Given** un usuario con rol USER, **When** consulta el identificador de otra cuenta, exista o no, **Then** recibe 403 y no se revela si esa cuenta existe.
4. **Given** un administrador autenticado, **When** consulta cualquier cuenta existente, **Then** recibe su portfolio o historial.
5. **Given** un administrador autenticado, **When** consulta un identificador inexistente, **Then** recibe 404.

---

### User Story 4 - Conocer la cuenta autenticada (Priority: P2)

Como frontend autenticado, quiero obtener los datos básicos de la sesión actual, incluido el identificador del usuario, para poder llamar a las consultas de portfolio e historial correctas.

**Why this priority**: El inicio de sesión sólo entrega la credencial; sin esta consulta el frontend no sabe qué identificador usar ni puede mostrar la identidad y saldo actuales.

**Independent Test**: Autenticar una cuenta, consultar `GET /auth/me` y comparar todos los campos con la cuenta; repetir sin credenciales.

**Acceptance Scenarios**:

1. **Given** un usuario autenticado, **When** consulta su identidad actual, **Then** recibe exactamente su id, username, email, rol y saldo vigente.
2. **Given** un administrador autenticado, **When** consulta su identidad actual, **Then** recibe esos mismos campos y el rol ADMIN.
3. **Given** una solicitud sin credenciales válidas, **When** consulta la identidad actual, **Then** recibe 401 y ningún dato de cuenta.
4. **Given** las operaciones de autenticación existentes, **When** se agrega esta consulta, **Then** registro y login conservan sus contratos y comportamiento anteriores.

---

### User Story 5 - Consultar contratos en OpenAPI (Priority: P3)

Como persona integradora, quiero encontrar documentadas las tres consultas con sus respuestas, parámetros, paginación, seguridad y errores, para consumirlas sin leer el código fuente.

**Why this priority**: La documentación es obligatoria y evita interpretaciones incompatibles entre backend y frontend.

**Independent Test**: Revisar la documentación publicada y comprobar que permite construir pedidos válidos e interpretar todas las respuestas de portfolio, transacciones e identidad actual.

**Acceptance Scenarios**:

1. **Given** la documentación disponible, **When** se consultan los contratos de portfolio y transacciones, **Then** están descriptos sus parámetros, esquemas de respuesta, reglas de autorización y respuestas 200, 400 cuando corresponda, 401, 403 y 404.
2. **Given** la documentación disponible, **When** se consulta el contrato de identidad actual, **Then** están descriptos su esquema de respuesta, seguridad y respuestas 200 y 401.

### Edge Cases

- **Posición totalmente vendida**: no aparece como posición activa. Su historial permanece disponible. Una compra posterior inicia un nuevo precio promedio con las nuevas compras, sin arrastrar el costo de la posición cerrada.
- **Venta parcial**: reduce cantidad y costo remanente en la misma proporción; no cambia el precio promedio unitario de lo que queda.
- **Tokens emitidos al superusuario**: su costo inicial es 1.0000 por token, que es el valor base definido para la emisión; no requieren una operación de compra ficticia en el historial.
- **Tokens reservados en una orden de venta LIMIT**: siguen siendo propiedad del usuario hasta el calce; se incluyen en la cantidad y valuación del portfolio.
- **Créditos reservados por órdenes de compra LIMIT**: el campo `saldo` conserva el saldo total de la cuenta; la respuesta también informa créditos reservados y saldo disponible para evitar que el total aparente sea operable por completo.
- **Precios con más precisión**: precio promedio, costo, valor actual, ganancia/pérdida, saldo y totales se expresan con 4 decimales; no hay redondeos intermedios. El promedio se redondea a 4 decimales sólo para presentarlo, mientras los totales se calculan desde el costo acumulado exacto de la posición.
- **Precio de mercado ausente**: una posición sin cotización vigente no puede valuarse; la consulta falla de forma explícita en vez de inventar un valor. Se espera que la inicialización del catálogo garantice 1.0000 como mínimo.
- **Calce concurrente durante la lectura**: portfolio e historial deben observar una única fotografía confirmada; la respuesta puede reflejar el estado completo anterior o posterior al calce, pero nunca una mezcla de ambos.
- **Operaciones con la misma fecha y hora**: se ordenan además por identificador descendente para producir un historial estable entre páginas.
- **Página posterior a la última**: devuelve contenido vacío y conserva los metadatos reales; no se considera un error.
- **Parámetros de paginación inválidos**: página negativa, tamaño menor que 1 o mayor que 100 producen 400 sin consultar el historial.
- **Cuenta eliminada o identidad de credencial inválida**: se trata como credencial no válida y no expone datos personales.

## Requirements *(mandatory)*

### Functional Requirements

**Portfolio**

- **FR-001**: El sistema MUST exponer `GET /users/{id}/portfolio` para devolver el portfolio actual de la cuenta identificada.
- **FR-002**: La respuesta MUST incluir el id del usuario, su saldo total, créditos reservados por órdenes de compra LIMIT abiertas o parcialmente ejecutadas, saldo disponible, posiciones y totales.
- **FR-003**: Cada posición activa MUST incluir jugador (id y nombre), cantidad total de tokens, cantidad reservada para venta, cantidad disponible, precio promedio de compra, costo remanente, precio de mercado vigente, valor actual y ganancia o pérdida no realizada.
- **FR-004**: La cantidad de una posición MUST incluir todos los tokens que todavía pertenecen al usuario, incluidos los reservados por órdenes de venta abiertas. La cantidad disponible MUST ser cantidad total menos cantidad reservada.
- **FR-005**: El precio promedio de compra MUST ser el costo acumulado de los tokens aún poseídos dividido por su cantidad. Cada compra ejecutada MUST incorporar cantidad por precio al costo; una venta parcial MUST reducir el costo proporcionalmente y MUST NOT cambiar el promedio unitario restante.
- **FR-006**: Cuando una posición llega a cero, MUST dejar de aparecer y su costo remanente MUST quedar en cero. Una compra posterior MUST iniciar un nuevo promedio únicamente con las compras de la nueva posición.
- **FR-007**: El valor actual de cada posición MUST ser cantidad total por `marketValue` vigente del jugador. La ganancia o pérdida no realizada MUST ser valor actual menos costo remanente.
- **FR-008**: Los totales MUST incluir costo total de posiciones, valor actual total, ganancia o pérdida total y patrimonio total. Los tres primeros MUST ser las sumas exactas de las posiciones y patrimonio total MUST ser saldo total más valor actual total.
- **FR-009**: Saldo disponible MUST ser saldo total menos créditos reservados. La valuación del patrimonio MUST usar el saldo total para no descontar dos veces créditos que siguen perteneciendo al usuario.
- **FR-010**: Precio promedio, costo, cotización, valor actual, resultados y saldos MUST presentarse con 4 decimales. Los cálculos MUST evitar redondeos intermedios que alteren los totales.
- **FR-011**: Las posiciones MUST ordenarse por nombre de jugador ascendente y, ante empate, por id ascendente, para ofrecer una respuesta determinística.
- **FR-012**: Un usuario sin posiciones MUST recibir una lista vacía, totales de posiciones en cero y patrimonio total igual a su saldo total.
- **FR-013**: Si una posición no tiene precio de mercado vigente, el historial reconstruido no coincide con la tenencia o una venta supera la posición reconstruida, la consulta MUST responder 500 con código `portfolio_inconsistent` y mensaje público genérico; MUST NOT sustituir datos, devolver resultados parciales ni exponer detalles internos.

**Historial de transacciones**

- **FR-014**: El sistema MUST exponer `GET /users/{id}/transactions` para devolver todas las operaciones ejecutadas en las que la cuenta fue compradora o vendedora.
- **FR-015**: Cada elemento MUST representar un calce ejecutado individual e incluir id de operación, tipo desde la perspectiva del usuario (`COMPRA` o `VENTA`), jugador (id y nombre), cantidad, precio unitario, total y fecha y hora de ejecución.
- **FR-016**: El historial MUST estar paginado y ordenado por fecha de ejecución descendente y, ante empate, por id de operación descendente.
- **FR-017**: La consulta MUST aceptar `page` (base cero, valor por defecto 0) y `size` (valor por defecto 20, rango permitido 1 a 100). Valores fuera de rango MUST producir 400.
- **FR-018**: La respuesta paginada MUST incluir contenido, número de página, tamaño solicitado, cantidad de elementos de la página, cantidad total de elementos, cantidad total de páginas, e indicadores de primera y última página.
- **FR-019**: Una página posterior a la última MUST devolver contenido vacío y metadatos consistentes. Un usuario sin operaciones MUST recibir una página vacía con total de elementos y páginas igual a cero.
- **FR-020**: El historial MUST derivarse exclusivamente de operaciones ejecutadas inmutables; órdenes abiertas, canceladas o partes todavía no calzadas MUST NOT aparecer.

**Autenticación y autorización**

- **FR-021**: Las consultas de portfolio y transacciones MUST requerir una credencial válida; sin ella MUST responder 401.
- **FR-022**: Un usuario con rol USER MUST poder consultar sólo su propio id. Al intentar cualquier otro id, exista o no, MUST recibir 403 para no revelar la existencia de cuentas ajenas.
- **FR-023**: Un usuario con rol ADMIN MUST poder consultar cualquier cuenta. Si el id solicitado no existe, MUST recibir 404.
- **FR-024**: Las respuestas 401, 403, 404 y 400 MUST usar el formato de error común del sistema y MUST NOT incluir datos financieros ni personales.
- **FR-025**: El sistema MUST exponer `GET /auth/me`, autenticado, y devolver id, username, email, rol y saldo vigente de la cuenta representada por la credencial.
- **FR-026**: `GET /auth/me` sin credenciales válidas MUST responder 401. La incorporación de esta operación MUST NOT cambiar los contratos ni el comportamiento de las operaciones de autenticación existentes.

**Documentación y alcance**

- **FR-027**: Las tres consultas MUST estar documentadas en OpenAPI con parámetros, esquemas completos, ejemplos, esquema de seguridad y todos sus códigos de respuesta.
- **FR-028**: La documentación de transacciones MUST indicar los valores por defecto y límites de paginación y que cada elemento representa un calce, no una orden completa.
- **FR-029**: Esta feature MUST NOT crear, calzar, cancelar ni modificar órdenes; MUST NOT mover saldos o tokens; y MUST NOT modificar operaciones ejecutadas ni registros de auditoría.
- **FR-030**: Las consultas MUST usar únicamente datos persistidos del mercado y MUST NOT depender de fuentes externas ni disparar recálculos de cotización.
- **FR-031**: Agregar `GET /me` a `AuthController` es el único cambio autorizado en ese controller para esta entrega; las operaciones existentes MUST permanecer sin cambios.
- **FR-032**: Para calcular el portfolio del superusuario, cada token recibido por emisión MUST incorporarse con costo base de 1.0000. La emisión MUST NOT aparecer como una compra en el historial de transacciones.
- **FR-033**: Portfolio e historial MUST leer sus datos dentro de una fotografía consistente de los datos confirmados al comenzar la consulta. Un calce concurrente MUST NOT producir una respuesta que mezcle saldo, reservas, tenencias u operaciones de momentos distintos.
- **FR-034**: La cantidad de consultas de datos de portfolio e historial MUST ser constante respecto de la cantidad de posiciones y operaciones devueltas. “Consulta agregada” significa consultas masivas/proyectadas, no necesariamente una única sentencia SQL. El presupuesto constante MAY diferir entre dueño, ADMIN y API key de servicio por la resolución del actor y del usuario objetivo, pero MUST NOT crecer por posición, trade ni elemento de página.
- **FR-035**: Una API key de servicio sin dueño, autenticada con autoridad ADMIN, MUST poder consultar portfolio e historial de cualquier cuenta con las mismas reglas de existencia que un ADMIN.
- **FR-036**: Una API key de servicio sin dueño MUST recibir 401 con código `authenticated_user_not_found` en `GET /auth/me`, porque no representa una cuenta personal.

### Key Entities

- **Usuario**: cuenta autenticable con id, username, email, rol y saldo. Es dueño de posiciones y participa como comprador o vendedor en operaciones.
- **Posición de portfolio**: vista calculada de los tokens que un usuario aún posee de un jugador, su costo remanente, precio promedio, valuación vigente y resultado no realizado. No representa una nueva operación financiera.
- **Tenencia**: fuente de la cantidad total, reservada y disponible de tokens del usuario por jugador.
- **Operación ejecutada**: calce financiero inmutable entre comprador y vendedor. Es la fuente del historial y de la reconstrucción del costo promedio.
- **Jugador**: activo negociado cuyo `marketValue` vigente se usa para valuar las posiciones.
- **Página de transacciones**: subconjunto ordenado del historial junto con metadatos que permiten navegarlo sin duplicaciones ni omisiones.
- **Identidad actual**: proyección de la cuenta representada por la credencial, con los datos que necesita el frontend para identificar y mostrar la sesión.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En el 100% de los escenarios de prueba con compras a distintos precios, ventas parciales, cierre y reapertura de posición, la cantidad, el promedio, el costo, el valor actual y la ganancia o pérdida coinciden con el cálculo manual a 4 decimales.
- **SC-002**: Para cualquier portfolio, el 100% de los totales satisface costo total = suma de costos, valor actual total = suma de valores actuales, resultado total = valor actual total menos costo total y patrimonio total = saldo más valor actual total.
- **SC-003**: Al recorrer todas las páginas de un historial de 1.000 operaciones, cada operación aparece exactamente una vez y en orden de la más nueva a la más vieja, incluidos empates de fecha resueltos por id descendente.
- **SC-004**: El 100% de las consultas sin credenciales recibe 401; el 100% de los intentos de un USER sobre otro id recibe 403; y el 100% de los ids inexistentes consultados por ADMIN recibe 404, sin exposición de datos.
- **SC-005**: Cada una de 5 ejecuciones medidas de portfolio e historial sobre una cuenta con 10.000 operaciones, después de una ejecución de calentamiento y usando el perfil de integración PostgreSQL del proyecto en el entorno CI, completa en menos de 2 segundos y mantiene una cantidad constante de sentencias respecto del número de posiciones.
- **SC-006**: Un frontend autenticado obtiene id, username, email, rol y saldo vigentes en una única consulta, y puede usar el id recibido para consultar el portfolio y el historial propios sin información adicional del login.
- **SC-007**: Una persona integradora puede identificar en OpenAPI, sin leer código fuente, el 100% de los campos, parámetros, límites de paginación, reglas de acceso y respuestas posibles de las tres consultas.
- **SC-008**: Las consultas realizan cero cambios en saldos, tokens, órdenes, operaciones ejecutadas y auditorías, y cero llamadas a fuentes externas.

## Assumptions

- Las entidades de mercado y sus invariantes provienen de `specs/026-mercado-modelo`; esta feature las consume y no redefine su persistencia.
- Cada elemento del historial corresponde a una operación ejecutada individual. Una orden calzada en varias partes produce varios elementos, porque cada parte puede tener contraparte, precio y fecha distintos.
- El costo promedio se reconstruye cronológicamente a partir de compras y ventas ejecutadas. Se asume que el historial persistido es completo y consistente con la tenencia actual.
- La emisión inicial del superusuario es la única fuente de posición que no proviene de una compra ejecutada; se incorpora al costo a 1.0000 conforme al valor inicial definido por el mercado.
- La ganancia o pérdida solicitada es no realizada para los tokens todavía poseídos. La ganancia realizada de las ventas y métricas fiscales quedan fuera de alcance.
- Los tokens reservados siguen en el portfolio y los créditos reservados siguen en el saldo total hasta que exista un calce; por eso se informan también cantidades y saldo disponibles.
- El saldo se presenta con 4 decimales aunque su almacenamiento actual pueda tener menor escala; esta feature no cambia `User` ni mueve créditos.
- Los ids de usuario y jugador siguen el formato y validación ya usados por el sistema. Un id sintácticamente inválido recibe 400 mediante el manejo común de pedidos inválidos.
- La autenticación reutiliza la infraestructura existente y el rol vigente de la cuenta. Esta feature no cambia credenciales, roles ni reglas globales de seguridad.
- Las API keys con dueño representan a su usuario. Las API keys de servicio sin dueño conservan autoridad ADMIN para `/users/{id}/...`, pero no tienen una identidad válida para `/auth/me`.
- Portfolio e historial usan aislamiento de lectura repetible para conservar una fotografía coherente de los datos confirmados al comenzar; órdenes o calces confirmados después se reflejan en la siguiente consulta.

## Out of Scope

- `POST /orders/buy`, `POST /orders/sell`, `DELETE /orders/{id}`, `GET /orders/book` y el motor de calce.
- Creación o reprecio de las órdenes de venta del superusuario después de un recálculo.
- Ganancia realizada, rentabilidad porcentual, reportes fiscales, exportaciones y gráficos históricos del portfolio.
- Filtros del historial por jugador, tipo o rango de fechas; la única navegación incluida es la paginación.
- Cambios en registro, login, emisión de credenciales, modelo de usuario o escala persistida del saldo.
