<?php

namespace App\Services;

use App\Models\LegacyGatewayTransaction;
use Illuminate\Support\Facades\Http;

final class IdPayService
{
    private const CREATE_URL = 'https://api.idpay.ir/v1.1/payment';
    private const VERIFY_URL = 'https://api.idpay.ir/v1.1/payment/verify';

    private function headers(): array
    {
        $merchant = (string) config('services.idpay.merchant_code', '');
        if ($merchant === '') {
            throw new \RuntimeException('پرداخت آنلاین در حال حاضر در دسترس نیست.');
        }

        return [
            'X-API-KEY' => $merchant,
            'X-SANDBOX' => (string) config('services.idpay.sandbox', 0),
            'Content-Type' => 'application/json',
        ];
    }

    public function createPayment(
        int $userId,
        int $orderId,
        int $amountRial,
        string $mobile
    ): array {
        do {
            $trackingCode = '';
            for ($i = 0; $i < 16; $i++) {
                $trackingCode .= (string) random_int(0, 9);
            }
        } while (
            LegacyGatewayTransaction::query()
                ->where('tracking_code', $trackingCode)
                ->exists()
        );

        $callback = rtrim((string) config('app.url'), '/')
            . '/callback-gateway?res=' . rawurlencode($trackingCode);

        $response = Http::timeout(30)
            ->withHeaders($this->headers())
            ->post(self::CREATE_URL, [
                'order_id' => $trackingCode,
                'amount' => $amountRial,
                'phone' => $mobile,
                'callback' => $callback,
            ]);

        $gatewayId = (string) $response->json('id', '');
        $redirect = (string) $response->json('link', '');

        if (! $response->successful() || $gatewayId === '' || $redirect === '') {
            throw new \RuntimeException('برقراری ارتباط با درگاه پرداخت انجام نشد. لطفاً دوباره تلاش کنید.');
        }

        $now = now()->format('Y-m-d H:i:s');

        $transaction = LegacyGatewayTransaction::query()->create([
            'status' => 'redirected',
            'bank_type' => 'IDPAY',
            'tracking_code' => $trackingCode,
            'amount' => (string) $amountRial,
            'reference_number' => $gatewayId,
            'response_result' => $response->body(),
            'callback_url' => $callback,
            'extra_information' => json_encode([
                'user_id' => $userId,
                'order_id' => $orderId,
            ], JSON_UNESCAPED_SLASHES),
            'created_at' => $now,
            'update_at' => $now,
            'bank_choose_identifier' => null,
        ]);

        return [
            'transaction' => $transaction,
            'redirect_url' => $redirect,
        ];
    }

    public function verify(LegacyGatewayTransaction $transaction, array $callbackData): bool
    {
        if ($transaction->status === 'success') {
            return true;
        }

        if ((int) ($callbackData['status'] ?? 0) !== 10) {
            $this->mark($transaction, 'cancelled', json_encode($callbackData));
            return false;
        }

        $gatewayId = (string) ($callbackData['id'] ?? $transaction->reference_number);
        if ($gatewayId === '') {
            $this->mark($transaction, 'error', 'missing gateway id');
            return false;
        }

        $response = Http::timeout(30)
            ->withHeaders($this->headers())
            ->post(self::VERIFY_URL, [
                'id' => $gatewayId,
                'order_id' => (string) $transaction->tracking_code,
            ]);

        $status = (int) $response->json('status', 0);
        $verifiedAmount = $response->json('amount');

        if (
            $response->successful()
            && in_array($status, [100, 101], true)
            && ($verifiedAmount === null || (int) $verifiedAmount === (int) $transaction->amount)
        ) {
            $this->mark($transaction, 'success', $response->body());
            return true;
        }

        $this->mark($transaction, 'error', $response->body());
        return false;
    }

    private function mark(LegacyGatewayTransaction $transaction, string $status, string $result): void
    {
        $transaction->forceFill([
            'status' => $status,
            'response_result' => $result,
            'update_at' => now()->format('Y-m-d H:i:s'),
        ])->save();
    }
}
