<?php

// step-04 : Symfony 4.4 → 5.4 (PHP 7.4), Twig 3, Doctrine ORM 2.17 / DBAL 3. Les annotations restent des
// annotations (attributs impossibles avant PHP 8) ; @Route/@Method/@Template de SensioFrameworkExtraBundle sont
// convertis à step-06. Voir rector/README.md.

declare(strict_types=1);

use Rector\ValueObject\PhpVersion;

require __DIR__.'/common.php';

return flotte_rector('step-04', PhpVersion::PHP_74)
    ->withComposerBased(twig: true, doctrine: true, symfony: true);
