<?php
declare(strict_types=1);

$stageRoot = dirname(__DIR__) . DIRECTORY_SEPARATOR . 'matnyaab-laravel-stage';
$currentFile = $stageRoot . DIRECTORY_SEPARATOR . 'current.txt';

if (!is_file($currentFile)) {
    http_response_code(503);
    header('Content-Type: text/plain; charset=utf-8');
    echo 'Laravel stage release is not available.';
    exit;
}

$releaseId = trim((string) file_get_contents($currentFile));
if ($releaseId === '' || !preg_match('/^[a-f0-9]{40}$/', $releaseId)) {
    http_response_code(503);
    exit('Invalid Laravel stage release.');
}

$publicDir = $stageRoot
    . DIRECTORY_SEPARATOR . 'releases'
    . DIRECTORY_SEPARATOR . $releaseId
    . DIRECTORY_SEPARATOR . 'public';

$index = $publicDir . DIRECTORY_SEPARATOR . 'index.php';
if (!is_file($index)) {
    http_response_code(503);
    exit('Laravel public entry point is missing.');
}

chdir($publicDir);
$_SERVER['SCRIPT_FILENAME'] = $index;

require $index;
