# Implementation Plan: Pantalla Mi portfolio

**Branch**: `feature/front-portfolio` | **Date**: 2026-10-30 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/030-front-portfolio/spec.md`

## Summary

Pantalla protegida `/portfolio` que identifica la cuenta actual, carga su portfolio y su historial paginado desde los contratos de la 028, y presenta resumen patrimonial, posiciones enlazadas al jugador, comparación de precio promedio contra cotización, distribución por valor actual e historial de compras y ventas. Portfolio e historial tienen carga, error, vacío y reintento independientes; una falla parcial no elimina la información válida de la otra sección.

Enfoque: un cliente `api/portfolioApi.js`; un único hook `features/portfolio/usePortfolio.js` que resuelve `/auth/me` una vez y coordina dos recursos abortables; componentes `PositionsTable`, `PerformanceChart`, `AllocationDonut` y `TransactionsTable`; y `pages/PortfolioPage.jsx` como composición. Los gráficos reutilizan Recharts ya instalado y siempre exponen una tabla alternativa generada desde los mismos datos. Los tests Vitest cubren color más signo/etiqueta de ganancia y pérdida, vacío, errores parciales, paginado, respuestas tardías y tablas alternativas.

## Technical Context

**Language/Version**: JavaScript ES2023 con JSX; React 19.2.

**Primary Dependencies**: React 19, React Router 7, Recharts 3.10.1 y el `httpClient` propio. Sin dependencias nuevas.

**Storage**: N/A. Estado efímero en memoria; el frontend no persiste datos financieros.

**Testing**: Vitest 5, Testing Library 16 y jsdom; `npm test`, `npm run lint` y `npm run build`.

**Target Platform**: navegadores modernos; diseño utilizable desde 360 px de ancho.

**Project Type**: aplicación web frontend que consume el backend REST existente; no hay cambios de backend.

**Performance Goals**: carga inicial con una solicitud de identidad seguida por portfolio e historial en paralelo; un cambio de página realiza sólo una solicitud de historial; interacción de paginado y render de gráficos sin demora perceptible para una cuenta normal.

**Constraints**: importes financieros presentados con exactamente 4 decimales; no recalcular totales financieros recibidos; página backend base cero y UI base uno; respuestas tardías descartadas; animaciones deshabilitadas con preferencia de movimiento reducido; tablas alternativas visibles mediante un desplegable nativo operable con teclado; contraste WCAG 2.2 AA (4,5:1 texto normal, 3:1 texto grande y elementos gráficos/componentes); sin scroll horizontal de página a 360 px; archivos protegidos sin cambios salvo las excepciones explícitas en `App.jsx` y `AppLayout.jsx`.

**Scale/Scope**: 1 cliente API, 1 hook, 4 componentes principales, 2 utilidades puras de formato/proyección, 1 página, estilos y tests colocados junto al código; páginas de 20 operaciones.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Regla | Cómo se cumple | Estado |
|-------|----------------|--------|
| **§2.2 Frontend independiente** — aplicación responsiva, comunicación exclusiva por HTTP/REST y vista obligatoria de portfolio | `/portfolio` consume exclusivamente `/auth/me`, `/users/{id}/portfolio` y `/users/{id}/transactions` mediante `portfolioApi.js` y `httpClient`; es responsive desde 360 px y no accede a persistencia ni lógica backend. | ✅ |
| **§3.2 SonarCloud** — Quality Gate aprobado, menos de 10 issues y sin duplicación | Separación API/hook/presentación, proyecciones puras compartidas por gráfico y tabla, sin duplicar formatos ni estados; lint, tests y build forman parte del quickstart. El Quality Gate definitivo se verifica en CI. | ✅ |
| **§3.4 solicitado por el equipo** | La constitución vigente v1.1.0 no contiene §3.4: la sección 3 termina en §3.3. No se inventa una regla. Como control conservador se evalúan §3.3 y el DoD §7. | ⚠️ Referencia inexistente, sin violación de diseño |
| §1.1 Consistencia del portfolio | Se muestran cantidad, PPC, valuación, resultado e historial usando directamente el contrato 028; no se alteran ni recalculan posiciones o totales. | ✅ |
| §3.1 Integración continua | La validación exige Vitest, lint y build en verde; se agregan pruebas focalizadas y de página. | ✅ |
| §3.3 Escenarios de dominio | Los tests cubren posiciones con ganancia, pérdida y resultado neutro; vacío; historial paginado; errores parciales y consistencia entre gráficos y tablas. El escenario integral de cuatro usuarios/cinco jugadores permanece cubierto por backend 028. | ✅ |
| §4.1 Autenticación y autorización | La ruta está bajo `ProtectedRoute`; el id se obtiene de `/auth/me`, nunca de entrada del usuario; los 401 siguen el manejo global de sesión. | ✅ |
| §5.1 Endpoints mínimos | Se consumen sin cambios los endpoints obligatorios de portfolio e historial ya documentados por 028. | ✅ |
| §7 Definition of Done | Tests, lint, build y verificación responsive/accesible forman parte de la guía; no hay endpoints nuevos que documentar ni operaciones financieras que auditar. | ✅ |
| Archivos protegidos de Entrega 1 | Sólo `App.jsx` recibe import + ruta protegida, y `AppLayout.jsx` habilita `portfolio` con `/portfolio`, tal como fue autorizado. | ✅ |

**Resultado previo a Phase 0**: pasa. La referencia a §3.4 es una inconsistencia documental explícita, no un gate técnico incumplido.

**Re-check post-diseño**: pasa. Los contratos de Phase 1 mantienen acceso REST, sólo lectura, estados independientes, alternativas accesibles y cero dependencias o cambios backend. No aparecen violaciones que requieran excepción.

## Project Structure

### Documentation (this feature)

```text
specs/030-front-portfolio/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── frontend-contracts.md
└── tasks.md                     # Phase 2 ($speckit-tasks), no creado por este comando
```

### Source Code (repository root)

```text
frontend/src/
├── App.jsx                                      # + import y ruta protegida /portfolio (autorizado)
├── layout/
│   └── AppLayout.jsx                            # portfolio habilitado con /portfolio (autorizado)
├── routes/
│   └── routes.test.jsx                          # ruta protegida e ítem activo en ambos menús
├── api/
│   ├── portfolioApi.js                          # NUEVO: me, portfolio, transacciones
│   └── portfolioApi.test.js                     # NUEVO
├── features/portfolio/
│   ├── portfolioFormat.js                       # NUEVO: créditos a 4 decimales, signo, fecha
│   ├── portfolioFormat.test.js                  # NUEVO
│   ├── portfolioTestData.js                     # NUEVO: helper importado sólo por tests
│   ├── portfolioViewModel.js                    # NUEVO: proyecciones de resumen y gráficos
│   ├── portfolioViewModel.test.js               # NUEVO
│   ├── usePortfolio.js                          # NUEVO: identidad + recursos independientes
│   ├── usePortfolio.test.jsx                    # NUEVO
│   ├── PositionsTable.jsx / .css                # NUEVO
│   ├── PositionsTable.test.jsx                  # NUEVO
│   ├── PerformanceChart.jsx / .css              # NUEVO: barras + tabla alternativa
│   ├── PerformanceChart.test.jsx                # NUEVO
│   ├── AllocationDonut.jsx / .css               # NUEVO: dona + tabla alternativa
│   ├── AllocationDonut.test.jsx                 # NUEVO
│   ├── TransactionsTable.jsx / .css             # NUEVO: tabla/card + controles
│   └── TransactionsTable.test.jsx               # NUEVO
└── pages/
    ├── PortfolioPage.jsx / .css                  # NUEVO
    └── PortfolioPage.test.jsx                    # NUEVO

sonar-project.properties                         # + ruta exacta del helper en sonar.test.inclusions
```

**Structure Decision**: se conserva la organización existente por feature. El acceso HTTP queda en `api/`, la coordinación y presentación específica en `features/portfolio/`, y la página sólo compone secciones y estados. `SessionContext` no se modifica: `/auth/me` se resuelve dentro de esta feature porque la sesión actual no guarda el id y no corresponde ampliar el estado global para una sola pantalla. `routes.test.jsx` se actualiza porque hoy afirma que Mi portfolio está deshabilitado y debe validar el nuevo contrato de navegación. `portfolioTestData.js` no coincide deliberadamente con el patrón de descubrimiento `*.test.*` de Vitest, para que no sea ejecutado como suite vacía; su ruta exacta se agrega a `sonar.test.inclusions` para que Sonar lo clasifique como test y no como código productivo.

## Complexity Tracking

Sin violaciones de la constitución; no aplica.
