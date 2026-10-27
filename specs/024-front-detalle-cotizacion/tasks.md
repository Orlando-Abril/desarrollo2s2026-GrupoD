---

description: "Task list for Detalle de jugador y evolución de su cotización (frontend)"
---

# Tasks: Detalle de jugador y evolución de su cotización (frontend)

**Input**: Design documents from `/specs/024-front-detalle-cotizacion/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: Obligatorios: los pidió el plan y los exige SC-008. En cada fase se escriben primero los tests con Vitest, Testing Library y `fetch` mockeado (mismo patrón que `frontend/src/pages/AlbumPage.test.jsx`), se confirma que fallan por la capacidad ausente y recién después se implementa. Las aserciones de datos del gráfico se hacen sobre la tabla alternativa, nunca sobre el SVG de recharts (research R2).

**Organization**: Tareas agrupadas por historia de `spec.md`. No se modifican `frontend/src/api/playersApi.js`, `frontend/src/components/Figurita.jsx`, `frontend/src/components/Figurita.css`, `frontend/src/features/album/AlbumTable.jsx`, el ítem "Ranking" de `frontend/src/layout/AppLayout.jsx` ni el backend.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede correr en paralelo con otras tareas [P] del mismo bloque: toca archivos distintos y no depende de tareas incompletas.
- **[Story]**: Historia de usuario de `spec.md` (US1–US4; la US5 es dependencia de la feature 023, sin tareas acá).
- Rutas exactas desde la raíz del repositorio.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Línea base verde y dependencia de gráficos autorizada.

- [X] T001 Ejecutar `npm test`, `npm run lint` y `npm run build` en `frontend/` sobre la rama `feature/front-detalle-cotizacion` y registrar en el handoff cualquier fallo preexistente antes de tocar código
- [X] T002 En `frontend/`, ejecutar `npm install --save-exact recharts@3.10.1` y luego `npm install react-is@^19`, de modo que `frontend/package.json` quede con `"recharts": "3.10.1"` (sin `^`) y `"react-is": "^19.x"` en `dependencies`, y confirmar con `git diff` que `frontend/package.json` y `frontend/package-lock.json` no tienen otros cambios de dependencias
- [X] T003 Crear `frontend/src/features/player-detail/QuoteChart.test.jsx` y `frontend/src/pages/PlayerDetailPage.test.jsx` con solo los imports y, al inicio de cada uno, el mock inline `vi.mock('recharts', async (importOriginal) => ({ ...(await importOriginal()), ResponsiveContainer: ({ children }) => <div style={{ width: 800, height: 300 }}>{cloneElement(children, { width: 800, height: 300 })}</div> }))`, con un comentario de una línea que remita a research R2 (jsdom mide 0×0 y no tiene `ResizeObserver`); no crear un helper compartido como `frontend/src/test/mockRecharts.jsx` ni tocar `sonar-project.properties` (un archivo fuera de `*.test.jsx` contaría como fuente sin cubrir en SonarCloud)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Adaptadores HTTP, lógica pura, catálogo y formatos que usan todas las historias.

**⚠️ CRITICAL**: Ninguna historia puede empezar antes de terminar esta fase.

### Tests (escribir primero, deben fallar)

- [X] T004 [P] Escribir `frontend/src/api/playerDetailApi.test.js`: `getPlayerDetail('42', { signal })` pide `/players/42` vía `request` y pasa el `signal`; el id se codifica con `encodeURIComponent`; propaga `ApiError` 404 y `NetworkError` sin transformar; un caso a través del `httpClient` real verifica `Authorization: Bearer <jwt>` y ausencia de `X-API-KEY`
- [X] T005 [P] Escribir `frontend/src/api/quotesApi.test.js` con la tabla de `contracts/quotes-client-contract.md`: `{}` → `/players/42/quotes`; `{ strategy: 'MOMENTUM' }`; `{ strategy, from, to }` (comparar con `URLSearchParams`, sin depender del orden); `{ strategy, at }`; vacíos `''` omitidos; `{ at, from }` y `{ at, to }` lanzan `TypeError` **sin llamar a `fetch`**; el `signal` llega a `request`
- [X] T006 [P] Escribir `frontend/src/features/player-detail/quoteUtils.test.js` con `now` fijo `2026-10-27T03:00:00Z`: `rangeToDates('month', now)` → `{ from: '2026-09-28', to: '2026-10-27' }`; `'quarter'` → `{ from: '2026-07-30', to: '2026-10-27' }`; `'season'` → `{}`; `parsePlayerId`: válidos `'1'`, `'42'`, `'9223372036854775807'`; inválidos `'0'`, `'-1'`, `'abc'`, `'01'`, `'1.5'`, `''`, `'9223372036854775808'`, `'99999999999999999999'`; `findPrevious(history, current)` devuelve "la última cotización de `history` con `Date.parse(calculatedAt) < Date.parse(current.calculatedAt)`, o `null`" (incluir un caso con distinta precisión de segundos fraccionarios); `variation(currentValue, previousValue)` recibe números y devuelve `null` "si `currentValue` o `previousValue` son `null`, o si `previousValue === 0`", si no `{ delta, percent, direction }` con `direction` 'up' / 'down' / 'flat' según el signo de `delta` redondeado a 2 decimales (p. ej. 1,10 → 1,10001 es 'flat'); `toQuotePoints(quotes)` ordena por `t = Date.parse(calculatedAt)` ascendente y conserva `value`, `strategy`, `version`, `calculatedAt`; `hasSameDayPoints(points)` es true si dos puntos comparten día UTC; `todayUtc(now)` → `'2026-10-27'`
- [X] T007 [P] Agregar tests de formatos a un nuevo `frontend/src/features/player-detail/quoteFormat.test.js`: `formatDate('2026-10-20T18:00:00Z')` en es-AR y UTC (formato `dateStyle: 'medium'`); `formatDateTime` agrega hora:minutos UTC (una cotización `2026-10-20T23:30:00Z` sigue mostrándose el 20); `formatSignedCredits(0.12)` → `+0,12 cr`, `(-0.12)` → `−0,12 cr` con signo menos tipográfico, `(0)` → `0,00 cr`; `formatPercent(10.909)` → `+10,91 %`, negativo con `−`; `formatDecimal(7.42)` → `7,42`

### Implementación

- [X] T008 [P] Implementar `frontend/src/api/playerDetailApi.js` con `getPlayerDetail(id, { signal } = {})` que delega en `request(\`/players/${encodeURIComponent(id)}\`, { signal })` de `frontend/src/api/httpClient.js`, sin `fetch` directo y sin reinterpretar errores
- [X] T009 [P] Implementar `frontend/src/api/quotesApi.js` con `getPlayerQuotes(id, { strategy, from, to, at } = {}, { signal } = {})`: armar la query con `URLSearchParams` agregando solo valores no vacíos, lanzar `TypeError` antes de pedir si `at` viene junto con `from` o `to`, y delegar en `request(path, { signal })`, siguiendo el estilo de `frontend/src/api/playersApi.js`
- [X] T010 [P] Crear `frontend/src/features/player-detail/quoteCatalog.js` (sin modificar `frontend/src/domain/catalogo.js`) con las exportaciones `STRATEGIES = { OFFICIAL: { label: 'Oficial' }, MOMENTUM: { label: 'Momentum' } }`, `QUOTE_RANGES = { month: { label: 'Último mes', days: 30 }, quarter: { label: '3 meses', days: 90 }, season: { label: 'Toda la temporada', days: null } }` y `STAT_FIELDS` en este orden: `minutesPlayed` 'Minutos', `goals` 'Goles', `assists` 'Asistencias', `shots` 'Tiros', `keyPasses` 'Pases clave', `tackles` 'Tackles', `yellowCards` 'Amarillas', `redCards` 'Rojas', `rating` 'Rating' (sin `dribbles`)
- [X] T011 [P] Implementar `frontend/src/features/player-detail/quoteUtils.js` con `todayUtc`, `rangeToDates` (`month`: `from = now − 29 d`; `quarter`: `from = now − 89 d`; `to = hoy UTC`; `season`: `{}`; fechas `YYYY-MM-DD` UTC), `parsePlayerId` (`/^[1-9]\d*$/` y ≤ `9223372036854775807`, comparando longitud y después texto), `findPrevious` (comparando con `Date.parse`), `variation(currentValue, previousValue)` (recibe números), `toQuotePoints` y `hasSameDayPoints`, todas puras y con `now` inyectable, hasta que pase T006
- [X] T012 [P] Crear `frontend/src/features/player-detail/quoteFormat.js` con `formatDate`, `formatDateTime` (ambas `Intl.DateTimeFormat('es-AR', …, timeZone: 'UTC')`), `formatSignedCredits`, `formatPercent` y `formatDecimal` (2 decimales es-AR), reutilizando `formatCredits` de `frontend/src/utils/format.js` sin modificar ese archivo, hasta que pase T007

**Checkpoint**: `npm test` verde para T004–T007; adaptadores y utilidades listos.

---

## Phase 3: User Story 1 - Ver la ficha del jugador desde el Álbum (Priority: P1) 🎯 MVP

**Goal**: Desde una figurita del Álbum se llega a `/jugadores/:id` y se ve la cabecera tipo figurita con cotización actual y variación, más las estadísticas de la temporada.

**Independent Test**: Con `fetch` mockeado (detalle con stats y quote, historial completo con una cotización anterior), clic en una figurita del Álbum → la ruta cambia a `/jugadores/{id}`, el `h1` muestra el nombre, la cotización actual, la variación con ▲/▼/= y las 9 estadísticas (con "—" donde falten).

### Tests for User Story 1 (escribir primero, deben fallar) ⚠️

- [X] T013 [P] [US1] Escribir `frontend/src/components/VariationBadge.test.jsx` según `contracts/player-detail-ui-contract.md`: `{ current: 1.22, previous: 1.10 }` → texto `▲ +0,12 cr (+10,91 %)` y nombre accesible "Subió 0,12 cr (10,91 %)"; caso de baja con `▼` y "Bajó …"; `flat` → `= 0,00 cr (0,00 %)` y "Sin cambios"; `previous: null`, `current: null` o `previous: 0` → "—" con `aria-label="Sin variación disponible"`
- [X] T014 [P] [US1] Escribir `frontend/src/pages/PlayerDetailPage.test.jsx` (bloque "ficha"), renderizando con `MemoryRouter` en `/jugadores/42`, `SessionProvider` + `AutoLogin` como en `AlbumPage.test.jsx`, sobre el archivo con el mock inline creado en T003: el detalle completo del ejemplo `complete` de la 022 muestra `h1` "Bukayo Saka", equipo, "Premier League" en texto, "DEL", nacionalidad, edad, N° 042, "Cotización actual" `1,25 cr` y variación respecto de la cotización anterior del historial completo de `OFFICIAL`; `document.title` = "Bukayo Saka · Figuritas"; las 9 etiquetas de `STAT_FIELDS` en orden con rating `7,42`; `stats: null` → las 9 en "—"; un stat `null` puntual → "—" y nunca `0`; `quote: null` → cotización "—", variación "—" y sin request de historial de variación; enlace "← Volver al álbum" con `href="/album"`; al pasar de `/jugadores/42` a `/jugadores/43` no quedan datos del 42
- [X] T015 [P] [US1] Agregar a `frontend/src/pages/AlbumPage.test.jsx` un test: en la vista de figuritas, cada figurita es un `link` con nombre "Ver detalle de {fullName}" y `href="/jugadores/{id}"`; clic y Enter navegan a `/jugadores/{id}` (con un `Route` sonda); la vista tabla no tiene enlaces nuevos
- [X] T016 [P] [US1] Agregar a `frontend/src/routes/routes.test.jsx` que `App` con `/jugadores/42` sin sesión redirige a `/ingresar`, y con sesión renderiza la página de detalle (mockear `PlayerDetailPage` o `fetch` según el estilo del archivo)

### Implementation for User Story 1

- [X] T017 [P] [US1] Implementar `frontend/src/components/VariationBadge.jsx` + `frontend/src/components/VariationBadge.css` con props `{ current: number | null, previous: number | null }` llamando directamente a `variation(current, previous)` de `quoteUtils`, `formatSignedCredits` y `formatPercent`; símbolo `▲`/`▼`/`=` visible, `aria-label` con "Subió …" / "Bajó …" / "Sin cambios" / "Sin variación disponible", y colores `--verde` / `--rojo` / `--tinta-2` desde `frontend/src/styles/tokens.css`, hasta que pase T013
- [X] T018 [US1] Implementar la parte de detalle y variación de `frontend/src/features/player-detail/usePlayerDetail.js` con `usePlayerDetail(id, { now } = {})`: si `parsePlayerId(id)` es `null`, el estado es `notFound` sin request; si no, pide `getPlayerDetail` con `AbortController` abortado en el cleanup e ignora resultados abortados (patrón de `frontend/src/features/album/useAlbum.js`); `200` → `ready` con `{ player, stats, quote }`; con `quote` presente pide `getPlayerQuotes(id, { strategy: quote.strategy })` (historial completo, sin `from`/`to`) y expone `variation = { current: quote.value, previous: findPrevious(history, quote)?.value ?? null }`; si la variación falla, `previous: null` sin error; dejar `status`, `detail`, `variation` y `retry` en el valor de retorno (la clasificación de errores 400/404/5xx se completa en US4)
- [X] T019 [P] [US1] Implementar `frontend/src/features/player-detail/PlayerHeader.jsx` + `PlayerHeader.css`: figurita grande con la estructura visual de `frontend/src/components/Figurita.css` (N° con `padStart(3, '0')`, posición abreviada de `POSITIONS`, silueta, `--figurita-liga` desde `LEAGUES[league].colorVar` o `--tinta-2` si no se conoce la liga, nombre de liga en texto, equipo, nacionalidad · edad con "—"), y el bloque "Cotización actual" con `formatCredits(quote.value)` o "—" más `<VariationBadge>`; layout en columna en anchos angostos y en dos columnas desde 768 px, solo con tokens
- [X] T020 [P] [US1] Implementar `frontend/src/features/player-detail/StatsPanel.jsx` + `StatsPanel.css`: `section` con heading "Estadísticas de la temporada" y `aria-labelledby`, `dl` que recorre `STAT_FIELDS` mostrando enteros con `formatInteger`, rating con `formatDecimal` y "—" para `null`, `undefined` o `stats === null`; "Actualizado el {formatDate(fetchedAt)}" si existe; grilla responsive (2 columnas en 360 px, 3 o más en escritorio)
- [X] T021 [US1] Implementar `frontend/src/pages/PlayerDetailPage.jsx` + `PlayerDetailPage.css`: `PlayerDetailPage` lee `id` con `useParams` y renderiza `<PlayerDetail key={id} id={id} />`; `PlayerDetail` usa `usePlayerDetail`, muestra el enlace "← Volver al álbum" (`Link` a `/album`), el `h1` con `fullName`, `PlayerHeader` y `StatsPanel`, setea `document.title = \`${fullName} · Figuritas\`` cuando está `ready`, y muestra la carga como región `aria-busy="true"` con "Cargando jugador…" usando `Casillero` en variante cargando; layout apilado y sin scroll horizontal desde 360 px, con ancho máximo `--ancho-max`; dejar un lugar marcado para `QuoteChart` y `QuoteAtDate`
- [X] T022 [US1] Agregar en `frontend/src/App.jsx` `<Route path="/jugadores/:id" element={<PlayerDetailPage />} />` dentro del bloque `<Route element={<ProtectedRoute />}>`, junto a `/album`, hasta que pase T016
- [X] T023 [US1] Modificar `frontend/src/features/album/AlbumGrid.jsx` para envolver cada `<Figurita {...player} />` en `<Link key={player.id} to={\`/jugadores/${player.id}\`} className="figurita-grid__link" aria-label={\`Ver detalle de ${player.fullName}\`}>`, y agregar a `frontend/src/features/album/AlbumGrid.css` `.figurita-grid__link { display: block; min-width: 0; color: inherit; text-decoration: none; }`, un `:focus-visible` con `outline: 3px solid var(--tinta); outline-offset: 4px` y el mismo efecto de hover para `.figurita-grid__link:focus-visible .figurita` (respetando `prefers-reduced-motion` como hace `Figurita.css`), sin tocar `Figurita.jsx` ni `Figurita.css`, hasta que pase T015
- [ ] T024 [US1] Correr T013–T016 y `npm run lint`, y verificar manualmente en `npm run dev` que la figurita se ve idéntica en reposo y en hover respecto de `main` (SC-007, quickstart V1–V2)

**Checkpoint**: MVP navegable: Álbum → ficha con cabecera, variación y estadísticas.

---

## Phase 4: User Story 2 - Ver la evolución de la cotización en un gráfico (Priority: P1)

**Goal**: Gráfico de línea con selectores de estrategia y rango, tooltip con fecha, valor, estrategia y versión, tabla alternativa con los mismos datos y estado "todavía sin cotizaciones".

**Independent Test**: Con un historial mockeado de varios puntos (con un cambio de versión), la tabla alternativa muestra las mismas filas que los datos; al cambiar a MOMENTUM y a "Toda la temporada" se piden los parámetros correctos y la tabla se actualiza; con historial `[]` aparece "Todavía sin cotizaciones" y los selectores siguen activos.

### Tests for User Story 2 (escribir primero, deben fallar) ⚠️

- [X] T025 [P] [US2] Escribir los tests en `frontend/src/features/player-detail/QuoteChart.test.jsx` (sobre el mock inline de T003): con 3 puntos desordenados, que incluyan uno con `status: 'NO_STATS'` y versiones mezcladas (`version` 1 y 2), la `table` alternativa (columnas `Fecha | Valor | Estrategia | Versión`) tiene 3 filas en orden cronológico, iguales a `toQuotePoints`, con la etiqueta de `STRATEGIES` ("Oficial", "Momentum") en la columna Estrategia y la versión de cada punto; `figcaption` y el `aria-label` del gráfico dicen "Oficial · Último mes · 3 cotizaciones"; con dos puntos el mismo día UTC la columna Fecha incluye la hora; test unitario del componente propio `QuoteTooltip` (exportado desde `QuoteChart.jsx`): con `active` y un `payload` de un punto muestra fecha (con hora si `sameDay`), valor `formatCredits`, la etiqueta de `STRATEGIES` y "Versión N", y sin `active` no renderiza nada (la navegación con teclado sobre el gráfico se valida en quickstart V5, no en jsdom); el contenedor define `--quote-line` con el `colorVar` de la liga; con `points=[]` muestra `EstadoPanel` "Todavía sin cotizaciones" y los radios siguen habilitados; con `status='error'` muestra "No pudimos cargar la evolución" y el botón "Reintentar" llama a `onRetry`
- [X] T026 [P] [US2] Escribir `frontend/src/features/player-detail/usePlayerDetail.test.jsx` (bloque historial) con `renderHook` y `now` fijo `2026-10-27T03:00:00Z`: tras el detalle, el historial inicial pide `strategy=<quote.strategy>&from=2026-09-28&to=2026-10-27`, o `OFFICIAL` si `quote` es `null` o su estrategia no está en `STRATEGIES`; `setStrategy('MOMENTUM')` mantiene el rango; `setRange('season')` no envía `from`/`to`; `setRange('quarter')` envía `from=2026-07-30`; si se cambia rápido, la primera respuesta resuelta tarde **no** pisa a la última (su `signal` quedó abortado); `200 []` → `history.status === 'empty'`; un error → `'error'`, y `retryHistory` vuelve a pedir con la misma selección
- [X] T027 [P] [US2] Agregar a `frontend/src/pages/PlayerDetailPage.test.jsx` (bloque "evolución"): con historial mockeado, la tabla alternativa muestra sus filas; elegir el radio "Momentum" y luego "Toda la temporada" dispara los pedidos con los parámetros esperados y la tabla muestra las filas nuevas; historial vacío → "Todavía sin cotizaciones" con cabecera y estadísticas visibles; si falla solo el historial, cabecera y estadísticas siguen visibles y "Reintentar" de la sección recupera el gráfico (FR-023)

### Implementation for User Story 2

- [X] T028 [P] [US2] Agregar la clase `.quote-chart__table` para ocultar visualmente la tabla alternativa (patrón estándar: `position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0`) en `frontend/src/features/player-detail/QuoteChart.css`, sin modificar `frontend/src/styles/base.css`
- [X] T029 [US2] Extender `frontend/src/features/player-detail/usePlayerDetail.js` con la selección `{ strategy, range }` (inicial: `detail.quote?.strategy` si está en `STRATEGIES`, si no `'OFFICIAL'`; rango `'month'`) y el estado `history` (`loading | ready | empty | error`, con `points = toQuotePoints(response)`), activo solo con el detalle `ready`; cada cambio de `strategy`, `range` o `retryHistoryKey` aborta la consulta previa y pide `getPlayerQuotes(id, { strategy, ...rangeToDates(range, now) })`; exponer `strategy`, `range`, `setStrategy`, `setRange`, `history` y `retryHistory`; el historial y la variación se piden en paralelo tras el detalle, hasta que pase T026
- [X] T030 [US2] Implementar `frontend/src/features/player-detail/QuoteChart.jsx` + `QuoteChart.css` con props `{ points, status, strategy, range, league, onStrategyChange, onRangeChange, onRetry }`: `section` con heading "Evolución de la cotización"; dos `fieldset`/`legend` ("Estrategia", "Rango") con radios nativos (de `STRATEGIES` y `QUOTE_RANGES`) estilizados como pestañas con foco visible; según `status`: carga (`aria-busy` + "Cargando evolución…"), error (`EstadoPanel` stampVariant error, "No pudimos cargar la evolución", acción "Reintentar"), vacío (`EstadoPanel` sello "Sin datos", "Todavía sin cotizaciones") o `figure` con `figcaption` "{STRATEGIES[strategy].label} · {QUOTE_RANGES[range].label} · {n} cotizaciones"; dentro, `ResponsiveContainer` (altura 280) + `LineChart` con `accessibilityLayer`, `role="img"` y `aria-label` igual al caption, `XAxis dataKey="t" type="number" scale="time" domain={['dataMin', 'dataMax']}` con `formatDate`, `YAxis` con `formatCredits`, `CartesianGrid`, `Line dataKey="value"` con `dot` e `isAnimationActive` desactivado si `prefers-reduced-motion`, y `Tooltip content={<QuoteTooltip sameDay={hasSameDayPoints(points)} />}`, donde `QuoteTooltip` es un componente exportado del mismo archivo (fecha con `formatDateTime` si `sameDay`, si no `formatDate`; valor `formatCredits`; `STRATEGIES[strategy].label`; "Versión {version}"); colores por CSS con `--quote-line: var(<colorVar de la liga>)` o `--tinta-2` en `.quote-chart .recharts-line-curve`, `.recharts-dot` y `.recharts-active-dot`, y ejes y grilla con `--tinta-2` y `--papel-2` (research R9); debajo, `<table className="quote-chart__table">` (oculta visualmente, T028) con el mismo caption y una fila por punto (`Fecha | Valor | Estrategia | Versión`, con `STRATEGIES[strategy].label` en Estrategia) generada desde el **mismo** `points`, hasta que pase T025
- [X] T031 [US2] Integrar `QuoteChart` en `frontend/src/pages/PlayerDetailPage.jsx` entre `PlayerHeader` y `StatsPanel`, pasando `league` del jugador y los callbacks del hook, y ajustar `PlayerDetailPage.css` para que el gráfico ocupe el ancho completo sin scroll horizontal en 360 px, hasta que pase T027
- [ ] T032 [US2] Correr T025–T027 sin warnings de recharts en la salida y validar manualmente quickstart V3–V5 y V8 (las 5 ligas con su color de línea)

**Checkpoint**: US1 + US2 cumplen el núcleo de §2.2 ("detalle y gráfico de evolución de cotización").

---

## Phase 5: User Story 3 - Consultar la cotización en una fecha puntual (Priority: P2)

**Goal**: Selector de fecha que consulta con `at` la cotización vigente al fin de ese día para la estrategia del gráfico.

**Independent Test**: Elegir `2026-10-10` y "Consultar" → se pide `?strategy=<actual>&at=2026-10-10` y se muestra valor, "Calculada el …", estrategia y versión; un 404 `quote_not_found` → "Sin cotización para esa fecha"; cambiar a Momentum descarta el resultado.

### Tests for User Story 3 (escribir primero, deben fallar) ⚠️

- [X] T033 [P] [US3] Agregar a `frontend/src/features/player-detail/usePlayerDetail.test.jsx` (bloque "at"): `queryAt('2026-10-10')` pide `{ strategy, at: '2026-10-10' }` sin `from`/`to`; `200` → `atQuery.status === 'found'` con la cotización; `ApiError` 404 → `'empty'`; otro error → `'error'`, y `retryAt` repite la misma fecha; una fecha mayor a `todayUtc(now)` no hace request y deja un error de validación; `setStrategy` aborta la consulta en curso y deja `atQuery` en `'idle'`
- [X] T034 [P] [US3] Agregar a `frontend/src/pages/PlayerDetailPage.test.jsx` (bloque "fecha puntual"): el `input` con label "Fecha" tiene `max="2026-10-27"`; completar fecha + "Consultar" muestra el valor `formatCredits`, "Calculada el {formatDate}", estrategia y "Versión N" en una región `aria-live="polite"`; un 404 muestra "Sin cotización para esa fecha" sin `EstadoPanel` de error de página; al cambiar el radio a "Momentum" el resultado desaparece

### Implementation for User Story 3

- [X] T035 [US3] Extender `frontend/src/features/player-detail/usePlayerDetail.js` con `atQuery` (`idle | loading | found | empty | error`, más `quote`, `date` y `validationError`), `queryAt(date)` (fecha obligatoria y ≤ hoy UTC; si no, `validationError` "Elegí una fecha de hoy o anterior" sin request) y `retryAt`, usando `AbortController`; cualquier `ApiError` 404 → `empty`; al cambiar `strategy`, abortar y volver a `idle` (FR-018), hasta que pase T033
- [X] T036 [US3] Implementar `frontend/src/features/player-detail/QuoteAtDate.jsx` + `QuoteAtDate.css`: `section` con heading "Cotización en una fecha" y `form` con `Field` (o `input type="date"` con `label` "Fecha", `max={todayUtc(now)}`, `required`), botón "Consultar" (`Button`) y región `aria-live="polite"` con el resultado: `found` (valor, "Calculada el …", `STRATEGIES[strategy].label`, "Versión N"), `empty` ("Sin cotización para esa fecha"), `error` (mensaje + "Reintentar"), `loading` ("Consultando…"), y el `validationError` asociado al input con `aria-describedby`; apilado en 360 px
- [ ] T037 [US3] Integrar `QuoteAtDate` en `frontend/src/pages/PlayerDetailPage.jsx` debajo de `QuoteChart` y correr T033–T034 hasta verlos en verde, más quickstart V6

**Checkpoint**: Consulta puntual funcional e independiente del rango del gráfico.

---

## Phase 6: User Story 4 - Estados de carga, error e inexistencia (Priority: P2)

**Goal**: Error con reintento, "Jugador inexistente" para 404, 400 e id inválido, sesión vencida delegada al manejo existente.

**Independent Test**: Con `fetch` mockeado: 500 → "No pudimos cargar el jugador" → "Reintentar" con 200 → ficha; 404 y `/jugadores/abc` → "Jugador inexistente" con "Volver al álbum" y sin "Reintentar"; 401 → redirección al ingreso.

### Tests for User Story 4 (escribir primero, deben fallar) ⚠️

- [X] T038 [P] [US4] Agregar a `frontend/src/features/player-detail/usePlayerDetail.test.jsx` (bloque "estados"): `'abc'`, `'0'` y `'9223372036854775808'` → `notFound` sin llamar a `fetch`; `ApiError` 404 y 400 → `notFound`; 500 y `NetworkError` → `error`; `retry()` → `loading` → `ready`; una respuesta abortada no cambia el estado
- [X] T039 [P] [US4] Agregar a `frontend/src/pages/PlayerDetailPage.test.jsx` (bloque "estados"): la carga muestra la región `aria-busy="true"` con "Cargando jugador…"; un 500 muestra `EstadoPanel` "No pudimos cargar el jugador" con "Reintentar", que lleva a la ficha completa; 404 de `/jugadores/999999` y la ruta `/jugadores/abc` muestran sello "404", "Jugador inexistente" y la acción "Volver al álbum" (navega a `/album`), y **no** muestran "Reintentar"; un 401 deriva en `/ingresar` por el `onUnauthorized` de la sesión, igual que en `AlbumPage.test.jsx`

### Implementation for User Story 4

- [X] T040 [US4] Completar la clasificación de errores del detalle en `frontend/src/features/player-detail/usePlayerDetail.js` según `contracts/quotes-client-contract.md`: `ApiError` 400 o 404 → `notFound`; 401 → el hook queda en `loading` hasta que la redirección de `onUnauthorized` lo desmonta; otros `ApiError` y `NetworkError` → `error`; `retry` vuelve a `loading` y repide con `retryKey`, hasta que pase T038
- [X] T041 [US4] Renderizar en `frontend/src/pages/PlayerDetailPage.jsx` los estados de página con `EstadoPanel`: `error` (stampVariant `error`, sello "Sin conexión", título "No pudimos cargar el jugador", acción "Reintentar" → `retry`) y `notFound` (sello "404", título "Jugador inexistente", texto explicativo, acción "Volver al álbum" → `navigate('/album')`, sin reintento), manteniendo visible el enlace de volver, hasta que pase T039
- [ ] T042 [US4] Validar manualmente quickstart V9, V10 y V12

**Checkpoint**: Todas las historias de esta feature completas.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Accesibilidad, responsive, calidad y cierre.

- [ ] T043 [P] Revisar accesibilidad de `frontend/src/pages/PlayerDetailPage.jsx` y de los componentes de `frontend/src/features/player-detail/`: un solo `h1`, secciones con `aria-labelledby`, radios y botones con nombre accesible y foco visible, dirección de la variación y liga transmitidas con texto además del color (FR-028–FR-030), y quickstart V11 con un lector de pantalla
- [ ] T044 [P] Verificar responsive en 360, 768 y 1280 px sin scroll horizontal de página (quickstart V7) y ajustar solo los `.css` de `frontend/src/features/player-detail/` y `frontend/src/pages/PlayerDetailPage.css`
- [X] T045 Correr `npm test`, `npm run lint` y `npm run build` en `frontend/` sin errores y sin warnings nuevos de consola en los tests; revisar con `npm test -- --coverage` que `quoteUtils.js`, `quotesApi.js`, `playerDetailApi.js`, `usePlayerDetail.js` y `VariationBadge.jsx` queden bien cubiertos para SonarCloud
- [X] T046 Confirmar con `git diff main --stat` que no cambiaron `frontend/src/api/playersApi.js`, `frontend/src/components/Figurita.jsx`, `frontend/src/components/Figurita.css`, `frontend/src/features/album/AlbumTable.jsx`, `frontend/src/layout/AppLayout.jsx` ni nada en `backend/`
- [ ] T047 Después del push que haga el usuario, verificar el run de GitHub Actions en `SUCCESS` y el Quality Gate de SonarCloud con como máximo 10 issues menores (§3.1, §3.2), y registrar el resultado en `specs/024-front-detalle-cotizacion/quickstart.md` (sección Cierre)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias. T002 bloquea todo lo que importe `recharts` (T003, T025, T030).
- **Foundational (Phase 2)**: depende de Setup y bloquea todas las historias.
- **US1 (Phase 3)**: depende de Foundational. Crea `usePlayerDetail.js` y `PlayerDetailPage.jsx`, que las demás historias extienden.
- **US2 (Phase 4)**: depende de Foundational y de T018 y T021 (hook y página base).
- **US3 (Phase 5)**: depende de US2 por la selección `strategy` del hook (T029). Se puede probar aislada vía el hook.
- **US4 (Phase 6)**: depende de T018 y T021; es independiente de US2 y US3, y puede hacerse antes que ellas.
- **US5**: sin tareas. La feature 023 hará clickeable cada fila de su `RankingPage` hacia `/jugadores/:id` y podrá reutilizar `VariationBadge` (`contracts/player-detail-ui-contract.md`).
- **Polish (Phase 7)**: depende de todas las historias.

### Within Each User Story

- Tests primero (deben fallar), después la implementación, la integración en la página y la verificación.
- `usePlayerDetail.js` y `PlayerDetailPage.jsx` se tocan en varias historias: esas tareas son secuenciales entre sí (sin [P]).

### Parallel Opportunities

- Foundational: T004–T007 juntos; después T008–T012 juntos (cinco archivos distintos).
- US1: T013–T016 juntos; T017, T019 y T020 juntos; después T018 → T021 → T022 → T023.
- US2: T025–T027 juntos; T028 en paralelo con T029.
- US3: T033 y T034 juntos.
- US4: T038 y T039 juntos.
- Polish: T043 y T044 juntos.

---

## Parallel Example: User Story 1

```text
# Tests (archivos distintos):
T013 VariationBadge.test.jsx
T014 PlayerDetailPage.test.jsx (bloque ficha)
T015 AlbumPage.test.jsx (navegación desde la figurita)
T016 routes.test.jsx (/jugadores/:id protegida)

# Presentadores (archivos distintos):
T017 components/VariationBadge.jsx/.css
T019 features/player-detail/PlayerHeader.jsx/.css
T020 features/player-detail/StatsPanel.jsx/.css
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 + Phase 2.
2. Phase 3 (US1): se puede navegar del Álbum a la ficha con cabecera, variación y estadísticas.
3. **Parar y validar** (T024, quickstart V1–V2).

### Incremental Delivery

1. MVP (US1).
2. US2: gráfico y tabla alternativa. Con esto queda cumplida la vista obligatoria de §2.2.
3. US4: robustez (se puede adelantar antes de US2 si conviene).
4. US3: consulta puntual.
5. Polish y cierre de CI y SonarCloud.

---

## Notes

- El usuario hace todos los commits; las tareas no incluyen `git commit` ni `git push`.
- Mantener el estilo existente: comentarios escasos, nombres en castellano para dominio y UI, presentadores sin conocimiento de la API.
- Si una tarea requiere tocar un archivo de la lista "no se modifica", parar y consultar.
