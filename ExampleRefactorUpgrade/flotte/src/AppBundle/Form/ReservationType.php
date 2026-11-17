<?php

namespace AppBundle\Form;

use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\DateTimeType;
use Symfony\Component\Form\Extension\Core\Type\TextType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;

class ReservationType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options)
    {
        $builder
            ->add('driverName', TextType::class, array('label' => 'Conducteur'))
            ->add('startAt', DateTimeType::class, array(
                'label' => 'Début',
                'widget' => 'single_text',
                'format' => 'dd/MM/yyyy HH:mm',
            ))
            ->add('endAt', DateTimeType::class, array(
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

    /**
     * Explicit prefix: HTML field names must stay "reservation[...]".
     */
    public function getBlockPrefix()
    {
        return 'reservation';
    }
}
