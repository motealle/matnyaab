<?php

namespace App\Http\Controllers;

use App\Models\Content;
use App\Models\News;
use App\Models\Statistic;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
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
            ->where('expire_date', '>', now()->format('Y-m-d H:i:s'))
            ->orderByDesc('expire_date')
            ->get(['id', 'title', 'content', 'expire_date']);

        return response()->json($rows);
    }

    public function getContents(Request $request): JsonResponse
    {
        if (! $request->filled('password')) {
            abort(404, 'اطلاعات یافت نشد ;)');
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

    public function downloadContent(Request $request): BinaryFileResponse
    {
        return $this->downloadStoredFile($request, 'content_package_file');
    }

    public function downloadContentImage(Request $request): BinaryFileResponse
    {
        return $this->downloadStoredFile($request, 'content_cover_image');
    }

    private function downloadStoredFile(Request $request, string $column): BinaryFileResponse
    {
        $id = filter_var($request->query('id'), FILTER_VALIDATE_INT);
        abort_unless($id, 404, 'اطلاعات یافت نشد ;)');

        $stored = Content::query()->whereKey($id)->value($column);
        abort_unless(is_string($stored) && $stored !== '', 404, 'اطلاعات یافت نشد ;)');

        $root = realpath((string) env('MATNYAAB_LEGACY_ROOT', base_path('../matnyaab_with_license')));
        abort_unless($root !== false, 404);

        $relative = ltrim(str_replace('\\', '/', $stored), '/');
        abort_if($relative === '' || str_contains($relative, '../'), 404);

        $path = realpath($root.DIRECTORY_SEPARATOR.$relative);
        abort_unless(
            $path !== false
            && is_file($path)
            && str_starts_with($path, $root.DIRECTORY_SEPARATOR),
            404
        );

        return response()->download($path, basename($path), [
            'Cache-Control' => 'private, max-age=0, must-revalidate',
        ]);
    }
}
