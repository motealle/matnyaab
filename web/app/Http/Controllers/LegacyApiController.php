<?php

namespace App\Http\Controllers;

use App\Models\Content;
use App\Models\News;
use App\Models\Statistic;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Symfony\Component\HttpFoundation\BinaryFileResponse;

class LegacyApiController extends Controller
{
    public function health(): JsonResponse
    {
        return response()->json([
            'ok' => true,
            'service' => 'matnyaab-laravel',
            'php' => PHP_VERSION,
            'sqlite' => config('database.default') === 'sqlite',
            'contents' => Content::query()->count(),
        ]);
    }

    public function news(): JsonResponse
    {
        $rows = News::query()
            ->where('expire_date', '>', now('Asia/Tehran')->format('Y-m-d H:i:s'))
            ->orderByDesc('expire_date')
            ->get(['id', 'title', 'content', 'expire_date']);

        return response()->json($rows->map(static fn (News $row): array => [
            'id' => (int) $row->id,
            'title' => (string) $row->title,
            'content' => (string) $row->content,
            'expire_date' => (string) $row->expire_date,
        ]))->header('Cache-Control', 'no-store');
    }

    public function getContents(Request $request): JsonResponse|Response
    {
        if (! $request->isMethod('get') || ! $request->filled('password')) {
            return $this->legacyNotFound();
        }

        $rows = Content::query()
            ->where('content_show', 1)
            ->orderByDesc('content_date_added')
            ->get([
                'id',
                'content_title',
                'content_filesize',
                'content_date_added',
                'content_package_file',
                'content_cover_image',
            ])
            ->map(static function (Content $row): array {
                return [
                    'content_id' => (int) $row->id,
                    'content_title' => (string) $row->content_title,
                    'content_filesize' => (int) $row->content_filesize,
                    'content_date_added' => (string) $row->content_date_added,
                    'content_package_file' => (string) $row->content_package_file,
                    'content_cover_image' => (string) $row->content_cover_image,
                    'title' => (string) $row->content_title,
                    'filesize' => (int) $row->content_filesize,
                    'date_added' => (string) $row->content_date_added,
                    'package_file' => (string) $row->content_package_file,
                    'cover_image' => (string) $row->content_cover_image,
                ];
            });

        return response()->json($rows);
    }

    public function statistics(Request $request): JsonResponse
    {
        if (! $request->isMethod('post')) {
            return response()->json(['status' => 'error'], 405);
        }

        $systemId = trim((string) $request->input('user_system_id', ''));
        $action = trim((string) $request->input('action', ''));

        if ($systemId === '' || $action === '') {
            return response()->json(['status' => 'error'], 400);
        }

        Statistic::query()->create([
            'user_system_id' => $systemId,
            'has_subscription' => $request->boolean('has_subscription') ? 1 : 0,
            'action' => mb_substr($action, 0, 50),
            'action_datetime' => now()->format('Y-m-d H:i:s'),
            'app_version' => $request->filled('app_version')
                ? mb_substr((string) $request->input('app_version'), 0, 10)
                : null,
        ]);

        return response()->json(['status' => 'success']);
    }

    public function downloadContent(Request $request): BinaryFileResponse|Response
    {
        return $this->downloadStoredFile($request, 'content_package_file');
    }

    public function downloadContentImage(Request $request): BinaryFileResponse|Response
    {
        return $this->downloadStoredFile($request, 'content_cover_image');
    }

    private function legacyNotFound(): Response
    {
        return response('اطلاعات یافت نشد ;)', 404)
            ->header('Content-Type', 'text/plain; charset=utf-8')
            ->header('Cache-Control', 'no-store');
    }

    private function downloadStoredFile(Request $request, string $column): BinaryFileResponse|Response
    {
        $id = filter_var($request->query('id'), FILTER_VALIDATE_INT);
        if (! $id) {
            return $this->legacyNotFound();
        }

        $stored = Content::query()->whereKey($id)->value($column);
        if (! is_string($stored) || $stored === '') {
            return $this->legacyNotFound();
        }

        $root = realpath((string) env('MATNYAAB_LEGACY_ROOT', base_path('../matnyaab_with_license')));
        if ($root === false) {
            return $this->legacyNotFound();
        }

        $relative = ltrim(str_replace('\\', '/', $stored), '/');
        if ($relative === '' || str_contains($relative, '../')) {
            return $this->legacyNotFound();
        }

        $path = realpath($root.DIRECTORY_SEPARATOR.$relative);
        if (
            $path === false
            || ! is_file($path)
            || ! str_starts_with($path, $root.DIRECTORY_SEPARATOR)
        ) {
            return $this->legacyNotFound();
        }

        return response()->download($path, basename($path), [
            'Cache-Control' => 'private, max-age=0, must-revalidate',
        ]);
    }
}
