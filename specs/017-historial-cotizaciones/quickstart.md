# Quickstart: validar el historial de cotizaciones

**Feature**: 017-historial-cotizaciones  
**Contract**: [contracts/quote-history-api.yaml](contracts/quote-history-api.yaml)  
**Data model**: [data-model.md](data-model.md)

Esta guía valida la feature una vez implementada. No requiere fuentes externas; las pruebas automatizadas usan H2 y datos persistidos por el propio test.

## 1. Prerequisites

- Java 17.
- Rama con las features 013 y 016 integradas.
- Desde la raíz del repositorio, entrar a `backend`.
- Para la comprobación manual: PostgreSQL y Redis configurados como en el README, aplicación iniciada y una API key o bearer token válido.

## 2. Automated validation

En PowerShell:

```powershell
cd backend
.\mvnw.cmd test '-Dtest=QuoteHistoryControllerTest,QuoteOpenApiTest,QuoteControllerTest'
.\mvnw.cmd test '-DexcludedGroups=e2e'
# Ejecutar con la infraestructura E2E existente de la feature 016:
.\mvnw.cmd test '-Dtest=ExternalSourcesDownE2ETest'
```

Resultados esperados:

- `QuoteHistoryControllerTest` prueba filtros, UTC, `at`, DTO, 400, 401, 404, JWT/API key de `Role.USER` y coexistencia con rutas de `PlayerController` usando exclusivamente cotizaciones guardadas manualmente; su fixture temporal incluye jugadores de las cinco ligas y verifica cero interacciones con adaptadores externos.
- `QuoteOpenApiTest` encuentra ambos endpoints de cotizaciones; el GET publica cinco parámetros, lista/objeto, 200/400/401/404 y ambos esquemas de seguridad.
- `QuoteControllerTest` conserva verde `POST /quotes/recalculate` después del cambio de mapping.
- `ExternalSourcesDownE2ETest`, ya creado por 016, conserva verde la resiliencia ante caída de Football-Data.org y WhoScored; no se crea ni configura un E2E redundante para 017.
- La suite normal finaliza sin fallas. No se agrega otro `@DataJpaTest`; la cobertura de 013 permanece verde.

## 3. Prepare manual data

Usar un jugador existente y ejecutar previamente el recálculo de 016, o insertar cotizaciones de prueba siguiendo el modelo descrito en [data-model.md](data-model.md). Anotar:

- `PLAYER_ID`: jugador con al menos tres cotizaciones OFFICIAL en fechas distintas.
- `EMPTY_PLAYER_ID`: jugador existente sin cotizaciones para el rango elegido.
- una fecha anterior a la primera cotización.

Definir la credencial elegida:

```powershell
$apiBase = 'http://localhost:8080'
$playerId = 42
$headers = @{ 'X-API-KEY' = '<API_KEY>' }
```

Para JWT, reemplazar `$headers` por:

```powershell
$headers = @{ Authorization = 'Bearer <JWT>' }
```

## 4. History scenarios

Historial de la estrategia activa:

```powershell
Invoke-RestMethod -Headers $headers -Uri "$apiBase/players/$playerId/quotes"
```

Historial OFFICIAL dentro de un rango:

```powershell
Invoke-RestMethod -Headers $headers -Uri "$apiBase/players/$playerId/quotes?strategy=OFFICIAL&from=2026-10-01&to=2026-10-20"
```

Validar:

- respuesta 200 y array JSON;
- orden ascendente por `calculatedAt`;
- sólo la estrategia y los días solicitados;
- `value`, `score`, `status`, `strategy`, `version` y `calculatedAt` en cada elemento;
- jugador existente sin coincidencias → `[]`.

Repetir con sólo `from`, sólo `to` y `from=to` para confirmar intervalos abiertos y día completo.

## 5. Effective-date scenario

```powershell
Invoke-RestMethod -Headers $headers -Uri "$apiBase/players/$playerId/quotes?strategy=OFFICIAL&at=2026-10-10"
```

Validar respuesta 200 como objeto único y que `calculatedAt` corresponde a la última cotización anterior al `2026-10-11T00:00:00Z`.

Consultar una fecha anterior a la primera cotización y validar 404:

```powershell
try {
    Invoke-RestMethod -Headers $headers -Uri "$apiBase/players/$playerId/quotes?strategy=OFFICIAL&at=2026-01-01"
} catch {
    $_.Exception.Response.StatusCode.value__
}
```

El body debe tener `error: quote_not_found`.

## 6. Validation and security

Comprobar 400 para:

- `/players/0/quotes` y `/players/-1/quotes`
- `?from=2026-10-20&to=2026-10-01`
- `?at=2026-10-10&from=2026-10-01`
- `?from=2026-02-30`
- `?from=2026-1-1` y `?from=2025-02-29`; confirmar además que `?from=2024-02-29` es válido
- `?strategy=` y `?strategy=official`
- `?strategy=FOO`

Los cuerpos usan `{ "error": "...", "message": "..." }`; la estrategia desconocida usa `unknown_strategy` y las demás validaciones `validation_error`.

Sin `$headers`, una URL con id positivo inexistente debe responder 401 y el body no debe contener `player_not_found`. Un id positivo inexistente con credenciales válidas debe responder 404 `player_not_found`. Confirmar 200 para la misma consulta con JWT de `Role.USER` y con API key de `Role.USER`; los casos internos de bearer vencido y API key inválida/revocada permanecen cubiertos por los tests existentes de sus filtros.

## 7. Route and OpenAPI regression

Con las mismas credenciales, confirmar:

```powershell
Invoke-RestMethod -Headers $headers -Uri "$apiBase/players"
Invoke-RestMethod -Headers $headers -Uri "$apiBase/players/$playerId/quotes"
```

La primera respuesta sigue siendo el catálogo de `PlayerController`; la segunda es el historial de `QuoteController`.

Abrir `/swagger-ui/index.html` o inspeccionar `/v3/api-docs` y verificar:

- `GET /players/{id}/quotes` bajo el tag Quotes;
- parámetros `id`, `strategy`, `from`, `to`, `at`;
- respuesta 200 lista/objeto y errores 400/401/404;
- `bearerAuth` y `apiKeyAuth` como alternativas;
- `POST /quotes/recalculate` permanece con su path y contrato anteriores.

## 8. Performance protocol

Con exactamente 2.500 cotizaciones persistidas para un jugador y estrategia, usar el mismo host de desarrollo y sin carga concurrente. Ejecutar 5 consultas HTTP completas de warmup que no se miden y, a continuación, 20 consultas HTTP secuenciales midiendo el tiempo completo de respuesta. Deben cumplirse simultáneamente p95 menor a 1 segundo y al menos 19 de 20 mediciones menores a 1 segundo.

Registrar en `performance-results.md` el commit, fecha, entorno, dataset, las 20 duraciones, el cálculo de p95 y el resultado pass/fail. Si no se cumple, agregar el volumen y el plan de ejecución antes de proponer cambios; el índice esperado es `idx_player_quotes_player_strategy_time`.

## 9. Protected-file review

Antes de cerrar la implementación:

```powershell
git diff --name-only
```

Confirmar que `PlayerController.java`, `GlobalExceptionHandler.java`, `PlayerRepository.java`, `PlayerQuoteRepository.java` y demás archivos de Entrega 1 no aparecen modificados.

## 10. CI and SonarCloud

Sobre el mismo commit final, comprobar GitHub Actions en `SUCCESS` y SonarCloud con Quality Gate `PASSED`, cero vulnerabilidades y menos de 10 issues de severidad menor. Registrar enlaces o identificadores de ejecución y métricas en `validation-results.md`.
