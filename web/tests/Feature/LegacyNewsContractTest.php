<?php

namespace Tests\Feature;

use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use Tests\TestCase;

class LegacyNewsContractTest extends TestCase
{
    protected function setUp(): void
    {
        parent::setUp();
        config(['database.default' => 'news_contract',
            'database.connections.news_contract' => [
                'driver' => 'sqlite', 'database' => ':memory:', 'prefix' => '',
            ],
        ]);
        Schema::create('news_news', function (Blueprint $table): void {
            $table->increments('id');
            $table->text('title');
            $table->text('content');
            $table->text('expire_date');
        });
        // 08:30 UTC is 12:00 Tehran. Equality is expired in the recovery contract.
        $this->travelTo(\Carbon\Carbon::parse('2026-09-27 08:30:00', 'UTC'));
    }

    protected function tearDown(): void
    {
        $this->travelBack();
        DB::purge('news_contract');
        parent::tearDown();
    }

    public function test_news_matches_golden_expiry_order_types_and_persian_text(): void
    {
        DB::table('news_news')->insert([
            ['id' => 1, 'title' => 'expired', 'content' => 'old', 'expire_date' => '2026-09-27 11:59:59'],
            ['id' => 2, 'title' => 'boundary', 'content' => 'old', 'expire_date' => '2026-09-27 12:00:00'],
            ['id' => 3, 'title' => 'خبر سوم', 'content' => 'متن فارسی', 'expire_date' => '2026-09-27 12:00:01'],
            ['id' => 4, 'title' => 'خبر چهارم', 'content' => 'متن تازه', 'expire_date' => '2026-09-28 00:00:00'],
        ]);
        $expected = [
            ['id' => 4, 'title' => 'خبر چهارم', 'content' => 'متن تازه', 'expire_date' => '2026-09-28 00:00:00'],
            ['id' => 3, 'title' => 'خبر سوم', 'content' => 'متن فارسی', 'expire_date' => '2026-09-27 12:00:01'],
        ];
        foreach (['/news', '/news/'] as $path) {
            $response = $this->get($path)->assertOk();
            $this->assertSame($expected, $response->json());
            $this->assertTrue($response->headers->hasCacheControlDirective('no-store'));
        }
    }

    public function test_news_without_active_items_is_an_empty_json_array(): void
    {
        $this->get('/news')->assertOk()->assertContent('[]');
    }
}
