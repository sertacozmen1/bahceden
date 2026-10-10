package com.bahceden.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Gönderi testlerinin ortak sipariş akışı: sepetteki ürünlerden ödenmiş sipariş
 * ve satıcı siparişi, gönderi açma (add-shipments). Uçlar gerçek HTTP
 * akışıyla çağrılır; kısayol yoktur.
 */
public final class OrderFlows {

    private static final AtomicInteger KEYS = new AtomicInteger();

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ListingFixtures fixtures;

    public OrderFlows(MockMvc mockMvc, JdbcTemplate jdbc, ObjectMapper objectMapper, ListingFixtures fixtures) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.fixtures = fixtures;
    }

    /** Alıcının sepetinden sipariş oluşturur ve öder; dönen değer sipariş numarası. */
    public String paidOrder(long buyerAccountId, String buyerToken) throws Exception {
        var body = mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .header("Idempotency-Key", "gonderi-anahtari-" + KEYS.incrementAndGet())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("addressId", fixtures.address(buyerAccountId, "35")))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var number = objectMapper.readTree(body).get("orderNumber").asText();
        mockMvc.perform(post("/api/v1/orders/" + number + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(jsonPath("$.status").value("PAID"));
        return number;
    }

    /** Siparişin ilk satıcı siparişinin kimliği. */
    public long sellerOrderId(String orderNumber) {
        return jdbc.queryForObject("""
                select so.id from seller_orders so join orders o on o.id = so.order_id
                where o.order_number = ? order by so.id limit 1""", Long.class, orderNumber);
    }

    public ResultActions createShipment(String sellerToken, long sellerOrderId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/sellers/me/orders/" + sellerOrderId + "/shipment")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** Orta boy koliyle gönderi açar ve 201 bekler; dönen değer entegratör anahtarı. */
    public String openShipment(String sellerToken, long sellerOrderId) throws Exception {
        createShipment(sellerToken, sellerOrderId, "{\"packageSize\":\"MEDIUM\"}").andExpect(status().isCreated());
        return jdbc.queryForObject("select integrator_ref from shipments where seller_order_id = ? and status <> 'CANCELLED'",
                String.class, sellerOrderId);
    }
}
