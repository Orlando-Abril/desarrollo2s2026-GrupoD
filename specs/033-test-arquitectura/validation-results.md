# Resultados de validación: arquitectura del backend

Fecha: 2026-11-11. Comando focal desde `backend/`:

```powershell
./mvnw -B '-Dtest=LayeredArchitectureTest,LayeredArchitectureContractTest,NamingConventionTest,NamingAndCycleContractTest,CodingRulesTest,CodingRulesContractTest' test
```

Resultado: **BUILD SUCCESS**; seis clases ejecutadas, 26 tests, 0 fallos, 0 errores y 0 omitidos. Las suites normales descubrieron 7 reglas de capas, 5 de convenciones y 4 de código. Los contratos ejecutaron 4, 3 y 3 tests respectivamente. Evidencia: `backend/target/surefire-reports/com.example.demo.architecture.*.txt`.

| Criterio | Fixture negativo | Regla y violación esperada | Reporte |
|---|---|---|---|
| SC-001 | `ForbiddenControllerDependencies.RepositoryCase` | Controller → repository; origen y destino identificados | `LayeredArchitectureContractTest.txt` |
| SC-001 | `ForbiddenServiceDependencies.ControllerCase` | Service → controller | `LayeredArchitectureContractTest.txt` |
| SC-001 | `ForbiddenRepositoryDependencies.ServiceCase`, `ControllerCase` | Repository → service, incluido el antiguo desvío de `MarketPrincipal`, y → controller | `LayeredArchitectureContractTest.txt` |
| SC-001 | `ForbiddenAdapterDependencies.ServiceCase`, `ControllerCase`, `RepositoryCase` | Adapter → service, controller y repository | `LayeredArchitectureContractTest.txt` |
| SC-001 | `ForbiddenModelDependencies.ServiceCase`, `ControllerCase`, `RepositoryCase`, `AdapterCase`, `DtoCase`, `FilterCase` | Model → cada otra capa central y paquete auxiliar; la última por regla explícita | `LayeredArchitectureContractTest.txt` |
| SC-001 | `ForbiddenDtoDependencies.ServiceCase` | DTO no aprobado → service y vista no aprobada | `LayeredArchitectureContractTest.txt` |
| SC-002 | `ForbiddenControllerDependencies.AdapterCase`, `ConfigCase`, `SecurityCase`, `ModelCase`, `FilterCase` | Controller → paquete no permitido | `LayeredArchitectureContractTest.txt` |
| SC-002 | `HeaderUsageSamples.Forbidden` | Uso real de `CorrelationIdFilter.HEADER` fuera de `@Header(name=...)` detectado por AST | `LayeredArchitectureContractTest.txt` |
| SC-003 | `WrongName`, `MissingRestAnnotationController` | Sufijo y `@RestController` | `NamingAndCycleContractTest.txt` |
| SC-003 | `ClassBasedRepository`, `WrongSuffixSpringData`, `IndirectSpringData` | Interfaz y sufijo para Spring Data directo e indirecto | `NamingAndCycleContractTest.txt` |
| SC-003 | `MisplacedAdvice` | Advice fuera de exception | `NamingAndCycleContractTest.txt` |
| SC-003 | `CycleController` ↔ `CycleService` | Ciclo entre capas centrales | `NamingAndCycleContractTest.txt` |
| SC-004 | `TransactionalClassController`, `TransactionalMethodController` | `@Transactional` en clase y método controller | `CodingRulesContractTest.txt` |
| SC-004 | `StandardOutCall`, `StandardErrCall`, `PrintStackTraceCall` | Acceso a streams estándar y `printStackTrace()` | `CodingRulesContractTest.txt` |

Casos positivos: `AllowedControllerDependencies` usa `League` y `Position`; `AllowedHeaderUsage` usa la constante sólo en documentación e incluye comentarios y literales ignorados por el AST; `AllowedRepositoryHelper` queda fuera de la selección Spring Data. Los fixtures están bajo `src/test/java` y `DoNotIncludeTests` los excluye de las tres suites normales. No se inició Spring ni se usó base de datos o infraestructura externa en estas seis clases.

La regla de slices evalúa únicamente controller, service, repository, adapter y model; dto, exception, filter, config, security y scheduler quedan fuera del análisis de ciclos. No se usa `FreezingArchRule` ni baseline. El flujo de CI conserva `./mvnw -B verify` en `.github/workflows/ci.yml`.

## Verificación completa

`./mvnw -B verify` terminó en **BUILD SUCCESS**: 963 tests, 0 fallos, 0 errores y 49 omitidos. En este equipo Windows se fijó para el proceso Maven `JAVA_TOOL_OPTIONS=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dlogging.charset.console=UTF-8`; los tests existentes de captura de logs requieren esos charsets para conservar los acentos tras iniciar un contexto Spring. Sin ese ajuste de entorno, `PlayerStatsServiceTest` falló en dos aserciones de texto capturado. La comprobación focal `HealthEndpointTest,PlayerStatsServiceTest` pasó 40/40 con el ajuste. No se cambió código ni configuración del proyecto por este problema. El registro completo está en `backend/target/architecture-verify-final.log` y los resultados por clase en `backend/target/surefire-reports/`.
