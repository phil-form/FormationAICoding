package be.bstorm.formation.framework.exception;

import org.springframework.http.HttpStatus;

/** Conflit avec l'état actuel de la ressource (doublon, transition d'état interdite…). */
public class ConflictException extends BusinessException {

    public ConflictException(String title, String detail) {
        super(HttpStatus.CONFLICT, title, detail);
    }
}
