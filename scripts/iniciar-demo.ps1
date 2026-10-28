<#
  Inicia todos los servicios de Agronom-IA para la demo.

  Orden: MySQL (XAMPP) -> Django (usuario) -> Spring (admin) -> webs -> emulador.
  Si un servicio ya está corriendo, lo respeta y no lo duplica.

  Uso:
      powershell -ExecutionPolicy Bypass -File scripts\iniciar-demo.ps1
      powershell -ExecutionPolicy Bypass -File scripts\iniciar-demo.ps1 -ConEmulador

  No llama a Pl@ntNet ni a la IA: solo levanta los servicios.
#>
param(
    [switch]$ConEmulador
)

$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot

$mysqlExe = "C:\xampp\mysql\bin\mysqld.exe"
$mysqlIni = "C:\xampp\mysql\bin\my.ini"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$emuladorExe = Join-Path $env:LOCALAPPDATA "Android\Sdk\emulator\emulator.exe"
$jarSpring = Join-Path $raiz "backend-spring\target\agronomia-backend-0.1.0-SNAPSHOT.jar"

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

Write-Host ""
Write-Host "=== Agronom-IA | inicio de la demo ===" -ForegroundColor Green
Write-Host ""

# 1. MySQL -----------------------------------------------------------------
if (Puerto-Abierto 3306) {
    Write-Host "[1/5] MySQL ya esta encendido."
} else {
    Write-Host "[1/5] Encendiendo MySQL (XAMPP)..."
    if (-not (Test-Path $mysqlExe)) { throw "No se encontro $mysqlExe" }
    Start-Process -FilePath $mysqlExe `
        -ArgumentList @("--defaults-file=$mysqlIni", "--standalone") `
        -WindowStyle Minimized | Out-Null
    Esperar-Puerto 3306 "MySQL" 60
    Write-Host "      MySQL listo en el puerto 3306."
}

# 2. Django (servicio de usuario) ------------------------------------------
if (Puerto-Abierto 8000) {
    Write-Host "[2/5] Django ya esta corriendo."
} else {
    Write-Host "[2/5] Iniciando Django (servicio de usuario)..."
    $comando = "Set-Location '$raiz\backend-django'; " +
               "Write-Host 'Django | servicio de usuario' -ForegroundColor Cyan; " +
               "py manage.py runserver 0.0.0.0:8000 --noreload"
    Iniciar-Ventana $comando
    Esperar-Puerto 8000 "Django" 90
    Write-Host "      Django listo en http://127.0.0.1:8000"
}

# 3. Spring (servicio de administracion) -----------------------------------
if (Puerto-Abierto 8081) {
    Write-Host "[3/5] Spring ya esta corriendo."
} else {
    Write-Host "[3/5] Iniciando Spring Boot (panel de administracion)..."
    if (-not (Test-Path $jarSpring)) {
        Write-Host "      Construyendo el jar (solo la primera vez)..."
        Push-Location (Join-Path $raiz "backend-spring")
        try { .\mvnw.cmd -q -DskipTests package } finally { Pop-Location }
    }
    $dbUsuario = if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "agronomia_api" }
    $dbClave = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "agronomia_local_2026" }
    $token = if ($env:ADMIN_TOKEN_SECRET) { $env:ADMIN_TOKEN_SECRET } else { "dev-only-change-me" }
    $comando = "`$env:DB_USERNAME='$dbUsuario'; " +
               "`$env:DB_PASSWORD='$dbClave'; " +
               "`$env:ADMIN_TOKEN_SECRET='$token'; " +
               "`$env:SERVER_PORT='8081'; " +
               "Set-Location '$raiz\backend-spring'; " +
               "Write-Host 'Spring Boot | panel de administracion' -ForegroundColor Cyan; " +
               "java -jar 'target\agronomia-backend-0.1.0-SNAPSHOT.jar'"
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
    if (Puerto-Abierto $web.Puerto "localhost") {
        Write-Host "[4/5] $($web.Nombre) ya esta corriendo."
        continue
    }
    $ruta = Join-Path $raiz $web.Carpeta
    if (-not (Test-Path (Join-Path $ruta "node_modules"))) {
        throw "Falta $($web.Carpeta)\node_modules. Ejecuta: cd $($web.Carpeta); npm install"
    }
    Write-Host "[4/5] Iniciando $($web.Nombre)..."
    $comando = "Set-Location '$ruta'; " +
               "Write-Host '$($web.Nombre)' -ForegroundColor Cyan; npm run dev"
    Iniciar-Ventana $comando
    Esperar-Puerto $web.Puerto $web.Nombre 60 "localhost"
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
Write-Host "  Web usuario : http://localhost:5174   agricultor / Campo12345"
Write-Host "  Panel admin : http://localhost:5173   admin / Admin12345"
Write-Host "  Django API  : http://127.0.0.1:8000/agronomia-api/api/health"
Write-Host "  Spring API  : http://127.0.0.1:8081/api/admin/health"
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
