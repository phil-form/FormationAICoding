package be.bstorm.formation.framework.storage;

/**
 * Lecture de la seule balise EXIF {@code Orientation} (0x0112) d'un JPEG, pour redresser l'image avant de retirer
 * ses métadonnées (sinon une photo de smartphone s'afficherait tournée). Analyse bornée : toute incohérence → 1
 * (orientation normale), jamais d'exception.
 */
final class ExifOrientation {

    private static final int TAG_ORIENTATION = 0x0112;

    private ExifOrientation() {
    }

    static int read(byte[] jpeg) {
        int i = 2;
        while (i + 4 <= jpeg.length && u8(jpeg, i) == 0xFF) {
            int marker = u8(jpeg, i + 1);
            if (marker == 0xDA || marker == 0xD9) {
                break;
            }
            int length = (u8(jpeg, i + 2) << 8) | u8(jpeg, i + 3);
            if (length < 2 || i + 2 + length > jpeg.length) {
                break;
            }
            if (marker == 0xE1 && length >= 16 && isExifHeader(jpeg, i + 4)) {
                return tiffOrientation(jpeg, i + 10, i + 2 + length);
            }
            i += 2 + length;
        }
        return 1;
    }

    private static boolean isExifHeader(byte[] b, int at) {
        return b[at] == 'E' && b[at + 1] == 'x' && b[at + 2] == 'i' && b[at + 3] == 'f' && b[at + 4] == 0 && b[at + 5] == 0;
    }

    private static int tiffOrientation(byte[] b, int tiff, int end) {
        boolean little;
        if (b[tiff] == 'I' && b[tiff + 1] == 'I') {
            little = true;
        } else if (b[tiff] == 'M' && b[tiff + 1] == 'M') {
            little = false;
        } else {
            return 1;
        }
        long ifdOffset = u32(b, tiff + 4, little);
        if (ifdOffset < 8 || tiff + ifdOffset + 2 > end) {
            return 1;
        }
        int ifd = (int) (tiff + ifdOffset);
        int count = u16(b, ifd, little);
        for (int e = 0; e < count; e++) {
            int entry = ifd + 2 + e * 12;
            if (entry + 12 > end) {
                return 1;
            }
            if (u16(b, entry, little) == TAG_ORIENTATION) {
                int value = u16(b, entry + 8, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static int u8(byte[] b, int at) {
        return b[at] & 0xFF;
    }

    private static int u16(byte[] b, int at, boolean little) {
        return little ? u8(b, at) | (u8(b, at + 1) << 8) : (u8(b, at) << 8) | u8(b, at + 1);
    }

    private static long u32(byte[] b, int at, boolean little) {
        return little
                ? (long) u16(b, at, true) | ((long) u16(b, at + 2, true) << 16)
                : ((long) u16(b, at, false) << 16) | u16(b, at + 2, false);
    }
}
