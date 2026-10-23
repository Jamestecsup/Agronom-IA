# Agronom-IA: servicio de administración (Spring Boot)

Servicio Java/Spring Boot del panel de administración. Comparte **una sola base
MySQL (XAMPP)** con el backend Django: Django atiende al móvil y a la web de
usuario (identificación con Pl@ntNet + Gemini), y este servicio atiende a la web
de administración (login con roles, usuarios, identificaciones y estadísticas).

El módulo `images` proviene del trabajo de Fernando (rama `Fernando`) y se
conserva como contrato heredado de subida de imágenes.

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
2. Aplica las migraciones de Django una vez (crean `auth_user`, `core_profile`,
   `core_plantidentification`, `core_chatmessage`, etc.):
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

## Endpoints del panel

| Método y ruta | Descripción |
|---|---|
| `POST /api/admin/auth/login` | Login; exige rol `admin` y devuelve token |
| `GET /api/admin/auth/me` | Sesión actual (requiere token) |
| `GET /api/admin/health` | Estado del servicio y de la base (público) |
| `GET /api/admin/stats` | Totales, especies más identificadas y actividad de 14 días |
| `GET /api/admin/users?q=&limit=` | Usuarios con su rol y estado |
| `PATCH /api/admin/users/{id}` | Cambia `role` y/o `active` (no puedes quitarte tu propio rol) |
| `GET /api/admin/plants?limit=` | Identificaciones recientes |
| `GET /api/admin/chats?limit=` | Mensajes de chat recientes |

Las rutas protegidas esperan `Authorization: Bearer <token>`.

Ejemplo:

```powershell
curl.exe -s http://127.0.0.1:8081/api/admin/health

curl.exe -s -X POST http://127.0.0.1:8081/api/admin/auth/login `
  -H "Content-Type: application/json" `
  -d '{\"username\":\"admin\",\"password\":\"Admin12345\"}'
```

## Seguridad

- Las contraseñas se validan con el mismo formato de Django
  (`pbkdf2_sha256$iteraciones$salt$hash`): no se duplican usuarios ni se cambia
  el almacenamiento.
- El token va firmado con HMAC-SHA256 y caduca; no se guarda en base de datos.
- No hay permisos de escritura sobre plantas ni chat: el panel solo lee esos datos.
- El servicio escucha en loopback por defecto; no lo expongas a la red sin TLS y
  autenticación.
