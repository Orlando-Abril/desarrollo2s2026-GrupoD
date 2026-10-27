# Contratos internos: modelo de datos del mercado

Esta feature no expone endpoints (FR-026). Sus contratos son las interfaces Java que van a usar el motor de calce, el portfolio y los endpoints de las features siguientes. Las firmas son orientativas: los nombres y la semántica son el contrato; los detalles de tipos se ajustan en la implementación.

## Repositorios (`repository/market`)

Ninguno de estos repositorios extiende `JpaRepository`. Así no existen operaciones de borrado ni `saveAll` sobre entidades inmutables.

### `HoldingRepository extends Repository<Holding, Long>`

| Método | Semántica |
|--------|-----------|
| `Holding save(Holding)` | Inserta o actualiza. Viola `uk_holdings_user_player` si ya existe la tenencia. |
| `Optional<Holding> findByUserIdAndPlayerId(Long userId, Long playerId)` | Lectura sin lock. |
| `@Lock(PESSIMISTIC_WRITE) Optional<Holding> findForUpdate(Long userId, Long playerId)` | Para reservar o mover tokens en el calce. |
| `List<Holding> findByUserIdOrderByPlayerIdAsc(Long userId)` | Tenencias de un usuario (base del portfolio). |
| `List<Long> findPlayerIdsWithoutHolding(Long userId)` | Jugadores sin tenencia del usuario dado (`NOT EXISTS`). Lo usa la emisión. |
| `long sumQuantityByPlayerId(Long playerId)` | Total de tokens de un jugador entre todas las tenencias (verifica FR-008). |

### `MarketOrderRepository extends Repository<MarketOrder, Long>`

| Método | Semántica |
|--------|-----------|
| `MarketOrder save(MarketOrder)` | Inserta o actualiza. |
| `Optional<MarketOrder> findById(Long id)` | |
| `@Lock(PESSIMISTIC_WRITE) Optional<MarketOrder> findByIdForUpdate(Long id)` | Para cancelar. |
| `List<MarketOrder> findOpenBuys(Long playerId)` | LIMIT, BUY, activas, orden `limitPrice desc, createdAt asc, id asc`. |
| `List<MarketOrder> findOpenSells(Long playerId)` | LIMIT, SELL, activas, orden `limitPrice asc, createdAt asc, id asc`. |
| `@Lock(PESSIMISTIC_WRITE) List<MarketOrder> findOpenBuysForUpdate(Long playerId)` | Igual a `findOpenBuys`, bloqueando las filas. |
| `@Lock(PESSIMISTIC_WRITE) List<MarketOrder> findOpenSellsForUpdate(Long playerId)` | Igual a `findOpenSells`, bloqueando las filas. |
| `Page<MarketOrder> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable)` | Órdenes de un usuario, de la más nueva a la más vieja. |
| `BigDecimal sumReservedCredits(Long userId)` | `Σ (quantity − filledQuantity) × limitPrice` de BUY LIMIT activas. 0 si no hay. Escala 4. |

### `TradeRepository extends Repository<Trade, Long>`

| Método | Semántica |
|--------|-----------|
| `Trade save(Trade)` | Sólo inserción. Guardar una operación existente no la modifica (`@Immutable`). |
| `Optional<Trade> findById(Long id)` | |
| `Page<Trade> findByUser(Long userId, Pageable)` | Operaciones donde el usuario es comprador **o** vendedor, orden `executedAt desc, id desc`. |
| `List<Trade> findByPlayerIdOrderByExecutedAtDescIdDesc(Long playerId)` | |

### `AuditLogRepository extends Repository<AuditLog, Long>`

| Método | Semántica |
|--------|-----------|
| `AuditLog save(AuditLog)` | Sólo inserción. |
| `Optional<AuditLog> findById(Long id)` | |
| `List<AuditLog> findByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc(String type, String id)` | Historial de una entidad. |
| `List<AuditLog> findByActionOrderByIdAsc(AuditAction action)` | Para tests y diagnóstico. |

### `MarketUserRepository extends Repository<User, Long>`

Es un repositorio nuevo sobre la entidad existente `User`; `UserRepository` no se toca.

| Método | Semántica |
|--------|-----------|
| `@Lock(PESSIMISTIC_WRITE) Optional<User> findLockedById(Long id)` | Bloquea la fila del usuario para operar su saldo. |
| `Optional<BigDecimal> findBalanceById(Long id)` | Saldo total. |
| `@Modifying int addToBalance(Long id, BigDecimal delta)` | `balance = balance + delta`. Devuelve las filas afectadas (0 si no existe). **No valida saldo negativo**: lo valida quien llama, con el usuario bloqueado. **Sólo lo usa el motor de calce**, después de `findLockedById`, y cada llamada se audita como `BALANCE_CHANGED` en la misma transacción (FR-028). Hoy la columna tiene escala 2 (ver research D8). |

## Servicios

### `service/audit/TransactionAuditService`

```text
AuditLog record(AuditActor actor, AuditAction action, String entityType, Object entityId,
                Object before, Object after)
```

- Requiere una transacción activa (`Propagation.MANDATORY`). Sin transacción lanza `IllegalTransactionStateException`.
- `before` nulo se guarda como nulo. `after` es obligatorio.
- Los dos se serializan a JSON. Quien llama pasa snapshots (records), nunca entidades.
- El correlation id sale del MDC `correlationId`. Si no hay, se genera un UUID nuevo por llamada.
- La fecha sale del `Clock` del contexto.

### `service/market/TokenIssuanceService`

```text
IssuanceResult issueMissing()   // record(int issued, int failed)
```

- Requiere que exista el superusuario. Si no existe, lanza `IllegalStateException("superuser_missing")`.
- Emite 100 tokens al superusuario por cada jugador sin tenencia suya, cada uno en su transacción con su auditoría `TOKENS_ISSUED`.
- Es idempotente: una segunda llamada sin jugadores nuevos devuelve `issued = 0`.

### `service/market/SuperuserInitializer implements ApplicationRunner`

```text
void run(ApplicationArguments args)   // TransactionOperations(ensureSuperuser()) + issueMissing()
User ensureSuperuser()                // no es @Transactional: requiere una transacción activa
```

- `run()` ejecuta `ensureSuperuser()` dentro de `TransactionOperations`. Ante `DataIntegrityViolationException` relee en una transacción nueva y continúa si el superusuario es ADMIN.
- Si la contraseña configurada es la por defecto, `run()` loguea un `WARN` `market_superuser_default_password` sin la contraseña.
- Si `market.superuser.enabled` es `false`, `run()` loguea `market_superuser_disabled` y no crea el superusuario ni emite tokens.

| Propiedad | Default en código |
|-----------|-------------------|
| `market.superuser.enabled` | `true` en el código; además `market.properties` (nuevo): `true` en `src/main/resources`, `false` en `src/test/resources` |
| `market.superuser.username` | `superuser` |
| `market.superuser.email` | `superuser@futbol-market.local` |
| `market.superuser.password` | `Superuser.2026` |

Errores de arranque (`IllegalStateException`): `superuser_username_taken` (existe sin ADMIN) y `superuser_email_taken`.

### `service/market/SuperuserAccount`

```text
Long id()      // id del superusuario, resuelto por username configurado; lo usan la emisión y el futuro creador de mercado
String MISSING = "superuser_missing"   // mensaje de la IllegalStateException de id() si no existe; el listener compara contra esta constante
```

## Eventos

| Evento | Publicador | Consumidor |
|--------|------------|------------|
| `QuoteRunCompletedEvent` (existente) | `QuoteCalculationService.recalculate`, en `finally` (016) | `TokenIssuanceListener` → `issueMissing()`. Atrapa cualquier excepción y no la propaga; sin superusuario sólo loguea `superuser_missing`. El reprecio del superusuario es de la feature del motor. |

`PlayerCatalogService` no se modifica ni publica eventos nuevos.

## Configuración

### `config/MarketConfig`

`@Configuration` con `@PropertySource("classpath:market.properties")`. Archivos nuevos:

| Archivo | Contenido |
|---------|-----------|
| `backend/src/main/resources/market.properties` | `market.superuser.enabled=true` |
| `backend/src/test/resources/market.properties` | `market.superuser.enabled=false` (pisa al de main en los tests) |

### `config/ImmutableEntityConfig`

Bean `HibernatePropertiesCustomizer` que fija `hibernate.query.immutable_entity_update_query_handling_mode=exception`: una modificación JPQL masiva sobre `Trade` o `AuditLog` lanza excepción. Los `DELETE` JPQL masivos y el SQL nativo quedan fuera de la garantía (spec, Assumptions).
