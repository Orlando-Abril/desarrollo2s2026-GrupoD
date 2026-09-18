# Quickstart: Validar la Infraestructura de Seguridad

Esta feature no agrega pantallas ni endpoints de negocio, así que la validación end-to-end es a
nivel de tests automatizados (la forma de prueba correcta para una pieza de infraestructura) más
una verificación manual mínima de las únicas rutas HTTP reales que quedan expuestas
(documentación Swagger, que ya existe automáticamente una vez agregada la configuración).

## Prerrequisitos

- Java 17 y Maven configurados (usa el wrapper del proyecto si existe).
- Variables de entorno para levantar el backend completo: `DB_PASSWORD` (Postgres, ya requerida
  hoy) y, a partir de esta feature, `JWT_SECRET` (ver `data-model.md` → nueva configuración).
- No hace falta el frontend corriendo para validar esta feature (se valida CORS con `curl`/tests,
  no navegando la SPA).

## 1. Ejecutar el suite de tests de la feature

```powershell
cd backend
mvn -Dtest=SecurityConfigTest,JwtUtilTest,ApiKeyAuthFilterTest test
```

**Resultado esperado**: todos los tests en verde. Estos tests son la fuente de verdad del
comportamiento (ver `contracts/security-infrastructure.md`):

- `SecurityConfigTest` (MockMvc): 401 en una ruta protegida sin credenciales, 200 en `/v3/api-docs`
  sin credenciales, y el header `Access-Control-Allow-Origin` presente sólo para
  `http://localhost:5173`.
- `JwtUtilTest`: generación de un token a partir de un username, validación exitosa antes de
  expirar, extracción del username original, y rechazo de un token expirado o con firma alterada.
- `ApiKeyAuthFilterTest` (MockMvc): 401 sin header `X-API-KEY`, 401 con un valor que no matchea
  ningún hash, 401 con una key inactiva, y continuación normal de la request con una key activa.

## 2. Ejecutar el build completo (regresión)

```powershell
cd backend
mvn test
```

**Resultado esperado**: `BUILD SUCCESS`, sin romper los tests ya existentes de `model` y
`repository` (p.ej. `ApiKeyTest`, `ApiKeyRepositoryTest`).

## 3. Verificación manual de rutas públicas y CORS (smoke-test adicional)

Los escenarios de esta sección ya están cubiertos automáticamente por `SecurityConfigTest`
(paso 1). Este chequeo manual es un smoke-test opcional post-deploy, no la fuente de verdad.

Con el backend levantado (`mvn spring-boot:run` desde `backend/`, con `DB_PASSWORD` y
`JWT_SECRET` configuradas):

```powershell
# Ruta pública (documentación): debe responder 200 sin ninguna credencial
curl -i http://localhost:8080/v3/api-docs

# CORS: la respuesta debe incluir Access-Control-Allow-Origin: http://localhost:5173
curl -i -H "Origin: http://localhost:5173" -X OPTIONS `
  -H "Access-Control-Request-Method: GET" http://localhost:8080/v3/api-docs

# CORS desde un origen no autorizado: NO debe incluir Access-Control-Allow-Origin
curl -i -H "Origin: http://evil.example" -X OPTIONS `
  -H "Access-Control-Request-Method: GET" http://localhost:8080/v3/api-docs
```

**Resultado esperado**: el primer `curl` responde 200. El segundo incluye el header
`Access-Control-Allow-Origin: http://localhost:5173`. El tercero no lo incluye (o Spring
Security responde 403 en el preflight, según la política configurada).

> Nota: como esta feature no agrega controllers de negocio, no hay todavía una ruta "protegida"
> real para probar con `curl` end-to-end; ese escenario queda cubierto por `ApiKeyAuthFilterTest`
> (paso 1) hasta que la feature de login agregue su primer endpoint protegido.

## 4. Verificar CI y SonarCloud (antes de dar la feature por terminada)

Al abrir el PR contra `main`, confirmar en GitHub Actions que el job `build` de
`.github/workflows/ci.yml` termina en `SUCCESS` y que el análisis de SonarCloud pasa el Quality
Gate (menos de 10 issues), según la Definition of Done de la constitution (§7, puntos 2-3). Este
workflow ya corre automáticamente sobre cualquier cambio en `backend/**`; no requiere configuración
adicional para esta feature.

## Referencias

- Contratos de comportamiento: [contracts/security-infrastructure.md](./contracts/security-infrastructure.md)
- Modelo de datos y configuración nueva: [data-model.md](./data-model.md)
- Decisiones técnicas y su justificación: [research.md](./research.md)
