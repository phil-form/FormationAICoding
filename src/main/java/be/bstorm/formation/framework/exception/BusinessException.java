package be.bstorm.formation.framework.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Erreur métier attendue, traduite en {@code ProblemDetail} par {@link GlobalExceptionHandler}.
 * Le message est renvoyé au client : il ne doit contenir aucune donnée sensible ni détail technique.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    public BusinessException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public BusinessException(String title, String detail) {
        this(HttpStatus.UNPROCESSABLE_CONTENT, title, detail);
    }
}
