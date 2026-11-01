# UI Contracts: comparador de estrategias

Este documento fija interfaces internas y comportamientos observables. Los formatos HTTP son los de [strategy-comparison-api.yaml](../../031-comparador-estrategias/contracts/strategy-comparison-api.yaml); no se crean endpoints.

## API adapters

| Adaptador | Solicitud | Resultado que consume la UI |
|-----------|-----------|-----------------------------|
| `strategyApi.getActiveStrategy({ signal })` | `GET /strategies/active` | `active`, `alternative`, `marketStrategy`, `pendingRecalculation`. |
| `strategyApi.changeActiveStrategy(code, { signal })` | `PUT /strategies/active` con `{ "strategy": code }` | Configuración resultante y `changed`; 400/403/red se manejan por estado. |
| `comparisonApi.getLatestQuotes(ids, 'MOMENTUM', { signal })` | `GET /quotes/latest?playerIds=...&strategy=MOMENTUM` | Una entrada por id existente; `quote: null` es ausencia conocida. |
| `comparisonApi.getOpportunities(filters, { signal })` | `GET /quotes/opportunities` con `league`, `position` opcionales | Dos listas y contexto de estrategia; el orden viene del servidor. |
| `portfolioApi.getUserPortfolio(id, { strategy, signal })` | Sin query para Mercado actual; `?strategy=MOMENTUM` para Momentum | Campos originales valuados con `marketValue` persistido y `strategyValuation` únicamente en modo Momentum. |
| `quotesApi.getPlayerQuotes(id, options, { signal })` | Dos consultas existentes, una por OFFICIAL y una por MOMENTUM, mismo rango | Historias independientes para el gráfico comparativo. |
| `portfolioApi.getCurrentAccount({ signal })` | `GET /auth/me` | `role` decide visibilidad inicial del control ADMIN. |

El cliente HTTP existente adjunta credenciales y traduce fallos a `ApiError` o `NetworkError`. Ningún adaptador recalcula ni reordena oportunidades.

## StrategyViewContext y StrategySelector

- El contexto expone `viewStrategy` (`MARKET_CURRENT | MOMENTUM`, inicial `MARKET_CURRENT`), `setViewStrategy`, `activeConfig`, `configStatus`, `retryConfig` y `refreshConfig` mediante un store compartido como valor por defecto y un Provider opcional para pruebas. La suscripción entre Header y páginas no requiere cambios en `App.jsx`, `main.jsx` ni `AppLayout.jsx`.
- `StrategySelector` muestra «Ver con: Mercado actual / Momentum». «Mercado actual» es el nombre fijo del precio `marketValue` persistido, no de OFFICIAL; junto a él se indica el código real de `marketStrategy`. La etiqueta de Momentum proviene de la referencia de estrategia recibida. Se monta en Header sólo cuando hay sesión y la ruta es `/album`, `/jugadores/:id` o `/portfolio`.
- Cambiar opción actualiza el contexto sin petición de escritura. El modo persiste al navegar entre esas rutas y se reinicia al cambiar la identidad autenticada.
- Si la configuración no cargó, el selector comunica carga o error y ofrece reintento; no declara una estrategia como precio de operación sin respuesta válida.
- `activeConfig.active` se muestra como configuración activa; `marketStrategy` y `pendingRecalculation` gobiernan la leyenda del precio real. Si `active` difiere de `marketStrategy`, el precio para operar sigue siendo `marketValue` hasta el próximo recálculo. Ninguna comparación modifica el panel de trading.

## Álbum

- `useLatestPageQuotes(album.items, viewStrategy)` consulta sólo los ids visibles cuando `viewStrategy=MOMENTUM`; páginas vacías no provocan consulta.
- `AlbumGrid` pasa la prop opcional `alternativeQuote` a Figurita. Al no pasarla, el DOM y estilo por defecto quedan como antes. `AlbumTable` muestra la misma comparación en su modo tabla.
- La cotización alternativa incluye etiqueta, valor, diferencia y estado si no es `CALCULATED`. `quote:null` muestra «Sin cotización Momentum». Error de consulta mantiene los precios de mercado y ofrece reintento sin afirmar que falten cotizaciones.
- Una respuesta de página anterior se descarta tras cambiar página, filtros o modo.

## Detalle y QuoteChart

- El modo Mercado actual conserva una curva y los controles actuales de estrategia/rango del gráfico; esa curva lleva el código histórico real OFFICIAL o MOMENTUM y el precio destacado sigue siendo `marketValue`. El modo Momentum usa dos consultas del rango actual y oculta el control local de una sola estrategia para evitar instrucciones contradictorias.
- Se renderizan dos líneas con leyenda textual OFFICIAL/MOMENTUM. El tooltip informa fecha, estrategia y valor de cada punto presente; la tabla accesible lista ambos historiales, incluida versión y estado.
- Si una historia está vacía, se explica; si una falla, se informa y se ofrece reintento sin trazar una línea falsa. El precio de mercado y `TradingSection` quedan separados del gráfico.
- `#operar` conduce al panel existente, desplaza la vista hasta él y sitúa el foco en un encabezado o control apropiado; debe respetarse el comportamiento normal al entrar sin hash.

## Portfolio

- Mercado actual consulta el portfolio sin `strategy` y conserva la valuación al precio `marketValue` persistido, aunque lo haya fijado MOMENTUM. El modo Momentum consulta `strategy=MOMENTUM` al mismo id; cambios rápidos de modo cancelan la solicitud anterior.
- `PositionsTable` agrega una columna «Valor / resultado Momentum» con precio y estado contextual; el resumen muestra totales comparativos. Importes de mercado siguen visibles y separados.
- `null` de una posición se anuncia «Sin cotización Momentum»; `positionsWithoutQuote > 0` hace que los totales comparativos se anuncien «No disponibles».
- Historial de transacciones no cambia con el selector.

## Ranking: oportunidades

- `OpportunitiesSection` se monta en `RankingPage` debajo del ranking principal, sin ruta ni ítem de menú nuevo. Sus filtros de liga y posición son propios y no modifican los del ranking.
- Dos listas tituladas «Subvaluados» y «Sobrevaluados» muestran precios de mercado, alternativa, diferencias y estrategia de cada valor; se conservan el orden y exclusiones de la 031.
- Cada fila ofrece `Operar` como enlace a `/jugadores/{playerId}#operar`. Se proveen carga, error con reintento y vacío por lista.
- Después de un PUT exitoso de estrategia activa, la sección vuelve a consultar oportunidades porque la alternativa puede cambiar aunque `marketValue` no haya cambiado. Después de un recálculo exitoso vuelve a consultarlas junto con configuración y datos de mercado.

## Ranking: ActiveStrategyControl

- Se monta junto a `RecalculateButton`. Antes de mostrarlo, `GET /auth/me` confirma `role=ADMIN`; USER no ve selector, confirmación ni acción PUT.
- Presenta la estrategia activa de `GET /strategies/active`, con opción de la alternativa. La confirmación nombra estrategia anterior y nueva y aclara que el precio cambia con el próximo recálculo.
- Confirmar envía un solo PUT. Éxito con `changed=true`: refresca configuración compartida y oportunidades, y muestra Toast con texto del efecto diferido y una indicación accionable hacia «Recalcular cotizaciones», sin pulsarlo automáticamente. `changed=false` informa que ya estaba activa.
- 403: mensaje de permiso y ocultación para esa identidad. 400: mensaje claro de selección inválida y posibilidad de corregir. Error de red: mensaje de resultado incierto y GET nuevo antes de mostrar un estado definitivo. 401 usa la redirección de sesión existente.
- La acción de recálculo de la 025 conserva su confirmación y resumen. Al completarse, refresca Ranking, oportunidades y configuración para retirar el aviso pendiente si corresponde.

## Formato y accesibilidad

- Importes con cuatro decimales y diferencias porcentuales con dos; signo explícito y etiqueta textual «mayor/menor» o «subvaluado/sobrevaluado». Un mercado no positivo muestra porcentaje no disponible.
- Controles nativos o equivalentes accesibles, foco visible, confirmación con foco contenido y devuelto, Toast anunciado, leyenda y tabla accesible para las curvas.
- A 360/768/1440 px no hay scroll horizontal global. Las columnas comparativas pueden adaptarse a bloques etiquetados en móvil sin perder valores.
