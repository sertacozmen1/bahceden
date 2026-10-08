package com.bahceden.order.dto;

import com.bahceden.catalog.dto.MoneyView;
import com.bahceden.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Alıcının sipariş listesindeki satır (orders spec'i).
 *
 * @param orderNumber sipariş numarası
 * @param status      ödeme durumu
 * @param grandTotal  genel toplam
 * @param createdAt   oluşma anı (UTC)
 */
@Schema(name = "OrderSummary", description = "Alıcının sipariş listesindeki satır")
public record OrderSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sipariş numarası", example = "BHC-7K2M9QXA4T")
        String orderNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ödeme durumu", example = "PAID")
        OrderStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Genel toplam")
        MoneyView grandTotal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Oluşma anı (UTC)",
                example = "2026-10-08T09:30:00Z")
        Instant createdAt) {
}
