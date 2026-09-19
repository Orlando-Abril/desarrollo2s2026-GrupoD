# Quickstart: Validar Registro e Inicio de Sesión de Usuario

## Prerrequisitos

- Java 17 y Maven (usa el wrapper del proyecto).
- Variables de entorno para levantar el backend completo: `DB_PASSWORD` (Postgres) y `JWT_SECRET`
  (ya requeridas desde la feature de seguridad transversal).
- No hace falta el frontend corriendo; se valida con `curl`/Swagger UI y con los tests
  automatizados.

## 1. Ejecutar el suite de tests de la feature

```powershell
cd backend
mvn -Dtest=AuthServiceTest,AuthControllerTest,ApiKeyHasherTest test
```

**Resultado esperado**: todos los tests en verde (ver `contracts/auth-api.md` para el contrato
exacto que verifican):

- `AuthServiceTest` (Mockito puro, sin contexto de Spring): alta exitosa (genera hash de password,
  genera y hashea la `ApiKey`, deja el balance default), username duplicado, email duplicado,
  login correcto (devuelve JWT), login con password incorrecto y login con username inexistente
  (ambos con el mismo error).
- `AuthControllerTest` (`@SpringBootTest` + `@AutoConfigureMockMvc` + H2 real, mismo estilo que
  `ApiKeyAuthFilterTest`): `POST /auth/register` exitoso devuelve 201 con la `ApiKey` en claro,
  duplicar username/email devuelve 409, `POST /auth/login` exitoso devuelve 200 con un JWT válido,
  y login con credenciales inválidas devuelve 401.
- `ApiKeyHasherTest`: el mismo `rawValue` siempre produce el mismo hash hexadecimal SHA-256
  (necesario para que `ApiKeyAuthFilter` pueda encontrarlo luego).

## 2. Ejecutar el build completo (regresión)

```powershell
cd backend
mvn test
```

**Resultado esperado**: `BUILD SUCCESS`, sin romper los tests ya existentes de seguridad
transversal (`SecurityConfigTest`, `JwtUtilTest`, `ApiKeyAuthFilterTest` — este último debe seguir
en verde tras refactorizar su hashing hacia `ApiKeyHasher`).

## 3. Verificación manual end-to-end con `curl`

Con el backend levantado (`mvn spring-boot:run` desde `backend/`, con `DB_PASSWORD` y
`JWT_SECRET` configuradas):

```powershell
# 1) Registro: crea la cuenta y devuelve la ApiKey en claro (una única vez)
curl -i -X POST http://localhost:8080/auth/register `
  -H "Content-Type: application/json" `
  -d '{"username":"abril","email":"abril@example.com","password":"unPasswordSeguro123"}'

# 2) Repetir el mismo registro: debe responder 409 (username y email ya existen)
curl -i -X POST http://localhost:8080/auth/register `
  -H "Content-Type: application/json" `
  -d '{"username":"abril","email":"abril@example.com","password":"unPasswordSeguro123"}'

# 3) Login correcto: devuelve un JWT
curl -i -X POST http://localhost:8080/auth/login `
  -H "Content-Type: application/json" `
  -d '{"username":"abril","password":"unPasswordSeguro123"}'

# 4) Login con password incorrecto: 401 con el mismo mensaje genérico que un username inexistente
curl -i -X POST http://localhost:8080/auth/login `
  -H "Content-Type: application/json" `
  -d '{"username":"abril","password":"password-incorrecto"}'

# 5) Usar el JWT del paso 3 contra cualquier endpoint protegido existente
curl -i http://localhost:8080/v3/api-docs -H "Authorization: Bearer <token del paso 3>"
```

**Resultado esperado**: 201 → 409 → 200 (con `token`) → 401 → 200. La `ApiKey` devuelta en el
paso 1 no vuelve a aparecer en ninguna respuesta posterior.

## 4. Verificar la documentación en Swagger UI

Con el backend levantado, abrir `http://localhost:8080/swagger-ui/index.html` y confirmar que el
tag **Auth** lista `POST /auth/register` y `POST /auth/login`, cada uno con:

- El schema completo del request (`RegisterRequest`/`LoginRequest`, incluyendo las reglas de
  Bean Validation reflejadas por springdoc).
- El schema del response exitoso (`RegisterResponse`/`LoginResponse`).
- Cada código de error documentado (400, 401 y/o 409 según el endpoint), con su shape
  `{"error": "...", "message": "..."}`.

Esto valida directamente FR-012/SC-006 de `spec.md` (contrato consultable sin leer el código).

## 5. Verificar CI y SonarCloud

Al abrir el PR contra `main`, confirmar que el job de GitHub Actions termina en `SUCCESS` y que
SonarCloud pasa el Quality Gate, según la Definition of Done de la constitution (§7). Prestar
atención especial a que no aparezca ningún hallazgo de "hardcoded secret" o "sensitive data
exposure" sobre el password o la `ApiKey` cruda (ver `research.md` §5).

## Referencias

- Contrato REST completo: [contracts/auth-api.md](./contracts/auth-api.md)
- Modelo de datos y DTOs: [data-model.md](./data-model.md)
- Decisiones técnicas y su justificación: [research.md](./research.md)
