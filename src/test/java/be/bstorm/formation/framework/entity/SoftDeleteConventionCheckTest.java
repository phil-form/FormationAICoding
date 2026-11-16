package be.bstorm.formation.framework.entity;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SoftDeleteConventionCheckTest {

    /** Oubli de {@code @SQLRestriction} : les lignes supprimées réapparaîtraient. */
    static class Forgotten extends SoftDeletableEntity {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static SoftDeleteConventionCheck checkFor(Class<?>... types) {
        Set<EntityType<?>> entities = new java.util.HashSet<>();
        for (Class<?> type : types) {
            EntityType entity = mock(EntityType.class);
            when(entity.getJavaType()).thenReturn(type);
            entities.add(entity);
        }
        Metamodel metamodel = mock(Metamodel.class);
        when(metamodel.getEntities()).thenReturn(entities);
        EntityManagerFactory factory = mock(EntityManagerFactory.class);
        when(factory.getMetamodel()).thenReturn(metamodel);
        return new SoftDeleteConventionCheck(factory);
    }

    @Test
    void annotatedOrNonDeletableEntitiesPass() {
        assertThatCode(() -> checkFor(ProbeBooking.class, ProbeBookingNote.class).afterSingletonsInstantiated())
                .doesNotThrowAnyException();
    }

    @Test
    void missingRestrictionPreventsStartup() {
        assertThatThrownBy(() -> checkFor(ProbeBooking.class, Forgotten.class).afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Forgotten.class.getName());
    }
}
