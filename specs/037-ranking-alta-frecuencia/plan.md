# Implementation Plan: Ranking de alta frecuencia

**Branch**: `feature/ranking-alta-frecuencia` | **Date**: 2026-11-17 | **Spec**: [spec.md](spec.md)  
**Input**: Feature specification from `specs/037-ranking-alta-frecuencia/spec.md`

## Summary

Sacar la transacción del camino de cache hit en `QuoteQueryService.ranking`, después de la autenticación; mover la lectura persistida a `RankingLoader` con transacción de solo lectura; compartir el cálculo de misses concurrentes por clave; invalidar y precalentar en segundo plano la página que pide el frontend después de datos confirmados; publicar estadísticas y documentar tiempos HTTP completos. El contrato, la autenticación, el orden, el TTL de una hora y la paginación siguen iguales.

## Technical Context

**Language/Version**: Java 17, Spring Boot 4.1.1  
**Primary Dependencies**: Spring Cache, Spring Data Redis, Spring Data JPA, Spring Transactions, Spring Actuator, Micrometer Prometheus  
**Storage**: PostgreSQL para cotizaciones, Redis para el caché existente de ranking  
**Testing**: JUnit/Mockito y H2 en pruebas focalizadas; PostgreSQL y Redis en E2E; Awaitility ya es dependencia de test  
**Target Platform**: backend Spring Boot local y despliegue existente  
**Project Type**: servicio web REST en `backend/`  
**Performance Goals**: solicitud HTTP completa de primera página cacheada menor a 50 ms en local; 20 hilos de una misma instancia con la misma clave ausente producen una sola lectura de ranking  
**Constraints**: tras autenticar, hit en `QuoteQueryService.ranking` sin repositorios, transacción ni conexión de base atribuible al servicio; coordinación e invalidación por generación locales a una instancia; las consultas de autenticación siguen vigentes; fallback ante Redis caído; recarga después del commit y fuera del tiempo de respuesta; sin caché adicional de páginas en memoria, sin cambiar TTL ni paginación  
**Scale/Scope**: `GET /players/ranking` y los dos eventos de invalidación; cambios de código acotados por FR-014 actualizado

## Constitution Check

*GATE: evaluado antes de investigación y nuevamente tras el diseño.*

| Regla | Decisión y verificación | Estado |
|---|---|---|
| §2.1, capas estrictas | `QuoteQueryService`, `RankingLoader` y `RankingWarmer` permanecen en service; el acceso persistido sigue en repositories. No cambia el controller. | Pasa |
| §5.2, Redis e índices | Se usa el caché Redis existente, sin caché adicional de resultados ni cambio del TTL. El índice candidato queda condicionado a `EXPLAIN (ANALYZE, BUFFERS)` de la consulta real y comparación antes/después. | Pasa |
| §5.3, logs, correlación, health, latencia y errores | Se conservan logs JSON y filtro de correlación. Se habilitan estadísticas y se verifican hits/misses en Prometheus; quickstart mide el objetivo local de 50 ms. Errores de Redis y del precalentado quedan registrados sin afectar la respuesta principal. | Pasa |
| Seguridad y contrato | El endpoint conserva JWT/API key, incluidas sus consultas actuales a base, validación, respuesta y orden; no se modifican filtros, rutas públicas ni DTO. | Pasa |
| Alcance autorizado | El pedido de este turno amplía FR-014 para permitir los dos componentes nuevos, habilitación asíncrona y un índice sólo con evidencia. `CacheConfig` cambia para estadísticas; la coordinación de misses va en `QuoteQueryService`. | Pasa |

## Project Structure

### Documentation (this feature)

```text
specs/037-ranking-alta-frecuencia/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/
    └── ranking-api.md
```

### Source Code (repository root)

```text
backend/src/main/java/com/example/demo/
├── config/CacheConfig.java                 # estadísticas; sin cambiar TTL
├── service/valuation/QuoteQueryService.java # hit sin transacción, coordinación por clave
├── service/valuation/RankingLoader.java     # lectura transaccional de solo lectura
├── service/valuation/QuoteCacheEvictor.java # invalidación y disparo poscommit
└── service/valuation/RankingWarmer.java     # precalentado asíncrono
backend/src/test/java/com/example/demo/
├── service/valuation/                    # pruebas nuevas y regresión
└── e2e/                                  # medición real, si hay servicios de demo
```

**Structure Decision**: Mantener los paquetes por capa y el repositorio SQL actual. Si no existe `@EnableAsync`, habilitarlo en una configuración nueva de esta feature; no tocar configuración de entregas anteriores salvo `CacheConfig` para estadísticas. Un índice eventual requiere prueba previa y cambio mínimo de esquema permitido por la medición.

## Design

1. **Camino de lectura**: después de la autenticación, `ranking` deja de ser transaccional. Validar una estrategia explícita con el catálogo en memoria. Para la estrategia omitida, usar el alias lógico `active` sin llamar a `active()` antes del hit: hoy `active()` lee la base. Cada clave conserva estrategia/alias, liga, posición, página y tamaño, y agrega la generación capturada al entrar al servicio; formato `estrategia|liga|posición|página|tamaño|gen=<id-arranque>:<contador>`. Un ID de arranque evita reutilizar claves de una ejecución anterior. En el miss, `RankingLoader` resuelve la activa desde la fila persistida dentro de su transacción y devuelve el código efectivo junto con `RankingPageResponse`. La carga desde el alias publica esa misma página bajo las claves `active` y código explícito de la misma generación, incluso vacía, sin segunda consulta. No se promete equivalencia inversa cuando se carga primero la explícita.
2. **Miss simultáneo**: mantener una coordinación temporal por clave generacional en `QuoteQueryService`, retirada al completarse o fallar. Dentro de una instancia el líder ejecuta `Cache.get(key, Callable)` con el loader transaccional; los seguidores esperan el mismo resultado. No se promete coordinación entre réplicas. No se almacena una segunda copia de páginas después de la carga. Distinguir fallo del loader de fallo de Redis: un fallo de datos se propaga y libera esperas; un fallo de Redis activa la lectura desde base. Si Redis falla tras invocar el `Callable`, devolver su resultado ya calculado sin repetir la consulta.
3. **Generación e invalidación**: `QuoteQueryService` mantiene sólo un `AtomicLong` monotónico y el ID inmutable de arranque, sin páginas en memoria. Cada evento hace avanzar la generación antes de `cache.clear()`. Una carga que capturó la generación anterior sólo escribe en claves antiguas; al terminar compara su generación con la actual, elimina sus claves alias/explícita antiguas y no publica resultado vigente si quedó obsoleta. Comprobar antes y después de publicar la segunda clave para cubrir una invalidación intermedia. Una solicitud iniciada después del evento sólo consulta claves de la generación nueva. La garantía cubre una instancia.
4. **Precalentado**: `QuoteCacheEvictor` conserva la invalidación. Tras `QuoteRunCompletedEvent`, despachar el warmer después del commit si el evento se publicó dentro de una transacción, y también cuando se publica fuera de ella, como ocurre hoy. Tras `ActiveStrategyChangedEvent`, despacharlo después del commit. `RankingWarmer` se ejecuta vía proxy `@Async` e invoca `ranking(null, null, null, PageRequest.of(0,20))` tomando la generación actual al ejecutar; si otro evento la supera durante la carga, descarta la antigua y vuelve a cargar la vigente. Registrar errores sin propagarlos al recálculo o cambio.
5. **Métricas**: activar `enableStatistics()` del `RedisCacheManager` existente. Verificar en un scrape real `cache_gets_total{cache="quotes-ranking",result="hit|miss"}`; las estadísticas son locales a cada instancia. Mantener las métricas HTTP de latencia y errores de §5.3.
6. **Índice condicional**: ejecutar `EXPLAIN (ANALYZE, BUFFERS)` de la consulta real del ranking y su conteo sobre la base de demo. Evaluar si el ordenamiento histórico o un scan tiene coste evitable. El sort final por valor calculado no es candidato de eliminación con el índice propuesto. Sólo si la evidencia lo respalda, probar `(strategy_code, player_id, calculated_at DESC, id DESC)`, medir de nuevo con la misma población y documentar ambos planes en `research.md`; conservar el índice sólo si mejora el plan y tiempo sin regresión material.
7. **Pruebas y cierre**: en el límite de `QuoteQueryService.ranking` posterior a la autenticación, verificar hit sin llamadas a repositorios, sin transacción activa y, con instrumentación de datasource, sin checkout de conexión atribuible al servicio. Medir por separado el tiempo de la solicitud HTTP completa (<50 ms para hits). Cubrir 20 hilos/una consulta en una instancia; página vacía publicada bajo alias y código explícito; carrera determinista donde una carga vieja termina después de `clear()` y sólo la clave vigente contiene datos nuevos; evento y recarga con Awaitility; Redis caído; `QuoteRankingCacheIntegrationTest`; respuesta y orden previos. GitHub Actions `SUCCESS`, SonarCloud `PASSED` y Swagger UI con JWT/API key son verificaciones manuales posteriores al commit/push/PR del usuario; `$speckit-implement` no realiza esas acciones.

## Post-design Constitution Check

El diseño conserva las capas (§2.1), Redis como único caché persistente (§5.2) y las métricas/logs exigidos (§5.3). La coordinación de solicitudes sólo guarda trabajos en curso y se limpia al terminar; no es un caché de páginas adicional. No se propone índice sin medición de la base de demo. **Gate constitucional: pasa.** La garantía de cero consultas/transacciones/conexiones se evalúa sólo dentro de `QuoteQueryService.ranking` después de autenticar; el objetivo de latencia se evalúa sobre el HTTP completo. Los filtros JWT y API key permanecen fuera de alcance.
