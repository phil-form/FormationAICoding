<?php

namespace AppBundle\Form;

use AppBundle\Entity\Vehicle;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;

class VehicleType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options)
    {
        $builder
            ->add('plate', 'text', array('label' => 'Plaque'))
            ->add('brand', 'text', array('label' => 'Marque'))
            ->add('model', 'text', array('label' => 'Modèle'))
            ->add('mileage', 'integer', array('label' => 'Kilométrage'))
            ->add('status', 'choice', array(
                'label' => 'Statut',
                'choices' => Vehicle::getStatusLabels(),
            ))
        ;
    }

    public function configureOptions(OptionsResolver $resolver)
    {
        $resolver->setDefaults(array(
            'data_class' => 'AppBundle\Entity\Vehicle',
        ));
    }

    public function getName()
    {
        return 'vehicle';
    }
}
