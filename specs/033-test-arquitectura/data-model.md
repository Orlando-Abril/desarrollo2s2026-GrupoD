# Modelo de reglas arquitectónicas

La feature analiza tipos y dependencias de producción; no agrega datos persistentes.

| Elemento | Regla |
|---|---|
| Tipo de producción | Clase bajo `com.example.demo`, incluidos subpaquetes; se excluyen clases de test. |
| Repositorio Spring Data | Tipo asignable a `Repository`; debe ser interfaz y terminar en `Repository`. |
| `MarketPrincipal` | Proyección en `repository.market` con `Long id` y `String username`. |

## Matriz de capas

`consideringOnlyDependenciesInLayers()` cubre Controller, Service, Repository, Adapter, Model y DTO.

| Destino | Acceso entrante permitido |
|---|---|
| Controller | Ninguna otra capa |
| Service | Controller y DTO; DTO limitado a las respuestas y vistas documentadas |
| Repository | Service |
| Adapter | Service |
| Model | Controller, Service, Repository, Adapter y DTO; controller limitado a `League` y `Position` |
| DTO | Controller, Service y Repository |

La regla explícita de controller también excluye config y security. El uso encontrado de `CorrelationIdFilter.HEADER` en controllers corresponde a `@Header` de OpenAPI y se permite en `forbiddenControllerDependency`.

## Alcance de ciclos

Los slices importan sólo controller, service, repository, adapter y model. El recorte responde a las dependencias aceptadas `service ↔ dto` y `controller ↔ exception`. Dto, exception, filter, config, security y scheduler no participan en esa regla; las restricciones direccionales siguen vigentes.
