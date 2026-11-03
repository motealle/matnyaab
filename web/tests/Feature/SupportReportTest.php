<?php

namespace Tests\Feature;

use Tests\TestCase;

class SupportReportTest extends TestCase
{
    private string $path;

    protected function setUp(): void
    {
        parent::setUp();
        $this->path = sys_get_temp_dir().'/matnyaab-support-'.uniqid().'.jsonl';
        config(['services.support.reports_path' => $this->path]);
    }

    protected function tearDown(): void
    {
        @unlink($this->path);
        parent::tearDown();
    }

    public function test_support_report_is_stored_with_normalized_digits(): void
    {
        $this->post('/support', [
            'name' => 'پژوهشگر',
            'contact' => '09120000000',
            'amount' => '۲۰۰٬۰۰۰',
            'card_last4' => '۱۲۳۴',
            'message' => 'سپاس',
        ])->assertRedirect();

        $lines = file($this->path, FILE_IGNORE_NEW_LINES);
        $this->assertCount(1, $lines);
        $row = json_decode($lines[0], true);
        $this->assertSame(200000, $row['amount_toman']);
        $this->assertSame('1234', $row['card_last4']);
    }

    public function test_invalid_support_report_is_rejected(): void
    {
        $this->post('/support', ['name' => '', 'contact' => '', 'amount' => 'abc'])
            ->assertSessionHasErrors(['name', 'contact', 'amount']);
        $this->assertFileDoesNotExist($this->path);
    }

    public function test_honeypot_submission_is_ignored(): void
    {
        $this->post('/support', ['name' => 'x', 'contact' => 'y', 'amount' => '1000', 'website' => 'spam'])
            ->assertRedirect();
        $this->assertFileDoesNotExist($this->path);
    }

    public function test_seo_endpoints_respond(): void
    {
        $this->get('/robots.txt')->assertOk()->assertSee('Sitemap:', false);
        $this->get('/sitemap.xml')->assertOk()->assertSee('<urlset', false);
    }
}
