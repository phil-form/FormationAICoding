<?php

declare(strict_types=1);

namespace Flotte\Tests\Edge;

use Flotte\Tests\Support\CaseTestCase;
use Flotte\Tests\Support\Scenario;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * Piège : données legacy incohérentes (scénario anomalies) — index absent dans le tableau des libellés de statut
 * (notice PHP 7 → warning PHP 8), `number_format(null)` (déprécié 8.1), `number_format('N/A')` (warning PHP 7 →
 * TypeError PHP 8.0, 500 probable).
 * Fiches : BH-022 (liste), BH-051 (fiche véhicule). A5 (journal PHP) comparé en suite diff (proposition § 4).
 */
#[Group('zone-vehicles-list')]
#[Group('zone-vehicle-show')]
final class LegacyAnomaliesTest extends CaseTestCase
{
    #[Test]
    #[Group('BH-022')]
    #[Scenario('anomalies')]
    public function bh022_unknown_status_renders_empty_label_without_error(): void
    {
        $r = $this->send('GET', '/vehicules');
        $this->assertStatus(200, $r);
        $status = array_column($this->listRows($r), 3, 0);
        self::assertSame('', $status['1-ANO-001'] ?? null, 'Statut 3 : libellé vide attendu (notice PHP 7 sans interruption)');
        self::assertSame('', $status['1-ANO-002'] ?? null, 'Statut -1 : libellé vide attendu');
        $km = array_column($this->listRows($r), 2, 0);
        self::assertSame('-500 km', $km['1-ANO-003'] ?? null);
        self::assertSame('4.000 km', $km[''] ?? null, 'plaque vide listée');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-051')]
    #[Scenario('anomalies')]
    public function bh051_non_numeric_and_null_costs_render_without_error(): void
    {
        $r = $this->send('GET', '/vehicules/8');
        $this->assertStatus(200, $r);
        self::assertSame(
            ['09/01/2026 : Luc Peeters, 10 km,  €', '10/01/2026 : Nathalie Janssens, 10 km, 12,35 €'],
            $this->showItems($r, 1, true),
            "Coût 'N/A' : montant vide et page 200 attendus (PHP 8 : TypeError probable)",
        );

        $r = $this->send('GET', '/vehicules/6');
        $this->assertStatus(200, $r);
        self::assertSame(
            ['05/01/2026 : Jean Dupont, -100 km, -35,00 €', '06/01/2026 : Marie Lambert, 50 km, 0,00 €'],
            $this->showItems($r, 1),
            'Coût NULL → 0,00 € attendu (number_format(null), déprécié PHP 8.1)',
        );

        $r = $this->send('GET', '/vehicules/1');
        $this->assertStatus(200, $r);
        self::assertSame('Kilométrage : 1.000 km · Statut :', $r->crawler()->filter('p')->first()->text());
        $this->assertDbUnchanged();
    }
}
