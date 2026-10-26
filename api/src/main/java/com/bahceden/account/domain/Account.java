package com.bahceden.account.domain;

import com.bahceden.common.i18n.SupportedLocale;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Kullanıcı hesabı (ADR-008 §3).
 *
 * <p>Tek tablo, roller küme olarak: aynı kişi hem alıcı hem satıcı olabilir,
 * ikinci hesap açmaya gerek yok.
 *
 * <p>Parola yalnızca hash olarak taşınır; bu sınıf düz parolayı hiçbir zaman
 * görmez. Hash'leme {@code PasswordHasher} içindedir (AGENTS.md §11.6).
 *
 * <p>{@code emailVerified} bir <strong>bilgidir</strong>, giriş koşulu değildir
 * (ADR-017). {@code twoFactorEnabled} e-posta ile ikinci faktörü açar;
 * varsayılan kapalıdır ve ürün yüzeyinden değiştirilemez.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 320, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled;

    /** Hesaba giden e-postaların dili; etiket olarak saklanır ({@code tr}, {@code en}) (ADR-032). */
    @Column(nullable = false, length = 8)
    private String locale;

    /**
     * Roller ayrı bir entity değil değer kümesidir: kendi kimliği, yaşam
     * döngüsü veya davranışı yok. Hesapla birlikte yüklenir çünkü yetkilendirme
     * kararı her zaman rollere ihtiyaç duyar.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "account_roles", joinColumns = @JoinColumn(name = "account_id"))
    @Column(name = "role", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private Set<AccountRole> roles = EnumSet.noneOf(AccountRole.class);

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Account() {
    }

    /**
     * Yeni hesap: etkin durumda, doğrulanmamış e-posta ile, ikinci faktör
     * kapalı (ADR-017).
     *
     * @param email        normalleştirilmiş (küçük harf, kırpılmış) e-posta
     * @param passwordHash hash'lenmiş parola; düz parola bu sınıfa girmez
     * @param locale       kayıt isteğinin dili; e-postalar bu dilde gider (ADR-032)
     * @param now          enjekte edilen saatten gelen zaman (AGENTS.md §11.3)
     */
    public static Account register(String email, String passwordHash, SupportedLocale locale, Instant now) {
        var account = new Account();
        account.email = email;
        account.passwordHash = passwordHash;
        account.locale = locale.tag();
        account.status = AccountStatus.ACTIVE;
        account.emailVerified = false;
        account.twoFactorEnabled = false;
        account.roles = EnumSet.of(AccountRole.ROLE_CUSTOMER);
        account.createdAt = now;
        account.updatedAt = now;
        return account;
    }

    /**
     * E-posta normalleştirme tek yerde: küçük harf ve kırpma. Tekil kısıt bu
     * sayede gerçekten tekilliği garanti eder; {@code Ali@x} ve {@code ali@x}
     * iki hesap açamaz. Kayıt da giriş de aramadan önce bunu çağırır.
     */
    public static String normalizeEmail(String rawEmail) {
        return rawEmail.trim().toLowerCase(Locale.ROOT);
    }

    /** Doğrulama bağlantısı kullanıldığında çağrılır; idempotenttir. */
    public void markEmailVerified(Instant now) {
        if (!emailVerified) {
            emailVerified = true;
            updatedAt = now;
        }
    }

    /**
     * Parolayı değiştirir (add-password-reset design.md D3).
     *
     * @param newPasswordHash hash'lenmiş yeni parola; düz parola bu sınıfa girmez
     */
    public void changePassword(String newPasswordHash, Instant now) {
        passwordHash = newPasswordHash;
        updatedAt = now;
    }

    /**
     * İkinci adımı açar veya kapatır (ADR-103). Kurallar (parola, doğrulanmış
     * e-posta) servistedir; değer değişmediyse kayıt güncellenmez.
     *
     * @return değer gerçekten değişti mi
     */
    public boolean useTwoFactor(boolean enabled, Instant now) {
        if (twoFactorEnabled == enabled) {
            return false;
        }
        twoFactorEnabled = enabled;
        updatedAt = now;
        return true;
    }

    /**
     * Kullanıcının en son kullandığı dil: başarılı girişte çağrılır (ADR-032).
     * Dil değişmediyse kayıt güncellenmez.
     */
    public void useLocale(SupportedLocale newLocale, Instant now) {
        if (!newLocale.tag().equals(locale)) {
            locale = newLocale.tag();
            updatedAt = now;
        }
    }

    /**
     * Rol ekler; zaten varsa değişmez. Satıcı onayı ve askıdan çıkarma
     * {@link AccountRole#ROLE_SELLER} ekler (add-seller-onboarding D5). Rol
     * erişim token'ına bir sonraki yenilemede girer.
     */
    public void grantRole(AccountRole role, Instant now) {
        if (roles.add(role)) {
            updatedAt = now;
        }
    }

    /** Rol kaldırır; yoksa değişmez. Satıcı askısı {@link AccountRole#ROLE_SELLER} kaldırır (D5). */
    public void revokeRole(AccountRole role, Instant now) {
        if (roles.remove(role)) {
            updatedAt = now;
        }
    }

    public SupportedLocale getLocale() {
        return SupportedLocale.fromTag(locale);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public boolean isTwoFactorEnabled() {
        return twoFactorEnabled;
    }

    public Set<AccountRole> getRoles() {
        return Set.copyOf(roles);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
