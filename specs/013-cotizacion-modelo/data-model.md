# Data Model: Modelo de datos de la cotización

**Feature**: `specs/013-cotizacion-modelo` | **Fecha**: 2026-10-14

## 1. `PlayerQuote` (entidad NUEVA, tabla `player_quotes`)

Es el registro histórico e inmutable del precio del token de un jugador según una estrategia y una versión, en un instante dado. Se marca con `@org.hibernate.annotations.Immutable` y todas sus columnas son `updatable = false`. No tiene setters.

| Campo | Columna | Tipo Java / SQL | Nulo | Regla |
|-------|---------|-----------------|------|-------|
| `id` | `id` | `Long` / `bigint` identity | no | PK. También desempata cotizaciones con el mismo instante: el `id` mayor es la registrada más tarde. |
| `player` | `player_id` | `@ManyToOne(fetch = LAZY, optional = false)` → `players.id` | no | FK a `Player`. `Player` no se modifica ni conoce esta entidad. |
| `strategyCode` | `strategy_code` | `String` / `varchar(32)` | no | Código canónico del catálogo (`OFFICIAL`, `MOMENTUM`). |
| `strategyVersion` | `strategy_version` | `Integer` / `integer` | no | Mayor o igual a 1. Es `Integer` y no `int` para que omitirla se rechace por obligatoria y no pase como 0. |
| `value` | `quote_value` | `BigDecimal` / `numeric(12,4)` | no | Mayor o igual a 0. Es el precio del token en créditos. La columna no se llama `value` porque es palabra reservada en H2. |
| `score` | `score` | `BigDecimal` / `numeric(12,4)` | sí | Obligatorio si `status = CALCULATED`. |
| `status` | `status` | `QuoteStatus` / `varchar(32)` (`EnumType.STRING`) | no | Ver la sección 2. |
| `calculatedAt` | `calculated_at` | `Instant` / `timestamp with time zone` | no | Instante absoluto en UTC (FR-006). |
| `runId` | `run_id` | `UUID` / `uuid` | no | Id de la corrida de recálculo (feature 016). |
| `correlationId` | `correlation_id` | `String` / `varchar(64)` | no | Correlation id de la request o del job que originó la corrida. |

### Validaciones

- **Columnas obligatorias**: `nullable = false` y `@NotNull` de Bean Validation sobre jugador, código, versión, valor, estado, `calculatedAt`, `runId` y `correlationId` (FR-003).
- **Valores**: `@PositiveOrZero` sobre `value` (FR-004), y `@NotNull` + `@Min(1)` sobre `strategyVersion` (FR-003).
- **Score**: se exige en `@PrePersist`. Si `status == CALCULATED && score == null`, se lanza `IllegalStateException("score es obligatorio para CALCULATED")`.
- **Precisión**: `value` usa `numeric(12,4)`, es decir, 4 decimales sin redondeo (FR-004). Es más escala que `Player.marketValue`, que hoy no declara precisión y queda en `numeric(38,2)` por defecto.

### Índices

| Nombre | Columnas | Consultas que respalda |
|--------|----------|------------------------|
| `idx_player_quotes_player_strategy_time` | `(player_id, strategy_code, calculated_at)` | Última de un jugador, historial por rango, vigente en una fecha, y el `NOT EXISTS` de "última de todos". |
| `idx_player_quotes_strategy_time` | `(strategy_code, calculated_at)` | La consulta externa de "última de todos los jugadores" para una estrategia. |

Los dos se declaran con `@Table(indexes = ...)`, igual que `idx_players_external_id` en `Player` (FR-012, constitución §5.2).

### Relaciones

- `PlayerQuote` → `Player`: muchas a una, lazy. No hay relación inversa.
- `PlayerQuote` → estrategia: se referencia **por valor** con `strategyCode` y `strategyVersion`, no por FK, porque no existe tabla de estrategias (FR-017).

## 2. `QuoteStatus` (enum NUEVO, en `model`)

| Valor | Significado | `score` |
|-------|-------------|---------|
| `CALCULATED` | La cotización se calculó con datos suficientes. | Obligatorio |
| `NO_STATS` | El jugador no tenía estadísticas. | Opcional (normalmente nulo) |
| `INSUFFICIENT_HISTORY` | No había historial suficiente para la estrategia (por ejemplo, MOMENTUM con menos de `minHistory` cotizaciones previas). | Opcional |

No hay transiciones de estado: cada registro es definitivo y una nueva corrida crea registros nuevos.

## 3. `PlayerQuoteRepository` (repositorio NUEVO)

Es `interface PlayerQuoteRepository extends Repository<PlayerQuote, Long>`. Declara sólo lo que se lista abajo y no tiene `delete*` (FR-005).

| Método | Devuelve | Requisito |
|--------|----------|-----------|
| `save(PlayerQuote)`, `saveAll(Iterable<PlayerQuote>)`, `findById(Long)` | Lo que devuelve el método equivalente de Spring Data | Lo necesario para registrar y leer |
| `findFirstByPlayerIdAndStrategyCodeOrderByCalculatedAtDescIdDesc(Long, String)` | `Optional<PlayerQuote>` | FR-007, FR-011 |
| `findByPlayerIdAndStrategyCodeAndCalculatedAtBetweenOrderByCalculatedAtAscIdAsc(Long, String, Instant, Instant)` | `List<PlayerQuote>` | FR-008 (rango inclusivo) |
| `findFirstByPlayerIdAndStrategyCodeAndCalculatedAtLessThanEqualOrderByCalculatedAtDescIdDesc(Long, String, Instant)` | `Optional<PlayerQuote>` | FR-009, vigente en una fecha/hora exacta |
| `findFirstByPlayerIdAndStrategyCodeAndCalculatedAtLessThanOrderByCalculatedAtDescIdDesc(Long, String, Instant)` | `Optional<PlayerQuote>` | Soporte de `findEffectiveOn` |
| `default findEffectiveOn(Long, String, LocalDate)` | `Optional<PlayerQuote>` | Vigente al fin del día en UTC: delega en el método anterior con `date.plusDays(1).atStartOfDay(UTC)` (ver research D5) |
| `@Query findLatestForAllPlayers(String strategyCode)` | `List<PlayerQuote>` | FR-010, FR-011 |

La consulta de `findLatestForAllPlayers` es:

```text
select q from PlayerQuote q
where q.strategyCode = :code
  and not exists (select 1 from PlayerQuote q2
                  where q2.player = q.player and q2.strategyCode = q.strategyCode
                    and (q2.calculatedAt > q.calculatedAt
                         or (q2.calculatedAt = q.calculatedAt and q2.id > q.id)))
```

## 4. Tipos de estrategia (NUEVOS, en `service/valuation/strategy`)

No son entidades y no se persisten. El detalle está en [contracts/valuation-strategies.md](contracts/valuation-strategies.md).

| Tipo | Forma |
|------|-------|
| `StrategyType` | Enum `OFFICIAL`, `MOMENTUM` |
| `Metric` | Enum `GOALS, ASSISTS, SHOTS, KEY_PASSES, DRIBBLES, TACKLES, RATING, MINUTES, YELLOW_CARDS, RED_CARDS` |
| `MomentumParameters` | Record `(int window, int minHistory)` |
| `ValuationStrategyDefinition` | Record `(String code, int version, StrategyType type, BigDecimal baseValue, BigDecimal scaleFactor, BigDecimal maxVariation, Map<Position, Map<Metric, BigDecimal>> weights, MomentumParameters momentum)` |
| `OfficialStrategyV1`, `MomentumStrategyV1` | Clases `final` con una constante `DEFINITION` |
| `ValuationStrategyCatalog` | `@Component` con `all()`, `find(code)` y `active()` |

### Invariantes de `ValuationStrategyDefinition`

Se validan en el constructor compacto del record. Si alguna no se cumple, lanza `IllegalArgumentException` al cargar la clase, así que una definición inválida no compila en tiempo de ejecución.

1. `code` no está en blanco y `version >= 1`.
2. `baseValue > 0`, `scaleFactor > 0` y `0 < maxVariation <= 1` (FR-020).
3. `weights` tiene las 4 posiciones de `Position` (FR-015).
4. Todas las posiciones declaran el mismo conjunto de métricas, con ceros explícitos cuando corresponde.
5. `momentum != null` si y sólo si `type == MOMENTUM`.
6. Los mapas se copian con `Map.copyOf` y son inmutables.

## 5. Cambio aprobado a Entrega 1: `PlayerStats.dribbles` (bloque B)

| Campo | Columna | Tipo | Nulo | Regla |
|-------|---------|------|------|-------|
| `dribbles` | `dribbles` | `Integer` / `integer` | sí | Gambetas exitosas de la temporada según WhoScored (`dribbleWonTotal`). Si la fuente no lo informa, queda en `null` y nunca se completa con 0 (FR-033 a FR-035). |

Es el único cambio a un modelo existente, y sólo agrega una columna nullable. Las filas existentes quedan con `dribbles = null`.