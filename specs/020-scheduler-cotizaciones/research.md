# Research: Recálculo semanal de cotizaciones

## R1. Activación independiente del scheduling

**Decision**: Crear `QuoteSchedulingConfig` con `@Configuration`, `@EnableScheduling` y condición `valuation.schedule.enabled=true`, sin modificar `SchedulingConfig`.

**Rationale**: La configuración actual sólo habilita scheduling cuando WhoScored está activo. Una configuración separada permite habilitar la valuación con WhoScored apagado y respeta la protección de los archivos de Entrega 1. Si ambos procesos están activos, habilitar scheduling desde dos configuraciones sigue representando la misma infraestructura de procesamiento de métodos programados.

**Alternatives considered**:

- Ampliar la condición de `SchedulingConfig`: rechazado porque el archivo está protegido y acoplaría ambas features.
- Habilitar scheduling globalmente en la aplicación: rechazado porque cambiaría el comportamiento por defecto y excede el alcance.

## R2. Existencia condicional del bean

**Decision**: Aplicar `@ConditionalOnProperty(name = "valuation.schedule.enabled", havingValue = "true")` tanto a `QuoteSchedulingConfig` como a `QuoteRecalculationScheduler`.

**Rationale**: Condicionar sólo la configuración evita habilitar la infraestructura, pero no impide que el escaneo de componentes cree el scheduler. La doble condición cumple el requisito verificable de que el bean no exista con la propiedad ausente o apagada.

**Alternatives considered**:

- Declarar el scheduler mediante un método `@Bean` dentro de la configuración: viable, pero separa el componente del patrón ya usado por `PlayerStatsScheduler` y hace menos directa su prueba unitaria.
- Condicionar sólo el scheduler: evita el bean, pero no aporta la infraestructura independiente de `@EnableScheduling` cuando está habilitado.

## R3. Contrato de propiedades y defaults

**Decision**: Usar `valuation.schedule.enabled`, `valuation.schedule.cron` y `valuation.schedule.zone`. Los defaults de cron y zona viven exclusivamente en `@Scheduled` como `${valuation.schedule.cron:0 0 6 * * MON}` y `${valuation.schedule.zone:UTC}`; la habilitación queda apagada por ausencia gracias a `@ConditionalOnProperty` sin `matchIfMissing=true`.

**Rationale**: Es el contrato solicitado, no requiere modificar ningún archivo de propiedades y conserva un único lugar para cada default. La expresión de seis campos corresponde a lunes 06:00:00.

**Alternatives considered**:

- La convención anterior de nombres: reemplazada por la convención explícita más reciente `valuation.schedule.*`.
- Declarar defaults en `application.properties`: rechazado por la restricción de Entrega 1 y porque haría que tests carguen una activación global accidental.
- Crear una clase de propiedades dedicada: innecesaria para tres valores consumidos directamente por anotaciones.

## R4. Delegación y concurrencia

**Decision**: Invocar `QuoteCalculationService.recalculate(Optional.empty())` exactamente una vez por disparo y tratar `RecalculationInProgressException` como resultado salteado.

**Rationale**: `Optional.empty()` ya significa “todas las estrategias”. El servicio existente posee la exclusión mutua y distingue la concurrencia con una excepción específica, por lo que el scheduler no debe duplicar locks ni consultar estado previamente.

**Alternatives considered**:

- Agregar un lock en el scheduler: rechazado porque no coordinaría con corridas manuales y duplicaría reglas.
- Reintentar o encolar: rechazado por alcance; podría ejecutar cotizaciones fuera de la ventana esperada.

## R5. Correlation id y ciclo de vida del MDC

**Decision**: Generar `valuation-<uuid>` antes del log inicial, colocarlo en `CorrelationIdFilter.MDC_KEY` y removerlo incondicionalmente en `finally`.

**Rationale**: Replica el patrón operativo de `PlayerStatsScheduler`, hace que los logs internos y las cotizaciones de la corrida compartan el identificador y garantiza que el hilo reutilizado no contamine ejecuciones posteriores.

**Alternatives considered**:

- Dejar que `QuoteCalculationService` genere un UUID sin prefijo: rechazado porque no satisface la identificación propia del job.
- Restaurar un valor previo: no es el patrón solicitado; el contrato de esta feature exige MDC limpio al terminar.

## R6. Eventos y datos de observabilidad

**Decision**: Emitir `quote_scheduled_recalculation_started` y exactamente uno entre `quote_scheduled_recalculation_finished`, `quote_scheduled_recalculation_skipped` y `quote_scheduled_recalculation_failed`.

**Rationale**: Los nombres estables permiten pruebas con captura de salida y operación clara. El éxito reutiliza todos los campos relevantes de `QuoteRunResult`; la omisión informa el motivo conocido; la falla informa únicamente `errorType`, sin mensaje, stack trace ni contadores parciales que puedan exponer datos sensibles.

**Alternatives considered**:

- Reutilizar únicamente los logs internos del servicio: insuficiente para observar intentos salteados, porque el servicio rechaza concurrencia antes de crear su propia corrida.
- Un único evento terminal genérico: menos claro para alertas y requiere interpretar campos opcionales.

## R7. Estrategia de pruebas

**Decision**: Combinar tests unitarios directos con `OutputCaptureExtension` y tests livianos de contexto con `ApplicationContextRunner`.

**Rationale**: Las pruebas unitarias controlan resultados, excepciones, logs y MDC sin reloj real. El runner de contexto verifica la semántica de las condiciones y de `@EnableScheduling` sin levantar la aplicación completa ni infraestructura externa.

**Alternatives considered**:

- Esperar a que dispare un cron en una prueba de integración: lento y frágil; no agrega evidencia sobre la metadata ya verificable.
- `@SpringBootTest`: carga base, seguridad y almacenamiento innecesarios para dos beans sin I/O propio.

## Clarifications

No quedan aclaraciones pendientes. Las decisiones se resolvieron mediante el pedido explícito, la feature 016 y los patrones existentes del repositorio.
