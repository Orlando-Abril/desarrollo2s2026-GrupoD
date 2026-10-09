# Implementation Plan: Perfil e2e con infraestructura real

**Branch**: `feature/test/perfil-e2e` | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/010-perfil-e2e/spec.md`

## Summary

Agregar un perfil de test `e2e` que levante la aplicación completa en un puerto aleatorio contra PostgreSQL y Redis reales, seleccionable mediante el tag JUnit `e2e`. La solución incorpora exclusivamente archivos nuevos de configuración y soporte de test, más documentación: un perfil que reemplaza explícitamente el datasource H2 heredado, una meta-anotación e2e, una condición previa que omite localmente cuando los sockets no están disponibles pero nunca omite con `CI=true`, un limpiador de PostgreSQL/Redis antes de cada método y un smoke test HTTP con `RestClient`. Surefire seguirá ejecutando los grupos sin cambios en `pom.xml`; `./mvnw verify` seguirá siendo la ejecución canónica de CI y de cobertura combinada.

## Technical Context

**Language/Version**: Java 17

**Primary Dependencies**: Spring Boot 4.1.1, Spring Web MVC (`RestClient`), Spring Data JPA/JDBC, Spring Data Redis, Spring Security, Spring Boot Actuator, JUnit Jupiter 5, Maven Surefire y JaCoCo 0.8.12; todas ya presentes de forma directa o transitiva

**Storage**: PostgreSQL 16 real con esquema `create-drop`; Redis 7 real, base lógica configurada por la aplicación y vaciada antes de cada test

**Testing**: JUnit Jupiter, `@SpringBootTest(webEnvironment = RANDOM_PORT)`, Spring Test, AssertJ, `JdbcTemplate`, `RedisConnectionFactory`, `RestClient`, tags de Surefire y Maven Wrapper

**Target Platform**: GitHub Actions sobre Linux; ejecución local compatible con Windows PowerShell y Linux/macOS shell

**Project Type**: Aplicación web con backend Spring Boot y frontend separado; esta feature modifica sólo recursos, soporte y documentación de tests del backend

**Performance Goals**: Sondeo local de cada dependencia con timeout TCP de 500 ms; smoke test único y secuencial; sin objetivo de carga o latencia productiva

**Constraints**: Cero dependencias nuevas; no modificar `backend/pom.xml`, `.github/workflows/ci.yml`, ningún `application.properties` existente, `SecurityConfig`, filtros ni controllers; no usar puertos HTTP fijos; no contactar fuentes externas; no ejecutar e2e en paralelo sobre la misma infraestructura; no ocultar fallos de infraestructura en CI

**Scale/Scope**: Un perfil, una meta-anotación, una condición de disponibilidad, un limpiador, un test de humo con cuatro requests, dos contratos de diseño y documentación para tres selecciones de pruebas en dos plataformas

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

### Pre-design gate

| Principle | Evaluation | Result |
|-----------|------------|--------|
| §3.1 Integración Continua | El test e2e queda incluido en el `verify` existente y usa los servicios que el workflow ya ofrece. La condición prohíbe omitir por disponibilidad cuando `CI=true`. | Se verifica en el PR |
| §3.2 Control de Calidad en SonarCloud | Un solo `verify` conserva la ejecución conjunta y el reporte JaCoCo que consume SonarCloud; no se separan fases ni reportes. | Se verifica en el PR |
| §3.3 Cobertura de escenarios de dominio | N/A para el alcance funcional de esta feature: deja disponible la infraestructura e2e; los escenarios 2 y 3 se cubren en las features 016 y 021. | N/A |
| §4.1 Autenticación y autorización | El smoke valida registro y autenticación JWT real antes de acceder al catálogo protegido. | PASS |
| §5.2 Caché Redis obligatoria | El perfil fuerza caché Redis real y verifica su disponibilidad en salud. | PASS |
| §5.3 Observabilidad | El smoke exige `X-Correlation-ID` y salud general `UP`. | PASS |
| §7 Definition of Done | Se preservan build, documentación, autenticación y observabilidad sin alterar capas productivas. La cobertura de resiliencia corresponde a la feature 012. | Resiliencia cubierta por Feature 012 |

No hay cambios constitucionales dentro del alcance de esta feature. §3.1 y §3.2 se verifican en el PR; los escenarios 2 y 3 de §3.3 corresponden a las features 016 y 021; y los tests de resiliencia de §1.3/§7.6 están cubiertos por Feature 012.

### Post-design gate

El diseño de Phase 1 mantiene los gates dentro del alcance acordado: los contratos validan JWT, correlation ID y health; el quickstart conserva un `verify` único para CI/Sonar; y el modelo de aislamiento requiere PostgreSQL/Redis dedicados y ejecución secuencial. §3.1 y §3.2 se verifican en el PR; §3.3 queda cubierto por las features 016 y 021; y resiliencia por Feature 012. Failsafe, cambios de CI y aprovisionamiento automático no forman parte del diseño aprobado.

## Project Structure

### Documentation (this feature)

```text
specs/010-perfil-e2e/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── e2e-profile-contract.md
│   └── smoke-http-contract.md
└── tasks.md                 # se generará con $speckit-tasks
```

### Source Code (repository root)

```text
backend/
├── src/
│   └── test/
│       ├── java/com/example/demo/
│       │   ├── e2e/
│       │   │   └── SmokeE2ETest.java                 # nuevo
│       │   └── support/
│       │       ├── E2ETest.java                      # nuevo
│       │       ├── ExternalServicesAvailableCondition.java # nuevo
│       │       └── E2EDatabaseCleaner.java           # nuevo
│       └── resources/
│           └── application-e2e.properties            # nuevo
└── pom.xml                                            # protegido, sin cambios

.github/workflows/ci.yml                               # protegido, sin cambios
README.md                                              # ampliar guía de pruebas
```

**Structure Decision**: Mantener el backend existente y agregar sólo soporte en `src/test`. `E2EDatabaseCleaner` implementará `BeforeEachCallback` y `@E2ETest` lo registrará como extensión JUnit junto con `ExternalServicesAvailableCondition`, de modo que la limpieza se aplique automáticamente a toda clase marcada sin clase base ni incorporación manual adicional.

## Design Decisions

### Configuración del perfil

`backend/src/test/resources/application-e2e.properties` sobreescribirá las propiedades heredadas del `application.properties` de tests:

- `spring.datasource.url=${E2E_DB_URL:jdbc:postgresql://localhost:5432/desarrollo2_grupod}`
- `spring.datasource.driver-class-name=org.postgresql.Driver`
- `spring.datasource.username=${E2E_DB_USER:postgres}`
- `spring.datasource.password=${E2E_DB_PASSWORD:postgres}`
- `spring.jpa.hibernate.ddl-auto=create-drop`
- `spring.cache.type=redis`
- `spring.data.redis.host=${REDIS_HOST:localhost}`
- `spring.data.redis.port=${REDIS_PORT:6379}`

El driver PostgreSQL debe declararse explícitamente: cambiar sólo la URL no desplaza el `org.h2.Driver` fijado por la configuración base. El perfil repetirá también los destinos inválidos de Football-Data y WhoScored para que el aislamiento no dependa accidentalmente de herencia futura. `spring.data.redis.repositories.enabled=false` puede seguir heredado: desactiva repositorios Redis, no el `RedisCacheManager` ni el health contributor.

### Ciclo de ejecución e2e

1. Surefire descubre `SmokeE2ETest` por su nombre normal `*Test`.
2. `ExternalServicesAvailableCondition` se evalúa antes de crear el contexto Spring.
3. Localmente, resuelve `E2E_DB_URL`, `REDIS_HOST` y `REDIS_PORT`, sondea ambos sockets con 500 ms y acumula todos los servicios ausentes en el motivo de omisión.
4. Con `CI=true` sin distinción de mayúsculas, la condición habilita el test sin sondear; cualquier indisponibilidad debe fallar durante el arranque o uso real.
5. Spring levanta la aplicación con perfil `e2e` en puerto aleatorio y crea el esquema.
6. El `BeforeEachCallback` registrado por `@E2ETest` obtiene el contexto Spring y limpia todas las tablas del esquema y la base lógica Redis con operaciones síncronas antes de cada método.
7. El smoke construye un `RestClient` apuntando al valor de `@LocalServerPort` y ejecuta los cuatro requests del contrato.
8. Al cerrar el contexto, `create-drop` elimina el esquema.

### Disponibilidad y límite del sondeo

La condición sólo garantiza conectividad TCP. No valida credenciales, existencia de la base ni identidad del protocolo; esos errores fallarán al iniciar el contexto incluso localmente. Esta decisión sigue la instrucción explícita de usar sockets, evita duplicar clientes y no manipula secretos. Se documenta la diferencia respecto de una prueba profunda con JDBC/PING en [research.md](research.md).

### Limpieza y seguridad de datos

`E2EDatabaseCleaner` será una extensión JUnit `BeforeEachCallback` registrada desde `@E2ETest`. Obtendrá `JdbcTemplate` y `RedisConnectionFactory` desde el contexto administrado por `SpringExtension`, consultará dinámicamente las tablas base del esquema actual, citará identificadores y emitirá un único `TRUNCATE ... RESTART IDENTITY CASCADE`; si no hay tablas, no ejecutará SQL. No será transaccional, porque los requests HTTP operan en otros hilos y necesitan observar la limpieza confirmada. Para Redis obtendrá y cerrará una conexión, y ejecutará `FLUSHDB SYNC`, nunca `FLUSHALL`.

Estas operaciones son destructivas. La guía exigirá una base/esquema PostgreSQL y una base lógica Redis dedicados a e2e. La suite no debe correr en paralelo contra recursos compartidos.

## Implementation Sequence

1. Crear `application-e2e.properties` con los overrides exactos y una prueba de configuración que demuestre datasource PostgreSQL, driver no-H2, caché Redis y destinos externos inválidos.
2. Crear `ExternalServicesAvailableCondition` con resolución testeable de entorno, parsing seguro del JDBC URL, validación de puertos, timeout corto, motivos agregados y excepción `CI=true`.
3. Crear `E2EDatabaseCleaner` como `BeforeEachCallback` que obtiene `JdbcTemplate` y `RedisConnectionFactory` desde `SpringExtension` y limpia antes de cada método.
4. Crear la meta-anotación `E2ETest` con `RANDOM_PORT`, perfil `e2e`, tag `e2e`, la condición y el registro de la extensión de limpieza.
5. Crear `SmokeE2ETest`, construir `RestClient` después de la limpieza y validar exactamente el contrato HTTP.
6. Ampliar `README.md` con variables, advertencia destructiva y comandos Windows/Linux para no-e2e, e2e y todo.
7. Validar selección por tags, omisión local, obligatoriedad de CI, estado limpio repetible y `./mvnw -B verify` con servicios reales.

## Validation Strategy

- Tests unitarios de `ExternalServicesAvailableCondition` sin mutar el entorno global: separar resolución/parsing y sondeo detrás de métodos package-private o colaboradores inyectables.
- Verificación de la meta-anotación por reflexión para sus cuatro componentes.
- Verificación del perfil efectivo dentro del smoke o un test de soporte: URL/driver PostgreSQL, caché Redis y URLs externas de loopback inválidas.
- Dos ejecuciones consecutivas del smoke con el mismo usuario lógico para demostrar limpieza e identidades reiniciadas.
- Matriz manual/automatizada: ambos servicios disponibles; sólo PostgreSQL ausente; sólo Redis ausente; ambos ausentes; cada ausencia con `CI=true`.
- Ejecución de los tres comandos de grupo y confirmación de conteos/skip/fail esperados.
- `./mvnw -B verify` como validación final y fuente única del reporte JaCoCo combinado.

## Protected Optional Alternatives

Las siguientes mejoras quedan fuera de implementación y pendientes de aprobación explícita:

- **Failsafe en `backend/pom.xml`**: separar e2e en `integration-test`/`verify`, adaptar patrón de nombre o includes y coordinar JaCoCo unitario/integración. Cambio mínimo: configurar `maven-failsafe-plugin` y exclusión/inclusión por tag. Alternativa sin tocar el archivo: comandos Surefire `groups`/`excludedGroups`, elegida en este plan.
- **Dos pasos en `.github/workflows/ci.yml`**: mostrar resultados unitarios y e2e por separado. Cambio mínimo: reemplazar el único `verify` por pasos etiquetados y preservar/combinar cobertura. Alternativa sin tocar el archivo: conservar el `verify` único, que ya ejecuta todo.
- **Testcontainers como fallback local**: arrancar PostgreSQL 16 con `GenericContainer` y propiedades dinámicas. No requiere módulo PostgreSQL nuevo con las dependencias actuales, pero sí Docker, descarga de imagen y una decisión equivalente para Redis. Alternativa elegida: omisión local clara y servicios externos configurables.

## Complexity Tracking

No se registran violaciones constitucionales ni complejidad excepcional. Las alternativas que requerirían tocar archivos protegidos permanecen documentadas pero no autorizadas.
