# Data Model: Comparador de estrategias de valuación

## Entidad persistida nueva

### ActiveStrategySetting (`active_strategy_settings`)

Fila única con `id = 1`. Se crea al primer cambio de estrategia o al primer recálculo que fija precios (research R2/R4).

| Campo | Columna | Tipo | Nulo | Regla |
|-------|---------|------|------|-------|
| id | `id` | `Short` | no | Siempre `1`; sin generador |
| activeCode | `active_code` | `varchar(32)` | sí | Nulo = usar `valuation.active-strategy`. Si tiene valor, debe existir en el catálogo; se valida al arrancar y en el `PUT` |
| changedAt | `changed_at` | `timestamptz` | sí | Instante del último cambio efectivo |
| changedByUserId | `changed_by_user_id` | `bigint` | sí | Nulo si nunca hubo cambio o si lo hizo una API key de servicio |
| changedByActorType | `changed_by_actor_type` | `varchar(16)` | sí | `USER` / `SYSTEM` (`AuditActorType`) |
| marketStrategyCode | `market_strategy_code` | `varchar(32)` | sí | Estrategia del último recálculo que actualizó `marketValue` |
| marketStrategyAppliedAt | `market_strategy_applied_at` | `timestamptz` | sí | `calculatedAt` de esa corrida |

**Derivados (no persistidos)**:
- `active` = `activeCode` ?? propiedad.
- `alternative` = la única definición del catálogo distinta de `active`.
- `marketStrategy` = `marketStrategyCode` ?? `active`.
- `pendingRecalculation` = `marketStrategy ≠ active`.

**Transiciones**:
- `PUT` con un código distinto de `active` → `activeCode`, `changedAt` y `changedBy*` se actualizan, y se audita.
- `PUT` con el mismo código → sin cambios.
- Recálculo con `marketValueUpdated` → `marketStrategyCode` y `marketStrategyAppliedAt` se actualizan. Si `marketStrategyCode` cambió (incluido de nulo a un código), se audita una vez por corrida con `MARKET_STRATEGY_APPLIED`. Si no cambió, sólo se actualiza `marketStrategyAppliedAt`, sin auditar. El `marketValue` de cada jugador sigue sin auditarse, como en la 016.

## Cambios a entidades existentes

- **AuditAction**: nuevos valores (ver el riesgo del CHECK en research R10):
  - `ACTIVE_STRATEGY_CHANGED`: `entityType = "VALUATION_STRATEGY"`, `entityId = "active"`, actor usuario o sistema; `before`/`after` son `{strategy, version}`.
  - `MARKET_STRATEGY_APPLIED` (24 caracteres; la columna admite 32): actor `AuditActor.system()`, `entityType = "VALUATION_STRATEGY"`, `entityId = "market"`; `before` = `{strategy, version}` anterior o nulo si no había; `after` = `{strategy, version, runId}`; correlation id de la corrida.
- **PlayerQuote**, **Player**, **AuditLog**: sin cambios de esquema.

## Proyecciones / DTOs (no persistidos)

### StrategyRef
`code` (String), `version` (int), `label` (String, p. ej. `"MOMENTUM v1"`).

### ActiveStrategyResponse (`GET` y `PUT /strategies/active`)
| Campo | Tipo | Notas |
|-------|------|-------|
| active | StrategyRef | Estrategia que usará el próximo recálculo |
| alternative | StrategyRef | La otra del catálogo |
| marketStrategy | StrategyRef | Estrategia que fijó el `marketValue` vigente |
| pendingRecalculation | boolean | `marketStrategy ≠ active` |
| changedAt | Instant? | Nulo si nunca hubo cambio |
| changedBy | `{ actorType, userId?, username? }`? | Nulo si nunca hubo cambio |
| changed | boolean? | Sólo en la respuesta del `PUT`: `false` si ya estaba activa |

### ChangeActiveStrategyRequest
`strategy` (String, `@NotBlank`). Se recorta igual que la propiedad (`trim`) y se valida con `catalog.find`.

### PositionStrategyValuationResponse (dentro de cada posición, sólo con `?strategy`)
| Campo | Tipo | Regla |
|-------|------|-------|
| quotePrice | BigDecimal(4)? | Última cotización de la estrategia, cualquier estado |
| quoteStatus | QuoteStatus? | |
| quoteCalculatedAt | Instant? | |
| value | BigDecimal(4)? | `quantity × quotePrice` |
| profitLoss | BigDecimal(4)? | `value − costBasis` |

Todos los campos son nulos juntos cuando no hay cotización. El constructor canónico valida `profitLoss = value − costBasis`.

### PortfolioStrategyValuationResponse (en `PortfolioResponse`, sólo con `?strategy`)
| Campo | Tipo | Regla |
|-------|------|-------|
| strategy | StrategyRef | |
| totalValue | BigDecimal(4)? | Suma exacta de `value`; nulo si `positionsWithoutQuote > 0` |
| totalProfitLoss | BigDecimal(4)? | `totalValue − totalCost` |
| totalEquity | BigDecimal(4)? | `balance + totalValue` |
| positionsWithoutQuote | int ≥ 0 | |

`PortfolioResponse` y `PortfolioPositionResponse` agregan `strategyValuation` como último componente, anulable y omitido si es nulo (R5).

### LatestQuotesResponse (`GET /quotes/latest`)
`strategy`: StrategyRef. `items`: lista en el orden pedido, sin duplicados ni inexistentes, de `{ playerId, quote: { value, status, calculatedAt, strategyVersion } | null }`.

### OpportunitiesResponse (`GET /quotes/opportunities`)
`activeStrategy` (StrategyRef, la activa configurada), `alternativeStrategy` (StrategyRef, la otra), `marketStrategy` (StrategyRef, la que fijó `marketValue`; su estado se usa para excluir, ver R7), `pendingRecalculation` (boolean), `undervalued[]`, `overvalued[]`.

`OpportunityItem`: `playerId`, `name`, `team`, `league`, `positions` (lista), `marketValue` (4), `alternativeQuote` (4), `alternativeQuoteCalculatedAt`, `difference` (4), `differencePercent` (2).

**Reglas de validación**:
- `limit`: 1..50, por defecto 10.
- `league` y `position`: mismos enums que el ranking.
- Estados excluidos y orden: research R7.
