# Backend local: PHP legado y migración a Spring Boot

## Estado

El PHP existente se conserva como ruta de compatibilidad durante la migración. Su única operación comprobada es la carga de imágenes; no se encontraron endpoints de usuarios, autenticación, administración o análisis de IA. La implementación Java equivalente está en ../backend-spring/README.md.

El contrato que consume Android es POST /agronomia-api/api/images.php, multipart con partes repetidas photos[], de una a cinco imágenes. La respuesta exitosa es HTTP 201 con success, batch_id e images (id, ruta relativa, MIME y tamaño). El backend no guarda las fotos como BLOB en MySQL.

## Instalación PHP actual

En este equipo, Apache PHP se configuró para C:\xampp\htdocs\agronomia-api; MySQL/MariaDB usa agronomia y las imágenes van a C:\xampp\plant-images. La configuración de Apache contiene datos locales fuera del repositorio. No copies credenciales a Android, Git ni documentación.

schema.sql define plant_images: UUID de imagen y lote, ruta relativa única, MIME, tamaño, ancho, alto, estado pending|processed|failed y fecha. El DDL coincide con el SHOW CREATE TABLE consultado localmente. No se requirió cambiar el esquema.

## Inventario de la API PHP

| Método y ruta | Contrato y validación | Respuestas principales | Seguridad actual |
|---|---|---|---|
| POST /agronomia-api/api/images.php | multipart photos[], 1–5; límite 15 MiB/foto; JPEG, PNG, WebP por MIME detectado, dimensiones 16–4096 y decodificación GD; archivos fuera de htdocs; rutas insertadas en transacción | 201 éxito; 400 campo ausente; 415 formato; 422 conteo, archivo, tamaño o dimensiones inválidas; 500 almacenamiento/DB | Sin autenticación ni autorización; solo red local privada |
| Otros endpoints | No aparecen en el backend versionado | N/A | N/A |

PHP comprueba mysqli, fileinfo y GD. Si falla SQL o la escritura, intenta revertir y retirar los archivos del lote; no devuelve confirmación de éxito. No se encontraron migraciones aparte de schema.sql.

## Ejecutar el PHP legado como fallback

1. Importa schema.sql si plant_images todavía no existe. No uses opciones que borren o reconstruyan una base existente.
2. Copia backend/api/images.php a C:\xampp\htdocs\agronomia-api\api\images.php y crea C:\xampp\plant-images. Concede escritura solo a la cuenta que ejecuta Apache.
3. Configura en un archivo local no versionado de Apache las variables PLANT_DB_HOST, PLANT_DB_PORT, PLANT_DB_NAME, PLANT_DB_USER, PLANT_DB_PASSWORD y PLANT_UPLOAD_DIR. Incluye ese archivo en Apache y reinicia el servicio.
4. Habilita mysqli, fileinfo y GD; establece upload_max_filesize=16M, post_max_size=80M y max_file_uploads=10. Para confirmar que el endpoint está accesible, GET http://127.0.0.1/agronomia-api/api/images.php responde 405 JSON.

El emulador Android Studio llega al host usando http://10.0.2.2/agronomia-api. Un teléfono físico necesita la IP LAN del PC y una regla de firewall limitada a la red privada. El cliente Android puede recibir esa URL al compilar usando PLANT_API_BASE_URL. Este modo de desarrollo no tiene autenticación; no publicar el servicio en Internet.

## XAMPP durante la transición

En la inspección del 2026-10-13, MariaDB 10.4 estaba escuchando en 3306 y el DDL coincidía. Apache no escuchaba en el puerto 80; por ello, el endpoint PHP no pudo comprobarse por HTTP en esa sesión. No se alteró la base agronomia ni se copiaron archivos a htdocs.

Los móviles no se conectan a MySQL. La API se comunica con la base local y Android solo habla HTTP con la API. Para elegir Spring temporalmente, define PLANT_API_BASE_URL como http://10.0.2.2:8081/agronomia-api para el emulador. PHP continúa disponible en puerto 80 mientras Apache esté activo.

La API no tiene autenticación; no la expongas a Internet. Spring escucha por defecto en loopback (127.0.0.1). Para un teléfono de la misma LAN hay que configurar SERVER_ADDRESS=0.0.0.0, definir la IP del PC en la URL Android y habilitar solo el puerto 8081 en una red privada confiable. Antes de un despliegue público se necesita HTTPS, autenticación/autorización, límites operativos y revisión de seguridad.

## Plan por fases

1. Implementación Spring y validación automática en H2 completadas; prueba MariaDB real completada en una base aislada. No se alteró la base agronomia ni su esquema.
2. Cambio Android controlado pendiente: apuntar un APK de desarrollo al puerto Spring y validar la carga real desde el emulador mientras PHP sigue disponible.
3. Retirada de PHP solo después de aceptar el cliente Android contra Spring.

La tabla tiene una sola entidad sin relaciones y el endpoint solo inserta. El módulo Spring usa JDBC directo y una transacción explícita; Hibernate/JPA y Flyway/Liquibase no aportarían valor sin consultas, relaciones ni cambios de esquema. Si aparecen nuevos casos de uso, revaluar JPA y versionado de esquema.
