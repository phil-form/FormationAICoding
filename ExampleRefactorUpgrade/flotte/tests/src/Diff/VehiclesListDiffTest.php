<?php

declare(strict_types=1);

namespace Flotte\Tests\Diff;

use Flotte\Tests\Support\DiffTestCase;
use Flotte\Tests\Support\Scenario;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/** Variantes comparatives app ↔ app-ref, route `vehicle_index` (fiches sensibles à la migration). */
#[Group('zone-vehicles-list')]
final class VehiclesListDiffTest extends DiffTestCase
{
    #[Test]
    #[Group('BH-011')]
    #[Scenario('empty')]
    public function bh011_empty_list_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', '/vehicules');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-015')]
    #[Scenario('demo')]
    public function bh015_non_numeric_status_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', '/vehicules?status=abc');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-016')]
    #[Scenario('demo')]
    public function bh016_numeric_prefix_status_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', '/vehicules?status=0abc');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-017')]
    #[Scenario('demo')]
    public function bh017_leading_zero_and_space_status_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', '/vehicules?status=01');
        $this->assertSameAsReference('GET', '/vehicules?status=%201');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-018')]
    #[Scenario('demo')]
    public function bh018_array_status_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', '/vehicules?status%5B%5D=1');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-019')]
    #[Scenario('edge')]
    public function bh019_edge_plate_order_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', '/vehicules');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-020')]
    #[Scenario('edge')]
    public function bh020_edge_number_format_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', '/vehicules');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-021')]
    #[Scenario('edge')]
    public function bh021_edge_escaping_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', '/vehicules');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-022')]
    #[Scenario('anomalies')]
    public function bh022_anomalies_list_and_php_log_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', '/vehicules');
        // Contrôle du mécanisme A5 : la référence journalise bien les notices de la fiche.
        self::assertSame(
            ['PHP Notice: Undefined offset: 3', 'PHP Notice: Undefined offset: -1'],
            $this->lastRefLog,
            'A5 : signatures attendues dans le journal de app-ref',
        );
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-023')]
    #[Scenario('demo')]
    public function bh023_admin_list_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', '/vehicules', ['gestionnaire', 'flotte2016']);
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-024')]
    #[Scenario('demo')]
    public function bh024_wrong_credentials_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', '/vehicules', ['gestionnaire', 'mauvais']);
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-025')]
    #[Scenario('demo')]
    public function bh025_trailing_slash_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', '/vehicules/');
        $this->assertBothDbUnchanged();
    }

    /**
     * BH-028 : même contenu sur 500 véhicules, et temps de réponse app ≤ 2 × app-ref (médiane de 5 mesures
     * alternées après un échauffement, pour absorber le bruit ; seuil : proposition § 4).
     */
    #[Test]
    #[Group('BH-028')]
    #[Scenario('volume')]
    public function bh028_volume_list_content_and_performance_vs_reference(): void
    {
        $this->assertSameAsReference('GET', '/vehicules');
        $times = ['ref' => [], 'app' => []];
        for ($i = 0; $i < 5; ++$i) {
            foreach (['ref', 'app'] as $target) {
                $times[$target][] = $this->send('GET', '/vehicules', null, $target)->durationMs;
            }
        }
        $median = static function (array $v): float {
            sort($v);

            return $v[intdiv(\count($v), 2)];
        };
        $ref = $median($times['ref']);
        $app = $median($times['app']);
        self::assertLessThanOrEqual(2 * $ref, $app, \sprintf('BH-028 performance : app %.1f ms > 2 × app-ref %.1f ms', $app, $ref));
        $this->assertBothDbUnchanged();
    }
}
