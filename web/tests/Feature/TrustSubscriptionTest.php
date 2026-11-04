<?php

namespace Tests\Feature;

use App\Support\SpamGuard;
use Tests\TestCase;

class TrustSubscriptionTest extends TestCase
{
    protected function setUp(): void
    {
        parent::setUp();
        config(['services.matnyaab.data_dir' => sys_get_temp_dir().'/mx-sub-'.uniqid()]);
    }

    private function oldToken(): string
    {
        $t = (string) (time() - 10);

        return $t.'.'.substr(hash_hmac('sha256', $t, (string) config('app.key')), 0, 20);
    }

    private function subscribe(array $extra = []): string
    {
        $r = $this->post('/subscribe', $extra + ['plan' => 'm1', 'name' => 'پژوهشگر', 'contact' => '09120000000', 'trust' => '1', '_ts' => $this->oldToken(), 'tracking_code' => '۱۲۳۴۵۶', 'card_last4' => '۹۸۷۶']);
        $r->assertRedirect();
        parse_str(parse_url($r->headers->get('Location'), PHP_URL_QUERY), $q);

        return $q['code'];
    }

    public function test_subscription_issues_code_and_client_sync_matches(): void
    {
        $code = $this->subscribe();
        $this->assertMatchesRegularExpression('/^MT-[A-Z2-9]{4}-[A-Z2-9]{4}$/', $code);

        $res = $this->postJson('/api/client/activate', ['code' => strtolower(str_replace('-', '', $code)), 'system_id' => 'ABC', 'app_version' => '2.1'])
            ->assertOk()->json();
        $this->assertSame($code, $res['subscription']['code']);
        $this->assertSame(30, $res['subscription']['days_left']);
        $this->assertSame(1, $res['subscription']['day_number']);
        $this->assertSame(3, $res['config']['trial_days']);
        $this->assertSame('6037997770259241', $res['config']['cards'][0]['number']);

        // تمدید با همان کد، دوره را از پایان دورهٔ فعلی ادامه می‌دهد
        $this->post('/subscribe', ['plan' => 'm6', 'renew_code' => $code, 'trust' => '1', '_ts' => $this->oldToken()])->assertRedirect();
        $res = $this->postJson('/api/client/ping', ['code' => $code, 'system_id' => 'ABC'])->json();
        $this->assertSame(210, $res['subscription']['days_left']);
    }

    public function test_org_subscription_uses_tiered_price_and_shared_code(): void
    {
        $r = $this->post('/subscribe', ['type' => 'org', 'plan' => 'm12', 'seats' => 10, 'org' => 'کتابخانهٔ نمونه', 'name' => 'مسئول', 'contact' => 'a@b.ir', 'trust' => '1', '_ts' => $this->oldToken()]);
        $r->assertRedirect();
        parse_str(parse_url($r->headers->get('Location'), PHP_URL_QUERY), $q);
        $sub = (new \App\Services\SubscriptionStore(new \App\Services\SiteSettings))->find($q['code']);
        $this->assertSame(2450000, $sub['price']);
        $this->assertSame(10, $sub['seats']);
        foreach (['PC1', 'PC2', 'PC3'] as $pc) {
            $res = $this->postJson('/api/client/activate', ['code' => $q['code'], 'system_id' => $pc])->json();
            $this->assertSame('کتابخانهٔ نمونه', $res['subscription']['org']);
            $this->assertSame(365, $res['subscription']['days_left']);
        }
        $this->post('/subscribe', ['type' => 'org', 'plan' => 'm12', 'seats' => 1, 'org' => 'x', 'name' => 'x', 'contact' => 'x', 'trust' => '1', '_ts' => $this->oldToken()])
            ->assertSessionHasErrors('seats');
        $this->get('/subscribe?type=org')->assertOk()->assertSee('data-seats', false);
    }

    public function test_spam_submissions_are_rejected(): void
    {
        $this->post('/subscribe', ['plan' => 'm1', 'name' => 'x', 'contact' => 'y', 'trust' => '1', '_ts' => SpamGuard::token()])
            ->assertSessionHasErrors('form');
        $this->post('/contact', ['name' => 'x', 'contact' => 'y', 'message' => 'سلام وقت بخیر، سؤالی دارم', 'website' => 'spam', '_ts' => $this->oldToken()])
            ->assertRedirect();
        $this->assertSame([], (new \App\Services\MessageStore)->inbox());
    }

    public function test_contact_message_reaches_admin_and_notice_reaches_client(): void
    {
        $this->post('/contact', ['type' => 'feature', 'name' => 'علی', 'contact' => 'a@b.ir', 'message' => 'لطفاً خروجی PDF از نتایج اضافه شود', '_ts' => $this->oldToken()])->assertRedirect();
        $this->assertCount(1, (new \App\Services\MessageStore)->inbox());

        $pass = 'Initial-Pass-For-Test';
        (new \App\Services\JsonStore('admin'))->update(function (array &$d) use ($pass) {
            $d = ['username' => 'admin', 'hash' => password_hash($pass, PASSWORD_BCRYPT), 'must_change' => false];
        });
        $this->get('/adminarea')->assertRedirect('/adminarea/login');
        $this->post('/adminarea/login', ['username' => 'admin', 'password' => 'wrong'])->assertSessionHasErrors();
        $this->post('/adminarea/login', ['username' => 'admin', 'password' => $pass])->assertRedirect('/adminarea');
        $this->get('/adminarea?tab=inbox')->assertOk()->assertSee('خروجی PDF', false);
        foreach (['overview', 'subscriptions', 'notices', 'settings', 'account'] as $tab) {
            $this->get('/adminarea?tab='.$tab)->assertOk();
        }

        $this->post('/adminarea/notices', ['title' => 'سلام', 'body' => 'نسخهٔ تازه آماده است', 'level' => 'info', 'target' => 'all'])->assertRedirect();
        $res = $this->postJson('/api/client/ping', ['system_id' => 'X', 'mode' => 'trial'])->json();
        $this->assertSame('سلام', $res['messages'][0]['title']);

        $this->post('/adminarea/settings', [
            'cards' => [['number' => '1111 2222 3333 4444', 'holder' => 'تست', 'enabled' => '1']],
            'plans' => [['id' => 'm1', 'title' => 'ماهانه', 'days' => '30', 'price' => '99000', 'enabled' => '1']],
            'flags' => ['trial_enabled' => '1'], 'numbers' => ['trial_days' => 5, 'reminder_days' => 7], 'texts' => [],
        ])->assertRedirect();
        $cfg = $this->getJson('/api/client/config')->json('config');
        $this->assertSame(99000, $cfg['plans'][0]['price']);
        $this->assertSame(5, $cfg['trial_days']);
    }

    public function test_bootstrap_admin_must_change_password(): void
    {
        $this->post('/adminarea/login', ['username' => 'admin', 'password' => 'not-the-bootstrap'])->assertSessionHasErrors();
    }
}
