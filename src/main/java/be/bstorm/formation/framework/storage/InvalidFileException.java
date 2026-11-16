package be.bstorm.formation.framework.storage;

import be.bstorm.formation.framework.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** Fichier refusé : vide (400), trop gros (413), type non autorisé (415), image illisible ou démesurée (422). */
public class InvalidFileException extends BusinessException {

    InvalidFileException(HttpStatus status, String title, String detail) {
        super(status, title, detail);
    }

    static InvalidFileException empty() {
        return new InvalidFileException(HttpStatus.BAD_REQUEST, "Empty File", "The file is empty.");
    }

    static InvalidFileException tooLarge() {
        return new InvalidFileException(HttpStatus.CONTENT_TOO_LARGE, "File Too Large",
                "The file exceeds the maximum allowed size.");
    }

    static InvalidFileException unsupported() {
        return new InvalidFileException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported File Type",
                "Only JPEG and PNG images are accepted.");
    }

    static InvalidFileException unreadable() {
        return new InvalidFileException(HttpStatus.UNPROCESSABLE_CONTENT, "Invalid Image",
                "The image could not be read.");
    }

    static InvalidFileException tooManyPixels() {
        return new InvalidFileException(HttpStatus.UNPROCESSABLE_CONTENT, "Image Too Large",
                "The image dimensions exceed the maximum allowed.");
    }
}
