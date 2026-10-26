# Implementation Plan: Recálculo semanal de cotizaciones

**Branch**: `feature/scheduler-cotizaciones` | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/020-scheduler-cotizaciones/spec.md`

## Summary

Agregar un scheduler semanal de valuación, apagado por defecto e independiente del scheduler de WhoScored. Una nueva configuración condicional habilitará la infraestructura de scheduling cuando `valuation.schedule.enabled=true`, y un nuevo componente programado delegará en `QuoteCalculationService.recalculate(Optional.empty())` con cron `0 0 6 * * MON` y zona `UTC` como defaults embebidos en las anotaciones. Cada intento usará un correlation id `valuation-<uuid>`, emitirá un inicio y exactamente un resultado terminal (completado, salteado o fallido), absorberá las excepciones de ejecución y limpiará el MDC. No se modificarán archivos de Entrega 1 ni archivos de propiedades.

## Technical Context

**Language/Version**: Java 17

**Primary Dependencies**: Spring Boot 4.1.1; Spring scheduling (`@EnableScheduling`, `@Scheduled`); Spring Boot conditional configuration; SLF4J/MDC

**Storage**: Sin almacenamiento nuevo; reutiliza PostgreSQL y el historial de cotizaciones administrado por `QuoteCalculationService`

**Testing**: JUnit 5, Mockito, AssertJ, Spring Boot `ApplicationContextRunner` y `OutputCaptureExtension`; Maven Wrapper

**Target Platform**: Backend Spring Boot ejecutado en JVM, despliegue de una sola instancia

**Project Type**: Aplicación web con backend Java y frontend separado; alcance exclusivo en backend

**Performance Goals**: El disparador agrega trabajo constante alrededor de la corrida existente; si hay una corrida en curso, finaliza inmediatamente sin espera ni reintento

**Constraints**: Scheduler deshabilitado por defecto; cron semanal lunes 06:00 UTC; independiente de `whoscored.sync.enabled`; ninguna excepción de ejecución escapa; un correlation id nuevo por disparo; cero cambios en `SchedulingConfig`, `PlayerStatsScheduler` o cualquier `application.properties`

**Scale/Scope**: Una instancia, un disparo semanal, dos clases productivas nuevas y una suite de tests del scheduler; sin endpoints, tablas, migraciones ni cambios al servicio de recálculo

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

### Pre-design gate

| Principle | Status | Evidence |
|-----------|--------|----------|
| §1.2 Motor de valuación versionado | PASS | El scheduler delega en la corrida existente, que conserva estrategia, versión e historial; no altera el cálculo. |
| §1.3 Resiliencia y desacoplamiento externo | PASS | El job funciona con WhoScored apagado y no incorpora llamadas a fuentes externas. |
| §2.1 Backend en capas | PASS | Configuración y disparador quedan en `config` y `scheduler`; la lógica de negocio continúa en `QuoteCalculationService`. |
| §2.3 Procesos batch y asincronía | PASS | Implementa explícitamente el recálculo semanal y reutiliza la misma operación del recálculo manual. |
| §3.1 CI | PASS | Se agregan tests ejecutables por el `mvn verify` existente; no se modifica el workflow. |
| §3.2 SonarCloud | PASS | Diseño acotado, sin duplicar la lógica de cálculo; reutiliza el servicio y el patrón de scheduler existente. |
| §3.3 Pruebas de dominio | PASS | Se cubren éxito, excepción, concurrencia, MDC, logs y activación condicional; los escenarios financieros existentes no cambian. |
| §5.2 Caché e índices | N/A | La feature no agrega lecturas públicas, consultas ni almacenamiento. |
| §5.3 Observabilidad | PASS | Correlation id propio, logs estructurados de inicio/resultado y contadores del resumen; la corrida existente registra duración. |
| §6.1 OpenAPI | N/A | No se agrega ni modifica un endpoint. |
| §7 Definition of Done | PASS | El diseño conserva capas, contempla tests y resiliencia, y mantiene la trazabilidad de las cotizaciones. |

No hay violaciones constitucionales ni excepciones que justificar.

### Post-design gate

**PASS**. `research.md` fija una integración sin fuentes externas ni lógica duplicada; `data-model.md` no introduce persistencia; `contracts/scheduler-contract.md` hace verificables configuración, estados y logs; `quickstart.md` valida los escenarios constitucionales §2.3 y §5.3. No se agregaron decisiones que incumplan la constitución.

## Project Structure

### Documentation (this feature)

```text
specs/020-scheduler-cotizaciones/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── scheduler-contract.md
├── checklists/
│   └── requirements.md
└── tasks.md                    # futuro output de $speckit-tasks; no creado aquí
```

### Source Code (repository root)

```text
backend/
├── pom.xml
├── mvnw
├── mvnw.cmd
└── src/
    ├── main/java/com/example/demo/
    │   ├── config/
    │   │   ├── SchedulingConfig.java                  # protegido, sin cambios
    │   │   └── QuoteSchedulingConfig.java             # nuevo
    │   ├── scheduler/
    │   │   ├── PlayerStatsScheduler.java              # protegido, sin cambios
    │   │   └── QuoteRecalculationScheduler.java       # nuevo
    │   ├── filter/
    │   │   └── CorrelationIdFilter.java               # existente, sólo se reutiliza MDC_KEY
    │   ├── exception/
    │   │   └── RecalculationInProgressException.java  # existente
    │   └── service/valuation/
    │       ├── QuoteCalculationService.java           # existente, sin cambios
    │       └── QuoteRunResult.java                     # existente
    └── test/java/com/example/demo/scheduler/
        └── QuoteRecalculationSchedulerTest.java        # nuevo
```

**Structure Decision**: Se mantiene el backend Spring Boot existente. La activación de infraestructura pertenece a `config`; el adaptador temporal que dispara el caso de uso pertenece a `scheduler`; la valuación permanece en `service/valuation`. No hay cambios de frontend ni recursos.

## Design

### `QuoteSchedulingConfig`

- Clase nueva en `com.example.demo.config`.
- Anotaciones: `@Configuration`, `@EnableScheduling` y `@ConditionalOnProperty(name = "valuation.schedule.enabled", havingValue = "true")`.
- No declara propiedades, beans de dominio ni dependencias.
- Su única responsabilidad es habilitar el procesamiento de `@Scheduled` cuando la valuación automática está habilitada, independientemente de `SchedulingConfig` y `whoscored.sync.enabled`.

### `QuoteRecalculationScheduler`

- Clase nueva en `com.example.demo.scheduler`, registrada como componente y protegida por la misma condición `valuation.schedule.enabled=true`. La condición duplicada es deliberada: `@EnableScheduling` controla la infraestructura, mientras la condición del componente evita que el escaneo cree el bean con la propiedad apagada.
- Recibe únicamente `QuoteCalculationService` por constructor.
- Método público `run()` anotado con:
  - `cron = "${valuation.schedule.cron:0 0 6 * * MON}"`
  - `zone = "${valuation.schedule.zone:UTC}"`
- En cada llamada genera `valuation-` + `UUID.randomUUID()`, lo coloca bajo `CorrelationIdFilter.MDC_KEY` y emite `quote_scheduled_recalculation_started`.
- Delega exactamente una vez en `recalculate(Optional.empty())`.
- Si obtiene `QuoteRunResult`, emite `quote_scheduled_recalculation_finished` con `runId`, `calculated`, `skipped`, `playersProcessed`, `playersFailed`, `activeStrategy` y `marketValueUpdated`.
- Si recibe `RecalculationInProgressException`, emite `quote_scheduled_recalculation_skipped` a nivel WARN con motivo `recalculation_in_progress`; no reintenta ni la registra como falla.
- Si recibe otra `RuntimeException`, emite `quote_scheduled_recalculation_failed` a nivel ERROR únicamente con `errorType`, sin mensaje ni stack trace; no la propaga.
- Un bloque `finally` elimina `CorrelationIdFilter.MDC_KEY` en los tres caminos. Cada intento produce un inicio y exactamente uno de los tres eventos terminales.

### Tests

Una única clase `QuoteRecalculationSchedulerTest` combina pruebas unitarias y de contexto siguiendo el patrón de `PlayerStatsSchedulerTest`:

- Mockito verifica que éxito, corrida en curso y error invoquen `recalculate(Optional.empty())` exactamente una vez.
- `QuoteRunResult` de prueba incluye estrategias calculadas/salteadas y conteos no triviales para verificar todos los campos del log exitoso.
- `OutputCaptureExtension` verifica los cuatro nombres de evento, los contadores, el motivo de omisión y que la falla contenga únicamente el tipo de error —sin mensaje ni stack trace—; también comprueba que cada escenario tenga sólo el terminal esperado.
- La invocación del servicio captura el MDC y valida `valuation-<UUID>`; después de éxito, omisión y falla se comprueba que `CorrelationIdFilter.MDC_KEY` sea nulo.
- AssertJ comprueba que las excepciones no se propaguen.
- `ApplicationContextRunner`, con el servicio como bean mock y las dos clases nuevas como configuración de usuario, verifica ausencia por defecto, ausencia con `valuation.schedule.enabled=false` y presencia con `valuation.schedule.enabled=true` junto al procesador de `@Scheduled`.
- El escenario habilitado fija `whoscored.sync.enabled=false`, verifica que el scheduler de cotizaciones existe y que no ejecuta el servicio al iniciar el contexto.
- La metadata de `@Scheduled` se inspecciona para asegurar los placeholders y defaults exactos, sin depender de archivos de propiedades ni esperar al reloj real. Contextos adicionales verifican que cron y zona personalizados válidos inicialicen correctamente y que cada valor inválido produzca un fallo visible de inicialización sin fallback.

## Complexity Tracking

No aplica: el Constitution Check no detectó violaciones.
