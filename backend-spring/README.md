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
| `UPLOAD_DIR` | Directorio de imágenes, fotos de progreso y PDFs pendientes. Mantén el volumen privado. |

La cuenta de ejecución solo tiene permisos de datos. Flyway registra la línea base 1 después de que Django cree el esquema. Toda alteración posterior del dominio se versiona desde V2 y se refleja en los modelos de Django.

## Rutas y seguridad

La API conserva registro y login, `images.php`, `identify`, `confirm`, `plants` y portadas, `chat` e historial con fotos, `schedule` e importaciones y cuidados. Las rutas privadas requieren Bearer. La carga exige JPEG/PNG y usuario activo. Los intentos de login se limitan por cuenta e IP en cada proceso. Para un despliegue de varias instancias, aplica un límite compartido en el perímetro. `GET /agronomia-api/api/health` verifica la base sin usar cuota.

Ejecuta `.\mvnw.cmd test` para las pruebas locales de imágenes y proveedores HTTP simulados.
