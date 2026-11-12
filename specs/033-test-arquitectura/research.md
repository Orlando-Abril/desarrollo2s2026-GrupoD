# Investigación: tests de arquitectura

## Integración

Se usa `com.tngtech.archunit:archunit-junit6:1.5.1` en scope test con JUnit 6.0.3. La [guía oficial de ArchUnit](https://www.archunit.org/userguide/html/000_Index.html) describe `@AnalyzeClasses`, capas y slices. Se importa producción con `ImportOption.DoNotIncludeTests`; las reglas se descubren mediante `@ArchTest`.

## Reglas de código

`GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS` cubre streams estándar y `Throwable.printStackTrace()`. Se comprueban `@Transactional` en clases y métodos de controller por separado.

## Desvíos encontrados

Los controllers usan `League` y `Position` en parámetros HTTP; la regla permite sólo esos dos modelos. También usan `CorrelationIdFilter.HEADER` en `@Header` de OpenAPI. `HEADER` es un `public static final String` que Java inserta como literal al compilar; la regla de dependencias permite `CorrelationIdFilter` explícitamente y documenta ese uso.

La regla de ciclos importa únicamente controller, service, repository, adapter y model. `service ↔ dto` y `controller ↔ exception` son dependencias aceptadas y motivan el recorte. Las reglas direccionales para los paquetes pertinentes permanecen activas. No se usa baseline ni `FreezingArchRule`.
