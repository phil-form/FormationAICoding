package be.bstorm.formation.framework.type;

import be.bstorm.formation.framework.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** Période incohérente (bornes absentes, fin avant début, heures non pleines) → 400. */
public class InvalidPeriodException extends BusinessException {

    public InvalidPeriodException(String detail) {
        super(HttpStatus.BAD_REQUEST, "Invalid Period", detail);
    }
}
