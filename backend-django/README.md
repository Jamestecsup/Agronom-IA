# Backend Django (servicio de usuario)

Backend del **usuario** (móvil Kotlin + web React). Hace todo el análisis:

1. Recibe 1–5 fotos y las valida/almacena.
2. Llama a **Pl@ntNet** para identificar la especie.
3. Llama a **Gemini** con las **skills** versionadas en `skills/` para generar
   la ficha (8 categorías), el glosario, los materiales y el chat.

El panel de **administrador** usa el backend Spring Boot (rama `Fernando`) y
comparte la **misma base de datos MySQL**.

## Requisitos

- Python 3.11+ (probado con 3.14).
- MySQL Server en `127.0.0.1:3306` (o SQLite para pruebas).

## Instalación

```powershell
cd backend-django
py -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
Copy-Item .env.example .env    # solo si no existe
.\.venv\Scripts\Activate.ps1
```

### Base de datos

1. Inicia MySQL Server en `127.0.0.1:3306` y prepara la base siguiendo el [README raíz](../README.md#1-base-de-datos).
2. Configura `DB_USER=agronomia_api` para la ejecución y `DB_MIGRATE_USER=agronomia_admin` para mantenimiento en `.env`, con sus contraseñas respectivas.
3. Desde `backend-django/` aplica las migraciones:

```powershell
..\scripts\migrar-db.ps1
```

La migración `0008_spanish_table_names` renombra las tablas propias de
Agronom-IA a nombres en español y conserva los registros. Las tablas internas
`auth_*` y `django_*` mantienen sus nombres de Django.

> Para pruebas rápidas sin MySQL: `$env:USE_SQLITE="1"` antes de `python manage.py migrate`
> y `runserver`.

### Usuarios demo y versión de Django

```powershell
python manage.py seed_demo
```

Crea `admin` / `Admin12345` (rol admin) y `agricultor` / `Campo12345` (rol user).
Solo para desarrollo local.

El proyecto usa Django 4.2 con PyMySQL y `cryptography` para autenticar con MySQL Server.

## Ejecutar

```powershell
python manage.py runserver 0.0.0.0:8000
```

El emulador Android llega al host con `http://10.0.2.2:8000`.

## API

| Método | Ruta | Descripción |
| --- | --- | --- |
| GET | `/agronomia-api/api/health` | Estado del servicio y de la base (público, sin IA). |
| GET | `/agronomia-api/api/plants` | Plantas identificadas guardadas por la cuenta autenticada. |
| GET | `/agronomia-api/api/plants/<uuid>/cover` | Foto de identificación para las tarjetas del chat. |
| GET | `/agronomia-api/api/chat/history?plant_id=<uuid>` | Recupera el chat persistente de una identificación propia. |
| GET | `/agronomia-api/api/chat/images?plant_id=<uuid>` | Galería de progreso del chat de una planta. |
| POST | `/agronomia-api/api/images.php` | Sube 1–5 fotos (`photos[]`). Devuelve `batch_id` + imágenes. |
| POST | `/agronomia-api/api/identify` | `batch_id`, `organ`. Ejecuta Pl@ntNet + Gemini. |
| POST | `/agronomia-api/api/confirm` | `batch_id`, `scientific_name`. Ficha de la candidata elegida reutilizando la corrida de Pl@ntNet. |
| POST | `/agronomia-api/api/chat` | `message`, `plant_id` y foto opcional `image`. Analiza y guarda el progreso dentro del chat de esa planta. |
| POST | `/agronomia-api/api/register` | `username`, `password`, `email`. Siempre crea rol `user` (el admin se asigna en el servidor). |
| POST | `/agronomia-api/api/login` | `username`, `password`. Devuelve rol y token firmado. |

Las rutas de fotos, identificación, confirmación, lista de plantas e historial/chat requieren el token
devuelto por `/login` en `Authorization: Bearer <token>`.

Las fotos enviadas desde el chat se guardan vinculadas a su mensaje, planta y
cuenta. La galería de Mi Jardín solo lista esas fotos de progreso; las fotos de
identificación se usan únicamente como portada de las tarjetas del chat.

### Autenticación y propiedad de los datos

`POST /login` devuelve un token firmado válido por 14 días. La web y la app
móvil lo envían como `Authorization: Bearer <token>` al subir fotos, identificar,
confirmar candidatas y conversar. Django usa el usuario del token para guardar
`user_id` en las fotos, corridas, fichas y mensajes; nunca confía en un nombre
de usuario enviado por el cliente.

Las filas antiguas conservan `user_id = NULL` porque no existe una forma segura
de atribuirlas retrospectivamente. La estructura está descrita en
[`docs/BASE-DATOS.md`](../docs/BASE-DATOS.md).

### Respuesta de `identify`

```json
{
  "success": true,
  "status": "identified | ambiguous | unidentified | insufficient",
  "plant": {
    "scientific_name": "Helianthus giganteus",
    "common_name": "Girasol gigante",
    "family": "Asteraceae",
    "confidence": 0.57,
    "sections": [
      { "key": "description", "title": "Descripción", "body": "...", "terms": [], "materials": [] }
    ]
  }
}
```

Cuando `status` es `ambiguous` llega `candidates` (20–49 % de confianza);
cuando es `unidentified` llega `message` (menos de 20 %). Si la especie se
identificó pero la IA no tiene datos de cuidado, el estado es `insufficient`
y llega `message` + `plant_name`; las categorías sin datos muestran un texto
de respaldo con pautas generales (nunca la palabra «unknown»).

## Skills

Cada proceso y cada categoría tiene su propia skill en `skills/`:

- Procesos: `refine-prompt`, `glossary`, `materials`, `chat-assistant`.
- Categorías (`skills/categories/`): `description`, `light`, `watering`,
  `soil`, `climate`, `flowering`, `uses`, `care`.
- La guía de «cómo tomar mejores fotos» es texto de interfaz y vive en la app
  (constantes de Compose), no es una skill.

Django las **carga en runtime** en cada petición (`core/skills.py`), así que
se pueden editar sin recompilar (el proceso las relee en la siguiente petición).

> Para no agotar la cuota gratuita de Gemini (429 por ráfagas), el pipeline
> hace **4 llamadas**: refine, categorías, glosario y materiales. Las 8 skills
> de categoría se envían juntas en la llamada de categorías.

## Umbrales de confianza

| Confianza Pl@ntNet | Resultado |
| --- | --- |
| ≥ 50 % | `identified` (ficha completa) |
| 20–49 % | `ambiguous` (candidatas + guía) |
| < 20 % | `unidentified` (guía) |

Se ajustan en `core/services/pipeline.py` (`MIN_CONFIDENCE` y
`CONFIDENT_CONFIDENCE`).

## Pruebas

```powershell
python manage.py test core.tests
```

Son pruebas del pipeline con Pl@ntNet y Gemini simulados (`unittest.mock`):
umbrales, categorías «unknown», texto de respaldo, estado `insufficient` y
reintento del cliente de IA ante JSON inválido. No gastan cuota ni necesitan
la base de datos.
