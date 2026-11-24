<#
  Inicia todos los servicios de Agronom-IA para la demo.

  Requiere MySQL Server activo. Orden: migraciones Django -> Django (admin) -> Spring (usuario) -> webs -> emulador.
  Si un servicio ya está corriendo, lo respeta y no lo duplica.
  Los servicios escuchan en 127.0.0.1 por defecto; usa -ExponerRed para abrirlos a la red.

  Uso:
      powershell -ExecutionPolicy Bypass -File scripts\iniciar-demo.ps1
      powershell -ExecutionPolicy Bypass -File scripts\iniciar-demo.ps1 -ConEmulador

  No llama a Pl@ntNet ni a la IA: solo levanta los servicios.
#>
param(
    [switch]$ConEmulador,
    [switch]$ExponerRed
)

$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$emuladorExe = Join-Path $env:LOCALAPPDATA "Android\Sdk\emulator\emulator.exe"
$jarSpring = Join-Path $raiz "backend-spring\target\agronomia-backend-0.1.0-SNAPSHOT.jar"
$archivoEnvDjango = Join-Path $raiz "backend-django\.env"

$pythonDjango = Join-Path $raiz "backend-django\.venv\Scripts\python.exe"
if (-not (Test-Path -LiteralPath $pythonDjango)) { throw "Falta el entorno virtual de Django. Consulta backend-django/README.md." }
if (-not (Test-Path -LiteralPath $archivoEnvDjango)) { throw "Falta backend-django/.env." }

$archivoEnvSpring = Join-Path $raiz "backend-spring\.env"
if (-not (Test-Path -LiteralPath $archivoEnvSpring)) { throw "Falta backend-spring/.env." }
$direccion = if ($ExponerRed) { "0.0.0.0" } else { "127.0.0.1" }

function Puerto-Abierto {
    param([int]$Puerto, [string]$Equipo = "127.0.0.1")
    return [bool](Test-NetConnection -ComputerName $Equipo -Port $Puerto `
        -InformationLevel Quiet -WarningAction SilentlyContinue)
}

function Esperar-Puerto {
    param([int]$Puerto, [string]$Nombre, [int]$Segundos = 90,
          [string]$Equipo = "127.0.0.1")
    for ($i = 1; $i -le $Segundos; $i++) {
        if (Puerto-Abierto $Puerto $Equipo) { return }
        Start-Sleep -Seconds 1
    }
    throw "$Nombre no respondio en el puerto $Puerto tras $Segundos segundos."
}

function Iniciar-Ventana {
    param([string]$Comando)
    Start-Process -FilePath "powershell.exe" `
        -ArgumentList @("-NoExit", "-Command", $Comando) `
        -WindowStyle Normal | Out-Null
}

function Spring-BootJar-Valido {
    param([string]$Ruta)
    if (-not (Test-Path $Ruta)) { return $false }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archivoZip = [System.IO.Compression.ZipFile]::OpenRead($Ruta)
    try {
        $tieneClases = $archivoZip.Entries | Where-Object { $_.FullName -like "BOOT-INF/classes/*" } | Select-Object -First 1
        $tieneDependencias = $archivoZip.Entries | Where-Object { $_.FullName -like "BOOT-INF/lib/*" } | Select-Object -First 1
        return [bool]($tieneClases -and $tieneDependencias)
    } catch {
        return $false
    } finally {
        $archivoZip.Dispose()
    }
}

Write-Host ""
Write-Host "=== Agronom-IA | inicio de la demo ===" -ForegroundColor Green
Write-Host ""

# 1. MySQL Server ----------------------------------------------------------
if (-not (Puerto-Abierto 3306)) {
    throw "MySQL Server no responde en 127.0.0.1:3306. Inicia el servicio de Windows e inténtalo de nuevo."
}
Write-Host "[1/5] MySQL Server responde en el puerto 3306."

# Django crea las tablas base. Flyway adopta el esquema al arrancar Spring.
& (Join-Path $PSScriptRoot "migrar-db.ps1")
if ($LASTEXITCODE -ne 0) { throw "Fallaron las migraciones de Django." }

# 2. Django (administración) ------------------------------------------
if (Puerto-Abierto 8000) {
    Write-Host "[2/5] Django ya esta corriendo."
} else {
    Write-Host "[2/5] Iniciando Django (administracion)..."
    $comando = "Set-Location '$raiz\backend-django'; " +
               "Write-Host 'Django | panel de administracion' -ForegroundColor Cyan; " +
               "& '.\.venv\Scripts\python.exe' manage.py runserver ${direccion}:8000 --noreload"
    Iniciar-Ventana $comando
    Esperar-Puerto 8000 "Django" 90
    Write-Host "      Django listo en http://127.0.0.1:8000"
}

# 3. Spring (usuario, identificación y horarios) ------------
if (Puerto-Abierto 8081) {
    Write-Host "[3/5] Spring ya esta corriendo."
} else {
    Write-Host "[3/5] Iniciando Spring Boot (usuario y horarios)..."
    if (-not (Spring-BootJar-Valido $jarSpring)) {
        Write-Host "      Construyendo el jar de Spring Boot..."
        Push-Location (Join-Path $raiz "backend-spring")
        try {
            .\mvnw.cmd -q -DskipTests package
            if ($LASTEXITCODE -ne 0 -or -not (Spring-BootJar-Valido $jarSpring)) {
                throw "No se pudo generar un jar ejecutable de Spring Boot."
            }
        } finally { Pop-Location }
    }
    $comando = "Set-Location '$raiz\backend-spring'; " +
               "Write-Host 'Spring Boot | servicio de usuario' -ForegroundColor Cyan; " +
               "java -jar 'target\agronomia-backend-0.1.0-SNAPSHOT.jar' '--server.address=$direccion'"
    Iniciar-Ventana $comando
    Esperar-Puerto 8081 "Spring" 90
    Write-Host "      Spring listo en http://127.0.0.1:8081"
}

# 4. Webs ------------------------------------------------------------------
$webs = @(
    @{ Puerto = 5174; Carpeta = "web-user"; Nombre = "Web de usuario" },
    @{ Puerto = 5173; Carpeta = "web-admin"; Nombre = "Panel de administracion" }
)
foreach ($web in $webs) {
    if (Puerto-Abierto $web.Puerto "127.0.0.1") {
        Write-Host "[4/5] $($web.Nombre) ya esta corriendo."
        continue
    }
    $ruta = Join-Path $raiz $web.Carpeta
    if (-not (Test-Path (Join-Path $ruta "node_modules"))) {
        throw "Falta $($web.Carpeta)\node_modules. Ejecuta: cd $($web.Carpeta); npm install"
    }
    Write-Host "[4/5] Iniciando $($web.Nombre)..."
    $comando = "Set-Location '$ruta'; " +
               "Write-Host '$($web.Nombre)' -ForegroundColor Cyan; npm.cmd run dev -- --host $direccion"
    Iniciar-Ventana $comando
    Esperar-Puerto $web.Puerto $web.Nombre 60 "127.0.0.1"
    Write-Host "      $($web.Nombre) listo en http://localhost:$($web.Puerto)"
}

# 5. Emulador (opcional) ---------------------------------------------------
if ($ConEmulador) {
    $dispositivos = & $adb devices 2>$null
    if ($dispositivos -match "emulator-\d+\s+device") {
        Write-Host "[5/5] El emulador ya esta encendido."
    } else {
        Write-Host "[5/5] Abriendo el emulador (puede tardar 1-2 minutos)..."
        Start-Process -FilePath $emuladorExe `
            -ArgumentList @("-avd", "Test", "-no-snapshot-save", "-gpu", "auto") | Out-Null
        & $adb wait-for-device
        for ($i = 0; $i -lt 120; $i++) {
            $boot = (& $adb shell getprop sys.boot_completed 2>$null)
            if ($boot -and $boot.Trim() -eq "1") { break }
            Start-Sleep -Seconds 2
        }
        Write-Host "      Emulador listo."
    }
} else {
    Write-Host "[5/5] Emulador: omitido (usa -ConEmulador para abrirlo)."
}

# Resumen ------------------------------------------------------------------
Write-Host ""
powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "check-servicios.ps1")
Write-Host ""
Write-Host "Cuentas de demo:" -ForegroundColor Yellow
Write-Host "  Web usuario : http://localhost:5174"
Write-Host "  Panel admin : http://localhost:5173"
Write-Host "  Django API  : http://127.0.0.1:8000/api/admin/health"
Write-Host "  Spring API  : http://127.0.0.1:8081/agronomia-api/api/health"
Write-Host ""
Write-Host "Recorrido sugerido:" -ForegroundColor Yellow
Write-Host "  1. Web usuario: identificar un girasol y leer la ficha (8 secciones,"
Write-Host "     glosario, materiales y consejos)."
Write-Host "  2. Caso con dudas: foto recortada -> elegir candidata -> ficha."
Write-Host "  3. Caso sin deteccion: imagen gris -> guia de fotos."
Write-Host "  4. Chat: preguntar por riego o plagas de la planta detectada."
Write-Host "  5. Movil: mismo flujo desde la galeria del emulador."
Write-Host "  6. Panel admin: resumen, usuarios, identificaciones y chat."
Write-Host ""
