# Implementation Plan: Métricas propias de la aplicación

**Branch**: `feature/metricas-avanzadas` | **Date**: 2026-11-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/036-metricas-avanzadas/spec.md`

## Summary

Publicar en `/actuator/prometheus` métricas propias del mercado, las cotizaciones, las fuentes externas y los usuarios, con prefijo `gdmarket.`, descripción y etiquetas de valores cerrados. Un paquete nuevo `metrics` con un único componente `BusinessMetrics` registra los meters y ofrece métodos con nombre de negocio. Services y adapters dependen de él, nunca de Micrometer. Órdenes y trades se cuentan después del commit con `TransactionSynchronization`. El cambio de estrategia se cuenta escuchando su evento existente. El recálculo se mide en `QuoteCalculationService`, porque su evento no trae duración, resultado ni disparador. Los aciertos de caché de Football-Data se cuentan con un `CacheResolver` decorador, y los de WhoScored donde esa caché ya se consulta. Medir nunca altera el resultado de la operación. Se agrega un dashboard de Grafana provisionado y la regla de capas de la 033 incorpora `metrics`.

## Technical Context

**Language/Version**: Java 17; Spring Boot 4.1.1; Maven Wrapper existente.

**Primary Dependencies**: Micrometer y `micrometer-registry-prometheus` (ya en el `pom.xml` desde la 035); Spring Transaction (`TransactionSynchronizationManager`); Spring Cache (`CacheResolver`); ArchUnit existente. Sin dependencias nuevas.

**Storage**: PostgreSQL y Redis existentes. Ninguna tabla ni migración nueva; el gauge lee `users` con `count()`.

**Testing**: JUnit 5 y AssertJ; `SimpleMeterRegistry` para `BusinessMetrics` y las pruebas de service y adapter; `@SpringBootTest` y MockMvc con el setup de `ActuatorMonitoringTest` para la salida Prometheus; ArchUnit actualizado. Sin pruebas e2e en esta fase (las corre el usuario).

**Target Platform**: backend en el puerto 8080; demo de Prometheus y Grafana de la 035 en Docker Compose.

**Project Type**: servicio web (backend) con demo de observabilidad.

**Performance Goals**: medir agrega a cada operación sólo incrementos en memoria. El gauge de usuarios hace un `SELECT count(*)` por lectura del registry (un scrape cada 15 s en la demo).

**Constraints**: etiquetas sólo de dominios cerrados; nada de ids ni datos personales; un fallo al medir no cambia resultado, excepción ni transacción; mismo acceso ADMIN de la 035; ningún endpoint nuevo; cambios en archivos previos limitados a la lista autorizada de la spec; no se toca `CacheConfig` porque `E2EProfileTest` exige que el `CacheManager` sea un `RedisCacheManager`; sin commit, push ni PR automáticos.

**Scale/Scope**: 13 familias de métricas. Al arrancar hay 23 series de contadores y gauges con etiquetas cerradas, más las 4 combinaciones del timer (cada una con `_count`, `_sum`, `_max` y sus buckets) y 1 serie por estrategia después del primer recálculo. Una instancia de backend y un dashboard.

## Constitution Check

*GATE: passed before Phase 0; re-checked after Phase 1 design.*

| Regla | Comprobación | Resultado |
|---|---|---|
| §2.1 Capas estrictas | §2.1 enumera cuatro capas obligatorias (controllers, services, repositories, adapters), que no cambian. `metrics` no es una quinta capa de negocio: es un paquete de soporte, como `config`, `security`, `filter` y `scheduler`, sin lógica de negocio. Se declara en ArchUnit sólo para que sus dependencias queden verificadas, y lo usan sólo `service` y `adapter`. Depende de `model` y de `repository` (gauge), nunca de `service`, `controller` ni `adapter`. Los controllers no cambian. `LayeredArchitectureTest` declara la capa y `CodingRulesTest` la incluye en la verificación de ciclos (research R2). El listener de estrategia vive en `service`. | PASS |
| §5.3 Observabilidad | Se conservan logs JSON, correlation ID, health y las métricas HTTP de la 035. Se suman métricas de negocio y un dashboard que muestra latencia p95 y tasa de error 5xx junto con ellas. Los fallos de medición se registran como `business_metric_failed` en el log estructurado. | PASS |
| §4.1 Autorización | Las métricas sólo salen por `/actuator/prometheus` y `/actuator/metrics`, restringidos a ADMIN o API key ADMIN; una prueba verifica 403 para USER. Ninguna etiqueta lleva datos de usuarios o jugadores. | PASS |
| §3.1 Calidad/CI | Pruebas unitarias, de service y de contexto para cada escenario, más ArchUnit. | PASS |
| §7.2–7.3 CI y SonarCloud | El usuario confirma, publica y abre el PR; después se comprueba `CI` en `SUCCESS` y el Quality Gate en `PASSED`. | PENDIENTE, gate manual final |

**Reevaluación posterior al diseño**: el diseño (research, data-model, contrato y quickstart) respeta los gates. Hay una divergencia con la propuesta del usuario: el recálculo se mide en el service y no escuchando `QuoteRunCompletedEvent`, porque el evento sólo trae `runId` (research R4). No hay excepciones a la Constitution.

## Phase 0: Research

Ver [research.md](research.md). Decisiones clave:

- R1–R2: un paquete `metrics` con un componente y sus enums de etiquetas, y regla de capas.
- R3: `TransactionSynchronization.afterCommit` dentro de `BusinessMetrics`, en vez de `@TransactionalEventListener`.
- R4: medir el recálculo en el service, con sobrecarga para el disparador.
- R5: listener `AFTER_COMMIT` para el cambio de estrategia.
- R6: `CacheResolver` decorador para los aciertos de Football-Data; la caché de WhoScored se cuenta en `PlayerStatsService`.
- R7: gauge sobre `count()`.
- R8: aislamiento de fallos.
- R9: nombres y precisión.
- R10: dashboard.
- R11: pruebas.

No quedan decisiones abiertas.

## Phase 1: Design & Contracts

- [data-model.md](data-model.md): dominios de etiquetas, meters, puntos de registro, ciclo de vida e invariantes.
- [contracts/business-metrics.md](contracts/business-metrics.md): series exportadas, tipos, etiquetas, `# HELP`, prohibiciones y consultas del dashboard.
- [quickstart.md](quickstart.md): pruebas automatizadas, dos órdenes y demás pasos manuales, y dashboard.

### Cambios previstos

**Nuevos (`backend/src/main/java/com/example/demo/metrics/`)**

1. `BusinessMetrics`: registra en el constructor los meters del contrato, con todas las combinaciones de etiquetas cerradas en 0, el gauge sobre `UserRepository.count()` protegido y el `TimeGauge` `gdmarket.quotes.recalculation.last` sobre un `AtomicLong` de nanosegundos. `recalculationFinished` actualiza ese valor además del timer. Hace falta porque `_max` del timer vuelve a 0 a los ~2 minutos y el recálculo programado es semanal (contrato, "Por qué 7 días"). Métodos: `orderCreated(OrderSide, OrderType)` y `tradeExecuted(int tokens, BigDecimal credits)`, diferidos a `afterCommit` cuando hay sincronización activa; `orderRejected(OrderRejectionReason)`, `recalculationFinished(RecalculationTrigger, RecalculationOutcome, Duration)`, `quotesCalculated(String strategy, int count)`, `activeStrategyChanged()`, `externalCall(ExternalSource, ExternalCallOutcome)`, `registration(AuthOutcome)` y `login(AuthOutcome)`, inmediatos. Todos con `try/catch` y log `business_metric_failed`.
2. Enums de etiquetas: `OrderRejectionReason`, `RecalculationTrigger`, `RecalculationOutcome`, `ExternalSource`, `ExternalCallOutcome`, `AuthOutcome`. Cada uno con su valor de etiqueta en minúsculas.
3. `MeteredCacheResolver` (bean con nombre): resuelve con el `CacheManager` existente y envuelve la caché en un decorador que cuenta `externalCall(FOOTBALL_DATA, CACHE)` cuando `get(key, Callable)` no ejecuta el loader. El resto de los métodos delega sin cambios.

**Nuevo en service**

4. `service/valuation/ActiveStrategyMetricsListener`: `@TransactionalEventListener(AFTER_COMMIT)` sobre `ActiveStrategyChangedEvent` que llama a `activeStrategyChanged()`.

**Modificados (sólo los autorizados en la spec)**

5. `OrderService`: recibe `BusinessMetrics`. En `place`: `orderCreated` después de guardar la orden, `tradeExecuted` por cada trade, y un `try/catch` de las tres excepciones de rechazo que llama a `orderRejected` y relanza.
6. `MarketMakerService`: `orderCreated(SELL, LIMIT)` al guardar su orden.
7. `QuoteCalculationService`: sobrecarga `recalculate(Optional<String>, RecalculationTrigger)`; la firma actual delega con `MANUAL`. En `execute`, `recalculationFinished` en éxito y en fallo con la duración que ya se calcula, y `quotesCalculated` por estrategia en éxito.
8. `QuoteRecalculationScheduler`: llama a la sobrecarga con `SCHEDULED`.
9. `FootballDataAdapter`: `SUCCESS` y `ERROR` en `fetchCompetitionTeams`; `cacheResolver` en la anotación `@Cacheable`. `ERROR` se cuenta ante `RestClientException` (que sigue convirtiéndose en `FootballDataException`) y ante cualquier otra `RuntimeException`, que se relanza sin cambios.
10. `WhoScoredAdapter`: `SUCCESS` y `ERROR` por `fetchLeaguePlayers`. `ERROR` se cuenta ante `WhoScoredException` y ante cualquier otra `RuntimeException` (por ejemplo, del cliente Playwright o del mapper); ambas se relanzan sin cambios.
11. `PlayerStatsService`: `externalCall(WHOSCORED, CACHE)` cuando una liga no necesita consultar la fuente.
12. `AuthService`: `registration` y `login` por resultado.
13. `LayeredArchitectureTest` y `CodingRulesTest`: capa `Metrics` y paquete en la verificación de ciclos (research R2).
14. Pruebas existentes de las clases 5 a 12: constructores (pasando un `BusinessMetrics` sobre `SimpleMeterRegistry`), la firma verificada en `QuoteRecalculationSchedulerTest` y casos nuevos de métricas, sin cambiar los existentes.
15. `README.md`, sección Monitoreo: tabla de métricas (o enlace al contrato) y cómo abrir el dashboard **GD Market**.

**Observabilidad**

16. `observability/grafana/dashboards/gdmarket.json`: uid `gdmarket`, datasource `prometheus`, paneles y consultas del contrato. Lo carga el provisioning existente sin cambios.

**Pruebas nuevas**: `BusinessMetricsTest`, `MeteredCacheResolverTest`, `OrderMetricsTest`, `OrderMetricsFailureTest`, `ActiveStrategyMetricsListenerTest`, `FootballDataCacheMetricsTest`, `BusinessMetricsPrometheusTest`, y casos nuevos en `MarketMakerServiceTest`, `QuoteCalculationServiceTest`, `FootballDataAdapterTest`, `WhoScoredAdapterTest`, `PlayerStatsServiceTest` y `AuthServiceTest` (research R11).

- **Fallos de medición (SC-003)**: se simulan con `FailingMeterRegistry` (subclase de prueba de `SimpleMeterRegistry` en `…/test/demo/metrics/` que sobreescribe `newCounter` y `newTimer` para devolver meters que lanzan al medir). Un `SimpleMeterRegistry` cerrado no sirve: devuelve meters no-op y no lanza. Además de `BusinessMetricsTest`, `OrderMetricsFailureTest` (con un `@Primary BusinessMetrics` sobre ese registry) y `AuthServiceTest` comprueban que, con la medición fallando, la operación devuelve el mismo resultado o la misma excepción.
- **`FootballDataCacheMetricsTest`**: usa un contexto Spring mínimo (`@SpringJUnitConfig`) y no `@SpringBootTest`. El bean real `footballDataRestClient` (`config/FootballDataConfig`) apunta en tests a `http://127.0.0.1:1/v4` y no se puede sobrescribir sin habilitar bean overriding; un mock de `RestClient` exigiría stubs encadenados. El contexto declara `@EnableCaching`, un `ConcurrentMapCacheManager`, `BusinessMetrics` sobre `SimpleMeterRegistry`, `MeteredCacheResolver`, un `RestClient` armado con un `RestClient.Builder` enlazado a `MockRestServiceServer` (como `FootballDataAdapterTest`) y el `FootballDataAdapter` real. Así se prueba el proxy de `@Cacheable` con su resolvedor.

## Project Structure

### Documentation (this feature)

```text
specs/036-metricas-avanzadas/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── business-metrics.md
├── checklists/
│   └── requirements.md
└── tasks.md             # $speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/com/example/demo/
├── metrics/                                  # NUEVO
│   ├── BusinessMetrics.java
│   ├── MeteredCacheResolver.java
│   ├── OrderRejectionReason.java
│   ├── RecalculationTrigger.java
│   ├── RecalculationOutcome.java
│   ├── ExternalSource.java
│   ├── ExternalCallOutcome.java
│   └── AuthOutcome.java
├── service/
│   ├── AuthService.java                      # modificado
│   ├── PlayerStatsService.java               # modificado
│   ├── market/OrderService.java              # modificado
│   ├── market/MarketMakerService.java        # modificado
│   └── valuation/
│       ├── QuoteCalculationService.java      # modificado
│       └── ActiveStrategyMetricsListener.java  # NUEVO
├── adapter/
│   ├── footballdata/FootballDataAdapter.java # modificado
│   └── whoscored/WhoScoredAdapter.java       # modificado
└── scheduler/QuoteRecalculationScheduler.java  # modificado

backend/src/test/java/com/example/demo/
├── metrics/BusinessMetricsTest.java          # NUEVO
├── metrics/MeteredCacheResolverTest.java     # NUEVO
├── config/BusinessMetricsPrometheusTest.java # NUEVO
├── service/valuation/ActiveStrategyMetricsListenerTest.java  # NUEVO
├── service/market/OrderMetricsTest.java     # NUEVO
├── service/market/OrderMetricsFailureTest.java  # NUEVO
├── adapter/footballdata/FootballDataCacheMetricsTest.java  # NUEVO
└── architecture/{LayeredArchitectureTest,CodingRulesTest}.java  # modificados

observability/grafana/dashboards/gdmarket.json  # NUEVO
README.md                                       # sección Monitoreo
```

**Structure Decision**: backend existente en `backend/`, con un paquete hermano nuevo `metrics` al nivel de `service` y `adapter`, declarado como capa en ArchUnit. El dashboard se suma a la carpeta que ya provisiona la demo de la 035.

## Complexity Tracking

Sin violaciones de la Constitution. Dos desvíos respecto de la propuesta del usuario, justificados en research:

| Desvío | Por qué | Alternativa descartada |
|---|---|---|
| El recálculo se mide en `QuoteCalculationService`, no escuchando `QuoteRunCompletedEvent` | El evento sólo trae `runId`: no tiene duración, resultado, disparador ni conteos | Ampliar el evento cambia el contrato de tres listeners y sus pruebas |
| Los aciertos de Football-Data se cuentan con un `CacheResolver` y no envolviendo el `CacheManager` | `E2EProfileTest` exige un `RedisCacheManager` y `CacheConfig` no está autorizado | `BeanPostProcessor` sobre el `CacheManager`; estadísticas de Redis |
