# Implementation Plan: Recálculo manual desde Ranking

**Branch**: `feature/front-recalcular` | **Date**: 2026-10-28 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/025-front-recalcular/spec.md` y directivas técnicas del usuario para `quotesApi`, `RecalculateButton`, `useRecalculate`, integración con `useRanking` y pruebas Vitest.

## Summary

Agregar a Ranking una acción confirmada que invoque el contrato existente `POST /quotes/recalculate`, comunique progreso, muestre en el Toast compartido el resumen completo y recargue el ranking con los criterios vigentes. El transporte se amplía con `recalculateQuotes({ strategy })`; un hook nuevo aísla concurrencia, cancelación, errores y la denegación por sesión; un componente nuevo compone Button, confirmación y Toast; `useRanking` expone una recarga semántica sin resetear filtros ni página.

La implementación no modifica Button, Toast, SessionContext, httpClient ni ningún archivo de Entrega 1. Se conserva el timeout compartido de 10 segundos: la validación de demo debe confirmar temprano que la corrida termina dentro de ese límite. Si no lo hace, la implementación se detiene para solicitar autorización antes de proponer el cambio mínimo en `httpClient.js`.

## Technical Context

**Language/Version**: JavaScript con módulos ES; React 19.2.8

**Primary Dependencies**: React Router DOM 7.18.4; `fetch` encapsulado por `api/httpClient.js`; Button, Toast y tokens CSS existentes

**Storage**: Sin persistencia; estado efímero de React y una marca en memoria, ligada al token vigente, para recordar el 403 durante la sesión autenticada

**Testing**: Vitest 5.0.2, jsdom 29.1.1 y Testing Library React 16.3.3; oxlint; build con Vite 8.3.0

**Target Platform**: Navegadores web modernos; interfaz responsive desde 320 px hasta escritorio

**Project Type**: Aplicación web con frontend React y backend HTTP/REST separado; alcance productivo exclusivo en frontend

**Performance Goals**: El indicador de progreso aparece en el mismo ciclo de interacción; una confirmación genera como máximo un POST y un éxito genera exactamente una recarga GET; para la demo, el POST debe completar antes del timeout compartido de 10 segundos

**Constraints**: Recalcular todas las estrategias sin copiar el filtro de Ranking; no duplicar pedidos; preservar filtros y página en la recarga; mapear 409 al texto exacto y 403 a ocultamiento por sesión; no exponer detalles internos; ignorar resultados posteriores al desmontaje; no agregar dependencias; no modificar archivos de Entrega 1

**Scale/Scope**: Un endpoint existente, una pantalla existente, tres archivos productivos nuevos, tres archivos productivos existentes modificados, dos suites nuevas y tres suites existentes ampliadas

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

### Pre-design gate

| Principle | Status | Evidence |
|-----------|--------|----------|
| §2.2 Frontend independiente | PASS | La acción vive en la aplicación responsive y se comunica exclusivamente por HTTP/REST mediante `quotesApi` y el cliente compartido. |
| §2.3 Recálculo manual | PASS | El diseño consume `POST /quotes/recalculate` bajo confirmación explícita y no altera el batch ni el scheduler. |
| §3.1 Integración continua | **PENDING cierre** | El workflow vigente ya ejecuta instalación, tests con cobertura, lint y build. `SUCCESS` se verifica después del push/PR. |
| §3.2 SonarCloud | **PENDING cierre** | Las pruebas focalizadas y la suite con cobertura alimentan el análisis existente; `PASSED`, cero vulnerabilidades nuevas y menos de 10 issues se verifican remotamente. |
| §4.1 Autenticación y autorización | PASS | El bearer token sigue a cargo de `httpClient`; el backend conserva la decisión ADMIN. Ocultar tras 403 es sólo una mejora de UX. |
| §5.1 Endpoint mínimo | PASS | Se consume el endpoint obligatorio sin cambiar su contrato. |
| §6.1 OpenAPI | PASS | Se usa el esquema publicado por la feature 016; no se crean ni modifican endpoints o DTOs. |
| §7 Definition of Done | **PENDING cierre** | El plan incluye tests, cobertura, lint, build, accesibilidad y validación manual; CI y Sonar permanecen como gates externos. |
| Regla de Entrega 1 | PASS | Button, Toast, SessionContext, httpClient y configuraciones compartidas se reutilizan sin cambios. |

No hay violaciones constitucionales ni excepciones de diseño. El timeout fijo de 10 segundos es una restricción conocida, no una autorización implícita para cambiar `httpClient.js`.

### Post-design gate

**PASS para diseño; PENDING para cierre externo**. `research.md` resuelve contrato, estados, identidad de sesión, Toast, recarga y timeout; `data-model.md` no agrega persistencia; los contratos separan API y UI; `quickstart.md` cubre permisos, errores, responsive, accesibilidad y la verificación temprana del límite de 10 segundos. §3.1, §3.2 y §7 permanecen `PENDING` sólo para las comprobaciones posteriores al push/PR.

## Project Structure

### Documentation (this feature)

```text
specs/025-front-recalcular/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── recalculation-api.md
│   └── recalculation-ui.md
├── checklists/
│   └── requirements.md
└── tasks.md                    # futuro output de $speckit-tasks; no creado aquí
```

### Source Code (repository root)

```text
frontend/src/
├── api/
│   ├── httpClient.js                         # Entrega 1; reutilizado sin cambios
│   ├── quotesApi.js                          # existente: agrega recalculateQuotes
│   └── quotesApi.test.js                     # existente: contrato POST/body/signal
├── components/
│   ├── Button.jsx                            # Entrega 1; reutilizado sin cambios
│   └── Toast.jsx                             # Entrega 1; reutilizado sin cambios
├── features/ranking/
│   ├── RecalculateButton.jsx                 # nuevo: confirmación, Button, Toast y resumen
│   ├── RecalculateButton.css                 # nuevo: layout y resumen responsive
│   ├── RecalculateButton.test.jsx            # nuevo: flujos visibles e integración
│   ├── useRecalculate.js                     # nuevo: estado remoto, errores, abort y 403
│   ├── useRecalculate.test.js                # nuevo: transiciones, duplicados y sesión
│   ├── useRanking.js                         # existente: expone reload sin reset
│   └── useRanking.test.js                    # existente: recarga con criterios vigentes
├── pages/
│   ├── RankingPage.jsx                       # existente: monta la acción y conecta reload
│   └── RankingPage.test.jsx                  # existente: verifica composición/callback
└── session/
    └── SessionContext.jsx                    # Entrega 1; sólo consumido, sin cambios
```

**Structure Decision**: Se conserva la separación actual: transporte en `api`, estado remoto en hooks de `features/ranking`, presentación en un componente de feature y composición en `RankingPage`. Los estilos nuevos son locales al componente. No se toca backend, configuración, sesión ni componentes compartidos.

## Design

### `quotesApi.recalculateQuotes`

- Firma: `recalculateQuotes({ strategy } = {}, { signal } = {})`.
- Llama `request('/quotes/recalculate', { method: 'POST', ... })`, reenvía `signal` y devuelve la respuesta sin transformarla.
- Si `strategy` es `undefined` o `null`, omite el cuerpo para pedir todas las estrategias. Si existe, envía `{ strategy }` sin normalizarlo; el adaptador no convierte un string vacío en "todas".
- La pantalla invoca la función sin estrategia, independientemente del filtro visible de Ranking.

### `useRecalculate`

- Recibe `onRecalculated` y obtiene del contexto el token vigente sólo como clave opaca de sesión; nunca lo muestra, registra ni persiste.
- Expone `running`, `notification`, `forbidden`, `recalculate()` y `dismissNotification()`.
- Usa una referencia sincrónica además de `running` para impedir dos POST en el mismo tick. Cada intento crea un `AbortController`; el cleanup aborta y las continuaciones comprueban montaje/señal/identidad antes de publicar estado o llamar callbacks.
- Una marca de módulo conserva el último token que recibió 403. Remontar Ranking con el mismo token mantiene el botón oculto; un token nuevo restaura la evaluación. No se usa username ni almacenamiento del navegador.
- En 200 publica primero la notificación con el resultado, termina progreso y llama exactamente una vez `onRecalculated`. En 409 publica `in_progress`; en 403 publica `forbidden` y marca la sesión; en red u otros errores publica un tipo genérico. Ningún error llama la recarga.

### `RecalculateButton`

- Renderiza siempre el contenedor para que un Toast de 403 pueda seguir visible aunque el Button se oculte.
- El Button compartido usa variante secundaria, `type="button"`, `disabled`, `aria-busy` y texto `Recalculando…` durante el pedido.
- Antes de llamar al hook usa `window.confirm('Se recalcularán todas las cotizaciones. ¿Querés continuar?')`. Cancelar produce cero pedidos.
- Formatea el resumen como contenido estructurado del Toast: runId; estrategias calculadas con versión y los tres conteos; salteadas con motivo; procesados; fallidos; estrategia activa; y actualización de marketValue. Ceros y listas vacías se muestran explícitamente como `0` y `Ninguna`.
- El Toast de éxito permanece hasta que el usuario lo cierre mediante una acción Button de texto dentro del contenido; así el resumen no desaparece a los cuatro segundos. Los mensajes breves pueden usar el descarte automático existente. El CSS local limita altura, habilita scroll interno y fuerza wrapping sin cambiar `Toast.css`.
- 409 muestra exactamente "Ya hay un recálculo en curso". 403 muestra "No tenés permisos para recalcular cotizaciones.". Red y demás fallas usan mensajes genéricos sin `error.message` del servidor.

### Recarga de Ranking

- `useRanking` renombra conceptualmente la invalidación interna a una clave de recarga y expone `reload`.
- `reload` pone la consulta en loading, limpia su error e incrementa la clave; no cambia estrategia, liga, posición ni página.
- `retry` reutiliza el mismo callback para conservar el contrato existente. El efecto aborta cualquier GET anterior y lee los criterios más recientes cuando corre.
- `RankingPage` monta `<RecalculateButton onRecalculated={ranking.reload} />` junto al encabezado y antes de filtros/resultados. La acción permanece disponible aunque el ranking esté loading, vacío o en error; sus errores no reemplazan contenido de Ranking.

### Error, timeout y navegación

- La clasificación usa `ApiError.status`: 409 y 403 tienen tratamiento propio; todo lo demás, incluido `NetworkError`, se sanea.
- El cliente compartido también normaliza abortos a `NetworkError`; el hook suprime la notificación si su propia señal ya fue abortada.
- El timeout fijo de 10 segundos se conserva. El quickstart exige medir una corrida real antes de la demo. Si excede el límite, no se duplica `fetch` ni se cambia el archivo protegido: se pausa la implementación y se consulta al equipo.

### Test Strategy

- `quotesApi.test.js`: POST sin estrategia/cuerpo, POST con estrategia exacta, forwarding de signal, respuesta y errores sin transformar.
- `useRecalculate.test.js`: éxito y callback único; guardia de doble envío; 409; 403 persistente para el mismo token y reinicio con token nuevo; red/error genérico; abort/desmontaje sin publicación tardía.
- `RecalculateButton.test.jsx`: cancelar/confirmar; disabled, `aria-busy` y progreso; resumen completo con ceros/listas vacías; cierre del Toast; mensajes exactos; Button oculto tras 403 mientras el Toast permanece.
- `useRanking.test.js`: `reload` conserva los filtros y página más recientes, aborta un GET reemplazado y no publica datos obsoletos.
- `RankingPage.test.jsx`: mock del componente nuevo para verificar montaje y conexión exacta con `ranking.reload`, sin acoplar las pruebas existentes a sesión o red.
- Validación final: suites focalizadas, suite completa con cobertura, oxlint y build. Después del push/PR se verifican GitHub Actions y SonarCloud por separado.

## Complexity Tracking

No aplica: el Constitution Check no detectó violaciones.
