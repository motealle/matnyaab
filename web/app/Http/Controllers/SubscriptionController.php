<?php

namespace App\Http\Controllers;

use App\Models\LegacyGatewayTransaction;
use App\Models\Order;
use App\Models\OrderDiscount;
use App\Models\Subscription;
use App\Models\SubscriptionHistory;
use App\Models\User;
use App\Services\IdPayService;
use App\Services\SerialService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\DB;

class SubscriptionController extends Controller
{
    public function buySubscription(
        Request $request,
        SerialService $serials,
        IdPayService $idpay
    ): RedirectResponse {
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

        if ($amount > 0 && (string) config('services.idpay.merchant_code', '') === '') {
            return back()->withErrors([
                'payment' => 'درگاه پرداخت هنوز روی نسخه جدید پیکربندی نشده است.',
            ]);
        }

        $order = Order::query()->create([
            'wanted_subscription_id' => $subscription->id,
            'user_id' => $user->id,
            'order_discount_id' => $discount?->id,
            'is_complete' => 0,
            'order_amount' => $amount,
        ]);

        $user->forceFill(['new_order_id' => $order->id])->save();

        if ($amount <= 0) {
            DB::transaction(function () use ($user, $order, $serials): void {
                $this->activateSubscription($user, $order, $serials, '0', 0);
            });

            return redirect()->route('profile')->with('status', 'اشتراک شما با موفقیت فعال شد');
        }

        try {
            $payment = $idpay->createPayment(
                (int) $user->id,
                (int) $order->id,
                $amount * 10,
                (string) $user->phone_number,
            );

            return redirect()->away($payment['redirect_url']);
        } catch (\RuntimeException $e) {
            return back()->withErrors(['payment' => $e->getMessage()]);
        }
    }

    public function callbackGateway(
        Request $request,
        IdPayService $idpay,
        SerialService $serials
    ) {
        $trackingCode = trim((string) $request->input('res', ''));
        abort_if($trackingCode === '', 404);

        $transaction = LegacyGatewayTransaction::query()
            ->where('tracking_code', $trackingCode)
            ->firstOrFail();

        if (! $idpay->verify($transaction, $request->all())) {
            return response()->view('payments.result', [
                'success' => false,
                'trackingCode' => $trackingCode,
            ], 400);
        }

        $metadata = $transaction->metadata();
        $userId = (int) ($metadata['user_id'] ?? 0);
        $orderId = (int) ($metadata['order_id'] ?? 0);

        $user = User::query()->findOrFail($userId);
        $order = Order::query()->findOrFail($orderId);

        if (! $order->is_complete) {
            DB::transaction(function () use ($user, $order, $serials, $trackingCode): void {
                $this->activateSubscription(
                    $user,
                    $order,
                    $serials,
                    $trackingCode,
                    (int) $order->order_amount
                );
            });
        }

        if (Auth::id() === $user->id) {
            return redirect()->route('profile')->with('status', 'پرداخت با موفقیت انجام شد و اشتراک فعال شد.');
        }

        return response()->view('payments.result', [
            'success' => true,
            'trackingCode' => $trackingCode,
        ]);
    }

    private function activateSubscription(
        User $user,
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
