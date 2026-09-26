<?php

namespace App\Http\Controllers;

use App\Models\Order;
use App\Models\OrderDiscount;
use App\Models\Subscription;
use App\Models\SubscriptionHistory;
use App\Services\SerialService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class SubscriptionController extends Controller
{
    public function buySubscription(Request $request, SerialService $serials): RedirectResponse
    {
        $data = $request->validate([
            'subscription_id' => ['required', 'integer', 'exists:users_subscriptionmodel,id'],
            'coupon_code' => ['nullable', 'string', 'max:500'],
        ]);

        $user = $request->user();
        $subscription = Subscription::query()
            ->where('subscription_active', 1)
            ->findOrFail($data['subscription_id']);

        $discount = null;
        $amount = (int) $subscription->subscription_price;

        if (! empty($data['coupon_code'])) {
            $discount = OrderDiscount::query()
                ->where('discount_code', $data['coupon_code'])
                ->first();

            if (! $discount || ! $discount->isValidNow()) {
                return back()->withErrors(['coupon_code' => 'کد تخفیف اشتباه یا نامعتبر است']);
            }

            $amount = (int) ($amount * (1 - ((float) $discount->discount_rate / 100)));
        }

        $order = Order::query()->create([
            'wanted_subscription_id' => $subscription->id,
            'user_id' => $user->id,
            'order_discount_id' => $discount?->id,
            'is_complete' => 0,
            'order_amount' => $amount,
        ]);

        $user->forceFill(['new_order_id' => $order->id])->save();

        if ($amount > 0) {
            return back()->withErrors([
                'payment' => 'درگاه پرداخت در مرحله بعدی مهاجرت فعال می‌شود؛ سفارش شما بدون پرداخت تکمیل نشده است.',
            ]);
        }

        DB::transaction(function () use ($user, $order, $serials): void {
            $this->activateSubscription($user, $order, $serials, '0', 0);
        });

        return redirect()->route('profile')->with('status', 'اشتراک شما با موفقیت فعال شد');
    }

    private function activateSubscription(
        $user,
        Order $order,
        SerialService $serials,
        string $trackingCode,
        int $amountPaid
    ): void {
        $subscription = $order->wantedSubscription()->firstOrFail();
        $buyTime = now();
        $endTime = $buyTime->copy()->addDays((int) $subscription->subscription_duration_days);

        $serial = $serials->generateSerial(
            (string) $user->user_system_id,
            $buyTime->timestamp,
            $endTime->timestamp
        );

        $user->forceFill([
            'subscription_id' => $subscription->id,
            'user_serial_number' => $serial,
            'license_buy_time' => $buyTime->format('Y-m-d H:i:s'),
            'license_end_time' => $endTime->format('Y-m-d H:i:s'),
        ])->save();

        SubscriptionHistory::query()->create([
            'subscription_id' => $subscription->id,
            'user_id' => $user->id,
            'subscription_buy_time' => $buyTime->format('Y-m-d H:i:s'),
            'order_discount_id' => $order->order_discount_id,
            'tracking_code' => $trackingCode,
            'amount_paid' => $amountPaid,
            'user_serial_number' => $serial,
        ]);

        $order->forceFill(['is_complete' => 1])->save();
    }
}
