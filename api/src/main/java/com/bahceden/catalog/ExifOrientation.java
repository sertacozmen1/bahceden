package com.bahceden.catalog;

/**
 * JPEG'in EXIF yön etiketini ({@code 0x0112}) okuyan küçük okuyucu
 * (add-product-listings D4).
 *
 * <p>Telefonlar fotoğrafın yönünü piksellerde değil bu etikette taşır;
 * {@code javax.imageio} etiketi yok sayar. Tek bir etiket için meta veri
 * kütüphanesi alınmaz (K7): JPEG segmentleri gezilir, APP1 içindeki TIFF
 * başlığı ve IFD0 okunur. Her okuma sınır denetimlidir; bozuk ya da eksik
 * veri "döndürme yok" (1) demektir, hata değildir.
 */
final class ExifOrientation {

    /** Döndürme yok. */
    static final int NORMAL = 1;

    private static final int ORIENTATION_TAG = 0x0112;
    private static final int MARKER_PREFIX = 0xFF;
    private static final int SOI = 0xD8;
    private static final int APP1 = 0xE1;
    private static final int SOS = 0xDA;
    private static final int EOI = 0xD9;
    private static final int TIFF_MAGIC = 42;
    private static final int IFD_ENTRY_SIZE = 12;
    private static final int MAX_ORIENTATION = 8;
    private static final byte[] EXIF_HEADER = {'E', 'x', 'i', 'f', 0, 0};

    private ExifOrientation() {
    }

    /** @return 1–8 arası EXIF yönü; etiket yoksa veya okunamıyorsa {@link #NORMAL} */
    static int read(byte[] jpeg) {
        if (jpeg.length < 4 || u8(jpeg, 0) != MARKER_PREFIX || u8(jpeg, 1) != SOI) {
            return NORMAL;
        }
        int offset = 2;
        while (offset + 4 <= jpeg.length) {
            if (u8(jpeg, offset) != MARKER_PREFIX) {
                return NORMAL;
            }
            int marker = u8(jpeg, offset + 1);
            if (marker == SOS || marker == EOI) {
                return NORMAL;
            }
            int length = (u8(jpeg, offset + 2) << 8) | u8(jpeg, offset + 3);
            if (length < 2) {
                return NORMAL;
            }
            if (marker == APP1 && startsWith(jpeg, offset + 4, EXIF_HEADER)) {
                return fromTiff(jpeg, offset + 4 + EXIF_HEADER.length, offset + 2 + length);
            }
            offset += 2 + length;
        }
        return NORMAL;
    }

    private static int fromTiff(byte[] data, int tiff, int end) {
        if (tiff + 8 > end || end > data.length) {
            return NORMAL;
        }
        boolean littleEndian;
        if (data[tiff] == 'I' && data[tiff + 1] == 'I') {
            littleEndian = true;
        } else if (data[tiff] == 'M' && data[tiff + 1] == 'M') {
            littleEndian = false;
        } else {
            return NORMAL;
        }
        if (u16(data, tiff + 2, littleEndian) != TIFF_MAGIC) {
            return NORMAL;
        }
        long ifd = tiff + u32(data, tiff + 4, littleEndian);
        if (ifd + 2 > end) {
            return NORMAL;
        }
        int entries = u16(data, (int) ifd, littleEndian);
        for (int i = 0; i < entries; i++) {
            long entry = ifd + 2 + (long) i * IFD_ENTRY_SIZE;
            if (entry + IFD_ENTRY_SIZE > end) {
                return NORMAL;
            }
            if (u16(data, (int) entry, littleEndian) == ORIENTATION_TAG) {
                int value = u16(data, (int) entry + 8, littleEndian);
                return value >= NORMAL && value <= MAX_ORIENTATION ? value : NORMAL;
            }
        }
        return NORMAL;
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        if (offset + prefix.length > data.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static int u8(byte[] data, int offset) {
        return data[offset] & 0xFF;
    }

    private static int u16(byte[] data, int offset, boolean littleEndian) {
        return littleEndian
                ? u8(data, offset) | (u8(data, offset + 1) << 8)
                : (u8(data, offset) << 8) | u8(data, offset + 1);
    }

    private static long u32(byte[] data, int offset, boolean littleEndian) {
        return littleEndian
                ? (long) u16(data, offset, true) | ((long) u16(data, offset + 2, true) << 16)
                : ((long) u16(data, offset, false) << 16) | u16(data, offset + 2, false);
    }
}
