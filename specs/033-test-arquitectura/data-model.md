# Modelo de reglas arquitectónicas

Esta feature no agrega datos persistentes ni transiciones de estado. Sus elementos son tipos de producción, relaciones de dependencia y resultados de verificación.

## Tipos y relaciones

| Elemento | Atributos relevantes | Restricción |
|----------|----------------------|-------------|
| Clase de producción | Paquete de primer nivel, subpaquete, nombre, anotaciones y referencias de bytecode | Pertenece al universo `com.example.demo`; no se incluyen clases de prueba. |
| Dependencia | Tipo de origen, tipo o miembro de destino, ubicación de la referencia | Se comprueba directamente; los ciclos se evalúan sólo entre las cinco capas centrales. |
| Repositorio de persistencia | Tipo, interfaces heredadas directa o indirectamente, nombre | Si extiende `org.springframework.data.repository.Repository`, debe ser interfaz y terminar en `Repository`. |
| Desvío | Regla, origen, destino y descripción | Debe identificar la infracción; los desvíos no listados se reportan antes de cambiar producción. |

## Matriz de capas

La regla `consideringOnlyDependenciesInLayers()` cubre Controller, Service, Repository, Adapter, Model y DTO. La siguiente matriz indica **quién puede acceder a cada capa** dentro de esas seis; las restricciones de tipo y miembro se aplican además de la matriz.

| Capa destino | Acceso entrante permitido | Restricción adicional |
|--------------|---------------------------|-----------------------|
| Controller | Ninguna de las otras cinco | Service nunca depende de controller. |
| Service | Controller y DTO | DTO sólo desde los tres DTO de respuesta documentados y hacia sus vistas/resultados actuales. |
| Repository | Service | Controller y Adapter nunca acceden. |
| Adapter | Service | Controller y Repository nunca acceden. |
| Model | Service, Repository, Adapter, DTO y Controller | Controller sólo puede referenciar `League` y `Position`; no se abre el paquete model completo. |
| DTO | Controller, Service y Repository | Se conserva el uso actual de `BookLevelResponse` desde `MarketOrderRepository`; no implica permiso para `repository → service`. |

Las referencias a `filter.CorrelationIdFilter.HEADER` desde controller se comprueban en fuente porque la constante se inserta como literal al compilar. Cualquier dependencia de bytecode de controller hacia filter queda prohibida. Config, security y exception quedan fuera de `consideringOnlyDependenciesInLayers()`, pero la regla explícita de controller prohíbe config y security.

## Alcance de ciclos

La regla de slices importa únicamente tipos de controller, service, repository, adapter y model, con sus subpaquetes, y prohíbe ciclos entre esas cinco capas. Dto, exception, filter, config, security y scheduler no participan en esa regla. En particular, `service ↔ dto` y `controller ↔ exception` quedan fuera por las excepciones aceptadas, sin `FreezingArchRule` ni baseline. La exclusión no suspende las reglas de dependencias, convenciones o código que correspondan a esos paquetes.

## Proyección trasladada

`MarketPrincipal` conserva los componentes `Long id` y `String username`. Su paquete pasa de `service.market` a `repository.market`. `MarketUserRepository` la construye en su consulta; los servicios de mercado que la usan actualizan imports. No cambia el contrato funcional ni se introduce persistencia nueva.
