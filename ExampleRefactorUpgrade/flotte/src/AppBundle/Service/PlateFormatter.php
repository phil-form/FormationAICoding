<?php

namespace AppBundle\Service;

/**
 * Plaques belges : 1-ABC-123.
 */
class PlateFormatter
{
    /**
     * " 1abc 123 " -> "1-ABC-123". Renvoie la saisie nettoyee (majuscules,
     * sans espaces ni tirets) si elle ne ressemble pas a une plaque belge.
     */
    public function normalize($plate)
    {
        $clean = strtoupper(preg_replace('/[\s\-\.]/', '', (string) $plate));

        if (preg_match('/^(\d)([A-Z]{3})(\d{3})$/', $clean, $m)) {
            return $m[1].'-'.$m[2].'-'.$m[3];
        }

        return $clean;
    }

    public function isValid($plate)
    {
        return (bool) preg_match('/^\d-[A-Z]{3}-\d{3}$/', $this->normalize($plate));
    }
}
