# Data Model: Recálculo semanal de cotizaciones

## Persistencia

Esta feature no agrega tablas, documentos, cachés ni migraciones. Las cotizaciones, snapshots y precios de mercado continúan siendo responsabilidad de `QuoteCalculationService` y de las entidades existentes.

## Modelos conceptuales

### Configuración del scheduler de valuación

Representa el contrato de activación y calendario consumido al inicializar la aplicación.

| Campo | Tipo conceptual | Default | Validación / efecto |
|-------|-----------------|---------|---------------------|
| `enabled` | booleano | `false` por ausencia | Sólo `true` crea la configuración y el scheduler. |
| `cron` | expresión cron de seis campos | `0 0 6 * * MON` | Debe ser válida; un valor inválido falla visiblemente al inicializar scheduling. |
| `zone` | identificador de zona horaria | `UTC` | Debe ser válido; un valor inválido no se sustituye silenciosamente. |

No se persiste. Sus claves externas se documentan en [contracts/scheduler-contract.md](contracts/scheduler-contract.md).

### Intento automático de valuación

Representa una invocación del método programado; existe sólo durante su ejecución.

| Campo | Tipo | Reglas |
|-------|------|--------|
| `correlationId` | string | Obligatorio, único por intento, formato `valuation-<uuid>`. |
| `result` | enum | `STARTED`, luego exactamente uno de `COMPLETED`, `SKIPPED_IN_PROGRESS`, `FAILED`. |
| `runResult` | `QuoteRunResult` existente | Presente sólo en `COMPLETED`. |
| `errorType` | string | Presente sólo en `FAILED`; nombre del tipo de excepción y único detalle de error permitido. No se registran mensaje ni stack trace. |
| `skipReason` | string | Presente sólo en `SKIPPED_IN_PROGRESS`; valor `recalculation_in_progress`. |

### Resumen de corrida reutilizado

No se redefine ni persiste por esta feature. El scheduler consume los siguientes campos de `QuoteRunResult` para el log terminal exitoso:

- `runId`
- `calculatedAt` (disponible, aunque no es necesario duplicarlo en el log del scheduler)
- `calculated`, incluidos código, versión y conteos `calculated`, `noStats`, `insufficientHistory`
- `skipped`, incluidos código, versión y motivo
- `playersProcessed`
- `playersFailed`
- `activeStrategy`
- `marketValueUpdated`

## State transitions

```text
STARTED ── resultado normal ───────────────> COMPLETED
   │
   ├── RecalculationInProgressException ──> SKIPPED_IN_PROGRESS
   │
   └── otra RuntimeException ──────────────> FAILED
```

Reglas invariantes:

- Cada intento entra una vez en `STARTED` y exactamente una vez en un estado terminal.
- Ningún estado terminal vuelve a invocar el servicio ni programa un reintento.
- El correlation id es el mismo durante toda la transición.
- Después de cualquier estado terminal, `CorrelationIdFilter.MDC_KEY` queda ausente.
- Sólo `COMPLETED` tiene contadores del resumen; `FAILED` no publica contadores parciales.
