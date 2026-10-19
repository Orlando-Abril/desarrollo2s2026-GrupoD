# Implementation Plan: Historial de snapshots de estadísticas

**Branch**: `feature/stats-snapshots` | **Date**: 2026-10-19 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/014-stats-snapshots/spec.md`

## Summary

Agregar un historial append-only separado de `PlayerStats` mediante la nueva entidad `PlayerStatsSnapshot`, con identidad técnica propia, relación lazy al jugador, copia nullable de las diez métricas, origen, captura e id de corrida. Un nuevo `StatsSnapshotService` leerá las estadísticas persistidas, fijará una única hora de captura mediante un `Clock` inyectable, descartará claves ya capturadas con una consulta por lote, persistirá las restantes en lote y resolverá carreras mediante la restricción única y un reintento transaccional consciente de conflictos. La operación heredará o generará un correlation ID en el MDC y emitirá un log estructurado terminal con SLF4J. El mismo servicio consultará los últimos N snapshots y derivará `StatsWindow` consecutivas, incluidas las reglas de `null`, correcciones negativas, rating ponderado y reinicio de temporada.

## Technical Context

**Language/Version**: Java 17

**Primary Dependencies**: Spring Boot 4.1.1, Spring Data JPA, Hibernate ORM, Jakarta Validation, Lombok

**Storage**: PostgreSQL en producción/e2e; H2 en modo PostgreSQL para pruebas JPA livianas; esquema administrado actualmente por Hibernate (`ddl-auto`)

**Testing**: JUnit 5, AssertJ, Mockito, `@DataJpaTest` + `@UnitTestProfile`, y `@E2ETest` sobre PostgreSQL/Redis reales del perfil e2e

**Target Platform**: Servicio backend JVM ejecutable en Linux y en el entorno local de desarrollo

**Project Type**: Aplicación web con backend Spring Boot y frontend independiente; esta feature sólo modifica el backend y no expone API pública

**Performance Goals**: Una invocación captura todos los jugadores con estadísticas mediante una lectura y persistencia en lote; la consulta trae como máximo N snapshots; el índice `(player_id, captured_at)` evita recorrer el historial completo para obtener los más recientes

**Constraints**: Historial append-only; métricas nullable; cero llamadas externas; idempotencia por `(player_id, source_fetched_at)` incluso ante concurrencia; una sola hora por corrida; auditoría operativa mediante log SLF4J estructurado con correlation ID; no modificar `PlayerStats`, `PlayerStatsService`, `PlayerStatsRepository`, MOMENTUM ni `MomentumParameters`

**Scale/Scope**: Jugadores de las cinco grandes ligas, una captura por corrida de valuación y crecimiento histórico semanal; una entidad, un repositorio, un servicio, dos records de salida y una configuración de reloj nuevos

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Regla constitucional | Diseño y evidencia | Pre-design | Post-design |
|----------------------|-------------------|------------|-------------|
| §1.2 Motor de valuación | Conserva un insumo histórico trazable por jugador y corrida, sin alterar las estrategias ni sus versiones. | PASS | PASS |
| §1.3 Resiliencia y desacoplamiento | `snapshotAll` sólo lee `PlayerStatsRepository`; no incorpora adapters ni llamadas a WhoScored. | PASS | PASS |
| §2.1 Backend en capas | Entidad en `model`, acceso en `repository`, reglas y records de cálculo en `service/valuation`, configuración transversal en `config`. No hay controller porque no se expone contrato externo. | PASS | PASS |
| §3.1 CI y §3.3 pruebas | Se planifican pruebas unitarias, JPA livianas y e2e PostgreSQL, ejecutables por Maven/CI. | PASS | PASS |
| §5.2 Índices | La entidad declara `idx_player_stats_snapshots_player_captured` sobre `(player_id, captured_at)` y una unicidad separada sobre `(player_id, source_fetched_at)`. | PASS | PASS |
| §5.3 Observabilidad y §7 Auditoría/Logs | `snapshotAll` reutiliza el correlation ID del MDC o genera uno cuando falta, emite un log SLF4J estructurado con correlation ID, `runId`, guardados y salteados, y restaura el MDC. El log append-only cubre la auditoría operativa sin tabla nueva. | PASS | PASS |
| §6.1 OpenAPI | No aplica contrato OpenAPI: esta feature es interna y no agrega endpoints. | PASS | PASS |
| §7 Definition of Done | El diseño respeta capas, incluye verificación automatizada y no depende de fuentes externas. La validación completa de CI/Sonar corresponde a implementación. | PASS | PASS |

No hay violaciones que requieran justificación ni aclaraciones técnicas pendientes.

## Project Structure

### Documentation (this feature)

```text
specs/014-stats-snapshots/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── checklists/
    └── requirements.md
```

No se crea `contracts/`: el servicio es una interfaz interna del backend y esta feature no agrega endpoints, eventos ni contratos para consumidores externos.

### Source Code (repository root)

```text
backend/src/main/java/com/example/demo/
├── config/
│   └── ClockConfig.java
├── model/
│   └── PlayerStatsSnapshot.java
├── repository/
│   └── PlayerStatsSnapshotRepository.java
└── service/valuation/
    ├── SnapshotResult.java
    ├── StatsSnapshotService.java
    └── StatsWindow.java

backend/src/test/java/com/example/demo/
├── e2e/
│   └── PlayerStatsSnapshotE2ETest.java
├── repository/
│   └── PlayerStatsSnapshotRepositoryTest.java
└── service/valuation/
    ├── StatsSnapshotPersistenceTest.java
    ├── StatsSnapshotLoggingTest.java
    └── StatsSnapshotServiceTest.java
```

**Structure Decision**: Se extiende el backend único existente respetando sus paquetes por capa. `PlayerStatsSnapshotRepository` expone sólo inserción y lectura, sin `delete` ni actualización. `StatsSnapshotService` concentra captura, ordenamiento y cálculo; `StatsWindow` y `SnapshotResult` son records inmutables de salida del servicio. No se modifica el frontend ni los archivos protegidos de Entrega 1.

## Design Details

### Persistencia e inmutabilidad

- `PlayerStatsSnapshot` sigue el patrón de `PlayerQuote`: `@Entity`, `@Immutable`, getters, builder, constructor protegido sin argumentos y campos `updatable = false`.
- La tabla `player_stats_snapshots` usa `id` autogenerado para desempatar capturas con el mismo `capturedAt`.
- La relación `player` es `@ManyToOne(fetch = LAZY, optional = false)` y `Player` no mantiene colección inversa.
- El repositorio extiende la interfaz mínima `Repository`, declara `save`, `saveAll`, `saveAllAndFlush`/`flush`, lecturas y comprobaciones necesarias, pero no hereda operaciones de borrado.
- El índice de historial ordena por `player_id, captured_at`; la restricción `uk_player_stats_snapshots_player_source` garantiza que una versión de origen sólo se capture una vez por jugador.

### Reloj y captura masiva

- `ClockConfig` publica un único bean `Clock.systemUTC()`. `StatsSnapshotService` recibe ese `Clock` y evalúa `clock.instant()` una sola vez al comenzar `snapshotAll(runId)`.
- `runId` se valida como no nulo antes de leer o escribir.
- `snapshotAll` obtiene las filas actuales mediante el `PlayerStatsRepository` existente y crea las claves candidatas `(playerId, fetchedAt)`. El repositorio de snapshots ejecuta una sola consulta por lote que proyecta las claves existentes cuyo `playerId` y `sourceFetchedAt` pertenecen a los conjuntos candidatos; el servicio filtra después por el par exacto para evitar falsos positivos del producto cruzado y construye snapshots sólo para las restantes. No se ejecuta un `exists` por jugador.
- La escritura normal usa un lote y fuerza el flush dentro de una transacción independiente. Si la restricción única detecta una carrera, ese intento se revierte, se vuelven a consultar las claves y se reintenta en lote sólo con las todavía ausentes. Si el conjunto pendiente no disminuye, el error no es una duplicación esperada y se propaga. Así no se ocultan fallos de integridad ajenos a la concurrencia.
- `SnapshotResult(saved, skipped)` cuenta como `skipped` las claves preexistentes y las insertadas por otra corrida durante un reintento. Jugadores sin `PlayerStats` no forman parte del total.

### Correlation ID y auditoría operativa

- Al comenzar, `snapshotAll` lee `CorrelationIdFilter.MDC_KEY`. Si el valor falta o está vacío, genera un UUID, lo instala en el MDC durante la operación y lo elimina en el `finally`; si ya existía, lo conserva y no lo reemplaza.
- SLF4J emite exactamente un evento terminal estructurado por invocación: en éxito incluye correlation ID, `runId`, guardados y salteados; en error incluye correlation ID, `runId` y `errorType`, no publica conteos parciales y se emite antes de propagar la excepción.
- El estado previo del MDC se restaura en todos los caminos para evitar contaminación entre requests o ejecuciones. No se agrega una tabla de auditoría.

### Consulta y ventanas

- El repositorio consulta por `player.id` ordenando `capturedAt DESC, id DESC` y acepta `Pageable` para limitar en la base.
- El servicio valida `n > 0`, toma los N más recientes, invierte la lista a `capturedAt ASC, id ASC` y la usa tanto para `recentSnapshots` como para `recentWindows`.
- `recentWindows(playerId, n)` empareja cada elemento con su sucesor; cero o un snapshot producen lista vacía.
- En una ventana normal, cada contador se calcula con una función null-safe: si falta un extremo devuelve `null`; de lo contrario devuelve `max(0, nuevo - anterior)`.
- El rating normal se recupera desde promedios acumulados: `(ratingNuevo × minutosNuevos − ratingAnterior × minutosAnteriores) / deltaMinutos`, con `MathContext.DECIMAL64`, y luego se acota a `[0,10]`. Devuelve `null` cuando falta un extremo o `deltaMinutos <= 0`.
- Si ambos minutos existen y el nuevo es menor, la ventana se marca `seasonReset = true`: contadores y minutos copian el snapshot nuevo; el rating copia el nuevo sólo si hay minutos nuevos positivos y luego se acota.

### Estrategia de pruebas

- `StatsSnapshotServiceTest`: prueba cálculo puro/servicio con repositorio simulado para orden, `null`, delta negativo, rating acumulado que baja, clamps, reinicio y un solo snapshot.
- `PlayerStatsSnapshotRepositoryTest`: `@DataJpaTest` + `@UnitTestProfile`; persiste todos los campos, comprueba consulta de últimos N, ausencia de operaciones destructivas y rechazo de duplicados por `(player_id, source_fetched_at)` en H2.
- `StatsSnapshotPersistenceTest`: `@DataJpaTest` + `@UnitTestProfile`, importando el servicio y un `Clock.fixed`; ejecuta `snapshotAll` dos veces sobre las mismas estadísticas y verifica `(saved, skipped)`, un único snapshot y una única hora para toda la corrida.
- `StatsSnapshotPersistenceTest` desactiva la transacción externa del test con `@Transactional(propagation = NOT_SUPPORTED)` para que los fixtures confirmados sean visibles a los intentos `REQUIRES_NEW` del servicio.
- `StatsSnapshotLoggingTest`: captura los eventos SLF4J y verifica reutilización/generación del correlation ID, exactamente un evento terminal, guardados/salteados sólo en éxito, `errorType` sin conteos parciales en error y restauración del MDC en ambos caminos.
- `PlayerStatsSnapshotE2ETest`: `@E2ETest`, obtiene jugadores mediante `E2EFixtures`, guarda snapshots, valida límite/orden de “últimos N”, existencia del índice y restricción única; además sincroniza con `CountDownLatch` dos llamadas concurrentes a `snapshotAll` contra PostgreSQL y exige una sola fila con resultados guardado/salteado complementarios.
- El `Clock` fijo se inyecta directamente en pruebas unitarias; las pruebas de contexto que lo necesiten pueden aportar un bean de prueba primario sin editar propiedades.

## Complexity Tracking

No aplica: el Constitution Check no presenta violaciones.
