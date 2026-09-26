# Feature Specification: Base frontend y acceso a LaFigu

**Feature Branch**: `007-frontend-app-base-auth`
**Created**: 2026-09-26
**Status**: Draft
**Input**: Base de LaFigu: sistema visual reutilizable, layout, ruteo, sesión en memoria y pantallas de ingreso y registro, dependiente de `specs/002-auth-usuario`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ingresar y abrir el álbum (Priority: P1)

Una persona con cuenta ingresa su usuario y contraseña en un carnet de LaFigu y accede al álbum. La sesión dura solamente mientras la página permanece cargada y nunca expone credenciales técnicas.

**Why this priority**: El acceso autenticado es el requisito mínimo para usar la aplicación y habilita las experiencias futuras.

**Independent Test**: Ingresar con una cuenta válida e inválida y verificar el estado de envío, los mensajes, la navegación y que ninguna credencial técnica sea visible o persista tras recargar.

**Acceptance Scenarios**:

1. **Given** una persona sin sesión en `/ingresar`, **When** envía usuario y contraseña válidos, **Then** abre `/album`, ve el layout autenticado y la sesión queda sólo en memoria.
2. **Given** credenciales inválidas, **When** intenta ingresar, **Then** permanece en la pantalla y ve `✕ Usuario o contraseña incorrectos.`.
3. **Given** un envío en curso, **When** intenta enviarlo nuevamente, **Then** el botón sigue deshabilitado, muestra `Abriendo…` y no se produce un segundo envío.
4. **Given** una sesión activa, **When** selecciona `Salir`, **Then** la sesión se elimina y navega a `/ingresar`.
5. **Given** una sesión activa, **When** recarga la página, **Then** pierde la sesión y vuelve al flujo sin sesión.

---

### User Story 2 - Crear el carnet de coleccionista (Priority: P1)

Una persona nueva crea su cuenta con usuario, email y contraseña. Al completar el registro, la aplicación intenta abrir automáticamente su sesión sin mostrar ni conservar la clave técnica recibida.

**Why this priority**: Incorpora usuarios nuevos de forma segura y los lleva directamente al producto principal.

**Independent Test**: Probar el registro, las validaciones, los conflictos de identidad, el acceso automático y la recuperación cuando ese acceso falla.

**Acceptance Scenarios**:

1. **Given** datos válidos en `/registro`, **When** registro y acceso automático son exitosos, **Then** navega a `/album` y ve por 4 segundos `¡Bienvenida/o, {username}! Tu álbum ya está abierto.` con semántica de estado.
2. **Given** datos ausentes, un email que el control nativo `type="email"` marca con `typeMismatch`, o una contraseña menor a 8 caracteres, **When** intenta registrarse, **Then** aparecen errores, el foco pasa al primer campo inválido y no se envía la solicitud.
3. **Given** un campo con error, **When** lo modifica, **Then** el error de ese campo se elimina.
4. **Given** un usuario o email ya registrado, **When** el servicio rechaza el registro, **Then** ve `✕ El usuario o el email ya está registrado.`.
5. **Given** un registro exitoso y un acceso automático fallido, **When** termina el intento, **Then** navega a `/ingresar`, encuentra el username precargado y ve `Cuenta creada. Ingresá con tu contraseña.`.
6. **Given** una respuesta de registro con `apiKey`, **When** se procesa, **Then** se descarta sin mostrarla, registrarla ni conservarla.

---

### User Story 3 - Navegar con y sin sesión (Priority: P2)

Una persona llega a cualquier URL y es conducida a la pantalla válida para su sesión. Quien ingresó reconoce el área activa, anticipa secciones futuras y puede salir.

**Why this priority**: El ruteo predecible y la estructura común sostendrán todas las entregas futuras.

**Independent Test**: Recorrer cada ruta con y sin sesión, incluida una desconocida, y verificar destinos y elementos deshabilitados.

**Acceptance Scenarios**:

1. **Given** una visita a `/`, **When** hay sesión, **Then** dirige a `/album`; **When** no hay sesión, **Then** dirige a `/ingresar`.
2. **Given** una visita sin sesión a `/album`, **When** se evalúa el acceso, **Then** dirige a `/ingresar`.
3. **Given** una sesión activa, **When** visita `/ingresar` o `/registro`, **Then** dirige a `/album`.
4. **Given** una URL desconocida, **When** se resuelve, **Then** dirige a `/` y aplica la regla según sesión.
5. **Given** `/album`, **When** se muestra, **Then** contiene `El álbum` y un casillero vacío con `Las figuritas llegan en la próxima feature`.
6. **Given** una sesión activa, **When** se inspecciona la navegación, **Then** `01 Álbum` está activo y los otros tres ítems muestran `PRONTO`, están deshabilitados y no navegan.

---

### User Story 4 - Reconocer LaFigu en cualquier pantalla (Priority: P2)

Una persona identifica LaFigu como un álbum de figuritas impreso en una experiencia coherente desde 360 px hasta escritorio y accesible por teclado.

**Why this priority**: El lenguaje distintivo y reutilizable evita fragmentación y retrabajo en futuras features.

**Independent Test**: Revisar ingreso, registro y álbum a 360 px, 900 px y 901 px o más, verificando apariencia, ausencia de scroll horizontal, adaptación, teclado y reducción de movimiento.

**Acceptance Scenarios**:

1. **Given** cualquier pantalla, **When** se presenta, **Then** usa fondo crema con puntos, tinta y bordes negros gruesos, sombras sólidas desplazadas, esquinas rectas, títulos condensados en mayúsculas y datos monoespaciados.
2. **Given** una pantalla sin sesión, **When** se muestra el header, **Then** presenta `LaFigu`, `MERCADO DE TOKENS` y `Crear cuenta`; en `/registro`, la acción es `Ingresar`.
3. **Given** una sesión y más de 900 px, **When** se muestra el header, **Then** incluye logo, nav, avatar con inicial, username y `Salir`.
4. **Given** una sesión y hasta 900 px, **When** se muestra el layout, **Then** la nav de cuatro ítems queda fija abajo y el username está oculto.
5. **Given** una pantalla de auth con al menos 901 px, **When** se muestra, **Then** a la izquierda aparecen el claim, subtítulo y tres figuritas ficticias rotadas -9°, 3° y 11°.
6. **Given** una pantalla de auth con hasta 900 px, **When** se muestra, **Then** la columna decorativa desaparece y el carnet sigue utilizable sin scroll horizontal.
7. **Given** navegación por teclado o movimiento reducido, **When** se usa la app, **Then** hay foco amarillo visible, etiquetas asociadas, orden de foco operativo y ausencia de movimiento no esencial.

### Edge Cases

- `400 validation_error` muestra el `message` del servicio sin sustituirlo.
- La falta de conexión o timeout conserva los datos editables y muestra el mensaje exacto definido.
- Una respuesta 5xx, desconocida o sin `{error, message}` se trata como inesperada y no expone datos internos.
- Un 401 de cualquier operación protegida elimina la sesión, navega a `/ingresar` y muestra el aviso exacto de sesión vencida.
- Username vacío o sólo con espacios, email vacío o con `typeMismatch`, y contraseña de 7 caracteres impiden registrarse; 8 caracteres satisfacen el mínimo. Para la matriz contractual, `abril@example.com` y `a+b@example.com` son válidos; `abril`, `abril@`, `@example.com` y `abril example.com` son inválidos.
- El avatar obtiene una inicial estable incluso con espacios periféricos o primer carácter no alfabético.
- Mensajes extensos y usernames largos no generan scroll horizontal desde 360 px.
- Los ítems `PRONTO` no se activan con puntero ni teclado.
- El toast de bienvenida permanece anunciable y visible en `/album` durante sus 4 segundos.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: La aplicación MUST llamarse `LaFigu` y mostrar `MERCADO DE TOKENS` como subtítulo.
- **FR-002**: La interfaz MUST seguir `álbum de figuritas impreso` y excluir bordes redondeados, degradés, sombras difusas, glassmorphism, modo oscuro, tipografías no definidas y kits visuales genéricos.
- **FR-003**: El plan MUST incluir `SISTEMA VISUAL` con valores exactos de colores, tipografías, medidas y textos, obligatorios para esta y futuras features.
- **FR-004**: La base MUST ofrecer patrones reutilizables para header, nav, barra móvil, pghead, carnet, figurita, casillero, banner, toast, campos, botones y estados.
- **FR-005**: Sin sesión, el header MUST mostrar marca y `Crear cuenta`; en `/registro`, MUST mostrar `Ingresar`.
- **FR-006**: Con sesión y más de 900 px, el header MUST mostrar marca, cuatro ítems, avatar con inicial, username y `Salir`.
- **FR-007**: Con sesión y hasta 900 px, la nav MUST ser inferior fija con cuatro ítems y el username MUST ocultarse.
- **FR-008**: `01 Álbum` MUST ser el único ítem activo; `02 Ranking`, `03 Mercado` y `04 Mi portfolio` MUST mostrar `PRONTO`, declarar `aria-disabled="true"`, no ser links ni tener rutas.
- **FR-009**: La aplicación MUST admitir `/ingresar`, `/registro` y `/album`, siendo `/album` protegida.
- **FR-010**: `/` MUST dirigir a `/album` con sesión y a `/ingresar` sin sesión.
- **FR-011**: `/album` sin sesión MUST dirigir a `/ingresar`; las rutas auth con sesión a `/album`; una ruta desconocida a `/`.
- **FR-012**: `/album` MUST mostrar sólo `El álbum` y un casillero con `Las figuritas llegan en la próxima feature`.
- **FR-013**: `/ingresar` MUST presentar carnet azul `Ingresar`, campos `Usuario` y `Contraseña`, botón `Abrir mi álbum` y link `Creá tu carnet de coleccionista`.
- **FR-014**: Un ingreso exitoso MUST iniciar sesión en memoria y navegar a `/album`.
- **FR-015**: `/registro` MUST presentar carnet rojo `Carnet de coleccionista`, campos `Usuario`, `Email`, `Contraseña`, hint `Mínimo 8 caracteres.`, botón `Crear mi carnet` y link `Ingresá`.
- **FR-016**: El registro MUST validar al enviar los tres campos obligatorios, una contraseña de al menos 8 caracteres y el email mediante un input `type="email"`: es inválido si está vacío o si su `HTMLInputElement.validity.typeMismatch` es `true`. El frontend MUST NOT agregar una regex de email propia; el backend sigue siendo la autoridad final y cualquier `400 validation_error` muestra literalmente su `message`.
- **FR-017**: La validación MUST enfocar el primer campo inválido y limpiar el error individual al editarlo.
- **FR-018**: Tras registrar, la aplicación MUST intentar automáticamente ingresar con el mismo username y contraseña.
- **FR-019**: Si ese ingreso funciona, MUST navegar a `/album` y mostrar por 4 s `¡Bienvenida/o, {username}! Tu álbum ya está abierto.` con `role="status"`.
- **FR-020**: Si falla, MUST navegar a `/ingresar`, precargar username y mostrar `Cuenta creada. Ingresá con tu contraseña.` como información.
- **FR-021**: La `apiKey` de registro MUST descartarse y MUST NOT mostrarse, loguearse ni almacenarse.
- **FR-022**: La persona MUST usar sólo username y contraseña; MUST NOT ver, copiar, ingresar ni administrar credenciales técnicas.
- **FR-023**: La sesión MUST contener sólo token, tipo de token y username, exclusivamente en memoria de la carga actual.
- **FR-024**: La sesión MUST NOT persistirse en almacenamiento local, de sesión, cookies, bases del navegador ni otro medio; recargar MUST perderla.
- **FR-025**: `Salir` MUST borrar la sesión y navegar a `/ingresar`.
- **FR-026**: Todo 401 protegido MUST borrar sesión, navegar a `/ingresar` y mostrar `Tu sesión venció. Volvé a ingresar.`.
- **FR-027**: Durante un envío, el botón MUST deshabilitarse, mostrar `Abriendo…` o `Creando…` y evitar duplicados.
- **FR-028**: `401 invalid_credentials` MUST mostrar con `role="alert"` `✕ Usuario o contraseña incorrectos.`.
- **FR-029**: `409 duplicate_user` MUST mostrar con `role="alert"` `✕ El usuario o el email ya está registrado.`.
- **FR-030**: `400 validation_error` MUST mostrar en banner de error el `message` recibido.
- **FR-031**: Falta de conexión o timeout MUST mostrar con `role="alert"` `✕ No se pudo conectar con el servidor. Intentá de nuevo.`.
- **FR-032**: Una respuesta 5xx o inesperada MUST mostrar con `role="alert"` `✕ Algo salió mal. Intentá de nuevo en unos minutos.`.
- **FR-033**: Desde 901 px, auth MUST mostrar tres figuritas de nombres inventados, nunca jugadores reales, superpuestas a -9°, 3° y 11°.
- **FR-034**: `/ingresar` MUST mostrar `Coleccioná tokens de los CRACKS` con `CRACKS` rojo y `Jugadores de las 5 grandes ligas de Europa. Su valor se mueve con su rendimiento.` en tipografía monoespaciada.
- **FR-035**: `/registro` MUST mostrar `Tu álbum ARRANCA con 1.000 créditos` con `ARRANCA` rojo y `Creá tu cuenta y empezá a armar tu colección.` en tipografía monoespaciada.
- **FR-036**: La interfaz MUST funcionar desde 360 px hasta escritorio sin scroll horizontal.
- **FR-037**: Todo campo MUST tener label asociado; los errores MUST ser anunciables y todas las acciones operables con teclado.
- **FR-038**: Todo control MUST tener foco visible amarillo y la experiencia MUST respetar `prefers-reduced-motion`.
- **FR-039**: Auth MUST cumplir `specs/002-auth-usuario/contracts/auth-api.md`; si difiere, ese contrato prevalece.
- **FR-040**: La feature MUST NOT incluir catálogo real, detalle, ranking operativo, mercado, portfolio, saldo, ticker, recuperar contraseña, recordar sesión, exponer `apiKey` ni cambios backend.

### Key Entities

- **Sesión activa**: Estado efímero con token, tipo de token y username; desaparece al salir, vencer o recargar.
- **Credenciales de acceso**: Username y contraseña usados sólo para el intento actual.
- **Solicitud de registro**: Username, email y contraseña para crear una cuenta e intentar el acceso automático.
- **Respuesta de registro**: Datos de la cuenta; puede incluir una `apiKey` que se descarta sin exposición ni persistencia.
- **Mensaje de interfaz**: Banner de error/información o toast, con texto y semántica accesible.
- **Figurita decorativa**: Carta visual con identidad inventada y datos fijos, sin representar jugadores reales.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una persona con credenciales válidas completa el ingreso en menos de 2 minutos sin encontrarse con credenciales técnicas.
- **SC-002**: Una persona nueva completa registro y acceso automático en menos de 3 minutos cuando los servicios responden correctamente.
- **SC-003**: El 100% de combinaciones documentadas de ruta y sesión produce el destino esperado; ningún ítem `PRONTO` navega.
- **SC-004**: El 100% de errores definidos muestra exactamente el mensaje y semántica indicados, sin doble envío.
- **SC-005**: Tras salir o recargar, el 100% de verificaciones confirma que no quedan datos de sesión ni `apiKey` persistidos.
- **SC-006**: Las tres pantallas funcionan entre 360 px y escritorio sin scroll horizontal, contenido inaccesible ni superposición con la barra móvil.
- **SC-007**: El 100% de campos y acciones principales funciona sólo con teclado, foco visible, labels asociados y anuncios accesibles.
- **SC-008**: Ingreso, registro y álbum cumplen todos los criterios del `SISTEMA VISUAL` en cada tamaño objetivo y ninguno de los estilos prohibidos aparece.
- **SC-009**: Al menos 9 de cada 10 participantes identifican la interfaz como álbum o colección de figuritas sin recibir esa descripción.
- **SC-010**: Las futuras pantallas pueden adoptar los patrones base sin redefinir sus reglas visuales fundamentales.

## Assumptions

- `specs/002-auth-usuario/contracts/auth-api.md`, verificado el 2026-09-26, es la fuente de verdad y coincide con el contrato provisto.
- Los endpoints públicos de registro e ingreso ya existen; esta feature no los modifica.
- Perder la sesión al recargar es intencional y se documentará.
- La contraseña se retiene sólo el tiempo imprescindible para el ingreso automático.
- Los valores visuales exactos se definirán obligatoriamente y copiarán literalmente en `SISTEMA VISUAL` del plan.
- 900 px es inclusivo para layout móvil; la decoración aparece desde 901 px.
- Los tres destinos `PRONTO` no tendrán rutas vacías.
- El tratamiento común de 401 queda listo para operaciones protegidas futuras.

## Dependencies

- `specs/002-auth-usuario/spec.md` y, con precedencia contractual, `specs/002-auth-usuario/contracts/auth-api.md`.
- Constitución del proyecto: frontend responsivo independiente y comunicación con backend sólo por HTTP/REST.

## Out of Scope

- Catálogo real, detalle, ranking funcional, mercado y portfolio.
- Saldo en header y cinta de cotizaciones.
- Recuperación de contraseña y `recordarme`.
- Exposición, copiado, almacenamiento o administración de `apiKey`.
- Persistencia de sesión entre recargas.
- Cambios en el backend.
