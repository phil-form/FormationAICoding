<?php

// step-02 : Symfony 3.4 → 4.4 (PHP 7.2), Twig 2, Doctrine 2.7. La restructuration Flex (AppBundle → App, config/,
// public/, templates/) est faite par le migrator avec les recettes Flex, PAS par Rector. Voir rector/README.md.

declare(strict_types=1);

use Rector\ValueObject\PhpVersion;

require __DIR__.'/common.php';

return flotte_rector('step-02', PhpVersion::PHP_72)
    ->withComposerBased(twig: true, doctrine: true, symfony: true);
