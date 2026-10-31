# Research: Comparador de estrategias de valuación

**Feature**: `specs/031-comparador-estrategias` | **Fecha**: 2026-10-30

No quedan `NEEDS CLARIFICATION` en el contexto técnico. Cada sección documenta una decisión, su motivo y las alternativas descartadas.

## R1. Restricción a ADMIN sin tocar `SecurityConfig`

**Decision**: Nueva `config/MethodSecurityConfig` con `@Configuration @EnableMethodSecurity` y `@PreAuthorize("hasRole('ADMIN')")` sólo en `PUT /strategies/active`. `SecurityConfig` no cambia: `anyRequest().authenticated()` ya cubre `/strategies/**` y `/quotes/**` con 401 sin credenciales.

**Hallazgo importante**: el 403 de `@PreAuthorize` **no** lo responde `JsonAccessDeniedHandler`. La `AuthorizationDeniedException` (subclase de `AccessDeniedException`) se lanza dentro del `DispatcherServlet`, y la captura antes el `@ExceptionHandler(AccessDeniedException.class)` de `GlobalExceptionHandler` (Entrega 1). Ese handler responde 403 con el mismo cuerpo `{"error":"forbidden","message":"No tenés permisos para esta operación"}`, así que el contrato queda igual y no hace falta tocarlo. Hay una condición: el nuevo `StrategyExceptionHandler` (R9) **no** debe declarar handlers de `AccessDeniedException` ni de `Exception`, porque si no tomaría precedencia.

**Rationale**: Es la opción con menos superficie. No hay otros `@PreAuthorize` en el proyecto, así que habilitar la seguridad por método no cambia el comportamiento existente. La API key de servicio sin dueño ya trae `ROLE_ADMIN` y queda autorizada.

**Alternatives considered**: Un chequeo manual de `ROLE_ADMIN` en el service, como en `PortfolioService.authorizeAndLoad`, funciona, pero el pedido elige `@PreAuthorize`. Agregar un `requestMatchers(PUT, "/strategies/active").hasRole("ADMIN")` se descarta porque modifica `SecurityConfig`.

## R2. Persistencia de la estrategia activa

**Decision**: Nueva entidad `ActiveStrategySetting`, de una sola fila con id fijo `1` (tabla `active_strategy_settings`), creada con `ddl-auto=update`. `ValuationStrategyCatalog` recibe `ActiveStrategySettingRepository` en su constructor `@Autowired`. `active()` lee `findById(1)`: si la fila existe y tiene `activeCode`, devuelve `find(activeCode)`; si no, devuelve la definición de `valuation.active-strategy`. El constructor package-private `(List, String)`, que usan los tests unitarios, se conserva con un proveedor vacío y se comporta como hoy.

La fila se crea al primer `PUT`, **no** al arrancar. Si se sembrara al arrancar, el valor de la propiedad quedaría congelado y dejaría de aplicarse mientras nunca hubo un cambio (FR-021). `ValuationStrategyActivePropertyTest` sigue válido.

**Validación al arrancar**: el catálogo implementa `SmartInitializingSingleton` y en `afterSingletonsInstantiated()` resuelve `active()`. Un código persistido que ya no exista lanza `UnknownValuationStrategyException` y la aplicación no arranca, igual que hoy con la propiedad.

**Rationale**: `active()` ya es el único punto de decisión (5 usos). Leerlo en cada llamada hace que ranking, historial y recálculo vean el cambio de inmediato sin coordinación. El costo es una lectura por PK.

**Alternatives considered**: Un campo en memoria actualizado por el `PUT` no sobrevive reinicios y diverge con varias instancias. Guardar la configuración en Redis se descarta porque Redis es caché y no fuente de verdad. Una tabla clave/valor genérica no tiene otros usos que la justifiquen.

## R3. Estrategia fija durante una corrida

**Decision**: `QuoteCalculationService` resuelve `catalog.active().code()` **una vez** al construir el `Plan` de la corrida y usa ese valor para fijar `marketValue` (hoy se lee dentro del bucle por jugador, línea ~216) y para `summarize` (línea ~274).

**Rationale**: Con `active()` dinámico, un `PUT` durante la corrida haría que parte de los jugadores tomara un precio de una estrategia y parte de otra. FR-024 exige que la corrida termine con la estrategia con que comenzó.

**Alternatives considered**: Bloquear el `PUT` mientras corre un recálculo (409) es innecesariamente restrictivo y la spec no lo pide.

## R4. Qué estrategia fijó el precio de mercado vigente

**Decision**: La fila `ActiveStrategySetting` guarda también `marketStrategyCode` y `marketStrategyAppliedAt`. Al terminar una corrida con `marketValueUpdated = true`, `QuoteCalculationService` llama a `ActiveStrategyService.recordMarketStrategy(code, calculatedAt)`, que crea la fila si no existe y deja `activeCode` nulo en ese caso. Si `marketStrategyCode` cambia (incluido de nulo a un código), en la misma transacción se audita una vez por corrida con `AuditActor.system()` y `MARKET_STRATEGY_APPLIED`: `before` = `{strategy, version}` anterior o nulo, `after` = `{strategy, version, runId}`, y el correlation id de la corrida, que ya está en el MDC. Si no cambió, sólo se actualiza `marketStrategyAppliedAt`, sin auditar. El `marketValue` de cada jugador no se audita, como en la 016. Si esa escritura o su auditoría fallan, la transacción se revierte entera, se registra un log `error` y la corrida no falla, porque los precios ya se confirmaron por jugador. Si nunca se registró, la estrategia de mercado es `active()` (supuesto de la spec). `pendingRecalculation = marketStrategy != active`.

**Rationale**: Los resúmenes de corrida no se persisten. `QuoteRunCompletedEvent` sólo trae `runId` y se publica también en corridas fallidas, así que no alcanza.

**Alternatives considered**: Inferirla de las cotizaciones (la estrategia cuyo último valor coincide con `marketValue`) es ambiguo cuando ambas estrategias coinciden. Ampliar el evento obligaría a cambiar sus listeners existentes.

## R5. Portfolio idéntico sin `strategy`

**Decision**: `PortfolioResponse` y `PortfolioPositionResponse` agregan un componente final anulable, `strategyValuation`, con `@JsonInclude(JsonInclude.Include.NON_NULL)` en el componente: sin `strategy` vale `null` y no se serializa. Dentro de los objetos anidados (`PositionStrategyValuationResponse` y `PortfolioStrategyValuationResponse`) los campos vacíos **sí** se serializan como `null`, para cumplir FR-004. Cada record conserva un constructor secundario con la firma anterior, así las llamadas y los tests existentes no cambian. Las validaciones de totales del canónico se extienden al objeto anidado cuando está presente.

`PortfolioService.portfolio(Long, Authentication, Optional<String> strategy)` resuelve la estrategia **antes** de autorizar (R11). Delega el cálculo en un nuevo `PortfolioStrategyValuator`, para no subir la complejidad cognitiva que ya se redujo por Sonar. La sobrecarga sin `Optional` se mantiene y delega con `Optional.empty()`.

**Alternatives considered**: Campos planos anulables con `NON_NULL` no permiten devolver un `null` explícito cuando falta la cotización. Un DTO aparte para el portfolio con estrategia complica OpenAPI (dos esquemas para una ruta) y duplica la validación.

## R6. Última cotización por lote

**Decision**: Nuevo método en `PlayerQuoteRepository`, `findLatestForPlayers(code, playerIds)`, con el mismo `not exists` que `findLatestForAllPlayers` más `q.player.id in :ids` y `join fetch q.player`. Lo usan `/quotes/latest` y el portfolio. Las cantidades de consultas son constantes: una para los jugadores existentes y una para las cotizaciones.

`GET /quotes/latest` recibe `playerIds` como `String` opcional (`1,2,3`) y lo parsea a mano: vacío, más de 100 ids distintos o un id no numérico o no positivo producen `InvalidQuoteQueryException` → 400 `validation_error`, que ya maneja `QuoteExceptionHandler`. Se parsea a mano para no depender de `MissingServletRequestParameterException`, que ese handler no cubre. Los duplicados se eliminan conservando el orden del pedido. La respuesta respeta ese orden.

**Alternatives considered**: `POST` con cuerpo se descarta porque la operación es de lectura y el Álbum la cachea por URL. `List<Long>` enlazado por Spring da cuerpos de error inconsistentes ante faltantes.

## R7. Cálculo de oportunidades

**Decision**: `service/valuation/OpportunityService`:
1. Resuelve la activa configurada, la alternativa `A` (la otra del catálogo; con dos estrategias es única) y la estrategia de mercado `M` (R4). Sin cambio pendiente, `M` = activa. Con cambio pendiente, `M` = `A`: las diferencias dan cero y la lista queda vacía, como prevé el borde de la spec.
2. Carga jugadores con `PlayerRepository.findAll(spec)` filtrando liga y posición con `PlayerSpecifications`. Se reutiliza sin modificar; `positions` es `EAGER` en `Player` y se trae en la misma consulta o en un lote. Se verifica en el test de cantidad de sentencias.
3. `findLatestForAllPlayers(A)` y `findLatestForAllPlayers(M)` se indexan por `playerId`.
4. Se excluye un jugador si no tiene cotización `A`, si `A.status` o `M.status` ≠ `CALCULATED`, si `marketValue` es nulo o ≤ 0, o si la diferencia es 0.
5. `diff = A.value − marketValue` (escala 4). `pct = (A.value − marketValue) ÷ marketValue × 100` con `MathContext.DECIMAL128`, y se redondea a 2 decimales (`HALF_EVEN`) sólo para presentar. Se ordena por `|pct|` sin redondear, descendente; después por nombre y por id ascendentes. Se cortan `limit` elementos por lista.

La aritmética pura vive en `OpportunityCalculator` (sin Spring) para testearla unitariamente.

**Por qué el estado se mira en `M` y no en la activa configurada**: `marketValue` lo fijó `M`. Si la activa cambió y aún no hubo recálculo, mirar la nueva activa compararía contra un estado que no corresponde al precio. Sin cambio pendiente, `M` = activa.

**Volumen**: unos 2.500 jugadores, tres consultas, armado en memoria de O(n log n). Cumple SC-007 sin caché.

**Alternatives considered**: Hacer todo en una consulta SQL nativa con ventanas, como el ranking, se descarta porque el redondeo y el desempate quedan menos legibles y testeables. La diferencia con el pedido es de rendimiento y no hace falta a esta escala.

## R8. Caché del ranking al cambiar la estrategia

**Decision**: `ActiveStrategyService` publica `ActiveStrategyChangedEvent` cuando el cambio es efectivo. `QuoteCacheEvictor` agrega un `@TransactionalEventListener(phase = AFTER_COMMIT)` que vacía `quotes-ranking` con el mismo manejo de errores que hoy.

**Nota**: la clave del ranking ya usa el código **resuelto** (`rankingKey(code, …)`), así que un pedido sin `strategy` posterior al cambio no lee la entrada de la activa anterior. El vaciado es defensivo y alinea con lo pedido. No se crean cachés nuevos: registrarlos requiere tocar `CacheConfig` (Entrega 1).

## R9. Errores de estrategia inexistente fuera de `QuoteController`

**Decision**: Nuevo `exception/StrategyExceptionHandler`, `@RestControllerAdvice(assignableTypes = {UserMarketController.class, StrategyController.class})` con `@Order(HIGHEST_PRECEDENCE)`. Maneja sólo:
- `UnknownValuationStrategyException` → 400 `unknown_strategy`, igual que `QuoteExceptionHandler`;
- `HttpMessageNotReadableException` → 400 `validation_error`, sólo para el cuerpo del `PUT`.

El cuerpo `{ "strategy": "..." }` se valida con `@Valid @NotBlank` y lo atiende `GlobalExceptionHandler` (400 existente).

**Rationale**: Hoy, sin este handler, la excepción en `/users/{id}/portfolio` caería en un 500. Agregar `UserMarketController` a `QuoteExceptionHandler` cambiaría las respuestas 400 de paginación de `/transactions`, porque ese handler maneja también `MethodArgumentTypeMismatchException`.

## R10. Nueva acción de auditoría y constraint del enum

**Decision**: Se agregan `AuditAction.ACTIVE_STRATEGY_CHANGED` (23 caracteres) y `AuditAction.MARKET_STRATEGY_APPLIED` (24 caracteres; R4); la columna admite 32. Las dos acciones reciben el mismo tratamiento del CHECK descrito abajo. Se audita con `TransactionAuditService.audit(actor, ACTIVE_STRATEGY_CHANGED, "VALUATION_STRATEGY", "active", before, after)` dentro de la transacción del cambio (`MANDATORY`).
- `before` = `{ "strategy": "OFFICIAL", "version": 1 }`
- `after` = `{ "strategy": "MOMENTUM", "version": 1 }`

El actor es `AuditActor.user(id)` si `authentication.getName()` corresponde a un usuario, o `AuditActor.system()` para la API key de servicio sin dueño.

**Riesgo**: Hibernate 7 (Spring Boot 4.1) genera un `CHECK (action in (...))` para enums `STRING` al **crear** la tabla, y `ddl-auto=update` no lo altera. En una base PostgreSQL persistente creada antes de esta feature, insertar cualquiera de las dos acciones nuevas fallaría. Los tests usan H2 y el e2e usa `create-drop`, así que no lo detectarían.
**Mitigación**: el quickstart documenta el SQL de una sola vez para bases existentes. En `/speckit-tasks` se agrega una tarea para verificar, sobre la base de desarrollo, si el constraint existe (`pg_constraint`). Si existe, se evalúa con el equipo un `ApplicationRunner` idempotente que lo regenere. No se resuelve en silencio.

**Resultado de T002 (2026-10-30, base `desarrollo2_grupod` local)**: el constraint **existe** y no incluye las acciones nuevas.
- `audit_logs_action_check`: `CHECK (action IN ('SUPERUSER_CREATED', 'TOKENS_ISSUED', 'ORDER_CREATED', 'ORDER_FILLED', 'ORDER_CANCELLED', 'TRADE_EXECUTED', 'BALANCE_CHANGED', 'HOLDING_CHANGED'))`.
- `audit_logs_actor_type_check` (`USER`, `SYSTEM`) y `ck_audit_logs_actor` (SYSTEM sin usuario, USER con usuario) no afectan a esta feature: las dos auditorías nuevas cumplen esas reglas.

Mientras no se regenere, en esa base:
- `PUT /strategies/active` falla al insertar la auditoría. La transacción se revierte entera, así que la estrategia activa no cambia.
- Cada recálculo que actualiza `marketValue` loguea `market_strategy_record_failed`. Los precios se actualizan igual (otra transacción), pero la estrategia de mercado no queda registrada, y `GET /strategies/active` la sigue informando sin dato.

**Pendiente**: el equipo decide cómo regenerar el constraint (SQL manual del quickstart o un `ApplicationRunner` idempotente). No se ejecutó ningún DDL.

## R11. Orden de validación en el portfolio

**Decision**: Primero se resuelve `strategy` (400 si no existe) y después se autoriza (401/403/404). Validar el parámetro no lee datos de ninguna cuenta y no revela existencia. FR-006 pide que el 400 ocurra antes de leer el portfolio.

## R12. Nombre descriptivo de la estrategia

**Decision**: Las respuestas usan `code`, `version` y `label` (`"OFFICIAL v1"`, el formato que `ValuationStrategyDefinition` ya arma para sus mensajes). No se agrega un campo de nombre a la definición, porque cambiaría la huella (`fingerprint()`) de las estrategias versionadas en 013/019.

## R13. Concurrencia del `PUT`

**Decision**: `ActiveStrategyService.change` corre en `@Transactional`. Lee la fila con `@Lock(PESSIMISTIC_WRITE)` (`findLockedById(1)`). Si la fila no existe, la inserta. Si dos primeros cambios insertan a la vez, el que pierde recibe `DataIntegrityViolationException` y reintenta una vez leyendo con lock. El estado anterior auditado es el leído bajo lock, lo que cumple el borde "cambios concurrentes de dos ADMIN". Reactivar la misma estrategia devuelve `changed=false` sin escribir ni auditar.

## Ajustes durante la implementación

Decisiones tomadas en `/speckit-implement` que ajustan lo anterior:

- **R2, catálogo sin repositorio**: el constructor `@Autowired` recibe `ObjectProvider<ActiveStrategySettingRepository>`. Así los contextos mínimos (por ejemplo, `ValuationStrategyActivePropertyTest` de la 013) arrancan sin JPA y usan sólo la propiedad. Se conserva el constructor `(String)` que usan los tests existentes.
- **R3/R4, registro de la estrategia de mercado**: `QuoteCalculationService` depende de la interfaz `MarketStrategyRecorder`, que implementa `ActiveStrategyService`. Se conserva el constructor anterior, con un registrador vacío, para los tests de la 016.
- **R4, primer cambio sin recálculo previo**: al crear la fila con el primer `PUT`, `marketStrategyCode` queda en la activa anterior (`assumeMarketIfUnknown`). Si quedaba nula, se tomaba la nueva activa y `pendingRecalculation` daba `false`; lo detectó `StrategyControllerTest`.
- **R4/R10, correlation id del recálculo programado**: el scheduler (020) pone `valuation-<uuid>` (46 caracteres) en el MDC y `audit_logs.correlation_id` admite 36, así que **toda** auditoría hecha durante una corrida programada fallaba al insertar. `TransactionAuditService.correlationId()` ahora guarda el UUID final cuando el valor no entra. Sólo cambia casos que antes fallaban siempre. Afecta también a las auditorías del mercado (026/027) que corren dentro del recálculo programado, como la emisión de tokens.
- **R5, controller**: sin `strategy`, `UserMarketController` sigue llamando a `portfolio(id, auth)`, así los tests de la 028, que mockean esa firma, no cambian.
- **R7 y R6, lectura de jugadores**: `PlayerRepository.findAll(spec)` haría N+1 por las posiciones `EAGER` y `PlayerRepository` es de Entrega 1. Se agregó `ComparisonPlayerRepository`, de sólo lectura, con `left join fetch p.positions` y `findExistingIds`, siguiendo el precedente de `MarketPlayerRepository`. `findLatestForPlayers` también trae las posiciones con `left join fetch`.
