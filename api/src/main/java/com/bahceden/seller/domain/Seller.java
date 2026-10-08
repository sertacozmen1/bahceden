package com.bahceden.seller.domain;

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
 * Ürünü üreten ve gönderen üretici; aynı zamanda satıcı başvurusunun kendisi
 * (ADR-037, design.md D1).
 *
 * <p>Başvuru {@link SellerStatus#PENDING} durumunda açılan bir satıcı
 * kaydıdır; ayrı bir başvuru tablosu yoktur. Durum yalnızca
 * {@link #apply(SellerTransition, String, Instant)} ile, izinli geçişlerden biri
 * üzerinden değişir.
 *
 * <p>Hesap bir kimlik olarak tutulur, ilişki olarak değil: {@code account}
 * domaininin entity'sine bağlanmak iki domaini birbirine kilitlerdi. Seed'den
 * gelen satıcıların hesabı yoktur ({@code accountId} boş).
 *
 * <p>Telefon kişisel veridir (K10): yalnızca admin ve kaydın sahibi görür,
 * vitrine çıkmaz.
 */
@Entity
@Table(name = "sellers")
public class Seller {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Kaydın sahibi olan hesap; seed satıcılarında boş. */
    @Column(name = "account_id", unique = true)
    private Long accountId;

    /** URL'de kullanılan kalıcı, okunabilir tanımlayıcı; başvuruda üretilir ve değişmez. */
    @Column(nullable = false, length = 120, unique = true)
    private String slug;

    /** Vitrinde gösterilen ad. */
    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    /** Ürünün hangi yöreden geldiğini göstermek için. */
    @Column(nullable = false, length = 80)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SellerStatus status;

    /** İletişim telefonu, E.164 biçiminde ({@code +90…}). */
    @Column(length = 16)
    private String phone;

    /** Son ret veya askı gerekçesi; satıcıya gösterilir. */
    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Seller() {
    }

    /**
     * Yeni başvuru: kayıt {@link SellerStatus#PENDING} açılır.
     *
     * @param phone E.164 biçiminde normalleştirilmiş telefon
     */
    public static Seller applyFor(long accountId, String slug, String displayName, String city,
                                  String phone, Instant now) {
        var seller = new Seller();
        seller.accountId = accountId;
        seller.slug = slug;
        seller.displayName = displayName;
        seller.city = city;
        seller.phone = phone;
        seller.status = SellerStatus.PENDING;
        seller.statusChangedAt = now;
        seller.createdAt = now;
        seller.updatedAt = now;
        return seller;
    }

    /** Geçiş bu kaydın şimdiki durumundan yapılabilir mi? */
    public boolean allows(SellerTransition transition) {
        return status == transition.from();
    }

    /**
     * Durumu bir geçişle değiştirir. Çağıran önce {@link #allows} ile kontrol
     * eder; bu metot izinsiz geçişte çağrılırsa programlama hatasıdır.
     *
     * @param reason gerekçe; gerekçe istemeyen geçişte yok sayılır ve önceki
     *               gerekçe temizlenir
     */
    public void apply(SellerTransition transition, String reason, Instant now) {
        if (!allows(transition)) {
            throw new IllegalStateException("İzinsiz satıcı geçişi: " + status + " -> " + transition);
        }
        status = transition.to();
        statusReason = transition.requiresReason() ? reason : null;
        statusChangedAt = now;
        updatedAt = now;
    }

    /** Reddedilen başvurunun yeniden gönderiminde beyan alanları güncellenir; slug değişmez. */
    public void updateDeclaration(String displayName, String city, String phone, Instant now) {
        this.displayName = displayName;
        this.city = city;
        this.phone = phone;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Optional<Long> getAccountId() {
        return Optional.ofNullable(accountId);
    }

    public String getSlug() {
        return slug;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCity() {
        return city;
    }

    public SellerStatus getStatus() {
        return status;
    }

    public Optional<String> getPhone() {
        return Optional.ofNullable(phone);
    }

    public Optional<String> getStatusReason() {
        return Optional.ofNullable(statusReason);
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
