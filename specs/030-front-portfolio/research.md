# Phase 0 Research: Pantalla Mi portfolio

## R1. Resolución de la identidad actual

**Decision**: `portfolioApi.js` expondrá `getCurrentAccount()` para `/auth/me`. `usePortfolio` resolverá esa identidad una vez al montar y usará el id recibido para portfolio e historial. No se modifica `SessionContext`.

**Rationale**: la sesión actual conserva token, tipo y username, pero no id. El contrato 028 exige id en ambos paths y ya provee `/auth/me`. Mantener la identidad dentro de la feature evita ampliar estado global y respeta la restricción sobre archivos protegidos.

**Alternatives considered**:

- Agregar id al login y a `SessionContext`: descartado porque cambia contratos/estado global fuera del alcance.
- Inferir el id desde el token o username: descartado por acoplamiento y porque la API ya ofrece una fuente autorizada.
- Pedir el id en la URL o interfaz: descartado por privacidad y riesgo de consultar otra cuenta.

## R2. Orquestación y fallas parciales

**Decision**: `usePortfolio` mantiene un estado de identidad compartido y, después de resolverla, dos estados independientes: portfolio e historial. Cada recurso tiene su propio `AbortController`, `retryKey`, error y reintento; cambiar página sólo reinicia historial.

**Rationale**: cumple el requisito de conservar resumen/gráficos si falla el historial y viceversa. El patrón de `useRanking` ya aborta solicitudes reemplazadas y comprueba `signal.aborted`, necesario porque `httpClient` normaliza un aborto como `NetworkError`.

**Alternatives considered**:

- `Promise.all` con un único estado: descartado porque una falla oculta ambos recursos.
- Hooks públicos separados: descartado porque el equipo pidió `usePortfolio` y la identidad es una precondición común; internamente se mantienen efectos separados.
- Guardar datos financieros globalmente: descartado para no mostrar datos viejos tras logout o cambio de sesión.

## R3. Paginación estable y respuestas tardías

**Decision**: la UI usa páginas humanas 1..N y la API recibe `page - 1`, tamaño fijo 20. El hook aborta la solicitud anterior y descarta cualquier resolución tardía. Si una página queda fuera de rango, corrige a la última válida y la vuelve a pedir antes de publicar un vacío engañoso.

**Rationale**: coincide con el contrato backend base cero y el patrón existente. La corrección evita “página 3 de 2” si cambia el total entre pedidos. Los botones se deshabilitan durante la carga para evitar solicitudes duplicadas.

**Alternatives considered**:

- Mostrar páginas base cero: descartado por experiencia de usuario.
- Reutilizar `Pager` sin cambios: descartado porque no permite deshabilitar todos los controles durante una carga; `TransactionsTable` tendrá controles anterior/siguiente acotados.
- Publicar página vacía fuera de rango: descartado por FR-018 y el edge case definido.

## R4. Precisión y modelos de vista

**Decision**: crear `portfolioFormat.js` con formato `es-AR` de exactamente 4 decimales y signo explícito para resultados. Los totales e importes se presentan sin recalcularlos. `portfolioViewModel.js` sólo renombra/proyecta campos y calcula la proporción visual de distribución.

**Rationale**: `utils/format.js` muestra 2 decimales y no satisface FR-008. El backend 028 ya garantiza totales coherentes; recalcularlos en JavaScript introduciría diferencias. La única derivación necesaria es `currentValue / totalCurrentValue` para la dona.

**Alternatives considered**:

- Cambiar `formatCredits` global a 4 decimales: descartado porque alteraría todas las pantallas existentes.
- Usar aritmética decimal adicional en el frontend: descartado porque no se realizan operaciones financieras.
- Calcular resultado como valor menos costo: descartado; se usa `profitLoss` contractual.

## R5. Gráfico de rendimiento

**Decision**: `PerformanceChart` usará barras agrupadas por jugador para PPC y cotización actual, altura adaptable a la cantidad de posiciones, tooltip con nombre completo y leyenda textual. Una tabla alternativa con Jugador, Precio promedio y Cotización actual se genera desde el mismo arreglo y en el mismo orden, dentro de un desplegable nativo visible y operable con teclado.

**Rationale**: una comparación de dos magnitudes por categoría es más legible con barras que con una línea temporal inexistente. La tabla garantiza exactitud, acceso por lector de pantalla y una base estable para tests.

**Alternatives considered**:

- Línea de evolución: descartada porque el contrato sólo entrega una fotografía actual.
- Sólo SVG con tooltip: descartado porque depende de puntero y no comunica valores exactos a todos los usuarios.
- Reordenar por rendimiento: descartado porque la spec exige conservar el orden recibido.

## R6. Distribución de activos

**Decision**: `AllocationDonut` representará `currentValue` por jugador, conservará el orden contractual y usará una paleta determinística de tokens existentes. Leyenda y tabla alternativa desplegable mediante teclado mostrarán nombre, valor y participación. Con total cero no se dibuja la dona ni se calculan porcentajes; se explica el caso y se conservan valores absolutos. Los tests comprobarán que la suma de porcentajes presentados permanece dentro de 0,1 puntos porcentuales de 100%.

**Rationale**: el valor actual permite comparar activos con diferentes cotizaciones. Texto y tabla evitan depender de colores, especialmente cuando se cicla una paleta para más de seis posiciones.

**Alternatives considered**:

- Distribuir por cantidad de tokens: descartado porque tokens de jugadores distintos no son equivalentes.
- Generar colores arbitrarios: descartado por consistencia visual y contraste.
- Dividir por cero y mostrar 0%: descartado porque sería información engañosa.

## R7. Accesibilidad, responsive y movimiento

**Decision**: gráficos con `figure`, `figcaption`, `role="img"`, nombre accesible, leyenda HTML y tabla alternativa dentro de `<details><summary>Ver datos en tabla</summary>…</details>`; animación Recharts desactivada si `prefers-reduced-motion` está activo. Las tablas principales siguen el patrón responsive de `MyOrdersList`: encabezado de escritorio y filas tipo tarjeta con `data-label` debajo de 900 px. Texto, componentes y elementos gráficos necesarios cumplirán WCAG 2.2 AA: 4,5:1 para texto normal y 3:1 para texto grande, componentes y gráficos.

**Rationale**: son patrones ya usados y testeados en QuoteChart y trading. Evitan scroll horizontal de página, preservan relaciones campo/valor y no dependen del color o puntero.

**Alternatives considered**:

- Scroll horizontal para todas las tablas: descartado porque la spec exige que todos los datos sean operables a 360 px sin scroll de página.
- Tabla alternativa sólo en texto concatenado: descartado porque pierde relaciones de fila y columna.
- Confiar sólo en CSS global para movimiento reducido: descartado porque las animaciones de Recharts también se controlan desde JavaScript.

## R8. Estrategia de pruebas

**Decision**: tests colocados junto al código con Vitest y Testing Library. En gráficos se mockea únicamente `ResponsiveContainer` para darle dimensiones en jsdom y se asertan roles, captions, leyendas y tablas alternativas, no paths SVG. Las clases semánticas y etiquetas/signos verifican ganancia, pérdida y neutro.

**Rationale**: coincide con `QuoteChart.test.jsx`, evita pruebas frágiles sobre internals de Recharts y valida el contrato observable. Los tests del hook usan promesas diferidas para abortos, respuestas tardías, reintentos parciales y páginas fuera de rango.

**Alternatives considered**:

- Snapshots amplios: descartados por baja precisión y fragilidad.
- Asertar color computado en jsdom: descartado; se prueban clase/token semántico y texto accesible.
- Probar sólo PortfolioPage: descartado porque dificultaría aislar proyecciones, carreras y estados.

### Clasificación del helper de tests

`sonar-project.properties` clasifica como tests únicamente `frontend/src/**/*.test.js` y `frontend/src/**/*.test.jsx`, que son también los patrones de suites reales usados por Vitest. El helper compartido se mantendrá como `frontend/src/features/portfolio/portfolioTestData.js` para que Vitest no lo descubra como suite. Se agregará únicamente esa ruta exacta a `sonar.test.inclusions`; así Sonar lo excluye del código productivo sin ampliar el patrón ni descubrir archivos sin tests reales.

## R9. Alcance del backend y constitución

**Decision**: no se modifica backend ni OpenAPI; los contratos de la 028 son la fuente de verdad. Se evalúan §2.2, §3.2 y las reglas aplicables. La referencia solicitada a §3.4 se documenta como inexistente en la constitución v1.1.0 y se cubre conservadoramente §3.3 y DoD §7.

**Rationale**: los tres endpoints necesarios ya existen y están documentados. Inventar §3.4 o cambiar contratos ampliaría el alcance sin fundamento.

**Alternatives considered**:

- Crear un endpoint agregado para la pantalla: descartado; duplica contratos y no es necesario.
- Ignorar silenciosamente §3.4: descartado porque ocultaría una inconsistencia de gobernanza.
