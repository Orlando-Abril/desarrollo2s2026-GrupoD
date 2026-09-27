---

description: "Dependency-ordered implementation tasks for LaFigu frontend base and auth"
---

# Tasks: Base frontend y acceso a LaFigu

**Input**: Design documents from `/specs/007-frontend-app-base-auth/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: Required by the feature. Test tasks appear before the implementation they validate and must fail for the expected reason before implementation begins.

**Organization**: Tasks are grouped by user story so each story remains independently testable. All paths are repository-relative.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel because it changes different files and has no dependency on unfinished sibling tasks.
- **[Story]**: Maps the task to a user story from `spec.md`.
- Setup, foundational and polish tasks intentionally have no story label.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Replace starter configuration with the closed stack required by the feature.

- [X] T001 Install only `react-router-dom` as a runtime dependency and `vitest`, `jsdom`, `@testing-library/react`, `@testing-library/dom`, and the Sonar-justified `@vitest/coverage-v8` as dev dependencies; add `"test": "vitest run"` and update the lockfile in `frontend/package.json` and `frontend/package-lock.json`
- [X] T002 Configure Vitest with the existing React plugin, `test.environment = "jsdom"`, and `test.coverage = {provider: "v8", reporter: ["text", "lcov"], reportsDirectory: "coverage"}` so `npm test -- --coverage` produces exactly `frontend/coverage/lcov.info`, without a second config file, in `frontend/vite.config.js`
- [X] T003 [P] Create `VITE_API_BASE_URL=http://localhost:8080` in `frontend/.env.example` and add an explicit `.env` ignore rule while keeping `.env.example` versionable in `frontend/.gitignore`
- [X] T004 [P] Set `lang="es"`, `<title>LaFigu</title>`, Google Fonts preconnects, and Anton, Archivo 400/600/800, and Space Mono 400/700 loading in `frontend/index.html`
- [X] T005 Remove Vite starter imports/content and obsolete example assets from `frontend/src/App.jsx`, `frontend/src/main.jsx`, `frontend/src/App.css`, `frontend/src/index.css`, `frontend/src/assets/`, `frontend/public/icons.svg`, and `frontend/public/favicon.svg`, retaining only assets that become intentional LaFigu assets

**Checkpoint**: The repository has the exact allowed dependencies and no Vite demo UI.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Build the shared visual, domain, transport, session, and component foundation required by every story.

**⚠️ CRITICAL**: No user story implementation starts until this phase is complete.

- [X] T006 Copy the exact normative `:root` block from `plan.md`—with no additional variables or declarations—into `frontend/src/styles/tokens.css`
- [X] T007 [P] Implement reset, paper dot pattern, body typography, page container, global page headings, yellow `:focus-visible`, and `prefers-reduced-motion` rules without unauthorized colors, radii, gradients, or blurred shadows in `frontend/src/styles/base.css`
- [X] T008 [P] Define the closed `LEAGUES` map and ordered `POSITIONS` map exactly as specified, preserving order `GOALKEEPER`, `DEFENDER`, `MIDFIELDER`, `FORWARD`, in `frontend/src/domain/catalogo.js`
- [X] T009 [P] Implement `formatCredits(n)` with `Intl.NumberFormat('es-AR', {minimumFractionDigits:2, maximumFractionDigits:2})` plus the ` cr` suffix in `frontend/src/utils/format.js`
- [X] T010 Write failing transport tests for 400/401/409, malformed and 5xx responses, network rejection, abort at exactly 10,000 ms, timer cleanup, current Bearer header, protected `onUnauthorized`, and public-auth 401 exclusion in `frontend/src/api/httpClient.test.js`
- [X] T011 Implement the sole `fetch` boundary, base URL normalization, JSON parsing, 10 s AbortController timeout, token/callback registration, `ApiError {status, code, message}`, and `NetworkError` to satisfy T010 in `frontend/src/api/httpClient.js`
- [X] T012 Implement public `register(data)` and `login(data)` adapters with no UI logic; normalize registration to omit `apiKey` before returning and preserve the feature-002 contract in `frontend/src/api/authApi.js`
- [X] T013 Implement memory-only `SessionProvider` state `null | {token, tokenType, username}`, `login`, idempotent `logout`, derived `isAuthenticated`, and safe HTTP accessor/callback registration cleanup without any browser storage access in `frontend/src/session/SessionContext.jsx`
- [X] T014 [P] Implement `primary | secondary` variants, submit-safe disabled behavior, and exact solid-shadow interactions in `frontend/src/components/Button.jsx` and `frontend/src/components/Button.css`
- [X] T015 [P] Implement associated label/input, hint, forwarded focus ref, error description, and `aria-invalid` behavior in `frontend/src/components/Field.jsx` and `frontend/src/components/Field.css`
- [X] T016 [P] Implement `error | info` banners with the Banner component as the single owner of the `✕` prefix and `role="alert"` only for errors in `frontend/src/components/Banner.jsx` and `frontend/src/components/Banner.css`
- [X] T017 [P] Implement a `role="status"` toast with 4,000 ms dismissal and mobile positioning above BottomNav in `frontend/src/components/Toast.jsx` and `frontend/src/components/Toast.css`
- [X] T018 [P] Implement `login | register` Carnet variants with contractual header, form slot, footer slot, one screen `h1`, and exact 440 px/mobile presentation in `frontend/src/components/Carnet.jsx` and `frontend/src/components/Carnet.css`
- [X] T019 Compose `BrowserRouter`, global style imports, `SessionProvider`, and the application entry without persistence or starter code in `frontend/src/main.jsx` and `frontend/src/App.jsx`

**Checkpoint**: Shared infrastructure is testable; `fetch` exists only in `httpClient.js`; session is memory-only; base components can support every story.

---

## Phase 3: User Story 1 — Ingresar y abrir el álbum (Priority: P1) 🎯 MVP

**Goal**: A registered person can submit username/password, establish an in-memory session, enter the album, and log out without seeing technical credentials.

**Independent Test**: With the backend auth contract available, log in with valid and invalid credentials, verify `Abriendo…` and single submission, reach `/album`, log out, then reload after a new login and confirm the session is lost.

### Tests for User Story 1

- [X] T020 [P] [US1] Write failing LoginPage tests for successful login, `401 invalid_credentials`, `Abriendo…`, duplicate-submit prevention, logout/session loss, and zero `localStorage`/`sessionStorage` writes in `frontend/src/pages/LoginPage.test.jsx`
- [X] T021 [P] [US1] Write failing guard tests for unauthenticated `/album` → `/ingresar` and authenticated public-only route → `/album` using `MemoryRouter` and an observable location probe in `frontend/src/routes/routes.test.jsx`

### Implementation for User Story 1

- [X] T022 [P] [US1] Implement outlet-based authentication decisions and history-replacing redirects in `frontend/src/routes/ProtectedRoute.jsx` and `frontend/src/routes/PublicOnlyRoute.jsx`
- [X] T023 [P] [US1] Implement the shared authenticated/public layout, export the single ordered `{id,label,numero,ruta,habilitado}` navigation constant from AppLayout, and consume it from Header and BottomNav in `frontend/src/layout/AppLayout.jsx`, `frontend/src/layout/AppLayout.css`, `frontend/src/layout/Header.jsx`, `frontend/src/layout/Header.css`, `frontend/src/layout/BottomNav.jsx`, and `frontend/src/layout/BottomNav.css`
- [X] T024 [P] [US1] Create the protected album destination with page heading and temporary empty-slot content in `frontend/src/pages/AlbumPage.jsx` and `frontend/src/pages/AlbumPage.css`
- [X] T025 [US1] Implement the blue `INGRESAR`/`SOCIO` carnet, required username/password fields, submit-state lock, exact auth error mapping, successful session creation, logout behavior, and album navigation in `frontend/src/pages/LoginPage.jsx` and `frontend/src/pages/LoginPage.css`
- [X] T026 [US1] Wire `/ingresar`, the protected `/album`, and session-aware `/` routing so the T020–T021 journeys work end to end in `frontend/src/App.jsx`
- [X] T027 [US1] Register the protected-401 callback flow to clear session and navigate with replace to `/ingresar` carrying only the one-time `Tu sesión venció. Volvé a ingresar.` information state in `frontend/src/session/SessionContext.jsx` and `frontend/src/pages/LoginPage.jsx`

**Checkpoint**: User Story 1 is a usable MVP: login, protected album, logout, reload loss, and protected-session expiry all work without persistent credentials.

---

## Phase 4: User Story 2 — Crear el carnet de coleccionista (Priority: P1)

**Goal**: A new person can validate and register an account, be logged in automatically, or recover cleanly when automatic login fails, with `apiKey` discarded.

**Independent Test**: Exercise empty/invalid fields, duplicate account, successful register→login→album, and successful register followed by failed login; verify exact banners/toast, focus behavior, no double submit, no `apiKey` in DOM, and no storage writes.

### Tests for User Story 2

- [X] T028 [US2] Write failing tests that enforce username/email/password as “obligatorio”; email validation through `type="email"` and `validity.typeMismatch` with no custom regex; accepted emails `abril@example.com` and `a+b@example.com`; rejected emails `abril`, `abril@`, `@example.com`, and `abril example.com`; password “mínimo 8 caracteres”; focus on the first invalid field; per-field error clearing; 409 mapping; sequential register→login; four-second welcome toast; failed auto-login recovery; absent `apiKey` in DOM; and zero storage writes in `frontend/src/pages/RegisterPage.test.jsx`

### Implementation for User Story 2

- [X] T029 [US2] Implement the red `CARNET DE COLECCIONISTA`/`NUEVO` form with email `type="email"`, required checks, native `validity.typeMismatch` validation and no custom email regex; enforce the T028 matrix, password minimum, submit-time validation, first-error focus, per-field clearing, loading lock, and backend error mapping in `frontend/src/pages/RegisterPage.jsx` and `frontend/src/pages/RegisterPage.css`
- [X] T030 [US2] Add automatic login using the same in-memory username/password, session creation, `/album` navigation with exact welcome toast, and failure navigation to `/ingresar` with username only plus exact info banner in `frontend/src/pages/RegisterPage.jsx`, `frontend/src/pages/LoginPage.jsx`, and `frontend/src/App.jsx`

**Checkpoint**: User Story 2 independently covers account creation, automatic access, secure credential disposal, and manual-login recovery.

---

## Phase 5: User Story 3 — Navegar con y sin sesión (Priority: P2)

**Goal**: Every known or unknown URL resolves deterministically for the current session, and future sections are visible but genuinely disabled.

**Independent Test**: Run the full route/session matrix for `/`, `/ingresar`, `/registro`, `/album`, and an unknown URL; inspect desktop/mobile navigation and confirm all `PRONTO` items are non-links with `aria-disabled="true"`.

### Tests for User Story 3

- [X] T031 [US3] Extend failing route tests to cover the complete session matrix, unknown route → `/` normalization, public-only registration, and history replacement in `frontend/src/routes/routes.test.jsx`

### Implementation for User Story 3

- [X] T032 [P] [US3] Implement the unknown-route component as a redirect to `/` with replacement in `frontend/src/pages/NotFound.jsx`
- [X] T033 [US3] Complete `/`, `/ingresar`, `/registro`, `/album`, and `*` route composition with ProtectedRoute/PublicOnlyRoute so T031 passes in `frontend/src/App.jsx`
- [X] T034 [US3] Complete public header actions (`CREAR CUENTA`, or `INGRESAR` on `/registro`), authenticated active `01 Álbum`, and three non-link `PRONTO` items with `aria-disabled="true"` in `frontend/src/layout/Header.jsx` and `frontend/src/layout/BottomNav.jsx`
- [X] T035 [US3] Finalize the current-feature album contract as `El álbum` plus `···` and `Las figuritas llegan en la próxima feature`, with no catalog request or routes for future areas, in `frontend/src/pages/AlbumPage.jsx`

**Checkpoint**: User Story 3 passes the complete routing matrix and exposes no false links or future routes.

---

## Phase 6: User Story 4 — Reconocer LaFigu en cualquier pantalla (Priority: P2)

**Goal**: Login, registration, and album consistently look and behave like the prescribed printed sticker album from 360 px through desktop, including reusable future components.

**Independent Test**: Review the three screens at 360, 900, and 901 px plus desktop; navigate by keyboard; enable reduced motion; confirm visual tokens, responsive switch, invented decorative players, no horizontal scroll, and accessible announcements.

### Tests for User Story 4

- [X] T036 [US4] Write failing Figurita tests for `<article aria-label="{fullName}, {team}">`, fixed position ordering, id padding, `es-AR` credits, full-name title, and `nationality=null`/`age=null` rendered as `—` rather than `null` in `frontend/src/components/Figurita.test.jsx`

### Implementation for User Story 4

- [X] T037 [US4] Implement non-clickable Figurita props and semantics, domain lookup, inline decorative silhouette, league treatment, metadata, ellipsis, grid-only lift/foil, and reduced-motion shutdown in `frontend/src/components/Figurita.jsx` and `frontend/src/components/Figurita.css`
- [X] T038 [P] [US4] Implement `vacío | cargando` Casillero variants, exact aspect ratio/dashed presentation, and reduced-motion-safe loading pulse in `frontend/src/components/Casillero.jsx` and `frontend/src/components/Casillero.css`
- [X] T039 [P] [US4] Implement `error | neutral` Sello variants with exact double-border stamp presentation in `frontend/src/components/Sello.jsx` and `frontend/src/components/Sello.css`
- [X] T040 [US4] Implement reusable EstadoPanel with Sello, uppercase heading, copy, and optional secondary action in `frontend/src/components/EstadoPanel.jsx` and `frontend/src/components/EstadoPanel.css`
- [X] T041 [P] [US4] Add the desktop login claim with red `CRACKS`, exact monospaced subtitle, and three fixed fictional Figuritas rotated -9°, 3°, and 11°—never real players—in `frontend/src/pages/LoginPage.jsx` and `frontend/src/pages/LoginPage.css`
- [X] T042 [P] [US4] Add the desktop registration claim with red `ARRANCA`, exact monospaced subtitle, and three fixed fictional Figuritas rotated -9°, 3°, and 11°—never real players—in `frontend/src/pages/RegisterPage.jsx` and `frontend/src/pages/RegisterPage.css`
- [X] T043 [US4] Replace the temporary album slot with the reusable Casillero while preserving exact placeholder copy in `frontend/src/pages/AlbumPage.jsx` and `frontend/src/pages/AlbumPage.css`
- [X] T044 [US4] Enforce the inclusive ≤900 px Header/BottomNav/auth-decoration switch, 360 px no-overflow safeguards, toast clearance, visible focus, accessible labels/descriptions, and reduced-motion rules in `frontend/src/styles/base.css`, `frontend/src/layout/AppLayout.css`, `frontend/src/layout/Header.css`, `frontend/src/layout/BottomNav.css`, `frontend/src/components/Toast.css`, `frontend/src/components/Figurita.css`, `frontend/src/components/Casillero.css`, `frontend/src/pages/LoginPage.css`, `frontend/src/pages/RegisterPage.css`, and `frontend/src/pages/AlbumPage.css`

**Checkpoint**: User Story 4 satisfies the normative SISTEMA VISUAL and accessibility contract without generic UI substitutions.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Complete documentation, CI, security review, and end-to-end validation across all stories.

- [X] T045 [P] Document install/run commands, `VITE_API_BASE_URL`, mandatory port 5173/CORS constraint, tests, lint, build, and intentional memory-only session loss on reload in `frontend/README.md`
- [X] T046 [P] Add `npm test -- --coverage` after `npm ci` and before lint/build in the unified `.github/workflows/ci.yml`; preserve Node 22, the existing backend verify, the root Sonar analysis, the existing project key and `SONAR_TOKEN`, and confirm `frontend/coverage/lcov.info` is produced for SonarCloud
- [X] T047 Audit and remove every `console.log`, commented-out implementation, secret, unauthorized hardcoded color, unauthorized gradient/radius/blurred shadow, browser-storage write, and `fetch` outside `frontend/src/api/httpClient.js` across `frontend/src/`, `frontend/index.html`, and `frontend/.env.example`
- [ ] T048 Run `npm test`, `npm run lint`, `npm run build`, and every manual scenario in `specs/007-frontend-app-base-auth/quickstart.md`; fix only in-scope defects in the referenced `frontend/` files and record no unresolved failure
- [ ] T049 After an authorized push or pull request, verify the real run of the unified `.github/workflows/ci.yml` finishes in `SUCCESS`, the single SonarCloud analysis contains both `backend/` and `frontend/`, the existing project reports Quality Gate `PASSED` with fewer than 10 issues, and no `SONAR_FRONTEND_*` secret or separate frontend project exists

**Final Checkpoint**: All four stories, automated gates, privacy rules, responsive states, and visual constraints are complete; the real unified CI run succeeds and its single existing SonarCloud project analyzes backend and frontend with Quality Gate `PASSED` and fewer than 10 issues.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 — Setup**: Starts immediately.
- **Phase 2 — Foundational**: Depends on Phase 1 and blocks every user story.
- **Phase 3 — US1**: Depends on Foundation; establishes the MVP session and initial routes.
- **Phase 4 — US2**: Depends on Foundation and reuses the auth/session destination completed by US1; its form and tests remain independently executable with mocks.
- **Phase 5 — US3**: Depends on Foundation and completes the route/layout behavior introduced for US1/US2.
- **Phase 6 — US4**: Depends on Foundation; component work can begin alongside US1–US3 after shared tokens/components exist, but final responsive integration waits for their pages/layout.
- **Phase 7 — Polish**: Depends on every story selected for delivery.

### User Story Dependency Graph

```text
Setup → Foundation → US1 (MVP)
                   ├→ US2
                   ├→ US3
                   └→ US4 component work

US1 + US2 + US3 + US4 → Polish
```

### Within Each User Story

- Write the listed tests first and confirm failure for the missing behavior.
- Implement pure/domain or reusable pieces before page integration.
- Complete the story checkpoint before treating the story as delivered.
- Do not enable future routes or add endpoints while satisfying integration.

## Parallel Opportunities

- Setup: T003 and T004 can run in parallel after T001; T005 can run while configuration work proceeds if file ownership is coordinated.
- Foundation: T007–T009 and T014–T018 target different files and can run in parallel; T011 follows T010; T012 follows T011; T013 follows the HTTP configuration contract.
- US1: T020 and T021 can be written in parallel; T022–T024 target separate files; T025–T027 converge afterward.
- US2: T028 is written first; validation UI and auto-login are sequential in the same page files.
- US3: T032 can run while T031 is authored; T033–T035 then integrate separate concerns with limited overlap.
- US4: T038, T039, T041, and T042 target different files and can proceed in parallel after T036/T037 establish Figurita; T044 is the integration pass.
- Polish: T045 and T046 can run in parallel; T047–T048 run after code settles; T049 runs last against the real unified workflow and SonarCloud Quality Gate.

## Parallel Examples

### User Story 1

```text
Task T020: Login flow tests in frontend/src/pages/LoginPage.test.jsx
Task T021: Guard tests in frontend/src/routes/routes.test.jsx

After tests exist:
Task T022: Guards in frontend/src/routes/ProtectedRoute.jsx and PublicOnlyRoute.jsx
Task T024: Album destination in frontend/src/pages/AlbumPage.jsx and AlbumPage.css
```

### User Story 2

```text
Task T028 must complete first because it defines the required failing behavior.
Then T029 implements validation/form behavior before T030 adds the automatic-login integration.
```

### User Story 3

```text
Task T031: Complete routing matrix tests in frontend/src/routes/routes.test.jsx
Task T032: Unknown-route redirect in frontend/src/pages/NotFound.jsx
```

### User Story 4

```text
After T037 provides Figurita:
Task T038: Casillero component
Task T039: Sello component
Task T041: Login decoration
Task T042: Registration decoration
```

## Implementation Strategy

### MVP First — User Story 1

1. Complete Setup.
2. Complete Foundation and pass `httpClient.test.js`.
3. Write US1 tests and confirm expected failures.
4. Implement US1 through T027.
5. Stop and validate login, protected album, logout, reload loss, and session expiry independently.

### Incremental Delivery

1. **Foundation ready**: closed stack, tokens, HTTP, session, base components.
2. **US1 MVP**: existing users can enter and leave the album.
3. **US2**: new users can create a carnet and enter automatically.
4. **US3**: all routes and future navigation states become deterministic.
5. **US4**: full printed-album identity, responsive behavior, accessibility, and reusable cards/states.
6. **Polish**: documentation, CI, audits, and complete quickstart validation.

## Notes

- `[P]` never means parallel edits to the same file.
- No task may add dependencies beyond T001 without first updating `research.md` and obtaining scope approval.
- The exact `SISTEMA VISUAL` in `plan.md` prevails for all CSS decisions.
- Only explicitly authorized color literals may appear outside `tokens.css`.
- `specs/002-auth-usuario/contracts/auth-api.md` prevails for auth payloads/statuses; feature 004 prevails for `PlayerResponse` shape.
- Commit after each task or cohesive task group; stop at checkpoints for independent verification.
