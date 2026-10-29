# Quickstart: validar Recálculo manual desde Ranking

## Prerequisites

- Node.js 22 y npm.
- Dependencias del frontend instaladas.
- Backend local con las features 016 y 023 disponibles.
- Un usuario ADMIN y un usuario no ADMIN autenticables.
- `frontend/.env` o entorno con `VITE_API_BASE_URL` apuntando al backend.
- Dataset de demo cargado. Antes de implementar cambios protegidos, confirmar que una corrida completa termina en menos de 10 segundos.

## Baseline frontend suite

Ejecución del 2026-10-28, antes de modificar código de la feature:

- Comando: `npm test` desde `frontend/`.
- Resultado: bloqueado por una falla preexistente de instalación; 14 archivos y 146 tests pasaron, pero 5 suites no pudieron cargarse.
- Causa: Vite no pudo resolver `recharts`, aunque la dependencia `3.10.1` figura en `package.json` y `package-lock.json`; `npm ls recharts --depth=0` confirmó que no está instalada en `node_modules`.
- Suites afectadas: `QuoteChart.test.jsx`, `PlayerDetailPage.test.jsx`, `routes.test.jsx`, `LoginPage.test.jsx` y `RegisterPage.test.jsx`.
- Resolución: con autorización del equipo se ejecutó `npm ci`; `package.json` y `package-lock.json` conservaron sus hashes SHA-256 y quedó instalado `recharts@3.10.1`.
- Revalidación: `npm test` finalizó en verde con 19 archivos y 205 tests aprobados. T001 queda completa.

## Early timeout gate

1. Autenticarse como ADMIN y ejecutar una corrida completa contra el backend de demo.
2. Medir desde el envío del POST hasta la respuesta 200.
3. Si tarda menos de 10 segundos, continuar con la implementación prevista.
4. Si alcanza o supera 10 segundos, detenerse: no modificar `frontend/src/api/httpClient.js`, no usar fetch directo y solicitar al equipo autorización para un timeout configurable por pedido.

Registrar tiempo, volumen del dataset y ambiente como evidencia.

### Intento local 2026-10-28

- Ambiente: backend local en `localhost:8080`, PostgreSQL 17 en `localhost:5432` y Redis ausente.
- Estado operativo: `/actuator/health` respondió `503 DOWN`, consistente con Redis ausente; la caché y el health check estaban degradados. No se instalaron servicios ni se invocaron sincronizaciones externas.
- Dataset consultado antes de cualquier cambio: `players=0`, `player_stats=0`, `players_with_stats=0`.
- Decisión: dataset vacío y no representativo. T002 permanece pendiente; no se creó ni promovió `t002-admin` y no se ejecutó ni midió `POST /quotes/recalculate`.

## Automated validation

Desde `frontend/`:

```powershell
npm test -- src/api/quotesApi.test.js src/features/ranking/useRecalculate.test.js src/features/ranking/RecalculateButton.test.jsx src/features/ranking/useRanking.test.js src/pages/RankingPage.test.jsx
npm test -- --coverage
npm run lint
npm run build
```

Expected outcomes:

- La suite del API prueba POST, cuerpo opcional, strategy y signal.
- El hook prueba éxito, doble envío, abort, 409, 403 por token y errores saneados.
- El componente prueba confirmación, progreso, Toast completo, cierre y ocultamiento.
- Ranking prueba que reload conserva los criterios actuales y que la página conecta el callback correcto.
- La suite completa no introduce regresiones y genera `coverage/lcov.info`.
- Oxlint y Vite terminan sin errores.

## Manual validation

1. Iniciar backend y frontend; abrir `/ranking` con un ADMIN.
2. Confirmar que aparece `Recalcular cotizaciones` junto al encabezado y que filtros, tabla y paginador conservan su comportamiento.
3. Activar la acción y cancelar la confirmación. Verificar en Network que no hubo POST.
4. Activar y confirmar. Durante la espera verificar:
   - Button deshabilitado;
   - texto `Recalculando…`;
   - estado busy anunciado;
   - tabla y criterios sin romperse;
   - un solo POST aunque se intente activar repetidamente.
5. Al terminar, comprobar el Toast contra la respuesta real:
   - runId;
   - todas las estrategias calculadas con versión y tres conteos;
   - estrategias salteadas y motivo, o `Ninguna`;
   - procesados y fallidos, incluidos ceros;
   - estrategia activa;
   - actualización de marketValue.
6. Confirmar que el resumen permanece hasta cerrarlo y que puede leerse completo con scroll interno si es largo.
7. Antes de la corrida elegir estrategia/filtros/página; cambiarlos mientras el POST corre si la duración lo permite. Confirmar que el GET posterior usa los criterios vigentes al finalizar.
8. Provocar una recarga GET fallida después de un POST exitoso. Verificar que el Toast de éxito permanece y Ranking muestra su recuperación habitual.

## Error and permission validation

### 409

1. Mantener una corrida activa e intentar otra desde Ranking.
2. Verificar el texto exacto `Ya hay un recálculo en curso`.
3. Confirmar que el Button vuelve a habilitarse y no se dispara un GET de recarga.

### 403

1. Ingresar con usuario no ADMIN y confirmar el recálculo.
2. Verificar `No tenés permisos para recalcular cotizaciones.` y que el Button desaparece.
3. Salir y volver a `/ranking` sin cambiar la sesión: el Button sigue oculto.
4. Cerrar sesión e ingresar con otro token ADMIN: la acción vuelve a evaluarse y aparece.

### Network and generic errors

1. Detener el backend o simular offline antes de confirmar.
2. Verificar mensaje genérico, fin de progreso, Button habilitado y ranking intacto.
3. Simular otro error contractual y confirmar que no se presenta `error.message` interno.
4. Navegar fuera de Ranking con el POST pendiente y comprobar que su finalización no muestra Toast ni inicia una recarga en otra pantalla.

## Responsive and accessibility validation

- Revisar a 320 px, 768 px y 1440 px: acción, progreso y resumen no se superponen ni producen overflow horizontal global.
- Navegar sólo con teclado: activar/cancelar/confirmar y cerrar el resumen; verificar foco visible.
- Con lector de pantalla, confirmar nombre del Button, estado busy, contenido del output y acción de cierre.
- Verificar que progreso, error y permiso no dependan sólo de color.
- Confirmar contraste WCAG 2.2 AA con los estilos locales y tokens existentes.

## External usability validation (SC-007)

- Ejecutar con al menos 10 participantes y registrar tiempos y necesidad de asistencia.
- Cada participante debe confirmar el recálculo, reconocer el estado de progreso e identificar en el resumen si hubo jugadores fallidos.
- La medición excluye la duración propia del backend y termina cuando la persona identifica el dato de fallidos.
- El criterio pasa sólo si al menos 9 participantes completan el flujo sin asistencia en menos de 30 segundos.
- Anonimizar los resultados y adjuntar esta tabla, o evidencia equivalente, al pull request.

| Participante | Confirmó | Reconoció progreso | Identificó fallidos | Tiempo sin backend | Sin asistencia |
|--------------|----------|---------------------|----------------------|--------------------|----------------|
| 1 | | | | | |
| 2 | | | | | |
| 3 | | | | | |
| 4 | | | | | |
| 5 | | | | | |
| 6 | | | | | |
| 7 | | | | | |
| 8 | | | | | |
| 9 | | | | | |
| 10 | | | | | |

## Protected-file audit

Antes de cerrar:

```powershell
git diff --name-only
git diff --check
```

No deben aparecer modificados:

- `frontend/src/components/Button.jsx` ni `Button.css`;
- `frontend/src/components/Toast.jsx` ni `Toast.css`;
- `frontend/src/api/httpClient.js` ni su test;
- `frontend/src/session/SessionContext.jsx`;
- ningún otro archivo de Entrega 1.

## External closure gates

Después del push o pull request:

1. Verificar que GitHub Actions finalice en `SUCCESS`.
2. Verificar que SonarCloud finalice en `PASSED`, sin vulnerabilidades nuevas y con menos de 10 issues.
3. Adjuntar evidencia de la validación de demo y accesibilidad.

## Contract references

- [Recalculation API](contracts/recalculation-api.md)
- [Recalculation UI](contracts/recalculation-ui.md)
- [State/data model](data-model.md)
