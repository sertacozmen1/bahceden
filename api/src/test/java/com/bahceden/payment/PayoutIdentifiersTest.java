package com.bahceden.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Ödeme hesabı kimlik numaralarının doğrulaması ve maskelenmesi
 * (seller-payout-details spec'i, add-cart-and-checkout D2). Değerler sahte ama
 * sağlamaları geçerli örneklerdir (K10).
 */
@Tag("critical")
@DisplayName("PayoutIdentifiers: TCKN, VKN, IBAN doğrulaması ve maskeleme")
class PayoutIdentifiersTest {

    @ParameterizedTest
    @ValueSource(strings = {"10000000078", "12345678950"})
    @DisplayName("Sağlaması tutan TCKN geçerli")
    void gecerli_tckn(String value) {
        assertThat(PayoutIdentifiers.isValidTckn(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"10000000079", "12345678951", "02345678950", "1234567895", "123456789500", "1234567895a"})
    @DisplayName("Sağlaması tutmayan, 0 ile başlayan veya 11 hane olmayan TCKN geçersiz")
    void gecersiz_tckn(String value) {
        assertThat(PayoutIdentifiers.isValidTckn(value)).isFalse();
    }

    @Test
    @DisplayName("VKN yalnızca biçimle denetlenir: 10 hane")
    void vkn_bicimi() {
        assertThat(PayoutIdentifiers.isValidVkn("1234567890")).isTrue();
        assertThat(PayoutIdentifiers.isValidVkn("10000000078")).isFalse();
        assertThat(PayoutIdentifiers.isValidVkn("12345678a0")).isFalse();
    }

    @Test
    @DisplayName("IBAN boşluklu ve küçük harfle yazılabilir; mod-97 sağlaması denetlenir")
    void iban_normallestirme_ve_saglama() {
        var iban = PayoutIdentifiers.normalizeIban(" tr33 0006 1005 1978 6457 8413 26 ");

        assertThat(iban).isEqualTo("TR330006100519786457841326");
        assertThat(PayoutIdentifiers.isValidIban(iban)).isTrue();
        assertThat(PayoutIdentifiers.isValidIban("TR330006100519786457841327")).isFalse();
        assertThat(PayoutIdentifiers.isValidIban("DE89370400440532013000")).isFalse();
        assertThat(PayoutIdentifiers.isValidIban("TR33000610051978645784132")).isFalse();
    }

    @Test
    @DisplayName("Maskeleme yalnızca son dört haneyi (IBAN'da ülke kodunu da) gösterir")
    void maskeleme() {
        assertThat(PayoutIdentifiers.maskIban("TR330006100519786457841326")).isEqualTo("TR** **** **** **** **** **13 26");
        assertThat(PayoutIdentifiers.maskTaxId("10000000078")).isEqualTo("*******0078");
        assertThat(PayoutIdentifiers.maskTaxId("1234567890")).isEqualTo("******7890");
    }
}
