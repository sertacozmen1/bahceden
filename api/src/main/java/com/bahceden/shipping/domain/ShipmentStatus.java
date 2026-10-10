package com.bahceden.shipping.domain;

/**
 * Gönderinin entegratördeki durumu (add-shipments D2).
 *
 * <p>Durumlar sıralı ilerler: {@link #CREATED} → {@link #HANDED_OVER} →
 * {@link #IN_TRANSIT} → {@link #DELIVERED} veya {@link #DELIVERY_FAILED}.
 * Geri giden bildirim yok sayılır. {@link #CANCELLED} yalnızca kargoya
 * verilmeden önce olur.
 */
public enum ShipmentStatus {

    /** Gönderi açıldı, etiket üretildi; paket henüz kargoda değil. */
    CREATED(0),

    /** Kargo firması paketi teslim aldı. */
    HANDED_OVER(1),

    /** Paket yolda. */
    IN_TRANSIT(2),

    /** Alıcıya teslim edildi. */
    DELIVERED(3),

    /** Teslim edilemedi. */
    DELIVERY_FAILED(3),

    /** Kargoya verilmeden iptal edildi. */
    CANCELLED(-1);

    private final int rank;

    ShipmentStatus(int rank) {
        this.rank = rank;
    }

    /** Durum sorgusu bu gönderiyi hâlâ izliyor mu. */
    public boolean isOpen() {
        return this == CREATED || this == HANDED_OVER || this == IN_TRANSIT;
    }

    /** Paket kargo firmasına verildi mi (bu durum ve sonrası). */
    public boolean isHandedOver() {
        return rank >= HANDED_OVER.rank;
    }

    /** {@code next} bu durumdan ileri bir adım mı; eşit veya geri bildirim değildir. */
    public boolean isBefore(ShipmentStatus next) {
        return next.rank > rank;
    }
}
