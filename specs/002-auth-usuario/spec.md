# Feature Specification: Registro e Inicio de Sesión de Usuario

**Feature Branch**: `feature/auth-usuario`

**Created**: 2026-09-18

**Status**: Draft

**Input**: User description: "Usa la infraestructura de seguridad ya implementada (JwtUtil, BCryptPasswordEncoder, esquema de Swagger). Alcance: 1. POST de registro público: recibe username, email, password. Guarda el usuario con password hasheada, genera una ApiKey nueva para ese usuario (el valor en claro se devuelve una sola vez en la respuesta, solo se persiste el hash), balance inicial según ya define el modelo User (1000.00). 2. POST de login: recibe username y password, valida contra el hash guardado, y si es correcto devuelve un JWT generado con JwtUtil. 3. Ambos endpoints documentados en Swagger con sus request/response DTOs y códigos de error (usuario duplicado, credenciales inválidas, etc.). El modelo User, ApiKey y sus repositories YA EXISTEN, no modificar su estructura. Esta feature agrega Service y Controller sobre lo existente, respetando la arquitectura en capas."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registro de cuenta nueva (Priority: P1)

Un visitante sin cuenta se registra con un nombre de usuario, un email y una contraseña, y recibe de inmediato una credencial de acceso para poder empezar a operar en la plataforma.

**Why this priority**: Es la puerta de entrada a todo lo demás: sin poder crear una cuenta, ninguna otra funcionalidad del sistema es alcanzable para un usuario nuevo.

**Independent Test**: Se puede probar de forma completa enviando un registro con username, email y password únicos, y verificando que la respuesta incluya la confirmación de la cuenta y una credencial de acceso en texto plano, y que la cuenta quede creada con el saldo inicial estándar.

**Acceptance Scenarios**:

1. **Given** un visitante sin cuenta, **When** envía un username, email y password únicos y válidos, **Then** el sistema crea la cuenta con el saldo inicial estándar de la plataforma y devuelve una credencial de acceso que solo se muestra en ese momento.
2. **Given** un visitante que envía un username ya registrado por otra cuenta, **When** intenta registrarse, **Then** el sistema rechaza la solicitud con un error claro de "ya existe" y no crea ninguna cuenta duplicada.
3. **Given** un visitante que envía un email ya registrado por otra cuenta, **When** intenta registrarse, **Then** el sistema rechaza la solicitud con un error claro de "ya existe" y no crea ninguna cuenta duplicada.
4. **Given** un visitante que envía datos incompletos o inválidos (por ejemplo, email con formato inválido o password vacío), **When** intenta registrarse, **Then** el sistema rechaza la solicitud con un error de validación y no crea ninguna cuenta.

---

### User Story 2 - Inicio de sesión (Priority: P2)

Un usuario ya registrado se autentica con su username y password para obtener una credencial de sesión que le permite acceder al resto de la plataforma.

**Why this priority**: Sin poder iniciar sesión, una cuenta creada en la User Story 1 deja de ser utilizable apenas termina el registro; es el segundo flujo más crítico porque se repite en cada visita del usuario.

**Independent Test**: Se puede probar de forma completa registrando una cuenta y luego enviando el username y password correctos, verificando que la respuesta contenga una credencial de sesión válida.

**Acceptance Scenarios**:

1. **Given** un usuario registrado, **When** envía su username y password correctos, **Then** el sistema devuelve una credencial de sesión válida que lo identifica.
2. **Given** un usuario registrado, **When** envía un password incorrecto, **Then** el sistema rechaza el intento con un error genérico de credenciales inválidas y no emite ninguna credencial de sesión.
3. **Given** un username que nunca fue registrado, **When** alguien intenta iniciar sesión con él, **Then** el sistema rechaza el intento con el mismo error genérico de credenciales inválidas, sin revelar si el username existe o no.

---

### User Story 3 - Documentación consultable de los endpoints (Priority: P3)

Una persona integradora que explora la API puede ver, sin leer el código fuente, qué espera cada uno de estos dos endpoints y cada posible respuesta o error que pueden devolver.

**Why this priority**: No bloquea el flujo central de registro/login, pero es parte del alcance pedido y afecta directamente qué tan fácil es para el resto del equipo construir sobre esta API.

**Independent Test**: Se puede probar abriendo la documentación de la API y confirmando que ambos endpoints listan sus campos requeridos, la forma de la respuesta exitosa y cada condición de error documentada (cuenta duplicada, credenciales inválidas, errores de validación).

**Acceptance Scenarios**:

1. **Given** la documentación de la API disponible, **When** alguien consulta el endpoint de registro, **Then** ve sus campos requeridos, la forma de la respuesta exitosa y cada error que puede devolver.
2. **Given** la documentación de la API disponible, **When** alguien consulta el endpoint de login, **Then** ve sus campos requeridos, la forma de la respuesta exitosa y cada error que puede devolver.

---

### Edge Cases

- ¿Qué pasa si dos solicitudes de registro con el mismo username llegan casi al mismo tiempo? El sistema debe garantizar que solo una cuenta se cree y la otra sea rechazada como duplicada.
- ¿Qué pasa si el username coincide pero difiere solo en mayúsculas/minúsculas? Se trata como el mismo username ya registrado.
- ¿Qué pasa si un usuario pierde la credencial de acceso que se le mostró una sola vez durante el registro? Queda fuera de alcance de esta funcionalidad; requeriría una capacidad separada de recuperación o rotación.
- ¿Qué pasa si alguien intenta iniciar sesión repetidamente con credenciales incorrectas? El bloqueo o límite de intentos queda fuera de alcance de esta funcionalidad.
- ¿Qué pasa si el password enviado en el registro no cumple un largo mínimo razonable? El sistema lo rechaza como error de validación, igual que cualquier otro campo inválido.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir que cualquier visitante no autenticado cree una cuenta nueva enviando username, email y password.
- **FR-002**: El sistema DEBE rechazar un registro cuando el username o el email elegidos ya pertenecen a una cuenta existente, sin crear una cuenta nueva y devolviendo un error distinguible de "ya existe".
- **FR-003**: El sistema DEBE rechazar un registro cuando los campos obligatorios faltan o no cumplen un formato válido (por ejemplo, email mal formado o password vacío), sin crear ninguna cuenta.
- **FR-004**: El sistema DEBE almacenar el password del usuario únicamente en una forma segura e irreversible, nunca en texto plano.
- **FR-005**: Al registrarse exitosamente, el sistema DEBE crear exactamente una credencial de acceso nueva asociada a esa cuenta.
- **FR-006**: El sistema DEBE mostrar el valor en texto plano de la credencial de acceso recién creada únicamente en la respuesta del registro, y no debe poder recuperarse ni mostrarse de nuevo después de ese momento.
- **FR-007**: Al registrarse exitosamente, la cuenta nueva DEBE quedar creada con el saldo inicial estándar que ya define la plataforma para toda cuenta nueva.
- **FR-008**: El sistema DEBE permitir que un usuario ya registrado se autentique enviando su username y su password.
- **FR-009**: El sistema DEBE rechazar un intento de inicio de sesión cuando el username no existe o el password no coincide con el almacenado, usando en ambos casos el mismo mensaje de error genérico, sin revelar cuál de los dos motivos fue.
- **FR-010**: Al autenticarse exitosamente, el sistema DEBE emitir al usuario una credencial de sesión que acredite su identidad para operaciones posteriores.
- **FR-011**: El inicio de sesión NO DEBE generar, rotar ni volver a mostrar la credencial de acceso de la cuenta; esa credencial solo se entrega una vez, durante el registro.
- **FR-012**: El sistema DEBE documentar, para ambas operaciones, los campos de entrada esperados, la forma de la respuesta exitosa y cada condición de error posible (cuenta duplicada, credenciales inválidas, errores de validación), de manera que una persona integradora pueda construir contra la API sin necesidad de leer su código fuente.

### Key Entities *(include if feature involves data)*

- **Cuenta de Usuario**: representa a una persona registrada en la plataforma. Incluye un nombre de usuario y un email únicos, una contraseña almacenada de forma segura, un rol, un saldo que arranca en el valor inicial estándar de la plataforma, y la credencial de acceso asociada a esa cuenta. Ya existe como parte del sistema; esta funcionalidad la crea y la consulta, pero no cambia su estructura.
- **Credencial de Acceso**: representa el permiso para operar el resto de la API en nombre de una cuenta. Se genera exactamente una por cuenta en el momento del registro, se entrega en texto plano una única vez, y de ahí en adelante el sistema solo conserva su forma segura para poder validarla. Ya existe como parte del sistema; esta funcionalidad la genera, pero no cambia su estructura.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un visitante nuevo puede completar el registro y recibir su credencial de acceso en una única solicitud, sin intervención de soporte.
- **SC-002**: El 100% de los intentos de registro con un username o email ya utilizados son rechazados con un error claro, y nunca se crea una cuenta duplicada.
- **SC-003**: Un usuario registrado puede obtener una credencial de sesión válida en una única solicitud de login usando su username y password correctos.
- **SC-004**: El 100% de los intentos de login con credenciales incorrectas son rechazados sin otorgar ninguna credencial de sesión.
- **SC-005**: El 100% de las cuentas nuevas arrancan con el saldo inicial estándar de la plataforma, sin discrepancias.
- **SC-006**: Una persona integradora puede encontrar el contrato completo de request/response (incluyendo cada condición de error) de ambas operaciones sin necesidad de leer el código fuente.

## Assumptions

- Esta funcionalidad reutiliza la infraestructura de seguridad ya implementada en el proyecto (hasheo de contraseñas, emisión de credenciales de sesión, documentación de la API); no rediseña esa infraestructura, solo agrega las capacidades de registro e inicio de sesión sobre ella.
- Los modelos de Cuenta de Usuario y Credencial de Acceso, junto con su persistencia, ya existen en el sistema y no se modifican; esta funcionalidad agrega la lógica de negocio y los endpoints que operan sobre ellos.
- El valor de saldo inicial es el que ya define el sistema para toda cuenta nueva; esta funcionalidad no introduce ni cambia ese valor.
- Cada cuenta tiene exactamente una credencial de acceso, consistente con la relación uno a uno ya existente entre cuenta y credencial.
- Los requisitos mínimos de password siguen estándares razonables de la industria (no vacío, largo mínimo razonable) salvo que el proyecto ya defina reglas más estrictas en otro lugar.
- La rotación o recuperación de una credencial de acceso perdida, y el bloqueo de cuenta tras intentos fallidos repetidos de login, quedan fuera de alcance de esta funcionalidad.
- **Dependencia**: esta funcionalidad requiere que la infraestructura de seguridad subyacente (hasheo de contraseñas, emisión de credenciales de sesión, configuración de seguridad transversal) esté disponible en la base de código antes de poder implementarse. Esa infraestructura ya está integrada en la rama base de esta funcionalidad.
