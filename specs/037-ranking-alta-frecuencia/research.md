# Research: ranking de alta frecuencia

## Reanudación T024–T026: evidencia de la base de demo

La medición HTTP de T024 está registrada en [quickstart.md](quickstart.md): tras eliminar sólo la clave Redis de la primera página, once solicitudes dieron HTTP 200 y los diez hits siguientes tardaron 17,893–33,338 ms. Con el índice aplicado, otra tanda tuvo un hit de 59,945 ms y veinte hits posteriores quedaron en 12,713–23,340 ms; 39 de 40 hits de ambas tandas y la ampliación estuvieron por debajo de 50 ms. La API key de usuario autenticó el ranking, pero recibió HTTP 403 en Prometheus, que requiere `ROLE_ADMIN`. Con un JWT administrativo leído sólo en memoria, el scrape real devolvió HTTP 200 y confirmó `cache_gets_total{application="demo",cache="quotes-ranking",cache_manager="redis",name="quotes-ranking",result="hit"}` = 30 y `result="miss"` = 2 (`pending` = 0). Después de borrar sólo la clave `active`, una carga fría y dos hits HTTP 200, las series pasaron a `hit=32`, `miss=4`, `pending=0`. La carga fría incrementa dos veces `miss` por la lectura inicial y `Cache.get(key, Callable)`; los dos hits incrementan `hit` dos veces. No se registró el valor de ninguna credencial.

Con PostgreSQL 17.6 en `localhost:5432/desarrollo2_grupod`, la base contiene 2.651 jugadores y 47.718 cotizaciones: 23.859 para `OFFICIAL` y 23.859 para `MOMENTUM`, cada estrategia con 2.651 jugadores cotizados. La activa es `OFFICIAL`. Los índices previos de `player_quotes` son `idx_player_quotes_player_strategy_time (player_id, strategy_code, calculated_at)`, `idx_player_quotes_strategy_time (strategy_code, calculated_at)` y la clave primaria `(id)`. La SQL reproducible está en [ranking-explain.sql](evidence/ranking-explain.sql), con `strategy=OFFICIAL`, `league=NULL`, `position=NULL`, `page=0`, `size=20`, dentro de una transacción `READ ONLY`. Las salidas completas previas están en [corrida 1](evidence/before-index.txt), [corrida 2](evidence/before-index-run2.txt) y [corrida 3](evidence/before-index-run3.txt).

| Antes del índice | Corrida 1 | Corrida 2 | Corrida 3 | Mediana |
| --- | ---: | ---: | ---: | ---: |
| Página, ejecución | 35,047 ms | 36,598 ms | 39,992 ms | 36,598 ms |
| `countQuery`, ejecución | 38,118 ms | 30,856 ms | 33,657 ms | 33,657 ms |

Ambos planes aplican `Seq Scan` a las 47.718 cotizaciones, descartan 23.859 de la otra estrategia y ordenan las 23.859 restantes por `player_id, calculated_at DESC, id DESC` antes de la función de ventana. En la corrida 1 la página usa 931 buffers compartidos y el conteo 922; el `Sort` histórico emplea 2.260 kB y 2.073 kB, respectivamente. La página tiene además un `Sort` final por `quote_value DESC, p.id` (339 kB), que el índice histórico no puede evitar. El sort histórico sí coincide con el índice candidato `(strategy_code, player_id, calculated_at DESC, id DESC)`; por esa evidencia se justificó probarlo y comparar los mismos planes.

Se probó `CREATE INDEX CONCURRENTLY idx_player_quotes_strategy_player_time_id_desc ON public.player_quotes (strategy_code, player_id, calculated_at DESC, id DESC)` después de registrar los planes iniciales. Los tres planes completos posteriores están en [corrida 1](evidence/with-index-run1.txt), [corrida 2](evidence/with-index-run2.txt) y [corrida 3](evidence/with-index-run3.txt). El índice quedó `indisvalid=true`, `indisready=true` y ocupa 2.768 kB. No se modificaron filas ni la SQL del repositorio.

| Con índice | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | Mediana |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Página (ms) | 52,957 | 42,062 | 34,950 | 38,599 | 42,026 | 45,878 | 32,459 | 31,604 | 36,337 | 31,682 | 37,468 |
| `countQuery` (ms) | 11,698 | 14,274 | 10,266 | 13,418 | 13,922 | 16,472 | 12,221 | 10,856 | 10,671 | 11,127 | 11,960 |

**Comparación**: El `countQuery` sustituye `Seq Scan` + sort histórico por `Index Only Scan` sobre el índice nuevo, sin lecturas de heap (`Heap Fetches: 0`): 213 buffers compartidos frente a 922 y mediana 11,960 ms frente a 33,657 ms (64,5 % menos). La página mantiene el mismo `Seq Scan` y los mismos dos sorts, con 931 buffers; su mediana cambia de 36,598 a 37,468 ms (+0,870 ms, 2,4 %), dentro de la variación de las corridas y sin regresión estructural del plan. Por la mejora del conteo y la ausencia de regresión material de la página, **se conserva el índice** en la base de demo y se declara en `PlayerQuote.java`. El `Sort` final por valor calculado permanece; el índice no lo resuelve. La comparación usa la misma población y los mismos parámetros, con caché de datos compartida ya caliente en todas las corridas; las salidas completas de las primeras tres corridas por estado permiten inspeccionar planes, buffers y tiempos.

## Evidencia de implementación

**Línea de base (2026-11-17)**: `QuoteQueryServiceCacheTest` (2), `QuoteQueryServiceRepositorySpyTest` (1), `QuoteCacheEvictorTest` (3), `QuoteRankingCacheIntegrationTest` (1) y `RankingControllerTest` (7): 14 pruebas, 0 fallos, 0 errores. Maven requirió acceso a dependencias fuera del sandbox.

**Disponibilidad al cierre anterior (2026-11-17)**: PostgreSQL aceptaba conexiones TCP en `localhost:5432`, pero esta sesión aún no tenía acceso a `pgpass` ni a `psql`. Redis, el backend y una API key tampoco estaban disponibles entonces. Esos límites quedaron superados en la reanudación, incluido el scrape autorizado de Prometheus registrado arriba.

**Revisión de alcance**: El diff de producción modifica solamente `QuoteQueryService`, `QuoteCacheEvictor` y `CacheConfig` en los puntos autorizados, y añade `RankingLoader`, `RankingWarmer` y `RankingAsyncConfig`. Los filtros JWT y API key, el controlador, DTO, repositorio SQL, TTL y paginación siguen intactos. La prueba de ausencia de acceso a base aplica al servicio de ranking después de autenticar; el objetivo de 50 ms corresponde al HTTP completo, incluida la autenticación. La configuración existente conserva logs JSON con `correlationId`, health y métricas HTTP de latencia y errores. La prueba focalizada dio 27 pruebas sin fallos ni errores antes de las últimas ampliaciones de pruebas.

**Verificación de código anterior a T025/T026**: `./mvnw.cmd test -q` cerró con 1.076 pruebas, 0 fallos, 0 errores y 49 omitidas según sus condiciones de entorno. Tras esa ejecución se reforzaron dos pruebas; la pasada focalizada final de eventos y concurrencia cerró con 8 pruebas, 0 fallos y 0 errores. `git diff --check` no señaló problemas de espacios. `QuoteRankingCacheIntegrationTest` y `ActiveStrategyRecalculationTest` pasaron dentro de la suite completa. Tras declarar el índice medido, una nueva pasada focalizada cerró con 50 pruebas, 0 fallos y 0 errores.

## R1. Carga única por clave

**Decision**: Usar `Cache.get(key, Callable)` en el líder de cada miss y coordinar temporalmente en `QuoteQueryService` las solicitudes con la misma clave; retirar la coordinación al terminar. No guardar páginas como un segundo caché.

**Rationale**: El `RedisCacheManager` actual usa el escritor sin bloqueo. Spring Data Redis indica que su escritor predeterminado no bloquea y que el escritor con bloqueo actúa a nivel de caché; el Javadoc sólo condiciona la carga única a que se configure bloqueo. Por ello `Cache.get(key, Callable)` sin coordinación no demuestra la prueba de 20 hilos. El control temporal por clave permite que filtros/páginas diferentes progresen sin bloquear todo el caché.

**Alternatives considered**: `@Cacheable(sync = true)` delega en la capacidad del proveedor y no cambia el escritor actual; además dificulta conservar el fallback diferenciado. Un `lockingRedisCacheWriter` sincronizaría a nivel de caché y modificaría `CacheConfig` más allá de las estadísticas previstas. Un segundo caché de páginas en memoria está fuera de alcance.

**Limit**: FR-004 y la prueba de 20 hilos cubren solicitudes concurrentes de una misma instancia. No se promete coordinación distribuida entre réplicas con el escritor Redis actual.

Sources: [Spring Data Redis cache](https://docs.spring.io/spring-data/redis/reference/redis/redis-cache.html), [RedisCacheWriter Javadoc](https://docs.spring.io/spring-data-redis/reference/api/java/org/springframework/data/redis/cache/RedisCacheWriter.html).

## R2. Hit del servicio sin conexión a la base

**Decision**: Después de completar la autenticación, el camino previo al caché dentro de `QuoteQueryService.ranking` no invoca repositorios, no abre transacción ni adquiere conexión de base atribuible al servicio. Para `strategy` omitida, usar un alias de clave `active` y resolver la estrategia persistida dentro de `RankingLoader` sólo en miss. Para una estrategia explícita, validar el código sin acceder a persistencia.

**Rationale**: `ValuationStrategyCatalog.active()` consulta `ActiveStrategySettingRepository.findById` en cada llamada; quitar sólo `@Transactional` de `ranking` no cumple el requisito de cero accesos a base. La invalidación completa ya asociada al cambio de estrategia hace que el alias represente la activa vigente. `RankingLoader` como bean separado permite que el proxy de `@Transactional(readOnly = true)` se aplique.

**Alternatives considered**: Mantener el código efectivo en la clave exigiría consultar la base en cada hit o mantener otra copia de la estrategia activa en memoria/Redis. La primera opción incumple FR-002 y la segunda amplía el alcance de caché.

**Alcance confirmado**: `JwtAuthFilter` (`userRepository.findByUsername`) y `ApiKeyAuthFilter` (`apiKeyRepository.findByKeyHash`) pueden seguir consultando la base antes de entrar al servicio. FR-002 y su prueba de cero consultas, transacciones y conexiones aplican únicamente a `QuoteQueryService.ranking` tras la autenticación. El objetivo menor a 50 ms se mide, en cambio, sobre la solicitud HTTP completa. No se modifican los filtros.

**Equivalencia de claves**: Cuando una carga por alias `active` resuelve el código efectivo, `RankingLoader` entrega el código y la página. `QuoteQueryService` usa esa única lectura para publicar el mismo `RankingPageResponse` bajo `active` y bajo el código explícito, con la misma generación, incluso si `items` es vacío. Así siguen pasando `QuoteQueryServiceCacheTest` y `QuoteQueryServiceRepositorySpyTest` sin editarlos. No se promete el camino inverso (explícita primero, alias después), porque exigiría resolver la activa antes del hit.

## R3. Fallback al fallar Redis

**Decision**: Distinguir la excepción del cargador de la falla del proveedor. Capturar el resultado producido por el `Callable` dentro de esa invocación; si falla la escritura posterior, devolverlo sin una segunda carga. Si Redis falla antes de invocarlo, cargar desde la base una vez. Propagar el error del cargador y liberar a los seguidores.

**Rationale**: `Cache.ValueRetrievalException` puede envolver fallos del `Callable`; tratarla como fallo de Redis y reintentar duplicaría el acceso y ocultaría el error real.

**Alternatives considered**: Un `try/catch RuntimeException` indiferenciado es simple, pero puede repetir la consulta y alterar errores de dominio.

Source: [Spring Cache.ValueRetrievalException](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/cache/Cache.ValueRetrievalException.html).

## R4. Precalentado y confirmación

**Decision**: `QuoteQueryService` mantiene una generación local monotónica en un `AtomicLong` y un ID inmutable de arranque para no reutilizar claves después de reiniciar. El formato es `estrategia|liga|posición|página|tamaño|gen=<id-arranque>:<contador>`; la solicitud captura la generación al entrar y sólo consulta esa clave. Cada evento incrementa la generación antes de `cache.clear()`. Una carga anterior que termina después de `clear()` escribe como máximo sus claves viejas, las elimina cuando detecta que quedó obsoleta y nunca publica bajo la nueva generación. Para la doble publicación del alias, comprobar la generación antes y después de escribir la clave explícita, y limpiar ambas claves viejas si cambió. El `AtomicLong` sólo conserva una versión; no hay caché de páginas en memoria. La garantía se limita a una instancia.

Invalidar antes de programar la recarga; usar listener poscommit para eventos dentro de transacción y ejecución inmediata si el evento de recálculo se publica fuera de una. Llamar a un bean distinto `RankingWarmer` por proxy asíncrono. Calentar la ruta sin `strategy` con la generación actual al ejecutar; si otra invalidación la supera, limpiar el intento antiguo y repetir con la generación vigente. Una prueba determinista debe bloquear la carga vieja hasta después del `clear()`, completar la nueva con datos posteriores al evento, soltar la antigua y verificar que sólo la clave vigente contiene esos datos y que la vieja se eliminó o quedó inaccesible mientras se limpia.

**Rationale**: `ActiveStrategyService.change` publica el evento dentro de su transacción; `QuoteCalculationService` publica el de finalización fuera de su transacción, después de las operaciones de persistencia. Un listener sólo poscommit sin `fallbackExecution` omitiría el segundo caso. `@Async` sobre una llamada al mismo objeto no se aplica por proxy.

**Alternatives considered**: Precalentar síncronamente retrasa la respuesta. Publicar antes del commit permite una página obsoleta. Calentar la clave explícita de la estrategia deja frío el alias que pide el frontend.

## R4a. Verificaciones constitucionales posteriores a publicación

**Decision**: GitHub Actions en `SUCCESS`, SonarCloud en `PASSED` y el ranking probado en Swagger UI con JWT y API key quedan como tareas manuales pendientes hasta que la usuaria confirme, haga commit/push y abra el PR. `$speckit-implement` termina antes de esas tareas y no realiza commit, push, PR ni verificaciones manuales en nombre de la usuaria.

**Rationale**: La constitución exige esas comprobaciones para el DoD, pero todavía no existe la publicación necesaria y el usuario reservó explícitamente esas acciones.

**Alternatives considered**: Marcarlas completadas por las pruebas locales ocultaría el estado real del DoD.

## R5. Estadísticas y evidencia de latencia

**Decision**: Activar `enableStatistics()` en el manager Redis ya existente y verificar un scrape real de Prometheus. El nombre interno de Micrometer es `cache.gets`; la exportación esperada es `cache_gets_total` con `cache="quotes-ranking"` y `result="hit"` o `"miss"`. Medir primera consulta y diez posteriores con `curl -w "%{time_total}"`.

**Rationale**: El caché del ranking se declara al iniciar, condición necesaria para que Boot lo instrumente. Las estadísticas Redis son locales a cada instancia. El objetivo local de cada consulta cacheada es menor a 50 ms.

**Alternatives considered**: Inferir hits desde tiempos HTTP no identifica fallos/aciertos; agregar contadores propios duplicaría la instrumentación existente.

Source: [Spring Boot cache metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html).

## R6. Índice condicional

**Decision**: No agregar índice en la etapa de planificación. Ejecutar en implementación `EXPLAIN (ANALYZE, BUFFERS)` sobre la consulta real de `findRanking` y su `countQuery` con la base de demo; conservar salida, población y tiempos en este archivo. Sólo si muestra un sort histórico o scan evitable, probar `(strategy_code, player_id, calculated_at DESC, id DESC)` y registrar antes/después. El sort final por `current_value DESC, player_id ASC` depende de valores calculados y no es evitable con ese índice.

**Evidencia disponible durante la planificación (superada por T025/T026)**: La entidad declaraba `(player_id, strategy_code, calculated_at)` y `(strategy_code, calculated_at)`. `RankingPerformanceE2ETest` tenía un EXPLAIN de una versión simplificada sobre 2.500 jugadores, pero no el resultado de la consulta completa. En la reanudación se ejecutaron los planes completos con acceso a PostgreSQL mediante `pgpass` y se documentó arriba la comparación antes/después.

**Alternatives considered**: Añadir el índice por intuición incumpliría el criterio de evidencia del pedido y podría aumentar el costo de escritura sin mejorar el ranking.
