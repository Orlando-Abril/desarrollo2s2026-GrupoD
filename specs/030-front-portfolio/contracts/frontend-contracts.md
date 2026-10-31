# Frontend Contracts: Pantalla Mi portfolio

## 1. Ruta y navegación

- Ruta protegida: `/portfolio` → `<PortfolioPage />` dentro de `ProtectedRoute`.
- Menú: `{ id: 'portfolio', label: 'Mi portfolio', numero: '04', ruta: '/portfolio', habilitado: true }`.
- Detalle desde posiciones y operaciones: `/jugadores/{playerId}` mediante enlace real.
- Fuera de sesión: comportamiento existente de `ProtectedRoute` hacia `/ingresar`.

## 2. `api/portfolioApi.js`

Todas las funciones devuelven el payload sin transformarlo y reenvían `AbortSignal` a `request`.

```js
getCurrentAccount({ signal } = {})
// GET /auth/me

getUserPortfolio(userId, { signal } = {})
// GET /users/{encodeURIComponent(userId)}/portfolio

getUserTransactions(
  userId,
  { page = 0, size = 20 } = {},
  { signal } = {},
)
// GET /users/{encodeURIComponent(userId)}/transactions?page={page}&size={size}
```

La query se arma con `URLSearchParams` y conserva `page=0`; no omite valores falsy válidos. Los contratos JSON son los definidos en `specs/028-portfolio/contracts/user-market-api.yaml`.

Errores: no se traduce ni se intercepta 401 en esta capa. `httpClient` ejecuta el cierre de sesión global. Los demás errores llegan al hook para un mensaje público genérico; nunca se muestra `error.message` crudo.

## 3. `usePortfolio()`

Contrato de salida:

```js
{
  identityStatus,          // 'loading' | 'ready' | 'error'
  identityError,
  retryIdentity,

  portfolioStatus,         // 'idle' | 'loading' | 'ready' | 'empty' | 'error'
  portfolio,
  portfolioError,
  portfolioRefreshing,     // true si se conserva una respuesta anterior durante recarga
  retryPortfolio,

  transactionsStatus,      // 'idle' | 'loading' | 'ready' | 'empty' | 'error'
  transactions,
  transactionsError,
  transactionsRefreshing,
  page,                    // humana, comienza en 1
  totalPages,
  totalElements,
  setPage(nextHumanPage),
  retryTransactions,
}
```

Reglas:

1. No solicita portfolio ni historial hasta resolver `account.id`.
2. Al resolver identidad, solicita portfolio y página backend 0/size 20 en paralelo.
3. `setPage` acepta sólo enteros dentro de 1..`totalPages`, ignora la página vigente y no afecta portfolio.
4. Durante una solicitud de historial los controles de página quedan deshabilitados.
5. Un retry de portfolio no solicita historial; un retry de historial conserva página y no solicita portfolio.
6. Cada efecto aborta su solicitud al desmontar o reemplazarla y comprueba `signal.aborted` antes de publicar.
7. Una página fuera de rango se corrige y se vuelve a pedir sin publicar el vacío intermedio.
8. Los estados `empty` significan respectivamente `positions.length === 0` e `items.length === 0` con respuesta válida.

## 4. `PositionsTable`

```js
<PositionsTable positions={positions} />
```

Columnas: Jugador, Tokens, Precio promedio, Cotización, Valor actual, Ganancia/Pérdida. Cada fila usa `playerId` como key y el nombre es enlace al detalle. Los importes tienen 4 decimales.

Contrato semántico de resultado:

- positivo: clase `portfolio-result--gain`, texto accesible “Ganancia” y signo `+`;
- negativo: clase `portfolio-result--loss`, texto accesible “Pérdida” y signo negativo;
- cero: clase `portfolio-result--neutral`, texto accesible “Sin variación”.

En móvil, cada `td` conserva `data-label`; el header puede ocultarse visualmente, no semánticamente.

## 5. `PerformanceChart`

```js
<PerformanceChart positions={positions} />
```

- Título: “Rendimiento del portfolio”.
- Figura/caption: “Precio promedio y cotización actual por jugador · N posiciones”.
- Barras agrupadas: `averagePurchasePrice` y `marketValue`.
- Tooltip: nombre completo y ambos valores con 4 decimales.
- Leyenda HTML: “Precio promedio” y “Cotización actual”, con texto y swatch.
- Tabla alternativa, mismo arreglo y orden: Jugador | Precio promedio | Cotización actual, dentro de un `<details>` con `<summary>Ver datos en tabla</summary>` visible y operable con teclado.
- `role="img"` y `aria-label` igual al caption.
- Altura suficiente por posición y etiquetas de eje abreviables sólo visualmente; tooltip y tabla conservan el nombre completo.
- Animación deshabilitada cuando `prefers-reduced-motion: reduce`.

## 6. `AllocationDonut`

```js
<AllocationDonut positions={positions} totalCurrentValue={totalCurrentValue} />
```

- Título: “Distribución de activos”.
- Figura/caption: “Distribución por valor actual · N posiciones”.
- Cada sector usa `currentValue`; key `playerId`; paleta determinística de tokens existentes.
- Tooltip y leyenda HTML: nombre completo, valor con 4 decimales y porcentaje.
- Tabla alternativa, mismo arreglo y orden: Jugador | Valor actual | Participación, dentro de un `<details>` con `<summary>Ver datos en tabla</summary>` visible y operable con teclado.
- Una única posición positiva muestra 100,0%.
- Si `totalCurrentValue <= 0` o no es finito: no se renderiza dona ni porcentajes; se muestra “No se puede calcular la distribución porque el valor actual total es cero.” y la tabla conserva valores con participación `—`.
- La información no depende del color; la paleta puede repetirse después del sexto jugador.

## 7. `TransactionsTable`

```js
<TransactionsTable
  pageData={transactions}
  page={page}
  status={transactionsStatus}
  onPageChange={setPage}
/>
```

Columnas: Tipo, Jugador, Cantidad, Precio unitario, Total, Fecha y hora. Compra/Venta se expresa en texto; el jugador es enlace.

Controles:

- “Página anterior”: deshabilitado si `first`, no hay datos o `status === 'loading'`.
- “Página siguiente”: deshabilitado si `last`, no hay datos o `status === 'loading'`.
- Estado textual `Página X de Y · Z operaciones` en región `aria-live="polite"`.
- `onPageChange(page - 1)` y `onPageChange(page + 1)` usan páginas humanas.

Con historial vacío se muestra una explicación específica, no una tabla sin filas.

## 8. `PortfolioPage`

Composición:

1. Encabezado “Mi portfolio”.
2. Error/carga de identidad a nivel de pantalla.
3. Sección portfolio:
   - carga perceptible sin ceros;
   - error con `retryPortfolio`;
   - vacío exacto “Todavía no compraste tokens” y acción a `/album`;
   - ready: resumen, posiciones, `PerformanceChart`, `AllocationDonut`.
4. Sección historial siempre independiente cuando la identidad está lista:
   - carga inicial, error con `retryTransactions`, vacío o `TransactionsTable`.

Si el portfolio está vacío pero existen operaciones, el historial sigue visible. Si una recarga falla con datos anteriores, se muestra que no se pudieron actualizar y no se etiquetan como actuales.

## 9. Contrato de tests

- `portfolioApi.test.js`: paths, id codificado, page 0, size 20, query, signal y payload intacto.
- `usePortfolio.test.jsx`: `/me` antes del fan-out, estados/retries independientes, paginado humano/base cero, abort en unmount/reemplazo, respuesta tardía y corrección fuera de rango.
- `PositionsTable.test.jsx`: gain/loss/neutral por clase + signo + etiqueta, 4 decimales, links y nombres repetidos.
- `PerformanceChart.test.jsx`: caption/rol, leyenda y tabla alternativa exacta en orden.
- `AllocationDonut.test.jsx`: tabla alternativa desplegable, 60/40, una posición 100%, total cero sin gráfico ni porcentaje inválido y suma presentada dentro de 0,1 puntos porcentuales de 100%.
- `TransactionsTable.test.jsx`: campos, links, límites, disabled durante carga y callbacks de página.
- `PortfolioPage.test.jsx`: carga sin ceros, texto vacío exacto, historial conservado con portfolio vacío, errores y reintentos parciales.
- `routes.test.jsx`: `/portfolio` protegida, render de página e ítem activo en navegación principal y móvil.
