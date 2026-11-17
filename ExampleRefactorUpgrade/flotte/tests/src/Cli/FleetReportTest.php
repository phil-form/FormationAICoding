<?php

declare(strict_types=1);

namespace Flotte\Tests\Cli;

use Flotte\Tests\Support\ConsoleTestCase;
use Flotte\Tests\Support\Golden;
use Flotte\Tests\Support\Normalizer;
use Flotte\Tests\Support\Scenario;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * `fleet:report [-m|--month=AAAA-MM]` — rapport mensuel d'utilisation (tmp/tests-application.md § 6.2,
 * BH-400 à BH-425). Lecture seule : A4 = base inchangée pour toutes les fiches.
 * Goldens : tests/golden/cli/report/<mois>.txt (scénario report-months), <scénario>-<mois>.txt sinon ; stdout comparé
 * octet par octet. A5 : aucune entrée de journal PHP sur données saines.
 */
#[Group('zone-cli-report')]
final class FleetReportTest extends ConsoleTestCase
{
    private const JANUARY_PLATES = ['1-REP-001', '1-REP-002', '1-REP-003', '1-REP-004', '1-REP-006', '1-REP-008', '2-REP-007'];
    private const INVALID_FORMAT = "Format de mois invalide (attendu : AAAA-MM).\n";

    #[Test]
    #[Group('BH-400')]
    #[Scenario('demo')]
    public function bh400_readme_example_for_may_2016(): void
    {
        $run = $this->console(['fleet:report', '--month=2016-05']);
        $this->assertConsoleOk($run);
        $this->assertStdoutGolden('cli/report/demo-2016-05.txt', $run);
        self::assertSame('Rapport de flotte - 2016-05', self::firstLine($run->stdout));
        self::assertSame([
            ['1-BCD-234', '2', '450', '157,50 €'],
            ['1-ABC-123', '1', '210', '73,50 €'],
            ['2-EFG-567', '1', '157', '54,95 €'],
            ['TOTAL', '', '817', '285,95 €'],
        ], self::reportRows($run->stdout));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-401')]
    #[Scenario('report-months')]
    public function bh401_january_report_table_with_total(): void
    {
        $run = $this->console(['fleet:report', '--month=2026-01']);
        $this->assertConsoleOk($run);
        $this->assertStdoutGolden('cli/report/2026-01.txt', $run);
        self::assertSame('Rapport de flotte - 2026-01', self::firstLine($run->stdout));
        self::assertSame(self::JANUARY_PLATES, self::reportPlates($run->stdout));
        self::assertSame(['1.707', '597,45 €'], self::reportTotal($run->stdout));
        self::assertMatchesRegularExpression('/^\+-+\+-+\+-+\+-+\+$/m', $run->stdout, 'bordures +---+');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-402')]
    #[Scenario('report-months')]
    public function bh402_default_month_follows_container_clock(): void
    {
        $default = $this->console(['fleet:report']);
        $this->assertConsoleOk($default);
        $explicit = $this->console(['fleet:report', '--month=2026-01']);
        $this->assertConsoleOk($explicit);
        self::assertSame($explicit->stdout, $default->stdout, 'Sans --month : mois courant de l\'horloge (2026-01)');
        $this->assertStdoutGolden('cli/report/2026-01.txt', $default);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-403')]
    #[Scenario('report-months')]
    public function bh403_short_attached_and_spaced_option_syntaxes_are_equivalent(): void
    {
        foreach ([['-m', '2025-11'], ['-m2025-11'], ['--month', '2025-11']] as $syntax) {
            $run = $this->console(array_merge(['fleet:report'], $syntax));
            $this->assertConsoleOk($run);
            $this->assertStdoutGolden('cli/report/2025-11.txt', $run);
        }
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-404')]
    #[Scenario('report-months')]
    public function bh404_ties_at_300_km_keep_observed_order(): void
    {
        $run = $this->console(['fleet:report', '--month=2025-11']);
        $this->assertConsoleOk($run);
        $this->assertStdoutGolden('cli/report/2025-11.txt', $run);
        self::assertSame(['1-REP-001', '1-REP-002', '1-REP-003', '1-REP-008', '1-REP-004'], self::reportPlates($run->stdout));
        self::assertSame(['1-REP-003', '2', '300', '105,00 €'], self::reportRows($run->stdout)[2]);
        self::assertSame(['904', '316,40 €'], self::reportTotal($run->stdout));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-405')]
    #[Scenario('report-months')]
    public function bh405_ties_at_250_km_keep_observed_order(): void
    {
        $run = $this->console(['fleet:report', '--month=2026-01']);
        $this->assertConsoleOk($run);
        $this->assertStdoutGolden('cli/report/2026-01.txt', $run);
        $rows = self::reportRows($run->stdout);
        self::assertSame(
            [['1-REP-002', '1', '250', '87,50 €'], ['1-REP-003', '1', '250', '87,50 €'], ['1-REP-004', '3', '250', '87,50 €']],
            \array_slice($rows, 1, 3),
        );
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-406')]
    #[Scenario('edge')]
    public function bh406_four_ties_at_100_km_on_edge_data(): void
    {
        $run = $this->console(['fleet:report', '--month=2026-01']);
        $this->assertConsoleOk($run);
        $this->assertStdoutGolden('cli/report/edge-2026-01.txt', $run);
        self::assertSame(['1-LNG-040', '1-TIE-001', '1-TIE-002', '1-TIE-003', '1-TIE-004'], self::reportPlates($run->stdout));
        self::assertSame('265', self::reportRows($run->stdout)[0][2]);
        self::assertSame(['665', '232,75 €'], self::reportTotal($run->stdout));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-407')]
    #[Scenario('report-months')]
    public function bh407_month_without_data_gives_header_and_zero_total(): void
    {
        $run = $this->console(['fleet:report', '--month=2025-12']);
        $this->assertConsoleOk($run);
        $this->assertStdoutGolden('cli/report/2025-12.txt', $run);
        self::assertSame([], self::reportPlates($run->stdout));
        self::assertSame([['TOTAL', '', '0', '0,00 €']], self::reportRows($run->stdout));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-408')]
    #[Scenario('report-months')]
    public function bh408_month_bounds_on_end_date(): void
    {
        $run = $this->console(['fleet:report', '--month=2026-01']);
        $this->assertConsoleOk($run);
        $this->assertStdoutGolden('cli/report/2026-01.txt', $run);
        $km = array_column(self::reportRows($run->stdout), 2, 0);
        self::assertSame('900', $km['1-REP-001'] ?? null, 'fin le 01/01/2026 00:00:00 comptée');
        self::assertSame('44', $km['1-REP-006'] ?? null, 'réservation à cheval sur le Nouvel An comptée');
        self::assertSame('13', $km['1-REP-008'] ?? null, 'dernière seconde de janvier comptée');
        self::assertNotContains('101', $km, 'fin le 01/02/2026 00:00:00 exclue');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-409')]
    #[Scenario('report-months')]
    public function bh409_february_with_very_large_numbers(): void
    {
        $run = $this->console(['fleet:report', '--month=2026-02']);
        $this->assertConsoleOk($run);
        $this->assertStdoutGolden('cli/report/2026-02.txt', $run);
        $rows = self::reportRows($run->stdout);
        self::assertContains('101', array_column($rows, 2), 'réservation exclue de janvier comptée en février');
        self::assertContains('999.999', array_column($rows, 2));
        self::assertContains('349 999,65 €', array_column($rows, 3));
        self::assertSame(['1.000.112', '350 039,20 €'], self::reportTotal($run->stdout));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-410')]
    #[Scenario('report-months')]
    public function bh410_bookings_around_dst_changes_are_counted(): void
    {
        $costs = [];
        foreach (['2025-10', '2026-03'] as $month) {
            $run = $this->console(['fleet:report', "--month=$month"]);
            $this->assertConsoleOk($run);
            $this->assertStdoutGolden("cli/report/$month.txt", $run);
            self::assertNotSame([], self::reportPlates($run->stdout), "$month : réservations comptées");
            foreach (self::reportRows($run->stdout) as $row) {
                $costs[] = $row[2].' km = '.$row[3];
            }
        }
        self::assertContains('7 km = 2,45 €', $costs);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-411')]
    #[Scenario('report-months')]
    public function bh411_leap_day_counted_by_end_date(): void
    {
        // Lignes observées à l'implémentation : février 2024 = 1-REP-001 (120 km, 29 février compté) ; mars 2024 =
        // 1-REP-002 (80 km, réservation du 29/02 au 01/03 08:00 comptée par sa date de fin).
        $expected = [
            '2024-02' => [['1-REP-001', '1', '120', '42,00 €'], ['TOTAL', '', '120', '42,00 €']],
            '2024-03' => [['1-REP-002', '1', '80', '28,00 €'], ['TOTAL', '', '80', '28,00 €']],
        ];
        foreach ($expected as $month => $rows) {
            $run = $this->console(['fleet:report', "--month=$month"]);
            $this->assertConsoleOk($run);
            $this->assertStdoutGolden("cli/report/$month.txt", $run);
            self::assertSame($rows, self::reportRows($run->stdout), "$month");
        }
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-412')]
    #[Scenario('report-months')]
    public function bh412_month_13_is_accepted_with_next_january_data(): void
    {
        $run = $this->console(['fleet:report', '--month=2025-13']);
        $this->assertConsoleOk($run);
        $this->assertStdoutGolden('cli/report/2025-13.txt', $run);
        self::assertSame('Rapport de flotte - 2025-13', self::firstLine($run->stdout));
        self::assertSame(self::JANUARY_PLATES, self::reportPlates($run->stdout), 'données de janvier 2026');
        self::assertSame(['1.707', '597,45 €'], self::reportTotal($run->stdout));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-413')]
    #[Scenario('report-months')]
    public function bh413_out_of_range_months_are_accepted(): void
    {
        foreach (['2026-00', '2016-13', '0000-01', '9999-12'] as $month) {
            $run = $this->console(['fleet:report', "--month=$month"]);
            $this->assertConsoleOk($run);
            $this->assertStdoutGolden("cli/report/$month.txt", $run);
            self::assertSame("Rapport de flotte - $month", self::firstLine($run->stdout));
            self::assertSame([['TOTAL', '', '0', '0,00 €']], self::reportRows($run->stdout), "$month : tableau vide");
        }
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-414')]
    #[Scenario('report-months')]
    public function bh414_invalid_month_format_message_on_stdout_with_code_1(): void
    {
        foreach (['abc', '2026-1', '26-01', '2026/01', '2026-01-15', ' 2026-01', '2026-01 '] as $month) {
            $run = $this->console(['fleet:report', "--month=$month"]);
            self::assertSame(1, $run->exit, 'A1 — '.$run->describe());
            self::assertSame('', $run->stderr, 'A2 stderr vide — '.$run->describe());
            self::assertSame(self::INVALID_FORMAT, $run->stdout, 'A3 message sur stdout — '.$run->describe());
            self::assertSame([], $run->log, 'A5 — '.$run->describe());
        }
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-415')]
    #[Scenario('report-months')]
    public function bh415_empty_month_option_requires_a_value(): void
    {
        $run = $this->console(['fleet:report', '--month=']);
        $this->assertConsoleError(1, 'The "--month" option requires a value.', $run);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-416')]
    #[Scenario('anomalies')]
    public function bh416_non_numeric_cost_aborts_in_dev(): void
    {
        $run = $this->console(['fleet:report', '--month=2026-01']);
        $this->assertConsoleError(1, 'Warning: A non-numeric value encountered', $run);
        self::assertSame([], $run->log, 'A5 : exception non journalisée (observé à l\'implémentation)');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-417')]
    #[Scenario('anomalies')]
    public function bh417_non_numeric_cost_ignored_in_prod(): void
    {
        $run = $this->console(['fleet:report', '--month=2026-01', '--env=prod']);
        self::assertSame(0, $run->exit, 'A1 — '.$run->describe());
        self::assertSame('', $run->stderr, 'A2 — '.$run->describe());
        self::assertSame(['PHP Warning: A non-numeric value encountered'], $run->log, 'A5 (observé à l\'implémentation) — '.$run->describe());
        $this->assertStdoutGolden('cli/report/anomalies-2026-01-prod.txt', $run);
        self::assertSame([
            ['1-ANO-001', '1', '100', '35,00 €'],
            ['1-ANO-008', '2', '20', '12,35 €'],
            ['1-ANO-006', '2', '-50', '-35,00 €'],
            ['1-ANO-007', '1', '-7.000', '0,00 €'],
            ['TOTAL', '', '-6.930', '12,35 €'],
        ], self::reportRows($run->stdout));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-418')]
    #[Scenario('demo')]
    public function bh418_unknown_option_is_refused(): void
    {
        $run = $this->console(['fleet:report', '--mois=2026-01']);
        $this->assertConsoleError(1, 'The "--mois" option does not exist.', $run);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-419')]
    #[Scenario('demo')]
    public function bh419_positional_month_is_refused(): void
    {
        $run = $this->console(['fleet:report', '2026-01']);
        $this->assertConsoleError(1, 'Too many arguments, expected arguments "command".', $run);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-420')]
    #[Scenario('report-months')]
    public function bh420_quiet_prints_nothing(): void
    {
        $run = $this->console(['fleet:report', '--month=2026-01', '-q']);
        $this->assertConsoleOk($run);
        self::assertSame('', $run->stdout, 'A3 aucune sortie');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-421')]
    #[Scenario('report-months')]
    public function bh421_prod_environment_gives_same_report_on_sane_data(): void
    {
        $run = $this->console(['fleet:report', '--month=2026-01', '--env=prod']);
        $this->assertConsoleOk($run);
        $this->assertStdoutGolden('cli/report/2026-01.txt', $run);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-422')]
    #[Scenario('report-months')]
    public function bh422_ansi_colours_title_and_table_headers(): void
    {
        $esc = \chr(27);
        $run = $this->console(['fleet:report', '--month=2026-01', '--ansi']);
        $this->assertConsoleOk($run);
        $this->assertStdoutGolden('cli/report/2026-01-ansi.txt', $run);
        $green = static fn (string $s): string => $esc.'[32m'.$s.$esc.'[39m';
        $lines = explode("\n", $run->stdout);
        self::assertSame($green('Rapport de flotte - 2026-01'), $lines[0], 'titre en vert');
        // En-têtes du tableau en vert, cellule par cellule, espaces de remplissage compris (observé à l'implémentation).
        self::assertSame(
            '|'.$green(' Plaque    ').'|'.$green(' Réservations ').'|'.$green(' Km    ').'|'.$green(' Coût     ').'|',
            $lines[2],
        );
        self::assertSame(10, substr_count($run->stdout, $esc), 'titre et 4 en-têtes colorés, lignes de données sans couleur');
        self::assertSame(
            self::existingGolden('cli/report/2026-01.txt'),
            preg_replace('/'.$esc.'\[\d+m/', '', $run->stdout),
            'sans les séquences de couleur : sortie de BH-401',
        );
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-423')]
    #[Scenario('volume')]
    public function bh423_volume_report_content(): void
    {
        $run = $this->console(['fleet:report', '--month=2024-03']);
        $this->assertConsoleOk($run);
        $this->assertStdoutGolden('cli/report/volume-2024-03.txt', $run);
        self::assertCount(172, self::reportPlates($run->stdout));
        self::assertSame(['187.606', '65 662,10 €'], self::reportTotal($run->stdout));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-424')]
    #[Scenario('report-months')]
    public function bh424_report_never_writes_to_the_database(): void
    {
        foreach ([['--month=2026-01'], ['--month=2025-13'], ['--month=abc'], ['--month=2026-01', '--env=prod'], ['-q'], []] as $options) {
            $this->console(array_merge(['fleet:report'], $options));
            $this->assertDbUnchanged();
        }
    }

    #[Test]
    #[Group('BH-425')]
    #[Scenario('demo')]
    public function bh425_list_and_help_describe_the_report(): void
    {
        $list = $this->console(['list']);
        $this->assertConsoleOk($list);
        Golden::assertMatchesGolden('cli/help/list.txt', self::helpText($list->stdout), 'A3 list — '.$list->describe());
        self::assertMatchesRegularExpression("/^  fleet:report +Rapport mensuel d'utilisation de la flotte$/m", Normalizer::cli($list->stdout));

        $help = $this->console(['help', 'fleet:report']);
        $this->assertConsoleOk($help);
        Golden::assertMatchesGolden('cli/help/fleet-report.txt', self::helpText($help->stdout), 'A3 help — '.$help->describe());
        self::assertMatchesRegularExpression('/^  -m, --month=MONTH +Mois au format AAAA-MM \[default: "2026-01"\]$/m', Normalizer::cli($help->stdout), 'défaut = mois de l\'horloge du conteneur');
        self::assertStringContainsString("Rapport mensuel d'utilisation de la flotte", $help->stdout);
        $this->assertDbUnchanged();
    }
}
