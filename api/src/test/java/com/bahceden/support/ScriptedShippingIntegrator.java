package com.bahceden.support;

import com.bahceden.shipping.ShippingIntegrator;
import com.bahceden.shipping.ShippingIntegratorException;
import com.bahceden.shipping.domain.ShipmentStatus;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Testlerin yönettiği kargo entegratörü (add-shipments D5).
 *
 * <p>Uygulamanın sahte entegratörü durumu zamanla ilerletir ve hiç hata
 * vermez; "teslim edilemedi", ara durum atlama, geri giden bildirim ve hata
 * dalları bu uygulamayla sınanır. Dış dünyaya çıkmaz (K10). Gönderi
 * isteklerini ve iptalleri kaydeder. Gerçek entegratör gibi kargoya verilmiş
 * gönderinin iptalini reddeder.
 */
public class ScriptedShippingIntegrator implements ShippingIntegrator {

    /** Testin kullandığı en küçük geçerli PDF imzası. */
    public static final byte[] PDF_LABEL = "%PDF-1.4\n% test etiketi\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    private final AtomicInteger sequence = new AtomicInteger();
    private final List<ShipmentRequest> created = new ArrayList<>();
    private final List<String> cancelled = new ArrayList<>();
    private final Map<String, ShipmentStatus> statuses = new HashMap<>();
    private final Set<String> failingStatus = new HashSet<>();
    private boolean unavailable;
    private boolean cancelRefused;
    private byte[] label = PDF_LABEL;

    @Override
    public synchronized CreatedShipment create(ShipmentRequest request) {
        failIfUnavailable();
        created.add(request);
        var n = sequence.incrementAndGet();
        var ref = "test-shp-" + n;
        statuses.put(ref, ShipmentStatus.CREATED);
        return new CreatedShipment(ref, "Test Kargo", "TK" + String.format("%08d", n));
    }

    @Override
    public synchronized byte[] label(String integratorRef) {
        failIfUnavailable();
        return label.clone();
    }

    @Override
    public synchronized ShipmentStatus status(String integratorRef) {
        failIfUnavailable();
        if (failingStatus.contains(integratorRef)) {
            throw new ShippingIntegratorException("Test: durum alınamadı");
        }
        return statuses.getOrDefault(integratorRef, ShipmentStatus.CREATED);
    }

    @Override
    public synchronized void cancel(String integratorRef) {
        failIfUnavailable();
        if (cancelRefused || statuses.getOrDefault(integratorRef, ShipmentStatus.CREATED) != ShipmentStatus.CREATED) {
            throw new ShippingIntegratorException("Test: iptal reddedildi");
        }
        cancelled.add(integratorRef);
        statuses.put(integratorRef, ShipmentStatus.CANCELLED);
    }

    /** Entegratörün bir sonraki sorguda bildireceği durum. */
    public synchronized void report(String integratorRef, ShipmentStatus status) {
        statuses.put(integratorRef, status);
    }

    public synchronized void failStatusOf(String integratorRef) {
        failingStatus.add(integratorRef);
    }

    public synchronized void makeUnavailable() {
        unavailable = true;
    }

    public synchronized void refuseCancels() {
        cancelRefused = true;
    }

    public synchronized void answerLabelWith(byte[] next) {
        label = next.clone();
    }

    public synchronized List<ShipmentRequest> created() {
        return List.copyOf(created);
    }

    public synchronized List<String> cancelled() {
        return List.copyOf(cancelled);
    }

    public synchronized void reset() {
        sequence.set(0);
        created.clear();
        cancelled.clear();
        statuses.clear();
        failingStatus.clear();
        unavailable = false;
        cancelRefused = false;
        label = PDF_LABEL;
    }

    private void failIfUnavailable() {
        if (unavailable) {
            throw new ShippingIntegratorException("Test: entegratöre ulaşılamıyor");
        }
    }

    /** Uygulamanın sahte entegratörünün yerine geçer. */
    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        ScriptedShippingIntegrator scriptedShippingIntegrator() {
            return new ScriptedShippingIntegrator();
        }
    }
}
