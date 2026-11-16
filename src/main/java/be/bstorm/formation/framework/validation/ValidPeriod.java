package be.bstorm.formation.framework.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sur un record {@link PeriodRequest} : {@code start} et {@code end} sur des heures pleines, {@code end > start}
 * (mêmes règles que {@code framework.type.Period}). Erreurs rattachées au champ concerné ({@code errors[].field}).
 */
@Documented
@Constraint(validatedBy = PeriodValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPeriod {

    String message() default "must be a valid period";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
