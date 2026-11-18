package be.bstorm.formation.framework.exception;

/**
 * Refresh refusé (RG-11, RG-22) : 401, même réponse dans tous les cas ; le gestionnaire dédié de
 * {@link GlobalExceptionHandler} efface le cookie de rafraîchissement.
 */
public class InvalidSessionException extends UnauthorizedException {

    public static final String DETAIL = "Invalid or expired session.";

    public InvalidSessionException() {
        super(DETAIL);
    }
}
