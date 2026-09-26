# Data Model: Autenticación JWT en endpoints protegidos

## Resultado del análisis

Esta feature **no agrega ni modifica datos persistentes**. No requiere tablas, columnas, migraciones, entidades de dominio ni cambios en `User`, `ApiKey` o sus repositories.

Los únicos datos involucrados son credenciales recibidas en una solicitud y un estado de autenticación transitorio que existe sólo mientras se procesa esa solicitud.

## Objetos conceptuales transitorios

### Credencial Bearer recibida

| Atributo conceptual | Regla |
|---|---|
| Esquema | Sólo activa la validación JWT cuando comienza con `Bearer ` |
| Token | Segmento posterior al prefijo; debe tener firma válida y no estar vencido |
| Subject | Username emitido por el login; se convierte en el principal autenticado |
| Vigencia | Usa la expiración existente; no se extiende ni renueva |

No se persiste ni se registra el valor del token.

### Credencial API key recibida

Representa el valor presentado en `X-API-KEY`. Su modelo persistente y validación ya existen y no cambian. Sólo se evalúa cuando no hay un Bearer o cuando Authorization usa otro esquema.

### Identidad autenticada de la solicitud

| Atributo conceptual | JWT válido | API key válida |
|---|---|---|
| Principal | Subject/username del JWT | Username del owner o prefijo actual |
| Authorities | Vacías | Vacías |
| Autenticada | Sí | Sí |
| Persistencia nueva | Ninguna | Ninguna |

## Estados y transiciones

```text
Solicitud en ruta protegida
├── Authorization comienza con "Bearer "
│   ├── JWT válido   → contexto autenticado con username → continuar
│   └── JWT inválido → 401 de token                       → terminar
└── Sin Bearer
    ├── API key válida              → contexto autenticado actual → continuar
    └── API key ausente/no válida   → 401 actual de API key        → terminar
```

En rutas públicas no ocurre ninguna de estas transiciones: ambos mecanismos se omiten.

## Invariantes

- Una solicitud protegida necesita una sola credencial válida; nunca se exigen ambas.
- La presencia de un Bearer selecciona definitivamente la rama JWT.
- Un Bearer inválido nunca transiciona a la rama API key.
- Un contexto ya autenticado por JWT no es reemplazado ni rechazado por el filtro de API key.
- Ningún JWT ni API key en claro se persiste como parte de esta feature.
