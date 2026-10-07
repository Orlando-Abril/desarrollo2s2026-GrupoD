# Feature Specification: Perfil uniforme de pruebas del backend

**Feature Branch**: N/A — no se creó una rama para esta especificación

**Created**: 2026-10-07

**Status**: Draft

**Input**: User description: "Crear el perfil de ejecución `test` para los tests unitarios y de integración liviana del backend, junto con una meta-anotación uniforme equivalente a `@ActiveProfiles(\"test\")`, conservando el comportamiento actual: base H2 en memoria con compatibilidad PostgreSQL y recreación del esquema, caché simple y fuentes Football-Data y WhoScored inaccesibles. Agregar una prueba demostrativa y documentación breve. No modificar archivos de Entrega 1 ni agregar dependencias."

## Contexto y restricciones

Actualmente los tests del backend comparten la configuración general ubicada en los recursos de prueba, sin declarar perfiles activos. Esta feature formaliza ese comportamiento como un entorno de pruebas identificable y reutilizable, para que los tests nuevos de la Entrega 2 no dependan de una activación implícita.

La configuración resultante debe conservar sin cambios observables el aislamiento actual: datos efímeros en memoria con estrategia de esquema `create-drop`, caché local simple y fuentes Football-Data y WhoScored dirigidas a una dirección local inaccesible. Las integraciones Football-Data y WhoScored no deben tener destinos públicos configurados bajo el perfil `test`; ambas deben apuntar exclusivamente a loopback en el puerto inaccesible definido para pruebas.

Los archivos de Entrega 1 `pom.xml`, `ci.yml`, `application.properties` de main y test, y los tests existentes están protegidos y no pueden modificarse sin consulta previa. Si durante la planificación o implementación se determinara que uno de esos cambios es imprescindible, el trabajo debe detenerse y registrar `[NEEDS CLARIFICATION]` con el archivo, el cambio mínimo propuesto y una alternativa que lo evite. No se permiten dependencias nuevas.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Declarar un entorno de test uniforme (Priority: P1)

Como desarrollador de la Entrega 2, quiero marcar un test con una única anotación compartida para activar el perfil `test`, de modo que el entorno requerido sea explícito, uniforme y difícil de configurar de forma incompleta.

**Why this priority**: La declaración uniforme es el objetivo central de la feature y evita divergencias entre los tests nuevos.

**Independent Test**: Crear un test nuevo de la Entrega 2 que use sólo la meta-anotación compartida y comprobar que el contexto reconoce exactamente el perfil `test` como activo, sin declarar el perfil por separado.

**Acceptance Scenarios**:

1. **Given** un test nuevo de la Entrega 2, **When** el desarrollador aplica la meta-anotación compartida, **Then** el perfil `test` queda activo con el mismo efecto que una declaración directa de `@ActiveProfiles("test")`.
2. **Given** un test que usa la meta-anotación, **When** se inspecciona su declaración, **Then** no necesita repetir ni combinar manualmente la activación del perfil `test`.
3. **Given** la suite existente de Entrega 1, **When** se incorpora la nueva convención, **Then** ningún test existente es migrado ni modificado.

---

### User Story 2 - Ejecutar tests con aislamiento reproducible (Priority: P1)

Como desarrollador, quiero que el perfil `test` reproduzca el entorno de pruebas actual, para ejecutar tests unitarios e integraciones livianas sin depender de infraestructura persistente ni de servicios externos.

**Why this priority**: La convención sólo es segura si mantiene el comportamiento ya conocido por el equipo y evita efectos externos.

**Independent Test**: Iniciar un contexto de prueba mediante la meta-anotación y verificar en una sola prueba demostrativa que la base activa es H2 en memoria con compatibilidad PostgreSQL, que la recreación del esquema está configurada mediante `create-drop`, que el administrador de caché es el simple y que ambas fuentes externas apuntan exclusivamente a un destino local inaccesible, sin conexiones públicas.

**Acceptance Scenarios**:

1. **Given** un test marcado con la meta-anotación, **When** inicia su contexto, **Then** usa una base H2 en memoria, compatible con el comportamiento PostgreSQL esperado por la suite y con la estrategia de esquema `create-drop` activa.
2. **Given** el mismo contexto, **When** se resuelve el administrador de caché, **Then** se obtiene el administrador simple usado actualmente en tests y no el caché Redis obligatorio del runtime del producto.
3. **Given** el mismo contexto, **When** se inspeccionan los destinos configurados para Football-Data y WhoScored, **Then** ambos son direcciones de loopback deliberadamente inaccesibles y ninguna URL pública queda configurada.
4. **Given** que las fuentes externas no están disponibles, **When** se ejecuta la prueba demostrativa, **Then** confirma que las dos integraciones tienen destinos de loopback en el puerto inaccesible y ninguna tiene un destino público configurado.

---

### User Story 3 - Saber cuándo usar el perfil (Priority: P2)

Como integrante del equipo, quiero una guía breve sobre el alcance del perfil, para aplicarlo en los tests correctos y no confundirlo con pruebas e2e.

**Why this priority**: Una convención sin alcance documentado puede extenderse a suites que requieren otra infraestructura y perder su significado.

**Independent Test**: Consultar la documentación y confirmar que identifica los tests destinatarios, la forma uniforme de activación, las garantías del entorno y los casos fuera de alcance.

**Acceptance Scenarios**:

1. **Given** un desarrollador que crea un test unitario o de integración liviana de Entrega 2, **When** consulta la documentación, **Then** encuentra la meta-anotación que debe usar y las garantías del perfil `test`.
2. **Given** un desarrollador que prepara una prueba e2e, **When** consulta la misma sección, **Then** entiende que este perfil no corresponde y que el perfil e2e pertenece a Feature 011.
3. **Given** un test existente de Entrega 1, **When** se consulta la guía, **Then** queda claro que su migración es opcional y requiere aprobación previa.

### Edge Cases

- Si otro perfil se declara junto con `test`, la prueba demostrativa debe fallar o señalar de forma inequívoca que ya no se está validando el entorno uniforme esperado.
- Si una propiedad del perfil falta y el runtime intenta heredar una URL pública desde otra configuración, la verificación de aislamiento debe detectarlo antes de cualquier acceso externo.
- Si Football-Data y WhoScored usan claves de configuración diferentes, ambas deben verificarse de manera independiente; validar una fuente no implica validar la otra.
- Si el administrador de caché del runtime del producto está disponible en el entorno local, el perfil `test` debe seguir seleccionando el caché simple y no depender de Redis.
- Si dos tests comparten un contexto, sus resultados no deben depender de datos persistidos fuera de la base efímera propia de la ejecución de pruebas.
- Si preservar exactamente el comportamiento actual exigiera modificar un archivo protegido de Entrega 1, debe aplicarse la regla de consulta y no realizar el cambio de forma implícita.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El backend MUST ofrecer un perfil de ejecución llamado `test` destinado a tests unitarios y de integración liviana.
- **FR-002**: El perfil `test` MUST reproducir el comportamiento observable de la configuración de pruebas vigente antes de esta feature.
- **FR-003**: Al activar el perfil `test`, la persistencia MUST usar H2 en memoria en modo compatible con PostgreSQL y MUST tener configurada la estrategia `create-drop`, sin requerir una base externa.
- **FR-004**: Al activar el perfil `test`, el administrador de caché MUST ser el administrador simple en memoria y MUST NOT requerir Redis.
- **FR-005**: Al activar el perfil `test`, los destinos de Football-Data y WhoScored MUST apuntar a `http://127.0.0.1:1/` o a su forma equivalente normalizada de loopback inaccesible, y MUST NOT resolver a destinos públicos.
- **FR-006**: La ejecución de tests bajo el perfil `test` MUST poder completarse sin acceder a Football-Data ni WhoScored; ambas integraciones MUST tener exclusivamente destinos de loopback inaccesibles y MUST NOT tener destinos públicos configurados.
- **FR-007**: El backend MUST proporcionar una meta-anotación de test compartida cuya aplicación active el perfil `test` con semántica equivalente a `@ActiveProfiles("test")`.
- **FR-008**: Todos los tests nuevos de la Entrega 2 que sean unitarios o de integración liviana y necesiten contexto de aplicación MUST usar la meta-anotación compartida como forma uniforme de declarar el perfil.
- **FR-009**: Debe existir al menos una prueba demostrativa que use la meta-anotación y verifique conjuntamente: perfil activo, base H2 en memoria y compatibilidad esperada, administrador de caché simple, y destinos de loopback inaccesibles —sin destinos públicos— para ambas fuentes externas.
- **FR-010**: La prueba demostrativa MUST fallar si cualquiera de las garantías verificadas deja de cumplirse.
- **FR-011**: Debe existir una sección breve de documentación que indique cuándo usar el perfil `test`, cómo declararlo mediante la meta-anotación, qué aislamiento garantiza y cuándo no usarlo.
- **FR-012**: La feature MUST NOT modificar `pom.xml`, `ci.yml`, los archivos `application.properties` de main o test ni tests existentes de Entrega 1 sin aprobación explícita del equipo.
- **FR-013**: Si un cambio sobre un archivo protegido resulta imprescindible, la planificación o implementación MUST registrarlo como `[NEEDS CLARIFICATION]`, identificando el archivo, el cambio mínimo y una alternativa que no lo modifique, y MUST esperar respuesta antes de aplicarlo.
- **FR-014**: La feature MUST NOT agregar dependencias.
- **FR-015**: La feature MUST NOT crear el perfil e2e de Feature 011 ni migrar tests existentes a la nueva meta-anotación.
- **FR-016**: El uso de caché simple bajo `test` MUST quedar limitado al entorno de pruebas y MUST NOT alterar el mandato de Redis para el runtime del producto.

### Key Entities

- **Perfil `test`**: Entorno nominal y aislado para tests unitarios e integraciones livianas; agrupa las garantías de persistencia efímera, caché simple y fuentes externas inaccesibles.
- **Meta-anotación de test**: Declaración compartida que activa de forma uniforme el perfil `test` en los tests nuevos de la Entrega 2.
- **Prueba demostrativa del entorno**: Verificación automatizada que detecta cualquier divergencia en el perfil activo, persistencia, caché o aislamiento de red.
- **Fuente externa configurada**: Cada integración con Football-Data o WhoScored, representada por el destino efectivo que usaría durante una prueba.
- **Archivo protegido de Entrega 1**: Archivo o test cuya modificación requiere consulta y aprobación explícitas antes de implementarse.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los tests nuevos de Entrega 2 dentro del alcance puede declarar el entorno esperado mediante una única anotación compartida.
- **SC-002**: En el 100% de las ejecuciones de la prueba demostrativa, se verifican siete condiciones antes de aprobar el entorno: perfil correcto, base en memoria, compatibilidad esperada, configuración `create-drop`, caché local simple y aislamiento individual de las dos fuentes externas.
- **SC-003**: La prueba demostrativa identifica exactamente las dos fuentes externas alcanzadas por esta feature, confirma que ambas apuntan a loopback y contabiliza cero destinos públicos configurados.
- **SC-004**: La incorporación del perfil produce cero cambios en los archivos protegidos de Entrega 1 y agrega cero dependencias.
- **SC-005**: Cada garantía del entorno —perfil activo, H2 en memoria, compatibilidad PostgreSQL, `create-drop`, caché simple y aislamiento individual de ambas fuentes— está cubierta por al menos una aserción que provoca el fallo de la prueba cuando el valor efectivo no coincide con el esperado.
- **SC-006**: Un integrante del equipo puede identificar en menos de 2 minutos, usando la documentación, si debe aplicar el perfil `test` o reservar su caso para el futuro perfil e2e.
- **SC-007**: La suite existente conserva el 100% de sus tests sin migraciones ni modificaciones como parte de esta feature.

## Assumptions

- `specs/009-perfil-test` es el directorio solicitado y es independiente del nombre de cualquier rama Git.
- La configuración vigente en `backend/src/test/resources/application.properties` es la referencia de comportamiento que el nuevo perfil debe reproducir, pero el archivo permanece sin modificaciones.
- Crear nuevos recursos, código auxiliar de test, una prueba demostrativa y documentación no constituye modificar archivos de Entrega 1, siempre que no se alteren los archivos protegidos enumerados.
- La equivalencia de la meta-anotación se limita a activar el perfil `test`; no incorpora por defecto otras anotaciones de arranque o categorías de prueba.
- “Sin salida a Internet” significa, para las integraciones alcanzadas por esta feature, que la configuración no contiene destinos públicos y que ambas fuentes apuntan a loopback en un puerto deliberadamente inaccesible; no exige simular respuestas ni instrumentar todo el tráfico del proceso de prueba.
- El destino `127.0.0.1:1` es intencionalmente inaccesible para las integraciones externas durante tests y reproduce la configuración vigente.
- El caché simple es una excepción exclusiva del perfil de pruebas y no contradice el uso obligatorio de Redis en el runtime del producto.

## Out of Scope

- Crear o definir el perfil e2e previsto para Feature 011.
- Migrar tests existentes de Entrega 1 a la meta-anotación.
- Modificar `pom.xml`, `ci.yml`, cualquier `application.properties` existente de main o test, o tests existentes.
- Incorporar dependencias nuevas, contenedores, servicios externos, Redis o una base persistente para esta suite.
- Cambiar el comportamiento del runtime productivo o sus decisiones de persistencia, caché e integraciones.
