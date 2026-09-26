<?php

namespace Tests\Feature;

use App\Models\User;
use App\Support\DjangoPassword;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class LegacyPasswordChangeTest extends TestCase
{
    private const USERNAME = 'matnyaab-ci-password-user';
    private const OLD_PASSWORD = 'Old-CI-Password!';
    private const OLD_HASH = 'pbkdf2_sha256$390000$matnyaab-ci-salt$m9/bESSqUVkV3GNHFe6VbwJMOOrKeYPayOCXMsoIlUc=';

    protected function tearDown(): void
    {
        DB::table('users_accountmodel')->where('username', self::USERNAME)->delete();
        parent::tearDown();
    }

    public function test_django_password_can_be_replaced_by_new_laravel_hash(): void
    {
        $id = DB::table('users_accountmodel')->insertGetId([
            'password' => self::OLD_HASH,
            'last_login' => null,
            'is_superuser' => 0,
            'username' => self::USERNAME,
            'first_name' => 'CI',
            'last_name' => 'Password',
            'email' => 'ci-password@example.invalid',
            'is_staff' => 0,
            'is_active' => 1,
            'date_joined' => now()->format('Y-m-d H:i:s'),
            'phone_number' => '09999999995',
            'confirm_code' => null,
            'confirm_code_tried' => 0,
            'user_system_id' => 'CI-PASSWORD-SYSTEM-ID-1234567890',
            'has_user_system_id' => 1,
            'user_serial_number' => null,
            'is_phone_confirmed' => 1,
            'license_buy_time' => null,
            'license_end_time' => null,
            'new_order_id' => null,
            'subscription_id' => null,
        ]);

        $user = User::query()->findOrFail($id);
        $this->assertTrue(DjangoPassword::verify(self::OLD_PASSWORD, $user->password));

        $this->actingAs($user)
            ->post('/change_password', [
                'current_password' => self::OLD_PASSWORD,
                'password' => 'New-CI-Password!123',
                'password_confirmation' => 'New-CI-Password!123',
            ])
            ->assertRedirect('/profile');

        $user->refresh();
        $this->assertTrue(Hash::check('New-CI-Password!123', $user->password));
        $this->assertFalse(DjangoPassword::verify(self::OLD_PASSWORD, $user->password));
    }
}
