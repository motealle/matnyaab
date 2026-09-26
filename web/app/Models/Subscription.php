<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Subscription extends Model
{
    protected $table = 'users_subscriptionmodel';
    public $timestamps = false;
    protected $guarded = [];

    protected function casts(): array
    {
        return [
            'subscription_price' => 'integer',
            'subscription_duration_days' => 'integer',
            'subscription_active' => 'boolean',
        ];
    }
}
