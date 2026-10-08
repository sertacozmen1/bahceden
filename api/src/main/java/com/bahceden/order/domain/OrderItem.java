package com.bahceden.order.domain;

import com.bahceden.catalog.domain.ProductUnit;
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
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Sipariş satırı (add-cart-and-checkout D6): ürünün sipariş anındaki adı,
 * adresi, birimi ve birim fiyatı kopyalanır. Ürün sonradan değişse,
 * arşivlense veya kaldırılsa da sipariş aynı kalır.
 */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_order_id", nullable = false)
    private SellerOrder sellerOrder;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "product_slug", nullable = false, length = 140)
    private String productSlug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductUnit unit;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false)
    private long lineTotal;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderItem() {
    }

    public static OrderItem of(long productId, String productName, String productSlug, ProductUnit unit,
                               long unitPrice, int quantity, Instant now) {
        var item = new OrderItem();
        item.productId = productId;
        item.productName = productName;
        item.productSlug = productSlug;
        item.unit = unit;
        item.unitPrice = unitPrice;
        item.quantity = quantity;
        item.lineTotal = unitPrice * quantity;
        item.createdAt = now;
        item.updatedAt = now;
        return item;
    }

    void attachTo(SellerOrder owner) {
        this.sellerOrder = owner;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public String getProductSlug() {
        return productSlug;
    }

    public ProductUnit getUnit() {
        return unit;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getLineTotal() {
        return lineTotal;
    }
}
