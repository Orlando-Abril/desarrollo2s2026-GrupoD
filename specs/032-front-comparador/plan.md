# Implementation Plan: Comparador de estrategias en el frontend

**Branch**: `032-front-comparador` (valor informado por setup-plan; rama Git actual: `feature/front-comparador`) | **Date**: 2026-10-31 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/032-front-comparador/spec.md` and user direction for context, selector, opportunities in Ranking, ActiveStrategyControl, and Vitest coverage.

## Summary

Agregar el modo personal «Ver con: Mercado actual / Momentum» a Álbum, Detalle y Portfolio. Mercado actual usa siempre el `marketValue` persistido, aunque provenga de OFFICIAL o MOMENTUM; los historiales se etiquetan con sus códigos reales. Mostrar oportunidades dentro de Ranking con enlace al panel de operación. En Ranking, ADMIN podrá confirmar el cambio de estrategia activa y recibirá un Toast que ofrece el recálculo existente. El PUT exitoso refresca configuración y oportunidades; el recálculo exitoso vuelve a refrescarlas junto a los datos de mercado. Se consumen exclusivamente los contratos de la 031 y se conserva la lógica de órdenes de la 029.

## Technical Context

**Language/Version**: JavaScript ES modules, React 19.2.8

**Primary Dependencies**: React Router 7.18.4, Recharts 3.10.1, Vite 8.3.0, cliente HTTP existente

**Storage**: Estado de React en memoria para el modo «Ver con» durante la sesión de navegación, inicial `MARKET_CURRENT` («Mercado actual»); sin persistencia nueva en el frontend. El backend 031 conserva estrategia activa, precio de mercado e historiales.

**Testing**: Vitest 5, React Testing Library, jsdom, build Vite y lint Oxlint; fixtures con al menos 20 jugadores y 3 posiciones, con resultados esperados calculados a mano; CI y SonarCloud del repositorio. La medición de usabilidad con 10 participantes es validación externa posterior a la implementación y no bloquea su inicio.

**Target Platform**: Navegadores web de escritorio y móviles, anchos de referencia 360/768/1440 px

**Project Type**: Aplicación web React que consume el backend por HTTP/REST

**Performance Goals**: Una solicitud adicional por página visible del Álbum, con hasta 100 ids; cambios de modo sin recargar la aplicación; no generar una solicitud por figurita

**Constraints**: Precio para operar siempre tomado de `marketValue` persistido, cualquiera sea `marketStrategy`; `active` puede diferir de `marketStrategy` cuando `pendingRecalculation=true`. Cambio global no recalcula automáticamente; sólo ADMIN ve el control. Cambios en archivos de Entrega 1 limitados a `layout/Header.jsx` y prop opcional de Figurita.

**Scale/Scope**: Cinco vistas afectadas: Álbum, Detalle, Portfolio, Ranking y sección de oportunidades dentro de Ranking; dos estrategias existentes.

## Constitution Check

*GATE: Passed before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Check | Result |
|-----------|-------|--------|
| §2.2 Frontend independiente | Todas las lecturas y el cambio global usan contratos HTTP/REST existentes de la 031; no se incorpora acceso directo a datos de backend. Catálogo, Detalle, Ranking, trading y Portfolio siguen integrados como vistas web responsivas. | PASS |
| §1.1 Consistencia del mercado | «Ver con» sólo cambia la presentación; `marketValue`, saldo, PPC y órdenes no se reescriben. El panel de la 029 conserva sus reglas. | PASS |
| §1.2 Multiestrategia | Los historiales identifican OFFICIAL y MOMENTUM por código, etiqueta y versión devueltos por la 031; «Mercado actual» es el valor persistido y su origen se lee por separado. | PASS |
| §4.1 Autenticación y autorización | El rol se consulta desde la identidad de la 028 y el servidor decide el PUT; una denegación 403 oculta el control para esa identidad. | PASS |
| §3.1 Build y CI | Se añaden pruebas Vitest de los flujos sensibles y se ejecutan build, test y lint con los scripts existentes; no se altera el workflow. | PASS |
| §3.2 SonarCloud | Diseño en módulos pequeños, reutilización de utilidades y componentes, sin duplicar lógica financiera; en implementación se debe verificar que el Quality Gate siga PASSED y el proyecto quede bajo 10 issues menores. | PASS, sujeto a verificación en CI |

**Revisión post diseño**: los contratos de UI no agregan endpoints ni mutaciones fuera del cambio ADMIN de la 031; la sección de oportunidades se integra a Ranking sin nueva ruta ni ítem de menú. El modo Mercado actual representa `marketValue`, mientras los gráficos muestran OFFICIAL/MOMENTUM como códigos de series. Tras PUT se refrescan configuración y oportunidades; tras recálculo también se refrescan precios. No hay violaciones que requieran Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/032-front-comparador/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── ui-contracts.md
└── tasks.md                 # generado en la fase siguiente
```

### Source Code (repository root)

```text
frontend/
├── src/
│   ├── api/
│   │   ├── strategyApi.js          # GET/PUT /strategies/active
│   │   └── comparisonApi.js        # /quotes/latest y /quotes/opportunities
│   ├── context/
│   │   └── StrategyViewContext.jsx # modo y lectura de configuración de estrategias
│   ├── components/
│   │   ├── Figurita.jsx            # prop alternativa opcional, excepción Entrega 1
│   │   └── StrategySelector.jsx
│   ├── layout/
│   │   └── Header.jsx              # selector, excepción Entrega 1
│   ├── features/
│   │   ├── album/
│   │   │   └── useLatestPageQuotes.js
│   │   ├── player-detail/
│   │   │   └── QuoteChart.jsx
│   │   ├── portfolio/
│   │   │   └── PositionsTable.jsx
│   │   ├── ranking/
│   │   │   └── OpportunitiesSection.jsx
│   │   └── admin/
│   │       └── ActiveStrategyControl.jsx
│   └── pages/
│       ├── AlbumPage.jsx
│       ├── PlayerDetailPage.jsx
│       ├── PortfolioPage.jsx
│       └── RankingPage.jsx
└── package.json
```

**Structure Decision**: Extender los módulos de UI existentes y añadir sólo el estado y los adaptadores de API necesarios. `StrategyViewContext` expone un store compartido mediante el valor por defecto del contexto y `useSyncExternalStore`; así Header y las páginas consumen la misma elección sin editar `App.jsx`, `main.jsx` ni `AppLayout.jsx`. Un Provider opcional permite aislar pruebas. El selector se muestra en Header sólo para una sesión autenticada en las tres rutas pertinentes. Sus opciones son `MARKET_CURRENT` y `MOMENTUM`; esta última usa la etiqueta de la estrategia MOMENTUM recibida por GET, mientras «Mercado actual» se acompaña del código real de `marketStrategy`. La estrategia `active` se muestra aparte si todavía hay recálculo pendiente. No se crea página ni navegación nueva para oportunidades.

## Phase 0: Research

Resuelto en [research.md](research.md): relación entre estrategia activa y precio vigente, forma de los contratos 031, manejo de páginas del Álbum, dos series en el gráfico existente, integración del panel y autorización ADMIN.

## Phase 1: Design & Contracts

El estado y las transiciones se describen en [data-model.md](data-model.md). Las entradas, salidas y estados observables de cada pieza se fijan en [contracts/ui-contracts.md](contracts/ui-contracts.md). La validación manual y automatizada está en [quickstart.md](quickstart.md). La fase siguiente genera `tasks.md`.
