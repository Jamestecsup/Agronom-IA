# Agronom_IA

Aplicación Android Kotlin con Jetpack Compose y MVVM. La pantalla principal es un chat botánico que conserva conversaciones localmente, permite alternar entre DeepSeek V4.1 Flash Free (OpenCode) y Qwen 3.6 (Open WebUI), adjunta hasta cinco fotos para identificarlas mediante Pl@ntNet y conserva el guardado opcional en PHP/MySQL de XAMPP.

El proyecto también incorpora un chat web React/TypeScript en `frontend-web`, consumiendo el mismo backend Spring. En el chat web, el usuario elige DeepSeek V4.1 Flash Free (OpenCode) o Qwen (Open WebUI); Spring orquesta ambos con SSE. El historial web es local al navegador y no se guarda en MySQL. Consulta [[Proyecto Agronom_IA]] para el diagnóstico, pruebas y configuración de proveedores.

## Arquitectura

- UI: `app/src/main/java/com/correa/agronom_ia/MainActivity.kt` y `PlantChatScreen.kt` (Compose Material 3). Muestra un inicio conversacional, burbujas de chat, composer con fotos/cámara, envío, carga, error/reintento e identificación adjunta a la respuesta.
- Estado y flujo: `PlantPhotoViewModel.kt` y `StateFlow`; preguntas, respuestas, nombres de adjuntos e identificaciones se persisten en preferencias privadas (hasta 60 mensajes). Las fotos seleccionadas se guardan en `files/images`; una selección pendiente permanece disponible para guardar en XAMPP después del análisis.
- Validación local: copia limitada a 15 MiB, decodificación con dimensiones acotadas, normalización a JPEG (máximo 2048 px) en `files/images`; admite HEIF/HEIC si el decodificador Android del dispositivo puede abrirlos.
- Integración: `PlantAnalysisApi.kt` envía multipart JPEG directamente a Pl@ntNet. `AiChatApi.kt` consulta el catálogo de Spring y transmite chat SSE a `POST /api/ai/chat` con un `providerId` permitido; Spring añade las instrucciones botánicas y enruta a OpenCode u Open WebUI. Las fotos móviles se siguen enviando a Pl@ntNet, no al chat multimodal.
- Catálogo y selección: `AiModelCatalogParser` mantiene solo los IDs permitidos `deepseek-opencode` y `qwen-local`; se muestra la disponibilidad que devuelve el backend cuando pudo verificarse. La preferencia queda en SharedPreferences; cada mensaje conserva el modelo usado y cambiar el selector no reescribe el historial. El saludo del modelo es solo UI y no forma parte del historial ni de la solicitud.
- Persistencia opcional: la acción **Guardar imágenes** conserva el envío independiente al backend PHP/Apache y MySQL. Las imágenes quedan en `C:\xampp\plant-images`, y MySQL almacena rutas relativas y metadatos, no BLOB.

## Configuración de APIs

Pl@ntNet recibe desde Android `POST https://my-api.plantnet.org/v2/identify/all?api-key=...` con partes multipart repetidas `images`; admite hasta cinco JPEG/PNG (un mismo ejemplar) y 50 MB sumados. El chat móvil usa `GET /api/ai/models` y `POST /api/ai/chat` de Spring en el puerto 8081. Spring enruta `deepseek-opencode` a `deepseek-v4.1-flash-free` mediante OpenCode y `qwen-local` a `Qwen/Qwen3.6-35B-A3B-FP8` mediante Open WebUI.

Configura las propiedades en `%USERPROFILE%\.gradle\gradle.properties` (no en el repositorio):

```properties
PLANTNET_API_KEY=tu_clave_de_plantnet
```

Reinicia/sincroniza Gradle y ejecuta la variante debug. También se acepta la variable de entorno `PLANTNET_API_KEY`. La URL base del backend móvil puede cambiarse con `SPRING_API_BASE_URL`; el valor por defecto del emulador es `http://10.0.2.2:8081`.

**Seguridad:** Android no recibe claves de OpenCode ni Open WebUI; esas conexiones y secretos permanecen en Spring. La clave de Pl@ntNet sigue compilándose en el APK para este modo de desarrollo y puede extraerse; antes de producción debe trasladarse al backend y rotarse si el APK fue compartido. No pegues secretos en archivos versionados. OpenCode puede permanecer enlazado a loopback porque Spring se conecta desde el PC.

Los dos proveedores reciben texto y el historial reciente por Spring SSE. Cuando Pl@ntNet identifica una imagen, el cliente añade esa identificación como contexto textual a la consulta actual; no envía la foto a ningún proveedor conversacional. Un modelo no disponible no produce un cambio silencioso al otro proveedor.

La Skill botánica se carga en Spring como instrucción de sistema. Android reenvía hasta 38 mensajes previos acotados a 28 000 caracteres y la pregunta actual; Spring limita además cada solicitud. La app actualiza la burbuja progresivamente con cada delta SSE y conserva el turno y el modelo al reintentar.

## Ejecución y backend local

Abre `Agronom_IA` en Android Studio e inicia Spring y OpenCode siguiendo [`backend-spring/README.md`](backend-spring/README.md). El emulador usa `http://10.0.2.2:8081` para Spring y `http://10.0.2.2/agronomia-api` para guardar imágenes con PHP. Para un teléfono, configura `SPRING_API_BASE_URL=http://IP_LAN_DEL_PC:8081`, enlaza Spring con `SERVER_ADDRESS=0.0.0.0` y permite el puerto 8081 solo en la red privada local. La app debe ejecutarse en variante debug, que permite HTTP local. El puerto 4096 de OpenCode no necesita exposición a la LAN. Para XAMPP/MySQL, sigue [`backend/README.md`](backend/README.md).

El tipo `debug` permite HTTP para el backend local. `release` bloquea tráfico HTTP sin cifrar. La API local de desarrollo todavía no autentica clientes; limita su acceso a una red privada.

## Verificación

Verificaciones previas: `:app:assembleDebug`, `:app:testDebugUnitTest` y 15 pruebas instrumentadas en Pixel_8 AVD/API 37. Para el cambio móvil multi-modelo, consulta el apartado actualizado en [[Proyecto Agronom_IA]] y no extrapoles esas pruebas instrumentadas anteriores a este cambio.
