package be.bstorm.formation.framework.storage;

import be.bstorm.formation.framework.entity.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * Métadonnées d'un fichier téléversé ; le contenu, chiffré, est sur le volume {@code uploads} sous le nom
 * {@code <id>.bin} (non devinable). Pas de setter public : seul {@link FileStorage} crée et modifie ces lignes.
 * Une feature y fait référence par clé étrangère (ex. photo → sinistre).
 */
@Getter
@Entity
@Table(name = "stored_file")
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredFile extends SoftDeletableEntity {

    /** Nom d'origine nettoyé (sans chemin ni caractère de contrôle, extension conforme au type réel). */
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    /** Taille du contenu en clair (après neutralisation). */
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** SHA-256 (hex) du contenu en clair : contrôle d'intégrité à la lecture. */
    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    /** Clé de données AES-256 chiffrée par OpenBao Transit (clé {@code file}, contexte = id du fichier). */
    @Column(name = "wrapped_key", nullable = false)
    private String wrappedKey;

    StoredFile(String originalName, String contentType, long sizeBytes, String sha256) {
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
    }

    void setWrappedKey(String wrappedKey) {
        this.wrappedKey = wrappedKey;
    }
}
