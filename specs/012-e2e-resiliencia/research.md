# Research: Resiliencia e2e de fuentes externas

## R1. Forma del helper reutilizable

**Decision**: Crear `support/E2EFixtures` como utilidad sin estado que recibe `PlayerRepository`, persiste cinco jugadores mediante `saveAllAndFlush` y devuelve un `FixtureSet` con lista inmutable y búsquedas por liga, equipo y posición.

**Rationale**: El repositorio ya es la autoridad de persistencia para `Player`; `saveAllAndFlush` vuelve visible el estado antes de requests que corren en otros hilos. Una utilidad explícita no obliga a modificar `@E2ETest`, no contamina otros contextos Spring y puede ser reutilizada por las features 017, 018 y 021. El valor retornado evita que cada prueba replique consultas o builders.

**Alternatives considered**:

- Registrar un `@Component` de test: rechazado porque se incorporaría a todo contexto escaneado y ocultaría cuándo se crean datos.
- Agregar el helper a `@E2ETest` mediante `@Import`: rechazado porque exige modificar soporte existente fuera de los dos archivos nuevos solicitados.
- Cargar SQL o JSON: rechazado porque duplica el mapeo de la entidad y ofrece una API menos útil a futuras pruebas.

## R2. Datos mínimos representativos

**Decision**: Usar cinco jugadores, uno por `League`, cubrir las cuatro posiciones y asignar dos posiciones al jugador de La Liga. Mantener `externalId`, nombres y equipos estables; dejar que PostgreSQL genere `id`.

**Rationale**: Es el conjunto más pequeño que representa las cinco ligas, todos los filtros y la semántica multivaluada de posiciones. Una combinación conocida de liga/equipo/posición devuelve exactamente un jugador. La identidad funcional estable permite comparar ejecuciones aunque las identidades de base se reinicien.

**Alternatives considered**:

- Más de cinco jugadores: válido pero innecesario para los requerimientos actuales y más costoso de mantener.
- Cinco jugadores con una posición cada uno: no alcanza para demostrar que el filtro de posición busca dentro de un conjunto.
- IDs de base fijados: rechazado porque contradice la generación `IDENTITY` y vuelve frágil la reutilización.

## R3. Autenticación y cliente e2e

**Decision**: Repetir en cada método el recorrido de registro/login mediante un helper privado de la clase y usar el Bearer resultante en un `RestClient` dirigido al puerto aleatorio.

**Rationale**: La limpieza e2e elimina usuarios entre métodos. Autenticar por HTTP conserva seguridad y serialización reales, y coincide con `SmokeE2ETest`. Registro/login son preparación y se ejecutan fuera de la medición de resiliencia.

**Alternatives considered**:

- Inyectar un token o desactivar seguridad: rechazado porque ya no sería el recorrido real solicitado.
- Compartir usuario entre tests: rechazado porque contradice la limpieza por método y crea dependencia de orden.
- Usar API key del registro: técnicamente válido, pero el usuario pidió un usuario logueado y el Bearer prueba exactamente ese estado.

## R4. Escritura compatible en Redis

**Decision**: Inyectar el `CacheManager`, abrir `CacheConfig.COMPETITION_TEAMS_CACHE` y ejecutar `put(código, FootballDataResponse)` para cada liga.

**Rationale**: `CacheConfig` configura ese caché con `JacksonJsonRedisSerializer<FootballDataResponse>` y TTL de Football-Data. Escribir por la abstracción `Cache` garantiza el mismo prefijo de clave, conversión y serializer que lee `@Cacheable`. No se copia configuración y no se modifica el archivo productivo.

**Alternatives considered**:

- Escribir bytes con `RedisConnection`: rechazado por el riesgo de errar prefijos o formato.
- Crear otro `JacksonJsonRedisSerializer` en el test: rechazado porque sólo imitaría la configuración y podría divergir.
- Invocar primero la fuente para poblar caché: imposible por diseño del perfil y contrario al aislamiento.
- Usar un caché en memoria: viola el requisito de Redis real y §5.2.

## R5. Prueba de que no se llamó a Football-Data

**Decision**: Usar evidencia conductual del flujo real: precargar las cinco claves y exigir `COMPLETED`, cinco procesados, cero ligas fallidas y los cinco datos exactos, con el destino externo inválido del perfil.

**Rationale**: En el código actual, `@Cacheable` sólo evita ejecutar `FootballDataAdapter.fetchCompetitionTeams` cuando encuentra la clave. Si lo ejecuta, `127.0.0.1:1` falla, el adapter lanza y el servicio incrementa `failedLeagues`; no existe un fallback posterior capaz de convertir eso en `COMPLETED`. El conjunto de aserciones distingue inequívocamente cinco cache hits de cualquier llamada externa.

**Alternatives considered**:

- Mockear `FootballDataAdapter`: rechazado porque reemplaza la integración que se quiere probar.
- Reemplazar el `RestClient` productivo por uno contador: requeriría reconfigurar beans y convertiría el escenario en una prueba híbrida.
- Inspeccionar logs: más frágil, indirecto y dependiente del formato de observabilidad.
- Levantar un servidor falso: agrega infraestructura y cambiaría el perfil que explícitamente representa una caída.

## R6. Medición de 100 ms para GET y 500 ms para sync

**Decision**: Envolver sólo cada llamada HTTP objetivo en `assertTimeout`, con `Duration.ofMillis(100)` para GET tras una solicitud equivalente de calentamiento no medida y `Duration.ofMillis(500)` para POST sync tras una sincronización equivalente de calentamiento no medida, y comprobar después estado 200 y payload.

**Rationale**: Excluye arranque, limpieza, fixtures y autenticación según la spec. El timeout no preemptivo evita dejar procesamiento HTTP vivo si se supera el límite y entrega una falla con duración observable. El puerto 1 en loopback rechaza conexión inmediatamente; los timeouts de cliente heredados de test quedan como cota de seguridad, no como espera esperada.

**Alternatives considered**:

- `@Timeout` en el método completo: rechazado porque incluiría preparación no comprendida por el criterio y no permitiría diferenciar los límites de GET y POST.
- Cronometrar manualmente con `System.nanoTime`: posible, pero JUnit expresa mejor el límite y el diagnóstico.
- Timeout preemptivo: rechazado porque puede interrumpir el hilo de prueba mientras el request continúa en otro hilo.

## R7. Catálogo intacto tras `FAILED`

**Decision**: Comparar dos respuestas HTTP completas de `GET /players`, ordenadas por `externalId`, antes y después de `POST /players/sync`, además de validar todos los contadores del resumen.

**Rationale**: Compara el contrato visible al usuario y todos los campos de `PlayerResponse`, incluidas posiciones e ID. No depende de detalles internos ni se limita al conteo. La limpieza previa de Redis garantiza que el resultado no pueda ser cacheado.

**Alternatives considered**:

- Sólo `repository.count()`: no detecta modificaciones de campos.
- Comparar entidades JPA en el mismo contexto: puede ocultar cambios por identidad/caché de persistencia y no valida el contrato HTTP.
- Sólo comprobar `status=FAILED`: no prueba la propiedad no destructiva.

## R8. Cobertura constitucional por test

| Test | §1.3 | §3.3 escenario 1 | §7 punto 6 |
|------|------|-------------------|------------|
| GET sin filtros | Demuestra lectura desde datos persistidos con ambas fuentes caídas. | Usa una base de jugadores construida por fixtures controlados. | Garantiza respuesta 200 y ≤100 ms durante la caída. |
| GET por liga | Demuestra que la degradación no elimina filtrado local. | Verifica la representación de las cinco ligas cargadas. | Garantiza respuesta filtrada sin 5xx. |
| GET por equipo | Demuestra consulta local independiente de fuentes. | Verifica atributos del catálogo construido. | Garantiza respuesta filtrada sin 5xx. |
| GET por posición | Demuestra consulta de asociaciones locales durante la caída. | Verifica posiciones variadas y multivaluadas. | Garantiza respuesta filtrada sin 5xx. |
| GET combinado | Demuestra operación completa del catálogo local. | Verifica consistencia conjunta de los datos construidos. | Garantiza respuesta con intersección correcta y ≤100 ms. |
| Sync fallido no destructivo | Conserva el último estado persistido cuando Football-Data falla. | Protege la base ya construida contra pérdida o modificación. | Garantiza HTTP 200/`FAILED`, POST ≤500 ms, GET ≤100 ms tras calentamiento y datos intactos. |
| Sync desde Redis | Opera con datos cacheados sin depender de Football-Data. | Construye cinco jugadores desde payloads externos simulados para las cinco ligas. | Garantiza HTTP 200/`COMPLETED`, POST ≤500 ms, GET ≤100 ms tras calentamiento y cero acceso externo. |

**Decision**: Estos siete tests conforman la evidencia mínima y suficiente para los tres puntos constitucionales pedidos.

**Rationale**: Cada requisito observable tiene un método aislado y el caso cacheado satisface específicamente la construcción desde datos externos simulados de §3.3.1, mientras los casos persistidos prueban la tolerancia exigida por §1.3 y §7.6.

**Alternatives considered**:

- Un único test largo: rechazado porque mezcla causas de falla y contradice “un test por requerimiento”.
- Tests unitarios del service/adapter: ya existen coberturas parciales, pero no prueban el recorrido HTTP con PostgreSQL y Redis reales.

## R9. Archivos protegidos y alcance

**Decision**: Crear sólo `E2EFixtures.java` y `ExternalSourcesDownE2ETest.java`; leer y reutilizar `CacheConfig`, `E2ETest`, DTOs, repositorio y perfil sin modificarlos.

**Rationale**: Toda capacidad requerida ya existe. La solución no necesita dependencias, configuración productiva ni cambios de contratos.

**Alternatives considered**:

- Agregar hooks o propiedades al perfil e2e: innecesario; los destinos y Redis ya están configurados.
- Modificar `CacheConfig` para exponer serializer: innecesario porque `CacheManager` ya expone la operación tipada correcta.
- Cambiar controllers para instrumentar llamadas: fuera de alcance y protegido.

## Resolution status

No quedan incógnitas técnicas ni decisiones que requieran consulta. Si la implementación descubriera que uno de los dos archivos nuevos no basta y el mínimo cambio afectara un archivo protegido, deberá detenerse y aplicar la regla de consulta de FR-018.
