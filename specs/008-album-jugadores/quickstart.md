# Quickstart: Validación de El álbum de jugadores

## Prerequisites

- Node.js 22 y npm.
- Backend en `http://localhost:8080` con Features 004 y 006 implementadas.
- Catálogo con datos para escenarios normales y posibilidad de probar catálogo vacío/error.
- Puerto frontend 5173 libre.
- `.env` en `frontend/` con `VITE_API_BASE_URL=http://localhost:8080`.

## Automated gates

Desde `frontend/`:

```powershell
npm test
npm run lint
npm run build
```

Expected:

- Vitest cubre `playersApi`, `albumUtils`, `AlbumPage` y la señal externa de `httpClient` sin fallos.
- La prueba de `AlbumPage` con aproximadamente 2.600 jugadores mide menos de 2 s desde la resolución del `fetch` mockeado hasta que el primer tramo queda disponible.
- Oxlint no reporta errores.
- Vite produce el build sin errores.
- No cambia `package.json` ni el lockfile por dependencias nuevas.

## Unit and integration matrix

### playersApi

- Sin filtros produce `/players` sin `?` sobrante.
- Omite valores vacíos.
- Combina liga, equipo y posición.
- Un equipo con espacios queda correctamente codificado.
- Reenvía `signal` al cliente compartido.

### albumUtils

- `Ángel` coincide con `angel` y las mayúsculas no alteran el resultado.
- Orden usa comparación española y no muta la entrada.
- Paginación de 0, 24 y 25 elementos devuelve rangos, páginas y segmentos correctos.
- La ventana cubre inicio, centro, final, pocas páginas y cero páginas sin duplicados.
- Equipos quedan únicos y ordenados en español.

### AlbumPage

- Carga muestra exactamente 8 casilleros y ningún paginador.
- `[]` inicial muestra el estado de álbum vacío.
- Filtros/búsqueda sin coincidencias muestran el estado y `Limpiar filtros` restaura defaults.
- Red, timeout, 400 y 5xx muestran error; `Reintentar` repite la última consulta.
- Cambiar liga limpia equipo y vuelve a página 1.
- Toggle conserva resultados/página y actualiza `aria-pressed`.
- 401 dispara cierre de sesión, `/ingresar` y mensaje exacto.
- Una segunda consulta aborta la primera y sólo la segunda puede actualizar UI.

## Run with the real backend

En una terminal, iniciar el backend según el README del repositorio. En otra:

```powershell
cd frontend
npm run dev
```

Abrir `http://localhost:5173`.

## Scenario 1: register → login → album

1. Registrar un usuario nuevo o iniciar sesión con uno existente.
2. Confirmar que la navegación termina en `/album` sin copiar ni enviar API key desde el frontend.
3. En Network, verificar `GET /players` con `Authorization: Bearer <jwt>` y sin `X-API-KEY`.
4. Confirmar título, total inicial es-AR y `5 ligas`.
5. Confirmar orden alfabético y 24 jugadores en la primera página cuando hay al menos 24.

## Scenario 2: filters and search

1. Cambiar liga y posición; verificar query sólo con valores presentes.
2. Elegir equipo; verificar nombre completo en query y combinación AND.
3. Cambiar liga: equipo vuelve a `Todos`, opciones se recalculan y página vuelve a 1.
4. Buscar un nombre usando otras mayúsculas y quitando tildes; debe coincidir sin nuevo request.
5. Limpiar: todos los controles vuelven al default y aparece la primera página completa.
6. Cambiar filtros rápidamente con throttling: requests anteriores aparecen abortadas y nunca pisan el resultado final.

## Scenario 3: views and pagination

1. Confirmar Cartas activa al entrar.
2. Cambiar a Lista y comparar jugadores, orden, rango y valor con Cartas.
3. Confirmar todas las posiciones, `—` en nulos y colores/labels desde el dominio.
4. Recorrer primera, intermedia y última página; comprobar ventana, deshabilitados, rango vivo y scroll al inicio.
5. Confirmar que carta y fila no son clickeables y no existe detalle.

## Scenario 4: exact states

Usar mocks, herramientas de red o datos controlados para validar los estados de [album-ui-contract.md](./contracts/album-ui-contract.md):

- Cargando.
- Catálogo vacío.
- Sin resultados.
- Red/timeout/400/5xx con reintento.
- 401 con redirección y mensaje de sesión vencida.

Los textos y botones deben coincidir literalmente con el contrato.

## Scenario 5: 360 px visual and accessibility

1. Usar viewport 360 px.
2. Confirmar dos cartas por fila, controles de filtro utilizables y sin scroll horizontal global.
3. En Lista, confirmar que sólo el panel de tabla tiene scroll horizontal.
4. Recorrer filtros, selector y paginador sólo con teclado; verificar foco amarillo.
5. Inspeccionar `aria-pressed`, `aria-current`, `aria-live` y `aria-busy`.
6. Activar movimiento reducido y verificar que los casilleros no pulsan.

## Scenario 6: 1280 px visual

1. Usar viewport 1280 px.
2. Comparar con `SISTEMA VISUAL` de `specs/007-frontend-app-base-auth/plan.md`.
3. Confirmar grilla auto-fill con mínimo 180 px, barra blanca con wrap, tabla dentro de panel y sombras sin blur.
4. Revisar CSS: sólo tokens y literales/rgba autorizados; sin radius, dependencias de UI ni estilos paralelos.

## CI gate

1. Con autorización explícita, publicar el cambio según el flujo del equipo; si ya existe un run, usarlo sin volver a publicar.
2. Confirmar que `.github/workflows/ci.yml` ejecuta backend y frontend.
3. Confirmar que `backend/src/test/java/com/example/demo/config/PlayerOpenApiTest.java` pasa y que Swagger publica `GET /players` con `bearerAuth` o `apiKeyAuth` como alternativas.
4. Confirmar que `backend/src/test/java/com/example/demo/service/PlayerCatalogQueryServiceTest.java`, incluido `mapsLocalResultsWithoutCallingAnAdapter`, pasa; con datos persistidos y Football-Data no disponible, `GET /players` debe seguir respondiendo desde datos locales.
5. El run debe terminar `SUCCESS`; tests, lint, build y Quality Gate deben aprobar sin secretos ni proyectos nuevos, y SonarCloud debe mantener menos de 10 issues menores.
6. La feature no está terminada hasta completar estos gates y la verificación real a 360/1280 px.

## References

- [Specification](./spec.md)
- [Implementation plan](./plan.md)
- [Research](./research.md)
- [Data model](./data-model.md)
- [Players client contract](./contracts/players-client-contract.md)
- [Album UI contract](./contracts/album-ui-contract.md)
