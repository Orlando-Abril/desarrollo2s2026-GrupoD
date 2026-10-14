# Research: Modelo de datos de la cotización y catálogo de estrategias

**Feature**: `specs/013-cotizacion-modelo` | **Fecha**: 2026-10-14

## 1. Trazabilidad consigna → requerimiento → diseño

| Consigna | Requerimiento (spec) | Dónde se resuelve |
|----------|----------------------|-------------------|
| "Al menos dos estrategias configurables de ponderación" | FR-013, FR-018, FR-019, FR-021 | `OfficialStrategyV1`, `MomentumStrategyV1`, `ValuationStrategyCatalog` |
| "Cada métrica deberá tener un peso configurable dentro de la estrategia" | FR-014, FR-016 | `ValuationStrategyDefinition.weights` + enum `Metric` |
| "Se puede ponderar distinto según posición" | FR-015 | `Map<Position, Map<Metric, BigDecimal>>`, con las 4 posiciones obligatorias |
| Pesos positivos o negativos (tarjetas) | FR-014, FR-019 | MOMENTUM v1: `YELLOW_CARDS` y `RED_CARDS` negativos |
| "Seleccionarse, configurarse" | FR-023 a FR-026 | `valuation.active-strategy` (por defecto `OFFICIAL`) |
| "Dejar traza de cuál fue utilizada en cada cotización" | FR-001, FR-027 | `PlayerQuote.strategyCode` + `strategyVersion`; test de huellas |
| "Debe almacenarse el historial de cotizaciones" | FR-001, FR-005 | `PlayerQuote` inmutable (`@Immutable`, sin update/delete en el repositorio) |
| "Cotización actual e histórica", "para una fecha dada" | FR-007 a FR-011 | 4 consultas de `PlayerQuoteRepository` |
| "Valor inicial de 1 crédito" | FR-018, FR-019 | `baseValue = 1.00` en ambas definiciones |
| Constitución §1.2 (gambetas entre las métricas) | FR-016, FR-033 a FR-035 | `Metric.DRIBBLES` + bloque B (dato de gambetas en `PlayerStats`) |
| Constitución §5.2 (índices del historial) | FR-012 | 2 índices compuestos en `player_quotes` |

## 2. Decisiones

### D1. Estrategias definidas en código Java (no YAML, no base)

- **Decision**: cada versión de estrategia es una constante Java (`OfficialStrategyV1.DEFINITION`, `MomentumStrategyV1.DEFINITION`) de un record inmutable `ValuationStrategyDefinition`. El catálogo las conoce por constructor.
- **Rationale**:
  - Las versiones no cambian una vez publicadas. Un valor en código se revisa en un PR, se versiona con git y lo protege un test (D6). En cambio, un YAML se puede pisar por entorno sin dejar rastro, y entonces "OFFICIAL v1" podría significar cosas distintas en dos máquinas.
  - `E2EDatabaseCleaner` trunca todas las tablas en cada test e2e. Si las estrategias vivieran en una tabla, habría que volver a sembrarlas en cada test. En código no dependen de la base (FR-017).
  - No toca `application.properties` (regla de Entrega 1).
- **Alternatives considered**:
  - Una tabla `valuation_strategies` con datos sembrados: queda descartada por FR-017, por el truncado e2e y porque obligaría a crear una administración de estrategias que está fuera de alcance.
  - `@ConfigurationProperties` desde YAML: es configurable por entorno, que es justo lo que no queremos para una versión publicada. Además requiere editar `application.properties`.

### D2. Una sola propiedad: la estrategia activa

- **Decision**: `@Value("${valuation.active-strategy:OFFICIAL}")`. El valor por defecto vive en el código, así que no se agrega nada a ningún `application*.properties`. Se puede sobrescribir con la variable de entorno `VALUATION_ACTIVE_STRATEGY` (por el relaxed binding de Spring) o con `-Dvaluation.active-strategy=MOMENTUM`.
- **Rationale**: la consigna pide que la estrategia sea "seleccionable", no que sus pesos sean editables en tiempo de ejecución (la decisión del equipo fue que son fijos por versión).
- **Validación al arranque**: el constructor del catálogo resuelve la activa. Si el código no existe, lanza `UnknownValuationStrategyException` con el valor pedido y los códigos válidos. Spring no levanta el contexto (FR-025).
- **Comparación de códigos**: exacta, sólo con `trim()`. No se ignoran mayúsculas para que el código guardado en `PlayerQuote.strategyCode` sea siempre el canónico.

### D3. Las dos estrategias se calculan siempre; la activa sólo fija el precio de mercado

- **Decision**: `all()` devuelve siempre ambas definiciones, sin importar cuál esté activa. `active()` sólo la usa quien decide `Player.marketValue` (feature 016).
- **Rationale**: FR-026. Además, así se puede cambiar de estrategia activa sin quedarse sin historial de la otra.

### D4. Inmutabilidad de `PlayerQuote` en tres niveles

- **Decision**:
  1. `@org.hibernate.annotations.Immutable` en la entidad: Hibernate no hace dirty checking ni emite `UPDATE`.
  2. `updatable = false` en todas las columnas, y la entidad sin setters. Se construye sólo con un builder o un constructor completo.
  3. `PlayerQuoteRepository extends Repository<PlayerQuote, Long>` (no `JpaRepository`). Sólo declara `save`, `saveAll`, `findById` y las 4 consultas, así que no existe ningún `delete*`.
- **Rationale**: FR-005 pide que no exista la operación, no sólo que falle al usarla. Que no haya `delete` en la interfaz es verificable en compilación y lo cubre un test por reflexión.
- **Alternatives considered**:
  - `JpaRepository` con `delete` sobrescrito para lanzar una excepción: igual expone la operación y Sonar lo marca.
  - Triggers en la base: no son portables entre H2 y PostgreSQL, y con `ddl-auto` no hay migraciones donde declararlos.

### D5. Semántica de las 4 consultas

| Consulta | Método | Semántica |
|----------|--------|-----------|
| Última de un jugador por estrategia | `findFirstByPlayerIdAndStrategyCodeOrderByCalculatedAtDescIdDesc` | Desempate por `id` descendente, que es la registrada más tarde (FR-011). |
| Historial por rango | `findByPlayerIdAndStrategyCodeAndCalculatedAtBetweenOrderByCalculatedAtAscIdAsc` | `Between` es inclusivo en ambos extremos (FR-008). Si `from > to`, el resultado es vacío sin error. |
| Vigente en una fecha | `findEffectiveOn(playerId, code, LocalDate)` (método `default`), que delega en `findFirstByPlayerIdAndStrategyCodeAndCalculatedAtLessThanOrderByCalculatedAtDescIdDesc(..., inicioDelDíaSiguienteUTC)` | Es la última calculada **hasta el fin del día** en UTC. También se expone `findEffectiveAt(playerId, code, Instant)` con `LessThanEqual` para pedirla en una fecha/hora exacta (FR-009). |
| Última de todos los jugadores | `findLatestForAllPlayers(code)` con JPQL `NOT EXISTS` | Devuelve una fila por jugador. El desempate por `id` usa la misma regla que la primera consulta. |

- **Por qué `< inicio del día siguiente` y no `<= 23:59:59.999999999`**: PostgreSQL guarda microsegundos y redondea los nanosegundos. `23:59:59.999999999` se redondea a `00:00:00.000000` del día siguiente e incluiría cotizaciones de ese día. H2 trunca distinto. Por eso el test e2e contra PostgreSQL es obligatorio (FR-030), y la frontera exclusiva no depende de la precisión del motor.
- **Por qué `NOT EXISTS` y no `DISTINCT ON` ni una función de ventana**: es JPQL estándar, funciona igual en H2 y en PostgreSQL y la resuelve el índice `(player_id, strategy_code, calculated_at)`. `DISTINCT ON` sólo existe en PostgreSQL. En el volumen esperado (5 ligas, unos 2.500 jugadores, 2 estrategias y una corrida semanal) el costo es despreciable.
- **Todas las versiones juntas**: las consultas filtran por `strategyCode` y no por versión. Las cotizaciones de una futura v2 siguen la misma serie que las de v1, y cada fila conserva su versión.

### D6. Protección de versiones publicadas con huellas

- **Decision**: `ValuationStrategyDefinition.fingerprint()` devuelve un SHA-256 en hexadecimal de una representación canónica:
  - El texto es `code|version|type|baseValue|scaleFactor|maxVariation|momentum|POSICION:METRICA=peso;...`.
  - Las posiciones y métricas se ordenan **por nombre**, no por ordinal, para que reordenar un enum no altere la huella.
  - Los `BigDecimal` se normalizan con `stripTrailingZeros().toPlainString()`, para que `0.10` y `0.1` den la misma huella. Es un cambio cosmético, no de significado.
- El test `PublishedStrategyVersionsTest` tiene un mapa fijo `"OFFICIAL:1" -> "<hash>"`, `"MOMENTUM:1" -> "<hash>"`. La verificación está en un método `check(definitions, published)` que devuelve la lista de problemas: el test principal lo aplica al catálogo real, y hay casos sintéticos para una v1 modificada, una v2 registrada y una v2 sin registrar. Los mensajes son:
  - Si una definición del catálogo tiene entrada y el hash no coincide, el test falla con: *"OFFICIAL v1 ya está publicada y su contenido cambió (huella esperada X, actual Y). No modifiques una versión publicada: creá OFFICIAL v2 con los nuevos valores y registrá su huella en PUBLISHED."*
  - Si una definición no tiene entrada, el test falla con: *"OFFICIAL v2 no tiene huella publicada: agregá "OFFICIAL:2" -> "Y" a PUBLISHED."*
  - Las entradas de versiones viejas quedan en el mapa como registro histórico.
- **Rationale**: FR-027 y US5. El hash compara todos los parámetros a la vez y el mensaje explica qué hacer.
- **Alternatives considered**:
  - Comparar cada peso uno por uno en el test: duplica la tabla de pesos y es fácil actualizar los dos lados a la vez sin darse cuenta.
  - Un snapshot en un archivo de recursos: igual de efectivo, pero el mapa en el test es más visible en el PR.

### D7. Parámetros de las estrategias

Los valores exactos están en [contracts/valuation-strategies.md](contracts/valuation-strategies.md).

- `baseValue = 1.00` en ambas.
- `scaleFactor` y `maxVariation` se declaran como datos. Su interpretación (cómo se pasa del score al precio y cómo se limita la variación por corrida) es de las features 015 y 019. Esta feature sólo valida los rangos: `scaleFactor > 0` y `0 < maxVariation <= 1` (FR-020).
- **OFFICIAL v1**: el perfil de referencia es MIDFIELDER, con la fórmula de la consigna sin cambios. Las demás posiciones redistribuyen los pesos, que siguen sumando 1.00 y no son negativos.
- **MOMENTUM v1**: los pesos positivos suman 1.00 en cada posición. Las tarjetas suman aparte, con peso negativo. Los parámetros extra (`MomentumParameters`) son:
  - `window = 4`: cantidad de cotizaciones previas que mira la forma reciente.
  - `minHistory = 2`: mínimo de cotizaciones previas que necesita. Si hay menos, el algoritmo 019 registra `INSUFFICIENT_HISTORY`.
  - El record exige `momentum != null` si y sólo si `type == MOMENTUM`.
- **Por qué la forma reciente se mide sobre cotizaciones previas**: `PlayerStats` guarda una sola fila por jugador (los totales de la temporada), así que no hay una serie de estadísticas. La serie disponible es el historial de cotizaciones, y por eso existe el estado `INSUFFICIENT_HISTORY`.

### D8. Bloque B: dato de gambetas (único cambio a Entrega 1, aprobado en la Pregunta 1, opción B)

- **Decision**: agregar `Integer dribbles` (nullable) a `PlayerStats` y completarlo con una quinta consulta al feed de WhoScored, `FeedQuery("dribbles", "success", 2)`, que trae el campo `dribbleWonTotal`.
- **Resiliencia (FR-035)**: hoy `fetchLeaguePlayers` falla toda la liga si falta alguna respuesta. La consulta de gambetas va a ser **opcional**: si falla o devuelve una estructura inesperada, se loguea `whoscored_dribbles_unavailable league=...` y el mapper recibe una respuesta vacía. En ese caso las gambetas quedan en `null` y el resto de las estadísticas se guarda igual que hoy.
- **Esquema**: no hay migraciones (`ddl-auto=update` en dev, `create-drop` en tests y e2e). Hibernate agrega la columna nullable sin tocar las filas existentes (FR-033, US6-3).
- **A verificar contra el feed real (pendiente)**: el nombre exacto de categoría, subcategoría y campo (`dribbles` / `success` / `dribbleWonTotal`). Al implementar no se capturó una respuesta real, porque WhoScored está detrás de Cloudflare y requiere el transporte por navegador. `test/resources/whoscored/league-dribbles.json` es sintético, igual que los otros fixtures: tiene los mismos jugadores que `league-tackles.json` y Mbeumo sin el campo. Si el nombre real difiere, cambian sólo `DRIBBLES_QUERY` en `WhoScoredAdapter`, el componente `dribbleWonTotal` de `WhoScoredFeedResponse.Row` y el fixture. Mientras tanto, como la consulta es opcional, un nombre equivocado no rompe la sincronización: las gambetas quedan en `null`.
- **Archivos tocados**:
  - `model/PlayerStats.java` (Entrega 1, cambio aditivo aprobado).
  - `WhoScoredAdapter`, `WhoScoredStatsMapper`, `WhoScoredFeedResponse.Row`, `WhoScoredPlayerStats` y `PlayerStatsService` (una línea `setDribbles`).
  - No se tocan repositorios ni `application.properties`.

### D9. Tests y perfiles

- **Unitarios puros** (sin contexto): el catálogo, las definiciones y la huella. Construyen `new ValuationStrategyCatalog("MOMENTUM")` directamente.
- **Con contexto**:
  - `@UnitTestProfile` + `@DataJpaTest` para el repositorio: H2 en modo PostgreSQL y `TestEntityManager` para controlar `calculatedAt` y los empates.
  - `ApplicationContextRunner` para la propiedad activa y el arranque fallido. Es más liviano que un `@SpringBootTest` por cada caso.
  - Un `@SpringBootTest` + `@UnitTestProfile` que verifica que en el contexto real la activa por defecto es OFFICIAL.
- **E2E**: `@E2ETest` + `E2EFixtures.createPlayersFromFiveLeagues()` y las 4 consultas contra PostgreSQL real, con fronteras de día y empates. El cleaner trunca `player_quotes` sin configuración extra, porque lista todas las tablas del esquema.