package com.bahceden.order.dto;

import com.bahceden.catalog.dto.MoneyView;
import com.bahceden.order.domain.SellerOrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Admin listesindeki teslim edilemeyen satıcı siparişi (shipments spec'i
 * "Teslim edilemeyen siparişte admin karar verir", add-shipments D7).
 *
 * @param id               satıcı siparişinin kimliği
 * @param orderNumber      sipariş numarası
 * @param status           durum
 * @param sellerName       üreticinin görünen adı
 * @param refundableAmount iade kararında alıcıya dönecek tutar: ürünler + kargo
 * @param createdAt        oluşma anı (UTC)
 */
@Schema(name = "AdminSellerOrderSummary", description = "Admin listesinde satıcı siparişi")
public record AdminSellerOrderSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satıcı siparişinin kimliği", example = "17")
        long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sipariş numarası", example = "BHC-7K2M9QXA4T")
        String orderNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Durum", example = "DELIVERY_FAILED")
        SellerOrderStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Üreticinin görünen adı",
                example = "Yılmaz Bahçesi")
        String sellerName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "İade kararında alıcıya dönecek tutar: ürünler + kargo ücreti")
        MoneyView refundableAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Oluşma anı (UTC)",
                example = "2026-10-08T09:30:00Z")
        Instant createdAt) {
}
