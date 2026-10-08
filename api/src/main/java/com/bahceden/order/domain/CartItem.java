package com.bahceden.order.domain;

import com.bahceden.catalog.domain.Product;
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

/**
 * Sepet satırı: ürün ve adet (add-cart-and-checkout D4).
 *
 * <p>Fiyat tutulmaz: sepet her okumada ürünün güncel fiyatını, stoğunu ve
 * satın alınabilirliğini gösterir; fiyat siparişte sabitlenir (D6). Ürün
 * tembel yüklenir; sepet sorgusu ürünü ve satıcıyı tek sorguda getirir.
 */
@Entity
@Table(name = "cart_items")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cart_id", nullable = false)
    private Long cartId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CartItem() {
    }

    public static CartItem of(long cartId, Product product, int quantity, Instant now) {
        var item = new CartItem();
        item.cartId = cartId;
        item.product = product;
        item.quantity = quantity;
        item.createdAt = now;
        item.updatedAt = now;
        return item;
    }

    public void changeQuantity(int quantity, Instant now) {
        this.quantity = quantity;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getCartId() {
        return cartId;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }
}
