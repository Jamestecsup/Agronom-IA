# Agronom_IA

Aplicación Android Kotlin con Jetpack Compose y MVVM para seleccionar o tomar hasta cinco fotos de una planta, validarlas, identificarlas con Pl@ntNet y pedir una guía textual a Space Bunny. El almacenamiento local opcional sigue conectado a PHP/MySQL en XAMPP.

## Arquitectura

- UI: `app/src/main/java/com/correa/agronom_ia/MainActivity.kt` (Compose Material 3), con selección, vista previa horizontal desplazable, captura, estados y resultados separados por proveedor.
- Estado y flujo: `PlantPhotoViewModel.kt` y `StateFlow`; las tareas de red/imagen se ejecutan fuera del hilo principal.
- Validación local: copia limitada a 15 MiB, decodificación con dimensiones acotadas, normalización a JPEG (máximo 2048 px) en `files/images`; admite HEIF/HEIC si el decodificador Android del dispositivo puede abrirlos.
- Integración: `PlantAnalysisApi.kt` envía multipart JPEG directamente a Pl@ntNet y luego solo texto a Space Bunny. No se envían imágenes a Space Bunny.
- Persistencia opcional: la acción **Guardar imágenes** conserva el envío independiente al backend PHP/Apache y MySQL. Las imágenes quedan en `C:\xampp\plant-images`, y MySQL almacena rutas relativas y metadatos, no BLOB.

## Configuración de APIs

Las solicitudes de análisis salen del dispositivo a Internet. En la documentación consultada, Pl@ntNet recibe `POST https://my-api.plantnet.org/v2/identify/all?api-key=...` con partes multipart repetidas `images`; admite hasta cinco JPEG/PNG (un mismo ejemplar) y 50 MB sumados. Space Bunny usa `POST https://opencode.ai/inference/v1/chat/completions` con modelo `space-bunny-free` y formato Chat Completions. Sus docs dicen que los modelos gratuitos pueden llamarse sin token; si deseas usar tu clave de OpenCode, se admite como configuración opcional.

Configura las propiedades en `%USERPROFILE%\.gradle\gradle.properties` (no en el repositorio):

```properties
PLANTNET_API_KEY=tu_clave_de_plantnet
# Opcional para Space Bunny Free:
OPENCODE_API_KEY=tu_clave_de_opencode
```

Reinicia/sincroniza Gradle y ejecuta la variante debug. También se aceptan las variables de entorno `PLANTNET_API_KEY` y `OPENCODE_API_KEY`. Si falta la clave de Pl@ntNet, la app mostrará un error de configuración y no iniciará la llamada.

**Seguridad:** la clave de Pl@ntNet (y el token opcional de OpenCode) queda compilada en el APK y puede extraerse. Este modo directo cumple el flujo de desarrollo solicitado, pero no es adecuado para distribuir públicamente una app. Para producción, traslada las claves a un backend propio y rota cualquier clave que haya estado en un APK compartido. No pegues claves en el chat ni en archivos versionados.

Space Bunny recibe únicamente nombre científico, familia/nombre común si Pl@ntNet los devuelve, puntuación de coincidencia y alternativas. El prompt solicita JSON para riego, iluminación, toxicidad para mascotas y advertencias. Se valida la respuesta antes de mostrarla. La disponibilidad gratuita del modelo puede cambiar.

## Ejecución y backend local

Abre `Agronom_IA` en Android Studio. Para el backend XAMPP, Android Emulator usa `http://10.0.2.2/agronomia-api`; un teléfono usa `http://IP_LAN_DEL_PC/agronomia-api` y necesita acceso al puerto Apache/firewall en la red privada. Esta URL solo afecta a **Guardar imágenes**. La identificación directa usa servicios externos y requiere Internet; no solicita permiso de red local. Sigue [`backend/README.md`](backend/README.md) para MySQL/PHP.

El tipo `debug` permite HTTP para el backend local. `release` bloquea tráfico HTTP sin cifrar. La API local de desarrollo todavía no autentica clientes; limita su acceso a una red privada.

## Verificación

Pruebas automatizadas para los parsers de respuesta: `.\gradlew.bat :app:connectedDebugAndroidTest`. Compilación: `.\gradlew.bat :app:assembleDebug`. Las llamadas reales requieren una clave válida de Pl@ntNet y conectividad; Space Bunny Free puede no requerir token.
