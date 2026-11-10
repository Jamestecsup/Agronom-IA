# Estructura de la base de datos

Agronom-IA usa la base MySQL `agronomia`. Django crea y gestiona las tablas de
usuarios, identificaciones y chat; Spring Boot las consulta para el panel de
administración. `plant_images` es una tabla heredada que Django conserva y
amplía mediante migraciones.

```mermaid
erDiagram
    AUTH_USER ||--o| CORE_PROFILE : tiene
    AUTH_USER ||--o{ PLANT_IMAGES : sube
    AUTH_USER ||--o{ CORE_IDENTIFICATIONRUN : inicia
    AUTH_USER ||--o{ CORE_PLANTIDENTIFICATION : identifica
    AUTH_USER ||--o{ CORE_CHATMESSAGE : conversa
    AUTH_USER ||--o{ CORE_CHATPROGRESSIMAGE : registra
    CORE_PLANTIDENTIFICATION ||--o{ CORE_CHATPROGRESSIMAGE : sigue
    CORE_CHATMESSAGE ||--o{ CORE_CHATPROGRESSIMAGE : adjunta
    AUTH_USER {
        int id PK
        string username
        string email
        bool is_active
    }
    CORE_PROFILE {
        int id PK
        int user_id FK
        string role
    }
    PLANT_IMAGES {
        string id PK
        int user_id FK
        string batch_id
        string relative_path
        datetime created_at
    }
    CORE_IDENTIFICATIONRUN {
        uuid id PK
        int user_id FK
        string batch_id
        string status
        json raw_json
        datetime created_at
    }
    CORE_PLANTIDENTIFICATION {
        uuid id PK
        int user_id FK
        string batch_id
        string scientific_name
        string common_name
        string family
        float confidence
        json result_json
        datetime created_at
    }
    CORE_CHATMESSAGE {
        uuid id PK
        int user_id FK
        string session_id
        string role
        text content
        datetime created_at
    }
    CORE_CHATPROGRESSIMAGE {
        uuid id PK
        int user_id FK
        uuid plant_id FK
        uuid message_id FK
        string relative_path
        string mime_type
        bigint file_size
        text caption
        datetime created_at
    }
```

Las columnas `user_id` admiten `NULL` para conservar los registros que ya
existían antes de asociar cada operación a una cuenta. Las operaciones nuevas
requieren iniciar sesión y guardan el usuario autenticado en esas relaciones.
Si se elimina una cuenta, sus registros se conservan y quedan sin usuario.

El chat usa un `session_id` estable derivado del UUID de cada identificación.
Así, cada planta identificada tiene su propia conversación y el usuario puede
retomarla desde la web o el móvil. El historial se consulta siempre por
`user_id` y `session_id`, de modo que dos cuentas nunca comparten mensajes.

Las fotos de progreso pertenecen a una planta y a un mensaje de su conversación.
La base conserva su ruta, tipo, tamaño y descripción; el archivo se guarda en
`PLANT_UPLOAD_DIR`. La galería del móvil filtra por la identificación de la
planta y no mezcla las fotos iniciales del escáner con las fotos de progreso.

Al actualizar una base ya creada, aplica las migraciones después de iniciar
MySQL:

```powershell
cd backend-django
py manage.py migrate
```
