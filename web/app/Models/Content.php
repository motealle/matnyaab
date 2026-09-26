<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Content extends Model
{
    protected $table = 'matnyaab_contentsmodel';
    public $timestamps = false;
    protected $guarded = [];

    protected function casts(): array
    {
        return [
            'content_show' => 'boolean',
            'content_filesize' => 'integer',
        ];
    }
}
