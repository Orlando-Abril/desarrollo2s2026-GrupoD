# Research: E2E del escenario de evolución de cotizaciones

**Feature**: `specs/021-e2e-escenario-cotizaciones` | **Fecha**: 2026-10-26

## R1. Control de las fechas de cálculo

**Decisión**: una `@TestConfiguration` estática dentro de `QuoteEvolutionE2ETest` declara un bean `@Primary` de tipo `MutableClock`, que extiende `java.time.Clock`. El bean se llama `evolutionClock`, distinto de `clock`. El test lo ajusta con `set(Instant)` antes de cada corrida.

**Por qué funciona**:
- Spring Boot rechaza por defecto que dos beans tengan el mismo nombre (`spring.main.allow-bean-definition-overriding=false`), y esa propiedad no se puede cambiar sin tocar un `application.properties` protegido. Con otro nombre conviven los dos beans `Clock`.
- `@Primary` hace que todos los puntos de inyección reciban el reloj del test: `QuoteCalculationService`, `StatsSnapshotService`, `WhoScoredAdapter` y `PlayerCatalogService`. Spring resuelve el primario antes de desempatar por nombre de parámetro, así que el parámetro `Clock clock` no elige el bean de `ClockConfig`.
- Ningún otro componente que use el tiempo lo toma del `Clock`. `JwtUtil` usa `Instant.now()`, pero el test se autentica con API key, así que fijar fechas de octubre de 2026 no afecta la autenticación.
- `QuoteCalculationService` fija `calculatedAt = clock.instant()` antes del snapshot, y `StatsSnapshotService` usa el mismo reloj para `capturedAt`. Con el reloj fijo en una corrida, los dos instantes coinciden, y la regla de "datos nuevos" de MOMENTUM (019, FR-028) se cumple en la corrida siguiente: otro `runId` y un `capturedAt` posterior al `calculatedAt` anterior.

**Fechas**: `2026-10-05T18:00:00Z`, `2026-10-12T18:00:00Z` y `2026-10-19T18:00:00Z`. Son días UTC distintos y lejos de la medianoche, así que `at` (fin del día UTC, 017) las separa sin ambigüedad. La consulta negativa usa `at=2026-10-04`.

**Contexto propio**: la configuración extra hace que el test tenga su propio contexto de Spring en la caché de contextos. Es aceptable: es un solo test y no comparte estado con los otros e2e.

**Alternativas consideradas**:
- **`@MockitoBean Clock`:** el `Clock` mockeado devuelve `null` en `getZone()` y en otros métodos que use `LocalDate.now(clock)`, y la intención queda menos clara que con un reloj real ajustable.
- **Cambiar `ClockConfig` o habilitar el override de beans:** toca código de producción o un archivo protegido.
- **Insertar cotizaciones con fechas a mano:** no ejercita la corrida de recálculo, y la spec pide dispararla por HTTP.

## R2. Usuario ADMIN

**Decisión**: igual que en `specs/016-recalculo-cotizaciones/quickstart.md` §1:
1. `POST /auth/register` por HTTP con `username` `e2e-admin`, un email y una contraseña de 8 caracteres o más.
2. `UPDATE users SET role = 'ADMIN' WHERE username = 'e2e-admin'` con `JdbcTemplate`.
3. Se usa como `X-API-KEY` la `apiKey` que devolvió el registro.

El rol se lee de la base en cada pedido y la key hereda el rol de su dueño, así que no hace falta volver a iniciar sesión. La misma key autentica las consultas de historial y ranking, que sólo exigen estar autenticado.

**Alternativa considerada**: `AuthFixtures.user` y `AuthFixtures.apiKey`, como hace `QuoteRecalculationE2ETest`. Se descarta porque el usuario pidió replicar el quickstart de 016, y además así el escenario también ejercita el registro real.

## R3. Dónde se verifica cada cosa

| Qué | Cómo |
|-----|------|
| Resumen de cada corrida | JSON de `POST /quotes/recalculate`: `playersProcessed` 5, `playersFailed` 0, `skippedStrategies` vacío, `strategies` con OFFICIAL y MOMENTUM y `marketValueUpdated` true. Además, `calculatedAt` igual al instante del reloj. |
| Historial | JSON de `GET /players/{id}/quotes?strategy=X`: 3 elementos con `calculatedAt` en las tres fechas, en orden, `strategy` X y `version` 1. |
| Vigente por fecha | JSON de `GET /players/{id}/quotes?strategy=X&at=D`: igual al elemento del historial de la fecha D. Con `at=2026-10-04`, 404 `quote_not_found`. |
| Evolución | Valores del historial: comparación entre la segunda y la tercera fecha, estados, y el límite de ±15% en MOMENTUM. |
| Ranking | JSON de `GET /players/ranking?strategy=X` (y sin `strategy`). |
| Valor de mercado | `PlayerRepository.findById(...).getMarketValue()`, que es la única excepción a las aserciones por HTTP. Se compara con el `value` OFFICIAL del historial. |

Los valores se comparan con `BigDecimal.compareTo`, nunca con `equals`, porque el JSON puede perder ceros a la derecha.

**Estadísticas**: se cargan y actualizan con `PlayerStatsRepository.saveAndFlush`. Cada actualización cambia `fetchedAt`. El snapshot se identifica por `(jugador, fetchedAt)` (014), así que sin una `fetchedAt` nueva no hay ventana nueva.

## R4. Datos del escenario y por qué la dirección es segura

Los datos están en [data-model.md](data-model.md). Se verificaron con el modelo de referencia decimal de MOMENTUM usado en la 019 y con la fórmula de OFFICIAL del contrato de 015.

**Jugador que mejora: Premier League, FORWARD.**
- Es el único delantero, así que en OFFICIAL todas sus métricas por 90 se normalizan a 0.5 (max = min) y sólo el rating mueve su score. El rating sube de 7.05 a 7.40, y el score OFFICIAL sube sin depender de los otros jugadores.
- En MOMENTUM, su segunda ventana supera su promedio de temporada y S > 0.5, así que la variación es positiva.

**Jugador que deja de jugar: Bundesliga, MIDFIELDER.**
- La primera ventana está por debajo de su promedio, con R ≈ 0.26.
- La segunda ventana tiene `fetchedAt` nueva y los mismos minutos: no aporta rendimiento, sólo baja la disponibilidad.
- S = R − w_MIN·(1 − D) = 0.26 − 0.10·5/9 ≈ 0.21 < 0.5, así que baja.

**Hallazgo**: si la primera ventana fuera muy buena (R ≳ 0.56), la penalización por disponibilidad (0.10 × 5/9 ≈ 0.056) no alcanzaría y MOMENTUM subiría aunque el jugador no jugara. Con un primer intento de datos el modelo dio 1.0942 → 1.1790. La regla que se usa es que la ventana jugada del que deja de jugar tenga R < 0.5 + w_MIN·(1 − D). Esto no contradice la 019: MOMENTUM promedia la forma de las ventanas jugadas y descuenta la falta de minutos, sin anular la buena forma reciente.

**Valores de referencia del modelo** (no se verifican exactos, FR-020 y supuestos de la spec):

| Jugador | OFFICIAL 1 → 2 → 3 | MOMENTUM 1 → 2 → 3 |
|---------|--------------------|--------------------|
| Premier (mejora) | 1.0540 → 1.0541 → 1.0548 | 1.0000 → 1.0361 → 1.1237 |
| La Liga | 1.0398 → 1.0398 → 1.0373 | 1.0000 → 0.9732 → 0.9391 |
| Serie A | 1.0626 → 1.0626 → 1.0626 | 1.0000 → 1.0179 → 1.0155 |
| Bundesliga (deja de jugar) | 1.0840 → 1.0840 → 1.0840 | 1.0000 → 0.9286 → 0.8469 |
| Ligue 1 | 1.0560 → 1.0560 → 1.0613 | 1.0000 → 0.9967 → 1.0050 |

En los dos rankings finales no hay empates de valor, así que el orden no depende del desempate por id.

**Alternativas consideradas**:
- **Verificar valores exactos:** se descarta porque cualquier ajuste de fórmula que respete la spec de cada estrategia rompería el e2e. Los valores exactos ya los cubren los tests unitarios de 015 y 019.
- **Que el que mejora sea un mediocampista o defensor:** su score OFFICIAL depende de los demás de su posición (min-max), y la dirección quedaría atada a los datos de otro jugador.

## R5. Caché del ranking

Antes de la tercera corrida se consulta `GET /players/ranking?strategy=MOMENTUM`, para que quede en Redis. Después de la corrida se consulta otra vez y el valor del jugador que mejora tiene que ser el nuevo. Esto aprovecha la invalidación de la 018 sin depender de su implementación interna.

## R6. Legibilidad y determinismo

- Un único `@Test`, por la limpieza por método (plan, Structure Decision).
- `SoftAssertions` en los bloques por jugador × estrategia × fecha, con `as("%s %s %s", liga, estrategia, fecha)`.
- Sin `Thread.sleep` ni dependencia del reloj real: todos los instantes salen de `MutableClock`.
- El orden de los jugadores se toma de `E2EFixtures.FixtureSet.forLeague`, no de ids generados.
