<?php

namespace Tests\Feature;

use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\File;
use Illuminate\Support\Facades\Schema;
use Tests\TestCase;

class LegacyDownloadContractTest extends TestCase
{
    private string $root;
    private mixed $previousRoot;

    protected function setUp(): void
    {
        parent::setUp();
        // A separate connection prevents touching even the copied customer DB.
        config(['database.default' => 'download_contract',
            'database.connections.download_contract' => [
                'driver' => 'sqlite', 'database' => ':memory:', 'prefix' => '',
            ],
        ]);
        Schema::create('matnyaab_contentsmodel', function (Blueprint $table): void {
            $table->increments('id');
            $table->text('content_package_file');
            $table->text('content_cover_image');
        });
        $this->root = sys_get_temp_dir().'/matnyaab-contract-'.bin2hex(random_bytes(8));
        mkdir($this->root.'/contents', 0700, true);
        file_put_contents($this->root.'/contents/sample.txt', '0123456789');
        $this->previousRoot = $_ENV['MATNYAAB_LEGACY_ROOT'] ?? null;
        $_ENV['MATNYAAB_LEGACY_ROOT'] = $this->root;
        foreach (['contents/sample.txt', 'contents/missing.txt', '../outside.txt', ''] as $index => $path) {
            DB::table('matnyaab_contentsmodel')->insert([
                'id' => $index + 1, 'content_package_file' => $path, 'content_cover_image' => $path,
            ]);
        }
    }

    protected function tearDown(): void
    {
        if ($this->previousRoot === null) {
            unset($_ENV['MATNYAAB_LEGACY_ROOT']);
        } else {
            $_ENV['MATNYAAB_LEGACY_ROOT'] = $this->previousRoot;
        }
        File::deleteDirectory($this->root);
        DB::purge('download_contract');
        parent::tearDown();
    }

    public function test_invalid_and_missing_downloads_keep_the_legacy_404(): void
    {
        foreach (['download_content', 'download_content_img'] as $endpoint) {
            foreach (['', '?id=bad', '?id=0', '?id=999', '?id=2', '?id=3', '?id=4'] as $query) {
                $response = $this->get('/'.$endpoint.$query);
                $response->assertNotFound();
                $this->assertSame('اطلاعات یافت نشد ;)', $response->getContent());
                $this->assertStringStartsWith('text/plain', $response->headers->get('Content-Type'));
                $this->assertTrue($response->headers->hasCacheControlDirective('no-store'));
            }
        }
    }

    public function test_existing_files_can_be_downloaded_from_both_endpoints(): void
    {
        foreach (['download_content', 'download_content_img'] as $endpoint) {
            $response = $this->get('/'.$endpoint.'?id=1');
            $response->assertOk()->assertDownload('sample.txt');
            $this->assertSame(realpath($this->root.'/contents/sample.txt'),
                $response->baseResponse->getFile()->getRealPath());
        }
    }

    public function test_downloads_support_partial_and_unsatisfiable_ranges(): void
    {
        foreach (['download_content', 'download_content_img'] as $endpoint) {
            $this->get('/'.$endpoint.'?id=1', ['Range' => 'bytes=2-5'])
                ->assertStatus(206)->assertHeader('Content-Range', 'bytes 2-5/10')
                ->assertHeader('Content-Length', '4');
            $this->get('/'.$endpoint.'?id=1', ['Range' => 'bytes=20-30'])
                ->assertStatus(416)->assertHeader('Content-Range', 'bytes */10');
        }
    }

    public function test_missing_storage_root_keeps_the_legacy_404(): void
    {
        $_ENV['MATNYAAB_LEGACY_ROOT'] = $this->root.'/absent';
        foreach (['download_content', 'download_content_img'] as $endpoint) {
            $response = $this->get('/'.$endpoint.'?id=1');
            $response->assertNotFound();
            $this->assertSame('اطلاعات یافت نشد ;)', $response->getContent());
        }
    }
}
