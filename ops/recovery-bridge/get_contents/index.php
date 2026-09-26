<?php
declare(strict_types=1);
require dirname(__DIR__) . '/recovery/bootstrap.php';

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'GET') {
    matnyaab_not_found();
}
if (!isset($_GET['password']) || trim((string) $_GET['password']) === '') {
    matnyaab_not_found();
}

$stmt = matnyaab_db()->query(
    'SELECT id, content_title, content_filesize, content_date_added, content_package_file, content_cover_image
     FROM matnyaab_contentsmodel
     WHERE content_show = 1
     ORDER BY content_date_added DESC'
);

$items = [];
foreach ($stmt as $row) {
    $date = (string) ($row['content_date_added'] ?? '');
    $package = (string) ($row['content_package_file'] ?? '');
    $cover = (string) ($row['content_cover_image'] ?? '');
    $title = (string) ($row['content_title'] ?? '');
    $filesize = (int) ($row['content_filesize'] ?? 0);

    // Both legacy Django keys and the Kotlin-port keys are returned.
    $items[] = [
        'content_id' => (int) $row['id'],
        'content_title' => $title,
        'content_filesize' => $filesize,
        'content_date_added' => $date,
        'content_package_file' => $package,
        'content_cover_image' => $cover,
        'title' => $title,
        'filesize' => $filesize,
        'date_added' => $date,
        'package_file' => $package,
        'cover_image' => $cover,
    ];
}

matnyaab_json($items);
