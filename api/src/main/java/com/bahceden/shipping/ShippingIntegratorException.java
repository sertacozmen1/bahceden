package com.bahceden.shipping;

/**
 * Kargo entegratörüne ulaşılamadı veya işlem reddedildi (add-shipments D3, D6).
 *
 * <p>Çağıran servis bunu {@code SHIPPING_INTEGRATOR_ERROR} (502) yanıtına
 * çevirir; çağrının içinde bulunduğu transaction geri alınır.
 */
public class ShippingIntegratorException extends RuntimeException {

    public ShippingIntegratorException(String message) {
        super(message);
    }

    public ShippingIntegratorException(String message, Throwable cause) {
        super(message, cause);
    }
}
