package com.bahceden.payment.domain;

/** Ödemenin sağlayıcıdan doğrulanmış durumu (add-cart-and-checkout D8). */
public enum PaymentStatus {

    /** Başlatıldı; alıcı henüz ödeme sayfasından dönmedi. */
    INITIATED,

    /** Sağlayıcı ödemeyi doğruladı. İadeler ayrı kayıtlardır ({@link Refund}). */
    SUCCEEDED,

    /** Sağlayıcı ödemenin başarısız olduğunu bildirdi. */
    FAILED
}
