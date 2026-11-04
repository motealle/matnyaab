<?php

namespace App\Services;

/** پیام‌های پشتیبانی/پیشنهاد کاربران و پیام‌های مدیر به برنامه */
final class MessageStore
{
    private JsonStore $inbox;
    private JsonStore $notices;
    private JsonStore $installs;

    public function __construct()
    {
        $this->inbox = new JsonStore('inbox');
        $this->notices = new JsonStore('notices');
        $this->installs = new JsonStore('installs');
    }

    public function addInbox(array $m): void
    {
        $this->inbox->update(function (array &$all) use ($m) {
            $all[] = $m + ['id' => bin2hex(random_bytes(6)), 'at' => now('Asia/Tehran')->format('Y-m-d H:i:s'), 'status' => 'new'];
            $all = array_slice($all, -2000);
        });
    }

    public function inbox(): array
    {
        return array_reverse($this->inbox->read());
    }

    /** آیا پیامی با همین متن در ۲۴ ساعت اخیر ثبت شده؟ (ضد تکرار) */
    public function isDuplicate(string $hash): bool
    {
        $since = now('Asia/Tehran')->subDay()->format('Y-m-d H:i:s');
        foreach ($this->inbox->read() as $m) {
            if (($m['hash'] ?? '') === $hash && ($m['at'] ?? '') >= $since) {
                return true;
            }
        }

        return false;
    }

    public function inboxAction(string $id, string $action): void
    {
        $this->inbox->update(function (array &$all) use ($id, $action) {
            foreach ($all as $i => $m) {
                if (($m['id'] ?? '') === $id) {
                    if ($action === 'delete') { unset($all[$i]); } else { $all[$i]['status'] = $action === 'done' ? 'done' : 'new'; }
                }
            }
            $all = array_values($all);
        });
    }

    public function addNotice(array $n): void
    {
        $this->notices->update(function (array &$all) use ($n) {
            $all[] = $n + ['id' => bin2hex(random_bytes(5)), 'created_at' => now('Asia/Tehran')->format('Y-m-d H:i'), 'active' => true];
        });
    }

    public function notices(): array
    {
        return array_reverse($this->notices->read());
    }

    public function noticeAction(string $id, string $action): void
    {
        $this->notices->update(function (array &$all) use ($id, $action) {
            foreach ($all as $i => $n) {
                if (($n['id'] ?? '') === $id) {
                    if ($action === 'delete') { unset($all[$i]); } else { $all[$i]['active'] = ! $all[$i]['active']; }
                }
            }
            $all = array_values($all);
        });
    }

    /** پیام‌های فعالِ مخصوص این کلاینت (همگانی، بر پایهٔ وضعیت، یا مخصوص یک کد) */
    public function noticesFor(?string $code, ?string $state): array
    {
        $today = now('Asia/Tehran')->toDateString();
        $out = [];
        foreach ($this->notices->read() as $n) {
            if (empty($n['active']) || (! empty($n['until']) && $n['until'] < $today)) {
                continue;
            }
            $t = (string) ($n['target'] ?? 'all');
            if ($t === 'all' || ($code && $t === $code) || ($state && $t === $state) || ($t === 'no_code' && ! $code)) {
                $out[] = ['id' => $n['id'], 'title' => $n['title'], 'body' => $n['body'], 'level' => $n['level'] ?? 'info', 'date' => $n['created_at']];
            }
        }

        return $out;
    }

    public function purge(string $what): void
    {
        $store = ['inbox' => $this->inbox, 'notices' => $this->notices, 'installs' => $this->installs][$what] ?? null;
        $store?->update(function (array &$all) { $all = []; });
    }

    public function touchInstall(string $systemId, string $version, ?string $code, string $mode): void
    {
        if ($systemId === '') {
            return;
        }
        $this->installs->update(function (array &$all) use ($systemId, $version, $code, $mode) {
            $now = now('Asia/Tehran')->format('Y-m-d H:i:s');
            $key = substr(preg_replace('/[^A-Za-z0-9]/', '', $systemId), 0, 64);
            $all[$key] = ['last_seen' => $now, 'first_seen' => $all[$key]['first_seen'] ?? $now, 'version' => mb_substr($version, 0, 12), 'code' => $code, 'mode' => mb_substr($mode, 0, 12)];
        });
    }

    public function installStats(): array
    {
        $all = $this->installs->read();
        $d1 = now('Asia/Tehran')->subDay()->format('Y-m-d H:i:s');
        $d7 = now('Asia/Tehran')->subDays(7)->format('Y-m-d H:i:s');
        $c = fn ($since, $f = null) => count(array_filter($all, fn ($i) => ($i['last_seen'] ?? '') >= $since && ($f === null || $f($i))));

        return [
            'total' => count($all),
            'day' => $c($d1),
            'week' => $c($d7),
            'week_trial' => $c($d7, fn ($i) => ($i['mode'] ?? '') === 'trial'),
            'week_unpaid' => $c($d7, fn ($i) => in_array($i['mode'] ?? '', ['none', 'expired'], true)),
            'week_coded' => $c($d7, fn ($i) => ! empty($i['code'])),
        ];
    }
}
