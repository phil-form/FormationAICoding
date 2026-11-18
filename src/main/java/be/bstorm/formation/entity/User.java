package be.bstorm.formation.entity;

import be.bstorm.formation.framework.entity.SoftDeletableEntity;
import be.bstorm.formation.framework.security.Role;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Utilisateur minimal nécessaire à l'authentification (feature 001) ; complété par la feature 002.
 * <p>
 * {@code @DynamicUpdate} : une modification de l'entité (feature 002) n'écrit que les colonnes modifiées et n'écrase
 * donc pas {@code locked_until} / compteurs posés par les mises à jour conditionnelles de 001.
 * Aucun {@code toString} : le hachage du mot de passe ne doit jamais apparaître dans un log.
 */
@Entity
@Table(name = "app_user")
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User extends SoftDeletableEntity {

    /** Identifiant de connexion normalisé (trim + minuscules), RG-02. */
    @Column(name = "username", nullable = false, length = 50)
    private String username;

    /** {@code p<version de poivre>$<chaîne Argon2id>} (RG-14). SECRET : jamais renvoyé ni journalisé. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** Dépôt de rattachement ; FK ajoutée par la feature 003. */
    @Column(name = "depot_id")
    private UUID depotId;

    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "app_user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    private Set<Role> roles = new HashSet<>();

    @Builder.Default
    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
