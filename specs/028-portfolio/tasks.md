---

description: "Dependency-ordered implementation tasks for portfolio and user transaction queries"
---

# Tasks: Portfolio e historial de transacciones

**Input**: Design documents from `specs/028-portfolio/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/user-market-api.yaml](contracts/user-market-api.yaml), [quickstart.md](quickstart.md)

**Tests**: La especificación y el pedido del equipo requieren tests unitarios, de repositorio, MockMvc y OpenAPI. En cada historia se escriben primero y deben fallar antes de implementar.

**Organization**: Las tareas se agrupan por historia para entregar incrementos verificables. Los archivos de mercado existentes de la feature 026 se amplían sin exponer borrados ni modificar el esquema.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo con otras tareas marcadas porque usa archivos distintos y no depende de trabajo incompleto.
- **[Story]**: historia de usuario de [spec.md](spec.md).
- Toda tarea incluye la ruta exacta del archivo afectado o validado.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: confirmar una base verde antes de modificar el backend existente.

- [X] T001 Ejecutar la suite base con `backend/mvnw.cmd test` desde `backend/pom.xml` y registrar cualquier fallo preexistente antes de cambiar archivos de producción

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: tipos de error, proyecciones y fixtures compartidos por todas las historias.

**⚠️ CRITICAL**: ninguna historia comienza hasta completar esta fase.

- [X] T002 [P] Crear `UserNotFoundException` para el error 404 `user_not_found` en `backend/src/main/java/com/example/demo/exception/UserNotFoundException.java`
- [X] T003 [P] Crear `AuthenticatedUserNotFoundException` para el error 401 `authenticated_user_not_found` de principales sin cuenta en `backend/src/main/java/com/example/demo/exception/AuthenticatedUserNotFoundException.java`
- [X] T004 Crear `PortfolioInconsistentException` en `backend/src/main/java/com/example/demo/exception/PortfolioInconsistentException.java` y agregar handlers de T002, T003 y esta excepción en `backend/src/main/java/com/example/demo/exception/GlobalExceptionHandler.java`, devolviendo 500 `portfolio_inconsistent` genérico y los demás `ErrorResponse` sin datos personales
- [X] T005 [P] Crear la proyección escalar `PortfolioHoldingRow` con `holdingId`, `playerId`, `playerName`, `marketValue`, `quantity` y `reservedQuantity` en `backend/src/main/java/com/example/demo/repository/market/PortfolioHoldingRow.java`
- [X] T006 [P] Crear la proyección escalar `UserTradeRow` con `id`, `playerId`, `playerName`, `buyerId`, `sellerId`, `quantity`, `price`, `total` y `executedAt` en `backend/src/main/java/com/example/demo/repository/market/UserTradeRow.java`
- [X] T007 [P] Crear fixtures reutilizables para usuarios USER/ADMIN, jugadores, holdings, órdenes y trades con timestamps determinísticos en `backend/src/test/java/com/example/demo/support/PortfolioFixtures.java`

**Checkpoint**: errores, proyecciones y datos de prueba compartidos listos.

---

## Phase 3: User Story 1 - Consultar el portfolio valuado (Priority: P1) 🎯 MVP

**Goal**: devolver posiciones actuales, promedio ponderado, costo, valuación a `Player.marketValue`, resultado, saldo, reservas y totales.

**Independent Test**: preparar compras a precios distintos, ventas parciales, cierre/recompra, reservas y un usuario vacío; `GET /users/{id}/portfolio` debe coincidir con el cálculo manual a 4 decimales.

### Tests for User Story 1

> Escribir y ejecutar estos tests primero; deben fallar antes de la implementación.

- [X] T008 [P] [US1] Crear tests unitarios de `PortfolioService` para compras a distintos precios, ventas parciales que no cambian el promedio, compras/ventas intercaladas, cierre y recompra, pérdida, posición vacía, costo inicial 1.0000 del superusuario, `marketValue` ausente, venta imposible, discrepancias holding/trades y fotografía consistente frente a un calce concurrente en `backend/src/test/java/com/example/demo/service/market/PortfolioServiceTest.java`
- [X] T009 [P] [US1] Crear tests JPA para `PortfolioHoldingRow` y `UserTradeRow`, orden ascendente de costo, datos de jugador incluidos y cantidad constante de statements al aumentar posiciones en `backend/src/test/java/com/example/demo/repository/market/PortfolioQueryTest.java`
- [X] T010 [P] [US1] Crear tests MockMvc 200 para portfolio con posiciones y portfolio vacío, verificando todos los campos y 4 decimales, y 500 `portfolio_inconsistent` sin detalle interno para inconsistencias, en `backend/src/test/java/com/example/demo/controller/UserMarketControllerTest.java`

### Implementation for User Story 1

- [X] T011 [P] [US1] Crear el record `PortfolioPositionResponse` en `backend/src/main/java/com/example/demo/dto/market/PortfolioPositionResponse.java` con las restricciones exactas: `quantity > 0`, `0 <= reservedQuantity <= quantity`, `availableQuantity = quantity - reservedQuantity`, campos monetarios a escala 4 y `profitLoss = currentValue - costBasis`
- [X] T012 [US1] Crear el record `PortfolioResponse` en `backend/src/main/java/com/example/demo/dto/market/PortfolioResponse.java` con lista nunca nula, valores a escala 4, `availableBalance = balance - reservedCredits`, totales iguales a la suma de posiciones y `totalEquity = balance + totalCurrentValue`
- [X] T013 [US1] Agregar `findPortfolioRows(userId)` a `backend/src/main/java/com/example/demo/repository/market/HoldingRepository.java` para proyectar en una sola consulta holdings positivos y jugador, ordenados por `playerName ASC, playerId ASC`
- [X] T014 [US1] Agregar `findCostBasisRows(userId)` a `backend/src/main/java/com/example/demo/repository/market/TradeRepository.java` para proyectar todos los trades del usuario en una sola consulta, ordenados `executedAt ASC, id ASC`, sin cargar relaciones lazy
- [X] T015 [US1] Implementar `PortfolioService` con transacción read-only y aislamiento `REPEATABLE_READ` en `backend/src/main/java/com/example/demo/service/market/PortfolioService.java`: autorizar dueño/ADMIN antes de comprobar el objetivo, reconstruir costo con `BigDecimal` sin redondeos intermedios, sembrar al superusuario con 100 × 1.0000 por jugador, lanzar `PortfolioInconsistentException` ante `marketValue` ausente/venta imposible/diferencia con holdings, sumar `MarketOrderRepository.sumReservedCredits`, valuar y mapear a escala 4
- [X] T016 [US1] Crear `UserMarketController` bajo `/users` y exponer `GET /{id}/portfolio` con `id >= 1`, `Authentication` y delegación exclusiva a `PortfolioService` en `backend/src/main/java/com/example/demo/controller/UserMarketController.java`

**Checkpoint**: US1 entrega un portfolio completo y seguro; es el MVP desplegable.

---

## Phase 4: User Story 2 - Consultar el historial de operaciones (Priority: P1)

**Goal**: devolver todos los calces del usuario como compra o venta, paginados y ordenados de forma estable.

**Independent Test**: crear 25 trades, incluidos empates de timestamp y roles comprador/vendedor; las páginas 0 y 1 deben contener 20 y 5 elementos, 25 ids únicos y orden `executedAt DESC, id DESC`.

### Tests for User Story 2

> Escribir y ejecutar estos tests primero; deben fallar antes de la implementación.

- [X] T017 [P] [US2] Agregar tests de la proyección paginada, `countQuery`, comprador/vendedor y desempate por id en `backend/src/test/java/com/example/demo/repository/market/TradeRepositoryTest.java`
- [X] T018 [P] [US2] Agregar tests MockMvc de historial vacío, COMPRA/VENTA, defaults, caso rápido de 25 elementos, recorrido completo de 1.000 trades en 50 páginas con 1.000 ids únicos y orden `executedAt DESC, id DESC`, página posterior vacía y 400 para `page < 0`, `size < 1` o `size > 100` en `backend/src/test/java/com/example/demo/controller/UserMarketControllerTest.java`

### Implementation for User Story 2

- [X] T019 [P] [US2] Crear `TransactionType` con exactamente `COMPRA` y `VENTA` en `backend/src/main/java/com/example/demo/dto/market/TransactionType.java` y crear `TransactionResponse` con `id`, jugador, `quantity > 0`, `price`/`total` a escala 4 y `executedAt` requerido en `backend/src/main/java/com/example/demo/dto/market/TransactionResponse.java`
- [X] T020 [US2] Crear `TransactionPageResponse` en `backend/src/main/java/com/example/demo/dto/market/TransactionPageResponse.java` con `items` nunca null, `page >= 0`, `1 <= size <= 100`, `0 <= numberOfElements <= size`, totales no negativos y flags `first`/`last` coherentes
- [X] T021 [US2] Agregar `findTransactionRows(userId, pageable)` con proyección de trade y jugador, orden fijo `executedAt DESC, id DESC` y `countQuery` en `backend/src/main/java/com/example/demo/repository/market/TradeRepository.java`
- [X] T022 [US2] Implementar `transactions(targetUserId, authentication, page, size)` en `backend/src/main/java/com/example/demo/service/market/PortfolioService.java`, reutilizando autorización, validando rango, derivando COMPRA si `buyerId == targetUserId` y construyendo metadatos sin exponer contraparte ni órdenes
- [X] T023 [US2] Agregar `GET /{id}/transactions` con defaults `page=0`, `size=20`, límites Bean Validation y sin sort configurable en `backend/src/main/java/com/example/demo/controller/UserMarketController.java`
- [X] T024 [US2] Ejecutar `TradeRepositoryTest` y `UserMarketControllerTest` desde `backend/pom.xml` y corregir sólo fallos atribuibles a US2 manteniendo verde US1

**Checkpoint**: US2 permite recorrer el historial completo sin N+1, duplicados ni omisiones.

---

## Phase 5: User Story 3 - Respetar la privacidad por cuenta (Priority: P1)

**Goal**: garantizar 401 sin credenciales, acceso propio para USER, acceso global para ADMIN, 403 sin enumeración y 404 sólo para ADMIN.

**Independent Test**: ejecutar la misma matriz sobre portfolio e historial: sin credencial → 401; USER propio → 200; USER ajeno existente o inexistente → 403; ADMIN existente → 200; ADMIN inexistente → 404.

### Tests for User Story 3

- [X] T025 [P] [US3] Agregar tests unitarios de autorización actor-first, principal USER/ADMIN, API key de servicio `ROLE_ADMIN` sin dueño y ausencia de filtrado de existencia en `backend/src/test/java/com/example/demo/service/market/PortfolioServiceTest.java`
- [X] T026 [P] [US3] Agregar la matriz MockMvc 200/401/403/404 para ambos endpoints con JWT, API key con dueño y API key de servicio en `backend/src/test/java/com/example/demo/controller/UserMarketControllerTest.java`; en cada 400/401/403/404 verificar `error`/`message` y ausencia de balance, email, username, posiciones y transacciones, y el mismo 403 para id ajeno existente o inexistente

### Implementation for User Story 3

- [X] T027 [US3] Completar y endurecer la autorización centralizada de `PortfolioService` en `backend/src/main/java/com/example/demo/service/market/PortfolioService.java`: resolver actor antes del objetivo, devolver 403 para cualquier id ajeno a USER, permitir ADMIN y principal de servicio con `ROLE_ADMIN`, y lanzar `UserNotFoundException` sólo después de autorizar

**Checkpoint**: US3 protege ambas lecturas sin revelar cuentas ajenas.

---

## Phase 6: User Story 4 - Conocer la cuenta autenticada (Priority: P2)

**Goal**: permitir al frontend obtener id, username, email, rol y saldo vigente sin cambiar registro ni login.

**Independent Test**: `/auth/me` con JWT o API key con dueño devuelve los cinco campos de esa cuenta; sin credencial o con API key de servicio sin dueño devuelve 401; registro y login siguen verdes.

### Tests for User Story 4

- [X] T028 [P] [US4] Agregar tests MockMvc 200 con JWT, 200 con API key con dueño, 401 sin credenciales y 401 con API key de servicio sin dueño, preservando todos los tests de registro/login, en `backend/src/test/java/com/example/demo/controller/AuthControllerTest.java`

### Implementation for User Story 4

- [X] T029 [P] [US4] Crear `MeResponse` con `id`, `username`, email requerido, rol `USER|ADMIN` y balance a escala 4 en `backend/src/main/java/com/example/demo/dto/auth/MeResponse.java`
- [X] T030 [US4] Agregar `AuthService.me(username)` que busca la cuenta autenticada, mapea `MeResponse` y lanza `AuthenticatedUserNotFoundException` si el principal no tiene dueño en `backend/src/main/java/com/example/demo/service/AuthService.java`
- [X] T031 [US4] Agregar únicamente el método nuevo `GET /me` que recibe `Authentication` y delega a `AuthService.me`, sin cambiar `register` ni `login`, en `backend/src/main/java/com/example/demo/controller/AuthController.java`
- [X] T032 [US4] Ejecutar `AuthControllerTest` y `AuthServiceTest` desde `backend/pom.xml` y verificar explícitamente que registro y login no cambiaron

**Checkpoint**: US4 permite al frontend descubrir la cuenta de la sesión en una llamada.

---

## Phase 7: User Story 5 - Consultar contratos en OpenAPI (Priority: P3)

**Goal**: publicar contratos completos y verificables de portfolio, historial y `/auth/me`.

**Independent Test**: `/v3/api-docs` contiene las tres rutas, parámetros/defaults/límites, DTOs requeridos, `bearerAuth`/`apiKeyAuth`, correlation id y respuestas documentadas.

### Tests for User Story 5

- [X] T033 [P] [US5] Crear tests de `/v3/api-docs` para las tres rutas, esquemas, campos requeridos, seguridad, `X-Correlation-ID` y respuestas 200/400/401/403/404/500 según cada operación en `backend/src/test/java/com/example/demo/config/UserMarketOpenApiTest.java`

### Implementation for User Story 5

- [X] T034 [US5] Agregar `@Schema` con ejemplos, required, escalas y límites a `backend/src/main/java/com/example/demo/dto/market/PortfolioPositionResponse.java`, `backend/src/main/java/com/example/demo/dto/market/PortfolioResponse.java`, `backend/src/main/java/com/example/demo/dto/market/TransactionResponse.java`, `backend/src/main/java/com/example/demo/dto/market/TransactionPageResponse.java` y `backend/src/main/java/com/example/demo/dto/auth/MeResponse.java`
- [X] T035 [US5] Documentar operaciones, parámetros, `bearerAuth`/`apiKeyAuth`, header de correlación y respuestas —incluido 500 `portfolio_inconsistent` sólo para portfolio— en `backend/src/main/java/com/example/demo/controller/UserMarketController.java` y documentar `/auth/me` sin alterar las anotaciones existentes en `backend/src/main/java/com/example/demo/controller/AuthController.java`
- [X] T036 [US5] Ejecutar `UserMarketOpenApiTest` desde `backend/pom.xml` y comparar el OpenAPI generado con `specs/028-portfolio/contracts/user-market-api.yaml`

**Checkpoint**: US5 permite integrar los tres endpoints sin leer el código.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: rendimiento, regresión y validación completa de la feature.

- [X] T037 [P] Agregar `UserMarketPerformanceTest` —nombre ejecutable por Surefire— con PostgreSQL/CI, 10.000 trades, una ejecución de calentamiento, 5 mediciones de portfolio e historial menores a 2 segundos y cantidad constante de sentencias en `backend/src/test/java/com/example/demo/controller/UserMarketPerformanceTest.java`
- [X] T038 [P] Crear un escenario E2E con 4 usuarios operando sobre 5 jugadores y verificar cantidades, PPC, reservas, valuación, resultados y totales después de compras, ventas parciales y cierre/recompra en `backend/src/test/java/com/example/demo/e2e/PortfolioScenarioE2ETest.java`
- [X] T039 [P] Extender el escenario de fuentes caídas para comprobar que portfolio, historial y `/auth/me` responden usando sólo datos persistidos en `backend/src/test/java/com/example/demo/e2e/ExternalSourcesDownE2ETest.java`
- [X] T040 [P] Crear un E2E que compare saldo, holdings, órdenes, trades y auditorías antes y después de los tres GET y demuestre cero cambios en `backend/src/test/java/com/example/demo/e2e/PortfolioReadOnlyE2ETest.java`
- [X] T041 [P] Crear `PortfolioConsistencyE2ETest` sobre PostgreSQL real: coordinar con barreras una lectura y un calce concurrente que confirme entre las consultas del portfolio, y verificar que saldo, holdings, reservas y trades corresponden todos al estado anterior o todos al posterior, nunca a una mezcla, en `backend/src/test/java/com/example/demo/e2e/PortfolioConsistencyE2ETest.java`
- [X] T042 Ejecutar la suite focalizada indicada en `specs/028-portfolio/quickstart.md` y corregir redondeos, orden o metadatos sin relajar assertions
- [ ] T043 Ejecutar la suite completa `backend/mvnw.cmd test` desde `backend/pom.xml`, confirmar que `UserMarketPerformanceTest` fue ejecutado, revisar `backend/target/site/jacoco/jacoco.xml` y resolver regresiones o issues nuevos de calidad
- [ ] T044 Validar los escenarios restantes siguiendo `specs/028-portfolio/quickstart.md`, confirmar que `AuthController` sólo recibió `/me` y que no hay dependencias, tablas, caché ni endpoints fuera de alcance

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: comienza inmediatamente.
- **Foundational (Phase 2)**: depende de T001 y bloquea todas las historias.
- **US1 (Phase 3)**: depende de Phase 2; entrega el MVP.
- **US2 (Phase 4)**: depende de la autorización, servicio y controller creados por US1.
- **US3 (Phase 5)**: depende de US1 y US2 para aplicar y verificar la matriz sobre ambos endpoints.
- **US4 (Phase 6)**: depende sólo de Phase 2 y puede ejecutarse en paralelo con US1/US2/US3 porque toca el flujo auth.
- **US5 (Phase 7)**: depende de US1, US2 y US4, que crean rutas y DTOs.
- **Polish (Phase 8)**: depende de todas las historias seleccionadas.

### User Story Dependency Graph

```text
Setup → Foundation → US1 (portfolio/MVP) → US2 (historial) → US3 (matriz de privacidad)
                    └──────────────────────→ US4 (/auth/me)
US1 + US2 + US4 ──────────────────────────→ US5 (OpenAPI)
US1 + US2 + US3 + US4 + US5 ─────────────→ Polish
```

### Within Each User Story

- Escribir los tests y comprobar que fallan antes de implementar.
- Crear DTOs/proyecciones antes del servicio que los consume.
- Completar repositorios antes del servicio.
- Completar servicio antes del endpoint.
- Ejecutar el checkpoint antes de avanzar.

### Parallel Opportunities

- T002, T003, T005, T006 y T007 pueden ejecutarse en paralelo; T004 espera T002/T003.
- US1: T008, T009 y T010 pueden escribirse en paralelo; T011 puede avanzar en paralelo con ellos.
- US2: T017 y T018 pueden avanzar en paralelo; T019 puede avanzar en paralelo con ambos.
- US3: T025 y T026 pueden avanzar en paralelo antes de T027.
- US4 completo puede avanzar en paralelo con US1–US3 después de Foundation; dentro de US4, T028 y T029 pueden avanzar en paralelo.
- US5: T033 puede escribirse mientras se preparan las anotaciones, pero debe fallar antes de T034/T035.
- T037, T038, T039, T040 y T041 pueden prepararse en paralelo porque usan cinco clases de test distintas; T042–T044 esperan sus resultados.

---

## Parallel Examples

### User Story 1

```text
Task T008: tests matemáticos en service/market/PortfolioServiceTest.java
Task T009: tests de proyecciones en repository/market/PortfolioQueryTest.java
Task T010: contrato 200 en controller/UserMarketControllerTest.java
Task T011: DTO de posición en dto/market/PortfolioPositionResponse.java
```

### User Story 2

```text
Task T017: persistencia y orden en repository/market/TradeRepositoryTest.java
Task T018: paginación HTTP en controller/UserMarketControllerTest.java
Task T019: enum y DTO de transacción en dto/market/
```

### User Story 3

```text
Task T025: autorización unitaria en service/market/PortfolioServiceTest.java
Task T026: matriz HTTP en controller/UserMarketControllerTest.java
```

### User Story 4

```text
Task T028: tests /auth/me en controller/AuthControllerTest.java
Task T029: dto/auth/MeResponse.java
```

### User Story 5

```text
Task T033: contrato generado en config/UserMarketOpenApiTest.java
# Después de observar el fallo: T034 y T035 pueden dividirse entre DTOs y controllers.
```

---

## Implementation Strategy

### MVP First — User Story 1

1. Completar T001–T007.
2. Escribir T008–T010 y comprobar fallos.
3. Implementar T011–T016.
4. Detenerse y validar el portfolio independientemente con el escenario matemático de `quickstart.md`.
5. Demostrar el MVP antes de agregar historial.

### Incremental Delivery

1. **Foundation + US1**: portfolio valuado y seguro.
2. **US2**: historial paginado; no cambia el cálculo de US1.
3. **US3**: matriz completa de privacidad sobre ambas lecturas.
4. **US4**: identidad de sesión para que el frontend conozca el id.
5. **US5**: contrato público final verificado.
6. **Polish**: rendimiento, suite completa y no regresión.

### Parallel Team Strategy

Después de Foundation:

- Integrante A: US1 y luego US2, porque comparten `PortfolioService`, `TradeRepository` y `UserMarketController`.
- Integrante B: US4 en `AuthService`/`AuthController`.
- Integrante C: prepara tests de US3 y US5 sin editar simultáneamente los archivos de producción de A/B.
- Integración: aplicar US3, completar anotaciones US5 y ejecutar Polish.

---

## Notes

- `[P]` sólo aparece cuando los archivos no se pisan y no hay dependencia incompleta.
- Ninguna tarea agrega tablas, migraciones, dependencias o caché Redis.
- `TradeRepository` debe seguir sin operaciones de borrado.
- Las lecturas no crean auditorías porque no modifican estado financiero.
- No exponer contraparte ni ids de órdenes en el historial.
- No redondear costo después de cada trade; normalizar sólo la salida a 4 decimales.
- Mantener registro y login intactos al agregar `/auth/me`.
