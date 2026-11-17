<?php

namespace AppBundle\Controller;

use AppBundle\Entity\Reservation;
use AppBundle\Entity\Vehicle;
use AppBundle\Form\DataTransformer\LegacyIntegerToLocalizedStringTransformer;
use AppBundle\Form\ReservationType;
use Sensio\Bundle\FrameworkExtraBundle\Configuration\Method;
use Sensio\Bundle\FrameworkExtraBundle\Configuration\Route;
use Symfony\Bundle\FrameworkBundle\Controller\Controller;
use Symfony\Component\Form\Extension\Core\Type\IntegerType;
use Symfony\Component\Form\FormError;
use Symfony\Component\HttpFoundation\Request;

class ReservationController extends Controller
{
    /**
     * @Route("/vehicules/{id}/reserver", name="reservation_new", requirements={"id" = "\d+"})
     * @Method({"GET", "POST"})
     */
    public function newAction(Request $request, Vehicle $vehicle)
    {
        $reservation = new Reservation();
        $reservation->setVehicle($vehicle);

        $form = $this->createForm(ReservationType::class, $reservation);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            try {
                $this->get('app.reservation_manager')->create($reservation);
                $this->get('session')->getFlashBag()->add('success', 'Réservation enregistrée.');

                return $this->redirect($this->generateUrl('vehicle_show', array('id' => $vehicle->getId())));
            } catch (\RuntimeException $e) {
                $form->addError(new FormError($e->getMessage()));
            }
        }

        return $this->render('reservation/new.html.twig', array(
            'vehicle' => $vehicle,
            'form' => $form->createView(),
        ));
    }

    /**
     * @Route("/reservations/{id}/retour", name="reservation_return", requirements={"id" = "\d+"})
     * @Method({"GET", "POST"})
     */
    public function returnAction(Request $request, Reservation $reservation)
    {
        $builder = $this->createFormBuilder()
            ->add('endKm', IntegerType::class, array('label' => 'Kilométrage au retour'));
        // Symfony 2.8 parsing kept: a decimal mileage ("10100,6") is rounded down, not rejected.
        $builder->get('endKm')
            ->resetViewTransformers()
            ->addViewTransformer(new LegacyIntegerToLocalizedStringTransformer());
        $form = $builder->getForm();

        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            try {
                $cost = $this->get('app.reservation_manager')->close($reservation, $form->get('endKm')->getData());
                $this->get('session')->getFlashBag()->add(
                    'success',
                    sprintf('Retour enregistré. Coût : %s €', number_format($cost, 2, ',', ' '))
                );

                return $this->redirect($this->generateUrl('vehicle_show', array('id' => $reservation->getVehicle()->getId())));
            } catch (\RuntimeException $e) {
                $form->addError(new FormError($e->getMessage()));
            }
        }

        return $this->render('reservation/return.html.twig', array(
            'reservation' => $reservation,
            'form' => $form->createView(),
        ));
    }
}
