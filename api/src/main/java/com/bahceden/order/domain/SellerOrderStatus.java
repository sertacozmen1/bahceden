package com.bahceden.order.domain;

/**
 * Satıcı siparişinin durumu (add-cart-and-checkout D6, add-shipments D2).
 *
 * <p>Gönderinin açılması durumu değiştirmez: gönderisi açılmış ama kargoya
 * verilmemiş sipariş {@link #NEW} kalır ve iptal edilebilir (ADR-056 §1).
 * "Yolda" yalnızca gönderinin durumudur.
 */
public enum SellerOrderStatus {

    /** Siparişin ödemesi bekleniyor; satıcıya gösterilmez. */
    PENDING_PAYMENT,

    /** Ödendi; satıcı hazırlıyor ve kargoya verecek. */
    NEW,

    /** Kargodan önce alıcı veya satıcı iptal etti; tamamı iade edildi (ADR-056 §1). */
    CANCELLED,

    /** Ödeme gerçekleşmedi, süresi doldu veya stok yetmedi; satış hiç olmadı. */
    VOID,

    /** Kargo firması paketi teslim aldı; artık iptal edilemez (add-shipments D2). */
    SHIPPED,

    /** Alıcıya teslim edildi. */
    DELIVERED,

    /** Kargo teslim edemedi; admin kararı bekliyor (add-shipments D7). */
    DELIVERY_FAILED,

    /** Teslim edilemedi; admin kararıyla ürün ve kargo ücreti iade edildi. */
    REFUNDED,

    /** Teslim edilemedi; admin gerekçeyle iadesiz kapattı. */
    CLOSED
}
