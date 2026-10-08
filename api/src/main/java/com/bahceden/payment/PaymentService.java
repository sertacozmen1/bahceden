package com.bahceden.payment;

import com.bahceden.common.BahcedenProperties;
import com.bahceden.payment.PaymentProvider.PaymentOutcome;
import com.bahceden.payment.PaymentProvider.PaymentRequest;
import com.bahceden.payment.domain.Payment;
import com.bahceden.payment.domain.PaymentStatus;
import com.bahceden.payment.domain.Refund;
import com.bahceden.payment.domain.RefundReason;
import com.bahceden.payment.domain.SellerPayoutAccount;
import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Siparişlerin ödemesi ve iadesi (ADR-054, add-cart-and-checkout D8, D9).
 *
 * <p>{@code payment} domaini siparişi bilmez: sipariş bir kimlik ve sağlayıcıya
 * gidecek istek olarak gelir. Metotlar çağıranın transaction'ına katılır;
 * sağlayıcı hata verirse {@link PaymentProviderException} fırlar ve çağıranın
 * transaction'ı geri alınır, para ile kayıt ayrışmaz.
 */
@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final SellerPayoutAccountRepository accounts;
    private final PaymentProvider provider;
    private final String providerName;
    private final Clock clock;

    PaymentService(PaymentRepository payments, RefundRepository refunds, SellerPayoutAccountRepository accounts,
                   PaymentProvider provider, BahcedenProperties properties, Clock clock) {
        this.payments = payments;
        this.refunds = refunds;
        this.accounts = accounts;
        this.provider = provider;
        this.providerName = properties.payment().provider();
        this.clock = clock;
    }

    /** Verilen satıcıların etkin ödeme hesaplarının alt üye anahtarları, tek sorguda. */
    @Transactional(readOnly = true)
    public Map<Long, String> subMerchantKeys(Collection<Long> sellerIds) {
        return accounts.findActiveBySellerIds(sellerIds).stream()
                .collect(Collectors.toMap(SellerPayoutAccount::getSellerId,
                        account -> account.getProviderSubMerchantKey().orElseThrow()));
    }

    /**
     * Satıcının alt üye anahtarı, hesabın bugünkü durumundan bağımsız: iade,
     * ödeme anındaki satıcı payından yapılır (D9).
     */
    @Transactional(readOnly = true)
    public Optional<String> subMerchantKeyOf(long sellerId) {
        return accounts.findBySellerId(sellerId).flatMap(SellerPayoutAccount::getProviderSubMerchantKey);
    }

    /**
     * Ödemeyi sağlayıcıda başlatır ve kaydeder.
     *
     * @return alıcının yönlendirileceği adres
     */
    @Transactional
    public String start(long orderId, PaymentRequest request) {
        var session = provider.initiate(request);
        payments.save(Payment.initiated(orderId, providerName, session.reference(), session.redirectUrl(),
                request.amount(), request.currency(), clock.instant()));
        return session.redirectUrl();
    }

    /** Aynı siparişin tekrar isteği (Idempotency-Key) için kayıtlı yönlendirme adresi. */
    @Transactional(readOnly = true)
    public Optional<String> redirectUrlOf(long orderId) {
        return payments.findByOrderId(orderId).map(Payment::getRedirectUrl);
    }

    /**
     * Sonucu sağlayıcıdan okur ve kaydeder; bekleyen ödeme kayıtta değişmez.
     * İstemcinin söylediği sonuca güvenilmez (AGENTS.md §11.1).
     */
    @Transactional
    public PaymentOutcome verify(long orderId) {
        var payment = payment(orderId);
        var outcome = provider.verify(payment.getProviderReference());
        switch (outcome) {
            case SUCCEEDED -> payment.settle(PaymentStatus.SUCCEEDED, clock.instant());
            case FAILED -> payment.settle(PaymentStatus.FAILED, clock.instant());
            case PENDING -> {
                // Alıcı ödemeyi henüz tamamlamadı; kayıt INITIATED kalır.
            }
        }
        return outcome;
    }

    /**
     * Sağlayıcıdan iade eder ve iadeyi kaydeder.
     *
     * @param sellerOrderId  bir satıcı payı iade ediliyorsa o satıcı siparişi; ödemenin tamamıysa {@code null}
     * @param subMerchantKey bir satıcı payı iade ediliyorsa o satıcının anahtarı
     */
    @Transactional
    public void refund(long orderId, Long sellerOrderId, RefundReason reason, long amount,
                       Optional<String> subMerchantKey) {
        var payment = payment(orderId);
        var reference = provider.refund(payment.getProviderReference(), amount, subMerchantKey);
        refunds.save(Refund.of(payment.getId(), sellerOrderId, reason, amount, reference, clock.instant()));
    }

    private Payment payment(long orderId) {
        return payments.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("Ödemesi olmayan sipariş: " + orderId));
    }
}
