<?php

declare(strict_types=1);

namespace Flotte\Tests\Http;

use Flotte\Tests\Support\CaseTestCase;
use Flotte\Tests\Support\HttpResponse;
use Flotte\Tests\Support\Scenario;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * VehicleController, route `vehicle_show` — `GET /vehicules/{id}`.
 * Fiches : tmp/tests-vehicle-controller.md § 3.3. Lecture seule : A4 = base inchangée.
 * Pages d'erreur 404/405 : corps non contractuel (proposition § 4 appliquée, en attente de décision).
 */
#[Group('zone-vehicle-show')]
final class VehicleShowTest extends CaseTestCase
{
    private const GOLDEN = 'http/vehicle-show/';

    private function assertShowPage(HttpResponse $r): void
    {
        $this->assertStatus(200, $r);
        $this->assertCommonHeaders($r);
        $this->assertSessionCookie($r);
        self::assertSame('Flotte Delvaux & Fils', $r->crawler()->filter('title')->text());
    }

    /** Texte (espaces normalisés) des <li> de la n-ième liste : 0 = à venir, 1 = historique. */
    private function items(HttpResponse $r, int $list): array
    {
        return $r->crawler()->filter('ul')->eq($list)->filter('li')->each(static fn ($li): string => $li->text());
    }

    /** Texte brut (espaces conservés) des <li> de l'historique. */
    private function rawHistory(HttpResponse $r): array
    {
        return $r->crawler()->filter('ul')->eq(1)->filter('li')->each(static fn ($li): string => $li->text('', false));
    }

    private function bookLinks(HttpResponse $r): int
    {
        return $r->crawler()->filter('a')->reduce(static fn ($a): bool => 'Réserver ce véhicule' === $a->text())->count();
    }

    private function assertErrorPage(int $status, HttpResponse $r): void
    {
        $this->assertStatus($status, $r);
        $this->assertCommonHeaders($r);
        $this->assertNoCookie($r);
        self::assertNull($r->header('Location'), 'pas de redirection — '.$r->describe());
    }

    #[Test]
    #[Group('BH-040')]
    #[Scenario('demo')]
    public function bh040_vehicle_page_with_upcoming_and_history(): void
    {
        $r = $this->send('GET', '/vehicules/1');

        $this->assertShowPage($r);
        $this->assertGolden(self::GOLDEN.'bh040.html', $r);
        self::assertSame('1-ABC-123 — Renault Kangoo', $r->crawler()->filter('h1')->text());
        self::assertSame('Kilométrage : 84.210 km · Statut : Disponible', $r->crawler()->filter('p')->first()->text());
        self::assertSame('/vehicules/1/reserver', $r->crawler()->selectLink('Réserver ce véhicule')->attr('href'));
        self::assertSame(['15/06/2030 08:00 → 15/06/2030 18:00 : Sophie Martin (retour)'], $this->items($r, 0));
        self::assertSame('/reservations/5/retour', $r->crawler()->filter('ul')->eq(0)->filter('a')->attr('href'));
        self::assertSame(['02/05/2016 : Jean Dupont, 210 km, 73,50 €'], $this->items($r, 1));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-041')]
    #[Scenario('demo')]
    public function bh041_no_upcoming_and_history_in_date_order(): void
    {
        $r = $this->send('GET', '/vehicules/2');

        $this->assertShowPage($r);
        $this->assertGolden(self::GOLDEN.'bh041.html', $r);
        self::assertSame(['Aucune réservation à venir.'], $this->items($r, 0));
        self::assertSame([
            '03/05/2016 : Marie Lambert, 210 km, 73,50 €',
            '20/05/2016 : Jean Dupont, 240 km, 84,00 €',
        ], $this->items($r, 1));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-042')]
    #[Scenario('demo')]
    public function bh042_unavailable_vehicle_has_no_booking_link(): void
    {
        foreach ([3 => 'En entretien', 4 => 'Hors service'] as $id => $label) {
            $r = $this->send('GET', "/vehicules/$id");
            $this->assertShowPage($r);
            $this->assertGolden(self::GOLDEN."bh042-vehicle-$id.html", $r);
            self::assertStringEndsWith("Statut : $label", $r->crawler()->filter('p')->first()->text());
            self::assertSame(0, $this->bookLinks($r), "véhicule $id : pas de lien « Réserver »");
        }
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-043')]
    #[Scenario('edge')]
    public function bh043_vehicle_without_reservations_has_empty_history_list(): void
    {
        $r = $this->send('GET', '/vehicules/1');

        $this->assertShowPage($r);
        $this->assertGolden(self::GOLDEN.'bh043-edge-1.html', $r);
        self::assertSame(['Aucune réservation à venir.'], $this->items($r, 0));
        self::assertSame(0, $r->crawler()->filter('ul')->eq(1)->filter('li')->count(), 'historique : <ul> vide, sans message');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-044')]
    #[Scenario('edge')]
    public function bh044_maintenance_vehicle_shows_future_booking_without_link(): void
    {
        $r = $this->send('GET', '/vehicules/11');

        $this->assertShowPage($r);
        $this->assertGolden(self::GOLDEN.'bh044-edge-11.html', $r);
        self::assertSame(['03/02/2026 08:00 → 03/02/2026 18:00 : Jean Dupont (retour)'], $this->items($r, 0));
        self::assertSame(0, $this->bookLinks($r));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-045')]
    #[Scenario('edge')]
    public function bh045_overdue_booking_listed_as_upcoming(): void
    {
        $r = $this->send('GET', '/vehicules/21');

        $this->assertShowPage($r);
        $this->assertGolden(self::GOLDEN.'bh045-edge-21.html', $r);
        self::assertSame([
            '05/01/2026 08:00 → 06/01/2026 18:00 : Sophie Martin (retour)',
            '15/02/2026 08:00 → 15/02/2026 18:00 : Luc Peeters (retour)',
        ], $this->items($r, 0));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-046')]
    #[Scenario('edge')]
    public function bh046_same_start_bookings_keep_observed_order(): void
    {
        $r = $this->send('GET', '/vehicules/22');

        $this->assertShowPage($r);
        $this->assertGolden(self::GOLDEN.'bh046-edge-22.html', $r);
        self::assertSame([
            '20/02/2026 08:00 → 20/02/2026 12:00 : Chloé Dubois (retour)',
            '20/02/2026 08:00 → 20/02/2026 18:00 : Jan Vermeulen (retour)',
        ], $this->items($r, 0), 'ordre observé en step-00 (réservations 61 puis 62)');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-047')]
    #[Scenario('edge')]
    public function bh047_long_lists_without_pagination(): void
    {
        $r = $this->send('GET', '/vehicules/17');

        $this->assertShowPage($r);
        $this->assertGolden(self::GOLDEN.'bh047-edge-17.html', $r);
        $upcoming = $this->items($r, 0);
        $history = $this->items($r, 1);
        self::assertCount(7, $upcoming, 'confirmées 41 à 47');
        self::assertCount(29, $history, 'terminées : 40 réservations − 4 annulées − 7 confirmées');
        self::assertSame('09/06/2025 : Marie Lambert, 40 km, 14,00 €', $history[0]);
        self::assertSame('12/01/2026 : Ahmed Benali, 151 km, 52,85 €', $history[28]);
        foreach (['02/06/2025', '04/08/2025', '06/10/2025', '08/12/2025'] as $cancelled) {
            self::assertStringNotContainsString($cancelled, $r->body, "annulée du $cancelled absente");
        }
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-048')]
    #[Scenario('edge')]
    public function bh048_cost_rounding_in_history(): void
    {
        $r = $this->send('GET', '/vehicules/25');

        $this->assertShowPage($r);
        $this->assertGolden(self::GOLDEN.'bh048-edge-25.html', $r);
        self::assertSame([
            '01/11/2025 : Pierre-Yves Goffin, 1 km, 0,35 €',
            '04/11/2025 : Pierre-Yves Goffin, 3 km, 1,05 €',
            '07/11/2025 : Pierre-Yves Goffin, 7 km, 2,45 €',
            '10/11/2025 : Pierre-Yves Goffin, 13 km, 4,55 €',
            '13/11/2025 : Pierre-Yves Goffin, 101 km, 35,35 €',
            '16/11/2025 : Pierre-Yves Goffin, 0 km, 0,00 €',
            '19/11/2025 : Pierre-Yves Goffin, 2 km, 0,70 €',
            '22/11/2025 : Pierre-Yves Goffin, 5 km, 1,75 €',
            '25/11/2025 : Pierre-Yves Goffin, 999 km, 349,65 €',
        ], $this->items($r, 1));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-049')]
    #[Scenario('edge')]
    public function bh049_html_escaping_on_vehicle_page(): void
    {
        $r = $this->send('GET', '/vehicules/18');

        $this->assertShowPage($r);
        $this->assertGolden(self::GOLDEN.'bh049-edge-18.html', $r);
        self::assertStringContainsString(
            '<h1>1-XSS-001 — Citroën &lt;b&gt;C3&lt;/b&gt; &quot;Aircross&quot; &amp; &lt;script&gt;alert(&#039;x&#039;)&lt;/script&gt;</h1>',
            $r->body,
        );
        self::assertCount(11, $this->rawHistory($r));
        self::assertStringContainsString('<li>01/12/2025 : , 10 km, 3,50 €</li>', $r->body, 'nom vide');
        self::assertStringContainsString('<li>06/12/2025 : Marc &quot;Le Rapide&quot; Leroy, 10 km, 3,50 €</li>', $r->body);
        self::assertStringContainsString('<li>07/12/2025 : &lt;script&gt;alert(1)&lt;/script&gt;, 10 km, 3,50 €</li>', $r->body);
        self::assertStringContainsString('<li>09/12/2025 :   Paul Martin  , 10 km, 3,50 €</li>', $r->body, 'espaces conservés');
        self::assertStringContainsString('<li>11/12/2025 : O&#039;Brien; DROP TABLE reservation;--, 10 km, 3,50 €</li>', $r->body);
        self::assertStringNotContainsString('<script>', $r->body);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-050')]
    #[Scenario('edge')]
    public function bh050_naive_times_around_dst_changes(): void
    {
        $r = $this->send('GET', '/vehicules/24');

        $this->assertShowPage($r);
        $this->assertGolden(self::GOLDEN.'bh050-edge-24.html', $r);
        self::assertSame([
            '29/03/2026 01:30 → 29/03/2026 03:30 : Élodie Lefèvre (retour)',
            '25/10/2026 02:15 → 25/10/2026 02:45 : Ahmed Benali (retour)',
        ], $this->items($r, 0));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-051')]
    #[Scenario('anomalies')]
    public function bh051_legacy_anomalies_on_vehicle_page(): void
    {
        $unknown = $this->send('GET', '/vehicules/1');
        $this->assertShowPage($unknown);
        $this->assertGolden(self::GOLDEN.'bh051-anomalies-1.html', $unknown);
        self::assertSame('Kilométrage : 1.000 km · Statut :', $unknown->crawler()->filter('p')->first()->text(), 'libellé de statut vide');
        self::assertSame(0, $this->bookLinks($unknown), 'statut inconnu : pas de lien « Réserver »');

        $negative = $this->send('GET', '/vehicules/6');
        $this->assertShowPage($negative);
        $this->assertGolden(self::GOLDEN.'bh051-anomalies-6.html', $negative);
        self::assertSame([
            '05/01/2026 : Jean Dupont, -100 km, -35,00 €',
            '06/01/2026 : Marie Lambert, 50 km, 0,00 €',
        ], $this->items($negative, 1), 'km retour < départ ; coût NULL → 0,00 €');

        $costs = $this->send('GET', '/vehicules/8');
        $this->assertShowPage($costs);
        $this->assertGolden(self::GOLDEN.'bh051-anomalies-8.html', $costs);
        self::assertSame([
            '09/01/2026 : Luc Peeters, 10 km,  €',
            '10/01/2026 : Nathalie Janssens, 10 km, 12,35 €',
        ], $this->rawHistory($costs), 'coût « N/A » → montant vide ; 12.345 → 12,35 €');
        self::assertSame(['Aucune réservation à venir.'], $this->items($costs, 0), 'statuts « CONFIRMEE » et vide absents');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-052')]
    #[Scenario('demo')]
    public function bh052_leading_zero_id_serves_same_page(): void
    {
        $r = $this->send('GET', '/vehicules/01');

        $this->assertShowPage($r);
        $this->assertGolden(self::GOLDEN.'bh052.html', $r);
        self::assertSame($this->html($this->send('GET', '/vehicules/1')), $this->html($r), 'même contenu que /vehicules/1');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-053')]
    #[Scenario('demo')]
    public function bh053_unknown_id_is_not_found(): void
    {
        foreach (['/vehicules/999', '/vehicules/0'] as $path) {
            $this->assertErrorPage(404, $this->send('GET', $path));
        }
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-054')]
    #[Scenario('demo')]
    public function bh054_invalid_or_huge_id_is_not_found_never_500(): void
    {
        foreach (['/vehicules/abc', '/vehicules/-1', '/vehicules/99999999999999999999'] as $path) {
            $this->assertErrorPage(404, $this->send('GET', $path));
        }
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-055')]
    #[Scenario('demo')]
    public function bh055_trailing_slash_is_not_found(): void
    {
        $this->assertErrorPage(404, $this->send('GET', '/vehicules/1/'));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-056')]
    #[Scenario('demo')]
    public function bh056_post_is_method_not_allowed(): void
    {
        $r = $this->send('POST', '/vehicules/1');

        $this->assertErrorPage(405, $r);
        self::assertSame('GET, HEAD', $r->header('Allow'));
        $this->assertDbUnchanged();
    }
}
