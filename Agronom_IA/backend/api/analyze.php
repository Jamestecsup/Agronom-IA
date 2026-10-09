<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

function respond(int $status, array $body): never {
    http_response_code($status);
    echo json_encode($body, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    exit;
}

function requiredEnv(string $key): string {
    $value = getenv($key);
    if ($value === false || $value === '') {
        throw new RuntimeException("Falta configurar la variable de entorno $key.");
    }
    return $value;
}

function markBatch(mysqli $db, string $batchId, string $status): void {
    $stmt = $db->prepare('UPDATE plant_images SET processing_status = ? WHERE batch_id = ?');
    $stmt->bind_param('ss', $status, $batchId);
    $stmt->execute();
    $stmt->close();
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    header('Allow: POST');
    respond(405, ['success' => false, 'error' => 'Método no permitido. Usa POST.']);
}
if (!extension_loaded('mysqli') || !extension_loaded('curl')) {
    respond(500, ['success' => false, 'error' => 'PHP requiere las extensiones mysqli y curl.']);
}
$body = json_decode((string)file_get_contents('php://input'), true);
$batchId = is_array($body) ? (string)($body['batch_id'] ?? '') : '';
if (!preg_match('/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i', $batchId)) {
    respond(422, ['success' => false, 'error' => 'El identificador del lote no es válido.']);
}

try {
    $apiKey = requiredEnv('PLANT_AI_API_KEY');
    $model = getenv('PLANT_AI_MODEL') ?: 'gpt-4.1-mini';
    $storage = requiredEnv('PLANT_UPLOAD_DIR');
    $db = new mysqli(requiredEnv('PLANT_DB_HOST'), requiredEnv('PLANT_DB_USER'), requiredEnv('PLANT_DB_PASSWORD'), requiredEnv('PLANT_DB_NAME'), (int)(getenv('PLANT_DB_PORT') ?: 3306));
    $db->set_charset('utf8mb4');
} catch (Throwable $e) {
    if (isset($db) && $db instanceof mysqli) { $db->close(); }
    error_log('plant analysis configuration/database error: ' . $e->getMessage());
    $status = str_contains($e->getMessage(), 'PLANT_AI_API_KEY') ? 503 : 500;
    respond($status, ['success' => false, 'error' => str_contains($e->getMessage(), 'PLANT_AI_API_KEY') ? 'El análisis remoto aún no está configurado en el backend.' : 'El backend no tiene una configuración válida para analizar este lote.']);
}

try {
    $stmt = $db->prepare('SELECT relative_path, mime_type FROM plant_images WHERE batch_id = ? ORDER BY created_at, id LIMIT 6');
    $stmt->bind_param('s', $batchId);
    $stmt->execute();
    $result = $stmt->get_result();
    $images = $result->fetch_all(MYSQLI_ASSOC);
    $stmt->close();
    if (count($images) < 1 || count($images) > 5) {
        respond(404, ['success' => false, 'error' => 'No se encontró un lote válido con imágenes guardadas.']);
    }

    $root = realpath($storage);
    if ($root === false) { throw new RuntimeException('La carpeta de imágenes no está disponible.'); }
    $content = [[
        'type' => 'input_text',
        'text' => 'Analiza las fotografías de una planta para una guía de jardinería en español. Las imágenes pueden mostrar la misma planta desde varios ángulos. Identifica solo hasta donde la evidencia visual permita. Si no es posible identificarla con seguridad, identified=false, nombres vacíos y confidence="insuficiente". No inventes especie ni cuidados específicos; formula cuidados generales cuando falte certeza y explica las limitaciones. No des consejos de consumo o uso medicinal. Devuelve todos los campos en español; scientific_name vacío si no hay confianza suficiente.'
    ]];
    foreach ($images as $image) {
        $mime = (string)$image['mime_type'];
        if (!in_array($mime, ['image/jpeg', 'image/png', 'image/webp'], true)) {
            throw new RuntimeException('El lote contiene un formato no compatible con el modelo.');
        }
        $path = realpath($root . DIRECTORY_SEPARATOR . str_replace(['/', '\\'], DIRECTORY_SEPARATOR, (string)$image['relative_path']));
        if ($path === false || !str_starts_with($path, $root . DIRECTORY_SEPARATOR) || !is_file($path) || !is_readable($path)) {
            throw new RuntimeException('No se pudo encontrar una de las imágenes almacenadas.');
        }
        $size = filesize($path);
        if ($size === false || $size < 1 || $size > 15 * 1024 * 1024) {
            throw new RuntimeException('Una imagen almacenada no cumple el tamaño esperado.');
        }
        $encoded = base64_encode((string)file_get_contents($path));
        $content[] = ['type' => 'input_image', 'detail' => 'high', 'image_url' => "data:$mime;base64,$encoded"];
    }

    $schema = [
        'type' => 'object', 'additionalProperties' => false,
        'properties' => [
            'identified' => ['type' => 'boolean'],
            'common_name' => ['type' => 'string'],
            'scientific_name' => ['type' => 'string'],
            'confidence' => ['type' => 'string', 'enum' => ['alta', 'media', 'baja', 'insuficiente']],
            'description' => ['type' => 'string'],
            'watering' => ['type' => 'string'],
            'lighting' => ['type' => 'string'],
            'substrate' => ['type' => 'string'],
            'care' => ['type' => 'string'],
            'uncertainty' => ['type' => 'string']
        ],
        'required' => ['identified', 'common_name', 'scientific_name', 'confidence', 'description', 'watering', 'lighting', 'substrate', 'care', 'uncertainty']
    ];
    $payload = [
        'model' => $model,
        'store' => false,
        'max_output_tokens' => 900,
        'input' => [['role' => 'user', 'content' => $content]],
        'text' => ['format' => ['type' => 'json_schema', 'name' => 'plant_identification', 'strict' => true, 'schema' => $schema]]
    ];
    $curl = curl_init('https://api.openai.com/v1/responses');
    curl_setopt_array($curl, [
        CURLOPT_POST => true,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_CONNECTTIMEOUT => 15,
        CURLOPT_TIMEOUT => 120,
        CURLOPT_HTTPHEADER => ['Authorization: Bearer ' . $apiKey, 'Content-Type: application/json', 'Accept: application/json'],
        CURLOPT_POSTFIELDS => json_encode($payload, JSON_THROW_ON_ERROR)
    ]);
    $raw = curl_exec($curl);
    $httpStatus = (int)curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
    $curlError = curl_error($curl);
    curl_close($curl);
    if ($raw === false) { throw new RuntimeException('El proveedor de IA no respondió: ' . $curlError); }
    $response = json_decode($raw, true);
    if ($httpStatus < 200 || $httpStatus >= 300 || !is_array($response)) {
        error_log('OpenAI vision request failed with HTTP ' . $httpStatus . ': ' . substr((string)$raw, 0, 1200));
        throw new RuntimeException('El servicio de IA rechazó la solicitud o no está disponible (HTTP ' . $httpStatus . ').');
    }
    $text = null;
    foreach (($response['output'] ?? []) as $item) {
        if (($item['type'] ?? '') === 'message') {
            foreach (($item['content'] ?? []) as $part) {
                if (($part['type'] ?? '') === 'output_text') { $text = $part['text'] ?? null; break 2; }
                if (($part['type'] ?? '') === 'refusal') { throw new RuntimeException('El servicio de IA no pudo procesar estas imágenes.'); }
            }
        }
    }
    $analysis = is_string($text) ? json_decode($text, true) : null;
    if (!is_array($analysis) || !isset($analysis['identified'], $analysis['common_name'], $analysis['scientific_name'], $analysis['confidence'], $analysis['description'], $analysis['watering'], $analysis['lighting'], $analysis['substrate'], $analysis['care'], $analysis['uncertainty'])) {
        throw new RuntimeException('El servicio de IA devolvió una respuesta incompleta.');
    }
    markBatch($db, $batchId, 'processed');
    respond(200, ['success' => true, 'batch_id' => $batchId, 'model' => $model, 'analysis' => $analysis]);
} catch (Throwable $e) {
    try { markBatch($db, $batchId, 'failed'); } catch (Throwable) { }
    error_log('plant analysis failed: ' . $e->getMessage());
    respond(502, ['success' => false, 'error' => $e->getMessage()]);
} finally {
    $db->close();
}
