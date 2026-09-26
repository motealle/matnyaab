<?php

namespace Tests\Feature;

use App\Models\User;
use App\Support\DjangoPassword;
use Illuminate\Support\Facades\DB;
use Tests\TestCase;

class LegacyAuthTest extends TestCase
{
    private const USERNAME = 'matnyaab-ci-user';
    private const PASSWORD = 'Matnyaab-CI-Password!';
    private const HASH = 'pbkdf2_sha256$390000$matnyaab-ci-salt$m9/bESSqUVkV3GNHFe6VbwJMOOrKeYPayOCXMsoIlUc=';

    protected function tearDown(): void
    {
        DB::table('users_accountmodel')->where('username', self::USERNAME)->delete();
        parent::tearDown();
    }

    public function test_django_pbkdf2_password_verification(): void
    {
        $this->assertTrue(DjangoPassword::verify(self::PASSWORD, self::HASH));
        $this->assertFalse(DjangoPassword::verify('wrong-password', self::HASH));
    }

    public function test_existing_style_django_user_can_login_and_open_profile(): void
    {
        DB::table('users_accountmodel')->where('username', self::USERNAME)->delete();

        $id = DB::table('users_accountmodel')->insertGetId([
            'password' => self::HASH,
            'last_login' => null,
            'is_superuser' => 0,
            'username' => self::USERNAME,
            'first_name' => 'CI',
            'last_name' => 'User',
            'email' => 'ci@example.invalid',
            'is_staff' => 0,
            'is_active' => 1,
            'date_joined' => now()->format('Y-m-d H:i:s'),
            'phone_number' => '09999999999',
            'confirm_code' => null,
            'confirm_code_tried' => 0,
            'user_system_id' => 'ci-system-id',
            'has_user_system_id' => 1,
            'user_serial_number' => null,
            'is_phone_confirmed' => 1,
            'license_buy_time' => null,
            'license_end_time' => null,
            'new_order_id' => null,
            'subscription_id' => null,
        ]);

        $response = $this->post('/login', [
            'username' => self::USERNAME,
            'password' => self::PASSWORD,
        ]);

        $response->assertRedirect('/profile');
        $this->assertAuthenticatedAs(User::query()->findOrFail($id));

        $this->get('/profile')
            ->assertOk()
            ->assertSee(self::USERNAME);
    }
}
