# Research: El álbum de jugadores

No quedan dudas pendientes. Las decisiones siguientes resuelven integración, estado, cancelación, reutilización y pruebas dentro del stack cerrado.

## 1. Inventario cerrado y excepciones de archivos

**Decision**: Crear sólo los archivos productivos enumerados en la solicitud. Agregar como únicas excepciones `src/api/playersApi.test.js`, `src/features/album/albumUtils.test.js` y `src/pages/AlbumPage.test.jsx`. Extender archivos existentes cuando falte una capacidad: `httpClient.js/.test.js`, `Field.jsx/.css`, `Button.css`, `AlbumPage.css` y `utils/format.js`.

**Rationale**: La solicitud exige explícitamente pruebas de tres niveles y el patrón vigente coloca los tests junto al módulo. Es imposible satisfacer la matriz sin esos archivos de prueba. Las demás necesidades ya tienen una abstracción propia y deben resolverse ampliándola, no creando un duplicado.

**Alternatives considered**: Concentrar todas las pruebas en un test existente (mezcla responsabilidades y no cubre módulos de forma aislada); crear `Select`, `TextButton`, otro cliente HTTP o utilidades paralelas (prohibido); omitir pruebas (incumple criterios de terminado).

## 2. Señal externa y timeout único

**Decision**: Extender `httpClient.request` con `signal` opcional. El cliente conserva su controller de timeout y enlaza la señal externa al controller interno mediante un listener removido en `finally`. `useAlbum` crea un controller por consulta, aborta en cleanup y suprime el rechazo sólo si la señal de esa ejecución está abortada.

**Rationale**: Así `fetch` sigue existiendo en un solo archivo, el timeout continúa siendo responsabilidad transversal del cliente y el hook puede cancelar requests superadas sin confundirlas con red o timeout.

**Alternatives considered**: Hacer `fetch` en `playersApi` (viola el gate de transporte); usar sólo la señal externa y perder timeout (regresión); incorporar una librería o depender de `AbortSignal.any` (innecesario y menos compatible); convertir todos los aborts en silencio dentro del cliente (ocultaría timeouts).

## 3. Un efecto remoto y datos derivados

**Decision**: `useAlbum` usa un único `useEffect` para `league`, `position`, `team` y reintento. Búsqueda, orden, paginación, ventana y opciones de equipo se obtienen con funciones puras y `useMemo`; no se guardan copias derivadas en estado.

**Rationale**: Evita cascadas de efectos, estados desincronizados y renders con página o filtros anteriores. Separa claramente efectos de IO de transformaciones deterministas.

**Alternatives considered**: Un efecto por filtro y otro por resultados (propenso a carreras); almacenar lista filtrada/ordenada/paginada (estado redundante); mover lógica al componente de página (dificulta pruebas y viola la separación pedida).

## 4. Fuente estable para opciones de equipo

**Decision**: Mantener `responsePlayers` para resultados actuales y `teamSourcePlayers` para la última respuesta exitosa cuya consulta omitió `team`. Las opciones se derivan de `teamSourcePlayers`; seleccionar equipo no reemplaza esa fuente. Liga o posición limpian equipo antes de consultar.

**Rationale**: El backend filtra por equipo, por lo que derivar opciones de la respuesta ya filtrada dejaría una única opción y rompería el selector. La fuente separada implementa literalmente “última respuesta obtenida sin filtro de equipo” sin una consulta adicional al seleccionar equipo.

**Alternatives considered**: Derivar del resultado actual (pierde opciones); descargar siempre el catálogo completo además de cada consulta (requests duplicadas); filtrar equipo sólo en cliente (contradice el contrato que exige enviarlo al backend).

## 5. Ventana determinista del paginador

**Decision**: Construir una secuencia desde el conjunto `{1, current-1, current, current+1, last}`, limitado a páginas válidas, ordenado y sin duplicados; insertar un token de elipsis en huecos mayores a uno. Cero resultados produce ventana vacía.

**Rationale**: Cumple primera, última y actual ±1 con el mínimo de casos especiales; nunca repite números y es directamente testeable en límites.

**Alternatives considered**: Lista completa de páginas (no escala ni cumple el diseño); ramas manuales por cada posición (más errores); librería de paginación (dependencia prohibida).

## 6. Extensión de componentes y formatos existentes

**Decision**: Ampliar `Field` para renderizar input o select bajo el mismo label/ayuda/error; sumar variante CSS `text` a `Button`; agregar un formateador entero es-AR a `format.js`. Mantener `Figurita`, `Casillero`, `EstadoPanel`, `Sello`, `LEAGUES` y `POSITIONS` como fuentes únicas.

**Rationale**: Son capacidades del mismo concepto ya abstraído. Extenderlas evita divergencia visual, strings duplicados y formateadores dispersos.

**Alternatives considered**: Nuevos componentes `SelectField`/`LinkButton` (paralelos prohibidos); selects y números estilizados ad hoc en la página (duplica reglas); repetir labels/colores/abreviaturas (deriva de dominio).

## 7. Estrategia de pruebas sin dependencias nuevas

**Decision**: Mockear `fetch` con Vitest para `playersApi` y flujos de `AlbumPage`; probar `albumUtils` como unidad pura. Ampliar el test existente de `httpClient` para señal externa. Renderizar `AlbumPage` dentro de router y sesión reales o configurados para validar 401, navegación y cancelación.

**Rationale**: Las herramientas instaladas cubren requests, DOM, rutas y accesibilidad. La separación propuesta permite pruebas rápidas de bordes y pocas integraciones representativas.

**Alternatives considered**: MSW, user-event o navegador E2E (dependencias nuevas); mockear `useAlbum` en todos los tests de página (no demuestra cancelación ni 401); probar sólo snapshots (no verifica comportamiento).

## 8. Propiedad del scroll y del modo de vista

**Decision**: El hook expone `setPage` y mantiene el modo efímero, pero no toca DOM. `AlbumPage` posee la referencia al inicio de resultados y envuelve el cambio de página para desplazarla. El cambio de vista no cambia página ni resultados.

**Rationale**: Respeta que `useAlbum` no conozca DOM y mantiene la coordinación de accesibilidad/presentación donde existe el elemento real.

**Alternatives considered**: `document.querySelector` o ref dentro del hook (viola separación); scroll dentro de `Pager` (el presentador conocería layout externo); efecto global de página (efecto adicional innecesario).
