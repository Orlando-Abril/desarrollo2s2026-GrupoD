# Research: Perfil e2e con infraestructura real

**Feature**: `010-perfil-e2e`  
**Date**: 2026-10-07

## 1. Servicios externos configurables como camino principal

**Decision**: Conectar el perfil e2e a PostgreSQL y Redis reales mediante `E2E_DB_URL`, `E2E_DB_USER`, `E2E_DB_PASSWORD`, `REDIS_HOST` y `REDIS_PORT`, con los valores predeterminados del CI.

**Rationale**: El workflow ya aprovisiona PostgreSQL 16 y Redis 7; usar las mismas interfaces valida wiring, dialecto, driver, cache manager y health reales. No requiere dependencias ni cambios en CI. Una URL JDBC única permite reemplazar host, puerto y base de forma atómica, conforme a la instrucción de planificación más reciente.

**Alternatives considered**:

- Variables separadas para host/puerto/base PostgreSQL: más granulares, pero contradicen el contrato explícito `E2E_DB_URL` dado para este plan.
- H2 y caché simple: no prueban las integraciones que esta feature busca cubrir.
- Valores de producción: descartados por seguridad y falta de aislamiento.

## 2. Testcontainers `GenericContainer` como plan B

**Decision**: Documentar, pero no implementar, un fallback local con `GenericContainer<>(DockerImageName.parse("postgres:16"))`.

**Rationale**: El proyecto ya incluye Testcontainers Jupiter/core 1.21.4 y el driver PostgreSQL, por lo que un contenedor genérico puede configurar `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` y exponer 5432 sin agregar el módulo PostgreSQL. La forma `DockerImageName.parse` evita el constructor `String` deprecado.

**Alternatives considered**:

- `PostgreSQLContainer`: API más expresiva, pero requiere una dependencia nueva prohibida.
- Activar automáticamente el contenedor cuando no haya servicio: oculta diferencias con CI, requiere Docker y vuelve impredecible la semántica de omisión.
- Contenerizar sólo PostgreSQL: deja Redis externo y ofrece una experiencia parcial; un fallback completo necesitaría una decisión equivalente para Redis.

Fuente: [Testcontainers — Creating a container](https://java.testcontainers.org/features/creating_container/).

## 3. RestClient para HTTP real

**Decision**: Usar `org.springframework.web.client.RestClient`, creado en el test con base URL `http://localhost:{puerto}` obtenida mediante `@LocalServerPort`.

**Rationale**: `RestClient` ya está disponible por Spring Web MVC y se usa en el repositorio; sus message converters serializan los requests y deserializan `LoginResponse` sin un bean adicional. `TestRestTemplate`/`RestTestClient` implicarían la dependencia `spring-boot-resttestclient`, prohibida para esta feature.

**Alternatives considered**:

- `MockMvc`: no atraviesa un servidor HTTP real ni valida el puerto aleatorio.
- Cliente JDK manual: evita Spring, pero agrega serialización y manejo de errores innecesarios.
- `TestRestTemplate`/`RestTestClient`: ergonomía de test superior, descartada por dependencia nueva.

## 4. Override explícito del datasource H2

**Decision**: Definir en el nuevo perfil tanto la URL PostgreSQL como `spring.datasource.driver-class-name=org.postgresql.Driver`, usuario y contraseña.

**Rationale**: `src/test/resources/application.properties` fija explícitamente `org.h2.Driver`. Cambiar únicamente la URL puede dejar una combinación incoherente; el archivo específico de perfil tiene precedencia y debe reemplazar todos los campos incompatibles.

**Alternatives considered**:

- Eliminar el driver del archivo base: modificaría un archivo protegido y alteraría tests existentes.
- Confiar en autodetección: no desplaza de forma segura una propiedad explícita heredada.

## 5. Condición de disponibilidad antes del contexto

**Decision**: Implementar un `ExecutionCondition` JUnit puro que resuelva endpoints desde el entorno y abra sockets TCP con timeout de 500 ms. Acumula los servicios ausentes y deshabilita localmente con un mensaje sanitizado. Si `CI=true` ignorando mayúsculas, devuelve habilitado sin sondear.

**Rationale**: `ExecutionCondition` se evalúa antes del contexto Spring, evitando fallos y esperas costosas en estaciones sin infraestructura. Habilitar siempre en CI asegura que ninguna caída se transforme en skip. La resolución y el parsing se separarán del entorno global para poder probar defaults, valores inválidos y mensajes.

**Alternatives considered**:

- Conexión JDBC y Redis PING: valida credenciales/protocolo, pero duplica clientes, maneja secretos y excede la instrucción de sondeo por socket.
- Capturar fallos del contexto y convertirlos en skip: podría esconder defectos reales y ocurre demasiado tarde.
- Cache global del resultado: reduce sondeos, pero puede ocultar una caída entre clases; se admite cache por clase como máximo.

**Known limitation**: Un puerto abierto no demuestra credenciales válidas, existencia de la base ni servicio correcto. Esos errores fallan al crear/usar el contexto incluso localmente. No se imprimirán contraseñas ni la URL JDBC completa.

Fuente: [JUnit 5 User Guide — Conditional Test Execution](https://junit.org/junit5/docs/current/user-guide/).

## 6. Limpieza PostgreSQL antes de cada método

**Decision**: Implementar `E2EDatabaseCleaner` como una extensión JUnit `BeforeEachCallback`, registrada automáticamente por `@E2ETest`.

**Rationale**: La extensión obtiene `JdbcTemplate` y `RedisConnectionFactory` mediante `SpringExtension.getApplicationContext`, garantiza limpieza antes de cada método de toda clase `@E2ETest`, evita depender de herencia o configuración manual y mantiene la limpieza confirmada antes de requests procesados en otros hilos. La implementación consulta dinámicamente todas las tablas base del esquema actual, cita sus identificadores y ejecuta un solo `TRUNCATE TABLE ... RESTART IDENTITY CASCADE` fuera de una transacción de test.

**Alternatives considered**:

- Lista fija de cinco tablas actuales: simple, pero se rompe al crecer el modelo.
- `@Transactional` con rollback: no engloba transacciones iniciadas por requests HTTP reales.
- Recrear el contexto por método: más lento y no garantiza por sí solo limpieza de Redis.
- Clase base abstracta con `@BeforeEach`: descartada porque cada autor podría olvidar extenderla y Java sólo permite una superclase; no garantiza FR-010 para toda clase `@E2ETest`.

**Risks**: `TRUNCATE` toma locks exclusivos y `CASCADE` puede alcanzar tablas referenciantes. Sólo debe apuntar a un esquema dedicado y no ejecutarse en paralelo.

Fuente: [PostgreSQL — TRUNCATE](https://www.postgresql.org/docs/current/sql-truncate.html).

## 7. Limpieza Redis síncrona

**Decision**: Obtener una conexión desde `RedisConnectionFactory`, ejecutar `serverCommands().flushDb(FlushOption.SYNC)` y cerrarla; usar `FLUSHDB`, nunca `FLUSHALL`.

**Rationale**: El borrado síncrono garantiza estado vacío antes del request siguiente y limita el alcance a la base lógica seleccionada. Cerrar la conexión evita fugas en suites largas.

**Alternatives considered**:

- Borrar sólo claves conocidas: puede dejar entradas creadas por nuevos componentes.
- `FLUSHALL`: afecta otras bases lógicas y amplía innecesariamente el riesgo.
- Flush asíncrono: permite carreras con el test siguiente.

Fuentes: [Spring Data Redis — RedisServerCommands](https://docs.spring.io/spring-data-redis/reference/api/java/org/springframework/data/redis/connection/RedisServerCommands.html), [Redis — FLUSHDB](https://redis.io/docs/latest/commands/flushdb/).

## 8. Selección de grupos con Surefire

**Decision**: Mantener Surefire sin cambios y usar `-DexcludedGroups=e2e`, `-Dgroups=e2e` o ningún filtro.

**Rationale**: Surefire mapea `groups`/`excludedGroups` a tags JUnit Platform. `SmokeE2ETest` coincide con el patrón `*Test`, y Maven `verify` atraviesa la fase `test`; por eso la separación funciona hoy sin tocar `pom.xml`.

**Alternatives considered**:

- Perfiles Maven: requieren configuración protegida y duplican la selección disponible por tags.
- Convención de nombres solamente: menos explícita que `@Tag` y no satisface la meta-anotación requerida.

Fuentes: [Maven Surefire — JUnit Platform](https://maven.apache.org/surefire/maven-surefire-plugin/examples/junit-platform.html), [Maven build lifecycle](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html).

## 9. JaCoCo con ambos grupos

**Decision**: Usar un único `./mvnw -B verify` como ejecución canónica para cobertura conjunta.

**Rationale**: La configuración actual prepara el agente y genera el reporte en la fase `test`. Al ejecutar todos los tests en un solo ciclo, `target/site/jacoco/jacoco.xml` representa unitarios y e2e para SonarCloud. Los comandos filtrados generan cobertura del grupo seleccionado y no deben presentarse como reporte combinado.

**Alternatives considered**:

- Dos invocaciones `test` consecutivas: su acumulación depende de `jacoco.exec`, `append`, ausencia de `clean` y workspace compartido; es más frágil y no será la ruta de CI.
- Reportes separados y merge: válido, pero requiere cambiar `pom.xml` y coordinar Sonar.

Fuentes: [JaCoCo agent](https://www.jacoco.org/jacoco/trunk/doc/agent.html), [JaCoCo prepare-agent](https://www.jacoco.org/jacoco/trunk/doc/prepare-agent-mojo.html).

## 10. Failsafe y CI dividido, pendientes de aprobación

**Decision**: No implementar. Registrar como mejora opcional que requiere aprobación para modificar `backend/pom.xml` y `.github/workflows/ci.yml`.

**Rationale**: Failsafe ofrece fases y reportes separados, pero sus patrones predeterminados no incluyen `SmokeE2ETest`; habría que renombrar a `*IT` o configurar includes, excluir/incluir tags entre plugins y adaptar JaCoCo con agente/reporte de integración y posiblemente merge. Dos pasos de CI mejoran visibilidad, pero el `verify` actual ya satisface ejecución y cobertura conjunta.

**Alternatives considered**:

- Cambio mínimo protegido: configurar Failsafe para `integration-test`/`verify`, seleccionar `e2e`, adaptar JaCoCo y dividir CI.
- Alternativa aprobada sin archivos protegidos: Surefire con tags y un solo `verify`.

Fuentes: [Maven Failsafe usage](https://maven.apache.org/surefire/maven-failsafe-plugin/usage.html), [JaCoCo integration agent](https://www.jacoco.org/jacoco/trunk/doc/prepare-agent-integration-mojo.html), [JaCoCo merge](https://www.jacoco.org/jacoco/trunk/doc/merge-mojo.html).
