---

description: "Tareas de implementación para la pantalla de Ranking"
---

# Tasks: Pantalla de Ranking

**Input**: Design documents from `/specs/023-front-ranking/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Las pruebas Vitest son obligatorias. Se escriben primero y se confirma que fallen por la capacidad ausente antes de implementar.

**Organization**: Las historias P1 se ordenan por dependencia técnica: US2 crea y prueba `VariationBadge` antes de que US1 lo integre en `RankingTable`, evitando una representación provisional descartable. US3 y US4 siguen en prioridad P2.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo con otras tareas del mismo punto de dependencia porque trabaja en archivos diferentes.
- **[Story]**: Historia de usuario trazada desde spec.md.
- Todas las tareas incluyen rutas exactas del repositorio.

## Path Conventions

- Aplicación web: `frontend/src/`.
- Documentación y evidencias de la feature: `specs/023-front-ranking/`.
- No se agregan dependencias ni cambios de backend.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirmar una base verde y preservar el alcance autorizado.

- [X] T001 Ejecutar la línea base `npm test`, `npm run lint` y `npm run build` desde `frontend/package.json`, y confirmar que los únicos archivos existentes autorizados son `frontend/src/App.jsx`, `frontend/src/layout/AppLayout.jsx` y `frontend/src/routes/routes.test.jsx`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Implementar el contrato de transporte compartido por todas las historias.

**CRITICAL**: Ninguna historia puede consultar datos hasta completar esta fase.

- [X] T002 [P] Escribir primero pruebas fallidas de `getRanking` para endpoint, respuesta sin transformar, signal, orden estable `strategy, league, position, page, size`, omisión de filtros vacíos, encoding e inclusión de `page=0` en `frontend/src/api/quotesApi.test.js`
- [X] T003 Implementar `getRanking({ strategy, league, position, page, size } = {}, { signal } = {})` con `URLSearchParams` y `request` en `frontend/src/api/quotesApi.js`; omitir `strategy`, `league` y `position` vacíos, e incluir `page`/`size` cuando sean distintos de `undefined`

**Checkpoint**: El consumidor HTTP está probado y devuelve sin transformar `{ items, page, size, totalElements, totalPages }`.

---

## Phase 3: User Story 2 - Interpretar valor y variación (Priority: P1)

**Goal**: Crear primero el componente reutilizable que distingue suba, baja, cero y ausencia de comparación, sin confundir null con 0.

**Independent Test**: Renderizar directamente `VariationBadge` con valores positivo, negativo, cero y null; comprobar formato, significado accesible, signo/icono y variante visual.

### Tests for User Story 2

> Escribir y ejecutar la prueba antes del componente; debe fallar por el archivo todavía ausente.

- [X] T004 [US2] Crear pruebas de `VariationBadge` para `value > 0`, `value < 0`, `value === 0` y `value === null`, exigiendo nombres accesibles “Subió”, “Bajó”, “Sin cambios” y “Sin dato” en `frontend/src/features/ranking/VariationBadge.test.jsx`

### Implementation for User Story 2

- [X] T005 [US2] Crear `VariationBadge` y sus estilos en `frontend/src/features/ranking/VariationBadge.jsx` y `frontend/src/features/ranking/VariationBadge.css`, con `Intl.NumberFormat('es-AR')`, `--verde`, `--rojo`, `--tinta-2`, signo/icono y cuatro estados semánticos; no integrar todavía la tabla

**Checkpoint**: US2 queda probado de forma aislada y `VariationBadge` está listo para que US1 lo consuma sin código provisional.

---

## Phase 4: User Story 1 - Consultar el ranking actual (Priority: P1) MVP

**Goal**: Un usuario autenticado abre Ranking desde ambos menús y ve, en orden del servidor, puesto, jugador, equipo, liga, posiciones, cotización y `VariationBadge`; las filas no navegan.

**Independent Test**: Con una respuesta conocida, acceder a `/ranking`, verificar orden y siete columnas, liga, posiciones, créditos, variaciones, nombres largos y filas no interactivas; sin sesión, verificar redirección, y con sesión comprobar `aria-current="page"` en desktop/mobile.

### Tests for User Story 1

> Escribir y ejecutar estas pruebas antes de la implementación; deben fallar por la capacidad todavía ausente.

- [X] T006 [P] [US1] Crear pruebas de carga exitosa y conservación exacta de `items`/`rank` del servidor en `frontend/src/features/ranking/useRanking.test.js`
- [X] T007 [P] [US1] Crear pruebas de pantalla para orden, encabezados, liga por texto/color, todas las posiciones, créditos, integración de los cuatro estados de `VariationBadge`, filas no interactivas y fixture con nombres largos dentro del wrapper sin overflow global en `frontend/src/pages/RankingPage.test.jsx`
- [X] T008 [P] [US1] Ampliar el arnés protegido con `/ranking` y probar redirección sin sesión, acceso con sesión y `aria-current="page"` para Ranking en navegación desktop y mobile en `frontend/src/routes/routes.test.jsx`

### Implementation for User Story 1

- [X] T009 [P] [US1] Implementar la carga inicial en `frontend/src/features/ranking/useRanking.js` con estado `loading | ready`, página UI inicial 1, size 20, AbortController por efecto y guardas de `controller.signal.aborted`, conservando orden y rank recibidos
- [X] T010 [P] [US1] Implementar `RankingTable` y estilos base en `frontend/src/features/ranking/RankingTable.jsx` y `frontend/src/features/ranking/RankingTable.css`, importando el `VariationBadge` ya probado; usar `player.id` como key, mostrar todas las posiciones y no agregar Link, onClick, tabIndex ni role interactivo
- [X] T011 [US1] Crear la composición inicial lista/carga de Ranking en `frontend/src/pages/RankingPage.jsx` y `frontend/src/pages/RankingPage.css`, usando `useRanking`, región `aria-busy`, `RankingTable` y formatos existentes sin reordenar datos
- [X] T012 [P] [US1] Habilitar `{ id: 'ranking', label: 'Ranking', numero: '02', ruta: '/ranking', habilitado: true }` en `frontend/src/layout/AppLayout.jsx` para Header y BottomNav
- [X] T013 [US1] Importar `RankingPage` y registrar `/ranking` como sibling de `/album` dentro del `ProtectedRoute` existente en `frontend/src/App.jsx`

**Checkpoint**: US1 + US2 entregan el MVP protegido, con variación definitiva, ambos menús activos y filas no clickeables.

---

## Phase 5: User Story 3 - Acotar la comparación (Priority: P2)

**Goal**: Elegir estrategia activa/explicita y combinar liga y posición, conservando selecciones y reiniciando la página.

**Independent Test**: Cambiar Activa, OFFICIAL/MOMENTUM, liga y posición; comprobar query, combinación, selección visible y regreso a página 1. Con items, mostrar el código efectivo; con página vacía, mantener sólo “Activa”.

### Tests for User Story 3

> Escribir primero las expectativas de filtros y confirmar que fallen con el hook/página de US1.

- [X] T014 [P] [US3] Extender `frontend/src/features/ranking/useRanking.test.js` con filtros iniciales `{ strategy: '', league: '', position: '' }`, opción Activa omitida, filtros explícitos, effectiveStrategy inferida sólo del primer item, respuesta vacía sin código inferido y reset de página UI a 1
- [X] T015 [P] [US3] Extender `frontend/src/pages/RankingPage.test.jsx` con selectores accesibles, filtros combinados, persistencia visual y etiquetas “Activa (OFFICIAL/MOMENTUM)” sólo con resultados y “Activa” en vacío

### Implementation for User Story 3

- [X] T016 [P] [US3] Crear `RankingFilters` y estilos equivalentes al patrón de Álbum en `frontend/src/features/ranking/RankingFilters.jsx` y `frontend/src/features/ranking/RankingFilters.css`, con `strategy: '' | 'OFFICIAL' | 'MOMENTUM'`, `league: '' | LeagueCode`, `position: '' | PositionCode`, labels e IDs `ranking-*`
- [X] T017 [US3] Extender `frontend/src/features/ranking/useRanking.js` con `changeFilter`, loading al cambiar, page=1 y `effectiveStrategy = items[0]?.strategy ?? null`, manteniendo `strategy === ''` para Activa y sin inferir código de una respuesta vacía
- [X] T018 [US3] Integrar `RankingFilters` y la etiqueta Activa/effectiveStrategy en `frontend/src/pages/RankingPage.jsx`, preservando filtros y sin modificar `frontend/src/features/album/AlbumFilters.jsx`

**Checkpoint**: US3 respeta la estrategia backend configurable y no inventa un código cuando el ranking está vacío.

---

## Phase 6: User Story 4 - Navegar páginas y recuperarse de estados alternativos (Priority: P2)

**Goal**: Paginar con numeración humana y resolver carga, vacío, error, reintento, abortos y páginas que dejan de existir.

**Independent Test**: Recorrer un resultado multipágina; durante carga confirmar tabla/pager ausentes y filtros habilitados; simular error/reintento, vacío, pedido reemplazado y página fuera de rango.

### Tests for User Story 4

> Agregar primero los escenarios y confirmar que fallen antes de extender hook y página.

- [X] T019 [P] [US4] Extender `frontend/src/features/ranking/useRanking.test.js` con error, retry, traducción 1-based/0-based, from/to/window, abort de pedido reemplazado, descarte de resolución obsoleta y corrección a última página válida
- [X] T020 [P] [US4] Extender `frontend/src/pages/RankingPage.test.jsx` con loading sin tabla/pager previos y filtros habilitados, límites del pager, error genérico, reintento con criterios/página actuales y vacío exacto “Todavía no hay cotizaciones”

### Implementation for User Story 4

- [X] T021 [US4] Completar `frontend/src/features/ranking/useRanking.js` con estados `loading | ready | error`, error nullable, retryKey, retry conservando criterios, metadatos `page >= 0`, `1 <= size <= 100`, `totalElements >= 0`, totalPages, from/to y `buildPageWindow`
- [X] T022 [US4] Integrar `EstadoPanel` para error/vacío y `Pager` para resultados listos en `frontend/src/pages/RankingPage.jsx`; durante loading ocultar tabla/pager anteriores y mantener `RankingFilters` habilitado para reemplazar y abortar la consulta vigente
- [X] T023 [US4] Completar responsive, loading, scroll contenido y estados visuales sin overflow global en `frontend/src/pages/RankingPage.css` y `frontend/src/features/ranking/RankingTable.css` para 320 px, 768 px y 1440 px

**Checkpoint**: US4 completa el recorrido resiliente; ninguna respuesta abortada u obsoleta reemplaza los criterios vigentes.

---

## Phase 7: Polish, Evidence & Closure Gates

**Purpose**: Validar accesibilidad, responsive, rendimiento, usabilidad externa y gates constitucionales. T024-T029 completan validación local; T030-T031 quedan PENDING hasta después del push/PR y no bloquean planificación o implementación, pero sí el cierre.

- [ ] T024 [P] Auditar WCAG 2.2 AA, labels, `scope="col"`, foco, `aria-busy`, `aria-live`, liga/variación no dependientes sólo de color y contraste mínimo 4.5:1 para texto normal y 3:1 para texto grande/controles en `frontend/src/features/ranking/RankingFilters.jsx`, `frontend/src/features/ranking/RankingTable.jsx`, `frontend/src/features/ranking/VariationBadge.jsx` y `frontend/src/pages/RankingPage.jsx`
- [ ] T025 [P] Validar manualmente 320 px, 768 px y 1440 px con nombres largos según `specs/023-front-ranking/quickstart.md`, confirmando ausencia de solapamiento y overflow horizontal global y ajustando sólo `frontend/src/features/ranking/*.css` o `frontend/src/pages/RankingPage.css` si falla
- [ ] T026 Ejecutar el benchmark SC-010 de 20 consultas según `specs/023-front-ranking/quickstart.md`, medir desde la acción que inicia/reemplaza la consulta hasta tabla/estado terminal, registrar los 20 tiempos y confirmar al menos 19 menores a 2.000 ms
- [ ] T027 Ejecutar o coordinar la validación externa SC-009 bajo responsabilidad del equipo y adjuntar evidencia de al menos 10 participantes en el pull request; si la muestra no está completa, mantener esta tarea y SC-009 explícitamente PENDING sin bloquear implementación
- [ ] T028 Ejecutar las suites focalizadas de `specs/023-front-ranking/quickstart.md` y corregir la causa raíz en archivos productivos o tests autorizados por `specs/023-front-ranking/plan.md`, sin debilitar aserciones válidas ni adaptar pruebas para ocultar defectos
- [ ] T029 Ejecutar `npm test -- --coverage`, `npm run lint` y `npm run build` desde `frontend/package.json`, confirmar `frontend/coverage/lcov.info` y dejar todos los gates locales en verde
- [ ] T030 Verificar después del push/PR que el workflow de `.github/workflows/ci.yml` finalice en `SUCCESS`; mantener esta tarea PENDING hasta disponer del enlace de evidencia y no cerrar la feature antes de completarla
- [ ] T031 Verificar después del push/PR que el análisis configurado por `sonar-project.properties` finalice en `PASSED`, sin vulnerabilidades nuevas y con menos de 10 issues; mantener esta tarea PENDING hasta disponer del enlace de evidencia y no cerrar la feature antes de completarla

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Sin dependencias.
- **Foundational (Phase 2)**: Depende de T001 y bloquea las historias.
- **US2 (Phase 3, P1)**: Depende de T001; crea `VariationBadge` antes de la tabla.
- **US1 (Phase 4, P1)**: Depende de T002-T005; integra transporte y badge para entregar el MVP completo.
- **US3 (Phase 5, P2)**: Depende del hook y página de US1; no depende de cambios adicionales de US2 porque el badge ya está completo.
- **US4 (Phase 6, P2)**: Depende de US1; la secuencia recomendada espera US3 para probar retry con todos los criterios.
- **Polish local (T024-T029)**: Depende de todas las historias implementadas.
- **Cierre externo (T030-T031 y evidencia final T027)**: Se verifica después del push/PR; no bloquea planificación o implementación, pero sí el cierre de la feature.

### User Story Dependencies

```text
Setup -> Foundation
   |         |
   -> US2 ---+-> US1 (MVP completo) -> US3 -> US4
                                      \------> US4 (base)
US1 + US2 + US3 + US4 -> Polish local -> Push/PR -> CI + SonarCloud + evidencia SC-009 -> Cierre
```

- **US2 (P1)**: Independiente tras setup; produce el badge reutilizable.
- **US1 (P1)**: Requiere transporte y badge; entrega ruta, tabla y navegación.
- **US3 (P2)**: Reutiliza hook/página de US1.
- **US4 (P2)**: Reutiliza hook/página y, para retry completo, filtros de US3.

### Parallel Opportunities

- T002 puede prepararse después de T001; T003 espera su prueba.
- T004-T005 son secuenciales por TDD y terminan antes de `RankingTable`.
- En US1, T006-T008 son paralelas; después, T009, T010 y T012 trabajan en archivos distintos.
- En US3, T014-T016 trabajan en archivos distintos; T017-T018 integran secuencialmente.
- En US4, T019-T020 son paralelas; T023 puede avanzar junto a T021 cuando el contrato visual está fijado.
- T024-T025 son auditorías paralelas; T030 y T031 son verificaciones externas separadas posteriores al push/PR.

## Parallel Example: User Story 2

```text
Task T004: prueba aislada en frontend/src/features/ranking/VariationBadge.test.jsx
Luego Task T005: componente en frontend/src/features/ranking/VariationBadge.jsx y .css
```

## Parallel Example: User Story 1

```text
Task T006: pruebas del hook en frontend/src/features/ranking/useRanking.test.js
Task T007: pruebas de pantalla y nombres largos en frontend/src/pages/RankingPage.test.jsx
Task T008: ruta, desktop/mobile y aria-current en frontend/src/routes/routes.test.jsx

Después de esas pruebas:
Task T009: hook en frontend/src/features/ranking/useRanking.js
Task T010: tabla que consume VariationBadge en frontend/src/features/ranking/RankingTable.jsx y .css
Task T012: navegación en frontend/src/layout/AppLayout.jsx
```

## Parallel Example: User Story 3

```text
Task T014: pruebas de estado/filtros en frontend/src/features/ranking/useRanking.test.js
Task T015: pruebas de controles y Activa vacía en frontend/src/pages/RankingPage.test.jsx
Task T016: filtros en frontend/src/features/ranking/RankingFilters.jsx y .css
```

## Parallel Example: User Story 4

```text
Task T019: pruebas de paginación/abort/retry en frontend/src/features/ranking/useRanking.test.js
Task T020: pruebas de estados y filtros habilitados en frontend/src/pages/RankingPage.test.jsx
Después, Task T021: estado remoto y Task T023: CSS responsive pueden avanzar sobre archivos distintos
```

## Implementation Strategy

### MVP First (P1: User Stories 2 + 1)

1. Completar T001-T003.
2. Completar T004-T005 para disponer del badge definitivo.
3. Completar T006-T013 para ruta, hook, tabla, página y navegación.
4. Ejecutar las suites focalizadas P1 y demostrar `/ranking` protegido sin código provisional.

### Incremental Delivery

1. Setup + Foundation -> cliente HTTP probado.
2. US2 -> variación reusable probada.
3. US1 -> ranking protegido y navegable (MVP completo P1).
4. US3 -> estrategia y filtros.
5. US4 -> paginación y resiliencia.
6. T024-T029 -> validación local y evidencia de rendimiento/usabilidad disponible.
7. Push/PR -> T030 GitHub Actions y T031 SonarCloud; cerrar sólo con gates externos y SC-009 completos.

## Notes

- Las filas permanecen sin Link, onClick, tabIndex ni role interactivo hasta feature 024.
- La opción Activa usa valor vacío; sólo comunica código efectivo cuando existen resultados y nunca lo infiere de una página vacía.
- Durante loading desaparecen tabla y pager anteriores; los filtros permanecen habilitados para reemplazar/abortar.
- `percentageChange: null` y `percentageChange: 0` son estados diferentes.
- CI, SonarCloud y SC-009 pueden permanecer PENDING durante implementación, pero impiden declarar la feature cerrada.
- No modificar backend, `AlbumFilters`, componentes compartidos, catálogos, Pager, utilidades ni configuración de CI/SonarCloud.
