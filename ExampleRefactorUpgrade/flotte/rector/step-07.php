<?php

// step-07 : PHP 8.1 → 8.4 (Symfony 6.4). Dépréciations de PHP 8.4 : types nullables implicites, paramètre $escape
// de fputcsv & co. Voir rector/README.md.

declare(strict_types=1);

use Rector\Php84\Rector\FuncCall\AddEscapeArgumentRector;
use Rector\Php84\Rector\Param\ExplicitNullableParamTypeRector;
use Rector\ValueObject\PhpVersion;

require __DIR__.'/common.php';

return flotte_rector('step-07', PhpVersion::PHP_84)
    ->withRules([
        ExplicitNullableParamTypeRector::class,
        AddEscapeArgumentRector::class,
    ]);
