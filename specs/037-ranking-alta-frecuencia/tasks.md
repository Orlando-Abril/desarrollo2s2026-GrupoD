# Tasks: Ranking de alta frecuencia

**Input**: `specs/037-ranking-alta-frecuencia/spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/ranking-api.md` y `quickstart.md`  
**Tests**: Solicitados expresamente para hit sin trabajo de base, 20 hilos, precalentado, fallback y regresión.  
**Organization**: Una fase por historia, en orden de prioridad. Los archivos de entregas anteriores fuera del alcance autorizado no se editan.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo con otras tareas marcadas que usen archivos distintos y no dependan de tareas incompletas.
- **[Story]**: Historia de usuario a la que pertenece la tarea.
- Las rutas son relativas a la raíz del repositorio.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Registrar una línea de base del proyecto ya existente, sin reinicializar dependencias ni modificar entregas anteriores.

- [X] T001 Ejecutar los tests actuales `QuoteQueryServiceCacheTest`, `QuoteQueryServiceRepositorySpyTest`, `QuoteCacheEvictorTest`, `QuoteRankingCacheIntegrationTest` y `RankingControllerTest` desde `backend/mvnw.cmd`; anotar resultados y cualquier precondición de servicios en `specs/037-ranking-alta-frecuencia/research.md`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Fijar la compatibilidad del camino sin estrategia antes de cambiar el servicio.

- [X] T002 Verificar en `specs/037-ranking-alta-frecuencia/research.md` el diseño ya fijado: una carga por `active` publica el mismo `RankingPageResponse` bajo alias y código explícito de la estrategia resuelta dentro de la misma generación, incluso vacío y sin segunda consulta; la carga explícita primero no promete equivalencia inversa. Conservar sin cambios `backend/src/test/java/com/example/demo/service/valuation/QuoteQueryServiceCacheTest.java` y `QuoteQueryServiceRepositorySpyTest.java`.

**Checkpoint**: El alias, la equivalencia con la estrategia explícita y la compatibilidad de pruebas existentes están definidos antes de implementar.

---

## Phase 3: User Story 1 - Consultar el ranking repetidamente (Priority: P1) 🎯 MVP

**Goal**: Un hit del servicio, después de autenticar, devuelve la página correcta sin invocar repositorios, abrir transacción ni adquirir conexión de base atribuible al ranking.

**Independent Test**: Dos consultas sin parámetros producen el mismo contrato y orden; la segunda es un hit. La duración se mide sobre el HTTP completo y el trabajo de base se inspecciona sólo dentro de `QuoteQueryService.ranking`.

### Tests for User Story 1

- [X] T003 [P] [US1] Añadir `backend/src/test/java/com/example/demo/service/valuation/RankingHitBoundaryTest.java` para probar que, con página en caché y autenticación ya completada, `QuoteQueryService.ranking` no llama a repositorios, no tiene transacción activa y no adquiere conexión del pool atribuible al servicio, tanto con estrategia omitida como explícita.
- [X] T004 [P] [US1] Añadir `backend/src/test/java/com/example/demo/service/valuation/RankingEmptyAliasTest.java` para comprobar que una carga por `active` publica el mismo `RankingPageResponse` vacío también bajo la clave explícita resuelta, en la misma generación y con una sola consulta; no probar equivalencia inversa.
- [X] T005 [P] [US1] Añadir `backend/src/test/java/com/example/demo/controller/RankingHighFrequencyContractTest.java` para comparar respuesta, orden, paginación y seguridad de `GET /players/ranking` con `specs/018-ranking-jugadores/contracts/ranking-api.yaml`; cubrir página vacía, filtros y `percentageChange` nulo.

### Implementation for User Story 1

- [X] T006 [US1] Crear `backend/src/main/java/com/example/demo/service/valuation/RankingLoader.java` como componente separado con método público `@Transactional(readOnly = true)`; mover allí `findRanking`, carga de jugadores y armado de `RankingPageResponse`, devolver además el código de estrategia efectivo para publicar ambas claves y conservar `items` vacío válido, `page >= 0`, `1 <= size <= 100`, `totalElements >= 0`, `totalPages` consistente y el orden valor descendente/ID ascendente.
- [X] T007 [US1] Cambiar `backend/src/main/java/com/example/demo/service/valuation/QuoteQueryService.java`: quitar `@Transactional` sólo de `ranking`, capturar la generación local monotónica al entrar, formar la clave `estrategia|liga|posición|página|tamaño|gen=<id-arranque>:<contador>` antes del hit, usar alias `active` si falta `strategy` y delegar el miss a `RankingLoader` por proxy; publicar desde alias la misma página también bajo la clave explícita resuelta, aun vacía y sin segunda consulta, sin prometer equivalencia inversa.
- [X] T008 [US1] Ejecutar `RankingHitBoundaryTest`, `RankingEmptyAliasTest`, `RankingHighFrequencyContractTest`, `QuoteQueryServiceCacheTest`, `QuoteQueryServiceRepositorySpyTest` y `RankingControllerTest` desde `backend/mvnw.cmd`; resolver incompatibilidades sólo en `backend/src/main/java/com/example/demo/service/valuation/QuoteQueryService.java` y `RankingLoader.java`, sin editar tests de entregas anteriores.

**Checkpoint**: El ranking funciona sin transacción ni base en hits del servicio; el contrato y las pruebas existentes siguen válidos.

---

## Phase 4: User Story 2 - Atender consultas simultáneas (Priority: P1)

**Goal**: Veinte solicitudes de la misma clave ausente a una instancia comparten un solo cálculo; claves distintas no mezclan resultados.

**Independent Test**: Veinte hilos simultáneos con caché vacío reciben respuestas equivalentes y `PlayerQuoteRepository.findRanking` se invoca una vez; un fallo del líder libera a todos y permite reintento.

### Tests for User Story 2

- [X] T009 [P] [US2] Añadir `backend/src/test/java/com/example/demo/service/valuation/RankingSingleFlightTest.java` con 20 hilos sincronizados de una misma instancia, caché vacío y contador de `findRanking`; verificar un cálculo, respuestas iguales y aislamiento de claves con liga, posición, página o tamaño distintos, sin exigir coordinación entre réplicas.
- [X] T010 [P] [US2] Añadir `backend/src/test/java/com/example/demo/service/valuation/RankingSingleFlightFailureTest.java` para probar que un error del cargador libera seguidores, no queda cacheado, no se confunde con caída de Redis y un pedido posterior reintenta; cubrir resultado de ranking vacío.

### Implementation for User Story 2

- [X] T011 [US2] Implementar en `backend/src/main/java/com/example/demo/service/valuation/QuoteQueryService.java` coordinación temporal por clave que incluye la generación capturada y se retira en éxito y error; dentro de una instancia el líder usa `Cache.get(key, Callable)` con `RankingLoader`, los seguidores esperan el mismo resultado y claves diferentes progresan independientemente, sin caché de páginas en memoria ni coordinación distribuida.
- [X] T012 [US2] Ejecutar `RankingSingleFlightTest`, `RankingSingleFlightFailureTest` y `QuoteQueryServiceCacheTest` desde `backend/mvnw.cmd`; verificar 20 respuestas/una consulta con la configuración actual de `RedisCacheWriter`, sin modificar `backend/src/main/java/com/example/demo/config/CacheConfig.java` para bloqueo.

**Checkpoint**: El miss simultáneo de una instancia se calcula una sola vez por clave y sus errores no bloquean nuevos intentos.

---

## Phase 5: User Story 3 - Ver el ranking actualizado después de cambios (Priority: P2)

**Goal**: Tras recálculo o cambio confirmado de estrategia, invalidar y precalentar en segundo plano la página 0, tamaño 20, sin filtros, de la estrategia activa.

**Independent Test**: Cada evento deja obsoleta la página anterior; la operación responde sin esperar el cálculo y Awaitility confirma el retorno de la página vigente al caché.

### Tests for User Story 3

- [X] T013 [P] [US3] Añadir `backend/src/test/java/com/example/demo/service/valuation/RankingWarmerEventTest.java` para verificar con Awaitility la recarga de `active|-|-|0|20|gen=<actual>` después de `QuoteRunCompletedEvent` fuera y dentro de transacción y de `ActiveStrategyChangedEvent` sólo tras commit; comprobar que la respuesta del originador no espera.
- [X] T014 [P] [US3] Añadir `backend/src/test/java/com/example/demo/service/valuation/RankingWarmerRaceTest.java` con barreras deterministas: bloquear una carga vieja, incrementar generación y ejecutar `clear()`, completar la carga nueva con datos posteriores al evento, liberar la vieja y comprobar que sólo la clave vigente contiene datos nuevos y que las claves viejas alias/explícita se eliminan o quedan inaccesibles mientras se limpian; cubrir error asíncrono sin afectar al originador.

### Implementation for User Story 3

- [X] T015 [US3] Si no hay habilitación existente, crear `backend/src/main/java/com/example/demo/config/RankingAsyncConfig.java` con `@EnableAsync`, sin alterar configuración de entregas anteriores ni añadir un executor salvo necesidad demostrada.
- [X] T016 [US3] Crear `backend/src/main/java/com/example/demo/service/valuation/RankingWarmer.java` como bean `@Async` que llame por proxy a `QuoteQueryService.ranking(null, null, null, PageRequest.of(0, 20))` con la generación actual al ejecutar; si la carga queda superada, limpiar y repetir con la vigente, registrando errores sin propagarlos.
- [X] T017 [US3] Cambiar `backend/src/main/java/com/example/demo/service/valuation/QuoteCacheEvictor.java` para incrementar la generación local de `QuoteQueryService` antes de `cache.clear()` y luego despachar `RankingWarmer`; usar fase posterior al commit para eventos dentro de transacción y ejecutar también el recálculo publicado fuera de ella, manteniendo invalidación síncrona y constructor usado por pruebas previas.
- [X] T018 [US3] Completar en `backend/src/main/java/com/example/demo/service/valuation/QuoteQueryService.java` la invalidación de cargas en curso: una carga vieja sólo escribe claves de su generación; al terminar compara con `AtomicLong`, elimina claves alias y explícita obsoletas y no publica resultado vigente; comprobar la generación antes y después de la segunda publicación, sin caché de páginas en memoria ni cambio de TTL.
- [X] T019 [US3] Ejecutar `RankingWarmerEventTest`, `RankingWarmerRaceTest`, `QuoteCacheEvictorTest`, `ActiveStrategyRecalculationTest` y `QuoteRankingCacheIntegrationTest` desde `backend/mvnw.cmd`; corregir sólo archivos de producción autorizados y tests nuevos de esta feature.

**Checkpoint**: Ambas actualizaciones dejan la página frontal vigente y no esperan el precalentado.

---

## Phase 6: User Story 4 - Mantener servicio y medir la mejora (Priority: P2)

**Goal**: Responder desde la base cuando Redis falla y mostrar aciertos/fallos del caché junto con tiempos HTTP reproducibles.

**Independent Test**: Con Redis caído, el ranking conserva respuesta y orden; con Redis disponible, Prometheus muestra hit y miss y el quickstart registra primera solicitud y diez posteriores.

### Tests for User Story 4

- [X] T020 [P] [US4] Añadir `backend/src/test/java/com/example/demo/service/valuation/RankingRedisFallbackTest.java` para simular fallo de lectura y escritura Redis, comprobar respuesta desde base, evitar segunda consulta si falló la escritura después del `Callable` y propagar errores del cargador sin tratarlos como fallo de caché.
- [X] T021 [P] [US4] Añadir `backend/src/test/java/com/example/demo/config/RankingCacheMetricsTest.java` para comprobar que `quotes-ranking` tiene estadísticas habilitadas y que hit/miss se distinguen al consultar las métricas, sin cambiar TTL ni serializador.

### Implementation for User Story 4

- [X] T022 [US4] Completar en `backend/src/main/java/com/example/demo/service/valuation/QuoteQueryService.java` el fallback ante Redis caído: si `Cache.get(key, Callable)` falla antes de cargar, leer una vez desde base; si falla tras cargar, devolver el valor capturado; propagar `Cache.ValueRetrievalException` del cargador y liberar seguidores.
- [X] T023 [US4] Añadir únicamente `.enableStatistics()` al `RedisCacheManager` de `backend/src/main/java/com/example/demo/config/CacheConfig.java`; conservar el TTL de una hora y los serializadores actuales.
- [ ] T024 [US4] Ejecutar `RankingRedisFallbackTest`, `RankingCacheMetricsTest`, `CacheConfigTest` y `QuoteRankingCacheIntegrationTest` desde `backend/mvnw.cmd`; seguir `specs/037-ranking-alta-frecuencia/quickstart.md` para registrar la primera consulta y diez siguientes con `curl -w '%{time_total}'`, verificar <50 ms por hit sobre el HTTP completo y confirmar en `/actuator/prometheus` el nombre y tags reales de hit/miss.

**Checkpoint**: Redis caído no interrumpe el ranking y la mejora tiene evidencia visible.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Evidencia SQL, regresión final y límites de alcance.

- [ ] T025 Ejecutar sobre la base de demo `EXPLAIN (ANALYZE, BUFFERS)` de la SQL completa y el `countQuery` de `backend/src/main/java/com/example/demo/repository/PlayerQuoteRepository.java`; registrar parámetros, población, planes, buffers y tiempos iniciales en `specs/037-ranking-alta-frecuencia/research.md`, sin confundir el sort final por valor calculado con uno evitable. Si faltan credenciales, dejar explícito que no existe evidencia para crear índice.
- [ ] T026 Sólo si T025 demuestra sort histórico o scan evitable, probar el índice `(strategy_code, player_id, calculated_at DESC, id DESC)` en la base de demo, medir con los mismos datos y conservarlo en `backend/src/main/java/com/example/demo/model/PlayerQuote.java` únicamente si la comparación antes/después de `specs/037-ranking-alta-frecuencia/research.md` acredita mejora sin regresión material; de otro modo dejar el modelo intacto y documentar el rechazo.
- [X] T027 Ejecutar la suite focalizada y `./mvnw.cmd test` desde `backend/mvnw.cmd`; anotar resultado en `specs/037-ranking-alta-frecuencia/quickstart.md`, comprobar `QuoteRankingCacheIntegrationTest`, el contrato/OpenAPI, logs JSON, correlation ID, health y métricas de latencia/errores de §5.3.
- [X] T028 Revisar el diff contra `specs/037-ranking-alta-frecuencia/spec.md` y `plan.md`; registrar en `specs/037-ranking-alta-frecuencia/research.md` que JWT/API key no cambiaron, que la prueba de cero acceso a base aplica sólo al servicio y que la latencia <50 ms aplica al HTTP completo; confirmar que ningún archivo previo fuera del alcance autorizado se modificó.

## Phase 8: Verificaciones manuales posteriores a publicación

**Purpose**: Gates constitucionales pendientes hasta que la usuaria confirme, haga commit/push y abra el PR. `$speckit-implement` se detiene antes de esta fase: no hace commit, push, PR ni ejecuta o marca estas tareas.

- [ ] T029 Verificación manual posterior al PR de la usuaria: comprobar GitHub Actions en `SUCCESS` y registrar enlace y resultado en `specs/037-ranking-alta-frecuencia/quickstart.md`; permanece pendiente hasta confirmación.
- [ ] T030 Verificación manual posterior al PR de la usuaria: comprobar SonarCloud Quality Gate en `PASSED` y registrar enlace y resultado en `specs/037-ranking-alta-frecuencia/quickstart.md`; permanece pendiente hasta confirmación.
- [ ] T031 Verificación manual posterior al PR de la usuaria: probar `GET /players/ranking` en Swagger UI con JWT y con `X-API-KEY`, y registrar resultados en `specs/037-ranking-alta-frecuencia/quickstart.md`; permanece pendiente hasta confirmación.

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1 → Phase 2 → User Story 1.
- User Story 2 usa el camino de lectura de User Story 1.
- User Story 3 usa la ruta de ranking y su alias de User Story 1; sus pruebas de carrera también usan la coordinación de User Story 2.
- User Story 4 usa el camino de `Cache.get(key, Callable)` de User Story 2.
- Polish requiere las cuatro historias; T026 depende de la evidencia de T025.
- La fase manual T029–T031 depende de confirmación, commit/push y PR de la usuaria; `$speckit-implement` termina en T028 y deja esas tres tareas sin marcar.

### User Story Dependencies

- **US1 (P1)**: primer incremento funcional; depende de T002.
- **US2 (P1)**: depende de US1; prueba la carga compartida de una instancia.
- **US3 (P2)**: depende de US1 y US2 para evitar resultados obsoletos durante recargas.
- **US4 (P2)**: depende de US2; sus pruebas de fallback pueden prepararse mientras se implementa US3.

### Parallel Opportunities

- T003, T004 y T005 usan archivos nuevos distintos y pueden prepararse en paralelo.
- T009 y T010 usan archivos nuevos distintos y pueden prepararse en paralelo.
- T013 y T014 usan archivos nuevos distintos y pueden prepararse en paralelo.
- T020 y T021 usan archivos nuevos distintos y pueden prepararse en paralelo.
- Después de US2, las pruebas de US3 y US4 pueden prepararse en paralelo; sus cambios a `QuoteQueryService.java` deben integrarse de forma secuencial.

## Parallel Example: User Story 1

```text
T003: RankingHitBoundaryTest.java — límite del servicio sin repositorios, transacción ni conexión.
T004: RankingEmptyAliasTest.java — misma página vacía bajo alias y código explícito.
T005: RankingHighFrequencyContractTest.java — respuesta HTTP y seguridad sin cambios.
```

## Parallel Example: User Story 2

```text
T009: RankingSingleFlightTest.java — 20 hilos y aislamiento de claves.
T010: RankingSingleFlightFailureTest.java — error, liberación de esperas y reintento.
```

## Parallel Example: User Story 3

```text
T013: RankingWarmerEventTest.java — eventos y commit.
T014: RankingWarmerRaceTest.java — carga vieja terminada después de `clear()`.
```

## Parallel Example: User Story 4

```text
T020: RankingRedisFallbackTest.java — caída de Redis.
T021: RankingCacheMetricsTest.java — hits y misses.
```

## Implementation Strategy

### MVP First (User Story 1)

1. Completar T001-T002 y US1; verificar el hit en el límite del servicio y la regresión del contrato.
2. Añadir US2 para la garantía de carga única bajo 20 hilos.
3. Añadir US3 para invalidación y precalentado poscommit.
4. Añadir US4 para fallback, métricas y medición HTTP.
5. Ejecutar T025–T028. El índice sólo se materializa tras evidencia antes/después.
6. Detener `$speckit-implement`; T029–T031 son comprobaciones manuales posteriores a la confirmación, commit/push y PR de la usuaria y permanecen pendientes.

## Notes

- Las pruebas se escriben antes de cada cambio de producción y deben demostrar el comportamiento faltante.
- No modificar filtros JWT/API key, controller, DTO, repositorio SQL, TTL, paginación ni archivos de entregas anteriores fuera del alcance autorizado.
- `CacheConfig.java` sólo habilita estadísticas. La coordinación por clave reside en `QuoteQueryService.java`; no usar `@Cacheable(sync = true)` como sustituto de la prueba de 20 hilos.
- Si faltan credenciales de PostgreSQL/Redis para evidencia local, registrar la limitación; no afirmar mediciones ni crear el índice sin `EXPLAIN` comparativo.
- La generación local usa un `AtomicLong` sólo como versión y un ID inmutable de arranque; no guarda páginas. Las claves se comparten sólo entre llamadas de la misma instancia. El quickstart borra únicamente `quotes-ranking::active|-|-|0|20|gen=*` mediante `SCAN` y `DEL`.
