<?php

namespace App\Http\Controllers;

use App\Models\User;
use App\Services\SmsService;
use Illuminate\Database\QueryException;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\View\View;

class RegistrationController extends Controller
{
    public function showRegister(): View|RedirectResponse
    {
        if (Auth::check()) {
            return redirect()->route(Auth::user()->is_phone_confirmed ? 'profile' : 'smsconfirm');
        }

        return view('auth.register');
    }

    public function register(Request $request, SmsService $sms): RedirectResponse
    {
        if (Auth::check()) {
            return redirect()->route(Auth::user()->is_phone_confirmed ? 'profile' : 'smsconfirm');
        }

        $data = $request->validate([
            'username' => ['required', 'email', 'max:50'],
            'first_name' => ['required', 'string', 'max:100'],
            'phone_number' => ['required', 'regex:/^09[0-9]{9}$/'],
            'user_system_id' => ['required', 'string', 'size:32'],
            'password' => ['required', 'string', 'min:8', 'max:20'],
        ]);

        $duplicateQuery = User::query()->where(function ($query) use ($data): void {
            $query->where('username', $data['username'])
                ->orWhere('phone_number', $data['phone_number'])
                ->orWhere('user_system_id', $data['user_system_id']);
        });

        $duplicates = $duplicateQuery->get();
        foreach ($duplicates as $existing) {
            if ($existing->is_superuser || $existing->is_phone_confirmed) {
                $message = match (true) {
                    $existing->username === $data['username'] => 'کاربری با این ایمیل قبلاً ثبت و تأیید شده است.',
                    $existing->phone_number === $data['phone_number'] => 'کاربری با این شماره موبایل قبلاً ثبت و تأیید شده است.',
                    default => 'این شناسه سیستمی قبلاً ثبت شده است.',
                };

                return back()
                    ->withInput($request->except('password'))
                    ->withErrors(['register' => $message]);
            }
        }

        $confirmCode = (string) random_int(1000, 9999);

        try {
            // Send first: if the provider is unavailable we do not create a dangling account.
            $sms->sendConfirmationCode($data['phone_number'], $confirmCode);
        } catch (\RuntimeException $e) {
            return back()
                ->withInput($request->except('password'))
                ->withErrors(['register' => $e->getMessage()]);
        }

        try {
            $user = DB::transaction(function () use ($data, $duplicates, $confirmCode): User {
                foreach ($duplicates as $existing) {
                    if (! $existing->is_superuser && ! $existing->is_phone_confirmed) {
                        $existing->delete();
                    }
                }

                return User::query()->create([
                    'password' => Hash::make($data['password']),
                    'last_login' => null,
                    'is_superuser' => 0,
                    'username' => $data['username'],
                    'first_name' => $data['first_name'],
                    'last_name' => '',
                    'email' => $data['username'],
                    'is_staff' => 0,
                    'is_active' => 1,
                    'date_joined' => now()->format('Y-m-d H:i:s'),
                    'phone_number' => $data['phone_number'],
                    'confirm_code' => $confirmCode,
                    'confirm_code_tried' => 0,
                    'user_system_id' => $data['user_system_id'],
                    'has_user_system_id' => 1,
                    'user_serial_number' => null,
                    'is_phone_confirmed' => 0,
                    'license_buy_time' => null,
                    'license_end_time' => null,
                    'new_order_id' => null,
                    'subscription_id' => null,
                ]);
            });
        } catch (QueryException) {
            return back()
                ->withInput($request->except('password'))
                ->withErrors(['register' => 'این ایمیل، شماره موبایل یا شناسه سیستمی هم‌اکنون ثبت شده است.']);
        }

        Auth::login($user);
        $request->session()->regenerate();

        return redirect()->route('smsconfirm');
    }

    public function showSmsConfirm(Request $request): View|RedirectResponse
    {
        if ($request->user()->is_phone_confirmed) {
            return redirect()->route('profile');
        }

        return view('auth.smsconfirm');
    }

    public function smsConfirm(Request $request): RedirectResponse
    {
        $data = $request->validate([
            'confirm_code' => ['required', 'digits:4'],
        ]);

        /** @var User $user */
        $user = $request->user();

        if (hash_equals((string) $user->confirm_code, (string) $data['confirm_code'])) {
            $user->forceFill([
                'is_phone_confirmed' => 1,
                'confirm_code' => null,
                'confirm_code_tried' => 0,
            ])->save();

            return redirect()->route('profile')->with('status', 'شماره موبایل با موفقیت تأیید شد.');
        }

        $tried = (int) ($user->confirm_code_tried ?? 0);

        if ($tried >= 2) {
            Auth::logout();
            $request->session()->invalidate();
            $request->session()->regenerateToken();
            $user->delete();

            return redirect()->route('register')
                ->withErrors(['confirm_code' => 'کد منقضی شد. ثبت‌نام را دوباره انجام دهید.']);
        }

        $user->forceFill(['confirm_code_tried' => $tried + 1])->save();

        return back()->withErrors(['confirm_code' => 'کد تأیید اشتباه است.']);
    }
}
