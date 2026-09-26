<?php

namespace App\Http\Controllers;

use App\Models\User;
use App\Support\DjangoPassword;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\View\View;

class AuthController extends Controller
{
    public function showLogin(): View|RedirectResponse
    {
        if (Auth::check()) {
            return redirect()->route('profile');
        }

        return view('auth.login');
    }

    public function login(Request $request): RedirectResponse
    {
        $credentials = $request->validate([
            'username' => ['required', 'string', 'max:150'],
            'password' => ['required', 'string', 'max:500'],
        ]);

        $user = User::query()->where('username', $credentials['username'])->first();

        if (! $user || ! $user->is_active || ! DjangoPassword::verify($credentials['password'], $user->password)) {
            return back()
                ->withInput($request->only('username'))
                ->withErrors(['username' => 'نام کاربری یا رمز عبور صحیح نیست.']);
        }

        if (! $user->is_superuser && ! $user->is_phone_confirmed) {
            return back()
                ->withInput($request->only('username'))
                ->withErrors(['username' => 'شماره موبایل این حساب هنوز تأیید نشده است.']);
        }

        Auth::login($user);
        $request->session()->regenerate();

        $user->forceFill(['last_login' => now()->format('Y-m-d H:i:s')])->save();

        return redirect()->intended(route('profile'));
    }

    public function logout(Request $request): RedirectResponse
    {
        Auth::logout();
        $request->session()->invalidate();
        $request->session()->regenerateToken();

        return redirect()->route('login');
    }
}
