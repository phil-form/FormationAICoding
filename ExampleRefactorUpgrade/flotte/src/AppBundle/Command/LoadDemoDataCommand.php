<?php

namespace AppBundle\Command;

use AppBundle\Entity\Reservation;
use AppBundle\Entity\Vehicle;
use Symfony\Bundle\FrameworkBundle\Command\ContainerAwareCommand;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Output\OutputInterface;

class LoadDemoDataCommand extends ContainerAwareCommand
{
    protected function configure()
    {
        $this
            ->setName('fleet:demo:load')
            ->setDescription('Vide la base et charge les donnees de demonstration');
    }

    protected function execute(InputInterface $input, OutputInterface $output)
    {
        $em = $this->getContainer()->get('doctrine')->getManager();

        $em->createQuery('DELETE FROM AppBundle:Reservation')->execute();
        $em->createQuery('DELETE FROM AppBundle:Vehicle')->execute();

        $vehicles = array();
        foreach (array(
            array('1-ABC-123', 'Renault', 'Kangoo', 84210, Vehicle::STATUS_AVAILABLE),
            array('1-BCD-234', 'Peugeot', 'Partner', 120450, Vehicle::STATUS_AVAILABLE),
            array('1-CDE-345', 'Volkswagen', 'Crafter', 45800, Vehicle::STATUS_MAINTENANCE),
            array('1-DEF-456', 'Iveco', 'Daily', 210300, Vehicle::STATUS_OUT_OF_SERVICE),
            array('2-EFG-567', 'Skoda', 'Octavia', 15200, Vehicle::STATUS_AVAILABLE),
        ) as $data) {
            $v = new Vehicle();
            $v->setPlate($data[0])->setBrand($data[1])->setModel($data[2])->setMileage($data[3])->setStatus($data[4]);
            $v->setCreatedAt(new \DateTime('2016-01-04 08:00:00'));
            $em->persist($v);
            $vehicles[$data[0]] = $v;
        }

        // Reservations terminees en mai 2016 (rapport mensuel)
        foreach (array(
            array('1-ABC-123', 'Jean Dupont', '2016-05-02 08:00', '2016-05-02 17:00', 84000, 84210),
            array('1-BCD-234', 'Marie Lambert', '2016-05-03 08:00', '2016-05-04 18:00', 120000, 120210),
            array('2-EFG-567', 'Ahmed Benali', '2016-05-10 09:00', '2016-05-10 12:30', 15043, 15200),
            array('1-BCD-234', 'Jean Dupont', '2016-05-20 07:30', '2016-05-20 19:00', 120210, 120450),
        ) as $data) {
            $r = new Reservation();
            $r->setVehicle($vehicles[$data[0]])->setDriverName($data[1])
              ->setStartAt(new \DateTime($data[2]))->setEndAt(new \DateTime($data[3]))
              ->setStartKm($data[4])->setEndKm($data[5])
              ->setCost(round(($data[5] - $data[4]) * 0.35, 2))
              ->setStatus(Reservation::STATUS_DONE);
            $em->persist($r);
        }

        // Reservation a venir
        $r = new Reservation();
        $r->setVehicle($vehicles['1-ABC-123'])->setDriverName('Sophie Martin')
          ->setStartAt(new \DateTime('2030-06-15 08:00'))->setEndAt(new \DateTime('2030-06-15 18:00'))
          ->setStartKm(84210);
        $em->persist($r);

        $em->flush();

        $output->writeln('<info>Données de démonstration chargées : 5 véhicules, 5 réservations.</info>');

        return 0;
    }
}
