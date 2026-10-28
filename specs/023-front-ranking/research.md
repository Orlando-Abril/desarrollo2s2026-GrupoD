# Research: Pantalla de Ranking

## R1. Contrato real del ranking

**Decision**: Consumir `GET /players/ranking` con parámetros opcionales `strategy`, `league`, `position`, `page` y `size`. Tratar la respuesta como `{ items, page, size, totalElements, totalPages }`, no como una página Spring con `content`.

**Rationale**: Los DTOs, controller tests y OpenAPI de la feature 018 usan `items`; `page` es 0-based, el default es 0, `size` default 20 y rango 1..100. Cada item contiene `rank`, `player`, `value`, `percentageChange`, `strategy` y `version`.

**Alternatives considered**: Inferir `content` por convención Spring; descartado porque contradice el DTO real. Transformar la respuesta dentro del API client; descartado porque el patrón de `playersApi` devuelve el contrato sin cambios.

## R2. Estrategia activa configurable

**Decision**: Representar la selección inicial como `strategy: ''` con etiqueta “Activa” y omitir el query param. Ofrecer además OFFICIAL y MOMENTUM como selecciones explícitas. Inferir el código efectivo de `items[0].strategy` sólo para complementar la presentación cuando hay resultados.

**Rationale**: El backend resuelve la estrategia omitida desde configuración. OFFICIAL es el default técnico, pero puede cambiarse a MOMENTUM. La respuesta de página no incluye estrategia efectiva a nivel superior; en una página vacía no es posible conocerla sin ampliar el backend.

**Alternatives considered**: Inicializar OFFICIAL, descartado porque ignora una configuración activa MOMENTUM. Cambiar el backend para exponer la estrategia, descartado por alcance y autorización. Mutar el filtro al código del primer item, descartado porque dispararía una segunda consulta y convertiría una elección por defecto en explícita.

## R3. Construcción de query string

**Decision**: Usar `URLSearchParams`, omitir filtros de texto vacíos e incluir `page`/`size` cuando no sean `undefined`, incluyendo correctamente `page=0`.

**Rationale**: Replica `playersApi`, garantiza encoding y evita `league=`/`position=`, que el backend responde con 400. Un chequeo truthy perdería la primera página.

**Alternatives considered**: Interpolación manual, descartada por encoding y separadores. Enviar siempre strings vacíos, descartado por validación backend.

## R4. Estado remoto y cancelación

**Decision**: Seguir el patrón de `useAlbum`: efecto con `AbortController`, estados loading/ready/error, guardas de señal en `then` y `catch`, cleanup que aborta, y retry mediante clave incremental.

**Rationale**: Cambios rápidos de filtros/página deben cancelar el pedido anterior. `httpClient.request` convierte errores de fetch, timeout y abort a `NetworkError`; por eso el hook no puede identificar un abort por tipo y debe comprobar `controller.signal.aborted` antes de publicar cualquier resultado.

**Alternatives considered**: Comparar IDs de request sin abortar, descartado porque deja trabajo de red innecesario. Mostrar todo `NetworkError`, descartado porque un cambio normal de filtros produciría un falso error.

## R5. Orden y paginación

**Decision**: Preservar `items` y `rank` exactamente como llegan. Mantener página humana 1-based en hook/paginador y traducir a `page - 1` sólo al construir el pedido. Reutilizar `Pager` y `buildPageWindow`.

**Rationale**: El backend ordena por valor descendente e ID ascendente y calcula el puesto sobre el conjunto filtrado antes de paginar. Reordenar o recalcular en frontend rompería posiciones globales. El paginador existente ya brinda semántica accesible 1-based.

**Alternatives considered**: Ordenar por `value` localmente, descartado porque altera desempates y puede ocultar errores de contrato. Implementar otro paginador, descartado por duplicación.

## R6. Reutilización de filtros

**Decision**: Crear `RankingFilters` con CSS propio alineado al patrón de Álbum y reutilizar `Field`, catálogos y tokens. No modificar ni renderizar `AlbumFilters`.

**Rationale**: `AlbumFilters` siempre muestra liga, posición, equipo, búsqueda y selector cartas/lista. No tiene props para ocultar esos controles; pasar callbacks ficticios dañaría UX y accesibilidad. Un componente específico mantiene una API mínima y respeta el límite de no modificarlo.

**Alternatives considered**: Generalizar `AlbumFilters`, descartado por autorización. Reutilizarlo con opciones vacías, descartado porque seguiría mostrando controles ajenos. Compartir sus class names directamente, descartado por acoplamiento semántico entre features; se comparten tokens y decisiones visuales, no nombres internos.

## R7. Tabla responsive y accesible

**Decision**: Usar tabla semántica con `th scope="col"`, wrapper de overflow contenido, texto que puede envolver y filas no interactivas. Mostrar color de liga junto a su etiqueta.

**Rationale**: El patrón de `AlbumTable` valida la tabla con overflow local. Mantener semántica nativa satisface encabezados y lectores de pantalla; texto más color evita depender de color. Sin Link/onClick/tabIndex se comunica correctamente que 024 aún no existe.

**Alternatives considered**: Convertir filas en tarjetas con CSS en mobile, descartado por complejidad semántica adicional. Hacer filas clickeables anticipadamente, descartado porque no hay destino autorizado.

## R8. Variación reutilizable

**Decision**: Crear `VariationBadge` autocontenido con CSS propio, cuatro estados y formateador porcentual local.

**Rationale**: Null significa “Sin dato” y 0 significa “Sin cambios”; ambos deben diferenciarse. Colores `--verde`, `--rojo`, `--tinta-2`, signos/iconos y etiquetas accesibles cubren percepción visual y asistiva. La ubicación en `features/ranking` permite importarlo desde 024 sin tocar utilidades existentes.

**Alternatives considered**: Reutilizar `Sello`, descartado porque su tratamiento rotado y variantes no representan tendencia. Agregar formato a `utils/format.js`, descartado por el límite de archivos existentes y porque el componente puede encapsularlo.

## R9. Pruebas y calidad

**Decision**: Separar contrato API, hook y página; ampliar `routes.test.jsx`; ejecutar test con cobertura, lint y build.

**Rationale**: La separación localiza fallos de serialización, concurrencia y presentación. El workflow actual ya ejecuta `npm test -- --coverage`, `npm run lint` y `npm run build`; SonarCloud consume `frontend/coverage/lcov.info`.

**Alternatives considered**: Cubrir todo sólo desde `RankingPage`, descartado porque hace difícil probar abortos y query strings con precisión. Agregar dependencias de testing, descartado porque `renderHook`, jsdom y mocks necesarios ya están disponibles.

## Resolved Unknowns

No quedan cuestiones abiertas. La única limitación contractual —no conocer el código de estrategia activa con una página vacía— queda resuelta con la opción estable “Activa” sin inventar un valor.
