# Quickstart: Validación de Base frontend y acceso

## Prerequisites

- Node.js 22 y npm.
- Backend disponible en `http://localhost:8080` con el contrato de `specs/002-auth-usuario/contracts/auth-api.md`.
- Puerto 5173 libre. El backend sólo permite CORS desde `http://localhost:5173`; no iniciar Vite en otro puerto.

## Setup

Desde `frontend/`:

```powershell
Copy-Item .env.example .env
npm ci
```

`.env` debe contener:

```dotenv
VITE_API_BASE_URL=http://localhost:8080
```

Confirmar que `.env` está ignorado y `.env.example` permanece versionable.

## Automated gates

```powershell
npm test
npm run lint
npm run build
```

Expected:

- Vitest completa la matriz mínima sin fallos.
- Oxlint no reporta errores.
- Vite produce el build sin errores.
- `package.json` ejecuta tests mediante `vitest run`.
- El workflow unificado `.github/workflows/ci.yml` ejecuta cobertura/tests antes de lint, build y el único análisis SonarCloud.

## Run locally

```powershell
npm run dev
```

Abrir `http://localhost:5173`. Verificar `<html lang="es">`, título `LaFigu` y que no quedan logos, contador, assets ni estilos del starter.

## Scenario 1: routing without session

1. Abrir `/`, `/album` y una ruta inexistente en ventanas nuevas.
2. Confirmar destino final `/ingresar` en los tres casos.
3. Abrir `/registro` y usar `Ingresar` del header para volver.
4. Confirmar que no existen rutas para Ranking, Mercado o Mi portfolio.

## Scenario 2: login and logout

1. En `/ingresar`, enviar credenciales válidas.
2. Confirmar `Abriendo…`, botón deshabilitado y un solo request.
3. Confirmar `/album`, avatar/username y placeholder exacto.
4. Pulsar `Salir`: debe volver a `/ingresar`.
5. Ingresar otra vez y recargar: debe perder sesión y volver a `/ingresar`.

## Scenario 3: registration

1. Enviar vacío: errores visibles y foco en Usuario.
2. Escribir cada campo: su error individual desaparece.
3. Confirmar que `abril@example.com` y `a+b@example.com` superan la validación cliente; que `abril`, `abril@`, `@example.com` y `abril example.com` son rechazados antes del request; y que passwords de 7/8 caracteres fallan/pasan respectivamente.
4. Registrar datos válidos: debe llamar register y luego login, navegar a `/album` y mostrar 4 s el toast exacto.
5. Inspeccionar DOM, consola, storage y requests: `apiKey` no debe aparecer ni conservarse.
6. Simular fallo del auto-login después de 201: debe ir a `/ingresar`, precargar sólo username y mostrar el banner info exacto.

## Scenario 4: HTTP errors

Validar los casos definidos en [http-client-contract.md](./contracts/http-client-contract.md):

- 401 login → credenciales incorrectas, sin callback de sesión vencida.
- 409 register → duplicado.
- 400 → message backend.
- red/CORS/timeout a 10 s → conexión.
- 5xx/body inesperado → error genérico.
- 401 protegido → logout, redirect e info de sesión vencida.

## Scenario 5: privacy

Durante login, registro, auto-login y logout:

- `localStorage.setItem` y `sessionStorage.setItem` nunca son llamados.
- No aparecen token, password ni `apiKey` en DOM, URL, navigation state o logs.
- Cookies e IndexedDB no contienen sesión.

## Scenario 6: visual and responsive

Validar a 360 px, 900 px, 901 px y escritorio:

- Sin scroll horizontal.
- ≤900 px: barra inferior fija, username/decoración ocultos, toast sobre la barra.
- ≥901 px: nav superior, username y decoración visibles.
- Estilo coincide con `SISTEMA VISUAL`: sin radios, sombras borrosas, modo oscuro ni gradientes adicionales.
- Sólo se usan los literales de color explícitamente autorizados.
- Las tres figuritas auth usan nombres inventados y rotaciones -9°, 3°, 11°.

## Scenario 7: accessibility

- Recorrer toda la experiencia sólo con teclado.
- Confirmar foco amarillo, labels asociados y foco al primer error.
- Confirmar `role="alert"` en errores y `role="status"` en toast.
- Confirmar que `PRONTO` no es activable ni enlazable.
- Activar `prefers-reduced-motion: reduce`: no debe haber pulso, elevación ni foil en movimiento.

## Scenario 8: reusable components

- Renderizar `Figurita` con `nationality=null` y `age=null`: muestra `—`, conserva `<article>` y aria-label.
- Confirmar formato `es-AR` con dos decimales y sufijo ` cr`.
- Confirmar que Header y BottomNav leen la misma constante.
- Confirmar que ningún componente realiza fetch.

## Unified CI and SonarCloud gate

1. Abrir o actualizar un pull request hacia `main`; `.github/workflows/ci.yml` no tiene filtros por directorio y debe dispararse aunque el cambio afecte únicamente `frontend/**`.
2. Confirmar que el único job ejecuta `backend/` con Maven verify y `frontend/` con `npm test -- --coverage`, `npm run lint` y `npm run build`.
3. Confirmar que se publica exactamente un análisis en el proyecto SonarCloud existente `Orlando-Abril_desarrollo2s2026-GrupoD`, usando el `SONAR_TOKEN` existente y sin secretos `SONAR_FRONTEND_*`.
4. Revisar el alcance del análisis y confirmar que contiene archivos de `backend/` y `frontend/`, cobertura JaCoCo y cobertura LCOV del frontend.
5. Confirmar que el workflow termina en `SUCCESS`, el Quality Gate queda `PASSED` y el proyecto mantiene menos de 10 issues.
6. La implementación de 007 no está terminada si falla cualquier test, lint, build, el análisis conjunto o el Quality Gate.

## References

- [Specification](./spec.md)
- [Implementation plan](./plan.md)
- [Data model](./data-model.md)
- [UI contract](./contracts/ui-contract.md)
- [HTTP client contract](./contracts/http-client-contract.md)
