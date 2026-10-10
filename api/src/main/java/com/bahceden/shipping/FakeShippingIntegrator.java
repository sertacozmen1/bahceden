package com.bahceden.shipping;

import com.bahceden.common.BahcedenProperties;
import com.bahceden.shipping.domain.ShipmentStatus;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Gerçek entegratör seçilene kadar kullanılan sahte kargo entegratörü (ADR-057,
 * add-shipments D1, D4, D5; KALANLAR A2).
 *
 * <p>Ağ çağrısı yapmaz ve kişisel veriyi hiçbir yere göndermez (K10). Durumu
 * saklamaz: gönderinin açılış anı anahtarın içindedir ve durum, o andan bu yana
 * geçen süreyle yapılandırılmış eşiklere göre hesaplanır. Böylece uygulama
 * yeniden başlasa da gönderiler ilerlemeye devam eder. İptal edilen anahtarlar
 * yalnızca bellekte tutulur; sahte entegratör için yeterlidir.
 *
 * <p>Etiket, yazı tipi dosyası gerektirmeyen elle üretilmiş tek sayfalık bir
 * PDF'tir (PDF'in yerleşik Helvetica'sı; çalışma imajında yazı tipi yok). Üzerinde
 * kişisel veri yoktur, yalnızca "örnek, geçersiz" uyarısı, firma, takip numarası
 * ve referans bulunur. "Teslim edilemedi" ve hata dalları testlerde ayrı bir
 * test uygulamasıyla sınanır.
 */
@Component
class FakeShippingIntegrator implements ShippingIntegrator {

    static final String CARRIER = "Ornek Kargo";

    private static final String REF_PREFIX = "fake-shp-";
    private static final Pattern REF_FORMAT = Pattern.compile("fake-shp-(\\d+)-[0-9a-f]{8}");
    private static final int REF_RANDOM_BYTES = 4;
    private static final long TRACKING_BOUND = 10_000_000_000L;

    private final SecureRandom random = new SecureRandom();
    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();
    private final BahcedenProperties.Shipping.FakeIntegrator durations;
    private final Clock clock;

    FakeShippingIntegrator(BahcedenProperties properties, Clock clock) {
        this.durations = properties.shipping().fake();
        this.clock = clock;
    }

    @Override
    public CreatedShipment create(ShipmentRequest request) {
        var ref = REF_PREFIX + clock.instant().getEpochSecond() + "-" + randomHex();
        var tracking = String.format("FK%010d", Math.floorMod(random.nextLong(), TRACKING_BOUND));
        return new CreatedShipment(ref, CARRIER, tracking);
    }

    @Override
    public byte[] label(String integratorRef) {
        createdAt(integratorRef);
        return SimplePdf.page(List.of(
                "ORNEK ETIKET - GECERSIZ",
                "Bahceden sahte kargo entegratoru",
                "",
                "Firma: " + CARRIER,
                "Referans: " + integratorRef));
    }

    @Override
    public ShipmentStatus status(String integratorRef) {
        var age = Duration.between(createdAt(integratorRef), clock.instant());
        if (cancelled.contains(integratorRef)) {
            return ShipmentStatus.CANCELLED;
        }
        if (age.compareTo(durations.deliveredAfter()) >= 0) {
            return ShipmentStatus.DELIVERED;
        }
        if (age.compareTo(durations.inTransitAfter()) >= 0) {
            return ShipmentStatus.IN_TRANSIT;
        }
        if (age.compareTo(durations.handedOverAfter()) >= 0) {
            return ShipmentStatus.HANDED_OVER;
        }
        return ShipmentStatus.CREATED;
    }

    @Override
    public void cancel(String integratorRef) {
        if (status(integratorRef) != ShipmentStatus.CREATED) {
            throw new ShippingIntegratorException("Sahte entegratör: gönderi kargoya verilmiş, iptal edilemez");
        }
        cancelled.add(integratorRef);
    }

    private Instant createdAt(String integratorRef) {
        var matcher = REF_FORMAT.matcher(integratorRef);
        if (!matcher.matches()) {
            throw new ShippingIntegratorException("Sahte entegratör: bilinmeyen gönderi anahtarı");
        }
        return Instant.ofEpochSecond(Long.parseLong(matcher.group(1)));
    }

    private String randomHex() {
        var bytes = new byte[REF_RANDOM_BYTES];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Tek sayfalık, yalnızca ASCII metin taşıyan en küçük geçerli PDF
     * (10 x 15 cm etiket). Kütüphane alınmaz (K7); sahte etiket için yeterli.
     */
    static final class SimplePdf {

        private static final int WIDTH = 283;
        private static final int HEIGHT = 425;
        private static final int MARGIN = 24;
        private static final int LINE_HEIGHT = 20;
        private static final int FONT_SIZE = 12;

        private SimplePdf() {
        }

        static byte[] page(List<String> lines) {
            var content = new StringBuilder("BT /F1 " + FONT_SIZE + " Tf " + MARGIN + " " + (HEIGHT - 2 * MARGIN)
                    + " Td " + LINE_HEIGHT + " TL\n");
            for (var line : lines) {
                content.append('(').append(escape(line)).append(") Tj T*\n");
            }
            content.append("ET");
            var objects = List.of(
                    "<< /Type /Catalog /Pages 2 0 R >>",
                    "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + WIDTH + " " + HEIGHT + "]"
                            + " /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                    "<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream");
            var out = new ByteArrayOutputStream();
            write(out, "%PDF-1.4\n");
            var offsets = new ArrayList<Integer>();
            for (int i = 0; i < objects.size(); i++) {
                offsets.add(out.size());
                write(out, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
            }
            int xref = out.size();
            var table = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
            for (var offset : offsets) {
                table.append(String.format("%010d 00000 n \n", offset));
            }
            write(out, table + "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref
                    + "\n%%EOF\n");
            return out.toByteArray();
        }

        private static String escape(String text) {
            return text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
        }

        private static void write(ByteArrayOutputStream out, String text) {
            out.writeBytes(text.getBytes(StandardCharsets.US_ASCII));
        }
    }
}
