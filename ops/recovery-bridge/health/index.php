<?php
declare(strict_types=1);
require dirname(__DIR__) . '/recovery/bootstrap.php';

try {
    $pdo = matnyaab_db();
    $contentCount = (int) $pdo->query('SELECT COUNT(*) FROM matnyaab_contentsmodel')->fetchColumn();
    matnyaab_json([
        'ok' => true,
        'service' => 'matnyaab-recovery',
        'php' => PHP_VERSION,
        'sqlite' => true,
        'contents' => $contentCount,
    ]);
} catch (Throwable $e) {
    matnyaab_json([
        'ok' => false,
        'service' => 'matnyaab-recovery',
        'error' => get_class($e),
    ], 503);
}
