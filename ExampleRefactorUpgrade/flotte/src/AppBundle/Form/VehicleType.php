<?php

namespace AppBundle\Form;

use AppBundle\Entity\Vehicle;
use AppBundle\Form\DataTransformer\LegacyIntegerToLocalizedStringTransformer;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\ChoiceType;
use Symfony\Component\Form\Extension\Core\Type\IntegerType;
use Symfony\Component\Form\Extension\Core\Type\TextType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;

class VehicleType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options)
    {
        $builder
            ->add('plate', TextType::class, array('label' => 'Plaque'))
            ->add('brand', TextType::class, array('label' => 'Marque'))
            ->add('model', TextType::class, array('label' => 'Modèle'))
            ->add('mileage', IntegerType::class, array('label' => 'Kilométrage'))
            ->add('status', ChoiceType::class, array(
                'label' => 'Statut',
                // Choices as "label => value" (flipped status labels): option values stay
                // "0"/"1"/"2" in the same order, submitted values map back to integers.
                // Since Symfony 3.1 "label => value" is the only format ('choices_as_values' removed).
                'choices' => array_flip(Vehicle::getStatusLabels()),
            ))
        ;

        // Symfony 2.8 parsing kept: a decimal mileage ("1500,7") is rounded down, not rejected.
        $builder->get('mileage')
            ->resetViewTransformers()
            ->addViewTransformer(new LegacyIntegerToLocalizedStringTransformer());
    }

    public function configureOptions(OptionsResolver $resolver)
    {
        $resolver->setDefaults(array(
            'data_class' => 'AppBundle\Entity\Vehicle',
        ));
    }

    /**
     * Explicit prefix: HTML field names must stay "vehicle[...]".
     */
    public function getBlockPrefix()
    {
        return 'vehicle';
    }
}
