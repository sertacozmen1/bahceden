package com.bahceden.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Para temsilinin değişmezleri (ADR-006).
 *
 * <p>Para davranışı her zaman {@code critical}: yanlış bir tutar doğrudan
 * ticari zarardır (AGENTS.md §6.3).
 */
@Tag("critical")
@DisplayName("Money (ADR-006): minor unit tamsayı + açık para birimi")
class MoneyTest {

    private static final Currency TRY = Currency.getInstance("TRY");

    @Test
    @DisplayName("Tutar kuruş olarak saklanır ve olduğu gibi okunur")
    void tutar_kurus_olarak_saklanir() {
        var price = Money.of(12550, TRY);

        assertThat(price.amountInMinorUnits()).isEqualTo(12550L);
        assertThat(price.currencyCode()).isEqualTo("TRY");
        assertThat(price.currency()).isEqualTo(TRY);
    }

    @Test
    @DisplayName("Sıfır tutar geçerlidir")
    void sifir_tutar_gecerlidir() {
        assertThat(Money.of(0, TRY).amountInMinorUnits()).isZero();
    }

    @Test
    @DisplayName("Negatif tutar reddedilir: geçersiz durum temsil edilemez")
    void negatif_tutar_reddedilir() {
        assertThatThrownBy(() -> Money.of(-1, TRY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negatif");
    }

    @Test
    @DisplayName("Para birimi zorunludur")
    void para_birimi_zorunludur() {
        assertThatThrownBy(() -> Money.of(100, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Para birimi");
    }

    @Test
    @DisplayName("Eşitlik tutar ve para birimi üzerinden kurulur")
    void esitlik_tutar_ve_para_birimi_uzerinden() {
        assertThat(Money.of(12550, TRY)).isEqualTo(Money.of(12550, TRY));
        assertThat(Money.of(12550, TRY)).hasSameHashCodeAs(Money.of(12550, TRY));
        assertThat(Money.of(12550, TRY)).isNotEqualTo(Money.of(12551, TRY));
        assertThat(Money.of(12550, TRY)).isNotEqualTo(Money.of(12550, Currency.getInstance("EUR")));
    }
}
