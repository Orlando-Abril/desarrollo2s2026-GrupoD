# Quickstart: Motor de calce y creador de mercado

Guía para validar la feature 027. El diseño está en [data-model.md](data-model.md), las decisiones en [research.md](research.md) y el contrato en [contracts/orders-api.md](contracts/orders-api.md).

## Prerrequisitos

- Java 17 y Maven wrapper (`backend/mvnw.cmd`).
- Para los E2E: PostgreSQL y Redis locales (los mismos que usan los E2E existentes; ver `application-e2e.properties`).

## 0. Migración de `users.balance` (una vez, en bases existentes)

`spring.jpa.hibernate.ddl-auto=update` no cambia el tipo de una columna existente. En una base creada antes de esta feature, ejecutar una vez:

```sql
ALTER TABLE users ALTER COLUMN balance TYPE numeric(38,4);
```

Para verificar: `SELECT balance FROM users LIMIT 1;` muestra 4 decimales (por ejemplo, `1000.0000`). PostgreSQL conserva los valores. Los perfiles `test` y `e2e` crean la tabla de cero y no lo necesitan.

## 1. Tests unitarios, de servicio y de controller (H2)

```powershell
cd backend
.\mvnw.cmd "-Dtest=OrderMatchingEngineServiceTest,OrderServiceTest,MarketMakerServiceTest,MarketSimulationTest,OrderControllerTest,OrderOpenApiTest,TokenIssuanceListenerTest" test
```

Resultado esperado:

| Escenario | Spec |
|-----------|------|
| Una LIMIT de compra con tope 1.3000 contra una venta a 1.2000 se ejecuta a 1.2000 | US1-2, FR-009 |
| Ventas de C y D a 1.1000 (C más vieja) y del superusuario a 1.2000: una MARKET de 8 da 3 con C, 3 con D y 2 con el superusuario, en ese orden | US3-1, SC-001 |
| Parcial: LIMIT de 10 contra 4 ofrecidos queda `PARTIALLY_FILLED` con 6 en el libro y 7.8000 reservados | US1-4 |
| MARKET sin contraparte: 201, `CANCELLED`, 0 ejecutados | US2-4 |
| MARKET con 4 ofrecidos de 10: 4 ejecutados, resto cancelado | US1-5 |
| Órdenes propias salteadas | US3-5 |
| Banda: con marketValue 1.0000, 0.8000 y 1.2000 aceptados; 0.7999 y 1.2001 → 400 `price_out_of_band` | US4-3 |
| Cada código de error con `{error, message}`: 400, 401, 403, 404, 409, 422 | US4, US5, FR-029 |
| `"quantity": 1.5` → 400 `validation_error` | US4-1, research D11 |
| Rechazos sin rastro en `orders`, `trades` ni `audit_logs` | SC-005 |
| Cada calce: trade + `ORDER_FILLED` ×2 + `BALANCE_CHANGED` ×2 + `HOLDING_CHANGED` ×2 con el mismo correlation id | US1-6, FR-014 |
| Suma de saldos conservada; 100 tokens por jugador | SC-002, SC-004 |
| Cancelar libera créditos o tokens; 404 / 403 / 409 | US5 |
| Creador de mercado: orden al arrancar; reprecio tras un recálculo con los tokens restantes; sin cambios no rehace; sin tokens no crea; jugador emitido en el mismo recálculo recibe orden | US7, SC-006 |
| Simulación de 4 usuarios sobre 5 jugadores: tenencias, saldos y trades coinciden con el cálculo manual | Constitución §3.3 |
| OpenAPI: los cuatro endpoints con `bearerAuth` y `apiKeyAuth` | FR-031 |

## 2. E2E de concurrencia en PostgreSQL

```powershell
cd backend
.\mvnw.cmd "-Dtest=OrderConcurrencyE2ETest" test
```

Si PostgreSQL o Redis no están disponibles, `ExternalServicesAvailableCondition` saltea el test (no falla).

Resultado esperado (10 de 10 repeticiones, SC-003):

- 50 usuarios comprando a la vez 1 token de un jugador con 10 ofrecidos: exactamente 10 ejecutados, el resto cancelado; la suma de tenencias del jugador es 100.
- Ningún saldo ni tenencia negativos; la suma de saldos de todos los usuarios no cambia.
- Un usuario con saldo justo mandando compras simultáneas en jugadores distintos: sólo se aceptan las que alcanzan; las otras responden 422.

## 3. Suite completa

```powershell
cd backend
.\mvnw.cmd test "-DexcludedGroups=e2e"
.\mvnw.cmd test "-Dgroups=e2e"
```

Todo en verde, incluidos los tests de Entrega 1 sin cambios de expectativa (en particular `AuthControllerTest`, `UserTest` y `UserRepositoryTest` con el saldo a escala 4, research D5).

## 4. Prueba manual

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

1. Registrar un usuario con `POST /auth/register` y guardar el `apiKey` de la respuesta.
2. Ver el libro de un jugador: `GET /orders/book?playerId=<id>` con `X-API-KEY`. Esperado: `sells` con un nivel a la marketValue y 100 tokens (la orden del superusuario), `buys` vacío.
3. Comprar: `POST /orders/buy` con `{"playerId": <id>, "quantity": 10, "type": "MARKET"}`. Esperado: 201, `FILLED`, un trade a la marketValue.
4. Repetir el paso 2: el nivel del superusuario tiene 90.
5. Vender: `POST /orders/sell` con `{"playerId": <id>, "quantity": 5, "type": "LIMIT", "limitPrice": <marketValue × 1.1>}`. Esperado: 201, `OPEN` (nadie compra); el libro muestra el nuevo nivel de venta.
6. Cancelar: `DELETE /orders/<id de la orden del paso 5>`. Esperado: 200, `CANCELLED`; el nivel desaparece del libro. Repetir el `DELETE`: 409.
7. Recalcular con un ADMIN (`POST /quotes/recalculate`) y repetir el paso 2: la venta del superusuario tiene la nueva marketValue y 90 tokens.
8. En la base: `SELECT action, entity_type, correlation_id FROM audit_logs ORDER BY id DESC LIMIT 20;` muestra las entradas de cada paso; las del recálculo con autor `SYSTEM`.
9. Swagger UI (`/swagger-ui/index.html`): los cuatro endpoints bajo órdenes, con los candados de `bearerAuth` y `apiKeyAuth`.
