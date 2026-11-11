<?php

namespace App\Services;

/** تنظیمات و فیچرفلگ‌های قابل‌ویرایش از پنل مدیریت */
final class SiteSettings
{
    public static function defaults(): array
    {
        return [
            'cards' => [
                ['number' => '6037997770259241', 'holder' => 'محمد کاظم رحیمی', 'enabled' => true],
                ['number' => '5041721202227469', 'holder' => 'محمد رمضانی', 'enabled' => true],
            ],
            'plans' => [
                ['id' => 'm1', 'title' => 'ماهانه', 'days' => 30, 'price' => 89000, 'enabled' => true],
                ['id' => 'm6', 'title' => 'شش‌ماهه', 'days' => 180, 'price' => 299000, 'enabled' => true],
                ['id' => 'm12', 'title' => 'یک‌ساله', 'days' => 365, 'price' => 490000, 'enabled' => true],
            ],
            'flags' => [
                'subscribe_enabled' => true,
                'require_tracking_code' => false,
                'trial_enabled' => true,
                'contact_enabled' => true,
                'feature_requests_enabled' => true,
                'client_messages_enabled' => true,
                'announcement_enabled' => false,
                'org_enabled' => true,
                'word_addin' => true,
            ],
            // کاتالوگ پیشنهادی سرویس‌های هوش مصنوعی برای افزونهٔ ورد (کلیدها نزد خود کاربران است)
            'ai_catalog' => [
                ['id' => 'openai', 'title' => 'OpenAI', 'kind' => 'openai', 'base_url' => 'https://api.openai.com/v1', 'models' => 'gpt-4o-mini, gpt-4o', 'hint' => '', 'enabled' => true],
                ['id' => 'anthropic', 'title' => 'Anthropic Claude', 'kind' => 'anthropic', 'base_url' => 'https://api.anthropic.com/v1', 'models' => 'claude-sonnet-4-5, claude-haiku-4-5', 'hint' => '', 'enabled' => true],
                ['id' => 'gemini', 'title' => 'Google Gemini', 'kind' => 'gemini', 'base_url' => 'https://generativelanguage.googleapis.com/v1beta', 'models' => 'gemini-2.0-flash, gemini-1.5-pro', 'hint' => '', 'enabled' => true],
                ['id' => 'openrouter', 'title' => 'OpenRouter', 'kind' => 'openai', 'base_url' => 'https://openrouter.ai/api/v1', 'models' => 'openai/gpt-4o-mini', 'hint' => '', 'enabled' => true],
                ['id' => 'custom', 'title' => 'سرویس سازگار با OpenAI (دلخواه)', 'kind' => 'openai', 'base_url' => '', 'models' => '', 'hint' => 'نشانی پایهٔ API را وارد کنید؛ مثلاً درگاه‌های داخلی یا Ollama محلی (http://localhost:11434/v1)', 'enabled' => true],
            ],
            'numbers' => [
                'trial_days' => 3,
                'reminder_days' => 7,
                'org_min_seats' => 3,
                'org_tier1' => 60,
                'org_tier2' => 50,
                'org_tier3' => 45,
            ],
            'texts' => [
                'announcement' => '',
                'trust_note' => 'ما به شما اعتماد داریم؛ برای همین هیچ بررسی و پیگیری‌ای در کار نیست و کد اشتراک همان لحظه تقدیم‌تان می‌شود. همراهی شما چراغ متن‌یاب را روشن نگه می‌دارد. ممنون که هستید! 🌷',
                'tagline' => 'متن‌یاب، گوگلِ کامپیوتر شما',
                'support_hours' => 'پاسخ پیام‌ها معمولاً ظرف یک روز کاری داده می‌شود.',
            ],
        ];
    }

    private JsonStore $store;

    public function __construct()
    {
        $this->store = new JsonStore('settings');
    }

    public function all(): array
    {
        $d = self::defaults();
        $s = $this->store->read();
        foreach (['flags', 'numbers', 'texts'] as $k) {
            $d[$k] = array_merge($d[$k], is_array($s[$k] ?? null) ? $s[$k] : []);
        }
        if (str_contains((string) $d['texts']['trust_note'], 'وجدان')) {
            $d['texts']['trust_note'] = self::defaults()['texts']['trust_note'];
        }
        foreach (['cards', 'plans', 'ai_catalog'] as $k) {
            if (! empty($s[$k]) && is_array($s[$k])) {
                $d[$k] = array_values($s[$k]);
            }
        }

        return $d;
    }

    public function flag(string $key): bool
    {
        return (bool) ($this->all()['flags'][$key] ?? false);
    }

    public function number(string $key): int
    {
        return (int) ($this->all()['numbers'][$key] ?? 0);
    }

    public function text(string $key): string
    {
        return (string) ($this->all()['texts'][$key] ?? '');
    }

    /** @return array<int, array> فقط کارت‌های فعال؛ اولی کارت مبناست */
    public function cards(): array
    {
        return array_values(array_filter($this->all()['cards'], fn ($c) => ! empty($c['enabled']) && preg_replace('/\D/', '', (string) $c['number']) !== ''));
    }

    public function plans(bool $onlyEnabled = true): array
    {
        $plans = $this->all()['plans'];

        return array_values($onlyEnabled ? array_filter($plans, fn ($p) => ! empty($p['enabled'])) : $plans);
    }

    public function plan(string $id): ?array
    {
        foreach ($this->plans() as $p) {
            if ($p['id'] === $id) {
                return $p;
            }
        }

        return null;
    }

    public function save(array $settings): void
    {
        $this->store->update(function (array &$data) use ($settings) {
            $data = $settings;
        });
    }

    /** درصد قیمت فردی که هر کاربر سازمانی می‌پردازد (پلکانی بر پایهٔ تعداد کاربران) */
    public function orgFactor(int $seats): int
    {
        $n = $this->all()['numbers'];

        return (int) ($seats >= 50 ? $n['org_tier3'] : ($seats >= 10 ? $n['org_tier2'] : $n['org_tier1']));
    }

    /** قیمت کل اشتراک سازمانی؛ به نزدیک‌ترین ده هزار تومان گرد می‌شود */
    public function orgQuote(array $plan, int $seats): int
    {
        return (int) (round($plan['price'] * $seats * $this->orgFactor($seats) / 100 / 10000) * 10000);
    }

    public static function cardGroups(string $number): string
    {
        return implode(' ', str_split(preg_replace('/\D/', '', $number), 4));
    }
}
