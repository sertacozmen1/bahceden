package com.bahceden.order;

import com.bahceden.common.outbox.ProcessedEvents;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Sipariş ödendi" olayından sipariş e-postalarını gönderir (ADR-112).
 *
 * <p>E-postaların tek yolu budur: ödeme transaction'ı e-posta göndermez,
 * yalnızca olayı outbox'a yazar (ADR-110). Tüketici olayı tek transaction'da
 * işler: olayı işlenmiş olarak kaydeder, siparişi kimliğiyle okur ve
 * {@link OrderNotifier#orderPaid} e-postaları transaction bittikten sonra,
 * alıcının ve her satıcının hesap dilinde gönderir. Aynı olay ikinci kez
 * gelirse kayıt zaten vardır ve e-posta gitmez.
 *
 * <p>Gönderim hatası işlemi geri almaz ve yeniden denenmez (ADR-019, bugünkü
 * davranış). İşlenemeyen olay (bozuk gövde, veritabanı hatası) hata işleyicinin
 * denemelerinden sonra {@code .dlt} topic'ine düşer. Log'a e-posta adresi
 * yazılmaz; yalnızca olay kimliği ve sipariş numarası (K10).
 */
@Component
class OrderPaidEmailConsumer {

    /** {@code processed_events.consumer} değeri; tüketici grubu adından bağımsızdır. */
    static final String CONSUMER = "order-paid-email";

    private static final Logger log = LoggerFactory.getLogger(OrderPaidEmailConsumer.class);

    private final OrderRepository orders;
    private final OrderNotifier notifier;
    private final ProcessedEvents processedEvents;
    private final ObjectMapper objectMapper;

    OrderPaidEmailConsumer(OrderRepository orders, OrderNotifier notifier, ProcessedEvents processedEvents,
                           ObjectMapper objectMapper) {
        this.orders = orders;
        this.notifier = notifier;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
    }

    /**
     * @throws JsonProcessingException gövde okunamıyor; yeniden denenmez, doğrudan {@code .dlt}'ye gider
     */
    @KafkaListener(id = CONSUMER, topics = OrderPaidEvent.TOPIC, groupId = "bahceden-order-mail")
    @Transactional
    public void onOrderPaid(String payload) throws JsonProcessingException {
        var event = objectMapper.readValue(payload, OrderPaidEvent.class);
        if (!processedEvents.markIfFirst(CONSUMER, event.eventId())) {
            log.info("Sipariş ödendi olayı daha önce işlenmiş, atlandı: olay {}, sipariş {}",
                    event.eventId(), event.orderNumber());
            return;
        }
        var order = orders.findById(event.orderId());
        if (order.isEmpty()) {
            log.warn("Sipariş ödendi olayının siparişi bulunamadı, e-posta gönderilmedi: olay {}, sipariş {}",
                    event.eventId(), event.orderNumber());
            return;
        }
        notifier.orderPaid(order.get(), order.get().getSellerOrders());
        log.info("Sipariş ödendi e-postaları hazırlandı: olay {}, sipariş {}", event.eventId(), event.orderNumber());
    }
}
