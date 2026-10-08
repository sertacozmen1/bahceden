package com.bahceden.order;

import com.bahceden.common.BahcedenProperties;
import org.springframework.stereotype.Component;

/**
 * Sepet ve siparişin para kuralları, tek yerde (add-cart-and-checkout D7).
 *
 * <p>Kargo ücreti ADR-058'e, komisyon ADR-055'e göre hesaplanır. Bütün tutarlar
 * kuruş cinsinden {@code long}'dur (ADR-006); kayan nokta kullanılmaz.
 * Sepet ve sipariş aynı sınıfı kullandığı için ekrandaki toplam ile alınan
 * para ayrışamaz. Değerler yapılandırmadan gelir ve sipariş anında satıcı
 * siparişine yazılır.
 */
@Component
public class OrderPricing {

    /** Baz puan paydası: %100 = 10 000 bp. */
    private static final long BASIS_POINTS = 10_000;

    /** Yarım yukarı yuvarlama için eklenen yarı payda. */
    private static final long HALF_BASIS_POINTS = BASIS_POINTS / 2;

    private final BahcedenProperties.Commerce commerce;

    OrderPricing(BahcedenProperties properties) {
        this.commerce = properties.commerce();
    }

    /**
     * Bir satıcı grubunun kargo ücreti: ürün tutarı eşik ve üzerindeyse sıfır.
     * Boş grup (hiç satın alınabilir satır yok) kargo ücreti doğurmaz.
     */
    public long shippingFee(long itemsTotal) {
        if (itemsTotal == 0 || itemsTotal >= commerce.freeShippingThresholdMinor()) {
            return 0;
        }
        return commerce.shippingFeeMinor();
    }

    /** Ücretsiz kargoya kalan tutar; eşik aşıldıysa sıfır. */
    public long freeShippingRemaining(long itemsTotal) {
        return Math.max(0, commerce.freeShippingThresholdMinor() - itemsTotal);
    }

    /** Yapılandırmadaki oran ve sabit ücretle komisyon. */
    public Commission commission(long itemsTotal) {
        return commission(itemsTotal, commerce.commissionRateBp(), commerce.commissionFixedMinor());
    }

    /**
     * Komisyon: {@code min(ürün tutarı, yuvarla(ürün tutarı × oran) + sabit)}.
     * Yuvarlama kuruşa ve yarım yukarıdır; tutar negatif olmadığı için tamsayı
     * aritmetiğiyle {@code (tutar × bp + 5000) / 10000} aynı sonucu verir.
     * Satıcı hakedişi negatif olamaz (ADR-055 §3).
     */
    static Commission commission(long itemsTotal, int rateBp, long fixedMinor) {
        if (itemsTotal < 0) {
            throw new IllegalArgumentException("Ürün tutarı negatif olamaz: " + itemsTotal);
        }
        long percentPart = (itemsTotal * rateBp + HALF_BASIS_POINTS) / BASIS_POINTS;
        long amount = Math.min(itemsTotal, percentPart + fixedMinor);
        return new Commission(rateBp, fixedMinor, amount, itemsTotal - amount);
    }

    /**
     * Bir satıcı siparişinin komisyonu ve satıcı hakedişi.
     *
     * @param rateBp       uygulanan oran, baz puan
     * @param fixedMinor   uygulanan sabit ücret, kuruş
     * @param amount       komisyon, kuruş
     * @param sellerPayout satıcıya aktarılacak tutar: ürün tutarı − komisyon
     */
    public record Commission(int rateBp, long fixedMinor, long amount, long sellerPayout) {
    }
}
