# Research: Ranking de jugadores por cotización

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

## D1. CTE y funciones de ventana

**Decisión**: consulta nativa paginada. Un CTE filtra `strategy_code` y calcula por jugador `ROW_NUMBER()` y `LEAD(quote_value)` sobre `ORDER BY calculated_at DESC, id DESC`. Tras conservar `quote_order=1`, se aplican filtros y otro `ROW_NUMBER()` sobre `quote_value DESC, player_id ASC` asigna el puesto antes del `LIMIT/OFFSET`. Habrá `countQuery` explícito.

**Rationale**: `LEAD` obtiene la inmediatamente anterior, aun de otra versión, y el id resuelve empates temporales. PostgreSQL y H2 2.4.240/PG mode soportan CTE, `ROW_NUMBER`, `LEAD` y `EXISTS`.

**Alternativas consideradas**: JPQL con `NOT EXISTS` halla la última pero no puede proyectar una subconsulta escalar para la anterior; joins HQL no son JPQL portable; N+1 incumple rendimiento; subconsultas nativas correlacionadas son más complejas y costosas.

## D2. Filtros y posiciones

**Decisión**: filtrar liga en `players` y posición con `EXISTS` sobre `player_positions`, después de identificar la serie. La proyección entrega escalares e IDs; el servicio carga en bloque los jugadores de la página y toma todas sus posiciones.

**Rationale**: unir posiciones multiplica jugadores y rompe puesto/total. `array_agg`/`string_agg` no es portable entre los dos motores.

**Alternativas consideradas**: join con `DISTINCT`, que complica ventanas; agregación SQL de posiciones, que acopla tipos al motor.

## D3. Paginación fija

**Decisión**: usar `Pageable` sin `Sort`; el SQL fija el orden y calcula puestos antes del offset. La respuesta concreta expone sólo `items`, `page`, `size`, `totalElements`, `totalPages`.

**Rationale**: un sort externo separaría orden visible y `rank`. `PageImpl` expone metadatos extra y es frágil al deserializar desde Redis.

**Alternativas consideradas**: `Page<RankingEntryResponse>` como contrato/caché.

## D4. Variación en Java

**Decisión**: proyectar valores actual/anterior y calcular de forma determinista `actual.subtract(anterior).multiply(100).divide(anterior, 2, HALF_UP)`; devolver `null` sin anterior o con anterior cero.

**Rationale**: mantiene negocio fuera del SQL y evita diferencias de división/redondeo por motor.

**Alternativas consideradas**: cálculo SQL.

## D5. Validación y rutas

**Decisión**: agregar `@GetMapping("/players/ranking")` a `QuoteController`. Antes de fijar la firma, inspeccionar y seguir las convenciones existentes para request params, enums, errores y paginación. El controller se ocupa del contrato HTTP; `QuoteQueryService` resuelve la estrategia efectiva y reglas de negocio. Usar el mecanismo estándar `Pageable` de Spring salvo que exista otro patrón local. Reusar excepciones/advice de cotizaciones.

**Rationale**: ranking tiene dos segmentos y `/players/{id}/quotes` tres; no colisionan. Texto explícito mejora los 400. No se tocan controllers/handler protegidos.

**Alternativas consideradas**: duplicar validación en controller y service; imponer una conversión o firma sin contrastarla con las convenciones del proyecto.

## D6. Caché manual tipado y fail-open

**Decisión**: registrar `quotes-ranking` con TTL de una hora y `JacksonJsonRedisSerializer<RankingPageResponse>`. `QuoteQueryService.ranking` resuelve la estrategia efectiva y arma una clave String canónica con estrategia, liga/nulo, posición/nulo, página y tamaño. Usa `CacheManager` y captura/loguea fallas de get/put.

**Rationale**: estrategia ausente y activa explícita comparten entrada. El DTO ofrece round-trip tipado; el manejo manual sigue el precedente de `PlayerStatsService` y evita auto-invocación/proxy.

**Alternativas consideradas**: `@Cacheable` complica la estrategia efectiva y propaga errores Redis por defecto; `SimpleKey` no documenta canonicalización; cachear `PageImpl` es inestable.

## D7. Evento terminal

**Decisión**: crear `QuoteRunCompletedEvent(runId)` y publicarlo síncronamente en el `finally` de toda corrida aceptada, antes de liberar la guardia, en éxito o fallo. No se publica por estrategia inválida ni 409. `QuoteCacheEvictor` escucha, limpia todo `quotes-ranking` y atrapa/loguea fallas.

**Rationale**: la 016 aún no publica el evento. El listener síncrono normalmente termina antes de concluir `recalculate`; el fail-open evita enmascarar el resultado original.

**Alternativas consideradas**: invalidar en controller omite scheduler; sólo éxito contradice contrato; `@CacheEvict` sin control puede tapar errores.

## D8. Índices y portabilidad

**Decisión**: reutilizar `idx_player_quotes_player_strategy_time` e `idx_player_quotes_strategy_time`. Probar semántica con `@DataJpaTest @UnitTestProfile` en H2/PG mode y `@E2ETest` en PostgreSQL real. No agregar ahora el índice ideal protegido.

**Rationale**: los índices actuales cubren historial/filtro al volumen previsto. H2 prueba función, no planner PostgreSQL; por eso ambos tests son obligatorios. Si no se cumple SC-006, se pide autorización.

**Alternativas consideradas**: `INCLUDE` específico de PostgreSQL; cambio de esquema sin permiso.

## D9. Pruebas

**Decisión**: MockMvc con contexto real, `@UnitTestProfile`, H2 y datos manuales para orden, variación, filtros, páginas, 400 y 401. Tests de repositorio en H2 y E2E PostgreSQL. Con `@MockitoSpyBean`, dos llamadas equivalentes ejecutan una sola consulta; claves distintas hacen miss. Eventos y corridas exitosas/fallidas prueban limpieza total.

**Rationale**: sigue los patrones de `QuoteHistoryControllerTest`, `QuoteCalculationServiceTest` y el uso existente de spies, probando SQL real.

**Alternativas consideradas**: controller standalone o sólo H2, insuficientes para integración/caché/portabilidad.
