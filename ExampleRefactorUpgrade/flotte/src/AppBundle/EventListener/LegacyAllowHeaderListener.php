<?php

namespace AppBundle\EventListener;

use Symfony\Component\HttpKernel\Event\GetResponseForExceptionEvent;
use Symfony\Component\HttpKernel\Exception\MethodNotAllowedHttpException;
use Symfony\Component\Routing\Exception\MethodNotAllowedException;

/**
 * Symfony 2.8 behaviour of the "Allow" header on 405 responses, preserved.
 *
 * The Symfony 2.8 compiled URL matcher appended "HEAD" after the declared methods of any route
 * accepting "GET" ("GET, HEAD", "GET, POST, HEAD"); Symfony 3.4 only lists the declared methods.
 * Routes are left untouched: only the 405 error (header and message) is rebuilt as in 2.8.
 */
class LegacyAllowHeaderListener
{
    public function onKernelException(GetResponseForExceptionEvent $event)
    {
        $exception = $event->getException();
        if (!$exception instanceof MethodNotAllowedHttpException) {
            return;
        }

        $previous = $exception->getPrevious();
        if (!$previous instanceof MethodNotAllowedException) {
            return;
        }

        $allow = $previous->getAllowedMethods();
        if (!in_array('GET', $allow, true) || in_array('HEAD', $allow, true)) {
            return;
        }

        $legacyAllow = $allow;
        $legacyAllow[] = 'HEAD';

        $message = str_replace(
            sprintf('(Allow: %s)', implode(', ', $allow)),
            sprintf('(Allow: %s)', implode(', ', $legacyAllow)),
            $exception->getMessage()
        );

        $event->setException(new MethodNotAllowedHttpException($legacyAllow, $message, $previous, $exception->getCode()));
    }
}
