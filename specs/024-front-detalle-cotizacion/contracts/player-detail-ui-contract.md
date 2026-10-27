# Player Detail UI Contract

Contrato observable de la pantalla `/jugadores/:id` y de los componentes nuevos. Los tests se escriben contra roles y textos de este documento, no contra clases CSS ni contra el SVG de recharts.

## Ruta

- `/jugadores/:id` va dentro de `<ProtectedRoute>` en `App.jsx`. Sin sesión, redirige a `/ingresar`.
- `PlayerDetailPage` renderiza `<PlayerDetail key={id} id={id} />`.
- `document.title`: `"<fullName> · Figuritas"` cuando el detalle está `ready`.

## Estructura (detalle `ready`)

1. Enlace "← Volver al álbum" (`role=link`, `href=/album`).
2. `h1` con `fullName`.
3. **PlayerHeader**: figurita grande (N°, posición, liga con su color y su nombre en texto, equipo, nacionalidad, edad, con "—" en lo que falte). Debajo, "Cotización actual" con `formatCredits(quote.value)` o "—", y `VariationBadge`.
4. **QuoteChart** (`section` con heading "Evolución de la cotización"):
   - `fieldset` "Estrategia" con radios "Oficial" y "Momentum".
   - `fieldset` "Rango" con radios "Último mes", "3 meses" y "Toda la temporada".
   - Con datos: `figure` → `figcaption` `"<Estrategia> · <rango> · N cotizaciones"`, y el gráfico `role=img` con el mismo `aria-label`. Debajo, una `table` visualmente oculta, con caption igual y columnas `Fecha | Valor | Estrategia | Versión`, una fila por punto en orden cronológico. La columna Estrategia muestra la etiqueta de `STRATEGIES` ("Oficial", "Momentum"), no el código.
   - Tooltip (`QuoteTooltip`): fecha (con hora si hay varios puntos el mismo día UTC), valor en créditos, etiqueta de `STRATEGIES` ("Oficial", "Momentum") y "Versión N". Aparece con hover, toque o foco + flechas (`accessibilityLayer`).
   - Vacío: `EstadoPanel` con sello "Sin datos" y título "Todavía sin cotizaciones". Los fieldsets siguen visibles.
   - Error: `EstadoPanel` de error con título "No pudimos cargar la evolución" y botón "Reintentar".
   - Cargando: región con `aria-busy="true"` y texto "Cargando evolución…".
5. **QuoteAtDate** (`section` "Cotización en una fecha"): `input type=date` con label "Fecha" y `max` igual a hoy UTC, y botón "Consultar".
   - `found`: valor, "Calculada el <fecha>", estrategia y versión.
   - `empty`: "Sin cotización para esa fecha".
   - `error`: mensaje y botón "Reintentar".
   - El resultado está en una región `aria-live="polite"`.
6. **StatsPanel** (`section` "Estadísticas de la temporada"): `dl` con las 9 etiquetas de `STAT_FIELDS` en ese orden; el valor faltante es "—". El rating usa 2 decimales es-AR. Si existe `fetchedAt`, muestra "Actualizado el <fecha>".

## Estados de página

| Estado | UI |
|---|---|
| `loading` | Región `aria-busy="true"` con "Cargando jugador…" (con `Casillero` en variante cargando). |
| `error` | `EstadoPanel` de error, título "No pudimos cargar el jugador", acción "Reintentar". |
| `notFound` | `EstadoPanel` con sello "404", título "Jugador inexistente", acción "Volver al álbum" (navega a `/album`), **sin** "Reintentar". |

## `VariationBadge` (compartido, `src/components/`)

- Props: `{ current: number | null, previous: number | null }`.
- Si `variation()` devuelve `null`: muestra "—" con `aria-label="Sin variación disponible"`.
- `up`: `▲ +0,12 cr (+10,91 %)`, texto accesible "Subió 0,12 cr (10,91 %)" y color `--verde`.
- `down`: `▼ −0,12 cr (−8,82 %)`, texto accesible "Bajó …" y color `--rojo`.
- `flat`: `= 0,00 cr (0,00 %)`, texto accesible "Sin cambios" y color `--tinta-2`.
- El símbolo y el texto transmiten la dirección además del color (FR-029).

## Enlace desde el Álbum

- `AlbumGrid` envuelve cada `Figurita` en `<Link to="/jugadores/{id}" aria-label="Ver detalle de {fullName}" className="figurita-grid__link">`.
- `Figurita.jsx` y `Figurita.css` no cambian. La figurita debe verse idéntica en reposo y en hover (SC-007). Lo único nuevo es un outline de `:focus-visible`.
- La vista en tabla (`AlbumTable`) no cambia (FR-003).

## Dependencia hacia la feature 023 (fuera de alcance)

- La 023 hará clickeable cada fila de su `RankingPage` hacia `/jugadores/{id}` y podrá reutilizar `VariationBadge` con esta misma interfaz.
- Esta feature garantiza que la ruta funcione para cualquier id y no toca el ítem "Ranking" del menú.
