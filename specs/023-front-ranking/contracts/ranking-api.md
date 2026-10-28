# Contract: Ranking API consumer

## Operation

`GET /players/ranking`

Pedido autenticado mediante el bearer token que agrega `httpClient.request`.

## Query parameters

| Parameter | Required | Values / limits | Frontend behavior |
|-----------|----------|-----------------|-------------------|
| `strategy` | No | `OFFICIAL`, `MOMENTUM` | Se omite para usar la activa. Nunca se envía vacío. |
| `league` | No | Clave de `LEAGUES` | Se omite para todas. Nunca se envía vacío. |
| `position` | No | Clave de `POSITIONS` | Se omite para todas. Nunca se envía vacío. |
| `page` | No | Integer >= 0, default 0 | El hook envía la página UI menos uno. `0` debe conservarse. |
| `size` | No | Integer 1..100, default 20 | La pantalla envía 20. |

Orden canónico del query construido por el cliente: strategy, league, position, page, size.

## Success response

```json
{
  "items": [
    {
      "rank": 1,
      "player": {
        "id": 42,
        "name": "Jugador",
        "team": "Equipo",
        "league": "LA_LIGA",
        "positions": ["FORWARD", "MIDFIELDER"]
      },
      "value": 12.5000,
      "percentageChange": 25.00,
      "strategy": "OFFICIAL",
      "version": 1
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

`percentageChange` es un campo requerido nullable. La página vacía usa `items: []`, `totalElements: 0`, `totalPages: 0`. Una página válida fuera de rango puede tener items vacíos conservando totales mayores a cero.

## Ordering contract

- El backend ordena valor descendente y desempata por ID de jugador ascendente.
- `rank` se calcula sobre el conjunto filtrado antes de paginar.
- El frontend conserva orden y rank; no los recalcula.

## Error response

```json
{
  "error": "validation_error",
  "message": "Descripción segura"
}
```

- 400 `validation_error`: liga, posición o paginación inválida.
- 400 `unknown_strategy`: estrategia inválida.
- 401: credencial ausente, inválida o vencida; el cliente compartido activa el flujo de sesión.
- Error de red/timeout/abort: `httpClient` entrega `NetworkError`; el hook suprime sólo el que pertenece a una señal ya abortada.

## `getRanking` contract

- Acepta criterios y un segundo argumento opcional `{ signal }`.
- Llama una vez a `request` con path y signal.
- Devuelve la misma referencia de respuesta resuelta por `request`.
- No valida, ordena, pagina ni transforma datos.
