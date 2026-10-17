package com.bahceden.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Ayıplı ürün talebi (ADR-056 §4, ADR-079, ADR-080).
 *
 * <p>Satıcı siparişi başına tek talep vardır; satıcı siparişinin durumu
 * değişmez, sonucu talep taşır. Açıklama kişisel veri olabilir ve log'a
 * yazılmaz (ADR-084).
 */
@Entity
@Table(name = "defect_claims")
public class DefectClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_order_id", nullable = false, updatable = false)
    private SellerOrder sellerOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DefectClaimStatus status;

    @Column(nullable = false, length = 1000, updatable = false)
    private String description;

    @Column(name = "seller_deadline_at", nullable = false)
    private Instant sellerDeadlineAt;

    @Column(name = "seller_reason", length = 500)
    private String sellerReason;

    @Column(name = "buyer_deadline_at")
    private Instant buyerDeadlineAt;

    @Column(name = "refund_amount")
    private Long refundAmount;

    @Column(name = "shipping_refunded", nullable = false)
    private boolean shippingRefunded;

    @Column(name = "commission_after")
    private Long commissionAfter;

    @Column(name = "payout_after")
    private Long payoutAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolved_by", length = 10)
    private DefectClaimResolver resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "claim", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<DefectClaimLine> lines = new ArrayList<>();

    /** Küme: satırlarla birlikte tek sorguda okunabilsin diye (iki liste birlikte getirilemez). */
    @OneToMany(mappedBy = "claim", cascade = CascadeType.PERSIST)
    @OrderBy("position")
    private Set<DefectClaimPhoto> photos = new LinkedHashSet<>();

    protected DefectClaim() {
    }

    /**
     * Alıcının açtığı talep; satıcının yanıt süresi başlar (ADR-081).
     *
     * @param sellerDeadlineAt satıcının yanıt vermesi gereken son an
     */
    public static DefectClaim open(SellerOrder sellerOrder, String description, Instant sellerDeadlineAt, Instant now) {
        var claim = new DefectClaim();
        claim.sellerOrder = sellerOrder;
        claim.status = DefectClaimStatus.OPEN;
        claim.description = description;
        claim.sellerDeadlineAt = sellerDeadlineAt;
        claim.createdAt = now;
        claim.updatedAt = now;
        return claim;
    }

    /** @param quantity alıcının sorunlu bildirdiği adet; satın alınan adeti aşmadığı serviste denetlenir */
    public void addLine(OrderItem item, int quantity, Instant now) {
        lines.add(DefectClaimLine.of(this, item, quantity, now));
    }

    public void addPhoto(int position, String cardKey, String largeKey, int width, int height, Instant now) {
        photos.add(DefectClaimPhoto.of(this, position, cardKey, largeKey, width, height, now));
    }

    public boolean allows(DefectClaimTransition transition) {
        return transition.allowsFrom(status);
    }

    /**
     * Satıcının kısmi teklifi: önerilen adetler satırlara yazılır, alıcının
     * süresi başlar (ADR-080).
     *
     * @param offered sipariş satırı kimliğine göre önerilen adet; olmayan satır 0
     */
    public void offer(Map<Long, Integer> offered, Instant buyerDeadlineAt, Instant now) {
        apply(DefectClaimTransition.SELLER_OFFER, now);
        lines.forEach(line -> line.offer(offered.getOrDefault(line.getOrderItem().getId(), 0), now));
        this.buyerDeadlineAt = buyerDeadlineAt;
    }

    /** Satıcının gerekçeli reddi; alıcının süresi başlar. */
    public void reject(String reason, Instant buyerDeadlineAt, Instant now) {
        apply(DefectClaimTransition.SELLER_REJECT, now);
        this.sellerReason = reason;
        this.buyerDeadlineAt = buyerDeadlineAt;
    }

    /**
     * Yönetime taşınır: alıcı taşıdı ya da satıcı süresinde yanıt vermedi
     * (ADR-080, ADR-083).
     */
    public void escalate(DefectClaimTransition transition, Instant now) {
        if (transition.to() != DefectClaimStatus.ESCALATED) {
            throw new IllegalArgumentException("Yönetime taşımayan geçiş: " + transition);
        }
        apply(transition, now);
    }

    /**
     * Süresi doldu mu: açık talepte satıcının, teklif veya retten sonra
     * alıcının süresi (ADR-083).
     */
    public boolean isOverdue(Instant now) {
        return switch (status) {
            case OPEN -> !now.isBefore(sellerDeadlineAt);
            case OFFERED, REJECTED -> buyerDeadlineAt != null && !now.isBefore(buyerDeadlineAt);
            default -> false;
        };
    }

    /**
     * İadeyle sonuç: iade edilen adetler satırlara, tutarlar talebe yazılır
     * (ADR-082).
     *
     * @param refunded   sipariş satırı kimliğine göre iade edilen adet; olmayan satır 0
     * @param transition iadeyle biten geçiş (satıcı kabulü, alıcının teklifi kabulü)
     */
    public void refund(DefectClaimTransition transition, Map<Long, Integer> refunded, long amount,
                       boolean shippingRefunded, long commissionAfter, long payoutAfter, DefectClaimResolver by,
                       Instant now) {
        if (transition.to() != DefectClaimStatus.REFUNDED) {
            throw new IllegalArgumentException("İadeyle bitmeyen geçiş: " + transition);
        }
        apply(transition, now);
        lines.forEach(line -> line.refund(refunded.getOrDefault(line.getOrderItem().getId(), 0), now));
        this.refundAmount = amount;
        this.shippingRefunded = shippingRefunded;
        this.commissionAfter = commissionAfter;
        this.payoutAfter = payoutAfter;
        resolve(by, now);
    }

    /** İadesiz kapanış (alıcı reddi kabul etti). */
    public void close(DefectClaimTransition transition, DefectClaimResolver by, Instant now) {
        if (transition.to() != DefectClaimStatus.CLOSED) {
            throw new IllegalArgumentException("Kapanışla bitmeyen geçiş: " + transition);
        }
        apply(transition, now);
        resolve(by, now);
    }

    private void apply(DefectClaimTransition transition, Instant now) {
        if (!allows(transition)) {
            throw new IllegalStateException("İzinsiz talep geçişi: " + status + " -> " + transition);
        }
        status = transition.to();
        updatedAt = now;
    }

    private void resolve(DefectClaimResolver by, Instant now) {
        this.resolvedBy = by;
        this.resolvedAt = now;
    }

    public Long getId() {
        return id;
    }

    public SellerOrder getSellerOrder() {
        return sellerOrder;
    }

    public DefectClaimStatus getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public Instant getSellerDeadlineAt() {
        return sellerDeadlineAt;
    }

    public Optional<String> getSellerReason() {
        return Optional.ofNullable(sellerReason);
    }

    public Optional<Instant> getBuyerDeadlineAt() {
        return Optional.ofNullable(buyerDeadlineAt);
    }

    public Optional<Long> getRefundAmount() {
        return Optional.ofNullable(refundAmount);
    }

    public boolean isShippingRefunded() {
        return shippingRefunded;
    }

    public Optional<Long> getCommissionAfter() {
        return Optional.ofNullable(commissionAfter);
    }

    public Optional<Long> getPayoutAfter() {
        return Optional.ofNullable(payoutAfter);
    }

    public Optional<Instant> getResolvedAt() {
        return Optional.ofNullable(resolvedAt);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<DefectClaimLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public Set<DefectClaimPhoto> getPhotos() {
        return Collections.unmodifiableSet(photos);
    }
}
