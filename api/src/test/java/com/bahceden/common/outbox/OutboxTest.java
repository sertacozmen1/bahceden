package com.bahceden.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.common.BahcedenProperties;
import com.bahceden.common.security.AccessTokenIssuer;
import com.bahceden.order.OrderPaidEvent;
import com.bahceden.payment.PaymentProvider.PaymentOutcome;
import com.bahceden.support.ListingFixtures;
import com.bahceden.support.MutableClock;
import com.bahceden.support.OrderFlows;
import com.bahceden.support.RecordingMailSender;
import com.bahceden.support.ScriptedPaymentProvider;
import com.bahceden.support.TestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
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
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sipariş ödendi olayı: outbox'a yazım ve Kafka'ya aktarım (cart-and-checkout
 * "Ödeme sağlayıcıdan doğrulanır; stok ödeme başarılı olunca düşer", ADR-110, ADR-111).
 *
 * <p>{@code critical}: olay sipariş durumuyla atomik olmalı, kaybolmamalı ve iki
 * kez gönderilmemeli. Testler gerçek PostgreSQL ve Kafka'ya karşı koşar; relay
 * zamanlayıcısı kapalıdır, tur doğrudan çağrılır.
 */
@Tag("critical")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, RecordingMailSender.Config.class, MutableClock.Config.class,
        ScriptedPaymentProvider.Config.class})
@DisplayName("Outbox: sipariş ödendi olayı")
class OutboxTest {

    private static final Duration POLL = Duration.ofSeconds(15);

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
    private OutboxRepository events;

    @Autowired
    private ProcessedEventRepository processedEvents;

    @Autowired
    private OutboxPublisher publisher;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private BahcedenProperties properties;

    @Autowired
    private KafkaConnectionDetails kafka;

    private ListingFixtures fixtures;
    private long buyer;
    private String buyerToken;
    private long productId;

    @BeforeEach
    void setUp() {
        clock.advance(Duration.ofDays(1));
        paymentProvider.reset();
        fixtures = new ListingFixtures(jdbc, accessTokens);
        fixtures.cleanUp();
        var seller = fixtures.approvedSeller("uretici@ornek.test");
        fixtures.activePayoutAccount(seller);
        productId = fixtures.publishedProduct(seller, "olay-domates", 2450, 10);
        buyer = fixtures.account("alici@ornek.test", "tr", "ROLE_CUSTOMER");
        buyerToken = fixtures.token(buyer, "ROLE_CUSTOMER");
        fixtures.cartItem(buyer, productId, 2);
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanUp();
    }

    @Test
    @DisplayName("Başarılı ödeme aynı transaction'da tek ORDER_PAID satırı yazar; gövdede kişisel veri yok")
    void basarili_odeme_tek_olay_yazar() throws Exception {
        var number = flows().paidOrder(buyer, buyerToken);

        var rows = events.findByAggregateKeyOrderByIdAsc(number);
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getEventType()).isEqualTo(OrderPaidEvent.TYPE);
            assertThat(row.getTopic()).isEqualTo(OrderPaidEvent.TOPIC);
            assertThat(row.getPublishedAt()).isEmpty();
            assertThat(row.getPayload()).doesNotContain("@").doesNotContain("Ornek Mahallesi");
        });
        var payload = objectMapper.readTree(rows.getFirst().getPayload());
        var fields = new ArrayList<String>();
        payload.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder(
                "eventId", "eventType", "occurredAt", "orderId", "orderNumber", "sellerOrderIds", "grandTotal");
        assertThat(payload.get("eventId").asText()).isEqualTo(rows.getFirst().getEventId().toString());
        assertThat(payload.get("orderNumber").asText()).isEqualTo(number);
        assertThat(payload.get("sellerOrderIds")).hasSize(1);
        assertThat(payload.get("grandTotal").get("currency").asText()).isEqualTo("TRY");
        assertThat(payload.get("grandTotal").get("amount").asLong()).isPositive();
    }

    @Test
    @DisplayName("Başarısız ödeme olay yazmaz (spec: sonuçlanmayan ödeme olay üretmez)")
    void basarisiz_odeme_olay_yazmaz() throws Exception {
        var number = createOrder();
        paymentProvider.answerVerifyWith(PaymentOutcome.FAILED);

        pay(number).andExpect(jsonPath("$.status").value("PAYMENT_FAILED"));

        assertThat(events.findByAggregateKeyOrderByIdAsc(number)).isEmpty();
    }

    @Test
    @DisplayName("Stok yetmeyen ödeme iade edilir ve olay yazmaz (spec: sonuçlanmayan ödeme olay üretmez)")
    void stoksuz_odeme_olay_yazmaz() throws Exception {
        var number = createOrder();
        jdbc.update("update products set stock_quantity = 0 where id = ?", productId);

        pay(number).andExpect(jsonPath("$.status").value("CANCELLED_OUT_OF_STOCK"));

        assertThat(events.findByAggregateKeyOrderByIdAsc(number)).isEmpty();
    }

    @Test
    @DisplayName("Geri alınan transaction outbox satırı bırakmaz; transaction yoksa yazım reddedilir")
    void geri_alinan_transaction_satir_birakmaz() {
        var key = "GERI-ALINAN";
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            publisher.append(event(key));
            status.setRollbackOnly();
        });

        assertThat(events.findByAggregateKeyOrderByIdAsc(key)).isEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> publisher.append(event(key)))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("Relay olayı sipariş numarası anahtarıyla gönderir ve satırı yayınlandı işaretler")
    void relay_gonderir_ve_isaretler() throws Exception {
        var number = flows().paidOrder(buyer, buyerToken);
        var eventId = events.findByAggregateKeyOrderByIdAsc(number).getFirst().getEventId();

        assertThat(relay.relayOnce()).isEqualTo(1);

        assertThat(events.findByAggregateKeyOrderByIdAsc(number)).singleElement()
                .satisfies(row -> assertThat(row.getPublishedAt()).isPresent());
        var record = consumeEvent(eventId);
        assertThat(record.key()).isEqualTo(number);
        assertThat(objectMapper.readTree(record.value()).get("eventType").asText()).isEqualTo(OrderPaidEvent.TYPE);
        assertThat(relay.relayOnce()).isZero();
    }

    @Test
    @DisplayName("Kafka erişilemezken olay bekler ve deneme sayısı artar; broker dönünce gönderilir (spec: olay akışına ulaşılamıyor)")
    void kafka_yokken_olay_bekler() throws Exception {
        var number = flows().paidOrder(buyer, buyerToken);
        var unreachable = unreachableKafka();
        try {
            var down = new OutboxRelay(events, processedEvents, unreachable, transactionManager, properties, clock);

            assertThat(down.relayOnce()).isZero();
        } finally {
            unreachable.getProducerFactory().reset();
        }
        assertThat(events.findByAggregateKeyOrderByIdAsc(number)).singleElement().satisfies(row -> {
            assertThat(row.getPublishedAt()).isEmpty();
            assertThat(row.getAttempts()).isEqualTo(1);
        });

        assertThat(relay.relayOnce()).isEqualTo(1);
        consumeEvent(events.findByAggregateKeyOrderByIdAsc(number).getFirst().getEventId());
    }

    @Test
    @DisplayName("Başka bir relay satırları kilitlemişken ikinci relay onları atlar; olay iki kez gönderilmez")
    void kilitli_satirlar_atlanir() throws Exception {
        var number = flows().paidOrder(buyer, buyerToken);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var seen = new AtomicInteger();
        var holder = new Thread(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            seen.set(events.lockUnpublished(Limit.of(100)).size());
            locked.countDown();
            await(release);
        }));
        holder.start();
        try {
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            assertThat(relay.relayOnce()).isZero();
        } finally {
            release.countDown();
            holder.join(TimeUnit.SECONDS.toMillis(10));
        }
        assertThat(seen.get()).isEqualTo(1);

        assertThat(relay.relayOnce()).isEqualTo(1);
        assertThat(events.findByAggregateKeyOrderByIdAsc(number)).singleElement()
                .satisfies(row -> assertThat(row.getPublishedAt()).isPresent());
    }

    @Test
    @DisplayName("Saklama süresi geçmiş yayınlanmış satır silinir; yayınlanmamış satır kalır")
    void saklama_suresi_gecen_yayinlanmis_satir_silinir() throws Exception {
        var published = flows().paidOrder(buyer, buyerToken);
        relay.relayOnce();
        fixtures.cartItem(buyer, productId, 1);
        var pending = flows().paidOrder(buyer, buyerToken);

        clock.advance(properties.outbox().retention().plusMinutes(1));

        assertThat(relay.purgePublished()).isEqualTo(1);
        assertThat(events.findByAggregateKeyOrderByIdAsc(published)).isEmpty();
        assertThat(events.findByAggregateKeyOrderByIdAsc(pending)).hasSize(1);
    }

    private OrderFlows flows() {
        return new OrderFlows(mockMvc, jdbc, objectMapper, fixtures);
    }

    private String createOrder() throws Exception {
        var body = mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .header("Idempotency-Key", "olay-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("addressId", fixtures.address(buyer, "35")))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("orderNumber").asText();
    }

    private org.springframework.test.web.servlet.ResultActions pay(String number) throws Exception {
        return mockMvc.perform(post("/api/v1/orders/" + number + "/payment")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken));
    }

    private OrderPaidEvent event(String key) {
        return new OrderPaidEvent(UUID.randomUUID(), OrderPaidEvent.TYPE, Instant.now(clock), 1L, key, List.of(2L),
                new OrderPaidEvent.Amount(100, "TRY"));
    }

    /** Topic'i baştan okur ve verilen olay kimliğini taşıyan kaydı döndürür. */
    private ConsumerRecord<String, String> consumeEvent(UUID eventId) throws Exception {
        var config = new Properties();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", kafka.getBootstrapServers()));
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-" + UUID.randomUUID());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(OrderPaidEvent.TOPIC));
            var deadline = Instant.now().plus(POLL);
            while (Instant.now().isBefore(deadline)) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (objectMapper.readTree(record.value()).get("eventId").asText().equals(eventId.toString())) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("Olay topic'te bulunamadı: " + eventId);
    }

    /** Kapalı porta bağlanan, kısa zaman aşımlı üretici. */
    private static KafkaTemplate<String, String> unreachableKafka() {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1",
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 500,
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 500,
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 1000);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config, new StringSerializer(), new StringSerializer()));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Kilit tutan iş serbest bırakılmadı");
            }
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Kilit tutan iş kesildi", cause);
        }
    }
}
