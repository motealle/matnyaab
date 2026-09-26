<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Mail;
use Tests\TestCase;

class PasswordRecoveryTest extends TestCase
{
    private const USERNAME = 'matnyaab-ci-recovery@example.invalid';

    protected function tearDown(): void
    {
        User::query()->where('username', self::USERNAME)->delete();
        parent::tearDown();
    }

    private function user(): User
    {
        return User::query()->create([
            'password' => Hash::make('Old-Password-123!'),
            'last_login' => null,
            'is_superuser' => 0,
            'username' => self::USERNAME,
            'first_name' => 'CI Recovery',
            'last_name' => '',
            'email' => self::USERNAME,
            'is_staff' => 0,
            'is_active' => 1,
            'date_joined' => now()->format('Y-m-d H:i:s'),
            'phone_number' => '09999999995',
            'confirm_code' => null,
            'confirm_code_tried' => 0,
            'user_system_id' => 'ABCDEF0123456789ABCDEF0123456789',
            'has_user_system_id' => 1,
            'user_serial_number' => null,
            'is_phone_confirmed' => 1,
            'license_buy_time' => null,
            'license_end_time' => null,
            'new_order_id' => null,
            'subscription_id' => null,
        ]);
    }

    public function test_recovery_request_is_generic_and_uses_fake_mail(): void
    {
        $this->user();

        config([
            'mail.default' => 'smtp',
            'mail.mailers.smtp.host' => 'smtp.example.invalid',
            'mail.from.address' => 'noreply@example.invalid',
        ]);

        Mail::fake();

        $this->post('/restore_password', ['email' => self::USERNAME])
            ->assertRedirect('/restore_password_done');

        $this->post('/restore_password', ['email' => 'unknown@example.invalid'])
            ->assertRedirect('/restore_password_done');
    }

    public function test_valid_stateless_link_changes_password_and_invalidates_old_token(): void
    {
        $user = $this->user();
        $expires = now()->addMinutes(30)->timestamp;

        $token = hash_hmac(
            'sha256',
            $user->id.'|'.$expires.'|'.$user->password,
            (string) config('app.key')
        );

        $path = '/restore/'.$user->id.'/'.$expires.'/'.$token;

        $this->get($path)->assertOk();

        $this->post($path, [
            'password' => 'New-Password-456!',
            'password_confirmation' => 'New-Password-456!',
        ])->assertRedirect('/login');

        $user->refresh();
        $this->assertTrue(Hash::check('New-Password-456!', $user->password));

        // Password participates in the HMAC, therefore the previous link is single-use.
        $this->get($path)->assertForbidden();
    }

    public function test_expired_recovery_link_is_rejected(): void
    {
        $user = $this->user();
        $expires = now()->subMinute()->timestamp;

        $token = hash_hmac(
            'sha256',
            $user->id.'|'.$expires.'|'.$user->password,
            (string) config('app.key')
        );

        $this->get('/restore/'.$user->id.'/'.$expires.'/'.$token)
            ->assertForbidden();
    }
}
