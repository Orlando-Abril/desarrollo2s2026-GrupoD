# Implementation Plan: Autenticación JWT en endpoints protegidos

**Branch**: `feature/correccionJWT` | **Date**: 2026-09-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/006-jwt-protected-endpoints/spec.md` y diseño técnico cerrado provisto por la usuaria.

## Summary

Completar el flujo de autenticación web permitiendo que toda ruta protegida acepte el JWT emitido por el login o, como alternativa, la API key existente. Un nuevo filtro JWT se ejecutará antes del filtro de API key: un Bearer válido autenticará con el subject como principal; uno inválido responderá `401` sin fallback; y la ausencia de Bearer dejará actuar al filtro de API key sin alterar su comportamiento. La documentación de jugadores declarará ambos esquemas como alternativas. No se agregan endpoints, dependencias, persistencia ni cambios a los contratos de registro/login.

## Technical Context

**Language/Version**: Java 17 como nivel objetivo del proyecto y de CI (compatible con el JDK 22 usado localmente)

**Primary Dependencies**: Spring Boot 4.1.1, Spring Security 7.1.1, JJWT 0.13.0 y springdoc OpenAPI 3.1.1; todas ya presentes

**Storage**: PostgreSQL para usuarios y API keys existentes; H2 en tests. Esta feature no agrega ni modifica datos persistentes

**Testing**: JUnit 6, Spring Boot Test, MockMvc, Mockito y AssertJ mediante Maven Surefire

**Target Platform**: Backend HTTP stateless ejecutable en Windows y Linux; CI sobre Linux con JDK 17

**Project Type**: Aplicación web con backend Spring Boot y frontend independiente; esta feature modifica sólo el backend y su documentación

**Performance Goals**: La autenticación JWT no realiza acceso a datos; las solicitudes con API key conservan el único acceso actual al repositorio. No se agrega trabajo externo ni persistencia al camino de autenticación

**Constraints**: Precedencia estricta de Bearer; error JWT exacto en JSON UTF-8; API key sin cambios; rutas públicas sin cambios; sesión stateless; sin dependencias nuevas; sin cambios de modelos, repositorios, CORS, CSRF ni contratos de login/registro

**Scale/Scope**: Una clase de seguridad nueva; cambios mínimos en dos clases de seguridad y un controller; tests unitarios, de integración y de OpenAPI; un contrato, guía de validación y actualización del README raíz

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Evaluación previa a Phase 0

| Principio constitucional | Evaluación | Resultado |
|---|---|---|
| 2.1 Backend en capas | La autenticación permanece en la infraestructura transversal de seguridad y no introduce lógica de negocio en controllers o repositories. | PASS |
| 3.1 Integración continua | El cierre exige `mvnw verify` y la ejecución posterior del workflow existente, sin modificar su alcance. | PASS |
| 3.2 SonarCloud | El diseño minimiza duplicación, limita cambios y exige no introducir issues nuevos. | PASS |
| 4.1 Autenticación y autorización | Conecta el JWT ya emitido a las rutas protegidas y conserva la API key como alternativa. | PASS |
| 4.3 Validación estricta | Los Bearer vencidos, mal formados o con firma inválida se rechazan de manera determinista y sin fallback. | PASS |
| 5.3 Observabilidad | Los filtros no alteran Correlation ID ni el formato general de errores. | PASS |
| 6.1 OpenAPI / Swagger | Las operaciones protegidas documentarán ambos esquemas existentes como alternativas. | PASS |
| 7 Definition of Done | El plan incluye tests, verificación completa, CI, SonarCloud y documentación. | PASS |

No hay violaciones constitucionales ni excepciones que justificar. La auditoría financiera, las fuentes externas, la caché y las reglas de mercado no son afectadas.

### Reevaluación posterior a Phase 1

El diseño detallado conserva los mismos límites: no crea entidades ni migraciones, no cambia contratos existentes, no incorpora dependencias y documenta explícitamente la alternativa Bearer/API key. Todos los gates continúan en **PASS**.

## Project Structure

### Documentation (this feature)

```text
specs/006-jwt-protected-endpoints/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── protected-authentication.md
├── checklists/
│   └── requirements.md
└── spec.md
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                      # sin cambios
└── src/
    ├── main/java/com/example/demo/
    │   ├── config/
    │   │   ├── SecurityConfig.java              # ordenar JWT antes de API key
    │   │   └── OpenApiConfig.java               # esquemas existentes, sin cambios
    │   ├── controller/
    │   │   └── PlayerController.java            # declarar bearerAuth además de apiKeyAuth
    │   └── security/
    │       ├── JwtAuthFilter.java                # nuevo filtro Bearer
    │       ├── ApiKeyAuthFilter.java             # omitir si ya hay autenticación
    │       └── JwtUtil.java                      # reutilizado, sin cambios
    └── test/java/com/example/demo/
        ├── config/
        │   └── PlayerOpenApiTest.java            # comprobar alternativas OpenAPI
        └── security/
            ├── JwtAuthFilterTest.java            # nuevo test unitario
            ├── ApiKeyAuthFilterTest.java         # un caso nuevo; tests actuales intactos
            └── JwtOrApiKeyAuthenticationIntegrationTest.java
                                                    # criterios y flujo completo

README.md                                          # sección Backend: uso de bearerAuth en Swagger
```

**Structure Decision**: Se conserva la aplicación existente y se limita la implementación al módulo `backend/`. La seguridad transversal queda en `security/` y su cableado en `config/`. Fuera de `backend/`, sólo se modifican los artefactos de esta feature y el `README.md` raíz autorizado.

## Implementation Strategy

### 1. Filtro JWT aislado

Crear `JwtAuthFilter` como filtro ejecutado una vez por solicitud y componente administrado. Debe reutilizar la lista pública central mediante el mismo matching de rutas que el filtro de API key. Si Authorization no comienza con `Bearer `, delega sin modificar contexto ni respuesta. Si comienza con Bearer, extrae el token y usa `JwtUtil`: al ser válido, establece una autenticación sin authorities cuyo principal es el username y continúa; en cualquier otro caso escribe el `401` JSON exacto en UTF-8 y termina la cadena.

### 2. Composición sin alterar API key

Agregar al inicio de `ApiKeyAuthFilter.doFilterInternal` una única salida temprana: si el contexto ya contiene una autenticación autenticada, continuar la cadena y retornar. Todo el código posterior permanece intacto.

En `SecurityConfig`, primero registrar `ApiKeyAuthFilter` antes de `UsernamePasswordAuthenticationFilter` y después registrar `JwtAuthFilter` antes de `ApiKeyAuthFilter`. Spring necesita que el filtro personalizado de referencia ya tenga orden asignado; estas llamadas producen el orden efectivo JWT → API key → filtro estándar sin cambiar ninguna otra política.

### 3. Contrato OpenAPI alternativo

Conservar `apiKeyAuth` y agregar `bearerAuth` como anotación separada en `PlayerController`. Verificar que cada requisito ocupa un objeto alternativo de seguridad y no depender del orden del arreglo generado. No agrupar ambos esquemas en un mismo objeto, porque comunicaría una conjunción.

### 4. Estrategia de tests

- `JwtAuthFilterTest`: usar un `JwtUtil` real y ejecutar el método público del filtro para cubrir también `shouldNotFilter`. Validar token vigente, vencido con expiración negativa, firmado con otro secreto de longitud válida, mal formado, header ausente, esquema Basic y ruta pública. Limpiar el contexto de seguridad entre tests.
- `ApiKeyAuthFilterTest`: conservar los tests actuales y sumar sólo el caso de contexto previamente autenticado, comprobando continuidad sin exigir header ni consultar el repositorio.
- `JwtOrApiKeyAuthenticationIntegrationTest`: iniciar la cadena real con MockMvc y H2; cubrir todos los criterios, incluida la precedencia de Bearer inválido, regresión de API key, rutas públicas y flujo registro → login → `GET /players`. El catálogo vacío permite obtener `200 []` sin fuentes externas.
- `PlayerOpenApiTest`: ampliar la verificación existente para demostrar que `bearerAuth` y `apiKeyAuth` aparecen como alternativas separadas en GET y POST de jugadores sin depender del orden.

### 5. Documentación y cierre

Agregar en la sección Backend de `README.md` los pasos de Swagger: obtener JWT mediante login, abrir **Authorize**, cargarlo en `bearerAuth` y usarlo sin API key; mantener la API key como alternativa programática. Ejecutar `./mvnw verify`; luego validar GitHub Actions y el Quality Gate de SonarCloud sin issues nuevos.
