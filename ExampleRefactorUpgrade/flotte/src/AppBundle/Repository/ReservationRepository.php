<?php

namespace AppBundle\Repository;

use AppBundle\Entity\Reservation;
use AppBundle\Entity\Vehicle;
use Doctrine\ORM\EntityRepository;

class ReservationRepository extends EntityRepository
{
    /**
     * Reservations confirmees qui chevauchent la periode [start, end].
     */
    public function findOverlapping(Vehicle $vehicle, \DateTime $start, \DateTime $end)
    {
        return $this->createQueryBuilder('r')
            ->where('r.vehicle = :vehicle')
            ->andWhere('r.status = :status')
            ->andWhere('r.startAt <= :end')
            ->andWhere('r.endAt >= :start')
            ->setParameter('vehicle', $vehicle)
            ->setParameter('status', Reservation::STATUS_CONFIRMED)
            ->setParameter('start', $start)
            ->setParameter('end', $end)
            ->getQuery()
            ->getResult();
    }

    public function findDoneBetween(\DateTime $from, \DateTime $to)
    {
        return $this->createQueryBuilder('r')
            ->join('r.vehicle', 'v')
            ->where('r.status = :status')
            ->andWhere('r.endAt >= :from')
            ->andWhere('r.endAt < :to')
            ->setParameter('status', Reservation::STATUS_DONE)
            ->setParameter('from', $from)
            ->setParameter('to', $to)
            ->orderBy('r.id', 'ASC')
            ->getQuery()
            ->getResult();
    }
}
