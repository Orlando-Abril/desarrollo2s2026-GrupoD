# Tasks: Comparador de estrategias en el frontend

**Input**: `specs/032-front-comparador/spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/ui-contracts.md`, `quickstart.md`.

**Prerequisites**: Features 025, 028, 029 y 031 disponibles. Los tests Vitest están pedidos expresamente en el plan del usuario.

**Organization**: Una fase por historia, con pruebas antes de implementación. Ninguna tarea autoriza cambios a archivos de Entrega 1 fuera de `frontend/src/layout/Header.jsx` y la prop opcional de `frontend/src/components/Figurita.jsx`. Los estilos nuevos van en archivos nuevos.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede hacerse en paralelo con otras tareas marcadas [P] de la misma fase si no comparten archivo ni dependen de tareas incompletas.
- **[Story]**: Historia de `spec.md`.
- Todas las rutas son relativas a la raíz del repositorio.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Preparar datos de prueba y verificar las integraciones existentes sin reinicializar el proyecto.

- [X] T001 Crear fixtures de respuestas 031 en `frontend/src/test/strategyFixtures.js`: al menos 20 jugadores con `marketValue`, cotización MOMENTUM y diferencias absolutas/porcentuales esperadas calculadas a mano (incluidos origen OFFICIAL, origen MOMENTUM y recálculo pendiente); al menos 3 posiciones con costo, saldo, valuación y resultado esperados calculados a mano; incluir cotización nula y estados `CALCULATED`/`NO_STATS`/`INSUFFICIENT_HISTORY`.
- [X] T002 Registrar en `specs/032-front-comparador/quickstart.md` la línea base de `npm test`, `npm run lint` y `npm run build` ejecutados desde `frontend/`, sin alterar configuración ni dependencias existentes.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Contratos HTTP y estado compartido que usan varias historias.

- [X] T003 [P] Escribir y ejecutar hasta observar falla por funcionalidad ausente las pruebas de GET/PUT `/strategies/active` en `frontend/src/api/strategyApi.test.js`: cuerpo PUT `{strategy: code}`, respuesta con `active`/`alternative`/`marketStrategy`/`pendingRecalculation` y propagación 400/403/red.
- [X] T004 Implementar `getActiveStrategy` y `changeActiveStrategy` en `frontend/src/api/strategyApi.js` después de T003; no inferir que `active` ya fijó `marketValue` cuando `pendingRecalculation=true`.
- [X] T005 [P] Escribir y ejecutar hasta observar falla por funcionalidad ausente las pruebas de `/quotes/latest` y `/quotes/opportunities` en `frontend/src/api/comparisonApi.test.js`: `playerIds` únicos de 1 a 100, `strategy=MOMENTUM` explícita y filtros `league`/`position` opcionales.
- [X] T006 Implementar `getLatestQuotes` y `getOpportunities` en `frontend/src/api/comparisonApi.js` después de T005; rechazar ids vacíos, más de 100 ids distintos y no enviar filtros vacíos.
- [X] T007 [P] Escribir y ejecutar hasta observar falla por funcionalidad ausente las pruebas del estado compartido en `frontend/src/context/StrategyViewContext.test.jsx`: `viewStrategy` inicial `MARKET_CURRENT`, cambios sin PUT, suscripción de dos consumidores, carga/error/reintento/refresh y reinicio al cambiar identidad.
- [X] T008 Implementar `StrategyViewContext` en `frontend/src/context/StrategyViewContext.jsx` después de T004 y T007: `viewStrategy` limitado a `MARKET_CURRENT | MOMENTUM`, `activeConfig` nullable, `configStatus` `idle | loading | ready | error`, store de módulo con `useSyncExternalStore`, Provider opcional para tests, reintento/refresh y reinicio por identidad; no editar `App.jsx`, `main.jsx` ni `AppLayout.jsx`.

**Checkpoint**: Los adaptadores y el estado compartido pasan sus pruebas y no crean una mutación al cambiar el modo personal.

---

## Phase 3: User Story 1 - Comparar precios en Catálogo y Detalle (Priority: P1) 🎯 MVP

**Goal**: Selector persistente, cotización Momentum por página del Álbum y dos curvas en Detalle, con precio de mercado inequívoco.

**Independent Test**: Con dos jugadores de precios conocidos, alternar modo en Álbum/Detalle, navegar entre ellos y verificar diferencias, gráfico y precio del panel; Mercado actual conserva el precio persistido incluso cuando su origen es MOMENTUM.

### Tests for User Story 1

- [X] T009 [P] [US1] Escribir tests de `StrategySelector` y Header en `frontend/src/components/StrategySelector.test.jsx` y `frontend/src/layout/Header.test.jsx`: «Mercado actual / Momentum», etiquetas de estrategia desde GET, origen real de `marketValue`, caso `active≠marketStrategy` pendiente, visibilidad en tres vistas, navegación persistente, error/reintento y ausencia de PUT.
- [X] T010 [P] [US1] Escribir tests de `useLatestPageQuotes` en `frontend/src/features/album/useLatestPageQuotes.test.jsx`: sólo ids de `album.items` (1–100 distintos), una solicitud por página Momentum, ninguna en Mercado actual, cancelación de respuesta obsoleta, `quote:null` distinto de error.
- [X] T011 [P] [US1] Ampliar tests de `Figurita`, `AlbumPage` y `AlbumTable` en `frontend/src/components/Figurita.test.jsx`, `frontend/src/pages/AlbumPage.test.jsx` y `frontend/src/features/album/AlbumTable.test.jsx`: usar los al menos 20 jugadores de T001 para verificar todas las diferencias absolutas/porcentuales y precio de mercado esperados; aspecto por defecto idéntico, cuatro y dos decimales, estado no calculado y falta de cotización.
- [X] T012 [P] [US1] Ampliar tests de `QuoteChart` y `PlayerDetailPage` en `frontend/src/features/player-detail/QuoteChart.test.jsx` y `frontend/src/pages/PlayerDetailPage.test.jsx`: dos series con códigos reales OFFICIAL/MOMENTUM por fecha sin ceros inventados, tabla/leyenda accesibles, rango común, errores parciales y precio de operación persistido con recálculo pendiente.

### Implementation for User Story 1

- [X] T013 [P] [US1] Crear `StrategySelector` y estilos en `frontend/src/components/StrategySelector.jsx` y `frontend/src/components/StrategySelector.css`; opciones «Mercado actual / Momentum», etiqueta real de `marketStrategy` para el precio persistido, `active` separada si hay recálculo pendiente, estado de carga/error y control accesible.
- [X] T014 [US1] Montar `StrategySelector` sólo para sesión autenticada y rutas `/album`, `/jugadores/:id`, `/portfolio`; sincronizar reinicio de contexto al cambiar token/identidad en `frontend/src/layout/Header.jsx`, sin otros cambios de Entrega 1.
- [X] T015 [P] [US1] Implementar `useLatestPageQuotes` en `frontend/src/features/album/useLatestPageQuotes.js` con ids de página visible, consulta sólo en modo Momentum, `strategy=MOMENTUM` explícita, `AbortController`, mapa id→quote/null, reintento y estados `idle | loading | ready | error`.
- [X] T016 [P] [US1] Agregar sólo prop opcional `alternativeQuote` a `frontend/src/components/Figurita.jsx` y estilos alternativos en `frontend/src/features/album/AlternativeQuote.css`; sin prop, DOM y aspecto actual idénticos; con prop, precio de mercado, diferencia, estado y ausencia se etiquetan.
- [X] T017 [US1] Conectar hook y estado de error/reintento a `frontend/src/pages/AlbumPage.jsx`, `frontend/src/features/album/AlbumGrid.jsx` y `frontend/src/features/album/AlbumTable.jsx`; la tabla debe mostrar la misma comparación que las tarjetas sin consulta adicional.
- [X] T018 [P] [US1] Crear unión de historiales por instante en `frontend/src/features/player-detail/compareQuoteSeries.js` y sus pruebas en `frontend/src/features/player-detail/compareQuoteSeries.test.js`; conservar puntos ausentes como null, ordenar por fecha y mantener estado/versión por estrategia.
- [X] T019 [US1] Ampliar `frontend/src/features/player-detail/usePlayerDetail.js` para cargar OFFICIAL y MOMENTUM en el mismo rango al elegir Momentum, cancelar consultas anteriores y conservar en Mercado actual el flujo habitual de una serie, etiquetada con su código real.
- [X] T020 [US1] Ampliar `frontend/src/features/player-detail/QuoteChart.jsx` y `frontend/src/features/player-detail/QuoteChart.css` con dos líneas, leyenda, tooltip y tabla de ambas estrategias; ocultar selector local de una serie sólo en modo Momentum y conservar rango/reintento.
- [X] T021 [US1] Conectar modo y contexto de mercado a `frontend/src/pages/PlayerDetailPage.jsx` y `frontend/src/features/player-detail/PlayerHeader.jsx`; en Mercado actual mostrar siempre `marketValue`, el código real `marketStrategy` y aviso si `active≠marketStrategy` por recálculo pendiente, sin modificar precio ni reglas de `TradingSection`.

**Checkpoint**: US1 se demuestra sola con Álbum/Detalle; no se necesita Portfolio ni Ranking para validar el MVP.

---

## Phase 4: User Story 2 - Valuar el Portfolio con Momentum (Priority: P1)

**Goal**: Presentar por posición y total los valores y resultados alternativos sin perder los importes de mercado.

**Independent Test**: Con al menos tres posiciones de resultados conocidos, alternar Mercado actual/Momentum y comprobar la query, la columna y los totales; una cotización ausente deja los totales comparativos no disponibles.

### Tests for User Story 2

- [X] T022 [P] [US2] Ampliar tests del adaptador en `frontend/src/api/portfolioApi.test.js` para consulta sin `strategy` en Mercado actual y `?strategy=MOMENTUM` en Momentum, sin afectar transacciones.
- [X] T023 [P] [US2] Ampliar tests de `usePortfolio` y `PortfolioPage` en `frontend/src/features/portfolio/usePortfolio.test.jsx` y `frontend/src/pages/PortfolioPage.test.jsx`: usar al menos tres posiciones de T001 para verificar valor, resultado y patrimonio alternativos contra cálculos manuales; cancelación al alternar y datos de mercado inalterados.
- [X] T024 [P] [US2] Ampliar tests de `PositionsTable` en `frontend/src/features/portfolio/PositionsTable.test.jsx`: `quotePrice`, `quoteStatus`, `quoteCalculatedAt`, `value`, `profitLoss` nullable juntos si falta cotización; `positionsWithoutQuote > 0` exige totales `null` y texto «No disponibles».

### Implementation for User Story 2

- [X] T025 [P] [US2] Extender `getUserPortfolio` en `frontend/src/api/portfolioApi.js` con `strategy` opcional; omitir la query en Mercado actual y no cambiar `getUserTransactions`.
- [X] T026 [US2] Hacer que `frontend/src/features/portfolio/usePortfolio.js` observe `viewStrategy`, consulte `strategy=MOMENTUM` sólo en ese modo y descarte respuestas antiguas al alternar o cambiar identidad.
- [X] T027 [US2] Añadir columna alternativa y etiquetas de estado/ausencia a `frontend/src/features/portfolio/PositionsTable.jsx` y `frontend/src/features/portfolio/PositionsTable.css`, conservando visibles mercado, cantidad y costo.
- [X] T028 [US2] Añadir resumen de `strategyValuation.totalValue`, `totalProfitLoss`, `totalEquity` y `positionsWithoutQuote` a `frontend/src/pages/PortfolioPage.jsx` y `frontend/src/pages/PortfolioPage.css`; los totales null se muestran no disponibles, no como cero.

**Checkpoint**: US2 funciona con el estado compartido; el historial de transacciones permanece igual.

---

## Phase 5: User Story 3 - Encontrar oportunidades y operar (Priority: P1)

**Goal**: Dos listas filtrables en Ranking y acceso al panel existente del jugador correcto.

**Independent Test**: Preparar una oportunidad subvaluada y otra sobrevaluada, filtrar, pulsar «Operar» y comprobar ruta, foco, panel y precio de mercado.

### Tests for User Story 3

- [X] T029 [P] [US3] Escribir tests de oportunidades en `frontend/src/features/ranking/OpportunitiesSection.test.jsx`: dos listas, filtros propios, orden del servidor, etiquetas de estrategia, carga/vacío/error/reintento y enlace `/jugadores/{playerId}#operar`.
- [X] T030 [P] [US3] Ampliar tests de `RankingPage` y `PlayerDetailPage` en `frontend/src/pages/RankingPage.test.jsx` y `frontend/src/pages/PlayerDetailPage.test.jsx`: sección sin nuevo ítem de menú, navegación «Operar», panel visible/enfocado y precio de mercado vigente.

### Implementation for User Story 3

- [X] T031 [P] [US3] Implementar `OpportunitiesSection` con consulta independiente, filtros liga/posición, listas separadas y estados de carga/vacío/error en `frontend/src/features/ranking/OpportunitiesSection.jsx` y `frontend/src/features/ranking/OpportunitiesSection.css`; usar respuesta 031 sin reclasificarla.
- [X] T032 [US3] Montar `OpportunitiesSection` bajo el ranking y conectar recarga tras recálculo en `frontend/src/pages/RankingPage.jsx` y `frontend/src/pages/RankingPage.css`; no modificar `NAV_ITEMS` ni crear ruta nueva.
- [X] T033 [US3] Añadir ancla `#operar` y destino de foco accesible al panel existente en `frontend/src/features/trading/TradingSection.jsx` y `frontend/src/pages/PlayerDetailPage.jsx`, sin cambiar envío, estimación o validación de órdenes.

**Checkpoint**: US3 se valida en Ranking y Detalle sin el control ADMIN.

---

## Phase 6: User Story 4 - Cambiar la estrategia activa desde Ranking (Priority: P1)

**Goal**: Control exclusivo de ADMIN con confirmación, efecto diferido claro, errores recuperables y acceso al botón de recálculo de la 025.

**Independent Test**: USER no ve control; ADMIN cancela y confirma; comprobar un PUT, Toast, recálculo voluntario, 403, 400 y error de red.

### Tests for User Story 4

- [X] T034 [P] [US4] Escribir tests de `ActiveStrategyControl` en `frontend/src/features/admin/ActiveStrategyControl.test.jsx`: rol de `GET /auth/me`, USER oculto, ADMIN con valor de GET, cancelación sin PUT, confirmación única, éxito `changed=true/false`, Toast sin recálculo automático, 403, 400 y red con GET de reconciliación.
- [X] T035 [P] [US4] Ampliar tests de integración de `RankingPage` y `RecalculateButton` en `frontend/src/pages/RankingPage.test.jsx` y `frontend/src/features/ranking/RecalculateButton.test.jsx`: control adyacente, acción del Toast que lleva al botón existente, GET de estrategia y GET de oportunidades después de PUT exitoso, y nueva recarga de ambos más datos de mercado tras recálculo.

### Implementation for User Story 4

- [X] T036 [P] [US4] Crear `ActiveStrategyControl` con lectura de rol, confirmación, bloqueo de doble envío, PUT y mensajes claros en `frontend/src/features/admin/ActiveStrategyControl.jsx` y `frontend/src/features/admin/ActiveStrategyControl.css`; 403 oculta por identidad, 400 corrige y red consulta GET antes de afirmar estado.
- [X] T037 [US4] Ampliar `frontend/src/features/ranking/RecalculateButton.jsx` para exponer una referencia enfocables al botón de la 025 y conservar su confirmación/progreso; el Toast del control ADMIN dirige allí sin invocar recálculo por sí solo.
- [X] T038 [US4] Montar `ActiveStrategyControl` junto al botón de recálculo en `frontend/src/pages/RankingPage.jsx`; después de PUT exitoso volver a consultar `GET /strategies/active` y `GET /quotes/opportunities` aunque el precio siga pendiente, y después del recálculo volver a consultar ambos junto a `ranking.reload`; mantener independencia del selector «Ver con».

**Checkpoint**: US4 prueba todo el ciclo de cambio global y deja intacto el precio de mercado hasta el recálculo.

---

## Phase 7: User Story 5 - Usar la comparación con asistencia (Priority: P2)

**Goal**: Completar flujos en móvil, teclado y lector de pantalla.

**Independent Test**: Recorrer selector, gráfico, oportunidades, «Operar», confirmación y Toast a 360 px y sólo con teclado/lector de pantalla.

### Tests for User Story 5

- [X] T039 [P] [US5] Añadir pruebas de nombres accesibles, foco al entrar en `#operar`, signo textual y anuncio de error/éxito en `frontend/src/components/StrategySelector.test.jsx`, `frontend/src/features/ranking/OpportunitiesSection.test.jsx`, `frontend/src/features/admin/ActiveStrategyControl.test.jsx` y `frontend/src/pages/PlayerDetailPage.test.jsx`.

### Implementation for User Story 5

- [X] T040 [US5] Ajustar estilos responsivos y foco visible en `frontend/src/components/StrategySelector.css`, `frontend/src/features/album/AlternativeQuote.css`, `frontend/src/features/player-detail/QuoteChart.css`, `frontend/src/features/portfolio/PositionsTable.css`, `frontend/src/features/ranking/OpportunitiesSection.css` y `frontend/src/features/admin/ActiveStrategyControl.css` para 360/768/1440 px sin scroll horizontal global.
- [X] T041 [US5] Revisar leyenda/tabla del gráfico, etiquetas de diferencias y mensajes de confirmación/Toast en `frontend/src/features/player-detail/QuoteChart.jsx`, `frontend/src/features/ranking/OpportunitiesSection.jsx` y `frontend/src/features/admin/ActiveStrategyControl.jsx` para que significado y resultado no dependan sólo del color.

**Checkpoint**: US5 se verifica con el recorrido de accesibilidad de `quickstart.md`.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Verificar regresiones, contratos, alcance autorizado y puertas de calidad.

- [X] T042 [P] Ejecutar `npm test`, `npm run lint` y `npm run build` desde `frontend/` y registrar resultados y cualquier corrección en `specs/032-front-comparador/quickstart.md`.
- [ ] T043 Validar manualmente los cuatro recorridos de `specs/032-front-comparador/quickstart.md`, incluidos caso de cambio activo pendiente, cotización ausente, 403/400/red y anchos 360/768/1440 px; registrar resultado en ese archivo.
- [X] T044 Revisar `git diff` de `frontend/src/layout/Header.jsx`, `frontend/src/components/Figurita.jsx` y demás archivos tocados contra FR-022 en `specs/032-front-comparador/tasks.md`; si aparece otro archivo protegido de Entrega 1, detener ese cambio y pedir autorización concreta antes de modificarlo.
- [ ] T045 Comprobar CI y Quality Gate de SonarCloud para la rama, con estado PASSED y menos de 10 issues menores según Constitution §3.2; anotar evidencia o bloqueo externo en `specs/032-front-comparador/quickstart.md`.
- [ ] T046 Registrar una **validación externa de usabilidad** con 10 participantes reales en `specs/032-front-comparador/quickstart.md`: por persona, medir si encontró una oportunidad, identificó el precio de operación y abrió el panel correcto sin asistencia en menos de 60 segundos; informar conteo de éxitos y verificar al menos 9/10. No simular participantes ni bloquear el inicio de implementación si la convocatoria está pendiente.

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup T001–T002 → Foundational T003–T008 → historias.
- En Foundational: T003 debe fallar por funcionalidad faltante antes de T004; T005 debe fallar antes de T006; T007 debe fallar antes de T008. T008 también depende de T004. Las pruebas de cada historia se escriben antes de sus tareas de implementación y deben fallar por el comportamiento faltante.
- Polish T042–T045 depende de las historias implementadas. T045 requiere resultado de CI externo. T046 es validación externa posterior y no bloquea el comienzo de ninguna fase de implementación.

### User Story Dependencies

| Story | Depends on | Independent completion criterion |
|-------|------------|----------------------------------|
| US1 | Foundational | Álbum/Detalle alternan modo; figura, gráfico y precio vigente son correctos. |
| US2 | Foundational para pruebas con contexto; US1 para alternancia manual en Header | Portfolio presenta columnas/totales Momentum exactos; se prueba aislado con `viewStrategy` provisto por el contexto. |
| US3 | Foundational | Ranking muestra oportunidades; «Operar» abre el panel del jugador. |
| US4 | Foundational | ADMIN cambia con confirmación y errores; USER no ve control. |
| US5 | US1–US4 para recorrido completo | Todos los flujos funcionan con teclado, asistencia y móvil. |

US2 puede implementarse y probarse con el contexto de Phase 2 antes de US1, pero su recorrido manual de selector requiere el Header terminado en US1. US3 y US4 no dependen de US1. T030 y T033 comparten `PlayerDetailPage` con US1, por lo que esas ediciones se coordinan secuencialmente. T032 y T038 comparten `RankingPage` y también se integran secuencialmente.

### Parallel Opportunities

- T003, T005 y T007 preparan pruebas en archivos distintos y pueden escribirse en paralelo. T004 sigue a T003; T006 sigue a T005; T008 sigue a T004 y T007.
- En US1, T009–T012 son pruebas separadas; T013, T015 y T016 crean componentes/hook/prop en archivos distintos; T018 puede avanzar separado del Álbum.
- En US2, T022–T024 cubren adaptador, hook/página y tabla por separado; T025 se puede implementar en paralelo con T027.
- En US3, T029 y T030 prueban componentes distintos; T031 puede implementarse separado del ancla T033.
- En US4, T034 y T035 preparan pruebas separadas; T036 puede avanzar separado de T037.
- En US5, las revisiones CSS de T040 pueden repartirse por archivo, después de que cada vista esté implementada.

### Parallel Example: User Story 1

```text
T009 StrategySelector/Header tests          || T010 useLatestPageQuotes tests
T011 Figurita/Album tests                    || T012 QuoteChart/Detail tests
T013 StrategySelector implementation         || T015 page quote hook
T016 optional Figurita prop                  || T018 chart series union
```

### Parallel Example: User Story 2

```text
T022 portfolio API tests                     || T024 PositionsTable tests
T025 optional portfolio query               || T027 comparison column
```

### Parallel Example: User Story 3

```text
T029 opportunities tests                     || T030 navigation integration tests
T031 opportunities section                   || T033 trading panel anchor
```

### Parallel Example: User Story 4

```text
T034 ActiveStrategyControl tests             || T035 Ranking/Recalculate integration tests
T036 ActiveStrategyControl implementation    || T037 recalculate focus target
```

### Parallel Example: User Story 5

```text
T039 accessibility assertions                || independent CSS files within T040
```

## Implementation Strategy

### MVP First (US1)

1. Completar Setup y Foundational.
2. Escribir las pruebas T009–T012 y confirmar que fallan por funciones faltantes.
3. Completar T013–T021.
4. Validar US1 en Álbum/Detalle antes de sumar Portfolio y Ranking.

### Incremental Delivery

1. US1: comparación de cotizaciones visible.
2. US2: efecto patrimonial hipotético.
3. US3: oportunidades y navegación al panel.
4. US4: cambio global para ADMIN y recálculo voluntario.
5. US5: recorrido de accesibilidad y tamaños; luego Polish.

## Notes

- `active` es la estrategia configurada; `marketStrategy` explica el origen del precio vigente hasta que el próximo recálculo actualice `marketValue`. Ninguna vista usa la cotización exploratoria como precio de orden.
- «Mercado actual» siempre representa `marketValue`, incluso si `marketStrategy=MOMENTUM`; las curvas históricas y el control ADMIN usan los códigos de estrategia reales. Después del PUT y después del recálculo se refrescan configuración y oportunidades.
- T046 documenta un resultado observado con participantes externos y no debe reemplazarse por fixtures ni tratarse como requisito previo para implementar.
- Los paths de tests existentes se amplían; los nuevos se crean junto al módulo. No se tocan archivos de Entrega 1 fuera de las dos excepciones autorizadas.
