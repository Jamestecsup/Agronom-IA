# Agronom-IA · Web de usuario

Web (React + Vite) para el **servicio de usuario (Django)**. Hace lo mismo que
la app móvil desde el navegador: subir fotos, identificarlas con Pl@ntNet +
Gemini, leer la ficha de 8 categorías y conversar con el asistente botánico.

## Requisitos

- Node 18+ (probado con Node 24).
- Django en `http://127.0.0.1:8000` (ver `backend-django/README.md`).

## Cómo ejecutarlo

```powershell
cd web-user
npm install
npm run dev
```

Abre `http://localhost:5174`.

Vite hace proxy de `/agronomia-api` hacia Django, así que no hace falta CORS en
desarrollo.

## Cuenta demo

| Usuario | Contraseña | Rol |
|---|---|---|
| `agricultor` | `Campo12345` | user |

Se crea con `py manage.py seed_demo`. También puedes registrarte desde la
pantalla de acceso (crea cuentas con rol `user`).

## Qué incluye

- **Escáner**: hasta 5 fotos (arrastrar o elegir), selector de órgano
  (automático, flor, hoja, fruto, corteza, planta entera) y estados de
  identificación.
- **Candidatas**: cuando Pl@ntNet duda, muestra las especies parecidas con su
  similitud y permite elegir; la ficha se genera sin volver a llamar a Pl@ntNet.
- **Ficha**: nombre común y científico, familia, confianza y las 8 categorías
  (descripción, luz, riego, suelo, clima, floración, usos y cuidados) con
  glosario pulsable, materiales recomendados y consejos generales. Si la IA no
  conoce la especie se muestra «Información insuficiente» en vez de una ficha
  vacía.
- **Mi jardín**: últimas 30 fichas guardadas en el navegador con una miniatura
  de la foto.
- **Chat flotante**: consulta sobre la planta detectada; el contexto viaja a
  Django para que la respuesta sea específica.

## Estructura

    src/
      api.js              cliente de Django (fotos, identify, confirm, chat)
      App.jsx             sesión, navegación y chat
      storage.js          sesión, jardín y miniaturas en localStorage
      components/         Header, ChatPanel, Markdown
      pages/              Login, Scanner, Result, Garden
      styles.css          paleta y estilos

## Notas

- La sesión web guarda solo el usuario y su rol (el backend de usuario no
  emite token; el panel de administración sí lo hace).
- `npm run build` genera `dist/` para publicarlo como sitio estático.
