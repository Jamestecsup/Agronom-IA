# Agronom-IA

Agronom-IA identifica plantas con Pl@ntNet y genera fichas orientativas de cuidado con IA. El usuario puede guardar identificaciones, chatear sobre una planta y planificar cuidados alrededor de su horario.

| Cliente | Backend local | API |
|---|---|---|
| Android y React de usuario (5174) | Spring Boot (8081) | `/agronomia-api/api/` |
| React de administración (5173) | Django (8000) | `/api/admin/` |
| Ambos backends | MySQL Server 8.4 (3306) | Esquema `agronomia` |

Django conserva su sitio nativo en `/admin/` con CSRF. Los backends comparten MySQL y la identidad de las cuentas. Spring escribe las imágenes en un directorio configurable. Si el panel necesita leer sus archivos, en un despliegue separado se monta ese directorio como solo lectura para Django.

## Instalación local en Windows

1. Arranca MySQL Server y aprovisiona la base con una copia temporal privada de `docs/sql/agronomia.sql`. Sustituye sus dos marcadores por contraseñas distintas, ejecuta la copia desde `mysql -u root -p` con `SOURCE` y elimina la copia.
2. Copia `backend-django/.env.example` a `backend-django/.env` y `backend-spring/application-example.env` a `backend-spring/.env`. Usa `agronomia_api` para ejecutar y `agronomia_admin` para migrar. Configura valores independientes para `DJANGO_SECRET_KEY` y `USER_TOKEN_SECRET`. Pon `AI_API_KEY` y `PLANTNET_API_KEY` solo en el entorno de Spring.
3. Prepara las dependencias:

```powershell
py -m venv backend-django\.venv
& backend-django\.venv\Scripts\python.exe -m pip install -r backend-django\requirements.txt
npm.cmd --prefix web-user ci
npm.cmd --prefix web-admin ci
```

4. Ejecuta la demo:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\iniciar-demo.ps1
powershell -ExecutionPolicy Bypass -File scripts\check-servicios.ps1
```

El script comprueba MySQL, aplica las migraciones de Django y arranca los cuatro servicios web. Spring registra la línea base Flyway 1 y valida el esquema. Los listeners usan `127.0.0.1` por defecto. Usa `-ExponerRed` solo si necesitas acceso desde la red y configura los hosts permitidos de Django y el firewall. `-ConEmulador` es independiente. Un servicio que ya escucha en su puerto se reutiliza. Para detener la demo cierra las ventanas de los procesos que abriste.

Para crear cuentas de demostración en desarrollo, ejecuta `manage.py seed_demo` desde Django después de migrar. Las cuentas son `admin` y `agricultor`. Consulta `DEMO_ADMIN_PASSWORD` y `DEMO_USER_PASSWORD` en el comando de alta si necesitas fijar valores propios.

## Contratos y mantenimiento

- La API de usuario acepta hasta cinco JPEG/PNG autenticados mediante `Authorization: Bearer`. Conserva la ruta `images.php` por compatibilidad con Android y web.
- El panel usa un token Bearer firmado por Django con vigencia de ocho horas. El usuario usa un token firmado por Spring. Las sesiones previas al cambio requieren iniciar sesión nuevamente.
- Las migraciones históricas de Django crean el esquema inicial. Los cambios futuros del dominio se migran en Flyway desde V2 y se reflejan en los modelos Django no gestionados.
- [Backend de usuario Spring](backend-spring/README.md) · [Panel Django](backend-django/README.md) · [IA](docs/CONFIG-IA.md) · [Base de datos](docs/BASE-DATOS.md) · [Progreso](backend-spring/PROGRESO.md)
