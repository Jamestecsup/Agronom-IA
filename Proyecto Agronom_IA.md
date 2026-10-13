# Proyecto Agronom_IA

## Estado inicial

La bóveda contiene el proyecto `Agronom_IA` de Android Studio. Al inicio era una app Kotlin con Jetpack Compose, Material 3 y plantilla; no tenía API, cámara, selector, persistencia ni nota de proyecto. XAMPP tenía Apache y MySQL activos. La bóveda no tenía un repositorio Git; para cumplir los commits del proyecto se creó uno en la raíz de la bóveda, sin incluir las preferencias locales `.obsidian`.

## Arquitectura y decisiones

- UI Compose; estado y acciones asíncronas en un ViewModel con `StateFlow`.
- Selector del sistema para galería, intent `TakePicture` con URI de `FileProvider` para cámara. No se requieren permisos de cámara/almacenamiento para estos contratos del sistema.
- El recurso de galería se copia con límite de tamaño al espacio privado, se decodifica a resolución acotada y se convierte a JPEG. HEIF/HEIC depende del decodificador del dispositivo; el servidor recibe los bytes normalizados.
- PHP/Apache funciona como puente local; Android nunca habla con MySQL. En emulador, Android accede al host con `10.0.2.2`; en teléfono usa la IP LAN del PC.
- Imágenes en `C:\xampp\plant-images/<batch UUID>/<image UUID>.<ext>`, fuera del directorio público `htdocs`. MySQL guarda ruta relativa, UUID, lote, MIME, tamaño, dimensiones, estado mínimo y fecha. No se guardan BLOB.
- PHP valida otra vez conteo, tamaño, contenido detectado (`fileinfo`), dimensiones (`getimagesize`) y decodificación GD. El lote es atómico a nivel lógico: staging, transacción SQL, limpieza de archivos si falla.
- El endpoint de análisis es configurable para OpenCode Zen Responses (`gpt-5.4-mini`) o OpenAI Responses; procesa imágenes solo tras la acción explícita del usuario. La respuesta estructurada incluye confianza y advertencias; el resultado no se trata como identificación botánica certificada.
- HTTP y endpoint sin autenticación se limitan al desarrollo local en red privada; preparar TLS y autenticación antes de exponer.
- Android 17 (API 37), que usa el emulador actual y coincide con `targetSdk 37`, exige el permiso runtime `ACCESS_LOCAL_NETWORK` para sockets hacia la red local. El flujo lo solicita al guardar o analizar y continúa solo si el usuario lo concede.
- El backend acepta `PLANT_AI_API_URL` y `PLANT_AI_MODEL`. Para OpenCode Zen usar `https://opencode.ai/inference/openai/v1/responses` y `gpt-5.4-mini`; falta guardar la clave del usuario en `C:\Users\lenovo\AppData\Local\Agronomia\apache-ai.conf` y reiniciar Apache para ejecutar una inferencia real.

## API y almacenamiento

`POST /agronomia-api/api/images.php` acepta `photos[]`, máximo cinco, JPEG/PNG/WebP válidos hasta 15 MiB cada uno. Devuelve `201` con `success`, `batch_id` y rutas relativas solo tras escribir y confirmar todas las filas. `POST /agronomia-api/api/analyze.php` recibe `batch_id` y devuelve JSON estructurado de la IA; requiere `PLANT_AI_API_KEY`. Consulta [[Agronom_IA/README]] y `Agronom_IA/backend/README.md`.

## Pruebas y pendientes

- Verificado después de los cambios: `:app:assembleDebug`, `:app:assembleRelease` y `:app:testDebugUnitTest` pasan (la prueba unitaria preexistente es la prueba plantilla de suma).
- `php -l backend/api/images.php`: sin errores de sintaxis; PHP local confirma GD con WebP y mysqli habilitados.
- Smoke tests PHP locales: GET responde 405 JSON; archivo de texto enviado como JPEG responde 415; imagen válida sin configuración devuelve 500 por `PLANT_UPLOAD_DIR` ausente antes de crear archivos (comportamiento esperado).
- Android Emulator Pixel 8 (API 37): se reprodujo el timeout inicial, se comprobó que expiraba al conectar al host antes del POST y se verificó el permiso nuevo con una carga real. Al conceder `ACCESS_LOCAL_NETWORK`, Apache respondió HTTP 201 y la pantalla confirmó el registro en servidor/MySQL.
- Esta verificación dejó dos lotes de prueba (uno por cURL y otro desde la app) en `agronomia.plant_images` y sus archivos en `C:\xampp\plant-images`; identificadores `4b6c158c-2a91-4c1b-949a-551622095bcb` y `b2cc9326-549a-4460-bcd0-7e52bd2b78c2`.
- En XAMPP ya se importó el esquema, se creó una cuenta API aleatoria de mínimo privilegio, se desplegó la API en `htdocs/agronomia-api`, se creó `C:\xampp\plant-images` con ACL restringida, se ajustó php.ini (16 MiB por archivo, 80 MiB POST) y Apache reinició con la configuración local protegida.
- Verificación real contra Apache/MySQL: GET 405, texto disfrazado de imagen 415, lote de cinco PNG 201 con cinco filas y archivos correspondientes, lote de seis 422; se eliminaron los lotes de prueba y se verificó la limpieza. El endpoint de IA devuelve 503 hasta configurar la clave.
- La selección interactiva y el permiso de red local se probaron en el emulador; la captura real mediante cámara todavía requiere verificación manual.
- La integración de IA está implementada con OpenAI Responses, `gpt-4.1-mini`, JSON estructurado y respuesta en Compose. El modelo admite imágenes ([ficha oficial](https://developers.openai.com/api/docs/models/gpt-4.1-mini)). Apache incluye el archivo privado `C:\Users\lenovo\AppData\Local\Agronomia\apache-ai.conf`; falta agregar allí `PLANT_AI_API_KEY`. La clave no estaba configurada y por eso no se ejecutó una inferencia real.
- La regla de firewall de Windows para permitir Apache en el perfil privado y la subred local no se pudo crear: Windows respondió “Acceso denegado” sin elevación administrativa. Para usar un teléfono físico, agregar como administrador una regla TCP entrante puerto 80, perfil Private y dirección remota LocalSubnet.
- La regla de firewall todavía requiere una consola administrativa: `New-NetFirewallRule -DisplayName "Agronomia local XAMPP API" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 80 -Profile Private -RemoteAddress LocalSubnet`.

## Commits

- `635316e` — Implementa flujo móvil de imágenes y análisis.
- `e6892f0` — Añade API PHP, validación y persistencia MySQL.
- `ef6ed26` — Documenta instalación local, IA y verificaciones.
- `784fc3e` — Incluye archivos base del proyecto Android.

El repositorio está en la raíz de la bóveda. `.obsidian`, `Bienvenido.md`, `local.properties`, compilaciones y archivos de IDE quedan fuera de Git.
