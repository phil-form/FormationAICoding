<?php

// step-06 : Symfony 5.4 → 6.4 (PHP 8.1), Doctrine ORM 2.20. Règles liées aux versions + conversion des annotations
// en attributs (Symfony, Doctrine, SensioFrameworkExtraBundle supprimé en 6.x) : changement de métadonnées sans
// effet sur le comportement. Voir rector/README.md.

declare(strict_types=1);

use Rector\ValueObject\PhpVersion;

require __DIR__.'/common.php';

return flotte_rector('step-06', PhpVersion::PHP_81)
    ->withComposerBased(twig: true, doctrine: true, symfony: true)
    ->withAttributesSets(symfony: true, doctrine: true, sensiolabs: true);
