<?php

declare(strict_types=1);

namespace Flotte\Tests\Edge;

use Flotte\Tests\Support\Db;
use Flotte\Tests\Support\FormTestCase;
use Flotte\Tests\Support\HttpResponse;
use Flotte\Tests\Support\Scenario;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * Pièges de `reservation_return` (tmp/tests-application.md § 3.3) : `IntegerType` en locale `fr` (troncature,
 * point des milliers, notation exponentielle, zéro initial, dépassement), arithmétique flottante du coût
 * (`round(km × 0,35, 2)`, type SQLite INTEGER ou REAL), entiers > 2^31-1, comparaison `null < start_km`,
 * POST sans corps. Chaque test vérifie la valeur et le **type SQLite** réellement enregistrés.
 */
#[Group('zone-reservation-return')]
#[Scenario('returns')]
final class ReturnMileageParsingTest extends FormTestCase
{
    private function giveBack(int $reservation, string $endKm): HttpResponse
    {
        $client = $this->session();
        $path = "/reservations/$reservation/retour";
        [, $token] = $this->openForm($client, $path);

        return $this->postForm($client, $path, ['form[endKm]' => $endKm, 'form[_token]' => $token]);
    }

    /** @return array{0: mixed, 1: string, 2: mixed, 3: string, 4: mixed} [end_km, typeof, cost, typeof, km véhicule] */
    private function stored(int $reservation): array
    {
        $row = Db::query(
            $this->target(),
            'SELECT r.end_km, typeof(r.end_km) AS t_end, r.cost, typeof(r.cost) AS t_cost, v.mileage
               FROM reservation r JOIN vehicle v ON v.id = r.vehicle_id WHERE r.id = ?',
            [$reservation],
        )[0];

        return [$row['end_km'], $row['t_end'], $row['cost'], $row['t_cost'], $row['mileage']];
    }

    #[Test]
    #[Group('BH-156')]
    public function bh156_cost_is_rounded_float_stored_as_real(): void
    {
        foreach (['1' => 0.35, '3' => 1.05, '7' => 2.45, '13' => 4.55] as $km => $cost) {
            $this->restoreScenarioAgain();
            $this->assertStatus(302, $this->giveBack(2, (string) $km));
            self::assertSame([(int) $km, 'integer', $cost, 'real', (int) $km], $this->stored(2), "$km km × 0,35 (round à 2 décimales, REAL)");
        }
    }

    #[Test]
    #[Group('BH-161')]
    public function bh161_mileage_beyond_int32_is_stored_as_64_bit_integer(): void
    {
        $this->assertStatus(302, $this->giveBack(6, '2147484000'));

        self::assertSame([2147484000, 'integer', 350, 'integer', 2147484000], $this->stored(6), 'au-delà de 2^31-1 : enregistré sans dépassement');
    }

    #[Test]
    #[Group('BH-162')]
    public function bh162_twenty_digit_number_is_not_valid(): void
    {
        $r = $this->giveBack(1, '99999999999999999999');

        $this->assertStatus(200, $r);
        self::assertSame(['form_endKm' => ['This value is not valid.']], $this->fieldErrors($r, 'form'));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-163')]
    public function bh163_text_sign_and_space_are_not_valid(): void
    {
        foreach (['abc', '+10100', '10 100'] as $value) {
            $r = $this->giveBack(1, $value);
            $this->assertStatus(200, $r);
            self::assertSame(['form_endKm' => ['This value is not valid.']], $this->fieldErrors($r, 'form'), "« $value »");
        }
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-164')]
    public function bh164_decimals_are_truncated_down(): void
    {
        foreach (['10100.6', '10100,6'] as $value) {
            $this->restoreScenarioAgain();
            $this->assertStatus(302, $this->giveBack(1, $value));
            self::assertSame([10100, 'integer', 35, 'integer', 10100], $this->stored(1), "« $value » tronqué à 10100 (arrondi vers le bas)");
        }
    }

    #[Test]
    #[Group('BH-165')]
    public function bh165_thousands_dot_reads_as_ten(): void
    {
        $r = $this->giveBack(1, '10.100');

        $this->assertStatus(200, $r);
        self::assertSame(['Kilométrage invalide.'], $this->globalErrors($r, 'form'), '« 10.100 » lu comme 10 (< 10000 au départ)');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-166')]
    public function bh166_leading_zero_reads_as_decimal(): void
    {
        $this->assertStatus(302, $this->giveBack(1, '010100'));

        self::assertSame([10100, 'integer', 35, 'integer', 10100], $this->stored(1), '« 010100 » = 10100 (pas d\'octal)');
    }

    #[Test]
    #[Group('BH-167')]
    public function bh167_exponent_reads_as_ten_thousand(): void
    {
        $this->assertStatus(302, $this->giveBack(1, '1e4'));

        self::assertSame([10000, 'integer', 0, 'integer', 10000], $this->stored(1), '« 1e4 » = 10000 → clôture à 0 km');
    }

    #[Test]
    #[Group('BH-168')]
    public function bh168_empty_value_compares_null_below_departure(): void
    {
        $r = $this->giveBack(1, '');

        $this->assertStatus(200, $r);
        self::assertSame(['Kilométrage invalide.'], $this->globalErrors($r, 'form'), 'null < 10000 (comparaison null PHP 7)');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-169')]
    public function bh169_empty_value_with_zero_departure_fails_without_writing(): void
    {
        $r = $this->giveBack(2, '');

        $this->assertStatus(500, $r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-173')]
    public function bh173_post_without_body_never_fails(): void
    {
        $client = $this->session();
        $this->openForm($client, '/reservations/1/retour');
        $r = $this->postForm($client, '/reservations/1/retour', null);

        $this->assertStatus(200, $r);
        self::assertSame([], $this->globalErrors($r, 'form'), 'formulaire non soumis : aucune erreur (Symfony 4+ : exception si isSubmitted() n\'est pas vérifié)');
        self::assertSame([], $this->fieldErrors($r, 'form'));
        $this->assertDbUnchanged();
    }
}
