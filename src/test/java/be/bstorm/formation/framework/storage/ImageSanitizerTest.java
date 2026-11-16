package be.bstorm.formation.framework.storage;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static be.bstorm.formation.framework.storage.TestImages.SECRET;
import static be.bstorm.formation.framework.storage.TestImages.contains;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageSanitizerTest {

    private final ImageSanitizer sanitizer = sanitizer(40_000_000);

    private static ImageSanitizer sanitizer(long maxPixels) {
        return new ImageSanitizer(new FileStorageProperties(Path.of("unused"), DataSize.ofMegabytes(10), maxPixels));
    }

    private static BufferedImage decode(byte[] content) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(content));
    }

    private static boolean isRedish(int rgb) {
        Color c = new Color(rgb);
        return c.getRed() > 180 && c.getBlue() < 80;
    }

    private static boolean isBlueish(int rgb) {
        Color c = new Color(rgb);
        return c.getBlue() > 180 && c.getRed() < 80;
    }

    @Test
    void jpegExifIncludingLocationIsRemoved() throws IOException {
        byte[] input = TestImages.jpegWithExif(1, false);
        assertThat(contains(input, SECRET)).isTrue();

        ImageSanitizer.SanitizedImage image = sanitizer.sanitize(input);

        assertThat(image.type()).isEqualTo(ImageType.JPEG);
        assertThat(contains(image.content(), SECRET)).isFalse();
        assertThat(contains(image.content(), "Exif")).isFalse();
        BufferedImage decoded = decode(image.content());
        assertThat(decoded.getWidth()).isEqualTo(16);
        assertThat(decoded.getHeight()).isEqualTo(8);
    }

    @Test
    void exifOrientationIsAppliedToPixels() throws IOException {
        // Orientation 6 : l'image doit être tournée de 90° horaire → la moitié gauche (rouge) passe en haut.
        BufferedImage decoded = decode(sanitizer.sanitize(TestImages.jpegWithExif(6, true)).content());

        assertThat(decoded.getWidth()).isEqualTo(8);
        assertThat(decoded.getHeight()).isEqualTo(16);
        assertThat(isRedish(decoded.getRGB(4, 2))).isTrue();
        assertThat(isBlueish(decoded.getRGB(4, 13))).isTrue();
    }

    @Test
    void orientation8RotatesCounterClockwise() throws IOException {
        BufferedImage decoded = decode(sanitizer.sanitize(TestImages.jpegWithExif(8, false)).content());

        assertThat(decoded.getHeight()).isEqualTo(16);
        assertThat(isBlueish(decoded.getRGB(4, 2))).isTrue();
        assertThat(isRedish(decoded.getRGB(4, 13))).isTrue();
    }

    @Test
    void pngTextMetadataIsRemovedAndTransparencyKept() throws IOException {
        BufferedImage source = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0x00000000);
        source.setRGB(1, 1, 0xFF00FF00);
        byte[] input = TestImages.pngWithTextChunk(source);
        assertThat(decode(input)).isNotNull();
        assertThat(contains(input, SECRET)).isTrue();

        ImageSanitizer.SanitizedImage image = sanitizer.sanitize(input);

        assertThat(image.type()).isEqualTo(ImageType.PNG);
        assertThat(contains(image.content(), SECRET)).isFalse();
        BufferedImage decoded = decode(image.content());
        assertThat(decoded.getRGB(0, 0) >>> 24).isZero();
        assertThat(decoded.getRGB(1, 1)).isEqualTo(0xFF00FF00);
    }

    @Test
    void dataHiddenAfterTheImageIsDropped() {
        byte[] jpeg = TestImages.encode(TestImages.redLeftBlueRight(), "jpg");
        byte[] payload = "PK\u0003\u0004<script>alert(1)</script>".getBytes(StandardCharsets.ISO_8859_1);
        byte[] polyglot = ByteBuffer.allocate(jpeg.length + payload.length).put(jpeg).put(payload).array();

        assertThat(contains(sanitizer.sanitize(polyglot).content(), "<script>")).isFalse();
    }

    @Test
    void typeIsDetectedFromContentNotName() {
        assertThatThrownBy(() -> sanitizer.sanitize("<html>not an image</html>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(InvalidFileException.class, e -> assertThat(e.getStatus().value()).isEqualTo(415));
        assertThatThrownBy(() -> sanitizer.sanitize("GIF89a....".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOfSatisfying(InvalidFileException.class, e -> assertThat(e.getStatus().value()).isEqualTo(415));
        assertThatThrownBy(() -> sanitizer.sanitize(TestImages.encode(TestImages.redLeftBlueRight(), "bmp")))
                .isInstanceOfSatisfying(InvalidFileException.class, e -> assertThat(e.getStatus().value()).isEqualTo(415));
    }

    @Test
    void corruptImageWithValidSignatureIsUnprocessable() {
        byte[] fake = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 1, 2, 3, 4, 5};

        assertThatThrownBy(() -> sanitizer.sanitize(fake))
                .isInstanceOfSatisfying(InvalidFileException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(422);
                    assertThat(e.getTitle()).isEqualTo("Invalid Image");
                });
    }

    @Test
    void dimensionsAreCheckedBeforeDecoding() {
        byte[] png = TestImages.encode(TestImages.redLeftBlueRight(), "png");

        assertThatThrownBy(() -> sanitizer(100).sanitize(png))
                .isInstanceOfSatisfying(InvalidFileException.class, e -> assertThat(e.getTitle()).isEqualTo("Image Too Large"));
        assertThat(sanitizer(128).sanitize(png).type()).isEqualTo(ImageType.PNG);
    }
}
