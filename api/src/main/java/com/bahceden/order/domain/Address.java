package com.bahceden.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;

/**
 * Hesabın kayıtlı teslimat adresi (cart-and-checkout spec'i, D5).
 *
 * <p>Kişisel veridir (K10): yalnızca sahibi görür, log'a yazılmaz.
 * Siparişte adresin kopyası alınır (D6); adres değişse veya silinse de geçmiş
 * sipariş etkilenmez. İl plaka koduyla tutulur.
 */
@Entity
@Table(name = "addresses")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(nullable = false, length = 40)
    private String title;

    @Column(name = "recipient_name", nullable = false, length = 120)
    private String recipientName;

    @Column(nullable = false, length = 16)
    private String phone;

    @Column(name = "province_code", nullable = false, length = 2)
    private String provinceCode;

    @Column(nullable = false, length = 80)
    private String district;

    @Column(name = "address_line", nullable = false, length = 250)
    private String addressLine;

    @Column(name = "postal_code", length = 5)
    private String postalCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Address() {
    }

    public static Address of(long accountId, Instant now) {
        var address = new Address();
        address.accountId = accountId;
        address.createdAt = now;
        return address;
    }

    /** Değerler doğrulanmış ve normalleştirilmiş gelir (telefon E.164). */
    public void update(String title, String recipientName, String phone, String provinceCode, String district,
                       String addressLine, String postalCode, Instant now) {
        this.title = title;
        this.recipientName = recipientName;
        this.phone = phone;
        this.provinceCode = provinceCode;
        this.district = district;
        this.addressLine = addressLine;
        this.postalCode = postalCode;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getTitle() {
        return title;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getPhone() {
        return phone;
    }

    public String getProvinceCode() {
        return provinceCode;
    }

    public String getDistrict() {
        return district;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public Optional<String> getPostalCode() {
        return Optional.ofNullable(postalCode);
    }
}
