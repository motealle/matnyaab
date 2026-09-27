<?php

namespace App\Http\Controllers;

use App\Models\Subscription;
use App\Models\SubscriptionHistory;
use App\Services\LicensePolicyService;
use App\Services\SerialService;
use Illuminate\Support\Facades\Auth;
use Illuminate\View\View;

class ProfileController extends Controller
{
    public function __invoke(LicensePolicyService $licensePolicy, SerialService $serials): View
    {
        $user = Auth::user();
        $user->load('subscription');

        $subscriptions = Subscription::query()
            ->where('subscription_active', 1)
            ->orderBy('subscription_price')
            ->get();

        $history = SubscriptionHistory::query()
            ->where('user_id', $user->id)
            ->orderByDesc('subscription_buy_time')
            ->limit(20)
            ->get();

        $clientLicenses = $licensePolicy->clientLicenses($user, $serials);

        return view('profile', [
            'user' => $user,
            'subscriptions' => $subscriptions,
            'history' => $history,
            'clientLicenses' => $clientLicenses,
            'isSubscriptionEnded' => ! $clientLicenses['bypassed']
                && ($user->license_end_time === null || now()->greaterThan($user->license_end_time)),
        ]);
    }
}
