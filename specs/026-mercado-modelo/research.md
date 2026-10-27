# Research: Modelo de datos del mercado

Decisiones de diseño de la feature 026. Cada una indica qué se eligió, por qué y qué alternativas se descartaron.

## D1 — Paquetes nuevos

- **Decision**: entidades y enums en `model/market`, repositorios en `repository/market`, servicios en `service/market` y la auditoría en `service/audit`.
- **Rationale**: el mercado es un dominio nuevo y grande (lo van a extender el motor de calce, el portfolio y los endpoints). Separarlo en subpaquetes evita mezclarlo con el catálogo y las cotizaciones, y respeta las capas de la constitución (§2.1).
- **Alternatives considered**: dejar todo en `model` y `repository` como en Entrega 1. Se descartó porque esos paquetes ya mezclan varios dominios.

## D2 — Tenencia (`Holding`)

- **Decision**:
  - Unicidad `(user_id, player_id)`.
  - `quantity` y `reservedQuantity` como `int`.
  - Bloqueo optimista con `@Version`.
  - Invariantes en tres niveles: métodos de dominio que lanzan `IllegalStateException` (`credit`, `debit`, `reserve`, `release`, `consumeReserved`), Bean Validation (`@PositiveOrZero`) y una restricción `CHECK` en la tabla: `quantity >= 0 AND reserved_quantity >= 0 AND reserved_quantity <= quantity`.
  - **Cómo se declaran los `CHECK`** (vale para todas las entidades de esta feature): al implementar, verificar si `@org.hibernate.annotations.Check` está deprecado en la versión de Hibernate en uso. Si lo está, usar `@Table(check = @CheckConstraint(name = ..., constraint = ...))` de JPA 3.2, para no sumar issues de uso deprecado en Sonar.
  - Sin setters públicos.
- **Rationale**: FR-010 y FR-011 piden nunca negativa y reserva ≤ cantidad. El `@Check` protege también contra escrituras que esquiven el dominio, como un `@Modifying`. `@Version` detecta dos actualizaciones concurrentes de la misma tenencia fuera del lock del libro (por ejemplo, una cancelación y un calce).
- **Alternatives considered**:
  - Sólo validación en el dominio: no protege contra consultas directas.
  - Guardar los tokens disponibles en una columna: es redundante con cantidad − reserva y puede desincronizarse.

## D3 — Orden (`MarketOrder`, tabla `orders`)

- **Decision**:
  - La clase se llama `MarketOrder` porque `Order` choca con `jakarta.persistence.criteria.Order` y con `org.springframework.core.annotation.Order`. La tabla se llama `orders`; `ORDER` es palabra reservada en SQL, pero `orders` no lo es ni en PostgreSQL ni en H2.
  - Se crea sólo con fábricas: `MarketOrder.limit(user, player, side, quantity, limitPrice, now)` y `MarketOrder.market(user, player, side, quantity, now)`.
  - Sus transiciones son `fill(int quantity, Instant now)` y `cancel(Instant now)`. `fill` calcula el estado: con todo ejecutado pasa a `FILLED` y fija `closedAt`, con una parte pasa a `PARTIALLY_FILLED`. `cancel` desde `FILLED` o `CANCELLED` lanza `IllegalStateException`.
  - `@Check`: `quantity > 0 AND filled_quantity BETWEEN 0 AND quantity`, más `(type = 'LIMIT' AND limit_price IS NOT NULL AND limit_price > 0) OR (type = 'MARKET' AND limit_price IS NULL)`.
  - Las fechas son `createdAt`, `updatedAt` y `closedAt`, de tipo `Instant` y tomadas del `Clock` de 014.
- **Rationale**: FR-012 a FR-015. Con fábricas y transiciones no se puede construir una orden incoherente desde el código, y el `@Check` lo garantiza en la base.
- **Alternatives considered**:
  - Setters y un validador externo: deja abiertas combinaciones inválidas.
  - Un `@Check` sobre la coherencia del estado: se descartó por complejo de leer. Esa coherencia queda en el dominio y en los tests.

## D4 — Enums

- **Decision**: `OrderSide { BUY, SELL }`, `OrderType { MARKET, LIMIT }` y `OrderStatus { OPEN, PARTIALLY_FILLED, FILLED, CANCELLED }`. Se guardan con `@Enumerated(STRING)` en columnas de largo 16. `OrderStatus` expone `isActive()` (OPEN o PARTIALLY_FILLED) y la constante `ACTIVE = EnumSet.of(OPEN, PARTIALLY_FILLED)` para las consultas.
- **Rationale**: es el mismo patrón que `QuoteStatus`. `STRING` sobrevive a reordenar el enum.

## D5 — Operación ejecutada (`Trade`) inmutable

- **Decision**:
  - `@Immutable`, todas las columnas `updatable = false`, sin setters.
  - Se crea sólo con la fábrica `Trade.of(buyOrder, sellOrder, quantity, price, executedAt)`. La fábrica toma el jugador, el comprador y el vendedor de las órdenes y calcula `total = price × quantity` con escala 4, exacto porque el precio tiene escala 4 y la cantidad es entera.
  - Valida que comprador ≠ vendedor, que el jugador sea el mismo en las dos órdenes, que los lados sean BUY y SELL, y que cantidad y precio sean positivos.
  - El repositorio extiende `Repository<Trade, Long>` y sólo expone `save` y consultas, sin `delete*`. Es el mismo patrón que `PlayerQuoteRepository`.
  - Un `@PreRemove` lanza `UnsupportedOperationException` para cubrir un `EntityManager.remove` directo, porque Hibernate permite borrar entidades `@Immutable`.
  - Un bean `HibernatePropertiesCustomizer` nuevo (`config/ImmutableEntityConfig`) fija `hibernate.query.immutable_entity_update_query_handling_mode=exception`. Así una modificación JPQL masiva sobre `Trade` o `AuditLog` falla sin depender del valor por defecto de Hibernate y sin tocar `application.properties`. Es global: también protege a `PlayerQuote` y `PlayerStatsSnapshot`, que son `@Immutable` y hoy no tienen modificaciones JPQL masivas.
  - **Fuera de la garantía**: los `DELETE` JPQL masivos (el modo anterior sólo cubre modificaciones y no pasan por `@PreRemove`) y el SQL nativo. La aplicación no los usa sobre estas tablas. No se agregan triggers.
  - `@Check`: `quantity > 0 AND price > 0 AND buyer_id <> seller_id`.
- **Rationale**: FR-018 a FR-020. `@Immutable` hace que Hibernate ignore los cambios sucios, y el customizer hace que las modificaciones JPQL masivas fallen. Lo que no cubre `@Immutable` (el borrado por entidad) lo cubren el repositorio y el `@PreRemove`.
- **Alternatives considered**:
  - Fijar el modo en `application.properties`: es un archivo protegido.
  - Confiar en el valor por defecto de Hibernate: no está verificado que sea `exception`.
  - Triggers de PostgreSQL que rechacen `UPDATE` y `DELETE`: descartados por el equipo.

## D6 — Auditoría (`AuditLog`) inmutable y `TransactionAuditService`

- **Decision**:
  - Entidad `AuditLog`, tabla `audit_logs`, con la misma protección que `Trade`: `@Immutable`, `@PreRemove`, repositorio sin borrado.
  - Campos:
    - `actorType`: `USER` o `SYSTEM`.
    - `actorUserId`: nulo si es `SYSTEM`.
    - `action`: enum `AuditAction`, en esta feature `SUPERUSER_CREATED` y `TOKENS_ISSUED`. Las features siguientes agregan `ORDER_CREATED`, `ORDER_FILLED`, `ORDER_CANCELLED`, `TRADE_EXECUTED` y `BALANCE_CHANGED`.
    - `entityType` (String, largo 32) y `entityId` (String, largo 64).
    - `beforeState` y `afterState`, String con `@JdbcTypeCode(SqlTypes.JSON)`. Se guardan como `jsonb` en PostgreSQL y `json` en H2.
    - `occurredAt` (`Instant`) y `correlationId` (String, largo 36, obligatorio).
  - `TransactionAuditService.record(AuditActor actor, AuditAction action, String entityType, Object entityId, Object before, Object after)`:
    - Serializa `before` y `after` con el `JsonMapper` de Jackson 3 (`tools.jackson`) que configura Spring Boot 4. Un `null` se guarda como columna nula, que representa "sin estado anterior".
    - Toma el correlation id del MDC (`CorrelationIdFilter.MDC_KEY`) o genera un UUID si no hay.
    - Usa `Clock` para la fecha.
    - Se ejecuta en la transacción de quien llama (`Propagation.MANDATORY`). Así el cambio y su auditoría se confirman o se revierten juntos.
  - Los estados se serializan desde records de snapshot (`HoldingSnapshot`, `UserSnapshot` sin contraseña), nunca desde la entidad. Esto evita cargar relaciones lazy y filtrar el hash de la contraseña o la API key (FR-024).
- **Rationale**: FR-021 a FR-024, constitución §4.2 y DoD §7.5 (correlation id). El nombre `TransactionAuditService.record(...)` es el del prompt maestro. `MANDATORY` evita que alguien audite fuera de una transacción y deje un registro de un cambio revertido.
- **Alternatives considered**:
  - `REQUIRES_NEW`: dejaría auditoría de cambios que luego se revierten.
  - Hibernate Envers: audita entidades completas, no tiene autor ni correlation id propios y agrega una dependencia.
  - Columna `text`: H2 y PostgreSQL la tratan distinto. `SqlTypes.JSON` funciona en ambos y permite consultar el JSON en PostgreSQL.

## D7 — Créditos reservados calculados

- **Decision**: `MarketOrderRepository.sumReservedCredits(userId)` es una consulta JPQL: `coalesce(sum((o.quantity - o.filledQuantity) * o.limitPrice), 0)`, filtrada por `side = BUY`, `type = LIMIT` y `status in ACTIVE`.
- **Rationale**: FR-016 y FR-017. No requiere agregar campos a `User`, que es un archivo protegido, y no puede desincronizarse.
- **Alternatives considered**: una columna `reservedBalance` en `User` o una tabla de cuentas aparte. La primera toca un archivo protegido; las dos son estado redundante.

## D8 — Saldo desde un repositorio nuevo (`MarketUserRepository`)

- **Decision**: `MarketUserRepository extends Repository<User, Long>` con:
  - `@Lock(PESSIMISTIC_WRITE) Optional<User> findLockedById(Long id)`, que bloquea la fila del usuario para leer y modificar su saldo en el calce.
  - `@Modifying @Query("update User u set u.balance = u.balance + :delta where u.id = :id") int addToBalance(Long id, BigDecimal delta)`.
  - `@Query("select u.balance from User u where u.id = :id") Optional<BigDecimal> findBalanceById(Long id)`.
  - Esta feature no lo usa para mover saldos (el spec no los mueve). Sólo se crea y se prueba para que el motor de calce lo use.
  - **Regla de uso (FR-028)**: el saldo sólo lo modifica el motor de calce, con el usuario bloqueado con `findLockedById`, y cada cambio se audita como `BALANCE_CHANGED` en la misma transacción. Queda escrito en el javadoc de `addToBalance` y en el contrato.
- **Rationale**: `UserRepository` es un archivo protegido de Entrega 1. Un segundo repositorio sobre la misma entidad no lo modifica.
- **Riesgo abierto (spec, Assumptions)**: `User.balance` no declara precisión y Hibernate lo genera como `numeric(38,2)`. `addToBalance` con un total de 4 decimales lo redondea en la base. El motor de calce MUST resolverlo con el equipo antes de mover saldos. Esta feature lo deja cubierto por un test que documenta el comportamiento actual, sin cambiarlo.
- **Alternatives considered**: agregar los métodos a `UserRepository`. Requiere autorización.

## D9 — Libro de órdenes con lock pesimista

- **Decision**: en `MarketOrderRepository`, dos consultas con `@Lock(PESSIMISTIC_WRITE)` y `status in ACTIVE` y `type = LIMIT`:
  - `findOpenBuysForUpdate(playerId)`: `order by limitPrice desc, createdAt asc, id asc`.
  - `findOpenSellsForUpdate(playerId)`: `order by limitPrice asc, createdAt asc, id asc`.
  - Más las versiones sin lock para lectura del libro: `findOpenBuys` y `findOpenSells`, con el mismo orden.
  - El `id` desempata órdenes creadas en el mismo instante.
- **Rationale**: User Story 5 y FR-025. El lock serializa dos calces simultáneos sobre el mismo jugador y lado. Las MARKET nunca quedan en el libro, así que se filtra por LIMIT.
- **Alternatives considered**: lock optimista en las órdenes. Con mucha contención sobre las órdenes del superusuario generaría reintentos constantes.

## D10 — Índices

- **Decision**: con `@Table(indexes = …)` en cada entidad:

  | Tabla | Índice | Columnas |
  |-------|--------|----------|
  | `orders` | `idx_orders_book` | `player_id, side, status, limit_price, created_at` |
  | `orders` | `idx_orders_user_created` | `user_id, created_at` |
  | `trades` | `idx_trades_buyer_executed` | `buyer_id, executed_at` |
  | `trades` | `idx_trades_seller_executed` | `seller_id, executed_at` |
  | `trades` | `idx_trades_player_executed` | `player_id, executed_at` |
  | `holdings` | `uk_holdings_user_player` (unique) | `user_id, player_id` |
  | `holdings` | `idx_holdings_user` | `user_id` |
  | `audit_logs` | `idx_audit_logs_entity` | `entity_type, entity_id` |
  | `audit_logs` | `idx_audit_logs_occurred` | `occurred_at` |

- **Rationale**: FR-025 y constitución §5.2. En `idx_orders_book`, las columnas de igualdad (jugador, lado, estado) van primero y las de orden (precio, antigüedad) después. Para las ventas (`asc, asc`) el índice da el orden completo. Para las compras (`desc, asc`) da el filtro y el orden por precio, y el desempate por antigüedad se resuelve sobre las pocas filas de un mismo precio, lo que alcanza para el volumen de SC-006. `idx_holdings_user` es redundante con el prefijo de la unicidad: se mantiene porque el spec lo pide explícitamente y su costo es mínimo.
- **Alternatives considered**: índices parciales (`WHERE status IN (...)`). No se pueden declarar con `@Index` y `ddl-auto`.

## D11 — Superusuario (`SuperuserInitializer`)

- **Decision**: un `ApplicationRunner` en `service/market`. Siempre existe como bean. `run()` lee `market.superuser.enabled` con `@Value` y, si es `false`, loguea `market_superuser_disabled` y no hace nada. No se usa `@ConditionalOnProperty`: las condiciones se evalúan mientras se procesan las clases de configuración, y una propiedad de `@PropertySource` puede no estar cargada todavía en ese momento.
  - Configuración con `@Value`, sin tocar `application.properties`:
    - `market.superuser.username` (`superuser` por defecto)
    - `market.superuser.email` (`superuser@futbol-market.local` por defecto)
    - `market.superuser.password` (`Superuser.2026` por defecto)
    - `market.superuser.enabled`: se lee de un archivo **nuevo** `market.properties`, cargado con `@PropertySource("classpath:market.properties")` en `config/MarketConfig`. `src/main/resources/market.properties` lo pone en `true`. `src/test/resources/market.properties` lo pone en `false`, y en los tests pisa al de main porque `test-classes` va primero en el classpath. Así los tests existentes de Entrega 1 corren sin superusuario y sin emisión, sin modificarlos. Los tests nuevos de la 026 que lo necesitan lo habilitan con `@TestPropertySource(properties = "market.superuser.enabled=true")`. Una variable de entorno `MARKET_SUPERUSER_ENABLED` sigue teniendo prioridad sobre el archivo.
  - Se pueden sobrescribir por variable de entorno con el binding relajado de Spring (`MARKET_SUPERUSER_USERNAME`, etc.).
  - `ensureSuperuser()` no es `@Transactional`: `run()` la ejecuta dentro de `TransactionOperations`, con el mismo patrón que `PlayerStatsService`. Así no depende de un proxy de Spring y la auditoría (`MANDATORY`) encuentra la transacción. Flujo:
    1. Busca por username con `UserRepository.findByUsername`, que se usa sin modificarse.
    2. Si existe con rol ADMIN, no hace nada. Si existe sin ADMIN, falla con `IllegalStateException("superuser_username_taken")`.
    3. Si no existe y `existsByEmail` da verdadero, falla con `superuser_email_taken`.
    4. Si no existe, lo crea con el `PasswordEncoder` existente, `role = ADMIN` y el saldo por defecto, y audita `SUPERUSER_CREATED` con autor `SYSTEM`, `before = null` y `after = UserSnapshot(id, username, email, role, balance)`.
  - Después llama a `TokenIssuanceService.issueMissing()`.
  - Una carrera por la unicidad de username (dos instancias) termina en `DataIntegrityViolationException`. `run()` la atrapa fuera de la transacción revertida, relee en una transacción nueva y continúa si el que quedó es ADMIN.
  - Si la contraseña configurada es la por defecto, `run()` loguea un `WARN` `market_superuser_default_password`, sin la contraseña.
  - La contraseña por defecto se mantiene para desarrollo y demo. Sonar la marca como hotspot `java:S2068` (credenciales en el código); se revisa en SonarCloud y se marca como seguro con esa justificación.
- **Rationale**: FR-001 a FR-005. Fallar el arranque es lo único seguro ante un username tomado: promover al usuario a ADMIN le daría privilegios a alguien que se registró.
- **Alternatives considered**: `@ConfigurationProperties`. Es más idiomático, pero con 4 propiedades y sin claves en `application.properties` no aporta. `@PostConstruct` corre antes de que el contexto esté listo.

## D12 — Emisión de tokens (`TokenIssuanceService`)

- **Decision**:
  - `issueMissing()` busca los jugadores sin tenencia del superusuario: `HoldingRepository.findPlayerIdsWithoutHolding(superuserId)` con `NOT EXISTS`.
  - Para cada uno, en su propia transacción (`TransactionOperations`, igual que `PlayerCatalogService`), crea `Holding(superuser, player, 100, 0)` y audita `TOKENS_ISSUED` con `before = null` y `after = HoldingSnapshot`.
  - Una `DataIntegrityViolationException` (otro proceso lo emitió) se ignora con un log `debug`. Cualquier otra falla se loguea con `warn` y sigue con el siguiente (FR-009).
  - **Marca de emitido**: la existencia de la tenencia del superusuario para ese jugador. La tenencia nunca se borra (edge case "tenencia en cero"), así que aunque llegue a 0 sigue marcando al jugador como emitido y no se repone (FR-007).
- **Rationale**: no hace falta una tabla o una columna extra en `Player` (archivo protegido). La unicidad `(user_id, player_id)` hace idempotente la emisión aun con concurrencia.
- **Alternatives considered**:
  - Columna `tokensIssued` en `Player`: toca un archivo protegido.
  - Buscar en la auditoría: la auditoría no debe ser fuente de estado.

## D13 — Jugadores nuevos: emisión al terminar cada recálculo

- **Decision**: `TokenIssuanceListener` (`@EventListener`) escucha el `QuoteRunCompletedEvent` existente y llama a `issueMissing()`. Junto con la emisión del arranque, cubre a los jugadores que la sincronización agrega después. `PlayerCatalogService` no se modifica.
- **Rationale**: User Story 2 y SC-002. `QuoteCalculationService` publica el evento en un `finally`, así que llega también cuando la corrida falla. Las cotizaciones de cada jugador ya están confirmadas en sus propias transacciones, así que no hace falta `@TransactionalEventListener`. El listener atrapa cualquier excepción para no cambiar el resultado del recálculo (FR-009). Si no hay superusuario (`market.superuser.enabled=false`, como en los tests), sólo loguea `superuser_missing` y no emite.
- **Consecuencias** (van en el javadoc del listener): el evento se publica de forma sincrónica, mientras la guardia de corrida sigue tomada. La primera emisión después de una sincronización grande alarga el recálculo y demora que se libere la guardia. Además, las auditorías `TOKENS_ISSUED` heredan el correlation id de la corrida.
- **Alternatives considered**:
  - Publicar un evento desde `PlayerCatalogService`: es un archivo de Entrega 1.
  - Un `@PostPersist` en `Player`: toca un archivo protegido.
  - Un `PostInsertEventListener` de Hibernate: emitiría dentro de la transacción del jugador, mezclando dominios, y requiere registrarlo con un `Integrator`.

## D14 — Superusuario y la limpieza de E2E

- **Decision**: `E2EDatabaseCleaner` trunca todas las tablas después de cada test, así que el superusuario creado al arrancar desaparece. Los tests E2E del mercado llaman a `SuperuserInitializer.run(...)` en su `@BeforeEach`. Como `src/test/resources/market.properties` lo deshabilita, esos tests lo habilitan con `@TestPropertySource`. Los tests existentes no cambian: corren sin superusuario y, si recalculan, el listener sólo loguea `superuser_missing`.
- **Rationale**: no se modifica la limpieza compartida y se evita acoplarla al mercado.

## D15 — Precisión de las columnas de dinero

- **Decision**: `limit_price` y `trades.price` usan `numeric(12,4)`, igual que `players.market_value`. `trades.total` usa `numeric(16,4)`. La suma de créditos reservados se devuelve como `BigDecimal` con escala 4.
- **Rationale**: consistencia con la cotización vigente. Con 100 tokens y precios menores a 10^8, `16,4` alcanza para cualquier total.
