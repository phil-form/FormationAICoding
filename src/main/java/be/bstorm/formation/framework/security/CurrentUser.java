package be.bstorm.formation.framework.security;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Utilisateur authentifié, tel que l'API le connaît après validation du jeton (partie 2) : identifiant, rôles
 * cumulés, dépôt de rattachement ({@code null} possible pour un admin sans dépôt).
 * <p>
 * Ne porte aucune donnée personnelle : seul l'identifiant technique peut figurer dans les logs.
 */
public record CurrentUser(UUID id, Set<Role> roles, UUID depotId) {

    public CurrentUser {
        Objects.requireNonNull(id, "id");
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Un utilisateur a au moins un rôle");
        }
        roles = Set.copyOf(EnumSet.copyOf(roles));
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }

    /** Propriétaire de la ressource (conducteur qui l'a créée). */
    public boolean owns(UUID ownerId) {
        return id.equals(ownerId);
    }

    /** Admin, ou gestionnaire du dépôt indiqué. */
    public boolean managesDepot(UUID depot) {
        return isAdmin() || (hasRole(Role.FLEET_MANAGER) && depotId != null && depotId.equals(depot));
    }
}
