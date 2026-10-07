# Implementation Plan: Perfil uniforme de pruebas del backend

**Branch**: `009-perfil-test` | **Date**: 2026-10-07 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-perfil-test/spec.md`

**Note**: This template is filled in by the `$speckit-plan` command; its definition describes the execution workflow.

## Summary

Formalizar el entorno vigente de tests unitarios e integración liviana mediante el perfil Spring `test`, sin modificar ningún archivo de Entrega 1. La implementación agregará un `application-test.properties` mínimo que hereda la configuración base de test, la meta-anotación `com.example.demo.support.UnitTestProfile` equivalente a `@ActiveProfiles("test")`, y un `UnitTestProfileTest` con contexto completo que prueba H2, `ConcurrentMapCacheManager` y los dos destinos externos en loopback. `quickstart.md` documentará el uso en tests nuevos y la ejecución con Maven Wrapper en Linux y Windows.

## Technical Context

**Language/Version**: Java 17

**Primary Dependencies**: Spring Boot 4.1.1; Spring Test; Spring Data JPA; Spring Cache; H2, JUnit Jupiter y AssertJ ya disponibles en alcance test

**Storage**: H2 en memoria (`jdbc:h2:mem:testdb`, modo PostgreSQL, esquema `create-drop`) heredado de `backend/src/test/resources/application.properties`; PostgreSQL productivo no se modifica

**Testing**: JUnit Jupiter con `@SpringBootTest`, Spring Test `@ActiveProfiles`, Maven Wrapper y una prueba de integración liviana nueva

**Target Platform**: JVM 17 en desarrollo local Windows/Linux y runner de CI existente

**Project Type**: Backend web Spring Boot dentro de un repositorio con frontend independiente; esta feature afecta sólo soporte y recursos de test del backend

**Performance Goals**: Agregar un único contexto de integración liviana; la prueba debe terminar dentro de la ejecución normal de `mvn test` y no realizar llamadas a servicios externos

**Constraints**: Sólo archivos nuevos; cero dependencias nuevas; no modificar `pom.xml`, `ci.yml`, `application.properties` de main o test ni tests existentes; no crear perfil e2e; no migrar tests de Entrega 1; URLs externas efectivas restringidas a `127.0.0.1`

**Scale/Scope**: Tres archivos nuevos bajo `backend/src/test` más los artefactos de diseño de Feature 009; una meta-anotación reutilizable por todos los tests nuevos de Entrega 2

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Evaluación previa a Phase 0

| Regla constitucional | Estado | Evidencia del plan |
|----------------------|--------|--------------------|
| §1.3 Resiliencia y desacoplamiento de fuentes externas | PASS | La prueba valida que Football-Data y WhoScored resuelven a loopback y no dependen de Internet. |
| §2.1 Backend en capas | PASS | No se cambia código productivo ni se introduce lógica de dominio; el soporte queda en el árbol de tests. |
| §3.1 Integración continua | PASS condicionado | `mvn test` debe aprobar localmente y el pipeline existente debe finalizar en `SUCCESS`; no se modifica `ci.yml`. |
| §3.2 SonarCloud | PASS condicionado | Los archivos nuevos deben introducir cero issues; el Quality Gate existente debe permanecer aprobado y por debajo de 10 issues menores. |
| §3.3 Cobertura de pruebas | PASS | La convención facilita los tests nuevos de Entrega 2 sin alterar la cobertura de dominio existente. |
| §5.2 Caché Redis obligatoria | PASS | Redis permanece obligatorio en runtime; `ConcurrentMapCacheManager` se verifica exclusivamente dentro del perfil aislado de test. |
| §7 Definition of Done | PASS con alcance | Se exige build/test en verde, CI `SUCCESS`, SonarCloud sin issues nuevos y aislamiento de integraciones. OpenAPI, auditoría y capas no aplican porque no se agrega endpoint ni operación de estado. |

No existen violaciones que requieran justificación y no hay `NEEDS CLARIFICATION` pendientes.

### Reevaluación posterior a Phase 1

PASS. `research.md`, `data-model.md` y `quickstart.md` conservan el alcance exclusivo de tests, no proponen cambios productivos, no agregan dependencias y definen las validaciones necesarias para §3.1 y §3.2. No se necesita contrato externo porque la feature introduce una convención interna de test y no modifica API, CLI ni formatos consumidos fuera del repositorio.

## Project Structure

### Documentation (this feature)

```text
specs/009-perfil-test/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── checklists/
│   └── requirements.md
└── spec.md
```

No se crea `contracts/`: la feature es soporte interno de pruebas y no expone interfaces a usuarios u otros sistemas.

### Source Code (repository root)

```text
backend/
└── src/
    └── test/
        ├── java/
        │   └── com/example/demo/support/
        │       ├── UnitTestProfile.java       # NUEVO: meta-anotación del perfil test
        │       └── UnitTestProfileTest.java   # NUEVO: prueba demostrativa del entorno
        └── resources/
            ├── application.properties         # EXISTENTE: sólo lectura, no modificar
            └── application-test.properties    # NUEVO: capa mínima específica del perfil
```

Archivos protegidos que permanecen sin cambios: `backend/pom.xml`, `.github/workflows/ci.yml`, los `application.properties` existentes y todos los tests existentes.

**Structure Decision**: La anotación y su prueba se ubican juntas en `com.example.demo.support`, bajo el source set de test, porque constituyen infraestructura reutilizable de pruebas y no API productiva. El archivo por perfil se agrega al mismo classpath de recursos de test para que Spring combine la base existente con la capa específica al activar `test`.

## Phase 0: Research

Las decisiones y alternativas están consolidadas en [research.md](./research.md). No quedan incógnitas técnicas ni marcadores `NEEDS CLARIFICATION`.

## Phase 1: Design

- [data-model.md](./data-model.md) define el modelo de configuración, la meta-anotación y las invariantes verificables; no agrega entidades persistentes.
- No se genera `contracts/` porque no hay interfaz externa.
- [quickstart.md](./quickstart.md) describe cómo anotar tests nuevos y ejecutar la validación completa en Linux y Windows.

## Implementation Sequence

1. Crear `backend/src/test/resources/application-test.properties` como capa mínima del perfil, con comentarios que expliquen la herencia y sin duplicar las propiedades ya presentes en la base.
2. Crear `backend/src/test/java/com/example/demo/support/UnitTestProfile.java` con `@Retention(RUNTIME)`, `@Target(TYPE)` y `@ActiveProfiles("test")`.
3. Crear `backend/src/test/java/com/example/demo/support/UnitTestProfileTest.java` con `@SpringBootTest` y `@UnitTestProfile`.
4. En la prueba, verificar que `test` está activo; que `DataSource` abre una conexión H2 en memoria; que el `CacheManager` es `ConcurrentMapCacheManager`; y que `football-data.base-url` y `whoscored.base-url` tienen host `127.0.0.1` y puerto `1`.
5. Ejecutar la prueba focalizada y después toda la suite mediante Maven Wrapper.
6. Confirmar que Git sólo muestra archivos nuevos autorizados, que CI finaliza en `SUCCESS` y que SonarCloud no reporta issues nuevos.

## Validation Gates

- **Perfil y precedencia**: el contexto reporta `test` activo y conserva los valores heredados del archivo base cuando `application-test.properties` no los redefine.
- **Persistencia**: el producto de base es H2 y la URL JDBC efectiva es en memoria; se conserva el modo PostgreSQL y `create-drop` heredados.
- **Caché**: el bean inyectado implementa `ConcurrentMapCacheManager`; ninguna conexión Redis es requisito de la prueba.
- **Integraciones**: ambas URLs efectivas usan `127.0.0.1:1`; se validan independientemente y no se invoca ningún cliente externo.
- **Regresión**: la suite completa pasa sin tocar tests existentes.
- **Constitution §3.1**: build y tests locales pasan; GitHub Actions debe quedar en `SUCCESS`.
- **Constitution §3.2**: cero issues nuevos en SonarCloud y Quality Gate aprobado.
- **Protección Entrega 1**: cualquier necesidad de cambiar un archivo protegido detiene la implementación y se registra como `[NEEDS CLARIFICATION]` con archivo, cambio mínimo y alternativa.

## Complexity Tracking

No hay violaciones constitucionales ni complejidad adicional que justificar.
