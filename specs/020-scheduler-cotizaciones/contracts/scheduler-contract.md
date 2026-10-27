# Contract: Scheduler de recálculo de cotizaciones

## Configuration

| Property | Required | Default | Contract |
|----------|----------|---------|----------|
| `valuation.schedule.enabled` | No | `false` | Con `true`, crea la configuración y el scheduler; ausente, `false` o cualquier valor distinto de `true` no los crea. |
| `valuation.schedule.cron` | No | `0 0 6 * * MON` | Expresión cron de seis campos que determina el disparo. |
| `valuation.schedule.zone` | No | `UTC` | Zona aplicada a la evaluación del cron. |

Los defaults se expresan únicamente en las anotaciones de las clases nuevas. Ninguna de estas claves se agrega a `application.properties` de producción o test.

La activación no depende de `whoscored.sync.enabled`. Las combinaciones obligatorias son:

| `valuation.schedule.enabled` | `whoscored.sync.enabled` | Quote scheduler | WhoScored scheduler |
|------------------------------|--------------------------|-----------------|---------------------|
| ausente / `false` | ausente / `false` | ausente | ausente |
| `true` | ausente / `false` | presente | ausente |
| ausente / `false` | `true` | ausente | presente |
| `true` | `true` | presente | presente |

## Scheduled invocation

En cada disparo, el scheduler invoca:

```text
QuoteCalculationService.recalculate(Optional.empty())
```

`Optional.empty()` significa todas las estrategias. No existe selección de estrategia, jugador o liga en este contrato.

## Observability events

Todos los eventos se emiten con `correlationId=valuation-<uuid>` en el MDC.

### Start

```text
quote_scheduled_recalculation_started
```

Se emite exactamente una vez antes de delegar.

### Completed

```text
quote_scheduled_recalculation_finished runId={} calculated={} skipped={} processed={} failed={} activeStrategy={} marketValueUpdated={}
```

Se emite exactamente una vez cuando el servicio devuelve `QuoteRunResult`. Los placeholders mantienen los valores del resumen sin recalcularlos.

### Skipped

```text
quote_scheduled_recalculation_skipped reason=recalculation_in_progress
```

Se emite a nivel WARN cuando el servicio lanza `RecalculationInProgressException`. No se emite evento de falla ni se reintenta.

### Failed

```text
quote_scheduled_recalculation_failed errorType={}
```

Se emite a nivel ERROR para cualquier otra `RuntimeException`. El único detalle registrado es `errorType`: no se incluyen mensaje, stack trace, datos sensibles ni contadores parciales. La excepción no se propaga.

## MDC postcondition

Después de `run()`, tanto en éxito como en omisión o falla:

```text
MDC.get(CorrelationIdFilter.MDC_KEY) == null
```

## Protected surface

Este contrato se implementa sin modificar:

- `backend/src/main/java/com/example/demo/config/SchedulingConfig.java`
- `backend/src/main/java/com/example/demo/scheduler/PlayerStatsScheduler.java`
- cualquier archivo `application.properties`
- `QuoteCalculationService` y su contrato manual existente
