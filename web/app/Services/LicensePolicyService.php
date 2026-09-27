<?php

namespace App\Services;

use App\Models\User;
use Carbon\CarbonInterface;

final class LicensePolicyService
{
    private string $path;

    public function __construct()
    {
        $this->path = (string) config('services.license.policy_path', storage_path('app/license-policy.json'));
    }

    public function globalBypass(): bool
    {
        return (bool) ($this->read()['global_bypass'] ?? false);
    }

    public function setGlobalBypass(bool $enabled): void
    {
        $data = $this->read();
        $data['global_bypass'] = $enabled;
        $this->write($data);
    }

    public function userSettings(int $userId): array
    {
        $data = $this->read();
        $row = $data['users'][(string) $userId] ?? [];

        return [
            'bypass' => (bool) ($row['bypass'] ?? false),
            'kotlin_system_id' => $this->normalizeSystemId($row['kotlin_system_id'] ?? null),
        ];
    }

    public function setUser(int $userId, bool $bypass, ?string $kotlinSystemId): void
    {
        $data = $this->read();
        $key = (string) $userId;
        $normalized = $this->normalizeSystemId($kotlinSystemId);

        if (! $bypass && $normalized === null) {
            unset($data['users'][$key]);
        } else {
            $data['users'][$key] = [
                'bypass' => $bypass,
                'kotlin_system_id' => $normalized,
            ];
        }

        $this->write($data);
    }

    public function isBypassed(User $user): bool
    {
        return $this->globalBypass() || $this->userSettings((int) $user->id)['bypass'];
    }

    public function clientLicenses(User $user, SerialService $serials): array
    {
        $settings = $this->userSettings((int) $user->id);
        $legacySystemId = $this->normalizeSystemId($user->user_system_id);
        $kotlinSystemId = $settings['kotlin_system_id'];
        $bypassed = $this->globalBypass() || $settings['bypass'];

        if ($bypassed) {
            $buy = now()->subDay()->timestamp;
            $end = now()->setDate(2099, 12, 31)->endOfDay()->timestamp;

            return [
                'bypassed' => true,
                'legacy_system_id' => $legacySystemId,
                'legacy_serial' => $legacySystemId ? $serials->generateSerial($legacySystemId, $buy, $end) : null,
                'kotlin_system_id' => $kotlinSystemId,
                'kotlin_serial' => $kotlinSystemId ? $serials->generateSerial($kotlinSystemId, $buy, $end) : null,
                'expires_at' => '2099-12-31',
            ];
        }

        $buy = $this->timestamp($user->license_buy_time);
        $end = $this->timestamp($user->license_end_time);
        $active = $buy !== null && $end !== null && now()->timestamp < $end;

        return [
            'bypassed' => false,
            'legacy_system_id' => $legacySystemId,
            'legacy_serial' => $active ? (string) ($user->user_serial_number ?: '') : null,
            'kotlin_system_id' => $kotlinSystemId,
            'kotlin_serial' => ($active && $kotlinSystemId) ? $serials->generateSerial($kotlinSystemId, $buy, $end) : null,
            'expires_at' => $active && $user->license_end_time ? $user->license_end_time->format('Y-m-d') : null,
        ];
    }

    private function timestamp(mixed $value): ?int
    {
        if ($value instanceof CarbonInterface) {
            return $value->timestamp;
        }

        if (! $value) {
            return null;
        }

        $timestamp = strtotime((string) $value);

        return $timestamp === false ? null : $timestamp;
    }

    private function normalizeSystemId(mixed $value): ?string
    {
        if (! is_string($value)) {
            return null;
        }

        $value = strtoupper(trim($value));

        return preg_match('/^[A-F0-9]{32}$/', $value) === 1 ? $value : null;
    }

    private function read(): array
    {
        if (! is_file($this->path)) {
            return ['version' => 1, 'global_bypass' => false, 'users' => []];
        }

        $raw = file_get_contents($this->path);
        $decoded = is_string($raw) ? json_decode($raw, true) : null;

        if (! is_array($decoded)) {
            return ['version' => 1, 'global_bypass' => false, 'users' => []];
        }

        $decoded['version'] = 1;
        $decoded['global_bypass'] = (bool) ($decoded['global_bypass'] ?? false);
        $decoded['users'] = is_array($decoded['users'] ?? null) ? $decoded['users'] : [];

        return $decoded;
    }

    private function write(array $data): void
    {
        $dir = dirname($this->path);
        if (! is_dir($dir) && ! mkdir($dir, 0750, true) && ! is_dir($dir)) {
            throw new \RuntimeException('Cannot create license policy directory.');
        }

        $handle = fopen($this->path, 'c+');
        if ($handle === false) {
            throw new \RuntimeException('Cannot open license policy file.');
        }

        try {
            if (! flock($handle, LOCK_EX)) {
                throw new \RuntimeException('Cannot lock license policy file.');
            }

            $data['version'] = 1;
            $data['updated_at'] = now()->toIso8601String();
            $json = json_encode($data, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES);
            if (! is_string($json)) {
                throw new \RuntimeException('Cannot encode license policy.');
            }

            rewind($handle);
            if (! ftruncate($handle, 0) || fwrite($handle, $json.PHP_EOL) === false) {
                throw new \RuntimeException('Cannot write license policy.');
            }
            fflush($handle);
            flock($handle, LOCK_UN);
        } finally {
            fclose($handle);
        }
    }
}
