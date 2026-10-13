package ke.ac.kca.cafeteria.menu;

/**
 * Reads the EXIF orientation of a JPEG (1 to 8; 1 means upright). Phone cameras often store photos sideways
 * with an orientation flag, and ImageIO ignores that flag, so the app turns the picture itself.
 * Any problem reading the data simply returns 1 (no rotation).
 */
final class ExifOrientation {

    private ExifOrientation() {
    }

    static int read(byte[] jpeg) {
        try {
            if (jpeg.length < 4 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
                return 1;
            }
            int pos = 2;
            while (pos + 4 <= jpeg.length) {
                if ((jpeg[pos] & 0xFF) != 0xFF) {
                    return 1;
                }
                int marker = jpeg[pos + 1] & 0xFF;
                if (marker == 0xFF) {                       // padding byte
                    pos++;
                    continue;
                }
                if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                    pos += 2;                               // markers without a length
                    continue;
                }
                if (marker == 0xDA || marker == 0xD9) {
                    return 1;                               // image data starts: no more headers
                }
                int length = ((jpeg[pos + 2] & 0xFF) << 8) | (jpeg[pos + 3] & 0xFF);
                if (length < 2) {
                    return 1;
                }
                int segmentEnd = Math.min(jpeg.length, pos + 2 + length);
                if (marker == 0xE1 && pos + 10 <= segmentEnd && isExifHeader(jpeg, pos + 4)) {
                    return fromTiff(jpeg, pos + 10, segmentEnd);
                }
                pos += 2 + length;
            }
        } catch (RuntimeException e) {
            return 1;
        }
        return 1;
    }

    private static boolean isExifHeader(byte[] b, int at) {
        return b[at] == 'E' && b[at + 1] == 'x' && b[at + 2] == 'i' && b[at + 3] == 'f'
                && b[at + 4] == 0 && b[at + 5] == 0;
    }

    private static int fromTiff(byte[] b, int tiff, int end) {
        if (tiff + 8 > end) {
            return 1;
        }
        boolean bigEndian;
        if (b[tiff] == 'M' && b[tiff + 1] == 'M') {
            bigEndian = true;
        } else if (b[tiff] == 'I' && b[tiff + 1] == 'I') {
            bigEndian = false;
        } else {
            return 1;
        }
        long ifdOffset = u32(b, tiff + 4, bigEndian);
        if (ifdOffset < 8 || tiff + ifdOffset + 2 > end) {
            return 1;
        }
        int ifd = (int) (tiff + ifdOffset);
        int count = u16(b, ifd, bigEndian);
        for (int i = 0; i < count; i++) {
            int entry = ifd + 2 + i * 12;
            if (entry + 12 > end) {
                break;
            }
            if (u16(b, entry, bigEndian) == 0x0112) {
                int value = u16(b, entry + 8, bigEndian);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static int u16(byte[] b, int at, boolean big) {
        int hi = b[at] & 0xFF;
        int lo = b[at + 1] & 0xFF;
        return big ? (hi << 8) | lo : (lo << 8) | hi;
    }

    private static long u32(byte[] b, int at, boolean big) {
        long result = 0;
        for (int i = 0; i < 4; i++) {
            int value = b[at + (big ? i : 3 - i)] & 0xFF;
            result = (result << 8) | value;
        }
        return result;
    }
}
