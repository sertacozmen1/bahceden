package com.bahceden.common.money;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Currency;
import java.util.Objects;

/**
 * Para tutarı: minor unit (kuruş) tamsayı + açık para birimi (ADR-006).
 *
 * <p>Kayan noktalı sayı ile para tutulmaz: {@code 0.1 + 0.2 != 0.3} ve kuruş
 * kaybı sipariş sayısıyla birlikte büyür, mutabakat tutmaz. Bu yüzden tutar
 * {@code long} kuruştur ve yanında her zaman açık bir para birimi taşır.
 *
 * <p>Değişmezdir (immutable) ve geçersiz durumu temsil edemez: negatif tutar ve
 * eksik para birimi yapıcıda reddedilir.
 *
 * <p>JPA için {@code @Embeddable}: tutar ve para birimi ürünün kendi satırında
 * iki kolon olarak yaşar, ayrı tablo yoktur.
 */
@Embeddable
public class Money {

    @Column(name = "price_amount", nullable = false)
    private long amountInMinorUnits;

    @Column(name = "price_currency", nullable = false, length = 3)
    private String currencyCode;

    /** JPA için gerekli; uygulama kodu {@link #of(long, Currency)} kullanır. */
    protected Money() {
    }

    private Money(long amountInMinorUnits, String currencyCode) {
        if (amountInMinorUnits < 0) {
            throw new IllegalArgumentException("Para tutarı negatif olamaz: " + amountInMinorUnits);
        }
        this.amountInMinorUnits = amountInMinorUnits;
        this.currencyCode = Objects.requireNonNull(currencyCode, "Para birimi zorunludur");
    }

    /**
     * @param amountInMinorUnits tutar, kuruş cinsinden (12550 = 125,50)
     * @param currency           para birimi; {@code null} olamaz
     */
    public static Money of(long amountInMinorUnits, Currency currency) {
        Objects.requireNonNull(currency, "Para birimi zorunludur");
        return new Money(amountInMinorUnits, currency.getCurrencyCode());
    }

    public long amountInMinorUnits() {
        return amountInMinorUnits;
    }

    public Currency currency() {
        return Currency.getInstance(currencyCode);
    }

    public String currencyCode() {
        return currencyCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Money money)) {
            return false;
        }
        return amountInMinorUnits == money.amountInMinorUnits
                && Objects.equals(currencyCode, money.currencyCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amountInMinorUnits, currencyCode);
    }

    @Override
    public String toString() {
        return amountInMinorUnits + " " + currencyCode;
    }
}
