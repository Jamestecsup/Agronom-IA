# Migración de backends (Opción A)

Actualizado 2026-11-24 · Rama local `feature/swap-backends`.

Destino acordado: Spring Boot sirve Android y la web de usuario. Django sirve la web de administración. Ambos usan MySQL Server 8.4 y comparten las cuentas del proyecto. El destino elimina el puente HTTP entre backends.

| Fase | Estado | Evidencia o siguiente hito |
|---|---|---|
| 0. Preparación | Completa | La rama parte de `config/mysql-server` (`1619e9b)) e integra `fix/api-csrf` (`8208c7c)) en `da16adf`. La matriz de rutas está en la nota de Obsidian «Migración de backends (Opción A)». Pasaron las dos pruebas de regresión CSRF con SQLite en memoria. |
| 1. Propiedad de datos | Completa | Django inicializa con 0001–0009 y 0010 modifica solo el estado. Spring valida el esquema y registra línea base 1. Verificado en MySQL 8.4.13 nuevo y existente. |
| 2. API de usuario en Spring | En pausa por paridad de carga | Spring tiene una ruta heredada y pruebas que aceptan WebP sin autenticación. Queda por confirmar el retiro de ese contrato. |
| 3. API de administración en Django | Pendiente | Portar el contrato `/api/admin/`, conservar CSRF en `/admin/` y retirar rutas y secretos del puente. |
| 4. Clientes y configuración | Pendiente | Cambiar los destinos, preparar el arranque y trasladar las variables privadas a su archivo ignorado. |
| 5. Calidad de las fichas | Pendiente | Actualizar las skills y evaluar los textos con datos simulados. |
| 6. Identificación asíncrona | Opcional, pendiente | Considerarla después de verificar las fases 0 a 5. |
| Verificación final | Pendiente | Pruebas automáticas, humo con dobles, compilación Android si hay SDK y un único escaneo real con máximo seis llamadas a IA. |

## Fase 1 · Propiedad del esquema

La instalación tiene un único orden. Primero se aprovisiona MySQL con el SQL del proyecto y se ejecuta `scripts/migrar-db.ps1`. Django aplica las migraciones históricas 0001–0009 y registra 0010 de solo estado. Spring comprueba que existan `auth_user` y las nueve tablas de dominio y registra la línea base Flyway 1. JPA valida las columnas mediante `ddl-auto=validate`. La línea base se documenta en Obsidian. Los cambios futuros del dominio van en Flyway desde V2 y se reflejan manualmente en el ORM de Django. El framework `auth_*` y `django_*` sigue bajo Django.

Flyway usa `agronomia_admin` y Spring usa `agronomia_api`. Las cuentas operativas comparten `auth_user` y `perfiles_usuario`. Se verificó con contraseñas sintéticas Unicode que ambos backends leen hashes escritos por el otro. El codificador de Spring valida y genera el formato `pbkdf2_sha256` que Django usa para esas contraseñas.

Se comprobó que Spring rechaza una base sin migrar antes de registrar Flyway, y que inicia con validación JPA después de Django tanto en una instancia MySQL 8.4.13 aislada como en la base actual. Las dos bases registraron 0010 y línea base 1. Las migraciones 0001–0009 se conservaron. La preparación y estas verificaciones no consumieron cuota externa.

## Fase 2 · Colisión en la ruta de carga

Django y Spring publican la misma ruta `POST /agronomia-api/api/images.php`. Django exige Bearer y acepta JPEG/PNG (`backend-django/core/views.py:32,441–468`). La versión heredada de Spring recibe la petición sin Bearer y una prueba exige WebP con HTTP 201 (`ImageUploadController.java:22–25` y `ImageUploadApiIntegrationTest.java:116–121`). La web de usuario filtra JPEG/PNG y Android convierte sus selecciones a JPEG antes de transmitirlas. Queda por confirmar si hay otros consumidores del contrato heredado. La recomendación es mantener la ruta y el JSON de Django, exigir Bearer y actualizar las pruebas para cubrir la paridad y rechazar WebP en esta ruta.
