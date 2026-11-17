<?php

declare(strict_types=1);

namespace Flotte\Tests\Diff;

use Flotte\Tests\Support\ConsoleDiffTestCase;
use Flotte\Tests\Support\Db;
use Flotte\Tests\Support\Scenario;
use Flotte\Tests\Support\Target;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * `fleet:demo:load` : variante différentielle des fiches « Sensible : oui » (tmp/tests-application.md § 6.3) — même
 * scénario restauré sur app-ref et app, même commande ; sorties identiques et **deux bases égales au scénario demo**
 * (dumps canoniques identiques entre eux). Commande destructive : chaque test restaure son scénario.
 */
#[Group('zone-cli-demo-load')]
final class FleetDemoLoadDiffTest extends ConsoleDiffTestCase
{
    /** @param list<string> $options */
    private function sameLoad(array $options = []): void
    {
        $this->assertConsoleSameAsReference(array_merge(['fleet:demo:load'], $options));
        $this->assertBothDbAreDemo();
        self::assertSame(Db::dump(Target::REF), Db::dump(Target::APP), 'A4 dump canonique app ≠ app-ref');
    }

    #[Test]
    #[Group('BH-450')]
    #[Scenario('empty')]
    public function bh450_load_into_empty_database_same_as_reference(): void
    {
        $this->sameLoad();
    }

    #[Test]
    #[Group('BH-451')]
    #[Scenario('nominal')]
    public function bh451_replace_realistic_fleet_same_as_reference(): void
    {
        $this->sameLoad();
    }

    #[Test]
    #[Group('BH-452')]
    #[Scenario('anomalies')]
    public function bh452_replace_inconsistent_data_same_as_reference(): void
    {
        $this->sameLoad();
    }

    #[Test]
    #[Group('BH-455')]
    #[Scenario('demo')]
    public function bh455_non_interactive_same_as_reference(): void
    {
        $this->sameLoad(['-n']);
    }

    #[Test]
    #[Group('BH-457')]
    #[Scenario('nominal')]
    public function bh457_prod_environment_same_as_reference(): void
    {
        $this->sameLoad(['--env=prod']);
    }

    #[Test]
    #[Group('BH-458')]
    #[Scenario('demo')]
    public function bh458_extra_argument_same_as_reference(): void
    {
        $this->assertConsoleSameAsReference(['fleet:demo:load', 'extra']);
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-459')]
    #[Scenario('demo')]
    public function bh459_help_and_list_same_as_reference(): void
    {
        $this->assertConsoleSameAsReference(['help', 'fleet:demo:load'], true);
        $this->assertConsoleSameAsReference(['list'], true);
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-460')]
    #[Scenario('nominal')]
    public function bh460_pages_api_and_report_after_loading_same_as_reference(): void
    {
        $this->sameLoad();
        $this->assertSameAsReference('GET', '/vehicules');
        $api = $this->assertSameAsReference('GET', '/api/vehicules');
        self::assertSame($api['ref']->body, $api['app']->body, 'API octet par octet');
        $this->assertConsoleSameAsReference(['fleet:report', '--month=2016-05']);
        $this->assertBothDbAreDemo();
    }
}
