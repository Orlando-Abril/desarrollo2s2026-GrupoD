# Implementation Plan: El álbum de jugadores

**Branch**: `008-album-jugadores` | **Date**: 2026-09-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-album-jugadores/spec.md`

## Summary

Reemplazar el placeholder protegido de `/album` por el catálogo completo de jugadores, manteniendo el stack y el sistema visual de Feature 007. La página delega transporte en `playersApi`, concentra estado y efectos remotos en `useAlbum`, calcula búsqueda, orden y paginación en funciones puras/memorizadas, y compone presentadores sin conocimiento de API para filtros, cartas, tabla y paginador. Se extienden `httpClient`, `Field`, `Button` y `format.js` sólo donde el contrato existente no cubre señal externa, select, botón de texto o enteros es-AR; no se agregan dependencias ni componentes paralelos.

## Technical Context

**Language/Version**: JavaScript ES modules; Node.js 22 en CI; React 19.2.x y Vite 8.3.x existentes

**Primary Dependencies**: React, React DOM y React Router DOM ya instalados; `fetch` nativo; sin dependencias nuevas

**Storage**: Ninguno; sesión, filtros, vista, página, catálogo y estados de consulta viven sólo en memoria

**Testing**: Vitest 5, jsdom, Testing Library y mocks de `fetch` existentes; Oxlint; build de Vite

**Target Platform**: Navegadores web modernos, responsive desde 360 px; validación manual en 360 px y 1280 px; backend local en `http://localhost:8080`

**Project Type**: SPA web dentro de `frontend/`; no modifica backend

**Performance Goals**: Ordenar, buscar y paginar localmente una respuesta de aproximadamente 2.600 jugadores sin consultas por búsqueda y mostrar el primer tramo en menos de 2 s desde que se resuelve la respuesta; mostrar 24 ítems por página; cancelar inmediatamente consultas superadas; conservar timeout HTTP de 10 s

**Constraints**: Inventario productivo cerrado; ningún `fetch` fuera de `httpClient`; JWT Bearer de sesión; una sola consulta activa; sin dependencias, persistencia, filtros URL, paginación/búsqueda backend ni estilos fuera de `tokens.css` y las excepciones normativas; sin scroll horizontal global desde 360 px

**Scale/Scope**: Una ruta protegida, un endpoint GET, cuatro filtros, dos vistas, cinco ligas, cuatro posiciones, aproximadamente 2.600 jugadores, 24 por página y seis estados de UI

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Gate constitucional | Evaluación pre-diseño | Evaluación post-diseño |
|---|---|---|
| Frontend independiente y responsive | PASS: todo el alcance productivo queda en `frontend/` y soporta 360 px a escritorio | PASS: contratos y quickstart verifican 360/1280 px y aíslan el scroll de tabla |
| Comunicación backend sólo por HTTP/REST y OpenAPI vigente | PASS: sólo consume `GET /players` de Feature 004, complementado por JWT de Feature 006 | PASS: `playersApi` adapta ese contrato sin agregar endpoints ni cambiar backend; el cierre verifica `PlayerOpenApiTest` y Swagger con Bearer/API key como alternativas |
| Autenticación JWT/API key | PASS: el frontend usa el JWT emitido por login; Feature 006 lo admite como alternativa y no obliga a exponer API key | PASS: el contrato interno exige Bearer desde `SessionContext` y prohíbe `X-API-KEY` |
| Catálogo con filtros por liga, equipo y posición | PASS: los tres filtros remotos se combinan y la búsqueda por nombre queda local | PASS: modelo y contratos preservan enumeraciones, AND y equipo no vacío |
| CI ejecuta build y tests | PASS condicionado: se amplían las pruebas existentes y se mantienen los comandos del proyecto | PASS por diseño: quickstart exige `npm test`, lint, build y run real de CI en `SUCCESS` |
| SonarCloud y límite de issues | PASS condicionado: no se cambia configuración ni proyecto, y los tests nuevos alimentan la cobertura existente | PASS por diseño: no se agregan exclusiones, secretos ni proyecto separado; el gate real y el máximo constitucional de 10 issues menores se verifican antes del cierre |
| Reglas financieras, auditoría, Redis, batch y mercado | N/A: esta feature es lectura de catálogo y no modifica backend ni operaciones financieras | N/A: el diseño mantiene fuera de alcance sincronización, cotizaciones, trading y portfolio |
| Resiliencia de lectura | PASS condicionado: la UI contempla red, timeout, 5xx, reintento y catálogo vacío; la independencia de Football-Data pertenece al contrato backend existente | PASS por diseño: además de los estados frontend, el cierre verifica `PlayerCatalogQueryServiceTest.mapsLocalResultsWithoutCallingAnAdapter` y una consulta real con la fuente externa no disponible |

**Resultado del gate**: PASS. No hay violaciones constitucionales ni clarificaciones pendientes.

## Project Structure

### Documentation (this feature)

```text
specs/008-album-jugadores/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── album-ui-contract.md
│   └── players-client-contract.md
├── checklists/
│   └── requirements.md
└── tasks.md                       # creado posteriormente por $speckit-tasks
```

### Source Code (repository root)

```text
frontend/src/
├── api/
│   ├── httpClient.js              # existente: aceptar señal externa y conservar timeout
│   ├── httpClient.test.js         # existente: cubrir señal externa
│   ├── playersApi.js              # nuevo
│   └── playersApi.test.js         # nuevo, excepción de pruebas justificada en research.md
├── components/
│   ├── Button.css                 # existente: variante visual text
│   ├── Button.jsx                 # existente; contrato de variantes se explicita si hace falta
│   ├── Field.css                  # existente: mismo aspecto para select a 14 px en álbum
│   └── Field.jsx                  # existente: extensión input/select, sin componente paralelo
├── domain/
│   └── catalogo.js                # existente, única fuente de labels, colores y abreviaturas
├── features/
│   └── album/
│       ├── useAlbum.js            # nuevo
│       ├── albumUtils.js           # nuevo
│       ├── albumUtils.test.js      # nuevo, excepción de pruebas justificada en research.md
│       ├── AlbumFilters.jsx        # nuevo
│       ├── AlbumFilters.css        # nuevo
│       ├── AlbumGrid.jsx           # nuevo
│       ├── AlbumGrid.css           # nuevo
│       ├── AlbumTable.jsx          # nuevo
│       ├── AlbumTable.css          # nuevo
│       ├── Pager.jsx               # nuevo
│       └── Pager.css               # nuevo
├── pages/
│   ├── AlbumPage.jsx               # existente: reemplaza placeholder
│   ├── AlbumPage.css               # existente: layout/estados de página
│   └── AlbumPage.test.jsx          # nuevo, excepción de pruebas justificada en research.md
└── utils/
    └── format.js                   # existente: sumar formato entero es-AR; conservar créditos
```

No se modifican `App.jsx`, `SessionContext`, `tokens.css`, `domain/catalogo.js` ni los componentes visuales que ya satisfacen su contrato. La ruta protegida `/album`, el Bearer automático y los mapas de dominio ya están conectados.

**Structure Decision**: Mantener la organización técnica de Feature 007 y agregar un único módulo cohesivo `features/album/`. `AlbumPage` orquesta la composición y el scroll; `useAlbum` posee estado y el único efecto remoto sin tocar DOM; `albumUtils` concentra transformaciones puras; `playersApi` es el único adaptador nuevo hacia el cliente HTTP; los presentadores reciben props y no importan API. Los tres archivos de prueba nuevos son la única excepción al inventario productivo porque los criterios de terminado exigen pruebas específicas y el proyecto coloca tests junto al módulo probado.

## Design Decisions

### Flujo de datos y consultas

- `playersApi.getPlayers({league, team, position}, {signal})` construye `URLSearchParams`, omite valores vacíos y delega en `request`; no invoca `fetch`.
- `httpClient.request` acepta una señal opcional del llamador, la enlaza con su `AbortController` interno y conserva el timeout de 10 s. Limpia timer y listener en `finally`. Un abort externo sigue rechazando la promesa, pero `useAlbum` lo descarta comprobando la señal de esa ejecución; un timeout no marca la señal externa y se presenta como error de conexión.
- `useAlbum` mantiene un único efecto para la consulta remota, dependiente de liga, posición, equipo y un contador de reintento. El cleanup aborta la request anterior. No existen efectos encadenados para derivar búsqueda, orden, opciones o página.
- La respuesta visible y la última respuesta exitosa sin equipo se guardan por separado. Esta segunda fuente sólo se reemplaza cuando la consulta no incluye `team`; las opciones se derivan de ella con `useMemo`, evitando que elegir un equipo borre las demás alternativas.
- Cambiar liga o posición actualiza ese valor, limpia equipo y lleva página a 1 en una sola transición. Cambiar equipo o búsqueda también lleva página a 1. La búsqueda no modifica las dependencias de la consulta remota.
- El primer éxito sin liga, posición ni equipo fija `initialTotal`; filtros y búsqueda no lo cambian. El subtítulo se muestra cuando ese total está disponible; un catálogo vacío fija 0.
- `retry` incrementa una clave interna y repite la consulta con filtros remotos actuales sin perder búsqueda, vista ni página. Los 401 quedan a cargo del callback compartido de sesión y no generan un panel intermedio.

### Derivación pura y paginación

- `normalizeText` aplica minúsculas, descomposición Unicode NFD, remoción de marcas y trim.
- `filterByName`, `sortPlayers`, `paginate`, `buildPageWindow` y `deriveTeamOptions` no mutan entradas y se prueban sin render.
- La cadena derivada es `response → filtro de nombre → copia ordenada con localeCompare('es') → página de 24`, memorizada desde respuesta, búsqueda y página.
- `paginate` retorna total, totalPages, page segura, from/to e items. Para 0 ítems: `totalPages=0`, `page=1`, `from=0`, `to=0`, `items=[]`; para 24 hay una página y para 25 hay dos.
- La ventana se forma con primera, última, actual y actual ±1; ordena, elimina duplicados e inserta una elipsis por cada salto mayor a uno. Con cero páginas retorna vacío.
- `useAlbum` no hace scroll. `AlbumPage` envuelve el cambio de página y desplaza su referencia al inicio de resultados después de actualizar la página.

### Componentes y formato compartidos

- `AlbumFilters` es controlado: recibe `value`, `onChange`, opciones de equipo, modo de vista y callbacks; no importa API ni administra una copia de filtros.
- `Field` se extiende con un selector de elemento (`input` por defecto, `select` cuando se solicita) y aplica el mismo label, descripción y semántica. Su CSS cubre ambos controles; AlbumFilters ajusta 14 px dentro de su alcance.
- `Button` conserva variantes existentes y suma el estilo `text` para `Limpiar filtros`; los controles segmentados y de paginación son botones semánticos específicos con estilos del módulo, no componentes genéricos paralelos.
- `AlbumGrid` sólo proyecta jugadores a `Figurita`. `AlbumTable` obtiene label/color de liga y label/abreviatura de posición exclusivamente desde `domain/catalogo.js`; ambos son no interactivos. Aunque `positions` es obligatorio, el OpenAPI no declara `minItems`: un array vacío se presenta como `—`.
- `format.js` conserva `formatCredits` y agrega formato de enteros es-AR para total, rango, identificador y edad. No se instancian formateadores en componentes.
- `EstadoPanel`, `Sello`, `Casillero` y `Button` existentes componen estados exactos. No se duplican sus estilos ni su estructura.

### Presentación, accesibilidad y responsive

- Cartas es la vista inicial y el modo queda sólo en estado de `AlbumPage`/hook durante la carga actual.
- El selector usa `aria-pressed`; página actual usa `aria-current="page"`; rango usa `aria-live="polite"`; la grilla de carga expone `aria-busy`.
- Las cartas y filas no reciben enlace, handler, tabIndex ni rol interactivo.
- CSS nuevo usa tokens y sólo los literales/rgba autorizados por Feature 007 y la spec 008. No se agrega radius, sombra difusa, breakpoint alternativo ni degradado.
- En móvil hay dos cartas por fila y filtros en una grilla de dos columnas que puede apilar grupos; en escritorio `auto-fill/minmax(180px,1fr)`. Sólo el panel de tabla tiene `overflow-x:auto`; la página conserva `overflow-x:hidden`.

### Verificación y archivos permitidos

- Nuevos productivos: exactamente los enumerados por el usuario bajo `api/`, `pages/` y `features/album/`; `AlbumPage.jsx` ya existe y se reemplaza en sitio.
- Excepciones nuevas: `playersApi.test.js`, `albumUtils.test.js` y `AlbumPage.test.jsx`, obligatorias por la matriz solicitada y coherentes con tests colocados junto a cada módulo.
- Extensiones existentes justificadas: `httpClient.js/.test.js`, `Field.jsx/.css`, `Button.css`, `AlbumPage.css` y `format.js`. No se crea una alternativa a ninguno.
- La implementación debe finalizar con `npm test`, `npm run lint`, `npm run build`, prueba del primer render de ~2.600 jugadores bajo 2 s, verificación manual con backend real a 360/1280 px, comprobación de Swagger y resiliencia local, y CI remoto `SUCCESS` con Quality Gate aprobado y menos de 10 issues menores.

## Complexity Tracking

No aplica: no hay violaciones constitucionales, dependencias nuevas ni estructuras paralelas.
