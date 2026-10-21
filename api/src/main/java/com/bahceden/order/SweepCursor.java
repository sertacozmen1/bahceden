package com.bahceden.order;

import java.time.Instant;

/**
 * Süre işlerinin sayfa imleci: kaydın kimliği ve sıralamadaki süresi
 * (change-deadline-sweeps-and-admin-claim-email D1, ADR-100).
 *
 * <p>Bir tur süresi dolmuş kayıtları (süre, kimlik) sırasıyla dolaşır; sonraki
 * sayfa son görülen kaydın bu değerinden sonrasıyla okunur. Hata veren kayıt
 * değişmeden kalsa da imleç onu geçer, arkadakiler aynı turda işlenir.
 *
 * <p>JPQL kurucu ifadesiyle üretildiği için {@code public}'tir.
 *
 * @param id    kaydın kimliği
 * @param dueAt sıralamadaki süre
 */
public record SweepCursor(long id, Instant dueAt) {
}
