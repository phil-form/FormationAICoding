<?php

// step-01 : Symfony 2.8 → 3.4 (PHP 7.2). Règles Symfony liées aux versions jusqu'à 3.4 (form types par classe,
// getBlockPrefix, security.context, ChoiceType…), Twig 1.x, Doctrine 2.5. Voir rector/README.md.

declare(strict_types=1);

use Rector\ValueObject\PhpVersion;

require __DIR__.'/common.php';

return flotte_rector('step-01', PhpVersion::PHP_72)
    ->withComposerBased(twig: true, doctrine: true, symfony: true);
