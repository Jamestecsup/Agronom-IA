# Agronom-IA

Asistente botánico para agricultores: se sube una foto de una planta, se
identifica la especie y se devuelve una **ficha clara en español** (luz, riego,
suelo, clima, floración, usos y cuidados) con glosario y materiales. Después se
puede **conversar** con un asistente que conoce la planta detectada.

El trabajo pesado (Pl@ntNet + Gemini con *skills*) lo hace el **backend de
usuario en Django**; la app móvil y la web de usuario solo envían fotos y
mensajes. El panel de administración es un **servicio aparte en Spring Boot**
que comparte la misma base de datos MySQL.

## Arquitectura

```
                    ┌──────────────────────────┐
   App Android ────►│                          │
   (Kotlin/Compose) │  Django (servicio de      │
                    │  usuario) :8000           │──► Pl@ntNet
   Web de usuario ─►│  identifica, ficha, chat  │──► Gemini (skills)
   (React) :5174    │  login + roles            │
                    └────────────┬─────────────┘
                                 │  MySQL Server :3306
                                 │  base `agronomia`
                    ┌────────────┴─────────────┐
                    │  Spring Boot (servicio    │
   Panel admin ────►│  de administración) :8081 │
   (React) :5173    │  usuarios, plantas, chat  │
                    └──────────────────────────┘
```

- **App Android** (`App movil/app/`): Kotlin + Jetpack Compose, paquete
  `com.agronomia`. Inicio de sesión, escáner, ficha y chat; Mi Jardín y
  Calendario. El horario semanal se importa desde el PDF oficial, se revisa en
  el móvil y se guarda por usuario en MySQL. Django autentica y delega la lectura
  del PDF y la propuesta de cuidados a Spring. Las entregas de Canvas aparecen
  en una pestaña aparte y no intervienen en el plan de cuidados.
- **Backend Django** (`backend-django/`): API del usuario. Ejecuta el pipeline
  de identificación y carga las *skills* en tiempo de ejecución desde
  `backend-django/skills/`.
- **Backend Spring Boot** (`backend-spring/`): API del administrador y servicio
  interno protegido para analizar el PDF del horario y proponer cuidados con IA.
  Django mantiene la autenticación del usuario móvil.
- **Web de usuario** (`web-user/`): React. Escáner, ficha, glosario, jardín y
  conversaciones persistentes por planta.
- **Panel de administración** (`web-admin/`): React. Resumen, usuarios, plantas
  y chat.

### Puertos

| Servicio | URL |
|---|---|
| Django (usuario) | http://127.0.0.1:8000 |
| Spring Boot (admin) | http://127.0.0.1:8081 |
| Web de usuario | http://localhost:5174 |
| Panel de administración | http://localhost:5173 |
| MySQL Server | 127.0.0.1:3306 |

### Capturas de la app móvil

| Escáner | Especies parecidas | Ficha |
|---|---|---|
| ![Escáner](docs/img/movil-escaner.jpg) | ![Dudas](docs/img/movil-dudas.jpg) | ![Ficha](docs/img/movil-ficha.jpg) |

## Puesta en marcha

### Inicio rápido para la demo

Con MySQL, las dependencias de Python/Node y el jar de Spring ya instalados:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\iniciar-demo.ps1 -ConEmulador
```

Comprueba que MySQL Server responde en el puerto 3306. Luego arranca Django → Spring → webs → emulador y respeta lo que ya
esté corriendo y al final ejecuta `check-servicios.ps1`. Sin emulador, omite
`-ConEmulador`. Cada servicio queda en su propia ventana: no las cierres
durante la demo.

**Recorrido sugerido:** identificación clara → ficha; foto con dudas →
candidatas → ficha; imagen sin planta → guía; abre Chat para escoger una planta
y continuar su conversación desde la web o el móvil; y panel de administración
con los datos compartidos. Las cuentas están más abajo.

Los pasos detallados de instalación siguen a continuación.

### 1. Base de datos

1. Inicia el servicio de Windows de MySQL Server y confirma que escucha en `127.0.0.1:3306`.
2. Usa una copia temporal privada de `docs/sql/agronomia.sql`. Sustituye los marcadores con las contraseñas de `DB_PASSWORD` y `DB_MIGRATE_PASSWORD` de tu entorno local. En el cliente `mysql -u root -p`, verifica `SELECT VERSION();` y ejecuta la copia con `SOURCE`. Elimina la copia al terminar.
3. Usa `agronomia_api` en ambos backends. Reserva `agronomia_admin` para `scripts/migrar-db.ps1`, que aplica las migraciones de Django.

### 2. Backend Django (servicio de usuario)

```powershell
cd backend-django
py -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
Copy-Item .env.example .env    # completa tus claves
..\scripts\migrar-db.ps1
python manage.py seed_demo         # usuarios de demo
python manage.py runserver 0.0.0.0:8000
```

Las claves (`AI_API_KEY`, `PLANTNET_API_KEY`) van **solo** en `.env`, que
está ignorado por Git. La app y las webs nunca reciben esas claves. El script
de demo comparte con Spring la clave interna `SCHEDULE_INTERNAL_TOKEN` y la
configuración de IA para el procesamiento de horarios. El arranque de la demo también requiere que `AI_API_KEY` esté configurada. Tras agregar las tablas,
ejecuta `..\scripts\migrar-db.ps1`. Sin MySQL
se puede trabajar con SQLite y ejecutar directamente `python manage.py migrate`: `$env:USE_SQLITE=1`.

Detalle: [`backend-django/README.md`](backend-django/README.md).

### 3. Backend Spring Boot (administración y análisis del horario)

```powershell
cd backend-spring
.\mvnw.cmd -q -DskipTests package
# Configura las variables del proceso según backend-spring/application-example.env.
java -jar target/agronomia-backend-0.1.0-SNAPSHOT.jar
```

El servicio interno de horarios se limita a `127.0.0.1` por defecto y acepta
llamadas de Django con `X-Schedule-Service-Key`. La app móvil nunca llama a
Spring directamente.

Detalle: [`backend-spring/README.md`](backend-spring/README.md).

### 4. Webs

```powershell
cd web-user ; npm install ; npm run dev    # http://localhost:5174
cd web-admin; npm install ; npm run dev    # http://localhost:5173
```

Detalle: [`web-user/README.md`](web-user/README.md) y
[`web-admin/README.md`](web-admin/README.md).

### 5. App Android

Abre la carpeta `App movil/` en Android Studio. Crea allí `local.properties`
con la ruta del SDK y la URL del backend:

```properties
sdk.dir=C\:\\Android\\Sdk
BACKEND_BASE_URL=http://10.0.2.2:8000/
```

Luego ejecuta `App movil\gradlew.bat installDebug`. El emulador llega al PC
con `10.0.2.2`. Consulta [`App movil/README.md`](<App movil/README.md>) para
los detalles.

## Cuentas de demo

Se crean con `python manage.py seed_demo` (solo desarrollo):

| Usuario | Contraseña | Rol | Entra en |
|---|---|---|---|
| `admin` | `Admin12345` | admin | panel de administración |
| `agricultor` | `Campo12345` | user | app móvil y web de usuario |

El panel rechaza las cuentas con rol `user` (403) y el backend de usuario no
deja que un usuario se asigne el rol `admin` al registrarse.

## Cómo funciona la identificación

1. **Pl@ntNet** devuelve la especie y su ficha. La confianza decide el caso:
   - `< 20 %` → no identificada: se explica y se dan consejos de foto.
   - `20 – 49 %` → ambigua: se muestran las candidatas; al elegir una, se
     genera la ficha reutilizando la corrida de Pl@ntNet.
   - `≥ 50 %` → identificada: se genera la ficha directamente.
2. **Gemini** trabaja con las *skills* de `backend-django/skills/`
   (refinar consulta, una por categoría, glosario y materiales). Se hacen 4
   llamadas en lote con reintento ante 429 y ante JSON inválido. Si no conoce
   la especie, la ficha no se inventa: se avisa con el estado `insufficient`;
   si solo falta una categoría, se muestra un texto de respaldo con pautas
   generales.
3. El **chat** recibe el contexto de la planta (nombre, familia, similitud) y
   responde en español con formato Markdown.

## Documentación

- [`docs/BASE-DATOS.md`](docs/BASE-DATOS.md): tablas y relaciones de usuarios,
  fotos, identificaciones y chat.
- [`docs/CONFIG-IA.md`](docs/CONFIG-IA.md): pipeline de IA y skills.
- [`docs/HU-07.md`](docs/HU-07.md): trazabilidad de la historia de cuidado de
  la planta (tareas del sprint, estados y pruebas).
- [`docs/sql/agronomia.sql`](docs/sql/agronomia.sql): base de datos compartida.
- [`backend-django/README.md`](backend-django/README.md): endpoints del usuario.
- [`backend-spring/README.md`](backend-spring/README.md): endpoints del admin.
- [`web-user/README.md`](web-user/README.md) y
  [`web-admin/README.md`](web-admin/README.md): las dos webs.

## Seguridad

- Ninguna clave vive en el repositorio: Django usa `backend-django/.env`
  (ignorado por Git). `App movil/local.properties.example` y `.env.example` solo muestran
  los nombres de las variables.
- La app Android **no lleva claves**: solo conoce la URL de Django, que es
  quien habla con Pl@ntNet y Gemini.
- El servicio de administración usa un token HMAC firmado (8 h por defecto) y
  comprueba el rol en cada petición.
- El login de usuario emite un token firmado de 14 días. Django lo verifica en
  las rutas de fotos, identificación y chat y almacena `user_id` en MySQL.
- El autorregistro crea siempre cuentas con rol `user`; el rol `admin` solo se
  asigna desde el servidor.
- Comentarios del código, mensajes de interfaz y documentación en español.

## Estado

Funcionando de punta a punta sobre MySQL Server: app móvil, web de usuario,
backend Django, backend Spring y panel de administración. Las pruebas se
hicieron con fotos de girasol (identificada), una foto recortada (ambigua con
candidatas) y una imagen gris (no identificada).
