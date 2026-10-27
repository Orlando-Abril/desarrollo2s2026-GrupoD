# Quickstart: Validación del scheduler de cotizaciones

## Prerequisites

- JDK 17 o compatible con el `pom.xml` del backend.
- PowerShell en Windows.
- Repositorio ubicado en su raíz.
- No se requieren PostgreSQL, Redis, WhoScored ni Football-Data para los tests unitarios y de contexto de esta feature.

## 1. Validar la suite específica

Desde `backend/`:

```powershell
Set-Location backend
.\mvnw.cmd "-Dtest=QuoteRecalculationSchedulerTest" test
```

Resultado esperado: build exitoso y todos los escenarios de `QuoteRecalculationSchedulerTest` aprobados.

La suite debe demostrar:

1. En éxito se llama una vez a `recalculate(Optional.empty())`, el servicio recibe un correlation id `valuation-<uuid>`, el log terminal contiene los contadores del resumen y el MDC queda limpio.
2. Ante una excepción inesperada, no se propaga, se registra `quote_scheduled_recalculation_failed` únicamente con `errorType` —sin mensaje ni stack trace— y el MDC queda limpio.
3. Ante `RecalculationInProgressException`, no se propaga, se registra `quote_scheduled_recalculation_skipped` con `recalculation_in_progress`, no se registra como falla y el MDC queda limpio.
4. Sin `valuation.schedule.enabled` y con `false`, no existen `QuoteSchedulingConfig`, `QuoteRecalculationScheduler` ni la infraestructura de scheduling aportada por esta configuración.
5. Con `valuation.schedule.enabled=true` y `whoscored.sync.enabled=false`, existen la configuración, el scheduler y el procesador de métodos programados, sin ejecutar el recálculo durante el arranque.
6. La anotación programada conserva `${valuation.schedule.cron:0 0 6 * * MON}` y `${valuation.schedule.zone:UTC}`.

## 2. Ejecutar la verificación completa del backend

Desde `backend/`:

```powershell
.\mvnw.cmd verify
```

Resultado esperado: build y suite completa en verde, incluido el reporte JaCoCo. Esta verificación confirma que el nuevo `@EnableScheduling` condicional no altera los tests existentes cuando la propiedad está ausente.

## 3. Revisar la superficie protegida

Desde la raíz del repositorio:

```powershell
git diff --name-only -- `
  backend/src/main/java/com/example/demo/config/SchedulingConfig.java `
  backend/src/main/java/com/example/demo/scheduler/PlayerStatsScheduler.java `
  ':(glob)**/application.properties'
```

Resultado esperado: salida vacía.

## 4. Validar el contrato de configuración

Los nombres, defaults, matriz de independencia y eventos esperados están en [contracts/scheduler-contract.md](contracts/scheduler-contract.md). No se deben agregar las propiedades a ningún archivo `.properties`; para una ejecución manual local se suministran externamente, por ejemplo mediante argumentos de arranque.

## Acceptance

La fase de implementación queda validada cuando:

- la suite específica y `mvn verify` finalizan exitosamente;
- no hay cambios en archivos protegidos;
- el bean está ausente por defecto y presente con valuación habilitada aunque WhoScored esté apagado;
- éxito, omisión y falla producen exactamente un evento terminal y dejan el MDC limpio;
- no se agregan endpoints, persistencia ni accesos a fuentes externas.
