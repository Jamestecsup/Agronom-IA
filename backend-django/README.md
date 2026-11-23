# Backend Django (panel admin)

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
2. Crea la base y la tabla inicial de imágenes `plant_images`:

```powershell
C:\xampp\mysql\bin\mysql.exe -u root < ..\docs\sql\agronomia.sql
```

3. Aplica las tablas nuevas de Django:

```powershell
py manage.py migrate
```

La migración `0008_spanish_table_names` renombra las tablas propias de
Agronom-IA a nombres en español y conserva los registros. Las tablas internas
`auth_*` y `django_*` mantienen sus nombres de Django.

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

## API admin

| M?todo | Ruta | Descripci?n |
|---|---|---|
| POST | `/api/admin/auth/login` | Login admin. |
| GET | `/api/admin/auth/me` | Sesi?n actual. |
| GET | `/api/admin/health` | Estado del servicio/base. |
| GET | `/api/admin/stats` | Resumen del panel. |
| GET | `/api/admin/users` | Listado de usuarios. |
| PATCH | `/api/admin/users/<id>` | Actualiza rol/estado. |
| GET | `/api/admin/plants` | Identificaciones recientes. |
| GET | `/api/admin/chats` | Mensajes recientes. |

Los endpoints de usuario (`/agronomia-api/api/*`) ya no los sirve Django; los sirve Spring Boot en `:8081`.
