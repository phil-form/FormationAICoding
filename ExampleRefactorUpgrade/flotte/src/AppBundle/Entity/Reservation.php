<?php

namespace AppBundle\Entity;

use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Validator\Constraints as Assert;

/**
 * @ORM\Entity(repositoryClass="AppBundle\Repository\ReservationRepository")
 * @ORM\Table(name="reservation")
 */
class Reservation
{
    const STATUS_CONFIRMED = 'confirmee';
    const STATUS_DONE = 'terminee';
    const STATUS_CANCELLED = 'annulee';

    /**
     * @ORM\Id
     * @ORM\Column(type="integer")
     * @ORM\GeneratedValue(strategy="AUTO")
     */
    private $id;

    /**
     * @ORM\ManyToOne(targetEntity="Vehicle", inversedBy="reservations")
     * @ORM\JoinColumn(nullable=false)
     */
    private $vehicle;

    /**
     * @ORM\Column(type="string", length=100)
     * @Assert\NotBlank(message="Le nom du conducteur est obligatoire.")
     */
    private $driverName;

    /**
     * @ORM\Column(type="datetime")
     * @Assert\NotBlank()
     */
    private $startAt;

    /**
     * @ORM\Column(type="datetime")
     * @Assert\NotBlank()
     */
    private $endAt;

    /**
     * @ORM\Column(type="string", length=20)
     */
    private $status = self::STATUS_CONFIRMED;

    /**
     * @ORM\Column(type="integer")
     */
    private $startKm = 0;

    /**
     * @ORM\Column(type="integer", nullable=true)
     */
    private $endKm;

    /**
     * @ORM\Column(type="decimal", precision=10, scale=2, nullable=true)
     */
    private $cost;

    public function getId() { return $this->id; }
    public function getVehicle() { return $this->vehicle; }
    public function setVehicle(Vehicle $vehicle) { $this->vehicle = $vehicle; return $this; }
    public function getDriverName() { return $this->driverName; }
    public function setDriverName($driverName) { $this->driverName = $driverName; return $this; }
    public function getStartAt() { return $this->startAt; }
    public function setStartAt($startAt) { $this->startAt = $startAt; return $this; }
    public function getEndAt() { return $this->endAt; }
    public function setEndAt($endAt) { $this->endAt = $endAt; return $this; }
    public function getStatus() { return $this->status; }
    public function setStatus($status) { $this->status = $status; return $this; }
    public function getStartKm() { return $this->startKm; }
    public function setStartKm($startKm) { $this->startKm = $startKm; return $this; }
    public function getEndKm() { return $this->endKm; }
    public function setEndKm($endKm) { $this->endKm = $endKm; return $this; }
    public function getCost() { return $this->cost; }
    public function setCost($cost) { $this->cost = $cost; return $this; }
}
