<?php

declare(strict_types=1);

namespace Flotte\Tests\Diff;

use Flotte\Tests\Support\DiffTestCase;
use Flotte\Tests\Support\Scenario;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\Test;

/** Variantes comparatives app ↔ app-ref, route `homepage` (fiches sensibles à la migration). */
#[Group('zone-homepage')]
final class HomepageDiffTest extends DiffTestCase
{
    #[Test]
    #[Group('BH-002')]
    #[Scenario('demo')]
    public function bh002_root_redirect_for_any_method_same_as_reference(): void
    {
        foreach (['GET', 'POST', 'PUT', 'DELETE'] as $method) {
            $this->assertSameAsReference($method, '/');
        }
        $this->assertBothDbUnchanged();
    }
}
