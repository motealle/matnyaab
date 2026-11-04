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
            ],
            'numbers' => [
                'trial_days' => 3,
                'reminder_days' => 7,
            ],
            'texts' => [
                'announcement' => '',
                'trust_note' => 'متن‌یاب بر اعتماد بنا شده است. ما پرداخت‌ها را بررسی نمی‌کنیم؛ کد اشتراک بلافاصله به شما داده می‌شود و انتخاب درست را به وجدان شما می‌سپاریم.',
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
        foreach (['cards', 'plans'] as $k) {
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

    public static function cardGroups(string $number): string
    {
        return implode(' ', str_split(preg_replace('/\D/', '', $number), 4));
    }
}
