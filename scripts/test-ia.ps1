<#
    Prueba rapida de la IA configurada en local.properties.

    Lee AI_BASE_URL, AI_API_KEY, AI_MODEL y AI_IDENTIFY_PATH del archivo
    local.properties (nunca imprime la API key) y envia una peticion de
    chat/vision, mostrando la respuesta del asistente.

    Uso:
        powershell -ExecutionPolicy Bypass -File scripts\test-ia.ps1 -Prompt "Hola"
        powershell -ExecutionPolicy Bypass -File scripts\test-ia.ps1 -ImagePath "C:\fotos\planta.jpg"
        powershell -ExecutionPolicy Bypass -File scripts\test-ia.ps1 -Prompt "Que es?" -ImagePath "C:\fotos\planta.jpg"
#>
param(
    [string]$Prompt = 'Identifica la planta y responde SOLO con JSON: {"commonName":"","scientificName":"","confidence":0.0}. Si no estas seguro usa "unknown".',
    [string]$ImagePath
)

$ErrorActionPreference = "Stop"

# --- Leer local.properties ---
$root = Split-Path -Parent $PSScriptRoot
$localPropsPath = Join-Path $root "local.properties"
if (-not (Test-Path $localPropsPath)) {
    throw "No se encontro local.properties en: $root"
}

$props = @{}
Get-Content $localPropsPath | ForEach-Object {
    if ($_ -match '^\s*([^#=\s][^=]*?)\s*=\s*(.*)$') {
        $props[$Matches[1].Trim()] = $Matches[2].Trim()
    }
}

$baseUrl = $props['AI_BASE_URL']
$apiKey = $props['AI_API_KEY']
$model = if ($props['AI_MODEL']) { $props['AI_MODEL'] } else { 'gpt-4o-mini' }
$path = if ($props['AI_IDENTIFY_PATH']) { $props['AI_IDENTIFY_PATH'] } else { 'v1/chat/completions' }

if ([string]::IsNullOrWhiteSpace($baseUrl)) { throw "Falta AI_BASE_URL en local.properties." }
if ([string]::IsNullOrWhiteSpace($apiKey)) { throw "Falta AI_API_KEY en local.properties (pega tu clave ahi)." }

$endpoint = ($baseUrl.TrimEnd('/')) + '/' + ($path.TrimStart('/'))
Write-Host "Endpoint: $endpoint"
Write-Host "Modelo:   $model"

# --- Construir contenido (texto + imagen opcional) ---
$content = @(@{ type = "text"; text = $Prompt })

if ($ImagePath) {
    if (-not (Test-Path $ImagePath)) { throw "No existe la imagen: $ImagePath" }
    $fullImagePath = (Resolve-Path $ImagePath).Path
    $bytes = [System.IO.File]::ReadAllBytes($fullImagePath)
    $ext = [System.IO.Path]::GetExtension($fullImagePath).ToLower()
    $mime = switch ($ext) {
        '.png'  { 'image/png' }
        '.webp' { 'image/webp' }
        default { 'image/jpeg' }
    }
    $base64 = [Convert]::ToBase64String($bytes)
    $content += @{ type = "image_url"; image_url = @{ url = "data:$mime;base64,$base64" } }
    Write-Host ("Imagen:   {0} ({1:N1} KB)" -f $fullImagePath, ($bytes.Length / 1KB))
}

$body = @{
    model           = $model
    temperature     = 0.0
    response_format = @{ type = "json_object" }
    messages        = @(@{ role = "user"; content = $content })
} | ConvertTo-Json -Depth 10

$headers = @{
    "Authorization" = "Bearer $apiKey"
    "Content-Type"  = "application/json"
}

# --- Enviar ---
try {
    $response = Invoke-RestMethod -Method Post -Uri $endpoint -Headers $headers -Body $body -TimeoutSec 60
    Write-Host "`n=== Respuesta del asistente ===" -ForegroundColor Green
    $response.choices[0].message.content
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    Write-Host "`n=== Error HTTP $status ===" -ForegroundColor Red
    if ($_.ErrorDetails.Message) { $_.ErrorDetails.Message } else { $_.Exception.Message }
    exit 1
}
