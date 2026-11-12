# Resultados de validación: arquitectura del backend

El 2026-11-12 se ejecutó `./mvnw -B verify` desde `backend/` con `JAVA_TOOL_OPTIONS=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dlogging.charset.console=UTF-8`: **BUILD SUCCESS**. Resultado global: 951 tests, 0 fallos, 0 errores y 49 omitidos. Log: `backend/target/architecture-verify-current.log`.

| Clase | Reglas ejecutadas | Fallos |
|---|---:|---:|
| `LayeredArchitectureTest` | 5 | 0 |
| `NamingConventionTest` | 5 | 0 |
| `CodingRulesTest` | 4 | 0 |

Los reportes de Surefire están en `backend/target/surefire-reports/com.example.demo.architecture.*Test.txt`. Las tres clases ejecutaron reglas: ninguna quedó con cero tests. La primera corrida sin el ajuste de charset falló en dos aserciones existentes de captura de logs de `PlayerStatsServiceTest`; la segunda corrida completa pasó sin modificar producción.

## Desvíos encontrados

- `League` y `Position` aparecen en controllers para binding/validación de parámetros HTTP y se permiten como tipos concretos.
- `CorrelationIdFilter.HEADER` aparece en controllers como nombre de `@Header` de OpenAPI; la regla permite `CorrelationIdFilter` de forma concreta.
- La regla de ciclos se limita a controller, service, repository, adapter y model porque `service ↔ dto` y `controller ↔ exception` son dependencias aceptadas. No se usa baseline ni `FreezingArchRule`.
