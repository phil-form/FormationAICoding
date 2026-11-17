<?php

namespace AppBundle\Form\DataTransformer;

use Symfony\Component\Form\Extension\Core\DataTransformer\IntegerToLocalizedStringTransformer;
use Symfony\Component\Form\Extension\Core\DataTransformer\NumberToLocalizedStringTransformer;

/**
 * Symfony 2.8 behaviour of the "integer" form type, preserved for the mileage fields.
 *
 * Since Symfony 3.x, IntegerToLocalizedStringTransformer rejects any input containing the
 * locale decimal separator ("1500,7" with locale fr). Symfony 2.8 parsed the localized number
 * and rounded it down ("1500,7" => 1500). This transformer skips that rejection and keeps
 * everything else identical (localized parsing, ROUND_DOWN, int cast, rendering).
 */
class LegacyIntegerToLocalizedStringTransformer extends IntegerToLocalizedStringTransformer
{
    /**
     * {@inheritdoc}
     */
    public function reverseTransform($value)
    {
        $result = NumberToLocalizedStringTransformer::reverseTransform($value);

        return null !== $result ? (int) $result : null;
    }
}
