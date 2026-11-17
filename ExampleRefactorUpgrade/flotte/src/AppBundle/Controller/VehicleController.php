<?php

namespace AppBundle\Controller;

use AppBundle\Entity\Vehicle;
use Sensio\Bundle\FrameworkExtraBundle\Configuration\Method;
use Sensio\Bundle\FrameworkExtraBundle\Configuration\Route;
use Sensio\Bundle\FrameworkExtraBundle\Configuration\Template;
use Symfony\Bundle\FrameworkBundle\Controller\Controller;
use Symfony\Component\HttpFoundation\Request;

class VehicleController extends Controller
{
    /**
     * @Route("/", name="homepage")
     */
    public function homeAction()
    {
        return $this->redirect($this->generateUrl('vehicle_index'));
    }

    /**
     * @Route("/vehicules", name="vehicle_index")
     * @Method("GET")
     * @Template()
     */
    public function indexAction(Request $request)
    {
        $status = $request->query->get('status');

        $vehicles = $this->getDoctrine()
            ->getRepository('AppBundle:Vehicle')
            ->findAllOrderedByPlate();

        if ($status !== null && $status !== '') {
            foreach ($vehicles as $i => $vehicle) {
                if ($vehicle->getStatus() != $status) {
                    unset($vehicles[$i]);
                }
            }
        }

        return array(
            'vehicles' => $vehicles,
            'status' => $status,
            'statusLabels' => Vehicle::getStatusLabels(),
        );
    }

    /**
     * @Route("/vehicules/{id}", name="vehicle_show", requirements={"id" = "\d+"})
     * @Method("GET")
     * @Template()
     */
    public function showAction(Vehicle $vehicle)
    {
        return array('vehicle' => $vehicle);
    }
}
