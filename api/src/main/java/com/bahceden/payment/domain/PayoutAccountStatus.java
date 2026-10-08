package com.bahceden.payment.domain;

/**
 * Satıcının ödeme hesabının durumu (add-cart-and-checkout D2).
 *
 * <p>Yalnızca {@link #ACTIVE} hesap satın alınabilirlik koşulunu sağlar
 * (D1): sağlayıcının reddettiği hesapla ödeme bölünemez.
 */
public enum PayoutAccountStatus {

    /** Sağlayıcıda alt üye işyeri kaydı açık. */
    ACTIVE,

    /** Sağlayıcı kaydı reddetti; satıcı bilgileri düzeltip yeniden gönderir. */
    PROVIDER_REJECTED
}
