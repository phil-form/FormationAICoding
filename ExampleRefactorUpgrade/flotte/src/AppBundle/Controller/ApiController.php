<?php

namespace AppBundle\Controller;

use Sensio\Bundle\FrameworkExtraBundle\Configuration\Method;
use Sensio\Bundle\FrameworkExtraBundle\Configuration\Route;
use Symfony\Bundle\FrameworkBundle\Controller\Controller;
use Symfony\Component\HttpFoundation\JsonResponse;

/**
 * API utilisee par l'ecran de planning des depots.
 */
class ApiController extends Controller
{
    /**
     * @Route("/api/vehicules", name="api_vehicles")
     * @Method("GET")
     */
    public function vehiclesAction()
    {
        $vehicles = $this->getDoctrine()->getRepository('AppBundle:Vehicle')->findAllOrderedByPlate();

        $data = array();
        foreach ($vehicles as $vehicle) {
            $next = '';
            foreach ($vehicle->getReservations() as $reservation) {
                if ($reservation->getStatus() == 'confirmee') {
                    $next = $reservation->getStartAt()->format('d/m/Y H:i');
                    break;
                }
            }

            $data[] = array(
                'id' => $vehicle->getId(),
                'plate' => $vehicle->getPlate(),
                'label' => $vehicle->getBrand().' '.$vehicle->getModel(),
                'mileage' => $vehicle->getMileage(),
                'status' => $vehicle->getStatusLabel(),
                'nextReservation' => $next,
            );
        }

        return new JsonResponse($data);
    }
}
