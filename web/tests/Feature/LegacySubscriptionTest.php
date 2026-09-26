<?php

namespace Tests\Feature;

use App\Models\Subscription;
use App\Models\SubscriptionHistory;
use App\Models\User;
use App\Services\SerialService;
use Illuminate\Support\Facades\DB;
use Tests\TestCase;

class LegacySubscriptionTest extends TestCase
{
    private const USERNAME = 'matnyaab-ci-sub-user';

    protected function tearDown(): void
    {
        $user = DB::table('users_accountmodel')->where('username', self::USERNAME)->first();

        if ($user) {
            DB::table('users_subscriptionhistorymodel')->where('user_id', $user->id)->delete();
            DB::table('users_accountmodel')->where('id', $user->id)->update(['new_order_id' => null]);
            DB::table('users_ordermodel')->where('user_id', $user->id)->delete();
            DB::table('users_accountmodel')->where('id', $user->id)->delete();
        }

        DB::table('orderdiscount_orderdiscountmodel')->where('discount_code', 'MATNYAAB-CI-FREE')->delete();

        parent::tearDown();
    }

    public function test_100_percent_coupon_activates_legacy_subscription_and_generates_compatible_serial(): void
    {
        $subscription = Subscription::query()->where('subscription_active', 1)->firstOrFail();

        DB::table('orderdiscount_orderdiscountmodel')->insert([
            'discount_title' => 'CI free coupon',
            'discount_date_start' => now()->subDay()->format('Y-m-d H:i:s'),
            'discount_date_end' => now()->addDay()->format('Y-m-d H:i:s'),
            'discount_rate' => 100,
            'discount_code' => 'MATNYAAB-CI-FREE',
        ]);

        $id = DB::table('users_accountmodel')->insertGetId([
            'password' => '!',
            'last_login' => null,
            'is_superuser' => 0,
            'username' => self::USERNAME,
            'first_name' => 'CI',
            'last_name' => 'Subscription',
            'email' => 'ci-sub@example.invalid',
            'is_staff' => 0,
            'is_active' => 1,
            'date_joined' => now()->format('Y-m-d H:i:s'),
            'phone_number' => '09999999998',
            'confirm_code' => null,
            'confirm_code_tried' => 0,
            'user_system_id' => 'CI-SYSTEM-SUB',
            'has_user_system_id' => 1,
            'user_serial_number' => null,
            'is_phone_confirmed' => 1,
            'license_buy_time' => null,
            'license_end_time' => null,
            'new_order_id' => null,
            'subscription_id' => null,
        ]);

        $user = User::query()->findOrFail($id);

        $response = $this->actingAs($user)->post('/buysubscription', [
            'subscription_id' => $subscription->id,
            'coupon_code' => 'MATNYAAB-CI-FREE',
        ]);

        $response->assertRedirect('/profile');

        $user->refresh();
        $this->assertSame($subscription->id, $user->subscription_id);
        $this->assertNotEmpty($user->user_serial_number);
        $this->assertNotNull($user->license_buy_time);
        $this->assertNotNull($user->license_end_time);

        $decoded = app(SerialService::class)->decrypt($user->user_serial_number);
        $this->assertStringStartsWith('CI-SYSTEM-SUB', $decoded);

        $history = SubscriptionHistory::query()->where('user_id', $user->id)->firstOrFail();
        $this->assertSame(0, $history->amount_paid);

        $order = DB::table('users_ordermodel')->where('user_id', $user->id)->latest('id')->first();
        $this->assertNotNull($order);
        $this->assertSame(1, (int) $order->is_complete);
    }
}
