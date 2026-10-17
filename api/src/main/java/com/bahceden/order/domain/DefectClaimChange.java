package com.bahceden.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Talebin bir durum geçişi: kim, ne zaman, hangi durumdan hangisine, neden
 * (ADR-080). Yalnızca eklenir; eylemci boşsa geçişi süre işi yapmıştır (ADR-083).
 */
@Entity
@Table(name = "defect_claim_changes")
public class DefectClaimChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false, updatable = false)
    private Long claimId;

    /** Talep açılırken boş: talep bu geçişle doğar. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20, updatable = false)
    private DefectClaimStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20, updatable = false)
    private DefectClaimStatus toStatus;

    @Column(length = 500, updatable = false)
    private String reason;

    @Column(name = "actor_account_id", updatable = false)
    private Long actorAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DefectClaimChange() {
    }

    /** Bir hesabın yaptığı geçiş. */
    public static DefectClaimChange byAccount(long claimId, DefectClaimStatus fromStatus, DefectClaimStatus toStatus,
                                              String reason, long actorAccountId, Instant now) {
        var change = new DefectClaimChange();
        change.claimId = claimId;
        change.fromStatus = fromStatus;
        change.toStatus = toStatus;
        change.reason = reason;
        change.actorAccountId = actorAccountId;
        change.createdAt = now;
        change.updatedAt = now;
        return change;
    }

    /** Süre işinin yaptığı geçiş: eylemci hesap yoktur (ADR-083). */
    public static DefectClaimChange byDeadline(long claimId, DefectClaimStatus fromStatus, DefectClaimStatus toStatus,
                                               Instant now) {
        var change = new DefectClaimChange();
        change.claimId = claimId;
        change.fromStatus = fromStatus;
        change.toStatus = toStatus;
        change.createdAt = now;
        change.updatedAt = now;
        return change;
    }

    public DefectClaimStatus getToStatus() {
        return toStatus;
    }
}
