<#
    Comprueba que los cuatro servicios y MySQL estén respondiendo.

    No usa claves ni llama a la IA: solo mira puertos y endpoints de salud.
    Úsalo antes de la demo para saber qué falta arrancar.

    Uso:
        powershell -ExecutionPolicy Bypass -File scripts\check-servicios.ps1
#>

$ErrorActionPreference = "SilentlyContinue"
$resultados = @()

function Test-Puerto {
    param([string]$Nombre, [string]$Host_, [int]$Puerto, [string]$Detalle)
    $ok = Test-NetConnection -ComputerName $Host_ -Port $Puerto -InformationLevel Quiet -WarningAction SilentlyContinue
    $script:resultados += [pscustomobject]@{
        Servicio = $Nombre
        Puerto   = $Puerto
        Estado   = if ($ok) { "OK" } else { "SIN RESPUESTA" }
        Detalle  = $Detalle
    }
}

function Test-Salud {
    param([string]$Nombre, [string]$Url, [int]$Puerto)
    try {
        $respuesta = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
        $estado = if ($respuesta.StatusCode -ge 200 -and $respuesta.StatusCode -lt 400) { "OK" } else { "HTTP $($respuesta.StatusCode)" }
    } catch {
        $estado = "SIN RESPUESTA"
    }
    $script:resultados += [pscustomobject]@{
        Servicio = $Nombre
        Puerto   = $Puerto
        Estado   = $estado
        Detalle  = $Url
    }
}

Test-Puerto  -Nombre "MySQL (XAMPP)"         -Host_ "127.0.0.1" -Puerto 3306 -Detalle "base agronomia"
Test-Salud   -Nombre "Django (usuario)"      -Url "http://127.0.0.1:8000/agronomia-api/api/login" -Puerto 8000
Test-Salud   -Nombre "Spring (admin)"        -Url "http://127.0.0.1:8081/api/admin/health" -Puerto 8081
Test-Salud   -Nombre "Web de usuario"        -Url "http://localhost:5174/" -Puerto 5174
Test-Salud   -Nombre "Panel de administración" -Url "http://localhost:5173/" -Puerto 5173

$resultados | Format-Table -AutoSize

$caidos = ($resultados | Where-Object { $_.Estado -ne "OK" }).Count
if ($caidos -eq 0) {
    Write-Host "Todo listo para la demo." -ForegroundColor Green
} else {
    Write-Host "$caidos servicio(s) sin respuesta. Revisa README.md para arrancarlos." -ForegroundColor Yellow
}
