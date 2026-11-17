<?php

namespace AppBundle\Validator\Constraints;

use AppBundle\Service\PlateFormatter;
use Symfony\Component\Validator\Constraint;
use Symfony\Component\Validator\ConstraintValidator;

class BelgianPlateValidator extends ConstraintValidator
{
    private $formatter;

    public function __construct(PlateFormatter $formatter)
    {
        $this->formatter = $formatter;
    }

    public function validate($value, Constraint $constraint)
    {
        if (null === $value || '' === $value) {
            return;
        }

        if (!$this->formatter->isValid($value)) {
            $this->context->addViolation($constraint->message, array('%plate%' => $value));
        }
    }
}
