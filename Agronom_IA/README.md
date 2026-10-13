# Agronom_IA

Aplicación Android Kotlin con Jetpack Compose y MVVM. La pantalla principal es un chat botánico que conserva conversaciones localmente, adjunta hasta cinco fotos validadas y combina la identificación de Pl@ntNet con respuestas contextuales de Space Bunny. La persistencia remota opcional sigue conectada a PHP/MySQL en XAMPP.

## Arquitectura

- UI: `app/src/main/java/com/correa/agronom_ia/MainActivity.kt` y `PlantChatScreen.kt` (Compose Material 3). Muestra un inicio conversacional, burbujas de chat, composer con fotos/cámara, envío, carga, error/reintento e identificación adjunta a la respuesta.
- Estado y flujo: `PlantPhotoViewModel.kt` y `StateFlow`; preguntas, respuestas, nombres de adjuntos e identificaciones se persisten en preferencias privadas (hasta 60 mensajes). Las fotos seleccionadas se guardan en `files/images`; una selección pendiente permanece disponible para guardar en XAMPP después del análisis.
- Validación local: copia limitada a 15 MiB, decodificación con dimensiones acotadas, normalización a JPEG (máximo 2048 px) en `files/images`; admite HEIF/HEIC si el decodificador Android del dispositivo puede abrirlos.
- Integración: `PlantAnalysisApi.kt` envía multipart JPEG directamente a Pl@ntNet y después solo contexto textual a Space Bunny mediante el servidor OpenCode local. El historial reciente también se pasa como texto. No se envían imágenes a Space Bunny. Si falla OpenCode, se conserva el mensaje/identificación y se puede reintentar.
- Skill de Space Bunny: [`.agents/skills/space-bunny-botany/SKILL.md`](.agents/skills/space-bunny-botany/SKILL.md). Gradle empaqueta esta misma fuente Markdown como asset; `SpaceBunnySkillRepository` quita el frontmatter de Codex y el `ViewModel` inyecta su contenido como instrucción `system` en cada turno. Contexto Pl@ntNet e historial/pregunta viajan por separado como texto del usuario.
- Persistencia opcional: la acción **Guardar imágenes** conserva el envío independiente al backend PHP/Apache y MySQL. Las imágenes quedan en `C:\xampp\plant-images`, y MySQL almacena rutas relativas y metadatos, no BLOB.

## Configuración de APIs

Pl@ntNet recibe desde Android `POST https://my-api.plantnet.org/v2/identify/all?api-key=...` con partes multipart repetidas `images`; admite hasta cinco JPEG/PNG (un mismo ejemplar) y 50 MB sumados. Space Bunny Free rechaza la invocación directa de inferencia fuera de OpenCode (HTTP 403 FreeTierError, comprobado), así que Android envía el texto identificado al servidor OpenCode del equipo en `http://10.0.2.2:4096`, que ejecuta `opencode/space-bunny-free`. Esta ruta requiere que el equipo tenga Internet y OpenCode CLI autenticado/configurado y que `opencode serve` esté activo.

Configura las propiedades en `%USERPROFILE%\.gradle\gradle.properties` (no en el repositorio):

```properties
PLANTNET_API_KEY=tu_clave_de_plantnet
```

Reinicia/sincroniza Gradle y ejecuta la variante debug. También se acepta la variable de entorno `PLANTNET_API_KEY`. La URL local se puede cambiar con `OPENCODE_SERVER_BASE_URL`; para el emulador Android la configuración predeterminada `10.0.2.2` apunta al equipo anfitrión.

**Seguridad:** la clave de Pl@ntNet queda compilada en el APK y puede extraerse. Este modo directo es solo de desarrollo; para producción, traslada la clave a un backend propio y rota cualquier clave que haya estado en un APK compartido. No pegues claves en el chat ni en archivos versionados. El servidor OpenCode se debe enlazar a loopback, no a la red LAN.

Space Bunny recibe únicamente texto: contexto de la especie (si Pl@ntNet la devuelve), puntuación y alternativas, el historial reciente y la pregunta actual. Nunca recibe las fotos. La respuesta en Markdown se valida antes de mostrarla. La disponibilidad gratuita del modelo puede cambiar.

En el flujo de chat, la Skill aporta las instrucciones de sistema persistentes y pide Markdown breve, sin JSON. La identificación y los candidatos de Pl@ntNet se construyen como contexto dinámico independiente; luego se adjunta el historial textual reciente y el mensaje actual. El parser valida que la respuesta no esté vacía, no supere 8.000 caracteres ni incluya marcadores internos o alfabetos CJK que el usuario no haya solicitado; si falla, se solicita una corrección una vez. Si sigue fallando, el chat presenta el error y habilita reintento.

## Ejecución y backend local

Abre `Agronom_IA` en Android Studio. En una terminal del PC ejecuta `opencode serve --hostname 127.0.0.1 --port 4096`; no cierres esa terminal durante el análisis. Android Emulator accede al host por `10.0.2.2`. El análisis requiere Internet para Pl@ntNet y para el proveedor configurado en OpenCode; la conexión a `10.0.2.2` es local. Para XAMPP, Android Emulator usa `http://10.0.2.2/agronomia-api`; un teléfono usa `http://IP_LAN_DEL_PC/agronomia-api` y necesita acceso al puerto Apache/firewall en la red privada. Esta URL solo afecta a **Guardar imágenes**. Sigue [`backend/README.md`](backend/README.md) para MySQL/PHP.

El tipo `debug` permite HTTP para el backend local. `release` bloquea tráfico HTTP sin cifrar. La API local de desarrollo todavía no autentica clientes; limita su acceso a una red privada.

## Verificación

Verificación del rediseño (2026-10-13): `:app:assembleDebug`, `:app:assembleRelease`, `:app:testDebugUnitTest` y `:app:connectedDebugAndroidTest` completados; 15 pruebas instrumentadas pasan en Pixel_8 AVD/API 37. La prueba real requiere una clave válida de Pl@ntNet, Internet y OpenCode CLI con Space Bunny disponible; inicia el servidor con el comando anterior. En una consulta genérica real, una respuesta lingüísticamente ilegible se rechazó y el chat mostró el error con reintento; la disponibilidad y calidad del modelo pueden variar. Nota de decisiones y verificaciones: [[Proyecto Agronom_IA]].
