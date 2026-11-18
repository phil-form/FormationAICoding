package be.bstorm.formation.repository;

import be.bstorm.formation.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Utilisateurs (feature 001). Les écritures de l'authentification passent exclusivement par les mises à jour
 * conditionnelles ci-dessous : elles n'incrémentent pas {@code version} (RG-23).
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Ne voit pas les supprimés (@SQLRestriction). Rôles chargés dans la même requête : l'entité est utilisée hors
     * transaction par le login, sans LazyInitializationException.
     */
    @EntityGraph(attributePaths = "roles")
    Optional<User> findByUsername(String normalizedUsername);

    boolean existsByUsername(String normalizedUsername);

    /**
     * Utilisateur non supprimé avec ses rôles (refresh, RG-22 étape 7). Requête JPQL : un utilisateur supprimé
     * logiquement donne un résultat vide, même si un proxy de cet utilisateur existe déjà dans le contexte de
     * persistance (association {@code RefreshTokenFamily.user}).
     */
    @EntityGraph(attributePaths = "roles")
    @Query("select u from User u where u.id = :id")
    Optional<User> findWithRolesById(@Param("id") UUID id);

    /**
     * SEC-10 : lu dans la même transaction, juste après registerFailedLogin = 1 ; non null ⇔ cette mise à jour a posé
     * le verrou.
     */
    @Query("select u.lockedUntil from User u where u.id = :id")
    Instant findLockedUntilById(@Param("id") UUID id);

    /**
     * RG-04 : échec sur un compte non verrouillé. 1 si appliqué, 0 si le compte est verrouillé (ou absent).
     * <p>
     * Requête native : une seule instruction atomique (verrou posé une seule fois en concurrence), sans incrément de
     * {@code version} (RG-23). Les branches du {@code CASE} sont typées explicitement en {@code timestamptz} : sans
     * cela PostgreSQL résout l'expression en {@code text} et refuse l'affectation (SQLState 42804). Dans un
     * {@code UPDATE} PostgreSQL, toutes les expressions lisent les valeurs d'avant la mise à jour.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            update app_user set
              failed_login_count = case when failed_login_count + 1 >= :maxAttempts then 0
                                        else failed_login_count + 1 end,
              locked_until       = case when failed_login_count + 1 >= :maxAttempts
                                        then cast(:lockUntil as timestamptz)
                                        else cast(null as timestamptz) end,
              updated_at         = cast(:now as timestamptz)
            where id = :id
              and deleted_at is null
              and (locked_until is null or locked_until <= cast(:now as timestamptz))""")
    int registerFailedLogin(@Param("id") UUID id, @Param("now") Instant now,
                            @Param("maxAttempts") int maxAttempts, @Param("lockUntil") Instant lockUntil);

    /**
     * RG-04 / RG-25 : succès → compteur 0, verrou null, last_login_at = now ; appliqué SEULEMENT si le hachage est
     * toujours celui qui a été vérifié, le compte actif et non supprimé. 1 si appliqué ; 0 sinon.
     */
    @Modifying
    @Query("""
            update User u set u.failedLoginCount = 0, u.lockedUntil = null, u.lastLoginAt = :now, u.updatedAt = :now
            where u.id = :id and u.passwordHash = :verifiedHash and u.enabled = true and u.deletedAt is null""")
    int registerSuccessfulLogin(@Param("id") UUID id, @Param("verifiedHash") String verifiedHash,
                                @Param("now") Instant now);

    /** RG-14 : re-hachage, appliqué seulement si le hachage n'a pas changé entre-temps. */
    @Modifying
    @Query("""
            update User u set u.passwordHash = :newHash, u.updatedAt = :now
            where u.id = :id and u.passwordHash = :expectedHash""")
    int updatePasswordHash(@Param("id") UUID id, @Param("expectedHash") String expectedHash,
                           @Param("newHash") String newHash, @Param("now") Instant now);
}
