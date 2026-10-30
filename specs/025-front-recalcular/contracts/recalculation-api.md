# Contract: Recalculation API consumer

## Operation

`POST /quotes/recalculate`

Pedido autenticado mediante el bearer token que agrega `httpClient.request`. La operación es sincrónica: la respuesta terminal llega cuando finaliza la corrida o se rechaza el inicio.

## Request body

| Input to `recalculateQuotes` | HTTP body | Meaning |
|------------------------------|-----------|---------|
| Sin argumento / `{}` | Omitido | Todas las estrategias. Uso de Ranking. |
| `{ strategy: null }` | Omitido | Todas las estrategias. |
| `{ strategy: 'OFFICIAL' }` | `{"strategy":"OFFICIAL"}` | Sólo el código exacto indicado. |

El adaptador no copia filtros de Ranking ni corrige códigos inválidos.

## Success response

```json
{
  "runId": "7f1c2a9e-3b7d-4c51-9a3e-2d4f6b8c0e11",
  "calculatedAt": "2026-10-28T18:00:00Z",
  "requestedStrategy": null,
  "strategies": [
    {
      "code": "OFFICIAL",
      "version": 1,
      "calculated": 2210,
      "noStats": 270,
      "insufficientHistory": 0
    }
  ],
  "skippedStrategies": [],
  "playersProcessed": 2480,
  "playersFailed": 0,
  "activeStrategy": "OFFICIAL",
  "marketValueUpdated": true
}
```

La UI presenta todos los campos requeridos aunque las colecciones estén vacías o los conteos sean cero.

## Error contract

| Status | Code | UI behavior |
|--------|------|-------------|
| 401 | `unauthorized` | Se conserva el flujo global de sesión de httpClient. |
| 403 | `forbidden` | Mensaje claro y acción oculta para el token vigente. |
| 409 | `recalculation_in_progress` | Texto exacto `Ya hay un recálculo en curso`; acción vuelve a estar disponible. |
| 400/500/otro | contractual o inesperado | Mensaje genérico, sin detalles internos; permite reintento. |
| Red/timeout | `NetworkError` | Mensaje de conexión genérico; permite reintento. |

Ningún error dispara la recarga de Ranking.

## `recalculateQuotes` contract

- Firma `recalculateQuotes({ strategy } = {}, { signal } = {})`.
- Hace exactamente una llamada a `request` con path `/quotes/recalculate` y method `POST`.
- Reenvía la señal opcional.
- Devuelve la misma referencia de respuesta y propaga los mismos errores.
- Usa el timeout de 10 segundos del cliente compartido; no lo modifica ni lo evita.
