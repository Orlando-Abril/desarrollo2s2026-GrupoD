# Data Model: Perfil uniforme de pruebas del backend

Esta feature no agrega entidades persistentes, tablas ni migraciones. El modelo describe configuración y metadatos de test que existen durante la carga del contexto.

## Perfil de ejecución `test`

Representa el entorno uniforme para tests unitarios asistidos por Spring e integraciones livianas de Entrega 2.

### Atributos efectivos

| Atributo | Fuente | Regla de validación |
|----------|--------|---------------------|
| Nombre activo | `@UnitTestProfile` | La colección de perfiles activos contiene `test`; la prueba demostrativa no declara otro perfil. |
| URL de DataSource | Configuración base de test | Es una URL `jdbc:h2:mem:` y conserva `MODE=PostgreSQL`. |
| Ciclo de esquema | Configuración base de test | El valor efectivo es `create-drop`. |
| Administrador de caché | Autoconfiguración efectiva | El bean es una instancia de `ConcurrentMapCacheManager`. |
| Football-Data base URL | Configuración base de test | URI válida con host `127.0.0.1`, puerto `1` y path heredado `/v4`. |
| WhoScored base URL | Configuración base de test | URI válida con host `127.0.0.1` y puerto `1`. |

### Relaciones

- Se activa mediante `UnitTestProfile`.
- Combina `application.properties` de test con la capa opcional `application-test.properties`.
- Es observado por `UnitTestProfileTest` a través de componentes y propiedades efectivas.

### Estado

```text
sin perfil activo
    └── aplicar @UnitTestProfile
            └── perfil test activo
                    ├── cargar configuración base de test
                    ├── aplicar overrides específicos del perfil (si existen)
                    └── construir contexto aislado verificable
```

No hay transiciones persistentes: el estado existe únicamente durante la ejecución del test.

## Meta-anotación `UnitTestProfile`

### Metadatos

| Campo | Valor requerido |
|-------|-----------------|
| Paquete | `com.example.demo.support` |
| Retención | `RUNTIME` |
| Destino | `TYPE` |
| Meta-anotación funcional | `@ActiveProfiles("test")` |

### Invariantes

- Aplicarla a una clase activa el perfil `test` sin repetir `@ActiveProfiles`.
- No decide si el test usa contexto completo, slice o configuración manual.
- Sólo existe en el source set de test y no forma parte del artefacto productivo.

## Capa `application-test.properties`

### Reglas

- Se ubica en `backend/src/test/resources/`.
- La base `application.properties` siempre se carga desde el mismo classpath de test.
- Una clave incluida aquí reemplaza la clave homónima de la base sólo cuando `test` está activo.
- En la primera versión no duplica propiedades porque no hay overrides exclusivos necesarios.
- No activa el perfil por sí misma ni agrega una propiedad marcadora artificial.

## Prueba `UnitTestProfileTest`

### Dependencias observadas

- `Environment`: perfiles y valores resultantes.
- `DataSource`: conexión y metadatos H2 efectivos.
- `CacheManager`: implementación efectiva.

### Casos de validación

1. El perfil `test` está activo.
2. El `DataSource` abre una conexión cuyo producto es H2 y cuya URL es en memoria.
3. La URL JDBC conserva compatibilidad PostgreSQL y la estrategia de esquema efectiva es `create-drop`.
4. El `CacheManager` es `ConcurrentMapCacheManager`.
5. Football-Data apunta a `127.0.0.1:1`.
6. WhoScored apunta a `127.0.0.1:1`.

La prueba falla ante cualquier desviación y no invoca clientes externos.
