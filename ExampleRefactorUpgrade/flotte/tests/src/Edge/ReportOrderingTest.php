<?php

declare(strict_types=1);

namespace Flotte\Tests\Edge;

use Flotte\Tests\Support\ConsoleTestCase;
use Flotte\Tests\Support\Scenario;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * Piège : `fleet:report` trie par km décroissants avec `usort()` et un comparateur **booléen**. En PHP 7.2 l'ordre des
 * ex æquo est celui observé ci-dessous ; PHP 8.0 (step-05) rend le tri stable et déprécie ce comparateur : l'ordre
 * peut changer. Proposition § 6.5 (provisoire) : figer l'ordre observé.
 * Fiches : BH-404, BH-405, BH-406. Chaque test exécute 3 fois la commande : l'ordre est aussi déterministe.
 */
#[Group('zone-cli-report')]
final class ReportOrderingTest extends ConsoleTestCase
{
    #[Test]
    #[Group('BH-404')]
    #[Scenario('report-months')]
    public function bh404_three_ties_at_300_km(): void
    {
        $this->assertTieOrder('2025-11', '300', ['1-REP-001', '1-REP-002', '1-REP-003'], ['1-REP-001', '1-REP-002', '1-REP-003', '1-REP-008', '1-REP-004']);
    }

    #[Test]
    #[Group('BH-405')]
    #[Scenario('report-months')]
    public function bh405_three_ties_at_250_km(): void
    {
        $this->assertTieOrder('2026-01', '250', ['1-REP-002', '1-REP-003', '1-REP-004'], ['1-REP-001', '1-REP-002', '1-REP-003', '1-REP-004', '1-REP-006', '1-REP-008', '2-REP-007']);
    }

    #[Test]
    #[Group('BH-406')]
    #[Scenario('edge')]
    public function bh406_four_ties_at_100_km(): void
    {
        $this->assertTieOrder('2026-01', '100', ['1-TIE-001', '1-TIE-002', '1-TIE-003', '1-TIE-004'], ['1-LNG-040', '1-TIE-001', '1-TIE-002', '1-TIE-003', '1-TIE-004']);
    }

    /**
     * @param list<string> $ties   plaques ex æquo à $km, dans l'ordre observé
     * @param list<string> $plates ordre complet observé
     */
    private function assertTieOrder(string $month, string $km, array $ties, array $plates): void
    {
        for ($i = 1; $i <= 3; ++$i) {
            $run = $this->console(['fleet:report', "--month=$month"]);
            $this->assertConsoleOk($run);
            $tied = array_keys(array_filter(array_column(self::reportRows($run->stdout), 2, 0), static fn (string $v): bool => $v === $km));
            self::assertSame($ties, $tied, "exécution $i : ex æquo à $km km");
            self::assertSame($plates, self::reportPlates($run->stdout), "exécution $i : ordre complet");
        }
        $this->assertDbUnchanged();
    }
}
