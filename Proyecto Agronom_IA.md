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
- Flujo conversacional Android: Pl@ntNet recibe las fotos y devuelve especies candidatas; la app envía solo los datos botánicos, el historial textual reciente y la pregunta a Space Bunny (`space-bunny-free`) mediante el servidor OpenCode local. Las imágenes nunca se envían a Space Bunny.
- HTTP y endpoint sin autenticación se limitan al desarrollo local en red privada; preparar TLS y autenticación antes de exponer.
- Android 17 (API 37), que usa el emulador actual y coincide con `targetSdk 37`, solicita `ACCESS_LOCAL_NETWORK` cuando se usa XAMPP o el servidor OpenCode del host. Pl@ntNet requiere permiso `INTERNET`.
- Configuración directa de Pl@ntNet para desarrollo: `PLANTNET_API_KEY` en `%USERPROFILE%\.gradle\gradle.properties` (se compila en el APK y puede extraerse). Space Bunny Free se ejecuta mediante el servidor local de OpenCode CLI (`127.0.0.1:4096` en el equipo; `10.0.2.2` desde emulador); no requiere añadir su clave de API al APK. Para producción, trasladar Pl@ntNet a un backend intermediario.

## Verificación de análisis IA (2026-10-13)

- La llamada directa al endpoint de inferencia de OpenCode para Space Bunny Free devolvió HTTP 403 `FreeTierError`. Se sustituyó por el servidor local OpenCode CLI, enlazado solo a `127.0.0.1:4096`; desde Android Emulator se accede vía `10.0.2.2:4096`.
- Flujo real probado en Pixel_8 AVD/API 37: imagen JPEG seleccionada y validada por la app; Pl@ntNet identificó `Hibiscus arnottianus`, familia Malvaceae, 40,2 % y alternativas. La app presentó la coincidencia como incierta y conservó las alternativas.
- Space Bunny recibió exclusivamente texto y devolvió riego, iluminación, toxicidad y advertencias; la primera respuesta tenía JSON/carácteres mezclados y fue rechazada. Un reintento correctivo produjo una guía JSON válida que se mostró en la app.
- Verificaciones del rediseño (2026-10-13): `:app:assembleDebug`, `:app:assembleRelease`, `:app:testDebugUnitTest` y `:app:connectedDebugAndroidTest` (15 pruebas instrumentadas, todas correctas).
- Para volver a analizar, mantener `opencode serve --hostname 127.0.0.1 --port 4096` activo. Rotar las claves compartidas durante la configuración; Pl@ntNet se compila en el APK local.

## API y almacenamiento

`POST /agronomia-api/api/images.php` acepta `photos[]`, máximo cinco, JPEG/PNG/WebP decodificables hasta 15 MiB cada uno. Devuelve `201` con `success`, `batch_id` y rutas relativas solo tras escribir y confirmar todas las filas. La carpeta local continúa siendo `C:\xampp\plant-images`; MySQL conserva metadatos y rutas, no las imágenes.

El análisis ya no usa `api/analyze.php` (endpoint anterior retirado del proyecto y de XAMPP). Pl@ntNet recibe multipart JPEG en `POST https://my-api.plantnet.org/v2/identify/all?api-key=...` (`lang=es`, 3 candidatos). OpenCode Free respondió HTTP 403 a la inferencia directa; Space Bunny ahora se llama por OpenCode CLI local (`opencode serve --hostname 127.0.0.1 --port 4096`, modelo `opencode/space-bunny-free`). Android Emulator accede a `10.0.2.2:4096`. `OPENCODE_SERVER_BASE_URL` puede sobrescribir la URL base; Pl@ntNet requiere `PLANTNET_API_KEY` en propiedades locales de Gradle/variable de entorno. Ninguna clave debe guardarse en esta nota o en Git.

## Chat y Skill de Space Bunny

- UI actual: `app/src/main/java/com/correa/agronom_ia/PlantChatScreen.kt` ofrece barra de acciones, lista desplazable de mensajes, bienvenida con preguntas sugeridas y composer con texto, adjuntos, cámara y envío. El estado ocupado bloquea acciones repetidas; los errores quedan junto al composer con reintento.
- `PlantPhotoViewModel.kt` persiste hasta 60 mensajes y los metadatos mínimos de análisis en `SharedPreferences` privadas de la app. Se conservan nombres de fotos en el historial, no se guardan en la nube. Una conversación nueva limpia el historial y selección local.
- [[Agronom_IA/.agents/skills/space-bunny-botany/SKILL|Skill Space Bunny botánica]] es la fuente única versionada. Gradle la empaqueta como asset desde `Agronom_IA/.agents/skills`; `SpaceBunnySkillRepository` elimina el frontmatter `SKILL.md` y pasa su cuerpo como instrucción `system` a cada solicitud OpenCode. La app añade en el mensaje del usuario el contexto actual de Pl@ntNet y hasta 12 turnos anteriores por separado. El modelo no recibe imágenes.
- El formato de respuesta es Markdown breve para chat, no JSON. El parser valida cuerpo no vacío, longitud y texto legible; una salida no válida activa un único intento de corrección. La prueba instrumentada comprueba que la Skill está realmente empaquetada y disponible.
- Prueba real del chat sin identificación: el modelo devolvió una respuesta en español con fragmentos CJK y nombres comerciales no solicitados. Se añadió validación para rechazar caracteres CJK salvo que el usuario use ese alfabeto; la app reintentó y, al persistir una salida ilegible, dejó el error visible con acción **Reintentar**. Esto evita mostrar texto malformado, pero la calidad factual y lingüística de Space Bunny Free sigue siendo variable y no queda garantizada por la app.
- No se añadieron bibliotecas ni se migró el tema. Se conservan Compose Material 3, colores botánicos claro/oscuro, selector, cámara y flujo de subida XAMPP/MySQL.

## Pruebas y pendientes

Las verificaciones históricas de XAMPP/MySQL anotadas abajo son del bloque anterior. El bloque de integración previa de Space Bunny se verificó con una imagen real en Pixel_8 AVD/API 37, junto a Pl@ntNet/OpenCode. Para el rediseño: ensamblados debug/release y pruebas unitarias completados; 15 pruebas instrumentadas completadas en Pixel_8 AVD/API 37. También se lanzó la app compilada en el emulador y se envió una pregunta genérica real al servidor OpenCode; la salida ilegible fue detectada, se reintentó y la interfaz mostró el error con opción de reintento. La generación real requiere conexión a Internet, Pl@ntNet y el `opencode serve` local activo. La disponibilidad y calidad de Space Bunny Free pueden cambiar.

- XAMPP: esquema importado, cuenta API de mínimo privilegio, endpoint de imágenes desplegado y carpeta con ACL restringida; PHP tiene GD, WebP y mysqli.
- Smoke tests previos del backend: GET responde 405, texto disfrazado de JPEG 415, lote de cinco imágenes 201 y lote de seis 422; se limpió el lote de prueba.
- En el emulador API 37 se probó la carga a Apache/MySQL y la concesión de permiso de red local. La captura real desde cámara aún requiere verificación manual.
- Para usar teléfono físico, el firewall aún necesita regla TCP entrante puerto 80 para perfil Private y LocalSubnet: `New-NetFirewallRule -DisplayName "Agronomia local XAMPP API" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 80 -Profile Private -RemoteAddress LocalSubnet`.

## Migración progresiva del backend a Spring Boot (2026-10-13)

### Inspección e inventario

- El repositorio versionado contiene un único endpoint PHP: `POST /agronomia-api/api/images.php`. No se encontraron rutas de usuarios, login, administración, análisis de IA ni otros módulos. No hay roles ni autenticación. React no forma parte del proyecto.
- Android usa `photos[]`, entre 1 y 5 imágenes, y espera `201 { success, batch_id, images: [{ id, path, mime_type, size }] }`. El endpoint PHP acepta JPEG/PNG/WebP decodificables, 15 MiB por foto y 4096 px máximos; los guarda fuera de `htdocs` y registra metadatos en MySQL.
- MariaDB 10.4.32 de XAMPP escucha localmente en 3306. `SHOW CREATE TABLE plant_images` coincide con `backend/schema.sql`; la tabla InnoDB no guarda BLOB y no tiene relaciones. La cuenta de API carece de permiso para consultar el DDL, por eso se verificó con consulta local solo lectura. No se modificó la base.
- Apache no estaba escuchando en puerto 80 al revisar este bloque, así que el PHP desplegado no se verificó por HTTP ni se reemplazó.

### Arquitectura migrada

- Nuevo módulo `Agronom_IA/backend-spring`: Spring Boot 3.5.16, código Java con release 17 (JDK 24 local), MariaDB JDBC y monolito modular bajo `com.agronomia.backend.images`.
- `POST /agronomia-api/api/images.php` conserva el path, multipart `photos[]`, DTO exitoso y límites del cliente. Acepta JPEG/PNG/WebP detectados por firma, comprueba decodificación y dimensiones, guarda por UUID en `C:\xampp\plant-images` y registra filas JDBC con `TransactionTemplate`. Si falla la transacción se limpia el directorio del lote.
- Se eligió `JdbcTemplate`, no JPA/Hibernate: solo se inserta en una tabla simple sin relaciones ni lecturas; SQL explícito conserva el esquema legado. No se agrega Flyway porque no hay cambio de esquema. El DDL se deja intacto.
- La API anterior no tenía autenticación; no se inventó un mecanismo que rompa el cliente. Spring escucha por defecto en `127.0.0.1:8081`. No exponerlo a LAN/Internet sin TLS, autenticación y firewall. Los secretos salen de variables de entorno.
- PHP permanece como fallback en puerto 80; Android se puede dirigir al mismo path en Spring con `PLANT_API_BASE_URL=http://10.0.2.2:8081/agronomia-api`. El cambio del APK queda pendiente de aceptación end-to-end desde el emulador. PHP solo se retirará tras verificar el cliente.

### Pruebas y pendientes

- Pruebas del backend: `mvn test` en `Agronom_IA/backend-spring`; 14 correctas con H2 en memoria. Cubren contrato HTTP, 1/5/6 fotos, firma/MIME, JPEG/PNG/WebP, GIF, corrupción y rollback tras falla después de un INSERT con limpieza de archivos.
- Integración real contra MariaDB/XAMPP usando la base nueva `agronomia_spring_migration_test_20261013`: GET 405 JSON; lote de cinco JPEG 201 con cinco filas/archivos; seis 422; GIF con MIME/extensión falsos 415. Los errores no añadieron filas. No se modificó la base `agronomia`.
- La base aislada y cinco imágenes sintéticas permanecen en `%TEMP%\agronomia-spring-mariadb-test-images`; la herramienta rechazó la limpieza destructiva, así que quedan disponibles para inspección.
- Pendiente: prueba de carga desde Android Emulator al host Spring en `10.0.2.2:8081`; el valor predeterminado móvil continúa apuntando a PHP. La configuración XAMPP se revisó sin copiar secretos.
- Riesgo operacional: un cierre abrupto del proceso entre la escritura de archivos y commit puede dejar una carpeta huérfana; requiere reconciliación periódica antes de operación de larga duración.
- Configuración, inventario detallado de endpoints, estructura, seguridad y ejecución: [[Agronom_IA/backend-spring/README]].
## Commits

- `635316e` — Implementa flujo móvil de imágenes y análisis.
- `e6892f0` — Añade API PHP, validación y persistencia MySQL.
- `ef6ed26` — Documenta instalación local, IA y verificaciones.
- `784fc3e` — Incluye archivos base del proyecto Android.

El repositorio está en la raíz de la bóveda. `.obsidian`, `Bienvenido.md`, `local.properties`, compilaciones y archivos de IDE quedan fuera de Git.
