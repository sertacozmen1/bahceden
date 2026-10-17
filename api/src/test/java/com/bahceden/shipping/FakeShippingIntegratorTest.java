package com.bahceden.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bahceden.common.BahcedenProperties;
import com.bahceden.common.storage.FileType;
import com.bahceden.shipping.ShippingIntegrator.Recipient;
import com.bahceden.shipping.ShippingIntegrator.Sender;
import com.bahceden.shipping.ShippingIntegrator.ShipmentRequest;
import com.bahceden.shipping.domain.ShipmentStatus;
import com.bahceden.support.MutableClock;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Sahte kargo entegratörü (shipments spec'i "Geliştirme ve testte gönderi
 * dışarı çıkmaz"; add-shipments D4, D5). Spring bağlamı gerekmez.
 */
@DisplayName("Sahte kargo entegratörü")
class FakeShippingIntegratorTest {

    private MutableClock clock;
    private FakeShippingIntegrator integrator;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        var shipping = new BahcedenProperties.Shipping("fake", false, Duration.ofMinutes(1), 10,
                new BahcedenProperties.Shipping.PackageDesi(5, 15, 30),
                new BahcedenProperties.Shipping.FakeIntegrator(Duration.ofMinutes(2), Duration.ofMinutes(4),
                        Duration.ofMinutes(6)));
        var properties = new BahcedenProperties("http://localhost:3000", "bahceden@ornek.test", null, null, null, null,
                shipping, null);
        integrator = new FakeShippingIntegrator(properties, clock);
    }

    @Test
    @DisplayName("Durum açılıştan geçen süreyle ilerler: CREATED → HANDED_OVER → IN_TRANSIT → DELIVERED")
    void durum_zamanla_ilerler() {
        var ref = integrator.create(request()).integratorRef();

        assertThat(integrator.status(ref)).isEqualTo(ShipmentStatus.CREATED);
        clock.advance(Duration.ofMinutes(2));
        assertThat(integrator.status(ref)).isEqualTo(ShipmentStatus.HANDED_OVER);
        clock.advance(Duration.ofMinutes(2));
        assertThat(integrator.status(ref)).isEqualTo(ShipmentStatus.IN_TRANSIT);
        clock.advance(Duration.ofMinutes(2));
        assertThat(integrator.status(ref)).isEqualTo(ShipmentStatus.DELIVERED);
    }

    @Test
    @DisplayName("Etiket kişisel veri taşımayan geçerli bir PDF'tir")
    void etiket_pdf() {
        var created = integrator.create(request());

        var label = integrator.label(created.integratorRef());

        assertThat(FileType.detect(label)).contains(FileType.PDF);
        var text = new String(label, StandardCharsets.US_ASCII);
        assertThat(text).contains("ORNEK ETIKET - GECERSIZ", created.integratorRef()).endsWith("%%EOF\n")
                .doesNotContain("Ornek Alici", "+905550000000");
        assertThat(created.trackingNumber()).matches("FK\\d{10}");
    }

    @Test
    @DisplayName("Kargoya verilmeden iptal edilir; verildikten sonra iptal reddedilir; bilinmeyen anahtar hata")
    void iptal() {
        var first = integrator.create(request()).integratorRef();
        integrator.cancel(first);
        assertThat(integrator.status(first)).isEqualTo(ShipmentStatus.CANCELLED);

        var second = integrator.create(request()).integratorRef();
        clock.advance(Duration.ofMinutes(3));
        assertThatThrownBy(() -> integrator.cancel(second)).isInstanceOf(ShippingIntegratorException.class);
        assertThatThrownBy(() -> integrator.status("baska-anahtar")).isInstanceOf(ShippingIntegratorException.class);
    }

    @Test
    @DisplayName("Süreler sıralı olmalıdır; değilse yapılandırma reddedilir")
    void sureler_sirali() {
        assertThatThrownBy(() -> new BahcedenProperties.Shipping.FakeIntegrator(Duration.ofMinutes(5),
                Duration.ofMinutes(4), Duration.ofMinutes(6))).isInstanceOf(IllegalArgumentException.class);
    }

    private static ShipmentRequest request() {
        return new ShipmentRequest("BHC-1-1", new Sender("Satıcı", "Manisa", "+905550000000"),
                new Recipient("Ornek Alici", "+905550000000", "İzmir", "Bornova", "Ornek Mahallesi No: 1", null), 15);
    }
}
