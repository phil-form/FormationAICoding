package be.bstorm.formation.framework.security;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cloisonnement RM9 <b>dans la requête</b> (listes, pagination, statistiques) : le repository étend
 * {@code JpaSpecificationExecutor} et combine la spécification d'accès avec ses propres critères
 * ({@code access.and(filtres)}).
 * <p>
 * Chemins JPA séparés par des points, ex. {@code "driver.id"}, {@code "vehicle.depot.id"}.
 */
public final class AccessSpecifications {

    private AccessSpecifications() {
    }

    /** Admin : tout ; sinon les lignes dont l'utilisateur est propriétaire OU dont il gère le dépôt. */
    public static <T> Specification<T> ownedOrManaged(CurrentUser user, String ownerPath, String depotPath) {
        return (root, query, cb) -> {
            if (user.isAdmin()) {
                return cb.conjunction();
            }
            List<Predicate> allowed = new ArrayList<>();
            allowed.add(cb.equal(path(root, ownerPath), user.id()));
            managedDepot(user, root, depotPath, cb, allowed);
            return cb.or(allowed.toArray(Predicate[]::new));
        };
    }

    /** Admin : tout ; gestionnaire : son dépôt ; autres : rien. */
    public static <T> Specification<T> managed(CurrentUser user, String depotPath) {
        return (root, query, cb) -> {
            if (user.isAdmin()) {
                return cb.conjunction();
            }
            List<Predicate> allowed = new ArrayList<>();
            managedDepot(user, root, depotPath, cb, allowed);
            return allowed.isEmpty() ? cb.disjunction() : allowed.getFirst();
        };
    }

    private static void managedDepot(CurrentUser user, Root<?> root, String depotPath, CriteriaBuilder cb,
                                     List<Predicate> allowed) {
        UUID depot = user.depotId();
        if (user.hasRole(Role.FLEET_MANAGER) && depot != null) {
            allowed.add(cb.equal(path(root, depotPath), depot));
        }
    }

    private static Path<Object> path(Root<?> root, String dotted) {
        Path<Object> path = null;
        for (String segment : dotted.split("\\.")) {
            path = path == null ? root.get(segment) : path.get(segment);
        }
        return path;
    }
}
