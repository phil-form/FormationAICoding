package be.bstorm.formation.framework.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * {@code Authentication} dont le principal est un {@link CurrentUser} : à placer dans le {@code SecurityContext}
 * par le filtre d'authentification (partie 2) ; autorités = {@code ROLE_<rôle>}.
 */
public class CurrentUserAuthentication extends AbstractAuthenticationToken {

    private final CurrentUser user;

    public CurrentUserAuthentication(CurrentUser user) {
        super(user.roles().stream().map(role -> new SimpleGrantedAuthority(role.authority())).toList());
        this.user = user;
        setAuthenticated(true);
    }

    @Override
    public CurrentUser getPrincipal() {
        return user;
    }

    @Override
    public Object getCredentials() {
        return null;
    }
}
