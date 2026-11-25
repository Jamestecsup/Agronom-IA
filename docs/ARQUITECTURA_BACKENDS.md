# Auditoría incremental de los backends

Auditoría estática de la rama `test`, commit de partida `d6d864d`. El Paso 1 de clientes externos ya estaba implementado en ese commit; este documento registra su revisión y la corrección del test que lo cubre. No se modifican rutas, JSON, autenticación, tablas ni datos.

## 1. Mapa actual y smells

### Django: MVT del panel admin

- `backend-django/agronomia/urls.py:6-16` monta Django Admin y ocho rutas `/api/admin/*` que consume el panel web. `core/urls.py` está vacío; Django ya no monta las rutas de usuario que aún viven en `core/views.py`.
- `backend-django/core/admin_api.py:51-265` contiene login, autorización, health, estadísticas, listados y actualización de usuarios. Son function-based views MVT válidas, pero varias hacen en el mismo módulo validación HTTP, autenticación, consultas ORM y serialización JSON. Ejemplos: estadísticas `:101-138`, usuarios y PATCH `:142-212`, plantas y chats `:215-265`.
- `backend-django/core/models.py:15-191` define los modelos de Agronom-IA y `Profile`; `PlantImage` está deliberadamente `managed = False` (`:15-39`) por ser una tabla heredada compartida con Spring. No cambiar su DDL sin revisar el contrato de ambos backends.
- `backend-django/core/services/pipeline.py:173-300` ya separa el caso de uso de identificación, y `core/services/plantnet.py:8-50` y `core/services/gemini.py:78-271` concentran integraciones externas. El pipeline importa módulos concretos (`pipeline.py:15-16`), por lo que reemplazar/probar implementaciones depende de monkeypatch/imports, no de una interfaz inyectada.
- `backend-django/core/views.py:128-872` conserva endpoints y lógica de usuario (horarios, identificación, chat, archivos) que no están conectados por las URLs actuales. Algunas acciones combinan request/validación, ORM, filesystem y llamadas a Spring (`:128-145`, `:495-620`, `:769-872`). Riesgo de mantenimiento: código aparentemente disponible que en realidad no está montado. No se recomienda borrarlo en esta fase.

### Spring Boot: MVC REST de usuario/móvil

- `backend-spring/src/main/java/com/agronomia/backend/user/UserController.java:19-238` agrupa los contratos de registro/login, imágenes, identificación, plantas, chat y horarios bajo `/agronomia-api/api`. El controller conserva parámetros y autenticación; `identify` (`:67-73`) y `confirm` (`:75-80`) delegan ahora a `PlantIdentificationApplicationService.java:13-139`, que coordina archivos, repositorios y pipeline. Siguen existiendo mapas JSON dinámicos en las respuestas; sustituirlos por DTO queda para un paso posterior con pruebas de JSON.
- `user/UserPlantService.java:13-118` antes combinaba servicio de aplicación y acceso SQL con `JdbcTemplate`; en el Paso 2 el SQL de plantas/corridas se extrajo a `user/PlantIdentificationRepository.java:12-86` y las consultas a `plant_images` quedaron en `images/PlantImageRepository.java:9-49`. `user/UserScheduleService.java:19-320` y `user/UserChatService.java:13-155` aún mezclan servicio de aplicación y acceso SQL. `ImageBatchService` coordina validación/almacenamiento/transacción.
- `user/IdentificationPipelineService.java:9-233` coordina umbrales, Pl@ntNet, enriquecimiento con IA y armado de la ficha. El commit base ya introdujo `PlantIdentificationClient` (`user/PlantIdentificationClient.java:6-8`) y `PlantAiClient` (`user/PlantAiClient.java:6-10`), implementados por `PlantNetService` (`user/PlantNetService.java:21-85`) y `UserAiService` (`user/UserAiService.java:18-145`), permitiendo probar el pipeline sin red.
- `schedule/InternalScheduleController.java:22-60` expone dos endpoints servicio-a-servicio protegidos por `X-Schedule-Service-Key`. `ScheduleAiService` es otro cliente externo específico; no se debe consolidar con otros clientes hasta comparar contratos y políticas de error.
- `admin/AdminAuthController.java:15-48` y `admin/AdminDataController.java:19-69` siguen mapeados en Spring bajo `/api/admin`; el destino del Vite local está comprobado como Django (`:8000`), pero no hay configuración de despliegue de producción versionada. Los payloads de éxito se parecen, pero la autenticación difiere; no retirar Spring hasta verificar el proxy/cliente desplegado.

## 2. Patrones

| Decisión | Patrón | Problema y aplicación | Encaje / motivo |
|---|---|---|---|
| Usar | **Adapter** (ya iniciado) | Aísla el formato HTTP de Pl@ntNet y del proveedor IA detrás de `PlantIdentificationClient` y `PlantAiClient`; implementaciones actuales: `PlantNetService` y `UserAiService`. | En MVC, `IdentificationPipelineService` depende de contratos y no del protocolo externo. No cambia rutas ni payloads. En Django, conservar los módulos de integración como borde del pipeline y añadir protocolos/objetos adaptadores cuando una necesidad de sustitución/test justifique el cambio. |
| Aplicado | **Facade** (servicio de aplicación) | `PlantIdentificationApplicationService` reúne la carga de imágenes, pipeline, persistencia y selección de candidata detrás de `identify` / `confirm`. | Encaja con Spring MVC: el controller mantiene la frontera HTTP y el servicio de aplicación coordina repositorios/pipeline. Django queda fuera de este cambio. |
| No aplicar ahora | **Strategy** | No hay dos algoritmos de identificación o IA seleccionables en ejecución; las interfaces actuales son puertos para adapters, no estrategias de negocio intercambiables. | Introducir selección de estrategia hoy agregaría configuración y ramas sin requisito existente. |
| No aplicar ahora | **Chain of Responsibility** | No hace falta una cadena propia para autenticación. Spring ya usa `AdminAuthInterceptor` como cross-cutting; Django valida en vistas compartidas por helpers. | Mantener los mecanismos de framework y unificar autorización en pasos futuros con pruebas; una cadena casera podría cambiar precedencia/códigos HTTP. |
| No aplicar ahora | **Builder / Factory / Abstract Factory / Singleton / Prototype** | Los DTO actuales son records/mapas directos, y Spring gestiona ciclo de vida de componentes. No aparece una familia de objetos intercambiables ni construcción repetida compleja que requiera estos patrones. | Evita boilerplate y singletons manuales que duplicarían mecanismos del framework. |
| No aplicar ahora | **Decorator / Proxy / Bridge / Composite / Flyweight / Command / Iterator / Mediator / Memento / Observer / State / Template Method / Visitor** | La auditoría no encontró un problema concreto que justifique introducirlos en el Paso 1. | Reconsiderar solo ante una necesidad observable y pruebas de contrato. |

Nota: **Repository** es un patrón de arquitectura/persistencia, no uno de los GoF listados. Sí conviene como paso futuro para aislar SQL de los servicios Spring; Django ya obtiene ese aislamiento principalmente mediante ORM/modelos.

## 3. Estructura objetivo gradual

```text
backend-django/
  agronomia/                 # configuración y URLconf
  core/
    models.py                # modelos ORM, tablas compartidas respetan DDL existente
    admin_api.py             # MVT: HTTP, auth y JSON; delgadas progresivamente
    serializers/             # forma JSON compatible (serialización validada)
    services/                # casos de uso: admin, identificación, chat
    integrations/            # adapters externos: IA, Pl@ntNet, llamada interna Spring
    tests/{unit,integration}/

backend-spring/src/main/java/com/agronomia/backend/
  user/web/                  # controllers REST delgados + DTO de request/response
  user/application/          # casos de uso de identificación, chat y horario
  user/domain/               # reglas de negocio cuando estén separadas del SQL/JSON
  user/persistence/          # repositorios JdbcTemplate con SQL y mapeos
  integrations/              # adapters Pl@ntNet/IA/almacenamiento
  images/                    # mantener flujo de upload existente y su repositorio
  admin/                     # aclarar dueño de /api/admin antes de migrar/eliminar
  schedule/                  # API interna y servicio de análisis
```

El árbol es un destino conceptual, no una reestructuración que deba aterrizarse de una vez. Django sigue siendo MVT; Spring sigue siendo MVC.

## 4. Plan incremental

| Paso | Alcance | Riesgo y rollback | Contrato afectado | Pruebas requeridas |
|---|---|---|---|---|
| **1. Aislar proveedores de identificación/IA (hecho en la rama)** | Interfaces `PlantIdentificationClient` / `PlantAiClient`, implementación por servicios actuales e inyección en el pipeline; cubrir el pipeline con dobles. | Bajo. Revertir interfaces/inyección al acoplamiento concreto previo. | Ninguno esperado: mismos endpoints, JSON, umbrales y errores. | Unit test de pipeline con dobles; `mvnw verify`; suites Django y Ruff para comprobar que Django no cambió. |
| **2. Extraer persistencia de plantas Spring (hecho en esta continuación)** | `PlantIdentificationRepository` contiene SQL/mapeo para identificaciones y corridas; `PlantImageRepository` da acceso a paths; `UserPlantService` conserva composición/JSON y el controller ya no conoce `JdbcTemplate`. | Bajo/medio: riesgo en SQL y mapeo; rollback revirtiendo estos cambios Java y el esquema H2 de prueba. | Sin cambios esperados en `/identify`, `/confirm`, `/plants`, portada. | 3 tests H2 de repositorio/servicio + 5 tests MockMvc para listado, portada, identificación y confirmación; `mvnw verify`. |
| **3. Separar caso de uso de identificación MVC (hecho en esta continuación)** | `PlantIdentificationApplicationService` coordina `/identify` y `/confirm`; controller conserva auth, rutas y parámetros. | Medio: errores y estados; rollback revirtiendo el servicio/delegación y tests. | Sin cambios esperados para Android/web-user: rutas, campos, status y auth. | MockMvc para identified/ambiguous/insufficient/failed, candidata confirmada y pertenencia de lote/corrida. |
| **4. Extraer operaciones de Django admin MVT (hecho en esta continuación)** | `core/services/admin_api.py` coordina consultas ORM, serialización de filas y actualización de roles/estado; `core/admin_api.py` conserva métodos, JSON HTTP, token y autorización. Solo rutas montadas bajo `/api/admin/*`. | Medio: consultas y autorización de web-admin; rollback revirtiendo servicio, delegaciones y pruebas. | Sin cambios esperados en `/api/admin/*`: camelCase, arrays, status, mensajes y permisos. | Contratos de login, roles, stats, users, PATCH, plants y chats; Ruff y `core.tests` en SQLite temporal. |
| **5. Extraer persistencia de chat Spring (hecho en esta continuación)** | `UserChatRepository` concentra SQL/mapeos de mensajes e imágenes de progreso; `UserChatService` conserva autorización de pertenencia, reglas conversacionales, llamada IA y filesystem. | Bajo/medio: orden, IDs y aislamiento por usuario; rollback revirtiendo repositorio, delegación y pruebas. | Sin cambios esperados en `/chat/history`, `/chat`, `/chat/images` ni imagen binaria. | Repositorio H2; MockMvc para historial, listado/descarga de imágenes y POST multipart del web-user/Android; suites Spring relevantes. |
| **6. Extraer persistencia de horarios Spring (hecho en esta continuación)** | `UserScheduleRepository` concentra SQL y mapeos JDBC de horario, importación y tareas del care-plan; `UserScheduleService` conserva validación, normalización, conflictos, transacciones, coordinación del parser PDF y JSON de respuesta. | Bajo/medio: estado/importación y mapeo; rollback revirtiendo el repositorio y delegaciones, sin tocar el esquema. | Sin cambios esperados en rutas, parámetros, JSON, status ni autenticación. | 2 pruebas H2 de persistencia + 4 pruebas MockMvc para lectura/importación/confirmación/cancelación y tareas; suite Spring dirigida y `mvnw verify`. |

## 5. Paso 1 y validación

El commit base ya había extraído los clientes. En esta continuación corregí únicamente el test `backend-spring/src/test/java/com/agronomia/backend/user/IdentificationPipelineServiceTest.java`: `String.valueOf(inv.getArgument(0))` podía elegir la sobrecarga `String.valueOf(char[])`, lanzar `ClassCastException` y producir un falso estado `insufficient`. El test obtiene ahora el argumento con `inv.getArgument(0, String.class)`. No cambia producción ni contratos.

Comandos/resultados:

- `py -m ruff check .` en `backend-django`: **pasa**.
- `py manage.py test core.tests -v 2` con MySQL por defecto: no pudo conectarse a `127.0.0.1:3306` (MariaDB no estaba activa). No altera la base existente.
- `$env:USE_SQLITE='1'; py manage.py test core.tests -v 2`: **18 tests pasan**, incluye integración HTTP/ORM en SQLite efímera; Django system check sin problemas.
- `.\mvnw.cmd verify` en `backend-spring`: primera ejecución detectó el falso fallo anterior; tras corregirlo, **BUILD SUCCESS**, 18 pruebas Surefire + 5 Failsafe. Los dos `*IntegrationTest` se ejecutan tanto en Surefire como Failsafe por el patrón `*Test`; conviene excluirlos de Surefire en un cambio independiente.

### Paso 2

Se añadió `backend-spring/src/main/java/com/agronomia/backend/user/PlantIdentificationRepository.java`; `PlantImageRepository` ahora consulta paths del lote y portada. `UserPlantService` ya no contiene SQL y conserva el parseo/shape de los mapas de salida; `UserController` ya no inyecta `JdbcTemplate` y usa el repositorio de imágenes para leer el batch. El esquema agregado está únicamente en `src/test/resources/schema.sql` para H2; no se cambió el esquema de producción.

- Antes: `.\mvnw.cmd verify` pasó con 18 Surefire y 5 Failsafe.
- Dirigidas: `.\mvnw.cmd "-Dtest=PlantIdentificationRepositoryTest,UserPlantsApiIntegrationTest" test` pasó con **8 pruebas**.
- Después: `.\mvnw.cmd verify` terminó en **BUILD SUCCESS**, 24 Surefire + 5 Failsafe, sin fallos.
- `git diff --check`: sin errores de whitespace.

### Paso 3

Se añadió `PlantIdentificationApplicationService` para coordinar ambos casos de uso. El controller ahora autentica y delega; el servicio usa `PlantImageRepository`, `IdentificationPipelineService` y `UserPlantService`, sin añadir SQL ni cambiar el esquema. Las pruebas MockMvc en `UserPlantsApiIntegrationTest` cubren los cuatro estados de identificación, confirmación, y los rechazos cuando el lote o la corrida no pertenecen al usuario autenticado.

- Antes: `.\mvnw.cmd verify` pasó (24 Surefire + 5 Failsafe).
- Dirigidas: `.\mvnw.cmd "-Dtest=UserPlantsApiIntegrationTest,IdentificationPipelineServiceTest,PlantIdentificationRepositoryTest" test` pasó (14 tests).
- Después: `.\mvnw.cmd verify` finalizó **BUILD SUCCESS** (29 Surefire + 13 Failsafe; integración se repite porque Surefire incluye `*Test` y Failsafe `*IntegrationTest`).
- `git diff --check`: sin errores de whitespace.

Riesgos/pendientes: las pruebas Spring usan H2 y las de Django SQLite, no sustituyen una comprobación de compatibilidad de dialecto con MariaDB ni una prueba contra frontends/Android. Spring informa `@MockBean` deprecated y tipos raw/unchecked en el cliente Pl@ntNet. No se hizo una migración de esquema ni una llamada de red a IA/Pl@ntNet. El primer intento MySQL falló por servicio apagado, por eso Django se validó con SQLite.

### Paso 4

Se extrajeron las consultas y operaciones de las vistas activas del panel a `core/services/admin_api.py`. Las vistas mantienen métodos HTTP, lectura/validación de request, autenticación/autorización y construcción de `JsonResponse`; `update_user` traduce errores de operación a los mismos status/mensajes. Las vistas no montadas de `core/views.py`, modelos y migraciones no se tocaron.

- Antes: `py -m ruff check .` pasó; `$env:USE_SQLITE='1'; py manage.py test core.tests -v 2` pasó con **18 tests**.
- Después: contratos de login/roles, `/auth/me`, filtros de users, PATCH (incluido self-protection), plants y chats, además de stats existente. `py -m ruff check .` pasó; `$env:USE_SQLITE='1'; py manage.py test core.tests -v 2` pasó con **25 tests** y el system check de Django sin incidencias.

Riesgos/pendientes: se validó con SQLite temporal, no contra MariaDB ni desde el navegador real del web-admin. No se tocaron las vistas no montadas de `core/views.py`, migraciones ni esquemas.

### Paso 5

Se añadió `backend-spring/src/main/java/com/agronomia/backend/user/UserChatRepository.java`; `UserChatService` ya no contiene JDBC, SQL ni mapeos de `ResultSet`. El servicio conserva validaciones, pertenencia de planta, construcción del prompt, llamada IA y escritura de archivos. El repositorio recibe el ID del mensaje insertado para que la imagen de progreso siga enlazada al mensaje del usuario. `schema.sql` agrega únicamente tablas H2 de prueba; no se cambió esquema de producción.

- Antes: `.\mvnw.cmd "-Dtest=UserPlantsApiIntegrationTest,ImageUploadApiIntegrationTest" test` pasó con **13 tests**.
- Después: `.\mvnw.cmd "-Dtest=UserChatRepositoryTest,UserChatApiIntegrationTest,UserPlantsApiIntegrationTest,ImageUploadApiIntegrationTest" test` pasó con **24 tests**: 5 de repositorio H2, 6 de contrato chat y 13 existentes de plantas/imágenes.
- `UserChatApiIntegrationTest` cubre historial, auth, listado y bytes de imagen, POST multipart del web-user y multipart con foto de Android.
- `.\mvnw.cmd verify` terminó en **BUILD SUCCESS** (40 Surefire + 19 Failsafe); tras ajustar la prueba al multipart real del web-user, el comando dirigido volvió a pasar con **24 tests**.

### Auditoría de propiedad y enrutamiento de `/api/admin/*`

Se compararon las rutas Django, los controllers Spring, el cliente React, Vite, el script local de arranque y los README. No se eliminaron ni migraron endpoints.

| Ruta/uso | Django (`:8000`) | Spring (`:8081`) | Cliente web-admin comprobado |
|---|---|---|---|
| `POST /api/admin/auth/login` | `agronomia/urls.py:9` → `admin_api.login` | `AdminAuthController.java:26-34` | Sí: `src/api.js:71-75` |
| `GET /api/admin/auth/me`, `/health` | `urls.py:10-11` | `AdminAuthController.java:37-47` | No encontré llamadas en `web-admin/src` |
| `GET /api/admin/stats` | `urls.py:12` | `AdminDataController.java:28-31` | Sí: `pages/Dashboard.jsx:15` |
| `GET /api/admin/users`, `PATCH /api/admin/users/{id}` | `urls.py:13-14` | `AdminDataController.java:33-54` | Sí: `pages/Users.jsx:25-27,64-67` |
| `GET /api/admin/plants`, `/chats` | `urls.py:15-16` | `AdminDataController.java:56-68` | Sí: `pages/Plants.jsx:17`, `pages/Chats.jsx:18` |

**Destino local demostrable:** `web-admin/vite.config.js:10-14` proxy `/api` a `http://127.0.0.1:8000`; el cliente usa URLs relativas (`src/api.js:54`) y el script `scripts/iniciar-demo.ps1:136-152` inicia Django en `8000` y el panel en `5173`. El README raíz también asigna Django al panel (`README.md:24-25,36-37,110-113`). Así que el web-admin de desarrollo llega a Django; Spring no es el destino de ese proxy.

**Ambigüedad de producción:** no se encontró configuración versionada de reverse proxy, hosting o despliegue para `web-admin`. En cambio, `web-admin/README.md:9-10,23-25` todavía instruye usar Spring y afirma que el proxy/producción apunta allí, lo que contradice tanto `vite.config.js` como el README raíz y el script local. Ese README no demuestra el enrutamiento de un servidor desplegado. La ruta efectiva de producción queda pendiente de confirmar con la configuración del entorno publicado.

**Compatibilidad:** ambos controllers exponen las mismas rutas/verbos y los nombres camelCase de los campos que actualmente consume el panel. No se hizo prueba HTTP contra el servidor publicado. Sus respuestas no son completamente intercambiables en todos los casos: Django serializa `userId: null` en plantas/chats, mientras que Spring configura Jackson con `NON_NULL` y puede omitir esa propiedad cuando el usuario es nulo (`application.yml`); el panel actual no lee `userId`. Tampoco son intercambiables sus tokens: Django emite `django.core.signing` con máximo de 14 días (`core/authentication.py:5-11`); Spring usa HMAC propio y TTL de 8 horas por defecto (`AdminTokenService.java:14-18,30-50`; `application.yml:41-43`). Django vuelve a consultar usuario activo/rol en cada request (`core/authentication.py:29-30`, `core/admin_api.py:28-38`); el interceptor Spring valida firma y caducidad del token sin consultar el estado/rol actual en BD (`AdminAuthInterceptor.java:38-50`). Por tanto, una desactivación o degradación de rol puede revocarse inmediatamente en Django, mientras que el token Spring puede permanecer válido hasta expirar. Los status de errores también pueden diferir.

**Decisión pendiente:** mantener ambos mappings por ahora. Antes de retirar Spring `/api/admin/*`, confirmar con el dueño/configuración real de producción que ningún deployment, cliente externo o URL pública sigue apuntando a `:8081/api/admin/*`; luego ejecutar una prueba de contrato contra la ruta publicada. Esta auditoría no cambia producción ni requiere rollback de código.

**Validación de esta auditoría:** `py -m ruff check .` pasó; `$env:USE_SQLITE='1'; py manage.py test core.tests -v 1` pasó con **25 tests**; `.\mvnw.cmd "-Dtest=AdminAuthInterceptorTest" test` pasó con **3 tests**; `npm run build` en `web-admin` pasó. No hay prueba MVC de integración para los controllers admin de Spring. `git diff --check` no reportó errores. No se hizo smoke contra MariaDB ni contra producción porque no hay un proxy/despliegue verificable en el repo.

### Paso 6: extracción de persistencia de horarios Spring

`backend-spring/src/main/java/com/agronomia/backend/user/UserScheduleRepository.java` concentra las consultas y el mapeo JDBC de `weekly_schedules`, `pending_schedule_imports`, `care_plan_tasks` y las lecturas de `plant_identifications` usadas por el plan de cuidados. `UserScheduleService` mantiene las reglas de normalización, detección de cruces, secuencia transaccional, parser PDF/IA y respuestas. `UserController` y sus mappings no cambiaron. Las tablas de prueba se crean dentro de la base H2 efímera en `UserScheduleRepositoryTest`; no se modificaron DDL de producción, migraciones ni datos.

**Antes del cambio:** `.\mvnw.cmd "-Dtest=UserPlantsApiIntegrationTest,ImageUploadApiIntegrationTest" test` en `backend-spring`: **13 tests**, todos pasaron.

**Después del cambio:** `.\mvnw.cmd "-Dtest=UserScheduleRepositoryTest,UserScheduleApiIntegrationTest,UserPlantsApiIntegrationTest,ImageUploadApiIntegrationTest" test`: **19 tests**, todos pasaron. `.\mvnw.cmd verify`: **BUILD SUCCESS**, 46 pruebas Surefire y 23 Failsafe, todas pasaron.

**Riesgo/rollback:** riesgo bajo/medio en orden de escrituras, forma de mapear JSON/horas e IDs de UUID. Rollback: revertir `UserScheduleRepository.java`, las delegaciones de `UserScheduleService.java`, las dos nuevas pruebas y esta entrada documental. El cambio no modifica tablas ni endpoints.

## Auditoría de vistas de usuario Django no montadas

Revisé `backend-django/core/views.py`, los URLconf Django, referencias Python del repositorio, tests, scripts de arranque, clientes web y Android, y los mappings activos de Spring. No leí archivos `.env` ni valores de configuración local. Esta auditoría no conecta, elimina ni cambia contratos.

### Mapa de uso

| Grupo y vistas de `core/views.py` | Evidencia y clasificación | Riesgo/certeza |
|---|---|---|
| Horarios: `schedule_import` (`:148`), `schedule_import_action` (`:191`), `weekly_schedule` (`:225`), `care_plan_draft` (`:242`), `care_plan_confirm` (`:306`), `care_plan_task_action` (`:356`) | **Obsoletas demostradas como endpoints Django en este repo.** Ninguna está montada en `agronomia/urls.py:6-16`; `core/urls.py:1-7` declara expresamente desactivados los endpoints de usuario y `urlpatterns=[]`. Los equivalentes están montados en Spring `UserController.java:137-180`; Android los declara en `DjangoApiService.kt:81-111`, y web-user usa el API relativo bajo el proxy a Spring (`web-user/src/api.js:7`, `vite.config.js:4-14`). | El uso externo de una URL Django antigua no puede descartarse para despliegues/clientes fuera del repo. El script local levanta Spring como API usuario (`scripts/iniciar-demo.ps1:121-133`). |
| Salud, identificación y plantas: `health` (`:399`), `upload_images` (`:455`), `identify` (`:495`), `confirm` (`:571`), `identified_plants` (`:625`), `plant_cover` (`:676`) | **Obsoletas demostradas como endpoints Django en este repo.** No tienen mapping Django activo ni import interno. Spring expone `/health`, `/images.php`, `/identify`, `/confirm`, `/plants` y `/plants/{plantId}/cover` en `UserController.java:52-96`. El cliente Android conserva estos paths (`Constants.kt:19-22`, `DjangoApiService.kt:55`) y su base se configura externamente (`NetworkModule.kt:67-80`); el README raíz indica `BACKEND_BASE_URL=http://10.0.2.2:8081/` (`README.md:134`). | No es posible generalizar el valor de la URL base en instalaciones Android ya distribuidas; esas instalaciones podrían apuntar a un host antiguo. El cliente web local sí va a Spring (`web-user/vite.config.js:4-14`). |
| Chat: `chat_progress_images` (`:692`), `chat_progress_image` (`:718`), `chat_history` (`:731`), `chat` (`:769`) | **Obsoletas demostradas como endpoints Django en este repo.** No están montadas ni referenciadas por tests/imports. Spring contiene los mappings equivalentes en `UserController.java:99-134`; web-user usa `/chat` y `/chat/history` (`web-user/src/api.js:113-122`); Android declara historial/listado y POST multipart en `DjangoApiService.kt:59-78`. La URL de descarga está construida en `UserController.java:111` y aparece en `UserChatService.java`. | La base configurada para Android y el enrutamiento del deployment publicado no están versionados como evidencia autoritativa. |
| Helpers privados de horarios/archivo/plantas/chat: `_schedule_user` (`:50`), `_schedule_classes` (`:55`), `_schedule_body` (`:90`), `_care_task_json` (`:100`), `_has_class_conflict` (`:113`), `_spring_schedule_request` (`:128`), `_error` (`:395`), `_validate_image` (`:426`), `_plant_chat_session` (`:651`), `_find_owned_plant` (`:656`), `_uploaded_file_response` (`:664`) | **Referenciados internamente**, no son rutas. Sus referencias observadas proceden de las vistas anteriores; no encontré importadores externos desde URLconf, tests, comandos o frontend. | Son código alcanzable solo si otra función Python lo invoca; conservarlos o retirarlos depende de decidir el destino del módulo completo. |

**Estado de rutas Django:** `agronomia/urls.py:6-16` monta `/admin/` y ocho rutas `/api/admin/*`; no importa `core.views`. `core/urls.py:1-7` está vacío. La búsqueda del repo no encontró `from core.views`, `import core.views`, `core.views.*` ni tests que importen el módulo. Los únicos tests Django encontrados son los de pipeline y admin (`core/tests/unit/test_pipeline.py`, `core/tests/integration/test_admin_api.py`); no hay pruebas de estas vistas de usuario. La configuración WSGI apunta a `agronomia.wsgi.application` (`agronomia/settings.py:83`), que resuelve el URLconf principal; no apareció otro URLconf/deployment versionado que monte `core.views`.

**Discrepancias documentales:** el encabezado/comentario de `core/views.py:1-10` aún presenta sus endpoints como disponibles. `backend-django/README.md:1-18` también describe Django como backend de usuario y menciona `:8000` para Android, pese a `core/urls.py` y al script local, que reservan Django para admin y Spring para usuario. En Android, `DjangoApiService.kt` y `NetworkModule.kt:12-16,61-80` conservan nombres/comentarios que dicen “Django”, mientras que `README.md:134` configura el emulador en `:8081` y los paths actuales están implementados en Spring. Son nombres/comentarios heredados, no evidencia de que las vistas Django estén montadas.

### Recomendación de limpieza (separada del mapa)

- **Grupo de horarios:** retirar las funciones Django duplicadas solo después de confirmar que no hay clientes Android instalados con `BACKEND_BASE_URL` apuntando a Django ni despliegues que monten un URLconf alternativo. Mantener por ahora las funciones Spring y sus contratos.
- **Salud/identificación/plantas y chat:** misma recomendación: la evidencia versionada muestra Spring como implementación activa, pero conviene comprobar configuración de despliegue y hosts de clientes existentes antes de borrar el código legado. Actualizar en un trabajo separado los comentarios/README que afirman que Django sirve la API de usuario.
- **Helpers privados:** si se confirma la retirada de todas las vistas, retirar juntos los helpers que solo ellas usan y ejecutar Ruff, tests Django y búsqueda de referencias; no extraerlos ni cambiar de módulo durante esta auditoría.
- **Incierto fuera del repo:** no afirmar que el uso histórico o externo es cero. La configuración Android permite un `BACKEND_BASE_URL` por instalación y no se inspeccionó `.env`, `local.properties` ni hosting real. No se recomienda conectar estas vistas otra vez ni borrarlas de inmediato.

**Validación de la auditoría:** `py -m ruff check .` pasó; con `USE_SQLITE=1`, `py manage.py test core.tests -v 1` pasó con **25 tests** y system check limpio. No hay tests actuales para `core.views`; no se añadieron porque las rutas no están montadas y el alcance solicitado es solo auditoría. No se hizo prueba HTTP de despliegue ni contra un dispositivo Android configurado.

## Migración gradual de mapas JSON: GET `/schedule`

### Inventario y alcance

En Spring aún hay respuestas/endpoints con `Map<String,Object>` en `UserController`: `health` (`:53`), `identify`/`confirm` (`:67-80`), `plants` (`:83-85`), historial/listado/chat (`:99-134`) y varios endpoints de horario/importación/care-plan (`:137-180`). Los servicios de esos flujos también construyen mapas. Además existen mapas internos para JSON de proveedores/IA y persistencia, que no son contratos HTTP directos y quedan fuera de esta migración.

Se eligió **solo GET `/agronomia-api/api/schedule`** (`UserController.java:137-139`). Android modela esa respuesta como `WeeklyScheduleResponse` (`App movil/app/src/main/java/com/agronomia/data/remote/DjangoDto.kt:187-193`), con `classes`, `care_tasks` y el campo opcional `pending_import`; sus clases/tareas declaran `@SerialName` para los nombres snake_case (`:159-184`). Android llama el endpoint en `DjangoApiService.kt:84-85`. No encontré consumo de horario en `web-user/src` ni `web-admin/src`; web-admin se limita a `/api/admin/*` y queda fuera de este flujo.

### Cambio

`UserScheduleDtos.java` define records para `WeeklyResponse`, `WeeklyClass`, `CareTask` y `PendingImport`, con nombres JSON explícitos y omisión de nulos. `UserScheduleService.weeklyResponse` adapta la lectura existente a esos DTOs; `UserController.weeklySchedule` retorna el DTO tipado. `weekly()` conserva su mapa anterior porque `confirmCarePlan` lo reutiliza: ese endpoint y los demás contratos de horario no se migraron. No cambian ruta, parámetros, autenticación, lógica de negocio, esquema ni datos.

### Evidencia de equivalencia JSON

Antes de cambiar producción, añadí y ejecuté una aserción exacta contra la respuesta del mapa anterior. La forma observada fue:

```json
{"success":true,"classes":[{"course_code":"BIO-1","course_name":"Botánica","day_of_week":1,"start_time":"08:00","end_time":"09:00","section":"A"}],"care_tasks":[{"id":"task-1","plant_id":"plant-1","title":"Regar","instructions":"cada semana","day_of_week":2,"start_time":"10:30","duration_minutes":15}]}
```

`plant_name:null` y `pending_import:null` se omiten por la configuración Jackson `non_null` existente (`application.yml:22`). Después de tipar, el test compara el JSON serializado con la misma expectativa; otra aserción cubre `pending_import` presente y sus nombres de campo. El 401 conserva status y `{"success":false,"error":"Inicia sesión para continuar."}`. Las pruebas usan los nombres/tipos que declara el modelo Android. No hay consumidor de web-user/web-admin para `/schedule`.

**Validación:** antes: `.\mvnw.cmd "-Dtest=UserScheduleApiIntegrationTest,UserScheduleRepositoryTest" test` pasó con **6 tests** existentes. Antes del cambio de producción, la nueva expectativa JSON corrió contra la respuesta Map y pasó. Después: `.\mvnw.cmd "-Dtest=UserScheduleApiIntegrationTest,UserScheduleRepositoryTest,UserScheduleResponseContractTest" test` pasó con **9 tests** (5 MockMvc existentes, 2 H2 y 2 MockMvc del servicio real con repositorio simulado). `.\mvnw.cmd verify` finalizó en **BUILD SUCCESS** (50 Surefire + 24 Failsafe).

**Riesgo/rollback:** riesgo bajo, limitado al mapeo de valores Map a campos record, incluyendo nulos/campos omitidos. Revertir `UserScheduleDtos.java`, la conversión `weeklyResponse`, el tipo de retorno del método del controller y las aserciones nuevas restaura el contrato anterior; no se revierte el extractor de persistencia previo. No se probó dispositivo Android real ni tráfico de producción.
