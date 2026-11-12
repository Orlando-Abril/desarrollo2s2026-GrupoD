# Validación rápida: tests de arquitectura

Desde `backend/`, con JDK 17 y las dependencias Maven disponibles:

```powershell
./mvnw -B '-Dtest=LayeredArchitectureTest,NamingConventionTest,CodingRulesTest' test
./mvnw -B verify
```

Revisar `target/surefire-reports/com.example.demo.architecture.*.txt`: las tres clases deben ejecutar reglas, con conteo mayor a cero, sin fallos. Estas pruebas analizan producción sin iniciar Spring ni infraestructura externa.

Comprobar el traslado de `MarketPrincipal` y el alcance de los desvíos existentes: `League` y `Position` en parámetros de controllers, `CorrelationIdFilter.HEADER` en `@Header` de OpenAPI, y ciclos sólo entre controller, service, repository, adapter y model. `service ↔ dto` y `controller ↔ exception` quedan fuera de esa regla de ciclos.

En PowerShell sobre Windows, los tests existentes que capturan logs con acentos pueden requerir `$env:JAVA_TOOL_OPTIONS='-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dlogging.charset.console=UTF-8'` antes de Maven.
