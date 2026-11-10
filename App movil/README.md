# App móvil Agronom-IA

Este directorio contiene el proyecto Android independiente en Kotlin y Jetpack
Compose, junto con sus archivos Gradle y la configuración local del SDK.

## Abrir en Android Studio

Abre la carpeta `App movil` como proyecto. Si Android Studio ya tenía abierto el
proyecto desde la raíz anterior, cierra ese proyecto y vuelve a abrir esta
carpeta.

## Instalar en un emulador

Con un emulador iniciado y el backend Django disponible en el equipo anfitrión:

```powershell
cd "App movil"
\.\gradlew.bat installDebug
```

La URL de Django para el emulador se configura en `local.properties` mediante
`BACKEND_BASE_URL=http://10.0.2.2:8000/`.
