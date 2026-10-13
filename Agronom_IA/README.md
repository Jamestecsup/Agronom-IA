# Agronom_IA

Aplicación Android Kotlin con Jetpack Compose y MVVM para seleccionar o tomar hasta cinco fotos de una planta, validarlas, identificarlas con Pl@ntNet y pedir una guía textual a Space Bunny. El almacenamiento local opcional sigue conectado a PHP/MySQL en XAMPP.

## Arquitectura

- UI: `app/src/main/java/com/correa/agronom_ia/MainActivity.kt` (Compose Material 3), con selección, vista previa horizontal desplazable, captura, estados y resultados separados por proveedor.
- Estado y flujo: `PlantPhotoViewModel.kt` y `StateFlow`; las tareas de red/imagen se ejecutan fuera del hilo principal.
- Validación local: copia limitada a 15 MiB, decodificación con dimensiones acotadas, normalización a JPEG (máximo 2048 px) en `files/images`; admite HEIF/HEIC si el decodificador Android del dispositivo puede abrirlos.
- Integración: `PlantAnalysisApi.kt` envía multipart JPEG directamente a Pl@ntNet y después solo texto a Space Bunny mediante el servidor OpenCode local. No se envían imágenes a Space Bunny. Si falla la guía, conserva la identificación para reintentar sin consumir otra llamada Pl@ntNet.
- Persistencia opcional: la acción **Guardar imágenes** conserva el envío independiente al backend PHP/Apache y MySQL. Las imágenes quedan en `C:\xampp\plant-images`, y MySQL almacena rutas relativas y metadatos, no BLOB.

## Configuración de APIs

Pl@ntNet recibe desde Android `POST https://my-api.plantnet.org/v2/identify/all?api-key=...` con partes multipart repetidas `images`; admite hasta cinco JPEG/PNG (un mismo ejemplar) y 50 MB sumados. Space Bunny Free rechaza la invocación directa de inferencia fuera de OpenCode (HTTP 403 FreeTierError, comprobado), así que Android envía el texto identificado al servidor OpenCode del equipo en `http://10.0.2.2:4096`, que ejecuta `opencode/space-bunny-free`. Esta ruta requiere que el equipo tenga Internet y OpenCode CLI autenticado/configurado y que `opencode serve` esté activo.

Configura las propiedades en `%USERPROFILE%\.gradle\gradle.properties` (no en el repositorio):

```properties
PLANTNET_API_KEY=tu_clave_de_plantnet
```

Reinicia/sincroniza Gradle y ejecuta la variante debug. También se acepta la variable de entorno `PLANTNET_API_KEY`. La URL local se puede cambiar con `OPENCODE_SERVER_BASE_URL`; para el emulador Android la configuración predeterminada `10.0.2.2` apunta al equipo anfitrión.

**Seguridad:** la clave de Pl@ntNet queda compilada en el APK y puede extraerse. Este modo directo es solo de desarrollo; para producción, traslada la clave a un backend propio y rota cualquier clave que haya estado en un APK compartido. No pegues claves en el chat ni en archivos versionados. El servidor OpenCode se debe enlazar a loopback, no a la red LAN.

Space Bunny recibe únicamente nombre científico, familia/nombre común si Pl@ntNet los devuelve, puntuación de coincidencia y alternativas. El prompt solicita JSON para riego, iluminación, toxicidad para mascotas y advertencias. Se valida la respuesta antes de mostrarla. La disponibilidad gratuita del modelo puede cambiar.

## Ejecución y backend local

Abre `Agronom_IA` en Android Studio. En una terminal del PC ejecuta `opencode serve --hostname 127.0.0.1 --port 4096`; no cierres esa terminal durante el análisis. Android Emulator accede al host por `10.0.2.2`. El análisis requiere Internet para Pl@ntNet y para el proveedor configurado en OpenCode; la conexión a `10.0.2.2` es local. Para XAMPP, Android Emulator usa `http://10.0.2.2/agronomia-api`; un teléfono usa `http://IP_LAN_DEL_PC/agronomia-api` y necesita acceso al puerto Apache/firewall en la red privada. Esta URL solo afecta a **Guardar imágenes**. Sigue [`backend/README.md`](backend/README.md) para MySQL/PHP.

El tipo `debug` permite HTTP para el backend local. `release` bloquea tráfico HTTP sin cifrar. La API local de desarrollo todavía no autentica clientes; limita su acceso a una red privada.

## Verificación

Pruebas automatizadas para los parsers y límites de selección: `.\gradlew.bat :app:connectedDebugAndroidTest`. Compilación: `.\gradlew.bat :app:assembleDebug`. La prueba real requiere una clave válida de Pl@ntNet, Internet y OpenCode CLI con Space Bunny disponible; inicia el servidor con el comando anterior.
