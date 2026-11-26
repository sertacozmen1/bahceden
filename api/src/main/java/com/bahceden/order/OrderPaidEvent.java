package com.bahceden.order;

import com.bahceden.common.outbox.OutboxMessage;
import com.bahceden.order.domain.Order;
import com.bahceden.order.domain.SellerOrder;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * "Sipariş ödendi" iş olayı, {@code bahceden.order.paid.v1} topic'inin gövdesi (ADR-111).
 *
 * <p>Kişisel veri taşımaz (K10): ad, e-posta, adres, telefon yoktur; tüketici
 * ihtiyaç duyduğunu kimliklerle veritabanından okur. Para minor unit + para
 * birimidir (ADR-006), zaman UTC'dir. Alan eklemek kırıcı değildir; alan silmek
 * veya anlamını değiştirmek yeni sürümlü topic ({@code .v2}) gerektirir.
 *
 * @param eventId        tüketicinin tekrar teslimi tanıdığı kimlik
 * @param eventType      her zaman {@code ORDER_PAID}
 * @param occurredAt     ödemenin kaydedildiği an
 * @param orderId        sipariş kimliği
 * @param orderNumber    sipariş numarası; Kafka anahtarı
 * @param sellerOrderIds siparişin satıcı siparişleri
 * @param grandTotal     alıcının ödediği toplam
 */
public record OrderPaidEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        long orderId,
        String orderNumber,
        List<Long> sellerOrderIds,
        Amount grandTotal) implements OutboxMessage {

    public static final String TYPE = "ORDER_PAID";
    public static final String TOPIC = "bahceden.order.paid.v1";

    /** İşlenemeyen olayların topic'i; ad hata işleyicinin son ekiyle aynıdır (ADR-112). */
    public static final String DEAD_LETTER_TOPIC = TOPIC + ".dlt";

    /** Minor unit tutar (ADR-006); HTTP sözleşmesindeki para şekliyle aynıdır. */
    public record Amount(long amount, String currency) {
    }

    public OrderPaidEvent {
        sellerOrderIds = List.copyOf(sellerOrderIds);
    }

    static OrderPaidEvent of(Order order, Instant paidAt) {
        return new OrderPaidEvent(UUID.randomUUID(), TYPE, paidAt, order.getId(), order.getOrderNumber(),
                order.getSellerOrders().stream().map(SellerOrder::getId).toList(),
                new Amount(order.getGrandTotal(), order.getCurrency()));
    }

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public String aggregateKey() {
        return orderNumber;
    }
}
