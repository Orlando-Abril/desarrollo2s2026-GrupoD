# Implementation Plan: Portfolio e historial de transacciones

**Branch**: `feature/portfolio` | **Date**: 2026-10-29 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/028-portfolio/spec.md`

## Summary

La feature agrega tres lecturas autenticadas sobre el modelo de mercado existente:

- `GET /users/{id}/portfolio`: `service/market/PortfolioService` arma posiciones y totales a partir de tenencias, operaciones ejecutadas y `Player.marketValue`. El precio promedio se reconstruye cronológicamente: las compras agregan costo, las ventas parciales consumen costo al promedio vigente sin modificarlo, y una posición cerrada reinicia el costo antes de una recompra.
- `GET /users/{id}/transactions`: historial paginado de trades desde la perspectiva del usuario, ordenado por `executedAt DESC, id DESC`.
- `GET /auth/me`: método nuevo en `AuthController`, respaldado por `AuthService`, que devuelve `MeResponse` con la cuenta autenticada.

`UserMarketController` vive bajo `/users`; los DTOs y proyecciones son nuevos. Las consultas de tenencias y trades proyectan todos los campos requeridos, incluido jugador y cotización, en consultas masivas: no se recorre una relación lazy por fila ni se introduce N+1. “Consulta agregada” se concreta como una cantidad constante de lecturas consolidadas, no como una única sentencia ni como una consulta por posición. El presupuesto se mide por modo de autorización —dueño, ADMIN o API key de servicio— porque resolver actor y objetivo puede requerir distinta cantidad fija de consultas. No se agregan entidades, tablas, migraciones, caché ni dependencias.

## Technical Context

**Language/Version**: Java 17

**Primary Dependencies**: Spring Boot 4.1.1; Spring Web MVC, Security, Data JPA/Hibernate 7, Validation y springdoc OpenAPI 3.1.1; Lombok existente. No se agregan dependencias.

**Storage**: PostgreSQL en ejecución; H2 en tests. Se reutilizan `users`, `players`, `holdings`, `orders` y `trades`, con sus índices existentes. No hay cambios de esquema.

**Testing**: JUnit 5, AssertJ, Mockito; `@DataJpaTest` para proyecciones; `@SpringBootTest` + `@AutoConfigureMockMvc` para contratos, seguridad, paginación y OpenAPI.

**Target Platform**: backend web service ejecutable en Linux o Windows.

**Project Type**: aplicación web con `backend/` y `frontend/`; esta feature sólo modifica `backend/`.

**Performance Goals**: después de una ejecución de calentamiento, cada una de 5 ejecuciones sobre PostgreSQL en CI para una cuenta con 10.000 trades completa en menos de 2 segundos; para cada modo de autorización, la cantidad de consultas por request es constante e independiente de posiciones o elementos de página.

**Constraints**: promedio calculado desde trades; valuación exclusivamente con `Player.marketValue`; precisión de respuesta a 4 decimales; aislamiento `REPEATABLE_READ` read-only para una fotografía consistente; autorización USER-propio/ADMIN-cualquiera; 401/403/404 sin filtrar existencia; inconsistencias de portfolio → 500 `portfolio_inconsistent`; página base cero, tamaño 20 por defecto y máximo 100; cambios en `AuthController` limitados al nuevo `GET /me`.

**Scale/Scope**: unos 2.500 jugadores, decenas de usuarios, hasta 100 posiciones por cuenta y 10.000 operaciones históricas por cuenta en el objetivo de prueba.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

El pedido cita “§3.4 portfolio”, que corresponde al enunciado funcional. En la constitución vigente la consistencia del portfolio está en §1.1 y los endpoints obligatorios en §5.1. También se verifica expresamente §6.1, como pidió el equipo.

| Principio | Cumplimiento de diseño | Estado |
|-----------|------------------------|--------|
| Enunciado §3.4 / Constitución §1.1: cantidad, PPC, valuación actual, ganancia/pérdida e historial | `PortfolioService` reconstruye costo promedio desde trades, cruza cantidades con holdings y valúa con `marketValue`; el historial usa trades inmutables. | ✅ |
| §2.1 Capas estrictas | `UserMarketController` expone HTTP; `PortfolioService` concentra autorización y cálculo; repositorios/proyecciones realizan lecturas; no hay adapters porque no hay fuente externa. | ✅ |
| §3.1–§3.3 Calidad y tests | Unitarios cubren compras/ventas intercaladas, cierre y recompra; repositorio cubre proyección masiva; MockMvc cubre 200/400/401/403/404/500 y páginas; un E2E simula 4 usuarios sobre 5 jugadores; OpenAPI tiene prueba propia. | ✅ |
| §4.1 Autenticación y autorización | Security existente exige credenciales; el servicio valida dueño o ADMIN antes de buscar el usuario objetivo. `/auth/me` resuelve la cuenta del principal. | ✅ |
| §4.2 Auditoría inmutable | No aplica a escrituras: los tres endpoints son read-only y no alteran saldo, tokens, órdenes, trades ni auditoría. | ✅ N/A |
| §4.3 Validación estricta | `id >= 1`, `page >= 0` y `1 <= size <= 100`; errores usan el shape común. | ✅ |
| §5.1 Endpoints mínimos | Se implementan ambos GET obligatorios de usuario. | ✅ |
| §5.2 Índices y Redis | Se usan índices existentes de holdings y trades. No se cachea información financiera mutable; Redis no participa para evitar datos obsoletos. | ✅ |
| §5.3 Correlation ID | El filtro existente propaga el header en todas las respuestas; no se modifica. | ✅ |
| §6.1 OpenAPI / Swagger | Los tres endpoints, DTOs, parámetros, ejemplos, esquemas de seguridad y respuestas quedan anotados y verificados contra `/v3/api-docs`. | ✅ |
| §7 Definition of Done | Separación en capas, suite Maven, documentación, E2E con fuentes externas caídas y prueba automatizada de ausencia de escrituras/auditorías. | ✅ |

**Gate pre-diseño**: PASS. No hay violaciones ni aclaraciones técnicas pendientes.

**Re-check post-diseño**: PASS. [research.md](research.md), [data-model.md](data-model.md), [contracts/user-market-api.yaml](contracts/user-market-api.yaml) y [quickstart.md](quickstart.md) mantienen las decisiones anteriores. Las consultas consolidadas evitan N+1 y el contrato conserva precisión, seguridad y documentación.

### Cambios previstos en archivos existentes

| Archivo | Cambio acotado |
|---------|----------------|
| `controller/AuthController.java` | Agregar únicamente `GET /me`, su documentación y la dependencia ya existente de `AuthService`; registro y login no cambian. |
| `service/AuthService.java` | Agregar la lectura `me(username)` que devuelve `MeResponse`; no cambiar registro ni login. |
| `repository/market/HoldingRepository.java` | Agregar proyección masiva `findPortfolioRows(userId)` con datos de holding y jugador. |
| `repository/market/TradeRepository.java` | Agregar proyecciones masivas para costo cronológico y página de historial, sin exponer mutaciones. |
| `repository/market/MarketOrderRepository.java` | Reutilizar `sumReservedCredits`; no requiere cambio salvo que la implementación necesite renombrar el método sin alterar semántica. |
| `exception/GlobalExceptionHandler.java` | Mapear usuario inexistente a 404 e identidad autenticada inexistente a 401 con el formato común. |
| `exception/PortfolioInconsistentException.java` | Agregar error interno de dominio para `marketValue` ausente o divergencia entre trades y holding; el handler publica 500 genérico. |
| `controller/AuthControllerTest.java` | Agregar MockMvc 200/401 para `/auth/me`; tests existentes se conservan. |

## Project Structure

### Documentation (this feature)

```text
specs/028-portfolio/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── user-market-api.yaml
├── checklists/
│   └── requirements.md
└── tasks.md                    # lo genera $speckit-tasks, no este comando
```

### Source Code (repository root)

```text
backend/src/main/java/com/example/demo/
├── controller/
│   ├── AuthController.java                 # sólo nuevo GET /me
│   └── UserMarketController.java           # nuevo: /users/{id}/...
├── dto/
│   ├── auth/
│   │   └── MeResponse.java                 # nuevo
│   └── market/
│       ├── PortfolioResponse.java          # nuevo
│       ├── PortfolioPositionResponse.java  # nuevo
│       ├── TransactionResponse.java        # nuevo
│       ├── TransactionPageResponse.java    # nuevo
│       └── TransactionType.java            # nuevo: COMPRA / VENTA
├── exception/
│   ├── AuthenticatedUserNotFoundException.java  # nuevo
│   ├── PortfolioInconsistentException.java       # nuevo: 500 público y genérico
│   ├── UserNotFoundException.java               # nuevo
│   └── GlobalExceptionHandler.java
├── repository/market/
│   ├── HoldingRepository.java
│   ├── PortfolioHoldingRow.java            # nueva proyección escalar
│   ├── TradeRepository.java
│   └── UserTradeRow.java                   # nueva proyección escalar
└── service/
    ├── AuthService.java
    └── market/
        └── PortfolioService.java           # nuevo

backend/src/test/java/com/example/demo/
├── controller/
│   ├── AuthControllerTest.java             # /me 200 y 401
│   └── UserMarketControllerTest.java       # 200/400/401/403/404/paginado
├── service/market/
│   └── PortfolioServiceTest.java           # promedio y totales
├── repository/market/
│   └── PortfolioQueryTest.java             # proyecciones y ausencia de lazy N+1
├── config/
│   └── UserMarketOpenApiTest.java          # contratos publicados
└── e2e/
    ├── PortfolioScenarioE2ETest.java        # 4 usuarios, 5 jugadores
    ├── PortfolioReadOnlyE2ETest.java        # cero cambios
    ├── PortfolioConsistencyE2ETest.java     # snapshot PostgreSQL bajo calce concurrente
    └── ExternalSourcesDownE2ETest.java      # se amplía con los 3 GET
```

**Structure Decision**: se mantiene el backend único existente. El subpaquete `market` agrupa cálculo y DTOs del dominio financiero; `AuthController` y `AuthService` reciben sólo la ampliación necesaria para la identidad actual.

## Design Detail

### Flujo de portfolio

1. `UserMarketController` valida `id` y entrega `Authentication` a `PortfolioService`; el método de servicio abre una transacción read-only con aislamiento `REPEATABLE_READ`.
2. El servicio resuelve el actor por `authentication.getName()`. Un USER con otro id falla 403 antes de consultar si el objetivo existe; un ADMIN o una API key de servicio con `ROLE_ADMIN` puede continuar.
3. Se obtiene el usuario objetivo o se responde 404.
4. `HoldingRepository.findPortfolioRows(id)` devuelve todas las tenencias positivas con `playerId`, nombre, `marketValue`, cantidad y reserva en una consulta.
5. `TradeRepository.findCostBasisRows(id)` devuelve en una consulta todos los trades del usuario, ordenados `executedAt ASC, id ASC`, con ids de jugador/comprador/vendedor, cantidad y precio. No devuelve entidades lazy.
6. Por jugador, el servicio mantiene cantidad y costo exacto. Compra: suma cantidad y `cantidad × precio`. Venta: resta cantidad y costo al promedio exacto previo; si queda cero, costo cero. Para el superusuario se parte de 100 tokens a costo 1.0000 por jugador emitido.
7. El servicio cruza el estado calculado con la tenencia actual. `marketValue` ausente, una venta imposible o una diferencia de cantidad lanzan `PortfolioInconsistentException`, que se publica como 500 `portfolio_inconsistent` sin detalle interno. Luego suma reservas de créditos y construye posiciones y totales a escala 4.

### Flujo de transacciones

1. Se aplica la misma autorización y existencia.
2. Se valida la página.
3. `TradeRepository.findTransactionRows(id, pageable)` proyecta trade y jugador en una consulta paginada más su `countQuery`, orden fijo descendente.
4. El lado se deriva por `buyerId == id`: `COMPRA`; en otro caso, `VENTA`.

### Flujo de `/auth/me`

1. Security exige credencial porque `/auth/me` no se agrega a rutas públicas.
2. `AuthController.me(Authentication)` llama `AuthService.me(authentication.getName())`.
3. El servicio busca por username y devuelve `MeResponse` con id, username, email, rol y saldo a escala 4.
4. JWT y API key con dueño resuelven la cuenta. Una API key de servicio sin dueño no representa un usuario y recibe 401.

## Complexity Tracking

Sin violaciones de la constitución que justificar.
