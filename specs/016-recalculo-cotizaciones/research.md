# Research: Recálculo de cotizaciones

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Cada decisión tiene: Decisión, Motivo y Alternativas descartadas.

## D1. La rama tiene que incluir la feature 015

**Decisión**: antes de implementar, `feature/recalculo-cotizaciones` incorpora `origin/main` (merge `d4252c7` de 015). La rama hoy está en `78c17bc` y no tiene `ValuationAlgorithm`, `ValuationInput`, `QuoteResult`, `PopulationEntry`, `NormalizedPopulation`, `MetricNormalizer` ni `OfficialPerformanceAlgorithm`.

**Motivo**: la corrida depende de ese contrato. Todo el plan se escribió contra el código de `origin/main`.

**Alternativas descartadas**: copiar las clases a la rama, que generaría conflictos en el merge.

## D2. Cómo arma la corrida la población sin castear

**Decisión**: `ValuationAlgorithm` gana un método default:

```java
default NormalizedPopulation preparePopulation(Collection<PopulationEntry> entries) { return null; }
```

`OfficialPerformanceAlgorithm` lo sobrescribe y delega en su `normalize(entries)`, que ya existe. La corrida llama a `preparePopulation` una vez por estrategia, sin saber qué algoritmo es.

**Motivo**: OFFICIAL necesita la población y MOMENTUM (019) puede no necesitarla. El default `null` mantiene compatible a cualquier implementación futura, y `ValuationInput` ya admite `population` null. Agregar un default no rompe los tests de 015.

**Alternativas descartadas**:
- `instanceof OfficialPerformanceAlgorithm` en la corrida: acopla la corrida a OFFICIAL.
- Que `calculate` arme la población: la armaría una vez por jugador, contra FR-006.

## D3. Elegir el algoritmo de cada estrategia

**Decisión**: `QuoteCalculationService` recibe `List<ValuationAlgorithm>` y arma un `Map<StrategyType, ValuationAlgorithm>`. Si dos beans declaran el mismo tipo, falla al arrancar. Una estrategia del catálogo cuyo `type()` no está en el mapa se saltea con motivo `no_algorithm`.

**Motivo**: hoy sólo hay un bean (OFFICIAL). Cuando 019 agregue MOMENTUM como `@Component`, entra en el mapa sin cambiar la corrida.

**Alternativas descartadas**: inyectar `OfficialPerformanceAlgorithm` directo, que obligaría a cambiar la corrida en 019.

## D4. Cotizaciones previas que recibe el algoritmo

**Decisión**: antes de calcular, en una transacción de sólo lectura, la corrida carga por estrategia `PlayerQuoteRepository.findLatestForAllPlayers(code)` (ya existe, desde 013) y la indexa por jugador. Cada `ValuationInput` recibe como `previousQuotes` una lista con esa única cotización, o vacía. `windows` va vacío.

**Motivo**:
- OFFICIAL sólo usa `previousQuotes.get(0)` (el tope de variación).
- La regla de NO_STATS / INSUFFICIENT_HISTORY también usa sólo la última.
- Una sola consulta por estrategia, en lugar de una por jugador. Para unos 2.500 jugadores evita 2.500 consultas.
- La consulta filtra por código (todas las versiones) y desempata por id mayor, como pide FR-007.
- Una lista de un elemento cumple el contrato de `ValuationInput` (sólo esa estrategia, de la más nueva a la más vieja). Como se carga antes de calcular, nunca incluye cotizaciones de la corrida en curso.

**Alternativas descartadas**: cargar todo el historial de cada jugador. Hoy ningún algoritmo lo usa. La feature 019 (MOMENTUM) amplía esta carga a las últimas N cotizaciones y a las ventanas de 014, cuando las necesite.

## D5. Instante, id de corrida y correlation id

**Decisión**:
- `runId = UUID.randomUUID()`.
- `calculatedAt = clock.instant()` con el bean `Clock` de `config/ClockConfig` (014). Se toma una sola vez al comenzar.
- El correlation id sale de `MDC.get(CorrelationIdFilter.MDC_KEY)`. Si no hay, se genera uno y se restaura el MDC al terminar, igual que `StatsSnapshotService.snapshotAll`.

**Motivo**: el `Clock` inyectado permite tests con `Clock.fixed`. Reusar el patrón de 014 deja un solo comportamiento de MDC en el proyecto. En un pedido HTTP, `CorrelationIdFilter` siempre pone el valor, así que cotizaciones, logs y header de respuesta comparten el mismo id. La corrida programada (017) cae en la rama que lo genera.

## D6. Exclusión de corridas simultáneas: guardia en memoria

**Decisión**: un `AtomicBoolean running` en `QuoteCalculationService`. Al entrar, `compareAndSet(false, true)`. Si falla, lanza `RecalculationInProgressException` (nueva), que se mapea a 409. Se libera en `finally`. La estrategia pedida se resuelve **antes** de tomar la guardia: un código inexistente da 400 aunque haya una corrida en curso, y nunca toma la guardia.

**Motivo**: el despliegue de la materia es una sola instancia. `compareAndSet` rechaza sin esperar (FR-019) y `finally` libera ante cualquier error (FR-020). El mismo método lo usará 017 (FR-036), así que la exclusión vale para corridas manuales y programadas.

**Alternativas descartadas**:
- `ReentrantLock.tryLock()`: es equivalente, pero ata la liberación al mismo hilo sin necesidad.
- Un lock en la base (fila de corridas o advisory lock de PostgreSQL): sólo hace falta con varias instancias. Además agrega una tabla o depende de PostgreSQL, y H2 no lo tiene en los tests. Si el sistema se escala a más instancias, se revisa.
- `synchronized`: el segundo pedido esperaría en lugar de recibir 409.

## D7. Transacciones

**Decisión**:
1. `recalculate` **no** es `@Transactional`.
2. `snapshotAll(runId)` maneja sus propias transacciones (REQUIRES_NEW por lote). Si lanza una excepción, la corrida termina antes de cargar nada.
3. La carga se hace en una sola transacción de sólo lectura (`TransactionTemplate` con `readOnly = true`). Incluye jugadores, estadísticas, población por estrategia y últimas cotizaciones por estrategia.
4. Cada jugador tiene una transacción propia con `TransactionOperations.executeWithoutResult`, igual que `PlayerStatsService`. Adentro hace tres cosas:
   - `playerRepository.findById(id)` (entidad administrada);
   - `quoteRepository.saveAll(quotes)`;
   - `player.setMarketValue(value)` si la estrategia activa se calculó. El cambio se guarda por dirty checking al confirmar.
5. Cualquier `RuntimeException` dentro de la transacción de un jugador la revierte entera: el jugador no queda con cotizaciones ni con precio nuevo. Se loguea y se suma a fallidos, y la corrida sigue.

**Motivo**: la atomicidad por jugador (FR-015) y el aislamiento de fallas (FR-016) piden una transacción por jugador. No se usa una transacción global, porque una falla revertiría a todos. El cálculo es puro, así que puede ir dentro de la transacción del jugador: si falla la segunda estrategia, también se descarta la primera (escenario US5-1). Escribir `marketValue` con el setter de Lombok y el `findById` heredado de `JpaRepository` no cambia `PlayerRepository`.

**Alternativas descartadas**:
- Calcular afuera y guardar adentro: no cambia el resultado y obliga a pasar resultados intermedios.
- Un `@Transactional(REQUIRES_NEW)` en otro bean: la llamada interna desde el mismo bean no pasa por el proxy.

## D8. Valor de NO_STATS e INSUFFICIENT_HISTORY

**Decisión**: si el resultado no es `CALCULATED`, `value = latest.getValue()` cuando hay última cotización (D4) y `definition.baseValue().setScale(4)` cuando no hay. `score = null`. El estado se copia del resultado.

**Motivo**: es lo que piden FR-009 y el contrato de `QuoteResult` de 015. Tomar `baseValue` de la definición y no de una constante deja que una versión futura con otro valor base funcione sin cambios.

## D9. `Player.marketValue` pasa a 4 decimales

**Decisión**: `@Column(name = "market_value", nullable = false, precision = 12, scale = 4)`, igual que `PlayerQuote.value`. Es el cambio de Entrega 1 que el equipo autorizó.

**Motivo**: OFFICIAL v1 da valores en [1.00, 1.10] (`baseValue` 1.00, `scaleFactor` 0.10). Con 2 decimales sólo habría 11 precios posibles, el ranking tendría empates masivos y el precio de mercado no coincidiría con la cotización guardada.

**Consecuencias verificadas**:
- `spring.jpa.hibernate.ddl-auto=update` no cambia el tipo de una columna existente. Las bases ya creadas necesitan una vez `ALTER TABLE players ALTER COLUMN market_value TYPE numeric(12,4);`, documentado en [quickstart.md](quickstart.md). PostgreSQL conserva los valores (1.00 → 1.0000). Los perfiles `test` y `e2e` usan `create-drop` y crean la columna nueva.
- Con escala 4, PostgreSQL devuelve `1.0000`. `BigDecimal.equals` distingue escala, así que `ExternalSourcesDownE2ETest` (línea 314, `new BigDecimal("1.00")` comparado dentro de un record) pasa a fallar. Se ajusta el valor esperado a `1.0000`. Los tests que sólo construyen `Player` con `1.00`, o que usan mocks (`PlayerControllerTest`), no cambian.
- `PlayerRepositoryTest` guarda 35.000.000: tiene 8 dígitos enteros y entra en `numeric(12,4)` (máximo 99.999.999,9999).
- `PlayerResponse` serializa `1.0000` en lugar de `1.00`. El frontend formatea con `formatCredits`, así que no cambia lo que se ve. Se actualiza el `@Schema(example)` de `PlayerResponse` a `"1.0000"` (no es archivo protegido).
- `PlayerCatalogService` sigue creando jugadores con `1.00`, que se guarda como `1.0000`. No hace falta tocarlo.

**Alternativas descartadas**: dejar 2 decimales y redondear al escribir, que es justo el problema que se quiere evitar.

## D10. Roles: JWT desde la base, API key con el rol de su dueño

**Decisión** (del equipo, resuelve la clarificación de FR-029):
- **JwtAuthFilter**: después de validar el token, busca al usuario con `UserRepository.findByUsername(subject)`. Crea la autenticación con `SimpleGrantedAuthority("ROLE_" + user.getRole())`. Si el usuario no existe, autentica igual pero sin authorities. El JWT de hoy no tiene claim de rol (`JwtUtil.generateToken` sólo pone `subject`), así que el rol siempre sale de la base. `JwtUtil` no cambia.
- **ApiKeyAuthFilter**: autentica con `ROLE_<rol del dueño>`, o con `ROLE_ADMIN` si la key no tiene dueño: `List.of(new SimpleGrantedAuthority("ROLE_" + (owner != null ? owner.getRole().name() : "ADMIN")))`. Usa el mismo `owner` que ya lee para el principal.
- **SecurityConfig**: agrega `.requestMatchers(HttpMethod.POST, "/quotes/recalculate").hasRole("ADMIN")` antes de `.anyRequest().authenticated()`. Agrega `.accessDeniedHandler(...)` en `exceptionHandling`, que responde 403 con `{"error":"forbidden","message":"..."}`, el mismo formato que los 401 de los filtros.

**Motivo**:
- Leer el rol de la base hace que un cambio de rol aplique sin volver a iniciar sesión, y no obliga a cambiar `JwtUtil` ni los tokens emitidos.
- Autenticar sin authorities cuando el usuario no existe mantiene igual a los demás endpoints. `JwtOrApiKeyAuthenticationIntegrationTest.validBearerAllowsPlayers` firma un token para "guada" sin crear el usuario y tiene que seguir dando 200.
- `AuthService.register` emite una API key por usuario. Si la key hereda el rol de su dueño, un usuario USER recibe 403 tanto con su JWT como con su key. Las keys sin dueño son de servicio y quedan como ADMIN, para Swagger y para la corrida programada de 017.
- Las authorities nuevas no cambian ningún otro endpoint, porque ninguno las exige (FR-030).
- Sin `accessDeniedHandler`, Spring responde 403 con cuerpo vacío. El handler se escribe en una clase nueva, `security/JsonAccessDeniedHandler`. En `SecurityConfig` sólo se agrega su registro, que es parte del cambio autorizado para proteger el endpoint.

**Alternativas descartadas**:
- Toda API key válida como ADMIN: cualquier usuario registrado podría recalcular con su propia key.
- `@PreAuthorize` en el controller: necesita `@EnableMethodSecurity`, otro cambio de configuración, y el 403 saldría por el `ControllerAdvice` en lugar de seguridad.
- Poner el rol en el JWT: cambia `JwtUtil` (protegido) y deja el rol congelado hasta que vence el token.

## D11. Errores del endpoint sin tocar `GlobalExceptionHandler`

**Decisión**: nuevo `exception/QuoteExceptionHandler` con `@RestControllerAdvice(assignableTypes = QuoteController.class)` y `@Order(Ordered.HIGHEST_PRECEDENCE)`. Devuelve `GlobalExceptionHandler.ErrorResponse`:

| Excepción | HTTP | `error` |
|-----------|------|---------|
| `UnknownValuationStrategyException` (existe, de 013) | 400 | `unknown_strategy` |
| `RecalculationInProgressException` (nueva) | 409 | `recalculation_in_progress` |
| `HttpMessageNotReadableException` (JSON inválido) | 400 | `validation_error` |
| `RecalculationFailedException` (nueva, envuelve cualquier `RuntimeException` del snapshot, de la carga read-only o del armado del resultado; no las fallas dentro de la transacción de un jugador) | 500 | `recalculation_failed` |

**Motivo**: `GlobalExceptionHandler` está protegido. Limitar el advice al controller nuevo hace que no cambie la respuesta de ningún otro endpoint. `ErrorResponse` es un record público y se reusa sin cambios. Hoy un JSON inválido o una excepción no manejada devuelven el error por defecto de Spring, que no cumple FR-025/FR-026. El mensaje de 500 es genérico (por ejemplo, "No se pudo completar el recálculo"). El detalle va sólo al log.

## D12. Respuesta del endpoint

**Decisión**: el servicio devuelve `QuoteRunResult` (record de dominio). El controller lo mapea a `QuoteRunResponse` (DTO con `@Schema`). Los campos están en [data-model.md](data-model.md) y en [contracts/quotes-api.yaml](contracts/quotes-api.yaml). Las estrategias calculadas y salteadas van en dos listas separadas, cada una con código y versión. Las salteadas llevan `reason` (`no_algorithm`).

**Motivo**: separar dominio y DTO sigue la regla de capas (§2.1) y deja que 017 use `QuoteRunResult` sin depender de Jackson ni de OpenAPI.

## D13. Logs

**Decisión**: SLF4J con el formato `evento clave=valor` de `StatsSnapshotService`. El patrón de consola JSON ya incluye `correlationId` desde el MDC.

- `quote_recalculation_started runId=… requestedStrategy=…`
- `quote_recalculation_player_failed runId=… playerId=… strategy=… errorType=…` (nivel error, con stacktrace)
- `quote_recalculation_finished runId=… calculated=[…] skipped=[…] processed=… failed=… counts={…} marketValueUpdated=… durationMs=…`
- `quote_recalculation_failed runId=… errorType=…` (nivel error) cuando la corrida termina con error general.

**Motivo**: cumple FR-031 a FR-033 sin agregar dependencias ni tablas. No es una transacción financiera, así que no aplica la auditoría de §4.2.

## D14. Sin fuentes externas

**Decisión**: `QuoteCalculationService` sólo depende de repositorios, del catálogo, de los algoritmos, de `StatsSnapshotService`, de `Clock` y de `TransactionOperations`. No inyecta `WhoScoredAdapter`, `FootballDataAdapter` ni `PlayerStatsService`. Un test unitario lo verifica con `verifyNoInteractions` sobre mocks de los adapters. El E2E corre con las URLs externas apuntando a `127.0.0.1:1`, como los E2E existentes.

**Motivo**: FR-010 y SC-007; §1.3 de la constitución.
