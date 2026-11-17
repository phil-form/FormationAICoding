<?php

declare(strict_types=1);

namespace Flotte\Tests\Edge;

use Flotte\Tests\Support\ConsoleTestCase;
use Flotte\Tests\Support\Scenario;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * Piège : coût `"N/A"`, coût `12.345`, km de retour < départ, fin sans `end_km` (scénario anomalies). En PHP 7.2 :
 * warning « A non-numeric value encountered » → exception en `dev` (code 1, rien sur stdout), ignoré en `prod`
 * (rapport produit, "N/A" compté 0). PHP 8 : TypeError (« Unsupported operand types ») dans les deux environnements.
 * Proposition § 6.5 (provisoire) : figer les deux comportements.
 * Fiches : BH-416 (dev), BH-417 (prod).
 */
#[Group('zone-cli-report')]
final class ReportInconsistentDataTest extends ConsoleTestCase
{
    #[Test]
    #[Group('BH-416')]
    #[Scenario('anomalies')]
    public function bh416_dev_aborts_before_any_output(): void
    {
        $run = $this->console(['fleet:report', '--month=2026-01']);
        $this->assertConsoleError(1, 'Warning: A non-numeric value encountered', $run);
        self::assertStringNotContainsString('Rapport de flotte', $run->stdout, 'aucun titre partiel');
        self::assertSame([], $run->log, 'A5 : exception non journalisée en step-00 — '.$run->describe());
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-417')]
    #[Scenario('anomalies')]
    public function bh417_prod_counts_non_numeric_cost_as_zero_and_negative_km(): void
    {
        $run = $this->console(['fleet:report', '--month=2026-01', '--env=prod']);
        self::assertSame(0, $run->exit, 'A1 — '.$run->describe());
        self::assertSame('', $run->stderr, 'A2 — '.$run->describe());
        // Observé à l'implémentation : le warning est journalisé une fois par PHP, la commande continue.
        self::assertSame(['PHP Warning: A non-numeric value encountered'], $run->log, 'A5 — '.$run->describe());
        $rows = array_column(self::reportRows($run->stdout), null, 0);
        self::assertSame(['1-ANO-008', '2', '20', '12,35 €'], $rows['1-ANO-008'] ?? null, '"N/A" compté 0, 12.345 arrondi à l\'affichage');
        self::assertSame(['1-ANO-006', '2', '-50', '-35,00 €'], $rows['1-ANO-006'] ?? null, 'km de retour < départ : négatif');
        self::assertSame(['1-ANO-007', '1', '-7.000', '0,00 €'], $rows['1-ANO-007'] ?? null, 'fin sans end_km');
        self::assertSame(['-6.930', '12,35 €'], self::reportTotal($run->stdout));
        $this->assertDbUnchanged();
    }
}
