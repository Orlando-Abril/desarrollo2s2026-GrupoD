# Quickstart: validar portfolio e historial de transacciones

Guía de validación para la implementación de `specs/028-portfolio`. El contrato esperado está en [contracts/user-market-api.yaml](contracts/user-market-api.yaml) y los cálculos en [data-model.md](data-model.md).

## Prerrequisitos

- Java 17.
- Docker disponible sólo para los tests E2E/PostgreSQL que ya lo requieran; la suite normal usa H2.
- Ejecutar desde `backend/`.
- No hace falta Redis para los tests unitarios de esta feature; las lecturas financieras no se cachean.

## 1. Compilar y ejecutar la suite

PowerShell:

```powershell
cd backend
.\mvnw.cmd test
```

Resultado esperado: build `SUCCESS`, incluidos los tests existentes y los nuevos de portfolio, historial, `/auth/me` y OpenAPI.

## 2. Ejecutar sólo las pruebas de la feature

```powershell
.\mvnw.cmd '-Dtest=PortfolioServiceTest,PortfolioQueryTest,UserMarketControllerTest,AuthControllerTest,UserMarketOpenApiTest' test
```

Resultado esperado:

- promedio ponderado correcto con compras a precios distintos;
- venta parcial conserva el promedio;
- cierre y recompra reinician el costo;
- posición del superusuario parte de 1.0000;
- endpoints devuelven 200, 400, 401, 403 y 404 según el caso;
- el historial pagina sin repetir ni omitir trades;
- `/auth/me` devuelve 200 con los cinco campos y 401 sin credenciales;
- `/v3/api-docs` publica los tres contratos y ambos esquemas de seguridad.
- una inconsistencia de `marketValue` o trades/holding devuelve 500 `portfolio_inconsistent` sin detalles internos.

## 3. Escenario matemático mínimo

Preparar un usuario con estos trades, en orden:

1. Compra 10 tokens a 1.0000.
2. Compra 5 tokens a 1.2000.
3. Vende 6 tokens.

Con `marketValue = 1.3000`, el portfolio debe mostrar:

| Campo | Esperado |
|-------|----------|
| quantity | 9 |
| averagePurchasePrice | 1.0667 |
| costBasis | 9.6000 |
| currentValue | 11.7000 |
| profitLoss | 2.1000 |

Después de vender los 9 restantes, la posición desaparece. Si luego compra 2 a 1.5000, reaparece con promedio 1.5000 y costo 3.0000.

## 4. Validar seguridad con MockMvc

La clase `UserMarketControllerTest` debe probar ambos endpoints `/users/{id}/...` con la misma matriz:

| Actor | Objetivo | Resultado |
|-------|----------|-----------|
| Sin credencial | cualquiera | 401 |
| USER | su id | 200 |
| USER | otro id existente | 403 |
| USER | otro id inexistente | 403 |
| ADMIN | id existente | 200 |
| ADMIN | id inexistente | 404 |

Agregar además:

- id inválido, `page=-1`, `size=0` y `size=101` → 400;
- API key con dueño y JWT → aceptados;
- API key de servicio sin dueño → acceso ADMIN a `/users/{id}/...`;
- `/auth/me` sin credencial → 401;
- `/auth/me` con JWT o API key con dueño → 200;
- `/auth/me` con API key sin dueño → 401.

## 5. Validar paginación estable

Mantener un caso rápido con 25 trades y agregar el criterio completo con 1.000 trades del mismo usuario, incluidos empates de `executedAt`.

```text
GET /users/{id}/transactions
GET /users/{id}/transactions?page=1&size=20
```

Esperado:

- caso rápido: página 0 con 20 elementos y página 1 con 5;
- caso completo: recorrer las 50 páginas de 20 elementos, con `totalElements = 1000` y `totalPages = 50`;
- orden `executedAt DESC, id DESC`;
- la unión de ids del caso completo tiene exactamente 1.000 ids únicos;
- una página posterior devuelve `items: []` y metadatos reales.

## 6. Validar ausencia de N+1

`PortfolioQueryTest` debe crear varias tenencias y trades de jugadores distintos y comprobar que:

- `findPortfolioRows` entrega id, nombre y `marketValue` de todos los jugadores en una consulta;
- `findCostBasisRows` entrega todos los eventos escalares en una consulta y en orden ascendente;
- `findTransactionRows` entrega la página con jugador incluido y un `countQuery`, sin consultas por fila;
- aumentar de 1 a 20 posiciones no aumenta el número de statements de lectura por posición.

Puede verificarse con estadísticas de Hibernate habilitadas sólo en el test o con un inspector de statements. No se debe cambiar la configuración global para hacerlo.

“Consulta agregada” significa una cantidad constante de lecturas consolidadas. Medir por separado dueño, ADMIN y API key de servicio: cada modo puede tener un presupuesto fijo distinto por la resolución de actor/objetivo, pero aumentar de 1 a 20 posiciones no debe agregar sentencias por fila.

## 7. Validar OpenAPI

Con la aplicación de test levantada, consultar:

```text
GET /v3/api-docs
```

Comprobar:

- rutas `/users/{id}/portfolio`, `/users/{id}/transactions` y `/auth/me`;
- `bearerAuth` y `apiKeyAuth` como alternativas;
- defaults y límites de `page`/`size`;
- esquemas requeridos de portfolio, posición, transacción, página y `MeResponse`;
- respuestas 200/400/401/403/404 según [el contrato](contracts/user-market-api.yaml);
- header `X-Correlation-ID` en respuestas exitosas.

## 8. Escenarios constitucionales y concurrencia

Ejecutar pruebas automatizadas que demuestren:

- 4 usuarios operando sobre 5 jugadores, con compras, ventas parciales, cierre/recompra y portfolios calculados manualmente;
- portfolio, historial y `/auth/me` responden con Football-Data y WhoScored caídos;
- los tres GET no cambian saldo, holdings, órdenes, trades ni auditorías;
- `PortfolioConsistencyE2ETest`, sobre PostgreSQL real y con barreras entre transacciones, confirma un calce entre las consultas del portfolio y demuestra que la respuesta es íntegramente anterior o posterior, nunca una mezcla, mediante aislamiento `REPEATABLE_READ`.

## 9. Rendimiento reproducible

Usar PostgreSQL con el perfil de integración del proyecto en CI:

1. Crear una cuenta con 10.000 trades.
2. Ejecutar una llamada de calentamiento.
3. Medir 5 ejecuciones de portfolio y 5 de historial.
4. Exigir menos de 2 segundos en cada ejecución.
5. Confirmar que la cantidad de sentencias no crece con la cantidad de posiciones.

La clase debe terminar en `Test` para que `mvn test` la ejecute: `UserMarketPerformanceTest`.

## 10. Verificación de no regresión

Confirmar que:

- registro y login mantienen exactamente sus rutas y respuestas;
- ninguna consulta modifica `users.balance`, `holdings`, `orders`, `trades` o `audit_logs`;
- no se realizan llamadas a Football-Data, WhoScored ni procesos de recálculo;
- no se agrega Redis al flujo del portfolio;
- `git diff` muestra en `AuthController` únicamente el nuevo método `/me` y sus imports/documentación asociados.
