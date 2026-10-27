# Feature Specification: Recálculo semanal de cotizaciones

**Feature Branch**: `feature/scheduler-cotizaciones`

**Created**: 2026-10-26

**Status**: Draft

**Input**: User description: "Job semanal que dispara automáticamente el recálculo de cotizaciones, con cron y zona configurables, habilitación independiente, trazabilidad propia y manejo seguro de concurrencia y errores."

## Contexto y restricciones

La feature 016 ya aporta la operación interna que recalcula cotizaciones para todas las estrategias cuando no se indica una estrategia concreta. Esa operación garantiza una única corrida a la vez y rechaza un segundo intento mediante `RecalculationInProgressException`.

La actualización de estadísticas de WhoScored tiene hoy su propio proceso programado los lunes a las 04:00 UTC. El nuevo proceso de valuación se ejecutará por defecto los lunes a las 06:00 UTC para utilizar las estadísticas ya sincronizadas, pero no dependerá de que esa sincronización esté habilitada: también debe poder operar con los datos persistidos existentes.

Los archivos de Entrega 1 `SchedulingConfig`, `PlayerStatsScheduler` y cualquier archivo llamado `application.properties` están protegidos y no se modifican en esta feature. Si durante la planificación o implementación un cambio allí resultara imprescindible, el trabajo MUST detenerse y consultar al equipo, identificando el archivo, el cambio mínimo propuesto y una alternativa que preserve el archivo.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Recalcular cotizaciones semanalmente (Priority: P1)

Como responsable del mercado, quiero que las cotizaciones se recalculen automáticamente una vez por semana después de la actualización prevista de estadísticas, para mantener los precios vigentes sin intervención manual.

**Why this priority**: Es el objetivo central de la feature y satisface el proceso batch semanal exigido por el producto.

**Independent Test**: Habilitar únicamente el proceso programado de valuación con una expresión y zona controladas, alcanzar el instante configurado y comprobar que se solicita exactamente una corrida para todas las estrategias.

**Acceptance Scenarios**:

1. **Given** el proceso programado de valuación habilitado con sus valores por defecto, **When** llega el lunes a las 06:00 UTC, **Then** se dispara exactamente una solicitud de recálculo para todas las estrategias.
2. **Given** una expresión cron y una zona válidas configuradas, **When** llega el instante que ambas determinan, **Then** el recálculo se dispara en ese instante y no en el horario por defecto.
3. **Given** el proceso programado de valuación deshabilitado, **When** transcurren uno o más instantes que coincidirían con el cron, **Then** no se solicita ningún recálculo.

---

### User Story 2 - Operar con independencia de WhoScored (Priority: P1)

Como operador del sistema, quiero habilitar el recálculo automático aunque la sincronización de WhoScored esté apagada, para usar las estadísticas ya persistidas y administrar ambos procesos por separado.

**Why this priority**: La dependencia actual de la activación general del scheduler impediría usar esta feature en forma autónoma y contradice la tolerancia a fuentes externas del proyecto.

**Independent Test**: Deshabilitar la sincronización de WhoScored, habilitar el proceso programado de valuación y verificar que el disparador queda activo y ejecuta la corrida en el horario configurado.

**Acceptance Scenarios**:

1. **Given** la sincronización de WhoScored deshabilitada y el proceso programado de valuación habilitado, **When** llega su horario, **Then** se solicita el recálculo normalmente con los datos persistidos.
2. **Given** la sincronización de WhoScored habilitada y el proceso programado de valuación deshabilitado, **When** corre la sincronización, **Then** no se dispara un recálculo de cotizaciones como consecuencia.
3. **Given** ambos procesos habilitados con sus horarios por defecto, **When** llega el lunes, **Then** WhoScored conserva su ejecución prevista a las 04:00 UTC y la valuación solicita su corrida a las 06:00 UTC.

---

### User Story 3 - Trazar cada ejecución sin afectar el scheduler (Priority: P1)

Como integrante del equipo de operación, quiero identificar cada intento automático y conocer su resultado y contadores, para diagnosticar corridas exitosas, fallidas o salteadas sin que un error detenga futuras ejecuciones.

**Why this priority**: Un proceso sin supervisión necesita trazabilidad completa y aislamiento de fallas para ser operable.

**Independent Test**: Ejecutar el disparador con un recálculo exitoso, con una falla general y con una corrida manual ya activa; capturar los registros y verificar el identificador, el nivel, el resultado y que ninguna excepción se propaga.

**Acceptance Scenarios**:

1. **Given** que llega un horario configurado, **When** comienza el intento automático, **Then** genera un correlation id nuevo con formato `valuation-<uuid>` y registra el inicio con ese identificador.
2. **Given** un recálculo exitoso, **When** termina, **Then** registra el fin con el mismo correlation id y con el id de corrida, estrategias calculadas y salteadas, jugadores procesados y fallidos, conteos por estado y estado de actualización del precio de mercado provistos por el resumen.
3. **Given** que el recálculo lanza una excepción inesperada, **When** el disparador la recibe, **Then** registra el fin fallido con el mismo correlation id y el tipo de error, no propaga la excepción y queda disponible para el siguiente horario.
4. **Given** que ya existe una corrida manual en curso, **When** llega el horario automático, **Then** el intento se saltea inmediatamente, se registra como salteado por corrida en curso con su correlation id y no altera ni espera a la corrida manual.
5. **Given** cualquier resultado del intento automático, **When** el disparador finaliza, **Then** elimina del MDC el correlation id generado y no deja contexto residual.

### Edge Cases

- El catálogo no contiene jugadores: la corrida sigue siendo exitosa y el registro de fin conserva los contadores en cero devueltos por el resumen.
- No hay estadísticas recientes o WhoScored está inaccesible: el job no consulta fuentes externas y delega en la corrida existente, que aplica sus reglas para datos persistidos ausentes o antiguos.
- Una corrida manual comienza instantes antes del horario automático: el intento automático se saltea sin reintento inmediato y la corrida manual continúa sin cambios.
- Una corrida termina con falla general después de haber comenzado: se registra el resultado fallido, se limpia el MDC del job y el siguiente disparo puede volver a intentarlo.
- La configuración contiene una expresión cron o una zona inválida: el error de configuración debe ser visible durante la inicialización y no debe sustituirse silenciosamente por otro horario.
- Se habilitan simultáneamente la sincronización y la valuación: cada proceso conserva su habilitación, cron, zona y correlation id propios.

## Requirements *(mandatory)*

### Functional Requirements

**Programación y configuración**

- **FR-001**: El sistema MUST ofrecer un proceso programado que solicite el recálculo de cotizaciones para todas las estrategias disponibles, reutilizando la operación interna existente de recálculo.
- **FR-002**: El proceso programado MUST habilitarse exclusivamente mediante `valuation.schedule.enabled`; si la propiedad está ausente, su valor MUST ser `false`.
- **FR-003**: La expresión de ejecución MUST configurarse mediante `valuation.schedule.cron`; si la propiedad está ausente, MUST usarse `0 0 6 * * MON` (lunes a las 06:00).
- **FR-004**: La zona horaria MUST configurarse mediante `valuation.schedule.zone`; si la propiedad está ausente, MUST usarse `UTC`.
- **FR-005**: Los valores por defecto de las tres propiedades de valuación MUST definirse fuera de cualquier archivo `application.properties`, siguiendo el criterio de configuración ya usado por las propiedades de valuación existentes.
- **FR-006**: El entorno de tests MUST mantener el proceso deshabilitado salvo en las pruebas que lo habiliten explícitamente para verificar esta feature.
- **FR-007**: Una expresión cron o zona inválida MUST impedir que el proceso se inicialice con un horario distinto al configurado y MUST producir un diagnóstico de configuración visible.

**Independencia entre procesos**

- **FR-008**: La activación efectiva de la programación de valuación MUST depender únicamente de `valuation.schedule.enabled` y MUST funcionar cuando `whoscored.sync.enabled` sea `false` o esté ausente.
- **FR-009**: Habilitar o deshabilitar la programación de valuación MUST NOT habilitar, deshabilitar ni disparar la sincronización de WhoScored.
- **FR-010**: La ejecución automática MUST NOT consultar WhoScored, Football-Data ni ninguna otra fuente externa; MUST operar exclusivamente a través del recálculo existente y sus datos persistidos.

**Concurrencia y resiliencia**

- **FR-011**: Cada disparo MUST solicitar el recálculo para todas las estrategias, sin filtrar por una estrategia concreta.
- **FR-012**: Si el recálculo informa que ya existe una corrida en curso, el disparo automático MUST finalizar inmediatamente como salteado, MUST NOT reintentar dentro del mismo disparo y MUST NOT afectar la corrida activa.
- **FR-013**: El caso de corrida en curso MUST registrarse como una omisión esperada, distinguible de una falla del proceso.
- **FR-014**: El disparador MUST capturar toda excepción no comprobada (`RuntimeException`) producida durante su ejecución, registrarla y finalizar sin propagarla al mecanismo de programación. Los errores de la JVM y los errores de configuración detectados durante la inicialización quedan fuera de esta captura.
- **FR-015**: Después de una ejecución exitosa, fallida o salteada, el proceso MUST quedar en condiciones de atender el siguiente disparo configurado.

**Observabilidad**

- **FR-016**: Antes de invocar el recálculo, cada disparo MUST generar un correlation id nuevo y único con formato `valuation-<uuid>`, donde `<uuid>` sea un UUID válido.
- **FR-017**: Todos los registros del intento automático y todos los registros y cotizaciones producidos por la corrida delegada MUST compartir el correlation id generado por el disparador.
- **FR-018**: Cada intento MUST emitir exactamente un registro estructurado de inicio con su correlation id.
- **FR-019**: Cada intento MUST emitir exactamente un registro estructurado terminal con el mismo correlation id y uno de tres resultados distinguibles: completado, salteado por corrida en curso o fallido.
- **FR-020**: En un resultado completado, el registro terminal MUST incluir los contadores y datos del resumen existente: id de corrida, estrategias calculadas y salteadas, jugadores procesados y fallidos, conteos por estado y si se actualizó el precio de mercado.
- **FR-021**: En un resultado fallido, el registro terminal MUST incluir únicamente el tipo de error (`errorType`), sin mensaje, stack trace, datos sensibles ni contadores que el recálculo no haya podido producir.
- **FR-022**: En un resultado salteado, el registro terminal MUST indicar como motivo que existe una corrida en curso y MUST NOT presentarlo como recálculo completado.
- **FR-023**: Al finalizar por cualquier camino, el disparador MUST eliminar del MDC el correlation id generado y dejar esa clave sin valor residual.

**Alcance y archivos protegidos**

- **FR-024**: Esta feature MUST NOT modificar `SchedulingConfig`, `PlayerStatsScheduler` ni ningún archivo `application.properties` sin aprobación explícita previa del equipo.
- **FR-025**: La incorporación del proceso programado MUST NOT cambiar el contrato ni el comportamiento observable del recálculo manual definido por la feature 016.

### Key Entities

- **Configuración del scheduler de valuación**: Conjunto de habilitación, expresión cron y zona que determina si el proceso existe y cuándo solicita la corrida. Sus valores por defecto son deshabilitado, lunes 06:00 y UTC.
- **Intento automático de valuación**: Ejecución individual del disparador, identificada por su correlation id propio y un resultado terminal completado, salteado o fallido.
- **Resumen de corrida**: Resultado existente del recálculo con el id de corrida y sus contadores; alimenta el registro terminal de un intento completado, pero no se redefine en esta feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Con la configuración por defecto, se ejecutan 0 recálculos automáticos; al habilitarla sin sobrescribir horario ni zona, se solicita exactamente 1 recálculo cada lunes a las 06:00 UTC.
- **SC-002**: El 100% de las pruebas con valuación habilitada y WhoScored deshabilitado activa y ejecuta el proceso de valuación en el horario configurado; se producen 0 activaciones de WhoScored por esa causa.
- **SC-003**: El 100% de los intentos automáticos genera un correlation id único que coincide con `valuation-<uuid>` y aparece tanto en el registro de inicio como en un único registro terminal.
- **SC-004**: El 100% de las ejecuciones exitosas informa en su registro terminal todos los contadores entregados por el resumen de corrida, sin discrepancias.
- **SC-005**: El 100% de los escenarios probados donde el recálculo termina con una excepción no comprobada finaliza sin propagarla y deja un registro terminal fallido que contiene únicamente el tipo de error.
- **SC-006**: Si una corrida manual ya está activa, el 100% de los disparos automáticos concurrentes se saltea sin iniciar una segunda corrida, sin esperar a la manual y sin modificar su resultado.
- **SC-007**: Después de cualquier resultado probado —completado, salteado o fallido—, la clave de correlation id queda ausente del MDC y el siguiente horario puede ejecutarse.
- **SC-008**: La revisión de cambios de la feature muestra 0 modificaciones en `SchedulingConfig`, `PlayerStatsScheduler` y archivos `application.properties`.

## Assumptions

- `specs/020-scheduler-cotizaciones` es el directorio solicitado y es independiente del nombre de la rama Git.
- La aplicación se despliega en una sola instancia, de acuerdo con el alcance de exclusión mutua de la feature 016; la coordinación distribuida entre instancias queda fuera de alcance.
- El horario por defecto deja dos horas entre la sincronización prevista de WhoScored y el recálculo, pero no garantiza que existan estadísticas nuevas ni espera a esa sincronización.
- Si una corrida manual ocupa el horario automático, omitir ese intento es preferible a encolarlo; la próxima oportunidad será el siguiente instante definido por el cron.
- Los contadores del log exitoso son los provistos por el resumen de la feature 016; esta feature no vuelve a contar jugadores ni cotizaciones.
- “Nunca deja escapar una excepción” se refiere a excepciones no comprobadas (`RuntimeException`) durante la ejecución de `run()`. Los errores de la JVM no se capturan y una configuración cron o zona inválida falla visiblemente durante la inicialización según FR-007.
- No se requiere persistir una entidad nueva ni agregar auditoría financiera: la trazabilidad de esta automatización se realiza mediante los logs estructurados y las cotizaciones que ya conservan correlation id.

## Out of Scope

- Cambiar el algoritmo, los contadores, la atomicidad o el endpoint manual de la feature 016.
- Ejecutar una estrategia específica, un subconjunto de jugadores o una liga desde el scheduler.
- Reintentos inmediatos, encolado, recuperación de una ejecución salteada o disparos compensatorios.
- Coordinar la finalización de WhoScored antes de recalcular o disparar el recálculo como consecuencia directa de una sincronización.
- Consultar fuentes externas o modificar la sincronización de estadísticas.
- Coordinación o bloqueo distribuido entre múltiples instancias.
- Modificar los archivos protegidos indicados en esta especificación.
