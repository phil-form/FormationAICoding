<?php

declare(strict_types=1);

namespace Flotte\Tests\Support;

/**
 * Base des tests de commandes console (suites cli et edge, tmp/tests-application.md § 6) : scénario restauré par
 * #[Scenario('…')] sur la cible, une exécution par vérification, A1 à A5 (cf. ConsoleCommands).
 */
abstract class ConsoleTestCase extends CaseTestCase
{
    use ConsoleCommands;

    /** A4 de fleet:demo:load : base de la cible exactement égale au scénario demo (types SQLite compris). */
    protected function assertDbIsDemo(?string $target = null): void
    {
        $target ??= $this->target();
        self::assertSame(self::demoFingerprint(), Db::fingerprint($target), "A4 base de « $target » ≠ scénario demo");
    }
}
