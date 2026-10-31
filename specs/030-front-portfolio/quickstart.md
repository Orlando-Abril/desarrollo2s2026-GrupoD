# Quickstart Validation: Pantalla Mi portfolio

## Prerrequisitos

- Node.js y npm compatibles con el frontend actual.
- Dependencias instaladas en `frontend/`.
- Para validación manual: backend ejecutándose con la feature 028, un usuario autenticable y datos de prueba con posiciones y más de 20 operaciones.

Los contratos esperados están en [frontend-contracts.md](contracts/frontend-contracts.md) y los modelos en [data-model.md](data-model.md).

## 1. Validación automática focalizada

Desde la raíz:

```powershell
Set-Location frontend
npm test -- src/api/portfolioApi.test.js src/features/portfolio src/pages/PortfolioPage.test.jsx src/routes/routes.test.jsx
```

Resultado esperado:

- cliente API construye `/auth/me`, portfolio e historial con página 0/tamaño 20;
- hook aborta respuestas reemplazadas y mantiene errores/reintentos separados;
- ganancia, pérdida y neutro tienen clase semántica, signo y etiqueta;
- ambos gráficos ofrecen tabla alternativa con los mismos datos;
- las tablas alternativas se abren con teclado mediante “Ver datos en tabla” y son visibles al desplegarse;
- total cero no produce porcentajes inválidos;
- un test genera 1.000 operaciones en memoria, recorre todas las páginas y verifica cantidad total, IDs únicos, cero omisiones/repeticiones y orden completo;
- vacío muestra “Todavía no compraste tokens” y no oculta historial existente;
- paginado respeta límites y se deshabilita durante carga;
- `/portfolio` está protegida y activa en ambos menús.

## 2. Suite completa y calidad

```powershell
Set-Location frontend
npm test
npm run lint
npm run build
```

Resultado esperado: todos los comandos terminan con código 0. En CI, SonarCloud debe conservar el Quality Gate aprobado y menos de 10 issues.

## 3. Preparar datos manuales

Usar una cuenta que tenga, como mínimo:

- una posición con `profitLoss > 0`;
- una posición con `profitLoss < 0`;
- opcionalmente una posición con resultado cero;
- créditos reservados para comprobar saldo total/disponible;
- 25 o más operaciones mezclando COMPRA y VENTA;
- nombres de jugadores extensos y, si es posible, repetidos.

También disponer de:

- una cuenta sin posiciones ni operaciones;
- una cuenta sin posiciones activas pero con historial de posiciones cerradas.

## 4. Flujo principal

1. Iniciar frontend y backend según la guía general del repositorio.
2. Ingresar con la cuenta preparada.
3. Activar “04 Mi portfolio” en navegación de escritorio y móvil.
4. Confirmar que la URL es `/portfolio` y que el ítem queda marcado como actual.
5. Comparar saldo, invertido, valor actual y resultado contra la respuesta de portfolio.
6. Verificar que cada fila muestra jugador, tokens, PPC, cotización, valor actual y resultado a 4 decimales.
7. Abrir una posición y confirmar navegación a `/jugadores/{id}`.
8. Comparar PPC/cotización de la tabla con el gráfico de rendimiento y su tabla alternativa.
9. Comparar valores/proporciones de la dona con su leyenda y tabla alternativa.
10. Recorrer el historial: primera página, siguiente, anterior y última; no debe haber duplicados ni omisiones.
11. Abrir un jugador desde una operación.

## 5. Vacío y errores parciales

### Portfolio vacío

1. Ingresar con la cuenta sin posiciones.
2. Confirmar el texto exacto “Todavía no compraste tokens”.
3. Confirmar que no aparecen tabla de posiciones ni gráficos vacíos.
4. Si la cuenta tiene historial previo, confirmar que sigue visible y paginado.

### Error de portfolio

1. Simular una falla sólo en `/users/{id}/portfolio`.
2. Confirmar mensaje público y botón Reintentar.
3. Confirmar que el historial válido permanece visible.
4. Reintentar y verificar que no se vuelve a solicitar una página del historial.

### Error de historial

1. Simular una falla sólo en `/users/{id}/transactions`.
2. Confirmar que resumen, posiciones y gráficos permanecen visibles.
3. Reintentar en una página distinta de la primera y confirmar que repite esa página únicamente.

## 6. Accesibilidad y responsive

1. Ajustar viewport a 360 px: no debe aparecer scroll horizontal de página; filas deben conservar etiquetas.
2. Recorrer la pantalla sólo con teclado: menú, links de jugadores, reintentos y paginación tienen foco visible y orden lógico.
3. Con lector de pantalla, verificar títulos/captions, leyendas y tablas alternativas de ambos gráficos.
4. Desactivar percepción del color: signo y texto siguen distinguiendo Ganancia, Pérdida y Sin variación; medir WCAG 2.2 AA con mínimo 4,5:1 para texto normal y 3:1 para texto grande, controles y elementos gráficos necesarios.
5. Activar `prefers-reduced-motion: reduce`: barras y dona no deben animarse.
6. Con un nombre largo, confirmar nombre completo en tooltip, leyenda o tabla alternativa aunque el eje lo abrevie.
7. Con teclado, abrir “Ver datos en tabla” en cada gráfico y confirmar que la tabla se vuelve visible y conserva todos los valores exactos.

## 7. Seguridad y sólo lectura

1. Abrir `/portfolio` sin sesión: debe redirigir a `/ingresar`.
2. Vencer la sesión durante una carga: debe aplicarse el manejo global y retirarse la información financiera.
3. Verificar en red que sólo se ejecutan GET de `/auth/me`, portfolio, historial y navegación de detalle.
4. Confirmar que consultar y paginar no cambia saldos, tokens, posiciones, órdenes, operaciones ni cotizaciones.

## 8. Evidencia anonimizada de usabilidad (SC-001)

Realizar esta validación al final de la implementación con al menos 10 participantes. No registrar nombres, correos, cuentas, grabaciones ni otros datos personales. Usar identificadores `P01` a `P10` (o superiores), medir desde que aparece la pantalla cargada hasta que la persona identifica correctamente Saldo, Invertido, Valor actual y Resultado, y conservar únicamente:

| Participante anónimo | Duración (segundos) | Identificó los 4 valores | Observación no identificable |
|----------------------|---------------------|--------------------------|-----------------------------|
| P01 | | Sí / No | |
| P02 | | Sí / No | |
| P03 | | Sí / No | |
| P04 | | Sí / No | |
| P05 | | Sí / No | |
| P06 | | Sí / No | |
| P07 | | Sí / No | |
| P08 | | Sí / No | |
| P09 | | Sí / No | |
| P10 | | Sí / No | |

**Resultado agregado**:

- Participantes totales: ____ (mínimo 10)
- Completaron correctamente en menos de 10 segundos: ____
- Tasa de éxito: ____ %
- SC-001 aprobado: Sí / No (requiere al menos 9 de cada 10)

La evidencia queda en esta tabla anonimizada de `quickstart.md`; cualquier nota temporal con información identificable debe descartarse y no incorporarse al repositorio.
