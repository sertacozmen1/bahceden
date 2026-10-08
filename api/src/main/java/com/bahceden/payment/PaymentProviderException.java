package com.bahceden.payment;

/**
 * Ödeme sağlayıcısına ulaşılamadı veya işlem reddedildi (add-cart-and-checkout D8, D9).
 *
 * <p>Çağıran servis bunu {@code PAYMENT_PROVIDER_ERROR} (502) yanıtına çevirir;
 * çağrının içinde bulunduğu transaction geri alınır, böylece para ile kayıt
 * ayrışmaz.
 */
public class PaymentProviderException extends RuntimeException {

    public PaymentProviderException(String message) {
        super(message);
    }

    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
