package com.bahceden.common.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Kargoya verme son tarihi (orders spec'i "Kargoya verme son tarihi
 * gösterilir", add-cart-and-checkout D3). Türkiye saati UTC+3'tür.
 */
@Tag("critical")
@DisplayName("BusinessDays: iş günü sonu, hafta sonu atlanır")
class BusinessDaysTest {

    @Test
    @DisplayName("Cuma ödenen 2 iş günlük sipariş: salı 23:59:59 (Türkiye saati)")
    void cuma_arti_iki() {
        // 2026-10-09 cuma 14:00 Türkiye saati.
        var paid = Instant.parse("2026-10-09T11:00:00Z");

        assertThat(BusinessDays.endOfBusinessDay(paid, 2)).isEqualTo(Instant.parse("2026-10-13T20:59:59Z"));
    }

    @Test
    @DisplayName("Pazartesi + 1 → salı; cumartesi + 1 → pazartesi; cuma gece yarısına yakın UTC anı Türkiye gününe göre sayılır")
    void sinir_durumlari() {
        assertThat(BusinessDays.endOfBusinessDay(Instant.parse("2026-10-12T09:00:00Z"), 1))
                .isEqualTo(Instant.parse("2026-10-13T20:59:59Z"));
        assertThat(BusinessDays.endOfBusinessDay(Instant.parse("2026-10-10T09:00:00Z"), 1))
                .isEqualTo(Instant.parse("2026-10-12T20:59:59Z"));
        // 2026-10-09 22:30 UTC = cumartesi 01:30 Türkiye saati: sayım cumartesiden başlar.
        assertThat(BusinessDays.endOfBusinessDay(Instant.parse("2026-10-09T22:30:00Z"), 1))
                .isEqualTo(Instant.parse("2026-10-12T20:59:59Z"));
    }

    @Test
    @DisplayName("Sıfır iş günü reddedilir")
    void sifir_reddedilir() {
        assertThatThrownBy(() -> BusinessDays.endOfBusinessDay(Instant.now(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
