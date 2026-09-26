<?php
declare(strict_types=1);

date_default_timezone_set('Asia/Tehran');

function app_root(): string { return __DIR__; }
function legacy_root(): string { return app_root() . '/matnyaab_with_license'; }
function db_path(): string {
    $p = getenv('MATNYAAB_DB_PATH');
    return (is_string($p) && $p !== '') ? $p : legacy_root() . '/db.sqlite3';
}
function db(): PDO {
    static $pdo = null;
    if ($pdo instanceof PDO) return $pdo;
    $path = db_path();
    if (!is_file($path)) throw new RuntimeException('SQLite database not found');
    $pdo = new PDO('sqlite:' . $path, null, null, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    ]);
    $pdo->exec('PRAGMA busy_timeout = 5000');
    return $pdo;
}
function json_out(mixed $data, int $status = 200): never {
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}
function not_found(): never {
    http_response_code(404);
    header('Content-Type: text/plain; charset=utf-8');
    echo 'اطلاعات یافت نشد ;)';
    exit;
}
function bool_int(mixed $v): int {
    if (is_bool($v)) return $v ? 1 : 0;
    return in_array(strtolower(trim((string)$v)), ['1','true','yes','on'], true) ? 1 : 0;
}
function legacy_file(string $stored): string {
    $relative = ltrim(str_replace('\\', '/', $stored), '/');
    if ($relative === '' || str_contains($relative, '../')) throw new RuntimeException('invalid path');
    $root = realpath(legacy_root());
    $path = realpath(legacy_root() . '/' . $relative);
    if ($root === false || $path === false || !str_starts_with($path, $root . DIRECTORY_SEPARATOR)) {
        throw new RuntimeException('file missing');
    }
    return $path;
}
function send_file_range(string $path, ?string $name = null): never {
    if (!is_file($path) || !is_readable($path)) not_found();
    while (ob_get_level() > 0) ob_end_clean();
    @set_time_limit(0);
    $size = filesize($path);
    if ($size === false) not_found();

    $start = 0; $end = $size - 1; $status = 200;
    $range = $_SERVER['HTTP_RANGE'] ?? '';
    if (preg_match('/bytes=(\d*)-(\d*)/', $range, $m)) {
        if ($m[1] !== '') $start = max(0, (int)$m[1]);
        if ($m[2] !== '') $end = min($end, (int)$m[2]);
        if ($start > $end || $start >= $size) {
            header('Content-Range: bytes */'.$size);
            http_response_code(416);
            exit;
        }
        $status = 206;
    }

    $length = $end - $start + 1;
    http_response_code($status);
    header('Accept-Ranges: bytes');
    header('Content-Length: '.$length);
    header('Content-Type: '.(mime_content_type($path) ?: 'application/octet-stream'));
    header('Content-Disposition: attachment; filename="'.str_replace('"','',$name ?? basename($path)).'"');
    header('Cache-Control: private, max-age=0, must-revalidate');
    if ($status === 206) header("Content-Range: bytes $start-$end/$size");

    $fp = fopen($path, 'rb');
    if ($fp === false) not_found();
    fseek($fp, $start);
    $remaining = $length;
    while ($remaining > 0 && !feof($fp)) {
        $chunk = fread($fp, min(1024*1024, $remaining));
        if ($chunk === false || $chunk === '') break;
        echo $chunk;
        flush();
        $remaining -= strlen($chunk);
        if (connection_aborted()) break;
    }
    fclose($fp);
    exit;
}

$route = (string)($_GET['_route'] ?? '');

try {
    switch ($route) {
        case 'health':
            $count = (int)db()->query('SELECT COUNT(*) FROM matnyaab_contentsmodel')->fetchColumn();
            json_out(['ok'=>true,'service'=>'matnyaab-recovery','php'=>PHP_VERSION,'sqlite'=>true,'contents'=>$count]);

        case 'news':
            $stmt = db()->prepare(
                'SELECT id,title,content,expire_date FROM news_news WHERE expire_date > ? ORDER BY expire_date DESC'
            );
            $stmt->execute([date('Y-m-d H:i:s')]);
            json_out(array_map(static fn(array $r): array => [
                'id'=>(int)$r['id'],
                'title'=>(string)$r['title'],
                'content'=>(string)$r['content'],
                'expire_date'=>(string)$r['expire_date'],
            ], $stmt->fetchAll()));

        case 'get_contents':
            if (!isset($_GET['password']) || trim((string)$_GET['password']) === '') not_found();
            $stmt = db()->query(
                'SELECT id,content_title,content_filesize,content_date_added,content_package_file,content_cover_image
                 FROM matnyaab_contentsmodel WHERE content_show=1 ORDER BY content_date_added DESC'
            );
            $items = [];
            foreach ($stmt as $r) {
                $title=(string)$r['content_title'];
                $size=(int)($r['content_filesize'] ?? 0);
                $date=(string)($r['content_date_added'] ?? '');
                $package=(string)($r['content_package_file'] ?? '');
                $cover=(string)($r['content_cover_image'] ?? '');
                $items[]=[
                    'content_id'=>(int)$r['id'],
                    'content_title'=>$title,
                    'content_filesize'=>$size,
                    'content_date_added'=>$date,
                    'content_package_file'=>$package,
                    'content_cover_image'=>$cover,
                    'title'=>$title,
                    'filesize'=>$size,
                    'date_added'=>$date,
                    'package_file'=>$package,
                    'cover_image'=>$cover,
                ];
            }
            json_out($items);

        case 'statistics':
            if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') json_out(['status'=>'error'],405);
            $system=trim((string)($_POST['user_system_id'] ?? ''));
            $action=trim((string)($_POST['action'] ?? ''));
            if ($system==='' || $action==='') json_out(['status'=>'error'],400);
            $version=isset($_POST['app_version']) ? trim((string)$_POST['app_version']) : null;
            $stmt=db()->prepare(
                'INSERT INTO matnyaab_statistics
                 (user_system_id,has_subscription,action,action_datetime,app_version)
                 VALUES (?,?,?,?,?)'
            );
            $stmt->execute([
                $system,
                bool_int($_POST['has_subscription'] ?? 0),
                mb_substr($action,0,50),
                date('Y-m-d H:i:s'),
                $version === '' ? null : mb_substr((string)$version,0,10),
            ]);
            json_out(['status'=>'success']);

        case 'download_content':
        case 'download_content_img':
            $id=filter_input(INPUT_GET,'id',FILTER_VALIDATE_INT);
            if (!$id) not_found();
            $column=$route==='download_content' ? 'content_package_file' : 'content_cover_image';
            $stmt=db()->prepare("SELECT $column FROM matnyaab_contentsmodel WHERE id=? LIMIT 1");
            $stmt->execute([$id]);
            $stored=$stmt->fetchColumn();
            if (!is_string($stored) || $stored==='') not_found();
            send_file_range(legacy_file($stored));

        case 'update':
            $name=basename((string)($_GET['file'] ?? ''));
            if ($name==='' || $name==='.' || $name==='..') not_found();
            $root=realpath(legacy_root().'/update');
            $path=$root===false ? false : realpath($root.'/'.$name);
            if ($root===false || $path===false || !str_starts_with($path,$root.DIRECTORY_SEPARATOR)) not_found();
            send_file_range($path,$name);

        default:
            not_found();
    }
} catch (Throwable $e) {
    error_log('Matnyaab recovery error: '.get_class($e).': '.$e->getMessage());
    json_out(['ok'=>false,'error'=>'service_unavailable'],503);
}
