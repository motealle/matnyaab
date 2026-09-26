<?php
declare(strict_types=1);
require dirname(__DIR__) . '/recovery/bootstrap.php';

$id = filter_input(INPUT_GET, 'id', FILTER_VALIDATE_INT);
if (!$id) matnyaab_not_found();

$stmt = matnyaab_db()->prepare('SELECT content_package_file FROM matnyaab_contentsmodel WHERE id = ? LIMIT 1');
$stmt->execute([$id]);
$stored = $stmt->fetchColumn();
if (!is_string($stored) || $stored === '') matnyaab_not_found();

try {
    $path = matnyaab_legacy_file($stored);
} catch (Throwable) {
    matnyaab_not_found();
}
matnyaab_send_file($path);
