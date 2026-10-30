# Implementation Plan: Motor de calce y creador de mercado

**Branch**: `feature/motor-calce` | **Date**: 2026-10-29 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/027-motor-calce/spec.md`

## Summary

Esta feature pone a funcionar el mercado sobre el modelo de la 026: el motor de calce, los endpoints de órdenes y la orden de venta del superusuario como creador de mercado.

- **Servicios nuevos** en `service/market`:
  - `OrderMatchingEngineService` (nombre del prompt maestro): calce puro sobre el lado opuesto del libro ya ordenado. Prioridad precio-tiempo, ejecución al precio de la orden del libro, parciales, salteo de órdenes propias y la regla de la banda ±20%. Sin base ni Spring; se prueba con tests unitarios (D2).
  - `OrderService`: el caso de uso transaccional. Valida, bloquea, llama al motor, aplica cada calce (tokens, créditos, órdenes, trade y auditoría juntos), reserva o cancela el resto, y también cancela órdenes y arma el libro agregado (D3–D6, D10).
  - `MarketMakerService`: `syncPlayer(playerId)` idempotente, una transacción por jugador. Corre al arrancar (`ApplicationReadyEvent`) y después de cada recálculo (`@TransactionalEventListener(fallbackExecution = true)` sobre `QuoteRunCompletedEvent`, ordenado después de la emisión de tokens) (D7).
- **Locks** `PESSIMISTIC_WRITE` en orden fijo: jugador → órdenes del lado opuesto → usuarios involucrados por id ascendente → tenencias. El lock del jugador se agrega a lo pedido (órdenes y usuarios) para cubrir el libro vacío y las órdenes nuevas (D3).
- **Web**: `controller/OrderController` con `POST /orders/buy`, `POST /orders/sell`, `DELETE /orders/{id}` y `GET /orders/book`; `exception/MarketExceptionHandler` acotado al controller, como `QuoteExceptionHandler` (D9); DTOs en `dto/market`.
- **Saldo a 4 decimales**: único cambio en `User` (autorizado): `@Column(precision = 38, scale = 4)` en `balance`. El `ALTER TABLE` para bases existentes está en el quickstart (D5).

Las decisiones están en [research.md](research.md) (D1–D13). Por qué el calce es al crear la orden y al precio de la orden existente: D1.

## Technical Context

**Language/Version**: Java 17

**Primary Dependencies**:
- Spring Boot 4.1.1: Web MVC, Data JPA (Hibernate 7), Validation, Security, `TransactionOperations`, eventos (`@TransactionalEventListener`).
- springdoc-openapi (ya presente) para la documentación.
- Jackson 3 (`tools.jackson`) para los snapshots de auditoría y los DTOs.
- Lombok.
- No se agregan dependencias.

**Storage**:
- PostgreSQL: sin tablas ni índices nuevos. `users.balance` pasa a `numeric(38,4)`.
- H2 / create-drop (`MODE=PostgreSQL`) en el perfil `test`.

**Testing**:
- JUnit 5 + AssertJ sin Spring para el motor.
- `@SpringBootTest` + `@UnitTestProfile` (H2) para `OrderService`, `MarketMakerService`, la simulación de §3.3 y los controllers con `@AutoConfigureMockMvc`, como `PlayerDetailControllerTest`.
- `@E2ETest` (PostgreSQL) para la concurrencia real con locks.

**Target Platform**: backend web service en Linux o Windows.

**Project Type**: aplicación web (`backend/` y `frontend/`). Esta feature sólo toca `backend/`.

**Performance Goals**:
- Crear una orden que barre hasta 20 niveles en menos de 500 ms; consultar el libro en menos de 200 ms (SC-008).
- Libro agregado con una consulta sobre `idx_orders_book`.

**Constraints**:
- Archivos protegidos de Entrega 1 sin cambios, salvo la anotación de `User.balance` (FR-035).
- Sin sobreventa ni saldos negativos con órdenes simultáneas (FR-025 a FR-027).
- Cada pedido de orden es una única transacción: todo o nada (FR-013).

**Scale/Scope**: unos 2.500 jugadores, 100 tokens por jugador, decenas de usuarios. El superusuario es contraparte de casi todas las compras iniciales, así que esas compras se serializan en su fila (D3, consecuencia aceptada).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

El pedido cita "§5.2 auditoría". En la constitución la auditoría es la **§4.2**; §5.2 son caché e índices. Se evalúan las dos, igual que en la 026.

| Principio | Cumplimiento | Estado |
|-----------|--------------|--------|
| §1.1 Operativa: compras contra el superusuario, validar disponibilidad y cotización vigente; venta valida tokens, acredita saldo y ajusta posición | El superusuario ofrece todos sus tokens a marketValue (D7); la compra valida saldo disponible y se ejecuta al precio del libro, que para el superusuario es la cotización vigente (D1, D4); la venta valida tokens disponibles, acredita y ajusta la tenencia. | ✅ |
| §2.1 Capas | `OrderController` (contrato y validación de entrada) → `OrderService` / `MarketMakerService` / `OrderMatchingEngineService` (reglas) → repositorios de `repository/market`. Sin adapters (no hay fuentes externas). | ✅ |
| §3.3 Escenario 3: simulación con al menos 4 usuarios sobre 5 jugadores y valuación de portfolios | `MarketSimulationTest`: 4 usuarios × 5 jugadores con compras, ventas, parciales y cancelaciones, verificando tenencias y saldos contra el cálculo manual (D13). La **valuación** del portfolio es de la feature siguiente (`GET /users/{id}/portfolio`), que extiende este escenario. | ✅ (parte de mercado) |
| §4.1 Autenticación | Los cuatro endpoints exigen JWT o API key (seguridad existente, `anyRequest().authenticated()`); el usuario sale de la autenticación, nunca del body (D8). | ✅ |
| §4.2 Auditoría inmutable con autor, timestamp, detalle y estados anterior y posterior | Cada calce audita trade, las dos órdenes, los dos saldos y las dos tenencias; creación y cancelación de órdenes y el reprecio también. Todo con `TransactionAuditService` (`MANDATORY`), en la misma transacción y con el correlation id (D6). | ✅ |
| §4.3 Validación estricta | Bean Validation en `OrderRequest` (cantidad entera > 0, precio > 0 con 4 decimales), coherencia tipo/precio, banda ±20%, saldo y tokens disponibles con locks; los `CHECK` de la 026 como última barrera (D4, D11). | ✅ |
| §5.1 Endpoints obligatorios | `POST /orders/buy` y `POST /orders/sell` en esta feature. `GET /users/:id/portfolio` y `GET /users/:id/transactions` quedan para la siguiente (spec, alcance). | ✅ (parcial, por alcance) |
| §5.2 Índices | Sin consultas nuevas que necesiten índices: el libro, sus locks y su agregación usan `idx_orders_book`; los trades por usuario ya están indexados (026). | ✅ |
| §5.2 Caché Redis | No aplica: el libro y los saldos son transaccionales y cambian con cada orden (D10). | ✅ N/A |
| §5.3 Logs estructurados y correlation id | Logs `key=value` con el correlation id del MDC en órdenes, trades, rechazos, cancelaciones y reprecios (D12). | ✅ |
| §6.1 OpenAPI | Los cuatro endpoints con `@Operation`, `@ApiResponse` por código, DTOs con `@Schema` y `@SecurityRequirement` `bearerAuth` y `apiKeyAuth`, verificado por `OrderOpenApiTest` (contrato en [contracts/orders-api.md](contracts/orders-api.md)). | ✅ |
| §7 DoD: auditoría con correlation id, resiliencia | Auditoría por cada cambio financiero. El mercado no depende de fuentes externas: opera con los datos persistidos. | ✅ |
| §3.1 / §3.2 CI y Sonar | Tests en el build normal; el E2E se saltea sin PostgreSQL. Sin duplicación: un único motor, un único servicio de órdenes, snapshots compartidos. | ✅ |
| Archivos protegidos de Entrega 1 | Único cambio: anotación de `User.balance` (autorizado 2026-10-29). `UserRepository` sólo se lee. `GlobalExceptionHandler`, `application.properties`, `PlayerCatalogService`, `PlayerRepository`, `SecurityConfig` sin cambios. | ✅ |

**Re-check post-diseño**: todo sigue en ✅. El riesgo de la 026 (D8 de la 026: saldo con 2 decimales) queda resuelto con D5.

### Cambios en archivos existentes

| Archivo | Origen | Cambio |
|---------|--------|--------|
| `model/User.java` | Entrega 1 (protegido, autorizado) | `balance`: agrega `@Column(precision = 38, scale = 4)`. Nada más. |
| `model/market/AuditAction.java` | 026 | Suma `ORDER_CREATED`, `ORDER_FILLED`, `ORDER_CANCELLED`, `TRADE_EXECUTED`, `BALANCE_CHANGED`, `HOLDING_CHANGED`; actualiza el javadoc. |
| `repository/market/MarketUserRepository.java` | 026 | Javadoc de `addToBalance`: escala 4 y que el motor modifica el saldo sobre la entidad bloqueada (FR-034). |
| `repository/market/MarketOrderRepository.java` | 026 | Consulta agregada del libro y órdenes activas del superusuario por jugador con lock. |
| `repository/market/HoldingRepository.java` | 026 | `findPlayerIdsWithTokens(userId)`. |
| `service/market/TokenIssuanceListener.java` | 026 | `@Order(MarketListenerOrder.TOKEN_ISSUANCE)`, para correr antes del reprecio. |

## Project Structure

### Documentation (this feature)

```text
specs/027-motor-calce/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── orders-api.md
├── checklists/
│   └── requirements.md
└── tasks.md             # $speckit-tasks (no lo crea este comando)
```

### Source Code (repository root)

```text
backend/src/main/java/com/example/demo/
├── controller/
│   └── OrderController.java                  # NUEVO: /orders/buy, /orders/sell, /orders/{id}, /orders/book
├── dto/market/                               # NUEVO
│   ├── OrderRequest.java
│   ├── OrderResponse.java
│   ├── OrderTradeResponse.java
│   ├── OrderBookResponse.java
│   └── BookLevelResponse.java
├── exception/
│   ├── MarketExceptionHandler.java           # NUEVO: @RestControllerAdvice(assignableTypes = OrderController.class)
│   ├── InvalidOrderException.java            # NUEVO
│   ├── PriceOutOfBandException.java          # NUEVO
│   ├── InsufficientBalanceException.java     # NUEVO
│   ├── InsufficientTokensException.java      # NUEVO
│   ├── PlayerNotQuotedException.java         # NUEVO
│   ├── OrderNotFoundException.java           # NUEVO
│   ├── OrderAccessDeniedException.java       # NUEVO
│   └── OrderClosedException.java             # NUEVO
├── model/
│   ├── User.java                             # escala de balance (autorizado)
│   └── market/AuditAction.java               # acciones nuevas
├── repository/market/
│   ├── MarketPlayerRepository.java           # NUEVO: lock del jugador
│   ├── MarketOrderRepository.java            # libro agregado, órdenes del superusuario
│   ├── HoldingRepository.java                # jugadores con tokens
│   └── MarketUserRepository.java             # javadoc
└── service/market/
    ├── OrderMatchingEngineService.java       # NUEVO: motor puro + banda
    ├── IncomingOrder.java                    # NUEVO (record)
    ├── BookEntry.java                        # NUEVO (record)
    ├── Fill.java                             # NUEVO (record)
    ├── MatchResult.java                      # NUEVO (record)
    ├── OrderService.java                     # NUEVO
    ├── OrderResult.java                      # NUEVO (record: orden + trades del pedido)
    ├── MarketLocks.java                      # NUEVO: orden fijo de locks (D3)
    ├── MarketUserResolver.java               # NUEVO: usuario autenticado (D8)
    ├── MarketMakerService.java               # NUEVO
    ├── SyncResult.java                       # NUEVO (record)
    ├── MarketListenerOrder.java              # NUEVO: constantes de @Order
    ├── OrderSnapshot.java                    # NUEVO
    ├── TradeSnapshot.java                    # NUEVO
    ├── BalanceSnapshot.java                  # NUEVO
    └── TokenIssuanceListener.java            # @Order

backend/src/test/java/com/example/demo/
├── service/market/
│   ├── OrderMatchingEngineServiceTest.java   # unitario, sin Spring
│   ├── OrderServiceTest.java                 # H2
│   ├── MarketMakerServiceTest.java           # H2, recálculo real
│   └── MarketSimulationTest.java             # §3.3: 4 usuarios × 5 jugadores
├── controller/
│   └── OrderControllerTest.java              # MockMvc, cada código de error
├── config/
│   └── OrderOpenApiTest.java
└── e2e/
    └── OrderConcurrencyE2ETest.java          # @E2ETest, PostgreSQL, hilos simultáneos
```

**Structure Decision**: la estructura existente de `backend/`, con el dominio del mercado en los subpaquetes `market` que abrió la 026 y los DTOs en `dto/market`, como `dto/auth`.

## Complexity Tracking

Sin violaciones de la constitución que justificar.

Una desviación del pedido, explicada en research D3: además de los locks sobre las órdenes del lado opuesto y los usuarios, se bloquea primero la fila del jugador. Sin ese lock, con el lado opuesto vacío no se bloquea nada, y una compra y una venta simultáneas podrían quedar cruzadas en el libro sin calzarse.
