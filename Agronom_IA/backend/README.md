# Backend local PHP/MySQL

Este backend conserva la operación **Guardar imágenes**. El análisis de planta se realiza desde Android directamente con Pl@ntNet y después consulta Space Bunny con datos textuales; Apache/PHP no participa en ese flujo.

## Instalación XAMPP existente

En este equipo la API está en `C:\xampp\htdocs\agronomia-api`, MySQL usa la base `agronomia`, y las imágenes se guardan fuera de `htdocs` en `C:\xampp\plant-images`.

1. Importa [`schema.sql`](schema.sql) con phpMyAdmin o MySQL. La tabla `plant_images` conserva UUID, lote, ruta relativa única, formato, tamaño, dimensiones, estado y fecha; no contiene BLOB. El estado sirve como metadato para procesos posteriores.
2. Copia `backend/api/images.php` a `C:\xampp\htdocs\agronomia-api\api\images.php` y crea `C:\xampp\plant-images`. Concede escritura a la cuenta que ejecuta Apache.
3. En `C:\xampp\apache\conf\extra\httpd-agronomia.conf`, configura únicamente la conexión MySQL y el almacenamiento, por ejemplo:

   ```apache
   SetEnv PLANT_DB_HOST 127.0.0.1
   SetEnv PLANT_DB_PORT 3306
   SetEnv PLANT_DB_NAME agronomia
   SetEnv PLANT_DB_USER agronomia_api
   SetEnv PLANT_DB_PASSWORD "TU_CONTRASENA_LOCAL"
   SetEnv PLANT_UPLOAD_DIR "C:/xampp/plant-images"
   ```

   Mantén este archivo fuera del repositorio y limita su lectura. Inclúyelo en la configuración de Apache y reinicia Apache desde XAMPP.
4. Verifica que `mysqli`, `fileinfo` y GD estén habilitados en PHP. Configura `upload_max_filesize=16M`, `post_max_size=80M`, `max_file_uploads=10` y reinicia Apache.

El endpoint es `POST /agronomia-api/api/images.php`, multipart con una a cinco partes `photos[]`. PHP valida contenido/MIME, decodificación y dimensiones (JPEG, PNG y WebP compatibles con GD; máximo 15 MiB y 4096 px por archivo). Escribe en una carpeta UUID y registra rutas en transacción; si falla MySQL o el almacenamiento, limpia los archivos del lote y devuelve error, no éxito parcial.

## Android Emulator y teléfono

El emulador llega al PC con `http://10.0.2.2/agronomia-api`. En un teléfono conectado a la misma Wi-Fi configura la URL con la IP LAN del PC (`ipconfig`), por ejemplo `http://192.168.1.20/agronomia-api`, y permite TCP 80 en el firewall de Windows para el perfil privado y la subred local. La propiedad Gradle para ese APK es `-PPLANT_API_BASE_URL=http://192.168.1.20/agronomia-api`.

Android 17 / API 37 puede pedir `ACCESS_LOCAL_NETWORK` al guardar en XAMPP. No hace falta ese permiso para el análisis directo remoto. `debug` permite HTTP de desarrollo; `release` no. La API local no autentica clientes: úsala únicamente en una red privada de confianza y no la expongas a Internet.

Para validar disponibilidad, `GET http://127.0.0.1/agronomia-api/api/images.php` debe responder 405 JSON.
