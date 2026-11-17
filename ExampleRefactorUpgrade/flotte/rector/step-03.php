<?php

// step-03 : PHP 7.2 → 7.4 (Symfony 4.4). UNIQUEMENT des règles qui suppriment des constructions dépréciées ou
// retirées sans changer le résultat. Exclues volontairement : JsonThrowOnErrorRector et StringifyStrNeedlesRector
// (changent le comportement), MoneyFormatToNumberFormatRector (sortie différente), les règles de modernisation
// (arrow functions, ??=, propriétés typées…). Voir rector/README.md.

declare(strict_types=1);

use Rector\Php73\Rector\ConstFetch\SensitiveConstantNameRector;
use Rector\Php73\Rector\FuncCall\RegexDashEscapeRector;
use Rector\Php73\Rector\FuncCall\SensitiveDefineRector;
use Rector\Php74\Rector\ArrayDimFetch\CurlyToSquareBracketArrayStringRector;
use Rector\Php74\Rector\FuncCall\ArrayKeyExistsOnPropertyRector;
use Rector\Php74\Rector\FuncCall\FilterVarToAddSlashesRector;
use Rector\Php74\Rector\FuncCall\HebrevcToNl2brHebrevRector;
use Rector\Php74\Rector\FuncCall\MbStrrposEncodingArgumentPositionRector;
use Rector\Php74\Rector\FuncCall\RestoreIncludePathToIniRestoreRector;
use Rector\Php74\Rector\StaticCall\ExportToReflectionFunctionRector;
use Rector\Php74\Rector\Ternary\ParenthesizeNestedTernaryRector;
use Rector\ValueObject\PhpVersion;

require __DIR__.'/common.php';

return flotte_rector('step-03', PhpVersion::PHP_74)
    ->withRules([
        SensitiveConstantNameRector::class,
        SensitiveDefineRector::class,
        RegexDashEscapeRector::class,
        CurlyToSquareBracketArrayStringRector::class,
        ArrayKeyExistsOnPropertyRector::class,
        FilterVarToAddSlashesRector::class,
        HebrevcToNl2brHebrevRector::class,
        MbStrrposEncodingArgumentPositionRector::class,
        RestoreIncludePathToIniRestoreRector::class,
        ExportToReflectionFunctionRector::class,
        ParenthesizeNestedTernaryRector::class,
    ]);
