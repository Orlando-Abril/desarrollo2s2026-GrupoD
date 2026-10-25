# Resultado de rendimiento

- Fecha: 2026-10-24 (America/Buenos_Aires)
- Estado del codigo: working tree de `feature/historial-cotizaciones`, basado en `e0e027e98bcae88f3e7378a0353f60168e997ba6` (la feature aun no tiene commit final)
- Entorno: Windows 10.0.26200, Java 22.0.2, Spring Boot 4.1.1, Tomcat embebido en puerto aleatorio, H2 2.4.240 en memoria
- Dataset: exactamente 2.500 `PlayerQuote` persistidas para un jugador y la estrategia `OFFICIAL`, ordenadas desde `2026-01-01T00:00:00Z`
- Protocolo: mismo host, sin carga concurrente, 5 solicitudes HTTP completas de warmup no medidas y 20 solicitudes HTTP completas secuenciales
- Duraciones (ms): `62, 66, 63, 66, 66, 46, 39, 48, 44, 42, 40, 48, 46, 47, 54, 59, 38, 31, 30, 36`
- Calculo p95: nearest-rank, `ceil(0.95 * 20) = 19`; valor ordenado numero 19 = **66 ms**
- Respuestas menores a 1 segundo: **20/20**
- Criterio: p95 < 1.000 ms y al menos 19/20 respuestas < 1.000 ms
- Resultado: **PASS**

La misma ejecucion levanto la aplicacion local y comprobo por HTTP historial, rango, `at`, 400/401/404, acceso con API key y JWT de `Role.USER`, coexistencia de `/players` y presencia del endpoint y ambos esquemas de seguridad en `/v3/api-docs`. El harness temporal se retiro despues de registrar la evidencia para no convertir un umbral sensible al entorno en una prueba ordinaria de CI.
