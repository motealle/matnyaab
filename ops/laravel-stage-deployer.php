<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');

$expected = '__DEPLOY_TOKEN__';
$provided = (string) ($_SERVER['HTTP_X_MATNYAAB_DEPLOY_TOKEN'] ?? '');

if ($expected === '' || !hash_equals($expected, $provided)) {
    http_response_code(403);
    echo json_encode(['ok' => false, 'error' => 'forbidden']);
    exit;
}

$releaseId = '__RELEASE_ID__';
$appKey = '__APP_KEY__';
$docRoot = __DIR__;
$stageRoot = dirname($docRoot) . DIRECTORY_SEPARATOR . 'matnyaab-laravel-stage';
$releaseDir = $stageRoot . DIRECTORY_SEPARATOR . 'releases' . DIRECTORY_SEPARATOR . $releaseId;
$sharedDir = $stageRoot . DIRECTORY_SEPARATOR . 'shared';
$zipPath = $docRoot . DIRECTORY_SEPARATOR . '.matnyaab-laravel-release.zip';

try {
    if (!extension_loaded('zip')) {
        throw new RuntimeException('zip extension is unavailable');
    }

    if (!is_file($zipPath)) {
        throw new RuntimeException('release archive is missing');
    }

    foreach ([
        $stageRoot,
        $stageRoot . DIRECTORY_SEPARATOR . 'releases',
        $sharedDir,
        $releaseDir,
    ] as $dir) {
        if (!is_dir($dir) && !mkdir($dir, 0755, true) && !is_dir($dir)) {
            throw new RuntimeException('cannot create ' . $dir);
        }
    }

    $zip = new ZipArchive();
    if ($zip->open($zipPath) !== true) {
        throw new RuntimeException('cannot open release archive');
    }

    if (!$zip->extractTo($releaseDir)) {
        $zip->close();
        throw new RuntimeException('cannot extract release archive');
    }
    $zip->close();

    foreach ([
        'storage',
        'storage/app',
        'storage/framework',
        'storage/framework/cache',
        'storage/framework/cache/data',
        'storage/framework/sessions',
        'storage/framework/views',
        'storage/logs',
        'bootstrap/cache',
    ] as $relative) {
        $dir = $releaseDir . DIRECTORY_SEPARATOR . str_replace('/', DIRECTORY_SEPARATOR, $relative);
        if (!is_dir($dir) && !mkdir($dir, 0755, true) && !is_dir($dir)) {
            throw new RuntimeException('cannot create writable directory');
        }
    }

    $database = $docRoot . DIRECTORY_SEPARATOR . 'matnyaab_with_license' . DIRECTORY_SEPARATOR . 'db.sqlite3';
    if (!is_file($database)) {
        throw new RuntimeException('legacy SQLite database is missing');
    }

    $keyFile = $sharedDir . DIRECTORY_SEPARATOR . 'app.key';
    if (is_file($keyFile)) {
        $persistentAppKey = trim((string) file_get_contents($keyFile));
    } else {
        $persistentAppKey = $appKey;
        if (file_put_contents($keyFile, $persistentAppKey . PHP_EOL, LOCK_EX) === false) {
            throw new RuntimeException('cannot persist application key');
        }
    }

    if ($persistentAppKey === '') {
        throw new RuntimeException('application key is empty');
    }

    $licensePolicy = $sharedDir . DIRECTORY_SEPARATOR . 'license-policy.json';
    if (!is_file($licensePolicy)) {
        $initialPolicy = json_encode([
            'version' => 1,
            'global_bypass' => false,
            'users' => [],
        ], JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES);
        if (!is_string($initialPolicy) || file_put_contents($licensePolicy, $initialPolicy . PHP_EOL, LOCK_EX) === false) {
            throw new RuntimeException('cannot initialize license policy');
        }
    }

    $envLines = [
        'APP_NAME=MATNYAAB',
        'APP_ENV=production',
        'APP_KEY=' . $persistentAppKey,
        'APP_DEBUG=false',
        'APP_URL=https://www.matnyaab.ir',
        'APP_TIMEZONE=Asia/Tehran',
        'LOG_CHANNEL=stack',
        'LOG_LEVEL=warning',
        'DB_CONNECTION=sqlite',
        'DB_DATABASE=' . $database,
        'SESSION_DRIVER=file',
        'SESSION_LIFETIME=18000',
        'CACHE_STORE=file',
        'QUEUE_CONNECTION=sync',
        'LICENSE_POLICY_PATH=' . $licensePolicy,
    ];

    $runtimeEnvFile = $sharedDir . DIRECTORY_SEPARATOR . 'runtime-services.env';
    if (is_file($runtimeEnvFile) && is_readable($runtimeEnvFile)) {
        $allowedRuntimeKeys = [
            'SMS_PANEL_URL',
            'SMS_PANEL_USERNAME',
            'SMS_PANEL_PASSWORD',
            'SMS_PANEL_NUMBER',
            'MAIL_PRODUCTION_ENABLED',
            'MAIL_MAILER',
            'MAIL_SCHEME',
            'MAIL_HOST',
            'MAIL_PORT',
            'MAIL_USERNAME',
            'MAIL_PASSWORD',
            'MAIL_ENCRYPTION',
            'MAIL_FROM_ADDRESS',
            'MAIL_FROM_NAME',
            'IDPAY_MERCHANT_CODE',
            'IDPAY_SANDBOX',
            'CONTENTS_API_PASSWORD',
        ];

        foreach (file($runtimeEnvFile, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES) ?: [] as $line) {
            if (!str_contains($line, '=')) {
                continue;
            }

            [$key] = explode('=', $line, 2);
            if (in_array($key, $allowedRuntimeKeys, true)) {
                $envLines[] = $line;
            }
        }
    }

    $envLines[] = '';
    $env = implode(PHP_EOL, $envLines);

    if (file_put_contents($releaseDir . DIRECTORY_SEPARATOR . '.env', $env, LOCK_EX) === false) {
        throw new RuntimeException('cannot write stage environment');
    }

    require $releaseDir . DIRECTORY_SEPARATOR . 'vendor' . DIRECTORY_SEPARATOR . 'autoload.php';
    $app = require $releaseDir . DIRECTORY_SEPARATOR . 'bootstrap' . DIRECTORY_SEPARATOR . 'app.php';

    $kernel = $app->make(Illuminate\Contracts\Console\Kernel::class);
    $kernel->bootstrap();

    $contents = App\Models\Content::query()->count();
    $users = App\Models\User::query()->count();

    file_put_contents(
        $stageRoot . DIRECTORY_SEPARATOR . 'current.txt',
        $releaseId . PHP_EOL,
        LOCK_EX
    );

    echo json_encode([
        'ok' => true,
        'release' => $releaseId,
        'php' => PHP_VERSION,
        'sqlite' => config('database.default') === 'sqlite',
        'contents' => $contents,
        'users' => $users,
        'stage_root_outside_document_root' => dirname($docRoot) === dirname($stageRoot),
    ], JSON_UNESCAPED_SLASHES);
} catch (Throwable $e) {
    error_log('Matnyaab Laravel stage deploy failed: ' . get_class($e) . ': ' . $e->getMessage());
    http_response_code(500);
    echo json_encode([
        'ok' => false,
        'error' => 'stage_deploy_failed',
        'type' => get_class($e),
    ]);
}
