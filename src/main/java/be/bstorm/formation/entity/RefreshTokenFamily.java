package be.bstorm.formation.entity;

import be.bstorm.formation.framework.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Famille de jetons de rafraîchissement : une par login réussi (RG-01), révoquée en bloc (RG-10, RG-12, RG-16).
 * Donnée technique de session : pas de suppression logique, purgée par RG-17.
 */
@Entity
@Table(name = "refresh_token_family")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RefreshTokenFamily extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "absolute_expires_at", nullable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revocation_reason", length = 30)
    private SessionRevocationReason revocationReason;
}
