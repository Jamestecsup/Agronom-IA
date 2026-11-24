# Migración de backends (Opción A)

Actualizado 2026-11-24 · Rama local `feature/swap-backends`.

Destino acordado: Spring Boot sirve Android y la web de usuario. Django sirve la web de administración. Ambos usan MySQL Server 8.4 y comparten las cuentas del proyecto. El destino elimina el puente HTTP entre backends.

| Fase | Estado | Evidencia o siguiente hito |
|---|---|---|
| 0. Preparación | Completa | La rama parte de `config/mysql-server` (`1619e9b)) e integra `fix/api-csrf` (`8208c7c)) en `da16adf`. La matriz de rutas está en la nota de Obsidian «Migración de backends (Opción A)». Pasaron las dos pruebas de regresión CSRF con SQLite en memoria. |
| 1. Propiedad de datos | En pausa por decisión de arranque | Se verificó el esquema actual con consultas de metadatos. Pendientes V1, configuración de Flyway, migraciones de estado de Django, codificador PBKDF2 y comprobación en ambas bases. |
| 2. API de usuario en Spring | Pendiente | Portar y comprobar paridad con dobles Pl@ntNet/IA. |
| 3. API de administración en Django | Pendiente | Portar el contrato `/api/admin/`, conservar CSRF en `/admin/` y retirar rutas y secretos del puente. |
| 4. Clientes y configuración | Pendiente | Cambiar los destinos, preparar el arranque y trasladar las variables privadas a su archivo ignorado. |
| 5. Calidad de las fichas | Pendiente | Actualizar las skills y evaluar los textos con datos simulados. |
| 6. Identificación asíncrona | Opcional, pendiente | Considerarla después de verificar las fases 0 a 5. |
| Verificación final | Pendiente | Pruebas automáticas, humo con dobles, compilación Android si hay SDK y un único escaneo real con máximo seis llamadas a IA. |

## Decisión de Fase 1

La base local usa MySQL Server 8.4.13. Contiene las diez tablas de framework y las nueve de dominio, sin `flyway_schema_history`. Las nueve tablas de dominio referencian `auth_user`. Las migraciones Django anteriores a este cambio crean y renombran las tablas de dominio. Una migración final que solo cambie `managed` dejaría ese DDL activo en una instalación nueva.

Con `baseline-on-migrate` y `baseline-version=1`, Flyway registra la versión 1 al encontrar un esquema ocupado y omite `V1`. Esto sirve para adoptar el esquema existente, pero omitiría la creación del dominio en una base nueva tras el primer `migrate` de Django. [Comportamiento de Flyway](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-baseline-on-migrate-setting)

**Propuesta pendiente de confirmación:** usar `baseline-version=0` en la instalación nueva para ejecutar V1 después de que Django cree `auth_user`. Adoptar la base existente con `baseline-version=1` solo después de verificar el esquema contra V1. Reemplazar la cadena de dominio de Django por una migración que reconstruya el estado del ORM sin DDL y preserve el historial de bases completamente migradas. Comprobar que un esquema parcial falle con diagnóstico antes de registrar la línea base.

Durante esta preparación se revisó el código y se consultó solo metadata de MySQL. El esquema, el tráfico de servicio y la configuración privada conservan el estado de la rama de origen.
