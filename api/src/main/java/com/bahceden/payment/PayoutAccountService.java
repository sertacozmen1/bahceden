package com.bahceden.payment;

import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.common.web.ApiException;
import com.bahceden.payment.PaymentProvider.SubMerchantDetails;
import com.bahceden.payment.PaymentProvider.SubMerchantResult;
import com.bahceden.payment.domain.PayoutLegalType;
import com.bahceden.payment.domain.SellerPayoutAccount;
import com.bahceden.payment.dto.PayoutAccountRequest;
import com.bahceden.payment.dto.PayoutAccountView;
import com.bahceden.seller.SellerService;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Satıcının ödeme hesabı (seller-payout-details spec'i, ADR-054,
 * add-cart-and-checkout D2).
 *
 * <p>Her işlem önce onaylı satıcı kapısından geçer (ADR-044); hesap bu
 * satıcının kimliğiyle sorgulanır (ADR-007).
 *
 * <p>Kayıt ve sağlayıcıdaki alt üye kaydı aynı istekte yapılır. Sağlayıcı
 * reddederse hesap {@code PROVIDER_REJECTED} olarak <strong>kaydedilir</strong>
 * ve istek 422 döner: transaction bu hata için geri alınmaz, satıcının girdiği
 * bilgiler ve "satışa kapalı" durumu saklanır.
 *
 * <p>IBAN ve kimlik numarası log'a yazılmaz (K10); log yalnızca satıcı
 * kimliğini ve sonucu taşır.
 */
@Service
public class PayoutAccountService {

    private static final Logger log = LoggerFactory.getLogger(PayoutAccountService.class);

    private final SellerPayoutAccountRepository accounts;
    private final SellerService sellers;
    private final PaymentProvider provider;
    private final Clock clock;

    PayoutAccountService(SellerPayoutAccountRepository accounts, SellerService sellers, PaymentProvider provider,
                         Clock clock) {
        this.accounts = accounts;
        this.sellers = sellers;
        this.provider = provider;
        this.clock = clock;
    }

    /**
     * @throws ApiException {@code PAYOUT_ACCOUNT_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public PayoutAccountView findOwn(long accountId) {
        var seller = sellers.requireApprovedSeller(accountId);
        return accounts.findBySellerId(seller.getId())
                .map(PayoutAccountService::toView)
                .orElseThrow(() -> new ApiException(ApiErrorCode.PAYOUT_ACCOUNT_NOT_FOUND, "error.payoutAccountNotFound"));
    }

    /**
     * Ödeme bilgilerini kaydeder veya günceller ve sağlayıcıda alt üye kaydını
     * açar ya da günceller.
     *
     * @throws ApiException alan bazlı {@code VALIDATION_FAILED}, {@code PAYOUT_ACCOUNT_REJECTED}
     *                      veya {@code PAYMENT_PROVIDER_ERROR}
     */
    @Transactional(noRollbackFor = PayoutRejected.class)
    public PayoutAccountView save(long accountId, PayoutAccountRequest request) {
        var seller = sellers.requireApprovedSeller(accountId);
        var legalType = PayoutLegalType.valueOf(request.legalType());
        var taxId = request.taxId().trim();
        checkTaxId(legalType, taxId);
        var taxOffice = Optional.ofNullable(request.taxOffice()).map(String::trim).filter(value -> !value.isEmpty());
        if (legalType.requiresTaxOffice() && taxOffice.isEmpty()) {
            throw ApiException.invalidField("taxOffice", "validation.taxOffice.required");
        }
        var iban = PayoutIdentifiers.normalizeIban(request.iban());
        if (!PayoutIdentifiers.isValidIban(iban)) {
            throw ApiException.invalidField("iban", "validation.iban.invalid");
        }

        var now = clock.instant();
        var account = accounts.findBySellerId(seller.getId()).orElseGet(() -> SellerPayoutAccount.open(seller.getId(), now));
        var legalName = request.legalName().trim();
        account.update(legalType, legalName, taxId, legalType.requiresTaxOffice() ? taxOffice.orElseThrow() : null,
                iban, now);

        SubMerchantResult result;
        try {
            result = provider.registerSubMerchant(new SubMerchantDetails(account.getProviderSubMerchantKey(),
                    legalType, legalName, taxId, taxOffice.filter(value -> legalType.requiresTaxOffice()), iban));
        } catch (PaymentProviderException cause) {
            log.warn("Alt üye kaydı yapılamadı: satıcı {}", seller.getId(), cause);
            throw new ApiException(ApiErrorCode.PAYMENT_PROVIDER_ERROR, "error.paymentProvider");
        }
        switch (result) {
            case SubMerchantResult.Registered registered -> account.activate(registered.subMerchantKey(), now);
            case SubMerchantResult.Rejected rejected -> account.reject(now);
        }
        accounts.save(account);
        log.info("Ödeme hesabı kaydedildi: satıcı {}, durum {}", seller.getId(), account.getStatus());
        if (!account.isActive()) {
            throw new PayoutRejected();
        }
        return toView(account);
    }

    private static void checkTaxId(PayoutLegalType legalType, String taxId) {
        switch (legalType.taxIdKind()) {
            case TCKN -> {
                if (!PayoutIdentifiers.isValidTckn(taxId)) {
                    throw ApiException.invalidField("taxId", "validation.taxId.tckn");
                }
            }
            case VKN -> {
                if (!PayoutIdentifiers.isValidVkn(taxId)) {
                    throw ApiException.invalidField("taxId", "validation.taxId.vkn");
                }
            }
        }
    }

    private static PayoutAccountView toView(SellerPayoutAccount account) {
        return new PayoutAccountView(account.getLegalType(), account.getLegalName(),
                PayoutIdentifiers.maskTaxId(account.getTaxId()), account.getTaxOffice().orElse(null),
                PayoutIdentifiers.maskIban(account.getIban()), account.getStatus(), account.getUpdatedAt());
    }

    /** Sağlayıcı reddi: kayıt saklanır, istek 422 döner (transaction geri alınmaz). */
    static final class PayoutRejected extends ApiException {

        PayoutRejected() {
            super(ApiErrorCode.PAYOUT_ACCOUNT_REJECTED, "error.payoutAccountRejected");
        }
    }
}
