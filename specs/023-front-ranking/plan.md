# Implementation Plan: Pantalla de Ranking

**Branch**: `feature/front-ranking` | **Date**: 2026-10-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/023-front-ranking/spec.md` y directivas técnicas del usuario para `quotesApi`, `useRanking`, componentes, página, rutas y pruebas Vitest.

## Summary

Agregar la ruta protegida `/ranking` y habilitarla en ambos menús. La pantalla consumirá `GET /players/ranking` mediante un cliente `quotesApi` nuevo y un hook `useRanking` que administra filtros remotos, paginación, reintento y cancelación de pedidos superpuestos. El resultado se mostrará sin reordenarlo en una tabla semántica, con liga identificada por texto y color, todas las posiciones, créditos y un `VariationBadge` reutilizable para la feature 024.

La estrategia inicial se representará con el valor vacío visible como “Activa”: así el pedido omite `strategy` y deja que el backend resuelva su configuración real. `OFFICIAL` y `MOMENTUM` serán selecciones explícitas. `AlbumFilters` no se puede reutilizar sin cambios porque siempre renderiza equipo, búsqueda y selector de vista; se creará `RankingFilters` con el mismo lenguaje visual y los mismos catálogos. Se reutilizarán `Pager`, `EstadoPanel`, `Field`, `Button`, `LEAGUES`, `POSITIONS`, los formateadores existentes y `buildPageWindow`.

## Technical Context

**Language/Version**: JavaScript con módulos ES; React 19.2.8

**Primary Dependencies**: React Router DOM 7.18.4; `fetch` encapsulado por `api/httpClient.js`; componentes y tokens CSS propios del frontend

**Storage**: N/A; estado efímero en React, sin persistencia local ni cambios de backend

**Testing**: Vitest 5.0.2, jsdom 29.1.1 y Testing Library React 16.3.3; oxlint; build con Vite 8.3.0

**Target Platform**: Navegadores web modernos, diseño responsive desde 320 px hasta escritorio

**Project Type**: Aplicación web con frontend React y backend HTTP/REST separado; alcance productivo exclusivo en frontend

**Performance Goals**: En 20 consultas consecutivas contra backend local, sesión autenticada, caché caliente y páginas de hasta 20 elementos, al menos 19 muestran tabla o estado terminal en menos de 2 segundos; cada tiempo se mide desde la acción que inicia o reemplaza la consulta hasta la aparición de la tabla o estado terminal y se registra como evidencia

**Constraints**: Ruta autenticada; preservar orden y puesto del backend; filtros vacíos se omiten; UI 1-based y API 0-based; `percentageChange: null` no equivale a cero; pedidos superpuestos no pueden publicar datos obsoletos; durante loading desaparecen tabla/pager anteriores pero los filtros siguen habilitados para reemplazar y abortar; no hay navegación a jugador en esta entrega; no agregar dependencias; no modificar `AlbumFilters`; cumplir WCAG 2.2 AA (4.5:1 texto normal, 3:1 texto grande y controles)

**Scale/Scope**: Dos estrategias, cinco ligas, cuatro posiciones, páginas de 20 elementos por defecto y máximo contractual de 100; diez archivos productivos nuevos, cuatro suites de prueba nuevas, una suite existente ampliada y tres archivos existentes modificados de forma explícita

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

### Pre-design gate

| Principle | Status | Evidence |
|-----------|--------|----------|
| §2.2 Frontend independiente | PASS | Implementa la vista obligatoria de ranking como pantalla responsive y obtiene sus datos exclusivamente mediante el contrato HTTP/REST existente. |
| §3.1 Integración continua | **PENDING** | El diseño reutiliza `.github/workflows/ci.yml` y agrega validación local, pero el estado `SUCCESS` sólo puede verificarse después del push/PR. Es gate de cierre, no bloqueo de planificación o implementación. |
| §3.2 SonarCloud | **PENDING** | El diseño evita duplicación y genera cobertura, pero `PASSED`, ausencia de vulnerabilidades nuevas y menos de 10 issues sólo pueden verificarse después del análisis remoto. Es gate de cierre. |
| §4.1 Autenticación y autorización | PASS | `/ranking` queda dentro de `ProtectedRoute`; `request` adjunta el bearer token y mantiene el manejo compartido de 401. |
| §5.1 Endpoint mínimo de ranking | PASS | El frontend consume `GET /players/ranking` sin alterar su contrato ni recalcular datos. |
| §6.1 OpenAPI | PASS | Se consume el esquema ya documentado por la feature 018; esta entrega no cambia endpoints ni DTOs. |
| §7 Definition of Done | **PENDING** | El diseño cubre pruebas, lint, build y cobertura; el cierre queda pendiente de CI `SUCCESS`, SonarCloud `PASSED` y la evidencia externa de usabilidad SC-009. |

No hay violaciones constitucionales de diseño ni excepciones que justificar. CI, SonarCloud y SC-009 quedan explícitamente `PENDING`: no bloquean planificación ni implementación, pero sí el cierre de la feature. La especificación autoriza `frontend/src/routes/routes.test.jsx` exclusivamente para probar la ruta y navegación, además de los dos archivos productivos existentes.

### Post-design gate

**PASS para diseño; PENDING para cierre externo**. `research.md` resuelve la estrategia activa sin asumir una configuración, el contrato real `items` y el tratamiento de abortos; `data-model.md` no agrega persistencia; los contratos separan API y UI; `quickstart.md` valida ruta protegida, estados, accesibilidad, benchmark, test, lint y build. §3.1 y §3.2 permanecen `PENDING` hasta verificar GitHub Actions y SonarCloud después del push/PR; SC-009 permanece `PENDING` hasta reunir 10 participantes y evidencia.

## Project Structure

### Documentation (this feature)

```text
specs/023-front-ranking/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── ranking-api.md
│   └── ranking-ui.md
├── checklists/
│   └── requirements.md
└── tasks.md                    # futuro output de $speckit-tasks; no creado aquí
```

### Source Code (repository root)

```text
frontend/src/
├── App.jsx                                      # existente: agrega /ranking bajo ProtectedRoute
├── api/
│   ├── httpClient.js                            # existente, reutilizado sin cambios
│   ├── quotesApi.js                             # nuevo
│   └── quotesApi.test.js                        # nuevo
├── components/
│   ├── EstadoPanel.jsx                          # existente, reutilizado sin cambios
│   ├── Field.jsx                                # existente, reutilizado sin cambios
│   └── Button.jsx                               # existente, reutilizado sin cambios
├── domain/
│   └── catalogo.js                              # existente, reutilizado sin cambios
├── features/
│   ├── album/
│   │   ├── Pager.jsx                            # existente, reutilizado sin cambios
│   │   ├── Pager.css                            # existente, reutilizado sin cambios
│   │   └── albumUtils.js                        # existente: reutiliza buildPageWindow sin cambios
│   └── ranking/
│       ├── useRanking.js                        # nuevo
│       ├── useRanking.test.js                   # nuevo
│       ├── RankingFilters.jsx                   # nuevo
│       ├── RankingFilters.css                   # nuevo; misma línea visual que AlbumFilters
│       ├── RankingTable.jsx                     # nuevo
│       ├── RankingTable.css                     # nuevo
│       ├── VariationBadge.jsx                   # nuevo; reutilizable por feature 024
│       ├── VariationBadge.css                   # nuevo
│       └── VariationBadge.test.jsx              # nuevo; se ejecuta antes de integrarlo en la tabla
├── layout/
│   └── AppLayout.jsx                            # existente: habilita Ranking y asigna /ranking
├── pages/
│   ├── RankingPage.jsx                          # nuevo
│   ├── RankingPage.css                          # nuevo
│   └── RankingPage.test.jsx                     # nuevo
├── routes/
│   └── routes.test.jsx                          # existente: agrega casos protegidos de /ranking
└── utils/
    └── format.js                                # existente, reutilizado sin cambios
```

**Structure Decision**: Se mantiene la arquitectura frontend actual: transporte en `api`, estado remoto en un hook de feature, presentación especializada en `features/ranking`, composición de estados en `pages` y registro de ruta en `App`. No se toca el backend. Los estilos de filtros son nuevos pero reproducen el patrón visual de Álbum con selectores propios, evitando forzar props ficticias o modificar `AlbumFilters`.

## Design

### `quotesApi.getRanking`

- Firma: `getRanking({ strategy, league, position, page, size } = {}, { signal } = {})`.
- Construye `URLSearchParams` en orden estable: `strategy`, `league`, `position`, `page`, `size`.
- Omite estrategia, liga o posición cuando su valor es vacío; nunca envía parámetros vacíos porque el backend los rechaza.
- Incluye `page=0` mediante comparación explícita con `undefined`, no por truthiness; incluye `size` cuando está definido.
- Delega en `request('/players/ranking?...', { signal })` y devuelve la respuesta sin transformar.

### `useRanking`

- Estado inicial: filtros `{ strategy: '', league: '', position: '' }`, página UI `1`, tamaño fijo `20`, `status: 'loading'`, página de datos vacía, error nulo y contador de reintento.
- Cada efecto crea un `AbortController`, llama a `getRanking` traduciendo página UI `n` a API `n - 1`, y aborta al cambiar filtros/página, reintentar o desmontar.
- Tanto éxito como error comprueban `controller.signal.aborted`. Esto es obligatorio porque `httpClient` normaliza también los abortos a `NetworkError`.
- Un éxito conserva exactamente `items`, `rank` y orden del servidor; deriva `from`, `to`, página humana y ventana para el `Pager` sin ordenar ni paginar localmente.
- `changeFilter` actualiza un criterio, limpia error, pasa a carga y vuelve a página UI 1. `retry` conserva filtros y página, limpia error e incrementa la clave de efecto.
- Si una respuesta indica elementos totales pero la página pedida quedó fuera de rango, el hook corrige a la última página válida y mantiene carga hasta obtenerla; no presenta ese caso como ranking vacío.
- `effectiveStrategy` se infiere del primer elemento cuando existe. Con `filters.strategy === ''`, el selector muestra “Activa” y puede complementar la etiqueta con el código inferido; una respuesta vacía no permite conocer el código activo y conserva “Activa”.

### `RankingFilters`

- No reutiliza `AlbumFilters`: sus cuatro campos y toggle de vista son obligatorios en su implementación actual y no coinciden con Ranking.
- Renderiza tres `Field` select: Estrategia, Liga y Posición. Estrategia ofrece “Activa”, “OFFICIAL” y “MOMENTUM”; liga y posición usan `LEAGUES` y `POSITIONS`.
- Expone `value` y `onChange(name, value)`; no requiere equipo, búsqueda, vista ni cambios en componentes existentes.
- Usa identificadores `ranking-*`, un `section` con `aria-label="Filtros del ranking"` y CSS nuevo que replica bordes, espaciado, breakpoints y tokens de Álbum sin copiar controles irrelevantes.

### `RankingTable` y `VariationBadge`

- Tabla real con encabezados `scope="col"`: Puesto, Jugador, Equipo, Liga, Posición, Cotización y Variación.
- Itera `items` en el orden recibido y usa `player.id` como key. No agrega enlaces, handlers, `tabIndex`, roles interactivos ni cursor de acción.
- La liga muestra swatch con `LEAGUES[league].colorVar` y etiqueta textual; posiciones muestra todas las etiquetas de `POSITIONS` en un orden de presentación estable definido por el catálogo.
- Cotización usa `formatCredits`; puesto usa `formatInteger`.
- `VariationBadge` acepta `value` nullable: positivo = flecha/signo y verde; negativo = flecha/signo y rojo; cero = “Sin cambios” neutro; null = “Sin dato” neutro. Un formateador local `Intl.NumberFormat('es-AR', { maximumFractionDigits: 2 })` evita modificar `format.js`. Sus tests y su implementación preceden a `RankingTable`; no existe una representación provisional descartable.
- El badge incluye texto/signo y nombre accesible, por lo que el color nunca es el único canal. Sus estilos viven junto al componente para reutilizarlo en 024.
- El panel de tabla limita el overflow horizontal dentro del componente para no desplazar toda la página; nombres largos pueden envolver y todos los datos permanecen accesibles.

### `RankingPage`, navegación y estados

- Compone encabezado, `RankingFilters`, región de resultados, tabla y `Pager` existente.
- Carga: región `aria-busy="true"` con mensaje de carga, sin tabla, resultados anteriores ni paginador. Los filtros permanecen habilitados para iniciar una consulta reemplazante y abortar la vigente.
- Error: `EstadoPanel` con mensaje genérico y botón Reintentar; no muestra detalles de `ApiError`/`NetworkError`.
- Vacío: `EstadoPanel` con título exacto “Todavía no hay cotizaciones”, sin tratarlo como error.
- Listo: tabla y, cuando `totalElements > 0`, `Pager` con página humana, rango y ventana derivados por el hook. El cambio de página se valida y desplaza la región de resultados al inicio.
- `App.jsx` importa `RankingPage` y registra `<Route path="/ranking" element={<RankingPage />} />` dentro del `ProtectedRoute` existente.
- `AppLayout.jsx` cambia sólo el item ranking a `ruta: '/ranking', habilitado: true`; `Header` y `BottomNav` lo incorporan automáticamente.

### Test Strategy

- `quotesApi.test.js`: omisión de filtros vacíos, orden/query exactos, inclusión de `page=0`, encoding, forwarding de `signal` y respuesta sin transformación.
- `VariationBadge.test.jsx`: prueba primero suba, baja, cero y null, formato, texto accesible y clases de variante; el componente se implementa antes de integrarse en la tabla.
- `useRanking.test.js`: carga inicial, éxito, error y reintento, cambio de filtros con reset a página 1, traducción 1-based/0-based, conservación del orden, abort de pedido reemplazado y descarte de éxito/error obsoleto.
- `RankingPage.test.jsx`: orden de filas, encabezados, filas no interactivas, liga por texto/color, todas las posiciones, créditos, integración de las cuatro variaciones, nombres largos sin solapamiento/overflow global, estrategia activa/explicita, filtros combinados, reset de página, paginación, carga, vacío exacto, error y reintento con criterios actuales.
- `routes.test.jsx`: añade `/ranking` al arnés protegido, cubre redirección sin sesión/acceso con sesión y verifica `aria-current="page"` en Ranking para navegación desktop y mobile; la página/fetch se aísla para evitar pedidos pendientes.
- Validación final proporcional: suites focalizadas, suite completa con cobertura, lint y build; benchmark con 20 tiempos registrados; prueba externa SC-009 con responsable/evidencia; después del push/PR se verifican por separado GitHub Actions y SonarCloud. La cobertura alimenta SonarCloud mediante `lcov.info`.

## Complexity Tracking

No aplica: el Constitution Check no detectó violaciones.
