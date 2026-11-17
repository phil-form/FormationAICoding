<?php

namespace AppBundle\Repository;

use Doctrine\ORM\EntityRepository;

class VehicleRepository extends EntityRepository
{
    public function findAllOrderedByPlate()
    {
        return $this->findBy(array(), array('plate' => 'ASC'));
    }
}
