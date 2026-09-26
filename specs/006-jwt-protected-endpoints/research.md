# Research: Autenticación JWT en endpoints protegidos

No quedaron `NEEDS CLARIFICATION`: la especificación y el diseño provisto fijan clases, precedencia, comportamiento, alcance y pruebas. Esta fase valida las decisiones contra la estructura actual del proyecto y registra las alternativas descartadas.

## R1 — Composición de JWT y API key

**Decision**: Ejecutar la validación Bearer antes de la API key. El filtro JWT sólo interviene cuando Authorization comienza con `Bearer `; si autentica, el filtro de API key detecta el contexto autenticado y continúa sin exigir `X-API-KEY`.

**Rationale**: Implementa exactamente la alternativa JWT O API key y la precedencia pedida, conservando intacta la lógica de API key para toda solicitud sin Bearer.

**Alternatives considered**:

- Un único filtro para ambas credenciales: descartado porque mezcla responsabilidades y obliga a refactorizar lógica estable.
- Ejecutar API key primero: descartado porque impediría autenticar con JWT sin API key.
- Intentar API key después de un Bearer inválido: descartado expresamente por el contrato.

## R2 — Registro seguro del orden de filtros

**Decision**: En la configuración, registrar primero `ApiKeyAuthFilter` respecto del filtro estándar y luego `JwtAuthFilter` respecto de `ApiKeyAuthFilter`. El orden de ejecución resultante es JWT → API key → filtro estándar.

**Rationale**: Spring Security necesita conocer previamente el orden del filtro personalizado usado como referencia. Invertir las llamadas puede impedir la construcción de la cadena aunque el orden conceptual sea correcto.

**Alternatives considered**:

- Registrar JWT respecto del filtro personalizado antes de asignar orden a API key: descartado porque el filtro de referencia todavía no tiene orden conocido.
- Registrar ambos directamente respecto del filtro estándar: descartado porque deja menos explícita la precedencia relativa entre los filtros propios.

## R3 — Validación y principal del JWT

**Decision**: Reutilizar `JwtUtil.isTokenValid(token)` y, sólo si devuelve verdadero, `JwtUtil.extractUsername(token)` para establecer una autenticación cuyo principal es el subject y sin authorities.

**Rationale**: `JwtUtil` ya centraliza firma y expiración; reutilizarlo mantiene idéntico el contrato y evita nuevos parsers o dependencias.

**Alternatives considered**:

- Validar claims o firma dentro del filtro: descartado por duplicación y riesgo de divergencia.
- Consultar el usuario en base de datos por cada JWT: descartado porque cambia la semántica stateless y agrega I/O.
- Incorporar roles al contexto: descartado por estar fuera de alcance.

## R4 — Rutas públicas

**Decision**: `JwtAuthFilter.shouldNotFilter` deriva el path de la misma manera que `ApiKeyAuthFilter` y compara contra `SecurityConfig.getPublicRoutes()` con el mismo matcher.

**Rationale**: La autorización `permitAll` por sí sola no impide que un filtro personalizado rechace antes. Ambos filtros deben omitir independientemente registro, login, Swagger, documentación y salud sin duplicar la lista.

**Alternatives considered**:

- Duplicar la lista dentro del filtro nuevo: descartado porque podría divergir.
- Confiar sólo en `permitAll`: descartado porque el filtro podría interceptar la ruta.
- Agregar otras rutas públicas: descartado por alcance.

## R5 — Respuesta a Bearer inválido

**Decision**: Responder inmediatamente `401`, JSON UTF-8, con `{"error":"unauthorized","message":"Token inválido o vencido"}`, sin continuar la cadena.

**Rationale**: Evita fallback ambiguo, mantiene el formato existente y no revela si fallaron firma, formato o expiración.

**Alternatives considered**:

- Mensajes distintos por causa: descartado por exposición innecesaria y por contradecir el body exacto.
- Delegar en el entry point genérico: descartado porque no garantiza el body requerido.
- Propagar la excepción JWT: descartado porque podría producir `500`.

## R6 — Semántica OpenAPI de alternativas

**Decision**: Mantener `@SecurityRequirement(name = "apiKeyAuth")` y agregar por separado `@SecurityRequirement(name = "bearerAuth")` en `PlayerController`.

**Rationale**: Los elementos separados del arreglo de seguridad representan OR; ambos esquemas dentro del mismo elemento representarían AND. Los esquemas ya existen y no deben redefinirse.

**Alternatives considered**:

- Un único requisito con ambos esquemas: descartado porque comunicaría credenciales simultáneas.
- Reemplazar API key por Bearer: descartado porque rompe acceso programático.
- Declaración global: descartada porque podría marcar rutas públicas como protegidas.

## R7 — Cobertura de tests

**Decision**: Separar pruebas unitarias por filtro, integración MockMvc para la cadena completa y el flujo register/login/players, y contrato OpenAPI para la documentación generada.

**Rationale**: Las unitarias localizan fallas de decisión; la integración demuestra orden y precedencia reales; OpenAPI evita divergencia documental.

**Alternatives considered**:

- Sólo tests unitarios: descartado porque no prueban la cadena real.
- Todos los casos sólo en integración: descartado porque dificulta localizar regresiones.
- Reescribir tests existentes de API key: descartado; permanecen como regresión y sólo se suma el caso autorizado.

## R8 — Persistencia y dependencias

**Decision**: No agregar entidades, migraciones, repositories ni dependencias. Los tests integrados reutilizan H2 y las bibliotecas existentes; producción mantiene PostgreSQL.

**Rationale**: La verificación JWT usa el secreto configurado y sólo produce estado transitorio durante la solicitud.

**Alternatives considered**:

- Persistir sesiones o tokens: descartado por diseño stateless y fuera de alcance.
- Agregar otra biblioteca JWT: descartado porque JJWT ya satisface generación y validación.
