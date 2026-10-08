package com.bahceden.common.time;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * İş günü hesabı (add-cart-and-checkout D3): Türkiye saatiyle, cumartesi ve
 * pazar atlanır. Resmi tatiller bilinçli olarak hesaba katılmaz; tatil
 * takvimi ayrı bir karar konusudur.
 */
public final class BusinessDays {

    /** Kargoya verme süreleri Türkiye saatiyle sayılır. */
    public static final ZoneId TURKEY = ZoneId.of("Europe/Istanbul");

    private BusinessDays() {
    }

    /**
     * {@code start} anının yerel gününden sonraki {@code days}. iş gününün
     * sonu (23:59:59). Örnek: cuma ödenen 2 iş günlük sipariş → salı 23:59:59.
     *
     * @param days en az 1
     */
    public static Instant endOfBusinessDay(Instant start, int days) {
        if (days < 1) {
            throw new IllegalArgumentException("İş günü sayısı en az 1 olmalıdır: " + days);
        }
        var date = start.atZone(TURKEY).toLocalDate();
        int counted = 0;
        while (counted < days) {
            date = date.plusDays(1);
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                counted++;
            }
        }
        return date.atTime(LocalTime.of(23, 59, 59)).atZone(TURKEY).toInstant();
    }
}
