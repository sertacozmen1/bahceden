package com.bahceden.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.shipping.ShipmentStatusPoller;
import com.bahceden.shipping.domain.ShipmentStatus;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Ayıplı ürün talebi testlerinin ortak akışı (add-defect-claims): gönderiyi
 * teslim etmek ve talep açmak gerçek HTTP akışıyla yapılır; kısayol yoktur.
 */
public final class ClaimFlows {

    public static final String DESCRIPTION = "Domates kasasının üst sırası ezik geldi.";

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final OrderFlows orders;
    private final ScriptedShippingIntegrator integrator;
    private final ShipmentStatusPoller poller;

    public ClaimFlows(MockMvc mockMvc, JdbcTemplate jdbc, OrderFlows orders, ScriptedShippingIntegrator integrator,
                      ShipmentStatusPoller poller) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.orders = orders;
        this.integrator = integrator;
        this.poller = poller;
    }

    /** Gönderiyi açar ve test entegratörüyle teslim eder; teslim anı saatin şimdiki anıdır. */
    public void deliver(String sellerToken, long sellerOrderId) throws Exception {
        var ref = orders.openShipment(sellerToken, sellerOrderId);
        integrator.report(ref, ShipmentStatus.DELIVERED);
        poller.pollOnce();
    }

    /**
     * Bir fotoğrafla talep açar ve 201 bekler; dönen değer talep kimliği.
     *
     * @param quantities sipariş satırı kimliğine göre bildirilen adet
     */
    public long open(String buyerToken, String orderNumber, long sellerOrderId, Map<Long, Integer> quantities)
            throws Exception {
        var request = multipart("/api/v1/orders/" + orderNumber + "/seller-orders/" + sellerOrderId + "/claim");
        request.file(new MockMultipartFile("photos", "fotograf.jpg", MediaType.IMAGE_JPEG_VALUE,
                TestImages.jpeg(800, 600)));
        request.param("description", DESCRIPTION);
        int index = 0;
        for (var line : quantities.entrySet()) {
            request.param("lines[" + index + "].orderItemId", Long.toString(line.getKey()));
            request.param("lines[" + index + "].quantity", Integer.toString(line.getValue()));
            index++;
        }
        mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("select id from defect_claims where seller_order_id = ?", Long.class, sellerOrderId);
    }

    /** Sipariş satırının kimliği, ürün adresine göre. */
    public long itemId(String productSlug) {
        return jdbc.queryForObject("select id from order_items where product_slug = ?", Long.class, productSlug);
    }
}
