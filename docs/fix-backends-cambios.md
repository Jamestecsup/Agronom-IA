# Fix de backends - Antes y Después

**Fecha**: 2026-11-23  
**Rama**: `James`  
**Commit**: `877d3b3 fix de backends` (amended, 1 solo commit, sin push)  
**Alcance**: inversión de responsabilidades entre Django y Spring Boot, manteniendo BD MySQL compartida.

## Resumen ejecutivo

| Área | Antes | Después |
|---|---|---|
| Spring Boot (`:8081`) | Admin web (`/api/admin/*`) | **Usuario/web/móvil** (`/agronomia-api/api/*`: auth, imágenes, identify/confirm, plantas, chat, horario/plan de cuidados) |
| Django (`:8000`) | Usuario/web/móvil (`/agronomia-api/api/*`) | **Admin web** (`/api/admin/*`) + Django Admin |
| Web usuario (Vite `:5174`) | Proxy a Django `:8000` | Proxy a Spring `:8081` |
| Web admin (Vite `:5173`) | Proxy a Spring `:8081` | Proxy a Django `:8000` |
| App móvil Android | `BACKEND_BASE_URL` apuntaba a Django | `BACKEND_BASE_URL` apunta a Spring `:8081` |
| BD | MySQL compartida | Misma BD MySQL compartida; sin limpieza de tablas |

## Antes (estado en `origin/James`)

- **Django** exponía endpoints de usuario en `core/urls.py` incluidos bajo `/agronomia-api/api/` desde `agronomia/urls.py`.
- **Spring Boot** exponía panel admin en `/api/admin/*` y tenía upload de imágenes legacy en `ImageUploadController` (`POST /agronomia-api/api/images.php`) sin auth de usuario.
- **Web-user** proxy Vite a `http://127.0.0.1:8000` con rutas `/agronomia-api`.
- **Web-admin** proxy Vite a `http://127.0.0.1:8081` con rutas `/api`.
- **Scripts/docs** describían la arquitectura anterior.

## Después (cambios aplicados)

### Django (`backend-django`)
- `agronomia/urls.py`: removido include de rutas usuario; quedan `admin/` y `api/admin/`.
- `core/urls.py`: rutas usuario desactivadas (vacío/comentado según implementación).
- `core/admin_api.py`: nueva API admin Django (`/api/admin/auth/login`, `/auth/me`, `/health`, `/stats`, `/users`, `/plants`, `/chats`, etc.).
- `README.md`: actualizado a rol admin.

### Spring Boot (`backend-spring`)
- Nuevo paquete `com.agronomia.backend.user`:
  - `UserController`: endpoints usuario (`register`, `login`, `images.php`, `identify`, `confirm`, `plants`, `plants/{id}/cover`, `chat/history`, `chat`, `chat/images`, `chat/images/{id}`, `schedule/*`, `care-plan/*`).
  - `UserAuthService`: register/login; compatibilidad con hashes Django `pbkdf2_sha256`.
  - `UserTokenService`: token HMAC propio, TTL configurable (`user.token.ttl-hours`).
  - `UserPlantService`: persistencia/listado plantas, portada, runs de identificación, raw_json.
  - `IdentificationPipelineService` + `PlantNetService` + `UserAiService`: identificación Pl@ntNet + IA; skills desde `skills.dir` (por defecto `backend-django/skills`).
  - `UserChatService`: historial chat, respuesta, imágenes progreso.
  - `UserScheduleService`: import PDF, draft/confirm care plan, reschedule/delete task, conflictos.
  - `UserApiException` / `UserApiExceptionHandler`: errores con status coherente.
- Upload imágenes:
  - **Eliminado** `ImageUploadController` (mapeo ambiguo con `/agronomia-api/api/images.php`).
  - `ImageBatchService`, `PlantImageRecord`, `PlantImageRepository`, `ImageFileStorage` soportan `user_id`.
  - `ApiExceptionHandler` mapea `UserApiException`.
- Tests/Schema:
  - `ImageUploadApiIntegrationTest`: actualizado a auth Bearer y nuevo contrato.
  - `src/test/resources/schema.sql`: incluye `user_id` en `plant_images`.
- `README.md`: actualizado a rol usuario.

### Clientes / scripts / docs
- `web-user/vite.config.js`: proxy `/agronomia-api` -> `http://127.0.0.1:8081`.
- `web-admin/vite.config.js`: proxy `/api` -> `http://127.0.0.1:8000`.
- `App movil/app/build.gradle.kts`: `BACKEND_BASE_URL` default a `http://10.0.2.2:8081/`.
- `scripts/iniciar-demo.ps1`, `scripts/check-servicios.ps1`: ajustados a arquitectura invertida.
- READMEs raíz/Django/Spring/web-user/web-admin actualizados.

## Riesgos detectados y estado

| Riesgo / hueco | Estado | Evidencia / nota |
|---|---|---|
| Mapeo ambiguo `POST /agronomia-api/api/images.php` | **Corregido** | Eliminado `ImageUploadController`; `ImageUploadApiIntegrationTest` 8/8 OK. |
| `confirm` sin raw Pl@ntNet completo | **Mitigado** | `UserPlantService.loadLatestRunRaw()`; `confirm` usa raw guardado cuando existe. Falta validar con datos reales. |
| IDs UUID con guiones vs columnas `char(32)` Django | **Corregido** | `UserPlantService/UserChatService/UserScheduleService` usan hex32/normalización para IDs de tablas Django. |
| `UserApiException` devolvía 500 genérico | **Corregido** | `ApiExceptionHandler` mapea `UserApiException`; smoke: sin auth 401, planta inexistente 404. |
| Compatibilidad tokens Django `signing` antiguos vs HMAC Spring | **Mitigado (smoke local)** | Spring `UserTokenService` ahora verifica tambi?n tokens legacy Django signing (`agronomia.user-api`, `DJANGO_SECRET_KEY`) y resuelve `user_id` activo en BD. Smoke local con token legacy real: `GET /plants` 200. Falta validar con sesiones/clientes reales antiguos. |
| `images.php` ahora requiere auth | **Probado integración** | 401 sin auth, 201 con Bearer en smoke. Validar todos los clientes. |
| Identify/confirm con PlantNet+IA real | **Pendiente entorno** | `identify` llegó a lógica; falló por `PLANTNET_API_KEY` no configurada en smoke. |
| Chat con IA/imagen progreso real | **Pendiente E2E** | Requiere AI/config/planta real. |
| Schedule import/draft/confirm con datos reales | **Pendiente E2E** | `GET /schedule` OK; draft 409 esperado sin horario/plantas. Falta PDF/confirm/reschedule/delete real. |
| Admin Django `/api/admin/*` | **Probado smoke básico** | login/me/stats/users OK en BD nueva local. |
| Android cobertura Retrofit | **Parcial** | `assembleDebug` BUILD SUCCESSFUL. Revisar flujos reales para register/upload/identify/confirm/chat POST fuera de `DjangoApiService.kt`. |

## Pruebas ejecutadas

### Automatizadas / build
- `backend-django`: `py manage.py check` OK; `py manage.py test core.tests -v 1` **15 tests OK**.
- `backend-spring`: `.\mvnw.cmd -q -DskipTests package` OK; `.\mvnw.cmd -q -Dtest=ImageUploadApiIntegrationTest test` **8/8 OK**; compile OK.
- `web-user`: `npm run build` OK.
- `web-admin`: `npm run build` OK.
- `App movil`: `.\gradlew.bat :app:assembleDebug --console=plain` **BUILD SUCCESSFUL**.

### Smoke real (con confirmación explícita opción 2: BD nueva local)
- BD: se creó `agronomia` en XAMPP local, se importó `docs/sql/agronomia.sql` y se ejecutó `py manage.py migrate --noinput`. **No se limpiaron tablas existentes** (la BD no existía en el datadir activo).
- Spring `:8081` / Django `:8000` levantados localmente.
- Spring usuario: register OK, login OK, GET plants OK, images.php sin auth 401, images.php con Bearer 201, identify llegó a lógica pero 500 por `PLANTNET_API_KEY` no configurada, chat/history inexistente 404, schedule GET OK, care-plan draft 409 esperado.
- Django admin: login OK, `/api/admin/auth/me` OK, `/api/admin/stats` OK, `/api/admin/users` OK.

## Archivos principales tocados

- Django: `backend-django/agronomia/urls.py`, `backend-django/core/urls.py`, `backend-django/core/admin_api.py`, READMEs.
- Spring: `backend-spring/src/main/java/com/agronomia/backend/user/*`, `backend-spring/src/main/java/com/agronomia/backend/images/*`, tests/schema, README.
- Clientes/scripts: `web-user/vite.config.js`, `web-admin/vite.config.js`, `App movil/app/build.gradle.kts`, `scripts/iniciar-demo.ps1`, `scripts/check-servicios.ps1`, `README.md`.

## Notas operativas

- No restaurar Qwen temporal en `backend-django/.env` hasta confirmación; no exponer/commitear claves.
- No limpiar tablas sin confirmación explícita.
- Mantener un solo commit `fix de backends`; para cambios posteriores: `git add -u && git commit --amend --no-edit`.
- `.emulator-start.log` quedó sin trackear y no debe incluirse en el commit.