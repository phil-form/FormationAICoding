<?php

namespace AppBundle\Controller;

use AppBundle\Entity\Vehicle;
use AppBundle\Form\VehicleType;
use Sensio\Bundle\FrameworkExtraBundle\Configuration\Method;
use Sensio\Bundle\FrameworkExtraBundle\Configuration\Route;
use Symfony\Bundle\FrameworkBundle\Controller\Controller;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Security\Core\Exception\AccessDeniedException;

class AdminController extends Controller
{
    /**
     * @Route("/admin/vehicules/nouveau", name="admin_vehicle_new")
     * @Method({"GET", "POST"})
     */
    public function newVehicleAction(Request $request)
    {
        if (!$this->get('security.context')->isGranted('ROLE_ADMIN')) {
            throw new AccessDeniedException();
        }

        $vehicle = new Vehicle();
        $form = $this->createForm(new VehicleType(), $vehicle);
        $form->handleRequest($request);

        if ($form->isValid()) {
            $vehicle->setPlate($this->get('app.plate_formatter')->normalize($vehicle->getPlate()));

            $em = $this->getDoctrine()->getManager();
            $em->persist($vehicle);
            $em->flush();

            $this->get('session')->getFlashBag()->add('success', sprintf(
                'Véhicule %s ajouté par %s.', $vehicle->getPlate(), $this->getUser()->getUsername()
            ));

            return $this->redirect($this->generateUrl('vehicle_show', array('id' => $vehicle->getId())));
        }

        return $this->render('admin/vehicle_new.html.twig', array('form' => $form->createView()));
    }
}
