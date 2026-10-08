package com.bahceden.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Komisyon hesabı (ADR-055, add-cart-and-checkout D7). Para kuralı olduğu için
 * {@code critical}; yapılandırmaya bağlı kargo ücreti sepet testlerinde
 * uygulama yapılandırmasıyla sınanır.
 */
@Tag("critical")
@DisplayName("OrderPricing: komisyon, yuvarlama ve üst sınır")
class OrderPricingTest {

    private static final int RATE_BP = 500;
    private static final long FIXED = 300;

    @Test
    @DisplayName("245,50 TL: komisyon 12,28 + 3 = 15,28 TL; hakediş 230,22 TL (spec örneği)")
    void spec_ornegi() {
        var commission = OrderPricing.commission(24_550, RATE_BP, FIXED);

        assertThat(commission.amount()).isEqualTo(1_528);
        assertThat(commission.sellerPayout()).isEqualTo(23_022);
        assertThat(commission.rateBp()).isEqualTo(RATE_BP);
        assertThat(commission.fixedMinor()).isEqualTo(FIXED);
    }

    @Test
    @DisplayName("Yarım kuruş yukarı yuvarlanır, altı aşağı")
    void yarim_yukari_yuvarlama() {
        // 10 kuruşun %5'i = 0,5 kuruş → 1; 9 kuruşun %5'i = 0,45 kuruş → 0.
        assertThat(OrderPricing.commission(10, RATE_BP, 0).amount()).isEqualTo(1);
        assertThat(OrderPricing.commission(9, RATE_BP, 0).amount()).isZero();
        // 30 kuruşun %5'i = 1,5 kuruş → 2.
        assertThat(OrderPricing.commission(30, RATE_BP, 0).amount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Komisyon ürün tutarını aşamaz: 2 TL'lik siparişte komisyon 2 TL, hakediş sıfır")
    void ust_sinir() {
        var commission = OrderPricing.commission(200, RATE_BP, FIXED);

        assertThat(commission.amount()).isEqualTo(200);
        assertThat(commission.sellerPayout()).isZero();
    }

    @Test
    @DisplayName("Büyük tutar long içinde kalır: 99 adet × 50 satır × 100.000 TL")
    void buyuk_tutar() {
        long itemsTotal = 10_000_000L * 99 * 50;

        var commission = OrderPricing.commission(itemsTotal, RATE_BP, FIXED);

        assertThat(commission.amount()).isEqualTo(itemsTotal / 20 + FIXED);
        assertThat(commission.sellerPayout()).isEqualTo(itemsTotal - commission.amount());
    }

    @Test
    @DisplayName("Negatif tutar reddedilir")
    void negatif_tutar() {
        assertThatThrownBy(() -> OrderPricing.commission(-1, RATE_BP, FIXED))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
