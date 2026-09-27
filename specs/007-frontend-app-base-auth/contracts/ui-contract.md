# LaFigu UI Contract

## Routes

| Path | Access | Outcome |
|---|---|---|
| `/` | any | sesión → `/album`; sin sesión → `/ingresar` |
| `/ingresar` | public-only | con sesión → `/album`; sin sesión → LoginPage |
| `/registro` | public-only | con sesión → `/album`; sin sesión → RegisterPage |
| `/album` | protected | sin sesión → `/ingresar`; con sesión → AlbumPage |
| `*` | any | redirect a `/`, luego regla de raíz |

Todos los redirects de guards y fallback usan reemplazo de historial.

## Navigation

Header y BottomNav consumen una única constante ordenada:

| id | numero | label | ruta | habilitado |
|---|---|---|---|---|
| album | 01 | Álbum | `/album` | true |
| ranking | 02 | Ranking | ninguna | false |
| mercado | 03 | Mercado | ninguna | false |
| portfolio | 04 | Mi portfolio | ninguna | false |

Un ítem deshabilitado no es link, no tiene handler, declara `aria-disabled="true"` y muestra `PRONTO`.

## Component contracts

### Button

- Variantes: `primary | secondary`.
- Acepta tipo, disabled, contenido y handlers estándar.
- La página controla `Abriendo…`/`Creando…`; disabled impide doble submit.

### Field

- Entradas: id/name, label, type, value, handler, hint, error y ref de foco.
- Label siempre asociado. Error produce `aria-invalid`, descripción asociada y reemplaza/acompaña al hint sin perder contexto.

### Banner

- Variantes: `error | info`.
- Error final tiene `role="alert"` y exactamente un prefijo `✕`; Banner es el único responsable de agregarlo si recibe texto sin prefijo.
- Info presenta el mensaje sin semántica de error.

### Toast

- `role="status"`, visible 4.000 ms.
- Debe sobrevivir la navegación registro→álbum y quedar sobre BottomNav en móvil.

### Carnet

- Variantes: `login | register`.
- Slots: cabecera contractual, formulario y pie.
- Su título es el único `h1` en pantallas auth; el claim decorativo no es `h1`.

### Figurita

- Props: `{id, fullName, team, league, positions, nationality, age, marketValue}`.
- `<article aria-label="{fullName}, {team}">`, no interactivo.
- Primera posición según orden de dominio; array vacío → `—`.
- `nationality` o `age` null → `—`.
- SVG decorativo queda fuera del árbol accesible.
- Hover/foil sólo cuando la carta está dentro de una grilla y sólo si no hay movimiento reducido.

### Casillero

- Variantes: `vacío | cargando`.
- Props de presentación: marca principal y texto.
- Cargando pulsa a 1.2 s salvo movimiento reducido.

### EstadoPanel

- Props: sello, título, texto, acción secundaria opcional.

### Sello

- Variantes: `error | neutral`.

## Auth screens

### Login

- Carnet azul: `INGRESAR`, `SOCIO`.
- Campos: `Usuario`, `Contraseña`.
- Submit: `Abrir mi álbum` → `Abriendo…`.
- Link: `Creá tu carnet de coleccionista`.
- Decoración escritorio: `Coleccioná tokens de los CRACKS`, `CRACKS` rojo; subtítulo exacto de spec; tres figuritas ficticias.

### Register

- Carnet rojo: `CARNET DE COLECCIONISTA`, `NUEVO`.
- Campos: `Usuario`, `Email`, `Contraseña`; hint `Mínimo 8 caracteres.`.
- Submit: `Crear mi carnet` → `Creando…`.
- Link: `Ingresá`.
- Validación al submit; foco al primer inválido; error individual se limpia al editar.
- Email: input `type="email"`, obligatorio e inválido sólo cuando está vacío o `validity.typeMismatch` es `true`; no se implementa una regex propia. La matriz acepta `abril@example.com` y `a+b@example.com`, y rechaza `abril`, `abril@`, `@example.com` y `abril example.com`. El backend conserva autoridad final y su `400 validation_error` prevalece.
- Registro 201 dispara login automático con las mismas credenciales.
- Auto-login exitoso: session, `/album`, toast exacto.
- Auto-login fallido: `/ingresar`, sólo username precargado y banner info exacto.
- `apiKey` nunca llega al DOM.

## Responsive and accessibility

- 360 px en adelante sin scroll horizontal.
- ≤900 px: Header 56px, nav superior oculta, BottomNav fija, username y decoración auth ocultos.
- ≥901 px: Header 64px, nav superior y decoración auth visibles, sin BottomNav.
- El contenido reserva espacio inferior para barra/toast.
- Todos los controles operables por teclado; foco global amarillo visible.
- Errores de campo asociados; banners error `role="alert"`; toast `role="status"`.
- `prefers-reduced-motion` elimina transiciones/animaciones no esenciales y hover transformado.

## Album placeholder

- Título `El álbum`.
- Casillero vacío con marca `···` y `Las figuritas llegan en la próxima feature`.
- No hay catálogo, requests de jugadores ni navegación de detalle.

## Visual conformance

El apartado `SISTEMA VISUAL` de `plan.md` es normativo. Ningún componente puede introducir variantes, colores, radios, sombras, fuentes o breakpoints no definidos allí.
