package be.bstorm.formation.framework.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité à suppression <b>logique</b> (RM10) : supprimée = {@code deleted_at} renseigné ; la ligne reste en base.
 * <p>
 * Chaque entité concrète porte {@code @SQLRestriction(SoftDeletableEntity.NOT_DELETED)} : les lignes supprimées
 * disparaissent alors de <b>toutes</b> les requêtes JPA (lecture par id, JPQL, Criteria, collections) — vérifié au
 * démarrage par {@link SoftDeleteConventionCheck}. Les requêtes SQL natives ne sont pas filtrées : y ajouter
 * {@code deleted_at IS NULL}.
 * <p>
 * Contraintes d'unicité : index unique <b>partiel</b> {@code ... WHERE deleted_at IS NULL} (une valeur supprimée
 * peut être reprise). Colonnes : {@code deleted_at timestamptz null}, {@code deleted_by uuid null}.
 * Supprimer : {@link SoftDeleter#delete}.
 */
@Getter
@MappedSuperclass
public abstract class SoftDeletableEntity extends BaseEntity {

    /** Restriction SQL à placer sur chaque entité concrète. */
    public static final String NOT_DELETED = "deleted_at IS NULL";

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Utilisateur ayant supprimé ; {@code null} pour une opération système. */
    @Column(name = "deleted_by")
    private UUID deletedBy;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Idempotent : une deuxième suppression ne modifie ni la date ni l'auteur. */
    void markDeleted(UUID by, Instant at) {
        if (deletedAt == null) {
            deletedAt = Objects.requireNonNull(at, "at");
            deletedBy = by;
        }
    }
}
