package be.bstorm.formation.framework.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import org.hibernate.Hibernate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Base de toutes les entités.
 * <ul>
 *   <li>Identifiant UUID v7 (ordonné dans le temps, bon pour les index) : non devinable, limite l'énumération
 *       des ressources (OWASP API1 — BOLA). Attribué à la persistance, ou avant par {@link #assignId()}.</li>
 *   <li>Audit de création / modification, verrou optimiste ({@code @Version}).</li>
 *   <li>{@code equals/hashCode} sûrs avec les proxys Hibernate et les entités non encore persistées.</li>
 * </ul>
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @UuidV7Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    /**
     * Attribue l'identifiant <b>avant</b> la persistance (idempotent) : à utiliser quand l'id sert de contexte de
     * chiffrement ({@code CryptoService}), car Hibernate fige l'état à insérer dès le {@code persist}.
     */
    public UUID assignId() {
        if (id == null) {
            id = UuidV7.next();
        }
        return id;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) {
            return false;
        }
        return id != null && id.equals(((BaseEntity) other).getId());
    }

    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
