package be.bstorm.formation.framework.entity;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import lombok.RequiredArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Refuse de démarrer si une {@link SoftDeletableEntity} n'a pas {@code @SQLRestriction(NOT_DELETED)} : sans elle,
 * les éléments supprimés réapparaîtraient silencieusement dans l'application (RM10).
 */
@Component
@RequiredArgsConstructor
public class SoftDeleteConventionCheck implements SmartInitializingSingleton {

    private final EntityManagerFactory entityManagerFactory;

    @Override
    public void afterSingletonsInstantiated() {
        List<String> missing = entityManagerFactory.getMetamodel().getEntities().stream()
                .map(EntityType::getJavaType)
                .filter(SoftDeletableEntity.class::isAssignableFrom)
                .filter(type -> {
                    SQLRestriction restriction = type.getAnnotation(SQLRestriction.class);
                    return restriction == null || !SoftDeletableEntity.NOT_DELETED.equals(restriction.value());
                })
                .map(Class::getName)
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("@SQLRestriction(SoftDeletableEntity.NOT_DELETED) manquant sur : " + missing);
        }
    }
}
