package be.bstorm.formation.framework.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NissValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    record Request(@ValidNiss String niss) {}

    @Test
    void acceptsValidNissWithOrWithoutSeparators() {
        assertThat(validator.validate(new Request("85073003328"))).isEmpty();
        assertThat(validator.validate(new Request("85.07.30-033.28"))).isEmpty();
    }

    @Test
    void acceptsNullSoThatNotBlankDecidesIfRequired() {
        assertThat(validator.validate(new Request(null))).isEmpty();
    }

    @Test
    void rejectsInvalidNissWithoutEchoingIt() {
        Set<ConstraintViolation<Request>> violations = validator.validate(new Request("85073003329"));

        assertThat(violations).singleElement().satisfies(v -> {
            assertThat(v.getMessage()).isEqualTo("NISS invalide");
            assertThat(v.getPropertyPath()).hasToString("niss");
        });
    }
}
