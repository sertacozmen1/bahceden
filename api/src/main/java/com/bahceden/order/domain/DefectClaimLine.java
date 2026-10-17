package com.bahceden.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;

/**
 * Talebin bir satırı: hangi sipariş satırının kaç adedi sorunlu (ADR-080).
 * Kısmi iade adet bazındadır; tutar birim fiyat × adettir (ADR-082).
 */
@Entity
@Table(name = "defect_claim_lines")
public class DefectClaimLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false, updatable = false)
    private DefectClaim claim;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false, updatable = false)
    private OrderItem orderItem;

    @Column(name = "claimed_quantity", nullable = false, updatable = false)
    private int claimedQuantity;

    @Column(name = "offered_quantity")
    private Integer offeredQuantity;

    @Column(name = "refunded_quantity")
    private Integer refundedQuantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DefectClaimLine() {
    }

    static DefectClaimLine of(DefectClaim claim, OrderItem orderItem, int claimedQuantity, Instant now) {
        var line = new DefectClaimLine();
        line.claim = claim;
        line.orderItem = orderItem;
        line.claimedQuantity = claimedQuantity;
        line.createdAt = now;
        line.updatedAt = now;
        return line;
    }

    void offer(int quantity, Instant now) {
        this.offeredQuantity = quantity;
        this.updatedAt = now;
    }

    void refund(int quantity, Instant now) {
        this.refundedQuantity = quantity;
        this.updatedAt = now;
    }

    public Optional<Integer> getOfferedQuantity() {
        return Optional.ofNullable(offeredQuantity);
    }

    public Optional<Integer> getRefundedQuantity() {
        return Optional.ofNullable(refundedQuantity);
    }

    public OrderItem getOrderItem() {
        return orderItem;
    }

    public int getClaimedQuantity() {
        return claimedQuantity;
    }
}
