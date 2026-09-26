<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class LegacyGatewayTransaction extends Model
{
    protected $table = 'azbankgateways_bank';
    public $timestamps = false;
    protected $guarded = [];

    public function metadata(): array
    {
        $decoded = json_decode((string) $this->extra_information, true);
        return is_array($decoded) ? $decoded : [];
    }
}
