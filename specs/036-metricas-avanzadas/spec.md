# Feature Specification: Métricas propias de la aplicación

**Feature Branch**: `feature/metricas-avanzadas`

**Created**: 2026-11-16

**Status**: Draft

**Input**: User description: "SPECIFY_FEATURE_DIRECTORY=specs/036-metricas-avanzadas. Métricas propias de la aplicación (mercado, cotizaciones, fuentes externas y usuarios) expuestas por el endpoint de Prometheus. Mercado: órdenes creadas por lado y tipo, rechazadas por motivo, trades, tokens operados y volumen en créditos. Cotización: duración de cada recálculo por disparador y resultado, cotizaciones calculadas por estrategia y cambios de estrategia activa. Fuentes externas: llamadas a Football-Data y WhoScored por resultado (éxito, error, caché). Usuarios: registros y logins por resultado y cantidad de usuarios registrados. Prefijo común, etiquetas de pocos valores, descripción en cada métrica; medir no cambia el resultado de ninguna operación; dashboard de Grafana versionado en observability/. Fuera de alcance: métricas por usuario o por jugador y un endpoint REST propio de métricas."

## Contexto y límites

La feature 035 dejó la exposición de métricas en formato Prometheus en `/actuator/prometheus` (sólo JWT ADMIN o API key con `ROLE_ADMIN`) y una demo con Prometheus y Grafana en `observability/`, pero la aplicación todavía no publica ninguna métrica de negocio. Esta feature agrega métricas propias del mercado, de las cotizaciones, de las fuentes externas y de los usuarios, publicadas por ese mismo endpoint y con su misma restricción de acceso, y un dashboard que las reúne junto con la latencia y la tasa de error HTTP de la 035.

No incluye métricas por usuario ni por jugador, un endpoint REST propio de métricas, alertas ni tracing. No cambia reglas de seguridad, contratos de la API ni comportamiento funcional existente.

Archivos de entregas anteriores autorizados a modificarse, exclusivamente para registrar mediciones sin alterar su lógica, contratos ni respuestas: `OrderService` y `MarketMakerService` (mercado; el market maker publica sus órdenes sin pasar por `OrderService`); `QuoteCalculationService` y `QuoteRecalculationScheduler` (duración, resultado y disparador del recálculo); `FootballDataAdapter` (resultado de la llamada y, en su anotación de caché, el resolvedor que cuenta los aciertos), `WhoScoredAdapter` y `PlayerStatsService` (la caché de WhoScored se consulta en ese servicio); `AuthService` (usuarios); `LayeredArchitectureTest` y `CodingRulesTest` de la 033, sólo para incorporar el paquete de métricas a las reglas de capas y de ciclos; `README.md` en su sección Monitoreo; y las pruebas existentes de esas clases, sólo para ajustar constructores o la llamada que verifican (por ejemplo, la sobrecarga del recálculo en la prueba del scheduler) y para agregar casos de métricas sin cambiar los existentes. Se agregan archivos nuevos para las métricas, sus pruebas y el dashboard bajo `observability/grafana/dashboards/`. No se modifican la Constitution, los controllers, `CacheConfig`, `SecurityConfig`, los filtros, los eventos existentes, los archivos de configuración de Prometheus o Grafana de la 035 ni otros archivos previos.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Observar la actividad del mercado (Priority: P1)

Como operador ADMIN, quiero ver cuántas órdenes se crean por lado y tipo, cuántas se rechazan y por qué motivo, cuántos trades se ejecutan, cuántos tokens se operan y qué volumen en créditos se mueve, para saber si el mercado funciona y detectar rechazos anómalos.

**Why this priority**: El mercado es la operación central de la aplicación; su actividad y sus rechazos son la señal de negocio más relevante.

**Independent Test**: Con la aplicación iniciada, crear órdenes BUY y SELL de tipo MARKET y LIMIT que se crucen, provocar un rechazo por saldo, otro por tokens y otro por banda de precio, y consultar `/actuator/prometheus` como ADMIN antes y después.

**Acceptance Scenarios**:

1. **Given** las métricas de mercado en su estado previo, **When** se crea una orden aceptada, **Then** el conteo de órdenes creadas aumenta en 1 únicamente para su combinación de lado (BUY/SELL) y tipo (MARKET/LIMIT).
2. **Given** un usuario sin saldo suficiente, sin tokens suficientes o con un precio límite fuera de banda, **When** intenta crear la orden, **Then** el conteo de órdenes rechazadas aumenta en 1 para el motivo correspondiente (saldo, tokens o banda de precio) y el conteo de órdenes creadas no cambia.
3. **Given** dos órdenes que se cruzan, **When** se ejecuta un trade de N tokens a precio P, **Then** el conteo de trades aumenta en 1, los tokens operados aumentan en N y el volumen aumenta en N × P créditos.
4. **Given** una orden que se ejecuta en varios trades, **When** termina el cruce, **Then** cada trade se cuenta una vez y la suma de tokens y volumen coincide con la de los trades registrados.

---

### User Story 2 - Seguir los recálculos de cotizaciones (Priority: P2)

Como operador ADMIN, quiero conocer la duración de cada recálculo según su disparador y resultado, cuántas cotizaciones calcula cada estrategia y cuántas veces cambia la estrategia activa, para detectar recálculos lentos o fallidos y relacionar cambios de precios con cambios de estrategia.

**Why this priority**: Las cotizaciones alimentan al mercado; un recálculo lento o fallido degrada toda la aplicación.

**Independent Test**: Disparar un recálculo manual, esperar o simular uno programado, provocar uno fallido, cambiar la estrategia activa y verificar las series en `/actuator/prometheus`.

**Acceptance Scenarios**:

1. **Given** un recálculo manual exitoso, **When** termina, **Then** se registra su duración con disparador manual y resultado éxito.
2. **Given** un recálculo programado que falla, **When** termina, **Then** se registra su duración con disparador programado y resultado fallo.
3. **Given** un recálculo que calcula cotizaciones con una o más estrategias, **When** termina, **Then** el conteo de cotizaciones calculadas aumenta, por estrategia, en la cantidad que informa el resumen de esa corrida.
4. **Given** una estrategia activa, **When** un ADMIN la cambia por otra, **Then** el conteo de cambios de estrategia activa aumenta en 1.
5. **Given** un recálculo en curso, **When** se rechaza otro pedido por estar ocupado, **Then** no se registra una duración de recálculo para ese pedido rechazado.

---

### User Story 3 - Vigilar las fuentes externas (Priority: P2)

Como operador ADMIN, quiero ver las llamadas a Football-Data y a WhoScored según resultado (éxito, error o servido desde caché) para saber si una fuente está fallando o si la caché evita llamadas.

**Why this priority**: Las estadísticas y los equipos dependen de fuentes no controladas por el equipo; sus fallas explican datos faltantes.

**Independent Test**: Consultar datos de Football-Data dos veces dentro del tiempo de vida de la caché, provocar un error de cada fuente con un servidor simulado y verificar las series.

**Acceptance Scenarios**:

1. **Given** una consulta a Football-Data no cacheada que responde correctamente, **When** termina, **Then** se cuenta una llamada a Football-Data con resultado éxito.
2. **Given** la misma consulta repetida dentro del tiempo de vida de la caché, **When** se resuelve desde caché, **Then** se cuenta con resultado caché y no como éxito.
3. **Given** una fuente que responde con error, excede el tiempo de espera, bloquea la solicitud o falla por cualquier otra excepción inesperada, **When** termina la llamada, **Then** se cuenta con resultado error para esa fuente y el comportamiento de la operación (excepción, reintento u omisión) es el mismo que sin métricas.
4. **Given** una liga de WhoScored cuyos jugadores pendientes están todos en la caché, **When** se actualizan sus estadísticas, **Then** se cuenta una llamada a WhoScored con resultado caché y no se consulta la fuente.

---

### User Story 4 - Medir registros, logins y usuarios (Priority: P3)

Como operador ADMIN, quiero ver los registros y los logins por resultado y la cantidad de usuarios registrados para observar la adopción y detectar picos de credenciales inválidas.

**Why this priority**: Aporta contexto de uso y de seguridad, pero no afecta la operación del mercado.

**Independent Test**: Registrar un usuario, repetir el registro con un nombre ya usado, hacer un login correcto y uno con contraseña inválida, y verificar las series.

**Acceptance Scenarios**:

1. **Given** un registro válido, **When** termina, **Then** el conteo de registros con resultado éxito aumenta en 1 y la cantidad de usuarios registrados aumenta en 1.
2. **Given** un registro rechazado (por ejemplo, usuario o email ya existentes), **When** termina, **Then** el conteo de registros con resultado fallo aumenta en 1 y la cantidad de usuarios no cambia.
3. **Given** un login con credenciales correctas o incorrectas, **When** termina, **Then** el conteo de logins aumenta en 1 con resultado éxito o fallo, respectivamente, sin etiqueta que identifique al usuario.

---

### User Story 5 - Ver todo en un dashboard (Priority: P3)

Como integrante del equipo, quiero un dashboard de Grafana versionado que muestre estas métricas junto con la latencia y la tasa de error HTTP de la 035, para demostrar la observabilidad sin armar paneles a mano.

**Why this priority**: Facilita la demostración; depende de que las métricas existan.

**Independent Test**: Levantar la demo de `observability/` con la aplicación, generar actividad y abrir el dashboard.

**Acceptance Scenarios**:

1. **Given** la demo activa, **When** se abre Grafana, **Then** el dashboard nuevo aparece cargado sin importarlo manualmente.
2. **Given** actividad de mercado, cotizaciones, fuentes externas y usuarios, **When** se observa el dashboard, **Then** hay al menos un panel por cada una de esas cuatro áreas y paneles de latencia y tasa de error HTTP con datos.

### Edge Cases

- Si registrar una métrica falla, la operación medida devuelve el mismo resultado o la misma excepción que sin medición, y el fallo de medición se registra en el log sin datos sensibles.
- Una operación que falla por un motivo distinto de saldo, tokens o banda de precio (por ejemplo, jugador sin cotización o datos inválidos) no se cuenta como orden creada ni como rechazo con uno de esos motivos.
- Una orden aceptada que no se cruza cuenta como creada pero no genera trades, tokens ni volumen.
- Los valores de etiqueta son siempre de un conjunto fijo y conocido; nunca contienen ids, nombres de usuario, emails, nombres de jugador ni mensajes de error.
- Las métricas existen desde el arranque con valor cero para cada combinación de etiquetas conocida, de modo que el dashboard muestra 0 y no "sin datos" antes de la primera actividad. La excepción son las cotizaciones por estrategia, cuyos códigos dependen de la configuración y aparecen con el primer recálculo que las calcula.
- Al reiniciar la aplicación los contadores vuelven a cero; la cantidad de usuarios registrados refleja la persistencia, no el reinicio. La duración del último recálculo también vuelve a 0 en la aplicación, pero el dashboard sigue mostrando la última duración registrada en los 7 días anteriores, aunque el reinicio haya sido posterior a esa corrida.
- Un recálculo que se ejecuta en una transacción revertida no suma cotizaciones calculadas que no quedaron persistidas.
- Las órdenes y trades del market maker del sistema se cuentan como cualquier otra actividad de mercado.
- Sin credencial ADMIN las métricas nuevas siguen inaccesibles (401 anónimo, 403 usuario común), igual que en la 035.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: La aplicación MUST contar las órdenes creadas, etiquetadas por lado (BUY, SELL) y tipo (MARKET, LIMIT).
- **FR-002**: La aplicación MUST contar las órdenes rechazadas por saldo insuficiente, tokens insuficientes o precio fuera de banda, etiquetadas con uno de esos tres motivos.
- **FR-003**: La aplicación MUST contar los trades ejecutados, acumular los tokens operados y acumular el volumen operado en créditos (cantidad × precio de cada trade).
- **FR-004**: La aplicación MUST registrar la duración de cada recálculo de cotizaciones, etiquetada por disparador (manual, programado) y resultado (éxito, fallo), de forma que se puedan obtener cantidad, total, duración media y la duración de la última corrida aunque haya ocurrido días antes (el recálculo programado es semanal).
- **FR-005**: La aplicación MUST contar las cotizaciones calculadas por cada recálculo, etiquetadas por estrategia, con la cantidad informada por el resumen de la corrida.
- **FR-006**: La aplicación MUST contar los cambios de estrategia activa.
- **FR-007**: La aplicación MUST contar las llamadas a Football-Data y a WhoScored, etiquetadas por fuente y por resultado (éxito, error, caché). Ambas fuentes tienen caché: caché se cuenta cuando la consulta se resuelve sin llamar a la fuente, y error ante cualquier excepción de la llamada, no sólo las de cliente HTTP.
- **FR-008**: La aplicación MUST contar los registros y los logins etiquetados por resultado (éxito, fallo) y publicar la cantidad actual de usuarios registrados.
- **FR-009**: Todas las métricas nuevas MUST usar el prefijo común `gdmarket.`, tener una descripción, usar sólo etiquetas con un conjunto fijo y pequeño de valores y MUST NOT incluir ids ni datos identificatorios de usuarios o jugadores. Las etiquetas comunes de la 035 (como `application`) se mantienen.
- **FR-010**: Registrar una métrica MUST NOT cambiar el resultado, la respuesta, la excepción, las transacciones ni los efectos persistidos de la operación medida; si la medición falla, la operación continúa.
- **FR-011**: Las métricas nuevas MUST publicarse sólo a través de `/actuator/prometheus` y `/actuator/metrics` con la misma restricción de acceso de la 035; MUST NOT agregarse endpoints REST nuevos.
- **FR-012**: El repositorio MUST incluir en `observability/` un dashboard de Grafana, cargado automáticamente por la demo existente, con paneles de mercado, cotizaciones, fuentes externas, usuarios, latencia HTTP y tasa de error HTTP.
- **FR-013**: La sección Monitoreo del README MUST listar las métricas nuevas con su significado y etiquetas, y cómo ver el dashboard.
- **FR-014**: La feature MUST respetar los archivos autorizados en Contexto y límites y la separación de capas de la Constitution §2.1: los controllers no registran métricas y el registro de mediciones queda en un paquete propio usado sólo por services y adapters.

### Key Entities

- **Métrica de negocio**: Medición con nombre bajo `gdmarket.`, descripción, tipo (contador, acumulado, duración o valor actual) y etiquetas de valores fijos.
- **Etiqueta**: Dimensión de una métrica con valores cerrados: lado, tipo de orden, motivo de rechazo, disparador, resultado, estrategia o fuente.
- **Dashboard**: Conjunto versionado de paneles que consultan las métricas nuevas y las HTTP de la 035.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Para cada escenario de aceptación de mercado, cotización, fuentes externas y usuarios, la serie correspondiente cambia exactamente en la cantidad esperada y ninguna otra serie de la misma métrica cambia.
- **SC-002**: El 100 % de las métricas nuevas tiene el prefijo `gdmarket`, una descripción no vacía y etiquetas con a lo sumo 5 valores posibles cada una (excepto estrategia, limitada a las estrategias configuradas); 0 series contienen ids o datos de usuarios o jugadores.
- **SC-003**: Con la medición forzada a fallar, el 100 % de las operaciones de mercado, recálculo, fuentes externas, registro y login probadas devuelve el mismo resultado o error que con la medición funcionando.
- **SC-004**: Un operador puede responder desde el dashboard, sin escribir consultas, cuántas órdenes se crearon y rechazaron, cuánto volumen se operó, cuánto duró el último recálculo, qué fuente está fallando, cuántos usuarios hay y cuáles son la latencia y la tasa de error HTTP.
- **SC-005**: Al levantar la demo, el dashboard nuevo está disponible en 1 de 1 intentos sin pasos manuales en Grafana.
- **SC-006**: Un usuario común y uno anónimo no pueden leer ninguna métrica nueva; 0 endpoints REST nuevos.
- **SC-007**: En una revisión de los cambios, 0 archivos previos fuera de los autorizados en Contexto y límites presentan modificaciones.

## Assumptions

- "Orden creada" es una orden aceptada y persistida por el mercado, haya o no cruzado; los rechazos de los tres motivos pedidos no cuentan como creadas.
- Sólo los tres motivos pedidos (saldo, tokens, banda de precio) se cuentan como rechazos; otros errores de validación ya se observan como errores HTTP por las métricas de la 035.
- El volumen se mide en créditos al precio de ejecución de cada trade.
- El resultado de un recálculo es fallo cuando la corrida termina con error; un pedido rechazado por haber otro recálculo en curso no es un recálculo y no se mide como tal.
- La etiqueta de estrategia usa el código de estrategia, cuyo conjunto está acotado por las estrategias configuradas (hoy OFFICIAL y MOMENTUM).
- Una llamada es una consulta de una competición (Football-Data) o de una liga (WhoScored). Ambas fuentes tienen caché: Football-Data por competición y WhoScored por jugador; una liga de WhoScored cuenta como servida desde caché cuando todos sus jugadores salieron de la caché y no hizo falta consultar la fuente.
- Un registro o login fallido es cualquier rechazo de la operación (datos duplicados, credenciales inválidas); no se distingue el motivo para no revelar información.
- La cantidad de usuarios registrados cuenta todas las cuentas persistidas, incluidas las del sistema, y puede reflejarse con la demora de un ciclo de recolección.
- Los contadores son acumulativos desde el arranque; tasas y totales por período se obtienen en Prometheus o Grafana.
- El dashboard se agrega como archivo nuevo junto al de la 035 y lo carga el provisioning existente.
