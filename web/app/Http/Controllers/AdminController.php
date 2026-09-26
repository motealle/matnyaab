<?php

namespace App\Http\Controllers;

use App\Models\Subscription;
use App\Models\SubscriptionHistory;
use App\Models\User;
use App\Services\SerialService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\View\View;

class AdminController extends Controller
{
    public function index(Request $request): View
    {
        $this->authorizeSuperuser($request);

        $query = trim((string) $request->query('q', ''));

        $users = User::query()
            ->when($query !== '', function ($builder) use ($query): void {
                $builder->where(function ($q) use ($query): void {
                    $q->where('username', 'like', '%'.$query.'%')
                        ->orWhere('first_name', 'like', '%'.$query.'%')
                        ->orWhere('last_name', 'like', '%'.$query.'%')
                        ->orWhere('phone_number', 'like', '%'.$query.'%')
                        ->orWhere('user_system_id', 'like', '%'.$query.'%');
                });
            })
            ->orderByDesc('id')
            ->limit(50)
            ->get();

        return view('admin.dashboard', [
            'query' => $query,
            'users' => $users,
            'subscriptions' => Subscription::query()->orderBy('subscription_price')->get(),
            'counts' => [
                'users' => User::query()->count(),
                'confirmed' => User::query()->where('is_phone_confirmed', 1)->count(),
                'histories' => SubscriptionHistory::query()->count(),
            ],
        ]);
    }

    public function gift(Request $request, SerialService $serials): RedirectResponse
    {
        $this->authorizeSuperuser($request);

        $data = $request->validate([
            'user_id' => ['required', 'integer', 'exists:users_accountmodel,id'],
            'subscription_id' => ['required', 'integer', 'exists:users_subscriptionmodel,id'],
        ]);

        $user = User::query()->findOrFail($data['user_id']);
        $subscription = Subscription::query()->findOrFail($data['subscription_id']);

        DB::transaction(function () use ($user, $subscription, $serials): void {
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
                'order_discount_id' => null,
                'tracking_code' => 'admin-gift-'.$buyTime->timestamp,
                'amount_paid' => 0,
                'user_serial_number' => $serial,
            ]);
        });

        return redirect()
            ->route('admin.dashboard', ['q' => $user->username])
            ->with('status', 'اشتراک برای کاربر فعال شد و سریال جدید صادر شد.');
    }

    private function authorizeSuperuser(Request $request): void
    {
        abort_unless($request->user()?->is_superuser, 403);
    }
}
