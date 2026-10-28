# Quickstart: Modelo de datos del mercado

Guía para validar la feature 026. El diseño está en [data-model.md](data-model.md) y los contratos en [contracts/market-model-contracts.md](contracts/market-model-contracts.md).

## Prerrequisitos

- Java 17 y Maven wrapper (`backend/mvnw.cmd`).
- Para los E2E: PostgreSQL y Redis locales (los mismos que usan los E2E existentes; ver `application-e2e.properties`).

## 1. Tests unitarios y de repositorio (H2)

```powershell
cd backend
.\mvnw.cmd "-Dtest=HoldingTest,MarketOrderTest,TradeTest,AuditLogTest,HoldingRepositoryTest,MarketOrderRepositoryTest,TradeRepositoryTest,AuditLogRepositoryTest,MarketUserRepositoryTest,TransactionAuditServiceTest,TokenIssuanceServiceTest,SuperuserInitializerTest,SuperuserInitializerCustomConfigTest,SuperuserInitializerDisabledTest,TokenIssuanceListenerTest,OrderBookQueryTest,ImmutableEntityConfigTest" test
```

Resultado esperado:

| Escenario | Spec |
|-----------|------|
| Una tenencia no queda negativa ni con reserva mayor que la cantidad, ni por dominio ni por `CHECK` | US3-2, SC-004 |
| Segunda tenencia del mismo usuario y jugador rechazada | US3-3 |
| Créditos reservados = Σ pendiente × límite sólo de BUY LIMIT activas; 0 sin órdenes | US3-4..6, SC-005 |
| Órdenes incoherentes rechazadas; transiciones FILLED y CANCELLED con `closedAt` | US3-7, US3-8 |
| Libro de venta `precio asc, fecha asc`; libro de compra `precio desc, fecha asc` | US5-2 |
| Modificar una `Trade` o un `AuditLog` no cambia la base; una modificación JPQL masiva y un borrado por entidad lanzan excepción; los repositorios no tienen `delete*` | US4-2, US4-5, SC-003 |
| `Trade` de 3 × 1.0437 con total 3.1311 | US4-1 |
| Auditoría con correlation id del MDC o generado, y sin transacción falla | FR-023 |
| Superusuario creado una vez; 5 ejecuciones de la inicialización no lo duplican; username tomado sin ADMIN o email tomado fallan sin modificar; `WARN` con la contraseña por defecto | US1, SC-001 |
| Emisión de 100 por jugador, idempotente, sin reponer tras vender; una falla no corta las demás | US2, FR-007..009 |
| Al recibir `QuoteRunCompletedEvent` los jugadores sin tokens quedan emitidos; una falla de la emisión no se propaga | US2-2, SC-002 |
| Ninguna auditoría contiene el hash de la contraseña | SC-007 |

## 2. E2E en PostgreSQL

```powershell
cd backend
.\mvnw.cmd "-Dtest=MarketBookQueryE2ETest" test
```

Si PostgreSQL o Redis no están disponibles, `ExternalServicesAvailableCondition` saltea el test (no falla).

Resultado esperado:

- El esquema tiene los índices de research D10 (consulta a `pg_indexes`) y las restricciones `CHECK` de data-model.
- Con 10.000 órdenes abiertas en 500 jugadores, `findOpenSells` y `findOpenBuys` de un jugador devuelven el orden correcto en menos de 100 ms y `EXPLAIN` muestra un `Index Scan` o un `Bitmap Index Scan` sobre `idx_orders_book` (SC-006).
- `findOpenSellsForUpdate` bloquea: mientras la primera transacción tiene el lock, una segunda que lo pide con timeout 0 (`NOWAIT`) falla con `PessimisticLockException`.
- `before_state` y `after_state` se guardan como `jsonb`.

## 3. Arranque manual

```powershell
cd backend
$env:MARKET_SUPERUSER_USERNAME = 'mm'; $env:MARKET_SUPERUSER_EMAIL = 'mm@local'; $env:MARKET_SUPERUSER_PASSWORD = 'Secret.123'
.\mvnw.cmd spring-boot:run
```

Verificar en la base:

```sql
SELECT id, username, role FROM users WHERE username = 'mm';                         -- 1 fila, ADMIN
SELECT count(*) FROM holdings h JOIN users u ON u.id = h.user_id
 WHERE u.username = 'mm' AND h.quantity = 100;                                      -- = count(*) de players
SELECT action, count(*) FROM audit_logs GROUP BY action;                             -- SUPERUSER_CREATED 1, TOKENS_ISSUED N
```

Reiniciar la aplicación y repetir las consultas: los números no cambian. Hacer login con `POST /auth/login` y la contraseña configurada debe devolver un JWT.

## 4. Bases existentes

Las tablas son nuevas y `ddl-auto=update` las crea con sus índices y `CHECK`. No hay migración manual de datos existentes. `users.balance` no cambia (sigue `numeric(38,2)`; ver research D8).
