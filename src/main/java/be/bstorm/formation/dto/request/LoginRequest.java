package be.bstorm.formation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Identifiants de connexion (RG-01, RG-02). Aucun {@code @Pattern} sur {@code username} : un identifiant hors format
 * suit le chemin « identifiant inconnu » (401), ce qui ne révèle pas le format.
 */
public record LoginRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(max = 128) String password
) {

    @Override
    public String toString() {
        return "LoginRequest[username=***, password=***]";
    }
}
