# App móvil Agronom-IA

Este directorio contiene el proyecto Android independiente en Kotlin y Jetpack
Compose, junto con sus archivos Gradle y la configuración local del SDK.

## Abrir en Android Studio

Abre la carpeta `App movil` como proyecto. Si Android Studio ya tenía abierto el
proyecto desde la raíz anterior, cierra ese proyecto y vuelve a abrir esta
carpeta.

## Instalar en un emulador

Con un emulador iniciado y el backend Spring Boot disponible en el equipo anfitrión:

```powershell
cd "App movil"
\.\gradlew.bat installDebug
```

La URL de Spring Boot para el emulador se configura en `local.properties` mediante
`BACKEND_BASE_URL=http://10.0.2.2:8081/`.

## Horario semanal, cuidados y Canvas

La pestaña **Horario semanal** permite importar el PDF oficial de clases. Spring Boot
valida la sesión y procesa el archivo en el mismo servicio para extraer el texto,
leer la grilla y analizarla con IA. Antes de guardar puedes editar, agregar o
quitar bloques; al confirmar, el PDF temporal se elimina y el horario se guarda
por usuario en MySQL.

Con horario confirmado, puedes pedir una propuesta de cuidado basada en las
fichas de tus plantas. La IA evita los bloques de clase; revisas y editas las
indicaciones, duración, día y hora antes de guardar. También puedes reprogramar
cuidados en espacios disponibles y exportar las clases y el plan confirmado como
PDF.

La pestaña **Tareas Canvas** se mantiene aparte. Canvas no se mezcla con el
horario oficial ni se usa para generar los cuidados.
