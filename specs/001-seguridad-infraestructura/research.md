# Phase 0 Research: Infraestructura Transversal de Seguridad

El `Technical Context` del plan no dejó ningún `NEEDS CLARIFICATION`: el usuario especificó
explícitamente el stack (Spring Boot/Java 17/Maven, jjwt, springdoc, JUnit5/MockMvc) y las piezas
a construir. Esta investigación resuelve, en cambio, las decisiones de diseño concretas que ese
enunciado deja abiertas y que impactan en `data-model.md` y `tasks.md`.

## 1. Cómo verificar el `X-API-KEY` contra el hash persistido

**Decision**: Hashear la key entrante con un hash determinístico y de una sola pasada
(`Hex.encode(SHA-256(rawKey))`, hexadecimal en minúsculas, sin separadores) y buscarla por
igualdad exacta con `ApiKeyRepository.findByKeyHash(hash)` (método ya existente). Esta
codificación exacta es el contrato que debe respetar cualquier componente que genere o rote una
`ApiKey` (ver `contracts/security-infrastructure.md` §2). `BCryptPasswordEncoder` se reserva
exclusivamente para passwords de usuario.

**Rationale**: BCrypt genera una sal aleatoria distinta en cada invocación, por lo que dos hashes
de la misma password (o key) no son iguales entre sí — sólo se puede verificar con
`passwordEncoder.matches(raw, hashAlmacenado)` contra un hash ya conocido de antemano. Como el
requisito explícito es "buscar el ApiKey por keyHash" (lookup indexado por igualdad, no un barrido
`matches()` contra cada fila activa), se necesita un hash determinístico. Las API Keys ya son
valores de alta entropía generados por el sistema (no memorizados por humanos, sin reutilización
entre sitios), por lo que un hash rápido como SHA-256 es una práctica aceptada para este caso de
uso (a diferencia de passwords de usuario, donde el costo computacional de BCrypt es deseable
para dificultar ataques de fuerza bruta offline).

**Alternatives considered**:
- *BCrypt + recorrido de todas las keys activas llamando `matches()`*: descartado por costo
  O(n) por request y porque contradice el requisito explícito de "buscar por keyHash".
- *HMAC-SHA256 con secreto de aplicación*: más robusto contra un volcado de la tabla combinado
  con conocimiento del algoritmo, pero agrega gestión de un secreto adicional sin que el alcance
  de la feature lo pida; se documenta como mejora futura, no bloqueante.

## 2. Emisión y validación de JWT con jjwt 0.13.0

**Decision**: `JwtUtil` usa la API moderna de `jjwt` (`Jwts.builder()` / `Jwts.parser()`) con
firma simétrica HS256, una clave de firma derivada de un secreto configurado
(`security.jwt.secret`, variable de entorno / `application.properties`, nunca hardcodeada) y una
expiración configurable (`security.jwt.expiration-ms`, default 3600000 = 1 hora). El claim
`subject` almacena el username. Métodos expuestos: generar token desde un username, extraer el
username de un token, y validar (firma + expiración) sin lanzar excepciones no controladas hacia
el caller.

**Rationale**: HS256 simétrico es suficiente porque el mismo backend monolítico emite y valida los
tokens (no hay un verificador externo que requiera clave pública/privada separada). Mantener el
secreto fuera del código evita el hallazgo típico de SonarCloud de "hardcoded credentials" y
permite rotarlo por entorno.

**Alternatives considered**:
- *RS256 (asimétrico)*: descartado por complejidad innecesaria dado que no hay un servicio externo
  que sólo necesite verificar tokens.
- *Sesiones de servidor (HttpSession)*: descartado porque la constitution exige explícitamente JWT
  para autenticación de usuarios (sección 4.1) y porque el frontend es una SPA separada del
  backend.

## 3. Alcance exacto de lo que se integra al `SecurityFilterChain`

**Decision**: En esta feature, únicamente `ApiKeyAuthFilter` se registra como filtro dentro del
`SecurityFilterChain` (antes del filtro de autenticación por usuario/contraseña de Spring
Security). `JwtUtil` se entrega como una clase de utilidad **standalone, sin un filtro que la
conecte** al `SecurityFilterChain` todavía.

**Rationale**: Conectar JWT a la cadena de filtros requeriría resolver un usuario a partir del
username del token (típicamente vía un `UserDetailsService`), lo cual implica lógica de usuarios
que el propio enunciado de la feature excluye explícitamente ("sin lógica de usuarios todavía").
Esa pieza (un `JwtAuthFilter` que sí puebla el `SecurityContext`) queda para la feature de
login/registro, que ya tendrá el repositorio de usuarios disponible y reutilizará `JwtUtil` para
la parte criptográfica. Mientras tanto, cualquier ruta marcada como protegida y sin `X-API-KEY`
válido queda bloqueada por el comportamiento por defecto de Spring Security
(`anyRequest().authenticated()` sin `Authentication` en el contexto → 401), que es exactamente lo
que pide `FR-002`/`SC-001` de la spec sin necesitar lógica de usuarios.

**Alternatives considered**:
- *Construir ya un `JwtAuthFilter` "vacío" que sólo valide firma/expiración sin resolver un
  usuario real*: descartado porque generaría una autenticación parcial engañosa (un token válido
  autenticaría la request sin verificar que el usuario siga existiendo/activo), y porque el
  enunciado de la feature es explícito en no incluir lógica de usuarios.

## 4. Rutas públicas por defecto y sesión

**Decision**: `SessionCreationPolicy.STATELESS`, CSRF deshabilitado (estándar para APIs stateless
consumidas por una SPA con JWT/ApiKey), y como rutas públicas explícitas: `/auth/register`,
`/auth/login` (aún no implementadas, pero reservadas para la feature de login), `/swagger-ui/**`,
`/v3/api-docs/**`. Todo lo demás requiere autenticación.

**Rationale**: Coincide literalmente con lo indicado por el usuario y con la Assumption ya
registrada en `spec.md` de que la documentación interactiva es pública por defecto. CSRF no aplica
porque no hay autenticación basada en cookies/sesión de navegador en este diseño.

**Alternatives considered**:
- *CSRF habilitado con tokens*: descartado porque no hay sesiones de servidor ni cookies de
  autenticación; el vector CSRF clásico no aplica a un backend stateless consumido con
  Bearer/ApiKey.

## 5. CORS

**Decision**: Un bean `CorsConfigurationSource` con `allowedOrigins` limitado a
`http://localhost:5173` (leído de `application.properties` como
`security.cors.allowed-origin` para no hardcodear el valor), métodos estándar
(GET/POST/PUT/PATCH/DELETE/OPTIONS) y headers `Authorization`, `X-API-KEY`, `Content-Type`.

**Rationale**: Cumple FR-003 (CORS restringido) y SC-004 de la spec; parametrizarlo evita
duplicar el literal del origen en varios lugares y facilita agregar orígenes de staging/prod más
adelante sin tocar código (alineado con la Assumption de la spec sobre orígenes futuros).
