# Quickstart: validar Pantalla de Ranking

## Prerequisites

- Node.js 22 y npm.
- Dependencias del frontend instaladas.
- Para validación manual, backend local disponible y un usuario autenticable con cotizaciones de ranking.
- `frontend/.env` o entorno con `VITE_API_BASE_URL` apuntando al backend.

## Automated validation

Desde `frontend/`:

```powershell
npm test -- src/api/quotesApi.test.js src/features/ranking/VariationBadge.test.jsx src/features/ranking/useRanking.test.js src/pages/RankingPage.test.jsx src/routes/routes.test.jsx
npm test -- --coverage
npm run lint
npm run build
```

Expected outcomes:

- Las suites focalizadas validan query string, estados/abort, presentación/filtros y ruta protegida.
- La suite completa termina sin regresiones y genera `coverage/lcov.info`.
- Oxlint termina sin issues nuevos.
- Vite genera el build productivo sin errores.

## Manual validation

1. Iniciar el backend y luego, desde `frontend/`, ejecutar `npm run dev`.
2. Abrir la URL informada por Vite e iniciar sesión.
3. Confirmar que `02 Ranking` está habilitado tanto en navegación de escritorio como en la inferior móvil y que el enlace correspondiente expone `aria-current="page"` al visitar `/ranking`.
4. Abrir `/ranking` y comprobar:
   - siete columnas con encabezados;
   - orden y puesto iguales al contrato del backend;
   - liga por texto y color, y todas las posiciones;
   - créditos y variaciones positiva, negativa, cero y sin dato diferenciadas;
   - filas sin navegación ni aspecto interactivo.
5. Elegir “Activa”, OFFICIAL y MOMENTUM; verificar en Network que Activa omite `strategy` y las otras lo envían. Con resultados, Activa puede comunicar el código efectivo; con respuesta vacía debe seguir diciendo únicamente “Activa” sin inferir un código.
6. Combinar liga y posición; verificar que el request omite filtros vacíos, conserva ambos criterios y vuelve a `page=0`.
7. Recorrer páginas; confirmar numeración visible desde 1, límites deshabilitados y puestos globales conservados.
8. Iniciar una consulta lenta y cambiar un filtro antes de que termine; confirmar que desaparecen la tabla y el paginador anteriores, los filtros permanecen habilitados y la consulta anterior se aborta sin publicar datos obsoletos.
9. Simular offline o detener el backend; confirmar error genérico y reintento con los mismos criterios.
10. Probar una combinación sin cotizaciones; confirmar el texto exacto “Todavía no hay cotizaciones”.
11. Probar nombres de jugador, equipo y liga deliberadamente largos; confirmar que no se solapan y que no generan overflow horizontal global.
12. Cerrar sesión e intentar `/ranking`; confirmar redirección a `/ingresar` sin datos visibles.

## Performance benchmark (SC-010)

Condiciones obligatorias:

- Backend local disponible, sesión autenticada y caché caliente mediante una consulta previa no registrada.
- Página de hasta 20 elementos y mismos navegador, máquina y viewport durante toda la muestra.
- Ejecutar 20 consultas consecutivas mediante cambio de estrategia, liga, posición o página.
- Cada medición comienza en el instante de la acción del usuario que inicia o reemplaza la consulta.
- Cada medición termina cuando aparece la tabla correspondiente o un estado terminal comprensible de error o vacío.
- Registrar los 20 tiempos individualmente. El criterio pasa sólo si al menos 19 son menores a 2.000 ms.

| Consulta | Criterios/acción | Inicio | Final visible | Tiempo (ms) | < 2.000 ms |
|----------|------------------|--------|---------------|-------------|------------|
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
| 11 | | | | | |
| 12 | | | | | |
| 13 | | | | | |
| 14 | | | | | |
| 15 | | | | | |
| 16 | | | | | |
| 17 | | | | | |
| 18 | | | | | |
| 19 | | | | | |
| 20 | | | | | |

Adjuntar la tabla completa o una evidencia equivalente al pull request.

## External usability validation (SC-009)

- **Status inicial**: `PENDING`.
- **Responsable**: equipo del proyecto.
- Ejecutar con al menos 10 participantes y registrar tiempos y necesidad de asistencia.
- Al menos 9 participantes deben identificar al jugador de mayor cotización y aplicar un filtro de liga o posición en menos de 30 segundos, sin asistencia.
- Adjuntar protocolo, resultados anonimizados y enlace de evidencia al pull request.
- Si todavía no se alcanzaron 10 participantes, mantener `PENDING`; no declarar el criterio cumplido. Este gate no bloquea planificación ni implementación, pero sí el cierre de la feature.

## External closure gates

Después del push o pull request:

1. Verificar por separado que GitHub Actions, definido en `.github/workflows/ci.yml`, finalice en `SUCCESS`.
2. Verificar que SonarCloud, configurado por `sonar-project.properties`, finalice en `PASSED`, sin vulnerabilidades nuevas y con menos de 10 issues.

Ambos gates permanecen `PENDING` antes del push/PR. No bloquean planificación o implementación, pero sí el cierre de la feature.

## Responsive and accessibility validation

- Revisar a 320 px, 768 px y 1440 px, incluyendo fixtures con nombres largos: sin overflow global, solapamientos ni pérdida de campos; el overflow de tabla queda contenido.
- Navegar sólo con teclado: filtros, paginación y reintento tienen foco visible y orden lógico.
- Inspeccionar la tabla: cada `th` tiene scope de columna; las filas no tienen `tabindex`, link ni role interactivo.
- Con lector de pantalla, confirmar que variación anuncia suba, baja, sin cambios o sin dato y que la liga conserva nombre textual.
- Verificar WCAG 2.2 AA: contraste mínimo 4.5:1 para texto normal y 3:1 para texto grande y componentes o controles visuales.

## Contract references

- [Ranking API](contracts/ranking-api.md)
- [Ranking UI](contracts/ranking-ui.md)
- [State/data model](data-model.md)
