package be.bstorm.formation.framework.storage;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/**
 * Réponse de téléchargement : toujours en pièce jointe ({@code Content-Disposition: attachment}, nom encodé
 * RFC 6266 / 5987), type réel du contenu ; {@code nosniff} et {@code no-store} viennent des en-têtes globaux.
 * À n'appeler qu'après le contrôle d'accès (RM9) de la ressource propriétaire.
 */
public final class FileResponses {

    private FileResponses() {
    }

    public static ResponseEntity<byte[]> attachment(FileContent file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }
}
