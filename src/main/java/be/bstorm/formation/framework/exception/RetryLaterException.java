package be.bstorm.formation.framework.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.Duration;

/**
 * Demande à réessayer plus tard : {@code 429} (limitation des logins, RG-05) ou {@code 503} (hachage saturé, RG-21).
 * L'en-tête {@code Retry-After} est posé par {@link GlobalExceptionHandler}.
 */
@Getter
public class RetryLaterException extends BusinessException {

    private final Duration retryAfter;

    public RetryLaterException(HttpStatus status, String title, String detail, Duration retryAfter) {
        super(status, title, detail);
        this.retryAfter = retryAfter;
    }
}
