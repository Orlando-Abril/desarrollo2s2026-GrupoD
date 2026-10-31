---

description: "Dependency-ordered implementation tasks for the Mi portfolio frontend"
---

# Tasks: Pantalla Mi portfolio

**Input**: Design documents from `specs/030-front-portfolio/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [frontend-contracts.md](contracts/frontend-contracts.md), [quickstart.md](quickstart.md)

**Tests**: La feature pide explícitamente tests Vitest. En cada historia, escribir y ejecutar primero las pruebas indicadas, comprobar que fallen por la capacidad todavía ausente y recién después implementar.

**Organization**: Las tareas están agrupadas por historia de usuario. Las historias P1 se implementan primero; las P2 conservan el orden del spec y la P3 cierra los requisitos transversales.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo porque trabaja en archivos distintos y no depende de otra tarea incompleta del mismo grupo.
- **[Story]**: historia del spec a la que aporta la tarea.
- Todos los paths son relativos a la raíz del repositorio.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: verificar la base existente y preparar datos de prueba compartidos sin agregar dependencias.

- [X] T001 Ejecutar la línea base `npm test`, `npm run lint` y `npm run build` definida en `frontend/package.json`, y documentar cualquier falla preexistente antes de tocar código en `specs/030-front-portfolio/tasks.md`
- [X] T002 [P] Crear builders de prueba para cuenta, portfolio, posiciones y páginas de operaciones con overrides seguros en `frontend/src/features/portfolio/portfolioTestData.js`
- [X] T003 Agregar únicamente `frontend/src/features/portfolio/portfolioTestData.js` a `sonar.test.inclusions` en `sonar-project.properties`, verificar que Sonar lo clasifique como test y confirmar con el listado de Vitest que sólo `*.test.js`/`*.test.jsx` con suites reales se descubren como tests

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: construir el acceso HTTP, formato financiero, proyecciones y carga inicial que usan todas las historias.

**⚠️ CRITICAL**: ninguna historia puede completarse hasta terminar esta fase.

### Tests foundational — escribir y comprobar que fallen primero

- [X] T004 [P] Crear tests del contrato cliente para `/auth/me`, id codificado, portfolio, `page=0`, `size=20`, orden de query, signal y payload intacto en `frontend/src/api/portfolioApi.test.js`
- [X] T005 [P] Crear tests de formato `es-AR` con exactamente 4 decimales, signo explícito y fecha-hora en `frontend/src/features/portfolio/portfolioFormat.test.js`
- [X] T006 [P] Crear tests de proyección que conserven orden y valores contractuales, clasifiquen `profitLoss > 0` como `gain`, `< 0` como `loss`, `= 0` como `neutral`, y calculen `share` sólo con total finito mayor que cero en `frontend/src/features/portfolio/portfolioViewModel.test.js`
- [X] T007 Crear tests del bootstrap `/auth/me` → portfolio e historial en paralelo, sin requests antes del id y con abort al desmontar, en `frontend/src/features/portfolio/usePortfolio.test.jsx`

### Implementation foundational

- [X] T008 Implementar `getCurrentAccount`, `getUserPortfolio` y `getUserTransactions` sobre `request`, `encodeURIComponent` y `URLSearchParams` en `frontend/src/api/portfolioApi.js`
- [X] T009 [P] Implementar créditos a 4 decimales, resultados con signo/etiqueta y fecha-hora coherente con la aplicación en `frontend/src/features/portfolio/portfolioFormat.js`
- [X] T010 [P] Implementar proyecciones de resumen, rendimiento y distribución sin recalcular totales financieros en `frontend/src/features/portfolio/portfolioViewModel.js`
- [X] T011 Implementar en `frontend/src/features/portfolio/usePortfolio.js` la resolución única de identidad y los efectos iniciales abortables de portfolio y transacciones, con página backend 0 y tamaño 20

**Checkpoint**: el cliente, formato, proyecciones y hook inicial están listos; los recursos aún no tienen toda su presentación ni recuperación avanzada.

---

## Phase 3: User Story 1 - Comprender la posición patrimonial actual (Priority: P1) 🎯 MVP

**Goal**: mostrar saldo, invertido, valor actual, resultado y tabla de posiciones con ganancia/pérdida comprensible.

**Independent Test**: con dos posiciones de resultados distintos, abrir `/portfolio` y comprobar los cuatro indicadores y cada fila contra el payload, incluidos 4 decimales, saldo reservado/disponible y estado gain/loss/neutral.

### Tests for User Story 1 — escribir y comprobar que fallen primero

- [X] T012 [P] [US1] Crear tests de `PositionsTable` para tokens enteros, PPC, cotización, valor actual, 4 decimales y clases/etiquetas/signos de ganancia, pérdida y neutro en `frontend/src/features/portfolio/PositionsTable.test.jsx`
- [X] T013 [P] [US1] Crear tests de `PortfolioPage` en estado ready para Saldo, Invertido, Valor actual, Resultado y desglose de `reservedCredits`/`availableBalance` sin doble suma en `frontend/src/pages/PortfolioPage.test.jsx`
- [X] T014 [P] [US1] Actualizar tests de rutas para mockear `PortfolioPage`, exigir `/portfolio` protegida y marcar Mi portfolio actual en navegación principal y móvil en `frontend/src/routes/routes.test.jsx`

### Implementation for User Story 1

- [X] T015 [US1] Implementar `PositionsTable` con columnas Jugador, Tokens, Precio promedio, Cotización, Valor actual y Ganancia/Pérdida, usando `playerId` como key, en `frontend/src/features/portfolio/PositionsTable.jsx`
- [X] T016 [P] [US1] Crear los estilos de tabla, números tabulares y tokens `--verde`, `--rojo`, `--tinta-2` para resultados semánticos en `frontend/src/features/portfolio/PositionsTable.css`
- [X] T017 [US1] Implementar la composición ready de resumen y posiciones, consumiendo `usePortfolio` y sin recalcular totales, en `frontend/src/pages/PortfolioPage.jsx`
- [X] T018 [P] [US1] Crear layout base, tarjetas de resumen y jerarquía visual consistente en `frontend/src/pages/PortfolioPage.css`
- [X] T019 [US1] Agregar sólo el import y `<Route path="/portfolio">` autorizados en `frontend/src/App.jsx`, y cambiar sólo el ítem `portfolio` a `ruta: '/portfolio', habilitado: true` en `frontend/src/layout/AppLayout.jsx`

**Checkpoint**: `/portfolio` autenticada muestra una fotografía patrimonial completa y testeable con payload exitoso. Éste es el MVP sugerido.

---

## Phase 4: User Story 5 - Recuperarse de carga, ausencia de datos y errores (Priority: P1)

**Goal**: distinguir carga, vacío y error, reintentar sólo el recurso fallido y conservar el otro recurso válido.

**Independent Test**: simular carga lenta, portfolio vacío con y sin historial, falla de portfolio y falla de historial; verificar texto, ausencia de ceros falsos y requests de reintento independientes.

### Tests for User Story 5 — escribir y comprobar que fallen primero

- [X] T020 [P] [US5] Ampliar tests del hook con error/retry independiente de identidad, portfolio e historial, respuesta tardía descartada, datos previos marcados como refreshing y 401 delegado al cliente global en `frontend/src/features/portfolio/usePortfolio.test.jsx`
- [X] T021 [P] [US5] Ampliar tests de página para carga sin ceros, texto exacto “Todavía no compraste tokens”, acción al álbum, historial visible con posiciones vacías, errores parciales y reintentos focalizados en `frontend/src/pages/PortfolioPage.test.jsx`

### Implementation for User Story 5

- [X] T022 [US5] Completar estados `identityStatus`, `portfolioStatus`, `transactionsStatus`, errores, refreshing y retries separados, descartando handlers con `signal.aborted`, en `frontend/src/features/portfolio/usePortfolio.js`
- [X] T023 [US5] Implementar con `EstadoPanel` y estados perceptibles la carga de identidad, carga sin ceros, vacío de portfolio, vacío de historial y errores parciales con reintento en `frontend/src/pages/PortfolioPage.jsx`
- [X] T024 [US5] Estilizar estados de carga, vacío, error y aviso de datos no actualizados sin ocultar secciones válidas en `frontend/src/pages/PortfolioPage.css`

**Checkpoint**: la pantalla ya es segura frente a datos incompletos; el historial puede verse aunque no haya posiciones y una falla parcial no borra el recurso sano.

---

## Phase 5: User Story 2 - Analizar composición y rendimiento (Priority: P2)

**Goal**: comparar PPC contra cotización y visualizar la distribución por valor actual con alternativas tabulares exactas.

**Independent Test**: con tres posiciones, comprobar que gráfico, leyenda y tabla alternativa conservan los mismos valores/orden que las posiciones; verificar 60/40, una posición al 100% y total cero sin porcentaje inválido.

### Tests for User Story 2 — escribir y comprobar que fallen primero

- [X] T025 [P] [US2] Crear tests de `PerformanceChart` con mock dimensional de `ResponsiveContainer`, caption/rol, ambas leyendas y tabla alternativa Jugador/Precio promedio/Cotización visible al abrir “Ver datos en tabla” con teclado en `frontend/src/features/portfolio/PerformanceChart.test.jsx`
- [X] T026 [P] [US2] Crear tests de `AllocationDonut` para tabla alternativa desplegable con teclado, 60/40, una posición 100%, nombres repetidos, total cero sin `role="img"` ni porcentajes inválidos, y suma presentada dentro de 0,1 puntos porcentuales de 100% en `frontend/src/features/portfolio/AllocationDonut.test.jsx`

### Implementation for User Story 2

- [X] T027 [P] [US2] Implementar barras agrupadas, tooltip, leyenda HTML y tabla alternativa desde el mismo arreglo dentro de `<details><summary>Ver datos en tabla</summary>` en `frontend/src/features/portfolio/PerformanceChart.jsx`, con estilos en `frontend/src/features/portfolio/PerformanceChart.css`
- [X] T028 [P] [US2] Implementar dona por `currentValue`, paleta determinística, tooltip, leyenda, tabla alternativa dentro de `<details><summary>Ver datos en tabla</summary>` y explicación para total no finito o `<= 0` en `frontend/src/features/portfolio/AllocationDonut.jsx`, con estilos en `frontend/src/features/portfolio/AllocationDonut.css`
- [X] T029 [US2] Escribir primero las expectativas de integración que exigen ambos gráficos sólo con posiciones, `totalCurrentValue` contractual y ausencia de gráficos en vacío en `frontend/src/pages/PortfolioPage.test.jsx`, y comprobar que fallen antes de integrar
- [X] T030 [US2] Integrar `PerformanceChart` y `AllocationDonut` sólo cuando existan posiciones, usando `totalCurrentValue` contractual, en `frontend/src/pages/PortfolioPage.jsx`

**Checkpoint**: las dos visualizaciones son consistentes con la tabla, exactas mediante su alternativa tabular y no aparecen en el estado vacío.

---

## Phase 6: User Story 3 - Revisar el historial de operaciones (Priority: P2)

**Goal**: listar compras/ventas ejecutadas y navegar páginas de 20 elementos sin duplicados, omisiones ni carreras.

**Independent Test**: con 25 operaciones, recorrer primera y segunda página, volver, comprobar orden/contenido y verificar límites, loading y corrección de página fuera de rango.

### Tests for User Story 3 — escribir y comprobar que fallen primero

- [X] T031 [P] [US3] Ampliar tests del hook para traducir páginas humanas a backend base cero, cargar sólo historial, deshabilitar navegación durante loading, abortar reemplazos, ignorar respuestas tardías y corregir página fuera de rango incluso con `totalPages = 0` en `frontend/src/features/portfolio/usePortfolio.test.jsx`
- [X] T032 [US3] Agregar en `frontend/src/features/portfolio/usePortfolio.test.jsx` un test que genere en memoria 1.000 operaciones sin dumps ni datos externos, simule todas las páginas, las recorra completas y verifique total 1.000, IDs únicos, cero omisiones/repeticiones y orden global descendente por fecha e id
- [X] T033 [P] [US3] Crear tests de `TransactionsTable` para COMPRA/VENTA, cantidad, precio/total a 4 decimales, fecha-hora, metadatos y botones anterior/siguiente en límites y loading en `frontend/src/features/portfolio/TransactionsTable.test.jsx`
- [X] T034 [P] [US3] Ampliar tests de página para historial vacío, error, página conservada y actualización del historial sin desmontar resumen/posiciones/gráficos en `frontend/src/pages/PortfolioPage.test.jsx`

### Implementation for User Story 3

- [X] T035 [US3] Implementar `page` humana, `setPage`, metadata, request `page - 1`, retry de página vigente y corrección a última página válida en `frontend/src/features/portfolio/usePortfolio.js`
- [X] T036 [P] [US3] Implementar tabla/card de operaciones y controles accesibles `Página X de Y · Z operaciones` con botones bloqueados durante carga en `frontend/src/features/portfolio/TransactionsTable.jsx` y `frontend/src/features/portfolio/TransactionsTable.css`
- [X] T037 [US3] Integrar `TransactionsTable` y sus estados independientes en `frontend/src/pages/PortfolioPage.jsx`

**Checkpoint**: el historial completo puede recorrerse de forma estable sin recargar ni ocultar el portfolio.

---

## Phase 7: User Story 4 - Continuar desde una posición al jugador (Priority: P2)

**Goal**: abrir la ficha correcta desde cualquier posición u operación mediante enlaces reales operables con teclado.

**Independent Test**: activar con teclado y mouse nombres de jugadores repetidos en posiciones y operaciones, y comprobar href por `playerId` sin confundir filas.

### Tests for User Story 4 — escribir y comprobar que fallen primero

- [X] T038 [P] [US4] Ampliar tests de posiciones para exigir links `/jugadores/{playerId}` correctos aun con nombres repetidos en `frontend/src/features/portfolio/PositionsTable.test.jsx`
- [X] T039 [P] [US4] Ampliar tests de operaciones para exigir links `/jugadores/{playerId}` correctos y nombres accesibles en `frontend/src/features/portfolio/TransactionsTable.test.jsx`

### Implementation for User Story 4

- [X] T040 [P] [US4] Convertir el nombre de cada posición en `Link` con destino por `playerId` y foco visible en `frontend/src/features/portfolio/PositionsTable.jsx` y `frontend/src/features/portfolio/PositionsTable.css`
- [X] T041 [P] [US4] Convertir el nombre de cada operación en `Link` con destino por `playerId` y foco visible en `frontend/src/features/portfolio/TransactionsTable.jsx` y `frontend/src/features/portfolio/TransactionsTable.css`

**Checkpoint**: posiciones e historial conectan inequívocamente con la ficha donde se puede operar.

---

## Phase 8: User Story 6 - Responsive y accesible (Priority: P3)

**Goal**: completar todos los flujos a 360 px, con teclado, lector de pantalla, movimiento reducido y sin depender del color.

**Independent Test**: recorrer resumen, tablas, gráficos, links, paginación y reintentos sólo con teclado/lector de pantalla y a 360 px, verificando alternativas tabulares y ausencia de scroll horizontal de página.

### Tests for User Story 6 — escribir y comprobar que fallen primero

- [X] T042 [P] [US6] Ampliar tests de ambos gráficos para `figure`/`figcaption`, `role="img"`, nombres completos, leyenda textual, `<details>`/`<summary>` visible y operable con teclado, tablas alternativas y `prefers-reduced-motion` sin animación en `frontend/src/features/portfolio/PerformanceChart.test.jsx` y `frontend/src/features/portfolio/AllocationDonut.test.jsx`
- [X] T043 [P] [US6] Ampliar tests de posiciones y operaciones para scopes, encabezados, `data-label`, nombres accesibles, foco operable y anuncios de paginación en `frontend/src/features/portfolio/PositionsTable.test.jsx` y `frontend/src/features/portfolio/TransactionsTable.test.jsx`
- [X] T044 [P] [US6] Ampliar tests de página para regiones/encabezados, `aria-busy`, anuncios de carga/error y conservación de foco lógico en `frontend/src/pages/PortfolioPage.test.jsx`

### Implementation for User Story 6

- [X] T045 [US6] Completar semántica de figuras, captions, tooltips, leyendas, tablas alternativas, contraste WCAG 2.2 AA de elementos gráficos y desactivar animaciones con `matchMedia('(prefers-reduced-motion: reduce)')` en `frontend/src/features/portfolio/PerformanceChart.jsx`, `frontend/src/features/portfolio/PerformanceChart.css`, `frontend/src/features/portfolio/AllocationDonut.jsx` y `frontend/src/features/portfolio/AllocationDonut.css`
- [X] T046 [US6] Implementar filas tipo tarjeta con etiquetas a menos de 900 px, números sin desborde, foco visible y contraste WCAG 2.2 AA mínimo 4,5:1 para texto normal y 3:1 para texto grande y controles en `frontend/src/features/portfolio/PositionsTable.css` y `frontend/src/features/portfolio/TransactionsTable.css`
- [X] T047 [US6] Completar layout sin overflow horizontal desde 360 px, grid responsive de gráficos y estados/anuncios accesibles en `frontend/src/pages/PortfolioPage.css` y `frontend/src/pages/PortfolioPage.jsx`
- [X] T048 [US6] Ejecutar y registrar la validación manual de teclado, lector de pantalla, tablas alternativas desplegables, contraste WCAG 2.2 AA, 360 px, nombres largos y movimiento reducido siguiendo `specs/030-front-portfolio/quickstart.md`

**Checkpoint**: todas las historias son utilizables en escritorio/móvil y con tecnologías de asistencia.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: validar la feature completa contra calidad, alcance y contratos.

- [X] T049 [P] Ejecutar la suite focalizada indicada en `specs/030-front-portfolio/quickstart.md`; ante una falla, crear una tarea correctiva con el archivo exacto antes de modificar cualquiera de los paths enumerados en `specs/030-front-portfolio/plan.md`
- [X] T050 Ejecutar `npm test`, `npm run lint` y `npm run build` desde `frontend/package.json`, resolver issues nuevos y confirmar que no se agregaron dependencias
- [X] T051 [P] Revisar consistencia final contra `specs/030-front-portfolio/contracts/frontend-contracts.md` y actualizar ese contrato y `specs/030-front-portfolio/quickstart.md` si la implementación aprobada requiere una corrección documental
- [X] T052 Auditar el diff final para confirmar cero cambios backend y que en archivos protegidos sólo se modificaron la ruta de `frontend/src/App.jsx` y el ítem portfolio de `frontend/src/layout/AppLayout.jsx`
- [ ] T053 Ejecutar como validación final SC-001 con al menos 10 participantes, medir identificación de Saldo/Invertido/Valor actual/Resultado en menos de 10 segundos y completar únicamente la evidencia anonimizada y el resultado agregado en `specs/030-front-portfolio/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup**: sin dependencias.
- **Phase 2 Foundational**: depende de Setup y bloquea todas las historias.
- **Phase 3 US1 (P1)**: depende de Foundational; entrega el MVP de lectura exitosa.
- **Phase 4 US5 (P1)**: depende de US1 porque agrega estados a la misma página/hook; convierte el MVP en una pantalla recuperable.
- **Phase 5 US2 (P2)**: depende de US1 para posiciones y resumen; puede avanzar en paralelo con US3 una vez terminada US1.
- **Phase 6 US3 (P2)**: depende de Foundational y de la carcasa de página de US1; puede avanzar en paralelo con US2.
- **Phase 7 US4 (P2)**: depende de que `PositionsTable` (US1) y `TransactionsTable` (US3) existan.
- **Phase 8 US6 (P3)**: depende de todas las superficies funcionales US1, US5, US2, US3 y US4.
- **Phase 9 Polish**: depende de todas las historias incluidas en la entrega.

### User Story Dependency Graph

```text
Setup → Foundational → US1 ──→ US5
                         ├──→ US2 ──┐
                         └──→ US3 ──┴──→ US4 ──→ US6 ──→ Polish
```

### Within Each User Story

- Las tareas de tests se escriben y ejecutan primero; deben fallar por la capacidad ausente.
- Proyecciones/estado antes de componentes que los consumen.
- Componentes antes de integración en `PortfolioPage`.
- Cada checkpoint se valida antes de avanzar a la siguiente prioridad.

### Parallel Opportunities

- T004–T006 pueden escribirse en paralelo; T009 y T010 pueden implementarse en paralelo después de sus tests.
- En US1, T012–T014 son paralelas; T016 y T018 trabajan estilos distintos.
- En US5, tests de hook y página (T020–T021) son paralelos.
- US2 permite desarrollar y probar PerformanceChart y AllocationDonut en paralelo (T025–T028).
- US2 y US3 pueden ejecutarse en paralelo después de US1 si se coordinan los cambios finales en `PortfolioPage`.
- En US3, T031, T033 y T034 son paralelas; T032 continúa en el mismo archivo de T031; hook y tabla (T035–T036) trabajan archivos distintos.
- En US4, links de posiciones y operaciones son completamente paralelos (T038–T041).
- En US6, los tres grupos de tests T042–T044 son paralelos.

---

## Parallel Execution Examples

### User Story 1

```text
Task T012: tests de PositionsTable en frontend/src/features/portfolio/PositionsTable.test.jsx
Task T013: tests ready de PortfolioPage en frontend/src/pages/PortfolioPage.test.jsx
Task T014: tests de ruta/menús en frontend/src/routes/routes.test.jsx
```

### User Story 5

```text
Task T020: tests de errores/retries del hook en frontend/src/features/portfolio/usePortfolio.test.jsx
Task T021: tests de carga/vacío/error de página en frontend/src/pages/PortfolioPage.test.jsx
```

### User Story 2

```text
Task T025 → T027: PerformanceChart test e implementación
Task T026 → T028: AllocationDonut test e implementación
```

### User Story 3

```text
Task T031 → T035: paginación y carreras en usePortfolio
Task T032: recorrido automatizado de 1.000 operaciones en usePortfolio.test.jsx después de T031
Task T033 → T036: tabla y controles de transacciones
Task T034: integración de historial en PortfolioPage
```

### User Story 4

```text
Task T038 → T040: navegación desde posiciones
Task T039 → T041: navegación desde operaciones
```

### User Story 6

```text
Task T042: accesibilidad y movimiento reducido de gráficos
Task T043: semántica responsive de tablas
Task T044: regiones y anuncios de página
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Completar Setup y Foundational.
2. Completar US1 y validar su checkpoint con payload exitoso.
3. Detenerse y demostrar resumen + posiciones en `/portfolio`.
4. Completar inmediatamente US5 antes de considerar la pantalla lista para producción, porque carga/error/vacío son P1.

### Incremental Delivery

1. **US1**: fotografía patrimonial y posiciones — MVP funcional.
2. **US5**: carga, vacío, errores parciales y reintentos — MVP robusto.
3. **US2**: análisis visual con tablas alternativas.
4. **US3**: historial paginado.
5. **US4**: navegación directa al jugador.
6. **US6**: cierre responsive y accesible transversal.
7. **Polish**: suite, lint, build, contratos y auditoría de alcance.

### Parallel Team Strategy

Después de Foundational y US1:

- Integrante A: US5 y coordinación final de `PortfolioPage`.
- Integrante B: US2 (gráficos).
- Integrante C: US3 (historial/hook).
- Tras integrar US3, US4 se divide entre posiciones y operaciones; US6 se realiza sobre todas las superficies terminadas.

---

## Notes

- `[P]` sólo marca trabajo en archivos distintos sin dependencia incompleta.
- No modificar `SessionContext`; el id se resuelve con `/auth/me` dentro de `portfolioApi.js`/`usePortfolio.js`.
- No cambiar backend, OpenAPI ni contratos de la 028.
- `utils/format.js` conserva 2 decimales para pantallas existentes; esta feature usa `portfolioFormat.js` con exactamente 4.
- No afirmar cumplimiento de una §3.4 inexistente; aplicar §2.2, §3.2, §3.3 y DoD §7 según el plan.
- Cada tarea debe dejar sus tests focalizados en verde antes del checkpoint.
