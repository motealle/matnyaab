<?php

namespace Tests\Feature;

use Illuminate\Support\Facades\DB;
use Tests\TestCase;

class LegacyApiParityTest extends TestCase
{
    private const SYSTEM_ID = '0123456789ABCDEF0123456789ABCDEF';
    private const ACTION = 'ci-api-parity';

    protected function tearDown(): void
    {
        DB::table('matnyaab_statistics')
            ->where('user_system_id', self::SYSTEM_ID)
            ->where('action', self::ACTION)
            ->delete();

        parent::tearDown();
    }

    public function test_health_contract_matches_recovery_shape(): void
    {
        $response = $this->get('/health');

        $response->assertOk()
            ->assertJson([
                'ok' => true,
                'service' => 'matnyaab-laravel',
                'sqlite' => true,
            ]);

        $json = $response->json();

        $this->assertIsString($json['php']);
        $this->assertSame(
            (int) DB::table('matnyaab_contentsmodel')->count(),
            (int) $json['contents']
        );
    }

    public function test_news_contract_matches_legacy_query_and_shape(): void
    {
        $now = now()->format('Y-m-d H:i:s');

        $expected = DB::table('news_news')
            ->where('expire_date', '>', $now)
            ->orderByDesc('expire_date')
            ->get(['id', 'title', 'content', 'expire_date'])
            ->map(static fn ($row): array => [
                'id' => (int) $row->id,
                'title' => (string) $row->title,
                'content' => (string) $row->content,
                'expire_date' => (string) $row->expire_date,
            ])
            ->values()
            ->all();

        $this->get('/news')
            ->assertOk()
            ->assertExactJson($expected);
    }

    public function test_get_contents_matches_legacy_keys_order_and_types(): void
    {
        $expected = DB::table('matnyaab_contentsmodel')
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
            ->map(static function ($row): array {
                $title = (string) $row->content_title;
                $filesize = (int) $row->content_filesize;
                $date = (string) $row->content_date_added;
                $package = (string) $row->content_package_file;
                $cover = (string) $row->content_cover_image;

                return [
                    'content_id' => (int) $row->id,
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
            })
            ->values()
            ->all();

        $this->get('/get_contents?password=compat')
            ->assertOk()
            ->assertExactJson($expected);
    }

    public function test_get_contents_preserves_legacy_not_found_behavior(): void
    {
        $missing = $this->get('/get_contents');

        $missing->assertStatus(404);
        $this->assertSame('اطلاعات یافت نشد ;)', $missing->getContent());
        $this->assertStringStartsWith(
            'text/plain',
            (string) $missing->headers->get('Content-Type')
        );

        $wrongMethod = $this->post('/get_contents', ['password' => 'compat']);
        $wrongMethod->assertStatus(404);
        $this->assertSame('اطلاعات یافت نشد ;)', $wrongMethod->getContent());
    }

    public function test_statistics_preserves_legacy_method_validation_and_insert(): void
    {
        $this->get('/statistics')
            ->assertStatus(405)
            ->assertExactJson(['status' => 'error']);

        $this->post('/statistics', [])
            ->assertStatus(400)
            ->assertExactJson(['status' => 'error']);

        $this->post('/statistics', [
            'user_system_id' => self::SYSTEM_ID,
            'has_subscription' => 'true',
            'action' => self::ACTION,
            'app_version' => '1.2.345678901234',
        ])
            ->assertOk()
            ->assertExactJson(['status' => 'success']);

        $row = DB::table('matnyaab_statistics')
            ->where('user_system_id', self::SYSTEM_ID)
            ->where('action', self::ACTION)
            ->latest('id')
            ->first();

        $this->assertNotNull($row);
        $this->assertSame(1, (int) $row->has_subscription);
        $this->assertSame('1.2.345678', (string) $row->app_version);
        $this->assertNotEmpty((string) $row->action_datetime);
    }

    public function test_download_endpoints_preserve_plain_text_not_found_contract(): void
    {
        foreach (['/download_content', '/download_content_img'] as $path) {
            $response = $this->get($path);
            $response->assertStatus(404);
            $this->assertSame('اطلاعات یافت نشد ;)', $response->getContent());
            $this->assertStringStartsWith(
                'text/plain',
                (string) $response->headers->get('Content-Type')
            );
        }
    }
}
