<?php

declare(strict_types=1);

namespace Flotte\Tests\Http;

use Flotte\Tests\Support\CaseTestCase;
use Flotte\Tests\Support\HttpResponse;
use Flotte\Tests\Support\Scenario;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * VehicleController, route `vehicle_index` — `GET /vehicules` (+ filtre `?status=`).
 * Fiches : tmp/tests-vehicle-controller.md § 3.2. Lecture seule : A4 = base inchangée.
 * A5 (journal PHP) comparé en suite diff uniquement (proposition § 4 appliquée, en attente de décision).
 */
#[Group('zone-vehicles-list')]
final class VehiclesListTest extends CaseTestCase
{
    private const GOLDEN = 'http/vehicles-list/';
    private const DEMO_ALL = ['1-ABC-123', '1-BCD-234', '1-CDE-345', '1-DEF-456', '2-EFG-567'];

    private function assertListPage(HttpResponse $r): void
    {
        $this->assertStatus(200, $r);
        $this->assertCommonHeaders($r);
        $this->assertSessionCookie($r);
        self::assertSame('Flotte Delvaux & Fils', $r->crawler()->filter('title')->text(), 'A3 titre');
        self::assertSame('Véhicules', $r->crawler()->filter('h1')->text(), 'A3 h1');
    }

    private function navLinks(HttpResponse $r): array
    {
        return $r->crawler()->filter('nav a')->each(static fn ($a): array => [(string) $a->attr('href'), $a->text()]);
    }

    #[Test]
    #[Group('BH-010')]
    #[Scenario('demo')]
    public function bh010_list_sorted_by_plate_with_formatted_columns(): void
    {
        $r = $this->send('GET', '/vehicules');

        $this->assertListPage($r);
        $this->assertGolden(self::GOLDEN.'bh010.html', $r);
        self::assertSame(self::DEMO_ALL, $this->listPlates($r));
        self::assertSame(['1-ABC-123', 'Renault Kangoo', '84.210 km', 'Disponible'], $this->listRows($r)[0]);
        self::assertSame('/vehicules/1', $r->crawler()->filter('table tbody tr td a')->first()->attr('href'));
        self::assertSame([], $this->selectedOptions($r), 'option « Tous » sans selected');
        self::assertSame([['/vehicules', 'Véhicules']], $this->navLinks($r), 'pas de lien « Ajouter » sans authentification');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-011')]
    #[Scenario('empty')]
    public function bh011_empty_list_shows_single_placeholder_row(): void
    {
        $r = $this->send('GET', '/vehicules');

        $this->assertListPage($r);
        $this->assertGolden(self::GOLDEN.'bh011.html', $r);
        self::assertSame([['Aucun véhicule.']], $this->listRows($r));
        self::assertSame('4', $r->crawler()->filter('table tbody td')->attr('colspan'));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-012')]
    #[Scenario('demo')]
    public function bh012_numeric_status_filter_selects_option(): void
    {
        $expected = [
            '0' => ['1-ABC-123', '1-BCD-234', '2-EFG-567'],
            '1' => ['1-CDE-345'],
            '2' => ['1-DEF-456'],
        ];
        foreach ($expected as $status => $plates) {
            $r = $this->send('GET', '/vehicules?status='.$status);
            $this->assertListPage($r);
            $this->assertGolden(self::GOLDEN."bh012-status-$status.html", $r);
            self::assertSame($plates, $this->listPlates($r), "status=$status");
            self::assertSame([(string) $status], $this->selectedOptions($r), "status=$status");
        }
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-013')]
    #[Scenario('demo')]
    public function bh013_empty_status_filter_lists_all(): void
    {
        $r = $this->send('GET', '/vehicules?status=');

        $this->assertListPage($r);
        $this->assertGolden(self::GOLDEN.'bh013.html', $r);
        self::assertSame(self::DEMO_ALL, $this->listPlates($r));
        self::assertSame([], $this->selectedOptions($r));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-014')]
    #[Scenario('demo')]
    public function bh014_unknown_numeric_status_shows_no_vehicle(): void
    {
        $r = $this->send('GET', '/vehicules?status=3');

        $this->assertListPage($r);
        $this->assertGolden(self::GOLDEN.'bh014.html', $r);
        self::assertSame([['Aucun véhicule.']], $this->listRows($r));
        self::assertSame([], $this->selectedOptions($r));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-015')]
    #[Scenario('demo')]
    public function bh015_status_filter_with_non_numeric_value(): void
    {
        $r = $this->send('GET', '/vehicules?status=abc');

        $this->assertListPage($r);
        $this->assertGolden(self::GOLDEN.'bh015-status-abc.html', $r);
        self::assertSame(['1-ABC-123', '1-BCD-234', '2-EFG-567'], $this->listPlates($r));
        self::assertSame(['0'], $this->selectedOptions($r), 'option « Disponible » sélectionnée (comparaison lâche)');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-016')]
    #[Scenario('demo')]
    public function bh016_status_filter_with_numeric_prefix_string(): void
    {
        $r = $this->send('GET', '/vehicules?status=0abc');

        $this->assertListPage($r);
        $this->assertGolden(self::GOLDEN.'bh016-status-0abc.html', $r);
        self::assertSame(['1-ABC-123', '1-BCD-234', '2-EFG-567'], $this->listPlates($r));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-017')]
    #[Scenario('demo')]
    public function bh017_status_filter_with_leading_zero_or_space(): void
    {
        foreach (['01' => 'bh017-status-01.html', '%201' => 'bh017-status-space-1.html'] as $value => $golden) {
            $r = $this->send('GET', '/vehicules?status='.$value);
            $this->assertListPage($r);
            $this->assertGolden(self::GOLDEN.$golden, $r);
            self::assertSame(['1-CDE-345'], $this->listPlates($r), "status=$value");
        }
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-018')]
    #[Scenario('demo')]
    public function bh018_status_filter_with_array_value(): void
    {
        $r = $this->send('GET', '/vehicules?status%5B%5D=1');

        $this->assertListPage($r);
        $this->assertGolden(self::GOLDEN.'bh018-status-array.html', $r);
        self::assertSame([['Aucun véhicule.']], $this->listRows($r));
        self::assertSame([], $this->selectedOptions($r));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-019')]
    #[Scenario('edge')]
    public function bh019_binary_plate_order_with_normalized_display(): void
    {
        $r = $this->send('GET', '/vehicules');

        $this->assertListPage($r);
        $this->assertGolden(self::GOLDEN.'bh019-edge.html', $r);
        self::assertSame([
            '2-BBB-004', '0-AAA-000', '1ABC12', '1-ADJ-001', '1-CST-001', '1-DST-001', '1-EDG-011', '1-EDG-012',
            '1-LNG-040', '1-MAX-050', '1-NEW-001', '1-OLD-001', '1-OVD-001', '1-SAM-001', '1-TIE-001', '1-TIE-002',
            '1-TIE-003', '1-TIE-004', '1-UNI-001', '1-XSS-001', '1-AAA-003', '2-BBB-006', '2-BBB-005', '9-ZZZ-999',
            'ABC123', 'CD1234', 'ZZZZ999',
        ], $this->listPlates($r), 'ordre binaire SQLite de la plaque brute, affichage normalisé');
        $hrefs = $r->crawler()->filter('table tbody tr td:first-child a')->each(static fn ($a): string => (string) $a->attr('href'));
        self::assertSame('/vehicules/4', $hrefs[0], '« 2-BBB-004 » (espace en tête en base) en premier');
        self::assertSame('/vehicules/3', $hrefs[20], 'plaque en minuscules « 1-aaa-003 » après les majuscules');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-020')]
    #[Scenario('edge')]
    public function bh020_mileage_number_format(): void
    {
        $r = $this->send('GET', '/vehicules');

        $this->assertListPage($r);
        $this->assertGolden(self::GOLDEN.'bh020-edge.html', $r);
        $byPlate = array_column($this->listRows($r), 2, 0);
        self::assertSame('0 km', $byPlate['0-AAA-000']);
        self::assertSame('2.147.483.647 km', $byPlate['9-ZZZ-999']);
        self::assertSame('100 km', $byPlate['1-TIE-001']);
        self::assertSame('500.000 km', $byPlate['1-OLD-001']);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-021')]
    #[Scenario('edge')]
    public function bh021_html_escaping_and_unicode(): void
    {
        $r = $this->send('GET', '/vehicules');

        $this->assertListPage($r);
        $this->assertGolden(self::GOLDEN.'bh021-edge.html', $r);
        self::assertStringContainsString(
            '<td>Citroën &lt;b&gt;C3&lt;/b&gt; &quot;Aircross&quot; &amp; &lt;script&gt;alert(&#039;x&#039;)&lt;/script&gt;</td>',
            $r->body,
        );
        self::assertStringContainsString('<td>Škoda Énergie Ölçü Ñandú 電気 🚐</td>', $r->body);
        self::assertStringNotContainsString('<script>', $r->body);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-022')]
    #[Scenario('anomalies')]
    public function bh022_legacy_anomalies_in_list(): void
    {
        $r = $this->send('GET', '/vehicules');

        $this->assertListPage($r);
        $this->assertGolden(self::GOLDEN.'bh022-anomalies.html', $r);
        $rows = $this->listRows($r);
        self::assertSame(['', 'Ford Ka', '4.000 km', 'Disponible'], $rows[0], 'plaque vide en premier');
        self::assertSame(['1-ANO-001', 'Renault Kangoo', '1.000 km', ''], $rows[1], 'statut 3 → libellé vide');
        self::assertSame(['1-ANO-002', 'Peugeot Partner', '2.000 km', ''], $rows[2], 'statut -1 → libellé vide');
        self::assertSame(['1-ANO-003', 'Citroën Jumpy', '-500 km', 'Disponible'], $rows[3], 'km négatif');
        self::assertSame(['1-ANO-005', ' ', '5.000 km', 'Disponible'], $rows[4], 'marque et modèle vides : une espace');
        self::assertCount(8, $rows);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-023')]
    #[Scenario('demo')]
    public function bh023_admin_sees_add_vehicle_link(): void
    {
        $r = $this->send('GET', '/vehicules', ['gestionnaire', 'flotte2016']);

        $this->assertListPage($r);
        $this->assertGolden(self::GOLDEN.'bh023-admin.html', $r);
        $links = $this->navLinks($r);
        self::assertSame(['/vehicules', 'Véhicules'], $links[0]);
        self::assertSame(['/admin/vehicules/nouveau', 'Ajouter un véhicule'], $links[1] ?? null, 'lien « Ajouter un véhicule » dans <nav>');
        self::assertCount(2, $links);
        self::assertSame(self::DEMO_ALL, $this->listPlates($r));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-024')]
    #[Scenario('demo')]
    public function bh024_wrong_credentials_on_public_page_are_rejected(): void
    {
        $r = $this->send('GET', '/vehicules', ['gestionnaire', 'mauvais']);

        $this->assertStatus(401, $r);
        self::assertSame('Basic realm="Secured Area"', $r->header('WWW-Authenticate'));
        $this->assertCommonHeaders($r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-025')]
    #[Scenario('demo')]
    public function bh025_trailing_slash_is_not_found(): void
    {
        $r = $this->send('GET', '/vehicules/');

        $this->assertStatus(404, $r);
        self::assertNull($r->header('Location'), 'pas de redirection');
        $this->assertCommonHeaders($r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-026')]
    #[Scenario('demo')]
    public function bh026_post_is_method_not_allowed(): void
    {
        $r = $this->send('POST', '/vehicules');

        $this->assertStatus(405, $r);
        self::assertSame('GET, HEAD', $r->header('Allow'));
        $this->assertCommonHeaders($r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-027')]
    #[Scenario('demo')]
    public function bh027_head_returns_same_headers_without_body(): void
    {
        $r = $this->send('HEAD', '/vehicules');

        $this->assertStatus(200, $r);
        $this->assertCommonHeaders($r);
        $this->assertSessionCookie($r);
        self::assertSame('', $r->body);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-028')]
    #[Scenario('volume')]
    public function bh028_volume_list_shows_all_vehicles(): void
    {
        $r = $this->send('GET', '/vehicules');

        $this->assertListPage($r);
        $plates = $this->listPlates($r);
        self::assertCount(500, $plates);
        $sorted = $plates;
        sort($sorted, SORT_STRING);
        self::assertSame($sorted, $plates, 'plaques générées triées');
        $this->assertDbUnchanged();
    }
}
