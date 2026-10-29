# Research: Recálculo manual desde Ranking

## R1. Contrato del recálculo y adaptador frontend

**Decision**: Agregar `recalculateQuotes({ strategy } = {}, { signal } = {})` en `quotesApi.js`. Delegar en el cliente compartido con método POST, omitir el cuerpo cuando `strategy` sea `undefined` o `null`, enviar `{ strategy }` cuando se provea y devolver la respuesta sin transformar. Ranking lo invoca sin estrategia para recalcular todas.

**Rationale**: El contrato existente acepta cuerpo ausente, `{}` o `strategy: null` como "todas", y un código exacto como una sola estrategia. La feature exige que el filtro visible de Ranking no limite la corrida. Signal y retorno sin transformación siguen el patrón de las otras funciones del mismo archivo.

**Alternatives considered**: Enviar la estrategia elegida en Ranking, descartado porque contradice FR-003. Enviar siempre `{}`, válido pero innecesario. Convertir string vacío a ausencia, descartado porque ocultaría un input inválido que el backend debe rechazar.

## R2. Propiedad del estado remoto

**Decision**: Crear `useRecalculate` para poseer progreso, notificación, denegación, guardia de duplicados, AbortController y callback de éxito. `RecalculateButton` sólo confirma, compone Button/Toast y presenta el resultado.

**Rationale**: La separación permite probar concurrencia, abortos y clasificación de errores sin acoplarlos al markup. Una referencia sincrónica impide el doble POST antes de que React publique `running=true`; la señal y la guarda de montaje impiden callbacks tardíos tras navegar.

**Alternatives considered**: Concentrar todo en `RankingPage`, descartado por mezclar dos estados remotos y dificultar tests. Confiar sólo en `disabled`, descartado porque dos eventos en el mismo tick pueden observar el estado anterior.

## R3. Confirmación previa

**Decision**: Usar `window.confirm` con texto explícito de recálculo completo antes de invocar el hook.

**Rationale**: No existe Dialog/Modal en el proyecto. La confirmación nativa es bloqueante, operable con teclado y tecnologías de asistencia, no agrega dependencias ni exige modificar un componente compartido.

**Alternatives considered**: Crear un modal nuevo, descartado por complejidad desproporcionada para la demo. Usar `confirm` dentro del hook, descartado porque mezclaría UI con transporte y haría menos claro que cancelar produce cero llamadas.

## R4. Persistencia de la denegación 403

**Decision**: Recordar en memoria de módulo el token de la sesión que recibió 403. El hook compara la identidad vigente al montar: mismo token mantiene la acción oculta; token nuevo restaura su evaluación.

**Rationale**: El componente se desmonta al abandonar Ranking, por lo que un estado local perdería la denegación. SessionContext expone el token pero es archivo protegido y no debe ampliarse. La autenticación actual también vive sólo en memoria, por lo que esta marca comparte su ciclo de vida. El token se trata como clave opaca y no sale a UI, logs ni almacenamiento.

**Alternatives considered**: Guardar por username, descartado porque un logout/login del mismo nombre heredaría una sesión anterior. `sessionStorage`, descartado porque sobreviviría a la sesión de autenticación en memoria. Modificar SessionContext, descartado por la regla de Entrega 1.

## R5. Resumen y Toast

**Decision**: Renderizar dentro del Toast existente un resumen estructurado y compacto que incluya todos los campos exigidos, muestre ceros y listas vacías, y tenga una acción local de cierre. Mantener el Toast de éxito hasta el cierre manual; usar CSS del componente para wrapping, altura máxima y scroll interno.

**Rationale**: Toast acepta JSX como children, por lo que no necesita cambios. Su descarte automático de cuatro segundos sería insuficiente para leer un resultado largo; omitir `onDismiss` evita el descarte y un Button de texto ofrece cierre explícito. El contenedor nuevo permanece renderizado aunque el Button se oculte por 403, de modo que el mensaje no desaparece con la acción.

**Alternatives considered**: Modificar Toast para duraciones configurables, descartado por protección de Entrega 1. Mostrar sólo procesados/fallidos, descartado porque omite datos contractuales. Crear otro sistema de avisos, descartado por duplicación.

## R6. Recarga con criterios vigentes

**Decision**: Exponer `reload` en `useRanking` reutilizando su clave de invalidación. `retry` y `reload` comparten el mismo callback: pasan a loading, limpian error e incrementan la clave sin cambiar filtros ni página.

**Rationale**: El efecto existente ya cancela consultas reemplazadas y lee strategy, league, position y currentPage actuales. Reutilizarlo garantiza que una selección cambiada durante el POST sea la que se consulte al terminar y conserva la corrección de página fuera de rango.

**Alternatives considered**: Llamar directamente a `getRanking` desde RecalculateButton, descartado por duplicar estado y poder publicar datos fuera del hook. Reutilizar `retry` como prop sin nombre semántico, funcional pero confuso para un éxito.

## R7. Clasificación de errores

**Decision**: Clasificar por `ApiError.status`: 409 usa el texto exacto solicitado; 403 informa falta de permiso y marca la sesión; `NetworkError` y cualquier otro caso muestran mensajes genéricos. Sólo 200 recarga Ranking.

**Rationale**: httpClient preserva status y código para errores contractuales, pero el mensaje backend de 409 no coincide exactamente con el texto UI requerido. No reutilizar mensajes desconocidos evita filtrar detalles internos. Los abortos propios se detectan por `signal.aborted` porque el cliente los normaliza también como NetworkError.

**Alternatives considered**: Mostrar siempre `error.message`, descartado por seguridad y claridad. Clasificar sólo por code, descartado porque status expresa directamente los dos casos requeridos y tolera cuerpos inesperados.

## R8. Timeout protegido de 10 segundos

**Decision**: No modificar `httpClient.js`. Tratar una corrida menor a 10 segundos como precondición de la demo y medirla antes de implementar/cerrar. Si el entorno real excede el límite, detener el trabajo y consultar al equipo para autorizar un timeout por pedido en el cliente compartido.

**Rationale**: httpClient aborta siempre a los 10.000 ms, mientras la feature 016 admite corridas de hasta un minuto. Cambiarlo sin permiso violaría la regla de Entrega 1. Usar fetch directo duplicaría autenticación, errores y timeout, contrariando la arquitectura existente.

**Alternatives considered**: Ignorar el riesgo, descartado porque produciría un falso error de red mientras el backend continúa. Duplicar fetch en quotesApi, descartado por arquitectura. Modificar ahora el timeout, descartado por falta de autorización.

## R9. Pruebas y calidad

**Decision**: Cubrir adaptador, hook, componente, recarga de Ranking y composición de página con Vitest/Testing Library existentes; ejecutar luego cobertura completa, lint y build.

**Rationale**: Las capas separadas localizan fallos de serialización, concurrencia, sesión y presentación. El pipeline vigente ya consume `frontend/coverage/lcov.info` para SonarCloud y no requiere cambios.

**Alternatives considered**: Probar sólo RankingPage, descartado porque hace frágiles los casos de abort, doble click y token. Agregar una librería de estado o requests, descartado porque el stack actual cubre todas las necesidades.

## Resolved Unknowns

No quedan `NEEDS CLARIFICATION`. El riesgo de timeout se resuelve como precondición verificable y gate de pausa: no autoriza cambios protegidos.
