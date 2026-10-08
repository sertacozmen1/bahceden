package com.bahceden.order.dto;

import com.bahceden.catalog.dto.MoneyView;
import com.bahceden.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Alıcının sipariş detayı (orders spec'i "Alıcı kendi siparişlerini görür").
 *
 * @param orderNumber     sipariş numarası
 * @param status          ödeme durumu
 * @param createdAt       oluşma anı (UTC)
 * @param paidAt          ödeme anı; ödenmemişse {@code null}
 * @param shippingAddress teslimat adresi
 * @param itemsTotal      ürün toplamı
 * @param shippingTotal   kargo toplamı
 * @param grandTotal      genel toplam
 * @param sellerOrders    satıcı siparişleri
 */
@Schema(name = "OrderDetail", description = "Alıcının sipariş detayı")
public record OrderDetail(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sipariş numarası", example = "BHC-7K2M9QXA4T")
        String orderNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ödeme durumu", example = "PAID")
        OrderStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Oluşma anı (UTC)",
                example = "2026-10-08T09:30:00Z")
        Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, description = "Ödeme anı (UTC)",
                example = "2026-10-08T09:31:00Z")
        Instant paidAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Teslimat adresi")
        ShippingAddressView shippingAddress,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ürün toplamı")
        MoneyView itemsTotal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Kargo toplamı")
        MoneyView shippingTotal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Genel toplam")
        MoneyView grandTotal,
        @ArraySchema(schema = @Schema(implementation = BuyerSellerOrderView.class),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satıcı siparişleri"))
        List<BuyerSellerOrderView> sellerOrders) {
}
