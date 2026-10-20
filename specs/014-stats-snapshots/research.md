# Research: Historial de snapshots de estadísticas

## 1. Fuente de la forma reciente

**Decision**: Construir la aproximación temporal a partir de snapshots de los totales de temporada ya persistidos en `PlayerStats`.

**Rationale**: La captura local preserva el estado usado por cada corrida, funciona aunque WhoScored esté caído y satisface §1.3 de la constitución. También evita aumentar la superficie del scraper en una feature cuyo consumidor inmediato es el motor de valuación.

**Alternatives considered**:

- Scrapear `/players/{id}/matchstatistics` de WhoScored: ofrecería datos reales por partido, pero se descarta por ahora porque requiere otro adapter/mapeo, multiplica solicitudes y bloqueos de Cloudflare y acopla el cálculo de cotizaciones a una fuente no oficial.
- Seguir usando una sola fila de `PlayerStats`: no conserva evolución; una vez sincronizada la fila anterior no puede reconstruirse.
- Derivar forma desde `PlayerQuote`: se mantiene como comportamiento de MOMENTUM v1, pero mezcla rendimiento deportivo con la salida ya valuada y no resuelve el historial estadístico requerido.

## 2. Entidad append-only y repositorio mínimo

**Decision**: Crear `PlayerStatsSnapshot` con `@Immutable`, todos los campos no actualizables, relación lazy unidireccional a `Player` y un repositorio que sólo exponga inserción y lectura.

**Rationale**: Replica el patrón probado de `PlayerQuote`, hace visible la intención append-only tanto en el modelo como en la interfaz de acceso y evita modificar `Player` o cualquiera de los tres archivos protegidos de estadísticas.

**Alternatives considered**:

- Reutilizar o versionar filas de `PlayerStats`: rompe su semántica de “estado actual” y exige tocar un modelo protegido.
- Heredar de `JpaRepository`: simplifica el código, pero expone `delete` y operaciones de modificación incompatibles con el contrato inmutable.
- Agregar una colección a `Player`: introduce acoplamiento bidireccional y modifica una entidad existente sin necesidad.

## 3. Identidad lógica, índice e idempotencia concurrente

**Decision**: Usar id técnico autogenerado, unicidad de base sobre `(player_id, source_fetched_at)` e índice de consulta sobre `(player_id, captured_at)`. Prefiltrar claves conocidas, escribir las nuevas en lote y, ante una carrera de unicidad, revertir el intento, refrescar las claves y reintentar sólo el conjunto todavía ausente.

**Rationale**: `sourceFetchedAt` identifica la misma versión persistida de estadísticas aunque cambien `capturedAt` o `runId`. El prefiltrado resuelve el camino normal; sólo la restricción en base cierra la carrera entre procesos. El reintento en una transacción nueva permite contabilizar la colisión como salteada sin dejar la transacción original en estado abortado. Verificar que el conjunto pendiente se reduzca evita confundir otra violación de integridad con un duplicado benigno.

**Alternatives considered**:

- Sólo `exists` antes de `saveAll`: tiene una carrera entre comprobación e inserción.
- Sólo capturar `DataIntegrityViolation` dentro de la misma transacción: PostgreSQL deja la transacción fallida y el lote no puede continuar ni confirmar.
- Insertar fila por fila: facilita aislar conflictos, pero abandona la persistencia en lote solicitada y aumenta viajes a la base.
- `INSERT ... ON CONFLICT DO NOTHING`: es eficiente en PostgreSQL, pero introduce SQL específico y una ruta distinta para H2; el patrón de retry mantiene el repositorio portable.
- Bloqueo global o pesimista de `PlayerStats`: serializa trabajo no relacionado y requeriría ampliar el repositorio protegido o agregar infraestructura de locking.

## 4. Hora de captura determinística

**Decision**: Agregar `ClockConfig` con `Clock.systemUTC()` e inyectar `Clock` en el servicio; llamar `instant()` exactamente una vez por `snapshotAll`.

**Rationale**: Todos los snapshots de la corrida comparten el instante, los tests pueden usar `Clock.fixed`, y la feature 016 podrá reutilizar la misma fuente temporal sin métodos estáticos difíciles de controlar.

**Alternatives considered**:

- Llamar `Instant.now()` por jugador: genera diferencias dentro de la misma corrida y tests dependientes del tiempo real.
- Pasar `capturedAt` como parámetro público: facilita tests, pero permite que cada consumidor aplique políticas temporales distintas.

## 5. Selección y orden de los últimos N

**Decision**: Consultar `capturedAt DESC, id DESC` con límite N en la base y revertir el resultado antes de formar ventanas.

**Rationale**: El orden descendente selecciona eficientemente los N más recientes usando el índice. Revertir el conjunto acotado entrega orden cronológico natural; `id` resuelve empates de forma estable y coincide con el patrón de `PlayerQuote`.

**Alternatives considered**:

- Leer todo el historial y recortar en memoria: degrada a medida que crece el historial.
- Ordenar sólo por `capturedAt`: dos filas con igual instante tendrían un orden no determinístico.
- Devolver descendente: complica emparejar “anterior → nuevo” y contradice la spec acordada.

## 6. Rating de una ventana

**Decision**: No restar ratings. Recuperar el promedio del intervalo mediante ponderación por minutos acumulados y usar `MathContext.DECIMAL64`; después acotar a `[0,10]`.

**Rationale**: `rating` es un promedio de temporada, no un contador. Si `R₀` resume `M₀` minutos y `R₁` resume `M₁`, el aporte agregado satisface `R₁M₁ = R₀M₀ + Rventana(M₁−M₀)`. Restar `R₁−R₀` sólo mide el cambio del promedio, no el nivel del nuevo intervalo. `DECIMAL64` evita errores por divisiones decimales no terminantes sin persistir una precisión falsa; el clamp protege el dominio de WhoScored frente a correcciones y redondeos acumulados.

**Alternatives considered**:

- `ratingNuevo - ratingAnterior`: matemáticamente incorrecto para promedios acumulados.
- Usar directamente `ratingNuevo`: mezcla toda la temporada con la ventana reciente.
- Promedio simple de ambos extremos: ignora cuántos minutos representa cada extremo.
- Devolver cero ante datos incompletos: confunde “sin dato” con rendimiento mínimo; se conserva `null`.

## 7. Reinicio de temporada

**Decision**: Considerar reinicio sólo cuando ambos minutos existen y `minutosNuevos < minutosAnteriores`; copiar los totales nuevos y usar el rating nuevo únicamente con minutos positivos.

**Rationale**: La caída de un contador aislado puede ser una corrección, mientras que la caída de minutos acumulados identifica el cambio de temporada definido por negocio. No hay una base compatible para ponderar ratings entre temporadas.

**Alternatives considered**:

- Detectar reinicio si baja cualquier contador: correcciones ordinarias generarían falsos reinicios.
- Aplicar deltas con piso cero: perdería el rendimiento ya acumulado al inicio de la nueva temporada.
- Marcar reinicio cuando faltan minutos: `null` expresa desconocimiento y no prueba un cambio de temporada.

## 8. Contratos externos

**Decision**: No crear artefactos bajo `contracts/`.

**Rationale**: La feature sólo introduce un servicio interno que será invocado por la feature 016. No agrega endpoint, evento, CLI ni integración externa; `StatsWindow` y `SnapshotResult` quedan documentados como records internos en `data-model.md`.

**Alternatives considered**:

- Diseñar un endpoint de snapshots: está fuera de alcance y obligaría a definir autorización, DTOs y OpenAPI sin consumidor solicitado.
