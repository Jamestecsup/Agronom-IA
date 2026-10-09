<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

function respond(int $status, array $body): never {
    http_response_code($status);
    echo json_encode($body, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    exit;
}

function envRequired(string $key): string {
    $value = getenv($key);
    if ($value === false || $value === '') {
        throw new RuntimeException("Falta configurar la variable de entorno $key.");
    }
    return $value;
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    header('Allow: POST');
    respond(405, ['success' => false, 'error' => 'Método no permitido. Usa POST.']);
}
if (!extension_loaded('mysqli') || !extension_loaded('fileinfo') || !extension_loaded('gd')) {
    respond(500, ['success' => false, 'error' => 'PHP requiere las extensiones mysqli, fileinfo y GD.']);
}
if (!isset($_FILES['photos']) || !is_array($_FILES['photos']['name'])) {
    respond(400, ['success' => false, 'error' => 'Envía entre una y cinco imágenes en el campo photos[].']);
}

$names = $_FILES['photos']['name'];
$count = count($names);
if ($count < 1 || $count > 5) {
    respond(422, ['success' => false, 'error' => 'La operación debe contener entre una y cinco imágenes.']);
}
$maxBytes = 15 * 1024 * 1024;
$maxDimension = 4096;
$allowed = ['image/jpeg' => 'jpg', 'image/png' => 'png', 'image/webp' => 'webp'];
$finfo = new finfo(FILEINFO_MIME_TYPE);
$validated = [];

foreach ($names as $index => $_name) {
    $error = $_FILES['photos']['error'][$index] ?? UPLOAD_ERR_NO_FILE;
    if ($error !== UPLOAD_ERR_OK) {
        respond(422, ['success' => false, 'error' => 'Una imagen no pudo recibirse completamente (código ' . $error . ').']);
    }
    $tmp = $_FILES['photos']['tmp_name'][$index] ?? '';
    $size = (int)($_FILES['photos']['size'][$index] ?? 0);
    if ($tmp === '' || !is_uploaded_file($tmp) || !is_readable($tmp) || $size < 1 || $size > $maxBytes) {
        respond(422, ['success' => false, 'error' => 'Una imagen está vacía, no es accesible o supera 15 MB.']);
    }
    $mime = $finfo->file($tmp);
    if (!isset($allowed[$mime])) {
        respond(415, ['success' => false, 'error' => 'Formato no admitido. Se aceptan JPEG, PNG y WebP decodificables.']);
    }
    $image = @getimagesize($tmp);
    if ($image === false || ($image['mime'] ?? '') !== $mime) {
        respond(422, ['success' => false, 'error' => 'El contenido de una imagen está dañado o no coincide con su formato.']);
    }
    $width = (int)$image[0];
    $height = (int)$image[1];
    if ($width < 16 || $height < 16 || $width > $maxDimension || $height > $maxDimension) {
        respond(422, ['success' => false, 'error' => 'Las dimensiones deben estar entre 16 y 4 096 píxeles.']);
    }
    $decoded = @imagecreatefromstring((string)file_get_contents($tmp));
    if ($decoded === false) {
        respond(422, ['success' => false, 'error' => 'Una imagen está dañada y no se pudo decodificar.']);
    }
    imagedestroy($decoded);
    $validated[] = ['tmp' => $tmp, 'size' => $size, 'mime' => $mime, 'extension' => $allowed[$mime], 'width' => $width, 'height' => $height];
}

try {
    $storage = envRequired('PLANT_UPLOAD_DIR');
    $dbHost = envRequired('PLANT_DB_HOST');
    $dbUser = envRequired('PLANT_DB_USER');
    $dbPassword = envRequired('PLANT_DB_PASSWORD');
    $dbName = envRequired('PLANT_DB_NAME');
    $dbPort = (int)(getenv('PLANT_DB_PORT') ?: 3306);
} catch (RuntimeException $e) {
    respond(500, ['success' => false, 'error' => $e->getMessage()]);
}
if (!is_dir($storage) && !@mkdir($storage, 0750, true) && !is_dir($storage)) {
    respond(500, ['success' => false, 'error' => 'No se pudo crear la carpeta de almacenamiento configurada.']);
}
if (!is_writable($storage)) {
    respond(500, ['success' => false, 'error' => 'Apache no tiene permiso de escritura en la carpeta de imágenes.']);
}

$db = null;
$createdFiles = [];
$stagingFiles = [];
$batchId = sprintf('%04x%04x-%04x-%04x-%04x-%04x%04x%04x', random_int(0, 65535), random_int(0, 65535), random_int(0, 65535), random_int(0, 4095) | 0x4000, random_int(0, 16383) | 0x8000, random_int(0, 65535), random_int(0, 65535), random_int(0, 65535));
$batchDir = rtrim($storage, DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR . $batchId;

try {
    if (!mkdir($batchDir, 0750)) {
        throw new RuntimeException('No se pudo crear la carpeta del registro.');
    }
    foreach ($validated as $image) {
        $id = sprintf('%04x%04x-%04x-%04x-%04x-%04x%04x%04x', random_int(0, 65535), random_int(0, 65535), random_int(0, 65535), random_int(0, 4095) | 0x4000, random_int(0, 16383) | 0x8000, random_int(0, 65535), random_int(0, 65535), random_int(0, 65535));
        $relative = $batchId . '/' . $id . '.' . $image['extension'];
        $stage = $batchDir . DIRECTORY_SEPARATOR . $id . '.part';
        $final = $batchDir . DIRECTORY_SEPARATOR . $id . '.' . $image['extension'];
        if (!move_uploaded_file($image['tmp'], $stage)) {
            throw new RuntimeException('No se pudo escribir una imagen en el almacenamiento local.');
        }
        $stagingFiles[] = $stage;
        $createdFiles[] = $final;
        $image['id'] = $id;
        $image['relative'] = $relative;
        $image['stage'] = $stage;
        $image['final'] = $final;
        $rows[] = $image;
    }

    mysqli_report(MYSQLI_REPORT_ERROR | MYSQLI_REPORT_STRICT);
    $db = new mysqli($dbHost, $dbUser, $dbPassword, $dbName, $dbPort);
    $db->set_charset('utf8mb4');
    $db->begin_transaction();
    foreach ($rows as $row) {
        if (!rename($row['stage'], $row['final'])) {
            throw new RuntimeException('No se pudo finalizar la escritura de una imagen.');
        }
        $stagingFiles = array_values(array_diff($stagingFiles, [$row['stage']]));
        $stmt = $db->prepare('INSERT INTO plant_images (id, batch_id, relative_path, mime_type, file_size, width, height) VALUES (?, ?, ?, ?, ?, ?, ?)');
        $stmt->bind_param('ssssiii', $row['id'], $batchId, $row['relative'], $row['mime'], $row['size'], $row['width'], $row['height']);
        $stmt->execute();
        $stmt->close();
    }
    $db->commit();
    respond(201, ['success' => true, 'batch_id' => $batchId, 'images' => array_map(static fn(array $row): array => ['id' => $row['id'], 'path' => $row['relative'], 'mime_type' => $row['mime'], 'size' => $row['size']], $rows)]);
} catch (Throwable $e) {
    if ($db instanceof mysqli) {
        try { $db->rollback(); } catch (Throwable) { }
    }
    foreach (array_merge($stagingFiles, $createdFiles) as $file) {
        if (is_file($file)) { @unlink($file); }
    }
    if (isset($batchDir) && is_dir($batchDir)) { @rmdir($batchDir); }
    error_log('plant image upload failed: ' . $e->getMessage());
    respond(500, ['success' => false, 'error' => 'No se completó el almacenamiento. No se confirmó ningún éxito; revisa la conexión y los permisos del servidor.']);
} finally {
    if ($db instanceof mysqli) { $db->close(); }
}
