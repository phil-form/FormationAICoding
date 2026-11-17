<?php

// step-05 : PHP 7.4 → 8.1 (Symfony 5.4). Règles de compatibilité qui conservent le résultat de PHP 7 :
// NullToStrictStringFuncCallArgRector caste explicitement null en chaîne là où PHP < 8.1 le faisait implicitement
// (number_format(null)…). Les CHANGEMENTS DE COMPORTEMENT de PHP 8.0 (comparaisons chaîne/nombre, tri stable avec
// comparateur booléen, arithmétique sur chaîne non numérique) ne sont PAS traités par Rector : le migrator les
// préserve explicitement, fiche par fiche (BH-015 à 018, BH-404 à 406, BH-416…). Voir rector/README.md.

declare(strict_types=1);

use Rector\Php81\Rector\FuncCall\NullToStrictIntPregSlitFuncCallLimitArgRector;
use Rector\Php81\Rector\FuncCall\NullToStrictStringFuncCallArgRector;
use Rector\ValueObject\PhpVersion;

require __DIR__.'/common.php';

return flotte_rector('step-05', PhpVersion::PHP_81)
    ->withRules([
        NullToStrictStringFuncCallArgRector::class,
        NullToStrictIntPregSlitFuncCallLimitArgRector::class,
    ]);
