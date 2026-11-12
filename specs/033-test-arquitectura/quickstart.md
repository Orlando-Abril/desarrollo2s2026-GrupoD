# Validación rápida: tests de arquitectura

## Prerrequisitos

- JDK 17 y Maven Wrapper del repositorio.
- Dependencias Maven disponibles para `backend/pom.xml`, incluido `com.tngtech.archunit:archunit-junit6:1.5.1` en scope `test`; JUnit efectivo 6.0.3.
- Las tres suites normales y los tres tests de contrato se ejecutan sin levantar Spring, base de datos, Redis ni otros servicios externos.

## Comprobación de integración

Desde la raíz del repositorio, tras implementar la suite:

```powershell
Set-Location backend
./mvnw -B '-Dtest=LayeredArchitectureTest,LayeredArchitectureContractTest,NamingConventionTest,NamingAndCycleContractTest,CodingRulesTest,CodingRulesContractTest' test
```

El reporte debe mostrar que las seis clases de test se ejecutaron correctamente con `archunit-junit6` y JUnit 6.0.3: las tres suites normales de arquitectura y los tres tests de contrato que importan explícitamente fixtures negativos de SC-001 a SC-004. Las suites normales excluyen esos fixtures mediante `ImportOption.DoNotIncludeTests`. Ninguna de las seis clases debe levantar Spring ni infraestructura externa; cero reglas o tests descubiertos no cuenta como éxito.

## Verificación habitual

```powershell
Set-Location backend
./mvnw -B verify
```

Es el mismo comando que usa `.github/workflows/ci.yml`. Las infracciones deben fallar la ejecución con origen, destino y motivo constitucional; la suite no debe iniciar la aplicación ni acceder a una base de datos. Consultar la matriz de [data-model.md](data-model.md) para interpretar los resultados.

En PowerShell sobre Windows, los tests existentes que capturan logs con acentos pueden requerir ejecutar antes `$env:JAVA_TOOL_OPTIONS='-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dlogging.charset.console=UTF-8'`. Esto sólo configura la ejecución local de Maven.

## Escenarios de aceptación

1. Verificar que `MarketPrincipal` se encuentra en repository/market con `id` y `username`, y que la consulta y los servicios usan la nueva ubicación.
2. Verificar que las referencias de controller a `League`, `Position` y `CorrelationIdFilter.HEADER` pasan, y que una referencia a otro tipo de model o filter falla. Para el campo constante, comprobar también la regla de fuente descrita en [research.md](research.md).
3. Los tests de contrato deben importar explícitamente los fixtures negativos de `src/test/java/com/example/demo/architecture/fixtures/` y confirmar que cada dependencia, convención, `@Transactional`, acceso a streams estándar y `printStackTrace` infringe su regla esperada. No se modifica producción para provocar fallos.
4. Ejecutar la regla de ciclos entre controller, service, repository, adapter y model con los fixtures de contrato. Confirmar que `service ↔ dto` y `controller ↔ exception` no participan en slices, mientras sus reglas direccionales específicas siguen activas. No usar `FreezingArchRule` ni baseline.

No se generan contratos externos: la suite sólo participa en el proceso interno de verificación.
