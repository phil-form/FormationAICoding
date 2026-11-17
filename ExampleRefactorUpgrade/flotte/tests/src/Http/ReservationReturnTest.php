<?php

declare(strict_types=1);

namespace Flotte\Tests\Http;

use Flotte\Tests\Support\Db;
use Flotte\Tests\Support\FormTestCase;
use Flotte\Tests\Support\HttpClient;
use Flotte\Tests\Support\HttpResponse;
use Flotte\Tests\Support\Scenario;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * ReservationController, route `reservation_return` — `GET|POST /reservations/{id}/retour`.
 * Fiches : tmp/tests-application.md § 3.3 (parcours § 3.1). Scénario returns (sauf BH-178 : anomalies).
 * A4 = diff exact de la base : réservation clôturée (status, end_km, cost typé) et kilométrage du véhicule.
 * Pages 404/405/500 : corps non contractuel (propositions § 2.5 et § 3.5, en attente de décision).
 */
#[Group('zone-reservation-return')]
#[Scenario('returns')]
final class ReservationReturnTest extends FormTestCase
{
    private const GOLDEN = 'http/reservation-return/';
    private const FORM = 'form';
    private const INVALID_KM = 'Kilométrage invalide.';
    private const CLOSED = 'Cette réservation est déjà clôturée.';
    private const NOT_VALID = 'This value is not valid.';
    private const CSRF_INVALID = 'The CSRF token is invalid. Please try to resubmit the form.';
    private const EXTRA_FIELDS = 'This form should not contain extra fields.';

    /**
     * GET du formulaire puis POST de `form[endKm]` (+ jeton valide sauf $token = false).
     *
     * @param array<string, string> $extra
     *
     * @return array{0: HttpClient, 1: HttpResponse}
     */
    private function submit(int $reservation, ?string $endKm, bool $token = true, array $extra = []): array
    {
        $client = $this->session();
        $path = "/reservations/$reservation/retour";
        [, $csrf] = $this->openForm($client, $path);
        $fields = null === $endKm ? [] : ['form[endKm]' => $endKm];
        $fields += $extra;
        if ($token) {
            $fields['form[_token]'] = $csrf;
        }

        return [$client, $this->postForm($client, $path, $fields)];
    }

    /** 302 vers la fiche du véhicule puis flash « Retour enregistré. Coût : … ». */
    private function assertReturned(HttpClient $client, HttpResponse $r, int $vehicle, string $cost): HttpResponse
    {
        $this->assertStatus(302, $r);
        $this->assertCommonHeaders($r);
        self::assertSame("/vehicules/$vehicle", $r->header('Location'), 'A2 Location relatif');
        $page = $this->follow($client, $r);
        $this->assertStatus(200, $page);
        self::assertSame(["Retour enregistré. Coût : $cost"], $this->flashes($page), 'A3 flash après redirection');

        return $page;
    }

    private function assertRedisplayed(HttpResponse $r): void
    {
        $this->assertStatus(200, $r);
        $this->assertCommonHeaders($r);
        self::assertSame(1, $r->crawler()->filter('form[name="form"]')->count(), 'formulaire réaffiché');
    }

    private function assertRefusedWithGlobalError(HttpResponse $r, string $message): void
    {
        $this->assertRedisplayed($r);
        self::assertSame([$message], $this->globalErrors($r, self::FORM), 'A3 erreur globale en tête du formulaire');
        self::assertSame([], $this->fieldErrors($r, self::FORM), 'A3 aucune erreur de champ');
    }

    private function assertFieldInvalid(HttpResponse $r): void
    {
        $this->assertRedisplayed($r);
        self::assertSame([], $this->globalErrors($r, self::FORM));
        self::assertSame(['form_endKm' => [self::NOT_VALID]], $this->fieldErrors($r, self::FORM));
    }

    /**
     * Diff attendu d'une clôture (format DbDiff) : véhicule (si son kilométrage change) puis réservation.
     *
     * @return list<string>
     */
    private function closed(int $reservation, int $vehicle, ?string $mileageBefore, string $endKm, string $cost): array
    {
        $lines = [];
        if (null !== $mileageBefore && $mileageBefore !== $endKm) {
            $lines[] = "~ vehicle $vehicle mileage: $mileageBefore → $endKm";
        }
        $lines[] = "~ reservation $reservation status: 'confirmee' → 'terminee'";
        $lines[] = "~ reservation $reservation end_km: NULL → $endKm";
        $lines[] = "~ reservation $reservation cost: NULL → $cost";

        return $lines;
    }

    // --- BH-150 à BH-179 ------------------------------------------------------------------------------------

    #[Test]
    #[Group('BH-150')]
    public function bh150_return_form_has_number_field_and_no_button(): void
    {
        $r = $this->send('GET', '/reservations/1/retour');

        $this->assertStatus(200, $r);
        $this->assertCommonHeaders($r);
        $this->assertSessionCookie($r);
        $this->assertGolden(self::GOLDEN.'bh150.html', $r);
        $c = $r->crawler();
        self::assertSame('Retour de 1-RET-001', $c->filter('h1')->text());
        self::assertSame('Kilométrage au départ : 10.000 km', $c->filter('p')->first()->text());
        $input = $c->filter('#form_endKm');
        self::assertSame('number', $input->attr('type'));
        self::assertSame('required', $input->attr('required'));
        self::assertSame(1, $c->filter('input[type="hidden"][name="form[_token]"]')->count(), 'jeton caché');
        self::assertSame(0, $c->filter('button, input[type="submit"]')->count(), 'aucun bouton de soumission');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-151')]
    public function bh151_nominal_return_closes_booking_and_updates_vehicle_mileage(): void
    {
        [$client, $r] = $this->submit(1, '10100');

        $page = $this->assertReturned($client, $r, 1, '35,00 €');
        $this->assertGolden(self::GOLDEN.'bh151-after-redirect.html', $page);
        self::assertSame([], $this->flashes($client->get('/vehicules/1')), 'le flash ne s\'affiche qu\'une fois');
        $this->assertDbDiff($this->closed(1, 1, '10000', '10100', '35'));
    }

    #[Test]
    #[Group('BH-152')]
    public function bh152_zero_km_return_costs_nothing(): void
    {
        [$client, $r] = $this->submit(1, '10000');

        $this->assertReturned($client, $r, 1, '0,00 €');
        $this->assertDbDiff($this->closed(1, 1, '10000', '10000', '0'));
    }

    #[Test]
    #[Group('BH-153')]
    public function bh153_mileage_below_departure_is_refused(): void
    {
        [, $r] = $this->submit(1, '9999');

        $this->assertRefusedWithGlobalError($r, self::INVALID_KM);
        self::assertSame('9999', $this->fieldValue($r, 'form_endKm'), 'saisie réaffichée');
        $this->assertGolden(self::GOLDEN.'bh153.html', $r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-154')]
    public function bh154_finished_booking_cannot_be_closed_again(): void
    {
        [, $r] = $this->submit(3, '33100');

        $this->assertRefusedWithGlobalError($r, self::CLOSED);
        $this->assertGolden(self::GOLDEN.'bh154.html', $r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-155')]
    public function bh155_cancelled_booking_gets_the_same_already_closed_message(): void
    {
        [, $r] = $this->submit(4, '44100');

        $this->assertRefusedWithGlobalError($r, self::CLOSED);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-156')]
    public function bh156_cost_rounding_for_small_distances(): void
    {
        foreach (['1' => ['0,35 €', '0.35'], '3' => ['1,05 €', '1.05'], '7' => ['2,45 €', '2.45'], '13' => ['4,55 €', '4.55']] as $km => [$label, $stored]) {
            $this->restoreScenarioAgain();
            [$client, $r] = $this->submit(2, (string) $km);
            $this->assertReturned($client, $r, 2, $label);
            $this->assertDbDiff($this->closed(2, 2, '0', (string) $km, $stored));
        }
    }

    #[Test]
    #[Group('BH-157')]
    public function bh157_vehicle_in_maintenance_can_be_returned(): void
    {
        [$client, $r] = $this->submit(7, '7050');

        $this->assertReturned($client, $r, 7, '17,50 €');
        $this->assertDbDiff($this->closed(7, 7, '7000', '7050', '17.5'));
    }

    #[Test]
    #[Group('BH-158')]
    public function bh158_vehicle_mileage_goes_backwards(): void
    {
        [$client, $r] = $this->submit(5, '50200');

        $this->assertReturned($client, $r, 5, '70,00 €');
        $this->assertDbDiff($this->closed(5, 5, '50500', '50200', '70'));
    }

    #[Test]
    #[Group('BH-159')]
    public function bh159_booking_not_yet_started_can_be_closed(): void
    {
        [$client, $r] = $this->submit(8, '8100');

        $this->assertReturned($client, $r, 8, '35,00 €');
        $this->assertDbDiff($this->closed(8, 8, '8000', '8100', '35'));
    }

    #[Test]
    #[Group('BH-160')]
    public function bh160_closing_one_booking_leaves_the_other_untouched(): void
    {
        [$client, $r] = $this->submit(9, '8300');

        $this->assertReturned($client, $r, 8, '105,00 €');
        $this->assertDbDiff($this->closed(9, 8, '8000', '8300', '105'));
        self::assertSame(
            [['status' => 'confirmee', 'start_km' => 8000]],
            Db::query($this->target(), 'SELECT status, start_km FROM reservation WHERE id = 8'),
            'r8 reste confirmée avec start_km 8000',
        );
    }

    #[Test]
    #[Group('BH-161')]
    public function bh161_mileage_beyond_int32_is_stored(): void
    {
        [$client, $r] = $this->submit(6, '2147484000');

        $this->assertReturned($client, $r, 6, '350,00 €');
        $this->assertDbDiff($this->closed(6, 6, '2147483000', '2147484000', '350'));
    }

    #[Test]
    #[Group('BH-162')]
    public function bh162_huge_number_is_not_valid(): void
    {
        [, $r] = $this->submit(1, '99999999999999999999');

        $this->assertFieldInvalid($r);
        $this->assertGolden(self::GOLDEN.'bh162.html', $r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-163')]
    public function bh163_non_integer_text_is_not_valid(): void
    {
        foreach (['abc' => 'abc', '+10100' => 'plus', '10 100' => 'space'] as $value => $variant) {
            [, $r] = $this->submit(1, (string) $value);
            $this->assertFieldInvalid($r);
            $this->assertGolden(self::GOLDEN."bh163-$variant.html", $r);
        }
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-164')]
    public function bh164_decimal_mileage_is_truncated(): void
    {
        foreach (['10100.6', '10100,6'] as $value) {
            $this->restoreScenarioAgain();
            [$client, $r] = $this->submit(1, $value);
            $this->assertReturned($client, $r, 1, '35,00 €');
            $this->assertDbDiff($this->closed(1, 1, '10000', '10100', '35'));
        }
    }

    #[Test]
    #[Group('BH-165')]
    public function bh165_thousands_dot_is_read_as_ten(): void
    {
        [, $r] = $this->submit(1, '10.100');

        $this->assertRefusedWithGlobalError($r, self::INVALID_KM);
        $this->assertGolden(self::GOLDEN.'bh165.html', $r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-166')]
    public function bh166_leading_zero_is_accepted(): void
    {
        [$client, $r] = $this->submit(1, '010100');

        $this->assertReturned($client, $r, 1, '35,00 €');
        $this->assertDbDiff($this->closed(1, 1, '10000', '10100', '35'));
    }

    #[Test]
    #[Group('BH-167')]
    public function bh167_exponent_notation_is_read_as_ten_thousand(): void
    {
        [$client, $r] = $this->submit(1, '1e4');

        $this->assertReturned($client, $r, 1, '0,00 €');
        $this->assertDbDiff($this->closed(1, 1, '10000', '10000', '0'));
    }

    #[Test]
    #[Group('BH-168')]
    public function bh168_empty_mileage_is_refused_as_invalid(): void
    {
        [, $r] = $this->submit(1, '');

        $this->assertRefusedWithGlobalError($r, self::INVALID_KM);
        $this->assertGolden(self::GOLDEN.'bh168.html', $r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-169')]
    public function bh169_empty_mileage_with_zero_departure_is_a_server_error(): void
    {
        [, $r] = $this->submit(2, '');

        $this->assertStatus(500, $r);
        self::assertNull($r->header('Location'), 'pas de redirection');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-170')]
    public function bh170_negative_mileage_is_refused(): void
    {
        [, $r] = $this->submit(2, '-5');

        $this->assertRefusedWithGlobalError($r, self::INVALID_KM);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-171')]
    public function bh171_missing_or_wrong_csrf_token_is_refused(): void
    {
        [, $r] = $this->submit(1, '10100', false);
        $this->assertRefusedWithGlobalError($r, self::CSRF_INVALID);
        $this->assertGolden(self::GOLDEN.'bh171-missing.html', $r);

        [, $r] = $this->submit(1, '10100', false, ['form[_token]' => 'faux-jeton']);
        $this->assertRefusedWithGlobalError($r, self::CSRF_INVALID);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-172')]
    public function bh172_extra_field_is_refused_without_assignment(): void
    {
        [, $r] = $this->submit(1, '10100', true, ['form[cost]' => '0']);

        $this->assertRefusedWithGlobalError($r, self::EXTRA_FIELDS);
        $this->assertGolden(self::GOLDEN.'bh172.html', $r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-173')]
    public function bh173_post_without_body_redisplays_form_without_error(): void
    {
        $client = $this->session();
        $this->openForm($client, '/reservations/1/retour');
        $r = $this->postForm($client, '/reservations/1/retour', null);

        $this->assertRedisplayed($r);
        self::assertSame([], $this->globalErrors($r, self::FORM));
        self::assertSame([], $this->fieldErrors($r, self::FORM));
        $this->assertGolden(self::GOLDEN.'bh173.html', $r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-174')]
    public function bh174_same_post_twice_closes_once(): void
    {
        $client = $this->session();
        [, $token] = $this->openForm($client, '/reservations/1/retour');
        $fields = ['form[endKm]' => '10100', 'form[_token]' => $token];

        $this->assertStatus(302, $this->postForm($client, '/reservations/1/retour', $fields));
        $second = $this->postForm($client, '/reservations/1/retour', $fields);
        $this->assertRefusedWithGlobalError($second, self::CLOSED);
        $this->assertDbDiff($this->closed(1, 1, '10000', '10100', '35'));
    }

    #[Test]
    #[Group('BH-175')]
    public function bh175_form_of_finished_booking_is_displayed(): void
    {
        $r = $this->send('GET', '/reservations/3/retour');

        $this->assertStatus(200, $r);
        $this->assertCommonHeaders($r);
        $this->assertGolden(self::GOLDEN.'bh175.html', $r);
        self::assertSame('Retour de 1-RET-003', $r->crawler()->filter('h1')->text());
        self::assertSame([], $this->globalErrors($r, self::FORM));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-176')]
    public function bh176_unknown_or_invalid_booking_is_not_found(): void
    {
        $r = $this->postForm($this->session(), '/reservations/999/retour', ['form[endKm]' => '10100']);
        $this->assertStatus(404, $r);
        $this->assertCommonHeaders($r);

        $r = $this->send('GET', '/reservations/abc/retour');
        $this->assertStatus(404, $r);
        $this->assertCommonHeaders($r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-177')]
    public function bh177_put_is_method_not_allowed(): void
    {
        $r = $this->send('PUT', '/reservations/1/retour');

        $this->assertStatus(405, $r);
        $this->assertCommonHeaders($r);
        self::assertSame('GET, POST, HEAD', $r->header('Allow'));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-178')]
    #[Scenario('anomalies')]
    public function bh178_finished_booking_without_end_km_is_already_closed(): void
    {
        [, $r] = $this->submit(3, '7100');

        $this->assertRefusedWithGlobalError($r, self::CLOSED);
        $this->assertGolden(self::GOLDEN.'bh178-anomalies.html', $r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-179')]
    public function bh179_returned_booking_moves_to_history_on_vehicle_page(): void
    {
        $before = $this->send('GET', '/vehicules/1');
        self::assertCount(1, $this->showItems($before, 0), 'r1 à venir avant le retour');

        [$client, $r] = $this->submit(1, '10100');
        $this->assertReturned($client, $r, 1, '35,00 €');
        $page = $client->get('/vehicules/1');

        $this->assertStatus(200, $page);
        $this->assertGolden(self::GOLDEN.'bh179-vehicle-page.html', $page);
        self::assertSame('Kilométrage : 10.100 km · Statut : Disponible', $page->crawler()->filter('p')->first()->text());
        self::assertSame(['Aucune réservation à venir.'], $this->showItems($page, 0));
        self::assertSame(
            ['02/01/2026 : Chloé Dubois, 100 km, 35,00 €', '14/01/2026 : Jean Dupont, 100 km, 35,00 €'],
            $this->showItems($page, 1),
            'la réservation rendue passe dans l\'historique (date de début, ordre des dates)',
        );
    }
}
