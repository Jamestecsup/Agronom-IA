# Progreso
## Estado actual
- Rama: `feature/ia-modelos-y-web`.
- Último commit al redactar: `3725b3c Ajusta cuota de IA y amplía las pruebas y los acentos web`.
## Hecho
- Spring sirve la API de usuario y Django la de administración.
- Flyway aplicó V2 y V3 en MySQL 8.4 descartable.
- Spring incorpora proveedores propios, consentimiento, cifrado y prueba de capacidades.
- Web-user usa rutas, caché en memoria, jardín desde Spring y escáner que convierte WebP a JPEG.
- La cuota del sistema es de 25 intentos por usuario y día UTC.
- La última verificación sin red aprobó 52 pruebas de Spring, cuatro de Django y cinco de web-user, además de las compilaciones registradas.
## Siguiente paso
por definir por el usuario
## Decisiones vigentes
- Spring y Django comparten MySQL 8.4 y cuentas; no hay puente HTTP entre backends.
- Django conserva `auth_*` y `django_*`; los cambios futuros del dominio van en Flyway y se reflejan en Django.
- Los proveedores propios usan claves cifradas, validación de destino y consentimiento; la cuota y los límites por proceso figuran en el historial.
- Las pruebas usan dobles y no hacen llamadas externas sin autorización.
## Pendientes y riesgos
- Incorporar el WOFF2 latino y la licencia OFL desde una fuente verificable.
- Instalar y ejecutar Vitest, Testing Library, jest-axe y ESLint; validar WCAG con axe y actualizar la auditoría npm.
- La advertencia de Flyway 11.7.2 sobre MySQL 8.4 persiste; V2 y V3 se probaron en MySQL 8.4.
- La base de la demo no se migró; los contadores y límites en memoria son por proceso.
## Cómo retomar
Leer `AGENTS.md`, ejecutar `git status --short` y `git log -3 --oneline`, y tomar la tarea del prompt. [HISTORIAL.md](HISTORIAL.md) (no leer salvo que haga falta).
