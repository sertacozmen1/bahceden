package com.bahceden.support;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/**
 * Testler için programla üretilen sahte görseller (K10: gerçek fotoğraf yok).
 *
 * <ul>
 *   <li>{@link #jpeg}: düz renkli JPEG;</li>
 *   <li>{@link #jpegWithExif}: yön etiketi ve sahte bir konum işareti taşıyan APP1 eklenmiş JPEG;</li>
 *   <li>{@link #transparentPng}: tamamen saydam PNG;</li>
 *   <li>{@link #pngDeclaring}: başlığı dev boyut beyan eden, piksel verisi olmayan PNG
 *       (görüntü bombası sınırını belleği tüketmeden sınamak için).</li>
 * </ul>
 */
public final class TestImages {

    /** Çıktıda bulunmaması gereken, APP1'e gömülü sahte konum işareti. */
    public static final String FAKE_LOCATION_MARKER = "SAHTE-KONUM-41.0N-29.0E";

    private TestImages() {
    }

    public static byte[] jpeg(int width, int height) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(new Color(40, 120, 60));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return encode(image, "jpeg");
    }

    /** SOI'nin hemen ardına Exif APP1 eklenir: IFD0'da yön ({@code 0x0112}) ve GPS işaretçisi. */
    public static byte[] jpegWithExif(int width, int height, int orientation) {
        byte[] plain = jpeg(width, height);
        byte[] marker = FAKE_LOCATION_MARKER.getBytes(StandardCharsets.US_ASCII);
        var tiff = ByteBuffer.allocate(8 + 2 + 2 * 12 + 4 + marker.length).order(ByteOrder.LITTLE_ENDIAN);
        tiff.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
        tiff.putShort((short) 2);
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        tiff.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(0);
        tiff.putInt(0);
        tiff.put(marker);
        byte[] exifHeader = {'E', 'x', 'i', 'f', 0, 0};
        int segmentLength = 2 + exifHeader.length + tiff.capacity();
        var out = new ByteArrayOutputStream();
        out.write(plain, 0, 2);
        out.write(0xFF);
        out.write(0xE1);
        out.write(segmentLength >> 8);
        out.write(segmentLength & 0xFF);
        out.writeBytes(exifHeader);
        out.writeBytes(tiff.array());
        out.write(plain, 2, plain.length - 2);
        return out.toByteArray();
    }

    public static byte[] transparentPng(int width, int height) {
        return encode(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png");
    }

    /** Geçerli PNG imzası ve IHDR; piksel verisi yok. Okuyucu boyutu başlıktan okur. */
    public static byte[] pngDeclaring(int width, int height) {
        var out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        var ihdr = ByteBuffer.allocate(13).putInt(width).putInt(height)
                .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0).array();
        chunk(out, "IHDR", ihdr);
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    public static BufferedImage decode(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    public static boolean contains(byte[] haystack, String needle) {
        byte[] target = needle.getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i + target.length <= haystack.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (haystack[i + j] != target[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
        out.writeBytes(typeBytes);
        out.writeBytes(data);
        var crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }

    private static byte[] encode(BufferedImage image, String format) {
        var out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, format, out);
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
        return out.toByteArray();
    }
}
