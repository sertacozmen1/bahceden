package com.bahceden.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.bahceden.common.outbox.OutboxRelay;
import com.bahceden.common.outbox.OutboxRepository;
import com.bahceden.common.security.AccessTokenIssuer;
import com.bahceden.support.ListingFixtures;
import com.bahceden.support.MutableClock;
import com.bahceden.support.OrderFlows;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.ScriptedPaymentProvider;
import com.bahceden.support.TestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Sipariş ödendi olayının e-posta tüketicisi (cart-and-checkout "Olay iki kez
 * teslim edilir", ADR-112).
 *
 * <p>{@code critical}: e-posta ödemenin tek bildirimidir; kaybolmamalı ve iki kez
 * gitmemeli. Testler gerçek Kafka'ya karşı koşar: dinleyici bağlamla açılır,
 * relay turu doğrudan çağrılır. "Tüketici kaydı işledi" anı, tüketici grubunun
 * offset'inin topic sonuna ulaşmasıyla beklenir; süreye güvenilmez.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class,
        ScriptedPaymentProvider.Config.class})
@DisplayName("OrderPaidEmailConsumer: sipariş ödendi e-postaları")
class OrderPaidEmailConsumerTest {

    private static final String GROUP = "bahceden-order-mail";
    private static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private AccessTokenIssuer accessTokens;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ScriptedPaymentProvider paymentProvider;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private OutboxRepository outbox;

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private KafkaConnectionDetails kafkaConnection;

    private ListingFixtures fixtures;
    private long buyer;
    private String buyerToken;

    @BeforeEach
    void setUp() throws Exception {
        clock.advance(Duration.ofDays(1));
        paymentProvider.reset();
        fixtures = new ListingFixtures(jdbc, accessTokens);
        fixtures.cleanUp();
        var seller = fixtures.approvedSeller("uretici@ornek.test");
        fixtures.activePayoutAccount(seller);
        var product = fixtures.publishedProduct(seller, "eposta-domates", 2450, 10);
        buyer = fixtures.account("alici@ornek.test", "en", "ROLE_CUSTOMER");
        buyerToken = fixtures.token(buyer, "ROLE_CUSTOMER");
        fixtures.cartItem(buyer, product, 2);
        awaitConsumed();
        mail.reset();
    }

    @AfterEach
    void tearDown() throws Exception {
        awaitConsumed();
        fixtures.cleanUp();
    }

    @Test
    @DisplayName("Olay işlenince alıcıya kendi dilinde, satıcıya kendi dilinde e-posta gider; işlenmiş kaydı yazılır")
    void olay_epostalari_gonderir() throws Exception {
        var number = flows().paidOrder(buyer, buyerToken);
        assertThat(mail.sent()).as("ödeme transaction'ı e-posta göndermez").isEmpty();

        assertThat(relay.relayOnce()).isEqualTo(1);

        await().atMost(WAIT).untilAsserted(() -> assertThat(mail.sent()).hasSize(2));
        assertThat(mail.onlySentTo("alici@ornek.test").subject()).contains("we received your order", number);
        assertThat(mail.onlySentTo("uretici@ornek.test").subject()).contains("yeni sipariş", number);
        assertThat(processedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Aynı olay ikinci kez teslim edilirse e-posta tekrar gitmez (spec: olay iki kez teslim edilir)")
    void tekrar_teslim_eposta_gondermez() throws Exception {
        var number = flows().paidOrder(buyer, buyerToken);
        var payload = outbox.findByAggregateKeyOrderByIdAsc(number).getFirst().getPayload();
        relay.relayOnce();
        await().atMost(WAIT).untilAsserted(() -> assertThat(mail.sent()).hasSize(2));

        kafka.send(OrderPaidEvent.TOPIC, number, payload).get();
        awaitConsumed();

        assertThat(mail.sent()).hasSize(2);
        assertThat(processedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Okunamayan gövde yeniden denenmeden .dlt topic'ine düşer; arkadaki olay işlenir")
    void bozuk_govde_dlt_ye_duser() throws Exception {
        var marker = "bozuk-govde-" + UUID.randomUUID();
        kafka.send(OrderPaidEvent.TOPIC, "BOZUK", marker).get();
        var number = flows().paidOrder(buyer, buyerToken);
        relay.relayOnce();

        await().atMost(WAIT).untilAsserted(() -> assertThat(mail.sent()).hasSize(2));
        assertThat(deadLetterValues()).contains(marker);
        assertThat(mail.onlySentTo("alici@ornek.test").subject()).contains(number);
    }

    @Test
    @DisplayName("Siparişi bulunamayan olay e-posta göndermez ama işlenmiş sayılır; yeniden denenmez")
    void siparisi_olmayan_olay() throws Exception {
        var event = new OrderPaidEvent(UUID.randomUUID(), OrderPaidEvent.TYPE, Instant.now(clock), -1L, "BHC-YOK",
                List.of(), new OrderPaidEvent.Amount(100, "TRY"));
        kafka.send(OrderPaidEvent.TOPIC, event.orderNumber(), objectMapper.writeValueAsString(event)).get();

        awaitConsumed();

        assertThat(mail.sent()).isEmpty();
        assertThat(processedCount()).isEqualTo(1);
    }

    private OrderFlows flows() {
        return new OrderFlows(mockMvc, jdbc, objectMapper, fixtures);
    }

    private int processedCount() {
        var count = jdbc.queryForObject("select count(*) from processed_events where consumer = ?", Integer.class,
                OrderPaidEmailConsumer.CONSUMER);
        return count == null ? 0 : count;
    }

    /** Tüketici grubunun offset'i her bölümde topic sonuna ulaşana kadar bekler. */
    private void awaitConsumed() throws Exception {
        var config = new Properties();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", kafkaConnection.getBootstrapServers()));
        try (var admin = AdminClient.create(config)) {
            var partitions = admin.describeTopics(List.of(OrderPaidEvent.TOPIC)).allTopicNames().get()
                    .get(OrderPaidEvent.TOPIC).partitions().stream()
                    .map(info -> new TopicPartition(OrderPaidEvent.TOPIC, info.partition()))
                    .toList();
            await().atMost(WAIT).until(() -> {
                var ends = admin.listOffsets(partitions.stream()
                                .collect(Collectors.toMap(partition -> partition, partition -> OffsetSpec.latest())))
                        .all().get();
                Map<TopicPartition, OffsetAndMetadata> committed = new HashMap<>(
                        admin.listConsumerGroupOffsets(GROUP).partitionsToOffsetAndMetadata().get());
                return partitions.stream().allMatch(partition -> {
                    long end = ends.get(partition).offset();
                    var position = committed.get(partition);
                    return end == 0 || (position != null && position.offset() >= end);
                });
            });
        }
    }

    private List<String> deadLetterValues() {
        var config = new Properties();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", kafkaConnection.getBootstrapServers()));
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-" + UUID.randomUUID());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        var values = new java.util.ArrayList<String>();
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(OrderPaidEvent.DEAD_LETTER_TOPIC));
            var deadline = Instant.now().plus(Duration.ofSeconds(10));
            while (Instant.now().isBefore(deadline) && values.isEmpty()) {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> values.add(record.value()));
            }
        }
        return values;
    }
}
