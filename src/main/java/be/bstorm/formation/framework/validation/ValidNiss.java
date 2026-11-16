package be.bstorm.formation.framework.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Champ texte contenant un NISS valide (séparateurs tolérés : {@code 85.07.30-033.28}). {@code null} est accepté :
 * combiner avec {@code @NotBlank} si le champ est obligatoire.
 */
@Documented
@Constraint(validatedBy = NissValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidNiss {

    String message() default "NISS invalide";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
