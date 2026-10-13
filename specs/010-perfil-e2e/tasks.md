---

description: "Dependency-ordered implementation tasks for the e2e test profile"
---

# Tasks: Perfil e2e con infraestructura real

**Input**: Design documents from `/specs/010-perfil-e2e/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Esta feature es infraestructura de pruebas y exige tests explícitos. Las tareas de verificación se incluyen dentro de cada historia y deben demostrar tanto los caminos exitosos como las condiciones de omisión/fallo.

**Protected files**: No modificar `backend/pom.xml`, `.github/workflows/ci.yml`, ningún `application.properties` existente, `SecurityConfig`, filtros ni controllers. Si una tarea revela que un cambio allí es imprescindible, detener la implementación y registrar `[NEEDS CLARIFICATION]` con archivo, cambio mínimo y alternativa.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo porque usa archivos distintos y no depende de otra tarea incompleta.
- **[Story]**: Historia de usuario cubierta (`US1`–`US4`).
- Cada tarea identifica el archivo exacto que crea, modifica o valida.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Definir el perfil compartido sin alterar la configuración base de tests.

- [X] T001 Crear `backend/src/test/resources/application-e2e.properties` con `E2E_DB_URL=jdbc:postgresql://localhost:5432/desarrollo2_grupod`, `E2E_DB_USER=postgres`, `E2E_DB_PASSWORD=postgres`, driver obligatorio `org.postgresql.Driver`, `create-drop`, caché Redis, `REDIS_HOST=localhost`, `REDIS_PORT=6379` y destinos Football-Data/WhoScored de loopback inválido, sobrescribiendo explícitamente H2 y caché simple sin editar `backend/src/test/resources/application.properties`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Proporcionar la condición y meta-anotación que todos los e2e necesitan.

**⚠️ CRITICAL**: Ninguna historia puede ejecutarse hasta completar esta fase.

- [X] T002 [P] Implementar `backend/src/test/java/com/example/demo/support/ExternalServicesAvailableCondition.java` como `ExecutionCondition`: resolver `E2E_DB_URL`, `REDIS_HOST`, `REDIS_PORT` y `CI`; extraer PostgreSQL host/puerto; validar rango 1..65535; sondear ambos sockets con 500 ms; acumular motivos sanitizados; deshabilitar sólo localmente y habilitar siempre cuando `CI=true` sin distinguir mayúsculas
- [X] T003 Implementar `backend/src/test/java/com/example/demo/support/E2ETest.java` con target TYPE, retención RUNTIME, `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@ActiveProfiles("e2e")`, `@Tag("e2e")` y `@ExtendWith(ExternalServicesAvailableCondition.class)`

**Checkpoint**: El perfil se puede declarar con una anotación y una máquina local sin sockets disponibles no intenta crear el contexto.

---

## Phase 3: User Story 1 — Validar el recorrido crítico sobre el sistema completo (Priority: P1) 🎯 MVP

**Goal**: Probar por HTTP real que registro, login JWT, catálogo protegido, correlation ID y health funcionan juntos sobre PostgreSQL/Redis reales.

**Independent Test**: Con ambos servicios disponibles, ejecutar sólo `SmokeE2ETest`; debe completar registro 201, login 200 con token Bearer, `/players` 200 con `X-Correlation-ID` no vacío y `/actuator/health` 200 con `status=UP`.

### Tests and implementation for User Story 1

- [X] T004 [P] [US1] Crear `backend/src/test/java/com/example/demo/e2e/E2EProfileTest.java` usando `@E2ETest` para verificar perfil activo exactamente `e2e`, URL JDBC PostgreSQL, driver exactamente `org.postgresql.Driver` y nunca `org.h2.Driver`, `ddl-auto=create-drop`, caché `redis`, Redis real y ambas URLs externas en loopback inválido
- [X] T005 [P] [US1] Crear `backend/src/test/java/com/example/demo/e2e/SmokeE2ETest.java` con `RestClient` construido desde `@LocalServerPort` y el flujo secuencial de `contracts/smoke-http-contract.md`: `POST /auth/register` 201 con `apiKey`, `POST /auth/login` 200 con `token` no vacío y `tokenType=Bearer`, `GET /players` autenticado 200 con array —vacío permitido— y `X-Correlation-ID` no vacío, y `GET /actuator/health` 200 con `status=UP`, sin registrar secretos

**Checkpoint**: US1 es demostrable con servicios reales y sin modificar endpoints, seguridad o filtros productivos.

---

## Phase 4: User Story 2 — Ejecutar e2e de forma predecible localmente y en CI (Priority: P1)

**Goal**: Omitir con diagnóstico claro sólo en entornos locales sin sockets y fallar obligatoriamente en CI.

**Independent Test**: Ejecutar la matriz PostgreSQL ausente, Redis ausente, ambos ausentes y ambos disponibles; localmente los tres primeros casos se omiten nombrando todas las dependencias faltantes y con `CI=true` nunca se omiten.

### Tests for User Story 2

- [X] T006 [US2] Crear `backend/src/test/java/com/example/demo/support/ExternalServicesAvailableConditionTest.java` con dobles de entorno/sondeo para cubrir defaults; overrides; JDBC URL o puertos inválidos; PostgreSQL ausente; Redis ausente; ambos ausentes en un único motivo; sockets disponibles; `CI=true` case-insensitive; y ausencia de contraseña/URL completa en diagnósticos

### Implementation for User Story 2

- [X] T007 [US2] Ajustar `backend/src/test/java/com/example/demo/support/ExternalServicesAvailableCondition.java` hasta pasar T006, separando resolución/parsing y sondeo en unidades package-private o colaboradores inyectables, sin mutar variables de entorno del proceso y sin capturar `Throwable`; documentar en el código que TCP disponible no valida credenciales ni protocolo

**Checkpoint**: US2 se verifica sin levantar Spring y sin depender de servicios externos reales en sus tests unitarios.

---

## Phase 5: User Story 3 — Seleccionar grupos de prueba sin ambigüedad (Priority: P2)

**Goal**: Garantizar una única anotación e instrucciones reproducibles para ejecutar no-e2e, e2e o todo en Windows y Linux.

**Independent Test**: Verificar por reflexión los cuatro componentes de `@E2ETest` y ejecutar los tres comandos documentados, confirmando que los filtros incluyen/excluyen `SmokeE2ETest` según el tag.

### Tests for User Story 3

- [X] T008 [P] [US3] Crear `backend/src/test/java/com/example/demo/support/E2ETestTest.java` para verificar target TYPE, retención RUNTIME, `RANDOM_PORT`, perfil exclusivo `e2e`, tag exacto `e2e` y registro de `ExternalServicesAvailableCondition`

### Documentation and validation for User Story 3

- [X] T009 [P] [US3] Ampliar `README.md` con prerequisitos y advertencia destructiva, variables/defaults, diferencia local frente a `CI=true`, y comandos desde `backend/` para Windows PowerShell (`.\mvnw.cmd test '-DexcludedGroups=e2e'`, `.\mvnw.cmd test '-Dgroups=e2e'`, `.\mvnw.cmd verify`) y Linux (`./mvnw test -DexcludedGroups=e2e`, `./mvnw test -Dgroups=e2e`, `./mvnw verify`)
- [X] T010 [US3] Ejecutar los tres comandos de selección documentados y corregir únicamente `backend/src/test/java/com/example/demo/support/E2ETest.java`, `backend/src/test/java/com/example/demo/e2e/SmokeE2ETest.java` o `README.md` si la inclusión por tag difiere del contrato, sin configurar Surefire/Failsafe en `backend/pom.xml`

**Checkpoint**: US3 permite elegir el costo de ejecución en ambas plataformas sin cambios de build.

---

## Phase 6: User Story 4 — Aislar cada prueba e2e (Priority: P2)

**Goal**: Comenzar cada método sin filas ni claves residuales, con identidades reiniciadas y sin depender del orden.

**Independent Test**: Ejecutar dos métodos que creen el mismo usuario y claves Redis; ambos deben comenzar con cero residuos, obtener la misma identidad inicial esperada y pasar en cualquier orden.

### Tests for User Story 4

- [X] T011 [US4] Crear `backend/src/test/java/com/example/demo/e2e/E2EDatabaseCleanerE2ETest.java` con `@E2ETest` para sembrar filas relacionadas y claves Redis, demostrar `postgresRows=0`, identidades reiniciadas y `redisKeys=0` antes del método siguiente, y repetir los mismos identificadores sin colisión

### Implementation for User Story 4

- [X] T012 [US4] Crear `backend/src/test/java/com/example/demo/support/E2EDatabaseCleaner.java` como extensión JUnit `BeforeEachCallback` no transaccional; obtener `JdbcTemplate` y `RedisConnectionFactory` desde el contexto de `SpringExtension`, descubrir todas las tablas base del esquema actual, citar identificadores, ejecutar un único `TRUNCATE TABLE ... RESTART IDENTITY CASCADE` si existen tablas y luego `FLUSHDB SYNC` mediante una conexión Redis cerrada correctamente, fallando ante limpieza parcial
- [X] T013 [US4] Actualizar `backend/src/test/java/com/example/demo/support/E2ETest.java` para registrar `E2EDatabaseCleaner` como extensión JUnit junto con `ExternalServicesAvailableCondition`, garantizando limpieza automática antes de cada método de toda clase `@E2ETest`, incluidas `backend/src/test/java/com/example/demo/e2e/E2EProfileTest.java`, `backend/src/test/java/com/example/demo/e2e/SmokeE2ETest.java` y `backend/src/test/java/com/example/demo/e2e/E2EDatabaseCleanerE2ETest.java`, sin clase base ni `@Transactional`
- [X] T014 [US4] Ejecutar `backend/src/test/java/com/example/demo/e2e/E2EDatabaseCleanerE2ETest.java` repetidamente con `-Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random`, ajustando sólo `backend/src/test/java/com/example/demo/support/E2EDatabaseCleaner.java` hasta que no haya residuos ni colisiones y dejando explícita la prohibición de paralelismo sobre servicios compartidos

**Checkpoint**: US4 garantiza aislamiento por método tanto en PostgreSQL como en la base lógica Redis seleccionada.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Validar regresiones, cobertura conjunta, seguridad de documentación y límites de alcance.

- [X] T015 [P] Ejecutar la suite no-e2e con `backend/mvnw.cmd test '-DexcludedGroups=e2e'` en Windows o `backend/mvnw test -DexcludedGroups=e2e` en Linux y corregir sólo los nuevos archivos bajo `backend/src/test/` si el perfil `test`, `UnitTestProfile` o tests existentes sufren regresiones
- [X] T016 Ejecutar sólo e2e con PostgreSQL/Redis dedicados mediante `backend/mvnw.cmd test '-Dgroups=e2e'` o `backend/mvnw test -Dgroups=e2e`, validar el contrato completo de `specs/010-perfil-e2e/quickstart.md` y confirmar que Football-Data/WhoScored permanecen inaccesibles
- [X] T017 Ejecutar `backend/mvnw.cmd -B verify` o `backend/mvnw -B verify` con ambos servicios disponibles y revisar `backend/target/site/jacoco/jacoco.xml` para confirmar ejecución conjunta y reporte único de unitarios más e2e sin separar fases
- [X] T018 Auditar el diff final contra `backend/pom.xml`, `.github/workflows/ci.yml`, `backend/src/main/resources/application.properties`, `backend/src/test/resources/application.properties`, `backend/src/main/java/com/example/demo/config/SecurityConfig.java`, `backend/src/main/java/com/example/demo/filter/` y `backend/src/main/java/com/example/demo/controller/`; confirmar cero cambios protegidos, cero dependencias nuevas y ausencia de tests de resiliencia de Feature 012

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 — Setup**: inicia inmediatamente.
- **Phase 2 — Foundational**: T002 puede comenzar inmediatamente y en paralelo con T001; T003 depende de T002. T001–T003 completos bloquean la ejecución de las historias.
- **US1 (Phase 3)**: depende de T001–T003; entrega el MVP.
- **US2 (Phase 4)**: depende de T002; puede desarrollarse en paralelo con US1 una vez establecida la interfaz de la condición.
- **US3 (Phase 5)**: depende de T003; puede desarrollarse en paralelo con US1/US2, salvo T010, que necesita un smoke descubrible.
- **US4 (Phase 6)**: T011–T012 pueden comenzar después de T001–T003; T013 requiere T005 y T012; T014 requiere T011–T013.
- **Polish (Phase 7)**: depende de todas las historias incluidas en la entrega.

### User Story Dependency Graph

```text
Setup T001 ───────────────────────┐
Foundation T002 ──> T003 ────────┤
                                  ├── US1 T004–T005 (MVP)
                                  ├── US2 T006–T007
                                  ├── US3 T008–T009 ── T010 depends on US1 smoke
                                  └── US4 T011–T012 ── T013–T014 depend on US1 smoke
                                                         └── Polish T015–T018
```

### Within Each User Story

- Escribir las verificaciones antes de ajustar el comportamiento que validan.
- Mantener parsing/sondeo unitario separado de la integración Spring.
- Completar el contrato HTTP antes de registrar la extensión de limpieza en la meta-anotación.
- Validar cada checkpoint antes de avanzar al siguiente grupo de prioridad.

## Parallel Opportunities

- T002 puede avanzar en paralelo con la preparación de T001, pero T003 espera que exista el tipo de condición.
- Tras Foundation, T004 y T005 son paralelizables porque crean archivos distintos de US1.
- T006/T007 (US2), T008/T009 (US3) y T011/T012 (US4) pueden repartirse entre integrantes; T010, T013 y T014 esperan las integraciones indicadas.
- T015 puede ejecutarse en paralelo con revisión documental final, pero T016–T018 deben ser secuenciales para conservar un diagnóstico claro y un único resultado de cobertura.

## Parallel Examples by User Story

### User Story 1

```text
Task T004: Crear E2EProfileTest.java para validar el perfil efectivo.
Task T005: Crear SmokeE2ETest.java para validar el contrato HTTP.
```

### User Story 2

```text
Task T006: Preparar todos los casos unitarios de la condición.
En paralelo, otro integrante puede ejecutar T004/T005 de US1; T007 comienza cuando T006 fija el contrato esperado.
```

### User Story 3

```text
Task T008: Crear el test por reflexión de E2ETest.java.
Task T009: Documentar comandos y variables en README.md.
```

### User Story 4

```text
Task T011: Crear E2EDatabaseCleanerE2ETest.java con el escenario de residuos.
Task T012: Implementar E2EDatabaseCleaner.java contra el contrato de limpieza.
```

## Implementation Strategy

### MVP First — User Story 1

1. Completar T001–T003.
2. Completar T004–T005.
3. Ejecutar sólo `SmokeE2ETest` con PostgreSQL y Redis dedicados.
4. Detenerse y demostrar el flujo registro → login → players → health.

El MVP prueba el sistema completo; la entrega de la feature requiere además US2–US4 para omisión segura, selección e aislamiento.

### Incremental Delivery

1. **Foundation**: perfil, condición y anotación.
2. **US1**: smoke HTTP real demostrable.
3. **US2**: matriz local/CI verificable sin Spring.
4. **US3**: selección y documentación multiplataforma.
5. **US4**: limpieza reproducible por método.
6. **Polish**: regresión, verify conjunto, JaCoCo y auditoría de protegidos.

### Optional work explicitly excluded

- No agregar Failsafe ni modificar `backend/pom.xml`.
- No dividir pasos ni modificar `.github/workflows/ci.yml`.
- No implementar fallback Testcontainers automático.
- No agregar pruebas de resiliencia de Football-Data/WhoScored.

## Notes

- `[P]` significa archivos distintos y ausencia de dependencia inmediata, no autorización para ejecutar e2e concurrentemente contra la misma infraestructura.
- `FLUSHDB` y `TRUNCATE ... CASCADE` son destructivos; usar únicamente servicios dedicados a e2e.
- El sondeo TCP no valida credenciales: un socket abierto con credenciales inválidas debe producir fallo de contexto, no skip.
- Los tests e2e no llevan `@Transactional` porque los requests HTTP reales se procesan en otros hilos.
- Si aparece la necesidad de tocar un archivo protegido, aplicar FR-023 y detener esa tarea hasta recibir aprobación.
