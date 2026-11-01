# Research: comparador de estrategias en el frontend

## 1. Estrategia activa y precio de operación

**Decision**: `GET /strategies/active` alimenta las etiquetas de estrategia y el estado global: `active` es la configuración elegida para el próximo recálculo; `marketStrategy` indica qué estrategia fijó el `marketValue` vigente; `pendingRecalculation` señala que difieren. Las órdenes y estimaciones siguen usando exclusivamente ese precio persistido. El selector personal ofrece «Mercado actual» (`MARKET_CURRENT`, siempre `marketValue`) y Momentum (`MOMENTUM`); no hace PUT. OFFICIAL y MOMENTUM permanecen como códigos reales para las curvas históricas.

**Rationale**: La 031 documenta explícitamente que PUT no modifica `marketValue` hasta el siguiente recálculo. La frase del pedido «la activa es la que se usa para operar» se presenta al usuario como intención de configuración; durante el intervalo pendiente, el valor de mercado real sigue siendo el de la última corrida. Mostrar ambos evita atribuir un precio nuevo antes de que exista.

**Alternatives considered**: Usar `active` como si ya fuera el origen del precio vigente, descartado porque contradice la respuesta `pendingRecalculation` y podría confundir la operación; recalcular al confirmar PUT, descartado porque la 031 lo difiere y el usuario pidió ofrecer el botón de la 025.

## 2. Estado compartido y etiquetas

**Decision**: Nuevo `StrategyViewContext` ofrece un store de módulo compartido como valor por defecto del contexto, suscripción mediante `useSyncExternalStore` y Provider opcional para pruebas. Guarda `viewStrategy` en memoria, inicialmente `MARKET_CURRENT`, y la última respuesta de `GET /strategies/active`, con carga, error, reintento y actualización tras PUT o recálculo. Nuevo `StrategySelector` en Header se muestra para las rutas Álbum, Detalle y Portfolio. «Mercado actual» es la etiqueta fija del modo de precio persistido y se acompaña del código real de `marketStrategy`; la etiqueta de Momentum procede de la referencia de estrategia recibida. Mientras no hay respuesta válida, no se atribuye origen a un precio y se ofrece reintento.

**Rationale**: Header persiste entre rutas, pero el modo requiere un estado compartido con las páginas. Un store de módulo evita modificar `App.jsx`, `main.jsx` o `AppLayout.jsx`, fuera de las excepciones de Entrega 1. La sesión no guarda el rol; el control ADMIN consulta `GET /auth/me` y se desmonta al cambiar identidad. No se introduce almacenamiento persistente porque la spec sólo exige conservar la elección durante la navegación.

**Alternatives considered**: Estado local en cada página, descartado porque perdería la elección al navegar; tomar etiquetas del catálogo local de Detalle, descartado porque la 031 es autoridad sobre activo, alternativa y versión.

## 3. Últimas cotizaciones del Álbum

**Decision**: `useLatestPageQuotes` recibe los ids de `album.items` (página visible), llama una vez a `GET /quotes/latest?playerIds=...&strategy=MOMENTUM` sólo en modo Momentum, indexa el resultado por `playerId` y cancela la consulta anterior al cambiar página, filtros, modo o desmontar. Su respuesta se pasa a la vista de figuritas y a la tabla; una `quote: null` se distingue de error de consulta.

**Rationale**: La 031 permite 1–100 ids, y el Álbum pagina en memoria después de buscar y ordenar. Consultar `responsePlayers` ignoraría la página y podría exceder el límite. La prop de Figurita permanece opcional y su ausencia conserva el aspecto previo.

**Alternatives considered**: Una llamada por figurita, descartada por costo y límite; consultar todos los jugadores del Álbum, descartado por cantidad y porque mostraría datos de páginas no visibles.

## 4. Gráfico de dos curvas

**Decision**: En modo Mercado actual, `QuoteChart` conserva la interacción actual de una serie y su selector local de estrategia, identificado con el código histórico real OFFICIAL o MOMENTUM; el precio destacado sigue siendo el `marketValue` persistido, que puede provenir de cualquiera de ellas. En modo Momentum, se solicitan ambos historiales para el mismo rango, se unen por instante de cotización sin inventar puntos, se muestran dos líneas con leyenda OFFICIAL/MOMENTUM y la tabla accesible enumera fecha, estrategia, valor, estado y versión. El selector local de una sola estrategia se oculta mientras se ven las dos curvas; el rango permanece. Si una serie falta, se explica y se muestra la disponible; fallas de una consulta no se presentan como serie completa.

**Rationale**: El gráfico existente sólo acepta una lista y una línea; su tabla y tooltip ya proporcionan una base accesible. La unión por tiempo respeta historiales con fechas distintas. Una serie vacía no debe producir una curva artificial de cero.

**Alternatives considered**: Unir por índice, descartado porque los recálculos pueden no coincidir; superponer dos gráficos, descartado porque impide comparación directa.

## 5. Portfolio y oportunidades

**Decision**: En modo Momentum, `getUserPortfolio` recibe `strategy=MOMENTUM` y se muestran los campos `strategyValuation` por posición y total, sin recomputar costos ni resultados. En Mercado actual se usa la consulta sin parámetro, valuada con el precio persistido cualquiera sea su estrategia de origen. La sección `OpportunitiesSection` vive en Ranking, independiente de filtros/página del ranking, con sus propios filtros de liga y posición y estado de consulta. `Operar` navega a `/jugadores/:id#operar`; el panel existente recibe un ancla/foco verificable sin cambiar reglas financieras.

**Rationale**: El contrato 031 calcula la valuación exacta y las oportunidades en servidor. Sus filtros son propios; reutilizar el estado del ranking acoplaría dos listados de alcance distinto. Ranking ya tiene entrada de navegación; no se añade ítem al menú.

**Alternatives considered**: Calcular portfolio y oportunidades en el cliente, descartado por diferencias de redondeo y reglas; crear una página nueva, descartado por instrucción expresa del usuario.

## 6. Control ADMIN y verificación

**Decision**: `ActiveStrategyControl` se monta junto a `RecalculateButton`, espera `GET /auth/me`, se muestra sólo con rol ADMIN y obtiene valor de `GET /strategies/active`. La selección abre confirmación, PUT se envía una vez y el éxito vuelve a consultar configuración y oportunidades; el Toast ofrece usar el botón de recálculo ya visible. Un 403 oculta el control para esa identidad; un 400 deja corregir; ante error de red se vuelve a consultar GET antes de declarar un valor definitivo. Tras recálculo exitoso se refrescan estrategia, oportunidades y datos de mercado visibles.

**Rationale**: La sesión actual sólo almacena username/token. El servidor sigue autorizando el cambio. La 025 ya dispone de confirmación, progreso y ejecución del recálculo.

**Alternatives considered**: Inferir ADMIN del username o mostrar el control hasta recibir 403, descartados porque la 028 proporciona el rol y la spec exige que USER no lo vea.

## Resolved unknowns

No quedan decisiones técnicas pendientes. Los detalles de integración se apoyan en el contrato 031 y los componentes existentes del frontend.
