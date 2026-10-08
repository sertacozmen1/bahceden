package com.bahceden.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;

/**
 * Satıcının ödeme hesabı: ödeme sağlayıcısındaki alt üye işyeri kaydının
 * karşılığı (ADR-054, add-cart-and-checkout D2).
 *
 * <p>Satıcı bir kimlik olarak tutulur, ilişki olarak değil: {@code payment}
 * domaini {@code seller} entity'sine kilitlenmez.
 *
 * <p>IBAN ve kimlik numarası kişisel veridir (K10): hiçbir yanıtta tam
 * dönmez, log'a yazılmaz. {@link #toString()} bilinçli olarak tanımlanmadı.
 */
@Entity
@Table(name = "seller_payout_accounts")
public class SellerPayoutAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false, unique = true)
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "legal_type", nullable = false, length = 20)
    private PayoutLegalType legalType;

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Column(name = "tax_id", nullable = false, length = 11)
    private String taxId;

    @Column(name = "tax_office", length = 80)
    private String taxOffice;

    @Column(nullable = false, length = 26)
    private String iban;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayoutAccountStatus status;

    /** Sağlayıcının alt üye anahtarı; reddedilen ilk kayıtta boş. */
    @Column(name = "provider_sub_merchant_key", length = 100)
    private String providerSubMerchantKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SellerPayoutAccount() {
    }

    /** Yeni hesap; durum sağlayıcının yanıtıyla {@link #activate} veya {@link #reject} ile belirlenir. */
    public static SellerPayoutAccount open(long sellerId, Instant now) {
        var account = new SellerPayoutAccount();
        account.sellerId = sellerId;
        account.status = PayoutAccountStatus.PROVIDER_REJECTED;
        account.createdAt = now;
        account.updatedAt = now;
        return account;
    }

    /** Satıcının girdiği bilgiler; değerler doğrulanmış ve normalleştirilmiş gelir. */
    public void update(PayoutLegalType legalType, String legalName, String taxId, String taxOffice, String iban,
                       Instant now) {
        this.legalType = legalType;
        this.legalName = legalName;
        this.taxId = taxId;
        this.taxOffice = taxOffice;
        this.iban = iban;
        this.updatedAt = now;
    }

    public void activate(String subMerchantKey, Instant now) {
        this.providerSubMerchantKey = subMerchantKey;
        this.status = PayoutAccountStatus.ACTIVE;
        this.updatedAt = now;
    }

    /** Sağlayıcı reddetti; önceki alt üye anahtarı (varsa) korunur ama hesap satışa kapanır. */
    public void reject(Instant now) {
        this.status = PayoutAccountStatus.PROVIDER_REJECTED;
        this.updatedAt = now;
    }

    public boolean isActive() {
        return status == PayoutAccountStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public PayoutLegalType getLegalType() {
        return legalType;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getTaxId() {
        return taxId;
    }

    public Optional<String> getTaxOffice() {
        return Optional.ofNullable(taxOffice);
    }

    public String getIban() {
        return iban;
    }

    public PayoutAccountStatus getStatus() {
        return status;
    }

    public Optional<String> getProviderSubMerchantKey() {
        return Optional.ofNullable(providerSubMerchantKey);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
