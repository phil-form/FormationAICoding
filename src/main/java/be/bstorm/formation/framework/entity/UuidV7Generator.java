package be.bstorm.formation.framework.entity;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.EventTypeSets;

import java.util.EnumSet;

/**
 * Générateur d'identifiant UUID v7 qui <b>respecte un identifiant déjà attribué</b> ({@link BaseEntity#assignId()}) :
 * nécessaire quand l'id sert de contexte de chiffrement avant la persistance (NISS, fichiers).
 */
public class UuidV7Generator implements BeforeExecutionGenerator {

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner, Object currentValue,
                           EventType eventType) {
        return currentValue != null ? currentValue : UuidV7.next();
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EventTypeSets.INSERT_ONLY;
    }

    @Override
    public boolean allowAssignedIdentifiers() {
        return true;
    }
}
