<?php
declare(strict_types=1);
require dirname(__DIR__) . '/recovery/bootstrap.php';

$now = date('Y-m-d H:i:s');
$stmt = matnyaab_db()->prepare(
    'SELECT id, title, content, expire_date
     FROM news_news
     WHERE expire_date > ?
     ORDER BY expire_date DESC'
);
$stmt->execute([$now]);

matnyaab_json(array_map(
    static fn(array $row): array => [
        'id' => (int) $row['id'],
        'title' => (string) $row['title'],
        'content' => (string) $row['content'],
        'expire_date' => (string) $row['expire_date'],
    ],
    $stmt->fetchAll()
));
