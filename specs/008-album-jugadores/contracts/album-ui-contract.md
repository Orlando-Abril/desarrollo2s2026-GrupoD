# Album UI Contract

## Route and heading

- `/album` continúa bajo el guard protegido existente.
- Título: `El álbum`.
- Tras el primer éxito sin filtros, subtítulo: `{N} jugadores · 5 ligas`, con `N` entero es-AR y estable durante la visita. Antes de conocer `N` no se presenta un total ficticio.

## AlbumFilters

Controlled inputs:

- `value = {league, position, team, search}`.
- `onChange(name, value)` comunica cada cambio; no hace fetch.
- `teamOptions` proviene de la última respuesta sin equipo.
- `viewMode`, `onViewChange` y `onClear` controlan presentación y reset.

Options:

- Liga: `Todas` + claves/labels de `LEAGUES`.
- Posición: `Todas` + claves/labels de `POSITIONS`.
- Equipo: `Todos` + opciones derivadas.
- Buscar por nombre: texto libre, filtro local.

Selector:

- `▦ Cartas` y `☰ Lista`, unidos y con `aria-pressed` coherente.
- `Limpiar filtros` usa la variante de texto compartida.

## Results

### Cards

- `AlbumGrid` recibe `players` y renderiza un `Figurita` por jugador.
- Dos columnas a 360 px; escritorio usa columnas automáticas con mínimo 180 px.
- Ninguna carta agrega interacción a `Figurita`.

### List

- `AlbumTable` recibe `players` y usa `LEAGUES`, `POSITIONS` y formatos compartidos.
- Columnas exactas y ordenadas: `N°`, `Jugador`, `Equipo`, `Liga`, `Posición`, `Nacionalidad`, `Edad`, `Valor`.
- `Jugador` contiene muestra 22 × 26 px del color de liga y nombre bold.
- `Posición` muestra todas las posiciones como etiquetas; un array vacío muestra `—`.
- Nacionalidad/edad null: `—`.
- La tabla no contiene links ni controles por fila.
- El scroll horizontal pertenece sólo al panel de tabla.

## Pager

Inputs: `page`, `totalPages`, `from`, `to`, `total`, `window`, `onPageChange`.

- Orden: `‹`, ventana con números/elipsis, `›`.
- Primera, última y actual ±1 siempre que existan; elipsis sólo ante huecos.
- Actual declara `aria-current="page"`.
- Anterior/siguiente deshabilitados en límites.
- Texto vivo exacto: `Mostrando {desde}–{hasta} de {total}`.
- No se renderiza durante carga ni con total 0.
- La página solicita el scroll al inicio de resultados después de un cambio válido.

## States

| State | Stamp | Title | Body | Action |
|---|---|---|---|---|
| Loading | — | — | 8 × `Cargando figuritas…` | Ninguna; `aria-busy` |
| Empty catalog | `Álbum vacío` neutral | `Todavía no hay figuritas` | `El catálogo de jugadores aún no fue cargado. Probá de nuevo más tarde.` | Ninguna |
| No results | `0 resultados` neutral | `No hay figuritas con esos filtros` | `Probá con otra liga, equipo o posición.` | `Limpiar filtros` |
| Connection/generic | `Sin conexión` error | `No pudimos abrir el álbum` | `No se pudo conectar con el servidor. Intentá de nuevo en unos segundos.` | `Reintentar` |
| 401 | Gestionado por sesión | — | `Tu sesión venció. Volvé a ingresar.` en `/ingresar` | — |

## Visual and accessibility invariants

- Usar exclusivamente tokens, componentes y excepciones visuales de Feature 007/008.
- Sin radius, sombra difusa, colores o degradados adicionales.
- Foco global visible y operación completa por teclado.
- `aria-pressed`, `aria-current`, `aria-live` y `aria-busy` según estado.
- `prefers-reduced-motion` desactiva el pulso por reglas compartidas.
- Sin scroll horizontal global desde 360 px.
