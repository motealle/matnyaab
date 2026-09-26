<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

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
}
