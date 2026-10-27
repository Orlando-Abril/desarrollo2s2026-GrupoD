# Data Model: E2E del escenario de evolución de cotizaciones

**Feature**: `specs/021-e2e-escenario-cotizaciones` | **Fecha**: 2026-10-26

No se agregan entidades, columnas ni tablas. Este documento fija los **datos del escenario** que carga el test.

## Jugadores

Son los cinco jugadores canónicos de `E2EFixtures.createPlayersFromFiveLeagues()` (012), sin cambios:

| Liga | Nombre | Posiciones | Rol en el escenario |
|------|--------|------------|---------------------|
| PREMIER_LEAGUE | E2E Premier Forward | FORWARD | **Mejora** (FR-012) |
| LA_LIGA | E2E Liga Utility | DEFENDER, MIDFIELDER | Estable |
| SERIE_A | E2E Serie Goalkeeper | GOALKEEPER | Estable |
| BUNDESLIGA | E2E Bundesliga Midfielder | MIDFIELDER | **Deja de jugar** (FR-013) |
| LIGUE_1 | E2E Ligue Defender | DEFENDER | Estable |

## Corridas

| Corrida | Instante del reloj | Fecha `at` |
|---------|--------------------|------------|
| 1 | 2026-10-05T18:00:00Z | 2026-10-05 |
| 2 | 2026-10-12T18:00:00Z | 2026-10-12 |
| 3 | 2026-10-19T18:00:00Z | 2026-10-19 |
| — | (ninguna) | 2026-10-04: 404 `quote_not_found` |

## Estadísticas acumuladas de temporada por corrida (`PlayerStats`)

Formato: minutos / goles / asistencias / tiros / pases clave / gambetas / quites / rating. Las tarjetas son 0 en todas.

| Jugador | Antes de la corrida 1 | Antes de la corrida 2 | Antes de la corrida 3 |
|---------|-----------------------|-----------------------|-----------------------|
| Premier | 900 / 4 / 2 / 20 / 10 / 10 / 5 / 7.00 | 990 / 5 / 2 / 22 / 11 / 11 / 5 / 7.05 | 1080 / 7 / 3 / 26 / 13 / 13 / 5 / 7.40 |
| La Liga | 900 / 1 / 1 / 5 / 10 / 5 / 25 / 6.90 | 990 / 1 / 1 / 5 / 11 / 6 / 28 / 6.90 | 1080 / 1 / 1 / 6 / 12 / 6 / 31 / 6.90 |
| Serie A | 900 / 0 / 0 / 0 / 5 / 0 / 5 / 6.80 | 990 / 0 / 0 / 0 / 6 / 0 / 6 / 6.80 | 1080 / 0 / 0 / 0 / 6 / 0 / 6 / 6.80 |
| Bundesliga | 900 / 2 / 2 / 10 / 15 / 10 / 15 / 7.00 | 990 / 2 / 2 / 11 / 16 / 11 / 16 / 7.00 | **990** / 2 / 2 / 11 / 16 / 11 / 16 / 7.00 |
| Ligue 1 | 900 / 1 / 0 / 3 / 5 / 2 / 30 / 7.00 | 990 / 1 / 0 / 3 / 6 / 2 / 33 / 7.00 | 1080 / 1 / 1 / 4 / 6 / 3 / 36 / 7.10 |

**`fetchedAt`**: cada columna usa una distinta. Son la víspera de cada corrida a las 10:00 UTC: `2026-10-04T10:00Z`, `2026-10-11T10:00Z` y `2026-10-18T10:00Z`. En la última columna, Bundesliga mantiene las estadísticas, pero con `fetchedAt` nueva. Así se registra un snapshot y una ventana con 0 minutos (spec, casos borde).

## Ventanas resultantes (014)

| Jugador | Ventana 1 (corrida 1 → 2) | Ventana 2 (corrida 2 → 3) |
|---------|---------------------------|---------------------------|
| Premier | 90' · 1 gol · rating 7.55 | 90' · 2 goles, 1 asistencia · rating 10.00 (acotado) |
| Bundesliga | 90' · 0 goles · rating 7.00 | **0'** · sin rating |
| Resto | 90' · cerca o por debajo de su promedio | 90' · cerca de su promedio |

## Estados esperados por cotización

| Estrategia | Corrida 1 | Corrida 2 | Corrida 3 |
|------------|-----------|-----------|-----------|
| OFFICIAL | `CALCULATED` (los 5 superan el umbral de 90') | `CALCULATED` | `CALCULATED` |
| MOMENTUM | `INSUFFICIENT_HISTORY`, valor 1.0000 | `CALCULATED` | `CALCULATED` |

Todas las cotizaciones tienen `version` 1.

## Relaciones verificadas

- historial[i] = `at`(fecha i), por jugador y estrategia.
- ranking(X)[k].value = historial(X)[2].value del jugador de esa fila, ordenado por valor descendente y luego por id ascendente.
- `Player.marketValue` después de la corrida i = historial(OFFICIAL)[i].value.

Los valores de referencia del modelo están en [research.md](research.md) R4 y no se verifican exactos.
