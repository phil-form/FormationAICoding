package be.bstorm.formation.framework.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Accès à l'utilisateur courant depuis le {@code SecurityContext}. */
@Component
public class CurrentUserProvider {

    /** Vide hors requête authentifiée (seed, tâche planifiée, anonyme). */
    public Optional<CurrentUser> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CurrentUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /** @throws AuthenticationCredentialsNotFoundException (→ 401) sans utilisateur authentifié */
    public CurrentUser require() {
        return current().orElseThrow(() -> new AuthenticationCredentialsNotFoundException("No authenticated user"));
    }
}
