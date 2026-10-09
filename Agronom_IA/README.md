# Agronom_IA

Aplicación Android Kotlin/Jetpack Compose para seleccionar o capturar hasta cinco fotos de plantas, validar y normalizar archivos localmente y enviarlos al backend PHP/MySQL de desarrollo.

## Arquitectura

- UI: `MainActivity.kt` con Compose Material 3 y estados vacíos, selección, validación, envío y confirmación.
- Estado y lógica: `PlantPhotoViewModel.kt`, `StateFlow` y corutinas; cámara del sistema vía `TakePicture` y `FileProvider`, galería vía selector de documentos.
- Validación Android: copia acotada del URI al caché privado, límite de 15 MiB y dimensiones de 16 a 12 000 px; `ImageDecoder` en API 28+ permite formatos del dispositivo como HEIF cuando se pueden decodificar. Las imágenes se reducen a un máximo de 2048 px y se normalizan a JPEG en `files/images`. El backend decodifica además con GD y acepta hasta 4 096 px.
- Persistencia servidor: API PHP en `backend/api/images.php`, transacción MySQL y almacenamiento fuera de `htdocs`. El APK no contiene credenciales MySQL.

## Ejecución

Abre `Agronom_IA` en Android Studio. La URL predeterminada `http://10.0.2.2/agronomia-api` sirve para Android Emulator con la API desplegada en XAMPP. Para dispositivo físico usa la IP LAN del PC en una propiedad Gradle, por ejemplo `-PPLANT_API_BASE_URL=http://192.168.1.20/agronomia-api`. Sigue `backend/README.md` para la configuración local y firewall.

El tipo `debug` permite HTTP para el backend local; `release` desactiva tráfico sin cifrar. Antes de distribuir, configura HTTPS. El API local todavía no autentica clientes.

## IA

El flujo de análisis ya está implementado en la app y en `backend/api/analyze.php`. Tras guardar el lote, el usuario puede solicitar análisis a OpenAI Responses con `gpt-4.1-mini`, que admite imágenes y salida estructurada ([ficha oficial del modelo](https://developers.openai.com/api/docs/models/gpt-4.1-mini), [guía de visión](https://developers.openai.com/api/docs/guides/images-vision), [salidas estructuradas](https://developers.openai.com/api/docs/guides/structured-outputs)). El resultado incluye nombre, cuidados, confianza e incertidumbre.

Falta configurar `PLANT_AI_API_KEY` en el archivo local de Apache para activar llamadas remotas. La clave no está en el APK ni en el proyecto. El análisis requiere Internet y cuota de API; el guardado de fotos y MySQL permanece local.
