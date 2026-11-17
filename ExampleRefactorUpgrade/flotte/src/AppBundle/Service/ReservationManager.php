<?php

namespace AppBundle\Service;

use AppBundle\Entity\Reservation;
use AppBundle\Entity\Vehicle;
use Doctrine\ORM\EntityManager;

class ReservationManager
{
    private $em;
    private $costPerKm;

    public function __construct(EntityManager $em, $costPerKm)
    {
        $this->em = $em;
        $this->costPerKm = $costPerKm;
    }

    /**
     * @throws \RuntimeException
     */
    public function create(Reservation $reservation)
    {
        $vehicle = $reservation->getVehicle();

        if (!$vehicle->isAvailable()) {
            throw new \RuntimeException('Ce véhicule n\'est pas disponible.');
        }

        if ($reservation->getEndAt() <= $reservation->getStartAt()) {
            throw new \RuntimeException('La date de fin doit être postérieure à la date de début.');
        }

        $overlapping = $this->em->getRepository('AppBundle:Reservation')
            ->findOverlapping($vehicle, $reservation->getStartAt(), $reservation->getEndAt());

        if (count($overlapping) > 0) {
            throw new \RuntimeException('Ce véhicule est déjà réservé sur cette période.');
        }

        $reservation->setStartKm($vehicle->getMileage());
        $reservation->setStatus(Reservation::STATUS_CONFIRMED);

        $this->em->persist($reservation);
        $this->em->flush();

        return $reservation;
    }

    /**
     * Cloture une reservation au retour du vehicule et renvoie le cout.
     *
     * @throws \RuntimeException
     */
    public function close(Reservation $reservation, $endKm)
    {
        if ($reservation->getStatus() != Reservation::STATUS_CONFIRMED) {
            throw new \RuntimeException('Cette réservation est déjà clôturée.');
        }

        if ($endKm < $reservation->getStartKm()) {
            throw new \RuntimeException('Kilométrage invalide.');
        }

        $km = $endKm - $reservation->getStartKm();
        $cost = round($km * $this->costPerKm, 2);

        $reservation->setEndKm($endKm);
        $reservation->setCost($cost);
        $reservation->setStatus(Reservation::STATUS_DONE);
        $reservation->getVehicle()->setMileage($endKm);

        $this->em->flush();

        return $cost;
    }

    /**
     * Rapport mensuel : une ligne par vehicule, tries par kilometres decroissants.
     *
     * @param string $month format Y-m
     */
    public function monthlyReport($month)
    {
        $from = \DateTime::createFromFormat('Y-m-d H:i:s', $month.'-01 00:00:00');
        $to = clone $from;
        $to->modify('+1 month');

        $rows = array();
        foreach ($this->em->getRepository('AppBundle:Reservation')->findDoneBetween($from, $to) as $r) {
            $plate = $r->getVehicle()->getPlate();
            if (!isset($rows[$plate])) {
                $rows[$plate] = array('plate' => $plate, 'count' => 0, 'km' => 0, 'cost' => 0);
            }
            $rows[$plate]['count']++;
            $rows[$plate]['km'] += $r->getEndKm() - $r->getStartKm();
            $rows[$plate]['cost'] += $r->getCost();
        }

        usort($rows, function ($a, $b) {
            return $a['km'] < $b['km'];
        });

        return $rows;
    }
}
