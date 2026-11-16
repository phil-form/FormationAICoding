package be.bstorm.formation.framework.security;

import be.bstorm.formation.framework.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Cloisonnement RM9, appliqué à une ressource <b>déjà chargée</b> (lecture par id, modification). Pour les listes,
 * filtrer dans la requête avec {@link AccessSpecifications} : jamais de filtrage après chargement.
 * <p>
 * Ressource d'autrui → {@link NotFoundException} (404), comme une ressource absente : ne pas révéler son existence
 * (OWASP API1:2023 — BOLA).
 */
@Component
@RequiredArgsConstructor
public class AccessPolicy {

    private final CurrentUserProvider currentUser;

    /** Conducteur propriétaire, gestionnaire du dépôt ou admin (réservations, pleins, sinistres). */
    public boolean canAccessOwned(CurrentUser user, UUID ownerId, UUID depotId) {
        return user.owns(ownerId) || user.managesDepot(depotId);
    }

    /** Gestionnaire du dépôt ou admin (véhicules, entretiens, traitement des sinistres). */
    public boolean canManage(CurrentUser user, UUID depotId) {
        return user.managesDepot(depotId);
    }

    /** @throws NotFoundException si l'utilisateur courant n'y a pas accès */
    public void checkOwned(UUID ownerId, UUID depotId, String resource) {
        if (!canAccessOwned(currentUser.require(), ownerId, depotId)) {
            throw notFound(resource);
        }
    }

    /** @throws NotFoundException si l'utilisateur courant ne gère pas ce dépôt */
    public void checkManaged(UUID depotId, String resource) {
        if (!canManage(currentUser.require(), depotId)) {
            throw notFound(resource);
        }
    }

    private static NotFoundException notFound(String resource) {
        return new NotFoundException(resource + " not found.");
    }
}
