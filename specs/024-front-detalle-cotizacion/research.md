# Research: Detalle de jugador y evolución de su cotización (frontend)

Feature: `specs/024-front-detalle-cotizacion` · Fecha: 2026-10-27

Cada decisión resuelve una incógnita del Technical Context de [plan.md](./plan.md).

## R1. Librería de gráficos

- **Decision**: `recharts@3.10.1` (versión exacta, publicada 2026-07-25) más su peer `react-is@^19` en `dependencies`.
- **Rationale**: autorizada explícitamente en la Entrega 1. La 3.x declara soporte para React 19 y trae `accessibilityLayer` activo por defecto: el gráfico es enfocable y las flechas recorren los puntos mostrando el tooltip, lo que cubre FR-012 para teclado sin código propio. Se evita la 3.11 porque al día de hoy solo existe como canary.
- **Impacto en el bundle**: recharts lleva el bundle de producción de ~283 kB a ~650 kB (sin comprimir). Vite muestra el aviso "Some chunks are larger than 500 kB after minification", que no rompe el build. Se acepta así: no se agrega carga diferida (`React.lazy`) en esta feature.
- **Alternatives considered**: SVG a mano (más código para escalas, ejes, tooltip y teclado, y más superficie para SonarCloud); Chart.js (canvas: el tooltip no es accesible y la tabla alternativa pasaría a ser la única vía); visx/d3 (más bajo nivel, agrega varias dependencias).

## R2. recharts en jsdom (Vitest)

- **Decision**: en los tests que renderizan el gráfico se mockea solo `ResponsiveContainer` con `vi.mock('recharts', async (importOriginal) => ({ ...(await importOriginal()), ResponsiveContainer: ({ children }) => <div style={{ width: 800, height: 300 }}>{cloneElement(children, { width: 800, height: 300 })}</div> }))`. El mock se define inline en los dos archivos que renderizan el gráfico (`QuoteChart.test.jsx` y `PlayerDetailPage.test.jsx`), sin helper compartido ni setup global. Un archivo fuera de `*.test.js(x)` contaría en SonarCloud como fuente sin cobertura (`sonar-project.properties` incluye todo `frontend/src` en `sonar.sources`). El contenido del tooltip se prueba con un test unitario del componente propio `QuoteTooltip`, y la navegación con teclado sobre el gráfico se valida en el quickstart V5, porque depende de mediciones que jsdom no tiene.
- **Rationale**: en jsdom el layout mide 0×0 y `ResizeObserver` no existe, así que `ResponsiveContainer` no renderiza hijos (y advierte por consola). Pasar un tamaño fijo al `LineChart` real deja el resto de recharts sin mockear. Las aserciones de datos se hacen sobre la **tabla alternativa** (fuente de verdad accesible, FR-013), no sobre paths SVG, así que el test no depende de detalles internos de recharts.
- **Alternatives considered**: polyfill de `ResizeObserver` más un `getBoundingClientRect` falso (frágil y global); mockear todo recharts (dejaría sin probar que el gráfico monta); tests visuales en navegador (fuera de la infraestructura actual, que solo tiene Vitest).

## R3. Rango: filtro en el servidor (`from`/`to`) o en el cliente

- **Decision**: en el servidor. "Último mes" envía `from = hoy UTC − 29 días` y `to = hoy UTC` (30 días incluidos). "3 meses" envía `from = hoy UTC − 89 días` y `to = hoy UTC`. "Toda la temporada" no envía `from` ni `to`. Cada cambio de estrategia o rango hace una consulta nueva.
- **Rationale**: respeta el contrato de la 017 (días UTC inclusivos) y el diseño pedido (`getPlayerQuotes(id, { strategy, from, to, at })`). El historial semanal es chico, así que el costo es bajo. "Hoy" se toma de un `now` inyectable para poder testear con fechas fijas.
- **Alternatives considered**: pedir todo el historial una vez por estrategia y filtrar en el cliente. Daría cambios de rango instantáneos, pero duplicaría la semántica de días UTC que ya resuelve el backend y dejaría sin uso los parámetros `from`/`to` del contrato.

## R4. Respuestas fuera de orden

- **Decision**: igual que en `useAlbum`. Cada efecto crea su `AbortController`, lo aborta en el cleanup y descarta resultados si `signal.aborted`. `httpClient.request` ya acepta `signal`. Se aplica al detalle, al historial y a la consulta puntual.
- **Rationale**: cumple FR-015 y SC-003 con el patrón existente del repo y sin dependencias.
- **Alternatives considered**: un contador de "última petición" (redundante si ya hay abort); una librería de data fetching (dependencia no autorizada).

## R5. Cálculo de la variación

- **Decision**: si `detail.quote` existe, se pide una vez el historial completo de `detail.quote.strategy` (sin `from`/`to`). La cotización anterior es la última del historial con `calculatedAt` menor que `detail.quote.calculatedAt`. La variación es `actual − anterior` en créditos y `(actual − anterior) / anterior × 100` en porcentaje. Si no hay anterior, o la anterior vale 0, se muestra "—". Si esta consulta falla, la variación muestra "—" sin panel de error, porque es un dato secundario de la cabecera.
- **Rationale**: el detalle (022) trae solo la última cotización. La anterior no depende del rango elegido en el gráfico, así que se calcula aparte para que la cabecera no cambie al mover el selector.
- **Alternatives considered**: derivarla del historial del gráfico (cambiaría con el rango y no existiría si el rango tiene un solo punto); agregar un campo al backend (fuera de alcance: no se modifica el backend).

## R6. Consulta puntual (`at`)

- **Decision**: `<input type="date">` con `max = hoy UTC` y un botón "Consultar". Se envía `{ strategy, at }` con la estrategia elegida en el gráfico. Un 200 muestra el resultado. Cualquier 404 (`quote_not_found`) muestra "Sin cotización para esa fecha". Cambiar la estrategia descarta el resultado y deja la consulta en `idle`. Otros errores muestran error con reintento solo en esa sección.
- **Rationale**: FR-016 a FR-018. El jugador ya se cargó, así que un 404 acá no significa "jugador inexistente". El input nativo es accesible y responsive sin dependencias.
- **Alternatives considered**: consultar automáticamente al cambiar la fecha (dispara pedidos mientras se tipea la fecha con teclado); recalcular automáticamente con la nueva estrategia (menos predecible para el usuario).

## R7. Identificador de la URL

- **Decision**: antes de pedir nada, se valida `id` con `/^[1-9]\d*$/` y que no supere `9223372036854775807` (se compara longitud y luego el texto). Si no es válido, se pasa directo a `notFound` sin request. Un 400 o 404 del detalle también lleva a `notFound`.
- **Rationale**: FR-024. Evita un round-trip con ids imposibles y trata del mismo modo los 400 de validación del backend.
- **Alternatives considered**: delegar todo al backend (un 400 se vería como error con reintento, lo cual es incorrecto).

## R8. Enlace desde la figurita sin cambiar su aspecto

- **Decision**: en `AlbumGrid` se envuelve cada `<Figurita>` en un `<Link to={`/jugadores/${id}`} className="figurita-grid__link" aria-label={`Ver detalle de ${fullName}`}>`. El CSS nuevo es `display:block; min-width:0; color:inherit; text-decoration:none` y un `:focus-visible` con outline de tinta. `Figurita.jsx` y `Figurita.css` no se tocan. Las reglas de hover existentes (`.figurita-grid .figurita:hover`) siguen aplicando porque la figurita sigue dentro de `.figurita-grid`. Se agrega el mismo efecto para `.figurita-grid__link:focus-visible .figurita`.
- **Rationale**: es el cambio mínimo autorizado (AlbumGrid). La figurita sigue siendo presentacional y la Entrega 1 la puede seguir usando en otros contextos sin enlace. Un enlace real (y no un `onClick` sobre `article`) da teclado, menú contextual y "abrir en pestaña nueva".
- **Alternatives considered**: agregar una prop `to` a Figurita (toca el componente compartido y su test); `onClick` + `role="link"` (pierde la semántica nativa).

## R9. Colores por liga en el gráfico

- **Decision**: el contenedor del gráfico define `--quote-line: var(<colorVar de la liga>)` (o `--tinta-2` si la liga es desconocida). Los trazos se pintan desde CSS (`.quote-chart .recharts-line-curve { stroke: var(--quote-line) }`, y lo mismo para puntos y cursor). Los ejes y la grilla usan `--tinta-2` y `--papel-2`.
- **Rationale**: los atributos de presentación SVG (`stroke="var(--x)"`) no resuelven custom properties de forma consistente entre navegadores, pero las propiedades CSS sí. Así el gráfico respeta `tokens.css` sin duplicar hex en JS.
- **Alternatives considered**: leer el color con `getComputedStyle` en un efecto (render extra, y en jsdom devuelve vacío); hardcodear hex (rompe la fuente única de tokens).

## R10. Tabla alternativa y descripción

- **Decision**: `<figure>` con `<figcaption>` visible ("Evolución OFFICIAL · último mes · N cotizaciones"). El SVG lleva `role="img"` con `aria-label` igual al caption, y además conserva la capa de teclado de recharts. Debajo va una `<table>` con clase `quote-chart__table`, oculta visualmente con estilos definidos en `QuoteChart.css` (no en `styles/base.css`, que es de Entrega 1), con columnas Fecha, Valor, Estrategia y Versión, construida desde el mismo array que el gráfico.
- **Rationale**: FR-013 y SC-004. Si las dos vistas salen del mismo array, la paridad queda garantizada por construcción, y los tests la verifican sobre la tabla.
- **Alternatives considered**: un botón "ver como tabla" visible (válido, pero agrega UI no pedida; se puede sumar después sin cambiar el contrato).

## R11. Fechas y formatos

- **Decision**: se agregan en un archivo propio de la feature, `features/player-detail/quoteFormat.js` (sin modificar `utils/format.js`, que es de Entrega 1), `formatDate(iso)` (`Intl.DateTimeFormat('es-AR', { dateStyle: 'medium', timeZone: 'UTC' })`), `formatDateTime(iso)` (agrega hora y minutos, en UTC), `formatSignedCredits(delta)`, `formatPercent(value)` y `formatDecimal(value)`; `formatCredits` se sigue importando de `utils/format.js`. El tooltip usa `formatDateTime` si hay más de un punto el mismo día UTC y `formatDate` si no. El eje X es numérico (`type="number"`, `scale="time"`, dominio `dataMin`/`dataMax`) sobre `Date.parse(calculatedAt)`, así los puntos quedan espaciados por tiempo real.
- **Rationale**: mantiene el formato es-AR del resto de la app. UTC alinea lo que se ve con la semántica del backend (`at` y rangos en días UTC).
- **Alternatives considered**: hora local (un recálculo de las 23:00 UTC se vería en otro día que el que usa `at`).

## R12. `VariationBadge` y la feature 023

- **Decision**: `VariationBadge` no existe en ninguna rama (la 023 todavía no empezó). Se crea en esta feature como componente compartido `src/components/VariationBadge.jsx` + `.css`, con la interfaz `{ current, previous }` documentada en `contracts/player-detail-ui-contract.md`, para que la 023 lo reutilice. Las filas clickeables de `RankingPage` quedan en la 023, según la clarificación del 2026-10-27 (no se construye ni se modifica el Ranking acá).
- **Rationale**: el pedido de plan decía "reusar VariationBadge de 023" y "hacer clickeable cada fila de RankingPage", pero ninguno de los dos existe, y la spec deja el Ranking fuera de alcance. Ubicar el componente en `components/` (no en `features/player-detail/`) evita que la 023 tenga que moverlo.
- **Alternatives considered**: esperar a la 023 (bloquea FR-008); dejar la variación inline en la cabecera (la 023 la duplicaría).
