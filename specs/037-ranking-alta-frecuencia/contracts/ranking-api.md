# Contratos externos

## Ranking

`GET /players/ranking` conserva íntegramente el contrato documentado en [ranking-api.yaml](../../018-ranking-jugadores/contracts/ranking-api.yaml): parámetros opcionales `strategy`, `league`, `position`, `page` y `size`; autenticación JWT o `X-API-KEY`; respuestas 200, 400 y 401; estructura `RankingPageResponse` y orden valor descendente/ID ascendente. Sin parámetros, usa estrategia activa, página 0 y tamaño 20. Un acierto y un fallo de caché son indistinguibles para el cliente salvo por el tiempo.

La meta de menos de 50 ms para una consulta cacheada se mide sobre la solicitud HTTP completa. La garantía de cero llamadas a repositorios, cero transacciones abiertas y cero conexiones de base adquiridas corresponde sólo a `QuoteQueryService.ranking` después de la autenticación. Los filtros JWT y API key pueden mantener sus consultas actuales a la base; no cambia el contrato de autenticación.

Dentro de una misma instancia, las solicitudes concurrentes de una misma página ausente comparten un cálculo; no hay garantía de coordinación distribuida entre réplicas. Una carga por alias `active` publica el mismo resultado bajo el alias y la estrategia explícita de la generación capturada, incluso si la página está vacía. No se promete la equivalencia inversa. Tras un evento, las solicitudes nuevas sólo consultan claves de la generación vigente; un cálculo anterior no puede publicar una página vigente.

## Métricas

`GET /actuator/prometheus` sigue la seguridad existente (requiere credencial válida). El contrato observable agregado es poder distinguir aciertos y fallos del caché `quotes-ranking`. Nombre esperado de exportación: `cache_gets_total{cache="quotes-ranking",result="hit"}` y `cache_gets_total{cache="quotes-ranking",result="miss"}`; confirmar nombre y etiquetas exactas mediante scrape real al implementar. Las métricas HTTP de latencia y errores existentes permanecen.

## Trazabilidad

La respuesta conserva el header de ID de correlación y los logs de la solicitud conservan el formato JSON existente. El precalentado no modifica el contrato HTTP del recálculo ni del cambio de estrategia.

Las verificaciones de GitHub Actions, SonarCloud y Swagger UI con ambas credenciales son manuales y permanecen pendientes hasta el commit/push/PR de la usuaria.
