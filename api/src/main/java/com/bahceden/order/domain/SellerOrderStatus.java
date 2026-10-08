package com.bahceden.order.domain;

/**
 * Satıcı siparişinin durumu (add-cart-and-checkout D6). Kargo durumları
 * (kargoya verildi, teslim edildi) gönderi change'inde eklenir (ADR-057).
 */
public enum SellerOrderStatus {

    /** Siparişin ödemesi bekleniyor; satıcıya gösterilmez. */
    PENDING_PAYMENT,

    /** Ödendi; satıcı hazırlıyor ve kargoya verecek. */
    NEW,

    /** Kargodan önce alıcı veya satıcı iptal etti; tamamı iade edildi (ADR-056 §1). */
    CANCELLED,

    /** Ödeme gerçekleşmedi, süresi doldu veya stok yetmedi; satış hiç olmadı. */
    VOID
}
