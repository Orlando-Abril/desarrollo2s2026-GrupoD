# Contrato del escenario: pedidos HTTP y aserciones

**Feature**: `specs/021-e2e-escenario-cotizaciones` | **Fecha**: 2026-10-26

La feature no expone interfaces nuevas. Consume sin cambios estos contratos:
- [quotes-api.yaml (016)](../../016-recalculo-cotizaciones/contracts/quotes-api.yaml);
- [quote-history-api.yaml (017)](../../017-historial-cotizaciones/contracts/quote-history-api.yaml);
- [ranking-api.yaml (018)](../../018-ranking-jugadores/contracts/ranking-api.yaml).

Este documento fija la secuencia de pedidos que hace `QuoteEvolutionE2ETest` y qué verifica en cada uno. Todos los pedidos llevan `X-API-KEY` del ADMIN (research R2).

## 1. Preparación

| Paso | Pedido / acción | Verifica |
|------|-----------------|----------|
| 1.1 | `E2EFixtures.createPlayersFromFiveLeagues()` | 5 jugadores, uno por liga |
| 1.2 | `POST /auth/register` `{username: e2e-admin, email, password}` | 201 con `apiKey` no vacía |
| 1.3 | `UPDATE users SET role = 'ADMIN' WHERE username = 'e2e-admin'` | 1 fila actualizada |
| 1.4 | Estadísticas de la columna "Antes de la corrida 1" ([data-model.md](../data-model.md)) por `PlayerStatsRepository` | — |

## 2. Corridas (i = 1, 2, 3)

| Paso | Pedido / acción | Verifica |
|------|-----------------|----------|
| 2.0 | Para i = 2 y 3: estadísticas de la columna i con `fetchedAt` nueva | — |
| 2.1 | `clock.set(instante i)` y `POST /quotes/recalculate` sin cuerpo | 200, `calculatedAt` = instante i, `playersProcessed` 5, `playersFailed` 0, `skippedStrategies` = [], `strategies` con `OFFICIAL` y `MOMENTUM` en versión 1, `activeStrategy` OFFICIAL, `marketValueUpdated` true |
| 2.2 | Corrida 1: conteos de `strategies` | OFFICIAL `calculated` 5; MOMENTUM `insufficientHistory` 5 |
| 2.3 | Corridas 2 y 3: conteos de `strategies` | OFFICIAL `calculated` 5; MOMENTUM `calculated` 5 |
| 2.4 | `playerRepository.findById(id).getMarketValue()` de los 5 | Igual (`compareTo`) al `value` OFFICIAL de la corrida i del historial (FR-018). Se verifica después de leer el historial en §3. |
| 2.5 | Sólo antes de la corrida 3: `GET /players/ranking?strategy=MOMENTUM` | 200. Se guarda para §5 (FR-017). |

## 3. Historial (por jugador × estrategia)

`GET /players/{id}/quotes?strategy={OFFICIAL|MOMENTUM}`

- 200 y un arreglo de **3** elementos.
- `calculatedAt` = [instante 1, instante 2, instante 3], en ese orden.
- Todos con `strategy` = la pedida y `version` = 1.
- OFFICIAL: los 3 en `CALCULATED`.
- MOMENTUM: [0] en `INSUFFICIENT_HISTORY` con `value` 1.0000; [1] y [2] en `CALCULATED`.

## 4. Cotización vigente por fecha

`GET /players/{id}/quotes?strategy=X&at=D`, con D = `2026-10-05`, `2026-10-12` y `2026-10-19`:
- 200 con un objeto igual al elemento del historial de esa fecha: mismos `value`, `status`, `version` y `calculatedAt`.

`GET /players/{id}/quotes?strategy=X&at=2026-10-04`, para un jugador y las dos estrategias:
- 404 con `error` = `quote_not_found`.

## 5. Evolución (FR-011 a FR-014)

Sobre los arreglos de §3:

| Jugador | Aserción |
|---------|----------|
| Premier (mejora) | OFFICIAL[2].value > OFFICIAL[1].value **y** MOMENTUM[2].value > MOMENTUM[1].value |
| Bundesliga (deja de jugar) | MOMENTUM[2].value < MOMENTUM[1].value |
| Los 5 | Para i = 1, 2: \|MOMENTUM[i].value / MOMENTUM[i−1].value − 1\| ≤ 0.15 + 0.00005 (margen de redondeo a 4 decimales) |

## 6. Ranking final y caché (FR-015 a FR-017)

| Pedido | Verifica |
|--------|----------|
| `GET /players/ranking?strategy=OFFICIAL` | `totalElements` 5. `items[k].player.id` en el orden de los 5 últimos `value` OFFICIAL, de mayor a menor (desempate por id ascendente). `items[k].value` = último `value` del historial. `strategy` OFFICIAL y `version` 1. `rank` 1..5. |
| `GET /players/ranking?strategy=MOMENTUM` | Lo mismo con MOMENTUM. El `value` del jugador Premier difiere del de la consulta 2.5 y es el de la corrida 3 (caché invalidada). |
| `GET /players/ranking` (sin `strategy`) | Mismos `items` que con `strategy=OFFICIAL`. |

## 7. Mensajes de fallo

Cada aserción de §3 a §6 se etiqueta con la liga, la estrategia y la fecha, por ejemplo `"BUNDESLIGA MOMENTUM 2026-10-19"`. Así un fallo indica qué parte del escenario se rompió sin tener que leer el stack trace.
