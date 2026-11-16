package be.bstorm.formation.framework.entity;

import be.bstorm.formation.framework.security.CurrentUser;
import be.bstorm.formation.framework.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Suppression logique (RM10) d'une entité <b>gérée</b> : date (horloge injectée) et auteur (utilisateur courant),
 * écrits au flush. Jamais de {@code repository.delete()} sur une {@link SoftDeletableEntity} depuis l'API.
 */
@Component
@RequiredArgsConstructor
public class SoftDeleter {

    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public void delete(SoftDeletableEntity entity) {
        entity.markDeleted(currentUser.current().map(CurrentUser::id).orElse(null), clock.instant());
    }
}
