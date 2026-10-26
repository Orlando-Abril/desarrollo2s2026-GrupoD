# Quickstart: Validación del ranking

Ejecutar desde `backend/`. Los unit/integration tests usan H2 2.4.240 en modo PostgreSQL y caché simple; E2E requiere PostgreSQL y Redis según `application-e2e.properties`.

## Evidencia de índices previa a la implementación

- No hay Flyway, Liquibase, `schema.sql` ni otro directorio de migraciones; producción usa `spring.jpa.hibernate.ddl-auto=update` y los tests `create-drop`.
- `PlayerQuote` declara `idx_player_quotes_player_strategy_time(player_id, strategy_code, calculated_at)` e `idx_player_quotes_strategy_time(strategy_code, calculated_at)`.
- `Player` sólo declara `idx_players_external_id`; las entidades no declaran índices para `players.league`, `players.team` ni `player_positions.position`.
- PostgreSQL local respondió en el puerto 5432, pero no fue posible autenticar la inspección de `pg_indexes`: no hay `DB_PASSWORD` disponible en el entorno y la contraseña E2E por defecto no corresponde a esa instancia. Por lo tanto, el esquema efectivo permanece sin verificar.

**Solicitud de autorización pendiente**: si el acceso al esquema confirma que faltan los índices de liga, equipo o posición, se solicita autorización explícita antes de modificar entidades, agregar migraciones o alterar PostgreSQL. Esta feature no agrega índices sin esa autorización.

## H2, contrato y seguridad

```powershell
./mvnw.cmd "-Dtest=PlayerQuoteRepositoryTest,RankingControllerTest,QuoteOpenApiTest" test
```

Debe validar actual/anterior, empates, filtros AND, multiposición sin duplicados, puestos antes de paginar, página fuera de rango, variaciones nulas/positivas/negativas, 400, 401 y OpenAPI completo.

## Caché e invalidación

```powershell
./mvnw.cmd "-Dtest=CacheConfigTest,QuoteQueryServiceCacheTest,QuoteQueryServiceRepositorySpyTest,QuoteCacheEvictorTest,QuoteCalculationServiceTest,QuoteRankingCacheIntegrationTest" test
```

Debe comprobar serializer tipado y TTL 1h; una sola consulta para dos llamadas equivalentes; miss al cambiar cada componente; invalidación total por evento; publicación tras éxito/fallo; disponibilidad ante falla de caché; y el recorrido integrado calentar caché → recalcular → invalidar → volver a consultar datos persistidos vigentes para ambos desenlaces.

## PostgreSQL real

```powershell
./mvnw.cmd "-Dtest=RankingQueryE2ETest,RankingPerformanceE2ETest" test
```

Debe reproducir la semántica H2 con datos manuales y sin fuentes externas. Con exactamente 2.500 jugadores, ejecutar 20 solicitudes HTTP completas y consecutivas de la misma página: la primera puede ser cache miss y las 19 restantes cache hits. Registrar los 20 tiempos y comprobar que al menos 19 sean menores a un segundo. Ejecutar además `EXPLAIN (ANALYZE, BUFFERS)` para evaluar la consulta sin caché. Si la inspección o medición evidencia un índice faltante, documentarlo y solicitar autorización antes de modificar el esquema.

## Smoke HTTP opcional

```powershell
curl.exe -H "X-API-KEY: <api-key>" "http://localhost:8080/players/ranking?strategy=OFFICIAL&league=PREMIER_LEAGUE&position=FORWARD&page=0&size=20"
```

La respuesta debe seguir [ranking-api.yaml](contracts/ranking-api.yaml), y `/players/{id}/quotes` debe continuar funcionando.

## Suite completa

```powershell
./mvnw.cmd test
```

Esperado: build exitoso sin cambios en `PlayerController` ni `GlobalExceptionHandler`.

## Evidencia local de implementación (2026-10-25)

- `PlayerQuoteRepositoryTest`: 27 tests H2 aprobados, incluidos la consulta nativa, historial actual/anterior, desempate, filtros y paginación.
- `RankingControllerTest`: 6 tests MockMvc aprobados con API key/JWT USER, orden, respuesta vacía, rutas coexistentes, filtros, paginado, porcentajes y errores 400/401.
- `QuoteOpenApiTest`: 3 tests aprobados; `percentageChange` aparece en `required` y como tipo nullable de OpenAPI 3.1 (`["number", "null"]`).
- Caché/eventos: 33 tests focalizados aprobados, incluido `@MockitoSpyBean PlayerQuoteRepository`, clave equivalente para estrategia omitida/activa, múltiples claves, fail-open, publicación terminal e invalidación integrada tras éxito y fallo.
- La corrida consolidada final de ranking, OpenAPI, repositorio, caché, evento e invalidación ejecutó 76 tests sin fallas ni omisiones.
- La suite local final recorrió 533 tests: 502 aprobaron, 2 fallaron y 29 quedaron omitidos por precondiciones ya existentes. Las dos assertions ajenas al ranking de `PlayerStatsServiceTest` fallan sólo al ejecutarse dentro de la suite completa por sustitución de caracteres Unicode en `CapturedOutput` bajo esta consola Windows; la clase aislada aprueba sus 39 tests. No se modificaron `PlayerController` ni `GlobalExceptionHandler`.
- `RankingQueryE2ETest` y `RankingPerformanceE2ETest` compilan, pero ambos quedaron omitidos porque Redis `localhost:6379` no está disponible. Por ello todavía no hay evidencia real PostgreSQL, 20 tiempos HTTP ni salida de `EXPLAIN (ANALYZE, BUFFERS)`; no se solicitó ni realizó ningún cambio de índice.
- La rama local `feature/ranking-jugadores` contiene cambios sin publicar. En consecuencia, todavía no existe una ejecución GitHub Actions ni un Quality Gate SonarCloud de esta implementación que puedan verificarse; ambas verificaciones permanecen pendientes para después del push/PR.
