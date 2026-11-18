package be.bstorm.formation.dto.response;

import be.bstorm.formation.framework.security.Role;

import java.util.List;
import java.util.UUID;

/** Utilisateur courant (RG-19) : rôles dans l'ordre de déclaration de {@link Role}, dépôt {@code null} possible. */
public record CurrentUserResponse(
        UUID id,
        List<Role> roles,
        UUID depotId
) {
}
