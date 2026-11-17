<?php

declare(strict_types=1);

namespace Flotte\Tests\Edge;

use Flotte\Tests\Support\CaseTestCase;
use Flotte\Tests\Support\HttpResponse;
use Flotte\Tests\Support\Scenario;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * Piège : comparaison lâche chaîne/nombre sur le filtre `GET /vehicules?status=` (PHP `0 != 'abc'`, Twig
 * `status == value`) et lecture d'un paramètre tableau.
 * Version qui change le comportement : PHP 8.0 (step-05, « string to number comparison ») pour BH-015 à BH-017 ;
 * Symfony 6 (`InputBag::get()` sur un tableau → exception, step-06) pour BH-018.
 * Fiches : BH-015, BH-016, BH-017, BH-018 (tmp/tests-application.md, VehicleController).
 * Comportement step-00 figé (bug pour bug, proposition § 4 appliquée, en attente de décision).
 */
#[Group('zone-vehicles-list')]
final class LooseStatusFilterTest extends CaseTestCase
{
    private const AVAILABLE = ['1-ABC-123', '1-BCD-234', '2-EFG-567'];

    private function assertFilterResult(string $query, array $plates, array $selected, string $why): HttpResponse
    {
        $r = $this->send('GET', '/vehicules?'.$query);
        $this->assertStatus(200, $r);
        $this->assertCommonHeaders($r);
        self::assertSame($plates, $this->listPlates($r), "Comportement historique changé ($why) : lignes listées pour ?$query");
        self::assertSame($selected, $this->selectedOptions($r), "Comportement historique changé ($why) : option sélectionnée pour ?$query");

        return $r;
    }

    #[Test]
    #[Group('BH-015')]
    #[Scenario('demo')]
    public function bh015_non_numeric_status_matches_available_vehicles(): void
    {
        $this->assertFilterResult('status=abc', self::AVAILABLE, ['0'], "PHP 7 : 'abc' == 0 → vrai ; PHP 8 : faux");
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-016')]
    #[Scenario('demo')]
    public function bh016_numeric_prefix_string_matches_status_zero(): void
    {
        $r = $this->assertFilterResult('status=0abc', self::AVAILABLE, ['0'], "PHP 7 : '0abc' == 0 (préfixe numérique)");
        self::assertSame(3, $r->crawler()->filter('table tbody tr')->count());
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-017')]
    #[Scenario('demo')]
    public function bh017_leading_zero_and_leading_space_match_status_one(): void
    {
        $this->assertFilterResult('status=01', ['1-CDE-345'], ['1'], "chaîne numérique '01' == 1");
        $this->assertFilterResult('status=%201', ['1-CDE-345'], ['1'], "chaîne numérique ' 1' == 1");
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-018')]
    #[Scenario('demo')]
    public function bh018_array_status_matches_nothing_and_never_fails(): void
    {
        $this->assertFilterResult('status%5B%5D=1', [], [], 'tableau ≠ entier : 0 ligne, 200 (Symfony 6 : exception → 400)');
        $this->assertDbUnchanged();
    }
}
