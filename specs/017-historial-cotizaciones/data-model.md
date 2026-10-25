# Data Model: Historial de cotizaciones de un jugador

**Feature**: 017-historial-cotizaciones  
**Date**: 2026-10-24

## Overview

Esta feature no agrega tablas, columnas, asociaciones ni migraciones. Expone como lectura modelos persistentes creados previamente y agrega una representación HTTP. `PlayerQuote` sigue siendo inmutable y sólo la feature de recálculo crea registros.

## Existing Persistent Entities

### Player

Representa el jugador cuyo historial se consulta.

| Field used | Type | Rule in this feature |
|------------|------|----------------------|
| `id` | Long | Path parameter; debe ser mayor a cero. Un valor no positivo produce 400 `validation_error`; un valor positivo sin registro produce 404 `player_not_found`. |

La verificación usa sólo existencia. No se modifica ni serializa el jugador en la respuesta.

### PlayerQuote

Registro histórico inmutable ya creado por 013.

| Field | Type | API mapping | Validation/semantics |
|-------|------|-------------|----------------------|
| `id` | Long | no expuesto | Desempate: ascendente en historial, descendente para vigencia. |
| `player` | Player | no expuesto | Debe corresponder al `id` del path. |
| `strategyCode` | String | `strategy` | Coincidencia exacta con el catálogo; incluye todas las versiones del código. |
| `strategyVersion` | Integer | `version` | Entero positivo; identifica la definición que produjo el registro. |
| `value` | BigDecimal(12,4) | `value` | No negativo, sin pérdida de precisión. |
| `score` | BigDecimal(12,4), nullable | `score` | Obligatorio para `CALCULATED`; puede ser nulo para otros estados. |
| `status` | QuoteStatus | `status` | `CALCULATED`, `NO_STATS` o `INSUFFICIENT_HISTORY`. |
| `calculatedAt` | Instant | `calculatedAt` | Instante absoluto serializado en ISO 8601 UTC. |
| `runId` | UUID | no expuesto | Sigue disponible para auditoría interna. |
| `correlationId` | String | no expuesto | Sigue disponible para trazabilidad interna. |

Índice existente utilizado: `idx_player_quotes_player_strategy_time (player_id, strategy_code, calculated_at)`.

## New API Model

### PlayerQuoteResponse

Record de salida, sin identidad ni comportamiento persistente.

| Field | Type | Required | Example | Source |
|-------|------|----------|---------|--------|
| `value` | BigDecimal | yes | `1.2450` | `PlayerQuote.value` |
| `score` | BigDecimal / null | yes, nullable | `8.7500` | `PlayerQuote.score` |
| `status` | QuoteStatus | yes | `CALCULATED` | `PlayerQuote.status` |
| `strategy` | String | yes | `OFFICIAL` | `PlayerQuote.strategyCode` |
| `version` | Integer | yes | `1` | `PlayerQuote.strategyVersion` |
| `calculatedAt` | Instant | yes | `2026-10-20T18:00:00Z` | `PlayerQuote.calculatedAt` |

Aunque `score` sea nulo, el campo permanece en el JSON para conservar un shape estable entre estados.

## Query Value Objects

No se crean clases persistentes para los filtros. El controller liga los valores y el servicio aplica estas reglas:

### History interval

| Input | Meaning |
|-------|---------|
| `from` ausente | Sin límite inferior de dominio. |
| `from=D` | `calculatedAt >= D 00:00:00Z`. |
| `to` ausente | Sin límite superior de dominio. |
| `to=D` | `calculatedAt < (D + 1 día) 00:00:00Z`. |
| `from > to` | Query inválida, 400. |

El resultado se ordena por `(calculatedAt ASC, id ASC)`.

### Effective date

`at=D` selecciona el máximo `(calculatedAt, id)` que cumpla `calculatedAt < (D + 1 día) 00:00:00Z` para el jugador y estrategia.

`at` no puede coexistir con `from` o `to`.

## Strategy Resolution

1. `strategy` ausente → `ValuationStrategyCatalog.active().code()`.
2. `strategy` presente → búsqueda exacta en el catálogo.
3. Código vacío, con distinto case o desconocido → `UnknownValuationStrategyException` → 400 `unknown_strategy`.

El filtro agrupa todas las versiones históricas del mismo código; cada respuesta conserva su versión concreta.

## Error Models

Las excepciones no se persisten y se proyectan al `ErrorResponse` existente:

| Condition | Exception | HTTP | `error` |
|-----------|-----------|------|---------|
| Id de jugador menor o igual a cero | `InvalidQuoteQueryException` | 400 | `validation_error` |
| Id positivo de jugador inexistente | `PlayerNotFoundException` | 404 | `player_not_found` |
| Sin cotización vigente hasta `at` | `QuoteNotFoundException` | 404 | `quote_not_found` |
| Rango invertido o parámetros incompatibles | `InvalidQuoteQueryException` | 400 | `validation_error` |
| Fecha/id con formato inválido | `MethodArgumentTypeMismatchException` | 400 | `validation_error` |
| Estrategia desconocida | `UnknownValuationStrategyException` | 400 | `unknown_strategy` |

## State Transitions

No hay transiciones de estado. Todas las operaciones son lecturas y no alteran `Player`, `PlayerQuote`, estrategia activa ni precio de mercado.
