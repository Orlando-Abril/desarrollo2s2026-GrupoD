# Phase 0 Research: Registro e Inicio de Sesión de Usuario

El `Technical Context` del plan no dejó ningún `NEEDS CLARIFICATION`: el usuario especificó
explícitamente el stack (Spring Boot, Service/Controller, DTOs separados de las entidades, Bean
Validation, tests con Mockito/MockMvc) y qué infraestructura reutilizar. Esta investigación
resuelve las decisiones de diseño concretas que ese enunciado deja abiertas.

## 1. Cómo generar el hash de la `ApiKey` nueva en el registro

**Decision**: Extraer el hashing SHA-256 hexadecimal que hoy vive como método privado
`sha256Hex` dentro de `ApiKeyAuthFilter` a una clase nueva y compartida,
`com.example.demo.security.ApiKeyHasher`, con un único método estático
`String sha256Hex(String rawValue)`. `ApiKeyAuthFilter` pasa a delegar en esa utilidad (mismo
comportamiento, sin cambio de contrato externo), y `AuthService` la usa para calcular el
`keyHash` que persiste al generar la `ApiKey` del registro.

**Rationale**: `specs/001-seguridad-infraestructura/contracts/security-infrastructure.md` §2
documenta como contrato obligatorio que cualquier componente que genere o rote una `ApiKey`
**MUST** usar exactamente ese mismo algoritmo (SHA-256 hex, sin separadores) — nunca
`BCryptPasswordEncoder`, porque BCrypt genera una sal aleatoria distinta en cada llamada y
`ApiKeyAuthFilter` necesita un lookup por igualdad exacta contra `keyHash`. Duplicar esa lógica
en `AuthService` (en vez de extraerla) arriesgaría que un cambio futuro en uno de los dos lugares
rompa el contrato en silencio (la key dejaría de autenticar sin ningún error explícito). Extraer
la utilidad es el cambio mínimo que preserva un único punto de verdad para ese algoritmo, sin
tocar la estructura de `ApiKey`/`ApiKeyRepository`.

**Alternatives considered**:
- *Duplicar el método `sha256Hex` dentro de `AuthService`*: descartado por el riesgo de
  divergencia silenciosa descrito arriba.
- *Mover el hashing a un método de instancia de `ApiKey` (modelo)*: descartado porque el enunciado
  de la feature prohíbe explícitamente modificar la estructura de `User`/`ApiKey`.

## 2. Formato del valor crudo de la `ApiKey` y su `keyPrefix`

**Decision**: Generar el valor crudo con `SecureRandom` (no `Random`), 24 bytes de entropía
codificados en Base64 URL-safe sin padding, con el prefijo literal `sk_` antepuesto (formato final
p. ej. `sk_9f3a1c...`). El campo `keyPrefix` que ya existe en `ApiKey` se completa con los
primeros 11 caracteres de ese valor (`sk_` + 8 caracteres), consistente con el formato ya usado en
los datos de prueba existentes (`ApiKeyTest`, `ApiKeyRepositoryTest`: `"sk_abcd1234"`,
`"sk_zzzz9999"`).

**Rationale**: 24 bytes (192 bits) de entropía es muy superior a lo necesario para hacer
inviable la adivinanza por fuerza bruta, y `SecureRandom` es el generador criptográficamente
seguro correcto para un secreto (a diferencia de `Random`, que es predecible). El prefijo
`keyPrefix` sólo sirve para que el usuario identifique visualmente su key en una futura UI de
gestión (ya documentado como fuera de alcance en `data-model.md` de la feature de seguridad
transversal); no es información sensible por sí sola.

**Alternatives considered**:
- *UUID aleatorio como valor crudo*: descartado por tener menos entropía efectiva y un formato
  menos reconocible como "API key" que un string `sk_...` (convención de la industria, ej.
  Stripe).

## 3. Verificación de password en login

**Decision**: `AuthService` recibe por inyección el bean `PasswordEncoder` ya declarado en
`PasswordEncoderConfig` (`BCryptPasswordEncoder`) y valida con
`passwordEncoder.matches(rawPassword, user.getPasswordHash())`. Si `UserRepository.findByUsername`
no encuentra la cuenta, `AuthService` igual ejecuta una verificación BCrypt contra un hash
"dummy" **calculado en runtime una única vez** (`passwordEncoder.encode(DUMMY_PASSWORD)` en un
campo `static final` inicializado en un bloque estático, nunca un literal de hash embebido en el
código) antes de lanzar el mismo error genérico, en vez de retornar inmediatamente. Esto evita
que un string con forma de hash BCrypt hardcodeado dispare el detector de "hardcoded credential"
de SonarCloud (mismo tipo de hallazgo ya visto en `SecurityConfig`).

**Rationale**: Reutiliza el bean ya existente sin introducir un segundo mecanismo de hashing.
Ejecutar siempre una verificación BCrypt (exista o no el username) evita que la diferencia de
tiempo de respuesta entre "username inexistente" (early return) y "username existe, password
incorrecto" (con el costo de BCrypt) sirva como oráculo para enumerar usernames válidos —
refuerzo directo de FR-009 ("sin revelar cuál de los dos motivos fue").

**Alternatives considered**:
- *Retornar inmediatamente si el username no existe*: descartado por la fuga de información por
  timing descrita arriba; es una mejora de bajo costo directamente alineada con un requisito ya
  explícito de la spec, no una funcionalidad nueva fuera de alcance.

## 4. Manejo de duplicados bajo condición de carrera

**Decision**: `AuthService` primero valida con `existsByUsername`/`existsByEmail` (chequeo
optimista, cubre el caso común) y lanza `DuplicateUserException` si corresponde. Como red de
seguridad ante dos registros concurrentes con el mismo username/email, el `save()` final se
envuelve para capturar `DataIntegrityViolationException` (lanzada por las constraints `unique`
que `User` ya define en `username`/`email`) y volver a mapearla a la misma
`DuplicateUserException`.

**Rationale**: El chequeo previo cubre el 99% de los casos con una consulta indexada barata y un
mensaje de error específico (username vs. email). La constraint `unique` de la base de datos ya
existente es la única garantía real contra la condición de carrera entre el chequeo y el `save()`
(edge case documentado en `spec.md`); capturar esa excepción evita que una carrera exponga un 500
en vez del 409 esperado.

**Alternatives considered**:
- *Sólo confiar en el chequeo previo, sin capturar la excepción de la constraint*: descartado
  porque bajo la condición de carrera documentada en `spec.md` devolvería un 500 no controlado en
  vez del 409 que pide FR-002/el edge case correspondiente.

## 5. Formato de error HTTP consistente y qué no debe loguearse

**Decision**: Un `@RestControllerAdvice` (`GlobalExceptionHandler`) mapea
`DuplicateUserException` → 409, `InvalidCredentialsException` → 401, y
`MethodArgumentNotValidException` (fallos de `@Valid`) → 400, todos con el mismo cuerpo JSON
`{"error": "<código>", "message": "<detalle>"}` — el mismo formato que `ApiKeyAuthFilter` ya usa
para sus 401. Ni el password (crudo o hasheado) ni el valor crudo de la `ApiKey` se incluyen
nunca en logs ni en excepciones que puedan propagarse a una respuesta; el valor crudo de la
`ApiKey` sólo existe en la variable local de `AuthService` y en `RegisterResponse`, nunca en un
campo persistido ni en un mensaje de log.

**Rationale**: Consistencia de contrato de error para el frontend (un solo shape de error sin
importar qué filtro o controller lo generó) y para SonarCloud, que marca como vulnerabilidad
tanto el log de secretos como las excepciones genéricas sin manejar (alineado con el Constitution
Check §3.2).

**Alternatives considered**:
- *Manejar cada excepción con `try/catch` local dentro de `AuthController`*: descartado por ser
  más repetitivo que un único `@RestControllerAdvice` y por alejarse del estilo ya usado en el
  proyecto de mantener el manejo de errores HTTP centralizado y consistente.

## 6. Documentación Swagger de los DTOs y códigos de error

**Decision**: `AuthController` se anota con `@Tag(name = "Auth")`; cada método con
`@Operation(summary = ...)` y `@ApiResponse` explícito por cada código posible (201/200, 400, 401,
409). Los DTOs de request usan Bean Validation (`@NotBlank`, `@Email`, `@Size`) — springdoc los
refleja automáticamente en el schema de OpenAPI sin anotaciones adicionales. Ninguno de los dos
endpoints requiere `@SecurityRequirement` (son públicos), a diferencia de futuros controllers que
sí deberán referenciar `bearerAuth`/`apiKeyAuth` (ya definidos en `OpenApiConfig`).

**Rationale**: Cumple FR-012/SC-006 de la spec (contrato completo consultable sin leer código) y
el principio 6.1 de la constitution, reutilizando exactamente las convenciones ya fijadas por la
feature de seguridad transversal (mismos nombres de esquema, mismo enfoque de Bean Validation
reflejado en el schema).

**Alternatives considered**:
- *Documentar sólo el happy path y dejar los errores implícitos*: descartado porque contradice
  directamente FR-012 y el edge-case/success-criteria de la spec sobre documentación consultable.

## 7. Unicidad de `username` insensible a mayúsculas/minúsculas

**Decision**: `AuthService` normaliza `username` a minúsculas (`username.toLowerCase()`) antes de
`existsByUsername`, `findByUsername` y de persistirlo. El valor guardado y devuelto en
`RegisterResponse`/`LoginResponse` es siempre la forma normalizada (todo en minúsculas).

**Rationale**: El edge case de `spec.md` exige tratar "Abril" y "abril" como el mismo username.
`UserRepository.existsByUsername`/`findByUsername` son derived queries sin `IgnoreCase`
(comparación exacta) y no se pueden modificar. Normalizar en el service es la única forma de
garantizar unicidad case-insensitive sin tocar el repository. Decisión confirmada: todo
`username` se guarda en minúsculas, sin preservar el casing que haya tipeado el usuario.

**Alternatives considered**:
- *Comparación case-insensitive sólo en la validación previa, sin normalizar el dato
  persistido*: descartada porque no evita duplicados si los datos ya existentes no están
  normalizados; degenera en el mismo problema.
- *Preservar el casing original y comparar de forma case-insensitive en memoria*: descartada por
  requerir traer todos los usernames a memoria (sin soporte de `IgnoreCase` en el repository) y
  por la decisión explícita del equipo de guardar todo en minúsculas.
