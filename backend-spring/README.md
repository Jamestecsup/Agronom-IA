# Backend Spring Boot · usuario

Spring Boot 3 y Java 17 sirven Android y la web de usuario en `127.0.0.1:8081/agronomia-api/api/`. Usa MySQL Connector/J, Flyway y PDFBox. El panel se sirve en Django.

## Arranque

Copia `application-example.env` a `.env`, configura las variables y lanza ambos backends desde la raíz con `scripts/iniciar-demo.ps1`. Para ejecutar Spring por separado, aprovisiona la base y ejecuta primero `scripts/migrar-db.ps1` desde la raíz. Luego ejecuta `.\mvnw.cmd spring-boot:run` desde este directorio. Spring lee su propio `.env`.

| Variables | Uso |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL Server 8.4 y cuenta `agronomia_api`. |
| `DB_MIGRATE_USER`, `DB_MIGRATE_PASSWORD` | Cuenta `agronomia_admin` para Flyway. |
| `USER_TOKEN_SECRET` | Firma de la API de usuario. Usa un secreto aleatorio de 24 caracteres como mínimo. |
| `PLANTNET_API_KEY` | Identificar especies. `PLANTNET_BASE_URL`, `PLANTNET_PROJECT` y `PLANTNET_LANG` son opcionales. |
| `AI_API_KEY`, `AI_BASE_URL`, `AI_MODEL` | Proveedor compatible con OpenAI. `AI_VISION_MODEL` usa el modelo de texto cuando se omite. |
| `AI_KEYS_MASTER_KEY` | Clave maestra AES-256 en Base64 (32 bytes aleatorios) para guardar perfiles propios. Mantén una copia protegida. |
| `AI_KEYS_KID`, `AI_KEYS_PREVIOUS` | Identificador activo (por defecto `v1`) y mapa JSON de claves históricas por identificador para rotación. Una alta o actualización de credencial cifra con el identificador activo. Conserva los identificadores históricos hasta reemplazar las credenciales. |
| `AI_ALLOW_PRIVATE_HOSTS` | Opt-in local de desarrollo, apagado por defecto. HTTP solo se admite para hosts locales permitidos. |
| `AI_SYSTEM_DAILY_QUOTA` | Máximo de intentos de proveedor del sistema por usuario al día en UTC. Por defecto, 25. Una ficha suele usar unas 5 llamadas. Ajusta la cuota al límite real del proveedor del sistema, teniendo en cuenta el uso conjunto de todas las cuentas y los reintentos. |
| `UPLOAD_DIR` | Directorio de imágenes, fotos de progreso y PDFs pendientes. Mantén el volumen privado. |

La cuenta de ejecución solo tiene permisos de datos. Flyway registra la línea base 1 después de que Django cree el esquema. V2 crea proveedores y preferencias y V3 crea el contador diario. Flyway usa `agronomia_admin`. Spring comprueba estas tablas después de migrar.

Spring mantiene Flyway 11.7.2 (núcleo y módulo MySQL). El aviso de MySQL 8.4 indica que el motor es más reciente que la versión verificada por ese componente. V2 y V3 se aplicaron correctamente en MySQL 8.4 y Spring verificó las tablas al arrancar. Esa comprobación cubre las migraciones actuales. Prueba cada nueva migración en MySQL 8.4 antes de desplegarla.

## Rutas y seguridad

La API conserva registro y login, `images.php`, `identify`, `confirm`, `plants` y portadas, `chat` e historial con fotos, `schedule` e importaciones y cuidados. Las rutas privadas requieren Bearer. La carga exige JPEG/PNG y usuario activo. Los intentos de login se limitan por cuenta e IP en cada proceso. Para un despliegue de varias instancias, aplica un límite compartido en el perímetro. `GET /agronomia-api/api/health` verifica la base sin usar cuota.

Ejecuta `.\mvnw.cmd -o test` con la caché de Maven disponible para las pruebas locales de imágenes, filtros, aislamiento de cuentas y proveedores simulados. La prueba en vivo queda desactivada por defecto.

## Contrato aditivo de IA y privacidad

Prefijo `/agronomia-api/api` y autenticación Bearer de usuario. Los perfiles y preferencias se devuelven con `Cache-Control: no-store`.

| Ruta | Contrato |
|---|---|
| GET `/ai/presets` | Devuelve `presets` con nombre, URL base e identificador del modelo para rellenar el formulario. |
| GET `/ai/providers` | Devuelve `active_id` y `providers` con estado, capacidades y `key_suffix`. |
| POST `/ai/providers` | Crea un perfil con `name`, `base_url`, `model`, `api_key` y límites opcionales `context_window`, `max_output_tokens`, `max_concurrency`. |
| PATCH o DELETE `/ai/providers/{id}` | Edita o quita un perfil propio. Si cambia la URL, se exige introducir la clave para ese destino. |
| POST `/ai/providers/{id}/test` | Prueba el modelo con datos sintéticos y devuelve un perfil actualizado. |
| PUT `/ai/selection` | Recibe `provider_id` y `consent`. El valor nulo vuelve al sistema. |
| GET `/ai/capabilities` | Devuelve `provider_id` y `features` con `available` y `reason` por función. |
| GET o PUT `/preferences` | Lee o guarda `{version: 1, preferences: {...}}`. Se admiten únicamente los tokens de la interfaz. |
| GET `/plants/{id}` | Devuelve `summary` y la ficha `plant` de una planta propia. |
| DELETE `/chat?plant_id=…` y `/chats?confirm=true` | Elimina mensajes y fotos con 204. Los archivos se aíslan durante la transacción y se purgan al confirmar. |

El proveedor propio recibe datos solo tras activación con consentimiento. La prueba de visión envía una imagen roja de 16 × 16 y exige la respuesta de color `rojo` o `red`. HTTPS con DNS validado y conexión fijada a las direcciones aprobadas es el modo normal. La cuota diaria del sistema se guarda en MySQL. Los límites de tasa, pruebas de conexión y concurrencia del proveedor propio son por proceso de Spring. Cada cinco minutos se retiran las entradas de horas anteriores sin solicitudes en curso. Para varias instancias, usa un límite compartido si necesitas un tope global.
