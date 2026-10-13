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
@DisplayName("OrderPricing: komisyon, yuvarlama, üst sınır ve ayıplı ürün iadesi")
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

    @Test
    @DisplayName("Kısmi iade (ADR-082 örneği): 200 TL'de 50 TL iade; komisyon 10,50, hakediş 139,50, kargo dönmez")
    void kismi_iade() {
        var settlement = OrderPricing.afterRefund(20_000, 9_900, 5_000, RATE_BP, FIXED, false);

        assertThat(settlement.itemsRefund()).isEqualTo(5_000);
        assertThat(settlement.shippingRefund()).isZero();
        assertThat(settlement.total()).isEqualTo(5_000);
        assertThat(settlement.commissionAfter()).isEqualTo(1_050);
        assertThat(settlement.payoutAfter()).isEqualTo(13_950);
        assertThat(settlement.shippingRefunded()).isFalse();
    }

    @Test
    @DisplayName("Tam iade: ürün ve kargo döner; komisyon ve hakediş sıfır")
    void tam_iade() {
        var settlement = OrderPricing.afterRefund(20_000, 9_900, 20_000, RATE_BP, FIXED, true);

        assertThat(settlement.total()).isEqualTo(29_900);
        assertThat(settlement.shippingRefunded()).isTrue();
        assertThat(settlement.commissionAfter()).isZero();
        assertThat(settlement.payoutAfter()).isZero();
    }

    @Test
    @DisplayName("Kısmi iadede komisyon kalan tutarı aşamaz; yarım kuruş yukarı yuvarlanır")
    void kalan_tutar_siniri_ve_yuvarlama() {
        var small = OrderPricing.afterRefund(1_000, 9_900, 900, RATE_BP, FIXED, false);
        assertThat(small.commissionAfter()).isEqualTo(100);
        assertThat(small.payoutAfter()).isZero();

        // Kalan 1010 kuruşun %5'i 50,5 → 51; + 300 sabit.
        var rounded = OrderPricing.afterRefund(2_010, 0, 1_000, RATE_BP, FIXED, false);
        assertThat(rounded.commissionAfter()).isEqualTo(351);
        assertThat(rounded.payoutAfter()).isEqualTo(659);
    }

    @Test
    @DisplayName("Sıfır, ürün tutarını aşan ya da eksik tam iade reddedilir")
    void gecersiz_iade() {
        assertThatThrownBy(() -> OrderPricing.afterRefund(1_000, 0, 0, RATE_BP, FIXED, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OrderPricing.afterRefund(1_000, 0, 1_001, RATE_BP, FIXED, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OrderPricing.afterRefund(1_000, 0, 999, RATE_BP, FIXED, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
