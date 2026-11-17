<?php

declare(strict_types=1);

namespace Flotte\Tests\Diff;

use Flotte\Tests\Support\FormDiffTestCase;
use Flotte\Tests\Support\HttpClient;
use Flotte\Tests\Support\Scenario;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * Variantes comparatives app ↔ app-ref, route `reservation_return` (fiches « sensibles à la migration » du § 3.3) :
 * même parcours sur les deux cibles, même scénario restauré ; réponses normalisées, diffs de base (valeurs et types
 * SQLite) et journal PHP comparés. BH-169 : statut 500 et base inchangée comparés, corps non contractuel (§ 3.5).
 */
#[Group('zone-reservation-return')]
#[Scenario('returns')]
final class ReservationReturnDiffTest extends FormDiffTestCase
{
    private const TOKEN = 'form[_token]';

    private function giveBack(int $reservation, ?string $endKm, string $label, array $extra = [], bool $withToken = true): void
    {
        $fields = null === $endKm ? [] : ['form[endKm]' => $endKm];
        $this->assertJourneySameAsReference(
            $this->submitJourney("/reservations/$reservation/retour", $fields + $extra, self::TOKEN, $withToken),
            $label,
        );
    }

    #[Test]
    #[Group('BH-150')]
    public function bh150_return_form_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', '/reservations/1/retour');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-151')]
    public function bh151_nominal_return_same_as_reference(): void
    {
        $this->giveBack(1, '10100', 'BH-151');
    }

    #[Test]
    #[Group('BH-156')]
    public function bh156_cost_rounding_same_as_reference(): void
    {
        foreach (['1', '3', '7', '13'] as $km) {
            $this->restoreScenarioAgain();
            $this->giveBack(2, $km, "BH-156 $km km");
        }
    }

    #[Test]
    #[Group('BH-161')]
    public function bh161_beyond_int32_same_as_reference(): void
    {
        $this->giveBack(6, '2147484000', 'BH-161');
    }

    #[Test]
    #[Group('BH-162')]
    public function bh162_huge_number_same_as_reference(): void
    {
        $this->giveBack(1, '99999999999999999999', 'BH-162');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-163')]
    public function bh163_invalid_text_same_as_reference(): void
    {
        foreach (['abc', '+10100', '10 100'] as $value) {
            $this->giveBack(1, $value, "BH-163 « $value »");
        }
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-164')]
    public function bh164_decimal_truncation_same_as_reference(): void
    {
        foreach (['10100.6', '10100,6'] as $value) {
            $this->restoreScenarioAgain();
            $this->giveBack(1, $value, "BH-164 « $value »");
        }
    }

    #[Test]
    #[Group('BH-165')]
    public function bh165_thousands_dot_same_as_reference(): void
    {
        $this->giveBack(1, '10.100', 'BH-165');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-166')]
    public function bh166_leading_zero_same_as_reference(): void
    {
        $this->giveBack(1, '010100', 'BH-166');
    }

    #[Test]
    #[Group('BH-167')]
    public function bh167_exponent_same_as_reference(): void
    {
        $this->giveBack(1, '1e4', 'BH-167');
    }

    #[Test]
    #[Group('BH-168')]
    public function bh168_empty_value_same_as_reference(): void
    {
        $this->giveBack(1, '', 'BH-168');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-169')]
    public function bh169_server_error_same_as_reference(): void
    {
        $this->giveBack(2, '', 'BH-169');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-171')]
    public function bh171_csrf_same_as_reference(): void
    {
        $this->giveBack(1, '10100', 'BH-171 sans jeton', [], false);
        $this->giveBack(1, '10100', 'BH-171 jeton faux', [self::TOKEN => 'faux-jeton'], false);
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-172')]
    public function bh172_extra_field_same_as_reference(): void
    {
        $this->giveBack(1, '10100', 'BH-172', ['form[cost]' => '0']);
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-173')]
    public function bh173_post_without_body_same_as_reference(): void
    {
        $this->assertJourneySameAsReference($this->submitJourney('/reservations/1/retour', null, self::TOKEN), 'BH-173');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-176')]
    public function bh176_unknown_booking_same_as_reference(): void
    {
        $this->assertJourneySameAsReference(
            fn (HttpClient $c): array => [$this->postForm($c, '/reservations/999/retour', ['form[endKm]' => '10100'])],
            'BH-176 POST 999',
        );
        $this->assertSameAsReference('GET', '/reservations/abc/retour');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-179')]
    public function bh179_vehicle_page_after_return_same_as_reference(): void
    {
        $journey = $this->submitJourney('/reservations/1/retour', ['form[endKm]' => '10100'], self::TOKEN);
        $this->assertJourneySameAsReference(
            static fn (HttpClient $c): array => [...$journey($c), $c->get('/vehicules/1')],
            'BH-179',
        );
    }
}
