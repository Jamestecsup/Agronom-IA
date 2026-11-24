# Agronom-IA: servicio de administración (Spring Boot)

Servicio Java/Spring Boot del panel de administración. Comparte **una sola base
MySQL Server** con el backend Django: Django atiende al móvil y a la web de
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
- MySQL Server encendido (puerto 3306).
- Base `agronomia` con las tablas de Django ya migradas (ver `backend-django/README.md`).

No hace falta instalar Maven: el proyecto incluye el wrapper (`mvnw.cmd`).

## Cómo ejecutarlo

1. Inicia el servicio de Windows de MySQL Server.
2. Aplica las migraciones de Django una vez (crean las tablas internas `auth_*`
   y `django_*`, además de las tablas de Agronom-IA con nombres en español):
   ```powershell
   cd backend-django
   ..\scripts\migrar-db.ps1
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
| `DB_URL` | `jdbc:mysql://127.0.0.1:3306/agronomia` | Conexión MySQL |
| `DB_USERNAME` | requerido | Usuario de ejecución `agronomia_api` |
| `DB_PASSWORD` | requerida | Contraseña de la cuenta de ejecución |
| `SERVER_PORT` | `8081` | Puerto HTTP |
| `ADMIN_TOKEN_SECRET` | `dev-only-change-me` | Firma de los tokens del panel |
| `ADMIN_TOKEN_TTL_HOURS` | `8` | Duración de la sesión |
| `ADMIN_CORS_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | Orígenes permitidos del frontend |

Define estas variables en el proceso antes de arrancar Spring. Usar `agronomia_api` con los permisos de ejecución descritos en el [README raíz](../README.md#1-base-de-datos). Reserva `agronomia_admin` para las migraciones de Django. Consulta `application-example.env` para el entorno de Spring.

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
| `GET /api/admin/plants?limit=` | Identificaciones recientes con usuario propietario |
| `GET /api/admin/chats?limit=` | Mensajes recientes con usuario propietario |
| `POST /agronomia-api/api/images.php` | Contrato heredado de Fernando para subir fotos (el flujo activo usa el de Django). |

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
