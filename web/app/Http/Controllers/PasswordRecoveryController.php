<?php

namespace App\Http\Controllers;

use App\Models\User;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Mail;
use Illuminate\View\View;

class PasswordRecoveryController extends Controller
{
    public function showRequest(): View|RedirectResponse
    {
        if (auth()->check()) {
            return redirect()->route('profile');
        }

        return view('auth.restore-password', [
            'mailReady' => $this->mailReady(),
        ]);
    }

    public function send(Request $request): RedirectResponse
    {
        $data = $request->validate([
            'email' => ['required', 'email', 'max:150'],
        ]);

        if (! $this->mailReady()) {
            return back()
                ->withInput()
                ->withErrors(['email' => 'بازیابی ایمیلی هنوز روی سرور جدید فعال نشده است.']);
        }

        $user = User::query()
            ->where('username', $data['email'])
            ->first();

        // Do not expose whether an account exists.
        if ($user) {
            $expires = now()->addMinutes(30)->timestamp;
            $token = $this->tokenFor($user, $expires);
            $url = route('password.restore.confirm', [
                'user' => $user->id,
                'expires' => $expires,
                'token' => $token,
            ]);

            Mail::raw(
                "برای تعیین رمز عبور جدید متن‌یاب، تا ۳۰ دقیقه آینده این لینک را باز کنید:\n\n{$url}\n\nاگر این درخواست از طرف شما نبوده، این پیام را نادیده بگیرید.",
                function ($message) use ($user): void {
                    $message
                        ->to($user->username)
                        ->subject('بازیابی گذرواژه متن‌یاب');
                }
            );
        }

        return redirect()
            ->route('password.restore.done')
            ->with('restore_email', $data['email']);
    }

    public function done(): View
    {
        return view('auth.restore-password-done');
    }

    public function showConfirm(int $user, int $expires, string $token): View
    {
        $account = User::query()->findOrFail($user);
        abort_unless($this->validToken($account, $expires, $token), 403);

        return view('auth.restore-password-confirm', [
            'account' => $account,
            'expires' => $expires,
            'token' => $token,
        ]);
    }

    public function confirm(Request $request, int $user, int $expires, string $token): RedirectResponse
    {
        $account = User::query()->findOrFail($user);
        abort_unless($this->validToken($account, $expires, $token), 403);

        $data = $request->validate([
            'password' => ['required', 'string', 'min:8', 'max:72', 'confirmed'],
        ]);

        $account->forceFill([
            'password' => Hash::make($data['password']),
        ])->save();

        return redirect()
            ->route('login')
            ->with('status', 'گذرواژه تغییر کرد. اکنون می‌توانید با رمز جدید وارد شوید.');
    }

    private function validToken(User $user, int $expires, string $token): bool
    {
        return $expires >= now()->timestamp
            && hash_equals($this->tokenFor($user, $expires), $token);
    }

    private function tokenFor(User $user, int $expires): string
    {
        return hash_hmac(
            'sha256',
            $user->id.'|'.$expires.'|'.$user->password,
            (string) config('app.key')
        );
    }

    private function mailReady(): bool
    {
        $mailer = (string) config('mail.default', '');

        if ($mailer === '' || $mailer === 'log' || $mailer === 'array') {
            return false;
        }

        if ($mailer === 'smtp') {
            return filled(config('mail.mailers.smtp.host'))
                && filled(config('mail.from.address'));
        }

        return filled(config('mail.from.address'));
    }
}
