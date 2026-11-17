<?php

declare(strict_types=1);

namespace Flotte\Tests\Cli;

use Flotte\Tests\Support\AcceptedChanges;
use Flotte\Tests\Support\ConsoleTestCase;
use Flotte\Tests\Support\Db;
use Flotte\Tests\Support\Golden;
use Flotte\Tests\Support\Scenario;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * `fleet:demo:load` — rechargement destructif des données de démonstration (tmp/tests-application.md § 6.3,
 * BH-450 à BH-460). La commande **détruit** la base de la cible : chaque test restaure son scénario (setUp) et ne
 * dépend d'aucun autre. A4 : base exactement égale au scénario demo (empreinte tests/fixtures/db/demo.sqlite.sha256).
 */
#[Group('zone-cli-demo-load')]
final class FleetDemoLoadTest extends ConsoleTestCase
{
    private const LOADED = "Données de démonstration chargées : 5 véhicules, 5 réservations.\n";
    /** Description de la commande, sans accents (texte exact contractuel, ACC-002). */
    private const DESCRIPTION = 'Vide la base et charge les donnees de demonstration';

    #[Test]
    #[Group('BH-450')]
    #[Scenario('empty')]
    public function bh450_loads_demo_data_into_empty_database(): void
    {
        $run = $this->console(['fleet:demo:load']);
        $this->assertConsoleOk($run);
        self::assertSame(self::LOADED, $run->stdout, 'A3 stdout exact (une ligne)');
        $this->assertDbIsDemo();
    }

    #[Test]
    #[Group('BH-451')]
    #[Scenario('nominal')]
    public function bh451_replaces_realistic_fleet_without_asking(): void
    {
        self::assertSame([['v' => 24, 'r' => 476]], Db::query($this->target(), 'SELECT (SELECT COUNT(*) FROM vehicle) AS v, (SELECT COUNT(*) FROM reservation) AS r'));
        $run = $this->console(['fleet:demo:load']);
        $this->assertConsoleOk($run);
        self::assertSame(self::LOADED, $run->stdout);
        $this->assertDbIsDemo();
        self::assertSame([['v' => 1, 'r' => 1]], Db::query($this->target(), 'SELECT (SELECT MIN(id) FROM vehicle) AS v, (SELECT MIN(id) FROM reservation) AS r'), 'ids repartent à 1');
    }

    #[Test]
    #[Group('BH-452')]
    #[Scenario('anomalies')]
    public function bh452_inconsistent_data_removed_without_error(): void
    {
        $run = $this->console(['fleet:demo:load']);
        $this->assertConsoleOk($run);
        self::assertSame(self::LOADED, $run->stdout);
        $this->assertDbIsDemo();
    }

    #[Test]
    #[Group('BH-453')]
    #[Scenario('edge')]
    public function bh453_edge_data_removed_including_non_normalized_plates(): void
    {
        self::assertSame([['r' => 75]], Db::query($this->target(), 'SELECT COUNT(*) AS r FROM reservation'));
        $run = $this->console(['fleet:demo:load']);
        $this->assertConsoleOk($run);
        self::assertSame(self::LOADED, $run->stdout);
        $this->assertDbIsDemo();
    }

    #[Test]
    #[Group('BH-454')]
    #[Scenario('demo')]
    public function bh454_running_twice_is_idempotent(): void
    {
        foreach ([1, 2] as $i) {
            $run = $this->console(['fleet:demo:load']);
            $this->assertConsoleOk($run);
            self::assertSame(self::LOADED, $run->stdout, "exécution $i");
            $this->assertDbIsDemo();
        }
    }

    #[Test]
    #[Group('BH-455')]
    #[Scenario('demo')]
    public function bh455_non_interactive_flag_changes_nothing(): void
    {
        $run = $this->console(['fleet:demo:load', '-n']);
        $this->assertConsoleOk($run);
        self::assertSame(self::LOADED, $run->stdout);
        $this->assertDbIsDemo();
    }

    #[Test]
    #[Group('BH-456')]
    #[Scenario('nominal')]
    public function bh456_quiet_prints_nothing_but_still_loads(): void
    {
        $run = $this->console(['fleet:demo:load', '-q']);
        $this->assertConsoleOk($run);
        self::assertSame('', $run->stdout, 'A3 aucune sortie');
        $this->assertDbIsDemo();
    }

    #[Test]
    #[Group('BH-457')]
    #[Scenario('nominal')]
    public function bh457_runs_in_prod_environment_without_guard(): void
    {
        $run = $this->console(['fleet:demo:load', '--env=prod']);
        $this->assertConsoleOk($run);
        self::assertSame(self::LOADED, $run->stdout);
        $this->assertDbIsDemo();
    }

    #[Test]
    #[Group('BH-458')]
    #[Scenario('demo')]
    public function bh458_extra_argument_is_refused_without_touching_data(): void
    {
        $run = $this->console(['fleet:demo:load', 'extra']);
        $this->assertConsoleError(1, 'Too many arguments, expected arguments "command".', $run);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-459')]
    #[Scenario('demo')]
    public function bh459_help_and_list_describe_without_accents(): void
    {
        // ACC-002 (à partir de step-01) : seule la partie propre à fleet:demo:load est contractuelle ; avant step-01,
        // la sortie complète reste comparée aux goldens step-00.
        $legacy = !AcceptedChanges::applies('ACC-002', $this->target());

        $help = $this->console(['help', 'fleet:demo:load']);
        $this->assertConsoleOk($help);
        self::assertSame(
            ['usage' => 'fleet:demo:load', 'arguments' => [], 'options' => [], 'help' => self::DESCRIPTION],
            self::helpContract(['help', 'fleet:demo:load'], $help->stdout),
            'A3 help : partie propre à fleet:demo:load (aucune option propre) — '.$help->describe(),
        );
        if ($legacy) {
            Golden::assertMatchesGolden('cli/help/fleet-demo-load.txt', self::helpText($help->stdout), 'A3 help (step-00) — '.$help->describe());
        }

        $list = $this->console(['list']);
        $this->assertConsoleOk($list);
        self::assertSame(self::DESCRIPTION, self::helpContract(['list'], $list->stdout)['fleet:demo:load'] ?? null, 'A3 list : description sans accents — '.$list->describe());
        if ($legacy) {
            Golden::assertMatchesGolden('cli/help/list.txt', self::helpText($list->stdout), 'A3 list (step-00) — '.$list->describe());
        }
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-460')]
    #[Scenario('nominal')]
    public function bh460_after_loading_pages_api_and_report_match_demo_fiches(): void
    {
        $run = $this->console(['fleet:demo:load']);
        $this->assertConsoleOk($run);
        $this->assertDbIsDemo();

        $list = $this->send('GET', '/vehicules');
        $this->assertStatus(200, $list);
        self::assertSame(self::existingGolden('http/vehicles-list/bh010.html'), $this->html($list), 'liste = BH-010');

        $api = $this->send('GET', '/api/vehicules');
        $this->assertStatus(200, $api);
        self::assertSame(self::existingGolden('http/api-vehicles/demo.json'), $api->body, 'API = BH-201 (octet par octet)');

        $report = $this->console(['fleet:report', '--month=2016-05']);
        $this->assertConsoleOk($report);
        self::assertSame(self::existingGolden('cli/report/demo-2016-05.txt'), $report->stdout, 'rapport = BH-400');
        $this->assertDbIsDemo();
    }
}
