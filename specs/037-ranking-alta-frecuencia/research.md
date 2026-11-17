# Research: ranking de alta frecuencia

## Evidencia de implementación

**Línea de base (2026-11-17)**: `QuoteQueryServiceCacheTest` (2), `QuoteQueryServiceRepositorySpyTest` (1), `QuoteCacheEvictorTest` (3), `QuoteRankingCacheIntegrationTest` (1) y `RankingControllerTest` (7): 14 pruebas, 0 fallos, 0 errores. Maven requirió acceso a dependencias fuera del sandbox.

**Disponibilidad de la demo (2026-11-17)**: PostgreSQL acepta conexiones TCP en `localhost:5432`, pero `DB_PASSWORD` no está definido y `psql` no está instalado. Redis no escucha en `localhost:6379`, no hay `redis-cli`, el backend no responde en `localhost:8080` y `RANKING_API_KEY` no está definida. Por ello todavía no hay tiempos HTTP, scrape de Prometheus ni `EXPLAIN (ANALYZE, BUFFERS)` de la SQL completa y del `countQuery` con la población de demo. La prueba automatizada de estadísticas comprueba los contadores locales del `RedisCache`; no sustituye el scrape real. No se creó el índice candidato porque no existe comparación antes/después que lo justifique.

**Revisión de alcance**: El diff de producción modifica solamente `QuoteQueryService`, `QuoteCacheEvictor` y `CacheConfig` en los puntos autorizados, y añade `RankingLoader`, `RankingWarmer` y `RankingAsyncConfig`. Los filtros JWT y API key, el controlador, DTO, repositorio SQL, TTL y paginación siguen intactos. La prueba de ausencia de acceso a base aplica al servicio de ranking después de autenticar; el objetivo de 50 ms corresponde al HTTP completo, incluida la autenticación. La configuración existente conserva logs JSON con `correlationId`, health y métricas HTTP de latencia y errores. La prueba focalizada dio 27 pruebas sin fallos ni errores antes de las últimas ampliaciones de pruebas.

**Verificación final de código**: `./mvnw.cmd test -q` cerró con 1.076 pruebas, 0 fallos, 0 errores y 49 omitidas según sus condiciones de entorno. Tras esa ejecución se reforzaron dos pruebas; la pasada focalizada final de eventos y concurrencia cerró con 8 pruebas, 0 fallos y 0 errores. `git diff --check` no señaló problemas de espacios. `QuoteRankingCacheIntegrationTest` y `ActiveStrategyRecalculationTest` pasaron dentro de la suite completa. El índice histórico permanece sin crear por falta de evidencia SQL sobre la base de demo.

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

**Evidence available now**: La entidad declara `(player_id, strategy_code, calculated_at)` y `(strategy_code, calculated_at)`. `RankingPerformanceE2ETest` ya tiene un EXPLAIN de una versión simplificada sobre 2.500 jugadores, pero no registra un resultado actual de la consulta completa. PostgreSQL responde en `localhost:5432`, pero la aplicación requiere `DB_PASSWORD`, ausente en esta sesión, y la contraseña E2E predeterminada no autentica esa instancia según la evidencia previa de la feature 018. Por eso no se ejecutó `EXPLAIN ANALYZE` ni existe una medición antes/después que permita justificar un índice.

**Alternatives considered**: Añadir el índice por intuición incumpliría el criterio de evidencia del pedido y podría aumentar el costo de escritura sin mejorar el ranking.
