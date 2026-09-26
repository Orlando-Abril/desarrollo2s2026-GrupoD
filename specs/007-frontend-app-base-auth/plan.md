# Implementation Plan: Base frontend y acceso a LaFigu

**Branch**: `007-frontend-app-base-auth` | **Date**: 2026-09-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/007-frontend-app-base-auth/spec.md`

## Summary

Reemplazar el boilerplate de Vite por la base reutilizable de LaFigu: SPA React 19 con rutas públicas/protegidas, sesión exclusivamente en memoria, cliente HTTP único basado en `fetch`, pantallas de ingreso y registro, placeholder protegido de álbum y componentes visuales compartidos. La implementación queda limitada al stack cerrado, consume el contrato de auth de la feature 002 y adopta literalmente el sistema visual de álbum impreso definido abajo.

## Technical Context

**Language/Version**: JavaScript ES modules sobre Node.js 22 en CI; React 19.2.x y Vite 8.3.x existentes

**Primary Dependencies**: React 19, React DOM, Vite, `react-router-dom`; sin librerías HTTP ni kits de UI

**Storage**: Ninguno; sesión y estados de presentación sólo en memoria de la carga actual

**Testing**: Vitest (`vitest run`), `@testing-library/react`, su peer directo `@testing-library/dom`, `jsdom` y `@vitest/coverage-v8` para generar el LCOV importado por SonarCloud

**Target Platform**: Navegadores web modernos, responsive desde 360 px; desarrollo obligatorio en `http://localhost:5173`

**Project Type**: Aplicación web SPA dentro de `frontend/`

**Performance Goals**: Validación cliente inmediata; bloqueo de doble envío; timeout HTTP exacto de 10 s; toast visible 4 s; navegación sin recarga completa

**Constraints**: URL vía `VITE_API_BASE_URL`; `fetch` nativo; CSS plano; bloque `:root` exacto; sin persistencia de sesión; sin scroll horizontal; breakpoint móvil inclusivo hasta 900 px; accesibilidad por teclado y movimiento reducido

**Scale/Scope**: 3 rutas funcionales, 2 guards, 3 piezas de layout, 10 componentes base, 2 endpoints públicos consumidos y 1 placeholder protegido

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Gate constitucional | Evaluación pre-diseño | Evaluación post-diseño |
|---|---|---|
| Frontend independiente y responsive | PASS: la feature vive en `frontend/`, cubre 360 px a escritorio y no modifica backend | PASS: estructura, contratos UI y quickstart preservan este límite |
| Comunicación backend sólo por HTTP/REST y contrato documentado | PASS: sólo consume `/auth/register` y `/auth/login` de la feature 002 | PASS: `authApi` delega en un único cliente HTTP conforme al contrato existente |
| Autenticación JWT/API Key | PASS: usa el JWT de login; la `apiKey` de registro se descarta por requerimiento de seguridad de UI | PASS: modelo y contrato impiden exposición o persistencia de `apiKey` |
| CI ejecuta build y tests | PASS condicionado: el workflow unificado ejecutará tests/build del backend y test/lint/build del frontend | PASS por diseño: T046 completa los tests y cobertura frontend en `.github/workflows/ci.yml`, T048 ejecuta los gates locales y T049 exige que el run real termine en `SUCCESS` |
| SonarCloud y límite de issues | PASS condicionado: el análisis existente se amplía desde la raíz sin crear otro proyecto | PASS por diseño: `.github/workflows/ci.yml` ejecuta un único análisis de `backend/` y `frontend/` con el proyecto y `SONAR_TOKEN` existentes; T049 verifica Quality Gate `PASSED` y menos de 10 issues |
| Reglas financieras, auditoría, Redis, batch y endpoints de mercado | N/A: esta feature no realiza operaciones financieras ni modifica backend | N/A: el diseño no amplía alcance hacia catálogo, mercado o persistencia |
| OpenAPI/Swagger backend | N/A: se consume el contrato existente y no se agregan endpoints | N/A: los contratos creados documentan la interfaz frontend, no sustituyen OpenAPI |

**Resultado del gate**: PASS para implementar el alcance funcional de 007 sobre el CI unificado. La feature reutiliza el único proyecto SonarCloud y `SONAR_TOKEN` existentes, analiza conjuntamente backend y frontend, no crea secretos ni proyectos adicionales y exige verificar el Quality Gate real antes del cierre.

## Project Structure

### Documentation (this feature)

```text
specs/007-frontend-app-base-auth/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── http-client-contract.md
│   └── ui-contract.md
└── tasks.md                    # creado posteriormente por $speckit-tasks
```

### Source Code (repository root)

```text
.github/workflows/
└── ci.yml                      # build/tests de backend y frontend + único análisis SonarCloud

sonar-project.properties        # alcance raíz para backend y frontend, mismo proyecto existente

frontend/
├── .env.example
├── .gitignore
├── README.md
├── index.html
├── package.json
├── package-lock.json
├── vite.config.js
└── src/
    ├── main.jsx
    ├── App.jsx
    ├── api/
    │   ├── httpClient.js
    │   ├── httpClient.test.js
    │   └── authApi.js
    ├── session/
    │   └── SessionContext.jsx
    ├── routes/
    │   ├── ProtectedRoute.jsx
    │   ├── PublicOnlyRoute.jsx
    │   └── routes.test.jsx
    ├── layout/
    │   ├── AppLayout.jsx
    │   ├── AppLayout.css
    │   ├── Header.jsx
    │   ├── Header.css
    │   ├── BottomNav.jsx
    │   ├── BottomNav.css
    ├── components/
    │   ├── Button.jsx / Button.css
    │   ├── Field.jsx / Field.css
    │   ├── Banner.jsx / Banner.css
    │   ├── Toast.jsx / Toast.css
    │   ├── Carnet.jsx / Carnet.css
    │   ├── Figurita.jsx / Figurita.css / Figurita.test.jsx
    │   ├── Casillero.jsx / Casillero.css
    │   ├── EstadoPanel.jsx / EstadoPanel.css
    │   └── Sello.jsx / Sello.css
    ├── pages/
    │   ├── LoginPage.jsx / LoginPage.css
    │   ├── RegisterPage.jsx / RegisterPage.css / RegisterPage.test.jsx
    │   ├── AlbumPage.jsx / AlbumPage.css
    │   └── NotFound.jsx
    ├── styles/
    │   ├── tokens.css
    │   └── base.css
    ├── utils/
    │   └── format.js
    └── domain/
        └── catalogo.js
```

Se eliminan `src/App.css`, `src/index.css`, `src/assets/` y los assets de ejemplo del boilerplate. Los tests se colocan junto al módulo probado para no crear una arquitectura paralela. La única constante de navegación se exporta desde `AppLayout.jsx` y es consumida por Header y BottomNav. `public/favicon.svg` y `public/icons.svg` sólo se conservan si dejan de ser referencias del starter; de lo contrario se eliminan como assets de ejemplo.

**Structure Decision**: Mantener una única SPA en `frontend/`, separada por responsabilidades técnicas estables (`api`, `session`, `routes`, `layout`, `components`, `pages`, `styles`, `utils`, `domain`). Las features futuras extienden esas carpetas y habilitan la constante de navegación, sin crear otra base visual o de transporte.

## Design Decisions

### Providers, sesión y ruteo

- `main.jsx` monta `BrowserRouter`; `App.jsx` compone `SessionProvider`, layout y árbol de rutas.
- `SessionProvider` mantiene `session = null | {token, tokenType, username}` en estado React y expone `login`, `logout`, `isAuthenticated`; nunca consulta ni escribe almacenamiento del navegador.
- El provider registra en el cliente HTTP un accessor del token vigente y un callback de 401 protegido. Ese callback hace logout idempotente y navega con `replace` a `/ingresar`, transportando sólo el indicador informativo de sesión vencida.
- `ProtectedRoute` y `PublicOnlyRoute` son elementos de ruta con `Outlet`/`Navigate`. La raíz decide entre `/album` y `/ingresar`; `*` dirige a `/`.
- Toast, banners, username precargado y mensajes de navegación son estado efímero de UI, no campos de sesión. Nunca se transportan password, token o `apiKey` en navigation state.

### HTTP y auth

- `httpClient.js` es el único módulo que invoca `fetch`. Normaliza base URL y path, serializa JSON, agrega `Authorization: Bearer <token>` cuando existe sesión y aplica `AbortController` con 10.000 ms y limpieza del timer en `finally`.
- Cada request declara explícitamente si es pública. Un 401 sólo invoca `onUnauthorized` cuando la request no es pública; luego igualmente lanza `ApiError`.
- `ApiError` conserva `status`, `code` y `message` para cuerpos válidos `{error,message}`. `NetworkError` representa red, CORS y timeout. Cuerpos inválidos, éxitos JSON inválidos, 5xx y respuestas inesperadas se traducen a un error seguro, sin filtrar contenido interno.
- `authApi.js` expone sólo `register(data)` y `login(data)`. Registro normaliza la respuesta excluyendo `apiKey`; la página usa las credenciales ya presentes en el formulario para el login automático.

### Testing y dependencias

- Dependencia runtime nueva: `react-router-dom`, exigida por el alcance.
- Dependencias dev nuevas: `vitest`, `jsdom`, `@testing-library/react`, `@testing-library/dom` y `@vitest/coverage-v8`. `@testing-library/dom` se declara porque es peer dependency directa documentada de React Testing Library; el provider de coverage es la única excepción justificada para producir `frontend/coverage/lcov.info` consumido por el análisis SonarCloud unificado.
- No se agrega `@testing-library/user-event`: `fireEvent`, `render` y queries accesibles cubren la matriz requerida sin ampliar dependencias. Tampoco Jest DOM, MSW, Playwright, axios ni librerías CSS.
- `vite.config.js` conserva React y agrega `test.environment = 'jsdom'` y `test.coverage = { provider: 'v8', reporter: ['text', 'lcov'], reportsDirectory: 'coverage' }`; así `npm test -- --coverage` produce exactamente `frontend/coverage/lcov.info`. Los tests importan APIs de Vitest explícitamente.
- `package.json` agrega exactamente `"test": "vitest run"`. CI ejecuta `npm test -- --coverage` después de `npm ci` y antes de lint, build y el análisis SonarCloud.

### Configuración y documentación

- `.env.example` contiene `VITE_API_BASE_URL=http://localhost:8080`.
- `.gitignore` excluye `.env` de forma explícita; `.env.example` permanece versionable.
- No se cambia el puerto por defecto 5173 porque el backend autoriza CORS sólo desde `http://localhost:5173`.
- `index.html` queda en español, titulado `LaFigu`, sin referencias a assets del starter y con preconnect/carga de Anton, Archivo 400/600/800 y Space Mono 400/700 desde Google Fonts.
- `README.md` documenta instalación, variable, ejecución, tests, lint, build, puerto y pérdida deliberada de sesión al recargar.
- El email de registro usa `type="email"` y falla en cliente sólo si está vacío o `validity.typeMismatch` es `true`; no se agrega regex propia. Casos válidos contractuales: `abril@example.com`, `a+b@example.com`. Casos inválidos: `abril`, `abril@`, `@example.com`, `abril example.com`. El backend conserva autoridad final.

### CI/Sonar unificado en esta feature

- `.github/workflows/ci.yml` es el único workflow: conserva el build/test Maven del backend, incorpora test/lint/build del frontend y elimina el workflow frontend redundante.
- El workflow se ejecuta en todo push a `main` y pull request hacia `main`, sin filtros por directorio, aunque cambie sólo `frontend/**` o sólo `backend/**`.
- `sonar-project.properties` mantiene el project key y organización existentes, analiza ambos directorios desde la raíz, consume JaCoCo y LCOV, espera el Quality Gate y reutiliza exclusivamente `SONAR_TOKEN`.
- No se crea un proyecto SonarCloud separado para frontend ni se agregan project keys, tokens o secretos nuevos.

## SISTEMA VISUAL

Esta sección es normativa. Sus valores se copian tal cual. `tokens.css` contiene solamente el bloque del punto 1. Fuera de él, sólo se permiten como excepciones los literales de color escritos explícitamente en estos puntos (`#fff`, `#fbe4df`, `#1b1a17` y los `rgba(...)` prescritos). Los gradientes de trama, rayado y foil aquí indicados son las únicas excepciones a la prohibición general de degradés.

### 1) `styles/tokens.css` (copiar EXACTO)

```css
:root{
  --papel:#f3ecdc; --papel-2:#e8dec6; --blanco:#fffdf7; --tinta:#1b1a17; --tinta-2:#4a4740;
  --rojo:#e2412f; --verde:#12805c; --azul:#1f4fd1; --amarillo:#f4c21b; --violeta:#6b3fa0;
  --liga-premier:var(--violeta); --liga-laliga:var(--rojo); --liga-seriea:var(--azul);
  --liga-bundesliga:var(--tinta-2); --liga-ligue1:var(--verde);
  --borde:2px solid var(--tinta); --borde-grueso:3px solid var(--tinta);
  --sombra-sm:4px 4px 0 var(--tinta); --sombra-md:5px 5px 0 var(--tinta); --sombra-lg:6px 6px 0 var(--tinta);
  --font-display:'Anton',Impact,sans-serif; --font-texto:'Archivo',system-ui,sans-serif; --font-mono:'Space Mono',ui-monospace,monospace;
  --esp-1:4px; --esp-2:8px; --esp-3:12px; --esp-4:16px; --esp-5:24px; --esp-6:32px;
  --ancho-max:1200px;
}
```

### 2) Reglas globales

- Nunca `border-radius`, nunca degradés de fondo fuera de las excepciones prescritas, nunca `box-shadow` con desenfoque. Todas las sombras son `Xpx Ypx 0 color`.
- `body`: fondo `var(--papel)` + `background-image: radial-gradient(rgba(27,26,23,.07) 1px, transparent 1.2px)`, `background-size: 6px 6px`, color `var(--tinta)` y fuente `var(--font-texto)`.
- Títulos de página (`h1`): `var(--font-display)`, weight 400, MAYÚSCULAS, `font-size: clamp(34px, 6vw, 54px)`, `line-height: .95`. Debajo, subtítulo `var(--font-mono)` de 12px y `var(--tinta-2)`.
- Etiquetas, números y datos: `var(--font-mono)`.
- `:focus-visible`: `outline: 3px solid var(--amarillo)` y offset 2px.
- Contenido: `max-width: var(--ancho-max)`, centrado, `padding: 28px 20px 100px`.
- Colores con significado: rojo = acción principal/error; verde = éxito; amarillo = foco/destacado/info.

### 3) Header

- Sticky arriba: fondo `var(--tinta)`, texto `var(--papel)`, `border-bottom: 4px solid var(--rojo)`, alto 64px (56px móvil), contenido centrado a `--ancho-max`.
- Logo: rectángulo 34x42px, amarillo, borde 2px papel, `10` en display 22px tinta, rotación -6° y sombra `3px 3px 0 var(--rojo)`. Al lado, `LAFIGU` display 26px; debajo, `MERCADO DE TOKENS` mono 9px, letter-spacing 1.5px, opacidad .7; subtítulo oculto en móvil.
- Nav autenticada: número en cajita mono 10px con borde 1.5px, texto 14px/800 uppercase. Inactivo opacidad .7; activo papel con borde inferior 4px amarillo; deshabilitado opacidad .35, cursor not-allowed y `PRONTO` mono 9px sobre papel-2.
- Derecha autenticada: chip con borde 1.5px papel semitransparente; avatar cuadrado 28px azul, borde papel e inicial display; username 13px/700. `SALIR` con borde 1.5px papel, transparente, uppercase 12px/800; hover papel/tinta.
- Sin sesión: logo y botón `CREAR CUENTA`, o `INGRESAR` en `/registro`, con el estilo de `SALIR`.
- Hasta 900px: se oculta nav superior y aparece barra inferior fija con cuatro ítems; fondo tinta, borde superior 4px rojo, 10px/800 uppercase con número arriba; activo amarillo y deshabilitados opacidad .3.

### 4) Botones

- Primario: ancho 100%, display 20px uppercase, fondo tinta, texto papel, sin borde, padding 12px 18px, sombra `4px 4px 0 var(--rojo)`. `:active` desplaza 2px y deja sombra 2px. Deshabilitado opacidad .6 y cursor progress.
- Secundario: ancho automático, fondo amarillo, texto tinta y `var(--sombra-sm)`.

### 5) Campos (`Field`)

- Label mono 11px bold uppercase.
- Input texto 600/16px, padding 10px, `var(--borde)`, fondo papel, sin radius.
- Error: `aria-invalid`, borde rojo, fondo `#fbe4df`, mensaje mono 11px bold rojo. Hint mono 11px tinta-2.

### 6) Banner

- Borde `var(--borde)`, padding 10px 12px, mono 12px bold.
- Error: fondo rojo, texto blanco, comienza con `✕`.
- Info: fondo amarillo, texto tinta.

### 7) Carnet

- Fondo blanco, borde grueso, sombra lg; 440px escritorio y 100% móvil.
- Cabecera: padding 14px 16px, borde inferior grueso, texto blanco. Título `h1` display 28px uppercase a la izquierda; etiqueta mono 11px con borde 2px blanco a la derecha.
- Ingresar: azul, título `INGRESAR`, etiqueta `SOCIO`.
- Registro: rojo, título `CARNET DE COLECCIONISTA` en dos líneas, etiqueta `NUEVO`.
- Cuerpo: formulario con padding 18px y gap 14px.
- Pie: borde superior 2px dashed tinta, padding 12px 18px, texto 14px y link 800.
- Auth: grilla `1fr 440px`, gap 48px, centrada verticalmente; decoración izquierda oculta en ≤900px.

### 8) Figurita

- Props exactas `{id, fullName, team, league, positions, nationality, age, marketValue}`, proyección de `PlayerResponse` de `specs/004-simplificar-catalogo-jugadores/contracts/players-api.yaml`.
- Contenedor: fondo `#fff`, padding 8px, borde y sombra sm.
- Foto: relación 1/1.05, borde, overflow hidden; fondo de liga + `repeating-linear-gradient(135deg, rgba(255,255,255,.13) 0 9px, transparent 9px 18px)`.
- Silueta SVG inline decorativa: círculo y hombros en `#1b1a17`, centrada abajo, ancho 72%; sin fotos, escudos ni logos.
- Superior izquierda: `N°` 14px + id con `padStart(3,'0')` 30px, display blanco y stroke tinta 1.5px. Superior derecha: abreviatura de primera posición según orden fijo, etiqueta amarilla borde 2px, mono 10px bold, rotada 6°.
- Debajo: nombre display 20px uppercase, una línea elíptica y `title`; fila mono 10px tinta-2 con equipo elíptico y liga; fila con borde superior 2px dashed con créditos display 19px y `nacionalidad · edad` mono 10px (`null` → `—`).
- Hover sólo dentro de grilla: `translate(-2px,-2px) rotate(-1deg)`, sombra 6px; foil `::after` con `linear-gradient(115deg, transparent 30%, rgba(255,255,255,.55) 45%, rgba(255,240,150,.4) 50%, transparent 62%)` y `mix-blend-mode: screen`. Ambos se desactivan con movimiento reducido.
- Semántica: `<article aria-label="{fullName}, {team}">`; no clickeable.

### 9) Casillero

- Fondo papel-2, borde 2px dashed tinta, relación 1/1.55, contenido centrado; marca display 44px opacidad .3 y texto mono 11px.
- Variante `cargando`: opacidad 1 → .5 → 1, 1.2 s infinita; desactivada con movimiento reducido.

### 10) EstadoPanel y Sello

- EstadoPanel: máximo 520px centrado, fondo blanco, borde grueso, sombra lg, padding 28px 24px, centrado. Sello, `h2` display 30px uppercase, párrafo 15px y botón secundario opcional.
- Sello: borde 3px double, mono 12px bold uppercase, padding 6px 10px, rotado -3°, fondo blanco. Variantes rojo error y gris tinta-2 neutro.

### 11) Toast

- Fijo abajo a la derecha; móvil: ancho completo menos 16px por lado y sobre barra inferior.
- Fondo amarillo, borde grueso, sombra md, padding 12px 16px, texto 800, `role="status"`; desaparece a los 4 s.

### 12) Placeholder `/album`

- `h1` `El álbum` y un `Casillero` con `···` y `Las figuritas llegan en la próxima feature`.

## Delivery Gates

- Ningún `fetch` fuera de `api/httpClient.js`; ninguna llamada HTTP dentro de componentes.
- Ningún color suelto fuera de las excepciones literales autorizadas por `SISTEMA VISUAL`.
- Sin `console.log`, secretos, código comentado, estilos o vistas fuera de alcance.
- `npm test`, `npm run lint` y `npm run build` finalizan sin errores.
- El run real de `.github/workflows/ci.yml` finaliza en `SUCCESS` con test/build del backend, test/lint/build del frontend y un único análisis SonarCloud de ambos directorios; el Quality Gate queda `PASSED` y mantiene menos de 10 issues.
- Matriz mínima: errores HTTP 400/401/409/5xx, red, timeout, Bearer y callback; registro y validación; auto-login y navegación; ausencia de `apiKey`; cero escrituras en local/session storage; guards; nulabilidad de Figurita.

## Complexity Tracking

No aplica: no existen violaciones constitucionales ni dependencias fuera del stack cerrado.
