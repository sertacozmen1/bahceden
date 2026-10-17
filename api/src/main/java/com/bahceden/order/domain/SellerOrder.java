package com.bahceden.order.domain;

import com.bahceden.order.OrderPricing.Commission;
import com.bahceden.seller.domain.Seller;
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
import java.util.List;
import java.util.Optional;

/**
 * Siparişin bir satıcıya düşen kısmı (add-cart-and-checkout D6): kargo,
 * komisyon, hakediş ve iptal birimi (ADR-055, ADR-056).
 *
 * <p>Ürün tutarı, kargo ücreti, komisyon oranı ve sabit ücreti, hesaplanan
 * komisyon ve hakediş sipariş anında yazılır ve sonra değişmez. İptal edilen
 * satıcı siparişinin tutarları denetim izi için korunur; geçersizliği durumdan
 * okunur. Durum yalnızca {@link #apply(SellerOrderTransition, Instant)} ile değişir.
 */
@Entity
@Table(name = "seller_orders")
public class SellerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SellerOrderStatus status;

    @Column(name = "items_total", nullable = false)
    private long itemsTotal;

    @Column(name = "shipping_fee", nullable = false)
    private long shippingFee;

    @Column(name = "commission_rate_bp", nullable = false)
    private int commissionRateBp;

    @Column(name = "commission_fixed", nullable = false)
    private long commissionFixed;

    @Column(name = "commission_amount", nullable = false)
    private long commissionAmount;

    @Column(name = "seller_payout", nullable = false)
    private long sellerPayout;

    @Column(name = "dispatch_days", nullable = false)
    private int dispatchDays;

    @Column(name = "dispatch_due_at")
    private Instant dispatchDueAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by", length = 10)
    private CancelledBy cancelledBy;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "sellerOrder", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    protected SellerOrder() {
    }

    /**
     * @param items        satırlar; ürün tutarı bunların toplamıdır
     * @param shippingFee  bu satıcının kargo ücreti (ADR-058)
     * @param commission   komisyon ve hakediş (ADR-055)
     * @param dispatchDays satırlardaki ürünlerin en uzun kargoya verme süresi
     */
    public static SellerOrder open(Order order, Seller seller, List<OrderItem> items, long shippingFee,
                                   Commission commission, int dispatchDays, Instant now) {
        var sellerOrder = new SellerOrder();
        sellerOrder.order = order;
        sellerOrder.seller = seller;
        sellerOrder.status = SellerOrderStatus.PENDING_PAYMENT;
        for (var item : items) {
            item.attachTo(sellerOrder);
            sellerOrder.items.add(item);
            sellerOrder.itemsTotal += item.getLineTotal();
        }
        if (commission.amount() + commission.sellerPayout() != sellerOrder.itemsTotal) {
            throw new IllegalArgumentException("Komisyon ürün tutarıyla tutarsız");
        }
        sellerOrder.shippingFee = shippingFee;
        sellerOrder.commissionRateBp = commission.rateBp();
        sellerOrder.commissionFixed = commission.fixedMinor();
        sellerOrder.commissionAmount = commission.amount();
        sellerOrder.sellerPayout = commission.sellerPayout();
        sellerOrder.dispatchDays = dispatchDays;
        sellerOrder.createdAt = now;
        sellerOrder.updatedAt = now;
        return sellerOrder;
    }

    public boolean allows(SellerOrderTransition transition) {
        return transition.allowsFrom(status);
    }

    public void apply(SellerOrderTransition transition, Instant now) {
        if (!allows(transition)) {
            throw new IllegalStateException("İzinsiz satıcı siparişi geçişi: " + status + " -> " + transition);
        }
        status = transition.to();
        updatedAt = now;
    }

    /** Ödeme anında hesaplanan kargoya verme son tarihi (D3). */
    public void scheduleDispatch(Instant dueAt) {
        this.dispatchDueAt = dueAt;
    }

    /** Kargodan önce iptal: taraf ve gerekçe yazılır (ADR-056 §1). */
    public void cancel(CancelledBy by, String reason, Instant now) {
        apply(SellerOrderTransition.CANCEL, now);
        this.cancelledBy = by;
        this.cancelReason = reason;
    }

    /** Son tarih geçti ve sipariş hâlâ kargoya verilmedi (bilgi; D9). */
    public boolean isDispatchOverdue(Instant now) {
        return status == SellerOrderStatus.NEW && dispatchDueAt != null && now.isAfter(dispatchDueAt);
    }

    /** İadede alıcıya dönen tutar: ürünler + kargo ücreti (ADR-056 §1). */
    public long refundableAmount() {
        return itemsTotal + shippingFee;
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public Seller getSeller() {
        return seller;
    }

    public SellerOrderStatus getStatus() {
        return status;
    }

    public long getItemsTotal() {
        return itemsTotal;
    }

    public long getShippingFee() {
        return shippingFee;
    }

    /** Sipariş anında uygulanan komisyon oranı, baz puan (ADR-055). */
    public int getCommissionRateBp() {
        return commissionRateBp;
    }

    /** Sipariş anında uygulanan sabit komisyon, kuruş (ADR-055). */
    public long getCommissionFixed() {
        return commissionFixed;
    }

    public long getCommissionAmount() {
        return commissionAmount;
    }

    public long getSellerPayout() {
        return sellerPayout;
    }

    public int getDispatchDays() {
        return dispatchDays;
    }

    public Optional<Instant> getDispatchDueAt() {
        return Optional.ofNullable(dispatchDueAt);
    }

    public Optional<CancelledBy> getCancelledBy() {
        return Optional.ofNullable(cancelledBy);
    }

    public Optional<String> getCancelReason() {
        return Optional.ofNullable(cancelReason);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
