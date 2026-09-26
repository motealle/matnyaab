<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class Order extends Model
{
    protected $table = 'users_ordermodel';
    public $timestamps = false;
    protected $guarded = [];

    protected function casts(): array
    {
        return [
            'is_complete' => 'boolean',
            'order_amount' => 'integer',
        ];
    }

    public function wantedSubscription(): BelongsTo
    {
        return $this->belongsTo(Subscription::class, 'wanted_subscription_id');
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
