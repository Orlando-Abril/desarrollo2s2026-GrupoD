# Implementation Plan: Infraestructura Transversal de Seguridad

**Branch**: `001-seguridad-infraestructura` | **Date**: 2026-09-16 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-seguridad-infraestructura/spec.md`

**Note**: This template is filled in by the `$speckit-plan` command; its definition describes the execution workflow.

## Summary

Dejar lista, dentro de `backend/`, la infraestructura transversal de seguridad que van a consumir
las features de negocio de esta entrega (registro/login, catálogo, trading): un
`SecurityFilterChain` que distingue rutas públicas de protegidas y habilita CORS sólo para el
frontend; una utilidad `JwtUtil` (sobre `jjwt`) para emitir y validar tokens a partir de un
username, sin lógica de usuarios; un filtro `ApiKeyAuthFilter` (`OncePerRequestFilter`) que
autoriza requests de servicio contra el hash ya persistido en `api_keys`; un bean
`BCryptPasswordEncoder`; y una configuración base de OpenAPI/Swagger con los esquemas de
seguridad `bearerAuth` y `apiKeyAuth`. No se agregan controllers de negocio ni se persiste
esquema nuevo.

## Technical Context

**Language/Version**: Java 17, Spring Boot 4.1.1 (Maven), módulo `backend/`

**Primary Dependencies**: `spring-boot-starter-security`, `spring-boot-starter-webmvc`,
`jjwt-api`/`jjwt-impl`/`jjwt-jackson` 0.13.0, `springdoc-openapi-starter-webmvc-ui` 3.1.1, Lombok
(todas ya declaradas en `backend/pom.xml`, no se agregan dependencias nuevas)

**Storage**: PostgreSQL vía Spring Data JPA. Esta feature no crea entidades ni tablas nuevas:
sólo lee la entidad `ApiKey` / tabla `api_keys` ya existente (`keyHash`, `active`, `owner`) a
través del `ApiKeyRepository.findByKeyHash` ya existente.

**Testing**: JUnit 5 + AssertJ (estilo ya usado en `ApiKeyTest`, `ApiKeyRepositoryTest`), MockMvc
y `spring-security-test` (ya en el pom) para `SecurityConfig`/CORS y el filtro de ApiKey.

**Target Platform**: API REST backend (Spring Boot) consumida por una SPA (Vite) corriendo en
`http://localhost:5173` durante desarrollo.

**Project Type**: Web application (monorepo `backend/` + `frontend/`). Esta feature sólo toca
`backend/`.

**Performance Goals**: Sin objetivos de performance específicos; el filtro de ApiKey debe agregar
una sobrecarga despreciable por request (una consulta indexada por `key_hash` como máximo).

**Constraints**: No debe exponerse ningún endpoint de negocio; CORS restringido exclusivamente a
`http://localhost:5173`; el secreto de firma JWT y su expiración se configuran vía
`application.properties`/variables de entorno, nunca hardcodeados en el código.

**Scale/Scope**: Un módulo de configuración transversal (~5-6 clases nuevas) en
`backend/src/main/java/com/example/demo/{config,security}`; no agrega pantallas ni endpoints.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio de la Constitution | Aplica | Evaluación |
|---|---|---|
| 2.1 Backend en capas estricto (Controllers/Services/Repositories/Adapters) | Sí | Esta feature no agrega lógica de negocio en ninguna de esas capas; introduce un paquete transversal `config`/`security` (patrón estándar de Spring) que no compite ni se mezcla con Controllers/Services/Repositories/Adapters. No es una violación: es infraestructura de la que esas capas dependen. |
| 4.1 Autenticación y Autorización (JWT + API Keys) | Sí | Esta feature es exactamente el prerequisito de este principio: deja lista la emisión/validación de JWT y la verificación de API Keys que las features de registro/login consumirán. Cumple de forma directa. |
| 6.1 OpenAPI / Swagger con esquemas Bearer y ApiKey | Sí | Esta feature entrega literalmente ese bean de configuración OpenAPI. Cumple de forma directa. |
| 3.1 CI en GitHub Actions en SUCCESS | Sí (transversal) | Los tests nuevos (JwtUtil, ApiKeyAuthFilter) deben correr en el build Maven existente; no se agrega pipeline nuevo. |
| 3.2 SonarCloud (Quality Gate, <10 issues) | Sí (transversal) | El secreto JWT y las comparaciones de credenciales deben evitar patrones marcados por Sonar (secretos hardcodeados, comparación de strings sensible a timing). Se resuelve en Phase 0 (research). |
| 5.1 Catálogo y endpoints mínimos obligatorios | No | Esos endpoints pertenecen a features de negocio futuras; esta feature explícitamente no los implementa. |
| 4.2 Auditoría inmutable de transacciones | No | No hay transacciones financieras en esta feature. |

**Resultado**: PASS. No hay violaciones que requieran justificación en Complexity Tracking.

**Re-check post Phase 1 (diseño)**: Con `research.md`, `data-model.md`, `contracts/` y
`quickstart.md` ya definidos, ninguna decisión de diseño introdujo lógica de negocio en
`config/`/`security/`, ni tocó las capas Controllers/Services/Repositories/Adapters, ni agregó
endpoints fuera de alcance. El punto 3.2 (SonarCloud) queda resuelto en `research.md` §1-2: el
secreto JWT se externaliza a configuración y la verificación de ApiKey usa comparación por hash
indexado en vez de comparación insegura de strings en texto plano. **Resultado**: PASS, sin
cambios respecto del check inicial.

## Project Structure

### Documentation (this feature)

```text
specs/001-seguridad-infraestructura/
├── plan.md              # This file ($speckit-plan command output)
├── research.md          # Phase 0 output ($speckit-plan command)
├── data-model.md        # Phase 1 output ($speckit-plan command)
├── quickstart.md        # Phase 1 output ($speckit-plan command)
├── contracts/           # Phase 1 output ($speckit-plan command)
└── tasks.md             # Phase 2 output ($speckit-tasks command - NOT created by $speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/example/demo/
│   ├── config/
│   │   ├── SecurityConfig.java        # SecurityFilterChain, CORS, rutas públicas/protegidas
│   │   ├── OpenApiConfig.java         # Bean OpenAPI: info + esquemas bearerAuth/apiKeyAuth
│   │   └── PasswordEncoderConfig.java # Bean BCryptPasswordEncoder
│   ├── security/
│   │   ├── JwtUtil.java               # generar/parsear/validar JWT (jjwt)
│   │   └── ApiKeyAuthFilter.java      # OncePerRequestFilter: valida X-API-KEY por hash
│   ├── model/                         # ApiKey, User (ya existen, sin cambios)
│   └── repository/                    # ApiKeyRepository (ya existe, sin cambios)
│
└── src/test/java/com/example/demo/
    ├── config/
    │   └── SecurityConfigTest.java    # MockMvc: público/protegido (401) + CORS por origen
    └── security/
        ├── JwtUtilTest.java           # generación / validación / expiración
        └── ApiKeyAuthFilterTest.java  # MockMvc: header ausente/inválido/inactivo/válido

frontend/                              # No se modifica en esta feature
```

**Structure Decision**: Monorepo `backend/` + `frontend/` ya existente (Opción "Web
application"). Esta feature sólo agrega dos paquetes nuevos y transversales dentro de
`backend/src/main/java/com/example/demo/`: `config/` (composición de Spring Security y OpenAPI)
y `security/` (utilidades de JWT y el filtro de ApiKey), más sus tests espejo en
`backend/src/test/java/com/example/demo/security/`. No se toca `frontend/` ni se agregan
paquetes `controller/` o `service/` porque esta feature no expone endpoints de negocio.

## Complexity Tracking

*No aplica: el Constitution Check no reportó violaciones que requieran justificación.*
