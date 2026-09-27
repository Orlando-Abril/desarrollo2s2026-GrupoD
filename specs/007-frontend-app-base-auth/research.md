# Phase 0 Research: Base frontend y acceso a LaFigu

**Date**: 2026-09-26  
**Scope**: Resolver decisiones técnicas dentro del stack cerrado, sin ampliar dependencias, vistas ni endpoints.

## 1. Router declarativo

**Decision**: Usar `react-router-dom` en modo declarativo con `BrowserRouter`, `Routes`, rutas anidadas, `Outlet` y `Navigate`. `ProtectedRoute` permite sólo sesión activa; `PublicOnlyRoute` permite sólo ausencia de sesión. Los redirects usan `replace`.

**Rationale**: La aplicación sólo necesita matching, navegación y estado activo; el modo declarativo ofrece esas capacidades con menor complejidad. Los guards centralizan acceso y evitan checks repetidos o parpadeos en páginas.

**Alternatives considered**: Data Router/loaders (no hay carga de ruta que lo justifique); navegación imperativa en `useEffect` por página (duplica responsabilidad); checks dentro de cada pantalla (difíciles de mantener).

**Sources**: [React Router — declarative mode](https://reactrouter.com/start/modes), [routing](https://reactrouter.com/start/declarative/routing), [navigation](https://reactrouter.com/start/declarative/navigating).

## 2. Sesión exclusivamente en memoria

**Decision**: `SessionProvider` mantiene `null | {token, tokenType, username}` con estado React; expone `login`, `logout` e `isAuthenticated`. No hay inicialización ni escritura en localStorage, sessionStorage, cookies o IndexedDB.

**Rationale**: El contexto distribuye el estado vigente sin dependencias adicionales; al desmontarse la aplicación en una recarga, la sesión desaparece por diseño.

**Alternatives considered**: Prop drilling (no escala al layout y futuras rutas); storage del navegador (prohibido); store externo (dependencia innecesaria).

**Sources**: [React `createContext`](https://react.dev/reference/react/createContext), [React `useContext`](https://react.dev/reference/react/useContext).

## 3. Integración entre sesión y cliente HTTP

**Decision**: `httpClient` ofrece configuración controlada mediante accessors/callbacks (`getToken`, `onUnauthorized`) sin importar React. El provider registra funciones que leen el token vigente y realizan logout+navegación ante 401 protegido, con cleanup seguro para StrictMode.

**Rationale**: Evita dependencias circulares entre `api` y `session`, permite token actualizado por request y hace testeable el comportamiento 401. La request continúa lanzando `ApiError` tras el callback.

**Alternatives considered**: Importar el contexto desde el cliente (hooks no válidos y acoplamiento circular); event bus (complejidad extra); tratar todo 401 como expiración (rompe `invalid_credentials`).

## 4. Transporte HTTP y timeout

**Decision**: Centralizar todo `fetch` en `api/httpClient.js`. Cada request construye la URL desde `VITE_API_BASE_URL`, marca explícitamente si es pública, serializa JSON, obtiene el token al momento de enviar y agrega `Authorization: Bearer <token>` si corresponde. Usa `AbortController`, timer de 10.000 ms y `clearTimeout` en `finally`.

**Rationale**: `fetch` no rechaza por 4xx/5xx, por lo que el cliente debe inspeccionar status/body. AbortController cancela efectivamente la operación; un `Promise.race` solo abandonaría la espera. Publicidad explícita evita clasificar endpoints por strings frágiles.

**Alternatives considered**: Axios (prohibido); fetch en páginas/componentes (duplica reglas); `Promise.race` (request viva); `AbortSignal.timeout` (el requisito prescribe AbortController y reduce control de tests).

**Sources**: [MDN `fetch`](https://developer.mozilla.org/en-US/docs/Web/API/Window/fetch), [MDN `AbortController`](https://developer.mozilla.org/en-US/docs/Web/API/AbortController), [Fetch Standard](https://fetch.spec.whatwg.org/).

## 5. Taxonomía de errores

**Decision**: `ApiError {status, code, message}` representa respuestas HTTP no exitosas; `NetworkError` representa rechazo de red, CORS o abort por timeout. El cuerpo se lee una vez y se parsea de forma segura. Shape inválido, éxito JSON inválido, 5xx u otra respuesta inesperada produce código/mensaje interno seguro; jamás se expone body crudo ni `statusText`.

**Rationale**: Preserva `message` para 400 y permite mapear 401/409, a la vez que separa fallas de transporte de respuestas del servidor.

**Alternatives considered**: `response.json()` directo (mezcla parse errors con red); devolver `{ok,error}` (contradice el contrato de errores lanzados); exponer el body (riesgo de información interna).

## 6. Descarte de `apiKey`

**Decision**: `authApi.register` normaliza el éxito a los datos no secretos necesarios y omite `apiKey`; `RegisterPage` jamás conserva la respuesta completa. El login automático usa username/password del formulario durante esa operación y luego libera el password.

**Rationale**: Descartar en el borde API reduce al mínimo la circulación de la credencial y vuelve verificable que no llegue al DOM, logs, sesión o storage.

**Alternatives considered**: Guardar el objeto completo y no renderizarlo (retención innecesaria); eliminar la propiedad después (ya circuló); usar la clave para login (fuera del contrato).

## 7. Testing en Vite

**Decision**: Integrar Vitest al `vite.config.js` existente con entorno `jsdom`; usar imports explícitos de Vitest y React Testing Library con queries por rol, label y texto. Agregar `vitest`, `jsdom`, `@testing-library/react`, `@testing-library/dom` y `@vitest/coverage-v8` como dev dependencies. Configurar coverage con provider `v8`, reporters `text` y `lcov`, y `reportsDirectory: 'coverage'` para que `npm test -- --coverage` genere exactamente `frontend/coverage/lcov.info`, consumido por el análisis SonarCloud unificado.

**Rationale**: Vitest reutiliza configuración y transformación de Vite; jsdom brinda DOM en Node. React Testing Library documenta `@testing-library/dom` como peer dependency directa. SonarCloud no genera cobertura JavaScript y requiere un reporte LCOV externo, por lo que `@vitest/coverage-v8` es la única dependencia adicional justificada para el gate unificado. LCOV no forma parte de los reporters predeterminados de Vitest, así que se declara explícitamente junto con su directorio de salida. Un config único evita duplicación.

**Alternatives considered**: Config separado (duplicación); happy-dom o Browser Mode (fuera del stack); Jest DOM, MSW y Playwright (no necesarios); omitir LCOV (dejaría el frontend sin cobertura importada en el Quality Gate); `@testing-library/user-event` (útil, pero `fireEvent` cubre la matriz y evita una dependencia extra).

**Sources**: [Vitest configuration](https://vitest.dev/config/), [Vitest environments](https://vitest.dev/guide/environment), [React Testing Library](https://testing-library.com/docs/react-testing-library/intro/).

## 8. CI y comandos

**Decision**: Mantener un único `.github/workflows/ci.yml` sin filtros de paths para pushes y pull requests sobre `main`. El job compila y prueba el backend con Maven, ejecuta `npm ci`, `npm test -- --coverage`, `npm run lint` y `npm run build` para el frontend, y luego lanza un único análisis SonarCloud desde la raíz. `sonar-project.properties` reutiliza `Orlando-Abril_desarrollo2s2026-GrupoD`, la organización `orlando-abril` y `SONAR_TOKEN`, incluye `backend/` y `frontend/`, importa JaCoCo y LCOV y espera el Quality Gate. El workflow redundante `frontend-ci.yml` se elimina.

**Rationale**: El modo run es determinista y no queda observando cambios en CI. Compilar Java antes del scanner aporta el bytecode obligatorio; ejecutar cobertura frontend antes del análisis produce el LCOV que SonarCloud no genera. Quitar los filtros por directorio garantiza el mismo análisis integral aunque un push modifique sólo frontend o sólo backend. Esperar el Quality Gate convierte un resultado rojo en fallo real del workflow.

**Alternatives considered**: `vitest` watch (inapropiado para CI); conservar dos workflows (duplica gates y fragmenta el estado); mantener el scanner Maven limitado a `backend/` (no analiza el frontend); crear un proyecto SonarCloud separado para frontend (rechazado: se exige un único proyecto y análisis); agregar secretos o project keys nuevos (rechazado).

**Sources**: [SonarCloud Java bytecode](https://docs.sonarsource.com/sonarqube-cloud/advanced-setup/languages/java), [JavaScript/TypeScript coverage](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/test-coverage/javascript-typescript-test-coverage), [Analysis scope](https://docs.sonarsource.com/sonarqube-cloud/managing-your-projects/project-analysis/setting-analysis-scope/setting-initial-scope).

## 9. Contrato visual y colores excepcionales

**Decision**: `tokens.css` contiene únicamente el bloque `:root` exacto. Fuera de él, sólo pueden aparecer los literales de color escritos en SISTEMA VISUAL: `#fff`, `#fbe4df`, `#1b1a17`, `rgba(27,26,23,.07)`, `rgba(255,255,255,.13)`, `rgba(255,255,255,.55)`, `rgba(255,240,150,.4)` y `transparent`. Los tres gradientes prescritos son excepciones específicas.

**Rationale**: Resuelve la contradicción entre el bloque cerrado y efectos que requieren literales, conforme a la aclaración explícita de la stakeholder del 2026-09-26.

**Alternatives considered**: Agregar tokens nuevos (prohibido); aproximar colores con tokens existentes (dejaría de copiar valores exactos); permitir literales generales (debilita el contrato).

## 10. Navegación, breakpoint y composición visual

**Decision**: Header y BottomNav consumen una única constante `{id,label,numero,ruta,habilitado}`. Hasta 900 px inclusive se usa barra inferior y se oculta username/decoración; desde 901 px se usa nav superior y decoración. Los ítems deshabilitados no son links ni handlers.

**Rationale**: Un origen evita divergencia y permite que futuras features sólo cambien `habilitado`. El límite inclusivo elimina estados intermedios.

**Alternatives considered**: Constantes separadas; links que cancelan click; breakpoints convencionales distintos (todos contradicen el contrato).

## 11. `Figurita` y contrato 004

**Decision**: La figurita acepta exactamente `{id, fullName, team, league, positions, nationality, age, marketValue}`. La posición visible es la primera según orden fijo `GOALKEEPER`, `DEFENDER`, `MIDFIELDER`, `FORWARD`, no según orden incidental del array. `null` y posición ausente se degradan a `—`.

**Rationale**: Es una proyección compatible de `PlayerResponse`; `externalId` no es necesario para presentar la carta. El orden de dominio es estable y `Intl.NumberFormat('es-AR')` cubre créditos.

**Alternatives considered**: Prop `player` completa (contradice props exactas); confiar en el array (inestable); inventar fallback de liga/posición (fuera de dominio).

## 12. Riesgos controlados

- 401 protegidos concurrentes: logout idempotente y aviso consumido una vez.
- StrictMode registra/limpia callbacks dos veces en desarrollo: cleanup ligado a la referencia registrada.
- Toast debe sobrevivir navegación registro→álbum sin incorporarse a la sesión.
- Textos largos requieren `min-width: 0`, wrap o ellipsis para sostener 360 px.
- `positions` puede estar vacío porque el contrato 004 no exige `minItems`; se muestra `—`.
- El contrato 004 usa API Key para catálogo, pero esta feature sólo reutiliza la forma de `PlayerResponse`; no consume catálogo ni conserva `apiKey`.

## 13. Compatibilidad de email con el backend

**Decision**: El frontend no replica internamente la expresión de Hibernate Validator `@Email`. Usa un input `type="email"` y considera inválido el valor vacío o aquel cuyo `HTMLInputElement.validity.typeMismatch` sea `true`. No agrega una regex propia. La matriz contractual acepta `abril@example.com` y `a+b@example.com`, y rechaza `abril`, `abril@`, `@example.com` y `abril example.com`. El backend sigue siendo la autoridad final y cualquier `400 validation_error` muestra su `message` literal.

**Rationale**: El backend declara `@NotBlank` y `@Email`, pero su implementación exacta depende de la versión de Hibernate Validator. Reimplementar esa expresión en JavaScript produciría deriva. La restricción nativa ofrece una regla determinista del lado cliente, y los casos contractuales fijan el comportamiento que los tests deben compartir.

**Alternatives considered**: Regex propia (deriva y mantenimiento duplicado); enviar todos los valores al backend sin validación cliente (incumple FR-016); incorporar una librería de validación (fuera del stack cerrado).

**Unresolved clarifications**: Ninguna.
