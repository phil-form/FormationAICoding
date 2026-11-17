<?php

declare(strict_types=1);

namespace Flotte\Tests\Http;

use Flotte\Tests\Support\AdminVehicleForm;
use Flotte\Tests\Support\ApiJson;
use Flotte\Tests\Support\Db;
use Flotte\Tests\Support\FormTestCase;
use Flotte\Tests\Support\HttpClient;
use Flotte\Tests\Support\HttpResponse;
use Flotte\Tests\Support\Scenario;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * AdminController, route `admin_vehicle_new` — `GET|POST /admin/vehicules/nouveau` (HTTP Basic).
 * Fiches : tmp/tests-application.md § 5.2 (BH-300 à BH-337) ; parcours § 3.1 + authentification § 5.1.
 * Entrée par défaut : 1-XYZ-789 / Renault / Master / 1500 / statut 0, jeton valide, Basic au GET et au POST.
 * A4 = diff exact de la base, `created_at` réduit à sa date (2026-01-15). Erreurs 500 / 401 / 405 : statut, en-têtes
 * et base ; corps non contractuel (propositions § 5.4 et § 2.5, provisoires). A5 comparé en suite diff.
 */
#[Group('zone-admin-vehicle-new')]
#[Scenario('demo')]
final class AdminVehicleNewTest extends FormTestCase
{
    use AdminVehicleForm;

    private const GOLDEN = 'http/admin-vehicle-new/';
    private const REALM = 'Basic realm="Secured Area"';
    private const NOT_BLANK = 'This value should not be blank.';
    private const NOT_VALID = 'This value is not valid.';
    private const CSRF_INVALID = 'The CSRF token is invalid. Please try to resubmit the form.';
    private const EXTRA_FIELDS = 'This form should not contain extra fields.';
    private const DEMO_PLATES = ['1-ABC-123', '1-BCD-234', '1-CDE-345', '1-DEF-456', '2-EFG-567'];

    private static function flash(string $plate, string $user = 'gestionnaire'): string
    {
        return "Véhicule $plate ajouté par $user.";
    }

    /** 302 vers la fiche du véhicule créé, puis flash (une seule fois). */
    private function assertCreated(HttpClient $client, HttpResponse $r, int $id, string $plate = '1-XYZ-789'): HttpResponse
    {
        $this->assertStatus(302, $r);
        $this->assertCommonHeaders($r);
        self::assertSame("/vehicules/$id", $r->header('Location'), 'A2 Location relatif');
        $page = $this->follow($client, $r);
        $this->assertStatus(200, $page);
        self::assertSame([self::flash($plate)], $this->flashes($page), 'A3 flash après redirection');
        self::assertSame([], $this->flashes($client->get("/vehicules/$id")), 'le flash ne s\'affiche qu\'une fois');

        return $page;
    }

    /** 200 : formulaire réaffiché avec exactement ces erreurs ; base inchangée. */
    private function assertRefused(HttpResponse $r, array $errors): void
    {
        $this->assertStatus(200, $r);
        $this->assertCommonHeaders($r);
        self::assertSame(1, $r->crawler()->filter('form[name="vehicle"]')->count(), 'formulaire réaffiché');
        self::assertSame($errors, $this->vehicleErrors($r), 'A3 erreurs du formulaire (texte et emplacement)');
        $this->assertDbUnchanged();
    }

    /** 500 : statut + en-têtes ; corps non contractuel ; base strictement inchangée (proposition § 5.4). */
    private function assertServerError(HttpResponse $r): void
    {
        $this->assertStatus(500, $r);
        self::assertSame(self::FLEET_VERSION, $r->header('X-Fleet-Version'), 'A2 X-Fleet-Version — '.$r->describe());
        self::assertSame(self::HTML_CONTENT_TYPE, $r->header('Content-Type'), 'A2 Content-Type — '.$r->describe());
        $this->assertDbUnchanged();
    }

    /** 401 : WWW-Authenticate, en-têtes communs, corps vide. */
    private function assertUnauthorized(HttpResponse $r): void
    {
        $this->assertStatus(401, $r);
        $this->assertCommonHeaders($r);
        self::assertSame(self::REALM, $r->header('WWW-Authenticate'), 'A2 WWW-Authenticate — '.$r->describe());
        self::assertSame('', $r->body, 'A3 corps vide');
    }

    private function hasAdminLink(HttpResponse $r): bool
    {
        return 1 === $r->crawler()->filter('nav a[href="/admin/vehicules/nouveau"]')->count();
    }

    // --- BH-300 à BH-337 ------------------------------------------------------------------------------------

    #[Test]
    #[Group('BH-300')]
    public function bh300_form_without_credentials_is_unauthorized(): void
    {
        $this->assertUnauthorized($this->send('GET', self::ADMIN_PATH));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-301')]
    public function bh301_wrong_unknown_or_empty_credentials_are_unauthorized(): void
    {
        foreach ([[self::ADMIN_USER, 'mauvais'], ['admin', self::adminPassword()], [self::ADMIN_USER, '']] as $credentials) {
            $this->assertUnauthorized($this->send('GET', self::ADMIN_PATH, $credentials));
        }
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-302')]
    public function bh302_username_is_case_insensitive(): void
    {
        foreach (['Gestionnaire', 'GESTIONNAIRE'] as $user) {
            $r = $this->send('GET', self::ADMIN_PATH, [$user, self::adminPassword()]);

            $this->assertStatus(200, $r);
            self::assertSame('Nouveau véhicule', $r->crawler()->filter('h1')->text(), "formulaire servi à « $user »");
        }
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-303')]
    public function bh303_form_with_defaults_admin_link_and_no_button(): void
    {
        $r = $this->send('GET', self::ADMIN_PATH, [self::ADMIN_USER, self::adminPassword()]);

        $this->assertStatus(200, $r);
        $this->assertCommonHeaders($r);
        $this->assertSessionCookie($r);
        $this->assertGolden(self::GOLDEN.'bh303.html', $r);
        $c = $r->crawler();
        self::assertSame('Nouveau véhicule', $c->filter('h1')->text());
        self::assertSame(
            ['vehicle[plate]', 'vehicle[brand]', 'vehicle[model]', 'vehicle[mileage]'],
            $c->filter('form[name="vehicle"] input[required]')->each(static fn ($i): string => (string) $i->attr('name')),
        );
        self::assertSame('number', $c->filter('#vehicle_mileage')->attr('type'));
        self::assertSame('0', $this->fieldValue($r, 'vehicle_mileage'), 'kilométrage pré-rempli');
        self::assertSame(
            [['0', 'Disponible', true], ['1', 'En entretien', false], ['2', 'Hors service', false]],
            $c->filter('#vehicle_status option')->each(static fn ($o): array => [(string) $o->attr('value'), $o->text(), null !== $o->attr('selected')]),
        );
        self::assertSame(1, $c->filter('input[type="hidden"][name="vehicle[_token]"]')->count(), 'jeton caché');
        self::assertSame(0, $c->filter('button, input[type="submit"]')->count(), 'aucun bouton de soumission');
        self::assertTrue($this->hasAdminLink($r), 'lien « Ajouter un véhicule » dans <nav>');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-304')]
    public function bh304_nominal_creation_redirects_with_flash(): void
    {
        [$client, $r] = $this->submitVehicle($this->vehicleFields());

        $page = $this->assertCreated($client, $r, 6);
        $this->assertGolden(self::GOLDEN.'bh304-after-redirect.html', $page);
        $this->assertVehicleDiff([self::addedVehicle(6, '1-XYZ-789')]);
    }

    #[Test]
    #[Group('BH-305')]
    #[Scenario('empty')]
    public function bh305_first_vehicle_on_empty_base_gets_id_1(): void
    {
        [$client, $r] = $this->submitVehicle($this->vehicleFields());

        $this->assertCreated($client, $r, 1);
        $this->assertVehicleDiff([self::addedVehicle(1, '1-XYZ-789')]);
    }

    #[Test]
    #[Group('BH-306')]
    public function bh306_plate_is_normalized_before_storage(): void
    {
        foreach (['1-xyz-789', ' 1 xyz 789 ', '1.XYZ.789', '1xyz789'] as $plate) {
            $this->restoreScenarioAgain();
            [$client, $r] = $this->submitVehicle($this->vehicleFields(['plate' => $plate]));

            $this->assertCreated($client, $r, 6);
            $this->assertVehicleDiff([self::addedVehicle(6, '1-XYZ-789')]);
        }
    }

    #[Test]
    #[Group('BH-307')]
    public function bh307_invalid_plate_is_refused_quoting_raw_input(): void
    {
        foreach (['ABC-123' => 'abc-123', '1-AB-123' => '1-ab-123', 'X-XYZ-789' => 'x-xyz-789'] as $plate => $variant) {
            [, $r] = $this->submitVehicle($this->vehicleFields(['plate' => $plate]));

            $this->assertRefused($r, ['vehicle_plate' => ["La plaque \"$plate\" n'est pas une plaque belge valide (format 1-ABC-123)."]]);
            $this->assertGolden(self::GOLDEN."bh307-$variant.html", $r);
        }
    }

    #[Test]
    #[Group('BH-308')]
    public function bh308_blank_plate_is_refused_after_trim(): void
    {
        foreach (['' => 'empty', '   ' => 'spaces'] as $plate => $variant) {
            [, $r] = $this->submitVehicle($this->vehicleFields(['plate' => (string) $plate]));

            $this->assertRefused($r, ['vehicle_plate' => [self::NOT_BLANK]]);
            $this->assertGolden(self::GOLDEN."bh308-$variant.html", $r);
        }
    }

    #[Test]
    #[Group('BH-309')]
    public function bh309_existing_plate_is_a_server_error(): void
    {
        foreach (['1-ABC-123', '1abc123'] as $plate) {
            [, $r] = $this->submitVehicle($this->vehicleFields(['plate' => $plate]));

            $this->assertServerError($r);
        }
    }

    #[Test]
    #[Group('BH-310')]
    public function bh310_same_post_twice_gives_302_then_500_and_a_single_row(): void
    {
        $client = $this->adminSession();
        [, $csrf] = $this->openForm($client, self::ADMIN_PATH);
        $fields = $this->vehicleFields() + [self::ADMIN_TOKEN => $csrf];

        $first = $this->postForm($client, self::ADMIN_PATH, $fields);
        $second = $this->postForm($client, self::ADMIN_PATH, $fields);

        $this->assertStatus(302, $first);
        self::assertSame('/vehicules/6', $first->header('Location'));
        $this->assertStatus(500, $second);
        self::assertSame(self::FLEET_VERSION, $second->header('X-Fleet-Version'));
        $this->assertVehicleDiff([self::addedVehicle(6, '1-XYZ-789')]);
    }

    #[Test]
    #[Group('BH-311')]
    public function bh311_blank_brand_and_model_are_refused(): void
    {
        [, $r] = $this->submitVehicle($this->vehicleFields(['brand' => '', 'model' => '']));

        $this->assertRefused($r, ['vehicle_brand' => [self::NOT_BLANK], 'vehicle_model' => [self::NOT_BLANK]]);
        $this->assertGolden(self::GOLDEN.'bh311.html', $r);
    }

    #[Test]
    #[Group('BH-312')]
    public function bh312_brand_and_model_are_trimmed(): void
    {
        [$client, $r] = $this->submitVehicle($this->vehicleFields(['brand' => '  Renault  ', 'model' => ' Master ']));

        $this->assertCreated($client, $r, 6);
        $this->assertVehicleDiff([self::addedVehicle(6, '1-XYZ-789')]);
    }

    #[Test]
    #[Group('BH-313')]
    public function bh313_brand_and_model_longer_than_columns_are_stored_in_full(): void
    {
        $brand = str_repeat('B', 51);
        $model = str_repeat('M', 60);
        [$client, $r] = $this->submitVehicle($this->vehicleFields(['brand' => $brand, 'model' => $model]));

        $this->assertCreated($client, $r, 6);
        $this->assertVehicleDiff([self::addedVehicle(6, '1-XYZ-789', $brand, $model)]);
    }

    #[Test]
    #[Group('BH-314')]
    public function bh314_html_and_unicode_stored_raw_and_displayed_escaped(): void
    {
        $brand = '<b>Citroën</b>';
        $model = '"C3" & \'Aircross\' 🚐';
        [$client, $r] = $this->submitVehicle($this->vehicleFields(['brand' => $brand, 'model' => $model]));

        $page = $this->assertCreated($client, $r, 6);
        $this->assertVehicleDiff([self::addedVehicle(6, '1-XYZ-789', $brand, $model)]);
        $escaped = '&lt;b&gt;Citroën&lt;/b&gt; &quot;C3&quot; &amp; &#039;Aircross&#039; 🚐';
        self::assertStringContainsString("<h1>1-XYZ-789 — $escaped</h1>", $page->body, 'A3 fiche : échappé');
        self::assertStringContainsString($escaped, $this->send('GET', '/vehicules')->body, 'A3 liste : échappé');
        $api = $this->send('GET', ApiJson::PATH)->body;
        self::assertStringContainsString(
            ApiJson::lit('"label":"{u003C}b{u003E}Citro{u00eb}n{u003C}\/b{u003E} {u0022}C3{u0022} {u0026} {u0027}Aircross{u0027} {ud83d}{ude90}"'),
            $api,
            'A3 API : échappements JSON_HEX_*',
        );
        self::assertSame("$brand $model", ApiJson::byPlate(ApiJson::decode($api), '1-XYZ-789')['label']);
    }

    #[Test]
    #[Group('BH-315')]
    public function bh315_negative_mileage_is_refused(): void
    {
        [, $r] = $this->submitVehicle($this->vehicleFields(['mileage' => '-1']));

        $this->assertRefused($r, ['vehicle_mileage' => ['This value should be greater than or equal to 0.']]);
        $this->assertGolden(self::GOLDEN.'bh315.html', $r);
    }

    #[Test]
    #[Group('BH-316')]
    public function bh316_zero_mileage_is_accepted(): void
    {
        [$client, $r] = $this->submitVehicle($this->vehicleFields(['mileage' => '0']));

        $this->assertCreated($client, $r, 6);
        $this->assertVehicleDiff([self::addedVehicle(6, '1-XYZ-789', mileage: '0')]);
    }

    #[Test]
    #[Group('BH-317')]
    public function bh317_non_numeric_or_huge_mileage_is_not_valid(): void
    {
        foreach (['abc' => 'abc', '99999999999999999999' => 'huge'] as $mileage => $variant) {
            [, $r] = $this->submitVehicle($this->vehicleFields(['mileage' => (string) $mileage]));

            $this->assertRefused($r, ['vehicle_mileage' => [self::NOT_VALID]]);
            $this->assertGolden(self::GOLDEN."bh317-$variant.html", $r);
        }
    }

    #[Test]
    #[Group('BH-318')]
    public function bh318_decimal_mileage_is_truncated(): void
    {
        [$client, $r] = $this->submitVehicle($this->vehicleFields(['mileage' => '1500,7']));

        $this->assertCreated($client, $r, 6);
        $this->assertVehicleDiff([self::addedVehicle(6, '1-XYZ-789', mileage: '1500')]);
    }

    #[Test]
    #[Group('BH-319')]
    public function bh319_exponent_mileage_is_stored_as_thousand(): void
    {
        [$client, $r] = $this->submitVehicle($this->vehicleFields(['mileage' => '1e3']));

        $this->assertCreated($client, $r, 6);
        $this->assertVehicleDiff([self::addedVehicle(6, '1-XYZ-789', mileage: '1000')]);
    }

    #[Test]
    #[Group('BH-320')]
    public function bh320_mileage_of_two_pow_31_is_stored(): void
    {
        [$client, $r] = $this->submitVehicle($this->vehicleFields(['mileage' => '2147483648']));

        $this->assertCreated($client, $r, 6);
        $this->assertVehicleDiff([self::addedVehicle(6, '1-XYZ-789', mileage: '2147483648')]);
    }

    #[Test]
    #[Group('BH-321')]
    public function bh321_empty_mileage_is_a_server_error(): void
    {
        [, $r] = $this->submitVehicle($this->vehicleFields(['mileage' => '']));

        $this->assertServerError($r);
    }

    #[Test]
    #[Group('BH-322')]
    public function bh322_status_two_is_stored(): void
    {
        [$client, $r] = $this->submitVehicle($this->vehicleFields(['status' => '2']));

        $this->assertCreated($client, $r, 6);
        $this->assertVehicleDiff([self::addedVehicle(6, '1-XYZ-789', status: '2')]);
    }

    #[Test]
    #[Group('BH-323')]
    public function bh323_unknown_status_is_not_valid(): void
    {
        foreach (['3', 'abc'] as $status) {
            [, $r] = $this->submitVehicle($this->vehicleFields(['status' => $status]));

            $this->assertRefused($r, ['vehicle_status' => [self::NOT_VALID]]);
            $this->assertGolden(self::GOLDEN."bh323-$status.html", $r);
        }
    }

    #[Test]
    #[Group('BH-324')]
    public function bh324_missing_status_is_a_server_error(): void
    {
        [, $r] = $this->submitVehicle($this->vehicleFields(['status' => null]));

        $this->assertServerError($r);
    }

    #[Test]
    #[Group('BH-325')]
    public function bh325_missing_or_wrong_csrf_token_is_refused(): void
    {
        foreach (['missing' => false, 'wrong' => 'faux'] as $variant => $token) {
            [, $r] = $this->submitVehicle($this->vehicleFields(), $token);

            $this->assertRefused($r, ['global' => [self::CSRF_INVALID]]);
            if ('missing' === $variant) {
                $this->assertGolden(self::GOLDEN.'bh325-missing.html', $r);
            }
        }
    }

    #[Test]
    #[Group('BH-326')]
    public function bh326_extra_fields_are_refused_without_assignment(): void
    {
        foreach (['vehicle[createdAt]' => '2020-01-01 00:00:00', 'vehicle[id]' => '99'] as $name => $value) {
            [, $r] = $this->submitVehicle($this->vehicleFields([$name => $value]));

            $this->assertRefused($r, ['global' => [self::EXTRA_FIELDS]]);
        }
    }

    #[Test]
    #[Group('BH-327')]
    public function bh327_post_without_body_redisplays_form_without_error(): void
    {
        [, $r] = $this->submitVehicle(null);

        $this->assertRefused($r, []);
        $this->assertGolden(self::GOLDEN.'bh327.html', $r);
    }

    #[Test]
    #[Group('BH-328')]
    public function bh328_put_is_method_not_allowed(): void
    {
        $r = $this->send('PUT', self::ADMIN_PATH, [self::ADMIN_USER, self::adminPassword()]);

        $this->assertStatus(405, $r);
        $this->assertCommonHeaders($r);
        self::assertSame('GET, POST, HEAD', $r->header('Allow'));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-329')]
    public function bh329_post_without_credentials_nor_session_is_unauthorized(): void
    {
        $r = $this->postForm($this->session(), self::ADMIN_PATH, $this->vehicleFields());

        $this->assertUnauthorized($r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-330')]
    public function bh330_flash_shows_account_name_not_typed_name(): void
    {
        [$client, $r] = $this->submitVehicle($this->vehicleFields(), client: $this->adminSession(user: 'GESTIONNAIRE'));

        $this->assertCreated($client, $r, 6);
        $this->assertVehicleDiff([self::addedVehicle(6, '1-XYZ-789')]);
    }

    #[Test]
    #[Group('BH-331')]
    public function bh331_created_vehicle_page(): void
    {
        [$client, $r] = $this->submitVehicle($this->vehicleFields());
        $this->assertCreated($client, $r, 6);

        $page = $this->send('GET', '/vehicules/6');
        $this->assertStatus(200, $page);
        $this->assertGolden(self::GOLDEN.'bh331-vehicle-page.html', $page);
        $c = $page->crawler();
        self::assertSame('1-XYZ-789 — Renault Master', $c->filter('h1')->text());
        self::assertStringContainsString('Kilométrage : 1.500 km · Statut : Disponible', $c->text());
        self::assertSame('Réserver ce véhicule', $c->filter('a[href="/vehicules/6/reserver"]')->text());
        self::assertSame(['Aucune réservation à venir.'], $this->showItems($page, 0), 'à venir : message seul');
        self::assertSame([], $this->showItems($page, 1), 'historique : <ul> vide');
    }

    #[Test]
    #[Group('BH-332')]
    public function bh332_created_vehicle_in_list_and_api_in_plate_order(): void
    {
        [$client, $r] = $this->submitVehicle($this->vehicleFields());
        $this->assertCreated($client, $r, 6);

        $expected = ['1-ABC-123', '1-BCD-234', '1-CDE-345', '1-DEF-456', '1-XYZ-789', '2-EFG-567'];
        $list = $this->send('GET', '/vehicules');
        $this->assertStatus(200, $list);
        self::assertSame($expected, $this->listPlates($list), 'A3 liste : 6 lignes triées par plaque');
        self::assertSame(['1-XYZ-789', 'Renault Master', '1.500 km', 'Disponible'], $this->listRows($list)[4]);

        $api = $this->send('GET', ApiJson::PATH);
        $this->assertStatus(200, $api);
        $items = ApiJson::decode($api->body);
        ApiJson::assertStructure($items);
        self::assertSame($expected, ApiJson::plates($items), 'A3 API : 6 éléments triés par plaque');
        self::assertSame(
            ['id' => 6, 'plate' => '1-XYZ-789', 'label' => 'Renault Master', 'mileage' => 1500, 'status' => 'Disponible', 'nextReservation' => ''],
            ApiJson::byPlate($items, '1-XYZ-789'),
        );
    }

    #[Test]
    #[Group('BH-333')]
    public function bh333_post_without_authorization_in_same_session_is_accepted(): void
    {
        $client = $this->adminSession();
        [, $csrf] = $this->openForm($client, self::ADMIN_PATH);
        $client->withoutBasicAuth();

        $r = $this->postForm($client, self::ADMIN_PATH, $this->vehicleFields() + [self::ADMIN_TOKEN => $csrf]);

        $this->assertCreated($client, $r, 6);
        $this->assertVehicleDiff([self::addedVehicle(6, '1-XYZ-789')]);
    }

    #[Test]
    #[Group('BH-334')]
    public function bh334_session_keeps_admin_link_on_public_list(): void
    {
        $client = $this->adminSession();
        $this->openForm($client, self::ADMIN_PATH);
        $client->withoutBasicAuth();

        $r = $client->get('/vehicules');

        $this->assertStatus(200, $r);
        self::assertTrue($this->hasAdminLink($r), 'lien « Ajouter un véhicule » affiché grâce à la session');
        self::assertSame(self::DEMO_PLATES, $this->listPlates($r));
        self::assertFalse($this->hasAdminLink($this->send('GET', '/vehicules')), 'contrôle : absent sans session');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-335')]
    public function bh335_created_at_is_set_from_the_container_clock(): void
    {
        [$client, $r] = $this->submitVehicle($this->vehicleFields());
        $this->assertCreated($client, $r, 6);

        $row = Db::query($this->target(), 'SELECT created_at, typeof(created_at) AS t FROM vehicle WHERE id = 6')[0];
        self::assertSame('text', $row['t'], 'created_at de type TEXT');
        self::assertMatchesRegularExpression('/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/', $row['created_at'], 'format Y-m-d H:i:s');
        self::assertSame('2026-01-15', substr($row['created_at'], 0, 10), 'date de l\'horloge du conteneur (heure non assertée)');
    }

    #[Test]
    #[Group('BH-336')]
    public function bh336_refused_request_still_opens_a_session(): void
    {
        $r = $this->send('GET', self::ADMIN_PATH);

        $this->assertUnauthorized($r);
        $this->assertSessionCookie($r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-337')]
    public function bh337_successful_creation_sets_session_cookie(): void
    {
        // Parcours réel d'un succès : la session est ouverte (Set-Cookie) dès le GET authentifié du formulaire ; le
        // POST, qui renvoie le cookie reçu, ne repose pas de cookie (observé sur app-ref).
        $client = $this->adminSession();
        $form = $client->get(self::ADMIN_PATH);
        $this->assertStatus(200, $form);
        $this->assertSessionCookie($form);
        [, $r] = $this->submitVehicle($this->vehicleFields(), client: $client);

        $this->assertStatus(302, $r);
        $this->assertNoCookie($r);
        $this->assertCreated($client, $r, 6);
    }
}
