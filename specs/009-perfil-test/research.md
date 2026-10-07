# Research: Perfil uniforme de pruebas del backend

## Decisión 1: Precedencia de configuración por perfil

**Decision**: Mantener `backend/src/test/resources/application.properties` como configuración base inmutable y agregar `application-test.properties` como capa específica. Cuando `test` está activo, Spring Boot carga la configuración base y además la variante del perfil; una clave presente en la variante tiene precedencia sobre la misma clave de la base. Las claves ausentes en la variante conservan el valor de la base.

**Rationale**: Es el mecanismo nativo de externalized configuration de Spring Boot y permite formalizar el perfil sin copiar H2, `create-drop`, caché simple, secretos de test ni URLs locales. Evita que dos archivos equivalentes se desincronicen. Referencia: documentación oficial de Spring Boot, “Profile Specific Files”, dentro de [Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.files.profile-specific).

**Alternatives considered**:

- Duplicar toda la configuración vigente en `application-test.properties`: rechazado por divergencia futura y porque viola el requisito de archivo mínimo.
- Importar manualmente `application.properties`: rechazado porque Spring Boot ya carga la base y la importación sería redundante.
- Modificar o renombrar el `application.properties` existente: rechazado porque es un archivo protegido de Entrega 1.

## Decisión 2: Contenido mínimo de `application-test.properties`

**Decision**: Crear el archivo con comentarios que documenten su función y sin duplicar propiedades mientras el perfil no necesite un override real. La existencia y activación del perfil provienen del nombre convencional del archivo y de `@ActiveProfiles("test")`, no de una propiedad marcadora artificial.

**Rationale**: Todos los valores requeridos ya están en el `application.properties` del classpath de test. Agregar una propiedad nueva o cambiar `spring.application.name` alteraría el comportamiento actual; repetir propiedades contradiría el objetivo de herencia mínima.

**Alternatives considered**:

- Agregar `spring.config.activate.on-profile=test`: rechazado por redundante en un archivo cuyo nombre ya es específico del perfil.
- Agregar una propiedad `test.profile.enabled=true`: rechazado porque crea configuración sin consumidor y no prueba la activación real del perfil.
- Repetir únicamente H2, caché y URLs: rechazado porque no son valores exclusivos del perfil nuevo; ya forman parte de la base de test que debe heredarse.

## Decisión 3: Contrato de la meta-anotación

**Decision**: Crear `com.example.demo.support.UnitTestProfile` en el source set de test con `@Retention(RetentionPolicy.RUNTIME)`, `@Target(ElementType.TYPE)` y `@ActiveProfiles("test")`. No combinarla con `@SpringBootTest`, para que cada test pueda elegir el tipo de contexto apropiado.

**Rationale**: Spring Test admite `@ActiveProfiles` como meta-anotación. Mantener separadas la selección del perfil y la estrategia de carga del contexto hace que la convención sirva tanto para tests unitarios asistidos por Spring como para integraciones livianas. Referencia: documentación oficial de Spring Framework sobre [`@ActiveProfiles`](https://docs.spring.io/spring-framework/reference/testing/annotations/integration-spring/annotation-activeprofiles.html).

**Alternatives considered**:

- Incluir `@SpringBootTest` dentro de `@UnitTestProfile`: rechazado porque obligaría a todos los consumidores a levantar el contexto completo.
- Crear una clase base: rechazado porque impone herencia y no expresa la intención en una sola anotación.
- Usar `@ActiveProfiles("test")` directamente en cada test: rechazado porque no proporciona la convención uniforme solicitada.

## Decisión 4: Alcance de la prueba demostrativa

**Decision**: Crear `UnitTestProfileTest` con `@SpringBootTest` y `@UnitTestProfile`. Inyectar `Environment`, `DataSource` y `CacheManager`; verificar el perfil activo, metadatos/URL de la conexión H2, propiedades efectivas de JPA, tipo concreto del caché y las dos URLs externas parseadas como URI.

**Rationale**: La prueba observa el contexto efectivo en lugar de limitarse a leer archivos. Una conexión obtenida del `DataSource` demuestra H2 en memoria; el tipo del bean demuestra el caché simple; `Environment` demuestra la configuración resultante después de aplicar precedencias. Parsear las URLs permite afirmar host `127.0.0.1` y puerto `1` sin contactar las fuentes.

**Alternatives considered**:

- Verificar sólo strings de propiedades: rechazado para base y caché porque no demuestra los componentes efectivos.
- Invocar Football-Data o WhoScored y esperar rechazo: rechazado porque agrega latencia, prueba comportamiento de sockets y no mejora la garantía de que no exista un destino público.
- Instrumentar todo el tráfico saliente: rechazado porque está fuera de alcance y podría requerir dependencias o cambios globales.

## Decisión 5: Interpretación verificable de “sin salida a Internet”

**Decision**: La prueba verificará por separado que `football-data.base-url` y `whoscored.base-url` usan el host literal de loopback `127.0.0.1` y el puerto cerrado `1`; no ejecutará los clientes externos.

**Rationale**: La feature controla esas dos integraciones. Afirmar sus destinos efectivos prueba que ninguna apunta a Internet y reproduce la protección existente sin depender de conectividad, firewall ni software adicional. El path `/v4` de Football-Data puede heredarse sin afectar la garantía de host/puerto.

**Alternatives considered**:

- Aceptar cualquier hostname local: rechazado porque una resolución DNS podría variar; el literal requerido es determinista.
- Comprobar únicamente que la URL contiene `127.0.0.1`: rechazado porque una aserción estructurada de URI evita falsos positivos.
- Abrir una conexión y esperar que falle: rechazado porque la ausencia de servicio en el puerto no es la propiedad principal a demostrar.

## Decisión 6: Dependencias y validación de calidad

**Decision**: Reutilizar exclusivamente dependencias ya declaradas y validar con Maven Wrapper. La Definition of Done exige suite verde, GitHub Actions en `SUCCESS` y cero issues nuevos de SonarCloud.

**Rationale**: El `pom.xml` ya incluye soporte Spring/JPA/cache test, H2, JUnit Jupiter y AssertJ. Modificarlo está prohibido y no es necesario. El chequeo de CI/Sonar satisface directamente Constitution §3.1 y §3.2.

**Alternatives considered**:

- Agregar una librería de bloqueo de red: rechazado por la prohibición de dependencias y por exceder el alcance.
- Cambiar `ci.yml` para activar el perfil globalmente: rechazado porque la meta-anotación ya activa el perfil donde corresponde y `ci.yml` está protegido.

## Resultado de investigación

No quedan `NEEDS CLARIFICATION`. Si el contexto real no produjera un `ConcurrentMapCacheManager` con la configuración heredada, no se autoriza corregir `CacheConfig`, el `pom.xml` ni el `application.properties` existente: la implementación deberá detenerse y solicitar aprobación indicando el archivo, el cambio mínimo y una alternativa.
