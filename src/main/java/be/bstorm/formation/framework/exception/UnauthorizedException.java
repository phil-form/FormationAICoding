package be.bstorm.formation.framework.exception;

import org.springframework.http.HttpStatus;

/** 401 métier (ex. login refusé, RG-03) au format {@code ProblemDetail}, {@code title} = {@code Unauthorized}. */
public class UnauthorizedException extends BusinessException {

    public UnauthorizedException(String detail) {
        super(HttpStatus.UNAUTHORIZED, "Unauthorized", detail);
    }
}
