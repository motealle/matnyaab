<?php

namespace App\Http\Controllers;

use App\Models\Statistic;
use App\Services\AdminAuth;
use App\Services\MessageStore;
use App\Services\SiteSettings;
use App\Services\SubscriptionStore;
use App\Services\SupportReportService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;

/** پنل مدیریت: داشبورد مالی، اشتراک‌ها، پیام‌ها، پیام به برنامه، تنظیمات و فیچرفلگ‌ها، حساب مدیر */
class AdminPanelController extends Controller
{
    public const TABS = [
        'overview' => 'داشبورد',
        'subscriptions' => 'اشتراک‌ها',
        'inbox' => 'پیام‌ها',
        'notices' => 'پیام به برنامه',
        'settings' => 'تنظیمات',
        'account' => 'حساب مدیر',
    ];

    public function __construct(private SiteSettings $settings, private SubscriptionStore $subs, private MessageStore $messages) {}

    public function index(Request $request, AdminController $legacy, \App\Services\LicensePolicyService $policy, SupportReportService $reports)
    {
        if ($request->filled('q') || $request->query('tab') === 'users') {
            return app()->call([$legacy, 'index']);
        }
        $tab = array_key_exists($request->query('tab'), self::TABS) ? $request->query('tab') : 'overview';
        $views = array_map(fn ($s) => $this->subs->view($s), array_values($this->subs->all()));
        usort($views, fn ($a, $b) => strcmp($a['end'], $b['end']));
        $filter = (string) $request->query('state', '');
        $search = trim((string) $request->query('s', ''));
        $list = array_values(array_filter($views, function ($v) use ($filter, $search) {
            if ($filter !== '' && $v['state'] !== $filter) { return false; }
            if ($search === '') { return true; }
            $hay = mb_strtolower($v['code'].' '.$v['name'].' '.$v['contact']);

            return str_contains($hay, mb_strtolower($search));
        }));
        $inbox = $this->messages->inbox();

        $legacyActive = 0;
        try {
            $legacyActive = Statistic::query()->where('action_datetime', '>=', now()->subDays(7)->format('Y-m-d H:i:s'))->distinct()->count('user_system_id');
        } catch (\Throwable) {
        }

        return view('admin.panel', [
            'tab' => $tab,
            'tabs' => self::TABS,
            'stats' => $this->subs->stats(),
            'installs' => $this->messages->installStats(),
            'legacyActive' => $legacyActive,
            'followUp' => array_values(array_filter($views, fn ($v) => in_array($v['state'], ['expiring', 'expired'], true) && $v['days_left'] >= -60)),
            'list' => $list,
            'inbox' => $inbox,
            'inboxNew' => count(array_filter($inbox, fn ($m) => ($m['status'] ?? '') === 'new')),
            'notices' => $this->messages->notices(),
            'settings' => $this->settings->all(),
            'reports' => $reports->latest(30),
            'admin' => AdminAuth::credentials()['username'],
        ]);
    }

    public function subscriptionAction(Request $request): RedirectResponse
    {
        $data = $request->validate(['code' => ['required', 'string'], 'action' => ['required', 'in:contacted,cancel,reactivate,extend,note,delete'], 'days' => ['nullable', 'integer'], 'notes' => ['nullable', 'string', 'max:1000']]);
        $this->subs->adminAction($data['code'], $data['action'], $data);

        return back()->with('status', 'انجام شد.');
    }

    public function subscriptionCreate(Request $request): RedirectResponse
    {
        $data = $request->validate(['name' => ['required', 'max:80'], 'contact' => ['required', 'max:120'], 'plan' => ['required'], 'start' => ['nullable', 'date_format:Y-m-d']]);
        $plan = $this->settings->plan($data['plan']);
        abort_unless($plan, 422);
        $s = $this->subs->register($data, $plan);

        return back()->with('status', 'اشتراک ثبت شد؛ کد: '.$s['code']);
    }

    public function inboxAction(Request $request): RedirectResponse
    {
        $data = $request->validate(['id' => ['required', 'string'], 'action' => ['required', 'in:done,new,delete']]);
        $this->messages->inboxAction($data['id'], $data['action']);

        return back()->with('status', 'انجام شد.');
    }

    public function purge(Request $request): RedirectResponse
    {
        $what = $request->validate(['what' => ['required', 'in:inbox,notices,installs,subs']])['what'];
        $this->messages->purge($what);
        if ($what === 'subs') {
            $this->subs->purge();
        }

        return back()->with('status', 'به‌طور کامل پاک شد.');
    }

    public function noticeCreate(Request $request): RedirectResponse
    {
        $data = $request->validate([
            'title' => ['required', 'string', 'max:80'], 'body' => ['required', 'string', 'max:600'],
            'level' => ['required', 'in:info,success,warning'], 'target' => ['required', 'string', 'max:20'],
            'until' => ['nullable', 'date_format:Y-m-d'],
        ]);
        $target = $data['target'];
        if (! in_array($target, ['all', 'expiring', 'expired', 'trial', 'no_code'], true)) {
            $target = SubscriptionStore::normalizeCode($target);
            abort_if($target === '', 422);
        }
        $this->messages->addNotice(['title' => $data['title'], 'body' => $data['body'], 'level' => $data['level'], 'target' => $target, 'until' => $data['until'] ?? null]);

        return redirect()->route('admin.dashboard', ['tab' => 'notices'])->with('status', 'پیام ثبت شد و در اولین اتصال برنامه نمایش داده می‌شود.');
    }

    public function noticeAction(Request $request): RedirectResponse
    {
        $data = $request->validate(['id' => ['required', 'string'], 'action' => ['required', 'in:toggle,delete']]);
        $this->messages->noticeAction($data['id'], $data['action']);

        return back()->with('status', 'انجام شد.');
    }

    public function settingsSave(Request $request): RedirectResponse
    {
        $cur = $this->settings->all();
        $in = $request->all();
        $cards = [];
        foreach ((array) ($in['cards'] ?? []) as $c) {
            $num = SupportReportService::normalizeDigits($c['number'] ?? '');
            if (strlen($num) === 16) {
                $cards[] = ['number' => $num, 'holder' => mb_substr(trim((string) ($c['holder'] ?? '')), 0, 60), 'enabled' => ! empty($c['enabled'])];
            }
        }
        $primary = (int) ($in['primary_card'] ?? 0);
        if (isset($cards[$primary])) {
            $cards = array_merge([$cards[$primary]], array_values(array_diff_key($cards, [$primary => 1])));
        }
        $plans = [];
        foreach ((array) ($in['plans'] ?? []) as $i => $p) {
            $days = (int) SupportReportService::normalizeDigits((string) ($p['days'] ?? ''));
            $price = (int) SupportReportService::normalizeDigits((string) ($p['price'] ?? ''));
            $title = mb_substr(trim((string) ($p['title'] ?? '')), 0, 30);
            if ($title !== '' && $days > 0) {
                $plans[] = ['id' => preg_replace('/[^a-z0-9]/', '', (string) ($p['id'] ?? '')) ?: 'p'.$i, 'title' => $title, 'days' => $days, 'price' => $price, 'enabled' => ! empty($p['enabled'])];
            }
        }
        $flags = [];
        foreach (array_keys(SiteSettings::defaults()['flags']) as $f) {
            $flags[$f] = ! empty($in['flags'][$f]);
        }
        $num = fn ($k, $min, $max, $def) => max($min, min($max, (int) SupportReportService::normalizeDigits((string) ($in['numbers'][$k] ?? $def)) ?: $def));
        $numbers = [
            'trial_days' => $num('trial_days', 1, 30, 3),
            'reminder_days' => $num('reminder_days', 1, 60, 7),
            'org_min_seats' => $num('org_min_seats', 2, 100, 3),
            'org_tier1' => $num('org_tier1', 10, 100, 60),
            'org_tier2' => $num('org_tier2', 10, 100, 50),
            'org_tier3' => $num('org_tier3', 10, 100, 45),
        ];
        $ai = [];
        foreach ((array) ($in['ai'] ?? []) as $i => $a) {
            $title = mb_substr(trim((string) ($a['title'] ?? '')), 0, 60);
            if ($title === '') {
                continue;
            }
            $ai[] = [
                'id' => preg_replace('/[^a-z0-9_-]/', '', strtolower((string) ($a['id'] ?? ''))) ?: 'ai'.$i,
                'title' => $title,
                'kind' => in_array($a['kind'] ?? '', ['openai', 'anthropic', 'gemini'], true) ? $a['kind'] : 'openai',
                'base_url' => mb_substr(trim((string) ($a['base_url'] ?? '')), 0, 200),
                'models' => mb_substr(trim((string) ($a['models'] ?? '')), 0, 300),
                'hint' => mb_substr(trim((string) ($a['hint'] ?? '')), 0, 200),
                'enabled' => ! empty($a['enabled']),
            ];
        }
        $texts = [];
        foreach (array_keys(SiteSettings::defaults()['texts']) as $t) {
            $texts[$t] = mb_substr(trim((string) ($in['texts'][$t] ?? '')), 0, 800);
        }
        $this->settings->save([
            'cards' => $cards ?: $cur['cards'], 'plans' => $plans ?: $cur['plans'],
            'flags' => $flags, 'numbers' => $numbers, 'texts' => $texts, 'ai_catalog' => $ai ?: $cur['ai_catalog'],
        ]);

        return redirect()->route('admin.dashboard', ['tab' => 'settings'])->with('status', 'تنظیمات ذخیره شد و بلافاصله روی سایت و برنامه اعمال می‌شود.');
    }

    // ------------------------------------------------------------ ورود و حساب مدیر
    public function showLogin()
    {
        if (AdminAuth::check()) {
            return redirect()->route('admin.dashboard');
        }

        return view('admin.login');
    }

    public function login(Request $request): RedirectResponse
    {
        $data = $request->validate(['username' => ['required', 'string', 'max:60'], 'password' => ['required', 'string', 'max:200']]);
        if (! AdminAuth::attempt($data['username'], $data['password'])) {
            return back()->withErrors(['username' => 'نام کاربری یا رمز عبور درست نیست.'])->onlyInput('username');
        }

        return redirect()->route('admin.dashboard');
    }

    public function account()
    {
        return redirect()->route('admin.dashboard', ['tab' => 'account']);
    }

    public function accountSave(Request $request): RedirectResponse
    {
        $data = $request->validate([
            'username' => ['required', 'string', 'min:3', 'max:60', 'regex:/^[A-Za-z0-9_.@-]+$/'],
            'password' => ['required', 'string', 'min:10', 'max:200', 'confirmed'],
        ], ['password.min' => 'رمز جدید باید دست‌کم ۱۰ نویسه باشد.', 'password.confirmed' => 'تکرار رمز با خود رمز یکسان نیست.', 'username.regex' => 'نام کاربری فقط با حروف و ارقام لاتین.']);
        AdminAuth::change($data['username'], $data['password']);

        return redirect()->route('admin.dashboard', ['tab' => 'account'])->with('status', 'نام کاربری و رمز مدیر به‌روز شد.');
    }

    public function logout(Request $request): RedirectResponse
    {
        $request->session()->forget('mx_admin');
        $request->session()->regenerate();

        return redirect()->route('admin.login');
    }
}
