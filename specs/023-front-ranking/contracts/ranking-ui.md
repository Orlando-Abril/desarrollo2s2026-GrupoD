# Contract: Ranking UI

## Route and navigation

- `/ranking` vive dentro de `ProtectedRoute`.
- Sin sesión redirige con replace a `/ingresar`.
- Con sesión renderiza `RankingPage` dentro de `AppLayout`.
- El item `02 Ranking` usa `/ranking`, está habilitado y aparece en Header y BottomNav.

## Filter contract

`RankingFilters` recibe:

- `value`: `{ strategy, league, position }`.
- `onChange(name, value)`: callback para cualquiera de los tres campos.
- `effectiveStrategy`: opcional, sólo para complementar la etiqueta de Activa cuando puede inferirse de al menos un resultado. Una página vacía conserva “Activa” sin código.

Controles accesibles: “Estrategia”, “Liga” y “Posición”. Cambiar cualquiera reinicia a página 1 y comienza carga.

## Table contract

`RankingTable` recibe `items: RankingEntry[]` y renderiza en el mismo orden:

1. Puesto
2. Jugador
3. Equipo
4. Liga
5. Posición
6. Cotización
7. Variación

Cada encabezado tiene `scope="col"`. Liga tiene texto y swatch. Se muestran todas las posiciones. Las filas no son links ni controles en esta entrega.

## VariationBadge contract

Prop única `value` (`number | null`). Cuatro salidas semánticas:

- positiva: sube, verde, signo/flecha y etiqueta accesible;
- negativa: baja, roja, signo/flecha y etiqueta accesible;
- cero: sin cambios, neutra;
- null: sin dato, neutra y distinta de cero.

No conoce jugador, ranking ni navegación, por lo que puede reutilizarse en 024.

## Page states

| Status / data | Visible result | Actions |
|---------------|----------------|---------|
| `loading` | Estado ocupado “Cargando ranking…”; sin tabla ni pager anteriores | Filtros habilitados para reemplazar y abortar la consulta vigente |
| `error` | `EstadoPanel` “No pudimos abrir el ranking” con mensaje genérico | Reintentar conserva criterios |
| `ready`, total 0 | `EstadoPanel` con “Todavía no hay cotizaciones” | Sin pager |
| `ready`, total > 0 | Tabla y pager | Filtrar y paginar |

## Pagination contract

- Presentación 1-based y request 0-based.
- Página anterior deshabilitada en 1; siguiente deshabilitada en totalPages.
- Rango: `from..to de totalElements`.
- Al cambiar página se desplaza la región de resultados al inicio.
- Si la página pedida quedó fuera de rango pero hay resultados, se corrige a la última válida antes de mostrar estado vacío.

## Accessibility and responsive contract

- Todos los selects tienen label programático y foco visible heredado de `Field`.
- Loading usa `aria-busy`; el rango del pager conserva `aria-live="polite"`.
- Variación y liga no dependen exclusivamente de color.
- Wrapper de tabla contiene su propio overflow; no provoca overflow horizontal global a 320 px.
- Nombres largos de jugador, equipo y liga no se solapan ni provocan overflow horizontal global.
- Contraste WCAG 2.2 AA: mínimo 4.5:1 para texto normal y 3:1 para texto grande y componentes o controles visuales.
- No se agrega semántica interactiva a filas hasta que exista el destino de feature 024.
