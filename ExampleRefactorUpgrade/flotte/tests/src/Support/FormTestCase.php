<?php

declare(strict_types=1);

namespace Flotte\Tests\Support;

/**
 * Base des tests de routes à formulaire (ReservationController…) : gabarit commun + parcours § 3.1.
 * L'instantané initial de la base est pris juste après la restauration du scénario.
 */
abstract class FormTestCase extends CaseTestCase
{
    use FormJourney;

    protected function setUp(): void
    {
        parent::setUp();
        if (null !== $this->scenario()) {
            foreach ($this->targets() as $target) {
                $this->initialSnapshot($target);
            }
        }
    }
}
