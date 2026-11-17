<?php

declare(strict_types=1);

namespace Flotte\Tests\Diff;

use Flotte\Tests\Support\ConsoleDiffTestCase;
use Flotte\Tests\Support\Scenario;
use Flotte\Tests\Support\Target;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * `fleet:report` : variante différentielle des fiches « Sensible : oui » (tmp/tests-application.md § 6.2) — même
 * scénario sur app-ref et app, même commande, A1 / A2 (message) / A3 (octets) / A5 identiques, bases inchangées.
 */
#[Group('zone-cli-report')]
final class FleetReportDiffTest extends ConsoleDiffTestCase
{
    /** @param list<list<string>> $commands */
    private function same(array $commands): void
    {
        foreach ($commands as $args) {
            $this->assertConsoleSameAsReference(array_merge(['fleet:report'], $args));
        }
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-400')]
    #[Scenario('demo')]
    public function bh400_readme_report_same_as_reference(): void
    {
        $this->same([['--month=2016-05']]);
    }

    #[Test]
    #[Group('BH-401')]
    #[Scenario('report-months')]
    public function bh401_table_rendering_same_as_reference(): void
    {
        $this->same([['--month=2026-01']]);
    }

    #[Test]
    #[Group('BH-402')]
    #[Scenario('report-months')]
    public function bh402_default_month_same_as_reference(): void
    {
        $this->same([[]]);
    }

    #[Test]
    #[Group('BH-403')]
    #[Scenario('report-months')]
    public function bh403_option_syntaxes_same_as_reference(): void
    {
        $this->same([['-m', '2025-11'], ['-m2025-11'], ['--month', '2025-11']]);
    }

    #[Test]
    #[Group('BH-404')]
    #[Scenario('report-months')]
    public function bh404_tie_order_at_300_km_same_as_reference(): void
    {
        $this->same([['--month=2025-11']]);
    }

    #[Test]
    #[Group('BH-405')]
    #[Scenario('report-months')]
    public function bh405_tie_order_at_250_km_same_as_reference(): void
    {
        $this->same([['--month=2026-01']]);
    }

    #[Test]
    #[Group('BH-406')]
    #[Scenario('edge')]
    public function bh406_tie_order_on_edge_data_same_as_reference(): void
    {
        $this->same([['--month=2026-01']]);
    }

    #[Test]
    #[Group('BH-407')]
    #[Scenario('report-months')]
    public function bh407_empty_table_same_as_reference(): void
    {
        $this->same([['--month=2025-12']]);
    }

    #[Test]
    #[Group('BH-408')]
    #[Scenario('report-months')]
    public function bh408_month_bounds_same_as_reference(): void
    {
        $this->same([['--month=2026-01']]);
    }

    #[Test]
    #[Group('BH-409')]
    #[Scenario('report-months')]
    public function bh409_large_numbers_same_as_reference(): void
    {
        $this->same([['--month=2026-02']]);
    }

    #[Test]
    #[Group('BH-410')]
    #[Scenario('report-months')]
    public function bh410_dst_months_same_as_reference(): void
    {
        $this->same([['--month=2025-10'], ['--month=2026-03']]);
    }

    #[Test]
    #[Group('BH-412')]
    #[Scenario('report-months')]
    public function bh412_month_13_same_as_reference(): void
    {
        $this->same([['--month=2025-13']]);
    }

    #[Test]
    #[Group('BH-413')]
    #[Scenario('report-months')]
    public function bh413_out_of_range_months_same_as_reference(): void
    {
        $this->same([['--month=2026-00'], ['--month=2016-13'], ['--month=0000-01'], ['--month=9999-12']]);
    }

    #[Test]
    #[Group('BH-414')]
    #[Scenario('report-months')]
    public function bh414_invalid_formats_same_as_reference(): void
    {
        $this->same(array_map(
            static fn (string $m): array => ["--month=$m"],
            ['abc', '2026-1', '26-01', '2026/01', '2026-01-15', ' 2026-01', '2026-01 '],
        ));
    }

    #[Test]
    #[Group('BH-415')]
    #[Scenario('report-months')]
    public function bh415_missing_option_value_same_as_reference(): void
    {
        $this->same([['--month=']]);
    }

    #[Test]
    #[Group('BH-416')]
    #[Scenario('anomalies')]
    public function bh416_dev_failure_on_inconsistent_data_same_as_reference(): void
    {
        $this->same([['--month=2026-01']]);
    }

    #[Test]
    #[Group('BH-417')]
    #[Scenario('anomalies')]
    public function bh417_prod_report_on_inconsistent_data_same_as_reference(): void
    {
        $this->same([['--month=2026-01', '--env=prod']]);
    }

    #[Test]
    #[Group('BH-418')]
    #[Scenario('demo')]
    public function bh418_unknown_option_same_as_reference(): void
    {
        $this->same([['--mois=2026-01']]);
    }

    #[Test]
    #[Group('BH-419')]
    #[Scenario('demo')]
    public function bh419_positional_argument_same_as_reference(): void
    {
        $this->same([['2026-01']]);
    }

    #[Test]
    #[Group('BH-421')]
    #[Scenario('report-months')]
    public function bh421_prod_environment_same_as_reference(): void
    {
        $this->same([['--month=2026-01', '--env=prod']]);
    }

    #[Test]
    #[Group('BH-422')]
    #[Scenario('report-months')]
    public function bh422_ansi_output_same_as_reference(): void
    {
        $this->same([['--month=2026-01', '--ansi']]);
    }

    /** Contenu identique, puis durée app ≤ 2 × app-ref (médiane de 3 mesures de chaque côté ; jamais de durée absolue). */
    #[Test]
    #[Group('BH-423')]
    #[Scenario('volume')]
    public function bh423_volume_report_content_and_performance_vs_reference(): void
    {
        $args = ['fleet:report', '--month=2024-03'];
        $this->same([['--month=2024-03']]);
        $median = function (string $target) use ($args): float {
            $t = [];
            for ($i = 0; $i < 3; ++$i) {
                $t[] = $this->consoleSeconds($args, $target);
            }
            sort($t);

            return $t[1];
        };
        $ref = $median(Target::REF);
        $app = $median(Target::APP);
        self::assertLessThanOrEqual(2 * $ref, $app, sprintf('Durée app %.3f s > 2 × app-ref %.3f s', $app, $ref));
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-425')]
    #[Scenario('demo')]
    public function bh425_list_and_help_same_as_reference(): void
    {
        $this->assertConsoleSameAsReference(['list'], true);
        $this->assertConsoleSameAsReference(['help', 'fleet:report'], true);
        $this->assertBothDbUnchanged();
    }
}
