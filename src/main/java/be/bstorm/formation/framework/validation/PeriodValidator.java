package be.bstorm.formation.framework.validation;

import be.bstorm.formation.framework.type.Period;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.Instant;

/** Valide un {@link PeriodRequest} ; les bornes absentes sont laissées à {@code @NotNull}. */
public class PeriodValidator implements ConstraintValidator<ValidPeriod, PeriodRequest> {

    @Override
    public boolean isValid(PeriodRequest value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        boolean valid = wholeHour(value.start(), "start", context) & wholeHour(value.end(), "end", context);
        if (value.start() != null && value.end() != null && !value.end().isAfter(value.start())) {
            violation(context, "end", "must be after start");
            valid = false;
        }
        return valid;
    }

    private static boolean wholeHour(Instant instant, String field, ConstraintValidatorContext context) {
        if (instant == null || Period.isWholeHour(instant)) {
            return true;
        }
        violation(context, field, "must be on a whole hour");
        return false;
    }

    private static void violation(ConstraintValidatorContext context, String field, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addPropertyNode(field).addConstraintViolation();
    }
}
