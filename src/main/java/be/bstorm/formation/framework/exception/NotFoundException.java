package be.bstorm.formation.framework.exception;

import org.springframework.http.HttpStatus;

/** Ressource absente — ou inaccessible à l'utilisateur courant, pour ne pas révéler son existence. */
public class NotFoundException extends BusinessException {

    public NotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, "Resource Not Found", detail);
    }
}
