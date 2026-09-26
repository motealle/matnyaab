<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Http;
use Tests\TestCase;

class LegacyRegistrationTest extends TestCase
{
    private const EMAIL = 'matnyaab-ci-register@example.invalid';
    private const PHONE = '09999999996';
    private const SYSTEM_ID = '0123456789ABCDEF0123456789ABCDEF';
    private const PASSWORD = 'Registration-CI!';

    protected function tearDown(): void
    {
        User::query()
            ->where('username', self::EMAIL)
            ->orWhere('phone_number', self::PHONE)
            ->orWhere('user_system_id', self::SYSTEM_ID)
            ->delete();

        parent::tearDown();
    }

    private function configureSms(): void
    {
        config([
            'services.sms.production_enabled' => true,
            'services.sms.url' => 'http://tsms.test/url/tsmshttp.php',
            'services.sms.username' => 'ci-user',
            'services.sms.password' => 'ci-password',
            'services.sms.number' => '300000',
        ]);

        Http::fake([
            'http://tsms.test/*' => Http::response('1', 200),
        ]);
    }

    public function test_registration_sends_sms_and_confirms_legacy_sqlite_user(): void
    {
        $this->configureSms();

        $register = $this->post('/register', [
            'username' => self::EMAIL,
            'first_name' => 'کاربر تست',
            'phone_number' => self::PHONE,
            'user_system_id' => self::SYSTEM_ID,
            'password' => self::PASSWORD,
        ]);

        $register->assertRedirect('/smsconfirm');

        $user = User::query()->where('username', self::EMAIL)->firstOrFail();
        $this->assertFalse((bool) $user->is_phone_confirmed);
        $this->assertSame(4, strlen((string) $user->confirm_code));
        $this->assertTrue(Hash::check(self::PASSWORD, $user->password));
        $this->assertAuthenticatedAs($user);

        Http::assertSent(function ($request) use ($user): bool {
            parse_str((string) parse_url($request->url(), PHP_URL_QUERY), $query);

            return ($query['to'] ?? null) === self::PHONE
                && str_contains((string) ($query['message'] ?? ''), (string) $user->confirm_code);
        });

        $confirm = $this->post('/smsconfirm', [
            'confirm_code' => $user->confirm_code,
        ]);

        $confirm->assertRedirect('/profile');

        $user->refresh();
        $this->assertTrue((bool) $user->is_phone_confirmed);
        $this->assertNull($user->confirm_code);
        $this->assertSame(0, (int) $user->confirm_code_tried);
    }

    public function test_missing_sms_configuration_does_not_create_user(): void
    {
        config([
            'services.sms.production_enabled' => true,
            'services.sms.url' => null,
            'services.sms.username' => null,
            'services.sms.password' => null,
            'services.sms.number' => null,
        ]);

        $response = $this->from('/register')->post('/register', [
            'username' => self::EMAIL,
            'first_name' => 'کاربر تست',
            'phone_number' => self::PHONE,
            'user_system_id' => self::SYSTEM_ID,
            'password' => self::PASSWORD,
        ]);

        $response->assertRedirect('/register');
        $response->assertSessionHasErrors('register');
        $this->assertDatabaseMissing('users_accountmodel', ['username' => self::EMAIL]);
    }

    public function test_third_wrong_sms_code_deletes_unconfirmed_account(): void
    {
        $this->configureSms();

        $this->post('/register', [
            'username' => self::EMAIL,
            'first_name' => 'کاربر تست',
            'phone_number' => self::PHONE,
            'user_system_id' => self::SYSTEM_ID,
            'password' => self::PASSWORD,
        ])->assertRedirect('/smsconfirm');

        for ($attempt = 1; $attempt <= 2; $attempt++) {
            $this->from('/smsconfirm')
                ->post('/smsconfirm', ['confirm_code' => '0000'])
                ->assertRedirect('/smsconfirm');
        }

        $third = $this->post('/smsconfirm', ['confirm_code' => '0000']);
        $third->assertRedirect('/register');

        $this->assertGuest();
        $this->assertDatabaseMissing('users_accountmodel', ['username' => self::EMAIL]);
    }
}
