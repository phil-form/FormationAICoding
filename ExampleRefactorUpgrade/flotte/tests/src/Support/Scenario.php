<?php

declare(strict_types=1);

namespace Flotte\Tests\Support;

/** Scénario de données restauré avant le test (méthode, ou classe par défaut). */
#[\Attribute(\Attribute::TARGET_METHOD | \Attribute::TARGET_CLASS)]
final class Scenario
{
    public function __construct(public readonly string $name)
    {
    }
}
