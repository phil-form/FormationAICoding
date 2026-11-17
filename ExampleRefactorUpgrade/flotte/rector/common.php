<?php

// Configuration Rector commune à toutes les marches de la migration flotte (conteneur tools, code monté en /srv/app).
// Voir rector/README.md. Ne JAMAIS ajouter de jeux de « modernisation » (withPhpSets, withPreparedSets, code quality,
// dead code, type declarations…) : ils changent le comportement. Seules des règles de compatibilité sont permises.

declare(strict_types=1);

use Rector\Config\RectorConfig;
use Rector\Configuration\RectorConfigBuilder;

const FLOTTE_ROOT = '/srv/app';

function flotte_rector(string $step, int $phpVersion): RectorConfigBuilder
{
    // Versions visées par la marche : manifeste vendor/composer/installed.json généré par .claude/bin/rector.sh
    // depuis rector/targets.json. Rector 2 lit les versions installées dans le répertoire courant pour activer ses
    // règles liées aux versions (symfony/form >=3.0, twig/twig >=3.0, doctrine/orm >=2.13…).
    $target = FLOTTE_ROOT.'/.claude/state/migration/rector/targets/'.$step;
    if (!is_file($target.'/vendor/composer/installed.json')) {
        throw new RuntimeException("Manifeste de versions absent pour $step : lancer .claude/bin/rector.sh $step");
    }
    chdir($target);

    // Code de l'application, quelle que soit la structure de l'étape (2.8 : app/ + src/AppBundle ; Flex : src/ + config/).
    $candidates = ['src', 'app/AppKernel.php', 'web/app.php', 'web/app_dev.php', 'config', 'public/index.php'];
    $paths = [];
    foreach ($candidates as $c) {
        if (file_exists(FLOTTE_ROOT.'/'.$c)) {
            $paths[] = FLOTTE_ROOT.'/'.$c;
        }
    }

    return RectorConfig::configure()
        ->withPaths($paths)
        ->withSkip([FLOTTE_ROOT.'/tests', FLOTTE_ROOT.'/vendor', FLOTTE_ROOT.'/.ref', FLOTTE_ROOT.'/var', FLOTTE_ROOT.'/app/cache'])
        ->withPhpVersion($phpVersion)
        ->withAutoloadPaths([FLOTTE_ROOT.'/vendor/autoload.php'])
        ->withCache(FLOTTE_ROOT.'/.claude/state/migration/rector/cache/'.$step)
        ->withImportNames(false);
}
