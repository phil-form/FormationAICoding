package be.bstorm.formation.dto.response;

/** Réponse de login / refresh. Jamais de jeton de rafraîchissement ici (cookie uniquement, RG-08). */
public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        CurrentUserResponse user
) {

    @Override
    public String toString() {
        return "TokenResponse[accessToken=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn
                + ", user=" + user + "]";
    }
}
