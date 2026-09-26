<?php

namespace Tests\Feature;

use App\Models\Subscription;
use App\Models\SubscriptionHistory;
use App\Models\User;
use App\Services\SerialService;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class AdminDashboardTest extends TestCase
{
    private const ADMIN = 'matnyaab-ci-admin@example.invalid';
    private const TARGET = 'matnyaab-ci-target@example.invalid';
    private const NORMAL = 'matnyaab-ci-normal@example.invalid';

    protected function tearDown(): void
    {
        foreach ([self::TARGET, self::ADMIN, self::NORMAL] as $username) {
            $user = User::query()->where('username', $username)->first();
            if (! $user) {
                continue;
            }

            DB::table('users_subscriptionhistorymodel')->where('user_id', $user->id)->delete();
            DB::table('users_accountmodel')->where('id', $user->id)->update(['new_order_id' => null]);
            DB::table('users_ordermodel')->where('user_id', $user->id)->delete();
            DB::table('users_accountmodel')->where('id', $user->id)->delete();
        }

        parent::tearDown();
    }

    private function makeUser(string $username, string $phone, bool $superuser = false): User
    {
        User::query()
            ->where('username', $username)
            ->orWhere('phone_number', $phone)
            ->delete();

        $systemId = strtoupper(md5('matnyaab-ci-admin-test|'.$username));

        return User::query()->create([
            'password' => Hash::make('Admin-CI-Password!'),
            'last_login' => null,
            'is_superuser' => $superuser ? 1 : 0,
            'username' => $username,
            'first_name' => 'CI',
            'last_name' => $superuser ? 'Admin' : 'User',
            'email' => $username,
            'is_staff' => $superuser ? 1 : 0,
            'is_active' => 1,
            'date_joined' => now()->format('Y-m-d H:i:s'),
            'phone_number' => $phone,
            'confirm_code' => null,
            'confirm_code_tried' => 0,
            'user_system_id' => $systemId,
            'has_user_system_id' => 1,
            'user_serial_number' => null,
            'is_phone_confirmed' => 1,
            'license_buy_time' => null,
            'license_end_time' => null,
            'new_order_id' => null,
            'subscription_id' => null,
        ]);
    }

    public function test_non_superuser_cannot_open_admin_dashboard(): void
    {
        $normal = $this->makeUser(
            self::NORMAL,
            '09999999994'
        );

        $this->actingAs($normal)
            ->get('/adminarea')
            ->assertForbidden();
    }

    public function test_superuser_can_search_and_gift_subscription(): void
    {
        $admin = $this->makeUser(
            self::ADMIN,
            '09999999993',
            true
        );

        $target = $this->makeUser(
            self::TARGET,
            '09999999992'
        );

        $subscription = Subscription::query()->firstOrFail();

        $this->actingAs($admin)
            ->get('/adminarea?q='.urlencode(self::TARGET))
            ->assertOk()
            ->assertSee(self::TARGET);

        $this->actingAs($admin)
            ->post('/adminarea/gift', [
                'user_id' => $target->id,
                'subscription_id' => $subscription->id,
            ])
            ->assertRedirect();

        $target->refresh();
        $this->assertSame($subscription->id, $target->subscription_id);
        $this->assertNotEmpty($target->user_serial_number);

        $decoded = app(SerialService::class)->decrypt($target->user_serial_number);
        $this->assertStringStartsWith($target->user_system_id, $decoded);

        $history = SubscriptionHistory::query()
            ->where('user_id', $target->id)
            ->latest('id')
            ->firstOrFail();

        $this->assertSame(0, $history->amount_paid);
        $this->assertStringStartsWith('admin-gift-', $history->tracking_code);
    }
}
