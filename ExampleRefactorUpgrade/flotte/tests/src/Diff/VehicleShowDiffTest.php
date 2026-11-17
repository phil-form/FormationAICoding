<?php

declare(strict_types=1);

namespace Flotte\Tests\Diff;

use Flotte\Tests\Support\DiffTestCase;
use Flotte\Tests\Support\Scenario;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/** Variantes comparatives app ↔ app-ref, route `vehicle_show` (fiches sensibles à la migration). */
#[Group('zone-vehicle-show')]
final class VehicleShowDiffTest extends DiffTestCase
{
    #[Test]
    #[Group('BH-040')]
    #[Scenario('demo')]
    public function bh040_vehicle_page_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', '/vehicules/1');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-041')]
    #[Scenario('demo')]
    public function bh041_history_order_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', '/vehicules/2');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-043')]
    #[Scenario('edge')]
    public function bh043_empty_history_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', '/vehicules/1');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-046')]
    #[Scenario('edge')]
    public function bh046_same_start_order_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', '/vehicules/22');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-048')]
    #[Scenario('edge')]
    public function bh048_cost_rounding_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', '/vehicules/25');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-049')]
    #[Scenario('edge')]
    public function bh049_escaping_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', '/vehicules/18');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-050')]
    #[Scenario('edge')]
    public function bh050_dst_times_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', '/vehicules/24');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-051')]
    #[Scenario('anomalies')]
    public function bh051_anomalies_pages_and_php_log_same_as_reference(): void
    {
        foreach (['/vehicules/1', '/vehicules/6', '/vehicules/7', '/vehicules/8'] as $path) {
            $this->assertSameAsReference('GET', $path);
            if ('/vehicules/8' === $path) {
                // Contrôle du mécanisme A5 : coût « N/A » → warning number_format() journalisé par la référence.
                self::assertNotEmpty(
                    array_filter($this->lastRefLog, static fn (string $s): bool => str_contains($s, 'number_format()')),
                    'A5 : warning number_format() attendu dans le journal de app-ref — '.implode(' | ', $this->lastRefLog),
                );
            }
        }
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-052')]
    #[Scenario('demo')]
    public function bh052_leading_zero_id_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', '/vehicules/01');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-053')]
    #[Scenario('demo')]
    public function bh053_unknown_id_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', '/vehicules/999');
        $this->assertSameAsReference('GET', '/vehicules/0');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-054')]
    #[Scenario('demo')]
    public function bh054_invalid_or_huge_id_same_as_reference(): void
    {
        foreach (['/vehicules/abc', '/vehicules/-1', '/vehicules/99999999999999999999'] as $path) {
            $this->assertSameAsReference('GET', $path);
        }
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-055')]
    #[Scenario('demo')]
    public function bh055_trailing_slash_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', '/vehicules/1/');
        $this->assertBothDbUnchanged();
    }
}
