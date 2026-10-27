# Implementation Plan: Detalle de jugador y evolución de su cotización

**Branch**: `feature/front-detalle-cotizacion` | **Date**: 2026-10-27 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/024-front-detalle-cotizacion/spec.md`

## Summary

Agregar la ruta protegida `/jugadores/:id` con la ficha de un jugador. La ficha tiene cuatro secciones:

- una cabecera con estética de figurita, que muestra la cotización actual y la variación respecto de la anterior;
- un gráfico de línea (recharts) de la evolución de la cotización, con selector de estrategia (OFFICIAL / MOMENTUM), selector de rango (30 días, 90 días o todo el historial) y una tabla alternativa oculta para lectores de pantalla;
- una consulta de la cotización en una fecha puntual;
- las estadísticas de la temporada.

**Capas.** El transporte vive en dos adaptadores nuevos: `playerDetailApi` y `quotesApi`. Todo el estado remoto y los descartes por `AbortController` se concentran en `usePlayerDetail`, igual que en `useAlbum`. Los cálculos puros (fechas de rango, variación, validación del id) van en `quoteUtils`. Los presentadores no conocen la API.

**Cambios mínimos autorizados:**
- se agrega `recharts`;
- se agrega la ruta en `App.jsx`;
- en `AlbumGrid` cada figurita se envuelve en un `Link`, sin tocar `Figurita` ni su aspecto.

**Ajuste al pedido.** `VariationBadge` y `RankingPage` todavía no existen (la 023 no empezó). Por eso esta feature crea `VariationBadge` como componente compartido, y las filas clickeables del Ranking quedan en la 023 ([research R12](./research.md#r12-variationbadge-y-la-feature-023)).

## Technical Context

**Language/Version**: JavaScript ES modules; Node.js 22 en CI; React 19.2.x, React Router DOM 7.18.x y Vite 8.3.x existentes

**Primary Dependencies**: se agregan `recharts@3.10.1` (exacta) y su peer `react-is@^19`, autorizados en la Entrega 1. Lo demás ya existe: `httpClient`, `EstadoPanel`, `Sello`, `Casillero`, `Button`, `Field`, `tokens.css`, `LEAGUES`/`POSITIONS` de `domain/catalogo.js` y `formatCredits`/`formatInteger` de `utils/format.js`. Esos archivos de Entrega 1 no se modifican: el catálogo y los formatos propios de la feature van en `features/player-detail/quoteCatalog.js` y `quoteFormat.js`.

**Storage**: Ninguno. La selección de estrategia, rango y fecha vive en memoria y no va a la URL (supuesto de la spec).

**Testing**: Vitest 5 + jsdom + Testing Library con mocks de `fetch`, como en `AlbumPage.test.jsx`. En jsdom se mockea `ResponsiveContainer` de recharts de forma inline dentro de `QuoteChart.test.jsx` y `PlayerDetailPage.test.jsx`, sin helper compartido ([R2](./research.md#r2-recharts-en-jsdom-vitest)). También corren Oxlint y el build de Vite.

**Target Platform**: Navegadores modernos, desde 360 px de ancho (el piso actual de la app). Backend local en `http://localhost:8080` con los endpoints de la 022 y la 017.

**Project Type**: SPA web en `frontend/`. No modifica el backend.

**Performance Goals**:
- cabecera, estadísticas y gráfico inicial visibles en menos de 2 s con conexión normal (SC-002);
- cambio de estrategia o rango en menos de 1 s (SC-003);
- a lo sumo 3 requests al abrir la ficha: detalle, historial del gráfico e historial de variación;
- se mantiene el timeout HTTP de 10 s.

**Constraints**:
- ningún `fetch` fuera de `httpClient`, y autenticación con Bearer JWT;
- no se modifican `playersApi.js`, `Figurita.jsx/.css`, `AlbumTable` ni el ítem "Ranking" del menú;
- los estilos solo usan `tokens.css`;
- sin scroll horizontal de página desde 360 px;
- la información de color (liga, suba o baja) también se transmite con texto o símbolo.

**Scale/Scope**: 1 ruta, 2 endpoints (3 modos de uso: detalle, historial y `at`), 2 estrategias, 3 rangos, alrededor de 50 cotizaciones por jugador y estrategia por temporada (recálculo semanal), 9 estadísticas y 8 estados de UI.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Gate constitucional | Pre-diseño | Post-diseño |
|---|---|---|
| §2.2 Frontend independiente: "detalle y gráfico de evolución de cotización" | PASS. Esta feature implementa exactamente esa vista obligatoria, en `frontend/`, solo por HTTP/REST. | PASS. Los contratos consumen `GET /players/{id}` (022) y `GET /players/{id}/quotes` (017) sin endpoints nuevos. La UI es responsive desde 360 px (quickstart V7). |
| §2.2 Comunicación solo por HTTP/REST y OpenAPI vigente | PASS. Se usan los OpenAPI de la 022 y la 017. | PASS. `quotesApi` y `playerDetailApi` delegan en `httpClient`, y el contrato cliente cita los YAML como fuente. |
| §1.2 Trazabilidad de valuación (versión de estrategia) | PASS. La spec exige mostrar estrategia y versión en el tooltip y en la tabla. | PASS. `QuotePoint` conserva `strategy` y `version`, y la tabla alternativa las muestra por fila. |
| §3.3 escenario 2: evolución temporal de cotizaciones en las 5 ligas | PASS. La vista hace observable ese escenario en la UI; el escenario en sí ya lo cubre el e2e de backend (021). | PASS. Los tests de página cubren historial con varios puntos, cambio de versión, ambas estrategias y los tres rangos. Quickstart V3 lo verifica con datos reales de una liga y V8 recorre las 5 ligas. |
| §3.1 CI en `SUCCESS` | PASS condicionado. Se agregan tests a la suite existente y no se cambia el workflow. | PASS por diseño. El quickstart exige `npm test`, `npm run lint` y `npm run build` en verde, y el run de CI en `SUCCESS` antes de cerrar. |
| §3.2 SonarCloud: Quality Gate y como máximo 10 issues menores | PASS condicionado. No se agregan exclusiones ni se toca la configuración. | PASS por diseño. La lógica pura queda en `quoteUtils` (alta cobertura y baja complejidad), los componentes son chicos, el mock de recharts vive dentro de los dos `*.test.jsx` que lo usan (ningún archivo de test queda fuera de `sonar.test.inclusions`, así que no aparece como fuente sin cubrir) y no hay código muerto. Hay que verificar el gate real antes del cierre. |
| §4.1 Autenticación JWT | PASS. La ruta va dentro de `ProtectedRoute` y el 401 usa el `onUnauthorized` existente. | PASS. El contrato prohíbe `X-API-KEY` desde el frontend. |
| §1.1, §2.3, §4.2, §5.2 (mercado, batch, auditoría, Redis) | N/A. Es una feature de lectura y no toca el backend. | N/A. |

**Resultado del gate**: PASS. No hay violaciones ni clarificaciones pendientes.

## Project Structure

### Documentation (this feature)

```text
specs/024-front-detalle-cotizacion/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── quotes-client-contract.md
│   └── player-detail-ui-contract.md
├── checklists/
│   └── requirements.md
└── tasks.md                          # lo crea después $speckit-tasks
```

### Source Code (repository root)

```text
frontend/
├── package.json                      # existente: + recharts@3.10.1, + react-is@^19
└── src/
    ├── App.jsx                       # existente: + <Route path="/jugadores/:id"> dentro de ProtectedRoute
    ├── api/
    │   ├── playerDetailApi.js        # nuevo: getPlayerDetail(id, {signal})
    │   ├── playerDetailApi.test.js   # nuevo
    │   ├── quotesApi.js              # nuevo: getPlayerQuotes(id, {strategy, from, to, at}, {signal})
    │   └── quotesApi.test.js         # nuevo
    ├── components/
    │   ├── VariationBadge.jsx        # nuevo, compartido (la 023 lo reutiliza)
    │   ├── VariationBadge.css        # nuevo
    │   └── VariationBadge.test.jsx   # nuevo
    ├── features/
    │   ├── album/
    │   │   ├── AlbumGrid.jsx         # existente: Link alrededor de cada Figurita
    │   │   └── AlbumGrid.css         # existente: .figurita-grid__link sin cambio visual + focus-visible
    │   └── player-detail/
    │       ├── usePlayerDetail.js        # nuevo: detalle, historial, variación y consulta puntual
    │       ├── usePlayerDetail.test.jsx  # nuevo
    │       ├── quoteUtils.js             # nuevo: parsePlayerId, rangeToDates, findPrevious, variation, hasSameDayPoints
    │       ├── quoteUtils.test.js        # nuevo
    │       ├── quoteCatalog.js           # nuevo: STRATEGIES, QUOTE_RANGES y STAT_FIELDS
    │       ├── quoteFormat.js            # nuevo: formatDate, formatDateTime, formatSignedCredits, formatPercent, formatDecimal
    │       ├── quoteFormat.test.js       # nuevo
    │       ├── PlayerHeader.jsx/.css     # nuevo: cabecera figurita + cotización + VariationBadge
    │       ├── QuoteChart.jsx/.css       # nuevo: selectores, recharts, figcaption y tabla alternativa (.quote-chart__table, oculta visualmente)
    │       ├── QuoteChart.test.jsx       # nuevo: paridad tabla/datos y tooltip con teclado
    │       ├── QuoteAtDate.jsx/.css      # nuevo
    │       └── StatsPanel.jsx/.css       # nuevo
    ├── pages/
    │   ├── PlayerDetailPage.jsx      # nuevo: useParams, key por id, composición y estados
    │   ├── PlayerDetailPage.css      # nuevo
    │   ├── PlayerDetailPage.test.jsx # nuevo: 404, id inválido, vacío, error y reintento, estrategia, rango, fecha puntual
    │   └── AlbumPage.test.jsx        # existente: + navegación de figurita a /jugadores/:id
    └── routes/
        └── routes.test.jsx           # existente: + /jugadores/:id protegida
```

**Structure Decision**: se sigue la estructura que fijó la 008. Los adaptadores HTTP van en `api/`, el estado y los presentadores de la feature en `features/player-detail/`, la composición en `pages/` y los componentes reutilizables entre features en `components/`. Por eso `VariationBadge` va en `components/`. `playersApi.js` no se toca: el detalle tiene su propio adaptador, como pidió el usuario. Tampoco se tocan `domain/catalogo.js`, `utils/format.js` ni `styles/base.css` (Entrega 1).

## Design notes

- **Ciclo de vida.** `PlayerDetailPage` lee `id` y renderiza `<PlayerDetail key={id} id={id} />`. Al cambiar de jugador se remonta todo y no se mezclan datos del anterior (edge case).
- **Orden de carga.** Primero se pide el detalle, porque la estrategia inicial del gráfico depende de `detail.quote?.strategy`. Si se pidiera el historial en paralelo, habría que adivinar la estrategia y a veces pedir dos veces. Con el detalle `ready` se lanzan en paralelo el historial del gráfico y el de variación (este último solo si hay `quote`). Son dos tandas de requests, compatibles con SC-002.
- **Estados por sección** (detalle en [data-model.md](./data-model.md#estados)):
  - si falla el detalle, la página muestra el error;
  - si falla el historial, solo el gráfico muestra error con reintento;
  - si falla la consulta puntual, solo esa sección muestra error;
  - si falla el historial de variación, se muestra "—" en silencio.
- **Accesibilidad.**
  - Cada sección es un `section` con `aria-labelledby`.
  - La carga se anuncia con `aria-busy` y un texto.
  - Los selectores de estrategia y rango son grupos de radio nativos (`fieldset`/`legend`) con estilo de pestañas, así son operables con flechas.
  - `document.title` pasa a ser `"<Nombre> · Figuritas"` cuando el detalle está listo.

## Complexity Tracking

Sin violaciones constitucionales que justificar.
