# Backend Django · administración

Django atiende la web administrativa en `127.0.0.1:8000/api/admin/`. El sitio Django nativo sigue en `/admin/` y exige CSRF. Los clientes de usuario se conectan a Spring.

## Instalación

En la raíz, sigue el [README de Agronom-IA](../README.md) para MySQL Server 8.4. Copia `.env.example` a `.env` y configura el secreto de Django y las cuentas de ejecución y migración. Las claves de Pl@ntNet e IA corresponden a Spring.

```powershell
py -m venv .venv
& .venv\Scripts\python.exe -m pip install -r requirements.txt
..\scripts\migrar-db.ps1
& .venv\Scripts\python.exe manage.py runserver 127.0.0.1:8000 --noreload
```

Las migraciones históricas 0001–0009 inicializan el dominio y la 0010 lo declara no gestionado. Flyway registra línea base 1. Los cambios del dominio posteriores pasan por Flyway y se reflejan manualmente en Django. Django sigue migrando `auth_*` y `django_*`.

## Panel y pruebas

`/api/admin/` conserva login, sesión, salud, estadísticas, usuarios, plantas y chats. Los tokens de panel se firman con Django, duran ocho horas y se vuelven a comprobar contra la cuenta activa y el rol. Los intentos de login se limitan por cuenta e IP con la caché de Django. En un despliegue de varios procesos, usa una caché compartida.

Para probar sin proveedores externos, define `USE_SQLITE=1` en un PowerShell de prueba y ejecuta `.venv\Scripts\python.exe manage.py test`.
