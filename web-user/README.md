# Agronom-IA · Web de usuario

Web (React + Vite) para el **servicio de usuario (Spring Boot)**. Hace lo mismo que
la app móvil desde el navegador: subir fotos, identificarlas con Pl@ntNet +
Gemini, leer la ficha de 8 categorías y conversar con el asistente botánico.

## Requisitos

- Node 18+ (probado con Node 24).
- Spring Boot en `http://127.0.0.1:8081` (ver `backend-spring/README.md`).

## Cómo ejecutarlo

```powershell
cd web-user
npm install
npm run dev
```

Abre `http://localhost:5174`.

Vite hace proxy de `/agronomia-api` hacia Spring, así que no hace falta CORS en
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
- **Candidatas**: cuando Pl@ntNet duda, muestra una foto de referencia de
  Wikimedia Commons, nombres, familia, similitud y una descripción de rasgos
  distintivos generada por la IA. Los términos del glosario se pueden pulsar;
  la ficha se genera sin volver a llamar a Pl@ntNet.
- **Ficha**: nombre común y científico, familia, confianza y las 8 categorías
  (descripción, luz, riego, suelo, clima, floración, usos y cuidados) con
  glosario pulsable, materiales recomendados y consejos generales. Si la IA no
  conoce la especie se muestra «Información insuficiente» en vez de una ficha
  vacía.
- **Mi jardín**: últimas 30 fichas guardadas en el navegador con una miniatura
  de la foto.
- **Chat flotante**: consulta sobre la planta detectada; el contexto viaja a
  Spring Boot para que la respuesta sea específica.

## Estructura

    src/
      api.js              cliente de Spring (fotos, identify, confirm, chat)
      App.jsx             sesión, navegación y chat
      storage.js          sesión, jardín y miniaturas en localStorage
      components/         Header, ChatPanel, Markdown
      pages/              Login, Scanner, Result, Garden
      styles.css          paleta y estilos

## Notas

- La sesión web guarda el token firmado de Spring junto al usuario y su rol;
  el backend usa ese token para asociar identificaciones y mensajes a la cuenta.
- `npm run build` genera `dist/` para publicarlo como sitio estático.

## Probar con tu modelo

1. Si la demo anterior sigue en ejecución, detén sus procesos. Desde `backend-spring`, recompila con `.\mvnw.cmd -DskipTests package`. Desde la raíz, ejecuta `scripts\iniciar-demo.ps1` y comprueba que Flyway aplicó V2 y V3 con la cuenta de migración.
2. Entra en Ajustes → Modelos de IA. Elige un modelo del formulario o rellena el nombre, URL HTTPS e identificador. Introduce la clave en el campo de contraseña.
3. Para un modelo pequeño, indica su ventana y el tope de salida. Prueba la conexión y revisa la latencia, visión y JSON.
4. Activa el proveedor y confirma el consentimiento. Escanea una planta y revisa que aparezca en el jardín al recargar. Prueba el chat y comprueba que se explique el estado del control de fotos.
5. Cambia el tema, guárdalo y verifica su persistencia al volver a entrar. Prueba el botón Atrás y el enlace directo de una ficha.
6. Prueba la eliminación únicamente sobre una conversación descartable. Vuelve al modelo del sistema y elimina el proveedor de prueba si ya no lo necesitas.

El router usa History API con importaciones dinámicas para evitar una dependencia de navegación. La caché en memoria deduplica las lecturas durante 30 s y se invalida al mutar o salir. El servidor de producción debe servir `index.html` para rutas de interfaz y conservar la API como proxy separado. Para CSP, el script inicial de apariencia se sirve desde el mismo origen y funciona con `script-src 'self'`.

`npm.cmd test` verifica preferencias, caducidad, jardín, solicitudes de proveedor, borrado y escape de Markdown con los recursos locales.
