<?php

namespace Tests\Feature;

use App\Models\LegacyGatewayTransaction;
use App\Models\Subscription;
use App\Models\SubscriptionHistory;
use App\Models\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Http;
use Tests\TestCase;

class LegacyPaymentTest extends TestCase
{
    private const USERNAME = 'matnyaab-ci-pay-user';
    private const GATEWAY_ID = 'ci-gateway-id';

    protected function tearDown(): void
    {
        $user = DB::table('users_accountmodel')->where('username', self::USERNAME)->first();

        if ($user) {
            DB::table('users_subscriptionhistorymodel')->where('user_id', $user->id)->delete();
            DB::table('users_accountmodel')->where('id', $user->id)->update(['new_order_id' => null]);
            DB::table('users_ordermodel')->where('user_id', $user->id)->delete();
            DB::table('users_accountmodel')->where('id', $user->id)->delete();
        }

        DB::table('azbankgateways_bank')->where('reference_number', self::GATEWAY_ID)->delete();

        parent::tearDown();
    }

    public function test_idpay_create_and_verify_complete_legacy_sqlite_order(): void
    {
        $subscription = Subscription::query()
            ->where('subscription_active', 1)
            ->where('subscription_price', '>', 0)
            ->firstOrFail();

        $id = DB::table('users_accountmodel')->insertGetId([
            'password' => '!',
            'last_login' => null,
            'is_superuser' => 0,
            'username' => self::USERNAME,
            'first_name' => 'CI',
            'last_name' => 'Payment',
            'email' => 'ci-pay@example.invalid',
            'is_staff' => 0,
            'is_active' => 1,
            'date_joined' => now()->format('Y-m-d H:i:s'),
            'phone_number' => '09999999997',
            'confirm_code' => null,
            'confirm_code_tried' => 0,
            'user_system_id' => 'CI-SYSTEM-PAY',
            'has_user_system_id' => 1,
            'user_serial_number' => null,
            'is_phone_confirmed' => 1,
            'license_buy_time' => null,
            'license_end_time' => null,
            'new_order_id' => null,
            'subscription_id' => null,
        ]);

        $user = User::query()->findOrFail($id);
        $amountRial = ((int) $subscription->subscription_price) * 10;

        config([
            'services.idpay.merchant_code' => 'ci-test-key',
            'services.idpay.sandbox' => 1,
        ]);

        Http::fake([
            'https://api.idpay.ir/v1.1/payment' => Http::response([
                'id' => self::GATEWAY_ID,
                'link' => 'https://idpay.test/pay/' . self::GATEWAY_ID,
            ], 201),
            'https://api.idpay.ir/v1.1/payment/verify' => Http::response([
                'status' => 100,
                'amount' => $amountRial,
            ], 200),
        ]);

        $purchase = $this->actingAs($user)->post('/buysubscription', [
            'subscription_id' => $subscription->id,
            'coupon_code' => '',
        ]);

        $purchase->assertRedirect('https://idpay.test/pay/' . self::GATEWAY_ID);

        $transaction = LegacyGatewayTransaction::query()
            ->where('reference_number', self::GATEWAY_ID)
            ->firstOrFail();

        $this->assertSame('redirected', $transaction->status);
        $this->assertSame($amountRial, (int) $transaction->amount);

        $callback = $this->post('/callback-gateway?res=' . urlencode($transaction->tracking_code), [
            'status' => 10,
            'id' => self::GATEWAY_ID,
        ]);

        $callback->assertRedirect('/profile');

        $user->refresh();
        $this->assertSame($subscription->id, $user->subscription_id);
        $this->assertNotEmpty($user->user_serial_number);

        $order = DB::table('users_ordermodel')
            ->where('user_id', $user->id)
            ->latest('id')
            ->first();

        $this->assertNotNull($order);
        $this->assertSame(1, (int) $order->is_complete);

        $history = SubscriptionHistory::query()
            ->where('user_id', $user->id)
            ->latest('id')
            ->firstOrFail();

        $this->assertSame((int) $subscription->subscription_price, $history->amount_paid);

        $transaction->refresh();
        $this->assertSame('success', $transaction->status);

        Http::assertSentCount(2);
    }
}
