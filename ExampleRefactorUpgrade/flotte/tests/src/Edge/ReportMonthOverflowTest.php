<?php

declare(strict_types=1);

namespace Flotte\Tests\Edge;

use Flotte\Tests\Support\ConsoleTestCase;
use Flotte\Tests\Support\Scenario;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * Piège : `--month` est lu par `DateTime::createFromFormat` sans contrôle de débordement : un mois 13 ou 00 est
 * accepté, le titre reprend la saisie, les données sont celles du mois « normalisé ». Sensible à PHP 8
 * (createFromFormat) ; proposition § 6.5 (provisoire) : préserver.
 * Fiches : BH-412, BH-413. Chaque variante est comparée au rapport du mois réel correspondant (hors titre).
 */
#[Group('zone-cli-report')]
final class ReportMonthOverflowTest extends ConsoleTestCase
{
    #[Test]
    #[Group('BH-412')]
    #[Scenario('report-months')]
    public function bh412_month_13_reports_next_january(): void
    {
        $this->assertSameTableAs('2025-13', '2026-01');
    }

    #[Test]
    #[Group('BH-413')]
    #[Scenario('report-months')]
    public function bh413_month_00_and_13_overflow_to_neighbour_months(): void
    {
        $this->assertSameTableAs('2026-00', '2025-12');
        $this->assertSameTableAs('2016-13', '2017-01');
        foreach (['0000-01', '9999-12'] as $month) {
            $run = $this->console(['fleet:report', "--month=$month"]);
            $this->assertConsoleOk($run);
            self::assertSame("Rapport de flotte - $month", self::firstLine($run->stdout));
            self::assertSame([['TOTAL', '', '0', '0,00 €']], self::reportRows($run->stdout), "$month accepté, tableau vide");
        }
    }

    private function assertSameTableAs(string $typed, string $real): void
    {
        $overflow = $this->console(['fleet:report', "--month=$typed"]);
        $this->assertConsoleOk($overflow);
        $reference = $this->console(['fleet:report', "--month=$real"]);
        $this->assertConsoleOk($reference);
        self::assertSame("Rapport de flotte - $typed", self::firstLine($overflow->stdout), 'titre = saisie');
        self::assertSame(self::withoutFirstLine($reference->stdout), self::withoutFirstLine($overflow->stdout), "$typed = données de $real");
        $this->assertDbUnchanged();
    }
}
