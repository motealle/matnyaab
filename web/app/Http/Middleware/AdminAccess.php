<?php

namespace App\Http\Middleware;

use App\Services\AdminAuth;
use Closure;
use Illuminate\Http\Request;

class AdminAccess
{
    public function handle(Request $request, Closure $next)
    {
        if (! AdminAuth::check()) {
            abort_if($request->user() !== null, 403);

            return redirect()->route('admin.login');
        }
        if (AdminAuth::mustChange() && ! $request->routeIs('admin.account*', 'admin.logout')) {
            return redirect()->route('admin.account')->with('status', 'برای امنیت، لطفاً همین حالا رمز اولیه را تغییر دهید.');
        }

        return $next($request);
    }
}
