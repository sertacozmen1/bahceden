package com.bahceden.payment;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Gerçek sağlayıcı seçilene kadar kullanılan sahte ödeme sağlayıcısı (ADR-054,
 * add-cart-and-checkout D8; KALANLAR A1).
 *
 * <p>Ağ çağrısı yapmaz ve kişisel veriyi hiçbir yere göndermez (K10). Her alt
 * üye kaydını ve her ödemeyi onaylar; yönlendirme adresi doğrudan dönüş
 * adresidir, yani alıcı bir ara ekran görmez. Başarısızlık ve iade hatası
 * dalları testlerde ayrı bir test uygulamasıyla sınanır.
 *
 * <p>Yapılandırmada tek geçerli sağlayıcı adı {@code fake}'tir
 * ({@code bahceden.payment.provider}); başka bir değerle uygulama açılmaz.
 */
@Component
class FakePaymentProvider implements PaymentProvider {

    private static final int REFERENCE_BYTES = 12;
    private static final String PAYMENT_PREFIX = "fake-pay-";

    private final SecureRandom random = new SecureRandom();

    @Override
    public SubMerchantResult registerSubMerchant(SubMerchantDetails details) {
        return new SubMerchantResult.Registered(details.existingKey().orElseGet(() -> "fake-sub-" + randomHex()));
    }

    @Override
    public PaymentSession initiate(PaymentRequest request) {
        return new PaymentSession(PAYMENT_PREFIX + randomHex(), request.returnUrl());
    }

    @Override
    public PaymentOutcome verify(String paymentReference) {
        if (!paymentReference.startsWith(PAYMENT_PREFIX)) {
            throw new PaymentProviderException("Sahte sağlayıcı bu referansı üretmedi");
        }
        return PaymentOutcome.SUCCEEDED;
    }

    @Override
    public String refund(String paymentReference, long amount, Optional<String> subMerchantKey) {
        return "fake-refund-" + randomHex();
    }

    private String randomHex() {
        var bytes = new byte[REFERENCE_BYTES];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
