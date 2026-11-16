package be.bstorm.formation.framework.type;

/** NISS invalide. Le message décrit la règle violée, jamais la valeur fournie. */
public class InvalidNissException extends IllegalArgumentException {

    public InvalidNissException(String message) {
        super(message);
    }
}
