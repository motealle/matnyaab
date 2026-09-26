<?php

namespace Tests\Unit;

use App\Services\SerialService;
use PHPUnit\Framework\TestCase;

class SerialServiceTest extends TestCase
{
    public function test_serial_format_matches_legacy_python_aes_base32_vector(): void
    {
        $service = new SerialService();

        $raw = 'CI-SYSTEM-123417000000001702592000';
        $key = implode('', array_map('chr', range(0, 15)));
        $iv = implode('', array_map('chr', range(16, 31)));

        $expected = 'AAAQEAYEAUDAOCAJBIFQYDIOB4IBCEQTCQKRMFYYDENBWHA5DYP7Y3JSEGLXEH4AU3HIWQDBTEUBBHLMCDZCJCUJYH3ALHP4ZQU2F576MQBWBIJUEG4OKQUADFFHKZVC';

        $encoded = $service->encryptWithKeyAndIv($raw, $key, $iv);

        $this->assertSame($expected, $encoded);
        $this->assertSame($raw, $service->decrypt($encoded));
    }

    public function test_generated_serial_round_trips(): void
    {
        $service = new SerialService();
        $encoded = $service->generateSerial('SYSTEM-X', 1700000000, 1702592000);

        $this->assertSame('SYSTEM-X17000000001702592000', $service->decrypt($encoded));
    }
}
