# Agronom-IA: backend Spring Boot (usuario/web/m?vil)

Servicio Java/Spring Boot del **usuario, web y m?vil**. Atiende auth, im?genes, identificaci?n, chat e horario/plan de cuidados en `/agronomia-api/api/*`. Comparte la **misma base MySQL** con Django, que ahora atiende el panel admin (`/api/admin/*`).

El m?dulo `images` y partes admin heredadas se conservan en el c?digo base, pero la responsabilidad de admin web pas? a Django.

## Módulos

    src/main/java/com/agronomia/backend/
      AgronomiaBackendApplication.java
      images/     subida de imágenes heredada (PHP-compatible)
      admin/      panel: login con roles, usuarios, plantas, chat y estadísticas

## Requisitos

- JDK 17 (el proyecto usa Java 17).
- MySQL/MariaDB de XAMPP encendido (puerto 3306).
- Base `agronomia` con las tablas de Django ya migradas (ver `backend-django/README.md`).

No hace falta instalar Maven: el proyecto incluye el wrapper (`mvnw.cmd`).

## Cómo ejecutarlo

1. Enciende MySQL desde el panel de XAMPP.
2. Aplica las migraciones de Django una vez (crean las tablas internas `auth_*`
   y `django_*`, además de las tablas de Agronom-IA con nombres en español):
   ```powershell
   cd backend-django
   py manage.py migrate
   py manage.py seed_demo
   ```
3. Arranca Spring:
   ```powershell
   cd backend-spring
   .\mvnw.cmd spring-boot:run
   ```

El servicio queda en `http://127.0.0.1:8081`.

### Variables de entorno

| Variable | Por defecto | Uso |
|---|---|---|
| `DB_URL` | `jdbc:mariadb://127.0.0.1:3306/agronomia` | Conexión MySQL |
| `DB_USERNAME` | `root` | Usuario (XAMPP) |
| `DB_PASSWORD` | vacío | Contraseña |
| `SERVER_PORT` | `8081` | Puerto HTTP |
| `ADMIN_TOKEN_SECRET` | `dev-only-change-me` | Firma de los tokens del panel |
| `ADMIN_TOKEN_TTL_HOURS` | `8` | Duración de la sesión |
| `ADMIN_CORS_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | Orígenes permitidos del frontend |

En un equipo compartido cambia `ADMIN_TOKEN_SECRET` y usa la cuenta
`agronomia_api` creada por `docs/sql/agronomia.sql` en lugar de `root`.

## Usuarios demo

`py manage.py seed_demo` crea (solo para desarrollo local):

| Usuario | Contraseña | Rol |
|---|---|---|
| `admin` | `Admin12345` | admin (entra al panel) |
| `agricultor` | `Campo12345` | user (solo móvil/web de usuario) |

Puedes cambiarlas con `DEMO_ADMIN_PASSWORD` / `DEMO_USER_PASSWORD` y
`--reset-passwords`.

## Endpoints de usuario

| M?todo y ruta | Descripci?n |
|---|---|
| `POST /agronomia-api/api/register` | Registro usuario. |
| `POST /agronomia-api/api/login` | Login usuario. |
| `GET /agronomia-api/api/health` | Estado del servicio/base. |
| `POST /agronomia-api/api/images.php` | Sube fotos y devuelve batch. |
| `POST /agronomia-api/api/identify` | Identifica especie con Pl@ntNet + IA. |
| `POST /agronomia-api/api/confirm` | Confirma candidata y genera ficha. |
| `GET /agronomia-api/api/plants` | Plantas del usuario. |
| `GET /agronomia-api/api/plants/{id}/cover` | Portada de planta. |
| `GET /agronomia-api/api/chat/history?plant_id=` | Historial de chat. |
| `GET/POST /agronomia-api/api/chat` | Chat con imagen opcional. |
| `GET /agronomia-api/api/chat/images?plant_id=` | Im?genes de progreso. |
| `GET /agronomia-api/api/chat/images/{imageId}` | Descarga imagen progreso. |
| `GET /agronomia-api/api/schedule` | Horario y cuidados. |
| `POST /agronomia-api/api/schedule/import` | Importa PDF horario. |
| `GET/POST/DELETE /agronomia-api/api/schedule/import/{id}` | Gesti?n importaci?n. |
| `POST /agronomia-api/api/schedule/care-plan/draft` | Borrador plan cuidados. |
| `POST /agronomia-api/api/schedule/care-plan/confirm` | Confirmar plan cuidados. |
| `PATCH/DELETE /agronomia-api/api/schedule/care-plan/tasks/{id}` | Reprogramar/eliminar tarea. |

## Seguridad

- Las contraseñas se validan con el mismo formato de Django
  (`pbkdf2_sha256$iteraciones$salt$hash`): no se duplican usuarios ni se cambia
  el almacenamiento.
- El token va firmado con HMAC-SHA256 y caduca; no se guarda en base de datos.
- No hay permisos de escritura sobre plantas ni chat: el panel solo lee esos datos.
- El servicio escucha en loopback por defecto; no lo expongas a la red sin TLS y
  autenticación.
