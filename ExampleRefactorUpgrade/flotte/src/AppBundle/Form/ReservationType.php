<?php

namespace AppBundle\Form;

use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;

class ReservationType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options)
    {
        $builder
            ->add('driverName', 'text', array('label' => 'Conducteur'))
            ->add('startAt', 'datetime', array(
                'label' => 'Début',
                'widget' => 'single_text',
                'format' => 'dd/MM/yyyy HH:mm',
            ))
            ->add('endAt', 'datetime', array(
                'label' => 'Fin',
                'widget' => 'single_text',
                'format' => 'dd/MM/yyyy HH:mm',
            ))
        ;
    }

    public function configureOptions(OptionsResolver $resolver)
    {
        $resolver->setDefaults(array(
            'data_class' => 'AppBundle\Entity\Reservation',
        ));
    }

    public function getName()
    {
        return 'reservation';
    }
}
