<?php

declare(strict_types=1);

namespace Flotte\Tests\Support;

use PHPUnit\Framework\Assert;

/**
 * Sorties de référence (golden masters) : tests/golden/<suite>/<zone>/<bh>[-<variante>].<ext>.
 * Écriture uniquement si GOLDEN_RECORD=1 **et** cible ref (enregistrement sur step-00, piloté par /golden-master
 * ou `tests.sh --record-golden`). Sinon : comparaison stricte avec le fichier existant.
 */
final class Golden
{
    public static function dir(): string
    {
        return Target::path(Target::env('GOLDEN_DIR') ?? 'golden');
    }

    public static function recording(): bool
    {
        return '1' === Target::env('GOLDEN_RECORD');
    }

    public static function assertMatchesGolden(string $name, string $content, string $message = ''): void
    {
        $file = self::dir().'/'.$name;
        if (self::recording()) {
            if (Target::REF !== Target::current()) {
                Assert::fail("GOLDEN_RECORD=1 n'est permis que sur la référence (TARGET=ref) : $name");
            }
            if (!is_dir(\dirname($file)) && !mkdir(\dirname($file), 0775, true) && !is_dir(\dirname($file))) {
                Assert::fail("Dossier golden impossible à créer : ".\dirname($file));
            }
            file_put_contents($file, $content);
        }
        if (!is_file($file)) {
            Assert::fail("Golden absent : golden/$name — l'enregistrer sur la référence (tests.sh --target ref --record-golden).");
        }
        Assert::assertSame((string) file_get_contents($file), $content, ('' !== $message ? "$message\n" : '')."Écart avec le golden golden/$name");
    }
}
