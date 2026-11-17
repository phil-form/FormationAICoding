<?php

namespace AppBundle\Validator\Constraints;

use Symfony\Component\Validator\Constraint;

/**
 * @Annotation
 */
class BelgianPlate extends Constraint
{
    public $message = 'La plaque "%plate%" n\'est pas une plaque belge valide (format 1-ABC-123).';

    public function validatedBy()
    {
        return 'belgian_plate';
    }
}
