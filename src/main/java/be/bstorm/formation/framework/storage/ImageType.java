package be.bstorm.formation.framework.storage;

import java.util.Optional;

/** Types d'image acceptés (réponse client C10), reconnus à leur <b>signature binaire</b>, jamais à l'extension. */
public enum ImageType {

    JPEG("image/jpeg", "jpg", "jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("image/png", "png", "png", new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});

    private final String mediaType;
    private final String extension;
    private final String imageIoFormat;
    private final byte[] signature;

    ImageType(String mediaType, String extension, String imageIoFormat, byte[] signature) {
        this.mediaType = mediaType;
        this.extension = extension;
        this.imageIoFormat = imageIoFormat;
        this.signature = signature;
    }

    public String mediaType() {
        return mediaType;
    }

    public String extension() {
        return extension;
    }

    String imageIoFormat() {
        return imageIoFormat;
    }

    public static Optional<ImageType> detect(byte[] content) {
        for (ImageType type : values()) {
            if (content.length >= type.signature.length
                    && java.util.Arrays.equals(content, 0, type.signature.length, type.signature, 0, type.signature.length)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
