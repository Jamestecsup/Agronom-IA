# Agronom-IA · Web de administración

Panel web (React + Vite) para el servicio **Spring Boot**. Permite entrar con
una cuenta de rol `admin`, ver el resumen de actividad, gestionar usuarios y
revisar las identificaciones y el chat generados por el backend Django.

## Requisitos

- Node 18+ (probado con Node 24).
- Spring Boot en `http://127.0.0.1:8081`.
- MySQL de XAMPP encendido con las tablas migradas por Django.

## Cómo ejecutarlo

```powershell
cd web-admin
npm install
npm run dev
```

Abre `http://localhost:5173`.

Vite hace proxy de `/api` hacia Spring (`vite.config.js`), así que en
desarrollo no hace falta configurar CORS. En producción sirve el contenido de
`dist/` y apunta `/api` al servicio Spring.

## Acceso

| Usuario | Contraseña | Rol |
|---|---|---|
| `admin` | `Admin12345` | admin (puede entrar) |
| `agricultor` | `Campo12345` | user (el panel lo rechaza con 403) |

Se crean con `py manage.py seed_demo` en `backend-django/`. Solo para
desarrollo local.

## Pantallas

- **Resumen**: usuarios, identificaciones, fotos, mensajes, actividad de 14
  días y especies más identificadas.
- **Usuarios**: búsqueda, cambio de rol y activar/desactivar cuentas. El
  servidor impide que un administrador se quite su propio rol.
- **Identificaciones**: especie, nombre común, familia, confianza y fecha.
- **Chat**: últimos mensajes del asistente, con filtro por autor.

## Estructura

    src/
      api.js            cliente HTTP + sesión (token en localStorage)
      App.jsx           login o panel según la sesión
      components/Shell.jsx
      pages/            Login, Dashboard, Users, Plants, Chats
      format.js         fechas y porcentajes en español
      styles.css        paleta y estilos

## Notas

- El token lo emite Spring (HMAC, caduca a las 8 horas). Si caduca o el
  servidor responde 401, la app vuelve al login.
- `npm run build` genera `dist/` para publicarlo como sitio estático.
