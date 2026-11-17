<?php

declare(strict_types=1);

namespace Flotte\Tests\Http;

use Flotte\Tests\Support\ApiJson;
use Flotte\Tests\Support\CaseTestCase;
use Flotte\Tests\Support\HttpResponse;
use Flotte\Tests\Support\Scenario;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * ApiController, route `api_vehicles` — `GET /api/vehicules` (API JSON du planning des dépôts).
 * Fiches : tmp/tests-application.md § 4.2 (BH-200 à BH-224).
 *
 * A3 : corps comparé **octet par octet** au golden (aucune normalisation) + empreinte SHA-256, puis assertions
 * structurelles après json_decode. Erreurs 401/404/405 : pages HTML du framework, statut + en-têtes seulement.
 * A4 : base inchangée pour toutes les fiches (lecture seule). A5 : comparé en suite diff uniquement (proposition § 2.5).
 */
#[Group('zone-api-vehicles')]
final class ApiVehiclesTest extends CaseTestCase
{
    private const DEMO_PLATES = ['1-ABC-123', '1-BCD-234', '1-CDE-345', '1-DEF-456', '2-EFG-567'];

    /** Ordre binaire des plaques brutes du scénario edge (observé sur app-ref). */
    private const EDGE_PLATES = [
        ' 2-BBB-004 ', '0-AAA-000', '1-ABC-12', '1-ADJ-001', '1-CST-001', '1-DST-001', '1-EDG-011', '1-EDG-012',
        '1-LNG-040', '1-MAX-050', '1-NEW-001', '1-OLD-001', '1-OVD-001', '1-SAM-001', '1-TIE-001', '1-TIE-002',
        '1-TIE-003', '1-TIE-004', '1-UNI-001', '1-XSS-001', '1-aaa-003', '2.BBB.006', '2bbb005', '9-ZZZ-999',
        'ABC-123', 'CD-1234', 'Z-ZZZ-999',
    ];

    private const CORS_AND_CACHE_VALIDATORS = [
        'access-control-allow-origin', 'access-control-allow-methods', 'access-control-allow-headers',
        'access-control-allow-credentials', 'access-control-expose-headers', 'etag', 'last-modified',
    ];

    /** A1 + A2 d'une réponse JSON réussie (sans authentification : aucun cookie). */
    private function assertJsonOk(HttpResponse $r, bool $cookie = false): void
    {
        $this->assertStatus(200, $r);
        self::assertSame(ApiJson::CONTENT_TYPE, $r->header('Content-Type'), 'A2 Content-Type (sans charset) — '.$r->describe());
        self::assertSame(self::FLEET_VERSION, $r->header('X-Fleet-Version'), 'A2 X-Fleet-Version — '.$r->describe());
        self::assertSame('no-cache', $r->header('Cache-Control'), 'A2 Cache-Control — '.$r->describe());
        $cookie ? $this->assertSessionCookie($r) : $this->assertNoCookie($r);
    }

    private function assertNoCorsNorValidators(HttpResponse $r): void
    {
        foreach (self::CORS_AND_CACHE_VALIDATORS as $name) {
            self::assertFalse($r->hasHeader($name), "A2 en-tête « $name » inattendu — ".$r->describe());
        }
    }

    /** Erreur du framework (401/404/405) : page HTML, en-têtes communs, pas de cookie ; corps non contractuel. */
    private function assertHtmlError(int $status, HttpResponse $r): void
    {
        $this->assertStatus($status, $r);
        $this->assertCommonHeaders($r);
        $this->assertNoCookie($r);
        self::assertNull(json_decode($r->body), 'A3 : corps HTML, pas de JSON d\'erreur — '.$r->describe());
    }

    #[Test]
    #[Group('BH-200')]
    #[Scenario('empty')]
    public function bh200_empty_fleet_returns_empty_array(): void
    {
        $r = $this->send('GET', ApiJson::PATH);

        $this->assertJsonOk($r);
        self::assertSame('[]', $r->body, 'A3 corps exactement « [] » (2 octets)');
        ApiJson::assertMatchesGolden('empty', $r->body);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-201')]
    #[Scenario('demo')]
    public function bh201_demo_fleet_byte_for_byte(): void
    {
        $r = $this->send('GET', ApiJson::PATH);

        $this->assertJsonOk($r);
        ApiJson::assertMatchesGolden('demo', $r->body);
        self::assertSame(591, \strlen($r->body), 'A3 taille');
        self::assertStringStartsWith('6fe447542af8', hash('sha256', $r->body), 'A3 empreinte');
        $items = ApiJson::decode($r->body);
        ApiJson::assertStructure($items);
        self::assertSame(self::DEMO_PLATES, ApiJson::plates($items));
        self::assertSame(['15/06/2030 08:00', '', '', '', ''], array_column($items, 'nextReservation'));
        self::assertStringContainsString('"nextReservation":"15\/06\/2030 08:00"', $r->body, 'A3 barres obliques échappées');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-202')]
    #[Scenario('demo')]
    public function bh202_json_headers_without_session_cors_or_validators(): void
    {
        $r = $this->send('GET', ApiJson::PATH);

        $this->assertJsonOk($r);
        self::assertSame([], $r->headerValues('Set-Cookie'), 'A2 aucun Set-Cookie');
        $this->assertNoCorsNorValidators($r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-203')]
    #[Scenario('demo')]
    public function bh203_compact_body_without_bom_or_trailing_newline(): void
    {
        $r = $this->send('GET', ApiJson::PATH);

        $this->assertJsonOk($r);
        self::assertStringStartsNotWith("\xEF\xBB\xBF", $r->body, 'A3 pas de BOM');
        self::assertStringStartsWith('[{"id":1,', $r->body);
        self::assertStringEndsWith('"}]', $r->body, 'A3 pas de retour à la ligne final');
        self::assertSame('', ApiJson::whitespaceOutsideStrings($r->body), 'A3 aucun blanc hors des valeurs');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-204')]
    #[Scenario('demo')]
    public function bh204_ids_and_mileage_are_numbers_other_fields_strings(): void
    {
        $r = $this->send('GET', ApiJson::PATH);

        $this->assertJsonOk($r);
        $items = ApiJson::decode($r->body);
        ApiJson::assertStructure($items);
        foreach ($items as $item) {
            self::assertIsString($item['status'], 'demo : tous les statuts sont connus');
        }
        self::assertStringContainsString('{"id":1,"plate":"1-ABC-123","label":"Renault Kangoo","mileage":84210,"status":"Disponible",', $r->body);
        self::assertStringNotContainsString('"mileage":"', $r->body, 'A3 mileage jamais en chaîne');
        self::assertStringNotContainsString('"id":"', $r->body, 'A3 id jamais en chaîne');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-205')]
    #[Scenario('edge')]
    public function bh205_raw_plates_in_binary_order(): void
    {
        $r = $this->send('GET', ApiJson::PATH);

        $this->assertJsonOk($r);
        ApiJson::assertMatchesGolden('edge', $r->body);
        $items = ApiJson::decode($r->body);
        ApiJson::assertStructure($items);
        self::assertSame(self::EDGE_PLATES, ApiJson::plates($items), 'A3 ordre binaire des plaques brutes');
        self::assertStringStartsWith('[{"id":4,"plate":" 2-BBB-004 ",', $r->body, 'A3 espaces conservés');
        self::assertSame(3, ApiJson::byPlate($items, '1-aaa-003')['id'], 'A3 plaque en minuscules non normalisée');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-206')]
    #[Scenario('edge')]
    public function bh206_html_quotes_and_non_ascii_are_escaped(): void
    {
        $r = $this->send('GET', ApiJson::PATH);

        $this->assertJsonOk($r);
        ApiJson::assertMatchesGolden('edge', $r->body);
        self::assertSame(3458, \strlen($r->body), 'A3 taille');
        self::assertStringStartsWith('6f2bebf09564', hash('sha256', $r->body), 'A3 empreinte');
        // Fragments littéraux : {uXXXX} = séquence d'échappement JSON (voir ApiJson::lit).
        self::assertStringContainsString(
            ApiJson::lit('{"id":18,"plate":"1-XSS-001","label":"Citro{u00eb}n {u003C}b{u003E}C3{u003C}\/b{u003E} {u0022}Aircross{u0022} {u0026} {u003C}script{u003E}alert({u0027}x{u0027}){u003C}\/script{u003E}",'),
            $r->body,
        );
        self::assertStringContainsString(
            ApiJson::lit('"label":"{u0160}koda {u00c9}nergie {u00d6}l{u00e7}{u00fc} {u00d1}and{u00fa} {u96fb}{u6c17} {ud83d}{ude90}"'),
            $r->body,
        );
        $items = ApiJson::decode($r->body);
        self::assertSame('Citroën <b>C3</b> "Aircross" & <script>alert(\'x\')</script>', ApiJson::byPlate($items, '1-XSS-001')['label']);
        self::assertSame('Škoda Énergie Ölçü Ñandú 電気 🚐', ApiJson::byPlate($items, '1-UNI-001')['label']);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-207')]
    #[Scenario('edge')]
    public function bh207_int32_max_mileage_is_a_plain_number(): void
    {
        $r = $this->send('GET', ApiJson::PATH);

        $this->assertJsonOk($r);
        self::assertStringContainsString('"plate":"9-ZZZ-999","label":"Peugeot Partner","mileage":2147483647,', $r->body);
        self::assertSame(2147483647, ApiJson::byPlate(ApiJson::decode($r->body), '9-ZZZ-999')['mileage']);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-208')]
    #[Scenario('edge')]
    public function bh208_label_of_maximum_length(): void
    {
        $r = $this->send('GET', ApiJson::PATH);

        $this->assertJsonOk($r);
        $label = ApiJson::byPlate(ApiJson::decode($r->body), '1-MAX-050')['label'];
        self::assertSame(str_repeat('B', 50).' '.str_repeat('M', 50), $label);
        self::assertSame(101, mb_strlen($label));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-209')]
    #[Scenario('edge')]
    public function bh209_overdue_booking_reported_as_next(): void
    {
        $r = $this->send('GET', ApiJson::PATH);

        $this->assertJsonOk($r);
        self::assertStringContainsString('"plate":"1-OVD-001","label":"Peugeot Expert","mileage":21000,"status":"Disponible","nextReservation":"05\/01\/2026 08:00"}', $r->body);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-210')]
    #[Scenario('edge')]
    public function bh210_same_start_bookings_give_that_start(): void
    {
        $r = $this->send('GET', ApiJson::PATH);

        $this->assertJsonOk($r);
        self::assertSame('20/02/2026 08:00', ApiJson::byPlate(ApiJson::decode($r->body), '1-SAM-001')['nextReservation']);
        self::assertStringContainsString('"plate":"1-SAM-001",', $r->body);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-211')]
    #[Scenario('edge')]
    public function bh211_unavailable_vehicles_still_report_next_booking(): void
    {
        $r = $this->send('GET', ApiJson::PATH);

        $this->assertJsonOk($r);
        $items = ApiJson::decode($r->body);
        $v11 = ApiJson::byPlate($items, '1-EDG-011');
        $v12 = ApiJson::byPlate($items, '1-EDG-012');
        self::assertSame(['En entretien', '03/02/2026 08:00'], [$v11['status'], $v11['nextReservation']]);
        self::assertSame(['Hors service', '14/01/2026 08:00'], [$v12['status'], $v12['nextReservation']]);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-212')]
    #[Scenario('edge')]
    public function bh212_first_confirmed_of_a_long_list(): void
    {
        $r = $this->send('GET', ApiJson::PATH);

        $this->assertJsonOk($r);
        self::assertSame('19/01/2026 08:00', ApiJson::byPlate(ApiJson::decode($r->body), '1-LNG-040')['nextReservation']);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-213')]
    #[Scenario('edge')]
    public function bh213_naive_time_on_dst_day(): void
    {
        $r = $this->send('GET', ApiJson::PATH);

        $this->assertJsonOk($r);
        self::assertStringContainsString('"plate":"1-DST-001","label":"Fiat Ducato","mileage":24000,"status":"Disponible","nextReservation":"29\/03\/2026 01:30"}', $r->body);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-214')]
    #[Scenario('booking')]
    public function bh214_next_reservation_on_booking_scenario(): void
    {
        $r = $this->send('GET', ApiJson::PATH);

        $this->assertJsonOk($r);
        ApiJson::assertMatchesGolden('booking', $r->body);
        $items = ApiJson::decode($r->body);
        ApiJson::assertStructure($items);
        self::assertCount(8, $items);
        self::assertSame(
            [
                '1-BOK-001' => '', '1-BOK-002' => '10/02/2026 10:00', '1-BOK-003' => '02/02/2026 08:00',
                '1-BOK-004' => '', '1-BOK-005' => '', '1-BOK-006' => '14/01/2026 08:00',
                '1-BOK-007' => '05/01/2026 08:00', '1-BOK-008' => '',
            ],
            array_column($items, 'nextReservation', 'plate'),
        );
        self::assertSame('En entretien', ApiJson::byPlate($items, '1-BOK-003')['status']);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-215')]
    #[Scenario('anomalies')]
    public function bh215_inconsistent_data_unknown_status_is_null(): void
    {
        $r = $this->send('GET', ApiJson::PATH);

        $this->assertJsonOk($r);
        ApiJson::assertMatchesGolden('anomalies', $r->body);
        $items = ApiJson::decode($r->body);
        ApiJson::assertStructure($items);
        self::assertCount(8, $items);
        self::assertSame('', $items[0]['plate'], 'A3 plaque vide triée en premier');
        self::assertNull(ApiJson::byPlate($items, '1-ANO-001')['status'], 'A3 statut 3 → null');
        self::assertNull(ApiJson::byPlate($items, '1-ANO-002')['status'], 'A3 statut -1 → null');
        self::assertStringContainsString('"plate":"1-ANO-001","label":"Renault Kangoo","mileage":1000,"status":null,', $r->body);
        self::assertSame(' ', ApiJson::byPlate($items, '1-ANO-005')['label'], 'A3 marque et modèle vides : une espace');
        self::assertSame(-500, ApiJson::byPlate($items, '1-ANO-003')['mileage']);
        self::assertSame('08/01/2026 18:00', ApiJson::byPlate($items, '1-ANO-007')['nextReservation'], 'A3 fin avant début');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-216')]
    #[Scenario('nominal')]
    public function bh216_realistic_fleet_byte_for_byte(): void
    {
        $r = $this->send('GET', ApiJson::PATH);

        $this->assertJsonOk($r);
        ApiJson::assertMatchesGolden('nominal', $r->body);
        self::assertSame(3124, \strlen($r->body), 'A3 taille');
        $items = ApiJson::decode($r->body);
        ApiJson::assertStructure($items);
        self::assertCount(24, $items);
        $plates = ApiJson::plates($items);
        $sorted = $plates;
        sort($sorted, SORT_STRING);
        self::assertSame($sorted, $plates, 'A3 tri par plaque');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-217')]
    #[Scenario('volume')]
    public function bh217_volume_returns_all_vehicles(): void
    {
        $r = $this->send('GET', ApiJson::PATH);

        $this->assertJsonOk($r);
        ApiJson::assertMatchesGolden('volume', $r->body);
        self::assertSame(58504, \strlen($r->body), 'A3 taille');
        $items = ApiJson::decode($r->body);
        ApiJson::assertStructure($items);
        self::assertCount(500, $items);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-218')]
    #[Scenario('demo')]
    public function bh218_query_parameters_are_ignored(): void
    {
        $r = $this->send('GET', ApiJson::PATH.'?status=1&callback=x');

        $this->assertJsonOk($r);
        ApiJson::assertMatchesGolden('demo', $r->body, 'A3 identique à BH-201 : ni filtre ni JSONP');
        self::assertStringStartsWith('[', $r->body, 'A3 pas d\'enveloppe JSONP');
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-219')]
    #[Scenario('demo')]
    public function bh219_no_content_negotiation_and_no_cors(): void
    {
        foreach ([['Accept' => 'application/xml'], ['Origin' => 'https://planning.example']] as $headers) {
            $r = $this->client()->request('GET', ApiJson::PATH, $headers);

            $this->assertJsonOk($r);
            ApiJson::assertMatchesGolden('demo', $r->body, 'A3 identique à BH-201 — '.key($headers));
            $this->assertNoCorsNorValidators($r);
        }
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-220')]
    #[Scenario('demo')]
    public function bh220_head_returns_json_headers_without_body(): void
    {
        $r = $this->send('HEAD', ApiJson::PATH);

        $this->assertJsonOk($r);
        self::assertSame('', $r->body);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-221')]
    #[Scenario('demo')]
    public function bh221_post_and_options_are_method_not_allowed_in_html(): void
    {
        foreach (['POST', 'OPTIONS'] as $method) {
            $r = $this->send($method, ApiJson::PATH);

            $this->assertHtmlError(405, $r);
            self::assertSame('GET, HEAD', $r->header('Allow'), 'A2 Allow — '.$r->describe());
        }
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-222')]
    #[Scenario('demo')]
    public function bh222_no_other_api_route(): void
    {
        foreach (['/api/vehicules/', '/api/vehicules.json', '/api/vehicules/1'] as $path) {
            $r = $this->send('GET', $path);

            $this->assertHtmlError(404, $r);
            self::assertNull($r->header('Location'), 'pas de redirection — '.$r->describe());
        }
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-223')]
    #[Scenario('demo')]
    public function bh223_wrong_credentials_block_the_public_api(): void
    {
        $r = $this->send('GET', ApiJson::PATH, ['gestionnaire', 'mauvais']);

        $this->assertHtmlError(401, $r);
        self::assertSame('Basic realm="Secured Area"', $r->header('WWW-Authenticate'));
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-224')]
    #[Scenario('demo')]
    public function bh224_valid_credentials_open_a_session(): void
    {
        $r = $this->send('GET', ApiJson::PATH, ['gestionnaire', 'flotte2016']);

        $this->assertJsonOk($r, cookie: true);
        ApiJson::assertMatchesGolden('demo', $r->body, 'A3 identique à BH-201');
        $this->assertDbUnchanged();
    }
}
