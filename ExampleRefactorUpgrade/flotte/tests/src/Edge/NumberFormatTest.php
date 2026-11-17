<?php

declare(strict_types=1);

namespace Flotte\Tests\Edge;

use Flotte\Tests\Support\CaseTestCase;
use Flotte\Tests\Support\Scenario;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * Piège : formats numériques (`number_format`, flottants) — kilométrage `84.210 km` et coût `73,50 €`.
 * Versions concernées : PHP 8.0 (typage strict des fonctions internes), 8.1 (null déprécié), flottants et
 * `serialize_precision` ; filtres Twig 1 → 3.
 * Fiches : BH-020 (liste, edge), BH-048 (fiche véhicule, arrondis du coût).
 */
#[Group('zone-vehicles-list')]
#[Group('zone-vehicle-show')]
final class NumberFormatTest extends CaseTestCase
{
    #[Test]
    #[Group('BH-020')]
    #[Scenario('edge')]
    public function bh020_mileage_zero_and_int32_max(): void
    {
        $r = $this->send('GET', '/vehicules');
        $this->assertStatus(200, $r);
        $byPlate = array_column($this->listRows($r), 2, 0);
        self::assertSame('0 km', $byPlate['0-AAA-000'] ?? null, 'Format historique changé : 0 km');
        self::assertSame('2.147.483.647 km', $byPlate['9-ZZZ-999'] ?? null, 'Format historique changé : 2^31-1 km (séparateur « . »)');
        self::assertSame('99.999 km', $byPlate['1-MAX-050'] ?? null);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-048')]
    #[Scenario('edge')]
    public function bh048_cost_rounding_two_decimals_with_comma(): void
    {
        $r = $this->send('GET', '/vehicules/25');
        $this->assertStatus(200, $r);
        $costs = array_map(
            static fn (string $li): string => (string) preg_replace('/^.*, (-?\d+ km, .*)$/u', '$1', $li),
            $this->showItems($r, 1),
        );
        self::assertSame(
            ['1 km, 0,35 €', '3 km, 1,05 €', '7 km, 2,45 €', '13 km, 4,55 €', '101 km, 35,35 €', '0 km, 0,00 €',
                '2 km, 0,70 €', '5 km, 1,75 €', '999 km, 349,65 €'],
            $costs,
            'Arrondi ou format du coût historique changé (number_format, flottants)',
        );
        $this->assertDbUnchanged();
    }
}
