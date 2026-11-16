package be.bstorm.formation.framework.storage;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

/**
 * Stockage des fichiers téléversés ({@code app.storage.*}).
 *
 * @param root        dossier des fichiers chiffrés (volume {@code uploads}, hors de l'image en lecture seule)
 * @param maxFileSize taille maximale d'un fichier (réponse client C10 : 10 Mo) — alignée sur
 *                    {@code spring.servlet.multipart} et la limite du WAF
 * @param maxPixels   nombre maximal de pixels d'une image, vérifié <b>avant</b> décodage (bombe de décompression)
 */
@Validated
@ConfigurationProperties(prefix = "app.storage")
public record FileStorageProperties(@NotNull Path root, @NotNull DataSize maxFileSize, @Positive long maxPixels) {
}
