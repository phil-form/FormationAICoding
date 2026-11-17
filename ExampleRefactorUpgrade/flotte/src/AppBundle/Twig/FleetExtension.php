<?php

namespace AppBundle\Twig;

use AppBundle\Service\PlateFormatter;

class FleetExtension extends \Twig_Extension
{
    private $plateFormatter;

    public function __construct(PlateFormatter $plateFormatter)
    {
        $this->plateFormatter = $plateFormatter;
    }

    public function getFilters()
    {
        return array(
            new \Twig_SimpleFilter('plate', array($this->plateFormatter, 'normalize')),
            new \Twig_SimpleFilter('km', array($this, 'formatKm')),
            new \Twig_SimpleFilter('euros', array($this, 'formatEuros')),
        );
    }

    public function formatKm($km)
    {
        return number_format($km, 0, ',', '.').' km';
    }

    public function formatEuros($amount)
    {
        return number_format($amount, 2, ',', ' ').' €';
    }

    public function getName()
    {
        return 'fleet';
    }
}
