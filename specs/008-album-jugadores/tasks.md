---

description: "Task list for El álbum de jugadores"
---

# Tasks: El álbum de jugadores

**Input**: Design documents from `/specs/008-album-jugadores/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: Obligatorios. Cada fase de historia escribe primero las pruebas solicitadas con Vitest, Testing Library y `fetch` mockeado, confirma que fallan por la capacidad ausente y recién después implementa.

**Organization**: Las tareas se agrupan por historia para entregar incrementos verificables. No se agregan dependencias, endpoints, vistas ni archivos productivos fuera del inventario aprobado; los tres tests nuevos están justificados en `research.md`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo con otras tareas marcadas del mismo bloque porque modifica archivos diferentes y no depende de una tarea incompleta.
- **[Story]**: Historia de usuario trazable a `spec.md`.
- Todas las tareas incluyen rutas exactas desde la raíz del repositorio.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirmar una línea base verde y preservar el stack cerrado antes de modificar código.

- [X] T001 Ejecutar la línea base `npm test`, `npm run lint` y `npm run build` desde `frontend/package.json`, informar cualquier fallo preexistente en el handoff de implementación antes de continuar y confirmar que `frontend/package.json` y `frontend/package-lock.json` no requieren cambios

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Extender transporte, campos, formatos y utilidades puras compartidas que bloquean todas las historias.

**⚠️ CRITICAL**: Ninguna historia comienza hasta completar esta fase y dejar sus pruebas fundacionales verdes.

- [X] T002 [P] Agregar a `frontend/src/utils/format.js` un formateador de enteros `es-AR` reutilizable para total, rango, identificador y edad, conservando sin regresiones `formatCredits`
- [X] T003 [P] Extender `frontend/src/components/Field.jsx` para renderizar `input` por defecto o `select` solicitado, preservando label, ref, hint, error, `aria-invalid` y `aria-describedby`, y extender `frontend/src/components/Field.css` para aplicar el mismo contrato visual a ambos controles
- [X] T004 [P] Agregar la variante visual `text` de `Button` en `frontend/src/components/Button.css` con texto mono 12 px bold, subrayado, sin borde ni sombra, sin alterar las variantes existentes
- [X] T005 [P] Escribir primero casos fallidos para señal externa ya abortada, abort durante `fetch`, limpieza del listener, timeout de 10 s y callback 401 intacto en `frontend/src/api/httpClient.test.js`
- [X] T006 Implementar `signal` opcional en `frontend/src/api/httpClient.js`, enlazándolo al `AbortController` interno, conservando timeout, Bearer y 401, y limpiando timer/listener en `finally` hasta aprobar T005
- [X] T007 [P] Crear primero pruebas fallidas con `fetch` mockeado para `getPlayers` sin query sobrante, omisión de vacíos, combinación de filtros, equipo con espacios y reenvío de señal en `frontend/src/api/playersApi.test.js`
- [X] T008 Implementar `getPlayers({league, team, position}, {signal})` en `frontend/src/api/playersApi.js` mediante `URLSearchParams` y `request`, sin `fetch` directo, sin `X-API-KEY` y sin parámetros de búsqueda/paginación; devolver intacto `PlayerResponse[]` preservando: `id` “Obligatorio; identificador visible como N°”, `externalId` “Obligatorio en respuesta; no se presenta”, `fullName` “Obligatorio; clave de búsqueda y orden español ascendente”, `team` “Obligatorio; nombre completo y opción de filtro”, `league` “Una clave de LEAGUES”, `positions` “campo obligatorio con cero o más claves únicas de POSITIONS”, `nationality` `string/null`, `age` “No negativo” o null y `marketValue` “No negativo”
- [X] T009 [P] Crear primero pruebas fallidas de normalización sin tildes/mayúsculas, filtro de nombre, orden español no mutante, equipos únicos ordenados, paginación de 0/24/25 ítems y ventanas de inicio/centro/final en `frontend/src/features/album/albumUtils.test.js`
- [X] T010 Implementar funciones puras `normalizeText`, `filterByName`, `sortPlayers`, `deriveTeamOptions`, `paginate` y `buildPageWindow` en `frontend/src/features/album/albumUtils.js`; para total 0 devolver `totalPages=0`, `page=1`, `from=0`, `to=0`, `items=[]`, y para equipos aplicar “Unicidad por nombre exacto recibido” y `localeCompare('es')` hasta aprobar T009

**Checkpoint**: Transporte cancelable, adaptador de jugadores, controles compartidos, formato y transformaciones puras listos; las historias pueden comenzar.

---

## Phase 3: User Story 1 - Recorrer el álbum completo (Priority: P1) 🎯 MVP

**Goal**: Cargar el catálogo protegido, mostrar el total inicial estable, ordenar por nombre y recorrerlo en páginas de 24 figuritas.

**Independent Test**: Iniciar sesión, abrir `/album` con más de 24 jugadores y verificar título/subtítulo es-AR, orden español, primeras 24 cartas, rango, ventana de páginas, navegación sin omisiones y scroll al inicio; con nulos, mostrar `—`.

### Tests for User Story 1

> Escribir y ejecutar T011 primero; debe fallar antes de implementar la historia.

- [X] T011 [US1] Crear en `frontend/src/pages/AlbumPage.test.jsx` las pruebas de carga exitosa inicial, total estable `{N} jugadores · 5 ligas`, orden ascendente, 24 ítems, bordes del paginador, rango vivo, avance sin duplicados y scroll al inicio; incluir una respuesta mock de aproximadamente 2.600 jugadores y medir menos de 2 s desde su resolución hasta que el primer tramo queda disponible

### Implementation for User Story 1

- [X] T012 [P] [US1] Implementar `AlbumGrid` puramente presentacional en `frontend/src/features/album/AlbumGrid.jsx` y su grilla de `Figurita` en `frontend/src/features/album/AlbumGrid.css`, sin API, handlers, links, `tabIndex` ni datos de dominio duplicados
- [X] T013 [P] [US1] Implementar `Pager` presentacional en `frontend/src/features/album/Pager.jsx` y `frontend/src/features/album/Pager.css` con `‹`, primera, elipsis, actual ±1, última y `›`; botones de 36 px, borde compartido, fondo blanco y mono 13 px bold; página actual con fondo tinta, texto papel y `aria-current="page"`; deshabilitados con opacidad `.35`; rango `Mostrando {desde}–{hasta} de {total}` en `aria-live="polite"`
- [X] T014 [US1] Implementar en `frontend/src/features/album/useAlbum.js` la consulta inicial, `responsePlayers`, `initialTotal`, página default 1 y derivación memorizada `respuesta → orden español → paginate(24) → buildPageWindow`, sin DOM ni estados derivados duplicados
- [X] T015 [US1] Reemplazar el placeholder de `frontend/src/pages/AlbumPage.jsx` y ajustar `frontend/src/pages/AlbumPage.css` para componer heading, total entero es-AR, `AlbumGrid`, `Pager` y ref de scroll; mantener `/album` protegido, ocultar un total ficticio antes del primer éxito y aprobar T011

**Checkpoint**: US1 entrega un MVP navegable del álbum completo en cartas, independientemente de filtros, tabla y estados avanzados.

---

## Phase 4: User Story 2 - Encontrar jugadores con filtros y búsqueda (Priority: P1)

**Goal**: Combinar liga, posición y equipo en backend, buscar nombre localmente y descartar consultas superadas.

**Independent Test**: Elegir liga/posición/equipo, verificar query AND sin vacíos, buscar ignorando tildes/mayúsculas sin request, cambiar liga para limpiar equipo/página y lanzar cambios rápidos para confirmar que sólo vence la última respuesta.

### Tests for User Story 2

> Agregar y ejecutar T016 primero; los nuevos casos deben fallar antes de implementar la historia.

- [X] T016 [US2] Ampliar `frontend/src/pages/AlbumPage.test.jsx` con pruebas de búsqueda sin request y sin tildes/mayúsculas, opciones de equipo únicas, liga/posición que limpian equipo y página, equipo enviado al backend, limpiar defaults y cancelación donde la respuesta anterior no pisa la vigente

### Implementation for User Story 2

- [X] T017 [P] [US2] Implementar `AlbumFilters` controlado en `frontend/src/features/album/AlbumFilters.jsx` y `frontend/src/features/album/AlbumFilters.css`, recibiendo `value`/`onChange`, usando `Field`, `Button`, `LEAGUES` y `POSITIONS`, con defaults `Todas`/`Todos`, barra blanca con borde/padding 12 px y controles de 14 px; no importar API ni duplicar labels
- [X] T018 [P] [US2] Extender `frontend/src/features/album/useAlbum.js` con `AlbumFilters` default `league=""`, `position=""`, `team=""`, `search=""`; liga/posición deben fijar `team=""` y `page=1` en la misma acción, equipo/búsqueda deben fijar `page=1`, búsqueda no debe disparar request, y `teamSourcePlayers` sólo debe actualizarse en éxitos sin team para derivar opciones únicas ordenadas mediante `useMemo`
- [X] T019 [US2] Integrar `AlbumFilters` en `frontend/src/pages/AlbumPage.jsx`, conectar filtros remotos y búsqueda local, limpiar todo a defaults, conservar el total inicial, abortar por cleanup cada consulta superada y descartar su error/resultado comprobando la señal hasta aprobar T016

**Checkpoint**: US2 permite encontrar jugadores con filtros y búsqueda manteniendo una sola consulta vigente.

---

## Phase 5: User Story 3 - Elegir entre cartas y lista (Priority: P2)

**Goal**: Alternar entre cartas y una tabla compacta sin alterar filtros, orden, página ni resultados.

**Independent Test**: Sobre un mismo tramo, alternar `▦ Cartas`/`☰ Lista`, comparar jugadores y orden, verificar `aria-pressed`, todas las posiciones, nulos `—`, formatos compartidos y ausencia de interacción en cartas/filas.

### Tests for User Story 3

> Agregar y ejecutar T020 primero; los nuevos casos deben fallar antes de implementar la historia.

- [X] T020 [US3] Ampliar `frontend/src/pages/AlbumPage.test.jsx` con pruebas de vista inicial Cartas, toggle con `aria-pressed`, conservación de página/resultados, columnas exactas, todas las posiciones, array de posiciones vacío como `—`, nacionalidad/edad nulas como `—` y cartas/filas no clickeables

### Implementation for User Story 3

- [X] T021 [P] [US3] Implementar `AlbumTable` presentacional en `frontend/src/features/album/AlbumTable.jsx` y `frontend/src/features/album/AlbumTable.css` dentro de un panel con fondo blanco, borde grueso, sombra md y `overflow-x:auto`, con columnas `N°`, `Jugador`, `Equipo`, `Liga`, `Posición`, `Nacionalidad`, `Edad`, `Valor`; encabezados mono 10 px bold uppercase con fondo papel-2 y borde inferior; celdas 10 px 12 px, separador `rgba(27,26,23,.18)` y hover `rgba(244,194,27,.13)`; muestra de liga 22×26 px y nombre bold; todas las posiciones como etiquetas amarillas mono 10 px con borde 1.5px, o `—` si el array está vacío; `N°`, `Edad` y `Valor` alineados a la derecha en mono; obtener labels/colores/abreviaturas de `frontend/src/domain/catalogo.js`, usar `frontend/src/utils/format.js`, aplicar `—` a nationality/age null y no agregar interacción por fila
- [X] T022 [P] [US3] Extender `frontend/src/features/album/AlbumFilters.jsx` y `frontend/src/features/album/AlbumFilters.css` con selector unido `▦ Cartas`/`☰ Lista`, fondo tinta/texto papel para activo y `aria-pressed`, sin persistencia ni componente paralelo
- [X] T023 [US3] Agregar `AlbumViewMode` `cards | list` con default `cards` y memoria sólo durante el montaje en `frontend/src/features/album/useAlbum.js`, y componer `AlbumGrid`/`AlbumTable` condicionalmente en `frontend/src/pages/AlbumPage.jsx` sin cambiar página ni resultados hasta aprobar T020

**Checkpoint**: US3 ofrece las dos presentaciones equivalentes y accesibles.

---

## Phase 6: User Story 4 - Comprender y recuperar estados excepcionales (Priority: P2)

**Goal**: Distinguir carga, catálogo vacío, cero coincidencias, error recuperable y sesión vencida con textos/acciones exactos.

**Independent Test**: Simular consulta pendiente, `[]` inicial, búsqueda/filtros sin coincidencias, red/timeout/400/5xx y 401; verificar 8 casilleros, paneles exactos, reintento de la última query y redirección de sesión.

### Tests for User Story 4

> Agregar y ejecutar T024 primero; los nuevos casos deben fallar antes de implementar la historia.

- [X] T024 [US4] Ampliar `frontend/src/pages/AlbumPage.test.jsx` con carga de 8 casilleros y sin pager, álbum vacío sin botón, cero resultados con limpiar, red/timeout/400/5xx con reintentar, reintento de filtros vigentes y 401 que navega a `/ingresar` con `Tu sesión venció. Volvé a ingresar.`

### Implementation for User Story 4

- [X] T025 [P] [US4] Extender `frontend/src/features/album/useAlbum.js` con transiciones `loading | ready | error`, `retryKey`, reintento sin perder filtros/búsqueda/vista/página, supresión de abort externo y delegación de 401 a `SessionContext`, respetando la prioridad de `AlbumPresentationState` documentada en `specs/008-album-jugadores/data-model.md`
- [X] T026 [US4] Después de T025, componer en `frontend/src/pages/AlbumPage.jsx` y estilizar en `frontend/src/pages/AlbumPage.css` los 8 `Casillero` con `aria-busy`, `EstadoPanel`/`Sello` y acciones existentes con todos los textos exactos de `specs/008-album-jugadores/contracts/album-ui-contract.md`, ocultando pager durante carga y total 0 hasta aprobar T024

**Checkpoint**: US4 comunica y recupera todos los estados sin reemplazar el flujo compartido de sesión.

---

## Phase 7: User Story 5 - Usar el álbum en pantallas pequeñas (Priority: P3)

**Goal**: Mantener filtros, cartas, lista, estados y paginador utilizables desde 360 px sin scroll horizontal global.

**Independent Test**: A 360 px verificar filtros en dos columnas/apilables, dos cartas por fila y scroll sólo dentro de tabla; a 1280 px verificar auto-fill mínimo 180 px y sistema visual; recorrer controles por teclado y con movimiento reducido.

### Tests for User Story 5

> Agregar y ejecutar T027 primero; los casos semánticos deben fallar antes del ajuste final.

- [X] T027 [US5] Ampliar `frontend/src/pages/AlbumPage.test.jsx` con aserciones de `aria-busy`, `aria-live`, `aria-current`, `aria-pressed`, operación por botones nativos y ausencia de roles/tabindex interactivos en cartas/filas

### Implementation for User Story 5

- [X] T028 [US5] Completar responsive y accesibilidad en `frontend/src/features/album/AlbumFilters.css`, `frontend/src/features/album/AlbumGrid.css`, `frontend/src/features/album/AlbumTable.css`, `frontend/src/features/album/Pager.css` y `frontend/src/pages/AlbumPage.css`: desde 360 px dos cartas, filtros de a dos/apilables, sin overflow global; escritorio `auto-fill/minmax(180px,1fr)`; `overflow-x:auto` sólo en panel de tabla; únicamente tokens y rgba autorizados, sin radius, sombra difusa, breakpoint o degradado nuevo

**Checkpoint**: Las cinco historias funcionan en móvil y escritorio con semántica accesible.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Auditar alcance, ejecutar gates y validar la integración real antes del cierre.

- [X] T029 [P] Auditar `frontend/src/features/album/`, `frontend/src/pages/AlbumPage.jsx`, `frontend/src/api/playersApi.js` y `frontend/src/domain/catalogo.js` para eliminar labels/colores/abreviaturas duplicados, confirmar ausencia de detalle/ranking/mercado/portfolio/sync/filtros URL y verificar que ningún archivo salvo `frontend/src/api/httpClient.js` invoque `fetch`
- [X] T030 Ejecutar la suite completa `npm test` definida en `frontend/package.json`, corregir regresiones en `frontend/src/api/httpClient.test.js`, `frontend/src/api/playersApi.test.js`, `frontend/src/features/album/albumUtils.test.js` y `frontend/src/pages/AlbumPage.test.jsx` hasta dejarla verde
- [X] T031 Ejecutar `npm run lint` y `npm run build` definidos en `frontend/package.json`, corregir sólo archivos autorizados de `frontend/src/` y confirmar que `frontend/package.json`/`frontend/package-lock.json` no agregaron dependencias
- [ ] T032 Validar manualmente register → login → `/album`, Bearer sin `X-API-KEY`, filtros, búsqueda, cancelación, vistas, paginación, estados, teclado y diseño a 360/1280 px siguiendo `specs/008-album-jugadores/quickstart.md`; comprobar además `backend/src/test/java/com/example/demo/config/PlayerOpenApiTest.java`, Swagger para `GET /players` con `bearerAuth` o `apiKeyAuth`, y `backend/src/test/java/com/example/demo/service/PlayerCatalogQueryServiceTest.java`/`mapsLocalResultsWithoutCallingAnAdapter` junto con una consulta desde datos locales cuando Football-Data no está disponible, sin modificar backend
- [ ] T033 Con autorización explícita del usuario para publicar, o sobre un run ya existente, verificar que `.github/workflows/ci.yml` termine `SUCCESS`, con tests/lint/build, Quality Gate `PASSED` y menos de 10 issues menores; no modificar archivos fuera de `frontend/` y `specs/008-album-jugadores/`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Sin dependencias; establece la línea base.
- **Foundational (Phase 2)**: Depende de Setup y bloquea todas las historias.
- **US1 (Phase 3)**: Depende de Foundational; es el MVP.
- **US2 (Phase 4)**: Depende de US1 porque extiende el hook y la página ya funcionales.
- **US3 (Phase 5)**: Depende de US2 porque el selector vive en la barra controlada; después puede avanzar en paralelo con US4.
- **US4 (Phase 6)**: Depende de US2 para distinguir cero coincidencias de catálogo vacío; puede avanzar en paralelo con US3.
- **US5 (Phase 7)**: Depende de US3 y US4 porque ajusta todos los modos y estados finales.
- **Polish (Phase 8)**: Depende de las cinco historias.

### User Story Dependency Graph

```text
Setup → Foundation → US1 (MVP) → US2 ─┬→ US3 ─┐
                                      └→ US4 ─┴→ US5 → Polish
```

### Within Each User Story

1. Escribir la tarea de pruebas y confirmar fallos por capacidad ausente.
2. Implementar presentadores puros en paralelo cuando tengan archivos distintos.
3. Implementar/expandir `useAlbum` contra `albumUtils` y `playersApi`.
4. Integrar en `AlbumPage`.
5. Ejecutar los tests de la historia y validar su checkpoint antes de seguir.

### Parallel Opportunities

- Foundational: T002, T003, T004, T005, T007 y T009 pueden escribirse en paralelo; luego T006, T008 y T010 resuelven sus dependencias.
- US1: T012 y T013 pueden ejecutarse en paralelo después de T011.
- US2: T017 y T018 pueden ejecutarse en paralelo después de T016; T019 integra ambos.
- US3: T021 y T022 pueden ejecutarse en paralelo después de T020; T023 integra ambos.
- US3 y US4 pueden desarrollarse en paralelo una vez cerrada US2, coordinando los archivos compartidos `useAlbum.js`, `AlbumPage.jsx` y `AlbumPage.test.jsx` al integrar.
- US4: T025 precede a T026 porque la página integra las transiciones expuestas por el hook.
- Polish: T029 puede auditarse mientras se prepara la ejecución local, pero T030–T033 se cierran en orden.

---

## Parallel Examples

### User Story 1

```text
Task T012: Implementar AlbumGrid.jsx/AlbumGrid.css con Figurita
Task T013: Implementar Pager.jsx/Pager.css con ventana y rango vivo
```

### User Story 2

```text
Task T017: Implementar AlbumFilters.jsx/AlbumFilters.css controlado
Task T018: Extender useAlbum.js con filtros, búsqueda y teamSourcePlayers
```

### User Story 3

```text
Task T021: Implementar AlbumTable.jsx/AlbumTable.css
Task T022: Extender AlbumFilters.jsx/AlbumFilters.css con selector de vista
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Setup.
2. Completar Foundation y dejar sus pruebas verdes.
3. Completar US1.
4. Detenerse y validar el álbum completo, ordenado y paginado en cartas.
5. Demostrar el MVP antes de sumar filtros y vistas si se necesita una entrega intermedia.

### Incremental Delivery

1. Setup + Foundation → transporte y lógica pura confiables.
2. US1 → catálogo navegable en cartas (MVP).
3. US2 → búsqueda y filtros remotos/locales.
4. US3 y US4 → tabla y estados resilientes, en paralelo si hay capacidad.
5. US5 → responsive y accesibilidad finales.
6. Polish → gates locales, backend real y CI.

### Parallel Team Strategy

Con varias personas después de Foundation:

1. Una persona completa US1 y US2, que establecen la integración central.
2. Tras US2, una persona implementa US3 y otra US4, evitando ediciones simultáneas al integrar `useAlbum.js`, `AlbumPage.jsx` y su test.
3. El equipo converge en US5 y gates finales.

---

## Notes

- `[P]` significa archivos distintos y ausencia de dependencia incompleta; no autoriza ediciones simultáneas del mismo archivo.
- Los tests nuevos son exactamente `playersApi.test.js`, `albumUtils.test.js` y `AlbumPage.test.jsx`; `httpClient.test.js` ya existe y se amplía.
- No modificar `App.jsx`, `SessionContext.jsx`, `tokens.css`, `domain/catalogo.js` ni backend salvo que una contradicción comprobable obligue a detenerse y consultar.
- No marcar T032 ni T033 completas sin evidencia de backend real y CI `SUCCESS`.
