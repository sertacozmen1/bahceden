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
     * Ayıplı ürün iadesinden sonraki tutarlar (ADR-056 §5, ADR-082). Değerler
     * satıcı siparişine sipariş anında yazılmış oran ve sabit ücrettir.
     *
     * <ul>
     *   <li><b>Tam iade</b> (her satırın satın alınan bütün adetleri): ürün
     *       tutarı ve kargo ücreti iade edilir; komisyon ve hakediş sıfırdır.</li>
     *   <li><b>Kısmi iade</b>: kargo iade edilmez; komisyon kalan ürün tutarı
     *       üzerinden aynı kuralla ({@link #commission(long, int, long)})
     *       yeniden hesaplanır, sabit ücret korunur ve kalan tutarı aşamaz.</li>
     * </ul>
     *
     * @param itemsTotal  satıcı siparişinin ürün tutarı
     * @param shippingFee satıcı siparişinin kargo ücreti
     * @param refunded    iade edilen ürün tutarı: Σ birim fiyat × adet
     * @param full        her satırın bütün adetleri mi iade ediliyor
     */
    public static RefundSettlement afterRefund(long itemsTotal, long shippingFee, long refunded, int rateBp,
                                               long fixedMinor, boolean full) {
        if (refunded <= 0 || refunded > itemsTotal) {
            throw new IllegalArgumentException("İade tutarı ürün tutarının dışında: " + refunded);
        }
        if (full) {
            if (refunded != itemsTotal) {
                throw new IllegalArgumentException("Tam iade ürün tutarının tamamı olmalı");
            }
            return new RefundSettlement(refunded, shippingFee, 0, 0);
        }
        var remaining = commission(itemsTotal - refunded, rateBp, fixedMinor);
        return new RefundSettlement(refunded, 0, remaining.amount(), remaining.sellerPayout());
    }

    /**
     * İadenin sonucu (ADR-082).
     *
     * @param itemsRefund     alıcıya iade edilen ürün tutarı
     * @param shippingRefund  alıcıya iade edilen kargo ücreti; yalnızca tam iadede
     * @param commissionAfter iadeden sonraki komisyon
     * @param payoutAfter     iadeden sonraki satıcı hakedişi
     */
    public record RefundSettlement(long itemsRefund, long shippingRefund, long commissionAfter, long payoutAfter) {

        /** Ödeme sağlayıcısından iade edilecek toplam. */
        public long total() {
            return itemsRefund + shippingRefund;
        }

        public boolean shippingRefunded() {
            return shippingRefund > 0;
        }
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
