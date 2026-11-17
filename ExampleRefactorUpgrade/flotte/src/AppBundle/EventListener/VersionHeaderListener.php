<?php

namespace AppBundle\EventListener;

use Symfony\Component\HttpKernel\Event\FilterResponseEvent;

class VersionHeaderListener
{
    private $version;

    public function __construct($version)
    {
        $this->version = $version;
    }

    public function onKernelResponse(FilterResponseEvent $event)
    {
        if (!$event->isMasterRequest()) {
            return;
        }

        $event->getResponse()->headers->set('X-Fleet-Version', $this->version);
    }
}
