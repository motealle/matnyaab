<?php
declare(strict_types=1);
require dirname(__DIR__) . '/recovery/bootstrap.php';

$name = basename((string) ($_GET['file'] ?? ''));
if ($name === '' || $name === '.' || $name === '..') {
    matnyaab_not_found();
}

$root = realpath(matnyaab_legacy_root() . '/update');
$path = $root === false ? false : realpath($root . '/' . $name);
if ($root === false || $path === false || !str_starts_with($path, $root . DIRECTORY_SEPARATOR)) {
    matnyaab_not_found();
}

matnyaab_send_file($path, $name);
