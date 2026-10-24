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
- MySQL/MariaDB de XAMPP en `127.0.0.1:3306` (o SQLite para pruebas).

## Instalación

```powershell
cd backend-django
py -m pip install -r requirements.txt
Copy-Item .env.example .env    # rellena las claves
```

### Base de datos

1. Arranca MySQL desde el panel de XAMPP.
2. Crea la base y la tabla legada `plant_images`:

```powershell
C:\xampp\mysql\bin\mysql.exe -u root < ..\docs\sql\agronomia.sql
```

3. Aplica las tablas nuevas de Django:

```powershell
py manage.py migrate
```

> Para pruebas rápidas sin MySQL: `$env:USE_SQLITE="1"` antes de `migrate`
> y `runserver`.

### Usuarios demo y versión de Django

```powershell
py manage.py seed_demo
```

Crea `admin` / `Admin12345` (rol admin) y `agricultor` / `Campo12345` (rol user).
Solo para desarrollo local.

La MariaDB de XAMPP es 10.4, por eso el proyecto fija **Django 4.2**: las
versiones 5.x exigen MariaDB 10.5 o superior.

## Ejecutar

```powershell
py manage.py runserver 0.0.0.0:8000
```

El emulador Android llega al host con `http://10.0.2.2:8000`.

## API

| Método | Ruta | Descripción |
| --- | --- | --- |
| GET | `/agronomia-api/api/health` | Estado del servicio y de la base (público, sin IA). |
| POST | `/agronomia-api/api/images.php` | Sube 1–5 fotos (`photos[]`). Devuelve `batch_id` + imágenes. |
| POST | `/agronomia-api/api/identify` | `batch_id`, `organ`. Ejecuta Pl@ntNet + Gemini. |
| POST | `/agronomia-api/api/confirm` | `batch_id`, `scientific_name`. Ficha de la candidata elegida reutilizando la corrida de Pl@ntNet. |
| POST | `/agronomia-api/api/chat` | `message`, `session_id`. Chat con contexto de la planta. |
| POST | `/agronomia-api/api/register` | `username`, `password`, `email`. Siempre crea rol `user` (el admin se asigna en el servidor). |
| POST | `/agronomia-api/api/login` | `username`, `password`. Devuelve el rol. |

### Respuesta de `identify`

```json
{
  "success": true,
  "status": "identified | ambiguous | unidentified",
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
cuando es `unidentified` llega `message` (menos de 20 %).

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
