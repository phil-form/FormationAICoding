package be.bstorm.formation.framework.validation;

import be.bstorm.formation.framework.type.Niss;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Validation Jakarta de {@link ValidNiss} (le message d'erreur ne contient jamais la valeur). */
public class NissValidator implements ConstraintValidator<ValidNiss, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || Niss.isValid(value);
    }
}
