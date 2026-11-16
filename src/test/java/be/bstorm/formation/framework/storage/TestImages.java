package be.bstorm.formation.framework.storage;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/** Images de test construites en mémoire, avec métadonnées injectées octet par octet. */
final class TestImages {

    /** Marqueur placé dans les métadonnées : ne doit jamais survivre à la neutralisation. */
    static final String SECRET = "GPS-50.4108N-4.4446E";

    private TestImages() {
    }

    /** 16 × 8 : moitié gauche rouge, moitié droite bleue. */
    static BufferedImage redLeftBlueRight() {
        BufferedImage image = new BufferedImage(16, 8, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 8; y++) {
                image.setRGB(x, y, (x < 8 ? Color.RED : Color.BLUE).getRGB());
            }
        }
        return image;
    }

    static byte[] encode(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** JPEG avec un segment APP1 EXIF (ordre {@code MM} ou {@code II}) : orientation + marqueur {@link #SECRET}. */
    static byte[] jpegWithExif(int orientation, boolean littleEndian) {
        byte[] jpeg = encode(redLeftBlueRight(), "jpg");
        byte[] secret = SECRET.getBytes(StandardCharsets.US_ASCII);
        ByteBuffer tiff = ByteBuffer.allocate(8 + 2 + 12 + 4 + secret.length);
        tiff.order(littleEndian ? java.nio.ByteOrder.LITTLE_ENDIAN : java.nio.ByteOrder.BIG_ENDIAN);
        tiff.put(littleEndian ? (byte) 'I' : (byte) 'M').put(littleEndian ? (byte) 'I' : (byte) 'M');
        tiff.putShort((short) 42).putInt(8);                 // en-tête TIFF, IFD0 à l'offset 8
        tiff.putShort((short) 1);                            // 1 entrée
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        tiff.putInt(0);                                      // pas d'IFD suivant
        tiff.put(secret);
        byte[] exif = ByteBuffer.allocate(6 + tiff.capacity()).put("Exif\0\0".getBytes(StandardCharsets.US_ASCII))
                .put(tiff.array()).array();
        ByteBuffer out = ByteBuffer.allocate(jpeg.length + 4 + exif.length);
        out.put(jpeg, 0, 2);                                 // SOI
        out.put((byte) 0xFF).put((byte) 0xE1).putShort((short) (exif.length + 2)).put(exif);
        out.put(jpeg, 2, jpeg.length - 2);
        return out.array();
    }

    /** PNG avec un bloc tEXt contenant {@link #SECRET}, inséré après IHDR. */
    static byte[] pngWithTextChunk(BufferedImage image) {
        byte[] png = encode(image, "png");
        byte[] data = ("Comment\0" + SECRET).getBytes(StandardCharsets.ISO_8859_1);
        byte[] type = "tEXt".getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(type);
        crc.update(data);
        int ihdrEnd = 8 + 4 + 4 + 13 + 4;
        return ByteBuffer.allocate(png.length + 12 + data.length)
                .put(png, 0, ihdrEnd)
                .putInt(data.length).put(type).put(data).putInt((int) crc.getValue())
                .put(png, ihdrEnd, png.length - ihdrEnd)
                .array();
    }

    static boolean contains(byte[] haystack, String needle) {
        return new String(haystack, StandardCharsets.ISO_8859_1).contains(needle);
    }
}
