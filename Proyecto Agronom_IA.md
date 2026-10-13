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
- Flujo de identificación directa en Android: Pl@ntNet recibe las fotos y devuelve especies candidatas; la app envía solo los nombres/datos botánicos a Space Bunny (`space-bunny-free`) para generar cuidados. Las imágenes nunca se envían a Space Bunny.
- HTTP y endpoint sin autenticación se limitan al desarrollo local en red privada; preparar TLS y autenticación antes de exponer.
- Android 17 (API 37), que usa el emulador actual y coincide con `targetSdk 37`, solicita `ACCESS_LOCAL_NETWORK` únicamente al guardar por HTTP en el backend local. El análisis remoto usa permiso `INTERNET`.
- Configuración directa para desarrollo: `PLANTNET_API_KEY` es obligatoria y `OPENCODE_API_KEY` opcional en `%USERPROFILE%\.gradle\gradle.properties`. Estas claves quedan en BuildConfig/APK y son extraíbles; para producción se requiere un backend intermediario.

## API y almacenamiento

`POST /agronomia-api/api/images.php` acepta `photos[]`, máximo cinco, JPEG/PNG/WebP decodificables hasta 15 MiB cada uno. Devuelve `201` con `success`, `batch_id` y rutas relativas solo tras escribir y confirmar todas las filas. La carpeta local continúa siendo `C:\xampp\plant-images`; MySQL conserva metadatos y rutas, no las imágenes.

El análisis ya no usa `api/analyze.php` (endpoint anterior retirado del proyecto y de XAMPP). Pl@ntNet recibe multipart JPEG en `POST https://my-api.plantnet.org/v2/identify/all?api-key=...` (`lang=es`, 3 candidatos). Space Bunny recibe solo texto por `POST https://opencode.ai/inference/v1/chat/completions`, modelo `space-bunny-free`; el JSON de cuidados se valida antes de presentarlo. Consulta [[Agronom_IA/README]] y `Agronom_IA/backend/README.md`.

## Pruebas y pendientes

Las verificaciones históricas de XAMPP/MySQL anotadas abajo son del bloque anterior y no verifican las nuevas APIs remotas. En este bloque: `:app:assembleDebug`, `:app:assembleRelease`, `:app:testDebugUnitTest` y `:app:connectedDebugAndroidTest` pasan en Pixel_8 AVD/API 37 (9 pruebas instrumentadas y 1 unitaria plantilla). La prueba real con servicios sigue pendiente porque requiere una clave válida de Pl@ntNet y conexión a Internet. Space Bunny Free podría aceptarse sin token; su disponibilidad externa puede cambiar.

- XAMPP: esquema importado, cuenta API de mínimo privilegio, endpoint de imágenes desplegado y carpeta con ACL restringida; PHP tiene GD, WebP y mysqli.
- Smoke tests previos del backend: GET responde 405, texto disfrazado de JPEG 415, lote de cinco imágenes 201 y lote de seis 422; se limpió el lote de prueba.
- En el emulador API 37 se probó la carga a Apache/MySQL y la concesión de permiso de red local. La captura real desde cámara aún requiere verificación manual.
- Para usar teléfono físico, el firewall aún necesita regla TCP entrante puerto 80 para perfil Private y LocalSubnet: `New-NetFirewallRule -DisplayName "Agronomia local XAMPP API" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 80 -Profile Private -RemoteAddress LocalSubnet`.
## Commits

- `635316e` — Implementa flujo móvil de imágenes y análisis.
- `e6892f0` — Añade API PHP, validación y persistencia MySQL.
- `ef6ed26` — Documenta instalación local, IA y verificaciones.
- `784fc3e` — Incluye archivos base del proyecto Android.

El repositorio está en la raíz de la bóveda. `.obsidian`, `Bienvenido.md`, `local.properties`, compilaciones y archivos de IDE quedan fuera de Git.
