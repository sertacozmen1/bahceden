package com.bahceden.order;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Sipariş olaylarının topic'leri (ADR-111).
 *
 * <p>Broker'da otomatik topic açılışı kapalıdır; topic'ler burada tanımlanır ve
 * uygulama açılışında yoksa oluşturulur. Bölüm sayısı sabittir; kopya sayısı
 * broker'ın varsayılanıdır (yerelde tek düğüm, üretimde barındırma kararı, B5).
 */
@Configuration
class OrderEventTopics {

    /** Anahtar sipariş numarasıdır; aynı siparişin olayları aynı bölümde sıralı kalır. */
    private static final int PARTITIONS = 3;

    @Bean
    NewTopic orderPaidTopic() {
        return TopicBuilder.name(OrderPaidEvent.TOPIC).partitions(PARTITIONS).build();
    }
}
