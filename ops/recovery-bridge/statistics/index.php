<?php
declare(strict_types=1);
require dirname(__DIR__) . '/recovery/bootstrap.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    matnyaab_json(['status' => 'error'], 405);
}

$systemId = trim((string) ($_POST['user_system_id'] ?? ''));
$action = trim((string) ($_POST['action'] ?? ''));
$appVersion = isset($_POST['app_version']) ? trim((string) $_POST['app_version']) : null;

if ($systemId === '' || $action === '') {
    matnyaab_json(['status' => 'error'], 400);
}

$stmt = matnyaab_db()->prepare(
    'INSERT INTO matnyaab_statistics
     (user_system_id, has_subscription, action, action_datetime, app_version)
     VALUES (?, ?, ?, ?, ?)'
);
$stmt->execute([
    $systemId,
    matnyaab_bool($_POST['has_subscription'] ?? 0),
    mb_substr($action, 0, 50),
    date('Y-m-d H:i:s'),
    $appVersion === '' ? null : mb_substr((string) $appVersion, 0, 10),
]);

matnyaab_json(['status' => 'success'], 200);
