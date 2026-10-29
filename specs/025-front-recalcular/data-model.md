# Data Model: Recálculo manual desde Ranking

La feature no agrega persistencia. Este documento describe el contrato remoto y los estados efímeros de la interfaz.

## RecalculateRequest

| Field | Type | Rules |
|-------|------|-------|
| `strategy` | string \| null \| absent | Código exacto cuando se pide una estrategia. Ausente o null significa todas. Ranking lo omite siempre. |

## QuoteRunSummary

| Field | Type | Rules |
|-------|------|-------|
| `runId` | UUID string | Identificador obligatorio de la corrida. Se muestra completo y puede envolver. |
| `calculatedAt` | date-time string | Instante informado por backend; no determina la recarga. |
| `requestedStrategy` | string \| null | Null en el flujo completo de esta feature. |
| `strategies` | `StrategyRunCounts[]` | Puede ser vacío; se muestra como `Ninguna`. |
| `skippedStrategies` | `SkippedStrategy[]` | Puede ser vacío; se muestra como `Ninguna`. |
| `playersProcessed` | integer | Mayor o igual a 0; siempre visible, incluso 0. |
| `playersFailed` | integer | Mayor o igual a 0; siempre visible, incluso 0. |
| `activeStrategy` | string | Estrategia activa informada por backend. |
| `marketValueUpdated` | boolean | Se presenta como Sí/No, nunca por truthiness implícita. |

## StrategyRunCounts

| Field | Type | Rules |
|-------|------|-------|
| `code` | string | Código de estrategia. |
| `version` | integer | Mayor o igual a 1. |
| `calculated` | integer | Conteo `CALCULATED`, mayor o igual a 0. |
| `noStats` | integer | Conteo `NO_STATS`, mayor o igual a 0. |
| `insufficientHistory` | integer | Conteo `INSUFFICIENT_HISTORY`, mayor o igual a 0. |

## SkippedStrategy

| Field | Type | Rules |
|-------|------|-------|
| `code` | string | Código de estrategia salteada. |
| `version` | integer | Mayor o igual a 1. |
| `reason` | string | Motivo contractual; actualmente `no_algorithm`. Se muestra sin omitirlo. |

## RecalculationViewState

| Field | Type | Initial | Meaning |
|-------|------|---------|---------|
| `running` | boolean | `false` | Hay un único POST vigente; deshabilita el Button y activa `aria-busy`. |
| `notification` | `Notification \| null` | `null` | Resultado terminal visible en Toast. |
| `forbidden` | boolean | Derivado del token vigente | Oculta el Button, pero no el Toast ni el contenedor. |
| `inFlightRef` | boolean | `false` | Guardia sincrónica contra doble activación antes del render. |
| `controller` | AbortController \| null | `null` | Permite abortar/ignorar continuaciones al desmontar. |

### Notification

| Type | Payload | Ranking reload | Persistence |
|------|---------|----------------|-------------|
| `success` | `QuoteRunSummary` | Exactamente una vez | Hasta cierre manual para permitir leer el resumen. |
| `in_progress` | Texto exacto de 409 | Nunca | Toast breve descartable. |
| `forbidden` | Mensaje de falta de permiso | Nunca | Toast breve; Button queda oculto. |
| `network_error` | Mensaje genérico | Nunca | Toast breve descartable. |
| `unexpected_error` | Mensaje genérico | Nunca | Toast breve descartable. |

### State transitions

```text
idle + cancel confirmation --------------------------> idle
idle + confirm --------------------------------------> running
running + repeated activation -----------------------> running (sin segundo POST)
running + 200 ----------------------------------------> success notification + ranking.reload
running + 409 ----------------------------------------> in_progress notification
running + 403 ----------------------------------------> forbidden notification + hidden action
running + network/other error ------------------------> error notification
running + unmount/own abort --------------------------> no visible transition/callback
forbidden + remount with same token -----------------> forbidden
forbidden + new token --------------------------------> idle/action visible
```

Toda transición terminal libera `running` y la guardia de vuelo. Sólo el éxito invoca la recarga.

## DeniedSessionMarker

| Field | Type | Rules |
|-------|------|-------|
| `lastDeniedToken` | opaque string \| null | Vive sólo en memoria del módulo. No se muestra, serializa, registra ni persiste. |

La marca no concede ni revoca permisos: sólo evita volver a ofrecer una acción que el backend ya rechazó para ese token. El backend sigue siendo la autoridad.

## RankingReload

| Field | Type | Rules |
|-------|------|-------|
| `reloadKey` | integer | Incrementa para repetir el GET vigente. |
| `filters` | `{ strategy, league, position }` | Se conservan sin cambios. |
| `page` | integer UI 1-based | Se conserva; si deja de existir, se aplica la corrección actual a la última página válida. |
| `status` | `loading \| ready \| error` | `reload()` pasa a loading y limpia el error anterior. |

La recarga toma los criterios en el momento en que se dispara, no los capturados al comenzar el POST.
