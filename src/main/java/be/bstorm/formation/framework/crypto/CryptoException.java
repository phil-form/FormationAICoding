package be.bstorm.formation.framework.crypto;

/**
 * Échec d'une opération cryptographique. Le message ne contient ni valeur en clair, ni chiffré, ni contexte :
 * il peut apparaître dans un log. Traduit en 500 générique (avec {@code errorId}) par le gestionnaire d'erreurs.
 */
public class CryptoException extends RuntimeException {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
