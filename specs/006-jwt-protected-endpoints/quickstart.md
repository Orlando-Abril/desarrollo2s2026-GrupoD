# Quickstart: validar JWT o API key en rutas protegidas

Guía de validación para [spec.md](./spec.md). Las reglas completas están en [contracts/protected-authentication.md](./contracts/protected-authentication.md) y el estado transitorio en [data-model.md](./data-model.md).

## Prerrequisitos

- JDK 17 o compatible.
- PostgreSQL y Redis disponibles para la ejecución manual.
- Variables `DB_PASSWORD`, `JWT_SECRET` y `FOOTBALL_DATA_TOKEN` configuradas.
- En Windows se usa `mvnw.cmd`; en Linux, `./mvnw`.

## 1. Suite automática

Desde `backend/`:

```powershell
.\mvnw.cmd verify
```

En Linux/CI:

```bash
./mvnw verify
```

Resultado esperado: `BUILD SUCCESS`, incluidos los tests del filtro JWT, la regresión del filtro de API key, todos los escenarios integrados y el contrato OpenAPI. `pom.xml` no incorpora dependencias nuevas.

## 2. Arranque local

Iniciar `DemoApplication` desde IntelliJ con las variables requeridas o, desde `backend/`:

```powershell
.\mvnw.cmd spring-boot:run
```

Swagger debe quedar disponible en:

```text
http://localhost:8080/swagger-ui/index.html
```

## 3. Flujo web con JWT

1. En Swagger, ejecutar `POST /auth/register` con un username, email y password nuevos.
2. Confirmar `201` y conservar la API key sólo para la prueba de compatibilidad.
3. Ejecutar `POST /auth/login` con el mismo username y password.
4. Confirmar `200` y copiar el campo `token`.
5. Pulsar **Authorize**, cargar el token en `bearerAuth` y no completar `apiKeyAuth`.
6. Ejecutar `GET /players`.

Resultado esperado: `200`. El usuario no necesita usar la API key para consumir la ruta protegida.

## 4. Rechazo definitivo de Bearer inválido

Configurar un valor mal formado, por ejemplo `abc`, en `bearerAuth` y ejecutar `GET /players`.

Resultado esperado:

```json
{"error":"unauthorized","message":"Token inválido o vencido"}
```

Repetir enviando además una API key válida. El resultado debe seguir siendo el mismo `401`: no existe fallback cuando se presentó Bearer. Los casos vencido y firma inválida quedan cubiertos determinísticamente por la suite automática.

## 5. Regresión de API key

1. Quitar `bearerAuth`.
2. Autorizar únicamente `apiKeyAuth` con la API key devuelta durante el registro.
3. Ejecutar `GET /players`.

Resultado esperado: `200`, igual que antes de la feature.

Sin ninguna credencial, el resultado esperado continúa siendo:

```json
{"error":"unauthorized","message":"Falta el header X-API-KEY"}
```

El caso `Authorization: Basic xxx` junto con una API key válida se valida automáticamente con MockMvc y debe responder `200`.

## 6. Rutas públicas

Cerrar las autorizaciones y repetir `POST /auth/register` o `POST /auth/login` con datos válidos.

Resultado esperado: siguen siendo públicas y mantienen sus respuestas actuales. Un Bearer inválido tampoco debe interceptarlas.

## 7. Contrato OpenAPI

Consultar:

```text
http://localhost:8080/v3/api-docs
```

Para cada operación protegida de jugadores, `security` debe contener dos objetos separados: uno para `bearerAuth` y otro para `apiKeyAuth`. Registro y login no deben declarar requisitos de seguridad.

## 8. Cierre en CI y SonarCloud

Después de subir la implementación:

1. Confirmar que el workflow backend de GitHub Actions termina en `SUCCESS`.
2. Confirmar que SonarCloud mantiene el Quality Gate aprobado y no registra issues nuevos atribuibles a la feature.
3. Confirmar que sólo cambiaron archivos bajo `backend/`, `specs/006-jwt-protected-endpoints/` y el `README.md` raíz.

## Resultado de validación local

Validado el 2026-09-26 en Windows con JDK 22, Maven Wrapper y Docker Desktop:

```powershell
$null = chcp 65001
$env:JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8'
.\mvnw.cmd '-Dlogging.charset.console=UTF-8' verify
```

Resultado: `BUILD SUCCESS`; 219 tests ejecutados, 0 fallas, 0 errores y 0 omitidos.
La propiedad `logging.charset.console` evita que los tests preexistentes que capturan logs con
tildes fallen por la codificación de la consola de Windows.

La auditoría de alcance confirmó que `backend/pom.xml` no cambió, no se modificaron modelos,
repositories ni contratos anteriores, y los cambios están limitados a `backend/`, esta feature
y el `README.md` raíz. La verificación remota de GitHub Actions y SonarCloud queda pendiente
hasta publicar la rama.
