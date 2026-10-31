# Quickstart: validar el comparador de estrategias

Guía de validación. Contratos: [contracts/strategy-comparison-api.yaml](contracts/strategy-comparison-api.yaml). Modelo: [data-model.md](data-model.md).

## Prerrequisitos

- Backend levantado (`cd backend && ./mvnw spring-boot:run`) con PostgreSQL y Redis del entorno de desarrollo.
- Un token de ADMIN (`superuser`) y uno de USER (`POST /auth/login`), en `$ADMIN` y `$USER`.
- Al menos un recálculo completado (`POST /quotes/recalculate` como ADMIN), así existen cotizaciones OFFICIAL y MOMENTUM.

### Base existente: constraint del enum de auditoría (research R10)

Si `audit_logs` se creó antes de esta feature, verificar si existe un CHECK sobre `action`:

```sql
SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint
WHERE conrelid = 'audit_logs'::regclass AND contype = 'c';
```

Si aparece un `audit_logs_action_check` sin `ACTIVE_STRATEGY_CHANGED` y `MARKET_STRATEGY_APPLIED`, el `PUT /strategies/active` y el registro de la estrategia de mercado al final del recálculo fallarán al auditar. Hay que acordar con el equipo cómo regenerarlo antes de seguir. No se ejecuta nada automáticamente.

## Tests automáticos

```bash
cd backend
./mvnw test -Dtest='OpportunityCalculatorTest,PortfolioStrategyValuatorTest,ValuationStrategyCatalog*Test'
./mvnw test -Dtest='PlayerQuoteLatestForPlayersTest,QuoteRunActiveSnapshotTest,ActiveStrategyServiceConcurrencyTest'
./mvnw test -Dtest='OpportunityControllerTest,LatestQuotesControllerTest,UserMarketStrategyControllerTest,StrategyControllerTest,ActiveStrategyRecalculationTest,StrategyComparisonOpenApiTest'
./mvnw test -Dtest='ActiveStrategyE2ETest'   # e2e: requiere E2E_DB_URL/E2E_DB_PASSWORD de una base dedicada y Redis (ver specs/021 quickstart §2); "Skipped: 1" significa que no corrió
./mvnw verify   # suite completa: los tests de 016/018/028 deben seguir en verde sin cambios
```

Resultado esperado: todo en verde. En particular, `UserMarketControllerTest` (028) pasa sin modificar, lo que confirma FR-001 (portfolio idéntico sin `strategy`).

## Validación manual

1. **Estrategia activa** (US5): `GET /strategies/active` con `$USER` devuelve 200 con `active=OFFICIAL`, `alternative=MOMENTUM`, `marketStrategy=OFFICIAL` y `pendingRecalculation=false`. Sin token devuelve 401.
2. **Portfolio** (US1): con `GET /users/{id}/portfolio`, sin `strategy` la respuesta no trae ninguna clave `strategyValuation`. Con `?strategy=MOMENTUM` cada posición y los totales la traen, con `value = quantity × quotePrice`. Con `?strategy=FOO` devuelve 400 `unknown_strategy`. Con el id de otro usuario y `$USER` devuelve 403.
3. **Oportunidades** (US2): `GET /quotes/opportunities?limit=5` devuelve dos listas de hasta 5 elementos, sin jugadores con estado no calculado, ordenadas por `|differencePercent|`. `?league=XX` o `?limit=0` devuelven 400.
4. **Catálogo** (US3): tomar los ids de `GET /players?page=0&size=20` y pedir `GET /quotes/latest?playerIds=<ids>`. Debe devolver un item por id con el valor que da `GET /players/{id}/quotes?strategy=MOMENTUM` para la última fecha. Con 101 ids o con `playerIds=a` devuelve 400.
5. **Cambio de estrategia** (US4):
   - `PUT /strategies/active` con `{"strategy":"MOMENTUM"}` y `$USER` devuelve 403.
   - El mismo `PUT` con `$ADMIN` devuelve 200 con `changed=true` y `pendingRecalculation=true`, y `marketValue` de los jugadores no cambia.
   - Hay un registro `ACTIVE_STRATEGY_CHANGED` con `before`=OFFICIAL y `after`=MOMENTUM: `SELECT * FROM audit_logs WHERE action='ACTIVE_STRATEGY_CHANGED'`.
   - Repetir el `PUT` devuelve 200 con `changed=false`, sin un registro nuevo.
   - `POST /quotes/recalculate` informa `activeStrategy=MOMENTUM`. Después, `marketValue` es igual a la última MOMENTUM y `GET /strategies/active` muestra `pendingRecalculation=false`.
   - Reiniciar el backend con `VALUATION_ACTIVE_STRATEGY=OFFICIAL`: la activa sigue siendo MOMENTUM.
6. **OpenAPI** (US6): en `/swagger-ui.html` aparecen `/quotes/opportunities`, `/quotes/latest`, `GET` y `PUT /strategies/active`, y el parámetro `strategy` del portfolio, con sus respuestas.

## Volver al estado inicial

`PUT /strategies/active` con `{"strategy":"OFFICIAL"}` y un recálculo.
