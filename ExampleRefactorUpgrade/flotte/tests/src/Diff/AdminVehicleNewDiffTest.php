<?php

declare(strict_types=1);

namespace Flotte\Tests\Diff;

use Flotte\Tests\Support\AdminVehicleForm;
use Flotte\Tests\Support\FormDiffTestCase;
use Flotte\Tests\Support\HttpClient;
use Flotte\Tests\Support\HttpResponse;
use Flotte\Tests\Support\Scenario;
use Flotte\Tests\Support\Target;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * Variantes comparatives app ↔ app-ref, route `admin_vehicle_new` (fiches « sensibles à la migration » du § 5.2) :
 * même parcours authentifié (GET du formulaire → POST → GET de Location…) sur les deux cibles, même scénario restauré
 * des deux côtés ; statuts, en-têtes contractuels (dont WWW-Authenticate et forme des Set-Cookie), corps normalisés,
 * diff de base et journal PHP comparés.
 *
 * Seule normalisation propre à cette route : `created_at` des véhicules **ajoutés** par le parcours est réduit à sa
 * date (horloge propre à chaque conteneur, § 5.1) ; les lignes du scénario restent comparées en entier.
 */
#[Group('zone-admin-vehicle-new')]
#[Scenario('demo')]
final class AdminVehicleNewDiffTest extends FormDiffTestCase
{
    use AdminVehicleForm;

    protected function comparableDbLine(string $line): string
    {
        if (preg_match('/^(?:\+ )?vehicle: (\d+)\|/', $line, $m) && !isset($this->initialSnapshot(Target::REF)['vehicle'][$m[1]])) {
            return self::maskCreatedAt($line);
        }

        return $line;
    }

    /**
     * Parcours : Basic ($user) → GET du formulaire → [sans Authorization si !$authOnPost] → POST ($times fois) →
     * GET de Location si 302 → GET de chaque chemin de $then.
     *
     * @param array<string, string>|null $fields
     * @param list<string>               $then
     *
     * @return \Closure(HttpClient): list<HttpResponse>
     */
    private function journey(?array $fields, bool|string $token = true, string $user = self::ADMIN_USER, bool $authOnPost = true, int $times = 1, array $then = []): \Closure
    {
        return function (HttpClient $c) use ($fields, $token, $user, $authOnPost, $times, $then): array {
            $c->withBasicAuth($user, self::adminPassword());
            [$form, $csrf] = $this->openForm($c, self::ADMIN_PATH);
            if (null !== $fields && false !== $token) {
                $fields[self::ADMIN_TOKEN] = true === $token ? $csrf : $token;
            }
            if (!$authOnPost) {
                $c->withoutBasicAuth();
            }
            $out = [$form];
            for ($i = 0; $i < $times; ++$i) {
                $out[] = $this->postForm($c, self::ADMIN_PATH, $fields);
            }
            if (302 === $out[1]->status) {
                $out[] = $this->follow($c, $out[1]);
            }
            foreach ($then as $path) {
                $out[] = $c->get($path);
            }

            return $out;
        };
    }

    private function submit(array $overrides, string $label): void
    {
        $this->assertJourneySameAsReference($this->journey($this->vehicleFields($overrides)), $label);
    }

    #[Test]
    #[Group('BH-300')]
    public function bh300_no_credentials_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', self::ADMIN_PATH);
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-301')]
    public function bh301_bad_credentials_same_as_reference(): void
    {
        foreach ([[self::ADMIN_USER, 'mauvais'], ['admin', self::adminPassword()], [self::ADMIN_USER, '']] as $credentials) {
            $this->assertSameAsReference('GET', self::ADMIN_PATH, $credentials);
        }
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-302')]
    public function bh302_case_insensitive_username_same_as_reference(): void
    {
        foreach (['Gestionnaire', 'GESTIONNAIRE'] as $user) {
            $this->assertSameAsReference('GET', self::ADMIN_PATH, [$user, self::adminPassword()]);
        }
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-303')]
    public function bh303_form_same_as_reference(): void
    {
        $this->assertSameAsReference('GET', self::ADMIN_PATH, [self::ADMIN_USER, self::adminPassword()]);
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-304')]
    public function bh304_nominal_creation_same_as_reference(): void
    {
        $this->submit([], 'BH-304');
    }

    #[Test]
    #[Group('BH-306')]
    public function bh306_plate_normalization_same_as_reference(): void
    {
        foreach (['1-xyz-789', ' 1 xyz 789 ', '1.XYZ.789', '1xyz789'] as $plate) {
            $this->restoreScenarioAgain();
            $this->submit(['plate' => $plate], "BH-306 « $plate »");
        }
    }

    #[Test]
    #[Group('BH-307')]
    public function bh307_invalid_plate_message_same_as_reference(): void
    {
        foreach (['ABC-123', '1-AB-123', 'X-XYZ-789'] as $plate) {
            $this->submit(['plate' => $plate], "BH-307 « $plate »");
        }
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-308')]
    public function bh308_blank_plate_same_as_reference(): void
    {
        foreach (['', '   '] as $plate) {
            $this->submit(['plate' => $plate], "BH-308 « $plate »");
        }
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-309')]
    public function bh309_duplicate_plate_same_as_reference(): void
    {
        foreach (['1-ABC-123', '1abc123'] as $plate) {
            $this->submit(['plate' => $plate], "BH-309 « $plate »");
        }
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-310')]
    public function bh310_double_post_same_as_reference(): void
    {
        $this->assertJourneySameAsReference($this->journey($this->vehicleFields(), times: 2), 'BH-310');
    }

    #[Test]
    #[Group('BH-312')]
    public function bh312_trimmed_brand_and_model_same_as_reference(): void
    {
        $this->submit(['brand' => '  Renault  ', 'model' => ' Master '], 'BH-312');
    }

    #[Test]
    #[Group('BH-313')]
    public function bh313_overlong_brand_and_model_same_as_reference(): void
    {
        $this->submit(['brand' => str_repeat('é', 51), 'model' => str_repeat('M', 60)], 'BH-313');
    }

    #[Test]
    #[Group('BH-314')]
    public function bh314_html_and_unicode_same_as_reference(): void
    {
        $journey = $this->journey(
            $this->vehicleFields(['brand' => '<b>Citroën</b>', 'model' => '"C3" & \'Aircross\' 🚐']),
            then: ['/vehicules', '/api/vehicules'],
        );
        $this->assertJourneySameAsReference($journey, 'BH-314');
    }

    #[Test]
    #[Group('BH-317')]
    public function bh317_invalid_mileage_same_as_reference(): void
    {
        foreach (['abc', '99999999999999999999'] as $mileage) {
            $this->submit(['mileage' => $mileage], "BH-317 « $mileage »");
        }
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-318')]
    public function bh318_decimal_mileage_same_as_reference(): void
    {
        $this->submit(['mileage' => '1500,7'], 'BH-318');
    }

    #[Test]
    #[Group('BH-319')]
    public function bh319_exponent_mileage_same_as_reference(): void
    {
        $this->submit(['mileage' => '1e3'], 'BH-319');
    }

    #[Test]
    #[Group('BH-320')]
    public function bh320_int32_overflow_mileage_same_as_reference(): void
    {
        $this->submit(['mileage' => '2147483648'], 'BH-320');
    }

    #[Test]
    #[Group('BH-321')]
    public function bh321_empty_mileage_same_as_reference(): void
    {
        $this->submit(['mileage' => ''], 'BH-321');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-323')]
    public function bh323_unknown_status_same_as_reference(): void
    {
        foreach (['3', 'abc'] as $status) {
            $this->submit(['status' => $status], "BH-323 « $status »");
        }
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-324')]
    public function bh324_missing_status_same_as_reference(): void
    {
        $this->submit(['status' => null], 'BH-324');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-325')]
    public function bh325_csrf_same_as_reference(): void
    {
        foreach ([false, 'faux'] as $token) {
            $this->assertJourneySameAsReference($this->journey($this->vehicleFields(), $token), 'BH-325 '.var_export($token, true));
        }
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-326')]
    public function bh326_extra_fields_same_as_reference(): void
    {
        foreach (['vehicle[createdAt]' => '2020-01-01 00:00:00', 'vehicle[id]' => '99'] as $name => $value) {
            $this->submit([$name => $value], "BH-326 $name");
        }
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-327')]
    public function bh327_post_without_body_same_as_reference(): void
    {
        $this->assertJourneySameAsReference($this->journey(null), 'BH-327');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-329')]
    public function bh329_post_without_credentials_same_as_reference(): void
    {
        $fields = $this->vehicleFields();
        $this->assertJourneySameAsReference(fn (HttpClient $c): array => [$this->postForm($c, self::ADMIN_PATH, $fields)], 'BH-329');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-330')]
    public function bh330_uppercase_username_same_as_reference(): void
    {
        $this->assertJourneySameAsReference($this->journey($this->vehicleFields(), user: 'GESTIONNAIRE'), 'BH-330');
    }

    #[Test]
    #[Group('BH-331')]
    public function bh331_vehicle_page_after_creation_same_as_reference(): void
    {
        $this->assertJourneySameAsReference($this->journey($this->vehicleFields(), then: ['/vehicules/6']), 'BH-331');
    }

    #[Test]
    #[Group('BH-332')]
    public function bh332_list_and_api_after_creation_same_as_reference(): void
    {
        $this->assertJourneySameAsReference($this->journey($this->vehicleFields(), then: ['/vehicules', '/api/vehicules']), 'BH-332');
    }

    #[Test]
    #[Group('BH-333')]
    public function bh333_session_remembers_authentication_same_as_reference(): void
    {
        $this->assertJourneySameAsReference($this->journey($this->vehicleFields(), authOnPost: false), 'BH-333');
    }

    #[Test]
    #[Group('BH-334')]
    public function bh334_admin_link_from_session_same_as_reference(): void
    {
        $this->assertJourneySameAsReference(function (HttpClient $c): array {
            $c->withBasicAuth(self::ADMIN_USER, self::adminPassword());
            [$form] = $this->openForm($c, self::ADMIN_PATH);
            $c->withoutBasicAuth();

            return [$form, $c->get('/vehicules')];
        }, 'BH-334');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-335')]
    public function bh335_created_at_same_date_as_reference(): void
    {
        $this->submit([], 'BH-335');
        $this->assertVehicleDiff([self::addedVehicle(6, '1-XYZ-789')], Target::REF);
        $this->assertVehicleDiff([self::addedVehicle(6, '1-XYZ-789')], Target::APP);
    }

    #[Test]
    #[Group('BH-336')]
    public function bh336_session_cookie_on_401_same_as_reference(): void
    {
        $responses = $this->assertSameAsReference('GET', self::ADMIN_PATH);
        self::assertSame(401, $responses['ref']->status);
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-337')]
    public function bh337_session_cookies_of_a_success_same_as_reference(): void
    {
        $this->submit([], 'BH-337');
    }
}
