# Quickstart: Perfil `test`

## Cuándo usarlo

Usá `@UnitTestProfile` en tests nuevos de Entrega 2 que necesiten configuración Spring y deban ejecutarse con H2 en memoria, caché simple y fuentes Football-Data/WhoScored aisladas en loopback.

No lo uses para pruebas e2e de Feature 011. Tampoco migres tests existentes de Entrega 1 sin aprobación explícita.

## Anotar un test nuevo

La activación del perfil y la carga del contexto son decisiones separadas. Para una integración liviana con contexto completo:

```java
package com.example.demo.somefeature;

import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@UnitTestProfile
class SomeFeatureTest {

    @Test
    void worksWithTheIsolatedTestEnvironment() {
        // Arrange / Act / Assert
    }
}
```

Para un test que carga otro tipo de contexto Spring, conservá `@UnitTestProfile` y elegí por separado la anotación de contexto apropiada. No repitas `@ActiveProfiles("test")`.

## Validar la prueba demostrativa

Ejecutá desde `backend/`.

Linux/macOS:

```bash
./mvnw -Dtest=UnitTestProfileTest test
```

Windows:

```powershell
mvnw.cmd -Dtest=UnitTestProfileTest test
```

Resultado esperado:

- el perfil `test` aparece activo;
- el `DataSource` efectivo es H2 en memoria;
- el administrador de caché es `ConcurrentMapCacheManager`;
- Football-Data y WhoScored apuntan a `127.0.0.1:1`;
- no se contacta ninguna fuente externa.

## Ejecutar toda la suite

Linux/macOS:

```bash
./mvnw test
```

Windows:

```powershell
mvnw.cmd test
```

La suite completa debe terminar sin fallos. La primera ejecución puede necesitar acceso al repositorio Maven para descargar dependencias ya declaradas; esto no agrega dependencias al proyecto ni habilita acceso externo durante los tests.

En Windows, si una terminal configurada con una página de códigos no UTF-8 corrompe caracteres acentuados capturados por tests de logging, ejecutá la suite en esa sesión con:

```powershell
$env:JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dlogging.charset.console=UTF-8'
mvnw.cmd test
```

Esta salvedad sólo corrige la codificación de la consola de la JVM; no modifica el perfil `test` ni la configuración del proyecto.

## Verificaciones de entrega

1. Confirmar que no se modificaron `pom.xml`, `ci.yml`, los `application.properties` existentes ni tests de Entrega 1.
2. Confirmar que GitHub Actions termina en `SUCCESS` conforme a Constitution §3.1.
3. Confirmar que SonarCloud conserva el Quality Gate aprobado y registra cero issues nuevos conforme a Constitution §3.2.
4. Si cualquier validación exigiera cambiar un archivo protegido, detenerse y solicitar aprobación con el archivo, cambio mínimo y alternativa.
