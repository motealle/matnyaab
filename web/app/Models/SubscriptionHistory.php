<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class SubscriptionHistory extends Model
{
    protected $table = 'users_subscriptionhistorymodel';
    public $timestamps = false;
    protected $guarded = [];
}
