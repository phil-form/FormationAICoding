package be.bstorm.formation.framework.security.jwt;

import be.bstorm.formation.framework.security.CurrentUser;
import be.bstorm.formation.framework.security.CurrentUserAuthentication;
import be.bstorm.formation.framework.security.Role;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * {@code Jwt} vérifié → {@link CurrentUserAuthentication} (RG-07). Toute claim invalide ({@code sub} non UUID,
 * {@code roles} absent / vide / inconnu, {@code depot_id} invalide) → {@link InvalidBearerTokenException} (401,
 * jamais 500). Pas un bean (il serait sinon ajouté au service de conversion MVC) : instancié dans {@code SecurityConfig}.
 */
public class CurrentUserJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            UUID id = strictUuid(jwt.getClaims().get("sub"));
            Set<Role> roles = roles(jwt.getClaims().get(AccessTokenIssuer.CLAIM_ROLES));
            Object depotClaim = jwt.getClaims().get(AccessTokenIssuer.CLAIM_DEPOT_ID);
            UUID depotId = depotClaim == null ? null : strictUuid(depotClaim);
            return new CurrentUserAuthentication(new CurrentUser(id, roles, depotId));
        } catch (RuntimeException e) {
            throw new InvalidBearerTokenException("Invalid access token");
        }
    }

    private static Set<Role> roles(Object claim) {
        if (!(claim instanceof Collection<?> values) || values.isEmpty()) {
            throw new IllegalArgumentException("roles");
        }
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (Object value : values) {
            if (!(value instanceof String name)) {
                throw new IllegalArgumentException("roles");
            }
            roles.add(Role.valueOf(name));
        }
        return roles;
    }

    private static UUID strictUuid(Object value) {
        if (!(value instanceof String s)) {
            throw new IllegalArgumentException("uuid");
        }
        UUID uuid = UUID.fromString(s);
        if (!uuid.toString().equalsIgnoreCase(s)) {
            throw new IllegalArgumentException("uuid");
        }
        return uuid;
    }
}
