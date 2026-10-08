package com.bahceden.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Alıcının siparişi (cart-and-checkout spec'i, add-cart-and-checkout D6).
 *
 * <p>Teslimat adresi ve tutarlar sipariş anında kopyalanır; sonradan değişen
 * adres, fiyat veya yapılandırma siparişi etkilemez. Sipariş silinmez
 * (AGENTS.md §11.4). Satıcı siparişleri bu kaydın parçasıdır ve onunla birlikte
 * yazılır.
 *
 * <p>Adres ve sipariş içeriği kişisel veridir (K10): {@link #toString()}
 * bilinçli olarak tanımlanmadı, log'a yalnızca sipariş numarası yazılır.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 16)
    private String orderNumber;

    @Column(name = "buyer_account_id", nullable = false)
    private Long buyerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "ship_recipient_name", nullable = false, length = 120)
    private String shipRecipientName;

    @Column(name = "ship_phone", nullable = false, length = 16)
    private String shipPhone;

    @Column(name = "ship_province_code", nullable = false, length = 2)
    private String shipProvinceCode;

    @Column(name = "ship_district", nullable = false, length = 80)
    private String shipDistrict;

    @Column(name = "ship_address_line", nullable = false, length = 250)
    private String shipAddressLine;

    @Column(name = "ship_postal_code", length = 5)
    private String shipPostalCode;

    @Column(name = "items_total", nullable = false)
    private long itemsTotal;

    @Column(name = "shipping_total", nullable = false)
    private long shippingTotal;

    @Column(name = "grand_total", nullable = false)
    private long grandTotal;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "payment_expires_at", nullable = false)
    private Instant paymentExpiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<SellerOrder> sellerOrders = new ArrayList<>();

    protected Order() {
    }

    /** Teslimat adresinin sipariş anındaki kopyası. */
    public record ShippingAddress(String recipientName, String phone, String provinceCode, String district,
                                  String addressLine, String postalCode) {
    }

    public static Order open(String orderNumber, long buyerAccountId, String idempotencyKey, ShippingAddress address,
                             String currency, Instant paymentExpiresAt, Instant now) {
        var order = new Order();
        order.orderNumber = orderNumber;
        order.buyerAccountId = buyerAccountId;
        order.idempotencyKey = idempotencyKey;
        order.status = OrderStatus.PENDING_PAYMENT;
        order.shipRecipientName = address.recipientName();
        order.shipPhone = address.phone();
        order.shipProvinceCode = address.provinceCode();
        order.shipDistrict = address.district();
        order.shipAddressLine = address.addressLine();
        order.shipPostalCode = address.postalCode();
        order.currency = currency;
        order.paymentExpiresAt = paymentExpiresAt;
        order.createdAt = now;
        order.updatedAt = now;
        return order;
    }

    /** Satıcı siparişini ekler ve toplamları günceller. */
    public void add(SellerOrder sellerOrder) {
        sellerOrders.add(sellerOrder);
        itemsTotal += sellerOrder.getItemsTotal();
        shippingTotal += sellerOrder.getShippingFee();
        grandTotal = itemsTotal + shippingTotal;
    }

    /** Ödeme doğrulandı ve stok düştü (D8). */
    public void markPaid(Instant now) {
        requirePending();
        status = OrderStatus.PAID;
        paidAt = now;
        updatedAt = now;
    }

    /** Ödeme sonuçlanmadan kapandı: başarısız, süresi doldu veya stok yetmedi (D8). */
    public void close(OrderStatus outcome, Instant now) {
        requirePending();
        if (outcome == OrderStatus.PENDING_PAYMENT || outcome == OrderStatus.PAID) {
            throw new IllegalArgumentException("Kapanış sonucu değil: " + outcome);
        }
        status = outcome;
        updatedAt = now;
    }

    public boolean isPaymentExpired(Instant now) {
        return now.isAfter(paymentExpiresAt);
    }

    private void requirePending() {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Sonuçlanmış sipariş değiştirilemez: " + orderNumber);
        }
    }

    public Long getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public Long getBuyerAccountId() {
        return buyerAccountId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public ShippingAddress getShippingAddress() {
        return new ShippingAddress(shipRecipientName, shipPhone, shipProvinceCode, shipDistrict, shipAddressLine,
                shipPostalCode);
    }

    public long getItemsTotal() {
        return itemsTotal;
    }

    public long getShippingTotal() {
        return shippingTotal;
    }

    public long getGrandTotal() {
        return grandTotal;
    }

    public String getCurrency() {
        return currency;
    }

    public Optional<Instant> getPaidAt() {
        return Optional.ofNullable(paidAt);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<SellerOrder> getSellerOrders() {
        return Collections.unmodifiableList(sellerOrders);
    }
}
