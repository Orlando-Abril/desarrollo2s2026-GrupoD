# LaFigu frontend

Base web de LaFigu construida con React 19 y Vite.

## Configuración local

1. Copiá `.env.example` como `.env`.
2. Ajustá `VITE_API_BASE_URL` si el backend no está disponible en `http://localhost:8080`.
3. Instalá las dependencias con `npm ci`.
4. Iniciá la aplicación con `npm run dev`.

Vite debe conservar el puerto `5173`: el backend acepta CORS desde `http://localhost:5173`.

## Verificaciones

- `npm test`: ejecuta los tests una vez.
- `npm test -- --coverage`: ejecuta los tests y genera `coverage/lcov.info` para SonarCloud.
- `npm run lint`: analiza el código con Oxlint.
- `npm run build`: genera el build de producción.

## Sesión

El token, su tipo y el nombre de usuario se guardan únicamente en memoria mediante el contexto de React. La aplicación no los escribe en `localStorage`, `sessionStorage`, cookies ni IndexedDB. Por diseño, recargar la página cierra la sesión y redirige a Ingresar.
