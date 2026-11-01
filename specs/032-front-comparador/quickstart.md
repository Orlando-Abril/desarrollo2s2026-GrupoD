# Quickstart: validar el comparador del frontend

## Prerrequisitos

- Backend con las capacidades 025, 028, 029 y 031 disponibles y al menos dos jugadores con historiales OFFICIAL y MOMENTUM.
- Una cuenta USER con posiciones y una cuenta ADMIN.
- Dependencias del frontend instaladas. Configurar `VITE_API_BASE_URL` si el backend no usa el origen por defecto.
- Contratos de referencia: [UI](contracts/ui-contracts.md) y [API 031](../031-comparador-estrategias/contracts/strategy-comparison-api.yaml).

## Comandos de validación

Desde `frontend/`:

```powershell
npm test
npm run lint
npm run build
npm run dev
```

El proyecto debe completar tests, lint y build sin errores. Verificar también el estado de CI y el Quality Gate de SonarCloud conforme a la Constitution §3.2.

**Línea base antes de implementar (2026-10-31)**: `npm test` pasó (38 archivos, 370 tests); `npm run lint` pasó; `npm run build` pasó con una advertencia preexistente de bundle mayor a 500 kB.

## Resultado de la implementación (2026-10-31)

- `npm test`: **49 archivos, 435 tests aprobados**. Un recorrido previo excedió el tiempo de 5 segundos del test de 1.000 transacciones bajo carga paralela; se amplió sólo el límite de ese test a 10 segundos y la corrida completa posterior pasó.
- `npm run lint`: **aprobado**, con cuatro advertencias no bloqueantes (dos de Fast Refresh en el contexto y dos de actualización de estado dentro de efectos existentes o ampliados).
- `npm run build`: **aprobado**, con la advertencia de bundle mayor a 500 kB ya presente en la línea base.
- `git diff --check`: **aprobado**. La revisión de FR-022 confirmó que los únicos archivos de Entrega 1 modificados son `Header.jsx` y `Figurita.jsx`, dentro del alcance autorizado.
- T043, validación manual de los cuatro recorridos y los anchos 360/768/1440 px: **pendiente**. No hay backend ni frontend escuchando en los puertos 8080/5173 en esta sesión; las pruebas automatizadas cubren los contratos y estados principales, pero no sustituyen el recorrido visual y con lector de pantalla.
- T045, CI y Quality Gate de SonarCloud: **pendiente de ejecución remota sobre esta rama**. Los cambios siguen en el workspace sin publicar y el comando `gh` no está disponible, por lo que no existe evidencia de un nuevo pipeline o Quality Gate PASSED.
- T046, validación externa de usabilidad: **pendiente**. No se convocaron ni simularon participantes. Registrar aquí diez resultados reales, uno por persona, con tiempo, oportunidad encontrada, precio de operación identificado, panel abierto y asistencia recibida; el criterio de aceptación es al menos 9/10 flujos completos en menos de 60 segundos sin asistencia.

## Recorrido 1: selector, Álbum y Detalle

1. Ingresar como USER y abrir `/album`. Confirmar que «Ver con» inicia en «Mercado actual» y que figuritas y tabla muestran el `marketValue` persistido, incluso si su origen es MOMENTUM.
2. Elegir Momentum. Confirmar que sólo se solicita `/quotes/latest` para los ids visibles y que cada figurita muestra cotización, diferencia y precio de mercado separado.
3. Pasar a otra página y volver: comprobar que la consulta usa los nuevos ids. Abrir un jugador: la elección permanece y el gráfico muestra dos curvas históricas llamadas OFFICIAL y MOMENTUM, con leyenda y tabla de datos.
4. Volver a «Mercado actual»: comprobar el precio persistido y una sola curva identificada por el código real de la estrategia histórica seleccionada. Probar jugador sin cotización Momentum y un valor `NO_STATS`.
5. Con `active=MOMENTUM`, `marketStrategy=OFFICIAL` y `pendingRecalculation=true`, comprobar que «Mercado actual» sigue mostrando el precio fijado por OFFICIAL y que la pantalla indica el recálculo pendiente sin usar la cotización Momentum como precio de operación.

## Recorrido 2: Portfolio

1. Abrir `/portfolio` en «Mercado actual» y comprobar que la consulta no agrega `strategy` y conserva las cifras basadas en `marketValue`, cualquiera sea su estrategia de origen.
2. Elegir Momentum y comprobar `?strategy=MOMENTUM`, columna comparativa por posición y totales, sin cambio en saldo ni historial de operaciones.
3. Probar una posición sin cotización alternativa: la fila debe indicarlo y los totales comparativos quedar como no disponibles.

## Recorrido 3: oportunidades y operación

1. Abrir `/ranking` y localizar la sección de oportunidades en la misma página; no debe existir ítem de menú nuevo.
2. Filtrar por liga y posición. Comprobar listas separadas, diferencias y origen del precio.
3. Pulsar «Operar» en una oportunidad. Confirmar que se abre `/jugadores/:id#operar`, el panel de la 029 está visible y su precio es el de mercado vigente.
4. Probar filtros sin resultados y falla de la consulta: verificar vacío y reintento.

## Recorrido 4: control ADMIN

1. Como USER, confirmar que Ranking no muestra «Estrategia activa».
2. Como ADMIN, confirmar que la muestra junto a «Recalcular cotizaciones» con la configuración devuelta por el backend.
3. Elegir la alternativa y cancelar: no debe enviarse PUT. Repetir y confirmar: debe enviarse uno solo.
4. Verificar que el Toast explica que el precio cambia en el próximo recálculo y ofrece el botón existente. Tras el PUT exitoso, comprobar nuevas consultas de configuración y oportunidades; antes de recalcular, `marketStrategy` y el precio persistido permanecen identificados.
5. Usar «Recalcular cotizaciones» con su confirmación habitual; al terminar, comprobar nueva consulta de configuración y oportunidades, estrategia de mercado, Ranking y precios actualizados.
6. Simular 403, 400 y fallo de red: verificar, respectivamente, control oculto para esa identidad, corrección posible y consulta de reconciliación sin mostrar éxito falso.

## Pruebas automatizadas esperadas

- Vitest/RTL: selector «Mercado actual / Momentum» y conservación de modo en Álbum, Detalle y Portfolio; petición por ids de página; dos series con códigos OFFICIAL/MOMENTUM y tabla accesible; columna/totales alternativos.
- Vitest/RTL: oportunidades, filtros, estado vacío y enlace «Operar» al jugador y panel correctos.
- Vitest/RTL: control oculto a USER; ADMIN con cancelación, confirmación única, éxito con Toast, 403, 400 y error de red. Verificar que el PUT no desencadena recálculo.
- Revisar manualmente teclado, lector de pantalla y anchos 360/768/1440 px.
