<?php

declare(strict_types=1);

namespace Flotte\Tests\Diff;

use Flotte\Tests\Support\ApiJson;
use Flotte\Tests\Support\DiffTestCase;
use Flotte\Tests\Support\HttpResponse;
use Flotte\Tests\Support\PhpLog;
use Flotte\Tests\Support\Scenario;
use Flotte\Tests\Support\Target;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * Variantes comparatives app ↔ app-ref, route `api_vehicles` (fiches « sensibles à la migration », § 4.2).
 *
 * Différence avec DiffTestCase::assertSameAsReference : le corps d'une réponse réussie est comparé **octet par
 * octet** (aucune normalisation, § 4.1), et la liste des en-têtes comparés inclut ceux de CORS et de validation de
 * cache (BH-202, BH-219). Pages d'erreur (≥ 400) : statut + en-têtes, corps non contractuel (§ 4.4).
 */
#[Group('zone-api-vehicles')]
final class ApiVehiclesDiffTest extends DiffTestCase
{
    private const HEADERS = [
        'location', 'content-type', 'x-fleet-version', 'cache-control', 'allow', 'www-authenticate', 'etag',
        'last-modified', 'access-control-allow-origin', 'access-control-allow-methods',
        'access-control-allow-headers', 'access-control-allow-credentials', 'access-control-expose-headers',
    ];

    /**
     * Même requête sur app-ref puis app (clients neufs), comparaison A1, A2, A3 (octets), A5.
     *
     * @param array<string, string> $headers
     *
     * @return array{ref: HttpResponse, app: HttpResponse}
     */
    private function assertApiSameAsReference(string $method, string $path, ?array $basicAuth = null, array $headers = []): array
    {
        $responses = $logs = [];
        foreach ([Target::REF, Target::APP] as $target) {
            $client = $this->client($target);
            if (null !== $basicAuth) {
                $client->withBasicAuth($basicAuth[0], $basicAuth[1]);
            }
            $offset = PhpLog::offset($target);
            $responses[$target] = $client->request($method, $path, $headers);
            $logs[$target] = PhpLog::signaturesSince($target, $offset);
        }
        $ref = $responses[Target::REF];
        $app = $responses[Target::APP];
        $where = "$method $path".([] === $headers ? '' : ' '.json_encode($headers));

        self::assertSame($ref->status, $app->status, "A1 statut app ≠ app-ref — $where");
        self::assertSame($this->headers($ref), $this->headers($app), "A2 en-têtes app ≠ app-ref — $where");
        if ($ref->status < 300) {
            self::assertSame(\strlen($ref->body), \strlen($app->body), "A3 taille du corps app ≠ app-ref — $where");
            self::assertSame(hash('sha256', $ref->body), hash('sha256', $app->body), "A3 empreinte app ≠ app-ref — $where");
            self::assertSame($ref->body, $app->body, "A3 corps (octets) app ≠ app-ref — $where");
        }
        self::assertSame($logs[Target::REF], $logs[Target::APP], "A5 journal PHP app ≠ app-ref — $where");
        $this->lastRefLog = $logs[Target::REF];

        return ['ref' => $ref, 'app' => $app];
    }

    /** @return array<string, list<string>> */
    private function headers(HttpResponse $r): array
    {
        $out = [];
        foreach (self::HEADERS as $name) {
            $out[$name] = $r->headerValues($name);
        }
        $out['set-cookie'] = $r->cookieShapes();

        return $out;
    }

    #[Test]
    #[Group('BH-200')]
    #[Scenario('empty')]
    public function bh200_empty_array_same_as_reference(): void
    {
        $this->assertApiSameAsReference('GET', ApiJson::PATH);
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-201')]
    #[Scenario('demo')]
    public function bh201_demo_bytes_same_as_reference(): void
    {
        $this->assertApiSameAsReference('GET', ApiJson::PATH);
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-202')]
    #[Scenario('demo')]
    public function bh202_headers_same_as_reference(): void
    {
        ['ref' => $ref] = $this->assertApiSameAsReference('GET', ApiJson::PATH);
        self::assertSame(ApiJson::CONTENT_TYPE, $ref->header('Content-Type'), 'contrôle : référence sans charset');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-203')]
    #[Scenario('demo')]
    public function bh203_body_bytes_same_as_reference(): void
    {
        ['app' => $app] = $this->assertApiSameAsReference('GET', ApiJson::PATH);
        self::assertStringEndsWith('"}]', $app->body, 'pas de retour à la ligne final');
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-204')]
    #[Scenario('demo')]
    public function bh204_json_types_same_as_reference(): void
    {
        ['app' => $app] = $this->assertApiSameAsReference('GET', ApiJson::PATH);
        ApiJson::assertStructure(ApiJson::decode($app->body));
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-205')]
    #[Scenario('edge')]
    public function bh205_raw_plate_order_same_as_reference(): void
    {
        $this->assertApiSameAsReference('GET', ApiJson::PATH);
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-206')]
    #[Scenario('edge')]
    public function bh206_escaping_same_as_reference(): void
    {
        $this->assertApiSameAsReference('GET', ApiJson::PATH);
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-207')]
    #[Scenario('edge')]
    public function bh207_large_numbers_same_as_reference(): void
    {
        $this->assertApiSameAsReference('GET', ApiJson::PATH);
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-210')]
    #[Scenario('edge')]
    public function bh210_same_start_order_same_as_reference(): void
    {
        $this->assertApiSameAsReference('GET', ApiJson::PATH);
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-213')]
    #[Scenario('edge')]
    public function bh213_dst_time_same_as_reference(): void
    {
        $this->assertApiSameAsReference('GET', ApiJson::PATH);
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-215')]
    #[Scenario('anomalies')]
    public function bh215_anomalies_and_php_log_same_as_reference(): void
    {
        $this->assertApiSameAsReference('GET', ApiJson::PATH);
        // Contrôle du mécanisme A5 : la référence journalise bien les notices de la fiche.
        self::assertSame(
            ['PHP Notice: Undefined offset: 3', 'PHP Notice: Undefined offset: -1'],
            $this->lastRefLog,
            'A5 : signatures attendues dans le journal de app-ref',
        );
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-216')]
    #[Scenario('nominal')]
    public function bh216_nominal_bytes_same_as_reference(): void
    {
        $this->assertApiSameAsReference('GET', ApiJson::PATH);
        $this->assertBothDbUnchanged();
    }

    /**
     * BH-217 : mêmes octets sur 500 véhicules, et temps de réponse app ≤ 2 × app-ref (médiane de 5 mesures
     * alternées après la comparaison, qui sert d'échauffement ; seuil : proposition § 4.4).
     */
    #[Test]
    #[Group('BH-217')]
    #[Scenario('volume')]
    public function bh217_volume_bytes_and_performance_vs_reference(): void
    {
        $this->assertApiSameAsReference('GET', ApiJson::PATH);
        $times = [Target::REF => [], Target::APP => []];
        for ($i = 0; $i < 5; ++$i) {
            foreach ([Target::REF, Target::APP] as $target) {
                $times[$target][] = $this->send('GET', ApiJson::PATH, null, $target)->durationMs;
            }
        }
        $median = static function (array $v): float {
            sort($v);

            return $v[intdiv(\count($v), 2)];
        };
        $ref = $median($times[Target::REF]);
        $app = $median($times[Target::APP]);
        self::assertLessThanOrEqual(2 * $ref, $app, \sprintf('BH-217 performance : app %.1f ms > 2 × app-ref %.1f ms', $app, $ref));
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-219')]
    #[Scenario('demo')]
    public function bh219_accept_and_origin_same_as_reference(): void
    {
        $this->assertApiSameAsReference('GET', ApiJson::PATH, null, ['Accept' => 'application/xml']);
        $this->assertApiSameAsReference('GET', ApiJson::PATH, null, ['Origin' => 'https://planning.example']);
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-221')]
    #[Scenario('demo')]
    public function bh221_method_not_allowed_same_as_reference(): void
    {
        $this->assertApiSameAsReference('POST', ApiJson::PATH);
        $this->assertApiSameAsReference('OPTIONS', ApiJson::PATH);
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-222')]
    #[Scenario('demo')]
    public function bh222_unknown_api_paths_same_as_reference(): void
    {
        foreach (['/api/vehicules/', '/api/vehicules.json', '/api/vehicules/1'] as $path) {
            $this->assertApiSameAsReference('GET', $path);
        }
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-223')]
    #[Scenario('demo')]
    public function bh223_wrong_credentials_same_as_reference(): void
    {
        $this->assertApiSameAsReference('GET', ApiJson::PATH, ['gestionnaire', 'mauvais']);
        $this->assertBothDbUnchanged();
    }

    #[Test]
    #[Group('BH-224')]
    #[Scenario('demo')]
    public function bh224_authenticated_session_cookie_same_as_reference(): void
    {
        $this->assertApiSameAsReference('GET', ApiJson::PATH, ['gestionnaire', 'flotte2016']);
        $this->assertBothDbUnchanged();
    }
}
