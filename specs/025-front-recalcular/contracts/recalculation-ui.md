# Contract: Recalculation UI

## Placement and availability

- `RankingPage` monta `RecalculateButton` junto al encabezado y le entrega `ranking.reload` como `onRecalculated`.
- La acción no depende del estado loading/ready/error del GET de Ranking.
- Texto disponible: `Recalcular cotizaciones`.
- Tras 403, el Button se oculta para el token vigente, pero el Toast de denegación permanece renderizable.

## Confirmation

- Activar el Button abre la confirmación nativa: `Se recalcularán todas las cotizaciones. ¿Querés continuar?`.
- Cancelar cierra el diálogo y genera cero POST.
- Confirmar inicia como máximo un POST, aunque haya eventos repetidos.

## Progress

Durante el POST:

- Button deshabilitado;
- `aria-busy="true"`;
- texto `Recalculando…`;
- no se reemplazan tabla, filtros ni paginador por este estado.

Todo resultado terminal termina el progreso.

## Success Toast

El Toast muestra:

1. Run ID.
2. Estrategias calculadas: código, versión y conteos CALCULATED, NO_STATS e INSUFFICIENT_HISTORY.
3. Estrategias salteadas: código, versión y motivo.
4. Jugadores procesados.
5. Jugadores fallidos.
6. Estrategia activa.
7. Si se actualizó marketValue.

Listas vacías se muestran como `Ninguna`; conteos cero se muestran como `0`. El resumen permanece hasta cierre manual, permite scroll interno si es largo y no causa overflow global.

Al publicar el Toast se llama una vez `ranking.reload()`. La recarga conserva los filtros y página vigentes. Si el GET falla, Ranking usa su error/reintento habitual y el resumen exitoso no se convierte en error de recálculo.

## Error Toasts

- 409: `Ya hay un recálculo en curso`.
- 403: `No tenés permisos para recalcular cotizaciones.` y Button oculto.
- Red: `No se pudo conectar con el servidor. Intentá de nuevo.`.
- Otro: `No se pudo recalcular las cotizaciones. Intentá de nuevo.`.

No muestran mensajes internos ni recargan Ranking.

## Accessibility and responsive behavior

- Button y cierre del resumen son operables con teclado y tienen foco visible heredado.
- La confirmación nativa administra su propio foco.
- `aria-busy` y el texto comunican progreso sin depender de color.
- Toast conserva su semántica `output`; el resumen usa encabezados/listas legibles y una acción de cierre con nombre accesible.
- A 320 px, 768 px y 1440 px no hay superposición ni overflow horizontal global.
