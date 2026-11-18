package be.bstorm.formation.repository;

import be.bstorm.formation.entity.RefreshTokenFamily;
import be.bstorm.formation.entity.SessionRevocationReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/** Familles de jetons de rafraîchissement : révocations conditionnelles, sans incrément de {@code version} (RG-23). */
public interface RefreshTokenFamilyRepository extends JpaRepository<RefreshTokenFamily, UUID> {

    /** Révoque une famille non encore révoquée ; 1 si appliqué, 0 si déjà révoquée (raison conservée). */
    @Modifying
    @Query("""
            update RefreshTokenFamily f set f.revokedAt = :now, f.revocationReason = :reason, f.updatedAt = :now
            where f.id = :id and f.revokedAt is null""")
    int revoke(@Param("id") UUID familyId, @Param("reason") SessionRevocationReason reason, @Param("now") Instant now);

    /**
     * RG-16 : révoque toutes les familles non révoquées de l'utilisateur ; renvoie leur nombre.
     * flushAutomatically = true OBLIGATOIRE : les modifications en attente de la transaction englobante (UPDATE
     * app_user de 002 par dirty checking ou SoftDeleter) sont envoyées en base AVANT la révocation (RG-16, RG-25).
     * <p>
     * Requête native sur la colonne {@code user_id} : la condition ne dépend pas de la visibilité de l'utilisateur
     * ({@code @SQLRestriction} de suppression logique sur {@code User}, RM10) — un utilisateur supprimé logiquement
     * dans la même transaction voit bien ses familles révoquées. La raison est écrite par son nom (colonne
     * {@code EnumType.STRING}).
     */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            update refresh_token_family
               set revoked_at = cast(:now as timestamptz),
                   revocation_reason = :#{#reason.name()},
                   updated_at = cast(:now as timestamptz)
             where user_id = :userId and revoked_at is null""")
    int revokeAllActiveByUserId(@Param("userId") UUID userId, @Param("reason") SessionRevocationReason reason,
                                @Param("now") Instant now);

    /** RG-17 : supprime les familles expirées ou révoquées avant {@code threshold} ; renvoie leur nombre. */
    @Modifying
    @Query("""
            delete from RefreshTokenFamily f
            where f.absoluteExpiresAt < :threshold or f.revokedAt < :threshold""")
    int deletePurgeable(@Param("threshold") Instant threshold);
}
