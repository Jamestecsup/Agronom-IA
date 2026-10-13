# API local de imágenes

La API PHP recibe `multipart/form-data` en `POST /api/images.php`, con una o más partes llamadas `photos[]` (máximo cinco). Comprueba tamaño, MIME detectado por `fileinfo`, contenido y decodificación GD, además de dimensiones hasta 4 096 px; solo acepta JPEG, PNG y WebP que admita GD. Android normaliza a JPEG imágenes HEIF/HEIC que su decodificador pueda abrir. Cada lote se almacena en una subcarpeta UUID de `PLANT_UPLOAD_DIR`, fuera de `htdocs`, y MySQL conserva solo rutas relativas y metadatos. La operación confirma el lote entero o borra los archivos si hay un error.

## Estado de la instalación local

En el equipo de desarrollo la API está desplegada en `C:\xampp\htdocs\agronomia-api`, la base `agronomia` y la cuenta `agronomia_api` están creadas, y el almacenamiento está en `C:\xampp\plant-images`. El archivo de configuración Apache es `C:\xampp\apache\conf\extra\httpd-agronomia.conf`; se restringió su lectura al usuario local, Administradores y SYSTEM. El usuario MySQL se generó con una contraseña aleatoria y tiene permisos de inserción y actualización del estado de análisis. El archivo `httpd-xampp.conf.codex-backup` conserva la configuración previa al include. Android Emulator utiliza `http://10.0.2.2/agronomia-api`.

Las instrucciones siguientes sirven para verificar la instalación o repetirla en otro equipo.

## XAMPP en Windows

1. Importa `schema.sql` en phpMyAdmin o ejecuta su contenido en MySQL. Crea el usuario dedicado con los permisos comentados al final del SQL. El host `127.0.0.1` del usuario coincide con la conexión TCP.
2. Copia `backend/api` a `C:\xampp\htdocs\agronomia-api\api`. Crea `C:\xampp\plant-images` y concede escritura a la cuenta que ejecuta Apache (en XAMPP local suele ser tu usuario o `SYSTEM`). No pongas imágenes privadas bajo `htdocs`.
3. Configura Apache en `C:\xampp\apache\conf\extra\httpd-agronomia.conf` y asegúrate de incluirlo desde `httpd-xampp.conf`:

   ```apache
   SetEnv PLANT_DB_HOST 127.0.0.1
   SetEnv PLANT_DB_PORT 3306
   SetEnv PLANT_DB_NAME agronomia
   SetEnv PLANT_DB_USER agronomia_api
   SetEnv PLANT_DB_PASSWORD "TU_CONTRASENA_LOCAL"
   SetEnv PLANT_UPLOAD_DIR "C:/xampp/plant-images"
   SetEnv PLANT_AI_API_URL https://opencode.ai/inference/openai/v1/responses
   SetEnv PLANT_AI_MODEL gpt-5.4-mini
   ```

   Protege ese archivo en el equipo y no lo copies al repositorio. Reinicia Apache desde el panel de XAMPP después de editarlo. Verifica que `mysqli`, `fileinfo` y GD estén activados; el PHP de XAMPP inspeccionado tiene esas extensiones.
4. En `C:\xampp\php\php.ini`, configura `upload_max_filesize=16M`, `post_max_size=80M`, `max_file_uploads=10` y `max_execution_time=120`; reinicia Apache. El límite propio de cada imagen sigue siendo 15 MiB.
5. Apache debe escuchar en la red local para un teléfono físico. En el firewall de Windows permite conexiones entrantes a Apache/puerto 80 solo en la red privada de confianza. Evita exponer esta API sin autenticación a redes públicas. El endpoint de desarrollo aún no tiene autenticación.

Comprueba el endpoint con `http://127.0.0.1/agronomia-api/api/images.php` (GET debe responder 405 con JSON). Android Emulator usa `http://10.0.2.2/agronomia-api`; un teléfono en la misma Wi-Fi usa `http://IP_LAN_DEL_PC/agronomia-api` (consulta `ipconfig`). Android 17 / API 37 exige `ACCESS_LOCAL_NETWORK` para apps que apuntan a API 37; la app lo pide al guardar o analizar y solo inicia la operación si se concede. Para configurar el APK del teléfono: `gradlew.bat assembleDebug -PPLANT_API_BASE_URL=http://192.168.1.20/agronomia-api`. La URL predeterminada del proyecto es la del emulador. El tráfico HTTP sin TLS solo es aceptable durante desarrollo en una red privada.

## Análisis de plantas con OpenCode Zen

El endpoint `POST /api/analyze.php` está implementado. Después de guardar el lote, la app permite solicitar el análisis. PHP carga solo los archivos asociados al lote y los envía con salida JSON estructurada al endpoint Responses. Para OpenCode Zen configura `PLANT_AI_API_URL=https://opencode.ai/inference/openai/v1/responses` y `PLANT_AI_MODEL=gpt-5.4-mini`; el modelo admite entrada de imágenes. El backend conserva la clave como variable de Apache `PLANT_AI_API_KEY`, en el encabezado Bearer. La llamada ocurre únicamente tras pulsar **Analizar planta con IA**; necesita Internet y puede consumir créditos de OpenCode Zen. `store=false` solicita que la respuesta no se almacene.

Apache incluye el archivo privado `C:\Users\lenovo\AppData\Local\Agronomia\apache-ai.conf`, cuya ACL da control al usuario local, Administradores y SYSTEM. Para configurar OpenCode Zen, agrega allí estas líneas y reinicia Apache desde XAMPP:

```apache
SetEnv PLANT_AI_API_KEY "TU_CLAVE_OPENCODE_ZEN"
SetEnv PLANT_AI_API_URL https://opencode.ai/inference/openai/v1/responses
SetEnv PLANT_AI_MODEL gpt-5.4-mini
```

No pegues la clave en el chat, en Android, en notas versionadas ni en el repositorio. El modelo informa confianza e incertidumbre; la salida sigue siendo una estimación visual, no una identificación botánica certificada. OpenCode Zen puede cambiar los modelos y endpoints disponibles; consulta su [catálogo y guía de API](https://opencode.ai/v2/docs/console/models/) si `gpt-5.4-mini` deja de estar disponible.

## Recuperación

La API valida todo antes de mover archivos, crea nombres aleatorios, inserta dentro de una transacción y elimina archivos de ese lote si MySQL o el almacenamiento fallan. Una caída abrupta del proceso podría dejar una carpeta UUID incompleta; las carpetas sin filas correspondientes se pueden auditar y limpiar comparando sus rutas con `plant_images.relative_path`. No se elimina automáticamente ningún archivo previo.
