<?php
declare(strict_types=1);

date_default_timezone_set('Asia/Tehran');

function matnyaab_root(): string
{
    return dirname(__DIR__);
}

function matnyaab_legacy_root(): string
{
    return matnyaab_root() . '/matnyaab_with_license';
}

function matnyaab_db_path(): string
{
    $configured = getenv('MATNYAAB_DB_PATH');
    if (is_string($configured) && $configured !== '') {
        return $configured;
    }
    return matnyaab_legacy_root() . '/db.sqlite3';
}

function matnyaab_db(): PDO
{
    static $pdo = null;
    if ($pdo instanceof PDO) {
        return $pdo;
    }

    $path = matnyaab_db_path();
    if (!is_file($path)) {
        throw new RuntimeException('SQLite database not found');
    }

    $pdo = new PDO('sqlite:' . $path, null, null, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    ]);
    $pdo->exec('PRAGMA busy_timeout = 5000');
    return $pdo;
}

function matnyaab_json(mixed $data, int $status = 200): never
{
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function matnyaab_not_found(): never
{
    http_response_code(404);
    header('Content-Type: text/plain; charset=utf-8');
    echo 'اطلاعات یافت نشد ;)';
    exit;
}

function matnyaab_bool(mixed $value): int
{
    if (is_bool($value)) return $value ? 1 : 0;
    $v = strtolower(trim((string) $value));
    return in_array($v, ['1', 'true', 'yes', 'on'], true) ? 1 : 0;
}

function matnyaab_legacy_file(string $storedPath): string
{
    $relative = ltrim(str_replace('\\', '/', $storedPath), '/');
    if ($relative === '' || str_contains($relative, '../')) {
        throw new RuntimeException('Invalid stored path');
    }

    $candidate = matnyaab_legacy_root() . '/' . $relative;
    $realRoot = realpath(matnyaab_legacy_root());
    $real = realpath($candidate);
    if ($realRoot === false || $real === false || !str_starts_with($real, $realRoot . DIRECTORY_SEPARATOR)) {
        throw new RuntimeException('File not found');
    }
    return $real;
}

function matnyaab_send_file(string $path, ?string $downloadName = null): never
{
    if (!is_file($path) || !is_readable($path)) {
        matnyaab_not_found();
    }

    while (ob_get_level() > 0) {
        ob_end_clean();
    }
    @set_time_limit(0);

    $size = filesize($path);
    if ($size === false) {
        matnyaab_not_found();
    }

    $start = 0;
    $end = $size - 1;
    $status = 200;

    $range = $_SERVER['HTTP_RANGE'] ?? '';
    if (preg_match('/bytes=(\d*)-(\d*)/', $range, $m)) {
        if ($m[1] !== '') $start = max(0, (int) $m[1]);
        if ($m[2] !== '') $end = min($end, (int) $m[2]);
        if ($start > $end || $start >= $size) {
            header('Content-Range: bytes */' . $size);
            http_response_code(416);
            exit;
        }
        $status = 206;
    }

    $length = $end - $start + 1;
    http_response_code($status);
    header('Accept-Ranges: bytes');
    header('Content-Length: ' . $length);
    header('Content-Type: ' . (mime_content_type($path) ?: 'application/octet-stream'));
    header('Content-Disposition: attachment; filename="' . str_replace('"', '', $downloadName ?? basename($path)) . '"');
    header('Cache-Control: private, max-age=0, must-revalidate');
    if ($status === 206) {
        header("Content-Range: bytes $start-$end/$size");
    }

    $fp = fopen($path, 'rb');
    if ($fp === false) {
        matnyaab_not_found();
    }
    fseek($fp, $start);
    $remaining = $length;
    while ($remaining > 0 && !feof($fp)) {
        $chunk = fread($fp, min(1024 * 1024, $remaining));
        if ($chunk === false || $chunk === '') break;
        echo $chunk;
        flush();
        $remaining -= strlen($chunk);
        if (connection_aborted()) break;
    }
    fclose($fp);
    exit;
}
