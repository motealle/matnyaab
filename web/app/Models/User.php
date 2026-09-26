<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;

class User extends Authenticatable
{
    use Notifiable;

    protected $table = 'users_accountmodel';
    public $timestamps = false;
    protected $guarded = [];

    protected $hidden = ['password', 'confirm_code'];

    protected function casts(): array
    {
        return [
            'is_superuser' => 'boolean',
            'is_staff' => 'boolean',
            'is_active' => 'boolean',
            'is_phone_confirmed' => 'boolean',
            'has_user_system_id' => 'boolean',
            'last_login' => 'datetime',
            'date_joined' => 'datetime',
            'license_buy_time' => 'datetime',
            'license_end_time' => 'datetime',
        ];
    }

    public function subscription(): BelongsTo
    {
        return $this->belongsTo(Subscription::class, 'subscription_id');
    }

    public function newOrder(): BelongsTo
    {
        return $this->belongsTo(Order::class, 'new_order_id');
    }
}
