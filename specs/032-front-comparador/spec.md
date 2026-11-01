# Feature Specification: Comparador de estrategias en el frontend

**Feature Branch**: `feature/front-comparador`

**Created**: 2026-10-31

**Status**: Draft

**Input**: User description: "SPECIFY_FEATURE_DIRECTORY=specs/032-front-comparador. Selector «Ver con: Mercado actual / Momentum» en Catálogo, Detalle y Portfolio; oportunidades con «Operar»; cambio confirmado de estrategia activa para ADMIN desde Ranking. Autorizado (Entrega 1): selector en layout/Header.jsx y prop opcional en Figurita sin cambiar su aspecto por defecto."

## Contexto y alcance

La comparación permite ver el precio de mercado persistido y la cotización MOMENTUM sin cambiar el precio vigente de operación. «Mercado actual» siempre representa `marketValue`, independientemente de si lo generó OFFICIAL o MOMENTUM. Los historiales de estrategias se identifican por sus códigos reales OFFICIAL y MOMENTUM. La estrategia activa global sólo puede cambiarla un ADMIN desde Ranking; la elección configura el próximo recálculo y, hasta entonces, el mercado puede seguir reflejando la estrategia anterior. «Ver con» es una elección personal de presentación, independiente del cambio global.

Esta feature consume las capacidades de la 031 (comparación, oportunidades y estrategia activa), la 028 (identidad y portfolio), la 029 (panel de compra y venta) y la 025 (recálculo). Cubre Catálogo/Álbum, Detalle, Portfolio, oportunidades y el control administrativo de Ranking. La autorización para tocar archivos de Entrega 1 se limita al selector en `layout/Header.jsx` y a una prop opcional de cotización alternativa en Figurita, con aspecto idéntico por defecto.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Comparar precios en Catálogo y Detalle (Priority: P1)

Como usuario quiero elegir «Ver con: Mercado actual / Momentum» en el Álbum y el Detalle, para conocer la cotización Momentum de cada jugador y comparar la evolución de las estrategias identificadas por sus códigos reales.

**Why this priority**: Es la base visual para interpretar oportunidades sin confundir el precio comparativo con el de operación.

**Independent Test**: Con cotizaciones conocidas de ambas estrategias, cambiar el selector en Catálogo y Detalle y comprobar importes, diferencias, gráfico y precio de operación.

**Acceptance Scenarios**:

1. **Given** el selector en Mercado actual, **When** se abre Catálogo o Detalle, **Then** se conserva la presentación habitual del precio de mercado persistido y del gráfico de una serie, sin dato alternativo en la figurita, aunque `marketValue` provenga de MOMENTUM.
2. **Given** OFFICIAL activa, precio de mercado 1.0000 y última MOMENTUM 1.2000, **When** se elige Momentum en el Álbum, **Then** la figurita muestra cotización Momentum 1.2000 y diferencia +0.2000 (+20.00%) respecto del precio de mercado 1.0000, identificado como precio para operar.
3. **Given** el modo Momentum y dos series históricas, **When** se abre Detalle, **Then** el gráfico superpone las curvas OFFICIAL y MOMENTUM con nombres distinguibles y el panel de operación conserva explícito el precio vigente de mercado.
4. **Given** que el usuario cambia de Catálogo a Detalle y Portfolio, **When** carga cada vista en la misma sesión, **Then** la elección «Ver con» se conserva.
5. **Given** MOMENTUM configurada como activa pero `marketStrategy=OFFICIAL` y un recálculo pendiente, **When** se elige Mercado actual, **Then** se ve el precio de mercado todavía fijado por OFFICIAL, se indica que MOMENTUM se aplicará después del recálculo y las curvas históricas conservan los nombres OFFICIAL y MOMENTUM.

---

### User Story 2 - Valuar el Portfolio con Momentum (Priority: P1)

Como titular quiero ver el valor y el resultado de mis posiciones con Momentum, junto a las cifras vigentes, para evaluar su impacto sin alterar saldo ni tenencias.

**Why this priority**: Expresa el efecto patrimonial de la comparación y separa resultados hipotéticos de resultados de mercado.

**Independent Test**: Con dos posiciones de costos y cotizaciones conocidos, alternar el selector y verificar valores por posición y totales.

**Acceptance Scenarios**:

1. **Given** 10 tokens, costo remanente 9.0000, precio de mercado 1.0000 y MOMENTUM 1.2000, **When** se elige Momentum, **Then** aparecen valor Momentum 12.0000 y resultado Momentum +3.0000, separados del valor vigente 10.0000 y resultado vigente +1.0000.
2. **Given** varias posiciones con cotización Momentum, **When** se elige Momentum, **Then** valor, resultado y patrimonio comparativos totales coinciden con las posiciones y el saldo.
3. **Given** el modo Mercado actual, **When** se abre Portfolio, **Then** se conserva la presentación habitual de la valuación vigente calculada con el precio de mercado persistido, cualquiera sea su estrategia de origen.
4. **Given** una posición sin cotización Momentum, **When** se consulta en ese modo, **Then** se indica «Sin cotización Momentum», los totales comparativos figuran como no disponibles y los importes vigentes siguen visibles.

---

### User Story 3 - Encontrar oportunidades y operar (Priority: P1)

Como usuario quiero ver jugadores subvaluados y sobrevaluados y abrir su panel de compra o venta con «Operar», para pasar del análisis a la acción con el precio correcto.

**Why this priority**: Conecta la comparación con el mercado existente.

**Independent Test**: Preparar una oportunidad de cada tipo, aplicar filtros y abrir «Operar» para comprobar jugador, panel y precio.

**Acceptance Scenarios**:

1. **Given** oportunidades disponibles, **When** se abre la sección, **Then** aparecen grupos separados de subvaluados y sobrevaluados con jugador, precio de mercado, cotización alternativa, diferencia absoluta y porcentual y etiquetas de estrategia.
2. **Given** filtros de liga y posición, **When** se aplican, **Then** ambos grupos se actualizan y conservan el orden de mayor diferencia porcentual absoluta informado por la capacidad de oportunidades.
3. **Given** una oportunidad, **When** se pulsa «Operar», **Then** se abre el Detalle del jugador correcto con el panel de compra y venta visible y el precio de mercado identificado.
4. **Given** que no hay oportunidades para los filtros, **When** termina la consulta, **Then** se muestra un estado vacío comprensible y se pueden ajustar los filtros.

---

### User Story 4 - Cambiar la estrategia activa desde Ranking (Priority: P1)

Como ADMIN quiero consultar y cambiar la estrategia activa junto a «Recalcular cotizaciones», confirmar el cambio y decidir cuándo recalcular, para controlar qué estrategia fijará los próximos precios.

**Why this priority**: La acción es global y su efecto diferido necesita una explicación clara.

**Independent Test**: Ingresar como ADMIN, cancelar un cambio, confirmar otro y verificar estrategia, notificación y acción de recálculo; repetir como usuario común.

**Acceptance Scenarios**:

1. **Given** un ADMIN en Ranking, **When** carga la pantalla, **Then** ve «Estrategia activa» junto a «Recalcular cotizaciones», con el valor actual y la alternativa.
2. **Given** un cambio pendiente, **When** cancela la confirmación, **Then** la estrategia activa no cambia.
3. **Given** un cambio confirmado y aceptado, **When** termina, **Then** el control refleja la nueva estrategia y un Toast aclara que el precio de mercado cambiará con el próximo recálculo y ofrece usar «Recalcular cotizaciones» en ese momento.
4. **Given** ese PUT exitoso, **When** se actualiza Ranking, **Then** se vuelven a consultar la configuración de estrategias y las oportunidades, porque la alternativa puede haber cambiado, aunque todavía no se hayan recalculado los precios.
5. **Given** un cambio aceptado sin recálculo posterior, **When** se consulta el precio para operar, **Then** sigue mostrándose el precio vigente con una indicación de que la nueva estrategia todavía no lo fijó.
6. **Given** un usuario común, **When** abre Ranking, **Then** no ve ni puede activar «Estrategia activa».

---

### User Story 5 - Usar la comparación con asistencia (Priority: P2)

Como usuario quiero poder comparar y operar con teclado, lector de pantalla y pantalla pequeña, para completar las mismas tareas sin depender del color.

**Why this priority**: Las diferencias y el cambio global deben poder comprenderse en todos los contextos.

**Independent Test**: Recorrer selector, gráfico, oportunidades, «Operar», confirmación y avisos con teclado y lector de pantalla; repetir a 360 px.

**Acceptance Scenarios**:

1. **Given** una pantalla de 360 px, **When** se recorren las vistas y Ranking, **Then** los controles y datos siguen utilizables sin desplazamiento horizontal global.
2. **Given** uso exclusivo de teclado, **When** se cambia de modo, se abre «Operar» o se confirma un cambio, **Then** los controles son alcanzables y activables con foco visible.
3. **Given** un lector de pantalla, **When** se consultan diferencias, curvas, errores y éxito, **Then** se anuncian con texto y nombres suficientes, sin depender sólo del color.

### Edge Cases

- Si falta una cotización alternativa, Catálogo y Detalle lo indican sin inventar diferencias ni sustituir el precio de mercado.
- Si una cotización tiene estado `NO_STATS` o `INSUFFICIENT_HISTORY`, su valor conservado aparece con ese estado en Catálogo, Detalle y Portfolio; oportunidades conserva la exclusión definida en la 031.
- Si el precio de mercado no permite calcular un porcentaje, la diferencia absoluta disponible se muestra y el porcentaje figura como no disponible.
- Tras un cambio exitoso de estrategia activa se vuelven a consultar configuración y oportunidades de inmediato; tras un recálculo se vuelven a consultar configuración, oportunidades y datos de mercado afectados para no mezclar precios de momentos distintos.
- Si falla una consulta comparativa, se informa el problema y se conserva la presentación de mercado con opción de reintento, sin atribuir a OFFICIAL o MOMENTUM un precio de origen desconocido.
- Un 403 al cambiar estrategia informa falta de permiso y oculta el control para esa identidad; un 400 permite corregir una selección inválida; una falla de red exige volver a consultar la estrategia activa antes de presentar un valor definitivo.
- Una doble confirmación no envía dos cambios. Una respuesta tardía después de salir de Ranking no altera otra pantalla.
- Si MOMENTUM es la activa o el mercado aún refleja OFFICIAL tras el cambio, se etiquetan por separado modo elegido, estrategia activa y estrategia que fijó el precio vigente; una cotización igual al mercado no sugiere ganancia potencial.

## Requirements *(mandatory)*

### Functional Requirements

**Selector y cotizaciones**

- **FR-001**: Catálogo, Detalle y Portfolio MUST mostrar «Ver con: Mercado actual / Momentum» de forma consistente y conservar la elección al navegar durante la sesión. El valor inicial MUST ser Mercado actual.
- **FR-002**: Cambiar «Ver con» MUST modificar sólo la presentación personal y MUST NOT cambiar la estrategia activa, el precio de mercado, el saldo, las posiciones ni las órdenes.
- **FR-003**: En modo Mercado actual, las tres vistas MUST mantener la presentación habitual usando el precio de mercado persistido, aun si su estrategia de origen es MOMENTUM; el gráfico habitual MUST identificar con su código real la estrategia histórica seleccionada y Figurita sin cotización alternativa MUST conservar su aspecto por defecto.
- **FR-004**: En modo Momentum, cada figurita del Álbum MUST presentar la última cotización MOMENTUM, su estado si no está calculada, y la diferencia absoluta y porcentual frente al precio de mercado vigente, con etiquetas claras.
- **FR-005**: En modo Momentum, Detalle MUST superponer el historial OFFICIAL y MOMENTUM en un gráfico con leyenda, valores accesibles y códigos reales de estrategia; el precio de mercado vigente MUST seguir visible.
- **FR-006**: En modo Momentum, Portfolio MUST mostrar por posición y en los totales valor y resultado con MOMENTUM junto a los importes vigentes. Si faltan cotizaciones, MUST identificar las posiciones y marcar los totales comparativos como no disponibles.
- **FR-007**: Las diferencias porcentuales MUST usar el precio de mercado vigente como base; diferencias positivas y negativas MUST tener signo o etiqueta textual; importes MUST conservar cuatro decimales y porcentajes dos.
- **FR-008**: Las vistas MUST distinguir «Mercado actual» (precio persistido), «Momentum» (cotización de esa estrategia), la estrategia activa configurada y la estrategia que fijó el precio de mercado vigente. Si hay recálculo pendiente, MUST indicar que la activa aún no fijó el precio usado para operar.

**Oportunidades y operación**

- **FR-009**: La sección de oportunidades MUST separar subvaluados y sobrevaluados y mostrar jugador, precio de mercado, cotización alternativa, diferencias absoluta y porcentual y etiquetas de estrategia; MUST permitir filtrar por liga y posición.
- **FR-010**: Cada oportunidad MUST ofrecer «Operar» y llevar al Detalle del jugador correspondiente con el panel de compra y venta de la 029 visible, conservando la selección «Ver con».
- **FR-011**: Al comparar en Detalle o entrar al panel desde una oportunidad, MUST indicarse que el precio para operar es el de mercado vigente, no la cotización exploratoria. Estimación y validación de órdenes MUST seguir la 029.
- **FR-012**: Las listas vacías, carga y fallos de oportunidades MUST tener estados comprensibles y opción de reintento cuando corresponda.

**Administración desde Ranking**

- **FR-013**: Ranking MUST mostrar «Estrategia activa» junto a «Recalcular cotizaciones» sólo a quien tenga rol ADMIN según la identidad autenticada y mostrar el valor activo informado por el sistema, sin deducirlo de «Ver con».
- **FR-014**: Elegir otra estrategia MUST solicitar confirmación del cambio global y del efecto diferido en precios. Cancelar MUST dejar intacto el valor activo.
- **FR-015**: Confirmar MUST enviar como máximo un cambio, bloquear repeticiones durante el proceso y actualizar el valor visible al obtener una respuesta confirmada.
- **FR-016**: El éxito MUST mostrar un Toast que explique que el precio de mercado cambia con el próximo recálculo y ofrezca «Recalcular cotizaciones» de la 025 en esa pantalla, sin recalcular automáticamente.
- **FR-017**: Un 403 MUST comunicar falta de permiso y ocultar el control para esa identidad; un 400 MUST explicar que la estrategia no es válida y permitir corregirla; una falla de red MUST informar que el resultado no está confirmado y volver a consultar el valor activo. Ningún error MUST presentar un cambio como confirmado.
- **FR-018**: Un usuario común MUST NOT ver el control administrativo, aunque podrá usar la comparación y oportunidades según sus permisos habituales.

**Calidad y límites**

- **FR-019**: Selector, gráfico, oportunidades, navegación, confirmación y avisos MUST ser utilizables con teclado y lector de pantalla, con foco visible, nombres accesibles y distinciones independientes del color.
- **FR-020**: Las vistas MUST ser utilizables a 360 px, 768 px y 1440 px sin superposición ni desplazamiento horizontal global.
- **FR-021**: La feature MUST consumir identidad, comparación, portfolio, oportunidades, operación y recálculo de las 025, 028, 029 y 031 sin cambiar sus reglas de negocio.
- **FR-022**: La autorización para archivos de Entrega 1 MUST limitarse al selector en `layout/Header.jsx` y una prop opcional de cotización alternativa en Figurita; sin esa prop, su aspecto MUST ser idéntico al actual. Otros cambios a archivos de Entrega 1 requieren autorización adicional.
- **FR-023**: Después de un cambio exitoso de estrategia activa, Ranking MUST volver a consultar la configuración y las oportunidades; después de un recálculo exitoso MUST repetir ambas consultas y actualizar los datos de mercado afectados.

### Key Entities

- **Modo de visualización**: elección personal Mercado actual o Momentum compartida entre las tres vistas durante la sesión, sin efecto global. Mercado actual es `marketValue`, no un sinónimo de OFFICIAL.
- **Estrategia activa**: elección global de ADMIN que fija precios en el próximo recálculo.
- **Precio de mercado vigente**: precio real para operar y valuar normalmente; puede provenir de la estrategia activa anterior hasta el siguiente recálculo.
- **Cotización de estrategia**: valor, estado e historial de OFFICIAL o MOMENTUM para un jugador.
- **Oportunidad**: jugador subvaluado o sobrevaluado según la diferencia entre mercado y alternativa, con filtros y destino de operación.
- **Valuación comparativa**: valor y resultado hipotéticos de posiciones y totales con MOMENTUM, separados de saldo y valuación vigente.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En el 100% de los escenarios de prueba, Mercado actual conserva la presentación vigente del precio persistido, incluso si lo fijó MOMENTUM, y cambiar el modo no modifica mercado ni crea operaciones.
- **SC-002**: Con al menos 20 jugadores de valores conocidos, el 100% de las diferencias coincide con el cálculo manual y el 100% de los precios de operación corresponde al mercado vigente.
- **SC-003**: Con al menos 3 posiciones, el 100% de valores, resultados y totales Momentum coincide con el cálculo manual, y las cotizaciones ausentes se identifican sin inventar totales.
- **SC-004**: Al menos 9 de cada 10 participantes encuentran una oportunidad, identifican el precio de operación y abren el panel del jugador correcto en menos de 60 segundos, sin asistencia.
- **SC-005**: En el 100% de los cambios administrativos probados, cancelar causa cero cambios, confirmar causa como máximo una solicitud y el éxito explica el recálculo pendiente; el 100% de cuentas USER carece del control.
- **SC-006**: A 360 px, 768 px y 1440 px, el 100% de flujos principales se completa sin desplazamiento horizontal global ni controles tapados.
- **SC-007**: El 100% de controles principales se alcanza y activa sólo con teclado, y precios, diferencias, curvas y resultados se identifican mediante texto accesible sin depender sólo del color.
- **SC-008**: En el 100% de fallas de consulta y cambio administrativo probadas, hay un mensaje claro y se puede seguir usando la vista o reintentar sin mostrar un estado no confirmado como definitivo.

## Assumptions

- «Mercado actual» es el modo inicial de presentación habitual del precio persistido. «Momentum» añade la cotización MOMENTUM para comparar. Si MOMENTUM pasa a ser la estrategia que fijó el mercado, ambos valores pueden coincidir; las etiquetas lo aclaran. OFFICIAL y MOMENTUM son códigos de estrategias históricas, no nombres intercambiables con «Mercado actual».
- La selección «Ver con» dura la sesión de navegación y no requiere persistencia entre sesiones.
- El precio de mercado puede seguir asociado a la estrategia de la última corrida tras un cambio global. La 031 informa estrategia activa y origen del precio vigente.
- El porcentaje es (cotización comparativa menos precio de mercado) dividido por precio de mercado. Un denominador no positivo deja el porcentaje sin valor.
- Oportunidades y exclusión de estados no calculados siguen la 031; el frontend presenta sus resultados sin reclasificar jugadores.
- Detalle permite llevar al usuario al panel de la 029; el mecanismo concreto de foco o desplazamiento se define en planificación.
- La identidad ADMIN procede de la 028. El servidor sigue siendo autoridad final sobre el cambio.

## Dependencies

- Feature 031: estrategia activa y origen del precio vigente, cotizaciones por lote, oportunidades, portfolio alternativo y cambio global.
- Feature 028: identidad con rol y portfolio personal.
- Feature 029: panel de compra y venta en Detalle.
- Feature 025: «Recalcular cotizaciones» en Ranking.
- Vistas existentes de Catálogo, Detalle, Ranking y Portfolio y componentes visuales compartidos.

## Out of Scope

- Recalcular automáticamente al cambiar la estrategia activa o modificar de inmediato el precio de mercado.
- Crear estrategias, editar pesos o elegir una tercera estrategia.
- Alterar el motor de órdenes, sus estimaciones o el precio de ejecución.
- Historial de oportunidades, alertas y recomendaciones automáticas.
- Modificaciones a archivos de Entrega 1 distintas de las dos excepciones autorizadas.
