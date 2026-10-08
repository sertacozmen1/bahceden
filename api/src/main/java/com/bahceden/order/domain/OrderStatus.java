package com.bahceden.order.domain;

/**
 * Alıcının siparişinin ödeme durumu (add-cart-and-checkout D6, D8).
 *
 * <p>Sipariş {@link #PENDING_PAYMENT} açılır ve ödeme tamamlanınca bir sonuca
 * bağlanır; sonuçlanmış sipariş bir daha değişmez. Kargo ve iptal satıcı
 * siparişi seviyesindedir ({@link SellerOrderStatus}).
 */
public enum OrderStatus {

    /** Sipariş oluştu, ödeme bekleniyor; stok henüz düşmedi. */
    PENDING_PAYMENT,

    /** Ödeme sağlayıcıdan doğrulandı ve stok düştü. */
    PAID,

    /** Sağlayıcı ödemenin başarısız olduğunu bildirdi. */
    PAYMENT_FAILED,

    /** Ödeme süresi içinde tamamlanmadı; alınmış para varsa iade edildi. */
    PAYMENT_EXPIRED,

    /** Ödeme alındı ama stok yetmedi; ödemenin tamamı iade edildi. */
    CANCELLED_OUT_OF_STOCK
}
