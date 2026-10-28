# Implementation Plan: Modelo de datos del mercado

**Branch**: `feature/mercado-modelo` | **Date**: 2026-10-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/026-mercado-modelo/spec.md`

## Summary

Esta feature agrega el modelo persistente del mercado de tokens, sin motor de calce ni endpoints:

- **Entidades nuevas** en `model/market`:
  - `Holding`: unicidad usuario + jugador, `quantity`, `reservedQuantity`, `@Version` y `CHECK` de no negatividad.
  - `MarketOrder` (tabla `orders`): fábricas `limit` y `market`, transiciones `fill` y `cancel`.
  - `Trade` y `AuditLog`: `@Immutable`, `@PreRemove` que rechaza el borrado, estados anterior y posterior en JSON. Un `HibernatePropertiesCustomizer` (`config/ImmutableEntityConfig`) hace fallar las modificaciones JPQL masivas.
  - Enums `OrderSide`, `OrderType`, `OrderStatus`, `AuditActorType` y `AuditAction`.
- **Repositorios nuevos** en `repository/market`. Todos extienden `Repository` y no `JpaRepository`, así no exponen borrado:
  - El libro abierto por jugador y lado: compra `precio desc, fecha asc`, venta `precio asc, fecha asc`, con variantes `PESSIMISTIC_WRITE`.
  - Órdenes y trades por usuario, y la suma de créditos reservados.
  - `MarketUserRepository` (`Repository<User, Long>`), que lee, bloquea y modifica `User.balance` sin tocar `UserRepository`. Sólo lo usa el motor de calce, auditando `BALANCE_CHANGED` (FR-028).
- **Servicios**:
  - `service/audit/TransactionAuditService.audit(...)`: transacción `MANDATORY`, correlation id del MDC y `Clock`.
  - `service/market/SuperuserInitializer` (`ApplicationRunner`): crea el superusuario ADMIN con `@Value` y valores por defecto en el código. `run()` ejecuta `ensureSuperuser()` dentro de `TransactionOperations` y loguea un `WARN` si usa la contraseña por defecto.
  - `service/market/TokenIssuanceService`: emite 100 tokens por jugador al superusuario, de forma idempotente, con la tenencia como marca.
  - `service/market/TokenIssuanceListener`: emite los jugadores nuevos al terminar cada recálculo (`QuoteRunCompletedEvent`). `PlayerCatalogService` no se modifica.

Las decisiones están en [research.md](research.md) (D1–D15).

## Technical Context

**Language/Version**: Java 17

**Primary Dependencies**:
- Spring Boot 4.1.1: Data JPA (Hibernate 7), Validation y `TransactionOperations`.
- Jackson 3 (`tools.jackson`), ya presente por Spring Boot, para serializar los estados de auditoría.
- Lombok.
- No se agregan dependencias.

**Storage**:
- PostgreSQL con 4 tablas nuevas: `holdings`, `orders`, `trades` y `audit_logs`. Las crea `ddl-auto=update` con sus índices y `CHECK`.
- `users` y `players` no cambian.
- H2 / create-drop (`MODE=PostgreSQL`) en el perfil `test`.

**Testing**:
- JUnit 5, AssertJ y Mockito para el dominio y los servicios, con `Clock.fixed`.
- `@DataJpaTest` + `@UnitTestProfile` para repositorios, inmutabilidad, restricciones `CHECK` y orden del libro.
- `@SpringBootTest` + `@UnitTestProfile` para el inicializador idempotente y el listener.
- `@E2ETest` contra PostgreSQL para la consulta del libro, el lock, los índices y `jsonb`.

**Target Platform**: backend web service en Linux o Windows.

**Project Type**: aplicación web (`backend/` y `frontend/`). Esta feature sólo toca `backend/`.

**Performance Goals**:
- Libro de un jugador y lado en menos de 100 ms con 10.000 órdenes abiertas en 500 jugadores (SC-006).
- Emisión inicial de unos 2.500 jugadores en una transacción por jugador.

**Constraints**:
- No se modifican `User`, `UserRepository`, `application.properties` ni `PlayerCatalogService` (FR-027).
- No hay endpoints ni calce (FR-026).
- Ninguna operación de borrado sobre `Trade` ni `AuditLog`.

**Scale/Scope**: unos 2.500 jugadores, 250.000 tokens emitidos y decenas de usuarios.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

El pedido cita "§5.2 auditoría". En la constitución la auditoría es la **§4.2**; §5.2 son caché e índices. Se evalúan las dos.

| Principio | Cumplimiento | Estado |
|-----------|--------------|--------|
| §1.1 Emisión de 100 tokens, todos del superusuario, a 1 crédito | US2 y D12: 100 tokens por jugador al superusuario. El precio de 1.00 se aplica cuando el motor publique su orden de venta (feature siguiente). | ✅ |
| §2.1 Capas | Entidades en `model/market`, acceso en `repository/market`, reglas en `service/market` y `service/audit`. Sin controllers (no hay endpoints) ni adapters (no hay fuentes externas). | ✅ |
| §4.2 Auditoría inmutable con autor, timestamp, detalle y estados anterior y posterior | `AuditLog` inmutable con `actorType` y `actorUserId`, `occurredAt`, `action` + `entityType` + `entityId`, y `beforeState` / `afterState` en JSON (D6). Se audita la creación del superusuario y cada emisión. Las modificaciones JPQL masivas fallan por `ImmutableEntityConfig` (D5); los `DELETE` JPQL masivos y el SQL nativo quedan fuera de la garantía (spec, Assumptions). Los cambios de saldo del motor se auditan como `BALANCE_CHANGED` (FR-028). | ✅ |
| §4.3 Validación estricta (sin montos negativos ni ventas de tokens no poseídos) | Invariantes de dominio + Bean Validation + restricciones `CHECK` en tenencias, órdenes y trades (D2, D3, D5). | ✅ |
| §5.2 Índices | Índices del libro, órdenes por usuario, trades por comprador, vendedor y jugador, tenencias por usuario y auditoría (D10). | ✅ |
| §5.2 Caché Redis | No aplica: datos transaccionales, que no se cachean. | ✅ N/A |
| §5.3 Correlation IDs | Cada auditoría guarda el correlation id del MDC o uno generado (D6). | ✅ |
| §7.5 DoD: operación financiera → auditoría con correlation id | `TransactionAuditService` con `MANDATORY` hace que el cambio y la auditoría se confirmen juntos. | ✅ |
| §3.1 / §3.2 CI y Sonar | Tests nuevos en el build normal. Sin código duplicado: un único servicio de auditoría y repositorios específicos. La contraseña por defecto genera el hotspot `java:S2068`, que se marca como revisado en SonarCloud con justificación (D11). Los `CHECK` usan la API no deprecada (D2). | ✅ |
| Archivos protegidos de Entrega 1 | Sin cambios en `User`, `UserRepository`, `application.properties` ni `PlayerCatalogService`. `UserRepository` se usa sin modificar; el saldo va por `MarketUserRepository`; los jugadores nuevos se emiten con `QuoteRunCompletedEvent`. | ✅ |

**Re-check post-diseño**: se mantiene todo en ✅. Un riesgo queda documentado y no viola la constitución en esta feature: `users.balance` es `numeric(38,2)` mientras los precios tienen 4 decimales (D8). El motor de calce MUST consultarlo con el equipo antes de mover saldos.

### Cambios en archivos existentes

No se modifica ningún archivo existente, ni de producción ni de tests. Los tests de Entrega 1 corren sin superusuario porque `src/test/resources/market.properties` (nuevo) pone `market.superuser.enabled=false` (research D11).

Archivos de recursos nuevos:

| Archivo | Contenido |
|---------|-----------|
| `backend/src/main/resources/market.properties` | `market.superuser.enabled=true` |
| `backend/src/test/resources/market.properties` | `market.superuser.enabled=false` |

## Project Structure

### Documentation (this feature)

```text
specs/026-mercado-modelo/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── market-model-contracts.md
├── checklists/
│   └── requirements.md
└── tasks.md             # $speckit-tasks (no lo crea este comando)
```

### Source Code (repository root)

```text
backend/src/main/java/com/example/demo/
├── model/market/
│   ├── Holding.java
│   ├── MarketOrder.java
│   ├── Trade.java
│   ├── AuditLog.java
│   ├── AuditActor.java            # record USER(id) / SYSTEM
│   ├── AuditActorType.java
│   ├── AuditAction.java
│   ├── OrderSide.java
│   ├── OrderType.java
│   └── OrderStatus.java
├── repository/market/
│   ├── HoldingRepository.java
│   ├── MarketOrderRepository.java
│   ├── TradeRepository.java
│   ├── AuditLogRepository.java
│   └── MarketUserRepository.java
├── config/
│   ├── MarketConfig.java              # @PropertySource("classpath:market.properties")
│   └── ImmutableEntityConfig.java     # HibernatePropertiesCustomizer
├── service/
│   ├── audit/
│   │   └── TransactionAuditService.java
│   └── market/
│       ├── SuperuserInitializer.java
│       ├── SuperuserAccount.java
│       ├── TokenIssuanceService.java
│       ├── TokenIssuanceListener.java
│       ├── IssuanceResult.java
│       ├── HoldingSnapshot.java
│       └── UserSnapshot.java

backend/src/test/java/com/example/demo/
├── model/market/         # HoldingTest, MarketOrderTest, TradeTest, AuditLogTest
├── config/               # ImmutableEntityConfigTest
├── repository/market/    # @DataJpaTest: Holding, MarketOrder (reservas), OrderBookQuery (orden del libro), Trade, AuditLog (inmutabilidad), MarketUser
├── service/audit/        # TransactionAuditServiceTest
├── service/market/       # TokenIssuanceServiceTest, SuperuserInitializerTest (idempotente), SuperuserInitializerCustomConfigTest, SuperuserInitializerDisabledTest, TokenIssuanceListenerTest
└── e2e/                  # MarketBookQueryE2ETest (@E2ETest, PostgreSQL)
```

**Structure Decision**: es la estructura existente de `backend/`, con subpaquetes `market` y `audit` para aislar el dominio nuevo (D1).

## Complexity Tracking

Sin violaciones de la constitución que justificar.
