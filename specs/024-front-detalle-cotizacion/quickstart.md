# Quickstart: validar el detalle de jugador con gráfico

Guía de validación. Los contratos están en [contracts/](./contracts/) y los estados en [data-model.md](./data-model.md).

## Prerrequisitos

- Node.js 22 y dependencias instaladas: `cd frontend && npm install` (incluye `recharts@3.10.1` y `react-is`).
- Para la validación manual: backend local en `http://localhost:8080` con jugadores cargados y cotizaciones recalculadas en ambas estrategias, al menos dos recálculos para ver la variación. Se pueden disparar con `POST /quotes/recalculate` o dejar que corra el scheduler (020).
- Un usuario registrado para ingresar.

## Validación automática

```bash
cd frontend
npm test          # Vitest: api, quoteUtils, hook, VariationBadge, QuoteChart, PlayerDetailPage, AlbumPage, routes
npm run lint      # Oxlint sin errores
npm run build     # build de Vite sin errores
```

Resultado esperado: todo en verde, sin warnings de recharts por tamaño 0 en la salida de tests (el mock de [R2](./research.md#r2-recharts-en-jsdom-vitest) aplicado).

Escenarios que deben estar cubiertos por tests (SC-008):

| Escenario | Archivo |
|---|---|
| Paths y parámetros, `at` incompatible con `from`/`to` | `api/quotesApi.test.js`, `api/playerDetailApi.test.js` |
| Rangos UTC, variación, `findPrevious`, `parsePlayerId` | `features/player-detail/quoteUtils.test.js` |
| Abort al cambiar estrategia o rango, y respuestas viejas descartadas | `features/player-detail/usePlayerDetail.test.jsx` |
| 404 e id inválido → "Jugador inexistente" sin reintento | `pages/PlayerDetailPage.test.jsx` |
| Error → Reintentar → detalle | `pages/PlayerDetailPage.test.jsx` |
| Historial vacío → "Todavía sin cotizaciones" con selectores activos | `pages/PlayerDetailPage.test.jsx` |
| Cambio de estrategia y rango → tabla con las filas nuevas | `pages/PlayerDetailPage.test.jsx` |
| Fecha puntual: found, empty (404), descarte al cambiar estrategia | `pages/PlayerDetailPage.test.jsx` |
| Estadísticas faltantes → "—" | `pages/PlayerDetailPage.test.jsx` |
| Tabla alternativa con las mismas filas que los datos (incluye `NO_STATS` y versiones mezcladas); contenido de `QuoteTooltip` | `features/player-detail/QuoteChart.test.jsx` |
| Figurita → `/jugadores/:id` | `pages/AlbumPage.test.jsx` |
| `/jugadores/:id` sin sesión → `/ingresar` | `routes/routes.test.jsx` |

## Validación manual (`npm run dev`)

| # | Pasos | Resultado esperado |
|---|---|---|
| V1 | Ingresar y, en el Álbum, hacer clic en una figurita | Navega a `/jugadores/{id}`. La figurita se ve igual que antes en reposo y en hover. |
| V2 | En el Álbum, recorrer con Tab y apretar Enter sobre una figurita | Foco visible y navega al detalle. |
| V3 | En el detalle, mirar la cabecera y el gráfico | Cotización actual, variación con ▲/▼/= y gráfico "último mes" en la estrategia de la cotización actual, con el color de la liga. Verificación manual de SC-002: cabecera, estadísticas y gráfico visibles en menos de 2 s. |
| V4 | Cambiar a Momentum y luego a "Toda la temporada" | Verificación manual de SC-003: el gráfico se actualiza en menos de 1 s. Si se cambia rápido, queda la última selección. |
| V5 | Pasar el mouse por un punto; tocar un punto en mobile (o con emulación táctil de DevTools); después enfocar el gráfico y usar las flechas | En los tres casos, el tooltip muestra fecha, valor, la etiqueta de la estrategia ("Oficial" o "Momentum") y la versión. |
| V6 | Consultar una fecha intermedia y después una anterior a la primera cotización | Primero el valor vigente; después "Sin cotización para esa fecha". |
| V7 | DevTools a 360, 768 y 1280 px | Sin scroll horizontal de página; las secciones se apilan en 360 px. |
| V8 | Abrir un jugador de cada una de las 5 ligas | Cada cabecera y cada línea usan el color de su liga; las evoluciones se ven en todas (§3.3 escenario 2). |
| V9 | Abrir `/jugadores/999999999` y `/jugadores/abc` | "Jugador inexistente" con "Volver al álbum" y sin "Reintentar". |
| V10 | Apagar el backend y recargar un detalle; prenderlo y apretar "Reintentar" | Error y luego detalle completo. |
| V11 | Lector de pantalla (NVDA o VoiceOver) sobre el gráfico | Lee el caption y la tabla con las mismas filas que el gráfico. |
| V12 | Abrir un jugador sin cotizaciones ni estadísticas | Cotización "—", variación "—", "Todavía sin cotizaciones" y todas las estadísticas en "—". |

## Cierre

- CI de GitHub Actions en `SUCCESS` sobre la rama.
- SonarCloud con el Quality Gate aprobado y como máximo 10 issues menores (§3.2).
