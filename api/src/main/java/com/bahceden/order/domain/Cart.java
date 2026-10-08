package com.bahceden.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Hesabın tek sepeti (add-cart-and-checkout D4). İlk ürün eklendiğinde açılır.
 *
 * <p>Hesap bir kimlik olarak tutulur, ilişki olarak değil: {@code order}
 * domaini {@code account} entity'sine kilitlenmez.
 */
@Entity
@Table(name = "carts")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, unique = true)
    private Long accountId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Cart() {
    }

    public static Cart of(long accountId, Instant now) {
        var cart = new Cart();
        cart.accountId = accountId;
        cart.createdAt = now;
        cart.updatedAt = now;
        return cart;
    }

    public void touch(Instant now) {
        updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }
}
