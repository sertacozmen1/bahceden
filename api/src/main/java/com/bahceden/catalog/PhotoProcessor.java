package com.bahceden.catalog;

import com.bahceden.common.BahcedenProperties;
import com.bahceden.common.storage.FileType;
import com.bahceden.common.web.ApiException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * İlan fotoğrafının işlenmesi (ADR-039, add-product-listings D4): yalnızca JDK.
 *
 * <ol>
 *   <li>tür içerikten (JPEG, PNG) ve boyut sınırı;</li>
 *   <li>görüntü belleğe açılmadan önce başlıktan piksel sınırı (görüntü bombası);</li>
 *   <li>EXIF yönü uygulanır ({@link ExifOrientation});</li>
 *   <li>saydamlık beyaza düzleştirilir;</li>
 *   <li>uzun kenarı 400 ve 1600 px iki çıktı, JPEG kalite 0,85.</li>
 * </ol>
 *
 * <p>Yeniden kodlama orijinal dosyanın bütün meta verisini (EXIF, konum) atar;
 * orijinal saklanmaz. Hatalar kullanıcının düzeltebileceği alan hatalarıdır
 * ({@code photo}).
 */
@Component
public class PhotoProcessor {

    /** İşlenmiş fotoğraf: iki JPEG ve büyük boyutun ölçüleri ({@code next/image} için). */
    public record ProcessedPhoto(byte[] card, byte[] large, int width, int height) {
    }

    static final int CARD_EDGE = 400;
    static final int LARGE_EDGE = 1600;

    /** 40 megapiksel: güncel telefon kameralarının üstünde, bellek için güvenli sınır. */
    static final long MAX_PIXELS = 40_000_000L;

    private static final float JPEG_QUALITY = 0.85f;
    private static final String FIELD = "photo";

    private final BahcedenProperties properties;

    PhotoProcessor(BahcedenProperties properties) {
        this.properties = properties;
    }

    public ProcessedPhoto process(byte[] bytes) {
        if (bytes.length == 0) {
            throw ApiException.invalidField(FIELD, "validation.photo.required");
        }
        if (bytes.length > properties.storage().maxPhotoBytes()) {
            throw ApiException.invalidField(FIELD, "validation.photo.tooLarge");
        }
        var type = FileType.detect(Arrays.copyOf(bytes, Math.min(bytes.length, FileType.SIGNATURE_LENGTH)))
                .filter(detected -> detected == FileType.JPEG || detected == FileType.PNG)
                .orElseThrow(() -> ApiException.invalidField(FIELD, "validation.photo.type"));

        var image = flattenToRgb(decode(bytes));
        if (type == FileType.JPEG) {
            image = orient(image, ExifOrientation.read(bytes));
        }
        var large = scaleToEdge(image, LARGE_EDGE);
        var card = scaleToEdge(image, CARD_EDGE);
        return new ProcessedPhoto(encodeJpeg(card), encodeJpeg(large), large.getWidth(), large.getHeight());
    }

    /** Boyutlar başlıktan okunur; sınırı aşan görüntünün piksellerine hiç dokunulmaz. */
    private static BufferedImage decode(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (input == null || !readers.hasNext()) {
                throw ApiException.invalidField(FIELD, "validation.photo.unreadable");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw ApiException.invalidField(FIELD, "validation.photo.tooManyPixels");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException cause) {
            // Bozuk dosya veya ImageIO'nun desteklemediği biçim (örn. CMYK JPEG): kullanıcı başka dosya dener.
            throw ApiException.invalidField(FIELD, "validation.photo.unreadable");
        }
    }

    /** Her girdi RGB'ye çevrilir; saydam alanlar beyaz zemine oturur (JPEG saydamlık taşımaz). */
    private static BufferedImage flattenToRgb(BufferedImage source) {
        var rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgb;
    }

    /**
     * EXIF yönünü uygular: 2–4 aynı ölçüde çevirme/döndürme, 5–8 genişlik ve
     * yüksekliği yer değiştirir (TIFF 6.0 tanımı).
     */
    static BufferedImage orient(BufferedImage source, int orientation) {
        if (orientation <= ExifOrientation.NORMAL || orientation > 8) {
            return source;
        }
        int w = source.getWidth();
        int h = source.getHeight();
        boolean swaps = orientation >= 5;
        // AffineTransform(m00, m10, m01, m11, m02, m12): x' = m00·x + m01·y + m02, y' = m10·x + m11·y + m12.
        var transform = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);   // yatay ayna: (w - x, y)
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);  // 180°: (w - x, h - y)
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);   // dikey ayna: (x, h - y)
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);    // transpoz: (y, x)
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);   // saat yönünde 90°: (h - y, x)
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);  // ters transpoz: (h - y, w - x)
            default -> new AffineTransform(0, -1, 1, 0, 0, w);  // 8, saat yönünün tersine 90°: (y, w - x)
        };
        var target = new BufferedImage(swaps ? h : w, swaps ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.drawImage(source, transform, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /**
     * Uzun kenarı {@code edge} olacak şekilde küçültür; küçük görüntü büyütülmez.
     * Büyük oranlarda adım adım yarıya indirilir: tek adımlı bikübik küçültme
     * 10 kat oranda kenar tırtıklanması bırakır.
     */
    static BufferedImage scaleToEdge(BufferedImage source, int edge) {
        int longest = Math.max(source.getWidth(), source.getHeight());
        if (longest <= edge) {
            return source;
        }
        double ratio = (double) edge / longest;
        int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * ratio));
        int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * ratio));
        var current = source;
        while (current.getWidth() / 2 >= targetWidth && current.getHeight() / 2 >= targetHeight) {
            current = draw(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return draw(current, targetWidth, targetHeight);
    }

    private static BufferedImage draw(BufferedImage source, int width, int height) {
        var target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        var output = new ByteArrayOutputStream();
        try (var stream = new MemoryCacheImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException cause) {
            throw new UncheckedIOException("JPEG kodlanamadı", cause);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }
}
