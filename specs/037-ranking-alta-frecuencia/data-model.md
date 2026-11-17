# Data model: ranking de alta frecuencia

No se introduce una entidad persistida nueva ni cambia el esquema de respuesta. Este documento describe el estado de lectura y las transiciones de caché.

## Página de ranking

| Campo | Regla |
|---|---|
| `items` | Lista ordenada; puede ser vacía. |
| `page` | Índice desde cero. |
| `size` | De 1 a 100; 20 por omisión. |
| `totalElements`, `totalPages` | Metadatos consistentes con la población filtrada. |

Cada entrada conserva `rank`, `player`, `value`, `percentageChange` (puede ser `null`), `strategy` y `version`. El orden es valor actual descendente e ID de jugador ascendente; el puesto se calcula antes de paginar.

## Identidad de consulta y entrada de caché

| Componente | Regla |
|---|---|
| Estrategia | Código explícito validado o alias lógico `active` cuando se omite. |
| Liga y posición | Valor exacto o ausencia de filtro. |
| Página y tamaño | Valores normalizados por el controller existente. |
| Generación | Versión local monotónica capturada al entrar al servicio; la clave añade `gen=<id-arranque>:<contador>` para que una ejecución nueva no reutilice claves antiguas. |
| Valor | `RankingPageResponse` completo, incluso si `items` es vacío. |
| Vigencia | TTL existente de una hora; invalidación total al terminar recálculo o confirmar cambio de estrategia. |

El alias `active` evita consultar la base para conocer la estrategia en un hit. En un miss, la estrategia efectiva se resuelve dentro de la lectura transaccional. La carga por alias publica la misma página bajo el alias y el código explícito en la misma generación, incluso vacía. No se garantiza el camino inverso. Un cambio de estrategia avanza la generación e invalida el alias junto con las claves explícitas.

## Carga en curso

Sólo existe mientras una clave ausente se está calculando en una instancia. Tiene la clave normalizada y un resultado pendiente que comparten líder y seguidores; desaparece al completar o fallar. No conserva páginas entre solicitudes ni altera el TTL de Redis.

## Transiciones

1. **Ausente → cargando → vigente**: una solicitud líder lee datos confirmados y llena Redis; los seguidores reciben el resultado.
2. **Cargando → ausente**: si el cargador falla, se liberan los seguidores y un nuevo pedido puede reintentar.
3. **Vigente → ausente**: una actualización incrementa la generación antes de vaciar las páginas anteriores; las solicitudes nuevas sólo consultan claves de la generación actual.
4. **Ausente → precalentando → vigente**: tras la invalidación y el commit, una tarea asíncrona carga la página 0, tamaño 20, sin filtros, mediante la ruta de estrategia omitida.
5. **Precalentando → descartado**: si otro evento invalida de nuevo antes de publicar, la carga antigua sólo puede escribir su clave vieja; al detectar la generación obsoleta elimina ambas claves antiguas y no restablece una página vigente.
6. **Redis no disponible**: la página se obtiene de datos persistidos; no se introduce una segunda entrada de caché.

## Índice candidato

No forma parte del modelo aprobado aún. Sólo una medición antes/después de la consulta real puede habilitar `(strategy_code, player_id, calculated_at DESC, id DESC)`; ver [research.md](research.md).
