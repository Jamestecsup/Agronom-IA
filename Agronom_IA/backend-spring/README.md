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

Ejecuta `mvn test`. Las pruebas automatizadas usan H2 en memoria y almacenamiento temporal; no necesitan claves, no usan datos reales y no conectan a MySQL de desarrollo. Pasaron 20 pruebas. Cubren el contrato de imágenes, selección 1/5/6, formatos/corrupción, rollback/limpieza, selección autorizada de proveedores, streaming simulado, límites, errores de tasa y respuestas JSON 400/503 previas al SSE.

La compatibilidad del DDL se comprobó leyendo SHOW CREATE TABLE; la cuenta API no tenía permisos para consultarlo. Se levantó el servidor contra la base aislada agronomia_spring_migration_test_20261013, con DDL equivalente: GET fue 405, cinco fotos devolvieron 201 con cinco filas/archivos JPEG, seis recibieron 422 y un GIF disfrazado fue 415 sin filas adicionales. No se escribió en la base agronomia de trabajo.

La base de prueba y cinco imágenes sintéticas permanecen para inspección: agronomia_spring_migration_test_20261013 y %TEMP%\agronomia-spring-mariadb-test-images. La herramienta bloqueó la operación destructiva de limpieza; los artefactos quedan aislados y no contienen datos de usuario.

## Chat web multi-modelo (desarrollo local)

El módulo `ai` agrega una API común de chat para dos proveedores configurados por el servidor. `GET /api/ai/models` publica solo identificadores, etiquetas y disponibilidad; el navegador nunca envía una URL ni credenciales. `POST /api/ai/chat` recibe `providerId` permitido (`deepseek-opencode` o `qwen-local`) y un historial acotado, y responde como SSE (`delta`, `done`, `error`). Cancelar el `AbortController` del navegador cierra la conexión y Spring cancela la lectura del proveedor. La selección se aplica a mensajes siguientes; el historial textual se conserva en el navegador y se reenvía como contexto a cada proveedor, sin asumir memoria compartida. No hay persistencia del chat en MySQL.

Proveedores:

- `qwen-local`: Open WebUI con `GET /api/models` para descubrir el identificador autorizado y `POST /api/chat/completions` con SSE compatible con OpenAI. La clave solo se lee desde `OPENWEBUI_API_KEY` en Spring.
- `deepseek-opencode`: adaptador al servidor OpenCode, usando `GET /global/health`, `GET /provider` y endpoints de sesiones/eventos. Modelo configurado por `OPENCODE_MODEL_ID`, cuyo valor por defecto es `deepseek-v4.1-flash-free`.

Para iniciar React en desarrollo usa `npm install`, `npm run dev` dentro de `frontend-web`; Vite proxya las rutas API a Spring. Para servir la web en el mismo puerto de Spring, ejecuta `npm run build` primero y después empaqueta/ejecuta el backend; Maven incluye `frontend-web/dist` en `static/`. El módulo web guarda historial únicamente en el `localStorage` del navegador.

Configuración adicional: `OPENWEBUI_BASE_URL`, `OPENWEBUI_API_KEY`, `OPENWEBUI_MODEL`, `OPENCODE_SERVER_BASE_URL`, `OPENCODE_SERVER_USERNAME`, `OPENCODE_SERVER_PASSWORD`, `OPENCODE_MODEL_ID`, `AI_CHAT_CONNECT_TIMEOUT` y `AI_CHAT_READ_TIMEOUT`. `application-example.env` muestra nombres y ejemplos inocuos; Spring no carga `.env` automáticamente. No se versiona ninguna clave real.

Verificación actual (2026-10-19): OpenCode CLI 1.19.3 anuncia `opencode/deepseek-v4.1-flash-free`; tanto el CLI como `opencode run --attach http://127.0.0.1:4096` devolvieron respuestas reales. El endpoint Spring respondió HTTP 200 con deltas y `done` para DeepSeek y Qwen; ambos aparecieron disponibles en `GET /api/ai/models`. La regresión del stream se debía a que OpenCode 1.19+ envuelve los eventos globales en `payload` y Spring leía `type` en el nivel superior. El adaptador ahora desenvuelve el evento, conserva compatibilidad con el formato anterior y reconoce `session.status=idle`. Maven: 22 pruebas pasan; React: 4 pruebas y build pasan; Android: assembleDebug y testDebugUnitTest pasan. No se cambió MySQL ni la configuración del modelo Qwen.

## Análisis multimodal desde el chat web

El chat web acepta una o varias imágenes mediante el selector del sistema, arrastrar y soltar sobre la ventana de conversación o pegado con `Ctrl+V` / `Cmd+V` en el campo de texto. Los tres mecanismos usan el mismo validador y conservan el borrador. La selección se limita a cinco imágenes por mensaje (misma política de lotes de la app), 15 MiB por archivo (límite de imagen que ya rige en Spring) y 50 MiB acumulados. El tope acumulado se deriva del máximo de petición Spring de 80 MB: cinco imágenes en Base64 expanden los bytes aproximadamente 4/3, y 50 MiB de contenido queda debajo del transporte incluyendo el historial textual. El tamaño individual queda por debajo del máximo de 20 MiB por adjunto documentado por OpenCode. La aplicación admite JPEG, PNG, WebP y GIF si los bytes, MIME de imagen declarado (si existe), dimensiones (16–4096 px) y decodificación coinciden. No acepta HEIF/HEIC ni SVG en este flujo. El backend vuelve a validar firma/contenido con ImageIO antes de llamar a un proveedor. No hay almacenamiento en MySQL.

`GET /api/ai/models` publica `supportsImages` solo cuando el catálogo/configuración del proveedor confirma la capacidad: OpenCode expone `capabilities.input.image`; Open WebUI, `info.meta.capabilities.vision`. En la instancia comprobada se detectó `deepseek-v4.1-flash-free` con entrada de imagen y `Qwen/Qwen3.6-35B-A3B-FP8` con `vision=true`. Si falta ese metadato, la UI conserva texto y desactiva adjuntos.

`POST /api/ai/chat` mantiene SSE y acepta en cada mensaje opcionalmente `images: [{ fileName, mimeType, data }]`, donde `data` es Base64 de los bytes originales. Es necesario codificar el contenido para los endpoints usados por estos proveedores; el navegador no recibe sus claves. Spring transforma cada imagen en los contratos reales: `FilePartInput` con `type=file`, `mime`, `filename` y `data:` URL para `POST /session/{sessionID}/prompt_async` de OpenCode; `content[]` con `type=image_url` y URL `data:` para `/api/chat/completions` compatible con OpenAI en Open WebUI. Las respuestas se siguen transmitiendo mediante el flujo SSE existente. Las miniaturas JPEG de 320 px quedan con los mensajes del historial de `localStorage`; los originales se guardan únicamente en IndexedDB del navegador para que puedan volver a incluirse en el contexto y se eliminan al borrar la conversación. Si IndexedDB falla, la imagen se usa en el turno actual y la UI lo informa.

Las instancias locales reales declararon ambas capacidades. Se envió por Spring la misma imagen sintética con una maceta azul y se pidió el color: tanto `deepseek-opencode` como `qwen-local` respondieron “Azul” (HTTP 200, SSE terminado). Esta verificación es una prueba puntual de transmisión visual, no una garantía de calidad de identificación botánica para cualquier imagen. OpenCode documenta soporte para JPEG/PNG/GIF/WebP y normaliza imágenes a dimensiones por defecto de 2000 px y Base64 de 5 MiB (por lo común reduciendo imágenes mayores); el proveedor puede imponer límites adicionales. Open WebUI indica que la imagen llega al modelo solo si la instancia habilita visión, y el catálogo consultado no expuso un límite por archivo.

Pruebas automatizadas actuales: `mvn clean package` (25 pruebas) y `npm test -- --run` (9 pruebas); `npm run build` valida tipos y bundle. Se añadieron pruebas para contenido corrupto, MIME falsificado, límite de cantidad, detección por firma y contrato multimodal de Open WebUI. Pruebas reales (19-oct-2026): `/api/ai/models` anunció los dos proveedores disponibles con soporte de imágenes; ambos procesaron la imagen por el backend y devolvieron color correcto. Se abrió la interfaz web local y se verificó su estado accesible, el saludo Qwen y la presencia del control de adjuntar; no se automatizaron el selector nativo, el arrastre ni el portapapeles en navegador. No se ha comprobado un conjunto amplio de cámaras, formatos HEIF ni todos los límites internos del servidor Qwen.
## Migración por fases

1. Implementado: API equivalente Spring en puerto 8081; PHP se conserva. Sin cambios de esquema.
2. Pruebas automatizadas y de integración: H2 y MariaDB aislada verificaron contratos, validaciones, persistencia y compensación sin usar la base de trabajo.
3. Siguiente paso: probar carga desde Android Emulator con PLANT_API_BASE_URL=http://10.0.2.2:8081/agronomia-api. El APK de desarrollo conserva por defecto la ruta PHP.
4. Criterio para retirar PHP: aceptar lotes de 1 y 5 desde Android, rechazar 6/corruptas, confirmar archivos y filas, verificar rollback/fallo de conexión y estabilidad; retirar solo tras aceptación del responsable.

React se agregó como chat de usuario, no como panel administrativo. No hay un módulo administrativo React ni persistencia MySQL para conversaciones.

Los metadatos públicos de `GET /api/ai/models` incluyen un saludo inicial visual por modelo. React lo muestra solo en conversaciones vacías; no se persiste ni se envía al modelo. Los saludos siguen al modelo de la conversación activa, y cambiarlo conserva los mensajes existentes.

## Referencias técnicas consultadas

- Requisitos Spring Boot 3.5: https://docs.spring.io/spring-boot/3.5/system-requirements.html
- Carga multipart Spring MVC: https://docs.spring.io/spring-boot/how-to/spring-mvc.html#howto.spring-mvc.multipart
- Java ImageIO WebP: https://github.com/usefulness/webp-imageio
