# Esquema compartido

La base MySQL Server 8.4 se llama `agronomia`. Spring atiende la API de usuario y Django la de administración. Los backends comparten las tablas. La identidad usa `auth_user` y `perfiles_usuario` con hashes PBKDF2-SHA256 compatibles con Django.

## Propiedad y arranque

1. El SQL inicial aprovisiona la base y la tabla heredada de imágenes.
2. `scripts/migrar-db.ps1` aplica Django. Las migraciones históricas 0001–0009 crean el esquema base y la 0010 marca los modelos de dominio como no gestionados.
3. Spring comprueba las tablas y registra Flyway en versión 1. Hibernate comprueba tipos de columnas con `ddl-auto=validate`.

Todo cambio posterior de dominio va en Flyway desde V2 y se refleja en el modelo Django. Django mantiene las tablas de su framework. Flyway y las migraciones iniciales usan `agronomia_admin`. Ambos servicios usan `agronomia_api` para sus operaciones normales.

| Tabla | Función |
|---|---|
| `auth_user`, `perfiles_usuario` | Identidad compartida y rol. |
| `imagenes_planta` | Lotes y referencias a imágenes originales. |
| `procesos_identificacion` | Corrida de Pl@ntNet, estado y JSON reutilizado al confirmar. |
| `identificaciones_planta` | Identificación y ficha generada en JSON. |
| `mensajes_chat`, `imagenes_progreso_chat` | Mensajes por cuenta y sesión, y adjuntos asociados a la planta y al turno. |
| `importaciones_horario_pendientes`, `horarios_semanales`, `tareas_cuidado` | PDFs temporales, horario confirmado y plan de cuidados. |

Spring guarda los archivos fuera de MySQL, en `UPLOAD_DIR`. Un respaldo completo incluye la base y este directorio. Para procesos con identidades distintas, el panel recibe acceso de solo lectura al volumen privado.

Los modelos de Django y los mapeos JPA en Spring documentan tipos y nulabilidad. El estado por fase está en [PROGRESO.md](../backend-spring/PROGRESO.md).
