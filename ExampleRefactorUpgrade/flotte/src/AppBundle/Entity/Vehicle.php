<?php

namespace AppBundle\Entity;

use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Validator\Constraints as Assert;
use AppBundle\Validator\Constraints\BelgianPlate;

/**
 * @ORM\Entity(repositoryClass="AppBundle\Repository\VehicleRepository")
 * @ORM\Table(name="vehicle")
 * @ORM\HasLifecycleCallbacks()
 */
class Vehicle
{
    const STATUS_AVAILABLE = 0;
    const STATUS_MAINTENANCE = 1;
    const STATUS_OUT_OF_SERVICE = 2;

    /**
     * @ORM\Id
     * @ORM\Column(type="integer")
     * @ORM\GeneratedValue(strategy="AUTO")
     */
    private $id;

    /**
     * @ORM\Column(type="string", length=20, unique=true)
     * @Assert\NotBlank()
     * @BelgianPlate()
     */
    private $plate;

    /**
     * @ORM\Column(type="string", length=50)
     * @Assert\NotBlank()
     */
    private $brand;

    /**
     * @ORM\Column(type="string", length=50)
     * @Assert\NotBlank()
     */
    private $model;

    /**
     * @ORM\Column(type="integer")
     * @Assert\GreaterThanOrEqual(0)
     */
    private $mileage = 0;

    /**
     * @ORM\Column(type="integer")
     */
    private $status = self::STATUS_AVAILABLE;

    /**
     * @ORM\Column(type="datetime")
     */
    private $createdAt;

    /**
     * @ORM\OneToMany(targetEntity="Reservation", mappedBy="vehicle")
     * @ORM\OrderBy({"startAt" = "ASC"})
     */
    private $reservations;

    public function __construct()
    {
        $this->reservations = new ArrayCollection();
    }

    /**
     * @ORM\PrePersist
     */
    public function onPrePersist()
    {
        if (null === $this->createdAt) {
            $this->createdAt = new \DateTime();
        }
    }

    public static function getStatusLabels()
    {
        return array(
            self::STATUS_AVAILABLE => 'Disponible',
            self::STATUS_MAINTENANCE => 'En entretien',
            self::STATUS_OUT_OF_SERVICE => 'Hors service',
        );
    }

    public function getStatusLabel()
    {
        $labels = self::getStatusLabels();

        return $labels[$this->status];
    }

    public function isAvailable()
    {
        return $this->status == self::STATUS_AVAILABLE;
    }

    public function getId() { return $this->id; }
    public function getPlate() { return $this->plate; }
    public function setPlate($plate) { $this->plate = $plate; return $this; }
    public function getBrand() { return $this->brand; }
    public function setBrand($brand) { $this->brand = $brand; return $this; }
    public function getModel() { return $this->model; }
    public function setModel($model) { $this->model = $model; return $this; }
    public function getMileage() { return $this->mileage; }
    public function setMileage($mileage) { $this->mileage = $mileage; return $this; }
    public function getStatus() { return $this->status; }
    public function setStatus($status) { $this->status = $status; return $this; }
    public function getCreatedAt() { return $this->createdAt; }
    public function setCreatedAt(\DateTime $createdAt) { $this->createdAt = $createdAt; return $this; }
    public function getReservations() { return $this->reservations; }
}
