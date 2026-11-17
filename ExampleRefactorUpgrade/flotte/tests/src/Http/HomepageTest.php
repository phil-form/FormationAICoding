<?php

declare(strict_types=1);

namespace Flotte\Tests\Http;

use Flotte\Tests\Support\CaseTestCase;
use Flotte\Tests\Support\Scenario;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/**
 * VehicleController, route `homepage` — `/` (toutes méthodes) : redirection vers la liste.
 * Fiches : tmp/tests-vehicle-controller.md § 3.1. Corps des 302 non contractuel (§ 2.4).
 */
#[Group('zone-homepage')]
#[Group('vehicle-controller')]
final class HomepageTest extends CaseTestCase
{
    private function assertRedirectToList(\Flotte\Tests\Support\HttpResponse $r): void
    {
        $this->assertStatus(302, $r);
        self::assertSame('/vehicules', $r->header('Location'), 'A2 Location relatif — '.$r->describe());
        $this->assertCommonHeaders($r);
        $this->assertNoCookie($r);
    }

    #[Test]
    #[Group('BH-001')]
    #[Scenario('demo')]
    public function bh001_root_redirects_to_vehicle_list(): void
    {
        $r = $this->send('GET', '/');

        $this->assertRedirectToList($r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-002')]
    #[Scenario('demo')]
    public function bh002_root_redirects_whatever_the_method(): void
    {
        foreach (['POST', 'PUT', 'DELETE'] as $method) {
            $this->assertRedirectToList($this->send($method, '/'));
        }
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-003')]
    #[Scenario('demo')]
    public function bh003_root_redirect_drops_query_string(): void
    {
        $r = $this->send('GET', '/?status=1');

        $this->assertRedirectToList($r);
        $this->assertDbUnchanged();
    }

    #[Test]
    #[Group('BH-004')]
    #[Scenario('demo')]
    public function bh004_head_on_root_redirects_with_empty_body(): void
    {
        $r = $this->send('HEAD', '/');

        $this->assertRedirectToList($r);
        self::assertSame('', $r->body, 'A3 corps vide (HEAD)');
        $this->assertDbUnchanged();
    }
}
