package be.bstorm.formation.repository;

import be.bstorm.formation.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Jetons de rafraîchissement (empreintes SHA-256). */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Consommation atomique (RG-09/RG-10/RG-22 étape 9) : 1 si le jeton était inutilisé, 0 sinon. */
    @Modifying
    @Query("update RefreshToken t set t.usedAt = :now, t.updatedAt = :now where t.id = :id and t.usedAt is null")
    int markUsed(@Param("id") UUID id, @Param("now") Instant now);

    /** RG-17 : supprime les jetons des familles purgeables (même critère que la purge des familles). */
    @Modifying
    @Query("""
            delete from RefreshToken t where t.family.id in (
              select f.id from RefreshTokenFamily f
              where f.absoluteExpiresAt < :threshold or f.revokedAt < :threshold)""")
    int deleteOfPurgeableFamilies(@Param("threshold") Instant threshold);
}
