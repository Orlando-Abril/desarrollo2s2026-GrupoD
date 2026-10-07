---

description: "Tareas de implementación para el perfil uniforme de pruebas del backend"
---

# Tasks: Perfil uniforme de pruebas del backend

**Input**: Design documents from `/specs/009-perfil-test/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: La feature exige una prueba demostrativa con `@SpringBootTest` y `@UnitTestProfile`; las tareas de test forman parte obligatoria de US1 y US2.

**Organization**: Las tareas están agrupadas por historia de usuario. US1 y US2 tienen prioridad P1 y componen juntas el MVP funcional; US3 documenta la adopción.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo porque modifica un archivo diferente y no depende de una tarea incompleta.
- **[Story]**: Historia de usuario cubierta (`US1`, `US2`, `US3`).
- Todas las tareas indican rutas exactas del repositorio.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirmar el punto de partida y proteger los archivos de Entrega 1 antes de cualquier edición.

- [X] T001 Verificar con `git diff --exit-code` que `backend/pom.xml`, `.github/workflows/ci.yml`, `backend/src/main/resources/application.properties`, `backend/src/test/resources/application.properties` y los tests existentes bajo `backend/src/test/java/` no tengan cambios; conservar esta lista como guardrail y detenerse con `[NEEDS CLARIFICATION]` antes de modificar cualquiera de esas rutas

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Crear la capa de recursos común que formaliza el perfil sin duplicar la configuración protegida.

**⚠️ CRITICAL**: Esta fase debe completarse antes de validar cualquiera de las historias.

- [X] T002 Crear `backend/src/test/resources/application-test.properties` únicamente con comentarios que documenten que `backend/src/test/resources/application.properties` siempre se carga como base y que las claves del archivo específico sobrescriben la base al activar `test`; no duplicar propiedades ni agregar una propiedad marcadora

**Checkpoint**: Existe la capa específica del perfil y todos los valores H2, `create-drop`, caché simple y loopback siguen heredándose del archivo base inmutable.

---

## Phase 3: User Story 1 - Declarar un entorno de test uniforme (Priority: P1) 🎯 MVP Parte 1

**Goal**: Permitir que un test nuevo active el perfil `test` mediante una única meta-anotación reutilizable.

**Independent Test**: Ejecutar `UnitTestProfileTest` usando solamente `@SpringBootTest` y `@UnitTestProfile`, y comprobar que `Environment.getActiveProfiles()` contiene exactamente `test` sin declarar `@ActiveProfiles` en la clase de prueba.

### Tests for User Story 1

- [X] T003 [US1] Crear `backend/src/test/java/com/example/demo/support/UnitTestProfileTest.java` con `@SpringBootTest`, `@UnitTestProfile`, inyección de `Environment` y una prueba que exige que el único perfil activo sea `test`; confirmar que inicialmente no compila o falla antes de crear la meta-anotación

### Implementation for User Story 1

- [X] T004 [US1] Crear `backend/src/test/java/com/example/demo/support/UnitTestProfile.java` en el paquete `com.example.demo.support` con las restricciones exactas `@Retention(RetentionPolicy.RUNTIME)`, `@Target(ElementType.TYPE)` y `@ActiveProfiles("test")`, sin incorporar `@SpringBootTest` ni otras estrategias de contexto
- [X] T005 [US1] Ejecutar desde `backend/` la prueba focalizada `mvnw.cmd -Dtest=UnitTestProfileTest test` en Windows o `./mvnw -Dtest=UnitTestProfileTest test` en Linux y confirmar que la aserción del perfil pasa usando `backend/src/test/java/com/example/demo/support/UnitTestProfileTest.java`

**Checkpoint**: US1 funciona de forma independiente: una clase nueva activa explícitamente `test` con una sola anotación compartida.

---

## Phase 4: User Story 2 - Ejecutar tests con aislamiento reproducible (Priority: P1) 🎯 MVP Parte 2

**Goal**: Demostrar que el perfil activo conserva H2 en memoria, caché simple y destinos externos sin salida a Internet.

**Independent Test**: Ejecutar `UnitTestProfileTest` y comprobar en el contexto efectivo H2 en memoria con modo PostgreSQL y `create-drop`, `ConcurrentMapCacheManager`, y las dos URLs externas con host `127.0.0.1` y puerto `1`, sin invocar clientes externos.

### Tests for User Story 2

- [X] T006 [US2] Ampliar `backend/src/test/java/com/example/demo/support/UnitTestProfileTest.java` para inyectar `DataSource`, abrir/cerrar una conexión y afirmar producto H2, URL `jdbc:h2:mem:`, presencia de `MODE=PostgreSQL` en `spring.datasource.url` y valor efectivo `create-drop` en `spring.jpa.hibernate.ddl-auto`
- [X] T007 [US2] Ampliar `backend/src/test/java/com/example/demo/support/UnitTestProfileTest.java` para inyectar `CacheManager` y afirmar que el bean efectivo es `ConcurrentMapCacheManager`; si falla por configuración productiva, detenerse y registrar `[NEEDS CLARIFICATION]` con el archivo protegido involucrado, el cambio mínimo y una alternativa, sin editarlo
- [X] T008 [US2] Ampliar `backend/src/test/java/com/example/demo/support/UnitTestProfileTest.java` para leer `football-data.base-url` y `whoscored.base-url`, parsear ambas como `URI` y afirmar por separado host literal `127.0.0.1` y puerto `1`, conservando `/v4` para Football-Data y sin ejecutar los clientes externos
- [X] T009 [US2] Ejecutar desde `backend/` `mvnw.cmd -Dtest=UnitTestProfileTest test` o `./mvnw -Dtest=UnitTestProfileTest test`, comprobar que todas las garantías de `backend/src/test/java/com/example/demo/support/UnitTestProfileTest.java` pasan sin Redis, base externa ni conexiones a Football-Data o WhoScored, y revisar que cada una de las siete condiciones de SC-002 esté cubierta por una aserción independiente o claramente identificable

**Checkpoint**: US2 funciona de forma independiente sobre la convención de US1 y falla ante cualquier cambio en base, caché o destinos externos.

---

## Phase 5: User Story 3 - Saber cuándo usar el perfil (Priority: P2)

**Goal**: Dar al equipo una guía breve, copiable y multiplataforma para adoptar la convención sólo en los tests correspondientes.

**Independent Test**: Una persona puede consultar `specs/009-perfil-test/quickstart.md`, identificar en menos de dos minutos cuándo usar o no el perfil, copiar un ejemplo con `@UnitTestProfile` y encontrar los comandos completos de Linux y Windows.

### Implementation for User Story 3

- [X] T010 [P] [US3] Revisar y finalizar `specs/009-perfil-test/quickstart.md` para incluir cuándo usar `@UnitTestProfile`, la separación respecto de `@SpringBootTest`, la exclusión de e2e/Feature 011 y de migraciones de Entrega 1, y un ejemplo mínimo compilable de test nuevo
- [X] T011 [US3] Verificar en `specs/009-perfil-test/quickstart.md` que figuren literalmente `./mvnw test` para Linux y `mvnw.cmd test` para Windows, además de los comandos focalizados y los resultados esperados para perfil, H2, `ConcurrentMapCacheManager` y `127.0.0.1:1`

**Checkpoint**: US3 es verificable por inspección y no requiere modificar README ni documentación de Entrega 1.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Validar regresión, protección de Entrega 1 y gates constitucionales.

- [X] T012 Ejecutar la suite completa desde `backend/` con `mvnw.cmd test` en Windows o `./mvnw test` en Linux y registrar el resultado en `specs/009-perfil-test/quickstart.md` sólo si aparece una salvedad operativa reutilizable
- [X] T013 Verificar con `git diff --exit-code -- backend/pom.xml .github/workflows/ci.yml backend/src/main/resources/application.properties backend/src/test/resources/application.properties` y con revisión de `backend/src/test/java/` que sólo se agregaron `support/UnitTestProfile.java` y `support/UnitTestProfileTest.java`, sin dependencias, migraciones ni cambios de Entrega 1
- [ ] T014 [P] Confirmar después del push que el workflow existente en `.github/workflows/ci.yml` finaliza en `SUCCESS` conforme a Constitution §3.1, sin modificar el workflow
- [ ] T015 [P] Confirmar en el análisis asociado a `sonar-project.properties` que SonarCloud mantiene el Quality Gate aprobado, menos de 10 issues menores y cero issues nuevos atribuibles a Feature 009 conforme a Constitution §3.2

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Sin dependencias; establece el guardrail de archivos protegidos.
- **Foundational (Phase 2)**: Depende de T001 y bloquea la validación integral de las historias.
- **US1 (Phase 3)**: Depende de T002 y entrega la meta-anotación.
- **US2 (Phase 4)**: Depende de US1 porque amplía la misma prueba y consume `@UnitTestProfile`.
- **US3 (Phase 5)**: Depende conceptualmente del contrato definido en US1, pero T010 puede redactarse en paralelo con el código una vez completada T002.
- **Polish (Phase 6)**: Depende de US1, US2 y US3 completas.

### User Story Dependency Graph

```text
Setup T001
   └── Foundation T002
          ├── US1 T003–T005 ──> US2 T006–T009
          └── US3 T010–T011
                              \
                               └── Polish T012–T015
```

### User Story Dependencies

- **US1 (P1)**: Independientemente demostrable después de T002.
- **US2 (P1)**: Reutiliza la anotación y el archivo de prueba creados por US1; no depende de US3.
- **US3 (P2)**: No depende de US2 para redactarse, pero debe verificarse contra el comportamiento final antes de cerrar la feature.

### Within Each User Story

- En US1, crear primero la prueba T003 y confirmar el fallo previo; después implementar T004 y validar T005.
- En US2, las tareas T006–T008 son secuenciales porque editan el mismo archivo; T009 valida el conjunto.
- En US3, T010 completa el contenido y T011 valida expresiones y comandos exactos.
- Ninguna tarea autoriza cambios en archivos protegidos; un bloqueo de ese tipo activa el procedimiento `[NEEDS CLARIFICATION]`.

### Parallel Opportunities

- Tras T002, T003–T005 (US1) y T010 (US3) pueden avanzar en paralelo porque afectan archivos distintos.
- Tras T005, US2 puede avanzar mientras se completa T011 de US3.
- T014 y T015 pueden verificarse en paralelo una vez que el código esté publicado y T012–T013 hayan pasado.
- No marcar T006–T008 como paralelas: todas modifican `UnitTestProfileTest.java`.

---

## Parallel Example: User Story 1 and User Story 3

```text
Task A: "T003–T005 implementar y validar UnitTestProfile en backend/src/test/java/com/example/demo/support/"
Task B: "T010 finalizar la guía de adopción en specs/009-perfil-test/quickstart.md"
```

## Parallel Example: Quality Gates

```text
Task A: "T014 verificar GitHub Actions mediante .github/workflows/ci.yml"
Task B: "T015 verificar SonarCloud mediante sonar-project.properties"
```

---

## Implementation Strategy

### MVP First (las dos historias P1)

1. Completar Setup T001 y Foundation T002.
2. Completar US1 T003–T005 y validar la activación uniforme.
3. Completar US2 T006–T009 y validar el aislamiento efectivo.
4. **STOP AND VALIDATE**: ejecutar la prueba focalizada; no avanzar si requiere modificar un archivo protegido.
5. El resultado es el MVP técnico: perfil declarable y comportamiento actual demostrado.

### Incremental Delivery

1. **Fundación**: archivo mínimo por perfil sin duplicación.
2. **US1**: anotación uniforme activa `test`.
3. **US2**: prueba demuestra H2, caché simple y loopback.
4. **US3**: guía permite adopción consistente.
5. **Polish**: suite, diff protegido, CI y SonarCloud.

### Parallel Team Strategy

Con más de una persona, después de T002:

- Persona A completa US1 y continúa con US2.
- Persona B completa US3 y revisa que la documentación refleje el resultado de US2.
- Al cierre, T014 y T015 se verifican en paralelo.

---

## Notes

- `[P]` sólo aparece cuando las tareas afectan archivos distintos y no dependen de trabajo incompleto.
- Las etiquetas `[US1]`, `[US2]` y `[US3]` trazan cada tarea hacia la historia correspondiente.
- No hay tareas para `contracts/` porque la feature no expone interfaces externas.
- No se agregan dependencias, perfil e2e ni migraciones de tests existentes.
- La descarga inicial de dependencias ya declaradas puede requerir acceso al repositorio Maven; los tests de la feature no requieren Internet.
