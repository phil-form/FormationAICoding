package be.bstorm.formation.entity;

import be.bstorm.formation.framework.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Jeton de rafraîchissement à usage unique (RG-08, RG-09). Seule l'empreinte SHA-256 (hex minuscule) de la valeur du
 * cookie est stockée ; la valeur brute n'est jamais persistée ni journalisée.
 */
@Entity
@Table(name = "refresh_token")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false)
    private RefreshTokenFamily family;

    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;
}
