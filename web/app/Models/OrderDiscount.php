<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class OrderDiscount extends Model
{
    protected $table = 'orderdiscount_orderdiscountmodel';
    public $timestamps = false;
    protected $guarded = [];

    protected function casts(): array
    {
        return [
            'discount_date_start' => 'datetime',
            'discount_date_end' => 'datetime',
            'discount_rate' => 'float',
        ];
    }

    public function isValidNow(): bool
    {
        $now = now();
        return $this->discount_date_start !== null
            && $this->discount_date_end !== null
            && $this->discount_date_start->lessThan($now)
            && $now->lessThan($this->discount_date_end);
    }
}
