package com.bahceden.payment;

import com.bahceden.payment.domain.PayoutLegalType;
import java.util.List;
import java.util.Optional;

/**
 * Ödeme sağlayıcısı (ADR-054, add-cart-and-checkout D8).
 *
 * <p>Arayüz bölünmüş ödeme modelinin ihtiyacı kadardır: alt üye kaydı, ödeme
 * başlatma, sonucu doğrulama ve iade. Sağlayıcı seçilene kadar (KALANLAR A1)
 * tek uygulama {@link FakePaymentProvider}'dır; gerçek uygulama sözleşme
 * imzalanınca ADR ile eklenir.
 *
 * <p>Her metot sağlayıcıya ulaşılamaz veya istek reddedilirse
 * {@link PaymentProviderException} fırlatır; alt üye kaydının reddi ise bir
 * istisna değil, {@link SubMerchantResult.Rejected} sonucudur (satıcı düzeltir).
 */
public interface PaymentProvider {

    SubMerchantResult registerSubMerchant(SubMerchantDetails details);

    /** Ödemeyi başlatır; alıcı {@link PaymentSession#redirectUrl()} adresine gider. */
    PaymentSession initiate(PaymentRequest request);

    /** Ödemenin sonucunu sağlayıcıdan okur; istemcinin söylediğine güvenilmez (§11.1). */
    PaymentOutcome verify(String paymentReference);

    /**
     * Ödemenin bir kısmını veya tamamını iade eder.
     *
     * @param subMerchantKey yalnızca bir satıcı payı iade ediliyorsa o satıcının anahtarı
     * @return sağlayıcının iade referansı
     */
    String refund(String paymentReference, long amount, Optional<String> subMerchantKey);

    /**
     * Alt üye kaydı için gönderilen bilgiler.
     *
     * @param existingKey daha önce açılmış kaydın anahtarı; güncellemede dolu
     */
    record SubMerchantDetails(Optional<String> existingKey, PayoutLegalType legalType, String legalName,
                              String taxId, Optional<String> taxOffice, String iban) {
    }

    /** Alt üye kaydının sonucu. */
    sealed interface SubMerchantResult {

        /** @param subMerchantKey sağlayıcının alt üye anahtarı */
        record Registered(String subMerchantKey) implements SubMerchantResult {
        }

        /** Sağlayıcı bilgileri kabul etmedi. */
        record Rejected() implements SubMerchantResult {
        }
    }

    /**
     * Bölünmüş ödeme isteği.
     *
     * @param orderNumber siparişin iş anahtarı
     * @param amount      alıcının ödeyeceği toplam, kuruş
     * @param returnUrl   ödemeden sonra alıcının döneceği adres
     * @param shares      satıcı payları; toplamları {@code amount}'a eşittir
     */
    record PaymentRequest(String orderNumber, long amount, String currency, String returnUrl,
                          List<PaymentShare> shares) {

        public PaymentRequest {
            shares = List.copyOf(shares);
        }
    }

    /**
     * Bir satıcının payı.
     *
     * @param subMerchantKey satıcının alt üye anahtarı
     * @param amount         alıcının bu satıcı için ödediği tutar (ürünler + kargo), kuruş
     * @param sellerPayout   satıcıya aktarılacak tutar; kalanı pazaryeri payıdır (komisyon + kargo)
     */
    record PaymentShare(String subMerchantKey, long amount, long sellerPayout) {
    }

    /**
     * @param reference   sağlayıcının ödeme referansı
     * @param redirectUrl alıcının gideceği ödeme sayfası
     */
    record PaymentSession(String reference, String redirectUrl) {
    }

    /** Ödemenin sağlayıcıdaki sonucu. */
    enum PaymentOutcome {
        SUCCEEDED,
        FAILED,
        /** Alıcı ödemeyi henüz tamamlamadı. */
        PENDING
    }
}
