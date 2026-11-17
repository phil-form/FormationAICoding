<?php

declare(strict_types=1);

namespace Flotte\Tests\Edge;

use Flotte\Tests\Support\AdminVehicleForm;
use Flotte\Tests\Support\Db;
use Flotte\Tests\Support\FormTestCase;
use Flotte\Tests\Support\Scenario;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * Pièges de `admin_vehicle_new` (tmp/tests-application.md § 5.2) : normalisation de la plaque (regex), longueur
 * non contrôlée des colonnes VARCHAR(50), `IntegerType` en locale `fr` (troncature, notation exponentielle,
 * entiers > 2^31-1), valeurs NULL refusées par SQLite (500, transaction annulée), authentification mémorisée en
 * session. Chaque test vérifie la valeur **et le type SQLite** réellement enregistrés.
 */
#[Group('zone-admin-vehicle-new')]
#[Scenario('demo')]
final class AdminInputParsingTest extends FormTestCase
{
    use AdminVehicleForm;

    /** @return array<string, mixed> colonnes du véhicule 6 + leur type SQLite */
    private function stored(): array
    {
        $rows = Db::query(
            $this->target(),
            'SELECT plate, typeof(plate) AS t_plate, length(brand) AS l_brand, length(model) AS l_model,
                    mileage, typeof(mileage) AS t_mileage, status, typeof(status) AS t_status
               FROM vehicle WHERE id = 6',
        );
        self::assertCount(1, $rows, 'véhicule 6 créé');

        return $rows[0];
    }

    private function vehicleCount(): int
    {
        return (int) Db::query($this->target(), 'SELECT count(*) AS n FROM vehicle')[0]['n'];
    }

    #[Test]
    #[Group('BH-306')]
    public function bh306_plate_variants_are_normalized_to_belgian_format(): void
    {
        foreach (['1-xyz-789', ' 1 xyz 789 ', '1.XYZ.789', '1xyz789'] as $plate) {
            $this->restoreScenarioAgain();
            [, $r] = $this->submitVehicle($this->vehicleFields(['plate' => $plate]));

            $this->assertStatus(302, $r);
            $row = $this->stored();
            self::assertSame(['1-XYZ-789', 'text'], [$row['plate'], $row['t_plate']], "« $plate » normalisée");
        }
    }

    #[Test]
    #[Group('BH-313')]
    public function bh313_brand_and_model_beyond_varchar_50_are_not_truncated(): void
    {
        [, $r] = $this->submitVehicle($this->vehicleFields(['brand' => str_repeat('é', 51), 'model' => str_repeat('M', 60)]));

        $this->assertStatus(302, $r);
        $row = $this->stored();
        self::assertSame([51, 60], [(int) $row['l_brand'], (int) $row['l_model']], 'longueurs en caractères, sans troncature');
    }

    #[Test]
    #[Group('BH-318')]
    public function bh318_decimal_comma_is_truncated_down(): void
    {
        [, $r] = $this->submitVehicle($this->vehicleFields(['mileage' => '1500,7']));

        $this->assertStatus(302, $r);
        $row = $this->stored();
        self::assertSame([1500, 'integer'], [$row['mileage'], $row['t_mileage']], '1500,7 → 1500 (vers le bas)');
    }

    #[Test]
    #[Group('BH-319')]
    public function bh319_exponent_is_read_as_thousand(): void
    {
        [, $r] = $this->submitVehicle($this->vehicleFields(['mileage' => '1e3']));

        $this->assertStatus(302, $r);
        $row = $this->stored();
        self::assertSame([1000, 'integer'], [$row['mileage'], $row['t_mileage']], '1e3 → 1000');
    }

    #[Test]
    #[Group('BH-320')]
    public function bh320_two_pow_31_is_stored_as_64_bit_integer(): void
    {
        [, $r] = $this->submitVehicle($this->vehicleFields(['mileage' => '2147483648']));

        $this->assertStatus(302, $r);
        $row = $this->stored();
        self::assertSame([2147483648, 'integer'], [$row['mileage'], $row['t_mileage']], '2^31 sans dépassement');
    }

    #[Test]
    #[Group('BH-321')]
    public function bh321_empty_mileage_fails_without_writing(): void
    {
        [, $r] = $this->submitVehicle($this->vehicleFields(['mileage' => '']));

        $this->assertStatus(500, $r);
        self::assertSame(5, $this->vehicleCount(), 'aucun véhicule ajouté');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-324')]
    public function bh324_missing_status_fails_without_writing(): void
    {
        [, $r] = $this->submitVehicle($this->vehicleFields(['status' => null]));

        $this->assertStatus(500, $r);
        self::assertSame(5, $this->vehicleCount(), 'aucun véhicule ajouté');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-333')]
    public function bh333_basic_authentication_is_remembered_by_the_session(): void
    {
        $client = $this->adminSession();
        [, $csrf] = $this->openForm($client, self::ADMIN_PATH);
        $client->withoutBasicAuth();

        $r = $this->postForm($client, self::ADMIN_PATH, $this->vehicleFields() + [self::ADMIN_TOKEN => $csrf]);

        $this->assertStatus(302, $r);
        self::assertSame(['Véhicule 1-XYZ-789 ajouté par gestionnaire.'], $this->flashes($this->follow($client, $r)), 'utilisateur lu dans la session');
        $row = $this->stored();
        self::assertSame(['1-XYZ-789', 1500, 0], [$row['plate'], $row['mileage'], $row['status']]);
    }
}
