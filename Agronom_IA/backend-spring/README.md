# Agronom-IA: backend Java/Spring Boot

Migración incremental del endpoint de almacenamiento PHP. El módulo conserva el contrato que llama la app Android y puede convivir con PHP en otro puerto.

## Inventario del sistema existente

- Android usa POST /agronomia-api/api/images.php, multipart photos[], 1–5 fotos. Envía la copia local normalizada a JPEG y lee success, batch_id e images[].{id,path,mime_type,size}.
- backend/api/images.php también admite JPEG, PNG y WebP decodificables, máximo 15 MiB por archivo y dimensiones 16–4096 px. Detecta el contenido, guarda por lote UUID fuera de htdocs y registra las rutas en una transacción.
- Tabla existente: plant_images (InnoDB), id CHAR(36) PK, batch_id CHAR(36), relative_path VARCHAR(255) UNIQUE, mime_type, file_size BIGINT UNSIGNED, dimensiones INT UNSIGNED, processing_status ENUM('pending','processed','failed') y created_at TIMESTAMP. No guarda BLOB.
- La instalación observada es MariaDB 10.4.32 de XAMPP. El DDL en vivo coincide con backend/schema.sql. La cuenta de aplicación queda fuera del APK.
- No hay autenticación, roles, usuarios ni otros endpoints PHP en el código disponible. Apache estaba detenido durante la inspección; MariaDB sí escuchaba. No se simuló Apache.

## Arquitectura y decisiones

Monolito modular pequeño: el módulo images contiene contrato HTTP, caso de uso, validación, repositorio y almacenamiento. El controlador delega. ImageBatchService valida el lote, guarda archivos UUID, ejecuta todas las inserciones con TransactionTemplate y borra la carpeta del lote si falla la transacción.

Se usa JdbcTemplate en lugar de JPA/Hibernate porque solo hay una escritura a una tabla sin relaciones ni operaciones de lectura. SQL explícito conserva el DDL legado y evita que Hibernate altere el esquema. No hay cambios en MySQL ni migración de datos, por lo que no se agrega Flyway/Liquibase. Si aparecen consultas o relaciones, reevaluar JPA; cambios de esquema requerirán migraciones versionadas y respaldo.

Java se compila con nivel 17; el JDK 24 instalado ejecuta las herramientas. Spring Boot 3.5.16 admite Java 17–25 según su documentación. El límite multipart usa el resolver servlet estándar de Spring con tamaño configurado explícitamente. webp-imageio y libwebp nativo conservan la compatibilidad WebP del PHP. JDK 24 puede avisar sobre acceso JNI; revisar las arquitecturas nativas distribuidas si cambia el host.

    src/main/java/com/agronomia/backend/
      AgronomiaBackendApplication.java
      images/
        ImageUploadController.java      contrato HTTP legado
        ImageBatchService.java          validación y coordinación
        ImageValidator.java             firma, decoder, MIME y límites
        ImageFileStorage.java            almacenamiento local y compensación
        PlantImageRepository.java        INSERT JDBC parametrizado
        ApiExceptionHandler.java         errores JSON uniformes

## Contrato migrado

| Método y ruta | Entrada | Éxito | Errores |
|---|---|---|---|
| POST /agronomia-api/api/images.php | multipart/form-data; 1–5 fotos[]; 15 MiB por archivo; 80 MiB por solicitud | 201 con success, batch_id e images (id, path, mime_type, size) | 400 campo ausente; 413 límite de transporte; 415 formato; 422 conteo, archivo vacío/corrupto, tamaño de aplicación o dimensiones inválidas; 500 almacenamiento/DB |
| GET a esa ruta | Sin parámetros | N/A | 405 JSON y Allow: POST |

JPEG, PNG y WebP se reconocen por firma binaria, se decodifican, y las dimensiones se inspeccionan antes de leer píxeles. Se ignoran nombre y MIME del cliente. GIF y formatos desconocidos reciben 415. Cada archivo se procesa secuencialmente. UUID evita sobrescrituras y las imágenes quedan fuera de la carpeta pública. El éxito se devuelve solo tras commit SQL. Si una inserción falla, se revierte la transacción y se elimina la carpeta del lote. Un cierre abrupto entre escritura y commit todavía puede dejar un directorio huérfano; hace falta reconciliación operacional antes de producción.

No existe autenticación/autorización en el backend anterior. Para mantener compatibilidad no se inventaron roles ni mecanismo de login. Spring escucha por defecto en loopback y no debe exponerse a la LAN/Internet sin TLS, autenticación, firewall y revisión. Los errores no incluyen stack traces. La cuenta SQL necesita permisos mínimos; la API no necesita SELECT. Los secretos se leen solo desde variables de entorno.

## Configuración y ejecución local

Requisitos: JDK 17+ (probado con JDK 24), Maven 3.6.3+ (local 3.9.16), MariaDB/MySQL existente. application-example.env es solo una referencia; Spring no carga archivos .env automáticamente. La contraseña queda en el entorno del proceso o secret store, nunca en Git. application-prod.yml exige las variables de conexión y desactiva init SQL; el perfil test usa H2 aislado.

En PowerShell, usando una cuenta local de mínimo privilegio ya creada:

~~~powershell
$env:DB_URL = 'jdbc:mariadb://127.0.0.1:3306/agronomia'
$env:DB_USERNAME = 'agronomia_api'
$secret = Read-Host 'Contraseña local de MySQL' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
$env:PLANT_UPLOAD_DIR = 'C:\xampp\plant-images'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:SERVER_PORT = '8081'
mvn spring-boot:run
$env:DB_PASSWORD = $null
~~~

El servicio no crea, migra ni borra tablas. Necesita que plant_images exista. Si se usa la cuenta de Apache, no se requiere SELECT; nunca uses root en el APK.

Disponibilidad local: GET http://127.0.0.1:8081/agronomia-api/api/images.php devuelve 405 JSON. Para el emulador Android Studio, conserva la ruta y cambia la URL base del build:

~~~powershell
cd ..\app
..\gradlew.bat -PPLANT_API_BASE_URL=http://10.0.2.2:8081/agronomia-api :app:installDebug
~~~

10.0.2.2 es el acceso del emulador al host. Para un teléfono en la red local, configura SERVER_ADDRESS=0.0.0.0, PLANT_API_BASE_URL=http://IP_DEL_PC:8081/agronomia-api y una regla de firewall privada limitada a la subred. Android puede regresar al PHP en puerto 80 cambiando la propiedad; ambos servidores pueden compartir C:\xampp\plant-images usando UUID.

## Pruebas

Ejecuta mvn test. Las pruebas automatizadas usan H2 en memoria y almacenamiento temporal; no necesitan claves, no usan datos reales y no conectan a MySQL de desarrollo. Pasaron 14 pruebas. Cubren contrato HTTP, selección 1/5/6, MIME/extensión falsos, JPEG, PNG, WebP decodificable, GIF, corrupción, ausencia de imágenes y rollback/limpieza cuando falla la persistencia después de un INSERT.

La compatibilidad del DDL se comprobó leyendo SHOW CREATE TABLE; la cuenta API no tenía permisos para consultarlo. Se levantó el servidor contra la base aislada agronomia_spring_migration_test_20261013, con DDL equivalente: GET fue 405, cinco fotos devolvieron 201 con cinco filas/archivos JPEG, seis recibieron 422 y un GIF disfrazado fue 415 sin filas adicionales. No se escribió en la base agronomia de trabajo.

La base de prueba y cinco imágenes sintéticas permanecen para inspección: agronomia_spring_migration_test_20261013 y %TEMP%\agronomia-spring-mariadb-test-images. La herramienta bloqueó la operación destructiva de limpieza; los artefactos quedan aislados y no contienen datos de usuario.

## Migración por fases

1. Implementado: API equivalente Spring en puerto 8081; PHP se conserva. Sin cambios de esquema.
2. Pruebas automatizadas y de integración: H2 y MariaDB aislada verificaron contratos, validaciones, persistencia y compensación sin usar la base de trabajo.
3. Siguiente paso: probar carga desde Android Emulator con PLANT_API_BASE_URL=http://10.0.2.2:8081/agronomia-api. El APK de desarrollo conserva por defecto la ruta PHP.
4. Criterio para retirar PHP: aceptar lotes de 1 y 5 desde Android, rechazar 6/corruptas, confirmar archivos y filas, verificar rollback/fallo de conexión y estabilidad; retirar solo tras aceptación del responsable.

React no existe en el repositorio y queda fuera del alcance. No hay panel administrativo que migrar.

## Referencias técnicas consultadas

- Requisitos Spring Boot 3.5: https://docs.spring.io/spring-boot/3.5/system-requirements.html
- Carga multipart Spring MVC: https://docs.spring.io/spring-boot/how-to/spring-mvc.html#howto.spring-mvc.multipart
- Java ImageIO WebP: https://github.com/usefulness/webp-imageio
