package be.bstorm.formation.framework.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Neutralise une image téléversée : type vérifié sur la signature binaire, dimensions vérifiées <b>avant</b>
 * décodage, puis décodage et <b>ré-encodage</b> par le JDK. Toutes les métadonnées (EXIF dont la géolocalisation,
 * commentaires, profils) disparaissent, ainsi que tout contenu caché après l'image (fichiers polyglottes).
 * L'orientation EXIF est appliquée aux pixels avant d'être perdue.
 * <p>
 * Aucun fichier temporaire : le cache disque d'ImageIO est désactivé (le contenu en clair ne touche pas le disque).
 */
@Component
@RequiredArgsConstructor
public class ImageSanitizer {

    static {
        ImageIO.setUseCache(false);
    }

    private static final float JPEG_QUALITY = 0.9f;

    private final FileStorageProperties properties;

    public SanitizedImage sanitize(byte[] content) {
        ImageType type = ImageType.detect(content).orElseThrow(InvalidFileException::unsupported);
        BufferedImage image = decode(content, type);
        int orientation = type == ImageType.JPEG ? ExifOrientation.read(content) : 1;
        return new SanitizedImage(type, encode(orient(image, orientation, type), type));
    }

    private BufferedImage decode(byte[] content, ImageType type) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(type.imageIoFormat());
        if (!readers.hasNext()) {
            throw InvalidFileException.unsupported();
        }
        ImageReader reader = readers.next();
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            reader.setInput(input, true, true);
            if ((long) reader.getWidth(0) * reader.getHeight(0) > properties.maxPixels()) {
                throw InvalidFileException.tooManyPixels();
            }
            return reader.read(0);
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvalidFileException invalid) {
                throw invalid;
            }
            throw InvalidFileException.unreadable();
        } finally {
            reader.dispose();
        }
    }

    /** Applique l'orientation EXIF (1–8) et normalise le modèle de couleur (RGB pour JPEG, ARGB si alpha en PNG). */
    static BufferedImage orient(BufferedImage source, int orientation, ImageType type) {
        int w = source.getWidth();
        int h = source.getHeight();
        // Matrices (m00, m10, m01, m11, m02, m12) : x' = m00·x + m01·y + m02 ; y' = m10·x + m11·y + m12.
        AffineTransform transform = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);   // miroir horizontal
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);  // 180°
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);   // miroir vertical
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);    // transposition
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);   // 90° horaire
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);  // transversale
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);   // 90° anti-horaire
            default -> new AffineTransform();
        };
        boolean swap = orientation >= 5 && orientation <= 8;
        int targetType = type == ImageType.PNG && source.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(swap ? h : w, swap ? w : h, targetType);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.drawImage(source, transform, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static byte[] encode(BufferedImage image, ImageType type) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName(type.imageIoFormat()).next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (type == ImageType.JPEG) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("Ré-encodage de l'image impossible", e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    /** Image neutralisée, prête à être chiffrée. */
    public record SanitizedImage(ImageType type, byte[] content) {
        @Override
        public String toString() {
            return "SanitizedImage[" + type + ", " + content.length + " bytes]";
        }
    }
}
