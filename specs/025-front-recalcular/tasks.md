---

description: "Tareas de implementación para el recálculo manual desde Ranking"
---

# Tasks: Recálculo manual desde Ranking

**Input**: Design documents from `/specs/025-front-recalcular/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: La solicitud exige pruebas Vitest para confirmación, progreso, éxito con Toast y recarga, 409, 403 y error de red. En cada historia se escriben y ejecutan primero los tests que deben fallar antes de implementar.

**Protected files**: No modificar `frontend/src/components/Button.jsx`, `Button.css`, `Toast.jsx`, `Toast.css`, `frontend/src/api/httpClient.js`, `frontend/src/session/SessionContext.jsx` ni ningún otro archivo de Entrega 1. Si una tarea pareciera exigirlo, detenerse y consultar al equipo indicando archivo, cambio mínimo y alternativa.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo con tareas del mismo bloque porque opera sobre archivos distintos y no depende de trabajo incompleto.
- **[Story]**: Historia de usuario trazada desde `spec.md`.
- Todas las tareas incluyen rutas exactas.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirmar una base verde y resolver el gate operativo antes de cambiar código.

- [X] T001 Ejecutar la suite frontend base definida en `frontend/package.json` y registrar cualquier falla preexistente en `specs/025-front-recalcular/quickstart.md` antes de implementar
- [X] T002 Ejecutar el Early timeout gate de `specs/025-front-recalcular/quickstart.md`, registrar tiempo/dataset/ambiente en ese archivo y detenerse para consultar al equipo si `POST /quotes/recalculate` tarda 10 segundos o más, sin modificar `frontend/src/api/httpClient.js`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Incorporar el único adaptador HTTP compartido por las tres historias.

**⚠️ CRITICAL**: No comenzar historias hasta que el contrato del adaptador esté cubierto y pase.

- [X] T003 Escribir primero tests fallidos de `recalculateQuotes({ strategy } = {}, { signal } = {})` en `frontend/src/api/quotesApi.test.js`: POST exacto, cuerpo omitido cuando strategy es ausente/null, cuerpo `{ strategy: 'OFFICIAL' }` sin normalizar cuando existe, forwarding de signal y propagación sin transformar de respuesta/error
- [X] T004 Implementar `recalculateQuotes` en `frontend/src/api/quotesApi.js` delegando exclusivamente en `request('/quotes/recalculate', { method: 'POST', ... })`, sin copiar filtros de Ranking ni usar fetch directo, hasta hacer pasar T003

**Checkpoint**: El frontend dispone del contrato POST tipado por comportamiento y los archivos de Entrega 1 permanecen intactos.

---

## Phase 3: User Story 1 - Recalcular y ver el ranking actualizado (Priority: P1) 🎯 MVP

**Goal**: Confirmar un recálculo completo, mostrar progreso y resumen, y recargar Ranking con los criterios vigentes.

**Independent Test**: Abrir Ranking, cancelar una confirmación y confirmar otra; comprobar cero POST al cancelar, un solo POST al confirmar, Button ocupado durante la espera, resumen completo al resolver y exactamente una recarga con estrategia/filtros/página actuales.

### Tests for User Story 1 ⚠️

> **NOTE: Escribir estos tests primero y confirmar que fallan antes de implementar.**

- [X] T005 [P] [US1] Ampliar `frontend/src/features/ranking/useRanking.test.js` con un test fallido para `reload`: conservar strategy/league/position/page vigentes, pasar a loading, abortar un GET reemplazado y emitir un solo GET nuevo sin publicar la respuesta obsoleta
- [X] T006 [P] [US1] Crear tests fallidos de éxito, guardia de doble envío y desmontaje en `frontend/src/features/ranking/useRecalculate.test.js`, verificando un solo POST, callback una sola vez, ningún callback tras abort propio y resultado `QuoteRunSummary` sin transformar
- [X] T007 [P] [US1] Crear tests fallidos de confirmación, cancelación, progreso y resumen en `frontend/src/features/ranking/RecalculateButton.test.jsx`, incluyendo `disabled`, `aria-busy`, `Recalculando…`, cierre manual y estas reglas literales: `strategies` y `skippedStrategies` pueden ser vacíos y se muestran como `Ninguna`; `playersProcessed`, `playersFailed`, `calculated`, `noStats` e `insufficientHistory` son mayores o iguales a 0 y siempre muestran `0`
- [X] T008 [P] [US1] Ampliar primero `frontend/src/pages/RankingPage.test.jsx` con un mock de RecalculateButton que demuestre montaje independiente del estado de Ranking y conexión exacta de `onRecalculated` con `ranking.reload`

### Implementation for User Story 1

- [X] T009 [P] [US1] Exponer `reload` en `frontend/src/features/ranking/useRanking.js` reutilizando la invalidación de retry, sin cambiar filtros ni página y conservando abort/corrección de página fuera de rango, hasta hacer pasar T005
- [X] T010 [P] [US1] Crear el flujo exitoso de `useRecalculate` en `frontend/src/features/ranking/useRecalculate.js` con estados running/notification, ref sincrónica anti-duplicados, AbortController, guardas de montaje/señal y callback posterior a publicar el resumen, hasta hacer pasar T006
- [X] T011 [US1] Crear `frontend/src/features/ranking/RecalculateButton.jsx` usando Button y Toast sin modificarlos, confirmación nativa exacta, progreso accesible y resumen persistente con runId, estrategias y versiones, los tres conteos, salteadas/motivos, procesados, fallidos, activeStrategy y marketValueUpdated; `runId` es obligatorio, `marketValueUpdated` se muestra como Sí/No y las listas/ceros nunca se omiten
- [X] T012 [US1] Crear estilos responsive locales en `frontend/src/features/ranking/RecalculateButton.css` para alinear la acción, permitir wrapping del runId, limitar altura del resumen y contener su scroll sin overflow global a 320 px, sin cambiar `frontend/src/components/Toast.css`, después de que T011 defina la estructura y las clases del componente
- [X] T013 [US1] Montar RecalculateButton junto al encabezado de `frontend/src/pages/RankingPage.jsx` y conectar `onRecalculated={ranking.reload}` sin condicionar la acción al loading/error/vacío ni reemplazar filtros, tabla o paginador, hasta hacer pasar T008
- [X] T014 [US1] Ejecutar las suites focalizadas de US1 en `frontend/src/api/quotesApi.test.js`, `frontend/src/features/ranking/useRanking.test.js`, `frontend/src/features/ranking/useRecalculate.test.js`, `frontend/src/features/ranking/RecalculateButton.test.jsx` y `frontend/src/pages/RankingPage.test.jsx`, corrigiendo sólo archivos de esta feature hasta dejarlas verdes

**Checkpoint**: US1 funciona como MVP completo para el camino feliz y es demostrable sin US2/US3.

---

## Phase 4: User Story 2 - Comprender y superar fallas (Priority: P1)

**Goal**: Terminar progreso y mostrar mensajes seguros ante 409, red u otros errores sin recargar ni romper Ranking.

**Independent Test**: Simular 409, NetworkError y otro ApiError; comprobar texto exacto para 409, mensajes genéricos para los demás, Button recuperado, cero recargas y ranking/criterios intactos; luego reintentar con éxito.

### Tests for User Story 2 ⚠️

> **NOTE: Escribir estos tests primero y confirmar que fallan antes de implementar.**

- [X] T015 [P] [US2] Ampliar primero `frontend/src/features/ranking/useRecalculate.test.js` con 409, NetworkError y ApiError genérico, comprobando running=false terminal, notification clasificada, callback de recarga nunca invocado y detalles privados nunca copiados
- [X] T016 [P] [US2] Ampliar primero `frontend/src/features/ranking/RecalculateButton.test.jsx` para exigir `Ya hay un recálculo en curso`, `No se pudo conectar con el servidor. Intentá de nuevo.`, error genérico, descarte/reintento y Button nuevamente habilitado; agregar un harness donde el POST exitoso publica el resumen, `onRecalculated` lleva Ranking a error y el Toast exitoso permanece visible mientras aparece la recuperación habitual de Ranking

### Implementation for User Story 2

- [X] T017 [US2] Implementar en `frontend/src/features/ranking/useRecalculate.js` la clasificación por `ApiError.status` para 409, NetworkError y error inesperado, suprimir aborts propios y liberar siempre running/ref en resultados terminales sin llamar onRecalculated
- [X] T018 [US2] Implementar en `frontend/src/features/ranking/RecalculateButton.jsx` los mensajes exactos/saneados y descarte de Toast breve, manteniendo la acción reintentable y el Toast de éxito independiente de un error posterior de `useRanking`
- [X] T019 [US2] Ejecutar los casos focalizados de error en `frontend/src/features/ranking/useRecalculate.test.js`, `frontend/src/features/ranking/RecalculateButton.test.jsx` y la regresión de error/reintento en `frontend/src/pages/RankingPage.test.jsx` hasta dejarlos verdes

**Checkpoint**: US2 puede probarse con el Ranking existente y cada error deja la pantalla recuperable.

---

## Phase 5: User Story 3 - Restringir la acción a administradores (Priority: P1)

**Goal**: Tras un 403, informar la falta de permiso y ocultar la acción para el mismo token, restaurándola para una sesión nueva.

**Independent Test**: Simular 403 con token A, remontar Ranking con token A y luego con token B; comprobar Toast claro y Button oculto en los dos primeros casos, y Button visible con token B.

### Tests for User Story 3 ⚠️

> **NOTE: Escribir estos tests primero y confirmar que fallan antes de implementar.**

- [X] T020 [P] [US3] Ampliar primero `frontend/src/features/ranking/useRecalculate.test.js` con ApiError 403 y tokens distintos, exigiendo que `lastDeniedToken` sea opaco, sólo en memoria, no persistido/mostrado/registrado, que el mismo token remonte forbidden=true y que un token nuevo restaure forbidden=false
- [X] T021 [P] [US3] Ampliar primero `frontend/src/features/ranking/RecalculateButton.test.jsx` para exigir `No tenés permisos para recalcular cotizaciones.`, Button ausente tras 403, Toast todavía visible y nueva identidad con acción disponible

### Implementation for User Story 3

- [X] T022 [US3] Implementar en `frontend/src/features/ranking/useRecalculate.js` la marca de módulo `lastDeniedToken`, derivar forbidden por el token de `frontend/src/session/SessionContext.jsx` sin modificar ese archivo y descartar respuestas de una identidad que ya cambió
- [X] T023 [US3] Ajustar `frontend/src/features/ranking/RecalculateButton.jsx` para conservar el contenedor/Toast cuando forbidden oculte sólo el Button y para restablecer presentación al cambiar el token, hasta hacer pasar T021
- [X] T024 [US3] Ejecutar los casos focalizados 403/remount/cambio de token en `frontend/src/features/ranking/useRecalculate.test.js` y `frontend/src/features/ranking/RecalculateButton.test.jsx` hasta dejarlos verdes

**Checkpoint**: Las tres historias funcionan y la autorización sigue perteneciendo al backend.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Validación completa, accesibilidad, responsive, archivos protegidos y gates externos.

- [X] T025 Ejecutar `npm test -- --coverage` desde `frontend/package.json`, revisar `frontend/coverage/lcov.info` y corregir regresiones o ramas sin cubrir de los archivos nuevos/modificados de feature 025
- [X] T026 Ejecutar `npm run lint` y `npm run build` desde `frontend/package.json`, corrigiendo issues de oxlint/Vite sin agregar dependencias ni cambiar configuración compartida
- [X] T027 Validar manualmente confirmación, progreso, resumen, 409, 403, red, navegación con pedido pendiente, teclado/lector de pantalla y viewports 320/768/1440 según `specs/025-front-recalcular/quickstart.md`, registrando evidencia en ese archivo
- [X] T028 Auditar `git diff --name-only` y `git diff --check` contra `specs/025-front-recalcular/plan.md`, confirmando que Button, Toast, SessionContext y todo archivo de Entrega 1 permanecen sin cambios salvo la excepción mínima autorizada en httpClient y su test; detenerse y consultar si aparece cualquier otro
- [ ] T029 Ejecutar la validación de usabilidad de SC-007 con al menos 10 participantes, comprobar que al menos 9 confirman el recálculo, reconocen el progreso e identifican si hubo jugadores fallidos en menos de 30 segundos, excluyendo la duración del backend, y registrar resultados anonimizados en `specs/025-front-recalcular/quickstart.md`
- [ ] T030 Tras push/PR, registrar en `specs/025-front-recalcular/quickstart.md` que GitHub Actions finaliza `SUCCESS` y SonarCloud `PASSED`, sin vulnerabilidades nuevas y con menos de 10 issues

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: empieza inmediatamente; T002 es gate de pausa si la demo excede 10 segundos.
- **Gate de timeout**: T002 bloquea T003-T030. Si la corrida tarda 10 segundos o más, no continuar hasta obtener autorización y actualizar `specs/025-front-recalcular/plan.md` y `specs/025-front-recalcular/tasks.md`.
- **Foundational (Phase 2)**: depende de Setup y bloquea todas las historias.
- **US1 (Phase 3)**: depende de Foundational y entrega el MVP.
- **US2 (Phase 4)**: depende del hook/componente base de US1, pero sus escenarios de error se prueban aisladamente.
- **US3 (Phase 5)**: depende del hook/componente base de US1; su regla 403 es independiente de US2 salvo por la clasificación compartida.
- **Polish (Phase 6)**: depende de todas las historias incluidas.

### User Story Dependency Graph

```text
Setup -> Foundational -> US1 (MVP) -> US2
                                \-> US3
US1 + US2 + US3 -> Polish
```

### Within Each User Story

- Los tests se escriben y se observan fallar antes de la implementación correspondiente.
- Hook/contrato de estado precede a la presentación que lo consume.
- Presentación y reload preceden a la integración final en RankingPage.
- Cada checkpoint se valida antes de avanzar.

### Parallel Opportunities

- En US1, T005-T008 pueden escribirse en paralelo porque tocan cuatro archivos de test distintos.
- Tras los tests de US1, T009 y T010 pueden implementarse en paralelo. T011 depende de T010; T012 depende de la estructura y clases definidas en T011; T013 integra T009-T012.
- En US2, T015 y T016 pueden escribirse en paralelo; T017 precede a T018.
- En US3, T020 y T021 pueden escribirse en paralelo; T022 precede a T023.
- US2 y US3 pueden desarrollarse en paralelo después de completar US1, coordinando cambios secuenciales en `useRecalculate.js` y `RecalculateButton.jsx`.

---

## Parallel Example: User Story 1

```text
Task T005: Tests de reload en frontend/src/features/ranking/useRanking.test.js
Task T006: Tests de hook exitoso en frontend/src/features/ranking/useRecalculate.test.js
Task T007: Tests de confirmación/resumen en frontend/src/features/ranking/RecalculateButton.test.jsx
Task T008: Test de composición en frontend/src/pages/RankingPage.test.jsx
```

## Parallel Example: User Story 2

```text
Task T015: Tests de clasificación de errores en frontend/src/features/ranking/useRecalculate.test.js
Task T016: Tests de mensajes y recuperación en frontend/src/features/ranking/RecalculateButton.test.jsx
```

## Parallel Example: User Story 3

```text
Task T020: Tests de persistencia por token en frontend/src/features/ranking/useRecalculate.test.js
Task T021: Tests de ocultamiento/Toast en frontend/src/features/ranking/RecalculateButton.test.jsx
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Setup y resolver el timeout gate.
2. Completar el adaptador Foundational.
3. Escribir primero T005-T008 y confirmar fallas.
4. Implementar T009-T013.
5. Ejecutar T014 y detenerse a validar US1 independientemente.
6. Demostrar confirmación, progreso, resumen y recarga antes de sumar ramas de error/permisos.

### Incremental Delivery

1. Setup + Foundational -> POST disponible y medición segura.
2. US1 -> camino feliz demostrable (MVP).
3. US2 -> concurrencia/fallas recuperables.
4. US3 -> UX de autorización por sesión.
5. Polish -> cobertura, calidad, accesibilidad y gates externos.

### Parallel Team Strategy

1. El equipo completa junto Setup/Foundational.
2. Una persona implementa el reload de Ranking mientras otra prepara hook/componente/tests de US1.
3. Tras US1, US2 y US3 pueden avanzar en paralelo en sus tests, pero los cambios productivos compartidos se integran secuencialmente.
4. El cierre se realiza una vez con la suite completa y la auditoría de archivos protegidos.

---

## Notes

- `[P]` sólo marca trabajo sobre archivos distintos sin dependencias incompletas.
- Las etiquetas `[US1]`, `[US2]`, `[US3]` trazan cada tarea a `spec.md`.
- No crear un cliente HTTP alternativo, modal compartido, Toast paralelo ni almacenamiento persistente.
- Un abort del frontend no garantiza cancelar la corrida backend; sólo evita efectos visibles tardíos.
- CI y SonarCloud son gates de cierre, no autorización para modificar sus configuraciones.
