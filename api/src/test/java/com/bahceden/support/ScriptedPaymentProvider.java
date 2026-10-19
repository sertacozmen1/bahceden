package com.bahceden.support;

import com.bahceden.payment.PaymentProvider;
import com.bahceden.payment.PaymentProviderException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Testlerin yönettiği ödeme sağlayıcısı (add-cart-and-checkout D8).
 *
 * <p>Uygulamanın sahte sağlayıcısı her şeyi onaylar; reddetme, başarısız ödeme
 * ve iade hatası dalları bu uygulamayla sınanır. Dış dünyaya çıkmaz (K10).
 * Başlatılan ödemeleri ve iadeleri kaydeder, böylece testler satıcı paylarını
 * ve iade tutarlarını doğrulayabilir.
 */
public class ScriptedPaymentProvider implements PaymentProvider {

    /** Yapılan bir iade. */
    public record RecordedRefund(String paymentReference, long amount, Optional<String> subMerchantKey) {
    }

    private final AtomicInteger sequence = new AtomicInteger();
    private final List<PaymentRequest> initiated = new ArrayList<>();
    private final List<RecordedRefund> refunds = new ArrayList<>();
    private boolean rejectSubMerchants;
    private boolean unavailable;
    private boolean refundsFailing;
    private PaymentOutcome outcome = PaymentOutcome.SUCCEEDED;
    private final Set<String> failingVerifies = new HashSet<>();

    @Override
    public synchronized SubMerchantResult registerSubMerchant(SubMerchantDetails details) {
        failIfUnavailable();
        if (rejectSubMerchants) {
            return new SubMerchantResult.Rejected();
        }
        return new SubMerchantResult.Registered(details.existingKey().orElse("test-sub-" + sequence.incrementAndGet()));
    }

    @Override
    public synchronized PaymentSession initiate(PaymentRequest request) {
        failIfUnavailable();
        initiated.add(request);
        return new PaymentSession("test-pay-" + sequence.incrementAndGet(), request.returnUrl());
    }

    @Override
    public synchronized PaymentOutcome verify(String paymentReference) {
        failIfUnavailable();
        if (failingVerifies.contains(paymentReference)) {
            throw new PaymentProviderException("Test: bu ödeme doğrulanamıyor");
        }
        return outcome;
    }

    @Override
    public synchronized String refund(String paymentReference, long amount, Optional<String> subMerchantKey) {
        failIfUnavailable();
        if (refundsFailing) {
            throw new PaymentProviderException("Test: iade reddedildi");
        }
        refunds.add(new RecordedRefund(paymentReference, amount, subMerchantKey));
        return "test-refund-" + sequence.incrementAndGet();
    }

    public synchronized void rejectSubMerchants() {
        rejectSubMerchants = true;
    }

    public synchronized void makeUnavailable() {
        unavailable = true;
    }

    public synchronized void failRefunds() {
        refundsFailing = true;
    }

    /** Yalnızca bu ödemenin doğrulaması hata verir; diğerleri etkilenmez. */
    public synchronized void failVerifyOf(String paymentReference) {
        failingVerifies.add(paymentReference);
    }

    public synchronized void answerVerifyWith(PaymentOutcome next) {
        outcome = next;
    }

    public synchronized List<PaymentRequest> initiated() {
        return List.copyOf(initiated);
    }

    public synchronized List<RecordedRefund> refunds() {
        return List.copyOf(refunds);
    }

    public synchronized void reset() {
        initiated.clear();
        refunds.clear();
        rejectSubMerchants = false;
        unavailable = false;
        refundsFailing = false;
        failingVerifies.clear();
        outcome = PaymentOutcome.SUCCEEDED;
    }

    private void failIfUnavailable() {
        if (unavailable) {
            throw new PaymentProviderException("Test: sağlayıcıya ulaşılamıyor");
        }
    }

    /** Uygulamanın sahte sağlayıcısının yerine geçer. */
    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        ScriptedPaymentProvider scriptedPaymentProvider() {
            return new ScriptedPaymentProvider();
        }
    }
}
