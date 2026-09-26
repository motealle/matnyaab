<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class SubscriptionHistory extends Model
{
    protected $table = 'users_subscriptionhistorymodel';
    public $timestamps = false;
    protected $guarded = [];

    protected function casts(): array
    {
        return [
            'subscription_buy_time' => 'datetime',
            'amount_paid' => 'integer',
        ];
    }

    public function subscription(): BelongsTo
    {
        return $this->belongsTo(Subscription::class, 'subscription_id');
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class, 'user_id');
    }

    public function orderDiscount(): BelongsTo
    {
        return $this->belongsTo(OrderDiscount::class, 'order_discount_id');
    }
}
