package com.bahceden.order.domain;

import java.util.Set;

/**
 * Talebin izinli geçişleri, tek yerde (ADR-080; {@code SellerOrderTransition}
 * ile aynı desen). Kimin hangi geçişi yapabileceği talep servisinde denetlenir;
 * burada yalnızca geçişin hangi durumlardan yapılabileceği tutulur.
 */
public enum DefectClaimTransition {

    /** Satıcı istenen adetlerin tamamını kabul etti; iade yapılır. */
    SELLER_ACCEPT(Set.of(DefectClaimStatus.OPEN), DefectClaimStatus.REFUNDED),

    /** Satıcı daha az adet önerdi; alıcının yanıtı beklenir. */
    SELLER_OFFER(Set.of(DefectClaimStatus.OPEN), DefectClaimStatus.OFFERED),

    /** Satıcı gerekçeyle reddetti; alıcının yanıtı beklenir. */
    SELLER_REJECT(Set.of(DefectClaimStatus.OPEN), DefectClaimStatus.REJECTED),

    /** Alıcı teklifi kabul etti; teklif edilen adetler iade edilir. */
    BUYER_ACCEPT_OFFER(Set.of(DefectClaimStatus.OFFERED), DefectClaimStatus.REFUNDED),

    /** Alıcı reddi kabul etti; talep iadesiz kapanır. */
    BUYER_ACCEPT_REJECTION(Set.of(DefectClaimStatus.REJECTED), DefectClaimStatus.CLOSED),

    /** Alıcı teklifi veya reddi yönetime taşıdı. */
    BUYER_ESCALATE(Set.of(DefectClaimStatus.OFFERED, DefectClaimStatus.REJECTED), DefectClaimStatus.ESCALATED),

    /** Süre işi: satıcı süresinde yanıt vermedi, talep yönetime düşer (ADR-083). */
    DEADLINE_ESCALATE(Set.of(DefectClaimStatus.OPEN), DefectClaimStatus.ESCALATED),

    /** Süre işi: alıcı teklife yanıt vermedi, teklif uygulanır (ADR-083). */
    DEADLINE_ACCEPT_OFFER(Set.of(DefectClaimStatus.OFFERED), DefectClaimStatus.REFUNDED),

    /** Süre işi: alıcı redde yanıt vermedi, talep kapanır (ADR-083). */
    DEADLINE_CLOSE(Set.of(DefectClaimStatus.REJECTED), DefectClaimStatus.CLOSED),

    /** Admin satır başına adetle iade etti (ADR-080). */
    ADMIN_REFUND(Set.of(DefectClaimStatus.ESCALATED), DefectClaimStatus.REFUNDED),

    /** Admin gerekçeyle reddetti; talep iadesiz kapanır (ADR-080). */
    ADMIN_REJECT(Set.of(DefectClaimStatus.ESCALATED), DefectClaimStatus.CLOSED);

    private final Set<DefectClaimStatus> from;
    private final DefectClaimStatus to;

    DefectClaimTransition(Set<DefectClaimStatus> from, DefectClaimStatus to) {
        this.from = from;
        this.to = to;
    }

    public boolean allowsFrom(DefectClaimStatus status) {
        return from.contains(status);
    }

    public DefectClaimStatus to() {
        return to;
    }
}
