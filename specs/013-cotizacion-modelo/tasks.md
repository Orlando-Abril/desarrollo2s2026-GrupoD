---

description: "Tareas de implementación del modelo de cotización, el catálogo de estrategias de valuación y el dato de gambetas"
---

# Tasks: Modelo de datos de la cotización y catálogo de estrategias de valuación

**Input**: Design documents from `/specs/013-cotizacion-modelo/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/valuation-strategies.md](contracts/valuation-strategies.md), [quickstart.md](quickstart.md)

**Tests**: Son obligatorios. La spec los exige explícitamente:
- FR-027: test de protección de versiones.
- FR-029: `@UnitTestProfile` en los tests que levantan contexto.
- FR-030: e2e de las 4 consultas contra PostgreSQL.

Dentro de cada historia, los tests se escriben primero y tienen que fallar antes de implementar.

**Organization**: Las tareas se agrupan por historia de la spec (`US1` a `US6`).
- Las tres historias P1 (US1, US2, US3) forman el MVP.
- US4 y US5 dependen de US3.
- US6 (gambetas, bloque B) es independiente del resto.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Se puede ejecutar en paralelo, porque toca otro archivo y no depende de trabajo incompleto.
- **[Story]**: La historia de `spec.md` a la que pertenece la tarea.
- Las rutas son relativas a la raíz del repositorio. `MAIN` = `backend/src/main/java/com/example/demo` y `TEST` = `backend/src/test/java/com/example/demo`.
- Los comandos Maven se corren desde `backend/`. En Windows, usar `.\mvnw.cmd` y poner entre comillas los `-D…`.

## Reglas transversales (aplican a todas las tareas)

- **Entrega 1**: no se modifica ningún archivo de `MAIN/model/` existente, de `MAIN/repository/` existente ni ningún `application*.properties`. La **única** excepción aprobada es agregar `dribbles` a `MAIN/model/PlayerStats.java` (T040).
- **Alcance**: no se crean controllers, schedulers ni endpoints, y no se modifica `Player`.
- **Estilo**: seguir el estilo del código vecino (Lombok, javadoc en español, logs `clave=valor` como en `WhoScoredAdapter`).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirmar que el punto de partida está en verde y que no existe nada de cotización todavía.

- [X] T001 Desde `backend/`, correr `./mvnw test -DexcludedGroups=e2e` y confirmar que termina en `BUILD SUCCESS`. Confirmar también que no existen `MAIN/model/PlayerQuote.java`, `MAIN/repository/PlayerQuoteRepository.java` ni `MAIN/service/valuation/`, y que ningún archivo de `backend/src/main/resources/` ni de `backend/src/test/resources/` menciona `valuation.`. Si algo de esto no se cumple, detenerse e informar.

**Checkpoint**: La base está en verde y no hay restos de cotización.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Crear los enums que usan varias historias: `QuoteStatus` (US1 y US2) y `StrategyType` y `Metric` (US3, US4 y US5).

**⚠️ CRITICAL**: Ninguna historia puede empezar hasta completar esta fase.

- [X] T002 [P] Crear `MAIN/model/QuoteStatus.java`:
  - `public enum QuoteStatus { CALCULATED, NO_STATS, INSUFFICIENT_HISTORY }`.
  - Javadoc por valor, según data-model.md §2: `CALCULATED` = "calculada con datos suficientes; score obligatorio"; `NO_STATS` = "el jugador no tenía estadísticas"; `INSUFFICIENT_HISTORY` = "no había historial suficiente para la estrategia".
- [X] T003 [P] Crear `MAIN/service/valuation/strategy/StrategyType.java`:
  - `public enum StrategyType { OFFICIAL, MOMENTUM }`.
  - Javadoc: indica qué algoritmo interpreta los pesos (feature 015 para OFFICIAL, 019 para MOMENTUM).
- [X] T004 [P] Crear `MAIN/service/valuation/strategy/Metric.java`:
  - `public enum Metric`, exactamente en este orden: `GOALS, ASSISTS, SHOTS, KEY_PASSES, DRIBBLES, TACKLES, RATING, MINUTES, YELLOW_CARDS, RED_CARDS`.
  - Javadoc por valor, con el campo de `PlayerStats` que le corresponde: `goals`, `assists`, `shots`, `keyPasses`, `dribbles`, `tackles`, `rating`, `minutesPlayed`, `yellowCards`, `redCards`.

**Checkpoint**: `./mvnw compile` pasa.

---

## Phase 3: User Story 1 - Registrar cotizaciones como historial inmutable y trazable (Priority: P1) 🎯 MVP

**Goal**: `PlayerQuote` se persiste con sus 9 datos, se rechaza si falta un dato obligatorio y no existe ninguna operación para modificarla o borrarla.

**Independent Test**: `./mvnw test -Dtest='PlayerQuoteTest,PlayerQuoteRepositoryTest'`. Una cotización guardada y releída es idéntica a la registrada, y el repositorio no expone `delete*`.

### Tests for User Story 1 ⚠️

- [X] T005 [P] [US1] Crear `TEST/model/PlayerQuoteTest.java`, un test unitario sin Spring:
  - Con el builder, una cotización `CALCULATED` con todos los campos expone los mismos valores por sus getters.
  - Al invocar el método `@PrePersist` (package-private, por ejemplo `validate()`) con `status = CALCULATED` y `score = null`, se lanza `IllegalStateException` con el mensaje exacto `"score es obligatorio para CALCULATED"`.
  - Con `NO_STATS` o `INSUFFICIENT_HISTORY` y `score = null`, no se lanza nada.
  - Por reflexión, `PlayerQuote` no tiene ningún método público cuyo nombre empiece con `set`.
- [X] T006 [US1] Crear `TEST/repository/PlayerQuoteRepositoryTest.java` con `@DataJpaTest` y `@UnitTestProfile`, inyectando `PlayerQuoteRepository`, `PlayerRepository` y `TestEntityManager`.
  - **Preparación**: en `@BeforeEach`, guardar un `Player` como en `TEST/repository/PlayerStatsRepositoryTest.java` (`Player.builder()…marketValue(new BigDecimal("1.00"))`).
  - **US1-1**: guardar una cotización `CALCULATED` (`value = 1.2345`, `score = 0.4321`, `strategyCode = "OFFICIAL"`, `strategyVersion = 1`, `calculatedAt = 2026-10-05T12:00:00Z`, `runId` aleatorio, `correlationId = "corr-1"`) con `save` y luego `flush`, `clear` y `findById`. Los 9 datos tienen que ser iguales (`value` y `score` comparados con `isEqualByComparingTo`). Además, `value.scale()` releído es 4 y su valor es exactamente `1.2345`, sin redondeo (FR-004).
  - **US1-3**: una cotización `NO_STATS` con `score = null` se guarda y se relee con `score` nulo y `status == NO_STATS`.
  - **US1-4**: test parametrizado. Por cada campo obligatorio (`player`, `strategyCode`, `strategyVersion`, `value`, `status`, `calculatedAt`, `runId`, `correlationId`), omitirlo y verificar que `save` + `flush` lanza una excepción (`ConstraintViolationException` o `DataIntegrityViolationException`). Además, `value = -0.01` se rechaza y `strategyVersion = 0` se rechaza.
  - **US1-2 (inmutabilidad)**:
    - Cargar una cotización, cambiar `value` con `ReflectionTestUtils.setField`, hacer `flush`, `clear` y recargar: el valor sigue siendo el original.
    - Por reflexión, `PlayerQuoteRepository` no declara ni hereda ningún método cuyo nombre empiece con `delete`, y no es asignable a `CrudRepository`, `ListCrudRepository` ni `JpaRepository`.

### Implementation for User Story 1

- [X] T007 [US1] Crear `MAIN/model/PlayerQuote.java` según data-model.md §1.
  - **Anotaciones de clase**: `@Entity`, `@Table(name = "player_quotes")` (los índices se agregan en T013), `@org.hibernate.annotations.Immutable`, `@Getter`, `@Builder`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)` y `@AllArgsConstructor(access = AccessLevel.PRIVATE)`. **Sin `@Setter`**.
  - **Columnas**: todas con `updatable = false`.
    - `id`: `Long`, `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)`.
    - `player`: `@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "player_id", nullable = false, updatable = false)`.
    - `strategyCode`: `@Column(name = "strategy_code", length = 32, nullable = false)` y `@NotBlank`.
    - `strategyVersion`: `Integer` (no `int`, para que omitirla sea `null` y se rechace por obligatoria), `@Column(name = "strategy_version", nullable = false)`, `@NotNull` y `@Min(1)`.
    - `value`: `BigDecimal`, `@Column(name = "quote_value", precision = 12, scale = 4, nullable = false)`, `@NotNull` y `@PositiveOrZero`. *(Al implementar: la columna se llama `quote_value` porque `value` es palabra reservada en H2 y la tabla no se creaba.)*
    - `score`: `BigDecimal`, `@Column(precision = 12, scale = 4)` y nullable.
    - `status`: `@Enumerated(EnumType.STRING) @Column(length = 32, nullable = false)` y `@NotNull`.
    - `calculatedAt`: `Instant`, `@Column(name = "calculated_at", nullable = false)` y `@NotNull`.
    - `runId`: `UUID`, `@Column(name = "run_id", nullable = false)` y `@NotNull`.
    - `correlationId`: `@Column(name = "correlation_id", length = 64, nullable = false)` y `@NotBlank`.
  - **Validación**: un método package-private `@PrePersist void validate()` que lance `IllegalStateException("score es obligatorio para CALCULATED")` si `status == QuoteStatus.CALCULATED && score == null`.
  - **Javadoc de clase**: registro histórico inmutable; la estrategia se referencia por código y versión, no por FK; `Player` no conoce esta entidad.
- [X] T008 [US1] Crear `MAIN/repository/PlayerQuoteRepository.java`:
  - `public interface PlayerQuoteRepository extends org.springframework.data.repository.Repository<PlayerQuote, Long>`.
  - Por ahora declara **sólo** `PlayerQuote save(PlayerQuote quote)`, `<S extends PlayerQuote> List<S> saveAll(Iterable<S> quotes)` y `Optional<PlayerQuote> findById(Long id)`.
  - Javadoc: explica que no extiende `JpaRepository` para que no exista ninguna operación de borrado (FR-005).
- [X] T009 [US1] Correr `./mvnw test -Dtest='PlayerQuoteTest,PlayerQuoteRepositoryTest'`. Tiene que terminar en verde.

**Checkpoint**: Se puede registrar el historial inmutable, que es la base de US2.

---

## Phase 4: User Story 2 - Consultar cotización actual, histórica y vigente en una fecha (Priority: P1)

**Goal**: Las 4 consultas de FR-007 a FR-011, cada una respaldada por un índice (FR-012) y verificadas en H2 y en PostgreSQL (FR-030).

**Independent Test**: `./mvnw test -Dtest='PlayerQuoteRepositoryTest'` y `./mvnw test -Dgroups=e2e -Dtest='PlayerQuoteQueriesE2ETest'`.

### Tests for User Story 2 ⚠️

- [X] T010 [US2] Ampliar `TEST/repository/PlayerQuoteRepositoryTest.java` con una clase `@Nested` `Queries`.
  - **Datos**:
    - Un helper `quote(Player, String code, int version, String isoInstant, String value)` que construye y guarda cotizaciones `CALCULATED`.
    - Jugador A: OFFICIAL v1 el `2026-09-01T12:00Z`, el `2026-09-08T12:00Z` y el `2026-09-15T12:00Z`, y MOMENTUM v1 el `2026-09-16T12:00Z`.
  - **Escenarios de la spec**:
    - **US2-1**: `findFirstByPlayerIdAndStrategyCodeOrderByCalculatedAtDescIdDesc(A, "OFFICIAL")` devuelve la del día 15.
    - **US2-2**: el historial OFFICIAL entre `2026-09-01T12:00Z` y `2026-09-08T12:00Z` devuelve [1, 8], en orden ascendente y sin MOMENTUM. Esto también verifica que los bordes son inclusivos.
    - **US2-3**: `findEffectiveOn(A, "OFFICIAL", LocalDate.of(2026, 9, 10))` devuelve la del día 8.
    - **US2-4**: `findEffectiveOn(A, "OFFICIAL", 2026-08-31)` devuelve vacío.
    - **US2-5**: con los jugadores B y C (con varias cotizaciones OFFICIAL cada uno) y D (sin ninguna), `findLatestForAllPlayers("OFFICIAL")` devuelve exactamente 3 filas, una por jugador y la más reciente de cada uno, y ninguna de D.
    - **US2-6**: para un jugador sin cotizaciones, la última y el historial devuelven vacío, sin lanzar excepción.
  - **Bordes**:
    - **Empate**: dos cotizaciones OFFICIAL de A con el mismo `calculatedAt`. La última, la vigente y `findLatestForAllPlayers` devuelven la de `id` mayor (FR-011).
    - **Rango invertido**: si `from > to`, la lista es vacía.
    - **Instante exacto**: `findEffectiveAt(A, "OFFICIAL", Instant.parse("2026-09-08T12:00:00Z"))` devuelve la del día 8.
    - **Fin del día**: con una cotización el `2026-09-10T23:59:59.999999Z` y otra el `2026-09-11T00:00:00Z`, `findEffectiveOn(2026-09-10)` devuelve la de `23:59:59.999999Z`.
    - **Versiones**: una cotización OFFICIAL **v2** posterior aparece en el historial y en la última junto con las v1, y conserva `strategyVersion == 2`.
- [X] T011 [P] [US2] Crear `TEST/e2e/PlayerQuoteQueriesE2ETest.java` con `@E2ETest`, inyectando `E2EFixtures`, `PlayerQuoteRepository` y `JdbcTemplate`.
  - **Preparación**: en `@BeforeEach`, `createPlayersFromFiveLeagues()`. Usar `forLeague(PREMIER_LEAGUE)` como jugador A y los demás como B, C y D.
  - **Escenarios**: repetir US2-1 a US2-6 y los bordes de empate, instante exacto y fin del día (`23:59:59.999999Z` frente a `00:00:00Z` del día siguiente) de T010.
  - **Índices**: con `jdbcTemplate.queryForList("select indexname from pg_indexes where tablename = 'player_quotes'", String.class)`, verificar que contiene `idx_player_quotes_player_strategy_time` y `idx_player_quotes_strategy_time`.
  - Javadoc: existe porque el manejo de fechas y la precisión de `timestamp` pueden diferir entre H2 y PostgreSQL (FR-030).

### Implementation for User Story 2

- [X] T012 [US2] Agregar a `MAIN/repository/PlayerQuoteRepository.java` los métodos de data-model.md §3:
  - `Optional<PlayerQuote> findFirstByPlayerIdAndStrategyCodeOrderByCalculatedAtDescIdDesc(Long playerId, String strategyCode)`.
  - `List<PlayerQuote> findByPlayerIdAndStrategyCodeAndCalculatedAtBetweenOrderByCalculatedAtAscIdAsc(Long playerId, String strategyCode, Instant from, Instant to)`.
  - `Optional<PlayerQuote> findFirstByPlayerIdAndStrategyCodeAndCalculatedAtLessThanEqualOrderByCalculatedAtDescIdDesc(Long playerId, String strategyCode, Instant at)`.
  - `Optional<PlayerQuote> findFirstByPlayerIdAndStrategyCodeAndCalculatedAtLessThanOrderByCalculatedAtDescIdDesc(Long playerId, String strategyCode, Instant before)`.
  - `default Optional<PlayerQuote> findEffectiveAt(Long playerId, String strategyCode, Instant at)`, que delega en el método `LessThanEqual`.
  - `default Optional<PlayerQuote> findEffectiveOn(Long playerId, String strategyCode, LocalDate date)`, que delega en el método `LessThan` con `date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()`. Javadoc: "vigente al fin del día UTC; frontera exclusiva para no depender de la precisión del motor (research D5)".
  - `@Query` `List<PlayerQuote> findLatestForAllPlayers(@Param("code") String strategyCode)`, con el JPQL `NOT EXISTS` textual de data-model.md §3, incluido el desempate `q2.id > q.id`.
- [X] T013 [US2] En `MAIN/model/PlayerQuote.java`, ampliar `@Table(name = "player_quotes", indexes = {…})` con:
  - `@Index(name = "idx_player_quotes_player_strategy_time", columnList = "player_id, strategy_code, calculated_at")`.
  - `@Index(name = "idx_player_quotes_strategy_time", columnList = "strategy_code, calculated_at")`.
- [X] T014 [US2] Correr `./mvnw test -Dtest='PlayerQuoteRepositoryTest'` (verde). Después, con PostgreSQL y Redis locales, correr `./mvnw test -Dgroups=e2e -Dtest='PlayerQuoteQueriesE2ETest'` (verde). Si alguna consulta da distinto entre H2 y PostgreSQL, corregir la consulta, no el test.
  - *Estado: verde en H2 (25 tests) y en PostgreSQL real (`PlayerQuoteQueriesE2ETest`, 10 tests).*

**Checkpoint**: Las consultas que pide la consigna funcionan en ambos motores.

---

## Phase 5: User Story 3 - Estrategias declaradas en código, con pesos explícitos y versión (Priority: P1)

**Goal**: OFFICIAL v1 y MOMENTUM v1 como datos inmutables y validados, más un catálogo con `all()` y `find(code)`.

**Independent Test**: `./mvnw test -Dtest='ValuationStrategyDefinitionTest,OfficialStrategyV1Test,MomentumStrategyV1Test,ValuationStrategyCatalogTest'`.

### Tests for User Story 3 ⚠️

- [X] T015 [P] [US3] Crear `TEST/service/valuation/strategy/ValuationStrategyDefinitionTest.java`, un test unitario.
  - **Datos**: un helper que construye una definición válida y permite cambiar un parámetro.
  - **Rechazos** (cada uno lanza `IllegalArgumentException`):
    - `code` en blanco.
    - `version = 0`.
    - `baseValue = 0`.
    - `scaleFactor = 0`.
    - `maxVariation = 0`.
    - `maxVariation = 1.01`.
    - `weights` sin `GOALKEEPER`.
    - Dos posiciones con distinto conjunto de métricas.
    - `type = MOMENTUM` con `momentum = null`.
    - `type = OFFICIAL` con `momentum != null`.
  - **Aceptado**: `maxVariation = 1` es válido.
  - **Inmutabilidad**: modificar el mapa de entrada después de construir no altera `weights()`, y `weights().get(FORWARD).put(…)` lanza `UnsupportedOperationException`.
  - **`weight(Position, Metric)`**: devuelve el peso declarado y lanza `IllegalArgumentException` para una métrica que la estrategia no declara.
- [X] T016 [P] [US3] Crear `TEST/service/valuation/strategy/OfficialStrategyV1Test.java`, que verifica `OfficialStrategyV1.DEFINITION` contra contracts/valuation-strategies.md §2 y §3.
  - **Parámetros**: `code "OFFICIAL"`, `version 1`, `type OFFICIAL`, `baseValue 1.00`, `scaleFactor 0.10`, `maxVariation 0.20` y `momentum` nulo.
  - **Pesos**:
    - Las 4 posiciones declaran exactamente las 7 métricas `GOALS, ASSISTS, SHOTS, KEY_PASSES, DRIBBLES, TACKLES, RATING`.
    - MIDFIELDER es igual a la fórmula de la consigna (0.25, 0.15, 0.10, 0.10, 0.10, 0.10, 0.20).
    - La tabla completa coincide con el contrato, usando un test parametrizado por posición.
  - **Invariantes**:
    - Todos los pesos son mayores o iguales a 0.
    - En cada posición la suma `compareTo` 1.00 da 0.
    - FORWARD tiene más peso que MIDFIELDER en `GOALS` y en `SHOTS`.
    - DEFENDER tiene más peso que MIDFIELDER en `TACKLES`.
- [X] T017 [P] [US3] Crear `TEST/service/valuation/strategy/MomentumStrategyV1Test.java`, que verifica `MomentumStrategyV1.DEFINITION` contra el contrato §2 y §4.
  - **Parámetros**: `code "MOMENTUM"`, `version 1`, `type MOMENTUM`, `baseValue 1.00`, `scaleFactor 0.15`, `maxVariation 0.30` y `momentum = (window 4, minHistory 2)`.
  - **Pesos**:
    - Las 4 posiciones declaran las 10 métricas.
    - La tabla completa coincide con el contrato.
  - **Invariantes**:
    - En cada posición, los pesos positivos suman 1.00.
    - `YELLOW_CARDS` y `RED_CARDS` son menores que 0, y `RED_CARDS` es menor que `YELLOW_CARDS`.
    - `MINUTES` es mayor que 0 en todas las posiciones.
- [X] T018 [P] [US3] Crear `TEST/service/valuation/strategy/ValuationStrategyCatalogTest.java`, un test unitario que usa `new ValuationStrategyCatalog("OFFICIAL")`.
  - **`all()`**: devuelve exactamente `[OfficialStrategyV1.DEFINITION, MomentumStrategyV1.DEFINITION]`, en ese orden, y la lista es inmodificable.
  - **`find`**: `find("OFFICIAL")` y `find("MOMENTUM")` devuelven la definición correspondiente.
  - **Código inexistente**: `find("XYZ")` lanza `UnknownValuationStrategyException` con el mensaje exacto `"Estrategia de valuación desconocida: 'XYZ'. Códigos válidos: OFFICIAL, MOMENTUM"`.
  - **Duplicados**: el constructor package-private `ValuationStrategyCatalog(List<ValuationStrategyDefinition>, String)`, si recibe dos definiciones con el mismo código, lanza `IllegalStateException`.

### Implementation for User Story 3

- [X] T019 [P] [US3] Crear `MAIN/service/valuation/strategy/MomentumParameters.java`:
  - `public record MomentumParameters(int window, int minHistory)`.
  - El constructor compacto exige `window >= 1`, `minHistory >= 1` y `minHistory <= window`. Si no se cumple, lanza `IllegalArgumentException`.
  - Javadoc: `window` es la cantidad de cotizaciones previas que mira la forma reciente; con menos de `minHistory` cotizaciones previas, el algoritmo 019 registra `INSUFFICIENT_HISTORY`.
- [X] T020 [US3] Crear `MAIN/service/valuation/strategy/ValuationStrategyDefinition.java`:
  - **Firma**: `public record ValuationStrategyDefinition(String code, int version, StrategyType type, BigDecimal baseValue, BigDecimal scaleFactor, BigDecimal maxVariation, Map<Position, Map<Metric, BigDecimal>> weights, MomentumParameters momentum)`.
  - **Constructor compacto**: valida las invariantes 1 a 5 de data-model.md §4, con un mensaje que nombre `code` y `version`. Además copia los mapas en profundidad a `EnumMap` y los envuelve con `Collections.unmodifiableMap`, para que la iteración siga el orden del enum y sea determinística.
  - **Métodos**:
    - `BigDecimal weight(Position, Metric)`.
    - `Set<Metric> metrics()`, que devuelve el conjunto común.
  - **Javadoc**: los valores se declaran acá y los interpretan las features 015 y 019; una versión publicada no se modifica (enlace a contracts/valuation-strategies.md §5).
- [X] T021 [P] [US3] Crear `MAIN/service/valuation/strategy/OfficialStrategyV1.java`:
  - Una `final class` con constructor privado y `public static final ValuationStrategyDefinition DEFINITION`.
  - Valores exactos de contracts/valuation-strategies.md §2 y §3, con los pesos escritos como `new BigDecimal("0.25")` y armados en un helper legible por posición.
  - Javadoc: MIDFIELDER es la fórmula de la consigna.
- [X] T022 [P] [US3] Crear `MAIN/service/valuation/strategy/MomentumStrategyV1.java`, igual que T021 pero con los valores del contrato §2 y §4, incluidos los pesos negativos de las tarjetas y `new MomentumParameters(4, 2)`.
- [X] T023 [P] [US3] Crear `MAIN/exception/UnknownValuationStrategyException.java`:
  - Extiende `RuntimeException`.
  - Constructor `(String requested, Collection<String> validCodes)` con el mensaje `"Estrategia de valuación desconocida: '" + requested + "'. Códigos válidos: " + String.join(", ", validCodes)`.
- [X] T024 [US3] Crear `MAIN/service/valuation/strategy/ValuationStrategyCatalog.java`:
  - **Anotación**: `@Component`.
  - **Constructor package-private** `(List<ValuationStrategyDefinition> definitions, String activeCode)`: copia la lista con `List.copyOf` y lanza `IllegalStateException` si hay códigos duplicados.
  - **Constructor público** `@Autowired (@Value("${valuation.active-strategy:OFFICIAL}") String activeCode)`. *(Al implementar: el `@Autowired` hace falta porque con dos constructores Spring no sabe cuál usar y no crea el bean.)* delega con `List.of(OfficialStrategyV1.DEFINITION, MomentumStrategyV1.DEFINITION)`. El `@Value` va desde esta tarea para que Spring pueda crear el bean y los `@SpringBootTest` existentes no se rompan. En esta fase `activeCode` se guarda pero todavía no se usa; `active()` y la resolución de la activa se agregan en T028.
  - **Métodos**: `all()` y `find(String code)`. Si el código no existe, `find` lanza `UnknownValuationStrategyException` con los códigos válidos en el orden de `all()`.
- [X] T025 [US3] Correr `./mvnw test -Dtest='ValuationStrategyDefinitionTest,OfficialStrategyV1Test,MomentumStrategyV1Test,ValuationStrategyCatalogTest'`. Tiene que terminar en verde.

**Checkpoint**: Las dos estrategias de la consigna existen como datos explícitos y validados. Junto con US1 y US2, esto completa el MVP.

---

## Phase 6: User Story 4 - Seleccionar la estrategia activa del sistema (Priority: P2)

**Goal**: Una sola estrategia activa, OFFICIAL por defecto, que se cambia con `valuation.active-strategy` sin editar ningún `.properties`. Un código inválido impide arrancar.

**Independent Test**: `./mvnw test -Dtest='ValuationStrategyActivePropertyTest,ValuationStrategyCatalogContextTest'`.

**Depende de**: US3.

### Tests for User Story 4 ⚠️

- [X] T026 [P] [US4] Crear `TEST/service/valuation/strategy/ValuationStrategyActivePropertyTest.java` con `new ApplicationContextRunner().withUserConfiguration(ValuationStrategyCatalog.class)`:
  - **US4-1**: sin propiedades, `active()` es OFFICIAL.
  - **US4-2**: con `valuation.active-strategy=MOMENTUM`, `active()` es MOMENTUM y `all()` sigue teniendo 2 definiciones.
  - **Espacios**: con `valuation.active-strategy= MOMENTUM ` (con espacios), `active()` es MOMENTUM.
  - **US4-3**: con `valuation.active-strategy=XYZ`, `context.hasFailed()` es verdadero y la causa raíz es `UnknownValuationStrategyException`, con un mensaje que contiene `'XYZ'` y `OFFICIAL, MOMENTUM`.
- [X] T027 [P] [US4] Crear `TEST/service/valuation/strategy/ValuationStrategyCatalogContextTest.java` con `@SpringBootTest` y `@UnitTestProfile`. Inyecta `ValuationStrategyCatalog` y verifica que `active().code()` es `"OFFICIAL"`, lo que prueba que ningún `.properties` define la propiedad.

### Implementation for User Story 4

- [X] T028 [US4] En `MAIN/service/valuation/strategy/ValuationStrategyCatalog.java` (el `@Value` ya está desde T024):
  - En el constructor, resolver `this.active = find(activeCode.trim())`, para que un código inválido haga fallar el arranque (FR-025).
  - Agregar `active()`.
  - Javadoc: la activa sólo decide el precio de mercado y las dos estrategias se calculan siempre (FR-026); se puede sobrescribir con `VALUATION_ACTIVE_STRATEGY` o `-Dvaluation.active-strategy`.
- [X] T029 [US4] Correr `./mvnw test -Dtest='ValuationStrategyActivePropertyTest,ValuationStrategyCatalogContextTest,ValuationStrategyCatalogTest'` (verde) y confirmar con grep que `valuation.` no aparece en `backend/src/main/resources/` ni en `backend/src/test/resources/`.

**Checkpoint**: La estrategia es seleccionable por configuración sin tocar archivos de Entrega 1.

---

## Phase 7: User Story 5 - Proteger las versiones publicadas (Priority: P2)

**Goal**: La suite falla si cambia cualquier parámetro de una versión publicada sin subir su número de versión.

**Independent Test**: `./mvnw test -Dtest='PublishedStrategyVersionsTest'` pasa, y el cambio manual de T034 lo hace fallar.

**Depende de**: US3.

### Tests for User Story 5 ⚠️

- [X] T030 [P] [US5] Ampliar `TEST/service/valuation/strategy/ValuationStrategyDefinitionTest.java` con una clase `@Nested` `Fingerprint`:
  - El resultado son 64 caracteres hexadecimales en minúscula.
  - Dos definiciones iguales salvo `0.10` frente a `0.1` en un peso dan la misma huella.
  - El orden de inserción de los mapas de entrada no altera la huella.
  - La huella cambia si cambia cualquiera de estos datos, uno por vez (test parametrizado): `type`, `baseValue`, `scaleFactor`, `maxVariation`, `momentum.window`, `momentum.minHistory` o un peso cualquiera.
- [X] T031 [US5] Crear `TEST/service/valuation/strategy/PublishedStrategyVersionsTest.java`:
  - **Datos**: `private static final Map<String, String> PUBLISHED = Map.of("OFFICIAL:1", "<huella>", "MOMENTUM:1", "<huella>")`.
  - **Verificación reutilizable**: un método `static List<String> check(Collection<ValuationStrategyDefinition> definitions, Map<String, String> published)` que, para cada definición, busca la clave `code + ":" + version` y devuelve un mensaje de error por cada problema (lista vacía si está todo bien):
    - **Clave ausente**: `"<CODE> v<N> no tiene huella publicada: agregá \"<CODE>:<N>\" -> \"<actual>\" a PUBLISHED."`.
    - **Huella distinta**: `"<CODE> v<N> ya está publicada y su contenido cambió (huella esperada <esperada>, actual <actual>). No modifiques una versión publicada: creá <CODE> v<N+1> con los nuevos valores y registrá su huella en PUBLISHED."`.
  - **Test principal (US5-3)**: `check(new ValuationStrategyCatalog("OFFICIAL").all(), PUBLISHED)` es vacía. Si no lo es, el test falla mostrando todos los mensajes unidos.
  - **Casos sintéticos**. Se arman a partir de `OfficialStrategyV1.DEFINITION` con un helper que copia la definición cambiando la versión y/o el peso `GOALS` de MIDFIELDER a `0.26`:
    - **US5-1**: la v1 modificada **sin** subir la versión, contra `PUBLISHED`, devuelve exactamente un mensaje que contiene `"OFFICIAL v1 ya está publicada"`.
    - **US5-2**: la definición modificada como **v2**, contra una copia de `PUBLISHED` a la que se agrega `"OFFICIAL:2" -> <su huella>`, devuelve una lista vacía. Además, la entrada `"OFFICIAL:1"` de esa copia sigue igual a la de `PUBLISHED` y coincide con `OfficialStrategyV1.DEFINITION.fingerprint()`.
    - **Versión nueva sin registrar**: la misma v2 contra `PUBLISHED` sin agregarle nada devuelve un mensaje que contiene `"OFFICIAL v2 no tiene huella publicada"`.
  - **Javadoc**: copiar la regla de versionado de contracts/valuation-strategies.md §5.

### Implementation for User Story 5

- [X] T032 [US5] Agregar `public String fingerprint()` a `MAIN/service/valuation/strategy/ValuationStrategyDefinition.java`, según research.md D6:
  - **Texto canónico**: `code|version|type.name()|norm(baseValue)|norm(scaleFactor)|norm(maxVariation)|momentum`.
    - `momentum` se escribe como `window,minHistory`, o `-` si es nulo.
    - Al final se agrega `|` seguido de las entradas `POSICION:METRICA=norm(peso)`, ordenadas por nombre de posición y de métrica y separadas con `;`.
    - `norm(x)` = `x.stripTrailingZeros().toPlainString()`.
  - **Hash**: SHA-256 del texto en UTF-8 (`MessageDigest`), en hexadecimal minúscula (`HexFormat.of().formatHex`).
- [X] T033 [US5] Correr `./mvnw test -Dtest='PublishedStrategyVersionsTest'` con las huellas de ejemplo. El test falla y su mensaje muestra la huella real de cada versión. Copiar esas huellas reales a `PUBLISHED` en `TEST/service/valuation/strategy/PublishedStrategyVersionsTest.java` y volver a correr el test hasta que pase. Verificar también que T030 está en verde.
- [X] T034 [US5] Verificación manual (quickstart.md §3):
  1. En `MAIN/service/valuation/strategy/OfficialStrategyV1.java`, cambiar el peso `GOALS` de MIDFIELDER a `0.26`.
  2. Correr `PublishedStrategyVersionsTest` y confirmar que falla con el mensaje "ya está publicada…".
  3. **Revertir** el cambio y confirmar que el test vuelve a pasar. No dejar ningún cambio en el archivo.

**Checkpoint**: "OFFICIAL v1" y "MOMENTUM v1" significan siempre lo mismo.

---

## Phase 8: User Story 6 - Recolectar gambetas para la fórmula OFFICIAL (Priority: P2)

**Goal**: `PlayerStats.dribbles` se completa desde WhoScored con una consulta opcional. Si la fuente no da el dato, queda en `null`, y la liga se sincroniza igual (FR-033 a FR-035).

**Independent Test**: `./mvnw test -Dtest='WhoScoredStatsMapperTest,WhoScoredAdapterTest,PlayerStatsServiceTest,PlayerStatsRepositoryTest,CacheConfigTest,WhoScoredRedisCacheIntegrationTest'`.

**Depende de**: nada. Se puede hacer en paralelo con US1 a US5, y se puede mover a otra feature sin afectar al resto.

### Fixture

- [X] T035 [US6] Crear `backend/src/test/resources/whoscored/league-dribbles.json` con la misma forma que `backend/src/test/resources/whoscored/league-tackles.json` (mismo envoltorio `playerTableStats`, mismos `playerId`, `teamId` y `name`), donde cada fila trae `dribbleWonTotal`.
  - Si se puede, capturar la respuesta real del feed con `category=dribbles`, `subcategory=success` y `statsAccumulationType=2`, igual que se capturaron los otros fixtures (ver `specs/005-whoscored-player-stats/`), y confirmar el nombre real de la categoría, la subcategoría y el campo.
  - Si el nombre real difiere, usar el real en T036, T041 y T042 y anotarlo en research.md D8.
  - Incluir al menos un jugador sin `dribbleWonTotal`.

### Tests for User Story 6 ⚠️

- [X] T036 [P] [US6] Ampliar `TEST/adapter/whoscored/WhoScoredStatsMapperTest.java`:
  - Adaptar todas las llamadas a `merge(...)` a la nueva firma, que agrega la respuesta de gambetas después de `tackles`. En las existentes pasar `empty()` o `parse("league-dribbles.json")`.
  - Con `league-dribbles.json`, un jugador del fixture tiene `dribbles()` igual al valor del JSON.
  - Al jugador sin dato le queda `dribbles() == null`.
  - Con una respuesta vacía de gambetas, todas las demás métricas son iguales a las de antes.
  - `hasAnyMetric()` es verdadero si la única métrica presente es `dribbles`.
- [X] T037 [P] [US6] Ampliar `TEST/adapter/whoscored/WhoScoredAdapterTest.java`:
  - Si el feed client falla (lanza `WhoScoredException` o devuelve un bloqueo de Cloudflare) **sólo** para la consulta `dribbles`, `fetchLeaguePlayers` devuelve las filas igual, `merge` recibe una respuesta vacía en lugar de las gambetas y se loguea `whoscored_dribbles_unavailable`.
  - Si falla `summary`, `shots`, `key-passes` o `tackles`, el comportamiento actual no cambia (se lanza la excepción).
  - Ajustar el `verify(mapper, never()).merge(...)` existente a 6 argumentos.
- [X] T038 [P] [US6] Ampliar `TEST/service/PlayerStatsServiceTest.java`:
  - Agregar el parámetro `dribbles` al helper que construye `WhoScoredPlayerStats` (línea ~680).
  - Una fila con `dribbles = 12` deja `PlayerStats.getDribbles() == 12` (US6-1).
  - Una fila con `dribbles = null` deja `getDribbles() == null` y no 0, y el resto de las métricas se guarda igual (US6-2).
- [X] T039 [P] [US6] Ampliar `TEST/repository/PlayerStatsRepositoryTest.java` (es un test, no está protegido): se persiste y se relee `dribbles = 12`, y un `PlayerStats` sin `dribbles` se relee con `null` (US6-3).

### Implementation for User Story 6

- [X] T040 [US6] **(Único cambio aprobado a Entrega 1)** En `MAIN/model/PlayerStats.java`, agregar después de `tackles`: `private Integer dribbles;`, sin `@Column` (la columna se llama `dribbles` y es nullable), con el javadoc "Gambetas exitosas (WhoScored `dribbleWonTotal`); {@code null} si la fuente no las informa (spec 013, FR-033)". No tocar nada más de ese archivo.
- [X] T041 [P] [US6] En `MAIN/adapter/whoscored/dto/WhoScoredFeedResponse.java`, agregar el componente `JsonNode dribbleWonTotal` al record `Row`, junto a `tackleWonTotal`.
- [X] T042 [US6] En `MAIN/adapter/whoscored/dto/WhoScoredPlayerStats.java`:
  - Agregar `Integer dribbles` después de `tackles`.
  - Incluirlo en el `Stream.of(...)` de `hasAnyMetric()`.
  - El DTO se cachea en Redis. Las entradas viejas sin `dribbles` tienen que deserializar con `null`: verificarlo en T045.
- [X] T043 [US6] En `MAIN/adapter/whoscored/WhoScoredStatsMapper.java`:
  - Cambiar la firma a `merge(summary, shots, keyPasses, tackles, dribbles, fetchedAt)`.
  - Indexar las gambetas con `index(dribbles, "dribbles")` e incluirlas en `teamNames(...)`.
  - Pasarlas al constructor con `integer(detail(dribbleRows, key, Row::dribbleWonTotal), key, "dribbles")`.
  - Actualizar el javadoc ("las 5 respuestas…; gambetas opcional").
- [X] T044 [US6] En `MAIN/adapter/whoscored/WhoScoredAdapter.java`:
  - Agregar la constante `DRIBBLES_QUERY = new FeedQuery("dribbles", "success", 2)`, fuera de `LEAGUE_QUERIES`.
  - Agregar un método privado `fetchOptional(league, tournamentId, query)` que llame a `fetch(...)`. Si atrapa `WhoScoredException`, loguea `log.warn("whoscored_dribbles_unavailable league={} reason={}", league, e.getMessage())` y devuelve una `WhoScoredFeedResponse` vacía.
  - En `fetchLeaguePlayers`, consultar las gambetas después de `tackles` y pasarlas a `merge`. Respetar el mismo `request-delay`.
  - Actualizar el javadoc del método: sólo las 4 consultas base son obligatorias.
- [X] T045 [US6] En `MAIN/service/PlayerStatsService.java`, agregar `stats.setDribbles(row.dribbles());` junto a `setTackles` (línea ~247).
  - Actualizar las construcciones de `WhoScoredPlayerStats` en `TEST/adapter/whoscored/WhoScoredRedisCacheIntegrationTest.java` y `TEST/config/CacheConfigTest.java` con el nuevo argumento.
  - En `CacheConfigTest`, agregar un caso que deserialice un JSON cacheado **sin** `dribbles` y obtenga `dribbles() == null`.
- [X] T046 [US6] Correr `./mvnw test -Dtest='WhoScoredStatsMapperTest,WhoScoredAdapterTest,PlayerStatsServiceTest,PlayerStatsRepositoryTest,CacheConfigTest,WhoScoredRedisCacheIntegrationTest'`. Tiene que terminar en verde.

**Checkpoint**: El término `0.10·gambetas` de OFFICIAL tiene datos reales, y la sincronización de estadísticas no perdió resiliencia.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Verificar la suite completa, el alcance y la regla de Entrega 1.

- [X] T047 Ampliar `TEST/e2e/PlayerQuoteQueriesE2ETest.java` para inyectar también `ValuationStrategyCatalog` y agregar un test que, después de la limpieza de `E2EDatabaseCleaner`, verifique que `catalog.all()` tiene 2 definiciones y `catalog.active().code()` es `"OFFICIAL"`. Esto cubre el edge case de spec L141: el catálogo no depende de datos sembrados. Va acá y no en T011 para no hacer depender US2 de US3. Después, correr `./mvnw test -DexcludedGroups=e2e` y `./mvnw test -Dgroups=e2e` desde `backend/`. Los dos tienen que terminar en `BUILD SUCCESS`, sin tests ignorados.
  - *Estado: verde. Sin e2e: 333 tests, 0 fallas, 2 salteados de Redis que ya se salteaban antes. Con `-Dgroups=e2e`: 21 tests, 0 fallas.*
- [X] T048 Correr `git diff --stat main -- backend/src/main/java/com/example/demo/model backend/src/main/java/com/example/demo/repository backend/src/main/resources`.
  - Los únicos archivos **modificados** tienen que ser `model/PlayerStats.java`, con el agregado de `dribbles` y nada más.
  - Los únicos archivos **nuevos** tienen que ser `model/PlayerQuote.java`, `model/QuoteStatus.java` y `repository/PlayerQuoteRepository.java`.
  - Confirmar que no hay controllers, schedulers ni endpoints nuevos y que `MAIN/model/Player.java` no cambió.
  - Confirmar con grep que no hay ningún `@Entity` ni `@Table` en `MAIN/service/valuation/`, es decir, que no existe una tabla de estrategias (FR-017).
- [X] T049 [P] Revisar con criterio de SonarCloud los archivos nuevos de `MAIN/service/valuation/strategy/`, `MAIN/model/PlayerQuote.java` y `MAIN/repository/PlayerQuoteRepository.java`: sin imports sin usar, sin código duplicado entre `OfficialStrategyV1` y `MomentumStrategyV1` más allá de los datos, y constantes con nombres claros.
- [X] T050 [P] Si algún nombre real difiere de lo documentado (por ejemplo, la categoría del feed de gambetas o la firma de un método del repositorio), actualizar `specs/013-cotizacion-modelo/quickstart.md`, `data-model.md` o `research.md` para que la documentación refleje el código.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Fase 1)**: no depende de nada.
- **Foundational (Fase 2)**: depende de Setup y bloquea US1 a US5. US6 no la necesita.
- **US1 (Fase 3)**: depende de Fase 2 (`QuoteStatus`).
- **US2 (Fase 4)**: depende de US1, porque usa la entidad y el repositorio.
- **US3 (Fase 5)**: depende de Fase 2 (`StrategyType` y `Metric`). Es independiente de US1 y US2.
- **US4 (Fase 6)** y **US5 (Fase 7)**: dependen de US3. Son independientes entre sí, salvo que ambas editan archivos de estrategia (ver más abajo).
- **US6 (Fase 8)**: depende sólo de Setup.
- **Polish (Fase 9)**: depende de todas las historias.

### User Story Dependencies

```text
Setup ─┬─ Foundational ─┬─ US1 ── US2
       │                └─ US3 ─┬─ US4
       │                        └─ US5
       └─ US6
```

### Within Each User Story

- Primero los tests, que tienen que fallar. Después la implementación y por último la corrida en verde.
- En US1 y US2, la entidad va antes que el repositorio y el repositorio antes que las consultas.
- En US3, el record va antes que las definiciones y las definiciones antes que el catálogo.
- En US5, la huella va antes de fijar los hashes (T033) y la verificación manual va al final.
- En US6, el fixture va primero. Después DTO, mapper, adapter y service, en ese orden, porque cada paso cambia firmas que usa el siguiente.

### Parallel Opportunities

- Las tres tareas de la Fase 2 (T002, T003 y T004) se pueden hacer en paralelo.
- Terminada la Fase 2, **US1→US2**, **US3→(US4 ∥ US5)** y **US6** son tres líneas de trabajo independientes.
- US4 (T028) y US5 (T032) tocan archivos distintos (`ValuationStrategyCatalog` y `ValuationStrategyDefinition`), así que se pueden hacer en paralelo.
- Dentro de US3, los tests T015 a T018 y las clases T019 y T021 a T023 van en paralelo. T020 va antes que T021 y T022 sólo para compilar; se pueden escribir a la vez.
- Dentro de US6, los tests T036 a T039 van en paralelo, y T041 también.

---

## Parallel Example: User Story 3

```bash
# Tests de US3 juntos:
Task: "T015 ValuationStrategyDefinitionTest en TEST/service/valuation/strategy/"
Task: "T016 OfficialStrategyV1Test en TEST/service/valuation/strategy/"
Task: "T017 MomentumStrategyV1Test en TEST/service/valuation/strategy/"
Task: "T018 ValuationStrategyCatalogTest en TEST/service/valuation/strategy/"

# Clases independientes de US3 juntas (después de T020):
Task: "T021 OfficialStrategyV1 en MAIN/service/valuation/strategy/"
Task: "T022 MomentumStrategyV1 en MAIN/service/valuation/strategy/"
Task: "T023 UnknownValuationStrategyException en MAIN/exception/"
```

## Parallel Example: User Story 6

```bash
Task: "T036 WhoScoredStatsMapperTest"
Task: "T037 WhoScoredAdapterTest"
Task: "T038 PlayerStatsServiceTest"
Task: "T039 PlayerStatsRepositoryTest"
```

---

## Implementation Strategy

### MVP First (US1 + US2 + US3, las tres P1)

1. Fases 1 y 2.
2. US1 y luego US2: historial inmutable y sus consultas, verificadas en H2 y en PostgreSQL.
3. US3: las dos estrategias con sus pesos.
4. **PARAR y validar**: con esto la consigna queda cubierta en lo esencial (dos estrategias con pesos por posición, historial y consultas).

### Incremental Delivery

1. MVP: US1, US2 y US3.
2. US4: la estrategia activa por configuración.
3. US5: el candado de versiones. Conviene hacerlo **antes** de que cualquier feature posterior (015 en adelante) guarde cotizaciones reales.
4. US6: las gambetas. Tiene que estar antes de que la feature 015 calcule OFFICIAL con datos reales.
5. Polish.

### Parallel Team Strategy

Con las dos integrantes, después de la Fase 2:
- **Integrante A**: US1, luego US2 y luego US6.
- **Integrante B**: US3, luego US4 y luego US5.

---

## Notes

- Una tarea [P] toca archivos distintos y no depende de trabajo incompleto.
- No commitear desde el agente. Los commits los hace el equipo.
- No editar `application*.properties` en ningún caso. La estrategia activa por defecto vive en el `@Value`.
- Si una tarea necesitara tocar otro archivo de Entrega 1 distinto de `PlayerStats.java`, detenerse y registrar `[NEEDS CLARIFICATION]` con el archivo, el cambio mínimo y una alternativa (FR-031).