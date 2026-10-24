# Implementation Plan: Historial de cotizaciones de un jugador

**Branch**: `feature/historial-cotizaciones` | **Date**: 2026-10-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/017-historial-cotizaciones/spec.md` y decisiones del equipo para `QuoteQueryService`, controller, DTO, errores y pruebas MockMvc.

## Summary

Exponer `GET /players/{id}/quotes` desde el área de cotizaciones existente, sin tocar `PlayerController` ni el manejo global de errores. Un nuevo `QuoteQueryService` de sólo lectura valida el jugador, resuelve la estrategia pedida o activa y reutiliza las consultas de `PlayerQuoteRepository` para devolver el historial o la cotización vigente al fin de un día UTC. `QuoteController` incorpora el GET y conserva `POST /quotes/recalculate`; un nuevo `PlayerQuoteResponse` fija la representación pública. `QuoteExceptionHandler` agrega errores 400/404 específicos con el shape común. La prueba principal es MockMvc con H2, perfil `test` y cotizaciones persistidas manualmente, complementada por la verificación OpenAPI y una regresión explícita de las rutas de `PlayerController`.

## Technical Context

**Language/Version**: Java 17

**Primary Dependencies**: Spring Boot 4.1.1, Spring Web MVC, Spring Data JPA, Spring Security, Jakarta Validation, springdoc-openapi 3.1.1

**Storage**: PostgreSQL en ejecución normal; H2 con el perfil de pruebas. Se reutilizan `players`, `player_quotes` y el índice `idx_player_quotes_player_strategy_time`; no hay migraciones ni entidades nuevas.

**Testing**: JUnit 5, Spring Boot Test, MockMvc, Spring Security Test y Mockito. La prueba de esta feature usa repositorios reales con H2 bajo `@UnitTestProfile`; no repite `@DataJpaTest` porque las consultas ya están cubiertas por la feature 013. Se ejecuta el `ExternalSourcesDownE2ETest` existente de 016 en lugar de crear otro escenario E2E.

**Target Platform**: Backend JVM desplegable como servicio HTTP en Linux o entorno local equivalente

**Project Type**: Servicio web REST dentro del backend existente

**Performance Goals**: Con 2.500 cotizaciones persistidas, 5 warmups y 20 mediciones HTTP secuenciales sin carga concurrente en el mismo host, p95 menor a 1 segundo y al menos 19 de 20 respuestas completas en menos de 1 segundo; evidencia en `performance-results.md`

**Constraints**:
- No modificar `PlayerController`, `GlobalExceptionHandler`, `PlayerRepository` ni otros archivos de Entrega 1.
- No modificar `PlayerQuoteRepository`: se reutilizan las consultas de rango y vigencia entregadas por 013.
- Conservar exactamente la ruta y seguridad de `POST /quotes/recalculate` al adaptar el mapping de `QuoteController`.
- Interpretar `from`, `to` y `at` como días UTC; `at` es excluyente con `from`/`to`.
- Devolver lista sin `at` y objeto único con `at`, ambos documentados en una sola operación OpenAPI.
- No consultar fuentes externas, no recalcular y no modificar cotizaciones ni precios de mercado.

**Scale/Scope**: Un endpoint GET, un servicio de consulta, un DTO, tres excepciones acotadas, dos archivos existentes de la feature 016 modificados y dos suites MockMvc/OpenAPI; hasta 2.500 cotizaciones por jugador sin paginación

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Constitución | Aplicación en el plan | Estado pre-diseño |
|-------------|------------------------|-------------------|
| **§1.2 Trazabilidad de valuación** | Cada respuesta conserva estrategia, versión, estado, valor, score y fecha del registro inmutable. | ✅ |
| **§1.3 Resiliencia** | El endpoint lee únicamente datos persistidos; su servicio no depende de clientes externos, se verifican cero interacciones y se reutiliza `ExternalSourcesDownE2ETest` de 016. | ✅ |
| **§2.1 Capas** | `QuoteController` valida/mapea; `QuoteQueryService` decide estrategia, rango y errores de negocio; los repositorios existentes acceden a datos. | ✅ |
| **§3.1 CI** | Los tests MockMvc y OpenAPI no llevan tag e2e y se ejecutan en el build normal; el cierre exige GitHub Actions en verde. | ✅ |
| **§3.2 SonarCloud** | El diseño reutiliza DTO/mapeo común, evita duplicar queries y limita el advice al controller de cotizaciones; el cierre exige Quality Gate aprobado, cero vulnerabilidades y menos de 10 issues menores. | ✅ |
| **§3.3 escenario 2** | La prueba MockMvc carga secuencias temporales para jugadores de las cinco ligas y verifica evolución y vigencia. La cobertura de repositorio contra PostgreSQL ya existe en 013. | ✅ |
| **§4.1 Autenticación** | `anyRequest().authenticated()` ya exige JWT o API key; el POST ADMIN conserva su matcher específico. | ✅ |
| **§5.1 Endpoint mínimo** | Se incorpora exactamente `GET /players/:id/quotes`. | ✅ |
| **§5.2 Índices** | Se reutiliza el índice compuesto de jugador, estrategia y fecha definido por 013; no hace falta un índice nuevo. | ✅ |
| **§5.3 Observabilidad** | El filtro existente propaga/genera `X-Correlation-ID`; al ser lectura no corresponde auditoría financiera. | ✅ |
| **§6.1 OpenAPI** | Ruta, parámetros, DTO, respuestas 200/400/401/404, `bearerAuth` y `apiKeyAuth` quedan anotados y probados contra `/v3/api-docs`. | ✅ |
| **§7 DoD** | Se preservan capas, seguridad, Swagger y regresiones; la suite normal valida el resultado. | ✅ |

No hay violaciones ni `NEEDS CLARIFICATION`; el gate pre-diseño pasa.

### Re-check post-diseño

Los artefactos de Phase 1 mantienen el gate en verde:

- [data-model.md](data-model.md) no agrega persistencia y conserva la trazabilidad completa de `PlayerQuote`.
- [contracts/quote-history-api.yaml](contracts/quote-history-api.yaml) define el endpoint obligatorio, los dos shapes de éxito, los errores comunes y ambos esquemas de autenticación.
- [quickstart.md](quickstart.md) valida la evolución temporal, seguridad, errores, OpenAPI y coexistencia de rutas sin depender de fuentes externas.
- Ningún diseño requiere modificar un archivo protegido de Entrega 1, la seguridad global ni las consultas de repositorio ya probadas.

**Resultado post-diseño**: ✅ PASS, sin excepciones que justificar.

## Project Structure

### Documentation (this feature)

```text
specs/017-historial-cotizaciones/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── performance-results.md                # NUEVO al ejecutar el protocolo de rendimiento
├── validation-results.md                 # NUEVO: evidencia final de CI y SonarCloud
├── contracts/
│   └── quote-history-api.yaml
├── checklists/
│   └── requirements.md
└── tasks.md                         # generado posteriormente por $speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/com/example/demo/
├── controller/
│   ├── QuoteController.java                 # MOD: GET nuevo + paths completos; conserva POST
│   └── PlayerController.java                # SIN CAMBIOS: archivo protegido
├── dto/quote/
│   └── PlayerQuoteResponse.java             # NUEVO
├── exception/
│   ├── QuoteExceptionHandler.java           # MOD: 400/404 de la consulta
│   ├── PlayerNotFoundException.java         # NUEVO
│   ├── QuoteNotFoundException.java          # NUEVO
│   ├── InvalidQuoteQueryException.java      # NUEVO
│   └── GlobalExceptionHandler.java          # SIN CAMBIOS: archivo protegido
├── repository/
│   ├── PlayerRepository.java                # SIN CAMBIOS
│   └── PlayerQuoteRepository.java           # SIN CAMBIOS; consultas de 013
└── service/valuation/
    └── QuoteQueryService.java               # NUEVO

backend/src/test/java/com/example/demo/
├── controller/
│   ├── QuoteHistoryControllerTest.java      # NUEVO: MockMvc + repos reales + seguridad/rutas
│   └── QuoteControllerTest.java             # MOD: mock del query service + validación sin interacciones
└── config/
    └── QuoteOpenApiTest.java                # MOD: contrato del GET y conserva el POST
```

**Structure Decision**: Se mantiene el backend Spring en capas existente. Como `QuoteController` y `QuoteExceptionHandler` de la feature 016 ya están presentes en la rama, se amplían esos componentes en lugar de crear otro controller o advice. El nuevo GET no se agrega a `PlayerController`: la pertenencia de la ruta al recurso `/players` no cambia la responsabilidad funcional de cotizaciones.

## Detailed Design

### QuoteQueryService

Crear `service/valuation/QuoteQueryService` con transacciones de sólo lectura y estas operaciones públicas:

```text
history(Long playerId, String strategy, LocalDate from, LocalDate to)
  -> List<PlayerQuoteResponse>

quoteAt(Long playerId, String strategy, LocalDate at)
  -> PlayerQuoteResponse
```

Flujo común:

1. Validar `playerId > 0`; si no se cumple, lanzar `InvalidQuoteQueryException` antes de consultar repositorios.
2. Verificar `PlayerRepository.existsById(playerId)`; si es falso para un id positivo, lanzar `PlayerNotFoundException` antes de consultar estrategia o cotizaciones.
3. Resolver `strategy`: `ValuationStrategyCatalog.active().code()` si es `null`; `catalog.find(strategy).code()` si fue enviado. No hacer trim ni normalizar mayúsculas.
4. Mapear cada `PlayerQuote` con `PlayerQuoteResponse.from(...)` dentro de la transacción de lectura.

El constructor del servicio contiene únicamente `PlayerRepository`, `PlayerQuoteRepository` y `ValuationStrategyCatalog`: no inyecta adaptadores, feeds ni clientes externos.

`history(...)`:

- Rechazar `from > to` con `InvalidQuoteQueryException`.
- Convertir `from` a inicio UTC inclusivo; convertir `to` a inicio UTC del día siguiente.
- Para extremos ausentes, usar constantes de instante finitas dentro del rango SQL soportado (`0001-01-01T00:00:00Z` y `9999-12-31T23:59:59.999999Z`).
- Invocar `findByPlayerIdAndStrategyCodeAndCalculatedAtBetweenOrderByCalculatedAtAscIdAsc`.
- Cuando hay `to`, excluir en memoria cualquier registro exactamente igual al inicio del día siguiente, porque la consulta existente usa `between` inclusivo; así el contrato conserva un límite superior exclusivo sin alterar el repositorio.

`quoteAt(...)`:

- Exigir `at` no nulo desde el controller.
- Invocar `PlayerQuoteRepository.findEffectiveOn(playerId, code, at)`, que ya usa el inicio UTC exclusivo del día siguiente y desempata por id descendente.
- Si el resultado está vacío, lanzar `QuoteNotFoundException(playerId, code, at)`.

### PlayerQuoteResponse

Crear el record `dto/quote/PlayerQuoteResponse` con:

```text
BigDecimal value
BigDecimal score              # nullable
QuoteStatus status
String strategy
Integer version
Instant calculatedAt
```

Agregar `@Schema` a record y componentes, ejemplos consistentes y `static from(PlayerQuote)`. No exponer id interno, jugador, run id ni correlation id.

### QuoteController y coexistencia de rutas

El `QuoteController` existente tiene `@RequestMapping("/quotes")`, que impediría mapear la nueva ruta hermana `/players/{id}/quotes`. Adaptarlo así:

1. Quitar el mapping de clase, sin cambiar `@RestController` ni `@Tag`.
2. Cambiar el mapping existente a `@PostMapping("/quotes/recalculate")`; su contrato, implementación y matcher ADMIN permanecen iguales.
3. Inyectar también `QuoteQueryService`.
4. Agregar un único `@GetMapping("/players/{id}/quotes")` con `id`, `strategy`, `from`, `to` y `at`; las fechas se ligan como `LocalDate` ISO.
5. Si `at` está presente junto con `from` o `to`, lanzar `InvalidQuoteQueryException` antes de invocar el servicio. Si está presente solo, devolver `queryService.quoteAt(...)`; si no, devolver `queryService.history(...)`.

El método GET devuelve `Object` únicamente en el borde HTTP porque el contrato exige dos shapes: lista sin `at`, objeto con `at`. Las operaciones del servicio y el DTO permanecen tipados. OpenAPI declara el 200 como `oneOf` entre `PlayerQuoteResponse` y un array del mismo esquema, explicando el discriminante por parámetro.

La prueba de coexistencia levanta ambos controllers, realiza un GET autenticado a `/players` y otro a `/players/{id}/quotes`, y verifica que cada respuesta conserva su shape. El arranque del contexto más ambas llamadas demuestran que no existe mapping ambiguo. El test existente de recálculo garantiza que `/quotes/recalculate` no cambia.

### Errores

Crear excepciones específicas sin anotaciones HTTP:

- `InvalidQuoteQueryException` para `playerId <= 0` → 400 `validation_error`.
- `PlayerNotFoundException(playerId)` → 404 `player_not_found`.
- `QuoteNotFoundException(playerId, strategy, at)` → 404 `quote_not_found`.
- `InvalidQuoteQueryException(message)` → 400 `validation_error`.

Ampliar `QuoteExceptionHandler`, que ya está acotado a `QuoteController` y tiene máxima precedencia, con esos mappings y con `MethodArgumentTypeMismatchException` → 400 `validation_error` para fechas o ids mal formados. Se sigue devolviendo `GlobalExceptionHandler.ErrorResponse`, pero `GlobalExceptionHandler` no se modifica.

`UnknownValuationStrategyException` conserva el mapping 400 `unknown_strategy` de 016. La cadena de seguridad responde 401 antes de entrar al controller; no se modifica `SecurityConfig`.

### Tests

Crear `QuoteHistoryControllerTest` con `@SpringBootTest`, `@AutoConfigureMockMvc`, `@UnitTestProfile` y `@Transactional`. Usar `PlayerRepository` y `PlayerQuoteRepository` reales; persistir jugadores y cotizaciones con fechas/estrategias/estados controlados. Simular `QuoteCalculationService` para aislar el endpoint POST y los adaptadores externos sólo para verificar cero interacciones; no configurar nuevamente Football-Data.org ni WhoScored.

Al ampliar el constructor de `QuoteController`, agregar `@MockitoBean QuoteQueryService` al `QuoteControllerTest` existente. Allí se prueban las combinaciones `at` + `from`/`to`, que deben responder 400 y ejecutar `verifyNoInteractions(queryService)`, sin spy ni un segundo contexto Spring pesado.

Cobertura mínima:

- historial con estrategia activa por defecto y estrategia explícita;
- evolución temporal cargada manualmente para al menos un jugador de cada una de las cinco ligas, cubriendo §3.3 escenario 2;
- orden ascendente, desempate y los seis campos del DTO, incluido `score: null`; `calculatedAt` debe representar el instante UTC persistido y serializarse con sufijo `Z`, sin exigir una cantidad concreta de fracciones de segundo;
- `from`, `to`, ambos, mismo día, extremos inclusivos y resultado vacío;
- `at` durante el día, entre cotizaciones, desempate y estrategia activa;
- 400 por `id <= 0`, estrategia vacía/desconocida/con case distinto, fecha vacía/mal formada/imposible, rango invertido y `at` combinado con rango;
- fechas de borde: `2026-1-1` inválida, `2025-02-29` imposible y `2024-02-29` válida;
- 401 sin credenciales, comprobando con un id positivo inexistente que no se filtra `player_not_found`;
- 404 `player_not_found` sólo para id positivo y `quote_not_found`;
- 200 con API key de `Role.USER` y 200 con JWT de `Role.USER`;
- no duplicar escenarios internos de bearer vencido ni API key inválida/revocada ya cubiertos por `JwtAuthFilterTest` y `ApiKeyAuthFilterTest` existentes;
- el flujo de historial usa exclusivamente cotizaciones persistidas, el servicio no depende de clientes externos y los adaptadores externos no reciben interacciones;
- coexistencia de `/players` y `/players/{id}/quotes` sin tocar `PlayerController`;
- ausencia de cambios en `/quotes/recalculate`, cubierta además por `QuoteControllerTest` existente.

Ampliar `QuoteOpenApiTest` para verificar `/players/{id}/quotes`, cinco parámetros, 200/400/401/404, `PlayerQuoteResponse`, respuesta lista/objeto y las alternativas `bearerAuth`/`apiKeyAuth`, conservando las aserciones del POST.

No crear otro `@DataJpaTest`: `PlayerQuoteRepositoryTest` y `PlayerQuoteQueriesE2ETest` de 013 ya prueban orden, rangos, vigencia y desempates; el nuevo MockMvc prueba su integración con la API. Tampoco crear otro escenario E2E de fuentes caídas: ejecutar el `ExternalSourcesDownE2ETest` existente de 016.

### Protocolo de rendimiento y cierre de calidad

Preparar exactamente 2.500 cotizaciones persistidas para un jugador y una estrategia. En el mismo host de desarrollo, sin carga concurrente, ejecutar 5 solicitudes de warmup no medidas y luego 20 solicitudes HTTP secuenciales midiendo el tiempo completo de cada respuesta. El criterio conjunto es p95 menor a 1 segundo y al menos 19 de 20 mediciones menores a 1 segundo. Registrar en `specs/017-historial-cotizaciones/performance-results.md` el commit, fecha, entorno, dataset, las 20 duraciones, el p95 calculado y el resultado pass/fail.

Como cierre, verificar el workflow de GitHub Actions asociado al commit y el Quality Gate de SonarCloud. Registrar referencias y resultados en `specs/017-historial-cotizaciones/validation-results.md`; se exige CI exitoso, Quality Gate aprobado, cero vulnerabilidades y menos de 10 issues de severidad menor.
